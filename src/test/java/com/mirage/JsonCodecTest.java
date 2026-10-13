package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.JsonCodec;
import com.mirage.common.MessageType;
import com.mirage.common.SensorMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class JsonCodecTest {

    @Test
    void eventRoundTrips() {
        AttackEvent event = new AttackEvent("10.10.0.5", 4444, "http-login",
                Action.LOGIN_ATTEMPT, "admin:123", Instant.parse("2026-10-13T10:15:30Z"));
        SensorMessage back = JsonCodec.decode(JsonCodec.encode(SensorMessage.event(event)));

        assertEquals(MessageType.EVENT, back.type());
        assertEquals(event, back.event());
    }

    @Test
    void heartbeatRoundTrips() {
        SensorMessage back = JsonCodec.decode(JsonCodec.encode(SensorMessage.heartbeat("sensor-a")));

        assertEquals(MessageType.HEARTBEAT, back.type());
        assertEquals("sensor-a", back.sensorId());
        assertNull(back.event());
    }

    @Test
    void encodesToASingleLine() {
        String json = JsonCodec.encode(SensorMessage.heartbeat("s1"));
        assertFalse(json.contains("\n"), "wire format must be one line per message");
    }
}
