package org.marj4n.smooth_fix.performance;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;

/** Exact installed-source teardown adapters. Caller holds the early-start lifecycle lock. */
final class StoppedOwnerAdapters {
    private static final Runnable NO_DISPOSAL = () -> {};

    private StoppedOwnerAdapters() {}

    private static boolean audit(Class<?> c, String sha) throws Exception {
        return LifecycleRetentionCleanup.auditedClass(c, sha);
    }

    private static void count(String key) {
        LifecycleRetentionCleanup.recordOwnerRelease(key, 1);
    }

    private static VarHandle handle(Class<?> c, String name, Class<?> type, boolean isStatic)
            throws Exception {
        Field f = c.getDeclaredField(name);
        if (f.getType() != type
                || Modifier.isStatic(f.getModifiers()) != isStatic
                || Modifier.isFinal(f.getModifiers()))
            throw new IllegalStateException(
                    "Audited mutable field shape changed: " + c.getName() + "." + name);
        var lookup = MethodHandles.privateLookupIn(c, MethodHandles.lookup());
        return isStatic
                ? lookup.findStaticVarHandle(c, name, type)
                : lookup.findVarHandle(c, name, type);
    }

    static void bclib(MinecraftServer ended) throws Exception {
        Class<?>
                owner = Class.forName("org.betterx.bclib.api.v2.dataexchange.handler.DataExchange"),
                api = Class.forName("org.betterx.bclib.api.v2.dataexchange.DataExchangeAPI"),
                connector =
                        Class.forName("org.betterx.bclib.api.v2.dataexchange.ConnectorServerside");
        if (!audit(owner, "b9130b583d4eefaa71eec8950c1f406f0e296b6ae7e80c139ae5e1a078afe566")
                || !audit(api, "ad002a4dade923de59940fcb817b936e70a7932f32cdc415f8cc7f8b259b37f1")
                || !audit(
                        connector,
                        "65b972f068a17fcd0bac821fbf08aabfe90129f754196f976f7c28a2199708d9")) return;
        Object singleton = handle(owner, "instance", api, true).get();
        if (singleton == null) return;
        Object value = handle(owner, "server", connector, false).get(singleton);
        if (value == null) return;
        // Preserve connector/event registrations. Native onPlayInit restores this mutable owner.
        if (handle(connector, "server", MinecraftServer.class, false)
                .compareAndSet(value, ended, (MinecraftServer) null))
            count("bclib.stoppedConnectorServerReferencesCleared");
    }

    static void fakeConnection(MinecraftServer ended) throws Exception {
        Class<?> type =
                Class.forName(
                        "net.fabricmc.fabric.impl.event.interaction.FakePlayerNetworkHandler");
        if (!audit(type, "5b7e3cf75c19d7672bae3928a30a17dc541edbe1fc56d8f377420bb6710c14d0"))
            return;
        Field singleton = type.getDeclaredField("FAKE_CONNECTION");
        if (!Modifier.isStatic(singleton.getModifiers())
                || singleton.getType() != ClientConnection.class)
            throw new IllegalStateException("Audited fake connection changed");
        singleton.setAccessible(true);
        Object connection = singleton.get(null);
        if (connection == null) return;
        if (!"1.20.1".equals(net.minecraft.SharedConstants.getGameVersion().getName()))
            throw new IllegalStateException("Unaudited native connection version");
        var fields =
                Arrays.stream(ClientConnection.class.getDeclaredFields())
                        .filter(
                                f ->
                                        !Modifier.isStatic(f.getModifiers())
                                                && f.getType() == PacketListener.class)
                        .toList();
        if (fields.size() != 1)
            throw new IllegalStateException("Audited native packet listener shape changed");
        VarHandle listener =
                handle(
                        ClientConnection.class,
                        fields.get(0).getName(),
                        PacketListener.class,
                        false);
        Object previous = listener.get(connection);
        if (previous != null
                && previous.getClass() == type
                && previous instanceof ServerPlayNetworkHandler handler
                && handler.player.getServer() == ended
                && listener.compareAndSet(connection, previous, (PacketListener) null))
            count("fabric.stoppedFakeConnectionListenersCleared");
        // The singleton remains reusable: ServerPlayNetworkHandler's constructor installs its next
        // listener.
    }

