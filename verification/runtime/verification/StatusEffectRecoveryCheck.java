package verification;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.entity.effect.*;
import net.minecraft.nbt.*;
import org.marj4n.smooth_fix.benchmark.StatusEffectRecovery;
import java.io.IOException;
import java.util.*;

public final class StatusEffectRecoveryCheck {
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static final class SourceAware extends StatusEffectInstance {
        SourceAware(){super(StatusEffects.SPEED,200,0);}
    }
    public static void main(String[] args)throws Exception{
        SharedConstants.createGameVersion();Bootstrap.initialize();
        var speed=new StatusEffectInstance(StatusEffects.SPEED,30,2,true,false,true);
        speed.upgrade(new StatusEffectInstance(StatusEffects.SPEED,120,0,false,true,false));
        var poison=new StatusEffectInstance(StatusEffects.POISON,400,1,false,true,false);
        NbtList before=StatusEffectRecovery.capture(List.of(speed,poison));
        require(before.equals(StatusEffectRecovery.capture(List.of(poison,speed))),"Capture depends on collection order");
        require(before.getCompound(0).contains("HiddenEffect")||before.getCompound(1).contains("HiddenEffect"),"Hidden effect fixture missing");
        var decoded=StatusEffectRecovery.decode(before);
        require(before.equals(StatusEffectRecovery.capture(decoded)),"Full status NBT did not roundtrip");
        speed.upgrade(new StatusEffectInstance(StatusEffects.SPEED,1000,3));
        require(before.equals(StatusEffectRecovery.capture(decoded)),"Snapshot aliases the live effect");
        require(!before.equals(StatusEffectRecovery.capture(List.of(speed,poison))),"Changed duration/amplifier ignored");
        try{StatusEffectRecovery.capture(List.of(new SourceAware()));throw new AssertionError("Unknown instance subtype silently lost source fields");}
        catch(IOException expected){require(expected.getMessage().contains("subtype"),"Wrong subtype rejection");}
        var link=Arrays.stream(StatusEffectInstance.class.getDeclaredFields()).filter(f->!java.lang.reflect.Modifier.isStatic(f.getModifiers())&&f.getType()==StatusEffectInstance.class).findFirst().orElseThrow();link.setAccessible(true);
        var hiddenSource=new StatusEffectInstance(StatusEffects.SPEED,50,2);link.set(hiddenSource,new SourceAware());
        try{StatusEffectRecovery.capture(List.of(hiddenSource));throw new AssertionError("Hidden source subtype silently lost source fields");}catch(IOException expected){require(expected.getMessage().contains("subtype"),"Wrong hidden subtype rejection");}
        var cyclic=new StatusEffectInstance(StatusEffects.SPEED,50,2);link.set(cyclic,cyclic);
        try{StatusEffectRecovery.capture(List.of(cyclic));throw new AssertionError("Cyclic hidden chain accepted");}catch(IOException expected){require(expected.getMessage().contains("cyclic"),"Wrong cycle rejection");}
        NbtList invalid=new NbtList();NbtCompound missing=new NbtCompound();missing.putByte("Id",(byte)127);missing.putInt("Duration",30);invalid.add(missing);
        try{StatusEffectRecovery.decode(invalid);throw new AssertionError("Missing effect ID silently dropped");}catch(IOException expected){ }
        require(StatusEffectRecovery.decode(new NbtList()).isEmpty(),"Empty effect snapshot changed");
        System.out.println("PASS: full vanilla status-effect NBT, hidden effects, flags, ordering, detached snapshot, changed values, unknown-ID and top-level/hidden source-subtype and cycle rejection. Not transformed full-pack/runtime recovery acceptance.");
    }
}
