package com.kanvra;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests: boots the full Spring context against a
 * real PostgreSQL container (Testcontainers) and runs Flyway migrations.
 * When Docker is unavailable the tests are skipped (not failed), so a plain
 * {@code mvn test} still completes. See docs/TECH_DOC.md §22 and AGENT.md §11.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("kanvra_test")
            .withUsername("test")
            .withPassword("test");
}
