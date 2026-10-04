package org.marj4n.smooth_fix.benchmark;
import com.google.gson.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.marj4n.smooth_fix.SmoothFix;
import org.marj4n.smooth_fix.diagnostics.*;
import org.marj4n.smooth_fix.mixin.client.HandledScreenPositionAccess;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Native movement/interaction/UI driver, with observed completion hooks and explicit failed coverage. */
public final class AdvancedClient {
    private static final Gson GSON=new Gson();
    private static volatile Session active;
    private static EmiBenchmarkAdapter sharedEmi;
    private static UUID sharedEmiRun;
    private AdvancedClient() { }
    public static boolean controlsMovement(){return active!=null;}
    public static Boolean controlledKey(KeyBinding key) {
        Session s=active;if(s==null || s.forward==null)return null;
        if(key==s.forward)return s.input.forward();
        if(key==s.jump)return s.input.jump();
        if(key==s.sprint)return s.input.sprint();
        if(key==s.back || key==s.left || key==s.right || key==s.sneak || key==s.attack || key==s.use)return false;
        return null;
    }
    private static void updateProgress(MinecraftClient client,Session s,long now) {
        if(client==null || now-s.hudAt<1_000_000_000L)return;s.hudAt=now;
        String state;
        if(s.searchProgress!=null && !s.prepared){
            int elapsed=s.searchProgress.has("elapsedMs")?(int)(s.searchProgress.get("elapsedMs").getAsDouble()/1000):0;
            state="Cari radius "+s.searchProgress.get("radiusBlocks").getAsInt()+" blok | kandidat "+(s.searchProgress.has("candidateChunksChecked")?s.searchProgress.get("candidateChunksChecked").getAsInt():0)+" | "+elapsed+" dtk"+(s.optionalSearchSeconds>0?" / batas "+s.optionalSearchSeconds:" ");
        }else if(!s.prepared)state="Menyiapkan workload di server";
        else if(client.player==null || client.world==null)state="Menunggu world dan player";
        else if(!s.ready)state=client.currentScreen!=null?"Tutup layar/menu agar tes mulai":!client.isWindowFocused()?"Fokuskan jendela Minecraft":"Menunggu chunk dan posisi player siap";
        else {
            int remaining=Math.max(0,s.seconds-(int)((now-s.readyAt)/1e9));
            state="Stress "+remaining+" dtk tersisa | "+(AdvancedStageInfo.walking(s.mode)?s.movementNote:s.ui()?"Aksi UI berulang":"Mengamati workload native");
        }
        s.progressState=state;
        List<String> lines=List.of("Smooth Fix Advanced "+(s.phase+1)+"/"+s.totalStages+" : "+s.name,AdvancedStageInfo.task(s.mode),state,"Berikutnya: "+s.nextStage+" | /smoothfixc stopstress");
        int limit=Math.max(80,client.getWindow().getScaledWidth()-24);List<String> trimmed=new ArrayList<>();s.hudWidth=0;
        for(String line:lines){String value=client.textRenderer.trimToWidth(line,limit);trimmed.add(value);s.hudWidth=Math.max(s.hudWidth,client.textRenderer.getWidth(value));}s.hudLines=trimmed;
    }
    private static void renderProgress(DrawContext context) {
        Session s=active;if(s==null || s.hudLines.isEmpty())return;MinecraftClient client=MinecraftClient.getInstance();
        context.fill(5,5,s.hudWidth+15,13+s.hudLines.size()*11,0xB0000000);
        for(int i=0;i<s.hudLines.size();i++)context.drawText(client.textRenderer,s.hudLines.get(i),10,10+i*11,i==0?0x9AFF9A:0xFFFFFF,true);
    }
    public static void install() {
        ClientPlayNetworking.registerGlobalReceiver(BenchmarkProtocol.ADVANCED_CONTROL,(client,handler,buf,sender)->{
            JsonObject data=JsonParser.parseString(buf.readString(24000)).getAsJsonObject();client.execute(()->control(client,data));
        });
        ClientTickEvents.START_CLIENT_TICK.register(AdvancedClient::tick);
        HudRenderCallback.EVENT.register((context, delta)->renderProgress(context));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{if(active!=null){active.error="owner_disconnected";finish(client,true);}ClientBenchmarkReports.end("disconnected");});
    }
    private static void control(MinecraftClient client,JsonObject data) {
        int action=data.get("action").getAsInt();UUID run=UUID.fromString(data.get("run").getAsString());int phase=data.get("phase").getAsInt();
        if(action==0 && active==null && run.equals(sharedEmiRun)){if(sharedEmi!=null)sharedEmi.restore();sharedEmi=null;sharedEmiRun=null;return;}
        if(action==1) {
            if(active!=null)finish(client,false);
            if(ClientFrameProfiler.isActive()){reply(run,phase,0,"manual/legacy profile running");return;}
            try{ClientBenchmarkReports.begin(run,"actual_world_advanced_v2");}catch(Exception e){reply(run,phase,0,"run report could not start: "+e);return;}
            active=new Session(data);active.bind(client);if(!ClientFrameProfiler.startBenchmark(client,600)){stopWithReason(client,"could not start frame recorder",0);return;}active.recording=true;
        } else if(active!=null && active.run.equals(run)) {
            if(action==0){finish(client,true);return;}
            if(active.phase!=phase)return;
            if(action==2){finish(client,false);return;}
            if(action==3 || action==6)active.update(data);
        }
    }
    private static void reply(UUID run,int phase,int action,String data) {
        if(!ClientPlayNetworking.canSend(BenchmarkProtocol.ADVANCED_RESPONSE))return;
        var buf=PacketByteBufs.create();buf.writeUuid(run);buf.writeInt(phase);buf.writeByte(action);buf.writeString(data,24000);ClientPlayNetworking.send(BenchmarkProtocol.ADVANCED_RESPONSE,buf);
    }
    public static boolean stop(MinecraftClient client){if(active==null)return false;stopWithReason(client,"explicit client stop",4);return true;}
    private static void stopWithReason(MinecraftClient client,String reason,int action){Session s=active;if(s==null)return;s.error=reason;reply(s.run,s.phase,action,reason);finish(client,true);}
    private static void finish(MinecraftClient client,boolean endRun) {
        Session s=active;if(s==null){if(endRun)ClientBenchmarkReports.end("awaiting_server_result");
        if(endRun && sharedEmi!=null){sharedEmi.restore();sharedEmi=null;}return;}
        active=null;
        try {
            Map<String,Object> report=new LinkedHashMap<>(s.recording?ClientFrameProfiler.finishNow():Map.of());report.put("runId",s.run.toString());report.put("phase",s.phase);report.put("stage",s.name);report.put("mode",s.mode);report.put("requestedActionSeconds",s.seconds);report.put("actionWindowSeconds",s.readyAt==0?0:(System.nanoTime()-s.readyAt)/1e9);report.put("actions",s.metrics.snapshot());report.put("successfulActions",s.metrics.successes());report.put("error",s.error);report.put("observations",s.observations);
            report.put("actionMeasurementNote","Native client interaction attempts and observed first Screen.renderWithTooltip completion / accepted EMI search results. First-render timings include scheduling/network waits, exclude GPU completion. API invocation latency is reported separately. No fake inventory linear-scan benchmark or claim that all mod-specific mechanics were exercised.");
            report.put("navigation",Map.of("horizontalDistanceBlocks",s.navigator.distance(),"maximumDisplacementBlocks",s.navigator.extent(),"state",s.navigator.status(),"strategy","bounded incremental ground pathfinding; no sneak; step-only native jumps","horizontalCollisionAtEnd",client!=null && client.player!=null && client.player.horizontalCollision));
            report.put("workloadValidated",s.validated() && s.error==null);
            String path=ClientBenchmarkReports.stage(s.run,s.phase,report);Map<String,Object> summary=new LinkedHashMap<>();summary.put("localReportFile",path);
            for(String key:List.of("runId","phase","stage","mode","elapsedSeconds","frameIntervals","frameWorkBeforePresent","garbageCollectorDeltas","sceneAtStart","sceneAtEnd","actions","successfulActions","workloadValidated","navigation","error","status"))if(report.containsKey(key))summary.put(key,report.get(key));
            String json=GSON.toJson(summary);if(json.length()>24000){summary.put("actions",Map.of("successfulActions",s.metrics.successes(),"detail","Full action report retained in client file"));json=GSON.toJson(summary);}reply(s.run,s.phase,2,json);
            SmoothFix.LOGGER.info("Advanced benchmark phase {}: {}",s.phase,path);
        }catch(Exception e){SmoothFix.LOGGER.error("Advanced client report failed",e);}
        if(s.owns(client.currentScreen) && client.player!=null)client.player.closeHandledScreen();
        if(client!=null && client.player!=null)client.player.setSprinting(false);
        if(endRun)ClientBenchmarkReports.end(s.error==null?"awaiting_server_result":s.error);
        if(endRun && sharedEmi!=null){sharedEmi.restore();sharedEmi=null;sharedEmiRun=null;}
    }
    public static void screenRendered(Screen screen) {
        Session s=active;if(s==null)return;
        if(s.pendingRenderAt!=0 && s.owns(screen)) {
            String detail=screen.getClass().getName();
            if(s.pendingRenderKind.equals("emi_recipe_first_render"))try{
                detail=sharedEmi==null?null:sharedEmi.renderedRecipe(screen);
                if(detail==null)return;
                s.metrics.success("emi_recipe_output_verified",s.pendingRenderAt,detail);
            }catch(Exception e){s.error="EMI recipe verification incompatible: "+e;return;}
            s.metrics.success(s.pendingRenderKind,s.pendingRenderAt,detail);s.pendingRenderAt=0;
        }
        s.metrics.count("screen_render_completions");
    }
    public static long tooltipStarted(){Session s=active;return s!=null && s.ui()?System.nanoTime():0;}
    public static void tooltipEnded(long started){Session s=active;if(started!=0 && s!=null)s.metrics.success("native_item_tooltip_cpu_work",started,"DrawContext.drawItemTooltip");}
    public static void searchStarted(String query){Session s=active;if(s!=null && s.mode.equals("emi_search") && query.equals(s.query)){s.queryScheduled=true;s.metrics.count("emi_search_worker_started");}}
    public static void searchApplied(Object worker,List<?> result) {
        Session s=active;if(s==null || !s.mode.equals("emi_search"))return;
        try{String query=EmiBenchmarkAdapter.acceptedQuery(worker,result);if(active==s && s.queryScheduled && query!=null && query.equals(s.query) && s.queryAt!=0){long at=s.queryAt;s.queryAt=0;s.metrics.success("emi_query_result_published",at,query+"; matches="+result.size());s.pendingRenderKind="emi_query_first_render";s.pendingRenderAt=at;}}
        catch(Exception e){s.error="EMI completion hook incompatible: "+e;}
    }
    private static void tick(MinecraftClient client) {
        Session s=active;if(s==null)return;long now=System.nanoTime();s.input=GroundNavigator.Input.NONE;
        updateProgress(client,s,now);
        try {
            if(now-s.heartbeat>1_000_000_000L){s.heartbeat=now;reply(s.run,s.phase,3,"");}
            boolean worldReady=client.player!=null && client.world!=null && client.player.getWorld()==client.world && client.world.getRegistryKey().getValue().toString().equals(s.dimension);
            if(!s.ready){if(now-s.preparationProgressAt>125_000_000_000L){stopWithReason(client,"client loading/preparation timeout",0);return;}if(!s.prepared || !worldReady || client.currentScreen!=null || !client.isWindowFocused() || client.player.squaredDistanceTo(s.x,s.y,s.z)>4096 || !client.world.isChunkLoaded(client.player.getBlockPos()))return;s.ready=true;s.readyAt=now;reply(s.run,s.phase,1,"");}
            if(!client.isWindowFocused()){stopWithReason(client,"window lost focus",0);return;}
            if(!worldReady){if(now-s.worldMissingAt>120_000_000_000L && s.worldMissingAt!=0)stopWithReason(client,"world transition timeout",0);else if(s.worldMissingAt==0)s.worldMissingAt=now;return;}s.worldMissingAt=0;
            if(client.currentScreen!=null && !s.owns(client.currentScreen)){stopWithReason(client,"unexpected screen: "+client.currentScreen.getClass().getName(),0);return;}
            double t=(now-s.readyAt)/1e9;
            if(s.swapAt!=0 && now-s.swapAt>5_000_000_000L){s.metrics.count("inventory_swap_timeout");s.error="Inventory slot update was not observed within five seconds";s.swapAt=0;}
            if(s.swapAt!=0 && !net.minecraft.item.ItemStack.areEqual(s.swapBefore,client.player.getInventory().getStack(0))){s.metrics.success("inventory_swap_observed",s.swapAt,"hotbar content changed after native slot action");s.swapAt=0;}
            if(now-s.observedAt>1_000_000_000L){s.observedAt=now;if(s.observations.size()<360){s.observations.add(Map.of("movementState",s.movementNote,"walkedBlocks",s.navigator.distance(),"flying",client.player.getAbilities().flying,"sneakInput",s.input==null?false:controlledKey(s.sneak)));s.observations.add(Map.of("seconds",t,"controllerStep",s.step,"screen",client.currentScreen==null?"none":client.currentScreen.getClass().getName(),"selectedSlot",client.player.getInventory().selectedSlot,"swapPending",s.swapAt!=0,"particles",client.particleManager.getDebugString(),"entities",client.worldRenderer.getEntitiesDebugString(),"x",client.player.getX(),"y",client.player.getY(),"z",client.player.getZ()));}}
            if(s.ui()) {driveUi(client,s,now);return;}
            if(s.mode.equals("tnt")){client.player.setYaw((float)(t*24));client.player.setPitch(40);s.metrics.count("tnt_observer_camera_ticks");return;}
            if(s.mode.equals("panorama") || s.mode.equals("save") || s.mode.equals("teleport")){client.player.setYaw((float)(t*25));client.player.setPitch(20);s.metrics.count("world_camera_ticks");return;}
            double tx,tz;
            if(Set.of("route","cold_route","nether","end","break").contains(s.mode)){
                tx=s.x+s.routeLeg*12;tz=s.z+Math.sin(s.routeLeg*.4)*12;
                if(Math.hypot(tx-client.player.getX(),tz-client.player.getZ())<2.5)s.routeLeg++;
            }else {
                double angle=s.routeLeg*Math.PI/4;tx=s.x+Math.cos(angle)*12;tz=s.z+Math.sin(angle)*12;
                if(Math.hypot(tx-client.player.getX(),tz-client.player.getZ())<2.5)s.routeLeg++;
            }
            Entity combatTarget=null;
            if(s.mode.equals("combat")) {
                double nearest=Double.MAX_VALUE;
                for(int id:s.entities){Entity e=client.world.getEntityById(id);if(e instanceof MobEntity mob && mob.isAlive() && e.squaredDistanceTo(client.player)<nearest){nearest=e.squaredDistanceTo(client.player);combatTarget=e;}}
                if(combatTarget!=null){tx=combatTarget.getX();tz=combatTarget.getZ();}
            }
            if(!client.player.isOnGround() && client.player.getAbilities().flying){
                s.movementNote="Menunggu mode berjalan dari server; tidak menekan shift/jump";
                s.metrics.count("navigation_flying_ability_conflict");
            }else {
                s.input=s.navigator.tick(client.world,client.player,tx,tz,s.metrics);s.movementNote=s.navigator.status();
                if(combatTarget!=null && combatTarget.squaredDistanceTo(client.player)<9){
                    Vec3d aim=combatTarget.getEyePos().subtract(client.player.getEyePos());
                    client.player.setYaw((float)(Math.toDegrees(Math.atan2(aim.z,aim.x))-90));
                    client.player.setPitch((float)-Math.toDegrees(Math.atan2(aim.y,Math.hypot(aim.x,aim.z))));
                    s.input=GroundNavigator.Input.NONE;
                }else client.player.setPitch(s.mode.equals("break")?65:12);
                s.metrics.count("world_route_ticks");
            }
            if(s.mode.equals("combat") && client.interactionManager!=null && client.player.getAttackCooldownProgress(0)>=1) {
                Entity target=null;double distance=9;
                for(int id:s.entities){Entity e=client.world.getEntityById(id);if(e instanceof MobEntity mob && mob.isAlive() && e.squaredDistanceTo(client.player)<distance){target=e;distance=e.squaredDistanceTo(client.player);}}
                if(target!=null){client.interactionManager.attackEntity(client.player,target);client.player.swingHand(Hand.MAIN_HAND);s.metrics.count("native_attacks_sent");}
            }
            if(s.mode.equals("break") && client.interactionManager!=null && client.crosshairTarget instanceof BlockHitResult hit && hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK && client.player.squaredDistanceTo(Vec3d.ofCenter(hit.getBlockPos()))<25) {
                client.interactionManager.attackBlock(hit.getBlockPos(),hit.getSide());client.interactionManager.updateBlockBreakingProgress(hit.getBlockPos(),hit.getSide());client.player.swingHand(Hand.MAIN_HAND);s.metrics.count("native_break_attempts");
            }
            if(now-s.routeSuccessAt>1_000_000_000L){s.routeSuccessAt=now;if(s.routePosition!=null && s.routePosition.squaredDistanceTo(client.player.getPos())>.25)s.metrics.success("world_route_observation",now,s.dimension);s.routePosition=client.player.getPos();}
        } catch(Exception e){if(s.mode.startsWith("emi_")){s.error="incompatible EMI adapter: "+e;reply(s.run,s.phase,5,s.error);finish(client,false);}else stopWithReason(client,"controller failure: "+e,0);}
    }
    private static void driveUi(MinecraftClient client,Session s,long now)throws Exception {
        if(s.mode.equals("chest") || s.mode.equals("lootr")) {
            if(s.container==null)return;
            if(client.currentScreen instanceof HandledScreen<?> screen) {
                hover(client,screen,(s.step%Math.max(1,screen.getScreenHandler().slots.size())));
                if(now-s.uiAt>1_000_000_000L && !s.transferred){int slots=Math.max(0,screen.getScreenHandler().slots.size()-36);for(int i=0;i<slots;i++)if(screen.getScreenHandler().getSlot(i).hasStack()){client.interactionManager.clickSlot(screen.getScreenHandler().syncId,i,0,SlotActionType.QUICK_MOVE,client.player);s.metrics.count("native_loot_transfer_attempts");break;}s.transferred=true;}
                if(now-s.uiAt>2_500_000_000L){client.player.closeHandledScreen();s.uiAt=now;s.step++;}
            }else if(now-s.uiAt>700_000_000L) {
                s.uiAt=now;s.transferred=false;s.pendingRenderKind=s.step==0?"container_first_open":"container_repeat_open";s.pendingRenderAt=now;
                client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(s.container),Direction.UP,s.container,false));s.metrics.count("native_container_use_attempts");
            }
            return;
        }
        if(client.currentScreen==null){s.pendingRenderKind="inventory_open_first_render";s.pendingRenderAt=now;client.setScreen(new InventoryScreen(client.player));s.uiAt=now;return;}
        if(client.currentScreen instanceof HandledScreen<?> screen) {
            hover(client,screen,9+(s.step%27));
            if(s.mode.equals("inventory") && s.swapAt==0 && now-s.uiAt>700_000_000L){s.swapBefore=client.player.getInventory().getStack(0).copy();s.swapAt=now;client.interactionManager.clickSlot(screen.getScreenHandler().syncId,9+s.step%27,0,SlotActionType.SWAP,client.player);client.player.getInventory().selectedSlot=s.step%9;client.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(client.player.getInventory().selectedSlot));s.metrics.count("native_inventory_swap_attempts");s.metrics.count("native_selected_slot_updates");s.uiAt=now;s.step++;if(s.step%6==0)client.player.closeHandledScreen();}
        }
        if(s.mode.startsWith("emi_")) {
            if(sharedEmi==null){if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("emi")){s.error="skipped: EMI not installed";reply(s.run,s.phase,5,s.error);finish(client,false);return;}sharedEmi=new EmiBenchmarkAdapter();sharedEmiRun=s.run;s.metrics.count("emi_adapter_linked");s.observations.add(Map.of("emiIndexEntries",sharedEmi.indexSize(),"emiRecipes",sharedEmi.recipeCount()));}
            if(sharedEmi.indexSize()==0 || sharedEmi.recipeCount()==0){s.metrics.count("emi_waiting_for_index_and_recipes");if(now-s.readyAt>45_000_000_000L){s.error="skipped: EMI index/recipe manager did not become ready within 45 seconds";reply(s.run,s.phase,5,s.error);finish(client,false);}return;}
            if(s.queryAt!=0 && now-s.queryAt>5_000_000_000L){s.metrics.count("emi_query_timeout");s.error="EMI query did not produce an observed accepted result";s.queryAt=0;}
            if(now-s.uiAt>2_000_000_000L && s.queryAt==0) {
                s.uiAt=now;
                if(s.mode.equals("emi_search")){s.query=s.queries.get(s.step++%s.queries.size());s.queryAt=now;s.queryScheduled=false;long call=System.nanoTime();sharedEmi.search(s.query);s.metrics.success("emi_set_text_api_call",call,s.query);}
                else {s.pendingRenderKind="emi_recipe_first_render";s.pendingRenderAt=now;if(!sharedEmi.displayRecipe(s.step++)){s.pendingRenderAt=0;s.error="skipped: EMI recipe manager empty";}else s.metrics.count("native_emi_recipe_display_attempts");}
            }
            if(s.mode.equals("emi_recipe") && s.pendingRenderAt==0 && client.currentScreen!=null)client.currentScreen.mouseScrolled(client.currentScreen.width*.5,client.currentScreen.height*.5,(s.step%2==0?1:-1));
        }
    }
    private static void hover(MinecraftClient client,HandledScreen<?> screen,int index) {
        if(!(screen instanceof HandledScreenPositionAccess position) || screen.getScreenHandler().slots.isEmpty())return;
        var slot=screen.getScreenHandler().getSlot(Math.floorMod(index,screen.getScreenHandler().slots.size()));
        double x=(position.smoothfix$getX()+slot.x+8)*client.getWindow().getWidth()/(double)client.getWindow().getScaledWidth(),y=(position.smoothfix$getY()+slot.y+8)*client.getWindow().getHeight()/(double)client.getWindow().getScaledHeight();
        GLFW.glfwSetCursorPos(client.getWindow().getHandle(),x,y);
    }
    private static final class Session {
        final UUID run;final int phase;final String name,mode,dimension;final long createdAt=System.nanoTime();final int seconds;
        final ActionMetrics metrics=new ActionMetrics();final List<Map<String,Object>> observations=new ArrayList<>();
        final List<String> queries=new ArrayList<>();final List<Integer> entities=new ArrayList<>();
        double x,y,z;BlockPos container;Vec3d routePosition;net.minecraft.item.ItemStack swapBefore;long swapAt;boolean prepared,ready,recording,transferred;
        final GroundNavigator navigator=new GroundNavigator();
        GroundNavigator.Input input=GroundNavigator.Input.NONE;
        KeyBinding forward,back,left,right,jump,sneak,sprint,attack,use;
        int routeLeg=1,totalStages=21,optionalSearchSeconds;String nextStage="selesai",movementNote="Menyiapkan world",progressState="";
        JsonObject searchProgress;List<String> hudLines=List.of();int hudWidth;long hudAt;
        volatile boolean queryScheduled;volatile long queryAt,pendingRenderAt;volatile String query="",pendingRenderKind="",error;
        long readyAt,heartbeat,worldMissingAt,uiAt,observedAt,routeSuccessAt,preparationProgressAt=System.nanoTime();int step;
        Session(JsonObject data){seconds=data.has("seconds")?data.get("seconds").getAsInt():60;run=UUID.fromString(data.get("run").getAsString());phase=data.get("phase").getAsInt();name=data.get("stage").getAsString();mode=data.get("mode").getAsString();dimension=data.get("dimension").getAsString();for(JsonElement q:data.getAsJsonArray("queries"))queries.add(q.getAsString());update(data);}
        void bind(MinecraftClient client){if(client==null)return;var o=client.options;forward=o.forwardKey;back=o.backKey;left=o.leftKey;right=o.rightKey;jump=o.jumpKey;sneak=o.sneakKey;sprint=o.sprintKey;attack=o.attackKey;use=o.useKey;}
        void update(JsonObject data){if(data.has("optionalSearchSeconds"))optionalSearchSeconds=data.get("optionalSearchSeconds").getAsInt();if(data.has("totalStages"))totalStages=data.get("totalStages").getAsInt();if(data.has("nextStage"))nextStage=data.get("nextStage").getAsString();if(data.has("structureSearch"))searchProgress=data.getAsJsonObject("structureSearch");if(data.has("prepared") && data.get("prepared").getAsBoolean()){prepared=true;preparationProgressAt=System.nanoTime();}if(data.has("searchingStructure") && data.get("searchingStructure").getAsBoolean()){preparationProgressAt=System.nanoTime();metrics.count("structure_search_progress_packets");if(data.has("structureSearch") && observations.size()<360)observations.add(Map.of("structureSearch",GSON.fromJson(data.get("structureSearch"),Map.class)));}x=data.get("x").getAsDouble();y=data.get("y").getAsDouble();z=data.get("z").getAsDouble();entities.clear();for(JsonElement id:data.getAsJsonArray("entities"))entities.add(id.getAsInt());if(data.has("container")){var p=data.getAsJsonObject("container");container=new BlockPos(p.get("x").getAsInt(),p.get("y").getAsInt(),p.get("z").getAsInt());}}
        boolean ui(){return Set.of("inventory","emi_search","emi_recipe","chest","lootr").contains(mode);}
        boolean validated(){return switch(mode){case "emi_search"->metrics.observed("emi_query_result_published") && metrics.observed("emi_query_first_render");case "emi_recipe"->metrics.observed("emi_recipe_first_render") && metrics.observed("emi_recipe_output_verified");case "inventory"->metrics.observed("inventory_open_first_render") && metrics.observed("inventory_swap_observed") && metrics.observed("native_item_tooltip_cpu_work");case "chest","lootr"->metrics.observed("container_first_open") || metrics.observed("container_repeat_open");case "combat"->metrics.attempted("native_attacks_sent") && metrics.observed("world_route_observation");case "break"->metrics.attempted("native_break_attempts");case "tnt"->metrics.attempted("tnt_observer_camera_ticks");default->AdvancedStageInfo.walking(mode)?navigator.distance()>=12 && navigator.extent()>=(Set.of("route","cold_route","nether","end").contains(mode)?8:6) && metrics.countValue("navigation_progress_samples")>=5:metrics.attempted("world_camera_ticks");};}
        boolean owns(Screen screen){return screen!=null && ui() && (Set.of("chest","lootr").contains(mode)?screen instanceof HandledScreen<?> && !(screen instanceof InventoryScreen):screen instanceof InventoryScreen || mode.equals("emi_recipe") && screen.getClass().getName().equals("dev.emi.emi.screen.RecipeScreen"));}
    }
}
