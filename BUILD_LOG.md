# BUILD LOG — Live shells & two-laptop demo

_Created: 2026-11-07 · Status: **PLANNED — waiting for "go"**_

This is the map for the next piece of work and its running log. Each phase gets ticked off,
and the **Progress log** at the bottom gets a line as each one lands.

## Goal

Make the demo **visible**. Today Specter and the Console only print plain log lines. After this
work:

- **Specter (attacker laptop):** clicking a campaign opens a terminal-style popup that streams each
  step as it happens (connect, decoy reply, result) and ends with a banner (`SCAN COMPLETE`,
  `ACCESS GRANTED`, `ACCESS REVOKED`, `CONNECTION STALLED`).
- **Console (defender laptop):** at the same time, a threat popup appears and escalates
  (`PROBE DETECTED` → `UNDER ATTACK` → `COMPROMISED` → `NEUTRALISED`), next to a live shell showing the
  Console's own pipeline: receive thread → correlation engine → rules → ledger → response.
- **Operator actions** (Tarpit, Block, Counter, Simultaneous counter) each open a shell showing the
  threads doing the work in real time. Multiple threads at once get **split panes**.
- **Two laptops:** defender and attacker run on separate machines on the same network.

The shells **show** what the existing code does. They add no new attack techniques, and every
outbound action still goes through `ScopeGuard`.

## Decisions (agreed 2026-11-07)

| # | Question | Decision |
| --- | --- | --- |
| 1 | How does Specter "get in"? | **Decoy deception.** The HTTP-login decoy accepts the planted honeytoken (and one deliberately weak decoy password) and returns a canned fake admin page. There's no real system behind it: every reply is fixed text served by the decoy. Specter's `ACCESS GRANTED` comes from that reply; the Console shows `COMPROMISED` and knows it's a trap. Blocking then cuts Specter off live. |
| 2 | Automatic or manual response? | **Auto-response toggle, manual by default.** The popup waits for the operator to click. Switching it on restores today's instant responses. |
| 3 | Should Specter notice a counter? | **Yes, passively.** A small listener on the attacker laptop logs any incoming connection and pops an `INCOMING` warning shell. The counter feature itself is unchanged: operator-triggered, bounded, `ScopeGuard`-gated. |
| 4 | How to show threads? | **Both.** One stream with each line tagged and coloured by thread, plus a live thread strip. Concurrent runs also split into one pane per thread. |

Defaults I picked (change any before "go"):

- **Roles:** Laptop A (defender) runs **Console + Sensor** (the decoys live on the protected host).
  Laptop B (attacker) runs **Specter + counter-watch**.
- **Git:** one branch `feature/live-shells`, one commit per phase, one **draft PR** to `master`.
- **Pacing:** shells show real events in order. A speed slider (default "presenter", about 40 ms per line) keeps
  a sub-second LAN scan readable. Pacing only delays the *display*, never the work.

## Safety (unchanged rule, plus a tighter check)

- `ScopeGuard` keeps its default-deny behaviour; nothing bypasses it.
- The lab range becomes **configurable** so two laptops on a home or hotspot network work (e.g.
  `192.168.0.0/24`). New check: a configured range must be **private or loopback** (10/8,
  172.16/12, 192.168/16, 127/8) with prefix **≥ /16**. Anything broader or public refuses to start.
  This gets unit tests.
- The fake admin page is static decoy text. Nothing real is exposed.
- The counter-watch listener only accepts and logs. It never sends anything outward.
- I will **not** change Windows Firewall or other system settings. Phase 7 gives you the exact
  commands to run yourself (as admin) on each laptop.

## Architecture map

```
 LAPTOP B — attacker                         LAPTOP A — defender (protected host)
┌──────────────────────────────┐            ┌──────────────────────────────────────────────┐
│ Specter                      │            │ Sensor                       Console         │
│  SpecterConsole ─Launch─┐    │  lab LAN   │  Decoys (pool/conn) ──EVENT──▶ ConsoleServer │
│  Attacker (ScopeGuard) ─┼────┼──────────▶ │   http-login :8081    ◀─CMD──   rx threads   │
│   └ strategies ─AttackTrace  │            │   ftp :8021, telnet :8023 ─ACK─▶   │         │
│        ▼                     │            │  DefenceState (block/tarpit)       ▼         │
│  [ShellWindow: campaign]     │            │                    CorrelationEngine (1 thr) │
│                              │            │                     rules → ledger → policy  │
│  CounterWatch :8080 ◀────────┼────────────┼── CounterOffensive (operator, ScopeGuard)    │
│   └ [ShellWindow: INCOMING]  │            │        all of it ──TraceBus──▶ [ShellWindows]│
└──────────────────────────────┘            │  Dashboard + ThreatPopup + AutoResponse      │
                                            └──────────────────────────────────────────────┘
```

