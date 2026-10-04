package verification;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class ClientPreLaunchCheck implements PreLaunchEntrypoint {
    @Override public void onPreLaunch() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) ClientRuntimeCheck.run();
    }
}
