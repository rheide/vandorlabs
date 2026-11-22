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
        return tile instanceof TileEntityProgrammableTrapdoor && (((TileEntityProgrammableTrapdoor)tile).splitCenter() || ((TileEntityProgrammableTrapdoor)tile).isSliding() || ((TileEntityProgrammableTrapdoor)tile).isCover() || ((TileEntityProgrammableTrapdoor)tile).isSlideOverSurface() || tile instanceof TileEntityProgrammableDiagonalTrapdoor)?(TileEntityProgrammableTrapdoor)tile:null;
    }
    public static AxisAlignedBB bounds(TileEntityProgrammableTrapdoor leaf) {
        return leaf.getWorld().getBlockState(leaf.getPos()).getBoundingBox(leaf.getWorld(),leaf.getPos()).offset(leaf.getPos());
    }
    /** Leaves of a group spanning up to 8 cells can move that far from their own block. */
    static final int REACH=8;
    private static boolean indexed(World world){
        Class<?> type=world.getClass();
        return type==net.minecraft.world.WorldServer.class||type==net.minecraft.world.WorldServerMulti.class
                ||type.getName().equals("net.minecraft.client.multiplayer.WorldClient")
                ||world instanceof com.vandorlabs.vehicle.VehicleWorld;
    }
    private static Iterable<BlockPos> rayOwners(World world,Vec3d start,Vec3d end){
        if(!indexed(world))return TrapdoorRayCandidates.extendedPositions(start,end);
        List<BlockPos> owners=new java.util.ArrayList<>();
        for(TileEntityProgrammableTrapdoor tile:INDEX.query(world,new AxisAlignedBB(start.x,start.y,start.z,end.x,end.y,end.z).grow(REACH)))owners.add(tile.getPos());
        return owners;
    }
    public static RayTraceResult trace(World world,Vec3d start,Vec3d end) {
        RayTraceResult nearest=null;double distance=Double.POSITIVE_INFINITY;
        for(BlockPos pos:rayOwners(world,start,end)) {
            TileEntityProgrammableTrapdoor leaf=leaf(world,pos);if(leaf==null)continue;
            RayTraceResult hit=leaf.splitCenter() || leaf instanceof TileEntityProgrammableDiagonalTrapdoor
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
        if(indexed(world)){
            List<TileEntityProgrammableTrapdoor> candidates=INDEX.query(world,query.grow(REACH));
            // Preserve getAllInBoxMutable's X-fastest traversal order.
            if(candidates.size()>1)candidates.sort((a,b)->{int z=Integer.compare(a.getPos().getZ(),b.getPos().getZ());if(z!=0)return z;int y=Integer.compare(a.getPos().getY(),b.getPos().getY());return y!=0?y:Integer.compare(a.getPos().getX(),b.getPos().getX());});
            for(TileEntityProgrammableTrapdoor tile:candidates)addLeafCollision(world,tile.getPos(),query,boxes);
            return;
        }
        // Modded world wrappers may supply tiles without forwarding their load callbacks.
        // Split panels can swing vertically as far as their hinge is from their owner.
        AxisAlignedBB search=query.grow(REACH);
        BlockPos first=new BlockPos(search.minX,search.minY,search.minZ),last=new BlockPos(search.maxX,search.maxY,search.maxZ);
        if(!TrapdoorSectionPresence.mayContain(world,first.getX(),first.getY(),first.getZ(),last.getX(),last.getY(),last.getZ()))return;
        for(BlockPos pos:BlockPos.getAllInBoxMutable(first,last)){
            if((pos.getX()+1<query.minX||pos.getX()>query.maxX)
                    &&(pos.getZ()+1<query.minZ||pos.getZ()>query.maxZ))continue;
            addLeafCollision(world,pos,query,boxes);
        }
    }
    private static void addLeafCollision(World world,BlockPos pos,AxisAlignedBB query,List<AxisAlignedBB> boxes){
        TileEntityProgrammableTrapdoor leaf=leaf(world,pos);if(leaf==null||!leaf.splitCenter()&&!leaf.isSliding()&&!leaf.isCover()&&!leaf.isSlideOverSurface())return;
        if(leaf.splitCenter()){
            for(AxisAlignedBB box:BlockProgrammableTrapdoor.splitBoxes(world.getBlockState(pos),leaf))if(box.intersects(query)&&!boxes.contains(box))boxes.add(box);
        }else{AxisAlignedBB box=bounds(leaf);if(box.intersects(query)&&!boxes.contains(box))boxes.add(box);}
    }
}
