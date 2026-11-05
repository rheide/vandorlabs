package com.vandorlabs.neoforge;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.*;

/** Independent frozen legacy support and Vec3d.rotateYaw operations, without a world or model parser. */
public final class SignalControlPosesChecks {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static double zero(double value){return value==0?0:value;}
    private static Vec3 point(double x,double y,double z){return new Vec3(zero(x),zero(y),zero(z));}
    private static Vec3 oldPoint(Vec3 original,String kind,Direction face,int rotation){
        if(kind.equals("wall_slider")&&face.getAxis()==Direction.Axis.Y){
            Vec3 p=point(original.x-.5,original.y-.5,original.z-.5);float angle=(float)(-((rotation+(face==Direction.UP?2:0))&3)*Math.PI/2);int index=(int)(angle*10430.378F);float sin=(float)Math.sin((index&65535)*Math.PI*2/65536),cos=(float)Math.sin(((int)(angle*10430.378F+16384.0F)&65535)*Math.PI*2/65536);
            p=point(p.x*cos+p.z*sin,p.y,p.z*cos-p.x*sin);return point(p.x+.5,p.y+.5,p.z+.5);
        }
        return face.getAxis()==Direction.Axis.Y&&rotation>=2?point(1-original.x,original.y,1-original.z):point(original.x,original.y,original.z);
    }
    private static AABB oldSupport(String kind,Direction face,int rotation){
        double a=kind.equals("thruster_lever")||kind.equals("wall_slider")?3/16D:kind.equals("fighter_throttle")?1.5/16D:1/16D,b=kind.equals("thruster_lever")?2/16D:1/16D;
        if(kind.equals("wall_slider")){a=.5-(.5-a)*.92;b=.5-(.5-b)*.92;}
        if(face.getAxis()==Direction.Axis.Y){if(rotation%2==1){double swap=a;a=b;b=swap;}double y=face==Direction.UP?0:1;return new AABB(a,y,b,1-a,y,1-b);}
        if(face.getAxis()==Direction.Axis.Z){double z=face==Direction.SOUTH?0:1;return new AABB(a,b,z,1-a,1-b,z);}double x=face==Direction.EAST?0:1;return new AABB(x,b,a,x,1-b,1-a);
    }
    private static void same(Vec3 a,Vec3 b){require(Double.doubleToRawLongBits(a.x)==Double.doubleToRawLongBits(b.x)&&Double.doubleToRawLongBits(a.y)==Double.doubleToRawLongBits(b.y)&&Double.doubleToRawLongBits(a.z)==Double.doubleToRawLongBits(b.z),"legacy float rotation/zero bits changed: "+a+" / "+b);}
    public static void main(String[] ignored){int cases=0;String[] steps={"off","low","medium","high"};
        for(String kind:new String[]{"thruster_lever","airliner_throttle","fighter_throttle","wall_slider"})for(Direction face:Direction.values())for(int rotation=0;rotation<4;rotation++){
            require(NativeSignalControlPoses.support(kind,face,rotation).equals(oldSupport(kind,face,rotation)),"logical support plate changed");
            for(int step=0;step<4;step++){boolean slider=kind.equals("wall_slider");String mount=face.getAxis()==Direction.Axis.Y?(face==Direction.UP?"floor":"ceiling")+(slider||rotation%2==0?"_z":"_x"):face.getSerializedName();String expected="/assets/vandorlabs/models/block/"+(slider||kind.equals("thruster_lever")?"thruster_controls":"aircraft_throttles")+"/32px/"+kind+"_"+steps[step]+"_"+mount+".json";require(NativeSignalControlPoses.modelPath(kind,face,step,rotation).equals(expected),"authored path changed");cases++;}
            for(double x:new double[]{-0.0,.0625,.2125,.5,.9375,1})for(double y:new double[]{0,.3,1})for(double z:new double[]{0,.0625,.9425,1}){Vec3 p=point(x,y,z);same(NativeSignalControlPoses.orientPoint(p,kind,face,rotation),oldPoint(p,kind,face,rotation));cases++;}
        }
        System.out.println("Signal control pose profile PASS cases="+cases+" paths logical support plates float yaw signed zeros and all six faces; no renderer/backend changes");
    }
}
