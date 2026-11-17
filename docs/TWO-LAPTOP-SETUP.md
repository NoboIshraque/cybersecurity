# Two-laptop setup: step by step

This guide takes you from two ordinary laptops to a working, tested Mirage demo. Do the parts
in order and don't skip the **Check** lines: each one confirms a step worked before you build
on it. If a check fails, the [Troubleshooting](#troubleshooting) table at the end says what to
do.

| | **Laptop A: defender** | **Laptop B: attacker** |
| --- | --- | --- |
| Runs | the Console (dashboard) and the Sensor (decoys) | Specter (attack simulator) and its counter-watch |
| Needs | a Java JDK 17+, the project folder | Java 17+ and 3 files you copy from Laptop A |
| Started with | `run-defender.bat` | `run-attacker.bat` |

**Time:** about 45 minutes the first time, including the full test. After that, a demo day
takes 5 minutes ([Part 12](#part-12-demo-day-quick-start)).

**Written for Windows 10/11.** macOS and Linux differences are at the end
([macOS and Linux](#macos-and-linux)).

**Nothing in this guide changes your system on its own.** Every command is one you type
yourself, and Part 11 undoes the firewall changes.

---

## Before you start

You need:

- the two laptops, each with an **administrator** account (only for the firewall rules);
- **internet on Laptop A** for Parts 1–3 (Java and the build downloads);
- a **phone with a mobile hotspot**, or a home Wi-Fi. University and hotel Wi-Fi often block
  laptop-to-laptop traffic ("client isolation"), so use a hotspot for the demo;
- a **USB stick** (or any way to copy a 5 MB file between the laptops).

Fill in this worksheet as you go. Every later step uses these three values. The examples
are used throughout the guide; **always type your own values instead.**

| Value | Example | Yours | Found in |
| --- | --- | --- | --- |
| Laptop A's IP address | `192.168.0.107` | | Part 5 |
| Laptop B's IP address | `192.168.0.112` | | Part 5 |
| Lab range | `192.168.0.0/24` | | Part 5 |

### How to open PowerShell in a folder

Many steps say "open PowerShell in the project folder". To do that: open the folder in **File
Explorer**, click the **address bar** at the top, type `powershell` and press **Enter**. A blue
or black window opens, and its prompt ends with that folder's path, for example
`PS C:\Users\you\Desktop\cybersecurity>`.

To open an **administrator** PowerShell (needed only in Part 7): press the **Start** button,
type `PowerShell`, right-click **Windows PowerShell**, choose **Run as administrator**, and
click **Yes**. The window's title starts with *Administrator*.

---

## Part 1: Install Java on both laptops

Do this on **Laptop A**, then again on **Laptop B**.

1. Open PowerShell (any folder) and type:
   ```powershell
   java -version
   ```
   If it prints `openjdk version "17..."` or higher (`21`, `25`, ...), Java is already there.
   On **Laptop B** you can then skip to the next laptop. On **Laptop A**, still do step 4 below
   to check `JAVA_HOME`.
2. Otherwise go to **https://adoptium.net**, choose **Temurin 21 (LTS)**, operating system
   **Windows**, architecture **x64**, package **JDK**, and download the **.msi** installer.
3. Run the installer. On the **Custom Setup** page, click the icon next to **Set JAVA_HOME
   variable** and choose **Will be installed on local hard drive**. (*Add to PATH* is already
   on.) Click **Next** and **Install**.
4. **Close every PowerShell window** and open a new one, so it sees the new settings. Then:
   ```powershell
   java -version
   echo $env:JAVA_HOME
   ```

**Check:** `java -version` prints version 17 or newer. On **Laptop A**, `echo $env:JAVA_HOME`
also prints a folder such as `C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot\`. The
build in Part 3 refuses to run without it.
(Laptop B only runs the program, so it doesn't need `JAVA_HOME`.)

---

## Part 2: Get the project onto Laptop A

Pick **one** of these:

- **With Git:** open PowerShell in the folder where you want the project (for example your
  Desktop) and run
  ```powershell
  git clone https://github.com/NoboIshraque/cybersecurity.git
  ```
- **Without Git:** on the GitHub page of the repository, click the green **Code** button →
  **Download ZIP**. Right-click the downloaded ZIP → **Extract All…** → choose your Desktop.

Then open PowerShell **in the project folder** (`cybersecurity`, or `cybersecurity-master`
from a ZIP). From now on, "the project folder" means this folder.

**Check:** in that PowerShell window,
```powershell
dir mvnw.cmd, run-defender.bat, run-attacker.bat, mirage.properties.example
```
lists all four files.

---

## Part 3: Build Mirage on Laptop A

1. In PowerShell in the project folder:
   ```powershell
   .\mvnw.cmd -DskipTests package
   ```
   The first run downloads Maven and the libraries (a few minutes, needs internet). Later
   runs take about 30 seconds.
2. *(Optional, about 3 minutes)* run the unit tests:
   ```powershell
   .\mvnw.cmd test
   ```
   It should end with `Tests run: 299, Failures: 0, Errors: 0, Skipped: 6` and
   `BUILD SUCCESS`.

**Check:** the build ends with `BUILD SUCCESS`, and
```powershell
dir target\mirage.jar
```
shows a file of about 5 MB.

---

## Part 4: Test everything on Laptop A alone

Before you add the network, make sure the program itself runs. With no settings file it uses
the single-machine defaults (`127.0.0.1`).

1. In the project folder, double-click **`run-defender.bat`**. A command window opens and,
   after a few seconds, the **Mirage Console — live** dashboard.
   If a **Windows Security Alert** about Java appears, tick **Private networks** only and
   click **Allow access**.
2. Double-click **`run-attacker.bat`**. Two windows open: **Specter — attack simulator (lab
   range only)** and **Specter — counter-watch (incoming)**.
3. In Specter, check that **Target** says `127.0.0.1`, choose **Campaign: Recon scan** and click
   **Launch**.

**Check:** Specter's shell ends with `SCAN COMPLETE — 3 OPEN`, and on the dashboard a threat
popup appears with an amber **PROBE DETECTED** banner.

4. Close the **Specter** window, then the **dashboard** window. Both programs stop. (The
   alert you just made is wiped in Part 10.)

---

## Part 5: Connect both laptops to the same network

1. Turn on the **phone hotspot** and connect **both** laptops to it over Wi-Fi.
2. On **each** laptop, set the connection to **Private**. In an **administrator** PowerShell:
   ```powershell
   Get-NetConnectionProfile
   ```
   Note the `InterfaceAlias` (usually `Wi-Fi`) and `NetworkCategory`. If it says `Public`:
   ```powershell
   Set-NetConnectionProfile -InterfaceAlias "Wi-Fi" -NetworkCategory Private
   ```
   (Or in Settings: **Network & internet → Wi-Fi → *your network* → Network profile type →
   Private network**.)
3. On **each** laptop, find its IP address. In PowerShell:
   ```powershell
   Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias "Wi-Fi" | Format-Table IPAddress, PrefixLength
   ```
   (Or run `ipconfig` and read **IPv4 Address** under **Wireless LAN adapter Wi-Fi**. Ignore
   adapters named *VirtualBox*, *VMware*, *vEthernet* or a VPN.)
   Write both addresses in the worksheet.
4. Work out the **lab range**: take Laptop A's address, replace the **last** number with `0`,
   and add `/24`.

   | Laptop A's IP | Lab range |
   | --- | --- |
   | `192.168.0.107` | `192.168.0.0/24` |
   | `192.168.43.25` (Android hotspot) | `192.168.43.0/24` |
   | `172.20.10.3` (iPhone hotspot) | `172.20.10.0/24` |

   Write it in the worksheet.

**Check:** both laptops' addresses share the **first three numbers** (e.g. `192.168.0.107` and
`192.168.0.112`), and `Get-NetConnectionProfile` says `Private` on both.
If the first three numbers differ, the laptops are on different networks: connect both to
the same hotspot. (If they are definitely on the same network and only the third number
differs, use the first two numbers and `.0.0/16`, e.g. `10.20.0.0/16`. Mirage accepts
nothing wider than `/16`.)

> Use `Test-NetConnection` (Part 9) to test the connection, not `ping`: Windows blocks ping by
> default, so a failed ping means nothing here.

---

## Part 6: Write the settings file and prepare Laptop B's folder

All of this happens on **Laptop A**, in PowerShell in the project folder.

1. **Create `mirage.properties`.** It needs only two lines (everything else has a default):

   ```properties
   # The lab range: the only addresses Mirage may touch (private, /16 or narrower)
   lab.cidrs=192.168.0.0/24,127.0.0.1/32
   # Laptop A's IP address: Specter on Laptop B attacks this address
   console.host=192.168.0.107
   ```

   Make it with this command. **Replace both example values with yours from the worksheet**,
   and keep the `,127.0.0.1/32`:
   ```powershell
   Set-Content -Path mirage.properties -Encoding ascii -Value "lab.cidrs=192.168.0.0/24,127.0.0.1/32", "console.host=192.168.0.107"
   ```
   (Writing it with a command avoids Notepad saving it as `mirage.properties.txt`. If you
   edit it by hand, put any comment on a line of its own starting with `#`, never after a
   value.)
2. **Check the file:**
   ```powershell
   Get-Content mirage.properties
   ```
   It shows your two lines, with your own range and IP.
3. **Make Laptop B's folder** with the three files it needs:
   ```powershell
   New-Item -ItemType Directory -Force Mirage-attacker
   Copy-Item target\mirage.jar, run-attacker.bat, mirage.properties Mirage-attacker\
   dir Mirage-attacker
   ```
4. Copy the whole **`Mirage-attacker`** folder to the USB stick, then from the USB stick to
   **Laptop B's Desktop**. (Run it from the Desktop, not from the USB stick.)

**Check:** on Laptop B, the `Mirage-attacker` folder on the Desktop contains exactly
`mirage.jar`, `run-attacker.bat` and `mirage.properties`. Both laptops use the **same**
`mirage.properties`.

---

## Part 7: Open the firewall (administrator PowerShell)

Each laptop must let the other one connect in. These rules allow only the lab range, only on
Private networks, only on Mirage's ports. **Replace `192.168.0.0/24` with your lab range.**

1. **Laptop A**, administrator PowerShell (lets Laptop B reach the decoys on 8081, 8021 and 8023):
   ```powershell
   New-NetFirewallRule -DisplayName "Mirage defender (lab)" -Direction Inbound -Protocol TCP -LocalPort 8081,8021,8023 -RemoteAddress 192.168.0.0/24 -Profile Private -Action Allow
   ```
2. **Laptop B**, administrator PowerShell (lets Laptop A's counter-attack reach the counter-watch on 8080):
   ```powershell
   New-NetFirewallRule -DisplayName "Mirage counter-watch (lab)" -Direction Inbound -Protocol TCP -LocalPort 8080 -RemoteAddress 192.168.0.0/24 -Profile Private -Action Allow
   ```
3. **Both laptops:** look for an old rule that **blocks** Java. (It's created when someone
   once clicked *Cancel* on a Java firewall prompt, and a block rule beats any allow rule.)
   ```powershell
   Get-NetFirewallRule | Where-Object { $_.DisplayName -like '*Java*' -and $_.Action -eq 'Block' } | Format-Table DisplayName, Profile, Enabled
   ```
   If it lists anything, disable each one for the demo (and re-enable it in Part 11):
   ```powershell
   Disable-NetFirewallRule -DisplayName "<the name it listed>"
   ```

**Check:** on each laptop,
```powershell
Get-NetFirewallRule -DisplayName "Mirage*" | Format-Table DisplayName, Enabled, Profile, Action
```
shows your rule with `Enabled True`, `Profile Private`, `Action Allow`; and the Java check
prints nothing (or only rules you disabled, showing `Enabled False`).

No rule is needed for port 8443: the Console and Sensor both run on Laptop A and talk over
`127.0.0.1`.

---

## Part 8: Start the defender (Laptop A)

1. In the project folder, double-click **`run-defender.bat`** (or type `.\run-defender.bat` in
   PowerShell there). If a **Windows Security Alert** for Java appears, tick **Private
   networks** only and click **Allow access**.
2. Read the command window. These are the lines that matter (your addresses and names will
   differ):
   ```text
   Config: lab=[192.168.0.0/24, 127.0.0.1/32] console=192.168.0.107:8443 decoys[http=8081,ftp=8021,telnet=8023] counterwatch=8080
   This host's LAN address: 192.168.0.107  (on the attacker laptop set console.host=192.168.0.107)
   Ledger: 0 stored alert(s) loaded, chain intact.
   Auto-response: OFF - the operator responds from the dashboard / threat popups
   Decoys listening: http-login:8081, ftp:8021, telnet:8023 (defence-gated).
   [link] connected to Console at 127.0.0.1:8443 as sensor-<laptop name>-8081
   ```
   Two `[warn]` lines about `MIRAGE_LEDGER_PASSWORD` and `MIRAGE_DB_PASSWORD` are normal for
   the demo. (The README explains these optional passwords.)
3. The dashboard opens. Its status bar at the bottom reads
   `Console at 192.168.0.107:8443 | Alerts: … | Sensors connected: 1 | Auto-response: OFF (operator) | Ledger: verified`.

**Check:**
- the `This host's LAN address:` line shows **the same IP** as `console.host` in your worksheet.
  If it says `No LAN address inside the lab range`, the lab range doesn't match Laptop A's
  Wi-Fi address: go back to Part 5 step 4;
- `Decoys listening` and `[link] connected` both appear;
- the status bar says `Sensors connected: 1`.

Leave the defender running.

---

## Part 9: Test the connection, both ways

1. **Laptop B**, PowerShell (no admin needed). Use Laptop A's IP:
   ```powershell
   Test-NetConnection 192.168.0.107 -Port 8081
   ```
   **Check:** the last line reads `TcpTestSucceeded : True`. (A `WARNING: Ping to ... failed`
   line above it is normal: Windows blocks ping, and only the TCP result counts.)
   If it says `False`, stop here and see [Troubleshooting](#troubleshooting) ("Test-NetConnection says False").
2. **Laptop B:** open the `Mirage-attacker` folder on the Desktop and double-click
   **`run-attacker.bat`**. Allow Java on **Private networks** if Windows asks.
   **Check:** the **Specter — attack simulator** window opens with **Target** already set to
   Laptop A's IP (port `8081`), and a **Specter — counter-watch (incoming)** window opens and
   waits. If Target says `127.0.0.1`, Specter didn't find `mirage.properties`: make sure it is
   in the same folder as `run-attacker.bat`.
3. **Laptop A**, PowerShell. Use Laptop B's IP:
   ```powershell
   Test-NetConnection 192.168.0.112 -Port 8080
   ```
   **Check:** `TcpTestSucceeded : True`. Laptop B's counter-watch logs this as an incoming
   connection, which shows it can hear Laptop A.

Both directions work, so the setup is done. Part 10 tests every feature over this connection.

---

## Part 10: Full system test (rehearsal)

Run through this once, in order, with both laptops side by side. It covers every feature
used in the demo. Tick each row when its **Expected** result appears. If a row fails, see
[Troubleshooting](#troubleshooting), then carry on.

On Laptop A, click **Pipeline shell** on the dashboard first, to watch the Console's own
processing. The threat popup for Laptop B's IP opens by itself at row 1; most defender actions
are buttons on that popup.

| # | Laptop | Do this | Expected |
| --- | --- | --- | --- |
| 1 | B | Campaign **Recon scan** → **Launch** | B: shell ends `SCAN COMPLETE — 3 OPEN`. A: popup for B's IP, amber **PROBE DETECTED** |
| 2 | B | **Password spray** → Launch, then **Brute force** → Launch | B: tries scroll by, ending `NO ACCESS`. A: popup turns orange **UNDER ATTACK** |
| 3 | B | **Honeytoken replay** → Launch | B: `ACCESS GRANTED` (it's the decoy's fake admin page). A: popup turns red **COMPROMISED** |
| 4 | A | Popup → **Tarpit** | A: an operation shell shows `ScopeGuard` → broadcast → `sensor-… confirms: TARPIT … applied` |
| 5 | B | **Honeytoken replay** → Launch | B: **CONNECTION STALLED** (the attacker is held on the line) |
| 6 | A | Popup → **Feed fake data** | A: op shell shows `sensor-… confirms: FEED_FAKE … applied` |
| 7 | B | **Brute force** → Launch | B: **ACCESS GRANTED** on the first guess, and Specter lists fake files (`passwords.csv`, `vpn.key`, `config/backup.cfg`) |
| 8 | A | Popup → **Block** | A: op shell shows the Sensor's ACK; popup turns green **NEUTRALISED** |
| 9 | B | **Honeytoken replay** → Launch | B: **CONNECTION BLOCKED** |
| 10 | A | Popup → **Counter-attack** | A: a counter shell, `in range — mirroring …`. B: counter-watch shows **INCOMING COUNTER-ATTACK** |
| 11 | A | Popup → **Simultaneous counter** | A: the counter shell splits into one pane per counter thread. B: more `INCOMING` lines |
| 12 | A | Popup → **Inspect in dashboard** → **Responses** tab → **Unblock**, then tick **Auto-response** | A: popup back to COMPROMISED; status bar `Auto-response: ON` |
| 13 | B | **Honeytoken replay** → Launch | A: popup turns **NEUTRALISED** with no click (the Console blocked B by itself) |
| 14 | A | Click **Verify ledger** | A: status bar `Ledger: verified` |
| 15 | A | In the project folder, open `bait\config\backup.cfg` in Notepad, add a character, save | A: a CRITICAL **Bait file tampered (possible ransomware)** alert (T1485) |
| 16 | B | Type `8.8.8.8` as Target → **Recon scan** → Launch | B: **REFUSED — OUTSIDE LAB RANGE**. Nothing is sent |
| 17 | B | *(Optional)* tick **All at once (split threads)**, campaign **Full campaign** → Launch | B: the shell splits into one pane per attack thread |

**Check:** all rows pass. You have now tested the whole system over the network. `DEMO.md` is
the same sequence with talking points for the audience.

Before the audience arrives, reset (next section).

### Reset for a clean demo

The dashboard reloads saved alerts at start-up, so the rehearsal's alerts would still be
there. To start from zero:

1. Close the **Specter** window on Laptop B and the **dashboard** window on Laptop A. Both
   programs stop.
2. On **Laptop A**, in PowerShell in the project folder, delete the saved data **and** the bait
   files together:
   ```powershell
   Remove-Item -Recurse -Force data, bait, bait.sha256
   ```
   Delete all three. Deleting only `bait` makes the next start report the missing files as a
   CRITICAL bait-tampering alert. The Sensor plants fresh bait files at its next start.
3. Start the defender on A (Part 8), then the attacker on B (Part 9, step 2).

**Check:** the dashboard status bar says `Alerts: 0`, and the feed and map are empty.

---

## Part 11: After the demo

1. Close the Specter window (B) and the dashboard (A).
2. Remove the firewall rules in an **administrator** PowerShell:
   ```powershell
   Remove-NetFirewallRule -DisplayName "Mirage defender (lab)"        # Laptop A
   Remove-NetFirewallRule -DisplayName "Mirage counter-watch (lab)"   # Laptop B
   ```
3. If you disabled a Java block rule in Part 7, turn it back on:
   `Enable-NetFirewallRule -DisplayName "<its name>"`.
4. *(Optional)* set the hotspot connection back to Public:
   `Set-NetConnectionProfile -InterfaceAlias "Wi-Fi" -NetworkCategory Public`.

---

## Part 12: Demo day quick start

Once Parts 1–10 have worked, a demo day is just:

1. Connect both laptops to the **same hotspot as the rehearsal**. If you use a different
   network, the IPs change: redo Part 5, Part 6 (new `mirage.properties` on **both** laptops)
   and Part 7 (firewall rules with the new range). Even on the same hotspot a laptop can get
   a new address; step 3 catches that.
2. If Part 11 removed the firewall rules, add them again (Part 7).
3. **Laptop A:** `run-defender.bat`. Check `This host's LAN address` matches `console.host`
   and `Sensors connected: 1`.
4. **Laptop B:** `Test-NetConnection <Laptop A's IP> -Port 8081` → `True`, then `run-attacker.bat`.
5. Make sure the feed is empty (otherwise [reset](#reset-for-a-clean-demo)), then follow `DEMO.md`.

---

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `Error: JAVA_HOME not found in your environment` when building | Part 1 step 3 was skipped. Reinstall Temurin with **Set JAVA_HOME variable** on, or set it for this window only: `$env:JAVA_HOME = (Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory)[0].FullName`, then build again. |
| `'java' is not recognized` | Java isn't installed or PowerShell was open during the install. Do Part 1, then open a **new** PowerShell. |
| `.\mvnw.cmd : The term ... is not recognized` | PowerShell isn't in the project folder. Open it there (see [How to open PowerShell in a folder](#how-to-open-powershell-in-a-folder)). |
| `mirage.jar not found` | Laptop A: build it (Part 3). Laptop B: copy `mirage.jar` into the same folder as `run-attacker.bat` (Part 6). |
| A double-clicked `.bat` window says `Mirage exited with an error` | Read the message above that line. It is one of the rows below. |
| `Refusing to start: lab range ... is not a private or loopback range` | `lab.cidrs` has a typo or a public address. It must start with `10.`, `172.16.`–`172.31.` or `192.168.` (Part 5 step 4). |
| `Refusing to start: lab range ... is too broad: prefix must be between /16 and /32` | Use `/24` (or at most `/16`). |
| `No LAN address inside the lab range ...` on Laptop A | The lab range doesn't contain Laptop A's Wi-Fi address. Recheck the IP (Part 5 step 3) and fix `mirage.properties` on **both** laptops. |
| `This host's LAN address` differs from `console.host` | Laptop A's IP changed (new network, or the hotspot was restarted). Put the printed address in `console.host` in **both** files. |
| Specter's Target shows `127.0.0.1` | `mirage.properties` is not next to `run-attacker.bat`, or it was saved as `mirage.properties.txt`. In that folder, run `dir mirage.properties*` to see the real name. |
| `Test-NetConnection` says `False` (or Specter says `CONNECTION FAILED (timed out ...)`) | Something between the laptops blocks the connection, in this order of likelihood: the defender isn't running on A (Part 8); the target IP is wrong; the firewall rule on A is missing or has the wrong range (Part 7); a Java **block** rule (Part 7 step 3); the network is **Public** (Part 5 step 2); the Wi-Fi isolates clients (use the phone hotspot). This is a set-up problem, not Mirage's defence: a real block reads `CONNECTION BLOCKED`. |
| Specter says `CONNECTION FAILED (refused ...)` | Laptop A answered, but nothing listens on that port: the defender isn't running, or the decoy ports differ between the two `mirage.properties` files. |
| Specter says `REFUSED — OUTSIDE LAB RANGE` for Laptop A | Laptop A's IP isn't inside `lab.cidrs`. Fix the range (Part 5 step 4) on both laptops. |
| No **INCOMING** on the counter-watch at Part 10 rows 10–11 | Part 9 step 3 failing: the firewall rule on **B** (Part 7 step 2), a Java block rule on B, or B's network is Public. |
| `counter-watch could not bind :8080` | Another program uses 8080 on B. Add `counterwatch.port=8090` to `mirage.properties` on **both** laptops and use `8090` in B's firewall rule. |
| `Defender not started: the Console can't listen on port 8443` | An older defender is still running. Close its window (or its dashboard) and start again. |
| `Defender not started: data\mirage is in use: ...` | Another defender is running from this folder, or one was just force-killed. Close it, wait a few seconds, start again. Never run two defenders from one folder. |
| `Defender stopped: ... decoy can't listen on port ...` | Another program (often an old defender) uses that port. Close it, or change that `decoy.*.port` on **both** laptops and in A's firewall rule. |
| `[link] Console at ... not reachable (...); retrying` on A | The Console is still starting. Wait. The Sensor reconnects by itself and keeps its events. |
| Old alerts on the dashboard at start-up | Expected after a rehearsal: [reset](#reset-for-a-clean-demo). |
| A **Bait file tampered** alert right at start-up | The `bait` folder was changed or deleted while the defender was closed. [Reset](#reset-for-a-clean-demo) deletes `data`, `bait` and `bait.sha256` together. |

**If the network won't cooperate**, everything still runs on one laptop: delete (or rename)
`mirage.properties` on Laptop A and run `run-defender.bat` and `run-attacker.bat` side by side,
as in Part 4. See *One-laptop run* in `DEMO.md`.

---

## macOS and Linux

The steps are the same, with these differences:

| Step | macOS / Linux |
| --- | --- |
| Part 1 | macOS: install Temurin's `.pkg` from adoptium.net (it sets everything up). Linux: `sudo apt install openjdk-21-jdk` (or your distribution's package). Check with `java -version`. |
| Part 3 | `./mvnw -DskipTests package` (and `./mvnw test`). |
| Part 4, 8, 9 | `./run-defender.sh` and `./run-attacker.sh` in a terminal, in the folder that holds them. |
| Part 5 | Find the IP with `ipconfig getifaddr en0` (macOS) or `ip -4 addr` (Linux). |
| Part 6 | `printf 'lab.cidrs=192.168.0.0/24,127.0.0.1/32\nconsole.host=192.168.0.107\n' > mirage.properties`, then copy `target/mirage.jar`, `run-attacker.sh` and `mirage.properties` for Laptop B. |
| Part 7 | macOS: allow Java when the firewall prompt appears. Linux with ufw: `sudo ufw allow from 192.168.0.0/24 to any port 8081,8021,8023 proto tcp` on A, `sudo ufw allow from 192.168.0.0/24 to any port 8080 proto tcp` on B. |
| Part 9 | Test with `nc -vz 192.168.0.107 8081` instead of `Test-NetConnection`. |
| Reset | `rm -rf data bait bait.sha256` |

---

## Safety

The lab range in `mirage.properties` is the only thing either side can touch. Every Specter
attack and every Console response or counter goes through `ScopeGuard`, which refuses any
address outside it, and no role starts on a range that is public or wider than `/16`. The
decoy's "admin page" is fixed text with nothing real behind it. The counter-watch only accepts
and logs; it never sends anything back.

The Console–Sensor link stays on Laptop A: the defender's Console listens on `127.0.0.1` only,
so nothing on the network can pose as a Sensor (that's why port 8443 needs no firewall rule).
To run a Sensor on a different machine, set up the lab keystore in
[`certs/README.md`](../certs/README.md); the link is then mutual TLS.
