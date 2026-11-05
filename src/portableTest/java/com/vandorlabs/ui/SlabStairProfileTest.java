package com.vandorlabs.ui;

import java.util.*;

/** Frozen current legacy shape layout, including compressed rows and dormant saved editor values. */
public final class SlabStairProfileTest {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] ignored){int cases=0;
        for(int width:new int[]{280,426,640,854})for(int height:new int[]{200,240,480,720})for(boolean faces:new boolean[]{false,true})for(boolean stair:new boolean[]{false,true})for(int target=-2;target<6;target++){
            ProgrammableDialogLayout p=new ProgrammableDialogLayout(width,height);ProgrammableBlockDialog.State housing=new ProgrammableBlockDialog.State(7,2,faces,new int[]{1,2,3,4,5,6},target);SlabStairDialog.State state=new SlabStairDialog.State(housing,9,16,2);List<DialogControl> rows=SlabStairDialog.controls(p,state,stair,"Done");
            int count=faces?8:2;require(rows.size()==1+count+2+(faces?1:0)+(target==-2?1:0)+(stair?2:0)+1,"exact visible row count");
            List<Integer> ids=new ArrayList<Integer>();ids.add(106);if(faces)ids.add(108);if(target==-2)ids.add(128);ids.add(103);if(stair){ids.add(130);ids.add(131);}int first=p.top+78,last=first+(ids.size()-1)*24,step=last+20>p.top+p.height-30?Math.min(24,(p.top+p.height-30-first)/ids.size()):24;
            for(int i=0;i<ids.size();i++){DialogControl c=rows.get(1+count+i);require(c.id==ids.get(i)&&c.x==p.controlsX&&c.y==first+i*step&&c.width==154&&c.height==(step==24?20:Math.min(20,step-2)),"frozen actual legacy control positions");if(c.id==130)require(c.label.equals("Step size: 16 px"),"saved16 retained slider label");if(c.id==131)require(c.label.equals("Orientation: Sideways"),"savedroll2 retained label");}
            require(SlabStairDialog.listY(p)==p.top+78&&SlabStairDialog.listRows(p)==Math.max(2,(p.height-108)/22),"shared picker search-row allocation");cases++;
        }
        SlabStairDialog.State s=new SlabStairDialog.State(new ProgrammableBlockDialog.State(7,1,true,new int[]{1,2,3,4,5,6},-1),9,16,2);s=s.action(127,1);require(s.selectedTexture()==9&&s.housing.target==-2,"side tab");s=s.choose(12);require(s.sideTexture==12&&s.housing.mainTexture==7&&s.housing.face(0)==1,"side edit preserves main and dormant perface data");s=s.action(128,1);require(s.sideTexture==-1&&s.selectedTexture()==7,"reset sides inherits main");s=s.action(131,-1);require(s.roll==0&&s.tread==16,"savedroll2 reverse toggle normal");s=s.chooseTread(3);require(s.tread==8,"editor16 endpoint chooses8");s=s.action(106,-1);require(!s.housing.facesEnabled&&s.housing.target==-1&&s.housing.face(0)==1,"toggle preserves dormant faces and resets target");s=s.action(103,-1);require(s.housing.layout==0,"layout reverse cycle");
        System.out.println("SlabStairProfile PASS cases="+cases+" frozen full rows compressed panels sides immediate state and dormant saved16 roll2");
    }
}
