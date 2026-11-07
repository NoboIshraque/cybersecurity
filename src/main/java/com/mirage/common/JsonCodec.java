package com.mirage.common;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Encodes/decodes the two wire message types as single-line JSON:
 * {@link SensorMessage} (Sensor to Console) and {@link ResponseCommand}
 * (Console to Sensor).
 */
public final class JsonCodec {

    /** Longest wire line either end will read; a message is far smaller (B-08, A-01). */
    public static final int MAX_LINE_BYTES = 64 * 1024;

    /** Unknown fields are ignored, so jars from different commits can still talk (B-13). */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JsonCodec() {
    }

    public static String encode(SensorMessage message) {
        return write(message);
    }

    public static String encode(ResponseCommand command) {
        return write(command);
    }

    public static SensorMessage decode(String json) {
        return read(json, SensorMessage.class);
    }

    public static ResponseCommand decodeCommand(String json) {
        return read(json, ResponseCommand.class);
    }

    private static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to encode message", e);
        }
    }

    private static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to decode message: " + json, e);
        }
    }
}
