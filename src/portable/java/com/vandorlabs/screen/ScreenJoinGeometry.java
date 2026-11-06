package com.vandorlabs.screen;
import com.vandorlabs.surface.JoinedRectangles;
/** Authored joining lattice; diagonal rows move vertically and along the facing normal together. */
public final class ScreenJoinGeometry {
 private ScreenJoinGeometry(){}
 private static final int[] X={0,0,0,0,-1,1},Y={-1,1,0,0,0,0},Z={0,0,-1,1,0,0};
 public static boolean supports(ScreenFamily family,boolean small){return family!=ScreenFamily.HALF_CONSOLE&&family!=ScreenFamily.HALF_DIAGONAL&&!(family==ScreenFamily.INPUT&&small);}
 public static Basis basis(ScreenFamily family,ScreenFamily.State state,boolean ceiling){if(family==null||state==null||family.horizontal&&state.facing<2)throw new IllegalArgumentException("Screen join orientation");int face=state.facing,right=face>=2?clockwise(face):5,up=face>=2?1:face==1?2:3;if(family.keyboard&&(state.keyboard||ceiling))up=opposite(face);int normal=family==ScreenFamily.DIAGONAL?(state.inverted?face:opposite(face)):-1;return new Basis(right,up,X[up]+(normal<0?0:X[normal]),Y[up]+(normal<0?0:Y[normal]),Z[up]+(normal<0?0:Z[normal]));}
 private static int opposite(int face){return face==0?1:face==1?0:face==2?3:face==3?2:face==4?5:4;}
 private static int clockwise(int face){return face==2?5:face==5?3:face==3?4:2;}
 public static final class Basis {
  public final int right,up;private final int rowX,rowY,rowZ;
  private Basis(int right,int up,int rowX,int rowY,int rowZ){this.right=right;this.up=up;this.rowX=rowX;this.rowY=rowY;this.rowZ=rowZ;}
  private static void step(int index){if(index<0||index>3)throw new IllegalArgumentException("Screen lattice step");}
  public int dx(int index){step(index);return index==0?X[right]:index==1?-X[right]:index==2?rowX:-rowX;}
  public int dy(int index){step(index);return index==0?Y[right]:index==1?-Y[right]:index==2?rowY:-rowY;}
  public int dz(int index){step(index);return index==0?Z[right]:index==1?-Z[right]:index==2?rowZ:-rowZ;}
  public long cell(int x,int y,int z){return JoinedRectangles.cell(x*X[right]+z*Z[right],x*X[up]+y*Y[up]+z*Z[up]);}
 }
}
