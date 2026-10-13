package com.vandorlabs.canopy;

import com.vandorlabs.tiles.TileEntityCanopy;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.*;

/** Only inspect loaded chunk tile maps; moving shells may occupy air cells. */
public final class CanopyInteractions {
    /** Place into an empty cabin cell when aiming through the front glazing with a block. */
    @SubscribeEvent
    public void placeInside(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if(event.getUseItem()==net.minecraftforge.fml.common.eventhandler.Event.Result.DENY)return;
        net.minecraft.item.ItemStack stack=event.getItemStack();
        if(!(stack.getItem() instanceof net.minecraft.item.ItemBlock) || stack.getItem() instanceof com.vandorlabs.blocks.BlockCanopy.CanopyItem)return;
        TileEntityCanopy selected=TileEntityCanopy.at(event.getWorld(),event.getPos());
        if(selected==null)return;
        TileEntityCanopy owner=selected.owner();
        if(!owner.resizable() || owner.progress!=0)return;
        BlockPos target=interiorTarget(owner,event.getEntityPlayer().getPositionEyes(1),event.getHitVec(),event.getEntityPlayer().getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue());
        if(target==null)return;
        net.minecraft.entity.player.EntityPlayer player=event.getEntityPlayer();
        if(!event.getWorld().isBlockModifiable(player,target) || !player.canPlayerEdit(target,event.getFace(),stack))return;
        int count=stack.getCount(),damage=stack.getItemDamage();
        net.minecraft.util.EnumActionResult result=event.getWorld().isRemote
                ?stack.getItem().onItemUse(player,event.getWorld(),target,event.getHand(),event.getFace(),.5F,.5F,.5F)
                :net.minecraftforge.common.ForgeHooks.onPlaceItemIntoWorld(stack,player,event.getWorld(),target,event.getFace(),.5F,.5F,.5F,event.getHand());
        if(player.capabilities.isCreativeMode){stack.setCount(count);stack.setItemDamage(damage);}
        event.setCanceled(true);event.setCancellationResult(result);
    }

    public static BlockPos interiorTarget(TileEntityCanopy owner,Vec3d eye,Vec3d surface,double reach) {
        Vec3d direction=surface.subtract(eye).normalize();
        double used=eye.distanceTo(surface);
        AxisAlignedBB bounds=owner.selectionBounds();
        Set<BlockPos> shell=new HashSet<>(owner.cells());
        for(double distance=.125;distance+used<=reach;distance+=.125) {
            Vec3d point=surface.add(direction.scale(distance));
            if(!bounds.contains(point))continue;
            BlockPos cell=new BlockPos(point);
            if(!shell.contains(cell) && owner.getWorld().isBlockLoaded(cell)
                    && owner.getWorld().getBlockState(cell).getBlock().isReplaceable(owner.getWorld(),cell))return cell;
        }
        return null;
    }

    public static double distanceSq(Vec3d point,AxisAlignedBB box){double x=MathHelper.clamp(point.x,box.minX,box.maxX),y=MathHelper.clamp(point.y,box.minY,box.maxY),z=MathHelper.clamp(point.z,box.minZ,box.maxZ);return point.squareDistanceTo(new Vec3d(x,y,z));}

    public static List<TileEntityCanopy> owners(World world, AxisAlignedBB region) {
        List<TileEntityCanopy> found = new ArrayList<>();
        AxisAlignedBB query = region.grow(12);
        for (int x = MathHelper.floor(query.minX) >> 4; x <= MathHelper.floor(query.maxX) >> 4; x++)
            for (int z = MathHelper.floor(query.minZ) >> 4;
                    z <= MathHelper.floor(query.maxZ) >> 4;
                    z++) {
                Chunk c = world.getChunkProvider().getLoadedChunk(x, z);
                if (c == null) continue;
                for (TileEntity t : c.getTileEntityMap().values())
                    if (t instanceof TileEntityCanopy
                            && query.intersects(new AxisAlignedBB(t.getPos()))
                            && ((TileEntityCanopy) t).draws()) found.add((TileEntityCanopy) t);
            }
        return found;
    }

    @SubscribeEvent
    public void collision(GetCollisionBoxesEvent event) {
        for (TileEntityCanopy t : owners(event.getWorld(), event.getAabb()))
            for (AxisAlignedBB box : t.boxes())
                if (box.intersects(event.getAabb())) event.getCollisionBoxesList().add(box);
    }

    public static RayTraceResult trace(World world, Vec3d start, Vec3d end) {
        RayTraceResult best = null;
        for (TileEntityCanopy t : owners(world, new AxisAlignedBB(start, end))) {
            RayTraceResult hit = t.trace(start, end);
            if (hit != null
                    && (best == null
                            || start.squareDistanceTo(hit.hitVec)
                                    < start.squareDistanceTo(best.hitVec))) best = hit;
        }
        return best;
    }
}
