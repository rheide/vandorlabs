package com.vandorlabs.neoforge.propulsion;
import com.vandorlabs.propulsion.PropulsionEmission;
import com.vandorlabs.drives.DriveControlPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
/** Engine particles from prepared placed or carried poses. */
public final class NativePropulsionParticles {
 private static final ThreadLocal<Emitter> EMITTERS=ThreadLocal.withInitial(Emitter::new);
 private NativePropulsionParticles(){}
 private static final class Emitter implements PropulsionEmission.Sink {ClientLevel level;boolean active;public void particle(double x,double y,double z,int face,int color,int style,float speed,float spread,boolean blockCenter){Minecraft.getInstance().particleEngine.add(new NativePropulsionParticle(level,x,y,z,face,color,style,speed,spread));}}
 public static void tick(NativePropulsionEntity owner){var visual=owner.visual();var part=visual.part();if(visual.inert()||!visual.powered()||!visual.selected()||visual.particleLevel()<=0||part.size()>1&&(part.x()!=0||part.y()!=0)||!(owner.getLevel() instanceof ClientLevel level)||!owner.available()||!NativePropulsionService.installed(owner))return;var emitter=EMITTERS.get();if(emitter.active)emitter=new Emitter();emitter.active=true;emitter.level=level;try{var pos=owner.getBlockPos();PropulsionEmission.emit(visual.definition().id,pos.getX(),pos.getY(),pos.getZ(),visual.facing().get3DDataValue(),visual.powered(),visual.selected(),visual.particleLevel(),part.size(),part.x(),part.y(),emitter);}finally{emitter.active=false;emitter.level=null;}}
 public static void drive(ClientLevel level,net.minecraft.world.phys.Vec3 point,net.minecraft.core.Direction face,int intensity,float spread){
  int count=DriveControlPolicy.plumeCount(intensity);
  float wide=intensity==2?1.75F:2.75F;
  var engine=Minecraft.getInstance().particleEngine;
  for(int i=0;i<count;i++)engine.add(new NativePropulsionParticle(level,point.x,point.y,point.z,
        face.get3DDataValue(),i%3==0?0xffffff:0xffe29a,0,.25F,i<8?spread:spread*wide));
 }
 public static void carriedMaxwell(ClientLevel level,Vec3 nozzle,Vec3 up,Vec3 rear,float spread){
  var engine=Minecraft.getInstance().particleEngine;
  var right=up.cross(rear);
  var count=DriveControlPolicy.plumeCount(1);
  for(int index=0;index<count;index++){
   engine.add(NativePropulsionParticle.carriedMaxwell(level,nozzle,right,up,rear,
         index%3==0?0xffffff:0xffe29a,index<8?spread:spread*2.75F));
  }
 }
 public static void clear(){EMITTERS.remove();NativePropulsionParticle.clearScratch();}
}
