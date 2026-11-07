package com.vandorlabs.vehicle;

import com.vandorlabs.gear.*;
import com.vandorlabs.gear.core.*;
import com.vandorlabs.physics.CouplerGraphConstraint;
import java.util.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;

/** Detached scalar Source pose model. No tile, world, payload, NBT or physical inventory is retained.
 * Each instance owns mutable projection scratch; callers serialize evaluate calls. */
public final class VehicleArmConstraintModel implements CouplerGraphConstraint.Model {
 public static final class Arm {
  private final GearMotion.Mode mode;private final GearMotion.Direction direction;private final int height;
  private final double stowed,hinge,socket,active,start,rootX,rootY,rootZ,offsetX,offsetY,offsetZ;
  private final double[] basis,inverse,correction;
  private Arm(GearTileEntity owner){
   if(owner==null||!owner.isOperational()||owner.assembly()==null||owner.payload()==null||owner.payload().hasOwnershipJournal()||owner.hasTaxiData()||owner.recoveryDropped())throw new IllegalArgumentException("Arm pose requires a healthy captured owner");
   ArmPayload payload=owner.payload();GearMotion.Pose zero=owner.pose(0),one=owner.pose(1);mode=zero.mode();direction=zero.direction();height=zero.columnHeight();stowed=zero.footOffsetY()-height;socket=owner.footMountOffset();hinge=mode==GearMotion.Mode.ROTATE_LEVEL&&owner.armSize()==2&&owner.assembly().kind()==GearTopology.FootKind.WHEEL?17/128D:0;active=zero.angleDegrees();start=one.angleDegrees();
   rootX=owner.getPos().getX();rootY=owner.getPos().getY();rootZ=owner.getPos().getZ();offsetX=payload.offset.getX();offsetY=payload.offset.getY();offsetZ=payload.offset.getZ();EnumFacing mount=owner.mountDirection();basis=basis(mount);inverse=new double[]{basis[0],basis[3],basis[6],basis[1],basis[4],basis[7],basis[2],basis[5],basis[8]};correction=new double[6];
   double[] raw=new double[3];for(int end=0;end<2;end++){write(end,0,0,0,raw,0,false);Vec3d actual=payload.transform(owner,end,0,0,0);correction[end*3]=actual.x+rootX-raw[0];correction[end*3+1]=actual.y+rootY-raw[1];correction[end*3+2]=actual.z+rootZ-raw[2];}
   // These queries verify the derived descriptor against real released callbacks before retaining it.
   for(double phase:new double[]{0,.25,.5,.75,1})for(int point=0;point<4;point++){double x=point==1?1:0,y=point==2?1:0,z=point==3?1:0;write(phase,x,y,z,raw,0,true);Vec3d actual=payload.transform(owner,phase,x,y,z);if(Math.abs(raw[0]-actual.x-rootX)>1e-8||Math.abs(raw[1]-actual.y-rootY)>1e-8||Math.abs(raw[2]-actual.z-rootZ)>1e-8)throw new IllegalArgumentException("Unproven Source arm pose descriptor");}
  }
  public static Arm capture(GearTileEntity owner){return new Arm(owner);}
  private GearMotion.Pose pose(double phase){return GearMotion.pose(mode,direction,height,phase,stowed,hinge,socket,active,start);}
  private static double[] basis(EnumFacing face){Vec3d x=ArmOrientation.vector(face,1,0,0),y=ArmOrientation.vector(face,0,1,0),z=ArmOrientation.vector(face,0,0,1);return new double[]{x.x,y.x,z.x,x.y,y.y,z.y,x.z,y.z,z.z};}
  /** Actual construction-payload point, with absolute construction-body root included. */
  public void write(double phase,double x,double y,double z,double[] out,int at){write(phase,x,y,z,out,at,true);}
  private void write(double phase,double x,double y,double z,double[] out,int at,boolean corrected){double px=x+offsetX-.5,py=y+offsetY-.5,pz=z+offsetZ-.5;GearMotion.Vec v=pose(phase).transformFootVertex(inverse[0]*px+inverse[1]*py+inverse[2]*pz+.5,inverse[3]*px+inverse[4]*py+inverse[5]*pz+.5,inverse[6]*px+inverse[7]*py+inverse[8]*pz+.5);writeMoved(phase,v,out,at,corrected);}
  private void writeMoved(double phase,GearMotion.Vec v,double[] out,int at,boolean corrected){writeMoved(phase,v.x,v.y,v.z,out,at,corrected);}
  private void writeMoved(double phase,double movedX,double movedY,double movedZ,double[] out,int at,boolean corrected){double x=movedX-.5,y=movedY-.5,z=movedZ-.5;double a=1-phase;a=a*a*(3-2*a);double b=phase*phase*(3-2*phase);for(int axis=0;axis<3;axis++)out[at+axis]=(axis==0?rootX:axis==1?rootY:rootZ)+.5+basis[axis*3]*x+basis[axis*3+1]*y+basis[axis*3+2]*z+(corrected?correction[axis]*a+correction[3+axis]*b:0);}
 }
 public static final class Endpoint {
  public final int arm;public final double x,y,z;
  public Endpoint(int arm,double x,double y,double z){if(arm < -1||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>1000000||Math.abs(y)>1000000||Math.abs(z)>1000000)throw new IllegalArgumentException("Endpoint bounds");this.arm=arm;this.x=x;this.y=y;this.z=z;}
 }
 private final Arm[] arms;private final Endpoint[] endpoints;private final GearFootProjection[] poses;private final double[] local;
 public VehicleArmConstraintModel(Arm[] arms,Endpoint[] endpoints){Objects.requireNonNull(arms);Objects.requireNonNull(endpoints);if(arms.length>CouplerGraphConstraint.MAX_ARMS||endpoints.length>CouplerGraphConstraint.MAX_LINKS*2||endpoints.length%2!=0)throw new IllegalArgumentException("Model dimensions");this.arms=arms.clone();for(Arm a:this.arms)Objects.requireNonNull(a);this.endpoints=endpoints.clone();poses=new GearFootProjection[arms.length];for(int i=0;i<poses.length;i++)poses[i]=new GearFootProjection();local=new double[endpoints.length*3];for(int i=0;i<this.endpoints.length;i++){Endpoint e=Objects.requireNonNull(this.endpoints[i]);if(e.arm>=arms.length)throw new IllegalArgumentException("Endpoint arm");if(e.arm>=0){Arm a=arms[e.arm];double x=e.x+a.offsetX-.5,y=e.y+a.offsetY-.5,z=e.z+a.offsetZ-.5;for(int axis=0;axis<3;axis++)local[i*3+axis]=a.inverse[axis*3]*x+a.inverse[axis*3+1]*y+a.inverse[axis*3+2]*z+.5;}}}
 public int arms(){return arms.length;}public int links(){return endpoints.length/2;}public long retainedBytes(){return 256L+arms.length*1024L+endpoints.length*96L;}
 @Override public boolean evaluate(double[] progress,double[] pairs){if(progress.length!=arms.length||pairs.length!=endpoints.length*3)return false;for(int i=0;i<arms.length;i++){if(!Double.isFinite(progress[i])||progress[i]<0||progress[i]>1)return false;Arm a=arms[i];poses[i].prepare(a.mode,a.direction,a.height,progress[i],a.stowed,a.hinge,a.socket,a.active,a.start);}for(int i=0;i<endpoints.length;i++){Endpoint e=endpoints[i];if(e.arm<0){pairs[i*3]=e.x;pairs[i*3+1]=e.y;pairs[i*3+2]=e.z;}else{int at=i*3;poses[e.arm].write(local[at],local[at+1],local[at+2],pairs,at);arms[e.arm].writeMoved(progress[e.arm],pairs[at],pairs[at+1],pairs[at+2],pairs,at,true);}}return true;}
}
