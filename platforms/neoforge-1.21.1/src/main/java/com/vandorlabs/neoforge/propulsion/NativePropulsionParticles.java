package com.vandorlabs.neoforge.propulsion;
import com.vandorlabs.propulsion.PropulsionEmission;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
/** Placed-engine client ticks consume the prepared immutable partition; no group scan or server clock. */
public final class NativePropulsionParticles {
 private static final ThreadLocal<Emitter> EMITTERS=ThreadLocal.withInitial(Emitter::new);
 private NativePropulsionParticles(){}
 private static final class Emitter implements PropulsionEmission.Sink {ClientLevel level;boolean active;public void particle(double x,double y,double z,int face,int color,int style,float speed,float spread,boolean blockCenter){Minecraft.getInstance().particleEngine.add(new NativePropulsionParticle(level,x,y,z,face,color,style,speed,spread));}}
 public static void tick(NativePropulsionEntity owner){var visual=owner.visual();var part=visual.part();if(visual.inert()||!visual.powered()||!visual.selected()||visual.particleLevel()<=0||part.size()>1&&(part.x()!=0||part.y()!=0)||!(owner.getLevel() instanceof ClientLevel level)||!owner.available()||!NativePropulsionService.installed(owner))return;var emitter=EMITTERS.get();if(emitter.active)emitter=new Emitter();emitter.active=true;emitter.level=level;try{var pos=owner.getBlockPos();PropulsionEmission.emit(visual.definition().id,pos.getX(),pos.getY(),pos.getZ(),visual.facing().get3DDataValue(),visual.powered(),visual.selected(),visual.particleLevel(),part.size(),part.x(),part.y(),emitter);}finally{emitter.active=false;emitter.level=null;}}
 public static void clear(){EMITTERS.remove();NativePropulsionParticle.clearScratch();}
}
