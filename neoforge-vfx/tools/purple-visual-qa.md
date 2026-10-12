# Purple visual QA driver (non-release source)

`src/qa/java/dev/jujutsuvfx/client/PurpleVisualQa.java` is excluded from the normal Gradle main source set. Include it only in an isolated QA build using a temporary Gradle init script. Do not ship this class in the final mod jar. It uses only the normal project/NeoForge dependencies.

The driver refuses all multiplayer connections, published LAN servers, and singleplayer worlds whose displayed name is not exactly `VFX-QA` or prefixed `VFX-QA-`. Create a disposable creative superflat world with that explicit name in the isolated QA instance; do not import user saves.

1. `/vfxqa scene` creates a small striped stone/wood exhibit at Y=80, restores three material walls, places the player at a fixed viewing position, and takes a baseline screenshot. Terrain edits and the creative game mode change are limited to the explicitly named QA world.
2. `/vfxqa purple 10` injects a real `KeyMapping.click` for the use key, holds it for 10 client ticks, then releases it. The item is supplied through the integrated server's inventory update. The driver never directly calls `SkillItem.use`, `LimitlessCast.release`, or skill network handlers.
3. Repeat `/vfxqa scene` then `/vfxqa purple 30` for a medium shot.
4. Repeat `/vfxqa scene` then `/vfxqa purple 80` for a full shot held longer than the maximum charge time. This must remain in phase 0 after 60 ticks, and launch exactly once only after the key is released, with radius 5.
5. `/vfxqa cancel` releases input and stops the driver. `/rift stop` remains the production way to remove an active effect before starting another run.

Keep the game window in the world and do not open screens during a run. Opening a screen cancels the run and releases the use key. A run normally takes charge time plus about 5.5 seconds for flight/afterglow.

Native game screenshots are taken in `RenderFrameEvent.Post` with `Screenshot.grab`, after rendering. Files use `screenshots/vfxqa-<timestamp>-runXX-holdNNN-*.png`. Relevant suffixes are `held-010t`, `held-030t`, `held-056t`, `held-060t`, `held-075t`, `launch-000t`, and `flight-002t/010t/020t`. The final held frame is also captured for every duration.

`logs/latest.log` contains `[VFX-QA] SAMPLE`, `NETWORK_LAUNCH`, `ASSERT_FAIL`, and `RESULT` lines. Assertions check one item-sourced session, one server-confirmed launch, no early auto-launch, a 1-to-5 charge radius, full charge remaining held after three seconds, and stable server radius during flight. `RESULT PASS` is an integration check, not a substitute for manually inspecting the reference and game images. The QA code does not assert pixel similarity or measure performance.

Before publishing, compile a clean production build without the init script and inspect the jar to ensure no entries contain `PurpleVisualQa`.

## Client world-time rollback QA

`src/qa/java/dev/jujutsuvfx/client/EffectClockVisualQa.java` is another QA-only event subscriber. It has the same private `VFX-QA` world restriction and never touches input, server time, or saved world time. It inspects the private Gojo domain through QA-only reflection; no production accessor is added.

Recommended combined run:

1. `/rift void 40 10` starts a domain that will remain active for the clock experiment.
2. `/vfxqa_clock` arms the clock experiment; after the command screen closes it counts ordinary, unpaused client ticks.
3. `/vfxqa purple 80` starts the independent item-input driver. Issue this promptly, before the first clock jump.

The clock driver changes only the current client level's raw game time by **-40 at elapsed tick 30**, then **+80 at tick 70**, and finishes at tick 130. `/vfxqa_clock <delayTicks>` accepts 10–200 and moves these events to `delayTicks`, `delayTicks + 40`, and `delayTicks + 100`. `/vfxqa_clock cancel` ends early and restores time. Completion, cancellation, world changes, and logout restore the original client-level timeline; when the integrated server is still running, the driver then restores a fresh authoritative server-time read. The server's clock is never written.

Look for `[VFX-QA-CLOCK] INJECT`, `RAW_CORRECTION`, `SAMPLE`, `ASSERT_FAIL`, `RESULT`, and `RESTORE_SERVER_TIME` in `logs/latest.log`. `RESULT PASS` requires both jumps to occur while at least one inspected effect is live, each immediate before/after injection to preserve the VFX clock and the effect ages, the VFX clock to advance exactly once per ordinary client tick, and the same effect's age to remain monotonic across both ticks and rendered frames. `covered=[purple, void]` (either order) confirms both requested effects were active during injections; a PASS listing only one effect covers only that effect and should be followed by the missing case. The check compares identities, so a newly cast effect legitimately starting at age zero is not mistaken for a rewind.

Before publishing, also ensure the release jar contains neither `EffectClockVisualQa` nor its nested `Run` class. The driver does not replace the existing gameplay/visual assertions or claim that server lag itself has been fixed.
