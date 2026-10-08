package io.github.kerbiy.crate.review.users;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UserProperties.class)
class UserConfig {

    @Bean
    UserClient userClient(UserProperties properties) {
        return UserClient.create(properties);
    }
}
