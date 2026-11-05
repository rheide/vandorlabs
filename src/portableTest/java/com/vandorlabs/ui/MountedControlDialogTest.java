package com.vandorlabs.ui;

import java.util.List;

public final class MountedControlDialogTest {
    public static void main(String[] ignored){int cases=0;String[] heights={"Base height: Standard","Base height: +2 px","Base height: +4 px","Base height: +6 px"},tilts={"Tilt: Flat","Tilt: 15 deg","Tilt: 30 deg","Tilt: 45 deg"},directions={"Forward","Right","Backward","Left"};
        for(int width:new int[]{240,280,640,854,1280})for(int screenHeight:new int[]{200,232,480,720})for(int height=0;height<4;height++)for(int tilt=0;tilt<4;tilt++)for(int direction=0;direction<4;direction++){
            int left=(width-280)/2,top=(screenHeight-232)/2;List<DialogControl> controls=MountedControlDialog.controls(left,top,height,tilt,direction);
            if(controls.size()!=3)throw new AssertionError("three mount controls");int[] ids={9,10,11},xs={left+14,left+14,left+144},ys={top+92,top+116,top+116},widths={252,122,122};String[] labels={heights[height],tilts[tilt],directions[direction]};
            for(int i=0;i<3;i++){DialogControl c=controls.get(i);if(c.id!=ids[i]||c.x!=xs[i]||c.y!=ys[i]||c.width!=widths[i]||c.height!=20||!c.enabled||c.tab||!c.label.equals(labels[i]))throw new AssertionError("frozen mount descriptor");}cases++;
        }
        for(boolean twin:new boolean[]{false,true})for(int size=0;size<2;size++)for(int left:new int[]{-20,0,100})for(int top:new int[]{-20,0,100}){
            String[] labels=twin?new String[]{"Size: Small","Size: Large"}:new String[]{"Size: Compact","Size: Industrial"};DialogControl c=MountedControlDialog.sizeControl(left,top,twin,size);
            if(c.id!=12||c.x!=left+14||c.y!=top+64||c.width!=252||c.height!=20||!c.enabled||c.tab||!c.label.equals(labels[size]))throw new AssertionError("frozen current Size12 row");
        }
        System.out.println("MountedControlDialog PASS cases="+cases+" exact IDs labels dimensions offsets centered small panels");
    }
}
