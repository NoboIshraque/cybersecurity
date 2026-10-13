package com.mirage.specter;

import com.mirage.common.ScopeGuard;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.util.List;

/** Entry point for the red-team Specter console. */
public final class SpecterMain {

    public static void main(String[] args) {
        ScopeGuard guard = new ScopeGuard(List.of("10.10.0.0/24", "127.0.0.1/32"));

        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Specter is a GUI and needs a display. None detected, so nothing to show.");
            System.out.println("ScopeGuard active: targets outside the lab range are refused.");
            return;
        }
        SwingUtilities.invokeLater(() -> new SpecterConsole(guard).setVisible(true));
    }
}
