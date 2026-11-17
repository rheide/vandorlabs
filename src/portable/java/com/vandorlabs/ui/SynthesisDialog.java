package com.vandorlabs.ui;
/** Authoritative Source layout, with a compact form for minimum GUI sizes. */
public final class SynthesisDialog {
 public static final String CATALOG_CHIP_TOOLTIP="Insert Catalog Chip to load designs";
 public static String inputLabel(String machine){return "assembly_press".equals(machine)?"Ingot":"Block";}
 public static String inputTooltip(String machine){return "assembly_press".equals(machine)?"Programmable Matter Ingots":"Programmable Matter Blocks";}
 public static final String[] DESIGNS={"synthesis_tower","assembly_press","enclosed_gantry"};
 public static final String[] NAMES={"Synthesis Tower","Assembly Press","Enclosed Gantry"};
 public static final DialogStyle.Shell STYLE=DialogStyle.SYNTHESIS;
 public static final int MACHINE_SLOTS=7,PLAYER_START=7,SLOT_COUNT=43;
 public static Layout layout(int screenWidth,int screenHeight){return new Layout(screenHeight<314,screenWidth<420);}
 public static String rfTooltip(int energy,int capacity){return String.format(java.util.Locale.ROOT,"%,d / %,d RF",Math.max(0,energy),Math.max(0,capacity));}
 public static final class Layout {
  public final boolean compact,narrow;
  public final int width,height,machineY,topSlotY,moduleTop,inventoryY,hotbarY,inventoryX,previewLeft,previewRight,previewTop,previewBottom;
  public final int listLeft,listRight,listTop,listBottom,rowHeight,rfX,rfY,rfWidth,rfHeight;
  public Layout(boolean compact){this(compact,compact);}
  public Layout(boolean compact,boolean narrow){
   this.compact=compact;this.narrow=narrow;width=narrow?312:420;height=compact?240:306;
   machineY=topSlotY=compact?64:66;moduleTop=compact?84:100;
   inventoryY=compact?162:224;hotbarY=compact?220:282;
   previewLeft=narrow?126:190;previewRight=width-42;previewTop=compact?96:100;previewBottom=compact?148:204;
   inventoryX=(previewLeft+width-12-162)/2;
   listLeft=12;listRight=previewLeft-12;listTop=compact?92:100;listBottom=height-12;rowHeight=compact?17:24;
   rfX=138;rfY=machineY;rfWidth=width-100-rfX;rfHeight=16;
  }
  public int listHeadingY(){return listTop-(compact?10:12);}
  public int visibleRows(){return Math.max(1,(listBottom-listTop)/rowHeight);}
  public int machineX(int slot){switch(slot){case 0:return 58;case 1:return 108;case 3:return 18;case 2:return width-84;case 4:return width-66;case 5:return width-48;case 6:return width-30;default:throw new IllegalArgumentException("Slot");}}
  public int slotX(int slot){return slot<7?machineX(slot):inventoryX+(slot-7)%9*18;}
  public int slotX(int slot,boolean digitize){return digitize&&slot==0?18:digitize&&slot==3?108:slotX(slot);}
  public int slotY(int slot){return slot<7?machineY:slot<34?inventoryY+(slot-7)/9*18:hotbarY;}
  public int progressX(){return 84;}
  public int progressWidth(){return 16;}
  public int receiverY(){return compact?88:100;}
  public int receiverWidth(){return previewRight-listLeft;}
  public int tabWidth(){return (width-28)/2;}
  public int secondTabX(){return 14+tabWidth();}
 }
 private SynthesisDialog(){}
}
