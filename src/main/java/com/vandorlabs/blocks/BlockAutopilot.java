package com.vandorlabs.blocks;

import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Distinct navigation computer and route-marker housings. */
public final class BlockAutopilot extends BlockVandorDirectional {
    private final boolean waypoint;
    public BlockAutopilot(boolean waypoint){super(waypoint?"waypoint":"autopilot");this.waypoint=waypoint;if(!waypoint)setCreativeTab(null);}
    public boolean hasTileEntity(IBlockState state){return true;}
    public TileEntity createTileEntity(World world,IBlockState state){return waypoint?new TileEntityWaypoint():new TileEntityAutopilot();}
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,net.minecraft.entity.EntityLivingBase placer,net.minecraft.item.ItemStack stack){super.onBlockPlacedBy(world,pos,state,placer,stack);if(placer instanceof EntityPlayer)WorldActionAuthorization.authorize(world.getTileEntity(pos),(EntityPlayer)placer);}
    public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){
        if(world.getTileEntity(pos) instanceof com.vandorlabs.rack.LegacyAutopilotRecovery)return false;
        if(hand!=EnumHand.MAIN_HAND||!com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos))return false;
        if(!world.isRemote)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_AUTOPILOT,world,pos.getX(),pos.getY(),pos.getZ());return true;
    }
    public void neighborChanged(IBlockState state,World world,BlockPos pos,Block block,BlockPos from){TileEntity tile=world.getTileEntity(pos);if(tile instanceof TileEntityAutopilot && !(tile instanceof TileEntityWaypoint))((TileEntityAutopilot)tile).refreshPower();}
    public boolean canProvidePower(IBlockState state){return waypoint;}
    public int getWeakPower(IBlockState state,net.minecraft.world.IBlockAccess world,BlockPos pos,EnumFacing side){TileEntity raw=world.getTileEntity(pos);return com.vandorlabs.redstone.TransmitterSlots.localRedstone(world,pos)&&raw instanceof TileEntityWaypoint&&((TileEntityWaypoint)raw).hasLocalRedstoneSignal()?15:0;}
    public void updateTick(World world,BlockPos pos,IBlockState state,java.util.Random random){TileEntity raw=world.getTileEntity(pos);if(raw instanceof TileEntityWaypoint)((TileEntityWaypoint)raw).endPulse();else if(raw instanceof TileEntityAutopilot)com.vandorlabs.vehicle.VehicleAutopilot.depart((TileEntityAutopilot)raw);}
    private net.minecraft.item.ItemStack recoveryItem(TileEntity tile){net.minecraft.item.ItemStack stack=new net.minecraft.item.ItemStack(this);if(tile instanceof com.vandorlabs.rack.LegacyAutopilotRecovery){net.minecraft.nbt.NBTTagCompound raw=tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound());for(String key:new String[]{"id","x","y","z"})raw.removeTag(key);stack.setTagInfo("BlockEntityTag",raw);}return stack;}
    @Override public net.minecraft.item.ItemStack getPickBlock(IBlockState state,net.minecraft.util.math.RayTraceResult hit,World world,BlockPos pos,EntityPlayer player){return recoveryItem(world.getTileEntity(pos));}
    @Override public void getDrops(net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> drops,net.minecraft.world.IBlockAccess world,BlockPos pos,IBlockState state,int fortune){TileEntity tile=world.getTileEntity(pos);if(tile instanceof com.vandorlabs.rack.LegacyAutopilotRecovery)drops.add(recoveryItem(tile));else super.getDrops(drops,world,pos,state,fortune);}
    @Override public boolean removedByPlayer(IBlockState state,World world,BlockPos pos,EntityPlayer player,boolean willHarvest){if(!world.isRemote&&!willHarvest&&world.getTileEntity(pos) instanceof com.vandorlabs.rack.LegacyAutopilotRecovery)spawnAsEntity(world,pos,recoveryItem(world.getTileEntity(pos)));return super.removedByPlayer(state,world,pos,player,willHarvest);}
    @Override public void harvestBlock(World world,EntityPlayer player,BlockPos pos,IBlockState state,TileEntity tile,net.minecraft.item.ItemStack tool){if(tile instanceof com.vandorlabs.rack.LegacyAutopilotRecovery)spawnAsEntity(world,pos,recoveryItem(tile));else super.harvestBlock(world,player,pos,state,tile,tool);}
}
