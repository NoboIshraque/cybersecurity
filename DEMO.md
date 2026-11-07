# Mirage — Demo Script

Two presenters, two laptops. **Laptop A (defender)** runs the Console and the Sensor (the
decoys live on the protected host). **Laptop B (attacker)** runs Specter and its counter-watch.
Every step streams live in terminal-style shells on both screens, so the audience sees the
attack land and the defence answer it in real time.

Everything stays inside the lab range. `ScopeGuard` refuses any other address, on both sides.

- **Set-up (once, before the audience arrives):** follow
  [`docs/TWO-LAPTOP-SETUP.md`](docs/TWO-LAPTOP-SETUP.md): same network, `mirage.properties` on both
  laptops, firewall rules, connectivity check.
- **Fallback:** the same script runs on one laptop. See [One-laptop run](#one-laptop-run) below.
- **Run time:** about 8 minutes. Rehearse once with the
  [automated dry run](tools/screenshots/README.md#live-shells-dry-run-livedemoshotjava) if you
  change anything.

## 0. Start (before you begin talking)

| Laptop A — defender | Laptop B — attacker |
| --- | --- |
| `run-defender.bat` | `run-attacker.bat` |
| Dashboard opens. Status bar: `Console at 192.168.0.107:8443 · Sensors connected: 1 · Auto-response: OFF (operator)` | Specter opens with the target already set to Laptop A; a *counter-watch (incoming)* window opens and waits |

Put the two screens side by side on the projector (or one each). On Laptop A, click
**Pipeline shell** to show the Console's own processing as it happens.

## 1. Reconnaissance

| Who | Does | Audience sees |
| --- | --- | --- |
| Attacker | Campaign **Recon scan** → **Launch** | Specter shell types out an `nmap`-style sweep and ends with `SCAN COMPLETE — 3 OPEN` (the decoys) |
| Defender | (nothing) | A **threat popup** appears for Laptop B's IP: amber **PROBE DETECTED**. Its shell shows the receive thread → correlation engine → `PortScanRule MATCH … T1046` → ledger entry `#0 sha256=… signed (ECDSA)` → *auto-response OFF, waiting for the operator* |

Talking point: the decoys are services nobody legitimate uses, so any touch is an intruder.
That's why the alert is high-confidence.

## 2. Credential attacks

| Who | Does | Audience sees |
| --- | --- | --- |
| Attacker | **Password spray** → Launch, then **Brute force** → Launch | Each try scrolls by (`try admin:Password123! -> 401`), ending in `NO ACCESS` |
| Defender | (nothing) | The popup escalates to orange **UNDER ATTACK** (T1110.003 / T1110, HIGH). Rules that already fired say *still match, already alerted — no new alert*: one ledger entry per finding |

## 3. The break-in (that is actually a trap)

| Who | Does | Audience sees |
| --- | --- | --- |
| Attacker | **Honeytoken replay** → Launch | `replay -> 200 OK  ACCESS GRANTED`. The attacker thinks they're in |
| Defender | (nothing yet) | The popup turns red: **COMPROMISED**: *Stolen credentials used (honeytoken)* and *Intruder inside decoy (honeypot session)*, both CRITICAL, T1078 |

Talking point: the credential was planted bait (a honeytoken), and the "admin page" is fixed
text served by the decoy. Nothing real sits behind it. The attacker is inside a trap, and the
Console knows exactly who and how.

**Optional:** tick **All at once (split threads)** in Specter and launch **Full campaign**. The
shell splits into one pane per attack thread, all running at once.

## 4. The operator responds

Auto-response is **off**, so the Console has recorded everything and is waiting. The operator
answers from the popup.

| Who | Does | Audience sees |
| --- | --- | --- |
| Defender | Popup → **Tarpit** | An **operation shell** opens: `$ operator: TARPIT …` → `ScopeGuard.require(…)` → `in range — dispatching` → `broadcast TARPIT … to 1 sensor(s)` → `sensor-… confirms: TARPIT … applied` (each line tagged with the thread doing it: `op-tarpit`, `rx-sensor-…`) |
| Attacker | **Honeytoken replay** → Launch | `no reply (tarpit)` → **CONNECTION STALLED**: the attacker is held on the line |
| Defender | Popup → **Block** | Op shell: `op-block` → broadcast → Sensor ACK; the popup turns green: **NEUTRALISED** — *Sensor confirmed the block, the source is cut off* |
| Attacker | **Honeytoken replay** → Launch | `connection reset (blocked)` → **CONNECTION BLOCKED** |

## 5. Turning it around (lab range only)

| Who | Does | Audience sees |
| --- | --- | --- |
| Defender | Popup → **Counter-attack** | A counter shell: the Console mirrors the attacker's own technique back at Laptop B, `in range — mirroring …` |
| Attacker | (nothing) | The counter-watch window flashes **INCOMING COUNTER-ATTACK** with each connection's source port and payload |
| Defender | Popup → **Simultaneous counter** | The counter shell splits: one pane per counter thread (`op-port-scan`, `op-mirror-…`, …), all at once. The bounded set of four methods is not a flood |
| Attacker | (nothing) | More `INCOMING` lines. The probes end in `NO REPLY`, because the counter-watch only listens and logs |

Talking point: every counter goes through the same `ScopeGuard` as everything else. It can
reach Laptop B because Laptop B is inside the lab range, and it cannot reach anything outside it.

## 6. Hands-off mode

| Who | Does | Audience sees |
| --- | --- | --- |
| Defender | Popup → **Inspect in dashboard** → **Responses** tab → **Unblock**, then tick **Auto-response** | The popup drops back to COMPROMISED; the status bar reads `Auto-response: ON` |
| Attacker | **Honeytoken replay** → Launch | It gets in for an instant, then is cut off |
| Defender | (nothing) | The pipeline shell shows `auto-response: block …` and the Sensor's ACK. The popup goes **NEUTRALISED** with no click |

## 7. Tamper evidence and the safety boundary

| Who | Does | Audience sees |
| --- | --- | --- |
| Defender | **Verify ledger** | Status bar: `Ledger: verified`. Every alert is hash-chained and ECDSA-signed; altering any stored alert turns this to `BROKEN (entry n)` with the row in red |
| Attacker | Type `8.8.8.8` as the target → **Recon scan** → Launch | **REFUSED — OUTSIDE LAB RANGE**. Nothing leaves the laptop |

## One-laptop run

Everything above works on one machine over `127.0.0.1`. With no `mirage.properties`, the
defaults are the single-machine lab:

```bash
.\mvnw.cmd -DskipTests package      # once; ./mvnw on macOS/Linux
run-defender.bat                    # window 1: Console + Sensor
run-attacker.bat                    # window 2: Specter + counter-watch
```

The individual apps still run on their own too (`java -jar target\mirage.jar console | sensor |
specter`, or `java -cp target/classes com.mirage.console.ConsoleMain` and friends after
`mvnw compile`). In this mode the source shown everywhere is `127.0.0.1`.

**Bait file (either mode):** on Laptop A, edit or delete any file inside the `bait/` folder next
to the jar. The Sensor plants three lures there on start (`passwords.csv`, `vpn.key`,
`config/backup.cfg`, the last one holding the honeytoken), so try `bait/config/backup.cfg`. A
CRITICAL *Bait file tampered (possible ransomware)* alert (T1485) appears. A change made while
the defender was closed is reported at its next start, marked *while the Sensor was down*.

## Safety

Everything is confined to the lab range by `ScopeGuard`, which refuses to even start on a
public or over-wide range (it must be private or loopback, `/16` or narrower). Specter refuses any target
outside the range; the Console only responds to, or counters, in-range sources. The decoy's
"admin page" is canned text, and the counter-watch only accepts and logs. Mirage never touches
external systems.
