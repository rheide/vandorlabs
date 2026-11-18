package com.vandorlabs.navigation;
/** Exact range endpoints, continuous arm height and capped linear size bonus. */
public final class NavigationRangeTest {
 public static void main(String[] args){int cases=0;for(int base:new int[]{1000,5000}){need(NavigationRange.radius(base,64)==base/2&&NavigationRange.radius(base,128)==base,"released altitude endpoints");need(NavigationRange.radius(base,-1000)==base/8&&NavigationRange.radius(base,16)==base/8,"low height minimum");for(double size:new double[]{1,1.25,1.5,2,3,8}){need(NavigationRange.radius(base,64,size)==base/2,"size does not alter Y64");int cap=(int)Math.round(base*(1+Math.min(1,size-1)));need(NavigationRange.radius(base,128,size)==cap&&NavigationRange.radius(base,10000,size)==cap,"size and altitude caps");int previous=0;for(int y=-32;y<=192;y++){int radius=NavigationRange.radius(base,y,size);need(radius>=previous&&radius<=base*2,"monotone bounded height response");previous=radius;cases++;}need(NavigationRange.radius(base,96,size)==(int)Math.round(base*(.75+Math.min(1,size-1)*.5)),"half size bonus at Y96");}need(NavigationRange.radius(base,65)>NavigationRange.radius(base,64)&&NavigationRange.radius(base,64.25,2)>NavigationRange.radius(base,64,2),"fractional physical arm height applied without percent steps");}need(NavigationRange.radius(5000,128,4/3.0)==6667&&NavigationRange.radius(5000,128,5/3.0)==8333&&NavigationRange.radius(5000,128,2)==10000,"Dish4/5/6cube endpoints");need(NavigationRange.radius(1000,128,1.5)==1500&&NavigationRange.radius(1000,128,2)==2000,"Array3x6/4x8 endpoints");for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})need(NavigationRange.radius(5000,bad,1)==0&&NavigationRange.radius(5000,128,bad)==0,"nonfinite refused");need(NavigationRange.radius(0,128,1)==0&&NavigationRange.radius(1000,128,.99)==0,"invalid maximum or size refused");checkTerrain(NavigationRange.SENTINEL_BASE,63,250,500,1000);
checkTerrain(NavigationRange.SURVEY_BASE,250,1000,2000,4000);
checkRadar();
System.out.println("NavigationRangeTest PASS heightCases="+cases+" baseEndpoints/fractionalArms/linearBonus/cappedAtDouble/invalidInput");}
 private static void checkTerrain(int base,int minimum,int seaLevel,int highSmall,int highLarge){
     need(NavigationRange.radius(base,16)==minimum,"minimum altitude endpoint");
     need(NavigationRange.radius(base,64)==seaLevel&&NavigationRange.radius(base,64,8)==seaLevel,"all sizes share Y64 radius");
     need(NavigationRange.radius(base,128)==highSmall&&NavigationRange.radius(base,128,8)==highLarge,"Y128 size endpoints");
     need(NavigationRange.radius(base,200,2)==highLarge,"mounting higher keeps the size and altitude cap");
 }
 private static void checkRadar(){
     int sentinel=NavigationRange.radarRadius("standalone_antenna"),survey=NavigationRange.radarRadius("standalone_dish");
     need(sentinel==128&&survey==256,"detection radii");
     need(NavigationSurface.period(sentinel)==160&&NavigationSurface.period(survey)==480,"scanner and display use 8- and 24-second periods");
     for(int radius:new int[]{sentinel,survey}){
         need(NavigationRange.detects(63,radius,64,0,false,0,0,radius),"above water and inclusive radar edge");
         need(!NavigationRange.detects(63,radius+.5,64,0,false,0,0,radius),"outside radar edge");
         need(!NavigationRange.detects(63,0,63,0,false,0,0,radius),"sea-level contact hidden");
         need(!NavigationRange.detects(63,0,68,0,true,0,0,radius),"crouched contact hidden");
     }
     NavigationCanvas canvas=new NavigationCanvas(384,384,true);
     NavigationOverlay radar=new NavigationOverlay(new int[]{-256,0,-160,256,0,248},new int[0],new int[]{-256,0,sentinel,256,0,survey});
     int[] rings={0,0},outerColors={0,0};
     radar.drawCircles(canvas,0,0,4,(x,y,ex,ey,color)->{
         int type=x<canvas.width/2.0?0:1;
         need(type==0?(color>>16&255)>(color&255):(color&255)>(color>>16&255),"observer-specific outer ring hue");
         outerColors[type]=color;
     });
     int[] sweeps={0};
     NavigationOverlay.Segment ring=(x,y,ex,ey,color)->{
         need(x>=-1e-6&&y>=canvas.mapTop-1e-6&&ex<=canvas.width+1e-6&&ey<=canvas.mapBottom+1e-6,"radar ring clipped on screen");
         int type=x<canvas.width/2.0?0:1;
         need(type==0?(color>>16&255)>(color&255):(color&255)>(color>>16&255),"observer-specific inner ring hue");
         need((color>>8&255)>(outerColors[type]>>8&255),"inner ring is lighter than the outer ring");
         rings[type]++;
     };
     radar.drawRadar(canvas,0,0,4,40,ring,(x,y,ex,ey,color)->{
         boolean array=x<canvas.width/2.0;
         need(x==(array?128:256),"radar centered on source");
         need(Math.abs(Math.hypot(ex-x,ey-y)-(array?32:64))<1e-6,"sweep length matches its detection radius");
         if(array)need(ex>x&&Math.abs(ey-y)<1e-6,"Sentinel reaches east at tick 40");
         else need(Math.abs(ex-x-32)<1e-6&&ey<y,"Survey advances only 30 degrees at tick 40");
         sweeps[0]++;
     });
     need(rings[0]>0&&rings[1]>0&&sweeps[0]==2,"both active radars draw their own ring and sweep");
     NavigationOverlay.EMPTY.drawRadar(canvas,0,0,4,60,(x,y,ex,ey,color)->need(false,"disabled radar ring"),(x,y,ex,ey,color)->need(false,"disabled radar sweep"));
 }
 private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
}
