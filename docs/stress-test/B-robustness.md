# Stress test B — correctness, concurrency, robustness, performance, docs/build

Branch under test: `feature/live-shells` @ `22f951e`. Tester: agent B (2026-11-07).

## Scope / method

Angle: correctness, concurrency, robustness, performance, tests, build/packaging and docs.
Security/safety (ScopeGuard, TLS, signing) is covered by a separate checklist (A).

Method: read the source under `src/main/java`, then reproduce suspicions with small headless
harnesses compiled against the already-built `target/classes` (nothing rebuilt in the repo).
Apps were run headless (`-Djava.awt.headless=true`) only on 127.0.0.1 and only on ports
29443 / 29081 / 29021 / 29023 / 29080, from a private scratch working directory. Load was kept
modest (at most a few thousand events, seconds long). **VERIFIED** = reproduced or measured;
**SUSPECTED** = from reading the code.

## Findings

### Console: correlation engine

- [ ] **B-01 · HIGH · VERIFIED** — One bad event kills the correlation engine for good, silently
  - Where: `console/CorrelationEngine.java:84-92` (`run()` catches only `InterruptedException`), `console/db/EventDao.java:279`, `console/detection/PasswordSprayRule.java:260`, `CorrelationEngine.java:97`
  - Problem: any `RuntimeException` thrown while handling one event ends the only consumer thread. After that the Console keeps accepting Sensor links and queueing events, so it looks healthy, but nothing is classified, alerted, recorded in the ledger or answered again until restart. Triggers include an H2 error (column too long, disk full, file problem), a LOGIN_ATTEMPT with no payload, or an event with no source address.
  - Evidence: harness `EngineHarness die|dbdie` (runs the real engine thread with the real 7 rules). (a) LOGIN_ATTEMPT with `payload=null` gave an NPE in `PasswordSprayRule`; the thread died, and 11 later valid events (brute force + DECOY_ACCESS) stayed in the queue: `queue backlog 11, ledger alerts=0, commands sent=[]`. (b) `sourceIp=null` gave an NPE in `ConcurrentHashMap.computeIfAbsent` with the same result. (c) With `H2Persistence`, a 60-character source gave `Value too long for column SRC_IP VARCHAR(45)`, and the engine was dead (`alive=false`). The only trace is a stack trace on stderr from a daemon thread. (d) **Live, over the wire:** one line `{"type":"EVENT","event":{"sourceIp":"10.10.0.77","decoyType":"ftp","payload":"x","timestamp":"…"}}` (no `action`) sent to the running headless Console made `EventDao.save` throw `NullPointerException … Action.name()`. `jstack` then showed no `correlation-engine` thread, while the Console process and its rx/accept threads kept running.
  - Fix: wrap the body of `process(...)` in `try { … } catch (RuntimeException e) { log; trace BAD line; continue; }` so one event can fail but the thread keeps running. Separately, validate incoming events in `ConsoleServer.route` (non-null source/action/timestamp, length caps) and make `PasswordSprayRule` handle a null payload.

- [ ] **B-02 · HIGH · VERIFIED** — Detection cost is O(n²) per source: one noisy source throttles the whole Console
  - Where: `CorrelationEngine.java:97-123` (7 rules × full `profile.events()` scan per event), `console/AttackerDirectory.java:68` (`List.copyOf` of the whole profile per event), `common/AttackerProfile.java:13`
  - Problem: every event re-scans and re-copies the source's whole lifetime event list. As the event count grows, per-event cost rises linearly, so total cost is quadratic. The engine is the only consumer and the queue is unbounded (`LinkedBlockingQueue` in `ConsoleMain:73`), so a source that sends faster than the shrinking rate builds an unbounded backlog. Alerts and responses for **every** source lag behind it, and memory grows.
  - Evidence: `EngineHarness scale` (Persistence.NONE, no trace, one source sending LOGIN_ATTEMPTs): marginal cost per event was 0.34 ms at n=1k, 1.37 ms at 5k, 2.99 ms at 10k and **7.37 ms at 20k** (≈136 events/s). Total for 20k events: **64.4 s**. By contrast, 20k events spread over 200 sources took 181 ms. **End-to-end on the real headless Console from the jar (H2 on):** after one fake Sensor sent 12 000 LOGIN_ATTEMPTs from 10.10.0.50 (8 000 + 4 000), a single DECOY_ACCESS from a *different* source (10.10.0.61) took **10.25 s** to raise its CRITICAL alert. The idle baseline was 124 ms.
  - Fix: keep incremental per-source counters and sets in the profile (login count, distinct decoy types, users-by-password, sensors, flags for DECOY_ACCESS/FILE_CHANGE/honeytoken), updated once per event, and let the rules read them. Publish snapshots lazily (on dashboard request) or capped (last N events). Bound the queue and/or the per-profile history.

- [ ] **B-03 · MEDIUM · VERIFIED** — "Unblock = clean slate" does not hold: with auto-response on, the source is re-blocked on its next packet
  - Where: `CorrelationEngine.java:138-152` (`resetAlerts` clears only the `alerted` memory, not the profile), `console/ConsoleMain.java:110`, rules are whole-history (see B-02)
  - Problem: after the operator unblocks a source, its profile still holds the old DECOY_ACCESS / honeytoken login. The next event of any kind, even one plain CONNECT (or a queued "blocked" CONNECT from before the unblock), makes those CRITICAL rules match "for the first time" again, and auto-response BLOCKs it again at once. So UNBLOCK can't be shown with auto-response on, and the javadoc/`ConsoleMain` comment ("starts with a clean slate") is wrong.
  - Evidence: `EngineHarness unblock`: an intrusion produced `[BLOCK, BLOCK]`. After `resetAlerts` and a single plain `CONNECT`, the engine again sent `[BLOCK 10.10.0.5, BLOCK 10.10.0.5]` and two new CRITICAL ledger entries. `Step6Harness` replays DEMO.md steps 1–3 and then step 6 (Unblock, tick Auto-response, replay). The replay's **first CONNECT** re-records all 5 earlier findings as 5 new ledger entries and sends 7 commands `[TARPIT, TARPIT, FEED_FAKE, TARPIT, FEED_FAKE, BLOCK, BLOCK]`. That contradicts DEMO.md's "one ledger entry per finding" claim and the step-6 narration.
  - Fix: on reset, start a new "epoch" for the source: drop or mark old events so rules only see activity after the unblock (or add time-windowed rules, B-05). At least document that auto-response re-blocks.

