package com.vandorlabs.navigation;
public final class NavigationMotionTest {
 private static int count;private static void need(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  for(int base:new int[]{1000,5000}){need(NavigationRange.radius(base,64)==base/2,"halved at64");need(NavigationRange.radius(base,128)==base,"maximum at128");need(NavigationRange.radius(base,255)==base,"capped above128");need(NavigationRange.radius(base,96)==base*3/4,"linear midpoint");need(NavigationRange.radius(base,100.5)>NavigationRange.radius(base,100),"fractional height");}
  NavigationMotion p=new NavigationMotion();p.snap(0,0,4);p.move(100,-40,2,10);need(p.x(10)==0&&p.scale(10)==4,"no jump on action");need(p.x(16)==50&&p.z(16)==-20,"smooth pan midpoint");need(Math.abs(p.scale(16)-Math.sqrt(8))<1e-8,"smooth geometric zoom");double before=p.x(16);p.move(-100,80,8,16);need(p.x(16)==before,"reversal starts at displayed view");need(p.x(28)==-100&&p.scale(28)==8,"exact endpoint");need(!p.settling(28),"early reply waits for interpolation");need(NavigationMotion.pulse(0)!=NavigationMotion.pulse(10),"slow bright pulse");need((NavigationMotion.pulse(10)&255)>=24&&(NavigationMotion.pulse(10)&255)<=88,"pulse stays in visible grey range");System.out.println("Navigation motion PASS assertions="+count+" radius endpoints capped smooth pan zoom reversal bright pulse");
 }
}
