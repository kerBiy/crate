package io.github.kerbiy.crate.review.catalog;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
class CatalogConfig {

    @Bean
    CatalogClient catalogClient(CatalogProperties properties) {
        return CatalogClient.create(properties);
    }
}
