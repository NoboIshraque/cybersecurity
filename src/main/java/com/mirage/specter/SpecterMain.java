package com.mirage.specter;

import com.mirage.common.LabConfig;
import com.mirage.common.ScopeGuard;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

/** Entry point for the red-team Specter console. */
public final class SpecterMain {

    public static void main(String[] args) {
        LabConfig config = LabConfig.load(args);
        ScopeGuard guard;
        try {
            guard = config.scopeGuard(); // validates the lab range before anything can be launched
        } catch (IllegalArgumentException e) {
            System.err.println("Refusing to start: " + e.getMessage());
            return;
        }

        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Specter is a GUI and needs a display. None detected, so nothing to show.");
            System.out.println("ScopeGuard active: targets outside the lab range are refused.");
            return;
        }
        SwingUtilities.invokeLater(() -> new SpecterConsole(guard, config).setVisible(true));
    }
}
