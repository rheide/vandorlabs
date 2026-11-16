package com.vandorlabs.ui;
/** Authoritative Source layout, with a compact form for minimum GUI sizes. */
public final class SynthesisDialog {
    public static final String CATALOG_CHIP_TOOLTIP="Insert Catalog Chip to load designs";
 public static final String[] DESIGNS={"synthesis_tower","assembly_press","enclosed_gantry"};
 public static final String[] NAMES={"Synthesis Tower","Assembly Press","Enclosed Gantry"};
 public static final DialogStyle.Shell STYLE=DialogStyle.SYNTHESIS;
 public static final int MACHINE_SLOTS=7,PLAYER_START=7,SLOT_COUNT=43;
 public static Layout layout(int screenWidth,int screenHeight){return new Layout(screenHeight<314||screenWidth<356);}
 public static final class Layout {
  public final boolean compact;public final int width,height,machineY,inventoryY,hotbarY,inventoryX,previewLeft,previewRight,previewTop,previewBottom;
  public Layout(boolean compact){this.compact=compact;width=compact?312:348;height=compact?240:306;machineY=compact?136:190;inventoryY=compact?162:224;hotbarY=compact?220:282;inventoryX=compact?76:100;previewLeft=compact?158:174;previewRight=width-12;previewTop=compact?58:72;previewBottom=compact?110:158;}
  public int machineX(int slot){switch(slot){case 0:return compact?18:28;case 1:return compact?104:118;case 2:return compact?190:208;case 3:return width-36;case 4:return compact?208:226;case 5:return compact?226:244;case 6:return compact?244:262;default:throw new IllegalArgumentException("Slot");}}
  public int slotX(int slot){return slot<7?machineX(slot):inventoryX+(slot-7)%9*18;}
  public int slotY(int slot){return slot<7?machineY:slot<34?inventoryY+(slot-7)/9*18:hotbarY;}
  public int progressX(){return compact?43:53;}
  public int progressWidth(){return 51;}
  public int receiverY(){return compact?60:68;}
  public int tabWidth(){return (width-28)/2;}
 }
 private SynthesisDialog(){}
}
