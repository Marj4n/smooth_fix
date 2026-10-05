# Verification tools

These sources are isolated test fixtures. They are excluded from the distributable
Smooth Fix JAR. Never add a verification-only JAR to the user's modpack.

Use JDK 21 to build `build remapVerificationJar`. The distributable and test classes
remain Java 17 bytecode. Main source and Gradle settings are portable; this source
archive does not include the build host's Minecraft/dependency/Gradle caches.

The production server fixture uses `-Dsmoothfix.verify.advanced=true`, a disposable
server world and a simulated network peer. For village replacement tests, create
the isolated data pack using `create_village_fixture.py` and the extracted 1.20.1
Minecraft server game JAR. A new fixture world is required for a genuine cold-route
check; do not rename an already generated route as new terrain. VALIDATION.json records the current unbuilt status. Previous fixture results do not
validate this revision.

`GroundNavigationCheck` exercises real collision shapes and the production
GroundNavigator through native player travel/jump in a temporary elevated obstacle
fixture. It restores changed blocks and the simulated player's location. It checks
walls, slabs, one-block steps, low ceilings, holes, lava and unavailable chunks.
`AdvancedClientCheck` uses native KeyboardInput/StickyKeyBinding and transformed
client auto-jump state without creating a graphical window. It verifies movement
input neutrality, preservation of toggle settings and rejection of stalled routes.
The current behavioral fixtures replace the obsolete Advanced1 source-text checker.

The headless pre-launch client verification also checks transformed rendering and
compatibility hooks, EMI result/recipe identity and report lifecycle. It intentionally
exits before a game window. Server full-suite tests simulate client responses, use
reduced actor counts and advance timestamps. They do not measure user GPU/FPS or
prove a full graphical multiplayer traversal or PlayerAbilityLib integration.

Advanced8 Laptop has not run these fixtures. Build and run them locally if required;
keeping fixture source in this archive does not claim that its checks passed.
