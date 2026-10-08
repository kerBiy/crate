package io.github.kerbiy.crate.review.events;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Reads a topic the way a test needs it: only messages sent after this reader was opened.
 * No consumer group (partitions are assigned by hand and nothing is committed), so it never
 * interferes with anything else reading the topic.
 */
class TopicReader implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;
    private final List<ConsumerRecord<String, String>> seen = new ArrayList<>();

    TopicReader(String bootstrapServers, String topic) {
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false));
        List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                .map(info -> new TopicPartition(topic, info.partition()))
                .toList();
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        // seekToEnd is lazy; position() resolves it now, before the test sends anything.
        partitions.forEach(consumer::position);
    }

    /** Polls until a message matches, and returns it; fails after {@code timeout}. */
    ConsumerRecord<String, String> await(Predicate<ConsumerRecord<String, String>> match, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : seen) {
                if (match.test(record)) {
                    return record;
                }
            }
            consumer.poll(Duration.ofMillis(100)).forEach(seen::add);
        }
        throw new AssertionError("No matching message within " + timeout + "; saw " + seen.size() + ": "
                + seen.stream().map(ConsumerRecord::value).toList());
    }

    /** Every message read so far, in the order received (per partition, that's the log order). */
    List<ConsumerRecord<String, String>> seen() {
        return seen;
    }

    @Override
    public void close() {
        consumer.close();
    }
}
