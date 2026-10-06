package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Opt-in live placement, shared configuration, save/load, geometry and atomic removal. */
final class LargeDoorRuntimeChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("large-door: "+message);}
    static void run(World world,EntityPlayer player){
        BlockLargeProgrammableDoor door=(BlockLargeProgrammableDoor)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs","large_programmable_door"));
        ItemStack previous=player.getHeldItemMainhand();float yaw=player.rotationYaw;boolean creative=player.capabilities.isCreativeMode;player.capabilities.isCreativeMode=true;
        BlockPos center=new BlockPos(8,80,8);int assertions=0;
        try{for(EnumFacing front:EnumFacing.Plane.HORIZONTAL){
            EnumFacing width=front.rotateYCCW();BlockPos anchor=center.offset(width,-1);
            for(BlockPos p:BlockPos.getAllInBox(center.add(-4,-1,-4),center.add(4,6,4))){require(world.isBlockLoaded(p),"fixture chunk loaded");world.setBlockToAir(p);}
            for(int x=0;x<3;x++)world.setBlockState(anchor.offset(width,x).down(),Blocks.STONE.getDefaultState(),2);
            ItemStack stack=new ItemStack(door,2);player.setHeldItem(EnumHand.MAIN_HAND,stack);player.rotationYaw=front.getOpposite().getHorizontalAngle();player.setPosition(center.getX(),center.getY()+1,center.getZ()+3);
            require(stack.getItem().onItemUse(player,world,center.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"place complete assembly");
            require(stack.getCount()==1,"consume one item");
            TileEntityLargeProgrammableDoor root=door.root(world,center.up(2));require(root!=null && root.getPos().equals(anchor),"top cell resolves anchor");
            int rendered=0;
            for(int x=0;x<3;x++)for(int y=0;y<3;y++){
                BlockPos p=anchor.offset(width,x).up(y);IBlockState state=world.getBlockState(p);
                require(state.getBlock()==door && door.getActualState(state,world,p).getValue(BlockConnectingDetailedDoor.PAIRED),"always paired cell");
                require(ProgrammableTarget.settingsPos(world,p).equals(anchor),"tools resolve anchor");
                TileEntityLargeProgrammableDoor tile=(TileEntityLargeProgrammableDoor)world.getTileEntity(p);
                TileEntity saved=TileEntity.create(world,tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));world.setTileEntity(p,saved);
                require(door.root(world,p)!=null,"role survives NBT");
                if(((TileEntityLargeProgrammableDoor)saved).shouldRenderInPass(0))rendered++;
                assertions+=3;
            }
            require(rendered==1,"one opaque renderer per assembly");root=door.root(world,anchor);
            root.configure(15,2,false,0,true,true,false,0,false);root.setPlacementDepth(2);
            ItemStack picked=door.getPickBlock(world.getBlockState(center),null,world,center,player);
            require(picked.getItem()==stack.getItem() && picked.getSubCompound("SpaceDoorSettings").getInteger("SpaceDesign")==15,"pick retains large type and design");
            door.onBlockActivated(world,center.up(2),world.getBlockState(center.up(2)),player,EnumHand.MAIN_HAND,front,.5F,.5F,.5F);
            for(int x=0;x<3;x++)for(int y=0;y<3;y++)require(world.getBlockState(anchor.offset(width,x).up(y)).getValue(BlockVandorDoor.OPEN),"both leaves open from any cell");
            for(int mode=4;mode<=7;mode++) {
                root.configure(2,1,true,mode,false,true,true,0,false);
                TileEntityLargeProgrammableDoor restored=new TileEntityLargeProgrammableDoor();
                restored.readFromNBT(root.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
                require(restored.getSlideDirection()==mode,"new mode survives world save");
                require(door.getPickBlock(world.getBlockState(center),null,world,center,player)
                        .getSubCompound("SpaceDoorSettings").getInteger("SpaceSlideDirection")==mode,"new mode survives pick");
                java.util.List<net.minecraft.util.math.AxisAlignedBB> closed=root.collisionGeometry(front,false),opened=root.collisionGeometry(front,true);
                require(!closed.equals(opened),"new mode collision did not move");
            }
            root.configure(2,1,true,0,false,false,true,0,true);
            require(!door.geometry(world,center).isEmpty(),"open hinged leaf geometry");
            world.setBlockToAir(center.up());
            for(int x=0;x<3;x++)for(int y=0;y<3;y++)require(world.getBlockState(anchor.offset(width,x).up(y)).getBlock()!=door,"breaking any cell removes assembly");
            // An obstructed upper cell rejects the whole operation without consuming or overwriting.
            world.setBlockState(anchor.up(2),Blocks.GOLD_BLOCK.getDefaultState(),2);stack=new ItemStack(door,2);player.setHeldItem(EnumHand.MAIN_HAND,stack);
            require(stack.getItem().onItemUse(player,world,center.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.FAIL && stack.getCount()==2,"obstruction is atomic");
            require(world.getBlockState(center).getBlock()!=door && world.getBlockState(anchor.up(2)).getBlock()==Blocks.GOLD_BLOCK,"obstructed placement unchanged");
        }}finally{player.setHeldItem(EnumHand.MAIN_HAND,previous);player.rotationYaw=yaw;player.capabilities.isCreativeMode=creative;}
        System.out.println("[vandorlabs][reprolab] large-door-runtime PASS orientations=4 cellContracts="+assertions);
    }
}
