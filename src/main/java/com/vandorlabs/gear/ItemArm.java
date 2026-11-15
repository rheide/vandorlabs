package com.vandorlabs.gear;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Placement face belongs to the tile, leaving every existing block metadata value readable. */
public class ItemArm extends net.minecraft.item.ItemBlock {
    public ItemArm(Block block){super(block);}
    @Override public net.minecraft.creativetab.CreativeTabs[] getCreativeTabs(){return getCreativeTab()==null?new net.minecraft.creativetab.CreativeTabs[0]:super.getCreativeTabs();}
    @Override public net.minecraft.util.EnumActionResult onItemUse(EntityPlayer player,World world,BlockPos pos,net.minecraft.util.EnumHand hand,EnumFacing side,float x,float y,float z){
        // Modded player controllers can send the ordinary placement packet for a posed selection.
        // Use the same server-recomputed interaction as our controller rather than its rest-cell face.
        net.minecraft.tileentity.TileEntity raw=world.isBlockLoaded(pos)?world.getTileEntity(pos):null;
        GearTileEntity root=raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;
        if(hand==net.minecraft.util.EnumHand.MAIN_HAND&&root!=null&&root.isLandingAssembly()&&root.isSwingArmController()&&!root.fullyDeployed()&&ArmLooseGearData.foot(getBlock().getDefaultState())&&((GearBlock)getBlock()).kind()!=GearBlock.Kind.TRACK){
            if(!world.isRemote&&player instanceof net.minecraft.entity.player.EntityPlayerMP){try{GearInteractions.use((net.minecraft.entity.player.EntityPlayerMP)player,pos);}catch(IllegalArgumentException refused){root.showError(refused.getMessage());return net.minecraft.util.EnumActionResult.FAIL;}}
            return net.minecraft.util.EnumActionResult.SUCCESS;
        }
        return super.onItemUse(player,world,pos,hand,side,x,y,z);
    }
    @Override public boolean placeBlockAt(ItemStack stack,EntityPlayer player,World world,BlockPos pos,EnumFacing side,float x,float y,float z,IBlockState state){
        if(!super.placeBlockAt(stack,player,world,pos,side,x,y,z,state))return false;
        net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);
        if(raw instanceof GearTileEntity){
            GearTileEntity tile=(GearTileEntity)raw;
            net.minecraft.tileentity.TileEntity clicked=world.getTileEntity(pos.offset(side.getOpposite()));
            if(raw instanceof com.vandorlabs.gear.track.TrackTile&&clicked instanceof com.vandorlabs.gear.track.TrackTile&&!(stack.hasTagCompound()&&stack.getTagCompound().getCompoundTag("ModularGearSettings").hasKey("TrackStyle",3)))((com.vandorlabs.gear.track.TrackTile)raw).style(((com.vandorlabs.gear.track.TrackTile)clicked).style().ordinal());
            // Chained arms inherit their column's axis rather than the clicked side edge.
            EnumFacing mounting=clicked instanceof GearTileEntity&&GearAssembly.isExtender(world.getBlockState(pos.offset(side.getOpposite())))?((GearTileEntity)clicked).mountDirection():GearAssembly.isFoot(world.getBlockState(pos.offset(side.getOpposite())))?side.getOpposite():side;
            if(GearAssembly.isExtender(state)&&!(stack.hasTagCompound()&&stack.getTagCompound().hasKey("ArmRecovery",10)))tile.setMountDirection(mounting);
            else if(clicked instanceof GearTileEntity)tile.setMountDirection(((GearTileEntity)clicked).mountDirection());
            GearTileEntity.requestNearbyRebuild(world,pos);
        }
        return true;
    }
}
