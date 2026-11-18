package com.vandorlabs.navigation;

public final class NavigationCoverageTest {
 private static int assertions;private static void need(boolean yes,String why){assertions++;if(!yes)throw new AssertionError(why);}
 private static boolean naive(int[] a,int x,int z){for(int i=0;i<a.length;i+=3)if(NavigationMap.covers(x,z,a[i],a[i+1],a[i+2]))return true;return false;}
 public static void main(String[] args){
  int[] circles=new int[1024*3];for(int i=0;i<1024;i++){circles[i*3]=((i%32)-16)*12000;circles[i*3+1]=((i/32)-16)*12000;circles[i*3+2]=5000;}
  NavigationCoverage indexed=new NavigationCoverage(circles);java.util.Random random=new java.util.Random(9127);for(int i=0;i<20000;i++){int x=random.nextInt(400000)-200000,z=random.nextInt(400000)-200000;need(indexed.contains(x,z)==naive(circles,x,z),"exact union including bounded-index fallback");}
  NavigationCoverage single=new NavigationCoverage(new int[]{-13,21,5000});need(single.contains(-13,21)&&single.exactTests==0,"full square avoids individual circle queries");need(single.contains(4987,21)&&!single.contains(4988,21),"one-block boundary preserved");need(!single.contains(4987,22),"boundary tile never grants unobserved corners");
  NavigationCoverage large=new NavigationCoverage(new int[]{0,0,10000});need(large.contains(10000,0)&&large.contains(-10000,0)&&large.contains(0,10000)&&large.contains(0,-10000),"scaled10000 inclusive cardinal boundary");need(!large.contains(10001,0)&&!large.contains(-10001,0)&&!large.contains(0,10001)&&!large.contains(0,-10001),"scaled10001 outside coverage");need(!large.contains(10000,1),"scaled edge tile retains exact circle test");boolean excessive=false;try{new NavigationCoverage(new int[]{0,0,10001});}catch(IllegalArgumentException expected){excessive=true;}need(excessive,"oversized radius rejected before indexing");
  int[] sparse=new int[1024*3];for(int i=0;i<1024;i++){sparse[i*3]=(i%32)*4096;sparse[i*3+1]=(i/32)*4096;sparse[i*3+2]=32;}
  NavigationCoverage complete=new NavigationCoverage(sparse);for(int i=0;i<1024;i++){int x=sparse[i*3],z=sparse[i*3+1];need(complete.contains(x,z),"fully indexed module center");need(complete.contains(x+32,z)&&!complete.contains(x+32,z+1),"small circle exact boundary");need(!complete.contains(x+1024,z+1024),"uncovered tile needs no fallback circles");}need(complete.retainedBytes()<=NavigationCoverage.MAX_BYTES,"fully indexed module remains within budget");
  int[] modest=new int[128*3];for(int i=0;i<128;i++){modest[i*3]=(i-64)*10000;modest[i*3+1]=0;modest[i*3+2]=500;}
  NavigationCoverage fast=new NavigationCoverage(modest);for(int i=0;i<128;i++)need(fast.contains((i-64)*10000,0),"sparse square lookup");need(fast.exactTests<=256,"square index prunes unrelated circles");need(indexed.retainedBytes()<=NavigationCoverage.MAX_BYTES,"bounded index retained memory");System.out.println("Navigation coverage PASS assertions="+assertions+" exact block edges negative coordinates budget fallback indexed-exact-tests="+fast.exactTests+" naive-worst="+(128*128));
 }
}