    static void xaeroConfig(MinecraftServer ended) throws Exception {
        Class<?> channel = Class.forName("xaero.lib.common.config.channel.ConfigChannel"),
                manager = Class.forName("xaero.lib.common.config.server.ServerConfigManager");
        if (!audit(channel, "8ae2738a2f940c90301c0bab86ac1a8721c8b5924d5aae441d42d69e759e75b7")
                || !audit(
                        manager,
                        "04dcf104081330ea68f51a5e4a204fed5d80bf0bbb2e974671e281e060e48fd0")) return;
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        VarHandle field = handle(manager, "server", MinecraftServer.class, false);
        for (String name :
                List.of("xaero.common.HudMod", "xaero.map.WorldMap", "xaero.lib.XaeroLib")) {
            Class<?> root;
            try {
                root = Class.forName(name);
            } catch (ClassNotFoundException absent) {
                continue;
            }
            Field singleton = root.getDeclaredField("INSTANCE");
            if (!Modifier.isStatic(singleton.getModifiers()))
                throw new IllegalStateException("Audited Xaero singleton changed");
            singleton.setAccessible(true);
            Object value = singleton.get(null);
            if (value == null) continue;
            for (Class<?> c = value.getClass(); c != null; c = c.getSuperclass())
                for (Field member : c.getDeclaredFields())
                    if (!Modifier.isStatic(member.getModifiers()) && member.getType() == channel) {
                        member.setAccessible(true);
                        Object config = member.get(value);
                        if (config == null) continue;
                        Object serverConfig =
                                channel.getMethod("getServerConfigManager").invoke(config);
                        if (serverConfig == null || !seen.add(serverConfig)) continue;
                        if (serverConfig.getClass() != manager)
                            throw new IllegalStateException("Unaudited Xaero manager subtype");
                        // setServer is a pure assignment with a running-server guard; this CAS
                        // preserves that ownership contract.
                        if (!ended.isRunning()
                                && field.compareAndSet(serverConfig, ended, (MinecraftServer) null))
                            count("xaero.stoppedConfigManagerServersCleared");
                    }
        }
    }

    static void puffish(MinecraftServer ended) throws Exception {
        Class<?> skills = Class.forName("net.puffish.skillsmod.SkillsMod"),
                listener = Class.forName("net.puffish.skillsmod.util.ChangeListener");
        if (!audit(skills, "982b952c8be24eabf277084762f43938074ec5dae49e43aa4ebb2f6bf8a78ab5")
                || !audit(
                        listener,
                        "39548ecef3fdc6c5b61cb0696c5861ffd0ce637941c3e556612cbc834c5ac43a")) return;
        Object singleton = skills.getMethod("getInstance").invoke(null);
        Field categories = skills.getDeclaredField("categories");
        if (categories.getType() != listener)
            throw new IllegalStateException("Audited Puffish categories changed");
        categories.setAccessible(true);
        Object value = categories.get(singleton);
        VarHandle undo = handle(listener, "undo", Runnable.class, false);
        Object previous = undo.get(value);
        if (!(previous instanceof Runnable)) return;
        boolean owned = false;
        for (Field capture : previous.getClass().getDeclaredFields())
            if (!Modifier.isStatic(capture.getModifiers())
                    && capture.getType() == MinecraftServer.class) {
                capture.setAccessible(true);
                owned |= capture.get(previous) == ended;
            }
        if (!owned) return;
        // Native set executes exactly the old CategoryConfig.dispose(DisposeContext(oldServer)).
        // Keep the category definitions/value and all saved player progress; replace only the
        // completed disposal.
        Object definitions = listener.getMethod("get").invoke(value);
        listener.getMethod("set", Object.class, Runnable.class)
                .invoke(value, definitions, NO_DISPOSAL);
        if (undo.get(value) != NO_DISPOSAL)
            throw new IllegalStateException(
                    "Native category disposal did not retire its old owner");
        count("puffish.nativeStoppedCategoryDisposalsCompleted");
    }

