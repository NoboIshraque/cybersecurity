package com.mirage.console;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Derives a readable dossier for one source from its observed events: the
 * services it probed, the credentials it tried, the honeytokens and bait files it
 * touched, and when it was active. Pure logic with no GUI, so it is unit-tested
 * directly.
 */
public final class AttackerDossier {

    private final String sourceIp;
    private final Set<String> servicesProbed = new LinkedHashSet<>();
    private final List<String> credentialsTried = new ArrayList<>();
    private final List<String> honeytokensUsed = new ArrayList<>();
    private final List<String> baitFilesTouched = new ArrayList<>();
    private int portProbes;
    private int eventCount;
    private Instant firstSeen;
    private Instant lastSeen;

    private AttackerDossier(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public static AttackerDossier from(AttackerSnapshot snapshot) {
        AttackerDossier d = new AttackerDossier(snapshot.sourceIp());
        for (AttackEvent e : snapshot.events()) {
            d.eventCount++;
            if (d.firstSeen == null || e.timestamp().isBefore(d.firstSeen)) {
                d.firstSeen = e.timestamp();
            }
            if (d.lastSeen == null || e.timestamp().isAfter(d.lastSeen)) {
                d.lastSeen = e.timestamp();
            }
            if (e.decoyType() != null) {
                d.servicesProbed.add(e.decoyType());
            }
            String payload = e.payload();
            switch (e.action()) {
                case LOGIN_ATTEMPT -> { if (payload != null) d.credentialsTried.add(payload); }
                case HONEYTOKEN_USE -> { if (payload != null) d.honeytokensUsed.add(payload); }
                case FILE_CHANGE -> { if (payload != null) d.baitFilesTouched.add(payload); }
                case PORT_PROBE -> d.portProbes++;
                default -> { }
            }
        }
        return d;
    }

    /** Distinct passwords from the credentials tried (the part after the last ':'). */
    public List<String> passwordsTried() {
        Set<String> out = new LinkedHashSet<>();
        for (String cred : credentialsTried) {
            int colon = cred.lastIndexOf(':');
            out.add(colon >= 0 ? cred.substring(colon + 1) : cred);
        }
        return List.copyOf(out);
    }

    public String sourceIp() { return sourceIp; }
    public List<String> servicesProbed() { return List.copyOf(servicesProbed); }
    public List<String> credentialsTried() { return List.copyOf(credentialsTried); }
    public List<String> honeytokensUsed() { return List.copyOf(honeytokensUsed); }
    public List<String> baitFilesTouched() { return List.copyOf(baitFilesTouched); }
    public int portProbes() { return portProbes; }
    public int eventCount() { return eventCount; }
    public Instant firstSeen() { return firstSeen; }
    public Instant lastSeen() { return lastSeen; }
}
