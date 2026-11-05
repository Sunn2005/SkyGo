package com.skygo.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="SKYGO_TEST_DB_URL",matches=".+")
@org.springframework.test.context.TestPropertySource(properties={"spring.datasource.url=${SKYGO_TEST_DB_URL}","spring.datasource.username=${SKYGO_TEST_DB_USERNAME:root}","spring.datasource.password=${SKYGO_TEST_DB_PASSWORD:}"})
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
