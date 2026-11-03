# Smooth Fix

Compatibility fixes and diagnostics for the Fabric 1.20.1 stack used by Smooth Odyssey. Includes inventory/accessory fixes, targeted mod API guards, rendering fixes and diagnostic tools. It does not replace Sodium, Lithium or the other performance mods.

## Build

Use JDK 21; output targets Java 17.

```sh
./gradlew build
```

Install the regular JAR from `build/libs/` with Fabric API. Options are stored in `config/smooth_fix.json`. The registry-cache lifecycle fix defaults on only for the audited Kilt/ModernFix binary pair. Existing configs retain their chosen value; set `kiltModernFixRegistryCacheBridge` to `true` to enable it, or `false` to opt out. Compatibility adapters check their supported upstream versions; do not remove those guards.

## Source layout

`src/main/java` contains fixes, integrations and diagnostics. `src/main/resources` contains mod/mixin metadata. `src/verification/java` and `src/verification/resources` contain isolated verification fixtures; build them with `./gradlew remapVerificationJar`. Verification-only JARs are not gameplay mods and belong in disposable test instances.

Experimental worldgen, cache, lifecycle and allocation patches stay opt-in unless their acceptance criteria are met. Some benchmark workloads and cross-mod parity checks remain unresolved. The 6 GiB gameplay-memory target has not been achieved; diagnostic results are not a general FPS or memory guarantee.

## License

By Marj4n. See [LICENSE.txt](LICENSE.txt). Horizon research informed testing and ownership principles; no Rust engine code is included.
