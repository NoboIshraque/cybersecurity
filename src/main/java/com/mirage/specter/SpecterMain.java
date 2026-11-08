package com.mirage.specter;

import com.mirage.common.LabConfig;
import com.mirage.common.ScopeGuard;
import com.mirage.common.trace.TraceBus;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.IOException;

/** Entry point for the red-team Specter console. */
public final class SpecterMain {

    public static void main(String[] args) {
        int code = launch(args);
        if (code != 0) {
            System.exit(code); // FB-08: a refused start fails, so scripts and run-attacker.bat can tell
        }
    }

    /** Start Specter; returns the process exit code: 1 when start-up is refused, else 0. */
    public static int launch(String[] args) {
        LabConfig config;
        ScopeGuard guard;
        try {
            config = LabConfig.load(args); // B-32: a mistyped flag or a bad port is refused here
            guard = config.scopeGuard(); // validates the lab range before anything can be launched
        } catch (IllegalArgumentException e) {
            System.err.println("Refusing to start: " + e.getMessage());
            return 1;
        }

        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Specter is a GUI and needs a display. None detected, so nothing to show.");
            System.out.println("ScopeGuard active: targets outside the lab range are refused.");
            return 0;
        }
        SwingUtilities.invokeLater(() -> {
            new SpecterConsole(guard, config).setVisible(true);
            startCounterWatch(config);
        });
        return 0;
    }

    /**
     * A passive listener so this laptop can see when the Console turns a technique back on it.
     * It only accepts and logs; it never reaches out. If the port is taken, Specter still runs.
     * Closing its window only hides it; the next counter brings it back (FB-19).
     */
    private static void startCounterWatch(LabConfig config) {
        TraceBus bus = new TraceBus();
        CounterWatchWindow window = new CounterWatchWindow();
        bus.subscribe(window::onTrace);
        CounterWatch watch = new CounterWatch(config.counterWatchPort(), bus);
        try {
            watch.start();
        } catch (IOException e) {
            System.out.println("counter-watch could not bind :" + config.counterWatchPort()
                    + " (" + e.getMessage() + "); continuing without it.");
            return;
        }
        window.show();
    }
}
