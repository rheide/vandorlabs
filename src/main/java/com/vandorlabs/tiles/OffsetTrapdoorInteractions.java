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
    public static final com.vandorlabs.world.LoadedTileIndex<TileEntityProgrammableTrapdoor> INDEX=new com.vandorlabs.world.LoadedTileIndex<>(TileEntityProgrammableTrapdoor.class);
    public static final OffsetTrapdoorInteractions INSTANCE=new OffsetTrapdoorInteractions();
    private OffsetTrapdoorInteractions() { }

    private static TileEntityProgrammableTrapdoor leaf(World world,BlockPos pos) {
        if(!world.isBlockLoaded(pos) || !(world.getBlockState(pos).getBlock() instanceof BlockProgrammableTrapdoor))return null;
        TileEntity tile=world.getTileEntity(pos);
        return tile instanceof TileEntityProgrammableTrapdoor && (((TileEntityProgrammableTrapdoor)tile).isSliding() || ((TileEntityProgrammableTrapdoor)tile).isCover() || ((TileEntityProgrammableTrapdoor)tile).isSlideOverSurface() || tile instanceof TileEntityProgrammableDiagonalTrapdoor)?(TileEntityProgrammableTrapdoor)tile:null;
    }
    public static AxisAlignedBB bounds(TileEntityProgrammableTrapdoor leaf) {
        return leaf.getWorld().getBlockState(leaf.getPos()).getBoundingBox(leaf.getWorld(),leaf.getPos()).offset(leaf.getPos());
    }
    public static RayTraceResult trace(World world,Vec3d start,Vec3d end) {
        RayTraceResult nearest=null;double distance=Double.POSITIVE_INFINITY;
        for(BlockPos pos:TrapdoorRayCandidates.positions(start,end)) {
            TileEntityProgrammableTrapdoor leaf=leaf(world,pos);if(leaf==null)continue;
            RayTraceResult hit=leaf instanceof TileEntityProgrammableDiagonalTrapdoor
                    ? world.getBlockState(pos).collisionRayTrace(world,pos,start,end)
                    : bounds(leaf).calculateIntercept(start,end);
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
        Class<?> type=world.getClass();
        if(type==net.minecraft.world.WorldServer.class||type==net.minecraft.world.WorldServerMulti.class
                ||type.getName().equals("net.minecraft.client.multiplayer.WorldClient")
                ||world instanceof com.vandorlabs.vehicle.VehicleWorld){
            List<TileEntityProgrammableTrapdoor> candidates=INDEX.query(world,query.grow(2,1,2));
            // Preserve getAllInBoxMutable's X-fastest traversal order.
            if(candidates.size()>1)candidates.sort((a,b)->{int z=Integer.compare(a.getPos().getZ(),b.getPos().getZ());if(z!=0)return z;int y=Integer.compare(a.getPos().getY(),b.getPos().getY());return y!=0?y:Integer.compare(a.getPos().getX(),b.getPos().getX());});
            for(TileEntityProgrammableTrapdoor tile:candidates)addLeafCollision(world,tile.getPos(),query,boxes);
            return;
        }
        int x0=MathHelper.floor(query.minX-2),y0=MathHelper.floor(query.minY-1),z0=MathHelper.floor(query.minZ-2);
        int x1=MathHelper.floor(query.maxX+2),y1=MathHelper.floor(query.maxY+1),z1=MathHelper.floor(query.maxZ+2);
        if(!TrapdoorSectionPresence.mayContain(world,x0,y0,z0,x1,y1,z1))return;
        scanCollisions(world,query,boxes);
    }
    private static void addLeafCollision(World world,BlockPos pos,AxisAlignedBB query,List<AxisAlignedBB> boxes){
        TileEntityProgrammableTrapdoor leaf=leaf(world,pos);if(leaf==null||!leaf.isSliding()&&!leaf.isCover()&&!leaf.isSlideOverSurface())return;
        AxisAlignedBB box=bounds(leaf);if(box.intersects(query)&&!boxes.contains(box))boxes.add(box);
    }
    private static void scanCollisions(World world,AxisAlignedBB query,List<AxisAlignedBB> boxes) {
        for(BlockPos pos:BlockPos.getAllInBoxMutable(new BlockPos(query.minX-2,query.minY-1,query.minZ-2),new BlockPos(query.maxX+2,query.maxY+1,query.maxZ+2))) {
            addLeafCollision(world,pos,query,boxes);
        }
    }
}
