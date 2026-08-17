package com.kanvra;

import org.junit.jupiter.api.Test;

class KanvraBackendApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Verifies the full application context, Flyway migration, and entity/schema
        // consistency (ddl-auto=validate) against a real PostgreSQL container.
    }
}

