package com.vandorlabs.neoforge;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Current four-detent authored pose selection; no resource parser, renderer or world access. */
public final class NativeSignalControlPoses {
    public interface Profile {String controlKind();}
    public static final IntegerProperty LEVEL=IntegerProperty.create("level",0,3);
    private static final String[] STEPS={"off","low","medium","high"};
    private NativeSignalControlPoses(){}
    private static String kind(BlockState state){return ((Profile)state.getBlock()).controlKind();}
    public static Direction face(BlockState state){return state.getValue(DirectionalBlock.FACING);}
    public static int rotation(BlockState state,int rotation){return face(state).getAxis()==Direction.Axis.Y?rotation&3:0;}
    /** Geometry identity, rather than a blockstate-variant resource key: ON does not change authored detents. */
    public static String key(BlockState state,int rotation){return kind(state)+"/"+face(state).getSerializedName()+"/"+state.getValue(LEVEL)+"/"+rotation(state,rotation);}
    public static String modelPath(BlockState state,int rotation){return modelPath(kind(state),face(state),state.getValue(LEVEL),rotation(state,rotation));}
    static String modelPath(String kind,Direction face,int step,int rotation){
        boolean slider=kind.equals("wall_slider");String mount=face.getAxis()==Direction.Axis.Y?(face==Direction.UP?"floor":"ceiling")+(slider||rotation%2==0?"_z":"_x"):face.getSerializedName();
        return "/assets/vandorlabs/models/block/"+(kind.equals("thruster_lever")||slider?"thruster_controls":"aircraft_throttles")+"/32px/"+kind+"_"+STEPS[step]+"_"+mount+".json";
    }
    public static AABB support(BlockState state,int rotation){return support(kind(state),face(state),rotation(state,rotation));}
    static AABB support(String kind,Direction face,int rotation){
        double a=kind.equals("thruster_lever")||kind.equals("wall_slider")?3/16D:kind.equals("fighter_throttle")?1.5/16D:1/16D,b=kind.equals("thruster_lever")?2/16D:1/16D;
        // The source artwork is already reduced. Only the logical slider support plate needs this factor.
        if(kind.equals("wall_slider")){a=.5-(.5-a)*.92;b=.5-(.5-b)*.92;}
        if(face.getAxis()==Direction.Axis.Y){if(rotation%2==1){double swap=a;a=b;b=swap;}double y=face==Direction.UP?0:1;return new AABB(a,y,b,1-a,y,1-b);}
        if(face.getAxis()==Direction.Axis.Z){double z=face==Direction.SOUTH?0:1;return new AABB(a,b,z,1-a,1-b,z);}
        double x=face==Direction.EAST?0:1;return new AABB(x,b,a,x,1-b,1-a);
    }
    /** Apply after each authored element's own rotation, before the shared mount transform. */
    public static Vec3 orientPoint(Vec3 point,BlockState state,int rotation){return orientPoint(point,kind(state),face(state),rotation(state,rotation));}
    static Vec3 orientPoint(Vec3 point,String kind,Direction face,int rotation){
        if(face.getAxis()!=Direction.Axis.Y)return point(point.x,point.y,point.z);
        if(kind.equals("wall_slider")){
            Vec3 p=point(point.x-.5,point.y-.5,point.z-.5);float angle=(float)(-((rotation+(face==Direction.UP?2:0))&3)*Math.PI/2),cos=Mth.cos(angle),sin=Mth.sin(angle);
            p=point(p.x*cos+p.z*sin,p.y,p.z*cos-p.x*sin);return point(p.x+.5,p.y+.5,p.z+.5);
        }
        return rotation>=2?point(1-point.x,point.y,1-point.z):point(point.x,point.y,point.z);
    }
    private static Vec3 point(double x,double y,double z){return new Vec3(x==0?0:x,y==0?0:y,z==0?0:z);}
}