- [ ] **B-04 · LOW · VERIFIED** — Two CRITICAL rules fire on the same intrusion, giving two alerts and two BLOCK broadcasts
  - Where: `console/detection/HoneytokenUseRule.java`, `console/detection/DecoyAccessRule.java`, `ResponsePolicy.java:32`
  - Problem: one honeytoken login on the HTTP decoy emits LOGIN_ATTEMPT(trap cred) + DECOY_ACCESS. Both rules match, so the ledger gets two CRITICAL entries and the Sensors get two identical BLOCK commands, then two ACKs. The ledger and feed show a doubled incident.
  - Evidence: `EngineHarness unblock` first phase: `after intrusion: [BLOCK 10.10.0.5, BLOCK 10.10.0.5]`.
  - Fix: de-duplicate responses per (source, command) within one `process()` call, or merge the two classifications (same T1078) into one alert.

### Sensor: decoys, link, defence

- [ ] **B-05 · HIGH · VERIFIED** — The Sensor never reconnects: a Console restart (or a Console started second) leaves the Sensor deaf for good
  - Where: `sensor/SensorMain.java:30-49, 51-61, 75-84`, `sensor/SensorClient.java:134-139, 170-174`
  - Problem: the link is opened once at start-up. If the Console is not up yet, the Sensor logs "Running decoys locally without reporting", never retries, and never sends heartbeats. If the Console restarts, or the link drops (or the Console drops it after one malformed line, `ConsoleServer.java:327-329`), every later `sendEvent` fails with an `IOException` that is swallowed. The decoys keep running, but no event reaches the Console and nothing is logged. In the two-laptop demo, a Console restart mid-demo silently breaks detection. The Sensor's old BLOCK/TARPIT state also outlives the Console that ordered it, and the new Console can't know about it.
  - Evidence: ran `mirage.jar console` + `mirage.jar sensor` on 29443/29081/29021/29023. Killed the Console (PID 15612) and started a new one (PID 12248). Then touched all three decoys (each logged `[decoy] … CONNECT` on the Sensor), but the new Console logged nothing in 15 s; no port-scan alert. The Sensor printed nothing about the lost link.
  - Fix: wrap `SensorClient` in a reconnecting reporter: a background loop with backoff (1 s → 10 s) that reconnects and re-registers (heartbeat first). Buffer a bounded number of events while down, and log link up/down. Start the heartbeat scheduler on every connection, not only when the first connect succeeds.

- [ ] **B-06 · HIGH · VERIFIED** — Decoys have no read timeout: 12 idle connections freeze a decoy for everyone (slowloris)
  - Where: `sensor/Decoy.java:216, 222, 259, 267-291` (fixed pool of 12, no `setSoTimeout`), `FakeHttpLoginDecoy.java:46`, `FakeFtpDecoy.java:121`, `FakeTelnetDecoy.java:174-176`
  - Problem: each accepted socket blocks in `readLine()` with no timeout. Twelve connections that send nothing take all 12 workers, so every later connection queues forever in the executor's unbounded queue. Those connections produce no events, so the attack also goes unseen. Any port scanner that leaves sockets half-open, or one script, can blind each decoy independently.
  - Evidence: `NetTool slowloris`: with 12 idle sockets on the HTTP decoy, a real Basic-auth login got **no reply in 4 s** (it was served 3 ms after the idle sockets closed). The Telnet decoy behaved the same. With 11 idle sockets the login got a `401` in 5 ms, which shows the 12-worker cliff exactly.
  - Fix: `s.setSoTimeout(10_000)` in `Decoy.handle` before `interact` (the catch already treats `IOException` as "done"). Also cap connections per source, and use a bounded queue with reject-and-log when full.

- [ ] **B-07 · MEDIUM · VERIFIED** — A tarpitted source starves every other source's access to that decoy
  - Where: `sensor/Decoy.java:283-306` (tarpit runs on the same 12-worker pool, sleeps 5 s per drip, exits only when a write fails)
  - Problem: the tarpit is meant to slow the attacker, but each tarpitted connection holds one shared worker until the attacker closes it (plus up to one or two 5 s drips to notice). A tarpitted attacker with 12 open connections takes the whole pool, so other sources, including a second attacker you would want to detect, get no service and raise no events. "Slowing the attacker" turns into "the attacker blinds the sensor".
  - Evidence: `tools.Starve` against the FTP decoy, with 127.0.0.1 tarpitted by the Console (BruteForce → TARPIT): a client bound to 127.0.0.2 got its banner in 36 ms at baseline. While 127.0.0.1 held 12 tarpitted sockets, the same client got **no banner in 6 s**. After 127.0.0.1 closed them, it still took **3.5 s** to be served.
  - Fix: run tarpits off the worker pool, e.g. one `ScheduledExecutorService` that drips to all tarpitted sockets (non-blocking), or a separate small capped pool. Limit tarpitted connections per source and close them after a maximum hold time.

- [ ] **B-08 · HIGH · VERIFIED** — One HTTP header line with no newline uses unbounded Sensor heap (about 4.6× the bytes sent)
  - Where: `sensor/FakeHttpLoginDecoy.java:46` (`BufferedReader.readLine()` with no limit); same pattern in `FakeFtpDecoy.java:121`, `FakeTelnetDecoy.java:174`
  - Problem: `readLine()` keeps buffering until it sees `\n`, so one connection can make the Sensor allocate memory without limit. In `defender` mode the Console shares that JVM, so an `OutOfMemoryError` can take out the engine, rx threads and dashboard too.
  - Evidence: `tools.BigLine` sent `GET / HTTP/1.0\r\nX: ` + 150 MB of `A` (from 127.0.0.3) to the HTTP decoy. Sensor heap (`jcmd GC.heap_info`) went from ~4 MB to **690 MB used** within 3 s. With the default 2 GB max heap on this laptop, about 450 MB would exhaust it. (Combined with B-06 there is no timeout to stop it either.)
  - Fix: read through a length-capped reader (e.g. reject a line over 8 KB and stop after 50 header lines), plus the socket timeout from B-06.

