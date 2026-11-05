package com.vandorlabs.ui;

import com.vandorlabs.persistence.*;
import java.util.List;

public final class PlainWallDialogTest {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] ignored){
        FrameSettings saved=new FrameSettings(-1,7,-2,0,-1,3,true,false,false,true);WallSettings walls=new WallSettings(true,true,3,11,true,3);SlabStairDialog.State base=new SlabStairDialog.State(new ProgrammableBlockDialog.State(7,0,true,new int[]{1,2,3,4,5,6},-1),9,16,2);PlainWallDialog.State initial=new PlainWallDialog.State(base,walls,saved,129),exterior=initial.action(197,1);
        require(exterior.frames==saved&&exterior.frameDraft.exterior==7&&exterior.frameDraft.splitTop==129&&exterior.frameDraft.splitBottom==3,"opening pane normalizes only its private draft");require(saved.exterior==-2&&saved.splitTop==-1,"editor initialization leaves saved fields untouched");PlainWallDialog.State main=exterior.action(195,1).choose(10);require(main.frames==saved&&main.base.housing.mainTexture==10&&main.base.sideTexture==9&&main.base.tread==16&&main.base.roll==2&&main.base.housing.face(0)==1,"main edit cannot publish hidden frame draft or lose retained housing fields");
        exterior=main.action(197,1).choose(-1);require(exterior.frames.interior==-1&&exterior.frames.exterior==7&&exterior.frames.splitTop==129&&exterior.frames.splitBottom==3,"frame choice publishes normalized legacy pane draft including old explicit exterior");require(exterior.selectedTexture()==-1&&exterior.frames.tile&&exterior.frames.bare,"Original artwork is a material sentinel and other flags remain");main=exterior.action(195,1).action(103,-1);require(main.base.housing.layout==2&&main.frames==exterior.frames&&main.walls==walls,"reverse layout and independent wall/frame state");int cases=0;
        for(int width:new int[]{280,426,640,854})for(int height:new int[]{200,240,480,720})for(boolean page:new boolean[]{false,true}){
            ProgrammableDialogLayout p=new ProgrammableDialogLayout(width,height);PlainWallDialog.State state=page?initial.action(197,1):initial;List<DialogControl> controls=PlainWallDialog.controls(p,state,"Done");require(controls.size()==(page?3:4),"only current plain-wall controls");int tabWidth=(p.width-22)/2;
            for(int i=0;i<2;i++){DialogControl c=controls.get(i);require(c.id==(i==0?195:197)&&c.x==p.left+12+i*tabWidth&&c.y==p.top+30&&c.width==tabWidth-2&&c.height==18&&c.tab&&c.enabled==(i==0?page:!page),"current Appearance/Exterior tabs");}
            DialogControl done=controls.get(controls.size()-1);require(done.id==100&&done.x==p.controlsX&&done.y==p.doneY&&done.width==154&&done.height==20,"shared Done position");if(!page){DialogControl layout=controls.get(2);require(layout.id==103&&layout.y==p.top+54&&layout.x==p.controlsX,"appearance layout row without face toggles");}
            require(PlainWallDialog.listY(p)==p.top+54&&PlainWallDialog.listWidth(p,state)==(page?p.width-24:p.listWidth)&&PlainWallDialog.listRows(p,state)==Math.max(2,(p.height-(page?110:84))/22),"legacy full-width Exterior pane with allocated search row");cases++;
        }
        FrameSettings display=saved.editor(true,true,129);require(display.front==-1&&display.artworkVersion==1&&display.exterior==7&&display.splitTop==129&&display.splitBottom==3,"shared main-front/device editor initialization");
        System.out.println("PlainWallDialog PASS cases="+cases+" current geometry private frame draft independent immediate edits and sentinel");
    }
}
