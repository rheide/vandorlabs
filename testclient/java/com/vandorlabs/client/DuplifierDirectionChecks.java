package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

final class DuplifierDirectionChecks {
    static void run(){
        for(boolean ignore:new boolean[]{false,true}){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
            EntityPlayer player=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"DirectionChecks")){
                public boolean isSpectator(){return false;}public boolean isCreative(){return true;}
            };
            ItemStack tool=new ItemStack(Items.STICK);
            require(!DuplifierApplyOptions.ignoreDirectionality(tool),"new option must default off");
            DuplifierApplyOptions.setIgnoreDirectionality(tool,ignore);
            NBTTagCompound copy=new NBTTagCompound();copy.setInteger(ProgrammableSettings.WALL_TEXTURE,4);tool.setTagInfo(ItemDuplifier.SETTINGS_TAG,copy);
            BlockPos origin=new BlockPos(4,100,4);
            for(int i=0;i<6;i++){
                world.setBlockState(origin.east(i),ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.values()[i]),2);
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(origin.east(i));
                tile.setCeilingMounted((i&1)!=0);tile.setCeilingPosition(i%3);tile.setWallPosition(i%3);
            }
            world.setBlockState(origin.north(),ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),2);
            ((TileEntityAnimatedScreenSelector)world.getTileEntity(origin.north())).setHousingTexture(2);
            world.setBlockState(origin.south(),ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),2);
            ((TileEntityAnimatedScreenSelector)world.getTileEntity(origin.south())).setFaceTextures(new FaceTextures(false,new int[]{1,-1,-1,-1,-1,-1}));
            world.setBlockState(origin.west(),ModBlocks.PROGRAMMABLE_SLAB.getDefaultState(),2);
            int applied=DuplifierConnectedApply.apply(world,origin,tool,player,EnumFacing.UP);
            require(applied==(ignore?6:1),"orientation selection ignore="+ignore+" actual="+applied);
            for(int i=0;i<6;i++){
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(origin.east(i));
                require(tile.getHousingTexture()==(ignore||i==0?4:0),"unexpected selected finish");
                require(world.getBlockState(origin.east(i)).getValue(BlockAnimatedScreenSelector.FACING)==EnumFacing.values()[i],"changed target facing");
                require(tile.isCeilingMounted()==((i&1)!=0)&&tile.getWallPosition(-1)==i%3,"changed target placement");
            }
            require(((TileEntityAnimatedScreenSelector)world.getTileEntity(origin.north())).getHousingTexture()==2,"crossed texture boundary");
            require(((TileEntityAnimatedScreenSelector)world.getTileEntity(origin.south())).getHousingTexture()==0,"crossed disabled face-override boundary");
            require(((TileEntityAnimatedScreenSelector)world.getTileEntity(origin.west())).getHousingTexture()==0,"crossed block type boundary");
            ItemStack saved=new ItemStack(tool.writeToNBT(new NBTTagCompound()));
            require(DuplifierApplyOptions.ignoreDirectionality(saved)==ignore,"item reload lost option");
            ItemDuplifier.clearCopyState(saved);require(DuplifierApplyOptions.ignoreDirectionality(saved)==ignore,"clearing copied data lost option");
        }
        System.out.println("Duplifier direction PASS strict/ignored six facings mounting data configuration/type boundaries save clear target orientation preserved");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private DuplifierDirectionChecks(){}
}
