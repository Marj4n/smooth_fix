package org.marj4n.smooth_fix.performance;
import java.util.regex.Pattern;
/** Compile the verified expression once; no cache of inventories, entities or results. */
public final class TrinketNames {
    private static final String EXPRESSION="(trinket_group_).*-";
    private static final Pattern GROUP=Pattern.compile(EXPRESSION);
    private TrinketNames() { }
    public static String replace(String value,String expression,String replacement) {
        return EXPRESSION.equals(expression)?GROUP.matcher(value).replaceAll(replacement):value.replaceAll(expression,replacement);
    }
}
