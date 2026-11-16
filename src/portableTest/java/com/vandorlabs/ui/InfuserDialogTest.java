package com.vandorlabs.ui;
/** Both adapters consume these same machine/player positions and non-combustion work gauges. */
public final class InfuserDialogTest {
 private static void need(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  need(InfuserDialog.WIDTH==320&&InfuserDialog.HEIGHT==222,"standard machine panel");int[][] expected={{40,74},{112,74},{264,74}};
  for(int slot=0;slot<3;slot++)need(InfuserDialog.slotX(slot)==expected[slot][0]&&InfuserDialog.slotY(slot)==expected[slot][1],"machine slot "+slot);
  for(int slot=0;slot<39;slot++){int x=InfuserDialog.slotX(slot),y=InfuserDialog.slotY(slot);need(x>=0&&y>=0&&x+16<InfuserDialog.WIDTH&&y+16<InfuserDialog.HEIGHT,"padded inventory");for(int other=0;other<slot;other++){int ox=InfuserDialog.slotX(other),oy=InfuserDialog.slotY(other);need(x+16<=ox||ox+16<=x||y+16<=oy||oy+16<=y,"non-overlapping slots");}}
  need(InfuserDialog.slotX(3)==100&&InfuserDialog.slotY(3)==124&&InfuserDialog.slotY(30)==182,"stable furnace indices with shared new placement");
  need(InfuserDialog.STYLE.headerHeight==28&&"Matter Infuser".equals(InfuserDialog.TITLE),"standard header");
  need(InfuserDialog.DONE_X+InfuserDialog.DONE_WIDTH<InfuserDialog.INVENTORY_X&&InfuserDialog.DONE_Y+InfuserDialog.DONE_HEIGHT<InfuserDialog.FOOTER_Y,"Done avoids hotbar/footer");
  need(InfuserDialog.fill(100,false,0,10000)==42&&InfuserDialog.fill(0,true,5000,10000)==42&&InfuserDialog.fill(0,true,10000,10000)==84,"iron work and persistent charge share gauge");
  need("10000/10000 RF".equals(InfuserDialog.detail(0,true,10000,10000)),"bounded numeric charge text");
  need(InfuserDialog.SUN_Y>InfuserDialog.STYLE.headerHeight&&InfuserDialog.FOOTER_Y+9<InfuserDialog.HEIGHT,"status labels fit");
  System.out.println("Infuser dialog PASS sharedStandardPanel/39slots/Done/header/solar/workAndBatteryGauge");
 }
}
