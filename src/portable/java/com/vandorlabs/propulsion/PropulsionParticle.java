package com.vandorlabs.propulsion;
import java.util.Random;

/** Released placed-plume scalar state. World adapters supply loaded collision boxes without retaining world references. */
public strictfp final class PropulsionParticle {
 private PropulsionParticle(){}
 public interface Boxes {int size();double coordinate(int box,int component);}
 private static final Boxes EMPTY=new Boxes(){public int size(){return 0;}public double coordinate(int box,int component){throw new IndexOutOfBoundsException();}};
 public static final int FULL_BRIGHT=0x00f000f0;
 public static final class State {
  public final int style,lifetime,sprite;
  public final float red,green,blue;
  public double x,y,z,previousX,previousY,previousZ,vx,vy,vz,minX,minY,minZ,maxX,maxY,maxZ;
  public float scale,phase;public int age;public boolean expired,onGround;
  public State(double x,double y,double z,int face,int color,int style,float speed,float spreadScale,Random random){
   if(face<0||face>5||style<0||style>4||random==null)throw new IllegalArgumentException("Invalid plume state");this.style=style;previousX=x;previousY=y;previousZ=z;phase=random.nextFloat()*6.2831855F;int dx=PropulsionEmission.dx(face),dy=PropulsionEmission.dy(face),dz=PropulsionEmission.dz(face);double spread=(style==1?.18D:style==3?.56D:style==2?.42D:.32D)*Math.max(.25D,spreadScale);double sx=dx==0?(random.nextDouble()-.5D)*spread:0,sy=dy==0?(random.nextDouble()-.5D)*spread:0,sz=dz==0?(random.nextDouble()-.5D)*spread:0;position(x+dx*.54D+sx,y+dy*.54D+sy,z+dz*.54D+sz);double actual=speed*(.78D+random.nextDouble()*.44D),jitter=style==2?.025D:style==3?.018D:.009D;vx=dx*actual+(random.nextDouble()-.5D)*jitter;vy=dy*actual+(random.nextDouble()-.5D)*jitter;vz=dz*actual+(random.nextDouble()-.5D)*jitter;red=((color>>16)&255)/255F;green=((color>>8)&255)/255F;blue=(color&255)/255F;scale=(style==2?.34F:style==3?.25F:style==0?.20F:.16F)+random.nextFloat()*.12F;lifetime=style==0?8+random.nextInt(6):style==1?16+random.nextInt(8):style==2?18+random.nextInt(10):style==3?22+random.nextInt(10):12+random.nextInt(8);sprite=style==0?48:style==1?65:style==2?128:style==3?7:65;
  }
  private void position(double x,double y,double z){this.x=x;this.y=y;this.z=z;double half=.2F/2F;minX=x-half;minY=y;minZ=z-half;maxX=x+half;maxY=y+(double).2F;maxZ=z+half;}
  private double min(int axis){return axis==0?minX:axis==1?minY:minZ;}
  private double max(int axis){return axis==0?maxX:axis==1?maxY:maxZ;}
  private double clip(Boxes boxes,int axis,double delta){int a=axis==0?1:0,b=axis==2?1:2;for(int i=0;i<boxes.size();i++){if(boxes.coordinate(i,a+3)>min(a)&&boxes.coordinate(i,a)<max(a)&&boxes.coordinate(i,b+3)>min(b)&&boxes.coordinate(i,b)<max(b)){if(delta>0D&&max(axis)<=boxes.coordinate(i,axis)){double d=boxes.coordinate(i,axis)-max(axis);if(d<delta)delta=d;}else if(delta<0D&&min(axis)>=boxes.coordinate(i,axis+3)){double d=boxes.coordinate(i,axis+3)-min(axis);if(d>delta)delta=d;}}}return delta;}
  private void offset(double x,double y,double z){minX+=x;maxX+=x;minY+=y;maxY+=y;minZ+=z;maxZ+=z;}
  public void tickAir(){tick(EMPTY);}
  /** Source Y/X/Z clipping, stopped X/Z only, then ground friction; no modern epsilon clipping policy. */
  public void tick(Boxes boxes){previousX=x;previousY=y;previousZ=z;if(age++>=lifetime)expired=true;double dy=clip(boxes,1,vy);offset(0,dy,0);double dx=clip(boxes,0,vx);offset(dx,0,0);double dz=clip(boxes,2,vz);offset(0,0,dz);x=(minX+maxX)/2D;y=minY;z=(minZ+maxZ)/2D;onGround=vy!=dy&&vy<0D;if(vx!=dx)vx=0D;if(vz!=dz)vz=0D;vx*=.9800000190734863D;vy*=.9800000190734863D;vz*=.9800000190734863D;if(onGround){vx*=.699999988079071D;vz*=.699999988079071D;}finishTick();}
  public void finishTick(){double drag=style==0?.985D:style==1?.995D:style==2?.965D:style==3?.945D:.97D;vx*=drag;vy*=drag;vz*=drag;if(style==2){phase+=.45F;vx+=Math.sin(phase)*.0025D;vy+=Math.cos(phase*.8F)*.0025D;}scale*=style==1?.99F:.97F;}
 }
}
