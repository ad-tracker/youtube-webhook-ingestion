package com.adtracker.webhookingest;

import com.adtracker.webhookingest.config.TestContainersInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitializer.class)
@ActiveProfiles("test")
@DisplayName("WebhookIngestionApplication Tests")
class WebhookIngestionApplicationTest {

    @Test
    @DisplayName("Application context should load successfully")
    void contextLoads() {
        // This test verifies that the Spring application context loads without errors
    }
}
