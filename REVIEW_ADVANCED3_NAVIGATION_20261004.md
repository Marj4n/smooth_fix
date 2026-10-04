# Review Advanced3 run e8c87e4d (2026-10-04)

Advanced4 fixes benchmark navigation and player feedback. Public version remains 1.0.5.

The loaded terrain route was marked passed despite only about 4.6 blocks of net
horizontal movement during its 60-second window (58 observed positions; two movement
success samples). The old controller chased a time-driven straight destination,
held vertical jump/sneak to pursue terrain height, and lacked a collision route.
That validation did not establish meaningful route traversal.

The mandatory village search succeeded: ctov:large/village_beach, 261 candidate
chunks, about 25.8 seconds in the first 10,000-block radius. Ad Astra's oil well
also succeeded. The next automatically selected structure, Alex's Caves abyssal
ruins, was still searching after about 311 seconds and 5,405 candidate chunks.
The server run ended owner_missing after 704.9 seconds: six stages passed and the
seventh interrupted. The supplied client checkpoint still said running and ended
at phase 5, so it does not prove the final disconnect/recovery packet reached the
client. Server recovery comparison passed all fields; both-side recovery was false.
The reports do not establish the underlying cause of the missing/disconnected peer.

Advanced4 uses loaded collision geometry and persistent local ground waypoints,
short native step-jump pulses, stalled-edge avoidance and bounded incremental A*.
It overrides key reads instead of mutating physical/toggle state, and suppresses
vanilla auto-jump only while the benchmark controller is active. Walking stages
use grounded survival with temporary protection. Route success now requires path
distance, maximum displacement and multiple movement samples; a small wall
oscillation is insufficient. Navigation data is retained in the same client file.

HUD/chat show current stage, purpose, preparation/search progress, remaining action
time and next stage. Automatic mod structures run after core coverage, before
save, and have a separate default 90-second search budget. Mandatory village and
manually configured searches retain the expanding 10,000/20,000/30,000/... behavior.

Historical native/headless verification belonged to Advanced4. Current validation
status is in VALIDATION.json; it does not rerun or certify that historical fixture.
The complete graphical modpack, PAL integration, mod movement effects, doors,
swimming and ladders still need direct gameplay verification.
