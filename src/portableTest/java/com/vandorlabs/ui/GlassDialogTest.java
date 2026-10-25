package com.vandorlabs.ui;

import com.vandorlabs.persistence.ProgrammableGlassSettings;
import com.vandorlabs.tiles.GlassTextures;

public final class GlassDialogTest {
    private static void need(boolean value){if(!value)throw new AssertionError("Glass dialog");}
    public static void main(String[] args){
        GlassDialog.State fresh=GlassDialog.State.edit(ProgrammableGlassSettings.fresh());need(fresh.size==0&&fresh.shade==0&&fresh.layout==1&&fresh.material==-1&&fresh.selected()==GlassDialog.GLASS_BASE);
        int cases=0;
        for(int[] viewport:new int[][]{{640,360},{427,240},{320,240}})for(int material:new int[]{-1,0,150})for(int tint=0;tint<3;tint++)for(int layout=0;layout<3;layout++)for(boolean framed:new boolean[]{false,true})for(boolean join:new boolean[]{false,true}){
            ProgrammableDialogLayout panel=new ProgrammableDialogLayout(viewport[0],viewport[1]);GlassDialog.State state=new GlassDialog.State(0,18,join,tint,framed,material,layout);java.util.List<ProgrammableDialogLayout.Button> controls=GlassDialog.controls(panel,state);need(controls.size()==(material<0?4:5));int[] ids=material<0?new int[]{3,4,5,2}:new int[]{3,4,5,6,2};
            for(int i=0;i<ids.length;i++){ProgrammableDialogLayout.Button button=controls.get(i);need(button.id==ids[i]&&button.x==panel.controlsX&&button.width==154&&button.height==20);need(button.y==(button.id==2?panel.doneY:panel.top+(button.id==3?54:button.id==4?78:button.id==5?102:126)));need(button.x>=panel.left&&button.x+button.width<=panel.left+panel.width&&button.y+button.height<=panel.top+panel.height);}
            need(GlassDialog.rows(panel)==Math.max(2,(panel.height-64)/22)&&GlassDialog.listY(panel)==panel.top+30);need(state.action(4,-1).tint==(tint+2)%3&&state.action(4,1).tint==(tint+1)%3&&state.action(6,-1).layout==(layout+2)%3&&state.action(6,1).layout==(layout+1)%3);need(state.action(3,-1).join!=join&&state.action(5,-1).framed!=framed);
            for(int shade=0;shade<GlassTextures.IDS.length;shade++){GlassDialog.State chosen=state.choose(GlassDialog.GLASS_BASE+shade);need(chosen.shade==shade&&chosen.material==-1&&chosen.layout==layout&&chosen.tint==tint&&chosen.framed==framed&&chosen.join==join);need(chosen.choose(150).shade==shade&&chosen.choose(150).material==150&&chosen.choose(150).layout==layout);}
            cases++;
        }
        System.out.println("Glass dialog PASS "+cases+" viewport/material/tint/layout/frame/join cases with stock/custom choices, dormant layout, exact control bounds and previous-value cycling");
    }
}
