# Mirage — Demo Script

A self-contained demonstration on one machine (or a small lab). All traffic stays on `127.0.0.1`
(or the configured lab range); `ScopeGuard` refuses anything else.

## Build

```bash
mvn -q -DskipTests package   # or: mvn test  (to also run the suite)
```

## Run (three processes)

**1. Console (blue team)** — opens the live dashboard, listens on 8443, persists to `./data/mirage`:
```bash
java -cp target/classes com.mirage.console.ConsoleMain
```

**2. Sensor** — starts the decoys (http-login:8081, ftp:8021, telnet:8023) and the bait-file watcher
(`./bait`), connects to the Console, sends heartbeats:
```bash
java -cp target/classes com.mirage.sensor.SensorMain
```

**3. Specter (red team)** — opens the attack console (target `127.0.0.1`, port `8081`):
```bash
java -cp target/classes com.mirage.specter.SpecterMain
```

## The story to show

1. **Recon scan** — in Specter pick *Recon scan* and Launch. The dashboard fills with connection
   alerts across the decoys, and a *Port / service scan* (T1046) alert appears.
2. **Brute force / spray** — pick *Password spray* (or *Brute force*) and Launch. A *Password spraying*
   (T1110.003) or *Credential brute-force* (T1110) alert appears (HIGH), and the Console broadcasts a
   tarpit / feed-fakes response to the Sensors.
3. **Honeytoken** — pick *Honeytoken replay* and Launch. A **CRITICAL** *Stolen credentials used
   (honeytoken)* (T1078) alert appears and the Console broadcasts a **network-wide BLOCK**. Launch any
   campaign again: the source is now dropped by the decoys.
4. **Bait file** — edit or delete any file inside `./bait`. A **CRITICAL** *Bait file tampered* (T1485)
   alert appears.
5. **Tamper-evidence** — click **Verify ledger** on the dashboard: it reports *verified*. (If a stored
   alert had been altered, it would report *BROKEN*.)

Use the **stealth slider** in Specter to switch between fast-and-loud and low-and-slow.

## Safety

Everything is confined to the lab range by `ScopeGuard`. Specter refuses any target outside the
authorised range, and the Console only ever dispatches responses for in-range sources. Mirage never
touches external systems. Run it on isolated host-only VMs or a lab VLAN.
