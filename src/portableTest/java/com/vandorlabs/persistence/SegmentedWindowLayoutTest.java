package com.vandorlabs.persistence;
/** Exhaustive small-grid connectivity, including holes and corner-only contacts. */
public final class SegmentedWindowLayoutTest {
 public static void main(String[] args){int checks=0;for(int mask=0;mask<4096;mask++){
  final int bits=mask;SegmentedWindowLayout.Cells cells=new SegmentedWindowLayout.Cells(){public boolean joined(int x,int y){return(bits&(1<<(y*3+x)))!=0;}public int shape(int x,int y){return x==2?1:0;}public int facing(int x,int y){return y==3?1:0;}};
  SegmentedWindowLayout[] actual=SegmentedWindowLayout.derive(3,4,cells);boolean[][] reach=new boolean[12][12];
  for(int i=0;i<12;i++)for(int j=0;j<12;j++){int x=i%3,y=i/3,u=j%3,v=j/3;reach[i][j]=i==j||cells.joined(x,y)&&cells.joined(u,v)&&cells.shape(x,y)==cells.shape(u,v)&&cells.facing(x,y)==cells.facing(u,v)&&Math.abs(x-u)+Math.abs(y-v)==1;}
  for(int k=0;k<12;k++)for(int i=0;i<12;i++)for(int j=0;j<12;j++)reach[i][j]|=reach[i][k]&&reach[k][j];
  for(int i=0;i<12;i++){int minX=i%3,maxX=minX,minY=i/3,maxY=minY;for(int j=0;j<12;j++)if(reach[i][j]){minX=Math.min(minX,j%3);maxX=Math.max(maxX,j%3);minY=Math.min(minY,j/3);maxY=Math.max(maxY,j/3);}SegmentedWindowLayout expected=new SegmentedWindowLayout(i%3-minX,i/3-minY,maxX-minX+1,maxY-minY+1);if(!actual[i].equals(expected))throw new AssertionError("window topology mask="+mask+" cell="+i);checks++;}
 }
 System.out.println("Segmented window layout PASS checks="+checks+" bottom-up rows holes shape facing corner contacts");}
}
