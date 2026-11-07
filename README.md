# Mirage

[![CI](https://github.com/NoboIshraque/cybersecurity/actions/workflows/ci.yml/badge.svg)](https://github.com/NoboIshraque/cybersecurity/actions/workflows/ci.yml)

**Deception-based intrusion detection with an integrated attack simulator and scoped active defence.**

Course project for *Object Oriented Design and Programming-II Laboratory (ICE-2206)*.

Mirage watches decoy services and bait files that only an intruder would ever touch, so
every alert is high-confidence. It profiles the attacker, classifies the behaviour against
MITRE ATT&CK, records it in a tamper-evident hash-chained ledger, and responds with scoped
active defence (tarpitting, feeding fake data, network-wide blocking). A built-in red-team
simulator, **Specter**, drives the whole demo — restricted to the lab's own address range.

> ⚠️ **Safety boundary.** Mirage runs only inside an isolated lab of the team's own machines
> or VMs. Every simulated attack and every active-defence response passes through
> `ScopeGuard`, which refuses any target outside the configured lab range (default-deny).
> It never targets external or third-party systems. Do not weaken `ScopeGuard`.

## Team

| Member | Roll | Area |
| --- | --- | --- |
| Nobo Ishraque | 25549011082 | Console server, networking/TLS, correlation + classification, ledger |
| Adil Akhtab Abir | 25549011096 | Sensor, decoy hierarchy, bait-file watcher, response commands, heartbeats |
| Noria Khan Sroti | 25549011020 | Specter simulator, both Swing UIs, database/DAO, report |

## Status

Feature-complete and **all tests green** (98). The live-shells release adds terminal-style
process shells on both sides, an escalating threat popup with operator-driven responses, and
a one-jar launcher for a two-laptop demo. See [`HANDOVER.md`](HANDOVER.md) for where to pick up.

## Architecture (in one line)

Sensors host decoys and report over TLS to the Console, which classifies and responds;
Specter attacks the decoys from inside the lab range. Both sides narrate every step into live
process shells (one pane per thread when work runs concurrently). Full design, with diagrams, is in the
proposal document (link in `HANDOVER.md`).

## Build and run

Maven need not be installed: use the committed wrapper (`.\mvnw.cmd` on Windows, `./mvnw` elsewhere).

```bash
./mvnw test                     # compile + run the unit tests
./mvnw -DskipTests package      # build the runnable jar: target/mirage.jar

run-defender.bat                # Laptop A: Console + Sensor (decoys)     (.sh on macOS/Linux)
run-attacker.bat                # Laptop B: Specter + counter-watch
java -jar target/mirage.jar     # lists the roles: defender | attacker | console | sensor | specter
```

With no `mirage.properties` everything runs on one machine over `127.0.0.1`. For two laptops,
copy `mirage.properties.example` to `mirage.properties` and follow
[`docs/TWO-LAPTOP-SETUP.md`](docs/TWO-LAPTOP-SETUP.md). [`DEMO.md`](DEMO.md) is the
who-clicks-what demo script.

Requires JDK 17+ (built and tested on JDK 21). Laptop B needs only Java, not Maven.

## Project layout

```
src/main/java/com/mirage/
  Launcher    the jar's entry point (defender / attacker / console / sensor / specter)
  common/     domain types, ScopeGuard (the safety boundary), LabConfig
    trace/      TraceEvent, TraceBus, TraceBacklog (what the live shells show)
  console/    AlertLedger, AlertBus, CorrelationEngine, ResponsePolicy, CounterOffensive,
              ThreatTracker, ShellFilters
    detection/  DetectionRule + rules (scan, brute-force, spray, honeytoken, decoy access, ...)
    response/   ResponseAction commands (tarpit, feed-fakes, block)
    ui/         Dashboard, ThreatPopup, OperationShells, network map, detail panel
  sensor/     Decoy (abstract, pooled) + HTTP/FTP/Telnet decoys, bait-file watcher, SensorMain
  specter/    AttackStrategy + attacks, Attacker, CounterWatch, SpecterConsole, SpecterMain
  ui/shell/   ShellModel + ShellWindow (terminal-style process shell, shared by both sides)
src/test/java/com/mirage/   unit tests (98)
tools/screenshots/          GUI dry run + figure harnesses
db/schema.sql               H2 database schema
docs/                       two-laptop setup guide and screenshots
```

## Documentation

- [`CLAUDE.md`](CLAUDE.md) — project guide and conventions (read first each session).
- [`HANDOVER.md`](HANDOVER.md) — live status, phases, decisions log, and next steps.
- [`PROPOSAL.md`](PROPOSAL.md) — the project proposal (submission).
- [`REPORT.md`](REPORT.md) — the detailed project report (report format).
- [`DEMO.md`](DEMO.md) — the two-laptop demo script (who clicks what, what the audience sees).
- [`docs/TWO-LAPTOP-SETUP.md`](docs/TWO-LAPTOP-SETUP.md) — network, config and firewall set-up.
- [`BUILD_LOG.md`](BUILD_LOG.md) — how the live-shells feature was built, phase by phase.

## Academic note

This is an educational prototype for a university lab, not a production IDS. It is designed
to be run only in an isolated lab environment.