- [ ] **B-09 · MEDIUM · VERIFIED** — "Feed fake data" is just a tarpit: there is no fake-data response anywhere
  - Where: `sensor/DefenceState.java:343` (`case TARPIT, FEED_FAKE -> tarpitted.add(...)`), `console/response/FeedFakeDataAction.java`, `ResponsePolicy.java:31`, the "Feed fake data" button in `ThreatPopup`/`AttackerDetailPanel`
  - Problem: CLAUDE.md, README and the report present "misleading (fake data)" as a separate active-defence response, and HIGH alerts send TARPIT + FEED_FAKE. On the Sensor, FEED_FAKE just adds the source to the tarpit set, so the attacker sees exactly the same `.` drip, and HIGH sends the same instruction twice. The only "fake data" is the fixed admin page for the trap credential, which every source gets regardless of the command.
  - Evidence: the code line above. Live: after a brute force the Sensor logged `[defence] applied TARPIT 127.0.0.1` then `[defence] applied FEED_FAKE 127.0.0.1`, and the next FTP connect from 127.0.0.1 got only the tarpit drip, no FTP banner (see B-07 run).
  - Fix: add a `fakeData` set in `DefenceState` with a `Decision.FEED_FAKE`, and have decoys answer such sources with plausible fake content (fake `230` login / fake directory listing / fake config file), or rename the response honestly in the UI and docs.

- [ ] **B-10 · MEDIUM · VERIFIED** — `defender` starts its Sensor even when its own Console failed, reporting to whatever already holds the port
  - Where: `Launcher.java:298-317, 332-346` (`waitForPort` only checks that *something* accepts on 127.0.0.1:port)
  - Problem: if a leftover Console or defender (or any process) already listens on the console port, the probe succeeds at once. The Sensor starts and links to that **other** process, and then this defender's Console dies with `BindException`. The user sees "Connected to Console …" and a running Sensor with no Console of its own (no dashboard), and events go into the old process's ledger. A double-click on `run-defender.bat` is enough to cause it.
  - Evidence: with my Console on 29443, `mirage.jar defender --console-port 29443 …` printed `Connected to Console at 127.0.0.1:29443 as sensor-Knight_27`, `Sensor running`, and only afterwards `Console stopped: java.net.BindException: Address already in use`. The process stayed up with a Sensor only.
  - Fix: have `ConsoleMain` signal readiness, e.g. a `CountDownLatch`/`CompletableFuture<Integer>` returned by a `ConsoleMain.start(args)` that completes with the bound port or fails with the exception, instead of probing the port. Exit non-zero if the Console fails at any point before the Sensor starts.

- [ ] **B-11 · LOW · VERIFIED** — Sensor id comes from the hostname: two Sensors on one host collide; dead-sensor warnings repeat forever
  - Where: `sensor/SensorMain.java:27`, `console/SensorRegistry.java:365-373`, `ConsoleMain.java:118-123`
  - Problem: (a) every Sensor on one machine (e.g. `defender` plus a standalone `sensor`, or a restarted one) has the same id `sensor-<host>`. `LateralMovementRule` counts distinct ids, so it can't fire in a one-machine demo, and the registry merges them. (b) A Sensor that disconnects is never removed from the registry, so the Console prints `[warn] sensor silent (possible tamper)` every 15 s for ever, including for a Sensor that was shut down on purpose, and stops the moment any other Sensor with the same hostname sends a heartbeat (which hides a real tamper).
  - Evidence: after a killed `defender` Sensor and a new `sensor` on the same laptop, the Console logged `[warn] sensor silent (possible tamper): sensor-Knight_27` while the new Sensor with the **same id** was connected and healthy.
  - Fix: id = `sensor-<host>-<decoy http port>` or a UUID persisted in `./data`, overridable with `--sensor-id`. On link close, mark the Sensor DISCONNECTED (warn once) rather than warn every sweep.

- [ ] **B-12 · LOW · VERIFIED** — A second Console in the same folder dies with a raw H2 stack trace
  - Where: `ConsoleMain.java:63` (`jdbc:h2:file:./data/mirage`, no `AUTO_SERVER`, no try/catch); port-in-use at `ConsoleMain.java:115` is the same
  - Problem: starting a Console twice (common when a previous run is still in the background) prints `JdbcSQLNonTransientConnectionException: Database may be already in use … The file is locked` plus a stack trace. A port clash gives `java.net.BindException` + stack trace. Neither tells the user what to do.
  - Evidence: second `mirage.jar console` in the same working dir printed the H2 lock trace and exited. In a fresh dir with the port taken it printed `Exception in thread "main" java.net.BindException: Address already in use: bind`.
  - Fix: catch both in `ConsoleMain.main` and print one line, e.g. "Another Mirage Console is already running here (./data/mirage is locked / port 8443 in use) — close it or use --console-port". Exit with code 1.

### Console: Sensor link (ConsoleServer, registry)

