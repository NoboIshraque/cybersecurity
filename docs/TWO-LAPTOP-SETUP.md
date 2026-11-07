# Two-laptop setup

How to run Mirage across two laptops: **Laptop A (defender)** runs the Console and the Sensor
(the decoys live on the protected host), and **Laptop B (attacker)** runs Specter and its
counter-watch listener. Plan for about 15 minutes the first time. Every step below is something
you run yourself. Nothing here changes system settings on its own.

> **Fallback:** if the network is not cooperating, everything still runs on one laptop:
> `run-defender.bat` and `run-attacker.bat` side by side, with no `mirage.properties`
> (the defaults use `127.0.0.1`).

## 1. Checklist

| | Laptop A — defender | Laptop B — attacker |
| --- | --- | --- |
| Java | 17 or newer | 17 or newer (nothing else, no Maven) |
| Files | the repo (or the four files below) | `mirage.jar`, `run-attacker.bat` (or `.sh`), `mirage.properties` |
| Inbound ports | 8081, 8021, 8023 (decoys) | 8080 (counter-watch) |
| Runs | `run-defender.bat` | `run-attacker.bat` |

Check Java on each laptop with `java -version`. If it's missing, install a JDK/JRE 17+ (for example
Eclipse Temurin from adoptium.net).

## 2. Put both laptops on the same network

Use a network that lets laptops talk to each other. A **phone hotspot** is the safest choice;
home Wi-Fi works too. University and hotel Wi-Fi often use "client isolation", which blocks
laptop-to-laptop traffic. If the connectivity test in step 7 fails there, switch to the hotspot.

Find each laptop's IPv4 address (Windows: `ipconfig`, look under the **Wi-Fi** adapter;
macOS/Linux: `ip addr` or `ifconfig`). Both addresses should share the first three numbers,
e.g. `192.168.0.107` (A) and `192.168.0.112` (B). That gives the lab range `192.168.0.0/24`.

> Ignore adapters such as *VirtualBox Host-Only*, *vEthernet*, *VMware* or a VPN. Use the
> address of the adapter you are actually connected through.

On Windows, make sure the connection is a **Private** network on both laptops (Settings →
Network & internet → Wi-Fi → your network → *Private network*). The firewall rules below apply
to the Private profile only.

## 3. Build the jar (on Laptop A)

```bash
.\mvnw.cmd -DskipTests package      # Windows PowerShell
./mvnw -DskipTests package          # macOS / Linux
```

This produces `target\mirage.jar` (about 5 MB, all dependencies included).
`java -jar target\mirage.jar` with no arguments prints the roles and flags.

## 4. Write `mirage.properties`

Copy `mirage.properties.example` to `mirage.properties` and set two values. **The same file
works on both laptops.**

```properties
# Your network's range (+ loopback for local tests)
lab.cidrs=192.168.0.0/24,127.0.0.1/32
# Laptop A's IP; Specter on Laptop B targets it
console.host=192.168.0.107
```

Comments go on their own line starting with `#`: Java `.properties` files have no end-of-line
comments.

Keep the ports at their defaults unless one is already in use (then change it in *both* copies).
The lab range must be private or loopback and `/16` or narrower. Anything else makes every
role refuse to start. That is `ScopeGuard`, by design.

## 5. Copy the files to Laptop B

Put these four files in one folder on Laptop B (USB stick, shared drive, whatever is handy):

```
mirage.jar          (from Laptop A's target\ folder)
run-attacker.bat    (or run-attacker.sh on macOS/Linux)
mirage.properties
```

On Laptop A, the scripts in the repo root find `target\mirage.jar` on their own. You can also
copy `mirage.jar`, `run-defender.bat` and `mirage.properties` into a folder of their own.

## 6. Firewall rules (run these yourself, as administrator)

Both apps listen for incoming connections, so each laptop's firewall has to let the other one
in. The rules below are limited to the lab range and the Private profile. Swap
`192.168.0.0/24` for your own range.

**Laptop A (defender)**: open an **admin PowerShell** and allow the decoys in:

```powershell
New-NetFirewallRule -DisplayName "Mirage defender (lab)" -Direction Inbound -Protocol TCP -LocalPort 8081,8021,8023 -RemoteAddress 192.168.0.0/24 -Profile Private -Action Allow
```

**Laptop B (attacker)**: open an **admin PowerShell** and allow the counter-watch in:

```powershell
New-NetFirewallRule -DisplayName "Mirage counter-watch (lab)" -Direction Inbound -Protocol TCP -LocalPort 8080 -RemoteAddress 192.168.0.0/24 -Profile Private -Action Allow
```

