package com.mirage.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Encodes/decodes {@link SensorMessage}s as single-line JSON for the wire. */
public final class JsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonCodec() {
    }

    public static String encode(SensorMessage message) {
        try {
            return MAPPER.writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to encode message", e);
        }
    }

    public static SensorMessage decode(String json) {
        try {
            return MAPPER.readValue(json, SensorMessage.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to decode message: " + json, e);
        }
    }
}
