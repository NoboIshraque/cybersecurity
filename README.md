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

Proposal complete; foundational code scaffolded and **all tests green**. See
[`HANDOVER.md`](HANDOVER.md) for exactly where to pick up next.

## Architecture (in one line)

Sensors host decoys and report over TLS to the Console, which classifies and responds;
Specter attacks the decoys from inside the lab range. Full design, with diagrams, is in the
proposal document (link in `HANDOVER.md`).

## Build and run

```bash
mvn test                 # compile + run the unit tests
mvn compile              # compile only

# run an entry point (after mvn compile)
java -cp target/classes com.mirage.console.ConsoleMain
java -cp target/classes com.mirage.sensor.SensorMain
java -cp target/classes com.mirage.specter.SpecterMain
```

The Console and Specter open GUI windows (a live dashboard and the red-team console); the Sensor runs headless. See [`DEMO.md`](DEMO.md) for the full walkthrough.

Requires JDK 17+ (built and tested on JDK 21) and Maven 3.9+.

## Project layout

```
src/main/java/com/mirage/
  common/     domain types + ScopeGuard (the safety boundary)
  console/    AlertLedger, AlertBus, CorrelationEngine, ResponsePolicy
    detection/  DetectionRule + rules (brute-force, port-scan, honeytoken)
    response/   ResponseAction commands (tarpit, feed-fakes, block)
  sensor/     Decoy (abstract) + FakeHttpLoginDecoy, SensorMain
  specter/    AttackStrategy + BruteForceAttack, Attacker, SpecterMain
src/test/java/com/mirage/   unit tests (ledger, scope guard, rules)
db/schema.sql               H2 database schema
docs/                       design notes and exported figures (as added)
```

## Documentation

- [`CLAUDE.md`](CLAUDE.md) — project guide and conventions (read first each session).
- [`HANDOVER.md`](HANDOVER.md) — live status, phases, decisions log, and next steps.
- [`PROPOSAL.md`](PROPOSAL.md) — the project proposal (submission).
- [`REPORT.md`](REPORT.md) — the detailed project report (report format).
- [`DEMO.md`](DEMO.md) — the projector demo script (run it end to end).

## Academic note

This is an educational prototype for a university lab, not a production IDS. It is designed
to be run only in an isolated lab environment.
