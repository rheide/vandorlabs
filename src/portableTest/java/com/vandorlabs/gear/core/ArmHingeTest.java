package com.vandorlabs.gear.core;
public final class ArmHingeTest {
 private static int checks;
 private static void close(double a,double b){checks++;if(Math.abs(a-b)>1e-9)throw new AssertionError(a+" != "+b);}
 private static void refused(Runnable action){checks++;try{action.run();throw new AssertionError("Invalid hinge input accepted");}catch(IllegalArgumentException expected){}}
 private static void equal(GearMotion.Vec a,GearMotion.Vec b){close(a.x,b.x);close(a.y,b.y);close(a.z,b.z);}
 public static void main(String[] args){GearFootProjection scratch=new GearFootProjection();double[] projected=new double[3];
  for(GearMotion.Direction fold:GearMotion.Direction.values())for(GearMotion.Direction kink:GearMotion.Direction.values())for(int height=1;height<=8;height++)for(int width=1;width<=2;width++)for(double socket:new double[]{0,13/16D,11/16D})for(int angle=-180;angle<=180;angle+=15){
   ReferenceHinge.Pose pose=ReferenceHinge.pose(ArmHinge.Mode.DOUBLE,fold,kink,height,socket,width,angle);
   equal(pose.upper.point(pose.elbow.x,pose.elbow.y,pose.elbow.z),pose.lower.point(pose.elbow.x,pose.elbow.y,pose.elbow.z));
   GearMotion.Pose ordinary=GearMotion.pose(GearMotion.Mode.ROTATE,fold,height,0,GearMotion.RETRACTED_FOOT_Y,0,socket,angle,0,false);
   GearMotion.Vec origin=ordinary.transformRigidVertex(0,0,0);
   for(int axis=0;axis<3;axis++){
    double x=axis==0?1:0,y=axis==1?1:0,z=axis==2?1:0;
    GearMotion.Vec a=ordinary.transformRigidVertex(x,y,z),b=pose.lower.vector(x,y,z);
    equal(new GearMotion.Vec(a.x-origin.x,a.y-origin.y,a.z-origin.z),b);
    close(b.x*b.x+b.y*b.y+b.z*b.z,1);
   }
   for(GearMotion.Mode mode:new GearMotion.Mode[]{GearMotion.Mode.ROTATE,GearMotion.Mode.ROTATE_LEVEL}){
    GearMotion.Pose integrated=GearMotion.pose(mode,fold,height,0,GearMotion.RETRACTED_FOOT_Y,0,socket,angle,0,ArmHinge.Mode.DOUBLE,kink,width);
    GearMotion.Vec ankle=new GearMotion.Vec(.5,-height+GearMotion.FOOT_SOCKET_Y+socket,.5),moved=pose.lower.point(ankle.x,ankle.y,ankle.z);
    GearMotion.Vec actual=integrated.transformFootVertex(.23,-height+.11,.76);
    scratch.prepare(mode,fold,height,0,GearMotion.RETRACTED_FOOT_Y,0,socket,angle,0,ArmHinge.Mode.DOUBLE,kink,width);scratch.write(.23,-height+.11,.76,projected,0);
    if(Double.doubleToLongBits(actual.x)!=Double.doubleToLongBits(projected[0])||Double.doubleToLongBits(actual.y)!=Double.doubleToLongBits(projected[1])||Double.doubleToLongBits(actual.z)!=Double.doubleToLongBits(projected[2]))throw new AssertionError("Double scratch bit parity");checks+=3;

    equal(actual,mode==GearMotion.Mode.ROTATE?pose.lower.point(.23,-height+.11,.76):new GearMotion.Vec(.23+moved.x-ankle.x,-height+.11+moved.y-ankle.y,.76+moved.z-ankle.z));
    equal(integrated.transformUpperLinkVertex(.23,-height+.11,.76),pose.upper.point(.23,-height+.11,.76));
    if(angle<=150){
     double delta=17.5,radians=Math.toRadians(delta),pad=integrated.footCurvatureBound(.23,-height+.11,.76,socket)*radians*radians/8;
     GearMotion.Pose finish=GearMotion.pose(mode,fold,height,0,GearMotion.RETRACTED_FOOT_Y,0,socket,angle+delta,0,ArmHinge.Mode.DOUBLE,kink,width);
     GearMotion.Vec end=finish.transformFootVertex(.23,-height+.11,.76);
     for(int step=1;step<10;step++){
      GearMotion.Pose between=GearMotion.pose(mode,fold,height,0,GearMotion.RETRACTED_FOOT_Y,0,socket,angle+delta*step/10,0,ArmHinge.Mode.DOUBLE,kink,width);
      GearMotion.Vec sample=between.transformFootVertex(.23,-height+.11,.76);
      double[] value={sample.x,sample.y,sample.z},start={actual.x,actual.y,actual.z},last={end.x,end.y,end.z};
      for(int axis=0;axis<3;axis++){checks++;if(value[axis]<Math.min(start[axis],last[axis])-pad-1e-9||value[axis]>Math.max(start[axis],last[axis])+pad+1e-9)throw new AssertionError("integrated sweep missed curve");}
     }
    }
   }
   GearMotion.Vec a=pose.lower.point(.23,-height+.11,.76),b=pose.lower.point(.63,-height+.61,.56);
   close((b.x-a.x)*(b.x-a.x)+(b.y-a.y)*(b.y-a.y)+(b.z-a.z)*(b.z-a.z),.45);
   if(angle==0)equal(a,new GearMotion.Vec(.23,-height+.11,.76));
   if(angle<=150){
    double[] point={.23,-height+.11,.76};double delta=17.5;
    GearMotion.Vec begin=pose.lower.point(point[0],point[1],point[2]);
    GearMotion.Vec end=ReferenceHinge.pose(ArmHinge.Mode.DOUBLE,fold,kink,height,socket,width,angle+delta).lower.point(point[0],point[1],point[2]);
    double pad=pose.chordPadding(point[0],point[1],point[2],delta);
    for(int step=1;step<10;step++){
     double ratio=step/10D;GearMotion.Vec sample=ReferenceHinge.pose(ArmHinge.Mode.DOUBLE,fold,kink,height,socket,width,angle+delta*ratio).lower.point(point[0],point[1],point[2]);
     double[] value={sample.x,sample.y,sample.z},start={begin.x,begin.y,begin.z},finish={end.x,end.y,end.z};
     for(int axis=0;axis<3;axis++){
      boolean hingeAxis=axis==0&&fold.axisX()!=0||axis==2&&fold.axisZ()!=0;
      if(hingeAxis)close(value[axis],point[axis]);
      else{checks++;if(value[axis]<Math.min(start[axis],finish[axis])-pad-1e-9||value[axis]>Math.max(start[axis],finish[axis])+pad+1e-9)throw new AssertionError("sweep missed curve");}
     }
    }
   }

   if(kink==fold&&width==1){
    ReferenceHinge.Pose legacy=ReferenceHinge.pose(ArmHinge.Mode.LEGACY_CENTER,fold,kink,height,socket,width,angle);
    GearMotion.Pose old=GearMotion.pose(GearMotion.Mode.ROTATE,fold,height,0,GearMotion.RETRACTED_FOOT_Y,0,socket,angle,0,true);
    equal(legacy.lower.point(.23,-height+.11,.76),old.transformFootVertex(.23,-height+.11,.76));
   }
  }
  if(ArmHinge.readSaved(true,null)!=ArmHinge.Mode.LEGACY_CENTER||ArmHinge.readSaved(false,null)!=ArmHinge.Mode.SINGLE||ArmHinge.readChoice(true,null)!=ArmHinge.Mode.DOUBLE)throw new AssertionError("Legacy saved motion and new choices differ deliberately");checks+=3;
  for(ArmHinge.Mode mode:ArmHinge.Mode.values()){
   boolean middle=mode!=ArmHinge.Mode.SINGLE;
   if(ArmHinge.readSaved(middle,mode.ordinal())!=mode||ArmHinge.readChoice(middle,mode.ordinal())!=mode)throw new AssertionError("Explicit hinge mode round trip");checks+=2;
   refused(()->ArmHinge.readSaved(!middle,mode.ordinal()));
  }
  refused(()->ArmHinge.readSaved(true,3));refused(()->ArmHinge.readChoice(false,-1));
  if(ArmHinge.atRest(ArmHinge.Mode.LEGACY_CENTER,true,false)!=ArmHinge.Mode.DOUBLE||ArmHinge.atRest(ArmHinge.Mode.LEGACY_CENTER,false,false)!=ArmHinge.Mode.LEGACY_CENTER||ArmHinge.atRest(ArmHinge.Mode.LEGACY_CENTER,true,true)!=ArmHinge.Mode.LEGACY_CENTER)throw new AssertionError("Only unowned zero rest adopts Double");checks+=3;
  double[] frame=new double[13];
  refused(()->ArmHinge.prepareDouble(null,GearMotion.Direction.NORTH,1,0,1,90,frame));
  refused(()->ArmHinge.prepareDouble(GearMotion.Direction.NORTH,null,1,0,1,90,frame));
  refused(()->ArmHinge.prepareDouble(GearMotion.Direction.NORTH,GearMotion.Direction.NORTH,0,0,1,90,frame));
  refused(()->ArmHinge.prepareDouble(GearMotion.Direction.NORTH,GearMotion.Direction.NORTH,1,0,3,90,frame));
  refused(()->ArmHinge.prepareDouble(GearMotion.Direction.NORTH,GearMotion.Direction.NORTH,1,Double.NaN,1,90,frame));
  refused(()->ArmHinge.prepareDouble(GearMotion.Direction.NORTH,GearMotion.Direction.NORTH,1,0,1,Double.POSITIVE_INFINITY,frame));
  refused(()->ArmHinge.prepareDouble(GearMotion.Direction.NORTH,GearMotion.Direction.NORTH,1,0,1,181,frame));
  refused(()->ArmHinge.prepareDouble(GearMotion.Direction.NORTH,GearMotion.Direction.NORTH,1,0,1,90,new double[12]));
  scratch.prepare(GearMotion.Mode.ROTATE,GearMotion.Direction.NORTH,1,0,0,0,0,90,0,ArmHinge.Mode.DOUBLE,GearMotion.Direction.NORTH,1);
  refused(()->scratch.prepare(GearMotion.Mode.ROTATE,GearMotion.Direction.NORTH,1,Double.NaN,0,0,0,90,0,ArmHinge.Mode.DOUBLE,GearMotion.Direction.NORTH,1));
  checks++;try{scratch.write(0,0,0,projected,0);throw new AssertionError("Stale Double projection after rejected prepare");}catch(IllegalStateException expected){}
  System.out.println("PASS double hinge checks="+checks+" connectedElbow/fullAngleOrientation/rigidity/zeroPose/legacyParity");
 }
}
