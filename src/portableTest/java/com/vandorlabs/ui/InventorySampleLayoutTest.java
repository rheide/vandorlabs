package com.vandorlabs.ui;

/** Independent inline inventory/hotbar cell, clipping, header and wheel goldens. */
public final class InventorySampleLayoutTest {
    private InventorySampleLayoutTest() { }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] ignored){
        InventorySampleLayout full=new InventorySampleLayout(10,20,162,88);
        require(full.columns()==9&&full.visibleRows()==4&&full.maxScroll()==0,"Full36-slot geometry");
        require(full.slotAt(10,36,0)==0&&full.slotAt(171,107,0)==35,"First/last inventory cell boundaries");
        require(full.slotAt(172,36,0)==-1&&full.slotAt(10,108,0)==-1&&full.slotAt(10,35,0)==-1,"Raw content half-open bounds");
        InventorySampleLayout compact=new InventorySampleLayout(4,6,55,52);
        require(compact.columns()==3&&compact.visibleRows()==2&&compact.maxScroll()==10,"Compact rows andscroll cap");
        require(compact.slotAt(57,57,0)==5&&compact.slotAt(57,57,10)==35,"Scrolled final inventory cell");
        require(compact.cellX(4)==22&&compact.cellY(4)==40&&compact.visibleSlot(4,3)==13,"Cell drawing order matches inventory indices");
        require(compact.clickAction(4,6,0,0,true)==InventorySampleLayout.BACK&&compact.clickAction(58,19,0,0,true)==InventorySampleLayout.BACK,"Catalog header14px hit region");
        require(compact.clickAction(4,20,0,0,true)==InventorySampleLayout.CONSUMED&&compact.clickAction(4,21,0,0,true)==InventorySampleLayout.CONSUMED,"14-15px header gap consumed");
        require(compact.clickAction(4,22,0,0,true)==0&&compact.clickAction(4,6,0,0,false)==InventorySampleLayout.CONSUMED,"Content/header without callback");
        require(compact.clickAction(4,22,1,0,true)==InventorySampleLayout.CONSUMED&&compact.clickAction(4,6,1,0,true)==InventorySampleLayout.CONSUMED,"Non-left inside events consumed without action");
        require(compact.clickAction(59,22,0,0,true)==InventorySampleLayout.OUTSIDE&&compact.clickAction(4,58,0,0,true)==InventorySampleLayout.OUTSIDE,"Dispatch clips outer panel");
        require(compact.acceptsWheel(4,6,-120)&&!compact.acceptsWheel(4,6,0)&&!compact.acceptsWheel(59,22,-120),"Wheel includes header but excludes zero/outside");
        require(compact.scrollAfterWheel(0,120)==0&&compact.scrollAfterWheel(0,-120)==1&&compact.scrollAfterWheel(10,-120)==10&&compact.scrollAfterWheel(4,0)==4,"One-row wheel normalization/clamping");
        InventorySampleLayout narrow=new InventorySampleLayout(10,20,13,23);
        require(narrow.columns()==1&&narrow.visibleRows()==1&&narrow.slotAt(27,53,0)==0,"Raw hover retains partial cell beyond panel");
        require(narrow.clickAction(27,53,0,0,false)==InventorySampleLayout.OUTSIDE,"Click differs from raw hover outside panel");
        InventorySampleLayout partial=new InventorySampleLayout(0,0,90,34);
        require(partial.maxScroll()==7&&partial.slotAt(0,16,7)==35&&partial.slotAt(18,16,7)==-1,"Incomplete last row has no phantom slot");
        InventorySampleLayout hotbar=InventorySampleLayout.hotbar(7,9);
        require(hotbar.width==162&&hotbar.height==34&&hotbar.slots==9&&hotbar.columns()==9&&hotbar.visibleRows()==1&&hotbar.maxScroll()==0,"Hotbar factory geometry");
        require(hotbar.slotAt(168,42,0)==8&&hotbar.slotAt(169,42,0)==-1&&hotbar.acceptsWheel(8,10,-1)&&hotbar.scrollAfterWheel(0,-1)==0,"Hotbar boundary and consumed stationary wheel");
        require(full.headerLabel(false).equals("Choose an inventory item")&&hotbar.headerLabel(false).equals("Choose a hotbar block")&&full.headerLabel(true).equals("< Catalog / inventory"),"Current header labels");
        System.out.println("Inventory sample layout PASS 36/9slots,18pxcells,14/16headergap,partial bounds, wheel and clipping");
    }
}
