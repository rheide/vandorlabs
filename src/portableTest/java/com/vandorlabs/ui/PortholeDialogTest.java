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
                require(PortholeDialog.action(glass,113,-1).tint==(tint+3)%4);require(PortholeDialog.action(glass,115,-1).textureLayout()==(layout+2)%3);for(int shade=0;shade<19;shade++){PanelGlassSettings chosen=PortholeDialog.choose(glass,PortholeDialog.shadeChoice(shade));require(chosen.shade==shade&&chosen.material==-1&&chosen.layout==layout&&chosen.tint==tint);}cases++;
            }
        }
        require(PortholeDialog.choiceShade(-1)==-1&&PortholeDialog.choiceShade(Integer.MIN_VALUE)==-1);System.out.println("Porthole profile PASS cases="+cases+" current tabs/body reverse cycles dormant palette/layout");
    }
}
