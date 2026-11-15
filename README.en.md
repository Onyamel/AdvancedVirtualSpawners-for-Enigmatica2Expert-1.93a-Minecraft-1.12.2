# Advanced Virtual Spawners

[Русская версия](README.md) · [Validation](VALIDATION.en.md) · [1.0.0 audit](AUDIT.en.md) · [GitHub release guide](GITHUB_RELEASE.en.md)

A Forge add-on for Minecraft 1.12.2 and Enigmatica 2: Expert 1.93a. It generates loot from mobs stored in Ender IO Soul Vials without spawning live mobs nearby. Retrieve the loot from the machine or through Forge item handlers.

## Installation

Replace the previous AVS JAR in `minecraft/mods` with `advancedvirtualspawners-1.1.0.jar`. Install the same version on the server and on each client. Keep gameplay configuration aligned across sides, especially the global Soul Vial stack limit. Use the `-runtime-checks` JAR only in a separate test profile.

The target pack includes Ender IO 5.3.72, EnderCore 0.5.78, Draconic Evolution 2.3.28.354 and Redstone Flux 2.1.1.1. The mod changes the registered Soul Vial's global stack limit to 64 by default. Minecraft and Forge still compare metadata and NBT when stacking.

## Gameplay

| Tier | Color | Soul slots | Speed | Beheading | Luck |
|---|---|---:|---:|---:|---:|
| I | Green | 1 | 3 | 1 | 1 |
| II | Blue | 2 | 6 | 2 | 2 |
| III | Purple | 4 | 9 | 3 | 3 |
| IV | Red | 7 | 12 | 4 | 5 |

- Put filled vials in the top row. Each slot yields one to seven virtual kills per cycle at thresholds of 1, 10, 20, 30, 40, 50 and 60 vials. Vials are reusable. Tier IV can process up to 49 virtual kills per cycle.
- Each upgrade has its own slot and stack limit. Loot goes to the 27-slot output. Automation can insert vials and upgrades and extract loot through the item capability on every side.
- The default cycle lasts 140 ticks. The power baseline is Ender IO's Powered Spawner with a zombie and Basic Capacitor, which is 1,280 RF/t in E2E 1.93a. A fully upgraded tier IV takes 12 ticks at 51,840,000 RF/t. A slower supply lets the machine work as energy accumulates.
- Beheading adds registered heads separately from native loot. Level IV grants two additional heads for a supported mob. The configuration can map more heads.
- Luck boosts known non-guaranteed outcomes, including outcomes whose item count can be zero. Luck V yields one item per eligible supported rare outcome. Luck IV and V multiply guaranteed quantities by five. Boosted alternatives can yield multiple outcomes when their probabilities sum to more than one.
- The machine saves energy, progress, inventory and pending loot to the world. When world item spawning is refused during removal, contents are saved in dimension data and retried in a loaded chunk.
- Tier I uses a Powered Spawner in the center, Weather Crystals in the corners, spider above, zombie below, enderman to the left and skeleton to the right. Tiers II–IV use a Stabilized Spawner and Draconic Evolution cores. All seven recipes can be looked up through the crafting recipe system.

## Configuration and compatibility

The first load creates `config/advancedvirtualspawners.cfg`. The file controls timings, speed curves, input limits, energy, probabilities and allowed or excluded mobs. The first Speed upgrade defaults to ×1. `additiveSpeed` switches to `1 + upgrade count`. Entries in `headMappings` take the form `mod:entity=mod:item@metadata`. Metadata is optional.

The machine validates captured entity identifiers. Each virtual kill uses a fresh reconstructed mob and an AVS FakePlayer in the same dimension. The machine checks `doMobLoot` before charging energy. The server synchronizes machine slot limits. Luck supports single-roll pools with ordinary item or empty entries and known conditions and count functions. Other pools use native Forge generation where the current implementation detects an unsupported layout. `logFallbacks` logs those fallbacks. Loot generated solely by death events or entity-specific Java methods requires an adapter.

A cycle has a 2,048-output-stack limit and a separate loot work budget. An oversized table or generation mistake preserves progress up to the final tick, shows a loot error and retries after a delay. Partial results stay out of the output.

## Source and build

`SpawnerTile` owns machine state and capabilities. `SpawnerMenu` and `SpawnerScreen` handle inventory interaction and display. `EnderIo` and `VirtualLoot` handle soul vials and loot. Models, PNGs and language files live in `src/main/resources`. `tools/generate_assets.py` regenerates the block assets and recipes. Upgrade icons are checked-in PNGs.

Use JDK 8, Gradle 4.10.3 and the installed E2E 1.93a mod JARs. The development build uses Forge 14.23.5.2847 mappings. The tested E2E profile starts from Forge 14.23.5.2860 and switches to Cleanroom 0.6.11-alpha through the pack's relauncher.

```powershell
.\build.ps1 -Offline -RuntimeChecks
```

Supply `-JavaHome`, `-GradleHome` and `-E2E` for other locations. `-E2E` points at the pack's `minecraft` directory. Omit `-Offline` for a first build without a Gradle cache. See [validation and reproduction](VALIDATION.en.md) for runtime tests.

## Credits

Special thanks to **@Danzalex-Alex** for shaping the original idea and contributing to its implementation. See the [release notes](RELEASE_NOTES.md).