The Console port 8443 does not need opening: in `defender` mode the Sensor runs on the same
laptop and connects over `127.0.0.1`.

The first time Java listens on a port, Windows may show a *Windows Security Alert* for Java.
Tick **Private networks** only and click **Allow**. If someone clicked *Cancel* on that prompt
in the past, Windows added a **block** rule for `java.exe`, and a block rule beats any allow
rule. Check for one like this:

```powershell
Get-NetFirewallRule | Where-Object { $_.DisplayName -like '*Java*' -and $_.Action -eq 'Block' } | Format-Table DisplayName, Profile, Enabled
```

If any show up, disable them for the demo (`Disable-NetFirewallRule -DisplayName "<name>"`,
admin) and re-enable them afterwards.

**After the demo**, remove the rules:

```powershell
Remove-NetFirewallRule -DisplayName "Mirage defender (lab)"        # Laptop A
Remove-NetFirewallRule -DisplayName "Mirage counter-watch (lab)"   # Laptop B
```

macOS: allow Java when the application-firewall prompt appears. Linux with ufw:
`sudo ufw allow from 192.168.0.0/24 to any port 8081,8021,8023 proto tcp` on A,
`sudo ufw allow from 192.168.0.0/24 to any port 8080 proto tcp` on B.

## 7. Start and test the connection

1. **Laptop A**: run `run-defender.bat`. Look for these lines:
   - `This host's LAN address: 192.168.0.107  (on the attacker laptop set console.host=...)`.
     It should match `console.host`. If it says `No LAN address inside the lab range`, then
     `lab.cidrs` doesn't cover this laptop's Wi-Fi address. Fix the range (step 4).
   - `Decoys listening: http-login:8081, ftp:8021, telnet:8023`
   - `[link] connected to Console at 127.0.0.1:8443 as sensor-...`
   - `Auto-response: OFF`. The dashboard opens and waits for the operator.
2. **Laptop B**: test that it can reach the decoy before starting anything:
   ```powershell
   Test-NetConnection 192.168.0.107 -Port 8081      # TcpTestSucceeded : True
   ```
3. **Laptop B**: run `run-attacker.bat`. Specter opens with the target already set to
   Laptop A, plus a *counter-watch (incoming)* window.
4. **Laptop A** (optional): check the reverse direction with
   `Test-NetConnection 192.168.0.112 -Port 8080` (Laptop B's IP).

## 8. Quick rehearsal

| Laptop B (Specter) | Laptop A (Console) |
| --- | --- |
| *Recon scan* → shell ends `SCAN COMPLETE` | threat popup `PROBE DETECTED` |
| *Honeytoken replay* → `ACCESS GRANTED` (it's the decoy's fake admin page) | popup `COMPROMISED` |
| | operator clicks **Block** → op shell: ScopeGuard → broadcast → Sensor ACK; popup `NEUTRALISED` |
| *Honeytoken replay* again → `CONNECTION BLOCKED` | |
| | operator clicks **Counter-attack** or **Simultaneous counter** |
| counter-watch shows `INCOMING` | op shell shows each counter thread |

Tick **Auto-response** on the dashboard to have the Console respond instantly instead.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `Refusing to start: lab range ...` | `lab.cidrs` must be private/loopback and `/16` or narrower. |
| Specter says `REFUSED — OUTSIDE LAB RANGE` | The target IP isn't in `lab.cidrs`. Fix the range or `console.host`. |
| Specter connections time out | Firewall on A (step 6), a Java block rule, a Public network profile, or Wi-Fi client isolation (use a hotspot). |
| Console prints the wrong LAN address | It prefers an address inside `lab.cidrs`. Check that the range matches your Wi-Fi network. |
| `counter-watch could not bind :8080` | Something else uses 8080. Set `counterwatch.port` to a free port in **both** files. |
| `Defender not started: the Console can't listen on port 8443` | Another Console or defender is still running (an old window). Close it and start again, or change `console.port` in **both** files. |
| `Defender stopped: ... decoy can't listen on port ...` | Something else uses that decoy port (often an old defender). Close it, or change that `decoy.*.port` (and the firewall rule in step 6). |
| `[link] Console at ... not reachable; retrying` on A | The Console isn't up yet or `console.port` differs. The Sensor keeps retrying and buffers events, and connects as soon as the Console is listening. |
| `mirage.jar not found` | Build it (step 3) or copy it next to the script. |

## Safety

The range in `mirage.properties` is the only thing either side can touch. Every Specter attack
and every Console response or counter goes through `ScopeGuard`, which refuses any address
outside it. The decoy's "admin page" is fixed text with nothing real behind it. The
counter-watch only accepts and logs; it never sends anything back.
