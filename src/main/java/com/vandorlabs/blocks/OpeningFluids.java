package com.vandorlabs.blocks;

import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.fluids.*;

/** Saved fluid sides are independent of the reversible, temporary opening fill. */
public final class OpeningFluids {
    private OpeningFluids(){}
    public static boolean supports(IBlockState state){return state.getBlock() instanceof BlockConfigurableSpaceDoor||state.getBlock() instanceof BlockProgrammableTrapdoor;}
    public static TileEntity root(TileEntity tile){
        if(!(tile instanceof TileEntitySpaceDoor)||tile.getWorld()==null)return tile;
        World world=tile.getWorld();BlockPos pos=tile.getPos();IBlockState state=world.getBlockState(pos);
        if(tile instanceof TileEntityLargeProgrammableDoor){TileEntityLargeProgrammableDoor large=(TileEntityLargeProgrammableDoor)tile;BlockPos anchor=large.anchorPos();return world.isBlockLoaded(anchor)?world.getTileEntity(anchor):tile;}
        if(tile instanceof TileEntitySpaceDoor&&state.getBlock() instanceof BlockConfigurableSpaceDoor&&state.getValue(BlockVandorDoor.HALF)==net.minecraft.block.BlockDoor.EnumDoorHalf.UPPER){BlockPos lower=pos.down();return world.isBlockLoaded(lower)?world.getTileEntity(lower):tile;}
        return tile;
    }
    public static boolean open(World world,BlockPos pos){TileEntity root=root(world.getTileEntity(pos));if(root instanceof TileEntitySpaceDoor)pos=root.getPos();IBlockState state=world.getBlockState(pos);return supports(state)&&state.getValue(state.getBlock() instanceof BlockProgrammableTrapdoor?BlockProgrammableTrapdoor.OPEN:BlockVandorDoor.OPEN);}
    /** Unit normal and signed near/far planes of the CLOSED leaf, stable through rotation. */
    public static double[] plane(World world,BlockPos pos){
        IBlockState state=world.getBlockState(pos);TileEntity tile=root(world.getTileEntity(pos));if(tile instanceof TileEntitySpaceDoor)state=world.getBlockState(tile.getPos());
        if(tile instanceof TileEntityProgrammableTrapdoor){
            double[][] points=tile instanceof TileEntityProgrammableDiagonalTrapdoor?BlockProgrammableDiagonalTrapdoor.corners(state,(TileEntityProgrammableDiagonalTrapdoor)tile,0):((TileEntityProgrammableTrapdoor)tile).corners(state,0);Vec3d origin=v(points[0]);Vec3d[] edges={v(points[1]).subtract(origin),v(points[2]).subtract(origin),v(points[4]).subtract(origin)};int thin=0;for(int i=1;i<3;i++)if(edges[i].lengthSquared()<edges[thin].lengthSquared())thin=i;
            Vec3d normal=edges[(thin+1)%3].crossProduct(edges[(thin+2)%3]).normalize();if(normal.dotProduct(edges[thin])<0)normal=normal.scale(-1);double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;for(double[] point:points){double d=normal.dotProduct(v(point));lo=Math.min(lo,d);hi=Math.max(hi,d);}return new double[]{normal.x,normal.y,normal.z,lo,hi};
        }
        EnumFacing facing=state.getValue(BlockVandorDoor.FACING);Vec3d normal=new Vec3d(facing.getDirectionVec());TileEntitySpaceDoor door=tile instanceof TileEntitySpaceDoor?(TileEntitySpaceDoor)tile:null;
        double center=normal.dotProduct(new Vec3d(.5,.5,.5))+(door==null||door.isSliding()?0:5.24/16D)+(door==null?0:door.positionOffset());
        return new double[]{normal.x,0,normal.z,center-1D/16,center+1D/16};
    }
    private static Vec3d v(double[] p){return new Vec3d(p[0],p[1],p[2]);}
    public static int side(World world,BlockPos pos,Vec3d point){double[] p=plane(world,pos);return point.x*p[0]+point.y*p[1]+point.z*p[2]<(p[3]+p[4])/2?0:1;}
    public static boolean adjacentWater(World world,BlockPos pos){
        for(EnumFacing face:EnumFacing.values()){BlockPos at=pos.offset(face);if(world.isBlockLoaded(at)&&world.getBlockState(at).getMaterial()==net.minecraft.block.material.Material.WATER)return true;}return false;
    }
    public static FluidStack effective(World world,BlockPos pos,int side){
        ContainedFluids stored=WaterloggedParts.data(world.getTileEntity(pos));FluidStack own=stored==null?null:stored.get(side);if(own!=null)return own;
        boolean open=open(world,pos);FluidStack opposite=stored==null?null:stored.get(1-side);
        if(open&&opposite!=null&&opposite.getFluid()==FluidRegistry.WATER)return opposite;
        // Most apertures are dry. Avoid generating leaf corners just to discover that.
        if(!adjacentWater(world,pos))return null;
        FluidStack external=ambient(world,pos,side);if(external!=null)return external;
        if(!open)return null;
        opposite=ambient(world,pos,1-side);return opposite!=null&&opposite.getFluid()==FluidRegistry.WATER?opposite:null;
    }
    private static FluidStack ambient(World world,BlockPos pos,int side){
        double[] plane=plane(world,pos);double sign=side==0?-1:1;
        // A diagonal side may meet two cell faces. Only loaded, directly adjacent water counts.
        for(EnumFacing face:EnumFacing.values())if(sign*(plane[0]*face.getFrontOffsetX()+plane[1]*face.getFrontOffsetY()+plane[2]*face.getFrontOffsetZ())>.1){BlockPos at=pos.offset(face);if(world.isBlockLoaded(at)&&world.getBlockState(at).getMaterial()==net.minecraft.block.material.Material.WATER)return new FluidStack(FluidRegistry.WATER,1000);}
        return null;
    }
}
