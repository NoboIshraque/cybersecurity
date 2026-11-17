# Mirage demo video: voice-over script

For **`Mirage demo video v2.mp4`** (1:48). The times match the cue sheet in
`docs/DEMO-VIDEO-PROGRESS.md`. Each line says what is on screen and **which OODP topic makes it
work**. The script is about 265 words, about 150 words a minute: a calm pace that fits 1:48.
The **bold** words are the course topics; stress them a little.

| Time | On screen | Narration |
| --- | --- | --- |
| 0:00–0:05 | Title card | Mirage: a deception-based intrusion detection system, built as three **object-oriented** Java applications. |
| 0:05–0:13 | Recon scan → SCAN COMPLETE; popup PROBE DETECTED | Specter, our attacker, scans the decoys. Every fake service **extends one abstract Decoy class**: **inheritance** and the **Template Method** pattern. |
| 0:13–0:21 | Brute force → NO ACCESS; popup UNDER ATTACK | Now a brute force. Each detection rule **implements one DetectionRule interface**, the **Strategy** pattern, so the engine checks them all **polymorphically**. |
| 0:21–0:30 | Honeytoken replay → ACCESS GRANTED; popup COMPROMISED | A planted credential "gets in", but only to a fake page. The alert is saved through **JDBC** into an **H2 database**. |
| 0:30–0:40 | Operator → Tarpit; op shell; CONNECTION STALLED | The operator clicks Tarpit. Each response is a **Command object** that runs on its **own named thread**, while the decoy drips one byte at a time. |
| 0:40–0:51 | Operator → Feed fake data; attacker "gets in" to fake files | Feed fake data lets the attacker in, to worthless files. It is just one more **Command**, and each decoy **overrides** its own fake-data method: **polymorphism** again. |
| 0:51–1:04 | Operator → Block; popup NEUTRALISED; CONNECTION BLOCKED | Block. The command travels over a **TLS socket** to the Sensor, which sends back an acknowledgement. Only then does the popup, an **Observer** of the event bus, turn green. Specter now gets a reset. |
| 1:04–1:18 | Counter-attack → INCOMING COUNTER-ATTACK; Simultaneous counter, one pane per thread | The counter-attack mirrors the attacker's own technique back. Every target first passes **ScopeGuard**, which **throws an exception** outside the lab range. The simultaneous counter runs four strategies on a **thread pool**: one thread, one pane each. |
| 1:18–1:31 | Dashboard: alert feed, dossier; Verify ledger → verified | The dashboard is built in **Swing**: a **JTable** with a custom table model, a hand-painted network map, and Swing timers. Verify ledger re-checks the **SHA-256 hash chain** and the **ECDSA signatures**. |
| 1:31–1:43 | Target 8.8.8.8 → REFUSED — OUTSIDE LAB RANGE | An outside target is refused before any thread starts. And underneath it all, network threads feed a **BlockingQueue** that one engine thread consumes: **producer–consumer**, with no locks. |
| 1:43–1:48 | Closing card | Swing, JDBC, sockets, threads and design patterns, all in one system. Thank you. |

## Plain script (to read from)

> **[0:00]** Mirage: a deception-based intrusion detection system, built as three object-oriented
> Java applications.
>
> **[0:05]** Specter, our attacker, scans the decoys. Every fake service extends one abstract Decoy
> class: inheritance and the Template Method pattern.
>
> **[0:13]** Now a brute force. Each detection rule implements one DetectionRule interface, the
> Strategy pattern, so the engine checks them all polymorphically.
>
> **[0:21]** A planted credential "gets in", but only to a fake page. The alert is saved through
> JDBC into an H2 database.
>
> **[0:30]** The operator clicks Tarpit. Each response is a Command object that runs on its own
> named thread, while the decoy drips one byte at a time.
>
> **[0:40]** Feed fake data lets the attacker in, to worthless files. It is just one more Command,
> and each decoy overrides its own fake-data method: polymorphism again.
>
> **[0:51]** Block. The command travels over a TLS socket to the Sensor, which sends back an
> acknowledgement. Only then does the popup, an Observer of the event bus, turn green. Specter now
> gets a reset.
>
> **[1:04]** The counter-attack mirrors the attacker's own technique back. Every target first
> passes ScopeGuard, which throws an exception outside the lab range. The simultaneous counter runs
> four strategies on a thread pool: one thread, one pane each.
>
> **[1:18]** The dashboard is built in Swing: a JTable with a custom table model, a hand-painted
> network map, and Swing timers. Verify ledger re-checks the SHA-256 hash chain and the ECDSA
> signatures.
>
> **[1:31]** An outside target is refused before any thread starts. And underneath it all,
> network threads feed a BlockingQueue that one engine thread consumes: producer–consumer, with no
> locks.
>
> **[1:43]** Swing, JDBC, sockets, threads and design patterns, all in one system. Thank you.

## Where each claim lives in the code (if you are asked)

| Topic | Class(es) |
| --- | --- |
| Inheritance, abstract class, Template Method | `sensor.Decoy` (`final start()`, `abstract interact()`, overridable `feedFake()`) → `FakeHttpLoginDecoy`, `FakeFtpDecoy`, `FakeTelnetDecoy` |
| Interface, Strategy, polymorphism | `console.detection.DetectionRule` (7 rules); `specter.AttackStrategy` (4 attacks) |
| JDBC, H2 | `console.db.*Dao` with `PreparedStatement`, `console.db.Database` |
| Command | `console.response.ResponseAction` → `TarpitAction`, `FeedFakeDataAction`, `BlockSourceAction` |
| Threads: named operator threads, thread pools | `console.ui.OperationShells` (`op-tarpit`, `op-block`, …); `specter.Attacker.launchConcurrent` (`ExecutorService`) |
| Sockets, TLS | `console.ConsoleServer`, `sensor.SensorClient`, `common.TlsFactories` (`SSLServerSocketFactory`) |
| Observer | `console.AlertBus`, `common.trace.TraceBus` → `ThreatPopup`, `Dashboard`, shells |
| Exceptions, safety | `common.ScopeGuard.require` throws `SecurityException` |
| Swing | `console.ui.Dashboard` (`JFrame`), `AlertTableModel` (`AbstractTableModel`), `NetworkMapPanel` (`paintComponent`), `javax.swing.Timer`, `SwingUtilities.invokeLater` |
| Hashing, signatures | `console.AlertLedger` (`MessageDigest` SHA-256), `console.KeyPairSigner` (`SHA256withECDSA`) |
| Producer–consumer | `ConsoleServer` receive threads → `BlockingQueue<AttackEvent>` → `CorrelationEngine.run()` |

## Recording tips

- Record each block as its own clip in Clipchamp, and drag it to its start time. A late line then
  never pushes the next one off its scene.
- If a block runs long, drop the part after the colon (for example "one thread, one pane each").
  The topic word before it is the part that matters.
- Say "J-D-B-C", "E-C-D-S-A", "S-H-A two-fifty-six", "H-two".
