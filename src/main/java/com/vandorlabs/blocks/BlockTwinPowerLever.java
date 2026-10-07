package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Twin-arm binary controls sharing Industrial Power Lever interaction and channels. */
public final class BlockTwinPowerLever extends BlockIndustrialLever {
    private final int offset;
    public boolean isLegacyLarge(){return offset==6;}
    public static final net.minecraftforge.common.property.IUnlistedProperty<Integer> SIZE=new net.minecraftforge.common.property.IUnlistedProperty<Integer>(){
        public String getName(){return "power_lever_size";}public boolean isValid(Integer value){return value!=null && value>=0 && value<=1;}
        public Class<Integer> getType(){return Integer.class;}public String valueToString(Integer value){return value.toString();}
    };
    @Override protected net.minecraft.block.state.BlockStateContainer createBlockState(){
        return new net.minecraftforge.common.property.ExtendedBlockState(this,new net.minecraft.block.properties.IProperty[]{FACING,POWERED,FLOOR},new net.minecraftforge.common.property.IUnlistedProperty[]{SIZE});
    }
    public int size(IBlockAccess world,BlockPos pos){
        net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);
        return tile instanceof com.vandorlabs.tiles.TileEntityRedstoneChannel?((com.vandorlabs.tiles.TileEntityRedstoneChannel)tile).getPowerLeverSize():offset/6;
    }
    @Override public IBlockState getExtendedState(IBlockState state,IBlockAccess world,BlockPos pos){return ((net.minecraftforge.common.property.IExtendedBlockState)state).withProperty(SIZE,size(world,pos));}
    private net.minecraft.item.ItemStack configured(IBlockAccess world,BlockPos pos){
        net.minecraft.item.ItemStack stack=new net.minecraft.item.ItemStack(net.minecraft.block.Block.getBlockFromName("vandorlabs:small_power_lever"));
        net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();tag.setInteger("PowerLeverSize",size(world,pos));
        net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);
        if(tile instanceof com.vandorlabs.tiles.TileEntityRedstoneChannel){tag.setInteger("Channel",((com.vandorlabs.tiles.TileEntityRedstoneChannel)tile).getRedstoneChannel());com.vandorlabs.redstone.ChannelData.write(tag,((com.vandorlabs.tiles.TileEntityRedstoneChannel)tile).getRedstoneChannels());}
        stack.setTagInfo("RedstoneChannelSettings",tag);return stack;
    }
    @Override public net.minecraft.item.ItemStack getPickBlock(IBlockState state,net.minecraft.util.math.RayTraceResult target,net.minecraft.world.World world,BlockPos pos,net.minecraft.entity.player.EntityPlayer player){return configured(world,pos);}
    @Override public void getDrops(net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> drops,IBlockAccess world,BlockPos pos,IBlockState state,int fortune){drops.add(configured(world,pos));}
    @Override public boolean removedByPlayer(IBlockState state,net.minecraft.world.World world,BlockPos pos,net.minecraft.entity.player.EntityPlayer player,boolean willHarvest){return willHarvest || super.removedByPlayer(state,world,pos,player,false);}
    @Override public void harvestBlock(net.minecraft.world.World world,net.minecraft.entity.player.EntityPlayer player,BlockPos pos,IBlockState state,net.minecraft.tileentity.TileEntity tile,net.minecraft.item.ItemStack tool){super.harvestBlock(world,player,pos,state,tile,tool);world.setBlockToAir(pos);}
    @Override public void onBlockPlacedBy(net.minecraft.world.World world,BlockPos pos,IBlockState state,net.minecraft.entity.EntityLivingBase placer,net.minecraft.item.ItemStack stack){
        super.onBlockPlacedBy(world,pos,state,placer,stack);
        net.minecraft.nbt.NBTTagCompound tag=stack.getSubCompound("RedstoneChannelSettings");
        if(!world.isRemote && tag!=null && tag.hasKey("PowerLeverSize",3) && world.getTileEntity(pos) instanceof com.vandorlabs.tiles.TileEntityRedstoneChannel)
            ((com.vandorlabs.tiles.TileEntityRedstoneChannel)world.getTileEntity(pos)).setPowerLeverSize(tag.getInteger("PowerLeverSize"));
    }
    private static final AxisAlignedBB[] BOUNDS={
        new AxisAlignedBB(0.2187500000,0.1685436963,0.4529624145,0.7812500000,0.7216638042,1.0000000000),
        new AxisAlignedBB(0.2187500000,0.1685436963,0.0000000000,0.7812500000,0.7216638042,0.5470375855),
        new AxisAlignedBB(0.0000000000,0.1685436963,0.2187500000,0.5470375855,0.7216638042,0.7812500000),
        new AxisAlignedBB(0.4529624145,0.1685436963,0.2187500000,1.0000000000,0.7216638042,0.7812500000),
        new AxisAlignedBB(0.2187500000,0.0000000000,0.1685436963,0.7812500000,0.5470375855,0.7216638042),
        new AxisAlignedBB(0.2783361958,0.0000000000,0.2187500000,0.8314563037,0.5470375855,0.7812500000),
        new AxisAlignedBB(0.0937500000,0.1022524356,0.4147524356,0.9062500000,0.8977475644,1.0000000000),
        new AxisAlignedBB(0.0937500000,0.1022524356,0.0000000000,0.9062500000,0.8977475644,0.5852475644),
        new AxisAlignedBB(0.0000000000,0.1022524356,0.0937500000,0.5852475644,0.8977475644,0.9062500000),
        new AxisAlignedBB(0.4147524356,0.1022524356,0.0937500000,1.0000000000,0.8977475644,0.9062500000),
        new AxisAlignedBB(0.0937500000,0.0000000000,0.1022524356,0.9062500000,0.5852475644,0.8977475644),
        new AxisAlignedBB(0.1022524356,0.0000000000,0.0937500000,0.8977475644,0.5852475644,0.9062500000),
    };
    public BlockTwinPowerLever(String name,boolean large){super(name);offset=large?6:0;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){
        EnumFacing face=state.getValue(FACING);
        int index=state.getValue(FLOOR)?(face.getAxis()==EnumFacing.Axis.X?5:4):face==EnumFacing.NORTH?0:face==EnumFacing.SOUTH?1:face==EnumFacing.EAST?2:3;
        return BOUNDS[size(world,pos)*6+index];
    }
}
