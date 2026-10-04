package verification;
import verification.mixin.ClientAutoJumpAccess;
import com.google.gson.*;
import org.marj4n.smooth_fix.benchmark.*;
import java.lang.reflect.*;
import java.util.*;
/** Headless regression: real transformed UI/search classes, validation and stale worker handling. */
public final class AdvancedClientCheck {
    private static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    public static void run()throws Exception {
        for(String name:List.of(net.minecraft.client.gui.screen.Screen.class.getName(),net.minecraft.client.gui.DrawContext.class.getName(),net.minecraft.client.gui.screen.ingame.HandledScreen.class.getName(),"dev.emi.emi.search.EmiSearch"))Class.forName(name,false,AdvancedClientCheck.class.getClassLoader());
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        Class<?> search=Class.forName("dev.emi.emi.search.EmiSearch"),worker=Class.forName("dev.emi.emi.search.EmiSearch$SearchWorker");
        Constructor<?> constructor=worker.getDeclaredConstructor(String.class,List.class);constructor.setAccessible(true);Object accepted=constructor.newInstance("iron",List.of()),obsolete=constructor.newInstance("sword",List.of());
        List<?> result=new ArrayList<>(),staleResult=new ArrayList<>();field(search,"currentWorker").set(null,accepted);Method apply=search.getMethod("apply",worker,List.class);apply.invoke(null,obsolete,staleResult);
        if(EmiBenchmarkAdapter.acceptedQuery(obsolete,staleResult)!=null)throw new AssertionError("obsolete EMI result accepted");
        apply.invoke(null,accepted,result);if(!"iron".equals(EmiBenchmarkAdapter.acceptedQuery(accepted,result)))throw new AssertionError("accepted EMI result lost");
        Class<?> session=Class.forName("org.marj4n.smooth_fix.benchmark.AdvancedClient$Session");Constructor<?> newSession=session.getDeclaredConstructor(JsonObject.class);newSession.setAccessible(true);
        JsonObject data=JsonParser.parseString("{\"run\":\"00000000-0000-0000-0000-000000000001\",\"phase\":0,\"stage\":\"inventory\",\"mode\":\"inventory\",\"dimension\":\"minecraft:overworld\",\"x\":0,\"y\":100,\"z\":0,\"queries\":[\"iron\"],\"entities\":[],\"prepared\":false}").getAsJsonObject();
        Object s=newSession.newInstance(data);Method validated=session.getDeclaredMethod("validated"),update=session.getDeclaredMethod("update",JsonObject.class);validated.setAccessible(true);update.setAccessible(true);
        ActionMetrics metrics=(ActionMetrics)field(session,"metrics").get(s);metrics.success("inventory_open_first_render",System.nanoTime(),"fixture");metrics.count("native_inventory_swap_attempts");
        if((boolean)validated.invoke(s))throw new AssertionError("unacknowledged slot attempt counted as success");metrics.success("inventory_swap_observed",System.nanoTime(),"fixture");
        if((boolean)validated.invoke(s))throw new AssertionError("missing tooltip counted as success");metrics.success("native_item_tooltip_cpu_work",System.nanoTime(),"fixture");if(!(boolean)validated.invoke(s))throw new AssertionError("observed inventory workload rejected");
        if(field(session,"prepared").getBoolean(s))throw new AssertionError("stage ready before server preparation");data.addProperty("prepared",true);update.invoke(s,data);if(!field(session,"prepared").getBoolean(s))throw new AssertionError("prepared stage update lost");
        data.addProperty("mode","emi_search");Object emiSession=newSession.newInstance(data);field(session,"query").set(emiSession,"iron");field(session,"queryScheduled").setBoolean(emiSession,true);field(session,"queryAt").setLong(emiSession,System.nanoTime());field(AdvancedClient.class,"active").set(null,emiSession);
        Object sameQueryObsolete=constructor.newInstance("iron",List.of());List<?> sharedEmpty=List.of();search.getField("stacks").set(null,sharedEmpty);field(search,"currentWorker").set(null,accepted);
        apply.invoke(null,sameQueryObsolete,sharedEmpty);ActionMetrics emiMetrics=(ActionMetrics)field(session,"metrics").get(emiSession);if(emiMetrics.observed("emi_query_result_published"))throw new AssertionError("obsolete same-query empty-list worker counted as published");
        apply.invoke(null,accepted,sharedEmpty);if(!emiMetrics.observed("emi_query_result_published"))throw new AssertionError("actual empty result publication hook missing");field(AdvancedClient.class,"active").set(null,null);
        Method finish=AdvancedClient.class.getDeclaredMethod("finish",net.minecraft.client.MinecraftClient.class,boolean.class);finish.setAccessible(true);finish.invoke(null,null,true);
        data.addProperty("mode","structure");data.addProperty("prepared",false);Object waiting=newSession.newInstance(data);field(session,"preparationProgressAt").setLong(waiting,System.nanoTime()-126_000_000_000L);data.addProperty("searchingStructure",true);data.add("structureSearch",JsonParser.parseString("{\"radiusBlocks\":20000}"));update.invoke(waiting,data);
        if(field(session,"prepared").getBoolean(waiting) || System.nanoTime()-field(session,"preparationProgressAt").getLong(waiting)>1_000_000_000L)throw new AssertionError("search progress incorrectly starts workload or fails to renew loading grace");
        System.out.println("SMOOTHFIX_VERIFY_PASS structure-search progress extends preparation grace without starting client workload");
        verifyLegacyInputAndCursor();verifyInputAndRouteValidation(newSession,session,data,validated);verifyRecipeOutputs();verifyFinishedReports();
        System.out.println("SMOOTHFIX_VERIFY_PASS advanced UI mixin classes link; actual EMI accepted/stale result hooks; inventory validation rejects attempts without observed swap/tooltip; stage preparation gate");
    }
    private static void verifyInputAndRouteValidation(Constructor<?> constructor,Class<?> session,JsonObject data,Method validated)throws Exception {
        var options=(net.minecraft.client.option.GameOptions)allocate(net.minecraft.client.option.GameOptions.class);
        // Reflection by field type/identity remains valid after Fabric remaps field names.
        for(Field option:net.minecraft.client.option.GameOptions.class.getDeclaredFields())if(net.minecraft.client.option.KeyBinding.class.isAssignableFrom(option.getType()) && !Modifier.isStatic(option.getModifiers())){
            option.setAccessible(true);option.set(options,new net.minecraft.client.option.KeyBinding("fixture_"+option.getName(),0,"fixture"));
        }
        var originalSneak=options.sneakKey;var originalSprint=options.sprintKey;
        for(Field option:net.minecraft.client.option.GameOptions.class.getDeclaredFields())if(net.minecraft.client.option.KeyBinding.class.isAssignableFrom(option.getType()) && !Modifier.isStatic(option.getModifiers())){
            option.setAccessible(true);if(option.get(options)==originalSneak)option.set(options,new net.minecraft.client.option.StickyKeyBinding("fixture_sneak",340,"fixture",()->true));
            else if(option.get(options)==originalSprint)option.set(options,new net.minecraft.client.option.StickyKeyBinding("fixture_sprint",341,"fixture",()->true));
        }
        data.addProperty("mode","route");Object s=constructor.newInstance(data);ActionMetrics metrics=(ActionMetrics)field(session,"metrics").get(s);
        String[] names={"forward","back","left","right","jump","sneak","sprint","attack","use"};
        var keys=List.of(options.forwardKey,options.backKey,options.leftKey,options.rightKey,options.jumpKey,options.sneakKey,options.sprintKey,options.attackKey,options.useKey);
        for(int i=0;i<names.length;i++)field(session,names[i]).set(s,keys.get(i));
        options.sneakKey.setPressed(true);options.sprintKey.setPressed(true);options.jumpKey.setPressed(true);
        field(AdvancedClient.class,"active").set(null,s);var keyboard=new net.minecraft.client.input.KeyboardInput(options);
        field(session,"input").set(s,new GroundNavigator.Input(true,false,true));
        for(int tick=0;tick<60;tick++){keyboard.tick(false,.3f);if(keyboard.sneaking || keyboard.jumping || keyboard.movementForward!=1 || !options.sprintKey.isPressed())throw new AssertionError("sticky key leaked into benchmark movement");}
        var nativePlayer=(net.minecraft.client.network.ClientPlayerEntity)allocate(net.minecraft.client.network.ClientPlayerEntity.class);
        ((ClientAutoJumpAccess)nativePlayer).fixtureAutoJumpEnabled(true);if(nativePlayer.isAutoJumpEnabled())throw new AssertionError("vanilla auto-jump still active during benchmark");
        field(session,"input").set(s,GroundNavigator.Input.NONE);keyboard.tick(false,.3f);
        if(keyboard.movementForward!=0 || keyboard.sneaking || keyboard.jumping)throw new AssertionError("preparation retained movement input");
        field(AdvancedClient.class,"active").set(null,null);if(!nativePlayer.isAutoJumpEnabled())throw new AssertionError("auto-jump option did not return after benchmark");if(!options.sneakKey.isPressed() || !options.jumpKey.isPressed())throw new AssertionError("benchmark mutated user's physical/toggled key state");
        metrics.success("world_route_observation",System.nanoTime(),"one tiny movement");if((boolean)validated.invoke(s))throw new AssertionError("tiny movement still validates a stuck route");
        var navigator=(GroundNavigator)field(session,"navigator").get(s);field(GroundNavigator.class,"distance").setDouble(navigator,13);
        for(int i=0;i<5;i++)metrics.count("navigation_progress_samples");field(GroundNavigator.class,"extent").setDouble(navigator,4.6);
        if((boolean)validated.invoke(s))throw new AssertionError("oscillation at a wall still validates a route");field(GroundNavigator.class,"extent").setDouble(navigator,9);
        if(!(boolean)validated.invoke(s))throw new AssertionError("meaningful observed route rejected");
        System.out.println("SMOOTHFIX_VERIFY_PASS production KeyBinding mixin drives native KeyboardInput for 60 ticks without sticky sneak/jump; neutral preparation clears input; inactive physical toggle and auto-jump option state preserved; route validation rejects 4.6-block stall and wall oscillation");
    }
    private static void verifyLegacyInputAndCursor()throws Exception {
        var mouse=(net.minecraft.client.Mouse)allocate(net.minecraft.client.Mouse.class);
        var cursor=(org.marj4n.smooth_fix.mixin.client.BenchmarkMouseAccess)mouse;cursor.smoothfix$setX(123);cursor.smoothfix$setY(456);
        if(mouse.getX()!=123 || mouse.getY()!=456)throw new AssertionError("automated cursor model not updated");
        Class<?> session=Class.forName("org.marj4n.smooth_fix.benchmark.ClientBenchmark$Session");var constructor=session.getDeclaredConstructor(UUID.class,int.class,String.class,String.class,String.class,double.class,double.class,double.class);constructor.setAccessible(true);
        Object legacy=constructor.newInstance(UUID.randomUUID(),0,"baseline","orbit","minecraft:overworld",0,70,0);
        var forward=new net.minecraft.client.option.KeyBinding("legacy_forward",0,"fixture");var sneak=new net.minecraft.client.option.StickyKeyBinding("legacy_sneak",340,"fixture",()->true);sneak.setPressed(true);
        field(session,"forward").set(legacy,forward);field(session,"sneak").set(legacy,sneak);field(session,"input").set(legacy,new GroundNavigator.Input(true,false,true));field(ClientBenchmark.class,"active").set(null,legacy);
        if(!forward.isPressed() || sneak.isPressed())throw new AssertionError("legacy input leaked sticky sneak");field(ClientBenchmark.class,"active").set(null,null);
        if(!sneak.isPressed())throw new AssertionError("legacy benchmark modified user key state");
        if(AdvancedStageInfo.legacyTask("baseline","orbit").equals(AdvancedStageInfo.legacyTask("village_32","orbit")))throw new AssertionError("ordinary stage descriptions all generic");
        System.out.println("SMOOTHFIX_VERIFY_PASS native mouse model receives scripted slot coordinates; ordinary stress read-time input preserves sticky state and has distinct baseline/village workload feedback");
    }
    private static Object allocate(Class<?> type)throws Exception {
        Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field instance=field(unsafe,"theUnsafe");
        return unsafe.getMethod("allocateInstance",Class.class).invoke(instance.get(null),type);
    }
    private static void verifyRecipeOutputs()throws Exception {
        Class<?> recipe=Class.forName("dev.emi.emi.api.recipe.EmiRecipe"),stack=Class.forName("dev.emi.emi.api.stack.EmiStack"),ingredient=Class.forName("dev.emi.emi.api.stack.EmiIngredient"),slot=Class.forName("dev.emi.emi.api.widget.SlotWidget"),group=Class.forName("dev.emi.emi.screen.WidgetGroup"),screen=Class.forName("dev.emi.emi.screen.RecipeScreen");
        Object output=stack.getMethod("of",net.minecraft.item.ItemStack.class).invoke(null,new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND));
        Object requested=Proxy.newProxyInstance(recipe.getClassLoader(),new Class<?>[]{recipe},(p,m,a)->m.getName().equals("getId")?new net.minecraft.util.Identifier("smooth_fix","fixture_recipe"):m.getName().equals("getOutputs")?List.of(output):null);
        Object other=Proxy.newProxyInstance(recipe.getClassLoader(),new Class<?>[]{recipe},(p,m,a)->null);
        EmiBenchmarkAdapter adapter=(EmiBenchmarkAdapter)allocate(EmiBenchmarkAdapter.class);field(EmiBenchmarkAdapter.class,"recipe").set(adapter,recipe);field(EmiBenchmarkAdapter.class,"expectedRecipe").set(adapter,requested);field(EmiBenchmarkAdapter.class,"expectedOutputs").set(adapter,List.of(output));
        Object view=allocate(screen),page=group.getConstructor(recipe,int.class,int.class,int.class,int.class).newInstance(requested,0,0,100,100);
        field(screen,"currentPage").set(view,List.of(page));List<Object> widgets=(List<Object>)group.getField("widgets").get(page);widgets.clear();
        Object wrong=stack.getMethod("of",net.minecraft.item.ItemStack.class).invoke(null,new net.minecraft.item.ItemStack(net.minecraft.item.Items.APPLE));
        Object wrongSlot=slot.getConstructor(ingredient,int.class,int.class).newInstance(wrong,0,0);slot.getMethod("recipeContext",recipe).invoke(wrongSlot,requested);widgets.add(wrongSlot);
        if(adapter.renderedRecipe(view)!=null)throw new AssertionError("wrong recipe output accepted");
        Object correct=slot.getConstructor(ingredient,int.class,int.class).newInstance(output,0,0);widgets.clear();widgets.add(correct);
        if(adapter.renderedRecipe(view)!=null)throw new AssertionError("input-only slot accepted as recipe result");
        slot.getMethod("recipeContext",recipe).invoke(correct,requested);
        if(adapter.renderedRecipe(view)==null)throw new AssertionError("matching recipe and result rejected");
        Object wrongPage=group.getConstructor(recipe,int.class,int.class,int.class,int.class).newInstance(other,0,0,100,100);((List<Object>)group.getField("widgets").get(wrongPage)).add(correct);field(screen,"currentPage").set(view,List.of(wrongPage));
        if(adapter.renderedRecipe(view)!=null)throw new AssertionError("stale recipe page accepted");
        System.out.println("SMOOTHFIX_VERIFY_PASS actual EMI RecipeScreen/WidgetGroup/SlotWidget objects reject wrong output, input-only slot and stale recipe; accept matching recipe/output; no graphical render claimed");
    }
    private static void verifyFinishedReports()throws Exception {
        var dir=java.nio.file.Files.createTempDirectory("smoothfix-final-report");var id=UUID.randomUUID();BenchmarkClientReport report=new BenchmarkClientReport(id,dir,"fixture","fixture");report.stage(0,Map.of("status","passed"));report.finish("completed");
        field(ClientBenchmarkReports.class,"report").set(null,report);ClientBenchmarkReports.end("disconnected");
        if(!JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(report.path()))).getAsJsonObject().get("status").getAsString().equals("completed"))throw new AssertionError("disconnect overwrote completed report");
        ClientBenchmarkReports.begin(UUID.randomUUID(),"fixture");
        if(!JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(report.path()))).getAsJsonObject().get("status").getAsString().equals("completed"))throw new AssertionError("new run overwrote completed report");
        field(ClientBenchmarkReports.class,"report").set(null,null);
        System.out.println("SMOOTHFIX_VERIFY_PASS completed client report remains completed after disconnect and subsequent run");
    }
}
