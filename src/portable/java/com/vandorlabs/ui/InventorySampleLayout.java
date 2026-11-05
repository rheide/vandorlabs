package com.vandorlabs.ui;

/** Inline inventory sample geometry and dispatch; stack access and authorization are host-owned. */
public final class InventorySampleLayout {
    public static final int CELL=18,HEADER=16,BACK_HEIGHT=14,MAX_COLUMNS=9,INVENTORY_SLOTS=36,HOTBAR_SLOTS=9;
    public static final int HOTBAR_WIDTH=MAX_COLUMNS*CELL,HOTBAR_HEIGHT=HEADER+CELL;
    public static final int OUTSIDE=-1,CONSUMED=-2,BACK=-3;
    public final int x,y,width,height,slots;
    public InventorySampleLayout(int x,int y,int width,int height){this(x,y,width,height,INVENTORY_SLOTS);}
    public InventorySampleLayout(int x,int y,int width,int height,int slots){this.x=x;this.y=y;this.width=width;this.height=height;this.slots=slots;}
    public static InventorySampleLayout hotbar(int x,int y){return new InventorySampleLayout(x,y,HOTBAR_WIDTH,HOTBAR_HEIGHT,HOTBAR_SLOTS);}
    public int columns(){return Math.max(1,Math.min(MAX_COLUMNS,width/CELL));}
    public int visibleRows(){return Math.max(1,(height-HEADER)/CELL);}
    public int maxScroll(){return Math.max(0,(slots+columns()-1)/columns()-visibleRows());}
    public boolean contains(int mx,int my){return mx>=x&&mx<x+width&&my>=y&&my<y+height;}
    /** Raw cell rectangle used for hover; dispatch separately clips it to the panel. */
    public int slotAt(int mx,int my,int scroll){
        if(mx<x||mx>=x+columns()*CELL||my<y+HEADER||my>=y+HEADER+visibleRows()*CELL)return -1;
        int slot=(scroll+(my-y-HEADER)/CELL)*columns()+(mx-x)/CELL;return slot<slots?slot:-1;
    }
    /** Negative results distinguish ignored input, consumed blank/right input and the catalog callback. */
    public int clickAction(int mx,int my,int button,int scroll,boolean hasCatalog){
        if(!contains(mx,my))return OUTSIDE;
        if(button==0&&my<y+BACK_HEIGHT&&hasCatalog)return BACK;
        int index=slotAt(mx,my,scroll);return button==0&&index>=0?index:CONSUMED;
    }
    public boolean acceptsWheel(int mx,int my,int delta){return delta!=0&&contains(mx,my);}
    public int scrollAfterWheel(int scroll,int delta){return delta==0?scroll:DialogInput.wheel(scroll,maxScroll(),delta);}
    public int cellX(int visibleIndex){return x+visibleIndex%columns()*CELL;}
    public int cellY(int visibleIndex){return y+HEADER+visibleIndex/columns()*CELL;}
    public int visibleSlot(int visibleIndex,int scroll){return scroll*columns()+visibleIndex;}
    public String headerLabel(boolean hasCatalog){return hasCatalog?"< Catalog / inventory":slots==HOTBAR_SLOTS?"Choose a hotbar block":"Choose an inventory item";}
}
