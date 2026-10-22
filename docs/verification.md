# Verification record

This record began with local verification of `0.1.0-alpha.1` on 2026-10-18 and retains that historical baseline below. The current candidate and subsequent checks are recorded separately. It describes the paths exercised by the tests and their limits; it is not a claim of complete IC2 feature parity or compatibility with every mod interaction.

The subsequent `0.1.0-alpha.2` Russian localization and its repeated server/client checks are recorded in [Russian localization](russian-localization.md).

The `0.1.0-alpha.3` recipe-discovery completion, 447-test baseline and native recipe-book crafting check are recorded in [recipe book](recipe-book.md).

Later fixes and their checks: [alpha.4 advancement background](advancement-background.md), [alpha.5 shaped recipe placement](recipe-book.md#автозаполнение-фигурных-рецептов--alpha5), and [alpha.6 drill sound crash](drill-sound-fix.md). Alpha.6 passed 455 server GameTests, 10 JUnit tests and the profile client smoke, including actual cobblestone drilling and native sound replacement assertions.

The [alpha.7 wood fuel fix](wood-fuel.md) passed a 462-test server baseline, seven focused fuel tests after the final provider adjustment, 10 JUnit tests and the profile client smoke with real wood-fuel GUI insertion and combustion.

The [alpha.8 worn crafting-tool fix](recipe-book.md#изношенный-молот-и-кусачки--alpha8) passed a 467-test baseline and 10 JUnit tests. All five tool regressions and JUnit passed again after retaining native named/enchanted-item filtering. The final profile client smoke crafted copper, iron and lead plates consecutively through the green book with the same hammer, preserving wear across native inventory return and autofill. Final evidence is in `build/verification-crafting-tools-final-alpha8.log` and `build/client-render-crafting-tools-alpha8/`; the verified JAR is installed in the Modrinth 26.3 profile with alpha.7 backed up.

## Environment and current results

Alpha.12 combines [fractional progress-gauge rendering](progress-animation.md) with the previously uninstalled [radiation fix](radiation-effect.md). All 507 server GameTests and 10 JUnit tests passed in `build/verification-progress-alpha12-full-first.log`. That run then stopped in a new client assertion which misclassified legitimate recipe-completion packet ordering. The test helper was corrected to correlate the completion snapshots; production code did not change. The subsequent client-only retry passed with all 11 staged profile mods: `build/verification-progress-alpha12-verified.log` ends with `BUILD SUCCESSFUL in 2m 17s`. The full server/JUnit run and the successful client retry are separate executions.

The two added client helpers do not increase the server GameTest count. `GuiTexturePrecisionChecks` passed 12 individual native blit snapshots and three queued snapshots, covering geometry/UVs, caller-pose restoration and delayed pose independence. `MachineProgressRenderChecks` completed real powered metal-former and macerator cycles, each with a 12-tick power pause, exact output and one legitimate completion reset. It sampled 806/1,720 rendered frames, including 766/1,673 fractional widths, and 224/324 server ticks respectively. The largest source/destination span mismatch was `1.52587890625E-5` GUI pixels; the largest fixed-interior texel drift was below `1.1E-5` texels. The report, 18 screenshots and `machine-progress-samples.csv` (1,047 lines including the header) are archived in `build/client-render-progress-alpha12/`. Both new machine screenshots were visually inspected. The server XML is `build/reports/gametest/results-progress-alpha12-final.xml`; the combined evidence record is `build/progress-verification-alpha12.json`. These scenarios cover two live machines and shared rendering primitives, rather than every machine GUI or shader configuration.

Alpha.12 archive, localization and binary-diff audits passed: `build/artifact-audit-progress-alpha12.json`, `build/progress-localization-audit-alpha12.json`, `build/progress-artifact-diff-alpha12.json`. The 8,179,445-byte JAR has SHA-256 `05b5c6f97013a7e938da73d8ecb991f1e52592f061de32588501879cef413ebf`, 1,300 classes matching the checked production output, 789 recipes and 634 item definitions, with no test classes and the license retained. Relative to previously installed alpha.10, executable changes are confined to `Ic2Gui.class`, `Ic2Potion.class` and version metadata; `Ic2Gui$Tooltip.class` differs only in seven debug line-number offsets. No recipe, texture or translation entries changed. Alpha.12 was installed at `2026-10-22T01:42:20.3754025+07:00` as `mods/ic2-fabric-26.3-0.1.0-alpha.12.jar`. Its hash matches the verified artifact, and exactly one IC2 JAR remains in the profile. Alpha.10 is backed up as `build/profile-backups/ic2-fabric-26.3-0.1.0-alpha.10.jar`; its original hash was verified. The installation record is `build/profile-installation.json`, and `build/progress-verification-alpha12.json` records the installed state. Alpha.11 was never installed.

The historical alpha.11 candidate passed all 507 server GameTests and 10 JUnit tests, followed by the Russian native client smoke with all 11 staged profile mods. Nine new regressions exercise actual radiation-effect ticks: original integer dose/interval, uranium-238 inventory exposure, vanilla armor bypass, accepted-hit nano EU costs, Hazmat/Quantum protection, quantum cure, creative immunity and lethal exposure. The connected client verifies health 20 -> 19.5 without armor and in full diamond/netherite, plus nano health 20 -> 19.85 and 6750 EU spent for a 1.5 HP dose. Evidence: [radiation effect](radiation-effect.md), `build/verification-radiation-alpha11-verified.log`, `build/reports/gametest/results-radiation-alpha11-final.xml` and `build/client-render-radiation-alpha11/` (16 screenshots). Binary comparison with alpha.10 changes only `Ic2Potion.class` and the version metadata. This candidate was superseded by combined alpha.12 before profile replacement.

Alpha.10 passed all 498 server GameTests and 10 JUnit tests, then the Russian native client smoke with all 11 staged profile mods. Fourteen new regressions exercise actual accepted damage, PvP, shield blocking, charge limits, mixed armor and fall callbacks. The connected client verifies nano 90%/quantum 100% protection, EU and health packets, discharged armor, fall absorption and the corrected helmet tooltip. All previous recipe-book checks still pass. Evidence: [electric armor](electric-armor.md), `build/verification-electric-armor-alpha10-verified.log`, `build/reports/gametest/results-electric-armor-alpha10-final.xml` and `build/client-render-electric-armor-alpha10/` (15 screenshots). The artifact audit confirms all 1,300 packaged classes match the tested production output; no recipe or asset entries changed from alpha.9.

Alpha.9 uses the profile's byte-identical Fabric API 0.164.0+26.3 and passed 484 GameTests plus 10 JUnit tests. Its exhaustive book audit executed all 414 ordinary crafts, naturally discovered all 449 crafting/smelting recipes, and repeated 43 crafts with worn IC ingredients. All six constrained fuel-rod recipes and their component variants passed. The final Russian client validated all 449 synchronized recipe displays and crafted nano helmets from empty and partly charged goggles through real book clicks. See [the alpha.9 record](recipe-book.md#полная-проверка-книги--alpha9), `build/verification-all-recipes-alpha9-final.log` and `build/client-render-all-recipes-alpha9/`. All 10 formerly hidden static recipes are shown at the user's request.

The current pinned build uses Minecraft Java 26.3, Fabric Loader 0.19.5, Fabric API 0.164.0+26.3, Fabric Loom 1.17.20, Java 25, and Gradle 9.6.0. The historical alpha.1 profile baseline below used Fabric API 0.163.0+26.3. The native client ran on Windows with an NVIDIA GeForce GTX 1070 Ti and driver 582.66, using an OpenGL 3.3 context.

The verified local client runs used development harness `fabric-client-gametest-api-v1:6.0.9+4be74c3f5d` alongside the byte-identical profile API modules. The [published Fabric API 0.164.0+26.3 POM](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.164.0%2B26.3/fabric-api-0.164.0%2B26.3.pom) instead declares `fabric-client-gametest-api-v1:6.0.10+131f8d475d`. Both use server harness `fabric-gametest-api-v1:4.0.34+3434d6d95d`. The optional local API mirror is excluded from this repository. A clean online clone therefore resolves the published client harness; exact reproduction of the recorded local client environment is not claimed. Production source builds do not require that local mirror.

| Check | Result | Evidence |
|---|---|---|
| Historical alpha.1 full baseline server GameTests after the final fluid and chunk-loader changes | Passed: 441 IC2 tests plus one framework/vanilla test, 442 total; zero failures | `build/verification-final-validated.log`, `build/reports/gametest/results-final-validated.xml` |
| JUnit/build after the final changes | Passed: all 10 JUnit tests; `BUILD SUCCESSFUL` | `build/verification-final-validated.log`, `build/test-results/test/` |
| Fluid placement/physics GameTests with staged profile mods | All 13 required tests passed | `build/verification-compat-fluids.log` |
| Native client smoke with staged profile mods | Passed; all 11 requested profile mod IDs present | Same log; archived report and screenshots under `build/client-render-profile-alpha/` |

The profile verification log ends with `BUILD SUCCESSFUL`. It contains a real filtered server run of `ic2-gametest:fluid_placement_game_tests_*` and the following `runClientGameTest` task. The local evidence is generated under `build` and can be removed or replaced by later runs; it is not bundled into the production mod.

## What the profile run exercised

The 13 server tests cover placed hydrogen/hot-water source spread, pahoehoe solidification before its slow flow tick, upward negative/zero-density flow, an upward escape route selected ahead of a downward hole, downward positive-density flow, source preservation and one-bucket draining, density-based displacement/preservation of vanilla water and lava, and UU matter's lava interaction and spread. These are world-based GameTests running with Lithium loaded.

The native client test opens a temporary flat world at a real 1600×900 window/framebuffer size with an 800×450 GUI at scale 2. Its saved report records:

- 13 sampled block model checks and 27 item samples covering 25 item IDs, including charged-battery and obscurator states. The checked geometry is nonempty and its sprites are not missing.
- Nine fluid tessellator checks for height, face winding, same-fluid culling, and slab occlusion. Steam, superheated steam, air, hydrogen, and oxygen fill from the top and use the custom gas dispatch; water, lava, coolant, and positive-density biogas retain bottom fill and the replacement default renderer path.
- A real rendered world/fluid scene, an item gallery, and generator/macerator/metal-former screens with server menu, energy, and field packets. A real mouse click switches the metal former from mode 1 to mode 2 and verifies client synchronization.
- Seven screenshots: world, fluids, items, generator GUI, macerator GUI, metal-former GUI, and metal-former cutting mode. The archived report is `build/client-render-profile-alpha/render-validation.txt`; the PNGs are in its `screenshots` directory.

The world, fluid, item, and metal-former cutting screenshots were also visually inspected. These checks concern the sampled scenes and states, rather than every model, GUI, fluid configuration, or gameplay interaction.

## Staged profile and versions

The staging directory is `build/profile-compat/mods`. Its 11 JARs were checked with SHA-256 against `build/profile-compat-copied-mods.json` and the source profile copies: all matched, with no extra or missing staged JARs. The profile's root Fabric API JAR is deliberately omitted because Fabric API is already a build/runtime dependency: 0.163.0+26.3 for the historical alpha.1 baseline and 0.164.0+26.3 for the current candidate.

| Mod ID | Loaded version | Dedicated-server test | Native client smoke |
|---|---|---|---|
| `badoptimizations` | `2.4.1` | Client environment only | Loaded |
| `betterblockentities` | `1.4.0+mc26.3` | Client environment only | Loaded |
| `c2me` | `0.4.2-alpha.0.106+26.3` | Loaded | Loaded |
| `entityculling` | `1.11.3` | Loaded | Loaded |
| `ferritecore` | `9.0.0` | Loaded | Loaded |
| `immediatelyfast` | `1.17.1+26.3` | Client environment only | Loaded |
| `iris` | `1.11.7+mc26.3` | Client environment only | Loaded; no shader pack |
| `lithium` | `0.26.2+mc26.3` | Loaded | Loaded |
| `modernfix` | `5.27.20-build.2` | Loaded | Loaded |
| `nvidium` | `0.4.4-beta7-26.3` | Client environment only | Loaded; renderer disabled itself |
| `sodium` | `0.9.2+mc26.3` | Client environment only | Loaded |

The build derives required IDs from each root JAR's actual `fabric.mod.json` environment. `entityculling` omits that field, so Loader treats its root mod as environment `*` and it is required/present on the server; that does not mean its client entrypoint runs there. The test initializer fails if any required profile mod is missing and logs each loaded version. Runtime resolution selected MixinExtras 0.5.5, meeting Lithium's dependency.

This is evidence that the exact copied set starts and passes the listed server/client checks. It does not demonstrate every feature of every mod. In particular, the log says that Nvidium disables its renderer because its requirements are not met, and Iris disables shaders because no valid shader pack is selected. Active Nvidium rendering and external shader-pack rendering remain untested.

## Repeat the checks

Run commands from the project root. `tools/gradle-local.ps1` selects the local JDK 25 and local Gradle without changing the system Java installation. `IC_JAVA_HOME` can select another JDK 25 installation.

Baseline build/JUnit and all server GameTests, with no copied profile mods:

```powershell
& .\tools\gradle-local.ps1 -PenableGameTests=true build
```

Native client smoke without copied profile mods:

```powershell
& .\tools\gradle-local.ps1 -PenableGameTests=true -PenableClientGameTests=true runClientGameTest
```

For the profile checks, prepare copies of the versioned JARs above in `build/profile-compat/mods`, omitting the duplicate root Fabric API JAR. Keep the source profile and existing worlds outside the test run directories. The staging directory must be outside both `build/client-render-smoke` and `build/run/gameTest`; the build reserves these locations and rejects staging there. The client run directory is recreated before launch; the server test world can retain data from earlier runs.

Profile fluid physics and client smoke can run together:

```powershell
& .\tools\gradle-local.ps1 `
  -PenableGameTests=true `
  -PenableClientGameTests=true `
  -PprofileGameTests=true `
  -PprofileModsDir=build/profile-compat/mods `
  '-PgameTestFilter=ic2-gametest:fluid_placement_game_tests_*' `
  runGameTest runClientGameTest
```

`profileGameTests=true` is the explicit opt-in that applies the staging directory to the server GameTests. Supplying `profileModsDir` applies it to an enabled client test. The default server baseline therefore remains separate from profile tests.

`gameTestFilter` passes `fabric-api.gametest.filter` to the server runner. It is a glob over the full test ID, including namespace. The pattern above selects the 13 physics tests in `FluidPlacementGameTests`; it does not select the separate 18 `FluidMachineGameTests`. Omit the filter for the complete suite. The server XML report is written to `build/reports/gametest/results.xml`.

Each client launch recreates `build/client-render-smoke`; preserve `render-validation.txt` and `screenshots` elsewhere under `build` before another client run. A baseline client run will otherwise replace the previous profile evidence.

## Chunk-loader regression

The first final baseline run completed 440 tests with one failure: the shutdown check found its chunk still registered. Saved world data identified an unpowered loader from the range-selection test in the same chunk. Editing the selected chunks had called the ticket update path even while inactive. The fix gates live ticket updates on the loader's active state and removes stale registrations when an inactive loader is restored. Two additional tests cover unpowered selection changes and restored stale ownership; the original shutdown assertion is unchanged. The successful 442-test run reused the same test world. After saving, all four previously persisted owners had been removed and no new owners remained, as recorded in `build/chunk-loader-state-cleanup.json`. The failed report and pre-fix saved data are preserved as `build/reports/gametest/results-final-first.xml` and `build/chunk-loader-state-before-fix.dat`.

## Historical alpha.1 final archive

`build/artifact-audit-final.json` records the production JAR audit. All 1,292 packaged classes match the tested production output byte for byte and use Java 25 class version 69. The archive contains 789 IC2 recipes, 634 item definitions and the license, with no test classes or source files. The source JAR also includes the license.

- Binary: `ic2-fabric-26.3-0.1.0-alpha.1.jar`, 8,079,965 bytes; SHA-256 `6227181e5cd7a724480be4a64cd8855415b1e7e8eec9e7dbef9387f5de1dfedf`.
- Sources: `ic2-fabric-26.3-0.1.0-alpha.1-sources.jar`; SHA-256 `db777d49b287e555fd05c68e2de6cf8128cfe163d0f9ba2f81a72151da1c38d3`.

## Alpha limits and provenance

These automated runs provide regression evidence for the checked mechanics, startup, sampled rendering, and selected GUIs. They do not establish long-duration world stability, existing-save migration, performance gains, all multiplayer/mod combinations, or every original IC2 behavior. The logs contain warnings, including third-party environment/driver and optional-target messages; passing does not mean the logs are warning-free.

The supplied `industrialcraft-2-2.8.222-ex112.jar` is the behavioral reference. The working source base is [BilibiliXiaoYun/IC2R-fabric](https://github.com/BilibiliXiaoYun/IC2R-fabric), pinned at commit `5233264b7e0ad30a3ef71206c74fc43f252bc012`, retained under `upstream/ic2r-fabric`. That code line was adapted from IC2 2.9.40 with backports from 2.8.222 and already contains differences, including a rewritten energy calculator. Passing this port's tests does not make it an unchanged 2.8.222 implementation; differences still need comparison with the supplied original.

Vanilla copper/iron/gold resources and selected processing balances also differ intentionally; see [material mapping](material-mapping.md). The supplied `ic2-reborn-0.1.5-alpha.jar` was studied for comparison and is not the source base. Original authors and upstream license notices are retained. Optional upstream JEI/AE2 integration sources are excluded from this Fabric build and have not been ported or verified here.
