package com.vandorlabs.neoforge.propulsion;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vandorlabs.propulsion.*;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
/** Source motion and billboard bytes on the native particle lifecycle. */
final class NativePropulsionParticle extends Particle implements PropulsionBillboard.Sink {
 private final PropulsionParticle.State motion;private final TextureAtlasSprite sprite;private AABB renderBounds;private VertexConsumer consumer;
 private final boolean terrainCollision;
 private static final ThreadLocal<Collision> COLLISIONS=ThreadLocal.withInitial(Collision::new);
 private static int yawBits=~0,pitchBits=~0;private static float rx,rz,ryz,rxy,rxz;
 private static final class Collision implements PropulsionParticle.Boxes,Shapes.DoubleLineConsumer {double[] data=new double[96];int count;boolean active;public int size(){return count;}public double coordinate(int box,int component){return data[box*6+component];}public void consume(double x0,double y0,double z0,double x1,double y1,double z1){int at=count*6;if(at+6>data.length)data=Arrays.copyOf(data,Math.max(at+6,data.length*2));data[at]=x0;data[at+1]=y0;data[at+2]=z0;data[at+3]=x1;data[at+4]=y1;data[at+5]=z1;count++;}void release(){count=0;active=false;if(data.length>6*4096)data=new double[96];}}
 NativePropulsionParticle(ClientLevel level,double x,double y,double z,int facing,int color,int style,float speed,float spread){
  this(level,new PropulsionParticle.State(x,y,z,facing,color,style,speed,spread,new Random()),true);
 }
 private NativePropulsionParticle(ClientLevel level,PropulsionParticle.State motion,boolean terrainCollision){
  super(level,motion.previousX,motion.previousY,motion.previousZ);
  this.motion=motion;this.terrainCollision=terrainCollision;
  sprite=((TextureAtlas)Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES)).getSprite(NativePropulsionParticleSprites.sprite(motion.style));
  sync();
 }
 static NativePropulsionParticle carriedMaxwell(ClientLevel level,Vec3 nozzle,Vec3 right,Vec3 up,Vec3 rear,int color,float spread){
  var motion=new PropulsionParticle.State(0,0,0,Direction.SOUTH.get3DDataValue(),color,0,.25F,spread,new Random());
  double x=nozzle.x+right.x*motion.x+up.x*motion.y+rear.x*motion.z;
  double y=nozzle.y+right.y*motion.x+up.y*motion.y+rear.y*motion.z;
  double z=nozzle.z+right.z*motion.x+up.z*motion.y+rear.z*motion.z;
  double vx=right.x*motion.vx+up.x*motion.vy+rear.x*motion.vz;
  double vy=right.y*motion.vx+up.y*motion.vy+rear.y*motion.vz;
  double vz=right.z*motion.vx+up.z*motion.vy+rear.z*motion.vz;
  double dx=x-motion.x,dy=y-motion.y,dz=z-motion.z;
  motion.minX+=dx;motion.maxX+=dx;motion.minY+=dy;motion.maxY+=dy;motion.minZ+=dz;motion.maxZ+=dz;
  motion.x=x;motion.y=y;motion.z=z;
  motion.previousX=nozzle.x;motion.previousY=nozzle.y;motion.previousZ=nozzle.z;
  motion.vx=vx;motion.vy=vy;motion.vz=vz;
  return new NativePropulsionParticle(level,motion,false);
 }
 private void sync(){x=motion.x;y=motion.y;z=motion.z;xo=motion.previousX;yo=motion.previousY;zo=motion.previousZ;xd=motion.vx;yd=motion.vy;zd=motion.vz;age=motion.age;lifetime=motion.lifetime;onGround=motion.onGround;var box=new AABB(motion.minX,motion.minY,motion.minZ,motion.maxX,motion.maxY,motion.maxZ);setBoundingBox(box);renderBounds=box.inflate(1);}
 @Override public void tick(){
  if(!terrainCollision)motion.tickAir();
  else{
   var scratch=COLLISIONS.get();if(scratch.active)scratch=new Collision();scratch.active=true;scratch.count=0;
   try{
    var query=new AABB(motion.minX,motion.minY,motion.minZ,motion.maxX,motion.maxY,motion.maxZ).expandTowards(motion.vx,motion.vy,motion.vz);
    for(var shape:level.getBlockCollisions(null,query))shape.forAllBoxes(scratch);
    motion.tick(scratch);
   }finally{scratch.release();}
  }
  sync();if(motion.expired)remove();
 }
 @Override public AABB getRenderBoundingBox(float partial){return renderBounds;}
 @Override public ParticleRenderType getRenderType(){return NativePropulsionParticleRenderType.INSTANCE;}
 @Override protected int getLightColor(float partial){return PropulsionParticle.FULL_BRIGHT;}
 @Override public void render(VertexConsumer target,Camera camera,float partial){int yaw=Float.floatToRawIntBits(camera.getYRot()),pitch=Float.floatToRawIntBits(camera.getXRot());if(yaw!=yawBits||pitch!=pitchBits){yawBits=yaw;pitchBits=pitch;float angle=camera.getYRot()*.017453292F,tilt=camera.getXRot()*.017453292F,sin=Mth.sin(angle),vertical=Mth.sin(tilt);rx=Mth.cos(angle);rz=Mth.cos(tilt);ryz=sin;rxy=-sin*vertical;rxz=rx*vertical;}var origin=camera.getPosition();consumer=target;try{PropulsionBillboard.emit(motion,partial,origin.x,origin.y,origin.z,rx,rz,ryz,rxy,rxz,this);}finally{consumer=null;}}
 @Override public void vertex(float x,float y,float z,float u,float v,int color,int light){float localU=PropulsionBillboard.localU(motion.sprite,u),localV=PropulsionBillboard.localV(motion.sprite,v);consumer.addVertex(x,y,z).setUv(sprite.getU0()+(sprite.getU1()-sprite.getU0())*localU,sprite.getV0()+(sprite.getV1()-sprite.getV0())*localV).setColor(color&255,(color>>>8)&255,(color>>>16)&255,(color>>>24)&255).setLight(light);}
 static void clearScratch(){COLLISIONS.remove();yawBits=~0;pitchBits=~0;}
 PropulsionParticle.State sourceState(){return motion;}
}
