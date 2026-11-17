package com.vandorlabs.ui;
public final class SynthesisDialogTest {
 private static int checks;
 private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  need(SynthesisDialog.MACHINE_SLOTS==7&&SynthesisDialog.PLAYER_START==7&&SynthesisDialog.SLOT_COUNT==43,"Persistent slot indices");
  for(boolean compact:new boolean[]{false,true})for(boolean narrow:new boolean[]{false,true}){
   SynthesisDialog.Layout d=new SynthesisDialog.Layout(compact,narrow);
   for(int i=0;i<43;i++){int x=d.slotX(i),y=d.slotY(i);need(x>=0&&y>=0&&x+16<=d.width&&y+16<=d.height,"Slot bounds "+i);for(int j=0;j<i;j++)need(Math.abs(x-d.slotX(j))>=18||Math.abs(y-d.slotY(j))>=18,"Slot overlap "+i+"/"+j);}
   int[] batteries={2,4,5,6};for(int i=0;i<batteries.length;i++){need(d.slotY(batteries[i])==d.machineY,"Horizontal battery row");need(d.slotX(batteries[i])==d.width-84+i*18,"Append-only bank order");}
   need(d.slotX(3)==18&&d.slotX(0)==58&&d.slotX(1)==108,"Production chip/input/output order");
   need(d.slotX(0,true)==18&&d.slotX(3,true)==108,"Digitize input/chip order");
   for(int slot=0;slot<43;slot++)if(slot!=0&&slot!=3)need(d.slotX(slot,true)==d.slotX(slot),"Mode preserves unrelated slot positions "+slot);
   need(d.rfWidth>0&&d.rfHeight>0&&d.rfY==d.machineY&&d.rfX>=d.slotX(1)+18&&d.rfX+d.rfWidth<=d.slotX(2)-2,"RF bar between output and batteries");
   need(d.progressX()>d.machineX(0)+16&&d.progressX()+d.progressWidth()<d.machineX(1)-1,"Progress between production input/output");
   need(d.progressX()>d.slotX(0,true)+16&&d.progressX()+d.progressWidth()<d.slotX(3,true)-1,"Progress between digitize input/chip");
   need(SynthesisDialog.layout(narrow?320:500,compact?240:340).compact==compact&&SynthesisDialog.layout(narrow?320:500,compact?240:340).narrow==narrow,"Shared viewport selection");
   if(compact&&narrow)need(d.width<=320&&d.height<=240,"Minimum GUI");
  }
  System.out.println("SynthesisDialogTest PASS checks="+checks+" fourViewports/43Slots/horizontalAppendOnlyBanks/modeMapping/RF/nonOverlap");
 }
}
