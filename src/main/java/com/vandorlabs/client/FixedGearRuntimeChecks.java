package com.vandorlabs.client;

import com.vandorlabs.gear.*;
import com.vandorlabs.gear.core.GearMotion;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Fixed feet keep their exact geometry under clicks, redstone, saved settings and capture poses. */
final class FixedGearRuntimeChecks {
    static void run(WorldServer world,EntityPlayerMP player){
        BlockPos root=new BlockPos(545,210,180);
        try{for(boolean large:new boolean[]{false,true})for(int foot=0;foot<3;foot++)for(EnumFacing facing:EnumFacing.HORIZONTALS){
            clear(world,root);GearBlock mount=large?GearRegistry.LARGE_GEAR_MOUNT:GearRegistry.GEAR_MOUNT;
            ItemBlock item=(ItemBlock)Item.getItemFromBlock(mount);
            require(item.placeBlockAt(new ItemStack(mount),player,world,root,EnumFacing.UP,.5F,.5F,.5F,mount.getDefaultState().withProperty(GearBlock.FACING,facing)),"mount placement");
            GearBlock base=foot==0?GearRegistry.LANDING_PLATE:foot==1?GearRegistry.LANDING_SKI:large?GearRegistry.LARGE_LANDING_WHEEL:GearRegistry.LANDING_WHEEL;
            if(foot==2){require(((ItemBlock)Item.getItemFromBlock(base)).placeBlockAt(new ItemStack(base),player,world,root.down(),EnumFacing.UP,.5F,.5F,.5F,base.getDefaultState().withProperty(GearBlock.FACING,facing)),"wheel placement");}
            else for(BlockPos at:mount.componentCells(world,root))world.setBlockState(at.down(),base.getDefaultState().withProperty(GearBlock.FACING,facing));
            world.setBlockState(root.up(),Blocks.IRON_BLOCK.getDefaultState());GearTileEntity tile=(GearTileEntity)world.getTileEntity(root);
            require(tile.rebuild()&&tile.mode()==GearMotion.Mode.STATIC,"fixed formation "+tile.error());
            List<AxisAlignedBB> before=new ArrayList<>(tile.collisionBoxes());
            NBTTagCompound settings=tile.settings();settings.setBoolean("Deployed",false);settings.setInteger("Trigger",1);tile.configure(settings);
            tile.target(false);tile.captureProgress(0);for(int tick=0;tick<40;tick++)tile.update();
            require(tile.target()&&tile.fullyDeployed()&&tile.progress()==1&&before.equals(tile.collisionBoxes()),"fixed mount cannot retract or fold");
            GearTileEntity copy=(GearTileEntity)TileEntity.create(world,tile.writeToNBT(new NBTTagCompound()));
            require(copy.isFixedMount()&&copy.fullyDeployed(),"fixed mount save/load");
            net.minecraft.util.NonNullList<ItemStack> catalog=net.minecraft.util.NonNullList.create();mount.getSubBlocks(com.vandorlabs.VandorLabs.VANDOR_LABS_TAB,catalog);require(catalog.size()==1,"fixed mount creative item");
        }}finally{clear(world,root);}
        System.out.println("[vandorlabs][reprolab] fixed-gear PASS both sizes three foot types four facings redstone clicks immutable pose and persistence");
    }
    static void fixture(WorldServer world,EntityPlayerMP player){
        for(boolean large:new boolean[]{false,true})for(int foot=0;foot<3;foot++){
            BlockPos root=new BlockPos(470+foot*4,32,large?126:120);
            GearBlock mount=large?GearRegistry.LARGE_GEAR_MOUNT:GearRegistry.GEAR_MOUNT;
            ((ItemBlock)Item.getItemFromBlock(mount)).placeBlockAt(new ItemStack(mount),player,world,root,EnumFacing.UP,.5F,.5F,.5F,mount.getDefaultState());
            GearBlock base=foot==0?GearRegistry.LANDING_PLATE:foot==1?GearRegistry.LANDING_SKI:large?GearRegistry.LARGE_LANDING_WHEEL:GearRegistry.LANDING_WHEEL;
            if(foot==2)((ItemBlock)Item.getItemFromBlock(base)).placeBlockAt(new ItemStack(base),player,world,root.down(),EnumFacing.UP,.5F,.5F,.5F,base.getDefaultState());
            else for(BlockPos at:mount.componentCells(world,root))world.setBlockState(at.down(),base.getDefaultState());
            world.setBlockState(root.up(),Blocks.IRON_BLOCK.getDefaultState());require(((GearTileEntity)world.getTileEntity(root)).rebuild(),"fixed visual fixture");
        }
    }
    private static void clear(WorldServer world,BlockPos root){for(BlockPos at:BlockPos.getAllInBox(root.add(-3,-5,-3),root.add(4,3,4)))if(!world.isAirBlock(at))world.setBlockToAir(at);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("fixed gear: "+message);}
}
