package com.vandorlabs.ui;

import com.vandorlabs.persistence.*;
import java.util.*;

public final class PortholeDialogTest {
    private static void require(boolean test){if(!test)throw new AssertionError("Current flat porthole profile");}
    public static void main(String[] args){
        int cases=0;for(int width:new int[]{320,460,800})for(int height:new int[]{240,332,600}){
            ProgrammableDialogLayout panel=new ProgrammableDialogLayout(width,height);WallSettings walls=new WallSettings(false,false,0,0,true,3);
            for(int page:new int[]{195,213,211,212})for(int material:new int[]{-1,7})for(int layout=0;layout<3;layout++)for(int tint=0;tint<4;tint++){
                PanelGlassSettings glass=new PanelGlassSettings(2,material,layout,layout!=1,tint,true,true);List<DialogControl> tabs=PortholeDialog.tabs(panel,page);int tabWidth=(panel.width-22)/4;for(int i=0;i<4;i++){DialogControl tab=tabs.get(i);require(tab.x==panel.left+12+i*tabWidth&&tab.y==panel.top+30&&tab.width==tabWidth-2&&tab.height==18&&tab.enabled==(tab.id!=page));}
                List<DialogControl> rows=PortholeDialog.body(panel,page,2,walls,glass);if(PortholeDialog.framePage(page))require(rows.isEmpty());else{int index=0,y=54;if(page==195){require(rows.get(index).id==103&&rows.get(index++).label.equals("Texture: Fit (aspect)"));y+=24;}require(rows.get(index).id==102&&rows.get(index).y==panel.top+y&&rows.get(index++).label.equals("Join: On"));y+=24;require(rows.get(index).id==104&&rows.get(index).y==panel.top+y&&rows.get(index++).label.equals("Shape: Round"));y+=24;if(page==213){require(rows.get(index).id==113&&rows.get(index++).y==panel.top+y);y+=24;if(material>=0)require(rows.get(index).id==115&&rows.get(index).y==panel.top+y);require(PortholeDialog.playbackY(glass)==(material>=0?150:126));}}
                require(PortholeDialog.action(glass,113,-1).tint==(tint+3)%4);require(PortholeDialog.action(glass,115,-1).textureLayout()==(layout+2)%3);for(int shade=0;shade<com.vandorlabs.tiles.GlassTextures.IDS.length;shade++){PanelGlassSettings chosen=PortholeDialog.choose(glass,PortholeDialog.shadeChoice(shade));require(chosen.shade==shade&&chosen.material==-1&&chosen.layout==layout&&chosen.tint==tint);}cases++;
            }
        }
        for(boolean half:new boolean[]{false,true})for(boolean full:new boolean[]{false,true})for(int facing=2;facing<6;facing++)for(boolean inverted:new boolean[]{false,true})for(int page:new int[]{195,213})for(int material:new int[]{-1,7}){
            ProgrammableDialogLayout p=new ProgrammableDialogLayout(800,600);WallSettings wall=new WallSettings(full,half,3,7,true,2);PanelGlassSettings glass=new PanelGlassSettings(2,material,2,true,1,true,true);List<DialogControl> rows=PortholeDialog.body(p,page,1,wall,glass,true,facing,inverted);int[] ids=page==195?new int[]{103,102,104,105,111}:material>=0?new int[]{102,104,105,111,113,115}:new int[]{102,104,105,111,113};require(rows.size()==ids.length);for(int i=0;i<ids.length;i++){require(rows.get(i).id==ids[i]&&rows.get(i).y==p.top+54+24*i);require(rows.get(i).id!=112&&rows.get(i).id!=109&&rows.get(i).id!=110);}require(PortholeDialog.playbackY(glass,true)==(material>=0?198:174));DiagonalWallDialog.Geometry next=new DiagonalWallDialog.Geometry(wall,facing,inverted).action(105,-1);require(next.walls.fill==3&&next.walls.join==7&&next.walls.joinPortholes&&next.walls.portholeShape==2);cases++;
        }
        require(PortholeDialog.choiceShade(-1)==-1&&PortholeDialog.choiceShade(Integer.MIN_VALUE)==-1);System.out.println("Porthole profile PASS cases="+cases+" current tabs/body reverse cycles dormant palette/layout");
    }
}
