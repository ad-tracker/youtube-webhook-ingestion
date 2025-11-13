package com.adtracker.webhookingest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.rabbitmq.host=localhost"
})
@DisplayName("WebhookIngestionApplication Tests")
class WebhookIngestionApplicationTest {

    @Test
    @DisplayName("Application context should load successfully")
    void contextLoads() {
        // This test verifies that the Spring application context loads without errors
    }
}
