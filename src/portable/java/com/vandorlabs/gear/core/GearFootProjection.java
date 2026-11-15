package com.vandorlabs.gear.core;

/** Caller-owned foot-pose scratch, including a zero-allocation Double hinge projection. */
public final class GearFootProjection {
 private GearMotion.Mode mode;
 private double offsetY,pivotX,pivotY,pivotZ,hinge,sine,cosine,ax,az,levelX,levelY,levelZ;
 private final double[] ankle=new double[3],splitHinge=new double[13];
 private boolean prepared,doubleHinge;
 public void prepare(GearMotion.Mode mode,GearMotion.Direction direction,int height,double phase,double stowedFootY,double hingeDrop,double socketOffset,double activeAngle,double startAngle){prepare(mode,direction,height,phase,stowedFootY,hingeDrop,socketOffset,activeAngle,startAngle,false);}
 public void prepare(GearMotion.Mode mode,GearMotion.Direction direction,int height,double phase,double stowedFootY,double hingeDrop,double socketOffset,double activeAngle,double startAngle,boolean middleHinge){
  doubleHinge=false;
  double angle=configure(mode,direction,height,phase,stowedFootY,hingeDrop,socketOffset,activeAngle,startAngle,middleHinge);
  sine=angle==90?1:Math.sin(Math.toRadians(angle));cosine=angle==90?0:Math.cos(Math.toRadians(angle));
  finish(height,socketOffset);
 }
 public void prepare(GearMotion.Mode mode,GearMotion.Direction direction,int height,double phase,double stowedFootY,double hingeDrop,double socketOffset,double activeAngle,double startAngle,ArmHinge.Mode hingeMode,GearMotion.Direction kink,int width){
  prepared=false;doubleHinge=false;
  if(hingeMode==null||kink==null||width<1||width>2)throw new IllegalArgumentException("Invalid hinge projection");
  double angle=configure(mode,direction,height,phase,stowedFootY,hingeDrop,socketOffset,activeAngle,startAngle,hingeMode==ArmHinge.Mode.LEGACY_CENTER);
  if(hingeMode==ArmHinge.Mode.DOUBLE){ArmHinge.prepareDouble(direction,kink,height,socketOffset,width,angle,splitHinge);doubleHinge=true;sine=splitHinge[11];cosine=splitHinge[12];}
  else{sine=angle==90?1:Math.sin(Math.toRadians(angle));cosine=angle==90?0:Math.cos(Math.toRadians(angle));}
  finish(height,socketOffset);
 }
 private double configure(GearMotion.Mode mode,GearMotion.Direction direction,int height,double phase,double stowedFootY,double hingeDrop,double socketOffset,double activeAngle,double startAngle,boolean middleHinge){
  prepared=false;
  if(mode==null||direction==null||height<1||height>GearTopology.MAX_COLUMN_HEIGHT||!Double.isFinite(phase)||!Double.isFinite(stowedFootY)||!Double.isFinite(hingeDrop)||!Double.isFinite(socketOffset)||!Double.isFinite(activeAngle)||!Double.isFinite(startAngle)||startAngle < -180||startAngle>180||activeAngle < -180||activeAngle>180)throw new IllegalArgumentException("Invalid foot projection");
  this.mode=mode;hinge=hingeDrop;ax=direction.axisX();az=direction.axisZ();
  double lateral=middleHinge?GearMotion.MIDDLE_HINGE_OFFSET:hingeDrop;
  pivotX=GearMotion.PIVOT_X+direction.axisZ()*lateral;pivotY=middleHinge?GearMotion.middlePivotY(height,socketOffset):GearMotion.PIVOT_Y-hingeDrop;pivotZ=GearMotion.PIVOT_Z-direction.axisX()*lateral;
  double progress=Math.max(0,Math.min(1,phase)),stowed=1-GearMotion.smoothstep(progress);
  offsetY=mode==GearMotion.Mode.EXTEND?(height+stowedFootY)*stowed:0;
  return mode==GearMotion.Mode.ROTATE||mode==GearMotion.Mode.ROTATE_LEVEL?startAngle+(activeAngle-startAngle)*stowed:0;
 }
 private void finish(int height,double socketOffset){
  if(mode==GearMotion.Mode.ROTATE_LEVEL){double deployedAnkleY=-height+GearMotion.FOOT_SOCKET_Y+socketOffset;rotate(GearMotion.PIVOT_X,deployedAnkleY,GearMotion.PIVOT_Z,ankle,0);levelX=ankle[0]-GearMotion.PIVOT_X;levelY=ankle[1]-deployedAnkleY;levelZ=ankle[2]-GearMotion.PIVOT_Z;}
  else levelX=levelY=levelZ=0;
  prepared=true;
 }
 public void write(double x,double y,double z,double[] out,int at){
  if(!prepared)throw new IllegalStateException("Foot projection not prepared");
  if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Nonfinite foot point");
  if(mode==GearMotion.Mode.EXTEND){out[at]=x;out[at+1]=y+offsetY;out[at+2]=z;}
  else if(mode==GearMotion.Mode.ROTATE_LEVEL){out[at]=x+levelX;out[at+1]=y+levelY;out[at+2]=z+levelZ;}
  else rotate(x,y,z,out,at);
  if(!Double.isFinite(out[at])||!Double.isFinite(out[at+1])||!Double.isFinite(out[at+2]))throw new IllegalArgumentException("Nonfinite projected foot");
 }
 public void vector(double x,double y,double z,double[] out,int at){
  if(!prepared)throw new IllegalStateException("Foot projection not prepared");
  if(mode!=GearMotion.Mode.ROTATE){out[at]=x;out[at+1]=y;out[at+2]=z;return;}
  double dot=ax*x+az*z,d=1-cosine;out[at]=x*cosine-az*y*sine+ax*dot*d;out[at+1]=y*cosine+(az*x-ax*z)*sine;out[at+2]=z*cosine+ax*y*sine+az*dot*d;
 }
 private void rotate(double x,double y,double z,double[] out,int at){
  if(doubleHinge){double dot=ax*x+az*z,d=1-cosine;out[at]=x*cosine-az*y*sine+ax*dot*d+splitHinge[0];out[at+1]=y*cosine+(az*x-ax*z)*sine+splitHinge[1];out[at+2]=z*cosine+ax*y*sine+az*dot*d+splitHinge[2];return;}
  double dx=x-pivotX,dy=y-pivotY,dz=z-pivotZ,dot=ax*dx+az*dz,d=1-cosine;out[at]=pivotX+dx*cosine-az*dy*sine+ax*dot*d;out[at+1]=pivotY+dy*cosine+(az*dx-ax*dz)*sine+hinge*sine*cosine;out[at+2]=pivotZ+dz*cosine+ax*dy*sine+az*dot*d;
 }
 public long retainedBytes(){return 384;}
}
