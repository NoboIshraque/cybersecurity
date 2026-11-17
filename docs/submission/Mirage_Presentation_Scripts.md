# Mirage presentation — speaker scripts

## Part 1 — Project details — Noria
Slides 1–8 · 168 words · about 85 s

**Slide 1 — Mirage (title)**  
Good morning. We are Noria, Nobo and Abir, and our project is Mirage: a deception-based intrusion detection and active defence system, written in Java.

**Slide 2 — 01 Project details**  
I'll start with what Mirage is, and why we built it.

**Slide 3 — Alert fatigue hides real attacks**  
Normal intrusion detection has to find a few attacks among millions of normal events, so it raises many false alarms. Mirage turns this around. It plants decoys that no real user ever touches, so any contact is a high-confidence alert.

**Slide 4 — Three cooperating Java applications**  
It has three Java applications. The Sensor hosts the decoys. The Console classifies, records and responds. And Specter is our own attacker, used for testing and the demo.

**Slide 5 — Architecture: hub and spoke over TLS**  
The Sensor reports every event to the Console over TLS. The Console sends commands back, and the Sensor confirms each one.

**Slide 6 — What Mirage does**  
In short: decoys, ATT&CK classification, a tamper-evident ledger, and scoped active defence.

**Slide 7 — From a decoy touch to a confirmed block**  
For example: an attacker uses a stolen password. The Sensor reports it, the Console marks it critical, records it, and blocks the source.

**Slide 8 — Demo: Mirage in action**  
Here is the whole flow in a short demo. [Click to play the video. It runs 3:20 and is narrated by Nobo; it is not counted in the 90 seconds.]

## Part 2 — Our work from a code perspective — Nobo
Slides 9–20 · 126 words · about 90 s

**Slide 9 — 02 Our work from a code perspective**  
Now, the code.

**Slide 10 — The codebase at a glance**  
About ten thousand lines of Java, with 299 tests.

**Slide 11 — Four class families**  
The code has four families, each behind one abstraction. A new feature is just a new class.

**Slide 12 — Template Method**  
Every decoy shares one connection loop. A new decoy only writes interact.

**Slide 13 — Producer–consumer**  
Network threads only queue events. One engine thread owns all the state, so there are no locks.

**Slide 14 — Strategy: seven detection rules**  
Seven detection rules, each mapped to a MITRE ATT&CK technique.

**Slide 15 — Hash-chained, signed ledger**  
Every alert is hash-chained and signed. Any tampering shows up in red.

**Slide 16 — ScopeGuard on every exit path**  
ScopeGuard checks every attack and every response. Nothing leaves the lab range.

**Slide 17 — Database design**  
Six tables, in an embedded H2 database.

**Slide 18 — Result: the defender's view**  
The defender sees each alert escalate, and every response confirmed.

**Slide 19 — Result: the attacker's view**  
The attacker gets fake access, then gets blocked.

**Slide 20 — Verified by tests**  
All 299 tests and all 28 GUI checks pass.

## Part 3 — Limitations and remarks — Abir
Slides 21–24 · 136 words · about 70 s

**Slide 21 — 03 Limitations and remarks**  
Finally, our limitations, and what we learned.

**Slide 22 — Limitations**  
Mirage is a lab prototype. It watches its own decoys, not real network traffic, and it supports IPv4 only. We still have to rehearse the two-laptop setup on the real network. A few small interface issues remain, and it is not hardened for production: we have not scanned its dependencies, and the counter-attack is for lab demonstration only.

**Slide 23 — Remarks: what we learned**  
What did we learn? Design patterns paid off: every new feature was a new class, not an edit. Giving all shared state one owner removed most race conditions. Honest output made the demo trustworthy. Safety had to be designed in from the start. And tests found what demos missed. Next, we want more decoy types, rules learned from data, and a SIEM export.

**Slide 24 — Thank you**  
Thank you. We're happy to take your questions.

