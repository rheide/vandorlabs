package com.vandorlabs.neoforge.credentials;

import com.vandorlabs.neoforge.NativeMountedControlGeometry;
import com.vandorlabs.neoforge.NativeMountedSwitchBlock;
import com.vandorlabs.persistence.ControlMountSettings;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;

/** Released reader selection and collision, independent of tile/world access. */
public final class NativeReaderGeometry {
    private NativeReaderGeometry(){}
    public static boolean flat(ControlMountSettings mount){return mount.height==0&&mount.tilt==0;}
    public static VoxelShape outline(BlockState state,ControlMountSettings mount,int rotation,boolean chip){
        if(!flat(mount))return NativeMountedControlGeometry.outline(state,mount,rotation);
        Direction face=state.getValue(NativeMountedSwitchBlock.FACING);double half=3D/16,height=(chip?3:4.4)/16;
        Vec3 a=point(face,rotation,-half,-height,0),b=point(face,rotation,half,height,.10);
        return Shapes.create(new AABB(a,b));
    }
    public static VoxelShape collision(BlockState state,ControlMountSettings mount,int rotation){
        return flat(mount)?Shapes.empty():NativeMountedControlGeometry.shape(state,mount,rotation);
    }
    public static Vec3 point(Direction face,int rotation,double x,double y,double depth){
        Vec3 u,v;
        switch(face){
            case SOUTH -> {u=new Vec3(-1,0,0);v=new Vec3(0,1,0);}
            case EAST -> {u=new Vec3(0,0,1);v=new Vec3(0,1,0);}
            case WEST -> {u=new Vec3(0,0,-1);v=new Vec3(0,1,0);}
            case UP -> {u=new Vec3(1,0,0);v=new Vec3(0,0,1);}
            case DOWN -> {u=new Vec3(1,0,0);v=new Vec3(0,0,-1);}
            default -> {u=new Vec3(1,0,0);v=new Vec3(0,1,0);}
        }
        if(face.getAxis()==Direction.Axis.Y)for(int i=0;i<(rotation&3);i++){Vec3 previous=u;u=v;v=previous.scale(-1);}
        return new Vec3(.5,.5,.5).subtract(Vec3.atLowerCornerOf(face.getNormal()).scale(.5-depth)).add(u.scale(x)).add(v.scale(y));
    }
}
