package io.github.kerbiy.crate.catalog.search;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SearchProperties.class)
class SearchConfig {
}
