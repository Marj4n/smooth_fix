package org.marj4n.smooth_fix.benchmark;
import java.util.*;
import java.lang.reflect.*;
/** API calls are reflected to keep EMI optional and out of the dedicated-server classpath. */
public final class EmiBenchmarkAdapter {
    private final Class<?> api,search,recipeManager,recipe;
    private final String originalSearch;
    private Object expectedRecipe;
    private List<?> expectedOutputs=List.of();
    public EmiBenchmarkAdapter() throws Exception {
        api=Class.forName("dev.emi.emi.api.EmiApi");search=Class.forName("dev.emi.emi.search.EmiSearch");recipeManager=Class.forName("dev.emi.emi.api.recipe.EmiRecipeManager");recipe=Class.forName("dev.emi.emi.api.recipe.EmiRecipe");
        originalSearch=(String)api.getMethod("getSearchText").invoke(null);
    }
    public int indexSize() throws Exception {return ((List<?>)api.getMethod("getIndexStacks").invoke(null)).size();}
    public int recipeCount() throws Exception {return recipes().size();}
    private List<?> recipes()throws Exception {Object manager=api.getMethod("getRecipeManager").invoke(null);return (List<?>)recipeManager.getMethod("getRecipes").invoke(manager);}
    public void search(String query)throws Exception {api.getMethod("setSearchText",String.class).invoke(null,query);}
    public boolean displayRecipe(int index)throws Exception {
        List<?> recipes=recipes();expectedRecipe=null;expectedOutputs=List.of();
        for(int offset=0;offset<recipes.size();offset++){
            Object candidate=recipes.get(Math.floorMod(index+offset,recipes.size()));
            List<?> outputs=(List<?>)recipe.getMethod("getOutputs").invoke(candidate);
            if(outputs.isEmpty())continue;
            expectedRecipe=candidate;expectedOutputs=List.copyOf(outputs);
            api.getMethod("displayRecipe",recipe).invoke(null,candidate);return true;
        }
        return false;
    }
    /** Verify the requested recipe is on the rendered page and has a matching output slot. */
    public String renderedRecipe(Object screen)throws Exception {
        if(expectedRecipe==null || screen==null || !screen.getClass().getName().equals("dev.emi.emi.screen.RecipeScreen"))return null;
        Field page=screen.getClass().getDeclaredField("currentPage");page.setAccessible(true);
        List<?> groups=(List<?>)page.get(screen);if(groups==null)return null;
        Class<?> slot=Class.forName("dev.emi.emi.api.widget.SlotWidget"),ingredient=Class.forName("dev.emi.emi.api.stack.EmiIngredient"),stack=Class.forName("dev.emi.emi.api.stack.EmiStack");
        for(Object group:groups){
            if(group.getClass().getField("recipe").get(group)!=expectedRecipe)continue;
            for(Object widget:(List<?>)group.getClass().getField("widgets").get(group))if(slot.isInstance(widget)){
                if(slot.getMethod("getRecipe").invoke(widget)!=expectedRecipe)continue;
                Object value=slot.getMethod("getStack").invoke(widget);
                for(Object displayed:(List<?>)ingredient.getMethod("getEmiStacks").invoke(value))for(Object output:expectedOutputs)
                    if(!(boolean)stack.getMethod("isEmpty").invoke(output) && (boolean)stack.getMethod("isEqual",stack).invoke(output,displayed))
                        return String.valueOf(recipe.getMethod("getId").invoke(expectedRecipe))+"; output="+stack.getMethod("getId").invoke(output);
            }
        }
        return null;
    }
    public void restore(){try{search(originalSearch);}catch(Exception e){org.marj4n.smooth_fix.SmoothFix.LOGGER.warn("EMI benchmark search restore failed",e);}}
    /** The apply hook fires only after an accepted publication; verify the currently published list as well. */
    public static String acceptedQuery(Object worker,List<?> result) throws Exception {
        if(Class.forName("dev.emi.emi.search.EmiSearch").getField("stacks").get(null)!=result)return null;
        return workerQuery(worker);
    }
    public static String workerQuery(Object worker)throws Exception {Field query=worker.getClass().getDeclaredField("query");query.setAccessible(true);return (String)query.get(worker);}
}
