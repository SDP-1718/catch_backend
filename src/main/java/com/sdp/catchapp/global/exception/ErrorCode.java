package com.sdp.catchapp.global.exception;

import org.springframework.http.HttpStatusCode;

public interface ErrorCode {

	HttpStatusCode getHttpStatus();

	String getCode();

	String getMessage();
}
