package com.quickcommerce.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;

/**
 * R2DBC configuration. Kept in a separate @Configuration class so that
 * @WebFluxTest slices don't pick up R2DBC auditing and fail to find r2dbcMappingContext.
 */
@Configuration
@EnableR2dbcAuditing
public class R2dbcConfig {
}