    static void biolith(MinecraftServer ended) throws Exception {
        Class<?>
                coordinator =
                        Class.forName("com.terraformersmc.biolith.impl.biome.BiomeCoordinator"),
                placement =
                        Class.forName(
                                "com.terraformersmc.biolith.impl.biome.DimensionBiomePlacement"),
                state = Class.forName("com.terraformersmc.biolith.impl.config.BiolithState");
        if (!audit(coordinator, "beacdc705d33daca2d8cca377cda809622b1ab2bcea2a9599677a27c9945ef13")
                || !audit(
                        placement,
                        "5db2e765821322b2b56828aab27b0f3cb0ef8ea48fdecf264fa39a45cdae30bb")
                || !audit(
                        state, "f8276e667f4f655b8e3501ef5db00cd724991c88e73da76a4546692f751d84e4"))
            return;
        Field started = coordinator.getDeclaredField("serverStarted");
        if (started.getType() != boolean.class || !Modifier.isStatic(started.getModifiers()))
            throw new IllegalStateException("Audited Biolith lifecycle changed");
        started.setAccessible(true);
        if (started.getBoolean(null))
            throw new IllegalStateException("Native Biolith stop has not completed");
        Field world = state.getDeclaredField("world");
        if (world.getType() != ServerWorld.class)
            throw new IllegalStateException("Audited Biolith world owner changed");
        world.setAccessible(true);
        VarHandle field = handle(placement, "state", state, false);
        for (String name : List.of("OVERWORLD", "NETHER", "END")) {
            Field singleton = coordinator.getDeclaredField(name);
            if (!Modifier.isStatic(singleton.getModifiers()) || singleton.getType() != placement)
                throw new IllegalStateException("Audited Biolith placement changed");
            singleton.setAccessible(true);
            Object value = singleton.get(null);
            if (value == null) continue;
            Object previous = field.get(value);
            if (previous != null
                    && previous.getClass() == state
                    && world.get(previous) instanceof ServerWorld owner
                    && owner.getServer() == ended
                    && field.compareAndSet(value, previous, null))
                count("biolith.stoppedPlacementStatesReleased");
        }
        // Native stop already drops *_STATE; release the omitted placement aliases only.
        // Do not alter seeds, noise, biome requests, terrain generation or persisted state.
    }

    private static final java.util.function.Consumer<Object> NO_STOPPED_WORLD = v -> {};

    static void betterEnd(MinecraftServer ended) throws Exception {
        Class<?>
                owner =
                        Class.forName(
                                "org.betterx.betterend.world.features.terrain.OreLayerFeature"),
                noise = Class.forName("org.betterx.bclib.sdf.operator.SDFCoordModify");
        if (!audit(owner, "aa437d0a33055bef7ad4faf098d9a693ab8d2f5b8a6197f5d6069e77d4bba119")
                || !audit(
                        noise, "617859e77e20db16df59029247534ad1316f5c7b24c150e447c8b3dbd05b3d81"))
            return;
        Field field = owner.getDeclaredField("NOISE");
        if (!Modifier.isStatic(field.getModifiers()) || field.getType() != noise)
            throw new IllegalStateException("Audited BetterEnd noise owner changed");
        field.setAccessible(true);
        Object instance = field.get(null);
        if (instance == null) return;
        VarHandle function = handle(noise, "function", java.util.function.Consumer.class, false);
        Object previous = function.get(instance);
        if (previous == null
                || !previous.getClass().getName().startsWith(owner.getName() + "$$Lambda")) return;
        boolean owned = false;
        for (Field capture : previous.getClass().getDeclaredFields())
            if (!Modifier.isStatic(capture.getModifiers())
                    && net.minecraft.world.StructureWorldAccess.class.isAssignableFrom(
                            capture.getType())) {
                capture.setAccessible(true);
                Object value = capture.get(previous);
                owned |=
                        value instanceof net.minecraft.world.StructureWorldAccess world
                                && world.toServerWorld().getServer() == ended;
            }
        // Native place always assigns its real function before fillRecursive. Retire only
        // a fully stopped owner's last closure; never alter any live generation inputs.
        if (owned
                && !ended.isRunning()
                && function.compareAndSet(instance, previous, NO_STOPPED_WORLD))
            count("betterend.stoppedOreFunctionOwnersReleased");
    }

