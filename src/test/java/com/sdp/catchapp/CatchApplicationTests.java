package com.sdp.catchapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class CatchApplicationTests {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
			.withDatabaseName("catch_test");

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	Flyway flyway;

	@Test
	void connectsToIsolatedPostgres() {
		assertThat(jdbcTemplate.queryForObject("select 1", Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select current_database()", String.class))
				.isEqualTo(POSTGRES.getDatabaseName());
	}

	@Test
	void initializesAndValidatesFlyway() {
		assertThat(jdbcTemplate.queryForObject(
				"select to_regclass('public.flyway_schema_history')", String.class)).isNotNull();
		assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
		assertThat(flyway.info().pending()).isEmpty();
	}

}
