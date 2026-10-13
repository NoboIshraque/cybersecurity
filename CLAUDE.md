# CLAUDE.md — working guide for the Mirage project

This file is read at the start of each session. Read **`HANDOVER.md`** next for the live
status and the next task.

## What this is

Mirage is a deception-based intrusion-detection and active-defence system, built in Java for
the *Object Oriented Design and Programming-II Laboratory (ICE-2206)* course. Three cooperating apps:

- **Console** (blue team) — accepts Sensor connections, classifies attacker behaviour, keeps
  the hash-chained ledger and the database, drives the Swing dashboard, issues responses.
- **Sensor** — hosts the decoys and bait files on a protected host, reports to the Console,
  executes responses.
- **Specter** (red team) — a built-in attack simulator used to drive and demo the system.

## ⚠️ Hard safety rule (non-negotiable)

Mirage is a **lab-only** tool. Every outbound action — a Specter simulated attack or an
active-defence response — MUST pass through `com.mirage.common.ScopeGuard`, which allows only
addresses inside the configured lab CIDR and denies everything else by default.

- Never remove, weaken, or bypass `ScopeGuard`, and never add an "attack any IP" path.
- Never add real denial-of-service, flooding, or remote-exploit code against external hosts.
- "Turning it around on the attacker" is realised as trapping, slowing (tarpit), misleading
  (fake data), and blocking — all on our own machines, inside the lab range.

This is what keeps the project lawful and submittable. Keep it that way.

## Build, test, run

```bash
mvn test        # compile + run unit tests (keep these green)
mvn compile     # compile only
java -cp target/classes com.mirage.console.ConsoleMain
java -cp target/classes com.mirage.sensor.SensorMain
java -cp target/classes com.mirage.specter.SpecterMain
```

JDK 17+ (built on JDK 21), Maven 3.9+. Language level is `maven.compiler.release=17`.

## Design patterns in use (keep extending these, don't bypass them)

| Pattern | Where |
| --- | --- |
| Template Method | `sensor.Decoy` fixes the loop; subclasses implement `respond()` |
| Strategy | `console.detection.DetectionRule`, `specter.AttackStrategy`, `console.ResponsePolicy` |
| Command | `console.response.ResponseAction` and its implementations |
| Observer | `console.AlertBus` notifies the dashboard |
| Producer–Consumer | network threads enqueue `AttackEvent`s; `CorrelationEngine` is the single consumer |

## How to extend (the common tasks)

- **New decoy** → subclass `sensor.Decoy`, implement `respond()`, register it in `SensorMain`.
- **New detection rule** → implement `console.detection.DetectionRule`, add it to the rules
  list in `ConsoleMain`, and add a unit test.
- **New response** → implement `console.response.ResponseAction`, wire it into `ResponsePolicy`.
- **New attack** → implement `specter.AttackStrategy` (only ever used via `Attacker`, which
  calls `ScopeGuard.require` first).

## Conventions

- One public type per file; records for immutable value types; `final` classes by default.
- Keep `CorrelationEngine` the single mutator of profiles and the ledger (thread-safety by
  confinement). Producers only enqueue.
- Add or update a JUnit test whenever you touch ledger logic, a detection rule, or ScopeGuard.
- Git: `master` is the default branch. Do feature work on a branch and open a **draft PR**;
  keep `master` buildable.

## Pointers

- Live status + next steps: `HANDOVER.md`
- Proposal: `PROPOSAL.md`; full report: `REPORT.md` (formatted master with figures: the Claude artifact link in
  `HANDOVER.md`)
- DB schema: `db/schema.sql`
