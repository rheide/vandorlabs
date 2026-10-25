package com.vandorlabs.render;
import com.vandorlabs.ui.MountedControlDialog;
public final class ControlMountTest {
    private static int count;
    private static void check(boolean value){count++;if(!value)throw new AssertionError("Control mount contract "+count);}
    private static void near(double expected,double actual){check(Math.abs(expected-actual)<1e-12);}
    public static void main(String[] args){
        for(int face=0;face<6;face++)for(int rotation=0;rotation<4;rotation++)for(int h=0;h<4;h++)for(int t=0;t<4;t++)for(int d=0;d<4;d++){
            ControlMount.Point n=ControlMount.normal(face);ControlMount.Box support=new ControlMount.Box(.3,.3,.3,.7,.7,.7);ControlMount m=new ControlMount(face,rotation,h,t,d,support);
            for(ControlMount.Point p:ControlMount.corners(support)){ControlMount.Point q=m.transform(p);check(Double.isFinite(q.x)&&Double.isFinite(q.y)&&Double.isFinite(q.z));near(0,m.depth(m.project(q)));if(t==0){near(p.x+n.x*h/8D,q.x);near(p.y+n.y*h/8D,q.y);near(p.z+n.z*h/8D,q.z);}}
            check(!m.wedgeBoxes(support).isEmpty());
        }
        ControlPose pose=new ControlPose(java.util.Collections.singletonList(new ControlPose.Element(new ControlMount.Box(.25,.25,.9375,.75,.75,1),null,0,0)),2,0,0);check(pose.components.size()==1);check(pose.supports.size()==1);near(1,pose.support.minZ);near(1,pose.support.maxZ);
        near(.75,ControlItemProjection.scale(-.5,-.5,.5,.5));near(.85F,ControlItemProjection.scale(-.1,-.1,.1,.1));
        for(int h=0;h<4;h++)for(int t=0;t<4;t++)for(int d=0;d<4;d++){MountedControlDialog dialog=new MountedControlDialog(640,360);MountedControlDialog.State s=new MountedControlDialog.State(h,t,d);int[] ids={9,10,11,1};int i=0;for(com.vandorlabs.ui.ProgrammableDialogLayout.Button b:dialog.controls(s)){check(b.id==ids[i++]);check(b.x>=dialog.left&&b.y>=dialog.top&&b.x+b.width<=dialog.left+240&&b.y+b.height<=dialog.top+168);}check(s.action(9,1).height==(h+1)%4);check(s.action(10,-1).tilt==(t+3)%4);check(s.action(11,-1).direction==(d+3)%4);}
        check(ControlMount.migratedTilt(3,false)==1);check(ControlMount.migratedTilt(3,true)==3);check(!ControlMount.valid(4,0,0));
        System.out.println("ControlMountTest PASS "+count+" assertions");
    }
}
