package io.github.kerbiy.crate.catalog.stats;

import io.github.kerbiy.crate.contracts.review.ReviewEvents;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * review-service owns review.events and declares it too. Declaring it here as well is harmless
 * (KafkaAdmin only creates missing topics) and means catalog doesn't depend on start order: a
 * consumer subscribed to a missing topic would only notice it at its next metadata refresh,
 * up to 5 minutes later.
 */
@Configuration(proxyBeanMethods = false)
class KafkaTopicConfig {

    @Bean
    NewTopic reviewEventsTopic() {
        return TopicBuilder.name(ReviewEvents.TOPIC).partitions(ReviewEvents.PARTITIONS).replicas(1).build();
    }
}
