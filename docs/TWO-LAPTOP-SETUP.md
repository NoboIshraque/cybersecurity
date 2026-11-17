# Two-laptop setup: step by step

This guide takes you from two ordinary laptops to a working Mirage demo, one step at a time.
Do the steps **in order** and do not skip the checks: each one tells you what you should see
before you move on. Plan for **30–45 minutes the first time** (most of it is installing Java and
the first build) and about 5 minutes on later runs.

**The two roles**

| | Laptop A: **defender** | Laptop B: **attacker** |
| --- | --- | --- |
| Runs | the **Console** (dashboard) + the **Sensor** (the decoys), in one program | **Specter** (attack simulator) + its **counter-watch** window |
| Started with | `run-defender.bat` (Windows) or `run-defender.sh` (macOS/Linux) | `run-attacker.bat` or `run-attacker.sh` |
| Needs | **Java JDK 17+**, the whole repository, internet for the first build | **Java 17+** only (no Maven, no repository): 3 files copied from A |
| Lets in (firewall) | TCP **8081, 8021, 8023** (the decoys) | TCP **8080** (the counter-watch) |

```
   Laptop B (attacker)                                   Laptop A (defender)
 ┌──────────────────────┐   attacks on 8081/8021/8023  ┌───────────────────────────┐
 │ Specter              │ ───────────────────────────▶ │ Sensor (3 decoys)         │
 │                      │                              │   │ 127.0.0.1:8443 (local) │
 │ counter-watch :8080  │ ◀─────────────────────────── │ Console + dashboard       │
 └──────────────────────┘   counter-attack on 8080     └───────────────────────────┘
          └────────────── same Wi-Fi / hotspot, e.g. 192.168.0.0/24 ──────────────┘
```

> **The commands below are for Windows PowerShell** unless marked otherwise. macOS/Linux
> versions follow each Windows block. Nothing in this guide changes a system setting on its
> own: you run every command yourself.

