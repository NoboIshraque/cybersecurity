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
  (fake data), blocking, and — for a source inside the lab range — mirroring the attacker's own
  technique back at them via Specter (`console.CounterOffensive` + `MirrorCounterStrategy`,
  operator-triggered only): one method, or several at once on separate threads
  (`Attacker.launchConcurrent`, a bounded set of techniques — never an unbounded flood/DoS).
  Every one of these, the mirror and the concurrent counter included, goes through `ScopeGuard`,
  so none can reach a host outside the lab range. The counter is a controlled in-range
  demonstration, never a hack-back against a real third party.

- The lab range is configurable (`LabConfig`: `--lab-cidr` / `mirage.properties`) so two laptops
  on a private LAN work, and `ScopeGuard.requireLabRange` refuses to start on anything that is not
  **private or loopback and `/16` or narrower**. Never loosen that check.
- The decoy's "admin page" is canned text; the attacker-side `CounterWatch` only accepts and
  logs (it never writes back). Keep both that way.

This is what keeps the project lawful and submittable. Keep it that way.

## Build, test, run

**Maven is not installed on the demo laptop — use the committed Maven Wrapper** (`./mvnw`, or
`mvnw.cmd` on Windows). The first run downloads Maven 3.9.9 into `~/.m2/wrapper`.

```bash
./mvnw test     # compile + run unit tests (keep these green)   — Windows: .\mvnw.cmd test
./mvnw compile  # compile only
./mvnw -DskipTests package         # runnable fat jar: target/mirage.jar
java -jar target/mirage.jar defender   # Laptop A: Console + Sensor   (run-defender.bat/.sh)
java -jar target/mirage.jar attacker   # Laptop B: Specter + counter-watch (run-attacker.bat/.sh)
java -cp target/classes com.mirage.console.ConsoleMain   # or each app on its own
java -cp target/classes com.mirage.sensor.SensorMain
java -cp target/classes com.mirage.specter.SpecterMain
```

Entry points accept `--console-host`, `--lab-cidr`, `--console-port`, `--http-port`, … (see
`com.mirage.common.LabConfig`) or a `mirage.properties` in the working dir; defaults keep the
single-machine demo on `127.0.0.1` (copy `mirage.properties.example` for two laptops; a local
`mirage.properties` is git-ignored). If `mvn` is on PATH it also works. Two-laptop set-up:
`docs/TWO-LAPTOP-SETUP.md`; demo script: `DEMO.md`.

**GUI dry run:** `tools/screenshots/LiveDemoShot.java` runs the real Console + Sensor + Specter in
one JVM, clicks through the whole demo, prints PASS/FAIL (27 checks) and regenerates
`docs/screenshots/live-*.png`. Run it after any change to the shells, popups or responses (how-to
in `tools/screenshots/README.md`).

JDK 17+ (built on JDK 21), Maven 3.9+. Language level is `maven.compiler.release=17`.

> **Live shells & two-laptop demo** were built on branch `feature/live-shells` (draft PR to
> `master`); the phase-by-phase log is `BUILD_LOG.md`.

## Design patterns in use (keep extending these, don't bypass them)

| Pattern | Where |
| --- | --- |
| Template Method | `sensor.Decoy` fixes the loop; subclasses implement `respond()` |
| Strategy | `console.detection.DetectionRule`, `specter.AttackStrategy`, `console.ResponsePolicy` |
| Command | `console.response.ResponseAction` and its implementations |
| Observer | `console.AlertBus` notifies the dashboard; `common.trace.TraceBus` carries trace lines to the shells (`TraceBacklog` replays what a late shell missed) |
| Producer–Consumer | network threads enqueue `AttackEvent`s; `CorrelationEngine` is the single consumer; `ui.shell.ShellModel` is a paced queue (worker threads produce, the Swing timer consumes) |

## How to extend (the common tasks)

- **New decoy** → subclass `sensor.Decoy`, implement `respond()`, register it in `SensorMain`.
- **New detection rule** → implement `console.detection.DetectionRule`, add it to the rules
  list in `ConsoleMain`, and add a unit test.
- **New response** → implement `console.response.ResponseAction`, wire it into `ResponsePolicy`.
- **New attack** → implement `specter.AttackStrategy` (only ever used via `Attacker`, which
  calls `ScopeGuard.require` first). Report every step through the `AttackTrace` and read the
  reply with `ProbeReply` so the shell shows an honest outcome (ACCEPTED / REJECTED / DROPPED =
  blocked reset / NO_REPLY / STALLED = tarpit).
- **Show something in a shell** → emit a `TraceEvent` on the `TraceSink` you were given
  (channels: `console` pipeline, `op` operator actions, `attack` Specter, `watch` counter-watch;
  `subject` = the source IP). Route it with `console.ShellFilters`; never do work in a listener.
- **New operator action** → add an `console.ui.Operation`, handle it in `Dashboard.perform`; the
  `OperationShells` runner opens its shell and runs it on a named `op-…` thread.

## Conventions

- One public type per file; records for immutable value types; `final` classes by default.
- Keep `CorrelationEngine` the single mutator of profiles, the ledger and its per-source alert
  memory (thread-safety by confinement). Producers only enqueue (`resetAlerts` queues a request).
  Each rule alerts once per source and again only when its severity rises or the source is
  unblocked.
- Auto-response (`console.AutoResponse`) is **off** when the dashboard is up (the operator
  responds) and **on** when headless.
- Add or update a JUnit test whenever you touch ledger logic, a detection rule, or ScopeGuard.
- Git: `master` is the default branch. Do feature work on a branch and open a **draft PR**;
  keep `master` buildable.

## Pointers

- Live status + next steps: `HANDOVER.md`
- Proposal: `PROPOSAL.md`; full report: `REPORT.md` (formatted master with figures: the Claude artifact link in
  `HANDOVER.md`)
- DB schema: `db/schema.sql`
