package com.artha.infrastructure.messaging;

import org.springframework.stereotype.Component;

/**
 * Maps aggregate types to Kafka topics. Keeping this centralized means we can
 * change topic layouts (e.g. split by event type, or by tenant) without touching
 * every event-producing site.
 */
@Component
public class TopicResolver {
    public String topicFor(String aggregateType) {
        return "artha." + aggregateType.toLowerCase() + ".events";
    }
}
