package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.items.ModItems;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Exercise credential policy on installed doors, with full NBT and picked settings. */
final class DoorSecurityRuntimeChecks {
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException("door security: "+reason);}
    static void run(WorldServer world){
        EntityPlayerMP player=net.minecraftforge.common.util.FakePlayerFactory.get(world,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("7065045d-d646-4403-ac53-b43f34a3b32d"),"DoorKeyChecks"));
        BlockPos pos=new BlockPos(430,180,430);world.getChunkFromBlockCoords(pos);player.setPosition(pos.getX()+.5,pos.getY(),pos.getZ()+1);player.capabilities.isCreativeMode=false;
        BlockConfigurableSpaceDoor block=(BlockConfigurableSpaceDoor)Block.getBlockFromName("vandorlabs:programmable_door");
        try{for(EnumFacing facing:EnumFacing.HORIZONTALS){
            world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),2);
            IBlockState state=block.getDefaultState().withProperty(BlockDoor.FACING,facing).withProperty(BlockDoor.HALF,BlockDoor.EnumDoorHalf.LOWER);
            world.setBlockState(pos,state,2);world.setBlockState(pos.up(),state.withProperty(BlockDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),2);
            TileEntitySpaceDoor tile=(TileEntitySpaceDoor)world.getTileEntity(pos);tile.setPlacementDepth(0);tile.setPanelMode(2);tile.configureSecurity(2,8);
            require(tile.panelMode()==2,"centered both panels");
            player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.DATA_CARD,2,4));tile.useSecurity(player,EnumHand.MAIN_HAND);
            require(tile.keyReader().isEmpty()&&player.getHeldItemMainhand().getCount()==2,"denied company untouched");
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);block.onBlockActivated(world,pos,state,player,EnumHand.MAIN_HAND,facing,.5F,.5F,.5F);
            require(!world.getBlockState(pos).getValue(BlockDoor.OPEN),"leaf cannot bypass reader");
            player.setSneaking(true);block.onBlockActivated(world,pos,state,player,EnumHand.MAIN_HAND,facing,.5F,.5F,.5F);player.setSneaking(false);
            require(!world.getBlockState(pos).getValue(BlockDoor.OPEN),"sneaking cannot bypass reader");
            ItemStack card=new ItemStack(ModItems.DATA_CARD,2,3);card.setStackDisplayName("Door credential");player.setHeldItem(EnumHand.MAIN_HAND,card);tile.useSecurity(player,EnumHand.MAIN_HAND);
            require(card.getCount()==1&&world.getBlockState(pos).getValue(BlockDoor.OPEN),"accepted card opens door");
            NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileEntitySpaceDoor copy=(TileEntitySpaceDoor)net.minecraft.tileentity.TileEntity.create(world,saved);copy.setWorld(world);
            require(copy.securityMode()==2&&copy.panelMode()==2&&copy.keyReader().getStackInSlot(0).getDisplayName().equals("Door credential"),"credential policy and full item survive reload");
            require(!tile.itemSettings().hasKey("DoorKeyReader"),"picked settings never clone physical token");
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);tile.useSecurity(player,EnumHand.MAIN_HAND);
            require(tile.keyReader().isEmpty()&&!world.getBlockState(pos).getValue(BlockDoor.OPEN),"removing retained card closes door");
            player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.DATA_CARD,2,3));tile.useSecurity(player,EnumHand.MAIN_HAND,0);tile.useSecurity(player,EnumHand.MAIN_HAND,1);
            require(!tile.keyReader(0).isEmpty()&&!tile.keyReader(1).isEmpty(),"both faces hold separate physical cards");
            TileEntitySpaceDoor both=(TileEntitySpaceDoor)net.minecraft.tileentity.TileEntity.create(world,tile.writeToNBT(new NBTTagCompound()));both.setWorld(world);require(!both.keyReader(0).isEmpty()&&!both.keyReader(1).isEmpty(),"both physical cards survive save/load");
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);tile.useSecurity(player,EnumHand.MAIN_HAND,1);require(!tile.keyReader(0).isEmpty()&&tile.keyReader(1).isEmpty()&&world.getBlockState(pos).getValue(BlockDoor.OPEN),"removal only affects clicked reader");player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);tile.useSecurity(player,EnumHand.MAIN_HAND,0);require(!world.getBlockState(pos).getValue(BlockDoor.OPEN),"door closes when both readers release");
            tile.setPlacementDepth(1);require(tile.panelMode()==1,"both restricted to centered");tile.setPlacementDepth(0);tile.configureSecurity(1,8);
            player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.DATA_CARD,2,3));tile.useSecurity(player,EnumHand.MAIN_HAND);
            require(tile.keyReader().returning()&&player.getHeldItemMainhand().getCount()==1,"return mode borrows one");tile.keyReader().expire(world.getTotalWorldTime()+8);
            require(world.getBlockState(pos).getValue(BlockDoor.OPEN),"return mode opens after insertion");
            tile.keyReader().takeForDrop();tile.configureSecurity(0,127);world.setBlockToAir(pos.up());world.setBlockToAir(pos);
        }}finally{world.setBlockToAir(pos.up());world.setBlockToAir(pos);world.setBlockToAir(pos.down());player.inventory.clear();player.setSneaking(false);}
        large(world,player,pos);
        System.out.println("[vandorlabs][reprolab] door-security PASS four facings both-panel restriction denied-company manual-bypass retained-token return-scan NBT no-copy-content large-nine-cell-lock");
    }
    private static void large(WorldServer world,EntityPlayerMP player,BlockPos pos){
        BlockLargeProgrammableDoor block=(BlockLargeProgrammableDoor)Block.getBlockFromName("vandorlabs:large_programmable_door");
        IBlockState state=block.getDefaultState().withProperty(BlockDoor.FACING,EnumFacing.SOUTH);
        try{
            for(int x=0;x<3;x++)for(int y=0;y<3;y++){BlockPos cell=pos.east(x).up(y);world.getChunkFromBlockCoords(cell);world.setBlockState(cell,state,2);((TileEntityLargeProgrammableDoor)world.getTileEntity(cell)).assign(pos);}
            TileEntityLargeProgrammableDoor tile=(TileEntityLargeProgrammableDoor)world.getTileEntity(pos);tile.setPlacementDepth(0);tile.setPanelMode(1);tile.configureSecurity(2,8);
            int single=tile.collisionGeometry(EnumFacing.SOUTH,false).size();tile.setPanelMode(2);require(tile.collisionGeometry(EnumFacing.SOUTH,false).size()==single+1,"large both-panel collision");
            player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.DATA_CARD,2,3));tile.useSecurity(player,EnumHand.MAIN_HAND);
            for(int x=0;x<3;x++)for(int y=0;y<3;y++)require(world.getBlockState(pos.east(x).up(y)).getValue(BlockDoor.OPEN),"large security opens all nine cells");
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);tile.useSecurity(player,EnumHand.MAIN_HAND);
            for(int x=0;x<3;x++)for(int y=0;y<3;y++)require(!world.getBlockState(pos.east(x).up(y)).getValue(BlockDoor.OPEN),"large removal closes all nine cells");
        }finally{for(int x=0;x<3;x++)for(int y=0;y<3;y++)world.setBlockToAir(pos.east(x).up(y));player.inventory.clear();}
    }
}
