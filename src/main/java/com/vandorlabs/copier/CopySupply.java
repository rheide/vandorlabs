package com.vandorlabs.copier;

import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.block.BlockChest;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** A concrete chest withdrawal plan, including exact before/after tile backups for rollback. */
public final class CopySupply {
    public final VehicleStructure before,after;
    public final BlockPos origin;
    private CopySupply(VehicleStructure before,VehicleStructure after,BlockPos origin){this.before=before;this.after=after;this.origin=origin;}
    private static final class Slot {final IInventory inventory;final int slot;ItemStack stack;Slot(IInventory inventory,int slot){this.inventory=inventory;this.slot=slot;stack=inventory.getStackInSlot(slot).copy();}}
    public static CopySupply prepare(CopyControllerTile owner,EntityPlayerMP actor,List<CopyMaterials.Need> needs){
        WorldServer world=(WorldServer)owner.getWorld();BlockPos back=owner.chest();for(EnumFacing side:EnumFacing.values())if(!world.isBlockLoaded(back.offset(side),false))throw new IllegalArgumentException("Load the material chest and its boundary first");
        TileEntity raw=world.getTileEntity(back);if(!(raw instanceof TileEntityChest)||!(world.getBlockState(back).getBlock() instanceof BlockChest))throw new IllegalArgumentException("Place a material chest behind the Copy Controller");
        net.minecraft.world.ILockableContainer accessible=((BlockChest)world.getBlockState(back).getBlock()).getLockableContainer(world,back);if(accessible==null||!actor.canOpen(accessible.getLockCode()))throw new IllegalArgumentException("The material chest is locked or obstructed");
        List<BlockPos> positions=new ArrayList<>();positions.add(back);for(EnumFacing side:EnumFacing.HORIZONTALS){BlockPos at=back.offset(side);if(world.getBlockState(at).getBlock()==world.getBlockState(back).getBlock()&&world.getTileEntity(at) instanceof TileEntityChest)positions.add(at);}if(positions.size()>2)throw new IllegalArgumentException("Use a valid single or double material chest");Collections.sort(positions);
        BlockPos min=positions.get(0);for(BlockPos p:positions)min=new BlockPos(Math.min(min.getX(),p.getX()),Math.min(min.getY(),p.getY()),Math.min(min.getZ(),p.getZ()));
        List<VehicleStructure.Cell> old=new ArrayList<>(),next=new ArrayList<>();List<Slot> slots=new ArrayList<>();Map<BlockPos,IInventory> clones=new LinkedHashMap<>();
        for(BlockPos p:positions){VehicleService.permission(actor,p);TileEntity tile=world.getTileEntity(p);NBTTagCompound tag=VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(min));old.add(new VehicleStructure.Cell(p.subtract(min),world.getBlockState(p),tag));TileEntity copy=TileEntity.create(null,tag);if(!(copy instanceof IInventory))throw new IllegalArgumentException("Material chest contents cannot be saved exactly");IInventory inventory=(IInventory)copy;clones.put(p,inventory);for(int slot=0;slot<inventory.getSizeInventory();slot++)slots.add(new Slot(inventory,slot));}
        for(CopyMaterials.Need need:needs){long missing=need.count;for(Slot slot:slots){if(!CopyMaterials.satisfies(need,slot.stack))continue;int used=(int)Math.min(missing,slot.stack.getCount());slot.stack.shrink(used);missing-=used;if(missing==0)break;}if(missing>0)throw new IllegalArgumentException("Missing material: "+need.item.getDisplayName()+" x "+missing+(need.exact?" (matching item data)":" (empty block item)"));}
        for(Slot slot:slots)slot.inventory.setInventorySlotContents(slot.slot,slot.stack);
        for(Map.Entry<BlockPos,IInventory> clone:clones.entrySet()){TileEntity tile=(TileEntity)clone.getValue();next.add(new VehicleStructure.Cell(clone.getKey().subtract(min),world.getBlockState(clone.getKey()),tile.writeToNBT(new NBTTagCompound())));}
        return new CopySupply(VehicleStructure.display(old),VehicleStructure.display(next),min);
    }
    public void validate(WorldServer world){if(!before.matchesExact(world,origin))throw new IllegalArgumentException("The material chest changed; try the copy again");}
    public void withdraw(WorldServer world){validate(world);CopyWorld.install(world,after,origin,CopyWorld.prepare(world,after,origin),true);if(!after.matchesExact(world,origin))throw new IllegalStateException("Material withdrawal could not be verified");}
    public void refund(WorldServer world){if(!after.matchesExact(world,origin)&&!before.matchesExact(world,origin))throw new IllegalArgumentException("Material chest changed; recovery backup retained");CopyWorld.install(world,before,origin,CopyWorld.prepare(world,before,origin),true);}
    public NBTTagCompound write(){NBTTagCompound tag=new NBTTagCompound();tag.setLong("Origin",origin.toLong());tag.setTag("Before",before.write());tag.setTag("After",after.write());return tag;}
    public static CopySupply read(NBTTagCompound tag){return new CopySupply(VehicleStructure.readDisplay(tag.getCompoundTag("Before")),VehicleStructure.readDisplay(tag.getCompoundTag("After")),BlockPos.fromLong(tag.getLong("Origin")));}
}
