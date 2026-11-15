package com.vandorlabs.gear.core;
import java.lang.management.ManagementFactory;
public final class ArmHingeAllocationTest {
 private static volatile double sink;
 private static volatile GearMotion.Pose poseSink;
 private static final GearPoseCache CACHE=new GearPoseCache();
 private static void poseLoop(int variant,int count){for(int i=0;i<count;i++){double phase=(i&7)/7D;
  poseSink=variant==2?CACHE.get(GearMotion.Mode.ROTATE_LEVEL,GearMotion.Direction.NORTH,3,phase,.1875,0,.6875,150,-30,ArmHinge.Mode.DOUBLE,GearMotion.Direction.EAST,2):variant==1?GearMotion.pose(GearMotion.Mode.ROTATE_LEVEL,GearMotion.Direction.NORTH,3,phase,.1875,0,.6875,150,-30,ArmHinge.Mode.DOUBLE,GearMotion.Direction.EAST,2):GearMotion.pose(GearMotion.Mode.ROTATE_LEVEL,GearMotion.Direction.NORTH,3,phase,.1875,0,.6875,150,-30,true);
 }}
 private static final GearFootProjection PROJECTION=new GearFootProjection();
 private static final double[] POINT=new double[3];
 private static void loop(boolean split,int count){double total=0;for(int i=0;i<count;i++){
  double phase=(i&1023)/1023D;
  if(split)PROJECTION.prepare(GearMotion.Mode.ROTATE_LEVEL,GearMotion.Direction.NORTH,3,phase,.1875,0,.6875,150,-30,ArmHinge.Mode.DOUBLE,GearMotion.Direction.EAST,2);
  else PROJECTION.prepare(GearMotion.Mode.ROTATE_LEVEL,GearMotion.Direction.NORTH,3,phase,.1875,0,.6875,150,-30,true);
  PROJECTION.write(.23,-3.11,.76,POINT,0);total+=POINT[0]+POINT[1]+POINT[2];
 }sink=total;}
 @SuppressWarnings("deprecation") // getId remains the Java 8 thread-allocation API.
 public static void main(String[] args){com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().getId();loop(false,100000);loop(true,100000);
  for(int variant=0;variant<3;variant++)poseLoop(variant,100000);
  for(int variant=0;variant<3;variant++){long before=bean.getThreadAllocatedBytes(id),start=System.nanoTime();poseLoop(variant,200000);long ns=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(id)-before;if(Boolean.getBoolean("vandorlabs.performanceDiagnostics"))System.out.println(new String[]{"Legacy uncached","Double uncached","Double cached"}[variant]+" pose bytes="+bytes+" ns/lookup="+(ns/200000D));if(variant==2&&bytes>1024)throw new AssertionError("cached pose lookup allocates");}
  for(boolean split:new boolean[]{false,true}){long before=bean.getThreadAllocatedBytes(id),start=System.nanoTime();loop(split,200000);long ns=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(id)-before;if(Boolean.getBoolean("vandorlabs.performanceDiagnostics"))System.out.println((split?"Double":"Legacy center")+" projection bytes="+bytes+" ns/prepare="+(ns/200000D));if(bytes>1024)throw new AssertionError("per-prepare allocations");}
  System.out.println("Arm hinge allocation PASS cached pose/projection bounded allocation");
 }
}
