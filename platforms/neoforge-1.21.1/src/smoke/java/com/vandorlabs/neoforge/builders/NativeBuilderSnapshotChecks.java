package com.vandorlabs.neoforge.builders;
import net.minecraft.nbt.*;
/** Pure comparison regression for old pending journals before the cosmetic bay counter existed. */
public final class NativeBuilderSnapshotChecks {
 private static void need(boolean value,String why){if(!value)throw new AssertionError("Builder snapshot: "+why);}
 public static void main(String[] args){run();}
 public static void run(){
  var old=new CompoundTag();old.putInt("Activation",1);old.putIntArray("Channels",new int[]{7,8});var inventory=new CompoundTag();inventory.put("Items",new ListTag());old.put("Inventory",inventory);old.putString("UnknownOwnedField","preserved");
  var zero=old.copy();zero.putLongArray("BatterySequences",new long[4]);var frozen=zero.copy();need(!old.equals(zero),"negative control: raw old/new tags differ");need(NativeBuilderData.sameSnapshots(old,zero)&&NativeBuilderData.sameSnapshots(zero,old),"absent counter equals exactly four zero long counters");need(zero.equals(frozen),"normalization does not mutate retained original");
  Tag[] invalid={StringTag.valueOf("0"),new IntArrayTag(new int[4]),new LongArrayTag(new long[0]),new LongArrayTag(new long[3]),new LongArrayTag(new long[5]),new LongArrayTag(new long[]{0,0,1,0}),new LongArrayTag(new long[]{-1,0,0,0}),new LongArrayTag(new long[]{0,0,0,Long.MAX_VALUE})};
  for(int i=0;i<invalid.length;i++){var changed=old.copy();changed.put("BatterySequences",invalid[i]);var retained=changed.copy();need(!NativeBuilderData.sameSnapshots(old,changed)&&!NativeBuilderData.sameSnapshots(changed,old),"typed/length/nonzero counter difference stays exact "+i);need(changed.equals(retained),"invalid original remains unchanged "+i);}
  for(int kind=0;kind<4;kind++){var changed=zero.copy();if(kind==0)changed.putInt("Activation",2);else if(kind==1)changed.putIntArray("Channels",new int[]{7,9});else if(kind==2)changed.getCompound("Inventory").putString("UnknownItemField","changed");else changed.putString("UnknownOwnedField","changed");need(!NativeBuilderData.sameSnapshots(old,changed),"settings/inventory/unknown ownership fields remain exact "+kind);}
  System.out.println("NativeBuilderSnapshotChecks PASS oldAbsent/fourZeroOnly/invalidTypeLengthAndNonzeroExact/ownershipFieldsExact/immutableInput");
 }
 private NativeBuilderSnapshotChecks(){}
}
