package com.vandorlabs.navigation;
/** Original-map square coverage, pixel identity, immutable cache keys and malformed input rejection. */
public final class SavedMapPixelsTest {
 private static int checks;
 public static void main(String[] args){
  byte[] colors=new byte[16384];int[] palette=new int[256];for(int i=0;i<256;i++)palette[i]=0xff000000|i;for(int i=0;i<colors.length;i++)colors[i]=(byte)i;
  for(int scale=0;scale<=4;scale++){
   SavedMapPixels map=new SavedMapPixels("0",-313,867,scale,colors,palette);int step=1<<scale;
   need(map.span()==128*step&&map.minX()==-313-64*step&&map.minZ()==867-64*step,"exact centered square");
   for(int z=0;z<128;z++)for(int x=0;x<128;x++){int wx=map.minX()+x*step,wz=map.minZ()+z*step;need(map.contains(wx,wz)&&map.colorAt(wx,wz)==palette[colors[z*128+x]&255],"every original unsigned palette index");need(map.colorAt(wx+step-1,wz+step-1)==map.colorAt(wx,wz),"pixel fills exact block footprint");}
   need(!map.contains(map.minX()-1,map.minZ())&&!map.contains(map.maxXExclusive(),map.minZ())&&!map.contains(map.minX(),map.maxZExclusive()),"half open boundary");
   SavedMapPixels same=new SavedMapPixels("0",-313,867,scale,colors,palette);need(same.equals(map)&&same.hashCode()==map.hashCode(),"equivalent independent raster cache key");need(!new SavedMapPixels("different",-313,867,scale,colors,palette).equals(map),"dimension key separates maps");
  }
  SavedMapPixels large=new SavedMapPixels("0",0,0,4,colors,palette);need(large.contains(-1024,-1024)&&!NavigationMap.covers(-1024,-1024,0,0,1000),"scale4 corner beyond pin radius remains covered");
  byte[] blank=new byte[16384];int[] transparent=new int[256];SavedMapPixels map=new SavedMapPixels("0",0,0,0,blank,transparent);need(map.colorAt(0,0)==0,"blank original stays blank");blank[0]=1;transparent[0]=123;map.colors()[0]=7;map.palette()[0]=9;need(map.colorAt(map.minX(),map.minZ())==0,"input and accessor arrays cannot mutate snapshot");
  int[] collision=new int[256];collision[0]=1;collision[1]=-31;SavedMapPixels other=new SavedMapPixels("0",0,0,0,new byte[16384],collision);need(other.hash()==map.hash()&&!other.equals(map),"hash collision compares original pixels/palette");
  reject(()->new SavedMapPixels("",0,0,0,colors,palette));reject(()->new SavedMapPixels("0",0,0,-1,colors,palette));reject(()->new SavedMapPixels("0",0,0,5,colors,palette));reject(()->new SavedMapPixels("0",NavigationMap.LIMIT+1,0,0,colors,palette));reject(()->new SavedMapPixels("0",0,0,0,new byte[16383],palette));reject(()->new SavedMapPixels("0",0,0,0,colors,new int[255]));reject(()->map.colorAt(map.maxXExclusive(),0));
  System.out.println("SavedMapPixelsTest PASS checks="+checks+" allScales/exactSquare/blank/immutability/hashCollision/invalidInput");
 }
 private static void reject(Runnable r){boolean rejected=false;try{r.run();}catch(IllegalArgumentException expected){rejected=true;}need(rejected,"invalid map rejected");}
 private static void need(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
}
