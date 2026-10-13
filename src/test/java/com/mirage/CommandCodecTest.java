package com.mirage;

import com.mirage.common.CommandType;
import com.mirage.common.JsonCodec;
import com.mirage.common.ResponseCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandCodecTest {

    @Test
    void commandRoundTrips() {
        ResponseCommand back = JsonCodec.decodeCommand(
                JsonCodec.encode(new ResponseCommand(CommandType.BLOCK, "10.10.0.5")));
        assertEquals(CommandType.BLOCK, back.type());
        assertEquals("10.10.0.5", back.targetIp());
    }
}
