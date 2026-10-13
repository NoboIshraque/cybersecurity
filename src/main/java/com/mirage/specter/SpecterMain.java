package com.mirage.specter;

import com.mirage.common.ScopeGuard;

import java.util.List;

/** Entry point for the red-team attack simulator. */
public final class SpecterMain {

    public static void main(String[] args) {
        System.out.println("Specter (attack simulator) starting...");

        ScopeGuard guard = new ScopeGuard(List.of("10.10.0.0/24"));
        Attacker attacker = new Attacker(guard);

        // Example run (disabled) — note it only ever targets the lab range:
        // attacker.launch(
        //         List.of(new BruteForceAttack(List.of("admin", "root", "123456"))),
        //         new Target("10.10.0.5", 8081));

        System.out.println("ScopeGuard active: targets outside the lab range are refused.");
        System.out.println("TODO: build the Swing control console (target picker, campaign "
                + "selector, stealth slider, live log). See HANDOVER.md.");

        attacker.shutdown();
    }
}
