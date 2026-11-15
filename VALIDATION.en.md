# Advanced Virtual Spawners 1.0.0 validation

[Русская версия](VALIDATION.md) · [Audit](AUDIT.en.md)

## 1.0.1 follow-up

- `AVS_AUDIT_FIXES_PASS` verifies that a rejected click preserves progress, an actual input change resets it, and disabling `doMobLoot` preserves energy and progress.
- Follow-up tests cover a compatible item behind a blocked queue entry, native generation for unknown Luck conditions, budget enforcement, and acceptance of ordinary captured entity NBT while rejecting inconsistent data.
- Vanilla command movement preserves exactly one inventory. Rejected item spawns are preserved in dimension data and retried once successfully. The test serializes and restores the queue. Abrupt crashes between saves and movement by every third-party tool remain outside the test coverage.
- The launcher rejected an unrelated existing profile before writing to it. The global Soul Vial limit still requires matching client and server settings.

The measurements below come from the original 1.0.0 validation.

## Coverage

- The offline build and regression checks passed. Seeded tests cover vial thresholds, tier limits, speed rounding, energy and overflow bounds, Beheading and Luck.
- The tier I crafting test accepts four distinct souls and additional vial NBT. An empty vial, a duplicate soul and the wrong crystal are rejected. Success is logged as `AVS_TIER_ONE_RECIPE_PASS`.
- All four enclosed block models and their 16 block textures were checked. The full E2E client rendered the models and the localized tier IV interface. Game checks logged `AVS_GUI_PASS`.
- The full E2E 1.93a client and integrated server loaded the recipes and exercised loot from Minecraft, Thermal Foundation and Twilight Forest. The E2E zombie table gave one carrot and one potato with Luck V. The pack's active zombie table omits iron.
- The dedicated Cleanroom server passed with E2E mods, configs and scripts after removing four client modules from its test copy (Cleanroom Relauncher, Nothirium, Naughthirium and RenderLib). A separate reduced profile with the key mods also passed and confirmed the 64-vial cap and 1,280 RF/t baseline.
- In-world checks cover metadata and NBT separation, stack size, admission limits, Beheading IV, insufficient power, a blocked output, chunk reloading in the reduced profile, restart recovery of pending loot and breaking a machine after reducing the global stack limit.
- Automation checks exercised AE2's inventory adaptor, Ender IO's item transfer into a placed chest, and Thermal Dynamics' insertion method. Forge Energy was charged and spent. Redstone Flux receive simulation returned the expected result.
- Timed loops ran 25 full cycles per machine. Recorded times were 50 ms for one machine and 76 ms for three in the reduced profile, and 51 ms and 93 ms in the E2E server profile. These loops omit the wait between game ticks.

## Reproduction

Build with `build.ps1 -Offline -RuntimeChecks`. Use a **disposable** test profile with no playthrough saves. The test module modifies blocks and item entities in its fixture world.

```powershell
py tools/launch_runtime.py --instance 'path-to-E2E-instance' --java-home 'path-to-JDK-8'
```

For a dedicated test, install Cleanroom 0.6.11-alpha in a separate directory with its accepted `eula.txt`. The reduced profile copies the required mods and omits the Ender IO user recipes that reference absent mods. It preserves the pack's power baseline settings. [Cleanroom server installation](https://github.com/CleanroomMC/Website/blob/main/docs/wiki/end-user-guide/installation/install-server.md)

```powershell
py tools/launch_runtime.py --server-core --run-dir 'path-to-test-server' --instance 'path-to-E2E-instance' --java-home 'path-to-JDK-25'
```

The test server binds to localhost and stops after the checks. Look for `AVS_RUNTIME_PASS` or `AVS_RUNTIME_FAIL` in `logs/latest.log`. A repeat run in the same fixture world verifies persisted state and logs `AVS_RESTART_PASS`.

## Scope

Physical networks with cables, filters, sorters and ME storage were outside these adaptor tests. Immediate forced chunk unloading was verified in the reduced profile. In full E2E the chunk stayed cached after one unload-handler call, so the full-pack check used restart and NBT checks for persistence. See the [audit](AUDIT.en.md) for separately reproduced failure cases and integration risks.
