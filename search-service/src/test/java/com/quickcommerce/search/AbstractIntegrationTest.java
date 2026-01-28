package com.quickcommerce.search;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static GenericContainer<?> meilisearch = new GenericContainer<>("getmeili/meilisearch:v1.9")
            .withExposedPorts(7700)
            .withEnv("MEILI_MASTER_KEY", "masterKey")
            .withEnv("MEILI_NO_ANALYTICS", "true");

    protected static WireMockServer wireMockServer;

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void afterAll() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("meilisearch.host",
                () -> "http://" + meilisearch.getHost() + ":" + meilisearch.getMappedPort(7700));
        registry.add("meilisearch.apiKey", () -> "masterKey");

        registry.add("clients.inventory.url", () -> wireMockServer.baseUrl());
        registry.add("clients.catalog.url", () -> wireMockServer.baseUrl());
    }
}
