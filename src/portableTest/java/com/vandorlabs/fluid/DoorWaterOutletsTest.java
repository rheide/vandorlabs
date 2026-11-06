package com.vandorlabs.fluid;
public final class DoorWaterOutletsTest {
 private static void need(boolean value){if(!value)throw new AssertionError("Door outlet decision");}
 public static void main(String[] args){
  need(DoorWaterOutlets.wetSide(true,false,true,false)==0);need(DoorWaterOutlets.wetSide(false,true,false,true)==1);need(DoorWaterOutlets.wetSide(true,true,true,true)==-1);need(DoorWaterOutlets.wetSide(true,false,true,true)==-1);need(DoorWaterOutlets.wetSide(false,false,false,false)==-1);
  need(DoorWaterOutlets.supply(true,false,false,false,false,true)==DoorWaterOutlets.RETAIN);need(DoorWaterOutlets.supply(false,false,false,false,false,true)==DoorWaterOutlets.NONE);
  need(DoorWaterOutlets.supply(false,true,true,true,true,false)==DoorWaterOutlets.NONE);need(DoorWaterOutlets.supply(true,true,true,true,true,false)==DoorWaterOutlets.RETAIN);need(DoorWaterOutlets.supply(false,true,true,false,true,false)==DoorWaterOutlets.PUBLISH);
  need(DoorWaterOutlets.retire(true,false,false,false,true,true)==DoorWaterOutlets.RETAIN);need(DoorWaterOutlets.retire(true,false,false,true,true,true)==DoorWaterOutlets.DRAIN);need(DoorWaterOutlets.retire(false,false,true,true,true,true)==DoorWaterOutlets.NONE);need(DoorWaterOutlets.retire(true,true,true,true,true,true)==DoorWaterOutlets.NONE);need(DoorWaterOutlets.retire(true,false,true,true,false,false)==DoorWaterOutlets.NONE);
  System.out.println("Door water outlets PASS loaded/unloaded/supply/natural sources/retirement");
 }
}
