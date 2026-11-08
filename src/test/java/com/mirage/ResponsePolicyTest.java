package com.mirage;

import com.mirage.common.Classification;
import com.mirage.common.CommandType;
import com.mirage.common.ResponseCommand;
import com.mirage.common.ScopeGuard;
import com.mirage.common.Severity;
import com.mirage.console.CommandSink;
import com.mirage.console.ResponsePolicy;
import com.mirage.console.response.ResponseAction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResponsePolicyTest {

    @Test
    void criticalBroadcastsABlock() {
        List<ResponseCommand> sent = new ArrayList<>();
        CommandSink sink = sent::add;
        ResponsePolicy policy = new ResponsePolicy(new ScopeGuard(List.of("10.10.0.0/24")), sink);

        policy.actionsFor("10.10.0.5",
                new Classification("Stolen credentials", "T1078", Severity.CRITICAL))
                .forEach(ResponseAction::execute);

        assertEquals(1, sent.size());
        assertEquals(CommandType.BLOCK, sent.get(0).type());
        assertEquals("10.10.0.5", sent.get(0).targetIp());
    }

    /** B-09: tarpit and fake data now exclude each other, so HIGH sends one clear instruction. */
    @Test
    void highFeedsFakeDataOnly() {
        List<ResponseCommand> sent = new ArrayList<>();
        ResponsePolicy policy = new ResponsePolicy(new ScopeGuard(List.of("10.10.0.0/24")), sent::add);

        policy.actionsFor("10.10.0.5",
                new Classification("Credential brute-force", "T1110", Severity.HIGH))
                .forEach(ResponseAction::execute);

        assertEquals(List.of(new ResponseCommand(CommandType.FEED_FAKE, "10.10.0.5")), sent);
    }

    @Test
    void outOfScopeTargetIsRefused() {
        ResponsePolicy policy = new ResponsePolicy(new ScopeGuard(List.of("10.10.0.0/24")), CommandSink.NONE);
        assertThrows(SecurityException.class, () -> policy.actionsFor("8.8.8.8",
                new Classification("x", "y", Severity.LOW)));
    }
}
