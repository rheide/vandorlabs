package com.vandorlabs.copier;

import com.vandorlabs.blocks.BlockVandorDirectional;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Player-facing artwork and pad directions start with the actual held ItemBlock path. */
public final class CopyPlacementChecks {
    public static void run(WorldServer world,EntityPlayerMP actor){
        BlockPos base=new BlockPos(1050,180,410);world.getChunkFromBlockCoords(base);Block block=Block.getBlockFromName("vandorlabs:copy_controller");ItemStack held=actor.getHeldItemMainhand();double x=actor.posX,y=actor.posY,z=actor.posZ;float yaw=actor.rotationYaw;boolean creative=actor.capabilities.isCreativeMode;
        try{actor.capabilities.isCreativeMode=true;for(EnumFacing front:EnumFacing.HORIZONTALS){
            world.setBlockState(base.down(),Blocks.STONE.getDefaultState(),2);actor.setPosition(base.getX()+.5+front.getFrontOffsetX()*3,base.getY()+1,base.getZ()+.5+front.getFrontOffsetZ()*3);actor.rotationYaw=front.getOpposite().getHorizontalAngle();actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block,2));
            if(Item.getItemFromBlock(block).onItemUse(actor,world,base.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)!=EnumActionResult.SUCCESS||world.getBlockState(base).getValue(BlockVandorDirectional.FACING)!=front)throw new IllegalStateException("Copy Controller normal placement must face the player: "+front);
            CopyControllerTile tile=(CopyControllerTile)world.getTileEntity(base);EnumFacing look=actor.getHorizontalFacing();if(tile.front()!=front||front.rotateY()!=look.rotateYCCW()||front.rotateYCCW()!=look.rotateY()||!tile.chest().equals(base.offset(look)))throw new IllegalStateException("Copy Controller player-left/input player-right/output rear/chest placement: "+front);
            world.removeTileEntity(base);world.setBlockToAir(base);
        }}finally{world.removeTileEntity(base);world.setBlockToAir(base);world.setBlockToAir(base.down());actor.setHeldItem(EnumHand.MAIN_HAND,held);actor.setPosition(x,y,z);actor.rotationYaw=yaw;actor.capabilities.isCreativeMode=creative;}
        System.out.println("[vandorlabs][reprolab] copy-placement PASS actual held ItemBlock four facings player-front left-input right-output away-chest");
    }
    private CopyPlacementChecks(){}
}
