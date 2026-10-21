package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.container.ContainerTokenSlot;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Survival token insertion, per-design authorization, channels, pulse expiry and non-consuming filters. */
final class TokenSlotRuntimeChecks {
    private static void require(boolean valid,String reason){if(!valid)throw new IllegalStateException("token mount: "+reason);}
    static void run(WorldServer world){
        EntityPlayerMP player=net.minecraftforge.common.util.FakePlayerFactory.get(world,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("8afda66c-e56d-4c52-906b-273a2b853c3b"),"TokenMountChecks"));player.capabilities.isCreativeMode=false;
        BlockPos pos=new BlockPos(416,185,416);world.getChunkFromBlockCoords(pos);player.setPosition(pos.getX()+.5,pos.getY()+1,pos.getZ()+2);
        int checked=0;
        for(boolean chip:new boolean[]{false,true})for(EnumFacing face:EnumFacing.values())for(int rotation=0;rotation<(face.getAxis()==EnumFacing.Axis.Y?4:1);rotation++){
            BlockTokenSlot block=(BlockTokenSlot)net.minecraft.block.Block.getBlockFromName("vandorlabs:data_"+(chip?"chip":"card")+"_slot");BlockPos support=pos.offset(face.getOpposite());world.setBlockState(support,net.minecraft.init.Blocks.STONE.getDefaultState(),2);world.setBlockState(pos,block.getDefaultState().withProperty(BlockVandorSwitch.FACING,face),2);
            TileEntityTokenSlot tile=(TileEntityTokenSlot)world.getTileEntity(pos);tile.setMountRotation(rotation);tile.setRedstoneChannels(ChannelList.of(64211));RedstoneChannels.register(tile);
            AxisAlignedBB box=block.getBoundingBox(world.getBlockState(pos),world,pos);require(box.minX>=0&&box.minY>=0&&box.minZ>=0&&box.maxX<=1&&box.maxY<=1&&box.maxZ<=1,"compact bounds "+face);
            for(int design=0;design<7;design++){
                tile.configure(false,design==0?2:1<<design,0);ItemStack denied=new ItemStack(chip?ModItems.DATA_CHIP:ModItems.DATA_CARD,2,design==0?2:(design+1)%7);player.setHeldItem(EnumHand.MAIN_HAND,denied);activate(block,world,pos,player,face);
                require(denied.getCount()==2&&tile.isEmpty()&&tile.getOutputLevel()==0&&tile.rejected(),"nonmatching design rejected without consuming item");player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);activate(block,world,pos,player,face);require(tile.isEmpty(),"empty hand removes one token");
                tile.configure(false,1<<design,0);
                if(design==0)for(int company=0;company<7;company++)require(tile.authorized(new ItemStack(chip?ModItems.DATA_CHIP:ModItems.DATA_CARD,1,company)),"plain icon accepts every company");
                ItemStack accepted=new ItemStack(chip?ModItems.DATA_CHIP:ModItems.DATA_CARD,2,design);accepted.setStackDisplayName("Reader persistence fixture");player.setHeldItem(EnumHand.MAIN_HAND,accepted);activate(block,world,pos,player,face);
                require(accepted.getCount()==1&&tile.getOutputLevel()==15&&RedstoneChannels.level(world,64211)==15,"matching design powers block and channel");
                NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileEntityTokenSlot copy=(TileEntityTokenSlot)net.minecraft.tileentity.TileEntity.create(world,saved);require(copy!=null&&copy.matches()&&copy.getStackInSlot(0).getDisplayName().equals("Reader persistence fixture"),"full token NBT preserved");
                tile.setLocalOn(false);require(tile.getOutputLevel()==15,"generic switch calls cannot replace token policy");
                tile.clear();require(RedstoneChannels.level(world,64211)==0,"removal releases channel");
                tile.configure(false,1<<design,2);ItemStack tap=accepted.copy();tap.setCount(2);
                require(tile.insertToggle(player,tap)&&tap.getCount()==1&&tile.returning(),"toggle borrows exactly one item");
                require(tile.removeStackFromSlot(0).isEmpty()&&!tile.isItemValidForSlot(0,tap),"pending return cannot be extracted or automated");
                NBTTagCompound pending=tile.writeToNBT(new NBTTagCompound());TileEntityTokenSlot pendingCopy=(TileEntityTokenSlot)net.minecraft.tileentity.TileEntity.create(world,pending);
                pendingCopy.expire(world.getTotalWorldTime()+8);require(pendingCopy.getOutputLevel()==15,"insertion flips saved output at scan midpoint");
                pendingCopy.configure(false,1<<design,2);require(pendingCopy.getOutputLevel()==15,"unchanged settings do not clear toggle");
                TileEntityTokenSlot resumed=(TileEntityTokenSlot)net.minecraft.tileentity.TileEntity.create(world,pendingCopy.writeToNBT(new NBTTagCompound()));resumed.expire(world.getTotalWorldTime()+40);
                require(resumed.getOutputLevel()==15&&resumed.returning(),"offline owner retains item and reload cannot toggle twice");
                require(resumed.takeForDrop().getCount()==1&&resumed.takeForDrop().isEmpty(),"interrupted insertion drops once");
                TileEntityTokenSlot emptyLatched=(TileEntityTokenSlot)net.minecraft.tileentity.TileEntity.create(world,resumed.writeToNBT(new NBTTagCompound()));require(emptyLatched.isEmpty()&&emptyLatched.getOutputLevel()==15,"empty toggle state persists");emptyLatched.configure(true,127,2);require(emptyLatched.getOutputLevel()==0,"policy change resets toggle");
                tile.takeForDrop();tile.configure(false,1<<design,3);ItemStack pulse=accepted.copy();pulse.setCount(2);require(tile.insertToggle(player,pulse),"pulse-return borrows item");
                TileEntityTokenSlot pulsed=(TileEntityTokenSlot)net.minecraft.tileentity.TileEntity.create(world,tile.writeToNBT(new NBTTagCompound()));pulsed.setWorld(world);pulsed.expire(world.getTotalWorldTime()+8);require(pulsed.getOutputLevel()==15,"pulse-return scan powers output");NBTTagCompound ended=pulsed.writeToNBT(new NBTTagCompound());ended.setLong("TokenPulseUntil",world.getTotalWorldTime()-1);pulsed.readFromNBT(ended);pulsed.expire(world.getTotalWorldTime());require(pulsed.getOutputLevel()==0&&pulsed.returning(),"pulse-return expires and retains offline token");
                tile.takeForDrop();tile.configure(false,1<<design,0);checked++;
            }
            tile.configure(true,0,0);tile.setInventorySlotContents(0,new ItemStack(chip?ModItems.DATA_CHIP:ModItems.DATA_CARD,1,6));require(tile.getOutputLevel()==15,"Any accepts company tokens");tile.clear();
            tile.configure(false,2,1);tile.setInventorySlotContents(0,new ItemStack(chip?ModItems.DATA_CHIP:ModItems.DATA_CARD,1,1));require(tile.getOutputLevel()==15,"pulse on accepted insertion");NBTTagCompound expired=tile.writeToNBT(new NBTTagCompound());expired.setLong("TokenPulseUntil",world.getTotalWorldTime()-1);tile.readFromNBT(expired);tile.refresh();require(tile.getOutputLevel()==0&&RedstoneChannels.level(world,64211)==0,"pulse expires without losing token");tile.clear();
            ContainerTokenSlot container=new ContainerTokenSlot(player.inventory,tile);player.inventory.setItemStack(new ItemStack(chip?ModItems.DATA_CHIP:ModItems.DATA_CARD,5,3));container.slotClick(1,0,ClickType.PICKUP,player);require(player.inventory.getItemStack().getCount()==5&&!tile.any&&(tile.allowed&8)!=0,"filter accepts a design without consuming items");player.inventory.setItemStack(ItemStack.EMPTY);
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);world.setBlockToAir(pos);world.setBlockToAir(support);
        }
        player.inventory.clear();System.out.println("[vandorlabs][reprolab] token-mounts PASS cases="+checked+" six-faces floor-ceiling-rotations seven-designs survival-count authorization channels pulses toggle-reload-lock offline-return full-item-NBT nonconsuming-filters");
    }
    private static void activate(BlockTokenSlot block,WorldServer world,BlockPos pos,EntityPlayerMP player,EnumFacing face){require(block.onBlockActivated(world,pos,world.getBlockState(pos),player,EnumHand.MAIN_HAND,face,.5F,.5F,.5F),"activation rejected");}
}
