package com.vandorlabs.ui;
import java.util.List;
public final class SlabStairDialogTest {
    public static void main(String[] ignored){int cases=0;String[] names={"Main","Sides","Down","Up","Front","Back","Left","Right"};
        for(int x:new int[]{-20,0,100})for(int top:new int[]{-20,0,100})for(int span:new int[]{123,263,436})for(boolean faces:new boolean[]{false,true})for(int target=-2;target<6;target++){
            List<DialogControl> tabs=SlabStairDialog.tabs(x,top,span,faces,target);int count=faces?8:2,w=span/count;
            for(int i=0;i<count;i++){DialogControl c=tabs.get(i);int selected=i==0?-1:i==1?-2:i-2;if(c.id!=(i==0?120:i==1?127:119+i)||c.x!=x+i*w||c.y!=top+54||c.width!=(i==count-1?span-i*w-2:w-2)||c.height!=18||!c.label.equals(names[i])||!c.tab||c.enabled==(target==selected))throw new AssertionError("frozen tabs");}cases++;
        }
        int[] steps={1,2,4,8,16};for(int i=0;i<steps.length;i++)for(int roll=0;roll<3;roll++){
            if(SlabStairDialog.treadIndex(steps[i])!=Math.min(3,i)||!SlabStairDialog.treadLabel(steps[i]).equals("Step size: "+steps[i]+" px")||SlabStairDialog.toggleRoll(roll)!=(roll==0?1:0)||!SlabStairDialog.orientationLabel(roll).equals("Orientation: "+(roll==0?"Normal":"Sideways")))throw new AssertionError("saved/editor distinction");cases++;
        }
        for(int i=0;i<4;i++)if(SlabStairDialog.treadForIndex(i)!=steps[i])throw new AssertionError("slider detent");
        for(int side:new int[]{-1,0,3,Integer.MAX_VALUE}){DialogControl c=SlabStairDialog.resetSides(12,78,154,side);if(c.id!=128||c.x!=12||c.y!=78||c.width!=154||c.height!=20||c.tab||c.enabled!=(side>=0)||!c.label.equals("Use default side texture"))throw new AssertionError("reset descriptor");}
        System.out.println("SlabStairDialog PASS cases="+cases+" exact tabs reset saved/editor tread and roll");
    }
}