New patterns used (and existing ones extended):

- **Observer:** `TraceBus`, the same idea as `AlertBus`, carrying pipeline trace lines to the shells.
- **Strategy:** strategies report through an `AttackTrace` callback; a no-op default keeps every caller unchanged.
- **Template Method:** `Decoy` keeps its fixed loop but serves each connection on a bounded pool.
- **Producer–Consumer:** a paced `ShellModel` queue (worker threads produce, the Swing timer consumes).

## Phases

### Phase 0 — Branch & toolchain ✅
- [x] Create branch `feature/live-shells`; commit this file.
- [x] Added the **Maven Wrapper** 3.3.2 (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/{maven-wrapper.jar,
      maven-wrapper.properties}`) → Maven 3.9.9. Official files from Apache's repo; jar committed so a
      checkout never has to fetch it.
- [x] `mvnw test`: all **48 tests green** via the wrapper (~18 s).

### Phase 1 — Configurable lab range & two-laptop networking ✅
- [x] `common.LabConfig`: reads `mirage.properties` and CLI flags (`--lab-cidr`, `--console-host`,
      `--console-port`, decoy ports, `--counterwatch-port`). Defaults reproduce the single-machine setup.
- [x] Range validation (private/loopback only, prefix ≥ /16) in `ScopeGuard.requireLabRange`; entry
      points refuse to start on a bad range.
- [x] Console prints its LAN address at start-up ("point the attacker laptop at 192.168.0.107:8443").
      _(Status-bar display folded into the Phase 6 dashboard rework.)_
- [x] Sensor and Specter read console host / target host / ports from the config.
- [x] Tests: `LabConfigTest` (5) + `ScopeGuardTest` lab-range cases (4). **57 tests green.**

### Phase 2 — Trace plumbing (no UI yet) ✅
- [x] `common.trace`: `TraceEvent` (time, channel, thread, stage, subject, level, text), `TraceLevel`,
      `TraceSink` (no-op `NONE`), `TraceBus` (Observer, isolates a broken listener).
- [x] Console emits traces from `ConsoleServer` (rx thread named per peer, event/heartbeat/ACK/dispatch),
      `CorrelationEngine` (dequeue, each rule miss/match, ledger index+hash+signature, response),
      `ResponseController` and `CounterOffensive` (operator ops on the `op` channel). Wired through
      `ConsoleMain` to a `TraceBus` (no subscriber yet → silent until Phase 6).
- [x] Wire protocol: Sensor sends **`ACK`** after applying a command (`MessageType.ACK`, `note` field);
      Console traces "sensor-X confirms: BLOCK … applied".
- [x] Specter: `AttackTrace` callback. `AttackStrategy.run(Target, AttackTrace)`; each strategy reports
      every step and reads the reply via `ProbeReply` (ACCEPTED / REJECTED / DROPPED=block /
      STALLED=tarpit), so outcomes are honest.
- [x] Tests: `TraceBusTest` (3), `AckCodecTest` (2), `StrategyOutcomeTest` (allow/block/tarpit). **65 green.**

### Phase 3 — Shell window component ✅
- [x] `ui.shell.ShellModel` (no GUI, testable): paced FIFO (`poll(max)`), stable thread→colour map
      (cycles a 6-colour palette), first-seen lane order, running/waiting/done state, banner flag.
- [x] `ui.shell.ShellWindow` (Swing): dark terminal `JTextPane`, monospace, thread-tagged colour
      lines, `$`-prompt command lines, live thread strip, big banners, **split-threads** view (one
      pane per concurrent thread), speed slider, Save log / Clear.
- [x] Shared by both apps in neutral `com.mirage.ui.shell`; `ShellTheme.console()` (blue/green) and
      `ShellTheme.specter()` (red/amber).
- [x] Tests: `ShellModelTest` (7) — order, batch pacing, colour stability+cycling, lanes, done-state,
      concurrency, banners. **72 tests green.**

### Phase 4 — Decoy deception (the fake "inside") ✅
- [x] `Decoy`: each connection served on a bounded 12-thread pool (template loop unchanged); a
      tarpitted client no longer stalls the others; `stop()` shuts the pool and closes the socket.
- [x] `FakeHttpLoginDecoy`: trap credentials (default = the honeytoken) → `200 OK` + canned fake
      admin page; everything else → `401`. Blocked/tarpit behaviour unchanged.
- [x] `Action.DECOY_ACCESS` + `DecoyAccessRule` → CRITICAL "Intruder inside decoy (honeypot
      session)", registered in `ConsoleMain`. Nothing real exposed — the page is fixed text.
- [x] Tests: `DecoyAccessRuleTest` (2), `DecoyDeceptionTest` (grant/deny/parallel, 3); existing
      `FtpDecoyTest`/`BlockedDecoyTest` green on the pooled decoy. **77 tests green.**

### Phase 5 — Specter side ✅
- [x] Launch opens its own `ShellWindow` popup (own bus + `Attacker`, no cross-talk), fed by `AttackTrace`.
- [x] Banners from real outcomes: `SCAN COMPLETE — n OPEN`, `ACCESS GRANTED`, `CONNECTION BLOCKED`
      (dropped), `CONNECTION STALLED` (tarpit), `NO ACCESS`, `REFUSED — OUTSIDE LAB RANGE`.
- [x] "All at once" checkbox → `launchConcurrent` + the shell opens in split-threads view.
- [x] `specter.CounterWatch`: passive listener (default :8080) → `INCOMING` banner + per-connection
      lines (peer, port, thread). Accept-and-log only, never writes back. Shown in its own window.
- [x] Test: `CounterWatchTest`. **78 tests green.**

### Phase 6 — Console side
- [ ] `ThreatPopup`: one per attacker source, escalating banner (`PROBE DETECTED` → `UNDER ATTACK` →
      `COMPROMISED` → `NEUTRALISED` after a block/ACK), with a live pipeline shell beneath.
- [ ] Operator buttons (Tarpit / Feed fake / Block / Counter / Simultaneous counter) open an
      operation shell: ScopeGuard check → dispatch thread → Sensor ACK. The simultaneous counter
      splits into four panes, one per pool thread.
- [ ] **Auto-response toggle** on the dashboard (default **off** = manual). `CorrelationEngine` still
      records every alert; only the automatic `ResponsePolicy` dispatch is gated.
- [ ] Tests: `AutoResponseSwitchTest`, and the engine still raises alerts while auto is off.

### Phase 7 — Packaging for two laptops
- [ ] Runnable fat jar via `maven-shade-plugin` → `target/mirage.jar`.
- [ ] `com.mirage.Launcher`: `defender` (Console + Sensor) | `attacker` (Specter + CounterWatch) |
      `console` | `sensor` | `specter`.
- [ ] Scripts: `run-defender.bat/.sh`, `run-attacker.bat/.sh`, and a sample `mirage.properties`.
- [ ] `docs/TWO-LAPTOP-SETUP.md`: checklist (same network, Java 17+ on Laptop B, copy the jar,
      find IPs, set the lab range) plus the **firewall commands for you to run** on each laptop.

### Phase 8 — Verification
- [ ] `mvnw verify` green (old 48 + new tests).
- [ ] Localhost dry run on this laptop: both roles, every campaign, every operator action,
      auto on/off, counter → counter-watch popup.
- [ ] Fresh screenshots of the shells and popups into `docs/screenshots/`.
- [ ] (Your part) the real two-laptop rehearsal using the setup doc.

### Phase 9 — Docs & PR
- [ ] `DEMO.md`: new two-laptop script ("who clicks what, what the audience sees").
- [ ] `HANDOVER.md` (status, decisions, session log), `README.md`, `CLAUDE.md` (TraceBus/shell
      pattern, LabConfig, the new safety check).
- [ ] `REPORT.md`: new §5.5 "Live process shells & two-host demo" with figures; republish the
      report artifact.
- [ ] Push the branch and open the **draft PR**.

## What you'll need on demo day

- Both laptops on the same network (home Wi-Fi, phone hotspot, or a cable). Note: some university
  Wi-Fi blocks laptop-to-laptop traffic, so a phone hotspot is the safe fallback.
- Java 17+ on Laptop B (only Java: the fat jar needs no Maven there).
- Firewall rules allowing the decoy/console ports in on Laptop A, and the counter-watch port in on
  Laptop B (commands in the setup doc).
- Fallback: everything still runs on one laptop over `127.0.0.1`.

## Progress log

| Date | Phase | Note |
| --- | --- | --- |
| 2026-11-07 | Plan | Map written, decisions agreed. Waiting for "go". |
| 2026-11-07 | 0 | Branch `feature/live-shells`; Maven Wrapper 3.3.2 → Maven 3.9.9; 48 tests green. |
| 2026-11-07 | 1 | `LabConfig` + `ScopeGuard.requireLabRange` (private/loopback, ≥/16); entry points wired. 57 tests green. |
| 2026-11-07 | 2 | `common.trace` (TraceBus) + ACK on the wire; Console & Specter emit honest step traces. 65 tests green. |
| 2026-11-07 | 3 | `ui.shell` ShellModel + ShellWindow (themes, strip, banners, split view, speed). 72 tests green. |
| 2026-11-07 | 4 | Pooled decoys; trap-credential grant → DECOY_ACCESS + DecoyAccessRule (CRITICAL). 77 tests green. |
| 2026-11-07 | 5 | Specter per-launch shells + banners + split concurrent view; CounterWatch listener. 78 tests green. |
