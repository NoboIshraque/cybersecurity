# Stress test — master fix checklist

Branch `feature/live-shells` @ `22f951e`, tested 2026-11-07. Two reviews ran in parallel:

- **A — security and safety** → [`A-security.md`](A-security.md) 
- **B — correctness, concurrency, robustness, performance, docs/build** →
  [`B-robustness.md`](B-robustness.md) (33 items; 32 reproduced or measured).

This file is the work list. Tick an item when its fix is merged **with a test that proves it**.
How to resume, and the rules for each fix: the **RESUME HERE** section of
[`HANDOVER.md`](../../HANDOVER.md).
Each line links to the full entry (where, problem, evidence, suggested fix). Items that overlap
are merged into one line. Fix the waves in order.


## Wave 1 — safety boundary and demo-breakers (fix before the next rehearsal)

- [x] **HIGH** · [B-22](B-robustness.md) — The `mirage.properties` snippet in `docs/TWO-LAPTOP-SETUP.md` uses inline `#` comments, which Java keeps as part of the value, so every role refuses to start. *Move comments to their own lines; reject or trim values with ` #` in `LabConfig`.*
- [x] **HIGH** · [B-01](B-robustness.md) — One bad event (null field, H2 "value too long") kills the correlation-engine thread for good, silently. *Catch per event, log it, keep consuming; validate events at the server.*
- [x] **HIGH** · [B-05](B-robustness.md) + [B-13](B-robustness.md) — The Sensor never reconnects, and one unparsable line or unknown JSON field makes the Console drop it. *Reconnect loop with backoff; skip bad lines instead of closing; ignore unknown JSON fields.*
- [x] **HIGH** · [B-06](B-robustness.md) + [B-07](B-robustness.md) + [A-04](A-security.md) — Decoys have no read timeout, and tarpits hold workers forever: 12 idle or tarpitted connections freeze a decoy for every source. *`setSoTimeout`; cap tarpit time and connections per source; separate tarpit pool.*
- [x] **HIGH** · [B-08](B-robustness.md) + [A-01](A-security.md) — Unbounded `readLine` (decoy headers, Console and Sensor wire) can exhaust memory; the Console starts one unbounded thread per connection. *Bounded line reader, fixed pools, connection caps.*
- [x] **MEDIUM** · [B-24](B-robustness.md) — Flaky test (2/7 runs) that is also a real race: a blocked probe is sometimes reported as "connection failed" instead of "blocked". *Treat write/connect resets as DROPPED; make the test deterministic.*
- [x] **MEDIUM** · [B-10](B-robustness.md) + [B-31](B-robustness.md) — `defender` starts its Sensor against whatever already holds the Console port, and keeps running with a "connected" Sensor that has no decoys when a decoy port is taken. *Fail the whole role if the Console or any decoy can't bind.*
- [x] **MEDIUM** · [B-29](B-robustness.md) — No bait files are planted, so DEMO's "edit a file in `bait/`" step has nothing to edit. *Plant sample bait files at Sensor start (and fix A-05 alongside).*
- [x] **MEDIUM** · [B-21](B-robustness.md) — Recon and mirror scans are hard-coded to ports 8020–8085, ignoring the configured ports; on one laptop Specter's own scan trips its own counter-watch. *Derive scan ports from `LabConfig`; skip the local counter-watch port.*

## Wave 2 — trust, integrity and correctness

