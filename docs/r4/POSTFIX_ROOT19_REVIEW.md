# Fresh post-fix world roots — review continuation

MAT completed normally; actual disconnected heap contains one ClientWorld and zero IntegratedServers. The depth-12 reverse-strong search visited 138537 objects without node-cap truncation. It excludes Reference.referent; results are bounded, not exhaustive. Static aliases must not be counted as separate retained worlds or additive memory savings.

Confirmed current paths include Railways cameraStorage, EMI Loot mobFuture preview entities, Iris rendering pipeline custom-uniform supplier, Xaero radar lastRenderEntity and Presence Footsteps. Prior PingWheel/SimplySwords targeted references cleared in native testing; fixing them alone does not make the world collectible.

## Additional installed-code evidence

Read the actual installed Steam_Rails-1.7.3+fabric-mc1.20.1.jar ClientChunkCacheMixin using javap. Preserved output: client-owner-audit/Railways-ClientChunkCacheMixin.javap.txt.

- `newStorage(int)` constructs ClientChunkMap with the current ClientChunkManager as its enclosing owner. This explains the strong chain seen in the heap.
- `railways$securitycraft$onInit` creates fresh camera storage and calls ConductorPossessionController.setCameraStorage.
- `railways$securitycraft$onUpdateViewRadius` reads current camera storage, dereferences its center fields and chunk array, then constructs replacement storage. It is not null-safe.
- ConductorPossessionController.setRenderPosition also directly dereferences storage while viewing a conductor.

Consequence: constructor recreation supports a potential narrowly timed disconnected-world retirement, but arbitrary nulling while a manager/camera callback remains active risks NPE or broken remote cameras. Constructor injection timing, old-world identity and delayed packet/render callbacks still require verification. No Railways patch applied or declared safe.

EMI preview entities and Iris GPU pipeline resources require native lifecycle analysis, not generic collection clearing. No additional cache deletion or active-world change made. No working-set improvement attributed to this investigation.
