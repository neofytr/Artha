package com.artha.infrastructure.persistence;

import com.artha.core.event.DomainEvent;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.HashMap;
import java.util.Map;

/**
 * Serializes domain events to/from JSON. A type registry maps event class names
 * to Java classes so deserialization works without storing fully-qualified names
 * in every row. The registry is the schema contract — renaming or removing an
 * event type is a breaking change to the event store.
 */
public class EventSerializer {

    private final ObjectMapper mapper;
    private final Map<String, Class<? extends DomainEvent>> registry = new HashMap<>();

    public EventSerializer() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        this.mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    public void register(Class<? extends DomainEvent> eventClass) {
        registry.put(eventClass.getSimpleName(), eventClass);
    }

    public String serialize(DomainEvent event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize event " + event.getEventType(), e);
        }
    }

    public DomainEvent deserialize(String eventType, String payload) {
        Class<? extends DomainEvent> clazz = registry.get(eventType);
        if (clazz == null) {
            throw new IllegalStateException("Unknown event type: " + eventType
                    + ". Register it via EventSerializer.register().");
        }
        try {
            return mapper.readValue(payload, clazz);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize event " + eventType, e);
        }
    }

    public ObjectMapper mapper() { return mapper; }
}
