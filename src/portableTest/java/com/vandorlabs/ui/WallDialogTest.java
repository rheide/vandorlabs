package com.vandorlabs.ui;

import com.vandorlabs.persistence.WallAppearance;
import com.vandorlabs.tiles.GlassTextures;
import java.util.List;

public final class WallDialogTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Wall dialog "+checks);}
    public static void main(String[] args){
        for(WallDialog.Profile profile:WallDialog.Profile.values())for(int[] viewport:new int[][]{{640,360},{427,240},{320,240}})for(int target:new int[]{-1,-2,-3})for(int material:new int[]{-1,9})for(int side:new int[]{-3,-1,7}){
            if(!profile.porthole()&&target!=-1)continue;ProgrammableDialogLayout panel=new ProgrammableDialogLayout(viewport[0],viewport[1]);WallAppearance appearance=new WallAppearance(2,1,material,true,false,true,true,0,-1);WallDialog.State state=new WallDialog.State(4,side,1,target,appearance);List<WallDialog.Control> controls=WallDialog.controls(panel,profile,state,"Done");int i=0,y=30;
            if(profile.porthole()){for(int id:new int[]{120,127,129}){WallDialog.Control c=controls.get(i++);check(c.bounds.id==id);check(c.bounds.y==panel.top+30);check(c.enabled!=(id==120?target==-1:id==127?target==-2:target==-3));}}
            if(target==-2){WallDialog.Control c=controls.get(i++);check(c.bounds.id==128);check(c.bounds.y==panel.top+y);check(c.enabled==(side>=0));y+=24;}
            if(target!=-3){WallDialog.Control c=controls.get(i++);check(c.bounds.id==103&&c.bounds.y==panel.top+y);y+=24;}
            if(profile.porthole()){for(int id:new int[]{102,104}){WallDialog.Control c=controls.get(i++);check(c.bounds.id==id&&c.bounds.y==panel.top+y);y+=24;}}
            if(target==-3){WallDialog.Control c=controls.get(i++);check(c.bounds.id==113&&c.bounds.y==panel.top+y);check(c.bounds.label.equals("Tint: Normal"));y+=24;if(material>=0){c=controls.get(i++);check(c.bounds.id==115&&c.bounds.y==panel.top+y);check(c.bounds.label.equals("Texture: Tile"));}}
            check(controls.get(i++).bounds.id==100&&i==controls.size());for(WallDialog.Control c:controls){check(c.bounds.x>=panel.left&&c.bounds.x+c.bounds.width<=panel.left+panel.width);check(c.bounds.y>=panel.top&&c.bounds.y+c.bounds.height<=panel.top+panel.height);}
            check(WallDialog.listY(panel,profile)==panel.top+(profile.porthole()?54:30));check(WallDialog.rows(panel,profile)==Math.max(2,(panel.height-(profile.porthole()?60:64))/22));check(state.selected()==(target==-3?(material>=0?material:WallDialog.GLASS_BASE+2):target==-2?(side>=0?side:26):4));
            check(state.action(103,-1).layout==0&&state.action(103,1).layout==2);check(state.action(102,-1).appearance.join==false);check(state.action(104,-1).appearance.shape==3);check(state.action(113,-1).appearance.tint==0);check(state.action(115,-1).appearance.textureLayout()==0);check(state.action(128,1).side==-1&&state.action(128,1).target==-2);
            WallDialog.State chosen=state.choose(12);check(target==-3?chosen.appearance.material==12:target==-2?chosen.side==12:chosen.main==12);
        }
        WallDialog.State glass=new WallDialog.State(4,7,1,-3,WallAppearance.fresh(true));check(glass.selected(18)==WallDialog.GLASS_BASE+18);check(glass.selected()==WallDialog.GLASS_BASE+2);for(int shade=0;shade<GlassTextures.IDS.length;shade++){WallDialog.State picked=glass.choose(WallDialog.GLASS_BASE+shade);check(picked.appearance.shade==shade&&picked.appearance.material==-1);check(picked.appearance.tint==1&&picked.appearance.framed);check(picked.selected()==WallDialog.GLASS_BASE+shade);}
        check(WallDialog.effect(120)==WallDialog.Effect.NONE&&WallDialog.effect(127)==WallDialog.Effect.NONE&&WallDialog.effect(129)==WallDialog.Effect.NONE);check(WallDialog.effect(103)==WallDialog.Effect.LAYOUT&&WallDialog.effect(128)==WallDialog.Effect.SIDE&&WallDialog.effect(113)==WallDialog.Effect.APPEARANCE&&WallDialog.effect(100)==WallDialog.Effect.CLOSE);
        check(!WallDialog.rebuild(102)&&!WallDialog.rebuild(103)&&!WallDialog.rebuild(104));check(WallDialog.rebuild(113)&&WallDialog.resetView(129)&&!WallDialog.resetView(127));
        System.out.println("WallDialogTest PASS "+checks+" profile/layout/stock/custom/previous/submission checks");
    }
}
