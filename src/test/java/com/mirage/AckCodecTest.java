package com.mirage;

import com.mirage.common.CommandType;
import com.mirage.common.JsonCodec;
import com.mirage.common.MessageType;
import com.mirage.common.ResponseCommand;
import com.mirage.common.SensorMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AckCodecTest {

    @Test
    void ackRoundTripsWithItsNote() {
        SensorMessage ack = SensorMessage.ack("sensor-lab1", new ResponseCommand(CommandType.BLOCK, "10.10.0.5"));
        SensorMessage back = JsonCodec.decode(JsonCodec.encode(ack));
        assertEquals(MessageType.ACK, back.type());
        assertEquals("sensor-lab1", back.sensorId());
        assertEquals("BLOCK 10.10.0.5", back.note());
    }

    @Test
    void eventAndHeartbeatStillRoundTripWithNewField() {
        SensorMessage hb = SensorMessage.heartbeat("sensor-lab1");
        assertEquals(MessageType.HEARTBEAT, JsonCodec.decode(JsonCodec.encode(hb)).type());
    }
}