    static void createDamage(MinecraftServer ended) throws Exception {
        Class<?> type =
                Class.forName("com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem");
        if (!audit(type, "a5e4658d4e6621d18f7845e148cd2b9af93e7cf08ee2c7f3a76dac7a54bda2fe"))
            return;
        VarHandle field =
                handle(
                        type,
                        "lastActiveDamageSource",
                        net.minecraft.entity.damage.DamageSource.class,
                        true);
        Object previous = field.get();
        if (!(previous instanceof net.minecraft.entity.damage.DamageSource damage)) return;
        boolean owned = false;
        for (net.minecraft.entity.Entity actor :
                new net.minecraft.entity.Entity[] {damage.getSource(), damage.getAttacker()})
            owned |=
                    actor != null
                            && actor.getWorld() instanceof ServerWorld world
                            && world.getServer() == ended;
        // Native attacks overwrite the transient source; never clear one belonging to a live owner.
        if (owned
                && !ended.isRunning()
                && field.compareAndSet(previous, (net.minecraft.entity.damage.DamageSource) null))
            count("create.stoppedDamageSourceOwnersReleased");
    }

    private static Object nativeField(Object value, String name, String expectedType)
            throws Exception {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass())
            try {
                Field field = type.getDeclaredField(name);
                if (!field.getType().getName().equals(expectedType)
                        || Modifier.isStatic(field.getModifiers()))
                    throw new IllegalStateException("Audited quest ownership field changed");
                field.setAccessible(true);
                return field.get(value);
            } catch (NoSuchFieldException absent) {
            }
        throw new IllegalStateException("Audited quest ownership field missing");
    }

    static void questKill(MinecraftServer ended) throws Exception {
        Class<?> type = Class.forName("dev.muon.questkilltask.QuestProcessor");
        if (!audit(type, "85d9cf1d5968d2923fb3019e9dcc70844c71c3cb035653398be9f6775fdd08f4"))
            return;
        VarHandle field = handle(type, "killTasks", java.util.List.class, true);
        Object previous = field.get();
        if (!(previous instanceof java.util.List<?> tasks) || tasks.isEmpty()) return;
        for (Object task : tasks.toArray()) {
            if (task == null) continue;
            Object quest = nativeField(task, "quest", "dev.ftb.mods.ftbquests.quest.Quest"),
                    chapter = nativeField(quest, "chapter", "dev.ftb.mods.ftbquests.quest.Chapter"),
                    file =
                            nativeField(
                                    chapter, "file", "dev.ftb.mods.ftbquests.quest.BaseQuestFile");
            if (file == null
                    || !file.getClass()
                            .getName()
                            .equals("dev.ftb.mods.ftbquests.quest.ServerQuestFile")
                    || nativeField(file, "server", MinecraftServer.class.getName()) != ended)
                return;
        }
        if (!ended.isRunning() && field.get() == previous) {
            type.getMethod("markDirty").invoke(null);
            if (field.get() != null)
                throw new IllegalStateException("Native quest lookup invalidation did not finish");
            count("questkill.nativeStoppedLookupInvalidations");
        }
        // Native shouldProcessKill/getQuestFile lazily recollects the current server's tasks.
        // Saved quest definitions and all team/player progress are left untouched.
    }
}
