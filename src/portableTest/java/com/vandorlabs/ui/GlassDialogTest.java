package com.vandorlabs.ui;

import java.util.*;

/** Current four-tab glass policy; hidden compatibility controls and panes are intentionally distinct. */
public final class GlassDialogTest {
    private static void require(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    public static void main(String[] ignored){int cases=0;String[] pages={"Glass","Front","Interior","Exterior"},tints={"Mild","Normal","Strong","Opaque"},layouts={"Stretch","Tile","Fit (aspect)"};int base=0x10000000;
        for(int width:new int[]{320,854,1280})for(int height:new int[]{240,720})for(int tint=0;tint<4;tint++)for(int layout=0;layout<3;layout++)for(boolean join:new boolean[]{false,true})for(boolean framed:new boolean[]{false,true})for(int material:new int[]{-1,0,91}){
            ProgrammableDialogLayout panel=new ProgrammableDialogLayout(width,height);GlassDialog.State initial=new GlassDialog.State(7,tint,material,layout,join,framed,false);
            require(initial.size==0,"current UI always writes zero size");
            for(int page=0;page<4;page++){
                GlassDialog.State state=initial.action(196+page,1);require(state.page==page&&state.framePage()==(page>0),"four current page transitions");List<GlassDialog.Control> tabs=GlassDialog.tabs(panel,state);int span=(panel.width-22)/4;require(tabs.size()==4,"four tabs");for(int i=0;i<4;i++){GlassDialog.Control c=tabs.get(i);require(c.id==196+i&&c.x==panel.left+12+i*span&&c.y==panel.top+30&&c.width==span-2&&c.height==18&&c.label.equals(pages[i])&&c.tab&&c.enabled==(page!=i)&&c.visible,"current full-width tabs");}
                List<GlassDialog.Control> body=GlassDialog.body(panel,state);if(page>0)require(body.isEmpty(),"frame pages delegate to existing pane");else{require(body.size()==(material>=0?4:3),"conditional layout control");String[] labels={"Join: "+(join?"On":"Off"),"Tint: "+tints[tint],framed?"Framed":"Bare","Texture: "+layouts[layout]};int[] ys={54,78,102,102};for(int i=0;i<body.size();i++){GlassDialog.Control c=body.get(i);require(c.id==3+i&&c.x==panel.controlsX&&c.y==panel.top+ys[i]&&c.width==154&&c.height==20&&c.label.equals(labels[i])&&c.visible==(i!=2)&&!c.tab&&c.enabled,"glass body row and hidden5");}}
                GlassDialog.Control done=GlassDialog.done(panel,"Done");require(done.id==2&&done.x==panel.controlsX&&done.y==panel.top+panel.height-26&&done.width==154&&done.height==20&&done.visible,"current Done rectangle");
                require(state.action(4,-1).tint==(tint+3)%4&&state.action(4,1).tint==(tint+1)%4,"four tint reverse cycles");GlassDialog.State next=state.action(6,-1);require(next.layout==(layout+2)%3&&next.stretch==(next.layout!=1),"layout reverse cycle updates legacy stretch");require(state.action(3,-1).join!=join&&state.action(5,-1).framed!=framed,"boolean previous matches next");
                GlassDialog.State shade=state.choose(base+18,base),selected=shade.choose(91,base);require(shade.shade==18&&shade.material==-1&&selected.shade==18&&selected.material==91&&selected.page==page&&selected.tint==tint,"shade/material choice retains dormant fields and page");require(state.withFramed(!framed).framed!=framed&&state.withFramed(!framed).page==page,"live frame callback input");require(state.action(999,1)==state&&state.action(2,1)==state,"host-owned unknown/Done dispatch");cases++;
            }
            require(GlassDialog.listY(panel)==panel.top+54&&GlassDialog.listWidth(panel)==panel.controlsX-panel.left-31&&GlassDialog.listRows(panel)==Math.max(2,(panel.height-64)/22),"existing searchable picker geometry");
        }
        require(Arrays.equals(GlassDialog.rightClickIds(),new int[]{0,3,4,5,6}),"legacy reverse dispatch IDs including hidden compatibility choices");
        System.out.println("GlassDialog PASS cases="+cases+" four tabs/tints, conditional rows, hidden5, reverse cycles and retained immediate editor state");
    }
}
