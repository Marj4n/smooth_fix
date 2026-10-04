# Smooth Fix 1.0.5 advanced benchmark patch validation

Build marker: `1.0.5-perf-r2-advanced1`

Implemented:
- crash-safe full inventory + hotbar selected-slot recovery and explicit S2C selected-slot synchronization;
- handled-screen close before inventory restore;
- optional PlayerAbilityLib temporary ALLOW_FLYING / FLYING / INVULNERABLE grants;
- server-side post-restore equality report for inventory, hotbar, dimension, location, rotation, gamemode and abilities;
- real accepted-hit counter injected after `LivingEntity.damage` returns true;
- EMI 1.1.24-compatible reflective readiness -> search completion -> result -> RecipeScreen verification;
- real-world terrain/cold chunk/structure/vanilla chest/Lootr/inventory/EMI/combat/TNT stage controller;
- per-stage readiness, heartbeat, timeout, server profile, client frame profile and checkpointing;
- deterministic stage transition fixture.

Local verification in this isolated environment:
- `AdvancedStagePlanCheck`: PASS, all 9 stages transition in the required order and terminate.
- Java syntax scan of the new runtime source: no parser-level syntax errors found.
- Full Loom build could not run here because the Gradle wrapper distribution/dependency network is unavailable and this session has no Gradle cache. Run `.\\gradlew clean build` on the project machine before installing the JAR.
