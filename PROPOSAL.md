# Mirage — Project Proposal

**A deception-based intrusion detection and active-defence system**

**Course:** Object Oriented Design and Programming-II Laboratory (ICE-2206)  ·  **Submission date:** 14 October 2026

**Team:**
- Nobo Ishraque — Roll 25549011082
- Adil Akhtab Abir — Roll 25549011096
- Noria Khan Sroti — Roll 25549011020

**Instructors:** Jasiya Fairiz Raisa (Lecturer, Dept. of ICT, BUP); Md. Shahariar Sarkar (Assistant Professor, Dept. of CSE, MIST)

> Formatted master (with the architecture figure):
> https://claude.ai/code/artifact/d5969a4d-9277-473a-8c66-4d32e04716df

## 1. Problem Statement

A defender watching a real network faces an impossible ratio: a few malicious actions hidden
among millions of legitimate events. Signature- and anomaly-based intrusion-detection systems
therefore raise large volumes of false alarms, and genuine intrusions are lost in the noise — a
problem known as *alert fatigue*. What a defender lacks is not more data, but a signal with no
ambiguity. Mirage produces that signal by inverting the problem: it plants decoys that no
legitimate user has any reason to touch, so any interaction with one is, by construction,
suspicious. The hard task of finding the attacker becomes the simple task of watching the traps.

## 2. Proposed Solution: Mirage

Mirage is built as three cooperating Java applications:

- **Sensors** run on protected machines and host the decoys (a router-style HTTP login, FTP and
  Telnet) and bait files, capturing every interaction and reporting it to the Console over TLS.
- The **Console** is the brain: it accepts many Sensors, builds a per-source attacker profile,
  classifies behaviour against MITRE ATT&CK, records every alert in a tamper-evident hash-chained
  ledger, and chooses a response.
- **Specter** is an integrated attack simulator that plays the intruder so the team can build,
  test and demonstrate the whole system safely inside its own lab.

The design is two-sided (blue team + red team). When an intruder touches a decoy, Mirage turns
the attacker's own techniques back on them *within the authorised lab range* — slowing the attack
with a tarpit, feeding it fabricated data, and cutting the source off network-wide. (See the
architecture figure in the formatted master, or `README.md`.)

## 3. Objectives

1. Build a deception layer of decoy services, bait files and honeytokens for high-confidence signals.
2. Detect and classify intrusions in real time, mapping each to a MITRE ATT&CK technique and severity.
3. Preserve the evidence in a tamper-evident, hash-chained alert ledger.
4. Respond with scoped active defence (tarpit, fake data, network-wide block).
5. Provide a red-team simulator (Specter), confined by `ScopeGuard` to an authorised lab range.
6. Exercise the syllabus: client–server, TLS, multithreading, Swing, and OOP design patterns.

## 4. Key Features

- Decoy services, bait files and honeytokens for high-confidence detection
- Per-source attacker profiling and MITRE ATT&CK classification
- Tamper-evident, hash-chained alert ledger with a one-click integrity check
- Scoped active defence: tarpit, feed fake data, network-wide block — all gated by `ScopeGuard`
- Specter red-team simulator: port scan, brute-force, password spray, honeytoken replay
- Live Java Swing dashboards for the Console (blue) and Specter (red)
- TLS-secured, multithreaded Sensor-to-Console channel with a producer–consumer pipeline

## 5. Scope and Safety Boundary

**In scope.** The features above, on an isolated lab network of the team's own machines/VMs.

**Safety boundary.** Every simulated attack and every active-defence response passes through
`ScopeGuard`, a default-deny allowlist that permits only addresses inside a declared lab range.
Mirage never targets external systems and performs no real exploitation or denial-of-service of
outside hosts. "Turning it around on the attacker" means trapping, slowing and misleading them on
our own machines — the lawful, standard form of active defence.

**Out of scope.** Production deployment, WAN packet capture, kernel-level monitoring.

## 6. Technology and Object-Oriented Design

Java 17, Maven, JUnit 5, Git/GitHub; TCP sockets + TLS, `java.util.concurrent`, Java Swing,
embedded H2 via JDBC, SHA-256 for the ledger.

| Course topic | Where it lives in Mirage |
| --- | --- |
| Client–server + TLS | Sensor–Console channel (`SSLSocket` + thread-pool server) |
| Multithreading | Decoy pools, producer–consumer pipeline, heartbeat sweeper, concurrent attackers |
| Java Swing | Console dashboard and Specter console |
| Inheritance / polymorphism | `Decoy` hierarchy (Template Method) |
| Strategy pattern | `DetectionRule`, `AttackStrategy`, `ResponsePolicy` |
| Command pattern | `ResponseAction` (tarpit / feed-fakes / block) |
| Observer pattern | `AlertBus` to the dashboard |
| Data integrity | SHA-256 hash-chained `AlertLedger` |

## 7. Methodology and Development Phases

Iterative: one subsystem at a time, each on a Git branch, integrated weekly, with JUnit tests for
core logic. Phase 0 is complete (foundation + passing tests on GitHub); Phase 1 is in progress.

| Phase | Focus | Status |
| --- | --- | --- |
| 0 | Foundation: domain model, ScopeGuard, ledger, rules, responses, engine, tests | Done |
| 1 | Networking & liveness: TLS Console server + Sensor client, JSON protocol, heartbeats | In progress |
| 2 | Decoys & capture: FTP/Telnet decoys, login parsing, bait-file watcher, honeytokens | Planned |
| 3 | Detection & persistence: more rules, cross-host correlation, H2 DAO | Planned |
| 4 | Active defence: response dispatch, tarpit, feed-fakes, block list | Planned |
| 5 | Specter red team: scan/spray/replay strategies, campaigns, Specter UI | Planned |
| 6 | Console dashboard (Swing): network map, alert feed, dossier, ledger view | Planned |
| 7 | Integration, testing & demo | Planned |

Roughly one iteration (~a week) per phase, with Phase 7 reserved for integration and the demo.

## 8. Expected Outcomes and Deliverables

- A running system (Console + Sensors + Specter) demonstrable on the team's lab machines.
- A live demo: a scan/brute-force is detected and classified, tarpitted, a honeytoken triggers a
  critical alert, the source is blocked network-wide, and the ledger is shown intact.
- The source code (Maven project on GitHub), this proposal, and the final report.

## References

1. L. Spitzner, *Honeypots: Tracking Hackers*. Addison-Wesley, 2002.
2. The MITRE Corporation, "MITRE ATT&CK." https://attack.mitre.org
3. B. Goetz et al., *Java Concurrency in Practice*. Addison-Wesley, 2006.
4. E. Gamma et al., *Design Patterns*. Addison-Wesley, 1994.
