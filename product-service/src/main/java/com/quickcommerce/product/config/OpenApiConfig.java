package com.quickcommerce.product.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for Product Service API documentation
 * Access docs at: /swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productServiceAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Product Service API")
                        .description("Inventory & Catalog Management for Quick Commerce Platform")
                        .version("v1.0")
                        .contact(new Contact()
                                .name("Quick Commerce Team")
                                .email("dev@quickcommerce.zm"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://quickcommerce.zm")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8081")
                                .description("Development Server"),
                        new Server()
                                .url("https://api.quickcommerce.zm")
                                .description("Production Server")
                ));
    }
}
