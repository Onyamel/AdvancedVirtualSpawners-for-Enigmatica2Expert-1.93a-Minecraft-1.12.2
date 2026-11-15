# Advanced Virtual Spawners 1.0.0 audit

[Русская версия](AUDIT.md) · [README](README.en.md) · [Runtime validation](VALIDATION.en.md)

This is the historical 1.0.0 audit. The fixes shipped in 1.0.1 and are described in the [current release notes](RELEASE_NOTES.md). Source links point to the corresponding files in the current project tree.

The audit covered registration, tier I crafting, NBT loading, the machine cycle and power use, loot generation, the pending queue, the container, block removal and automation. The build and test launcher were also reviewed. The standard regression suite and a dedicated Cleanroom 0.6.11-alpha profile with key E2E mods passed. Additional probes ran in a disposable test world. The audit left release code intact.

## Reproduced in game

| Priority | Issue | Observation and proposed fix |
|---|---|---|
| P1 | Cancelling item spawns loses machine contents | On removal, the machine clears its slots and queue. Forge can then cancel insertion of the item entities. A test handler cancelled two item spawns and the block and contents disappeared. Preserve contents when the world rejects an item drop. [SpawnerTile.java](src/main/java/ru/e2e/avs/SpawnerTile.java) |
| P2 | A rejected click erases paid progress | Clicking the Speed slot with flint on the cursor left both items intact but reset progress from 1 to 0. Reset the cycle only after a real input change. [SpawnerMenu.java](src/main/java/ru/e2e/avs/SpawnerMenu.java) · [SpawnerTile.java](src/main/java/ru/e2e/avs/SpawnerTile.java) |
| P2 | The first item can block the entire pending queue | With a full output and room for one diamond in a partial stack, the machine kept two pending entries and stayed in `OUTPUT`, even though the second entry fit. Allow a bounded pass through compatible pending items. [SpawnerTile.java](src/main/java/ru/e2e/avs/SpawnerTile.java) |
| P2 | The machine draws power with `doMobLoot=false` | It spent 1,280 RF and advanced the cycle with loot disabled. The gamerule is only checked during generation. Evaluate it before charging the tick and display the pause reason. [SpawnerTile.java](src/main/java/ru/e2e/avs/SpawnerTile.java) · [VirtualLoot.java](src/main/java/ru/e2e/avs/VirtualLoot.java) |
| P2 | An unknown random condition becomes guaranteed after it passes | A synthetic pool awarded two items on a 50% condition. Luck IV yielded 4,978 successes in 10,000 attempts with ten items per success. The code kept the chance and boosted quantity. Route unknown conditions to native generation before any rolls. [VirtualLoot.java](src/main/java/ru/e2e/avs/VirtualLoot.java) |

## Code and API findings

| Priority | Risk | Trigger |
|---|---|---|
| P1 | No total budget for loot generation per tick | A full cycle of up to 49 kills executes in one tick. Large custom tables, quantity multipliers and unstackable items can create many objects. Native fallback also materializes lists, and the whole queue is serialized. Bound generation work and queue size. The test did not deliberately overload the server. [SpawnerTile.java](src/main/java/ru/e2e/avs/SpawnerTile.java) · [VirtualLoot.java](src/main/java/ru/e2e/avs/VirtualLoot.java) |
| P1 | Checked soul type can differ from reconstructed entity type | Ender IO 5.3.72 can read a vial's declared ID from one field and reconstruct the entity from nested NBT. AVS applies allow and deny lists to the former and checks only the base class after reconstruction. Imported, inconsistent or modified vials can violate the intended restriction. Validate identifier consistency before construction. No mismatch was observed in ordinary E2E vials. [EnderIo.java](src/main/java/ru/e2e/avs/EnderIo.java) |
| P1 | The test launcher can overwrite an unrelated profile | The guard only compares `--run-dir` with the selected source instance. An unrelated existing profile passes, after which the launcher copies configs and overwrites launch settings. The test module can also change blocks and item entities in its fixture world. Require a dedicated tagged directory and validate resolved paths, including redirected directories. No user profiles were changed in testing. [launch_runtime.py](tools/launch_runtime.py) · [RuntimeChecks.java](src/test/java/ru/e2e/avs/RuntimeChecks.java) |
| P2 | NBT-preserving moves can count inventory twice | The inspected Forge command implementation saves NBT and clears the source via `IInventory`. AVS exposes an item capability and independently drops inventory when replaced. NBT copying can conflict with the drop path. Normal breaking was tested. Command moves and Carry On were not run in the full profile. [SpawnerBlock.java](src/main/java/ru/e2e/avs/SpawnerBlock.java) · [SpawnerTile.java](src/main/java/ru/e2e/avs/SpawnerTile.java) |
| P2 | Loot exceptions may leave partial output and hide the cause | `collect` appends to a shared list before finishing, catches `RuntimeException` and warns once per identifier. Paid work is not rolled back, and the UI cannot tell a failure from a valid empty result. Return a separate generation result and expose a diagnostic state. [VirtualLoot.java](src/main/java/ru/e2e/avs/VirtualLoot.java) |
| P2 | The interface reads local slot limits | The network sends power, duration, progress and status. The GUI still uses local configuration for slot limits. Different server settings can cause misleading limits, rejected inputs and inventory corrections. Sync server limits or validate configuration compatibility. [SpawnerMenu.java](src/main/java/ru/e2e/avs/SpawnerMenu.java) · [SpawnerScreen.java](src/main/java/ru/e2e/avs/SpawnerScreen.java) |
| P3 | Loot depends on a shared FakePlayer and reused entity context | The code uses Forge's shared FakePlayer and reuses one reconstructed entity across a slot's rolls. Position-dependent conditions and mods that mutate the context may differ from a real kill. The configured death loot table seed is not reproduced. Dedicated adapters and test cases are needed. [VirtualLoot.java](src/main/java/ru/e2e/avs/VirtualLoot.java) |

## Coverage and limits

- The ordinary paths passed. They include tier I crafting, distinct NBT storage, FE input, up to 49 virtual kills, resuming after output clears, chunk reload, and recovery of progress and pending loot after a clean restart.
- The audit did not exercise abrupt shutdown during region writes, physical ME and transport networks, dedicated-client configuration mismatches, server overload from large custom loot tables, or moves by third-party mods. The adaptor coverage is listed in [VALIDATION.en.md](VALIDATION.en.md).
- Prioritize preserving inventory, bounding per-tick work and validating soul data. Then address queue blocking, power use and classification of loot conditions.
