package io.github.kerbiy.crate.contracts;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * What every Kafka message carries (SPEC 6.1): metadata about the event, plus its payload.
 *
 * @param eventId      unique per event; consumers record it to skip duplicates (at-least-once delivery)
 * @param eventType    the payload's name, e.g. "ReviewCreated"; consumers dispatch on it, not on Java types
 * @param eventVersion bumped only for a breaking change to the payload (SPEC 6.3)
 * @param occurredAt   when it happened, in UTC
 * @param producer     the service that published it
 */
public record EventEnvelope<P>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        P payload) {

    /** A new event of version 1, with a fresh id, happening now. */
    public static <P> EventEnvelope<P> of(String producer, String eventType, P payload) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, 1, Instant.now().truncatedTo(ChronoUnit.MILLIS),
                producer, payload);
    }
}
