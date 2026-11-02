# Horizon-inspired maintenance development candidate

Public version1.0.5 remains unchanged. Build using JDK21, Java17 output:

`./gradlew build remapVerificationJar -Pmod_version=1.0.5-horizon-dev.20261101-1.20.1`

Use only a separate disposable modpack copy. Never put verification-only JARs in a normal instance. Current validation: VALIDATION_HORIZON_DEV.json and TEST_RESULTS_HORIZON_DEV.md. Earlier Advanced8 validation files are historical baseline records, not the status of this development revision. Source research: HORIZON_TRACEABILITY_DEV.md. Config and existing advanced benchmark: CONFIG_AND_STRESS_DEV.md. Measurements and limitations: PERFORMANCE_MEMORY_DEV.md.

No public release, push or approved-artifact overwrite. Original Java implementations follow source-supported principles; no Rust code copied from MIT Horizon. Exact pinned source attribution and non-adoption decisions are recorded in the matrix.
