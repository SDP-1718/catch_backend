package com.sdp.catchapp.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sdp.catchapp.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class,
		properties = "spring.jackson.default-property-inclusion=non_null")
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

	private static final String BASE_PATH = "/__test/common-api";

	@Autowired
	MockMvc mockMvc;

	@Test
	void serializesSuccessfulResponseWithExactCommonFields() throws Exception {
		mockMvc.perform(get(BASE_PATH + "/success"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(content().json("""
						{
						  "isSuccess": true,
						  "code": "SUCCESS",
						  "message": "요청을 성공적으로 처리했습니다.",
						  "result": {"name": "CATch"}
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void keepsNullResultEvenWhenGlobalJacksonConfigurationOmitsNulls() throws Exception {
		mockMvc.perform(get(BASE_PATH + "/empty"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "isSuccess": true,
						  "code": "SUCCESS",
						  "message": "요청을 성공적으로 처리했습니다.",
						  "result": null
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void returnsCreatedResponseWithControllerSelectedHttpStatus() throws Exception {
		mockMvc.perform(post(BASE_PATH + "/created"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.isSuccess").value(true))
				.andExpect(jsonPath("$.code").value("CREATED"))
				.andExpect(jsonPath("$.message").value("리소스가 생성되었습니다."))
				.andExpect(jsonPath("$.result.id").value(1));
	}

	@Test
	void doesNotWrapNoContentResponse() throws Exception {
		mockMvc.perform(post(BASE_PATH + "/no-content"))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));
	}

	@Test
	void doesNotWrapFileResponse() throws Exception {
		mockMvc.perform(get(BASE_PATH + "/file").accept(MediaType.APPLICATION_OCTET_STREAM))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sample.bin"))
				.andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
				.andExpect(content().bytes(new byte[] {1, 2, 3}));
	}

	@Test
	void doesNotWrapRedirectResponse() throws Exception {
		mockMvc.perform(get(BASE_PATH + "/redirect"))
				.andExpect(status().isFound())
				.andExpect(header().string(HttpHeaders.LOCATION, "/target"))
				.andExpect(content().string(""));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidRequests")
	void standardizesMvcClientErrors(String description, MockHttpServletRequestBuilder request,
			int expectedStatus, String expectedCode, String expectedMessage) throws Exception {
		mockMvc.perform(request)
				.andExpect(status().is(expectedStatus))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(content().json("""
						{
						  "isSuccess": false,
						  "code": "%s",
						  "message": "%s",
						  "result": null
						}
						""".formatted(expectedCode, expectedMessage), JsonCompareMode.STRICT));
	}

	static Stream<Arguments> invalidRequests() {
		return Stream.of(
				Arguments.of("유효성 검증 실패", post(BASE_PATH + "/body")
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"),
						400, "VALIDATION_ERROR", "입력값이 올바르지 않습니다."),
				Arguments.of("잘못된 JSON", post(BASE_PATH + "/body")
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":"),
						400, "VALIDATION_ERROR", "입력값이 올바르지 않습니다."),
				Arguments.of("필수 본문 누락", post(BASE_PATH + "/body")
						.contentType(MediaType.APPLICATION_JSON),
						400, "VALIDATION_ERROR", "입력값이 올바르지 않습니다."),
				Arguments.of("필수 파라미터 누락", get(BASE_PATH + "/query"),
						400, "VALIDATION_ERROR", "입력값이 올바르지 않습니다."),
				Arguments.of("파라미터 타입 오류", get(BASE_PATH + "/query").param("page", "abc"),
						400, "VALIDATION_ERROR", "입력값이 올바르지 않습니다."),
				Arguments.of("파라미터 제약 위반", get(BASE_PATH + "/query").param("page", "-1"),
						400, "VALIDATION_ERROR", "입력값이 올바르지 않습니다."),
				Arguments.of("존재하지 않는 경로", get(BASE_PATH + "/missing"),
						404, "RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
				Arguments.of("지원하지 않는 응답 형식", get(BASE_PATH + "/success")
						.accept(MediaType.APPLICATION_XML),
						406, "NOT_ACCEPTABLE", "지원하지 않는 응답 형식입니다."),
				Arguments.of("지원하지 않는 요청 형식", post(BASE_PATH + "/body")
						.contentType(MediaType.TEXT_PLAIN).content("CATch"),
						415, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 형식입니다.")
		);
	}

	@Test
	void preservesAllowedMethodsHeader() throws Exception {
		mockMvc.perform(post(BASE_PATH + "/success"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
				.andExpect(jsonPath("$.isSuccess").value(false))
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	void usesCustomBusinessErrorCodeAndHttpStatus() throws Exception {
		mockMvc.perform(get(BASE_PATH + "/business-error"))
				.andExpect(status().isConflict())
				.andExpect(content().json("""
						{
						  "isSuccess": false,
						  "code": "TEST_CONFLICT",
						  "message": "현재 상태에서는 처리할 수 없습니다.",
						  "result": null
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void preservesRetryAfterForRateLimitErrors() throws Exception {
		mockMvc.perform(get(BASE_PATH + "/rate-limit"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
				.andExpect(jsonPath("$.isSuccess").value(false))
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}

	@Test
	void preservesUnlistedHttpErrorStatusWithoutExposingItsReason() throws Exception {
		mockMvc.perform(get(BASE_PATH + "/gone"))
				.andExpect(status().isGone())
				.andExpect(content().json("""
						{
						  "isSuccess": false,
						  "code": "HTTP_410",
						  "message": "요청을 처리할 수 없습니다.",
						  "result": null
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void preservesMvcErrorHeadersWhileHidingProblemDetails() throws Exception {
		mockMvc.perform(get(BASE_PATH + "/unavailable"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
				.andExpect(content().json("""
						{
						  "isSuccess": false,
						  "code": "FEATURE_UNAVAILABLE",
						  "message": "일시적으로 서비스를 이용할 수 없습니다.",
						  "result": null
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void treatsControllerReturnValueValidationFailureAsServerError() throws Exception {
		mockMvc.perform(get(BASE_PATH + "/invalid-return"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.isSuccess").value(false))
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
	}

	@Test
	void hidesInternalExceptionDetails() throws Exception {
		var response = mockMvc.perform(get(BASE_PATH + "/unexpected-error"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().json("""
						{
						  "isSuccess": false,
						  "code": "INTERNAL_ERROR",
						  "message": "서버 내부 오류가 발생했습니다.",
						  "result": null
						}
						""", JsonCompareMode.STRICT))
				.andReturn().getResponse().getContentAsString();
		assertThat(response).doesNotContain("private-value", "IllegalStateException", "stackTrace");
	}

	@RestController
	@RequestMapping(value = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	static class TestController {

		@GetMapping("/success")
		ApiResponse<Map<String, String>> success() {
			return ApiResponse.success(Map.of("name", "CATch"));
		}

		@GetMapping("/empty")
		ApiResponse<Void> empty() {
			return ApiResponse.success(null);
		}

		@PostMapping("/created")
		ResponseEntity<ApiResponse<Map<String, Integer>>> created() {
			return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(Map.of("id", 1)));
		}

		@PostMapping("/no-content")
		ResponseEntity<Void> noContent() {
			return ResponseEntity.noContent().build();
		}

		@GetMapping(value = "/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
		ResponseEntity<byte[]> file() {
			return ResponseEntity.ok()
					.contentType(MediaType.APPLICATION_OCTET_STREAM)
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sample.bin")
					.body(new byte[] {1, 2, 3});
		}

		@GetMapping("/redirect")
		ResponseEntity<Void> redirect() {
			return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/target")).build();
		}

		@PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
		ApiResponse<NameRequest> body(@Valid @RequestBody NameRequest request) {
			return ApiResponse.success(request);
		}

		@GetMapping("/query")
		ApiResponse<Integer> query(@RequestParam("page") @Min(0) int page) {
			return ApiResponse.success(page);
		}

		@GetMapping("/business-error")
		ApiResponse<Void> businessError() {
			throw new BusinessException(TestErrorCode.CONFLICT);
		}

		@GetMapping("/rate-limit")
		ApiResponse<Void> rateLimit() {
			throw new RateLimitExceededException(30);
		}

		@GetMapping("/gone")
		ApiResponse<Void> gone() {
			throw new ResponseStatusException(HttpStatus.GONE, "private-value");
		}

		@GetMapping("/unavailable")
		ApiResponse<Void> unavailable() {
			ErrorResponseException exception = new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE,
					ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "private-value"), null);
			exception.getHeaders().set(HttpHeaders.RETRY_AFTER, "60");
			throw exception;
		}

		@GetMapping("/invalid-return")
		@NotNull
		ApiResponse<Void> invalidReturn() {
			return null;
		}

		@GetMapping("/unexpected-error")
		ApiResponse<Void> unexpectedError() {
			throw new IllegalStateException("private-value");
		}
	}

	record NameRequest(@NotBlank String name) {
	}

	enum TestErrorCode implements ErrorCode {
		CONFLICT;

		@Override
		public HttpStatus getHttpStatus() {
			return HttpStatus.CONFLICT;
		}

		@Override
		public String getCode() {
			return "TEST_CONFLICT";
		}

		@Override
		public String getMessage() {
			return "현재 상태에서는 처리할 수 없습니다.";
		}
	}
}
