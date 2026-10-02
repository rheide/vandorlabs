package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockProgrammableTrapdoor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.util.*;

/** Offset leaves retain their owning block for interaction, without occupying the shaft cell. */
public final class OffsetTrapdoorInteractions {
    public static final OffsetTrapdoorInteractions INSTANCE=new OffsetTrapdoorInteractions();
    private OffsetTrapdoorInteractions() { }

    private static TileEntityProgrammableTrapdoor leaf(World world,BlockPos pos) {
        if(!world.isBlockLoaded(pos) || !(world.getBlockState(pos).getBlock() instanceof BlockProgrammableTrapdoor))return null;
        TileEntity tile=world.getTileEntity(pos);
        return tile instanceof TileEntityProgrammableTrapdoor && ((TileEntityProgrammableTrapdoor)tile).isCover()?(TileEntityProgrammableTrapdoor)tile:null;
    }
    public static AxisAlignedBB bounds(TileEntityProgrammableTrapdoor leaf) {
        return leaf.getWorld().getBlockState(leaf.getPos()).getBoundingBox(leaf.getWorld(),leaf.getPos()).offset(leaf.getPos());
    }
    public static RayTraceResult trace(World world,Vec3d start,Vec3d end) {
        Set<BlockPos> owners=new HashSet<>();
        int[] cell={MathHelper.floor(start.x),MathHelper.floor(start.y),MathHelper.floor(start.z)};
        int[] last={MathHelper.floor(end.x),MathHelper.floor(end.y),MathHelper.floor(end.z)};
        double[] origin={start.x,start.y,start.z},delta={end.x-start.x,end.y-start.y,end.z-start.z},next=new double[3],step=new double[3];
        for(int axis=0;axis<3;axis++) {
            next[axis]=delta[axis]==0?Double.POSITIVE_INFINITY:((delta[axis]>0?cell[axis]+1:cell[axis])-origin[axis])/delta[axis];
            step[axis]=delta[axis]==0?Double.POSITIVE_INFINITY:Math.abs(1/delta[axis]);
        }
        int limit=Math.abs(cell[0]-last[0])+Math.abs(cell[1]-last[1])+Math.abs(cell[2]-last[2])+3;
        for(int i=0;i<limit;i++) {
            for(int dy=-1;dy<=1;dy++)for(int[] offset:new int[][]{{0,0},{-1,0},{1,0},{0,-1},{0,1}})
                owners.add(new BlockPos(cell[0]+offset[0],cell[1]+dy,cell[2]+offset[1]));
            if(Arrays.equals(cell,last))break;
            int axis=next[0]<=next[1] && next[0]<=next[2]?0:next[1]<=next[2]?1:2;
            cell[axis]+=delta[axis]>0?1:-1;next[axis]+=step[axis];
        }
        RayTraceResult nearest=null;double distance=Double.POSITIVE_INFINITY;
        for(BlockPos pos:owners) {
            TileEntityProgrammableTrapdoor leaf=leaf(world,pos);if(leaf==null)continue;
            RayTraceResult hit=bounds(leaf).calculateIntercept(start,end);
            if(hit!=null && start.squareDistanceTo(hit.hitVec)<distance) {
                distance=start.squareDistanceTo(hit.hitVec);nearest=new RayTraceResult(hit.hitVec,hit.sideHit,pos);
            }
        }
        return nearest;
    }
    @SubscribeEvent public void collisions(GetCollisionBoxesEvent event) {
        addCollisions(event.getWorld(),event.getAabb(),event.getCollisionBoxesList());
    }
    public static void addCollisions(World world,AxisAlignedBB query,List<AxisAlignedBB> boxes) {
        for(BlockPos pos:BlockPos.getAllInBox(new BlockPos(query.minX-1,query.minY-1,query.minZ-1),new BlockPos(query.maxX+1,query.maxY+1,query.maxZ+1))) {
            TileEntityProgrammableTrapdoor leaf=leaf(world,pos);if(leaf==null)continue;
            AxisAlignedBB box=bounds(leaf);
            if(box.intersects(query) && !boxes.contains(box))boxes.add(box);
        }
    }
}