> **Fallback:** if the network won't cooperate on the day, the whole demo still runs on **one**
> laptop. See [Fallback: one laptop](#fallback-one-laptop) at the end.

---

## Your fill-in sheet

You will need three values again and again. Write them down as you find them (steps 6 and 7):

| Value | Example used in this guide | **Yours** |
| --- | --- | --- |
| Laptop A's IP address (defender) | `192.168.0.107` | ______________ |
| Laptop B's IP address (attacker) | `192.168.0.112` | ______________ |
| Lab range | `192.168.0.0/24` | ______________ |

**Everywhere this guide shows `192.168.0.107`, `192.168.0.112` or `192.168.0.0/24`, type your
own value instead.**

---

## Part 1: Prepare Laptop A (defender)

You can do Part 1 at home, the day before. It needs internet.

### Step 1: Install Java on Laptop A (a JDK, version 17 or newer)

Laptop A builds the program, so it needs a **JDK** (Java Development Kit), not just a runtime.

1. Open a PowerShell window (Start menu → type `powershell` → Enter) and run:
   ```powershell
   java -version
   javac -version
   ```
2. **If both print version 17 or higher** (e.g. `openjdk version "21.0.5"` and `javac 21.0.5`),
   Java is ready. Go to step 2.
3. **If either says "not recognized", or shows a version below 17:**
   1. Go to **https://adoptium.net**, download **Temurin 21 (LTS)**, the **JDK**, the **`.msi`**
      installer for Windows x64 (macOS: the `.pkg`).
   2. Run the installer. On the *Custom Setup* page, set **"Add to PATH"** and **"Set JAVA_HOME
      variable"** to *"Will be installed on local hard drive"*.
   3. **Close every PowerShell window and open a new one** (old windows don't see the new PATH),
      then run `java -version` and `javac -version` again.

macOS/Linux: same commands in a Terminal. On Linux you can also install it with the package
manager, e.g. `sudo apt install openjdk-21-jdk`.

### Step 2: Put the project on Laptop A

Choose **one** of these:

- **With Git:** in PowerShell,
  ```powershell
  cd $HOME
  git clone https://github.com/NoboIshraque/cybersecurity.git Mirage
  cd Mirage
  ```
- **Without Git:** on the repository's GitHub page click **Code → Download ZIP**, right-click
  the downloaded ZIP → **Extract All…**, and extract it to a short path such as `C:\Mirage`.
  Then in PowerShell: `cd C:\Mirage` (adjust if the extracted folder has a different name: it
  is the folder that contains `pom.xml`, `mvnw.cmd` and `run-defender.bat`).

**Check:** `dir run-defender.bat, mvnw.cmd, pom.xml` lists all three files. From now on, "the
project folder" means this folder, and every Laptop A command runs **inside it**.

> **Tip, on both laptops:** to open PowerShell already inside a folder, open the folder in File
> Explorer, click the address bar, type `powershell` and press Enter.

### Step 3: Build the program (once, while online)

In PowerShell, inside the project folder:

```powershell
.\mvnw.cmd -DskipTests package
```

macOS/Linux: `./mvnw -DskipTests package` (if it says *permission denied*, run
`chmod +x mvnw` first).

- The first run downloads Maven and the libraries (a few minutes, needs internet). Later runs
  are offline and fast.
- **Check:** it ends with **`BUILD SUCCESS`**, and `dir target\mirage.jar` shows a file of about
  **5 MB**.
- Optional: `.\mvnw.cmd test` runs the unit tests (all should pass).
- If it fails with *"JAVA_HOME is not defined correctly"*, redo step 1 (JDK, *Set JAVA_HOME*)
  and open a new PowerShell window.

### Step 4 (recommended): Try it on Laptop A alone first

This proves the program works **before** any network is involved.

1. Make sure there is **no** `mirage.properties` in the project folder yet (`dir mirage.properties`
   should say it can't find it). Without one, everything runs on `127.0.0.1`.
2. In PowerShell window 1, in the project folder: `.\run-defender.bat`. The Mirage dashboard
   opens. If a *Windows Security Alert* about Java appears, tick **Private networks** only and
   click **Allow**.
3. Open a **second** PowerShell window in the same folder: `.\run-attacker.bat`. The Specter
   window opens with the target `127.0.0.1`.
4. In Specter: Campaign **Recon scan** → **Launch**. A shell ends with `SCAN COMPLETE — 3 OPEN`,
   and the dashboard shows a threat popup **PROBE DETECTED**.
5. Close all Mirage windows (Specter, dashboard, shells). If a PowerShell window is still busy,
   press **Ctrl+C** in it.
6. Delete the test data so the real run starts clean:
   ```powershell
   Remove-Item -Recurse -Force data
   ```

If this step fails, fix it now (see [Troubleshooting](#troubleshooting)): the network can't
help a program that doesn't start.

---

## Part 2: Install Java on Laptop B (attacker)

### Step 5: Install Java on Laptop B (version 17 or newer)

Laptop B only **runs** the program, so a JRE or a JDK both work, and it needs nothing else.

1. In PowerShell: `java -version`.
2. If it prints 17 or higher, you're done. Otherwise install **Temurin 21** from
   **https://adoptium.net** exactly as in step 1 (JDK or JRE, tick *Add to PATH*), then open a
   **new** PowerShell window and check `java -version` again.

---

## Part 3: The network (do this where you will demo)

### Step 6: Connect both laptops to the same network and find their IP addresses

1. **Pick the network.** Best: a **phone hotspot** (both laptops join it). Home Wi-Fi also
   works. University, office and hotel Wi-Fi often use *client isolation*, which silently blocks
   laptop-to-laptop traffic. If the connection test in step 13 fails there, switch to the hotspot.
2. **Connect both laptops** to that network. Turn **off any VPN** on both.
3. **Windows only: make the network "Private" on both laptops.** The firewall rules in step 10
   only apply to Private networks. In an **admin** PowerShell (Start → type `powershell` →
   right-click **Windows PowerShell** → **Run as administrator**):
   ```powershell
   Get-NetConnectionProfile
   ```
   Look at the entry for your Wi-Fi. If `NetworkCategory` is `Public`, change it (use the
   `InterfaceAlias` shown, usually `Wi-Fi`):
   ```powershell
   Set-NetConnectionProfile -InterfaceAlias "Wi-Fi" -NetworkCategory Private
   ```
   Or by mouse: Settings → Network & internet → Wi-Fi → *your network* → **Private network**.
4. **Find each laptop's IP address.** On each laptop, in PowerShell:
   ```powershell
   ipconfig
   ```
   Find the block named **`Wireless LAN adapter Wi-Fi:`** (or `Ethernet adapter Ethernet:` if
   that laptop is on a cable) and read the **`IPv4 Address`** line:
   ```
   Wireless LAN adapter Wi-Fi:
      IPv4 Address. . . . . . . . . . . : 192.168.0.107     ← this one
      Subnet Mask . . . . . . . . . . . : 255.255.255.0
   ```
   **Ignore** adapters called *VirtualBox Host-Only*, *vEthernet (WSL / Default Switch)*,
   *VMware*, *Bluetooth* or a VPN; and ignore any address starting with `169.254.` (that means
   "not connected").

   macOS: `ipconfig getifaddr en0` (prints just the address). Linux: `hostname -I` (the first
   address) or `ip -4 addr`.
5. **Write both addresses on the fill-in sheet**: Laptop A's and Laptop B's.

### Step 7: Work out the lab range

The lab range is the **only** set of addresses Mirage may touch. It must contain **both**
laptops: Laptop A so Specter may attack it, and Laptop B so the Console may block it and
counter-attack it.

Compare the two addresses:

| If the two addresses… | Lab range | Example |
| --- | --- | --- |
| share the **first three numbers** (the usual case) | first three numbers + `.0/24` | A `192.168.0.107`, B `192.168.0.112` → **`192.168.0.0/24`** |
| iPhone hotspot (`172.20.10.x`) | `172.20.10.0/24` | A `172.20.10.2`, B `172.20.10.3` → **`172.20.10.0/24`** |
| share only the **first two numbers** | first two numbers + `.0.0/16` | A `10.12.4.20`, B `10.12.7.9` → **`10.12.0.0/16`** |
| share fewer than two numbers | they are not on the same network: go back to step 6 | |

Rules that Mirage enforces (it refuses to start otherwise; that is `ScopeGuard`, by design):
the range must be **private** (inside `10.x.x.x`, `172.16–31.x.x`, `192.168.x.x`) or loopback
(`127.x.x.x`), and **`/16` or narrower** (`/16`, `/24`, `/28` … are fine; `/8` is not).

Write the lab range on the fill-in sheet.

### Step 8: Create `mirage.properties` on Laptop A

This small text file tells both laptops the lab range and where Laptop A is. **The same file
is used on both laptops.**

1. In PowerShell, in the project folder on Laptop A:
   ```powershell
   Copy-Item mirage.properties.example mirage.properties
   notepad mirage.properties
   ```
   (macOS/Linux: `cp mirage.properties.example mirage.properties`, then open it in any text
   editor.)
2. In Notepad, find these two lines and change **only the values** to yours from the fill-in
   sheet. Leave everything else as it is:
   ```properties
   # Your network's range (+ loopback for local tests)
   lab.cidrs=192.168.0.0/24,127.0.0.1/32
   # Laptop A's IP; Specter on Laptop B targets it
   console.host=192.168.0.107
   ```
   - `lab.cidrs` = **your lab range**, then `,127.0.0.1/32` (keep that part).
   - `console.host` = **Laptop A's** IP address (not Laptop B's).
   - No spaces inside the values. **Do not add a comment at the end of a line**: comments go on
     their own line, starting with `#`.
   - Leave the port lines (`console.port`, `decoy.*.port`, `counterwatch.port`) at their defaults.
3. Save (**Ctrl+S**) and close Notepad.
4. **Check** the file name and content:
   ```powershell
   dir mirage.properties*
   Select-String -Path mirage.properties -Pattern '^lab.cidrs|^console.host'
   ```
   You must see exactly **`mirage.properties`**. If you see `mirage.properties.txt`, Notepad
   added `.txt`: fix it with `Rename-Item mirage.properties.txt mirage.properties`. The
   `Select-String` lines must show your values.

> The file must sit in the **project folder** (next to `run-defender.bat`): the scripts read
> `mirage.properties` from their own folder. A `mirage.properties` is ignored by Git, so your
> addresses are never committed.

### Step 9: Copy the files to Laptop B

Laptop B needs exactly **three files** in **one folder**.

1. On Laptop A, in PowerShell in the project folder, gather them into a folder on the Desktop:
   ```powershell
   $dest = Join-Path ([Environment]::GetFolderPath('Desktop')) 'mirage-attacker'
   New-Item -ItemType Directory -Force $dest | Out-Null
   Copy-Item target\mirage.jar, run-attacker.bat, run-attacker.sh, mirage.properties $dest
   dir $dest
   ```
   You should see `mirage.jar`, `mirage.properties`, `run-attacker.bat` and `run-attacker.sh`
   (the `.sh` is for a macOS/Linux attacker; a Windows attacker ignores it).
2. Copy the whole **`mirage-attacker`** folder to Laptop B (USB stick, shared drive, cloud
   drive, whatever is handy). Put it somewhere simple, e.g. the Desktop or `C:\mirage-attacker`.
3. **On Laptop B, check** (PowerShell inside that folder): `dir` lists `mirage.jar` (about 5 MB),
   `mirage.properties` and `run-attacker.bat`.
4. macOS/Linux attacker only: copying via USB often removes the "executable" flag, so run
   `chmod +x run-attacker.sh` once (or start it with `sh run-attacker.sh`).

> **If you change `mirage.properties` later** (e.g. the IPs change on another network), change
> it on Laptop A **and** copy it to Laptop B again. Both copies must match.

### Step 10: Open the firewall (as administrator, on both laptops)

Each laptop listens for connections from the other, so each firewall must let the other one
in. These rules only allow your lab range, and only on Private networks. **Replace
`192.168.0.0/24` with your lab range.**

**On Laptop A (defender)**, in an **admin** PowerShell: let the attacker reach the decoys:

```powershell
New-NetFirewallRule -DisplayName "Mirage defender (lab)" -Direction Inbound -Protocol TCP -LocalPort 8081,8021,8023 -RemoteAddress 192.168.0.0/24 -Profile Private -Action Allow
```

**On Laptop B (attacker)**, in an **admin** PowerShell: let the counter-attack reach the
counter-watch:

```powershell
New-NetFirewallRule -DisplayName "Mirage counter-watch (lab)" -Direction Inbound -Protocol TCP -LocalPort 8080 -RemoteAddress 192.168.0.0/24 -Profile Private -Action Allow
```

**On both laptops, check for an old "block Java" rule.** If someone ever clicked *Cancel* on a
Windows Security Alert for Java, Windows added a **block** rule, and a block rule beats any
allow rule. In the admin PowerShell:

```powershell
Get-NetFirewallRule | Where-Object { $_.DisplayName -like '*Java*' -and $_.Action -eq 'Block' } | Format-Table DisplayName, Profile, Enabled
```

- No output: good, nothing to do.
- Any rows: disable each one for the demo, and write its name down so you can turn it back on
  afterwards (step 18):
  ```powershell
  Disable-NetFirewallRule -DisplayName "<the name shown>"
  ```

The Console port **8443 needs no rule**: the defender's Sensor runs on the same laptop and
connects over `127.0.0.1`.

**macOS:** if the firewall is on (System Settings → Network → Firewall), a prompt *"Do you want
the application "java" to accept incoming network connections?"* appears the first time
Mirage starts: click **Allow**.
**Linux with ufw:** on A `sudo ufw allow from 192.168.0.0/24 to any port 8081,8021,8023 proto tcp`;
on B `sudo ufw allow from 192.168.0.0/24 to any port 8080 proto tcp`.

---

## Part 4: Start and connect

### Step 11: Start the defender on Laptop A

1. In PowerShell, in the project folder: `.\run-defender.bat`
   (macOS/Linux: `./run-defender.sh`). Keep this window open for the whole demo: it is the log.
2. If a *Windows Security Alert* for Java appears: tick **Private networks** only → **Allow**.
3. **Check the start-up lines in the window** (with the example values):
   ```
   Config: lab=[192.168.0.0/24, 127.0.0.1/32] console=192.168.0.107:8443 decoys[http=8081,ftp=8021,telnet=8023] counterwatch=8080
   This host's LAN address: 192.168.0.107  (on the attacker laptop set console.host=192.168.0.107)
   Auto-response: OFF - the operator responds from the dashboard / threat popups
   Console listening for Sensors on port 8443.
   Decoys listening: http-login:8081, ftp:8021, telnet:8023 (defence-gated).
   [link] connected to Console at 127.0.0.1:8443 as sensor-...
   ```
   | Check | What it means if it's wrong |
   | --- | --- |
   | `Config: lab=[...]` shows **your** range and `console=` shows **A's IP** | the file wasn't found or wasn't saved: step 8 (is it named `mirage.properties`, in this folder?) |
   | `This host's LAN address:` shows **the same IP as `console.host`** | if it shows another IP, fix `console.host` (step 8) and copy the file to B again (step 9) |
   | It says `No LAN address inside the lab range ...` instead | the range doesn't contain this laptop's address: redo step 7 |
   | `Decoys listening: ...` and `[link] connected ...` both appear | if not, see [Troubleshooting](#troubleshooting) |

   The two `[warn]` lines about passwords (`MIRAGE_LEDGER_PASSWORD`, `MIRAGE_DB_PASSWORD`) are
   normal: the README explains these optional settings.
4. The **dashboard** opens. Its status bar reads something like
   `Console at 192.168.0.107:8443 | Alerts: 0 | Sensors connected: 1 | Auto-response: OFF (operator) | Ledger: verified`.
   **`Sensors connected: 1`** is the important part.

### Step 12: Test the connection from Laptop B (before starting Specter)

On Laptop B, in PowerShell (any folder), with **Laptop A's** IP:

```powershell
Test-NetConnection 192.168.0.107 -Port 8081
```

- **`TcpTestSucceeded : True`**: the network works. Go to step 13.
- **`TcpTestSucceeded : False`**: Laptop B can't reach the decoy. Check, in this order: the
  defender is running (step 11); you typed A's IP; the firewall rule on A (step 10); no Java block
  rule on A; the network is *Private* on A (step 6.3); then client isolation (switch to the
  phone hotspot). Don't continue until this says `True`.
- `PingSucceeded : False` alone doesn't matter: Windows blocks ping by default. Only
  `TcpTestSucceeded` counts.

macOS/Linux: `nc -vz 192.168.0.107 8081` should report *succeeded* / *open*.

Optionally test the other two decoys the same way: `-Port 8021` and `-Port 8023`.

### Step 13: Start the attacker on Laptop B

1. In PowerShell, **inside the `mirage-attacker` folder**: `.\run-attacker.bat`
   (macOS/Linux: `./run-attacker.sh`).
2. If a *Windows Security Alert* for Java appears: tick **Private networks** only → **Allow**.
3. **Check:** two windows open:
   - **"Specter — attack simulator (lab range only)"**, with **Target:** already showing
     **Laptop A's IP** and **port** `8081`. If it shows `127.0.0.1`, the `mirage.properties` is
     missing from this folder (step 9).
   - **"Specter — counter-watch (incoming)"**, waiting. (If the terminal says
     `counter-watch could not bind :8080`, see [Troubleshooting](#troubleshooting).)

### Step 14: Test the reverse direction (from Laptop A)

On Laptop A, in a **new** PowerShell window, with **Laptop B's** IP:

```powershell
Test-NetConnection 192.168.0.112 -Port 8080
```

- **`TcpTestSucceeded : True`**: counter-attacks will reach Laptop B. Laptop B's counter-watch
  window logs this test as an `INCOMING` connection. That is expected: it logs every
  connection, and this one was yours.
- **`False`**: check that Specter is running on B (step 13), the firewall rule on B (step 10), no
  Java block rule on B, and that the network is *Private* on B.

**The setup is complete.** Both directions work.

---

## Part 5: Rehearse, reset, and tidy up

### Step 15: Quick rehearsal

Do this once to see every part work across the two laptops. The full presenter script with
talking points is [`DEMO.md`](../DEMO.md).

| # | On Laptop B (Specter): choose the **Campaign**, click **Launch** | What you should see |
| --- | --- | --- |
| 1 | **Recon scan** | B: the shell ends `SCAN COMPLETE — 3 OPEN`. A: a threat popup for **B's IP**, **PROBE DETECTED** |
| 2 | **Password spray**, then **Brute force** | B: tries scroll by, ending `NO ACCESS`. A: the popup turns **UNDER ATTACK** |
| 3 | **Honeytoken replay** | B: `ACCESS GRANTED` (it is the decoy's fake admin page). A: the popup turns **COMPROMISED** |
| 4 | *(on A: click **Block** in the popup)* | A: an op shell shows ScopeGuard → broadcast → Sensor ACK; the popup turns **NEUTRALISED** |
| 5 | **Honeytoken replay** again | B: **CONNECTION BLOCKED** |
| 6 | *(on A: click **Counter-attack**, then **Simultaneous counter**)* | A: a counter shell, one pane per thread. B: the counter-watch window shows **INCOMING COUNTER-ATTACK** |
| 7 | Type `8.8.8.8` in **Target**, **Recon scan** | B: **REFUSED — OUTSIDE LAB RANGE** (ScopeGuard; nothing leaves the laptop) |

Tick **Auto-response** on the dashboard if you want the Console to respond on its own instead
of waiting for the operator.

### Step 16: Reset before the audience arrives

The dashboard reopens with every alert saved by earlier runs, so start the real demo clean:

1. Close all Mirage windows on **both** laptops (press **Ctrl+C** in any PowerShell window that
   is still busy).
2. On Laptop A, in the project folder:
   ```powershell
   Remove-Item -Recurse -Force data
   ```
3. Start again: step 11 on A (`.\run-defender.bat`), then step 13 on B (`.\run-attacker.bat`).
   The feed and the map start empty.

Also before the audience: plug both laptops in, and stop them from sleeping (sleep or a Wi-Fi
power-saving mode drops the connection).

### Step 17: Day-of-demo checklist (when steps 1–14 were already done once)

If you already did the full setup, on the day you only need this:

1. Join both laptops to the network (hotspot), no VPN, network **Private** on both.
2. `ipconfig` on both. **Did the IPs change?** (Different network or hotspot → usually yes.)
   - **Same IPs:** nothing to edit.
   - **Changed:** redo step 7 (lab range), step 8 (edit `mirage.properties` on A), step 9 (copy it
     to B), and if the range changed, recreate the firewall rules (step 10: remove the old ones
     first, as in step 18).
3. A: `.\run-defender.bat`, check the start-up lines (step 11).
4. B: `Test-NetConnection <A's IP> -Port 8081` → `True` (step 12).
5. B: `.\run-attacker.bat` (step 13). A: `Test-NetConnection <B's IP> -Port 8080` → `True` (step 14).
6. Close both, delete `data` on A, start both again (step 16).

### Step 18: After the demo, undo the firewall changes

In an **admin** PowerShell:

```powershell
Remove-NetFirewallRule -DisplayName "Mirage defender (lab)"        # on Laptop A
Remove-NetFirewallRule -DisplayName "Mirage counter-watch (lab)"   # on Laptop B
```

If you disabled a Java block rule in step 10, turn it back on:
`Enable-NetFirewallRule -DisplayName "<the name you wrote down>"`. If you switched a network to
Private only for the demo, you can switch it back:
`Set-NetConnectionProfile -InterfaceAlias "Wi-Fi" -NetworkCategory Public`.

Linux with ufw: `sudo ufw delete allow from 192.168.0.0/24 to any port 8081,8021,8023 proto tcp`
on A, and the same with `port 8080` on B.

---

## Alternative: flags instead of `mirage.properties`

Every value in the file can also be given on the command line, after the script name. Flags
override the file. For example, on Laptop B with no `mirage.properties`:

```powershell
.\run-attacker.bat --lab-cidr "192.168.0.0/24,127.0.0.1/32" --console-host 192.168.0.107
```

and on Laptop A:

```powershell
.\run-defender.bat --lab-cidr "192.168.0.0/24,127.0.0.1/32"
```

Keep the quotes around the range: without them PowerShell splits it at the comma. `java -jar mirage.jar` with no arguments lists every role and flag (`--console-port`,
`--http-port`, `--ftp-port`, `--telnet-port`, `--counterwatch-port`, `--bait-dir`,
`--sensor-id`). A mistyped flag is refused. The file is easier for a demo, because you type
the values once.

## Fallback: one laptop

If the network won't cooperate, run both sides on Laptop A:

1. Move `mirage.properties` out of the project folder (or rename it), so the defaults
   (`127.0.0.1`) apply.
2. PowerShell window 1: `.\run-defender.bat`. PowerShell window 2: `.\run-attacker.bat`.

Everything in [`DEMO.md`](../DEMO.md) works the same; the source shown everywhere is `127.0.0.1`.
(No firewall rules are needed for this.)

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `java` (or `javac`) *is not recognized…* | Java isn't installed or isn't on PATH: steps 1 / 5. Open a **new** PowerShell window after installing. |
| `.\mvnw.cmd` fails while downloading | The first build needs internet (step 3). Build at home, or on a network without a proxy. |
| `JAVA_HOME is not defined correctly` | Install a **JDK** with *Set JAVA_HOME* ticked (step 1), then open a new PowerShell window. |
| `mirage.jar not found` | A: build it (step 3). B: `mirage.jar` must be in the same folder as `run-attacker.bat` (step 9). |
| `No mirage.properties here: running the single-machine defaults` | The file is missing from the script's folder, or is named `mirage.properties.txt` (step 8, check 4). |
| `Refusing to start: lab range ...` | `lab.cidrs` must be private/loopback and `/16` or narrower (step 7). Check for typos and stray spaces. |
| `Refusing to start: unknown option ...` | A mistyped flag after the script name. Check the spelling (see *Alternative: flags*). |
| Specter says `REFUSED — OUTSIDE LAB RANGE` | The target IP isn't in `lab.cidrs`. Fix the range or `console.host` (steps 7–9). |
| Specter says `CONNECTION FAILED (timed out ...)` | Nothing answered within 3 s: a wrong target IP, the firewall on A (step 10), a Java block rule, a Public network profile, or Wi-Fi client isolation (use a hotspot). This is a setup problem, not the defender blocking you: a real block reads `CONNECTION BLOCKED`. |
| Specter says `CONNECTION FAILED (refused ...)` | A answered, but nothing listens on that port: the defender isn't running, or the decoy ports differ between the two `mirage.properties` files. |
| Specter's target shows `127.0.0.1` on Laptop B | `mirage.properties` isn't in the `mirage-attacker` folder, or you started the script from somewhere else. |
| Popups on A show `127.0.0.1` instead of B's IP | You are attacking from Laptop A itself. Run Specter on Laptop B. |
| Console prints the wrong LAN address | It prefers an address inside `lab.cidrs`. Check that the range matches your Wi-Fi network (steps 6–7). |
| `Test-NetConnection` says `False` | See step 12 (A→decoys) or step 14 (counter-watch on B). |
| Counter-attack runs on A but B's counter-watch shows nothing | Firewall on B (step 10), a Java block rule on B, or a Public network on B. Test with step 14. |
| `counter-watch could not bind :8080` | Something else on B uses 8080. Set `counterwatch.port` to a free port (e.g. `8090`) in **both** files, and use that port in B's firewall rule. Specter still runs without it. |
| `Defender not started: the Console can't listen on port 8443` | Another Console or defender is still running (an old window). Close it and start again (check with `Get-Process java`), or change `console.port` in **both** files. |
| `Defender not started: data\mirage is in use: ...` | A defender or Console is still running in this folder, or one was just force-killed (Task Manager, `Stop-Process -Force`). Start-up waits up to 10 s for a dead run's lock to clear. Close the other window and start again; after a force-kill, wait a few seconds and start again. Never run two defenders from one folder. |
| `Defender stopped: ... decoy can't listen on port ...` | Something else uses that decoy port (often an old defender). Close it, or change that `decoy.*.port` in **both** files (and in A's firewall rule, step 10). |
| `[link] Console at ... not reachable; retrying` on A | The Console isn't up yet or `console.port` differs. The Sensor keeps retrying, buffers events, and connects as soon as the Console is listening. |
| Dashboard shows `Sensors connected: 0` | Look for `[link] connected ...` in A's window. If it is missing, close every Mirage window, check `Get-Process java` is empty, and start the defender again. |
| The dashboard opens full of old alerts | Saved from earlier runs: step 16. |
| A double-clicked `.bat` window closes at once | Start it from PowerShell (`.\run-defender.bat`) to read the message, or read it before pressing a key: the script waits on an error. |

## Safety

The range in `mirage.properties` is the only thing either side can touch. Every Specter attack
and every Console response or counter goes through `ScopeGuard`, which refuses any address
outside it. The decoy's "admin page" is fixed text with nothing real behind it. The
counter-watch only accepts and logs; it never sends anything back.

The Console–Sensor link stays on Laptop A: the defender's Console listens on `127.0.0.1` only,
so nothing on the network can pose as a Sensor (there is no firewall rule for 8443, and none is
needed). To run a Sensor on a different machine, set up the lab keystore in
[`certs/README.md`](../certs/README.md); the link is then mutual TLS.