- [ ] **B-13 · MEDIUM · VERIFIED** — One unparsable line, or one unknown JSON field, makes the Console drop the Sensor link (and the Sensor never comes back)
  - Where: `console/ConsoleServer.java:320-329` (any `RuntimeException` → `finally` closes the connection), `common/JsonCodec.java:123-125` (Jackson's default `FAIL_ON_UNKNOWN_PROPERTIES=true`), `console/SensorRegistry.java:357` (`ConcurrentHashMap.put(id, null)` NPE)
  - Problem: the reader treats any decode or route error as fatal for the whole connection. Because of B-05 that means the Sensor is gone until both apps restart. Triggers include a stray non-JSON line, a heartbeat without a timestamp, and any field a newer build adds to `SensorMessage`/`AttackEvent`. So two laptops running jars from different commits lose the link on the first message: no wire compatibility at all.
  - Evidence: raw client against the live Console: `HEARTBEAT` then `hello not json` gave the link aborted by the Console. `{"type":"HEARTBEAT","sensorId":"s-raw2"}` (no timestamp) gave `<EOF: console closed the link>`. A valid heartbeat plus `"version":2` also gave `<EOF: console closed the link>`.
  - Fix: catch per line inside the loop (log and skip the bad line, keep the link). Configure the mapper with `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` disabled. Default a missing heartbeat timestamp to `Instant.now()`.

- [ ] **B-14 · MEDIUM · VERIFIED** — Sensor liveness uses the Sensor's clock: a laptop clock 30 s+ slow is "silent (possible tamper)" while heartbeating
  - Where: `console/ConsoleServer.java:344` (`registry.markSeen(id, message.timestamp())`), `ConsoleMain.java:119-123` (30 s timeout against the Console's `Instant.now()`)
  - Problem: the two-laptop demo compares two different machines' wall clocks. If the Sensor clock is behind by more than ~30 s, the Console raises false tamper warnings every 15 s. If it is ahead, a Sensor that really dies is not reported until the skew has passed (an hour ahead means an hour of no warning). Laptops not on NTP (offline lab Wi-Fi) drift easily.
  - Evidence: `NetTool sensorhold 29443 -45 35` (heartbeat every 5 s, timestamp 45 s in the past) against the live Console: `[warn] sensor silent (possible tamper): skewed-sensor` was printed on **both** sweeps during the 35 s it was heartbeating.
  - Fix: record the Console's own receive time (`registry.markSeen(id)`, i.e. `Instant.now()`) and keep the Sensor timestamp only for display/skew diagnostics.

- [ ] **B-15 · MEDIUM · VERIFIED** — A Sensor that stops reading blocks `broadcast` forever, which stalls the engine, the operator buttons and every other Sensor
  - Where: `console/ConsoleServer.java:245-253` (blocking `write`/`flush`, no write timeout, under the connection lock), `:361-370` (sequential loop), called from the engine thread (`CorrelationEngine.raise` → `ResponseAction.execute`) and from `ResponseController.issue` (which is `synchronized`)
  - Problem: once a peer's TCP receive window fills (laptop asleep, process suspended, Wi-Fi gone without a FIN/RST), `out.flush()` blocks with no timeout. The engine thread is then stuck inside `raise`, so all detection stops. Healthy Sensors stop getting commands because the loop is sequential. The operator's next click blocks too, on `ResponseController`'s monitor.
  - Evidence: `BroadcastHarness` (real `ConsoleServer`, one socket that never reads + one healthy reader): after **3 062** commands, `broadcast` stopped returning. For the next 3 s the "engine" thread sat in the socket write (`completed=3062` every second) and the healthy Sensor also stayed at 3 063 lines.
  - Fix: give each `Connection` its own bounded outbound queue and writer thread (drop and close the link when the queue is full), or at least set `SO_SNDBUF` small, enable TCP keep-alive, and do the broadcast off the engine thread.

### Live shells and dashboard (GUI logic, exercised headlessly)

- [ ] **B-16 · LOW · VERIFIED** — The thread strip never shows "done": lane labels are only rebuilt when the lane count changes
  - Where: `ui/shell/ShellWindow.java:209-229` (`refreshStrip`: same lane count → `strip.repaint()` only; the `JLabel` texts are never updated)
  - Problem: a lane's "•run" mark is fixed when its label is created. When the model moves the thread to DONE (or WAITING), the strip keeps "•run" until a new thread appears. Both the concurrent-attack shell and the counter shell show finished threads as running, which contradicts the end banner.
  - Evidence: `ShellHarness` (a real `ShellWindow` built headless on the EDT, its own timer draining). After "… finished" lines, `model.lanes()` = `[attack-brute DONE, attack-spray DONE]` while the strip labels still read `● attack-brute •run`, `● attack-spray •run`.
  - Fix: rebuild when the list of `(thread, state)` pairs changes (keep the last `List<Lane>` and compare it), or keep a `Map<String,JLabel>` and update `setText` per tick.

- [ ] **B-17 · MEDIUM · VERIFIED** — Shells lag without bound under load and keep every line forever
  - Where: `ui/shell/ShellModel.java:199, 206-229` (`pending` is never trimmed, delivered lines included), `ui/shell/ShellWindow.java:93, 135-150` (drain = speed lines per 45 ms tick, default 3), `JTextPane` document never trimmed (`:152-168`), `ThreatPopup` shells live for the whole run (HIDE_ON_CLOSE, never detached: `ThreatPopup.java:58,74`)
  - Problem: display pace is fixed at about 65 lines/s by default and at most about 440 lines/s at full speed. The pipeline emits 2–4 lines per event, so a 1 000-attempt brute force is shown minutes after it happened, and the "live" shell drifts further behind real time. Every line also stays in `ShellModel.pending` and in the styled document, so memory and `insertString`/layout cost grow for the life of the Console. That hits the threat popups hardest, because they are never closed.
  - Evidence: `ShellHarness`: a 1 000-line burst had only **327 lines on screen after 5 s** (≈65/s, about 15 s to catch up). A `ShellModel` fed 200 000 lines, all polled, still held `pending.size=200000` (≈39 MB retained) before counting the Swing document.
  - Fix: drop delivered lines (`pending.subList(0, delivered).clear()` and reset `delivered`). Cap the document (remove the head past ~5 000 lines). When the backlog is over N lines, speed up the drain or collapse it ("… 1 234 lines skipped …").

- [ ] **B-18 · LOW · VERIFIED** — The attacker dossier is a frozen snapshot, and "Port probes" is always 0
  - Where: `console/ui/AttackerDetailPanel.java:104-120` (rendered only on `select`), `console/AttackerDossier.java:53` (counts `Action.PORT_PROBE`, which no decoy or attack ever emits; the only `PORT_PROBE` uses are this line and `PortScanRule`), `:51` (honeytokens, known)
  - Problem: the dossier and session replay are drawn once, when the operator clicks a source. New events from that source don't appear until it is clicked again, so in the demo the panel shows stale counts beside a live popup. "Port probes: 0" sits next to an active port-scan alert, because a port scan is reported as CONNECT events. "Credentials tried" joins every credential into one line, so after a long brute force the field becomes a wall of text.
  - Evidence: `grep -rn PORT_PROBE src/main` matches only `AttackerDossier.java:53` and `PortScanRule.java:14` (consumers, no producer). `AttackerDetailPanel` has no timer or listener (`showSource` is called only from `Dashboard.select`).
  - Fix: refresh the selected source on each alert/trace for it (or with a 1 s Swing `Timer`). Count CONNECTs across distinct decoys as probes. Show only the last N credentials, plus a count.

### Specter

- [ ] **B-19 · MEDIUM · VERIFIED** — `ProbeReply` matches "200"/"230" anywhere in the line, so failures read as ACCESS GRANTED
  - Where: `specter/ProbeReply.java:153` (`trimmed.contains("200") || …contains("230")`)
  - Problem: any first line that happens to contain those digits is classified ACCEPTED. The brute force then stops with "valid credential found" and the shell shows **ACCESS GRANTED**. It only works today because the decoys' fixed texts contain no such digits. One changed banner (e.g. a username like `admin2001` echoed in FTP's `331 Password required for …`, or a timestamp or port in a message) breaks the demo's outcome reporting.
  - Evidence: `ProbeHarness` against a local stub server: `331 Password required for admin2001` gave **ACCEPTED**. `HTTP/1.1 401 Unauthorized (retry in 1200 ms)` gave **ACCEPTED**. `530 Login incorrect, 2 attempts left of 230` gave **ACCEPTED**.
  - Fix: parse the status code. For HTTP, `line.matches("HTTP/1\.[01] 2\d\d.*")`; for FTP, `line.startsWith("230 ")`. Choose the parser by protocol, or at least anchor on the code token.

- [ ] **B-20 · MEDIUM · VERIFIED** — A runtime error in a strategy is swallowed by the pool: the shell hangs with no banner (e.g. port 70000)
  - Where: `specter/Attacker.java:52, 64` (`pool.submit(...)`; the `Future` is dropped, so exceptions vanish), `specter/SpecterConsole.java:172-177` (the port field accepts any int), `BruteForceAttack.java:253` / `PasswordSprayAttack.java:294` / `HoneytokenReplayAttack.java:340` (catch only `IOException`)
  - Problem: `new Socket(host, 70000)` (or a negative port) throws `IllegalArgumentException`, which none of the strategies catch. The pool stores it in the discarded `Future`, so the shell shows the `$ hydra …` line and then nothing: no "finished", no banner, no log line. The same applies to any bug inside a strategy, and to `CounterOffensive` counters launched from the Console.
  - Evidence: `ProbeHarness`: launching `BruteForceAttack` at `127.0.0.1:70000` through `Attacker` produced exactly two trace lines (`start brute-force`, `$ hydra …`) and nothing after 1 s. No error appeared anywhere.
  - Fix: in `runOne`/`runCampaign`, `try { strategy.run(...) } catch (RuntimeException e) { trace BAD + BANNER "ERROR: …"; logger }`. Validate 1–65535 in `SpecterConsole.onLaunch` (and in `LabConfig.intValue` for ports).

- [ ] **B-21 · MEDIUM · VERIFIED** — The recon and mirror scans are hard-coded to 8020–8085, so they ignore the configured ports; in the one-laptop run Specter's own scan trips its own counter-watch
  - Where: `specter/SpecterConsole.java:230, 238` and `console/MirrorCounterStrategy.java:410, 419, 426` (`new PortScanAttack(8020, 8085)`), `LabConfig.java:48-52`
  - Problem: (a) if any port is moved (TWO-LAPTOP-SETUP's troubleshooting tells users to change `counterwatch.port` when 8080 is busy, and **on this laptop 8080 is busy**), the recon scan stops finding the decoys and the mirror scan stops reaching the counter-watch. Nothing reports this. (b) With the defaults on **one laptop**, the attacker's own *Recon scan* of 127.0.0.1 covers 8080 = Specter's counter-watch. DEMO step 1 ("`SCAN COMPLETE — 3 OPEN` (the decoys)") then shows **4 OPEN**, and the counter-watch flashes **INCOMING COUNTER-ATTACK** before any counter was launched.
  - Evidence: `ScanHarness`. `MirrorCounterStrategy.counterFor(T1046)` returns `PortScanAttack` with range `8020-8085` whatever the config. Reproducing the one-laptop layout shifted by +21000 (decoys 29021/29023/29081, `CounterWatch` on 29080, scan 29020–29085 through the real `Attacker`) gave `SCAN COMPLETE — 4 OPEN` with port 29080 in the list. `CounterWatch` logged `INCOMING connection #1 from 127.0.0.1` + `INCOMING COUNTER-ATTACK`.
  - Fix: build the scan from `LabConfig` (the set {http, ftp, telnet, counterwatch} ± a small margin, or explicit `scan.from/scan.to` keys). On one machine, leave the local counter-watch port out of Specter's own recon.

### Docs, build and tests

- [ ] **B-22 · HIGH · VERIFIED** — The `mirage.properties` snippet in TWO-LAPTOP-SETUP uses inline `#` comments, which Java Properties keeps as part of the value: every role refuses to start
  - Where: `docs/TWO-LAPTOP-SETUP.md:56-59` (`lab.cidrs=… # your network's range …`, `console.host=… # Laptop A's IP …`), `common/LabConfig.java:72-80` (`Properties.load` + `trim` only)
  - Problem: `.properties` has no inline comments. A user who copies the step-4 block gets `lab.cidrs` = `192.168.0.0/24,127.0.0.1/32     # your network's range (+ loopback for local tests)`, so Console, Sensor and Specter all print `Refusing to start: not a valid CIDR`. If only `lab.cidrs` is fixed, `console.host` still carries the comment, so Specter's target field is pre-filled with junk and refused by ScopeGuard (the Console prints the wrong address too). This is the main setup step of the two-laptop demo. (`mirage.properties.example` itself is correct.)
  - Evidence: wrote the doc's two lines into a scratch `mirage.properties` and ran `mirage.jar console` → `Refusing to start: not a valid CIDR: 127.0.0.1/32     # your network's range (+ loopback for local tests)`. With `lab.cidrs` fixed, `LabConfig.load` gave `console.host=[192.168.0.107                # Laptop A's IP] authorised=false`.
  - Fix: move the comments onto their own lines in the doc. Defensively, strip a trailing `\s+#.*$` in `LabConfig.load` (or reject values containing whitespace with a clear message).

- [ ] **B-23 · LOW · VERIFIED** — Smaller docs-vs-code contradictions
  - Where / Problem / Evidence:
    - `README.md:35` says "Sensors … report over TLS". By default they don't: both apps print `TLS not configured …; using plain sockets` (seen in every run here), and `run-defender.*` never set `-Dmirage.keystore`.
    - `docs/TWO-LAPTOP-SETUP.md` troubleshooting: "`Could not reach Console …` on A → Another Console already holds 8443". In fact the new defender's Sensor **connects** to the old Console and keeps running (B-10), so that message never appears in that case.
    - `docs/TWO-LAPTOP-SETUP.md:79` "Put these **four** files" lists three.
    - `DEMO.md` §2 / BUILD_LOG: "one ledger entry per finding". Not true across an unblock (B-03), and a honeytoken break-in is always two CRITICAL entries (B-04).
    - CLAUDE.md/README "built and tested on JDK 21". The shipped `target/mirage.jar` manifest says `Build-Jdk-Spec: 25` (bytecode is correctly major 61 / Java 17). CI uses JDK 17 (`.github/workflows/ci.yml`), so nothing tests on 21.
  - Fix: correct the wording. Mention `-Dmirage.keystore/-Dmirage.truststore` in the run scripts or README.

- [ ] **B-24 · MEDIUM · VERIFIED** — Flaky test, which is also a real race: a blocked probe is reported as "connection failed" instead of "connection reset (blocked)"
  - Where: `src/test/java/com/mirage/StrategyOutcomeTest.java:38-47`, product side `sensor/Decoy.java:274-279` (RST right after accept) vs `specter/BruteForceAttack.java:253-266` (an `IOException` on connect/write gives "connection failed"; only one during `ProbeReply.read` gives "reset (blocked)")
  - Problem: when the decoy's RST arrives before the client's `write`, the attempt line says `connection failed` (BAD), not `connection reset (blocked)`. The test asserts the lowercase "blocked" and fails. The end banner is correct (`CONNECTION BLOCKED`), so the demo text just varies between runs. On CI it's an intermittent red build.
  - Evidence: ran the 98 compiled tests with the JUnit Platform launcher (copied `test-classes`, scratch cwd), 7 runs in total: `blockedSourceSeesTheConnectionDropped` failed in **2 of 7** with `[… BAD try admin:x  ->  connection failed, BANNER CONNECTION BLOCKED]`.
  - Fix: treat `SocketException` "reset"/"aborted" on connect/write as DROPPED with the "connection reset (blocked)" text as well. In the test, assert on the banner `CONNECTION BLOCKED` or on outcome DROPPED.

- [ ] **B-25 · LOW · VERIFIED** — `PersistenceDaoTest` is not isolated: a named in-memory H2 with `DB_CLOSE_DELAY=-1` keeps rows between runs in one JVM
  - Where: `src/test/java/com/mirage/PersistenceDaoTest.java:22` (`jdbc:h2:mem:mirage_test;DB_CLOSE_DELAY=-1`, then `assertEquals(2, count)`)
  - Problem: the DB outlives the test, so a rerun in the same JVM (IDE "rerun", a suite runner, or any future test that uses the same name) sees old rows.
  - Evidence: in one JVM, runs 2–6 failed `expected: <2> but was: <4>`, `<6>`, `<8>`, `<10>`, `<12>`. It passes under Maven only because surefire runs it once per fork.
  - Fix: unique name per test (`jdbc:h2:mem:` + `UUID`) and no `DB_CLOSE_DELAY`, or drop the tables in `@AfterEach`.

- [ ] **B-26 · MEDIUM · VERIFIED** — Test gaps around the failure modes above
  - Where: `src/test/java/com/mirage/*`
  - Problem: the suite is green (98) but covers almost only happy paths. Things with **no test**: `BaitFileWatcher` (only the rule is tested); `ConsoleServer` behaviour on a malformed/unknown-field line (B-13); `CorrelationEngine` with `H2Persistence` or a throwing rule (B-01); Sensor link loss / Console restart (B-05); decoy idle/timeout behaviour (B-06); `ProbeReply` false positives (B-19: `ProbeOutcomeTest` has 2 cases); `Launcher.defender`'s failure path (B-10, `LauncherTest` covers only `sensorArgs`); `LabConfig` with inline comments / out-of-range ports (B-22); `SensorRegistry` with clock skew (B-14); `ShellWindow` strip state (B-16); unblock + re-alert (B-03). `AttackerDossierTest:20` builds `PORT_PROBE` events that no producer emits (B-18), so the test passes on a code path production never takes.
  - Evidence: `grep -l BaitFileWatcher src/test` gives nothing. The test names and assertions were read for each file listed.
  - Fix: add a focused test for each finding when fixing it (CLAUDE.md already requires one for ledger/rules/ScopeGuard changes).

- [ ] **B-27 · INFO · VERIFIED** — The shaded jar itself is fine for every role
  - Where: `pom.xml` (shade with Manifest + Services + license transformers), `target/mirage.jar`
  - Evidence: manifest `Main-Class: com.mirage.Launcher`, `Multi-Release: true`; `META-INF/services` merged (the H2 driver loads: the Console opened `./data/mirage` from the jar); classes are major 61. Ran `console`, `sensor`, `defender` (headless), `attacker` (prints "needs a display", exit 0), and a bogus role (usage, exit 2). Run scripts quote `%~dp0`/`$(dirname "$0")`, so paths with spaces are fine. Missing Java gives a clear message. (Note: `attacker` exits 0 headless, which can hide a failed start in scripts.)

### More: detection logic, bait files, persistence

- [ ] **B-28 · MEDIUM · VERIFIED** — Rules have no time window and the thresholds sit exactly at what Specter sends
  - Where: `console/detection/*.java` (`grep -c timestamp` = 0 across all rules), `BruteForceRule.java:111` (5), `PortScanRule.java:282` (3 distinct services, and only 3 decoys exist), `PasswordSprayRule.java:251` (3), `SpecterConsole.java:231-234` (brute = exactly 5 passwords)
  - Problem: every count is over the source's whole lifetime. Five failed logins spread over a week is "Credential brute-force". Because the count is cumulative, a spray (4 attempts) plus one later typo crosses the brute-force threshold. On the other side, detection has no slack: a scan has to touch **all three** decoys (if one decoy failed to bind, or the scan range misses one — B-21 — PortScan never fires), and the demo brute force sends exactly 5 attempts, so one lost or stalled attempt (`BruteForceAttack` stops at the first STALLED/DROPPED) means no T1110 alert. "Low-and-slow" (the stealth slider) is not modelled at all, since time is ignored.
  - Evidence: `grep -rn timestamp src/main/java/com/mirage/console/detection/` gives 0 matches. In the `EngineHarness scale` run, LOGIN_ATTEMPTs with arbitrary spacing all count toward the same lifetime totals.
  - Fix: per-rule sliding windows (e.g. ≥5 logins in 60 s, ≥3 services in 30 s) kept as incremental counters (also fixes B-02). Thresholds with headroom relative to the demo campaigns. A separate slow-scan rule if "low-and-slow" is a talking point.

- [ ] **B-29 · MEDIUM · VERIFIED** — No bait files are ever planted: the `bait/` folder is empty, so DEMO's "edit or delete any file inside bait/" has nothing to edit
  - Where: `sensor/SensorMain.java:70-73` (creates `bait/` and watches it, plants nothing), `sensor/BaitFileWatcher.java:220-251` (top-level only, no subfolders), `DEMO.md` "Bait file (either mode)"
  - Problem: on a fresh checkout (no `bait/` in the repo), the folder is created empty. The presenter has to create a file to cause a CREATE event, and nothing looks like a lure an attacker would touch. Files in subfolders are not watched. On Windows one save gives several MODIFY events (harmless thanks to de-dup, but noisy in the shells).
  - Evidence: after three Sensor runs here, `run2/bait` and `run3/bait` were empty directories. There is no `bait/` in `E:\cybersecurity`. No code writes into it.
  - Fix: on start-up, plant a few realistic lures if they are missing (`passwords.xlsx`, `backup.cfg` containing the honeytoken, `vpn.key`), and register subdirectories too.

- [ ] **B-30 · MEDIUM · VERIFIED** — The persisted ledger is not a chain across restarts, and the DB can't rebuild what the Console knew
  - Where: `console/AlertLedger.java:20-24` (always starts at GENESIS, nothing loaded), `ConsoleMain.java:59-64` (new `KeyPairSigner()` every start; the key is never saved), `console/db/EventDao.java:272` (stores src/ts/action/payload only: no decoy type, no sensor id), `db/schema.sql` (`sensor`, `decoy`, `attacker_profile`, `honeytoken`, `block_rule` are never written)
  - Problem: each Console start begins a new chain at `000…0` in the same `alert` table, so the stored table is several unlinked chains. The signatures of earlier runs can't be verified, because their public key is gone. Profiles, de-dup memory and block state are not restored, so after a restart every old attacker is "new" and the dashboard is empty, although the DB has the history. The stored events can't reproduce detections that need `decoyType` (PortScanRule) or `sensorId` (LateralMovementRule). The schema promises tables that stay empty, which a marker reading `REPORT.md`/`schema.sql` will notice.
  - Evidence: `DbCheck` on `run4/data/mirage` after two Console runs: alerts #1–#5 chained (`prev=000000000000` → … → `280995a944d5`), then alert **#33** from the second run with `prev=000000000000` again. `attack_event rows=12004`; `sensor`, `decoy`, `attacker_profile`, `honeytoken`, `block_rule` rows = **0**.
  - Fix: on start-up, load the last `hash` from the `alert` table as `lastHash` (and verify the stored chain), and persist the signer key pair (e.g. PKCS12 in `./data`). Add `decoy_type`/`sensor_id` columns. Either write the extra tables (sensor heartbeats, block rules, honeytokens) or remove them from the schema and report.

### More: launcher and configuration

- [ ] **B-31 · MEDIUM · VERIFIED** — `defender` with a decoy port in use: the Console keeps running with a "connected" Sensor that has no decoys
  - Where: `sensor/SensorMain.java:32-42` (link opened **before** the decoys bind), `:63-65` (a `BindException` escapes `main`), `Launcher.java:316` (no handling; the non-daemon `console-main` thread keeps the JVM alive)
  - Problem: if 8081/8021/8023 is taken (a leftover run, Skype/IIS on 8080-ish ports, etc.), the Sensor connects, then dies on bind. The socket is never closed, so the Console still counts one connected Sensor (`Sensors connected: 1` on the dashboard). No heartbeat was ever sent, so the dead-sensor sweep never warns about it either. The operator sees a healthy system, and the attacker's scan finds 0 open ports.
  - Evidence: held 29081 with a dummy listener, then ran `mirage.jar defender … --http-port 29081`. The log showed `Connected to Console at 127.0.0.1:29443 as sensor-Knight_27` followed by `Exception in thread "main" java.net.BindException`. Nine seconds later the process was still alive, and `jstack` showed `rx-127.0.0.1` and `sensor-client-rx` running and **no** `decoy-*` threads.
  - Fix: bind all decoys first and connect afterwards. In `SensorMain` catch the bind failure, close the client, and print which port is taken. In `Launcher.defender`, exit non-zero if `SensorMain` throws.

- [ ] **B-32 · LOW · VERIFIED** — Mistyped flags are silently ignored, and port values are not range-checked
  - Where: `common/LabConfig.java:92-126` (unknown `--flags` dropped, empty values dropped), `:230-240` (`intValue` accepts any int)
  - Problem: the flag is `--lab-cidr` but the property is `lab.cidrs`. Typing the natural `--lab-cidrs 192.168.0.0/24` is silently ignored, so the app runs on the default `10.10.0.0/24` and Specter then refuses Laptop A with no hint why. Ports like `-1`/`70000` are accepted and fail later with a raw `IllegalArgumentException: Port value out of range` stack trace. On the Sensor that happens **after** it has already tried the Console (see B-31).
  - Evidence: `FlagCheck`: `parseArgs(--lab-cidrs 192.168.0.0/24)` → `{}`, `--http-port -1` → `{decoy.http.port=-1}`, `--http-port 70000` → `70000`. `mirage.jar console --console-port 70000` gave `Exception in thread "main" java.lang.IllegalArgumentException: Port value out of range: 70000`.
  - Fix: warn (or fail) on unknown flags; also accept `--lab-cidrs`. Validate ports to 1–65535 in `intValue`, with a message naming the key.

### Suspected (from reading the code, not reproduced)

- [ ] **B-33 · LOW · SUSPECTED** — Smaller concurrency and robustness issues seen in the code
  - `console/AlertBus.java:338-340`: `publish` doesn't isolate listeners (unlike `TraceBus`). An exception in `Dashboard.onAlert` (e.g. a `null` classification label) is thrown on the engine thread and kills it (B-01).
  - `console/ConsoleServer.java:293-303`: `acceptLoop` swallows `IOException` and loops. A persistent accept error (e.g. out of file handles) becomes a 100 % CPU spin. The cached pool also gives one thread per inbound connection with no cap and no authentication.
  - `sensor/SensorMain.java:63-84`: heartbeats start only after all decoys and the bait watcher are up, and `ConsoleServer` tags events with `conn.sensorId`, which is set by the first heartbeat. Events in that window are stored with `sensorId=null`.
  - `console/ui/Dashboard.java:218-220`: the counter uses the *latest* alert's technique, not the most severe. After a honeytoken break-in (T1078), a later MEDIUM port-scan alert makes **Counter-attack** mirror a scan instead of the replay.
  - `console/ThreatTracker.java:205-226` with two Sensors: BLOCK-ack(S1) → UNBLOCK-ack(S1) → late BLOCK-ack(S2) gives NEUTRALISED even though the source is unblocked everywhere, until S2's UNBLOCK ack arrives. ACKs aren't matched to the command they confirm.
  - `console/ui/Dashboard.java:249-255`: one `ThreatPopup` (a JFrame + ShellWindow + 45 ms Timer) per source, never disposed. A scan from many in-range sources opens and keeps that many frames and timers.
  - `ConsoleMain.java:130-135`: the dashboard subscribes to the `AlertBus`, and its `TraceBacklog` to the `TraceBus`, only once the EDT has built it, *after* the server is listening. Alerts or trace lines in that window go to the ledger but never into the feed or popups.
  - `specter/SpecterConsole.java:188` creates a new `Attacker` (with a fixed pool of up to 16 non-daemon threads) per launch, shut down only when its popup is closed. A presenter who launches many times without closing popups accumulates idle threads and open frames, each with a live Timer (`ShellWindow.close()` is never called from `SpecterConsole.openShell`).

## Summary

| Severity | Count | Items |
| --- | --- | --- |
| CRITICAL | 0 | — |
| HIGH | 6 | B-01, B-02, B-05, B-06, B-08, B-22 |
| MEDIUM | 17 | B-03, B-07, B-09, B-10, B-13, B-14, B-15, B-17, B-19, B-20, B-21, B-24, B-26, B-28, B-29, B-30, B-31 |
| LOW | 9 | B-04, B-11, B-12, B-16, B-18, B-23, B-25, B-32, B-33 |
| INFO | 1 | B-27 |
| **Total** | **33** | 32 VERIFIED, 1 SUSPECTED (B-33, a bundle of code-reading notes) |

Fix these first for the demo: B-22 (the doc snippet stops every role from starting), B-05 + B-13 (a lost Sensor link is lost for good), B-01 (a single bad event silently stops detection), B-06/B-08 (any client can blind or exhaust a decoy), B-03 (step 6 of DEMO.md doesn't behave as scripted), B-21 (the one-laptop recon scan hits Specter's own counter-watch).

Harnesses and logs (scratch, not in the repo): `EngineHarness`, `Step6Harness`, `BroadcastHarness`, `ShellHarness`, `ProbeHarness`, `ScanHarness`, `NetTool`, `Starve`, `BigLine`, `DbCheck`, `FlagCheck`, `PropsCheck`, `RunTests` in the agent-b scratch folder.

## Not covered

- Security/safety (ScopeGuard bypasses, TLS configuration, ledger signing strength, hack-back limits). That is checklist A's job; only docs-vs-code TLS wording is noted here (B-23).
- The real Swing windows weren't opened (headless rule). GUI findings come from GUI-free logic or headless `JPanel`s (B-16/B-17) or from code reading (B-18, B-33). EDT freezes and real repaint cost with large documents are not measured.
- No two-machine run: LAN, firewall, Wi-Fi isolation, real clock skew between laptops (simulated in B-14), and TLS between hosts were not exercised.
- Not tested under sustained or large load: an actual OOM (B-08 stopped at 690 MB), thousands of concurrent Console connections, H2 under disk-full or locked-file conditions mid-run (only a schema-length failure was triggered).
- `run-*.sh` on macOS/Linux and paths with spaces were checked by reading only. The CI workflow was read, not run.
- `tools/screenshots` dry-run harnesses and REPORT.md's figures were not re-run or checked line by line.
