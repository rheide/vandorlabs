package com.vandorlabs.ui;

/** Literal current Type/Low/High rows and bounded input rules, independently of a native screen. */
public final class SignalControlDialogTest {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] ignored){int cases=0;String[] labels={"Type: Standard","Type: Twin","Type: Grip"};
        for(int left:new int[]{-40,0,117})for(int top:new int[]{-16,0,83}){
            for(int type=0;type<3;type++){DialogControl c=MountedControlDialog.typeControl(left,top,type);require(c.id==13&&c.x==left+14&&c.y==top+64&&c.width==252&&c.height==20&&c.label.equals(labels[type])&&c.enabled&&!c.tab,"Type13 General row");cases++;}
            for(int low=1;low<=13;low++)for(int high=low+2;high<=15;high++){
                java.util.List<DialogControl> rows=MountedControlDialog.limitControls(left,top,low,high);require(rows.size()==2,"two limits");DialogControl lo=rows.get(0),hi=rows.get(1);require(lo.id==7&&hi.id==8&&lo.x==left+14&&hi.x==left+144&&lo.y==top+132&&hi.y==top+132&&lo.width==122&&hi.width==122&&lo.height==20&&hi.height==20&&lo.label.equals("Low: "+low)&&hi.label.equals("High: "+high)&&lo.enabled&&hi.enabled&&!lo.tab&&!hi.tab,"Low7 High8 Redstone rows");
                for(int direction:new int[]{-3,-1,0,1,3}){require(MountedControlDialog.cycleLow(low,high,direction)==1+Math.floorMod(low-1+direction,high-2),"bounded Low cycle");require(MountedControlDialog.cycleHigh(low,high,direction)==low+2+Math.floorMod(high-(low+2)+direction,15-(low+2)+1),"bounded High cycle");}
                require(MountedControlDialog.detentLabel(low,high).equals("Off 0 / Medium "+((low+high+1)/2)),"medium summary");cases++;
            }
        }
        require(MountedControlDialog.TYPE_COUNT==3&&MountedControlDialog.LOW_MINIMUM==1&&MountedControlDialog.HIGH_MAXIMUM==15&&MountedControlDialog.LIMIT_LABEL_Y==160&&MountedControlDialog.LIMIT_HINT_Y==174&&MountedControlDialog.LIMIT_HINT.equals("Click to cycle four levels"),"current labels/bounds/offsets");
        System.out.println("Signal control dialog PASS cases="+cases+" exact General Type and Redstone limit rows, labels, hints and bounded left/right cycles");
    }
}
