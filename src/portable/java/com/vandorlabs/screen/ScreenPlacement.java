package com.vandorlabs.screen;
/** Released state-callback and item-mount policy; hit coordinates belong to the original clicked cell. */
public final class ScreenPlacement {
 private ScreenPlacement(){}
 public static final class Result {
  public final ScreenFamily.State state;public final int wallPosition,ceilingPosition;public final boolean ceilingMounted;
  private Result(ScreenFamily.State state,int wall,boolean ceiling,int position){this.state=state;wallPosition=wall;ceilingMounted=ceiling;ceilingPosition=position;}
 }
 /** supportFacing is -1 unless the supporting cell is this same canonical block; slabHalf is -1/0/1. */
 public static Result place(ScreenFamily family,int clickedFace,int playerHorizontal,boolean sneaking,float hitX,float hitY,float hitZ,int supportFacing,int slabHalf){
  if(family==null||clickedFace<0||clickedFace>5||playerHorizontal<2||playerHorizontal>5||supportFacing < -1||supportFacing>5||family.horizontal&&supportFacing>=0&&supportFacing<2||slabHalf < -1||slabHalf>1||!finite(hitX)||!finite(hitY)||!finite(hitZ))throw new IllegalArgumentException("Invalid Screen placement");
  boolean side=clickedFace>=2;int facing=supportFacing<0?opposite(playerHorizontal):supportFacing;boolean keyboard=false,upper=false,inverted=false;
  if(family.keyboard){keyboard=!side||sneaking;facing=side?clickedFace:facing;upper=clickedFace==0||side&&hitY>.5F;if(side&&slabHalf>=0)upper=slabHalf==1;}
  else if(family==ScreenFamily.DIAGONAL)inverted=clickedFace==0||side&&hitY>.5F;
  else if(family==ScreenFamily.HALF_DIAGONAL){upper=clickedFace==0||side&&hitY>.5F;if(side&&slabHalf>=0)upper=slabHalf==1;facing=side?clickedFace:facing;}
  int wall=family.keyboard&&side&&!sneaking&&!keyboard?(slabHalf<0?slot(hitY):slabHalf==1?2:0):-1;
  boolean ceiling=(family.keyboard||family==ScreenFamily.HALF_CONSOLE)&&clickedFace==0;int position=ceiling?ceilingSlot(facing,hitX,hitZ):-1;
  return new Result(new ScreenFamily.State(facing,keyboard,upper,inverted),wall,ceiling,position);
 }
 private static boolean finite(float value){return !Float.isNaN(value)&&!Float.isInfinite(value);}
 private static int opposite(int facing){return facing==2?3:facing==3?2:facing==4?5:4;}
 private static int slot(float value){return value<1F/3F?0:value<2F/3F?1:2;}
 private static int ceilingSlot(int facing,float x,float z){return slot(facing==3?1-z:facing==5?1-x:facing==4?x:z);}
}