- [ ] **HIGH** · [B-02](B-robustness.md) — Detection is O(n²) per source (every event rescans and copies the whole history): a 12k-event flood delayed another source's CRITICAL alert from about 0.12 s to 10 s. *Incremental rule state or a sliding window; cap per-profile history.*
- [ ] **MEDIUM** · [A-02](A-security.md) + [A-03](A-security.md) — TLS is optional, server-only, falls back to plaintext on a keystore error, and defaults to `changeit`; a Sensor obeys any host on the Console address. *TLS on by default in the scripts, fail closed, no default password, pinned lab CA.*
- [ ] **MEDIUM** · [A-06](A-security.md) + [B-30](B-robustness.md) — Ledger signing key regenerated each start and never saved; the persisted chain restarts at genesis; 5 schema tables are never written. *Persist the key, continue the chain from the DB, verify on start-up; trim or populate the unused tables.*
- [ ] **MEDIUM** · [B-15](B-robustness.md) — A Sensor that stops reading blocks `broadcast` forever, stalling the engine, the operator buttons and every other Sensor. *Per-connection send queue with a timeout; drop slow peers.*
- [ ] **MEDIUM** · [B-14](B-robustness.md) — Liveness uses the Sensor's own clock, so a skewed laptop gets false "possible tamper" warnings. *Use the Console's receive time.*
- [ ] **MEDIUM** · [B-19](B-robustness.md) — `ProbeReply` treats any "200"/"230" anywhere in the line as ACCESS GRANTED. *Parse the status code at the start of the line.*
- [ ] **MEDIUM** · [B-20](B-robustness.md) — A strategy's runtime error is swallowed by the pool; the shell hangs with no banner (e.g. port 70000). *Catch per strategy, emit an ERROR banner; validate ports.*
- [ ] **MEDIUM** · [B-09](B-robustness.md) — "Feed fake data" is only a tarpit; no fake-data response exists. *Implement a fake-data reply in the decoys, or rename the action honestly.*
- [ ] **MEDIUM** · [B-03](B-robustness.md) — "Unblock = clean slate" with auto-response on re-blocks on the next packet and re-records all earlier findings. *Reset only the response memory, not the ledger findings; or suppress auto-response for a grace period after an operator unblock.*
- [ ] **MEDIUM** · [B-17](B-robustness.md) — Shells lag without bound under load and keep every line forever. *Cap the shell buffer; drop or summarise when the backlog exceeds N.*
- [ ] **MEDIUM** · [B-28](B-robustness.md) — Rules have no time window, and thresholds sit exactly at what Specter sends. *Sliding windows; thresholds with margin; tests at the boundary.*
- [ ] **MEDIUM** · [B-26](B-robustness.md) — Test gaps around these failure modes. *Add tests with each fix above.*

## Wave 3 — polish and hygiene

- [x] **LOW** · [A-05](A-security.md) — Bait watcher has no integrity baseline and misses changes made while the Sensor was down.
- [ ] **LOW** · [A-07](A-security.md) — H2 DB has no password and sits in the working directory.
- [ ] **LOW** · [A-08](A-security.md) — CounterWatch starts one thread per connection with no cap.
- [ ] **LOW** · [B-04](B-robustness.md) — Two CRITICAL rules fire on one intrusion: two alerts, two BLOCK broadcasts.
- [ ] **LOW** · [B-11](B-robustness.md) — Sensor id = hostname: two Sensors on one host collide; dead-sensor warnings repeat forever.
- [ ] **LOW** · [B-12](B-robustness.md) — A second Console in the same folder dies with a raw H2 stack trace.
- [ ] **LOW** · [B-16](B-robustness.md) — The shell's thread strip never shows "done".
- [ ] **LOW** · [B-18](B-robustness.md) — Attacker dossier is a frozen snapshot; "Port probes" is always 0 (and "Honeytokens used" stays empty).
- [ ] **LOW** · [B-23](B-robustness.md) — Smaller docs-vs-code contradictions.
- [ ] **LOW** · [B-25](B-robustness.md) — `PersistenceDaoTest` isn't isolated (named in-memory H2 keeps rows).
- [ ] **LOW** · [B-32](B-robustness.md) — Mistyped flags are silently ignored; ports aren't range-checked.
- [ ] **LOW** · [B-33](B-robustness.md) — Smaller concurrency/robustness notes from code reading (SUSPECTED).
- [ ] **INFO** · [A-09](A-security.md) — No secrets committed; add a CI guard to keep it that way.
- [ ] **INFO** · [B-27](B-robustness.md) — The shaded jar runs every role correctly (no action needed).

- Not covered by either review: a dependency CVE scan, IPv6, and the real two-laptop network
  (both reviews ran on 127.0.0.1 only).
