package io.github.kerbiy.crate.review.events;

import io.github.kerbiy.crate.contracts.review.ReviewEvents;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * The broker doesn't create topics on first use (auto-creation is off), so we declare ours.
 * At startup Spring's KafkaAdmin creates any declared topic that doesn't exist yet, and leaves
 * existing ones alone.
 */
@Configuration(proxyBeanMethods = false)
class KafkaTopicConfig {

    @Bean
    NewTopic reviewEventsTopic() {
        // Replication factor 1: there is one broker (a documented limitation, SPEC 6.2).
        return TopicBuilder.name(ReviewEvents.TOPIC).partitions(ReviewEvents.PARTITIONS).replicas(1).build();
    }
}
