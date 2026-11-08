package com.mirage.specter;

import com.mirage.common.AttackEvent;
import com.mirage.common.CommandType;
import com.mirage.common.ResponseCommand;
import com.mirage.common.trace.TraceLevel;
import com.mirage.sensor.Decoy;
import com.mirage.sensor.DefenceState;
import com.mirage.sensor.FakeHttpLoginDecoy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FB-06: DEMO step 4 promises the audience a fake file manager with passwords.csv, vpn.key and
 * config/backup.cfg, but Specter kept only the reply's status line, so no screen ever showed a
 * file name. Now a granted page's file list is shown in the attack shell, ls-style.
 */
class FakeFilesShownTest {

    private static final String TRAP = "svc_backup:Bk-2026!";
    private final List<AttackEvent> events = new CopyOnWriteArrayList<>();
    private Decoy decoy;

    @AfterEach
    void stop() {
        if (decoy != null) {
            decoy.stop();
        }
    }

    private Target start(DefenceState gate) throws Exception {
        decoy = new FakeHttpLoginDecoy(0, Set.of(TRAP));
        decoy.start(events::add, gate);
        return new Target("127.0.0.1", decoy.boundPort());
    }

    private static DefenceState fedFakeData() {
        DefenceState defence = new DefenceState();
        defence.apply(new ResponseCommand(CommandType.FEED_FAKE, "127.0.0.1"));
        return defence;
    }

    private static String shell(List<String> lines) {
        return String.join("\n", lines);
    }

    @Test
    void bruteForceIntoFakeDataListsTheLureFiles() throws Exception {
        Target target = start(fedFakeData());
        List<String> lines = new CopyOnWriteArrayList<>();
        new BruteForceAttack(List.of("123456", "password")).run(target, (level, text) -> lines.add(text));
        String out = shell(lines);
        for (String file : List.of("passwords.csv", "vpn.key", "config/backup.cfg")) {
            assertTrue(out.contains("ls  " + file), file + " is listed:\n" + out);
        }
        assertEquals(1, lines.stream().filter(l -> l.contains("ls  passwords.csv")).count(), "listed once:\n" + out);
    }

    @Test
    void theHoneytokenReplayShowsTheAdminPagesFiles() throws Exception {
        Target target = start(new DefenceState());
        List<String> lines = new CopyOnWriteArrayList<>();
        List<TraceLevel> levels = new CopyOnWriteArrayList<>();
        new HoneytokenReplayAttack(TRAP).run(target, (level, text) -> {
            lines.add(text);
            levels.add(level);
        });
        String out = shell(lines);
        for (String file : List.of("config/backup.cfg", "config/vpn.key", "users.db")) {
            assertTrue(out.contains("ls  " + file), file + " is listed:\n" + out);
        }
        assertEquals(TraceLevel.BANNER, levels.get(levels.size() - 1), "the banner still comes last");
        assertEquals("ACCESS GRANTED", lines.get(lines.size() - 1));
    }

    @Test
    void aRejectedLoginListsNothing() throws Exception {
        Target target = start(new DefenceState());
        List<String> lines = new CopyOnWriteArrayList<>();
        new BruteForceAttack(List.of("wrong-1", "wrong-2")).run(target, (level, text) -> lines.add(text));
        assertTrue(lines.stream().noneMatch(l -> l.contains("ls  ")), shell(lines));
    }
}
