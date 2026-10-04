package org.marj4n.smooth_fix.benchmark;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.marj4n.smooth_fix.SmoothFix;
import org.marj4n.smooth_fix.diagnostics.ClientFrameProfiler;
import org.marj4n.smooth_fix.diagnostics.MemoryReport;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Client half of the real-world benchmark, including actual inventory/container/EMI UI actions. */
public final class AdvancedClientBenchmark {
    private static final Gson GSON = new Gson();
    private static Session active;
    private static boolean installed;

    private AdvancedClientBenchmark() { }

    public static void install() {
        if (installed) return;
        installed = true;
        ClientPlayNetworking.registerGlobalReceiver(BenchmarkProtocol.ADV_CONTROL, (client, handler, buf, sender) -> {
            int action = buf.readUnsignedByte(); UUID run = buf.readUuid(); int phase = buf.readInt();
            String name = buf.readString(); String mode = buf.readString(); String dimension = buf.readString();
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble(); long targetLong = buf.readLong(); int seconds = buf.readInt();
            client.execute(() -> {
                if (action == 1) {
                    if (active != null) active.finishProfiler(client, false);
                    active = new Session(run, phase, name, mode, dimension, x, y, z,
                            targetLong == Long.MIN_VALUE ? null : BlockPos.fromLong(targetLong), seconds);
                } else if (active != null && active.run.equals(run) && active.phase == phase) {
                    if (action == 2) active.finishProfiler(client, true);
                    else if (action == 0) { active.finishProfiler(client, false); release(client); active = null; }
                }
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(AdvancedClientBenchmark::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (active != null) { active.finishProfiler(client, false); release(client); active = null; }
        });
    }

    public static boolean stop(MinecraftClient client) {
        Session s = active;
        if (s == null) return false;
        respond(s.run, s.phase, 4, "manual_client_stop");
        s.finishProfiler(client, false); release(client); active = null;
        return true;
    }

    private static void respond(UUID run, int phase, int action, String data) {
        if (!ClientPlayNetworking.canSend(BenchmarkProtocol.ADV_RESPONSE)) return;
        var buf = PacketByteBufs.create();
        buf.writeUuid(run); buf.writeInt(phase); buf.writeByte(action); buf.writeString(data == null ? "" : data, 24000);
        ClientPlayNetworking.send(BenchmarkProtocol.ADV_RESPONSE, buf);
    }

    private static void tick(MinecraftClient client) {
        Session s = active;
        if (s == null) return;
        long now = System.nanoTime();
        if (now - s.lastHeartbeat > 1_000_000_000L) { s.lastHeartbeat = now; respond(s.run, s.phase, 3, ""); }
        if (client.player == null || client.world == null || client.interactionManager == null) return;
        if (!client.world.getRegistryKey().getValue().toString().equals(s.dimension)) return;

        if (!s.ready) {
            if (!client.isWindowFocused()) return;
            double dx=client.player.getX()-s.x, dz=client.player.getZ()-s.z;
            if (dx*dx+dz*dz>4096.0) return;
            if (!client.world.getChunkManager().isChunkLoaded(((int)Math.floor(s.x))>>4, ((int)Math.floor(s.z))>>4)) return;
            if (client.currentScreen != null) client.setScreen(null);
            if (ClientFrameProfiler.isActive() || !ClientFrameProfiler.start(client, Math.max(30, s.seconds + 20))) {
                s.fail(client, "frame_profiler_conflict"); return;
            }
            s.ready = true; s.started = now;
            respond(s.run, s.phase, 1, "");
        }

        try {
            switch (s.mode) {
                case "route", "cold_route", "structure" -> s.route(client, now);
                case "container", "lootr" -> s.container(client, now);
                case "inventory" -> s.inventory(client, now);
                case "emi" -> s.emi(client, now);
                case "combat" -> s.combat(client, now);
                case "tnt" -> s.watchTarget(client);
                default -> s.fail(client, "unknown_mode:" + s.mode);
            }
        } catch (Throwable t) {
            SmoothFix.LOGGER.error("Advanced client benchmark stage {} failed", s.name, t);
            s.fail(client, t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        }
    }

    private static void release(MinecraftClient client) { KeyBinding.updatePressedStates(); }

    private static final class Session {
        final UUID run; final int phase; final String name, mode, dimension; final BlockPos target; final int seconds;
        final double x, y, z;
        long started, lastHeartbeat, lastInteract, emiStarted;
        boolean ready, actionDone, failed, profilerFinished;
        String oldEmiSearch;
        Object emiCraftingTable;
        int emiIndexSize, emiResults;

        Session(UUID run, int phase, String name, String mode, String dimension, double x, double y, double z, BlockPos target, int seconds) {
            this.run=run; this.phase=phase; this.name=name; this.mode=mode; this.dimension=dimension;
            this.x=x; this.y=y; this.z=z; this.target=target; this.seconds=seconds;
        }

        void route(MinecraftClient client, long now) {
            double t=(now-started)/1e9;
            double radius="cold_route".equals(mode)?30:22;
            double tx=x+Math.cos(t*.32)*radius, tz=z+Math.sin(t*.32)*radius;
            double dx=tx-client.player.getX(), dz=tz-client.player.getZ();
            client.player.setYaw((float)(Math.toDegrees(Math.atan2(dz,dx))-90)); client.player.setPitch(18);
            client.options.forwardKey.setPressed(true); client.options.sprintKey.setPressed(true);
            client.options.jumpKey.setPressed(client.player.horizontalCollision);
        }

        void container(MinecraftClient client, long now) {
            if (actionDone || target == null) return;
            if (client.currentScreen instanceof HandledScreen<?> handled && !(client.currentScreen instanceof InventoryScreen)) {
                ScreenHandler sh = handled.getScreenHandler();
                for (int i=0;i<sh.getStacks().size();i++) {
                    Slot slot=sh.getSlot(i);
                    if (slot.inventory != client.player.getInventory() && slot.hasStack() && slot.canTakeItems(client.player)) {
                        client.interactionManager.clickSlot(sh.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, client.player);
                        actionDone=true;
                        respond(run,phase,5,GSON.toJson(Map.of("screen",client.currentScreen.getClass().getName(),"slot",slot.id,"action","QUICK_MOVE")));
                        return;
                    }
                }
            }
            if (now-lastInteract>1_500_000_000L) {
                lastInteract=now; face(client,target);
                BlockHitResult hit=new BlockHitResult(Vec3d.ofCenter(target),Direction.UP,target,false);
                client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,hit);
            }
        }

        void inventory(MinecraftClient client, long now) {
            if (actionDone) return;
            if (!(client.currentScreen instanceof InventoryScreen)) { client.setScreen(new InventoryScreen(client.player)); return; }
            if (!(client.currentScreen instanceof HandledScreen<?> handled)) return;
            ScreenHandler sh=handled.getScreenHandler();
            int next=(client.player.getInventory().selectedSlot+1)%9;
            Slot source=null;
            for(int i=0;i<sh.getStacks().size();i++) {
                Slot slot=sh.getSlot(i);
                if(slot.inventory==client.player.getInventory() && slot.getIndex()==9){source=slot;break;}
            }
            if(source==null) return;
            client.interactionManager.clickSlot(sh.syncId,source.id,next,SlotActionType.SWAP,client.player);
            client.player.getInventory().selectedSlot=next;
            client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(next));
            actionDone=true;
            respond(run,phase,5,GSON.toJson(Map.of("screen","InventoryScreen","swapInventoryIndex",9,"selectedSlot",next,"action","SWAP+UpdateSelectedSlotC2S")));
        }

        void emi(MinecraftClient client, long now) throws Exception {
            if (actionDone) return;
            if (!FabricLoader.getInstance().isModLoaded("emi")) { fail(client,"emi_mod_unavailable"); return; }
            Class<?> api=Class.forName("dev.emi.emi.api.EmiApi");
            if (emiStarted==0) {
                Object index=api.getMethod("getIndexStacks").invoke(null);
                if (!(index instanceof List<?> list) || list.isEmpty()) return;
                Object manager=api.getMethod("getRecipeManager").invoke(null);
                if(manager==null)return;
                emiIndexSize=list.size();
                emiCraftingTable=findEmiStack(list,"minecraft:crafting_table");
                if(emiCraftingTable==null){fail(client,"emi_crafting_table_missing_from_index");return;}
                oldEmiSearch=String.valueOf(api.getMethod("getSearchText").invoke(null));
                if(!(client.currentScreen instanceof InventoryScreen))client.setScreen(new InventoryScreen(client.player));
                api.getMethod("setSearchText",String.class).invoke(null,"crafting");
                Class<?> search=Class.forName("dev.emi.emi.search.EmiSearch");
                search.getMethod("search",String.class).invoke(null,"crafting");
                emiStarted=now;
                return;
            }
            Class<?> search=Class.forName("dev.emi.emi.search.EmiSearch");
            Field thread=search.getField("searchThread");
            if(thread.get(null)!=null)return;
            Object results=search.getField("stacks").get(null);
            if(!(results instanceof List<?> list) || list.isEmpty()){fail(client,"emi_search_returned_no_results");return;}
            emiResults=list.size();
            if(!client.currentScreen.getClass().getName().equals("dev.emi.emi.screen.RecipeScreen")) {
                Method display=null;
                for(Method m:api.getMethods())if(m.getName().equals("displayRecipes")&&m.getParameterCount()==1){display=m;break;}
                if(display==null){fail(client,"emi_displayRecipes_api_missing");return;}
                display.invoke(null,emiCraftingTable);
                return;
            }
            actionDone=true;
            respond(run,phase,5,GSON.toJson(Map.of(
                    "ready",true,"indexSize",emiIndexSize,"query","crafting","searchResults",emiResults,
                    "recipeScreen",client.currentScreen.getClass().getName(),"target","minecraft:crafting_table"
            )));
        }

        Object findEmiStack(List<?> list,String wanted) {
            for(Object o:list) try {
                Object id=o.getClass().getMethod("getId").invoke(o);
                if(id!=null && wanted.equals(id.toString()))return o;
            } catch(Throwable ignored) { }
            return null;
        }

        void combat(MinecraftClient client,long now) {
            if(target==null)return;
            MobEntity targetMob=null; double nearestToMarker=16.0;
            double tx=target.getX()+.5, ty=target.getY()+.5, tz=target.getZ()+.5;
            for(Entity e:client.world.getEntities()) if(e instanceof MobEntity mob && mob.isAlive()) {
                double dx=mob.getX()-tx, dy=mob.getY()-ty, dz=mob.getZ()-tz;
                double d=dx*dx+dy*dy+dz*dz;
                if(d<nearestToMarker){nearestToMarker=d;targetMob=mob;}
            }
            if(targetMob!=null){
                double playerDistance=targetMob.squaredDistanceTo(client.player);
                face(client,targetMob.getBlockPos());
                if(playerDistance<=16.0 && client.player.getAttackCooldownProgress(0)>=.9f){
                    client.interactionManager.attackEntity(client.player,targetMob);
                    client.player.swingHand(Hand.MAIN_HAND);
                } else if(playerDistance>9.0){
                    client.options.forwardKey.setPressed(true);
                    client.options.sprintKey.setPressed(true);
                }
            }
        }

        void watchTarget(MinecraftClient client){ if(target!=null)face(client,target); }

        void face(MinecraftClient client,BlockPos pos) {
            double dx=pos.getX()+.5-client.player.getX(), dy=pos.getY()+.5-client.player.getEyeY(), dz=pos.getZ()+.5-client.player.getZ();
            double horizontal=Math.sqrt(dx*dx+dz*dz);
            client.player.setYaw((float)(Math.toDegrees(Math.atan2(dz,dx))-90));
            client.player.setPitch((float)-Math.toDegrees(Math.atan2(dy,horizontal)));
        }

        void restoreEmiSearch() {
            if(oldEmiSearch==null)return;
            try {Class.forName("dev.emi.emi.api.EmiApi").getMethod("setSearchText",String.class).invoke(null,oldEmiSearch);}
            catch(Throwable ignored) { }
            oldEmiSearch=null;
        }

        void fail(MinecraftClient client,String reason) {
            if(failed)return;failed=true;
            respond(run,phase,0,reason); restoreEmiSearch(); release(client);
            if(client.player!=null)client.player.sendMessage(Text.literal("Smooth Fix advanced stage failed: "+reason),false);
        }

        void finishProfiler(MinecraftClient client,boolean sendReport) {
            if(profilerFinished)return;profilerFinished=true;
            restoreEmiSearch(); release(client);
            if(("container".equals(mode)||"lootr".equals(mode)||"inventory".equals(mode)||"emi".equals(mode)) && client.currentScreen!=null) client.setScreen(null);
            Map<String,Object> report;
            if(ClientFrameProfiler.isActive())report=new LinkedHashMap<>(ClientFrameProfiler.finishNow());
            else report=new LinkedHashMap<>(Map.of("error","frame profiler was not active"));
            report.put("runId",run.toString());report.put("phase",phase);report.put("stage",name);report.put("controller",mode);report.put("actionDone",actionDone);
            if(sendReport)try{
                String file=MemoryReport.write("benchmark_client_advanced",report).toString();
                Map<String,Object> summary=new LinkedHashMap<>();summary.put("localReportFile",file);summary.put("actionDone",actionDone);
                for(String k:List.of("elapsedSeconds","frameIntervals","frameWorkBeforePresent","heapBytes","garbageCollectors","garbageCollectorDeltas","memoryAtStart","sceneAtStart","sceneAtEnd","error"))if(report.containsKey(k))summary.put(k,report.get(k));
                respond(run,phase,2,GSON.toJson(summary));
                SmoothFix.LOGGER.info("Smooth Fix advanced client phase {} report: {}",phase,file);
            }catch(Throwable t){SmoothFix.LOGGER.error("Could not save advanced client benchmark report",t);respond(run,phase,2,GSON.toJson(Map.of("error",t.toString())));}
            if(active==this) active=null;
        }
    }
}
