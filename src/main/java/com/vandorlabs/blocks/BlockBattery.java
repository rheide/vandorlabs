package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityBattery;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class BlockBattery extends BlockVandor {
    public static final net.minecraft.block.properties.PropertyEnum<EnumFacing> FACING=BlockAnimatedScreenSelector.FACING;
    public BlockBattery(){super("battery_block");setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH));}
    @Override protected net.minecraft.block.state.BlockStateContainer createBlockState(){return new net.minecraft.block.state.BlockStateContainer(this,FACING);}
    // Retain the four horizontal metadata values used by batteries in existing worlds.
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(FACING,meta==4?EnumFacing.UP:meta==5?EnumFacing.DOWN:EnumFacing.getHorizontal(meta));}
    @Override public int getMetaFromState(IBlockState state){EnumFacing face=state.getValue(FACING);return face==EnumFacing.UP?4:face==EnumFacing.DOWN?5:face.getHorizontalIndex();}
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing face,float x,float y,float z,int meta,net.minecraft.entity.EntityLivingBase player){return getDefaultState().withProperty(FACING,DirectionalPlacement.towardView(player));}
    @Override public IBlockState withRotation(IBlockState state,Rotation rotation){return state.withProperty(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public IBlockState withMirror(IBlockState state,Mirror mirror){return state.withProperty(FACING,mirror.mirror(state.getValue(FACING)));}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public boolean canProvidePower(IBlockState state){return true;}
    @Override public int getWeakPower(IBlockState state,net.minecraft.world.IBlockAccess world,BlockPos pos,EnumFacing side){return com.vandorlabs.redstone.StorageChannelOutput.localLevel(world.getTileEntity(pos));}
    @Override public int getStrongPower(IBlockState state,net.minecraft.world.IBlockAccess world,BlockPos pos,EnumFacing side){return getWeakPower(state,world,pos,side);}
    @Override public EnumBlockRenderType getRenderType(IBlockState state){return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityBattery();}
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,net.minecraft.entity.EntityLivingBase player,net.minecraft.item.ItemStack stack){
        super.onBlockPlacedBy(world,pos,state,player,stack);if(world.isRemote)return;
        if(!stack.hasTagCompound()||!stack.getTagCompound().hasKey("BlockEntityTag",10)){TileEntity tile=world.getTileEntity(pos);if(tile instanceof TileEntityBattery)((TileEntityBattery)tile).place(state.getValue(FACING));}
    }
    @Override public void neighborChanged(IBlockState state,World world,BlockPos pos,net.minecraft.block.Block block,BlockPos from){if(!world.isRemote&&world.isBlockLoaded(pos)){TileEntity tile=world.getTileEntity(pos);if(tile instanceof TileEntityBattery)((TileEntityBattery)tile).neighborChanged();}}
    private net.minecraft.item.ItemStack drop(TileEntity tile){
        net.minecraft.item.ItemStack stack=new net.minecraft.item.ItemStack(this);
        if(tile instanceof TileEntityBattery){net.minecraft.nbt.NBTTagCompound tag=tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound());tag.removeTag("id");tag.removeTag("x");tag.removeTag("y");tag.removeTag("z");stack.setTagInfo("BlockEntityTag",tag);}return stack;
    }
    @Override public void getDrops(net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> drops,net.minecraft.world.IBlockAccess world,BlockPos pos,IBlockState state,int fortune){drops.add(drop(world.getTileEntity(pos)));}
    @Override public boolean removedByPlayer(IBlockState state,World world,BlockPos pos,EntityPlayer player,boolean willHarvest){
        if(!world.isRemote&&!willHarvest){TileEntity tile=world.getTileEntity(pos);if(tile instanceof TileEntityBattery&&((TileEntityBattery)tile).inert())spawnAsEntity(world,pos,drop(tile));}
        return super.removedByPlayer(state,world,pos,player,willHarvest);
    }
    @Override public void harvestBlock(World world,EntityPlayer player,BlockPos pos,IBlockState state,TileEntity tile,net.minecraft.item.ItemStack tool){spawnAsEntity(world,pos,drop(tile));}
    @Override public net.minecraft.item.ItemStack getPickBlock(IBlockState state,net.minecraft.util.math.RayTraceResult hit,World world,BlockPos pos,EntityPlayer player){return drop(world.getTileEntity(pos));}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){
        if(player.getHeldItem(hand).getItem() instanceof net.minecraft.item.ItemBlock)return false;
        return com.vandorlabs.ports.ResourcePorts.open(player,world,pos);
    }
}
