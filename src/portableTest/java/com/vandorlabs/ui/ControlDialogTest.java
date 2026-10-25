package com.vandorlabs.ui;

public final class ControlDialogTest {
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("Control dialog "+checks);checks++;}
    public static void main(String[] args){
        for(ControlDialog.Profile p:ControlDialog.Profile.values())for(int[] viewport:new int[][]{{640,360},{427,240},{320,240}}){
            ControlDialog layout=new ControlDialog(p,viewport[0],viewport[1]);int expected=p==ControlDialog.Profile.BINARY?168:p.power?198:p.selectable?248:224;
            check(layout.height==expected);check(layout.left==(viewport[0]-240)/2);check(layout.top==(viewport[1]-expected)/2);
            ControlDialog.State state=new ControlDialog.State(5,15,3,2,1,2,1);int[] ids=p.signal?p.selectable?new int[]{7,8,13,9,10,11,1}:new int[]{7,8,9,10,11,1}:p.power?new int[]{9,10,11,12,1}:new int[]{9,10,11,1};int index=0;
            for(ProgrammableDialogLayout.Button button:layout.controls(state)){check(button.id==ids[index++]);check(button.x>=layout.left);check(button.x+button.width<=layout.left+240);check(button.y>=layout.top);check(button.y+button.height<=layout.top+expected);if(button.id==9)check(button.y==layout.top+p.mountY());if(button.id==1){check(button.width==200);check(button.y==layout.top+expected-28);}}
            check(index==ids.length);check(layout.channels().x==layout.left+116);check(layout.channels().y==layout.top+38);
        }
        for(int low=1;low<=13;low++)for(int high=low+2;high<=15;high++)for(int delta:new int[]{-1,1}){
            ControlDialog.State s=new ControlDialog.State(low,high,0,0,0,0,0);ControlDialog.State l=s.action(7,delta),h=s.action(8,delta);check(l.low>=1&&l.low<=high-2);check(h.high>=low+2&&h.high<=15);check(l.action(7,-delta).low==low);check(h.action(8,-delta).high==high);
        }
        for(int v=0;v<4;v++){ControlDialog.State s=new ControlDialog.State(5,15,v,v,v,v%3,v%2);check(s.action(9,-1).height==(v+3)%4);check(s.action(10,1).tilt==(v+1)%4);check(s.action(11,-1).direction==(v+3)%4);check(s.action(13,-1).type==(v%3+2)%3);check(s.action(12,-1).size==1-v%2);}
        check(ControlDialog.typeLabel(2).equals("Type: Grip"));check(ControlDialog.sizeLabel(true,1).equals("Size: Large"));check(ControlDialog.sizeLabel(false,0).equals("Size: Compact"));check(ControlDialog.detentLabel(5,15).equals("Off 0 / Medium 10"));
        System.out.println("ControlDialogTest PASS "+checks+" assertions");
    }
}
