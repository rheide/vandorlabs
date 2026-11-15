package com.vandorlabs.gear.core;
import java.util.*;
/** Full native-cell endpoint admission from the same authored foot projection as geometry. */
public final class LandingAnglePolicy {
 public static final int END=1,START=2;private static final double EPS=1e-8;
 public static final String ERROR="Choose angles with at least one grid-aligned landing-foot endpoint";
 public static final class Result {
  public final int nativeMask,uprightMask;
  private Result(int nativeMask,int uprightMask){this.nativeMask=nativeMask;this.uprightMask=uprightMask;}
  public boolean valid(){return nativeMask!=0;}
  public boolean nativeAt(double phase){return phase==0?(nativeMask&END)!=0:phase==1&&(nativeMask&START)!=0;}
  /** World DOWN mounting remains a separate existing Taxi requirement. */
  public boolean uprightAt(double phase){return phase==0?(uprightMask&END)!=0:phase==1&&(uprightMask&START)!=0;}
  public int safeTarget(boolean preferStart){int desired=preferStart?START:END;if((uprightMask&desired)!=0)return preferStart?1:0;if((uprightMask&START)!=0)return 1;if((uprightMask&END)!=0)return 0;return -1;}
 }
 public static Result evaluate(GearMotion.Mode mode,GearMotion.Direction fold,int height,int width,double stowedY,double hingeDrop,double socketOffset,ArmHinge.Mode hinge,GearMotion.Direction kink,ArmLandingAngles angles,Collection<GearTopology.Cell> feet){
  Objects.requireNonNull(angles);Objects.requireNonNull(feet);if(feet.isEmpty()||feet.size()>GearTopology.MAX_FEET)throw new IllegalArgumentException("Complete landing foot layout required");if(new HashSet<GearTopology.Cell>(feet).size()!=feet.size()||feet.contains(null))throw new IllegalArgumentException("Complete unique landing foot layout required");GearFootProjection scratch=new GearFootProjection();double[] work=new double[12];int mask=0,upright=0;
  for(int phase=0;phase<=1;phase++){scratch.prepare(mode,fold,height,phase,stowedY,hingeDrop,socketOffset,angles.end(true),angles.start(true),hinge,kink,width);if(!nativeCells(scratch,feet,work))continue;int bit=phase==0?END:START;mask|=bit;scratch.vector(0,1,0,work,0);if(Math.abs(work[0])+Math.abs(work[1]-1)+Math.abs(work[2])<=EPS)upright|=bit;}
  return new Result(mask,upright);
 }
 private static boolean nativeCells(GearFootProjection projection,Collection<GearTopology.Cell> cells,double[] a){
  projection.vector(1,0,0,a,0);projection.vector(0,1,0,a,3);projection.vector(0,0,1,a,6);
  for(int column=0;column<3;column++){double sum=0;for(int row=0;row<3;row++){double value=a[column*3+row];if(!Double.isFinite(value)||Math.abs(value-Math.rint(value))>EPS)return false;sum+=Math.abs(Math.rint(value));}if(sum!=1)return false;}
  for(int x=0;x<3;x++)for(int y=x+1;y<3;y++)if(Math.abs(a[x*3]*a[y*3]+a[x*3+1]*a[y*3+1]+a[x*3+2]*a[y*3+2])>EPS)return false;
  double determinant=a[0]*(a[4]*a[8]-a[5]*a[7])-a[3]*(a[1]*a[8]-a[2]*a[7])+a[6]*(a[1]*a[5]-a[2]*a[4]);if(Math.abs(determinant-1)>EPS)return false;
  for(GearTopology.Cell cell:cells){if(cell==null)throw new IllegalArgumentException("Missing landing foot cell");for(int corner=0;corner<8;corner++){projection.write(cell.x+(corner&1),cell.y+((corner>>1)&1),cell.z+((corner>>2)&1),a,9);for(int axis=9;axis<12;axis++)if(!Double.isFinite(a[axis])||Math.abs(a[axis]-Math.rint(a[axis]))>EPS)return false;}}
  return true;
 }
 /** Caller-owned, one-descriptor cache. Motion-only phase changes do not rebuild endpoint admission. */
 public static final class Cache {
  private GearMotion.Mode mode;private GearMotion.Direction fold,kink;private ArmHinge.Mode hinge;
  private int height,width;private double stowed,drop,socket,start,end;private Collection<GearTopology.Cell> feet;private Result value;
  public Result get(GearMotion.Mode mode,GearMotion.Direction fold,int height,int width,double stowed,double drop,double socket,ArmHinge.Mode hinge,GearMotion.Direction kink,ArmLandingAngles angles,Collection<GearTopology.Cell> feet){
   return get(mode,fold,height,width,stowed,drop,socket,hinge,kink,angles.start(true),angles.end(true),feet);
  }
  public Result get(GearMotion.Mode mode,GearMotion.Direction fold,int height,int width,double stowed,double drop,double socket,ArmHinge.Mode hinge,GearMotion.Direction kink,double start,double end,Collection<GearTopology.Cell> feet){
   if(value==null||this.mode!=mode||this.fold!=fold||this.height!=height||this.width!=width||this.stowed!=stowed||this.drop!=drop||this.socket!=socket||this.hinge!=hinge||this.kink!=kink||this.start!=start||this.end!=end||this.feet!=feet){
    Result next=evaluate(mode,fold,height,width,stowed,drop,socket,hinge,kink,new ArmLandingAngles(1,start,end),feet);this.mode=mode;this.fold=fold;this.height=height;this.width=width;this.stowed=stowed;this.drop=drop;this.socket=socket;this.hinge=hinge;this.kink=kink;this.start=start;this.end=end;this.feet=feet;value=next;
   }return value;
  }
  public void clear(){value=null;feet=null;}
 }
 private LandingAnglePolicy(){}
}
