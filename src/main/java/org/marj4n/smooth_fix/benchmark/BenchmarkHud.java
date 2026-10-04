package org.marj4n.smooth_fix.benchmark;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import java.util.*;
/** Cached text shared by both stress controllers. */
public final class BenchmarkHud {
    private List<OrderedText> lines=List.of();private int width;private long updated;
    public void update(MinecraftClient client,long now,List<String> text){
        if(now-updated<500_000_000L)return;updated=now;width=0;List<OrderedText> trimmed=new ArrayList<>();
        int limit=Math.max(80,client.getWindow().getScaledWidth()-24);
        for(String line:text){String value=client.textRenderer.trimToWidth(line,limit);OrderedText ordered=Text.literal(value).asOrderedText();trimmed.add(ordered);width=Math.max(width,client.textRenderer.getWidth(ordered));}lines=trimmed;
    }
    public void render(DrawContext context){if(lines.isEmpty())return;var client=MinecraftClient.getInstance();
        context.fill(5,5,width+15,13+lines.size()*11,0xB0000000);
        for(int i=0;i<lines.size();i++)context.drawText(client.textRenderer,lines.get(i),10,10+i*11,i==0?0x9AFF9A:0xFFFFFF,true);
    }
}
