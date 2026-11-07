package com.mirage;

import com.mirage.common.CommandType;
import com.mirage.common.ResponseCommand;
import com.mirage.common.ScopeGuard;
import com.mirage.console.CommandSink;
import com.mirage.console.ResponseController;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResponseControllerTest {

    @Test
    void sendsInRangeCommandsAndRefusesOutOfRange() {
        List<ResponseCommand> sent = new ArrayList<>();
        CommandSink sink = sent::add;
        ScopeGuard guard = new ScopeGuard(List.of("10.10.0.0/24"));
        ResponseController controller = new ResponseController(guard, sink);

        String ok = controller.issue(CommandType.BLOCK, "10.10.0.5");
        assertEquals(1, sent.size());
        assertEquals(new ResponseCommand(CommandType.BLOCK, "10.10.0.5"), sent.get(0));
        assertFalse(ok.contains("REFUSED"));

        String refused = controller.issue(CommandType.BLOCK, "8.8.8.8");
        assertEquals(1, sent.size(), "out-of-range target must not be broadcast");
        assertTrue(refused.contains("REFUSED"));

        assertEquals(2, controller.log().size());
    }
}
