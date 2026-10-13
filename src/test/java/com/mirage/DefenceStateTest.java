package com.mirage;

import com.mirage.common.CommandType;
import com.mirage.common.ResponseCommand;
import com.mirage.sensor.ConnectionGate;
import com.mirage.sensor.DefenceState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefenceStateTest {

    @Test
    void blockThenUnblock() {
        DefenceState defence = new DefenceState();
        assertEquals(ConnectionGate.Decision.ALLOW, defence.decide("1.2.3.4"));

        defence.apply(new ResponseCommand(CommandType.BLOCK, "1.2.3.4"));
        assertEquals(ConnectionGate.Decision.BLOCK, defence.decide("1.2.3.4"));

        defence.apply(new ResponseCommand(CommandType.UNBLOCK, "1.2.3.4"));
        assertEquals(ConnectionGate.Decision.ALLOW, defence.decide("1.2.3.4"));
    }

    @Test
    void tarpitDecision() {
        DefenceState defence = new DefenceState();
        defence.apply(new ResponseCommand(CommandType.TARPIT, "5.6.7.8"));
        assertEquals(ConnectionGate.Decision.TARPIT, defence.decide("5.6.7.8"));
    }
}
