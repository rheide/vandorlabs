package com.vandorlabs.ui;

import com.vandorlabs.persistence.*;
import java.util.*;

public final class DiagonalWallDialogTest {
    private static void require(boolean value){if(!value)throw new AssertionError("Current diagonal dialog");}
    public static void main(String[] args){
        int cases=0;ProgrammableDialogLayout panel=new ProgrammableDialogLayout(800,600);FrameSettings frames=new FrameSettings(-1,-1,-2,0,-1,-1,false,false,false,false);SlabStairDialog.State base=new SlabStairDialog.State(new ProgrammableBlockDialog.State(7,1,false,new int[]{-1,-1,-1,-1,-1,-1},-1),-1,8,0);
        for(boolean full:new boolean[]{false,true})for(boolean half:new boolean[]{false,true})for(int fill=0;fill<4;fill++)for(int join=0;join<12;join++)for(int facing=2;facing<6;facing++)for(boolean inverted:new boolean[]{false,true}){
            WallSettings walls=new WallSettings(full,half,fill,join,true,3);DiagonalWallDialog.State state=new DiagonalWallDialog.State(new PlainWallDialog.State(base,walls,frames,129),facing,inverted);List<DialogControl> rows=DiagonalWallDialog.controls(panel,state,"Done");int[] ids={195,197,103,105,111,112,109,110,100};require(rows.size()==ids.length);for(int i=0;i<ids.length;i++)require(rows.get(i).id==ids[i]);require(rows.get(3).y==panel.top+78&&rows.get(4).y==panel.top+102&&rows.get(5).y==panel.top+126&&rows.get(5).enabled==!half&&rows.get(6).y==panel.top+150&&rows.get(7).y==panel.top+174);
            String[] names={"","","North","South","West","East"};require(DiagonalWallDialog.slopeLabel(state).equals((half?"Slope rises: ":"Slope leans: ")+names[inverted?facing:facing^1]));
            for(int direction:new int[]{-1,1})for(int id:new int[]{105,109,110,111,112}){DiagonalWallDialog.State next=state.action(id,direction);int mode=half?2:full?1:0;if(id==105)mode=(mode+(direction<0?2:1))%3;require(next.wall.walls.fullWidth==(mode!=0)&&next.wall.walls.halfHeight==(mode==2));require(next.wall.walls.fill==(id==109?fill^1:id==110?fill^2:fill));require(next.wall.walls.join==(id==112?(join+(direction<0?11:1))%12:join));require(next.facing==(id==111&&half?facing^1:facing)&&next.inverted==(id==111&&!half?!inverted:inverted));require(next.wall.frames==frames&&next.wall.walls.joinPortholes&&next.wall.walls.portholeShape==3);cases++;}
            DiagonalWallDialog.State exterior=state.action(197,1);require(exterior.wall.frames==frames&&exterior.wall.frameDraft.exterior==-1&&DiagonalWallDialog.controls(panel,exterior,"Done").size()==3);DiagonalWallDialog.State normalized=exterior.action(105,-1);require(normalized.wall.frameDraft==exterior.wall.frameDraft&&normalized.wall.frames==frames);DiagonalWallDialog.State back=normalized.action(195,1).choose(8);require(back.wall.frames==frames&&back.wall.frameDraft==exterior.wall.frameDraft);
        }
        System.out.println("Diagonal wall dialog PASS cases="+cases+" exact rows/reverse geometry normalization and private frame draft");
    }
}
