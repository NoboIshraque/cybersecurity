# Voice-over script — demo video v3 (2:30)

Matches `Mirage demo video v3.mp4` (made by `tools/video/annotate_video.py`). One short line per
annotation: **start each line when its red circle starts to draw**. Lines are kept to about
75 words a minute overall (no line faster than about 1.5 words a second), so there is room to speak
slowly and pause between lines.

Tips for recording:

- Run the video muted and read along; the "On screen" column is what you will see when the line starts.
- If you fall behind, skip a line rather than rushing to catch up.
- Lines that start with "…" finish the line before; let them run on.
- Seconds = how long until the next circle appears.

| Time | Seconds | On screen | Say |
| --- | --- | --- | --- |
| 0:00 | 7.4 | Title card | **This is Mirage. It catches attackers with decoys.** |
| 0:07 | 2.7 | ATTACKER Specter (red team) | **Left, the attacker.** |
| 0:10 | 2.6 | DEFENDER Mirage Console (blue team) | **Right, our defence.** |
| 0:12 | 4.8 | Step 1: Recon scan → Launch | **First, the attacker scans for open ports.** |
| 0:17 | 3.6 | Scan finds 3 open decoy ports | **It finds three decoy ports.** |
| 0:21 | 3.7 | Alert: PROBE DETECTED port scan · T1046 · MEDIUM | **Mirage raises an alert instantly.** |
| 0:24 | 4.0 | Step 2: Brute force → Launch | **Next, it tries guessing passwords.** |
| 0:28 | 3.1 | Every password rejected | **Every guess fails.** |
| 0:31 | 3.4 | Alert: UNDER ATTACK brute force · T1110 · HIGH | **Second alert: brute force.** |
| 0:35 | 5.2 | Step 3: Honeytoken replay → Launch | **Now it replays a stolen password.** |
| 0:40 | 2.8 | Replays a stolen credential… | **And it gets in…** |
| 0:43 | 3.0 | …which was planted bait (a honeytoken) | **But it was bait.** |
| 0:46 | 3.1 | COMPROMISED · T1078 · CRITICAL waiting for the operator | **Critical alert. Our move.** |
| 0:49 | 4.9 | Step 4: operator clicks Tarpit | **We click Tarpit, to slow them down.** |
| 0:54 | 3.0 | ScopeGuard checks the target first | **Safety check first.** |
| 0:57 | 2.8 | Sensor confirms: TARPIT applied | **The sensor confirms.** |
| 1:00 | 2.9 | The attacker's replay is stuck on the line | **The attacker is stuck.** |
| 1:03 | 4.1 | Step 5: operator clicks Feed fake data | **Next, we feed fake data.** |
| 1:07 | 2.1 | Fake data armed on the Sensor | **Fake files ready.** |
| 1:09 | 3.6 | Brute force again → Launch | **The attacker tries again.** |
| 1:12 | 2.8 | The attacker "gets in"… | **They think they're in…** |
| 1:15 | 3.3 | …to fake files: backup.cfg holds another honeytoken | **…but it's all fake.** |
| 1:18 | 3.6 | Step 6: operator clicks Block | **Now we block them.** |
| 1:22 | 2.8 | NEUTRALISED | **Threat neutralised.** |
| 1:25 | 4.0 | Sensor confirms: BLOCK applied | **The sensor confirms the block.** |
| 1:29 | 3.2 | Replay the credential again | **They try once more…** |
| 1:32 | 2.8 | Connection reset: the source is cut off | **…and get cut off.** |
| 1:35 | 4.2 | Step 7: operator clicks Counter-attack (lab range only) | **Now we counter-attack, lab only.** |
| 1:39 | 2.8 | The attacker's laptop sees the counter | **The attacker sees it.** |
| 1:42 | 3.0 | Their own technique, mirrored back | **Their own trick, returned.** |
| 1:45 | 4.5 | Operator clicks Simultaneous counter | **Now, several counters at once.** |
| 1:49 | 3.3 | One pane per thread: 4 methods at once | **Four methods, four threads.** |
| 1:53 | 4.5 | Each arrives as its own connection | **A fixed set, not a flood.** |
| 1:57 | 2.6 | Source 127.0.0.1: CRITICAL, 3 alerts | **Now, the dashboard.** |
| 2:00 | 2.8 | 3 alerts, one per technique | **One alert per technique.** |
| 2:03 | 2.9 | Attacker dossier | **A full attacker profile.** |
| 2:05 | 3.3 | Step 8: Verify ledger | **We verify the ledger.** |
| 2:09 | 3.7 | Hash-chained + signed ledger: verified | **Signed, chained, and verified.** |
| 2:12 | 4.1 | Step 9: type a target outside the lab range: 8.8.8.8 | **Last, an address outside the lab.** |
| 2:17 | 3.6 | 8.8.8.8 → Launch | **The attacker hits Launch.** |
| 2:20 | 4.3 | REFUSED: outside the lab range | **Refused: outside the lab range.** |
| 2:24 | 5.4 | ScopeGuard: nothing leaves the lab range | **Nothing leaves the lab. Thank you.** |

Ends at 2:30 (fade to black).

## Just the lines (for reading aloud)

0:00  This is Mirage. It catches attackers with decoys.  
0:07  Left, the attacker.  
0:10  Right, our defence.  
0:12  First, the attacker scans for open ports.  
0:17  It finds three decoy ports.  
0:21  Mirage raises an alert instantly.  
0:24  Next, it tries guessing passwords.  
0:28  Every guess fails.  
0:31  Second alert: brute force.  
0:35  Now it replays a stolen password.  
0:40  And it gets in…  
0:43  But it was bait.  
0:46  Critical alert. Our move.  
0:49  We click Tarpit, to slow them down.  
0:54  Safety check first.  
0:57  The sensor confirms.  
1:00  The attacker is stuck.  
1:03  Next, we feed fake data.  
1:07  Fake files ready.  
1:09  The attacker tries again.  
1:12  They think they're in…  
1:15  …but it's all fake.  
1:18  Now we block them.  
1:22  Threat neutralised.  
1:25  The sensor confirms the block.  
1:29  They try once more…  
1:32  …and get cut off.  
1:35  Now we counter-attack, lab only.  
1:39  The attacker sees it.  
1:42  Their own trick, returned.  
1:45  Now, several counters at once.  
1:49  Four methods, four threads.  
1:53  A fixed set, not a flood.  
1:57  Now, the dashboard.  
2:00  One alert per technique.  
2:03  A full attacker profile.  
2:05  We verify the ledger.  
2:09  Signed, chained, and verified.  
2:12  Last, an address outside the lab.  
2:17  The attacker hits Launch.  
2:20  Refused: outside the lab range.  
2:24  Nothing leaves the lab. Thank you.  
