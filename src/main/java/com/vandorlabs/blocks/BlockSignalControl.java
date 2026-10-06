package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntitySignalControl;
import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Supplied four-state controls reuse mounted switch placement and channel configuration. */
public class BlockSignalControl extends BlockVandorSwitch {
    public static final PropertyInteger LEVEL=PropertyInteger.create("level",0,3);
    private final String kind;
    private final int detail;
    public BlockSignalControl(String id,String kind,int detail){super(id);this.kind=kind;this.detail=detail;setDefaultState(getDefaultState().withProperty(LEVEL,0));}
    public String controlKind(){return kind;}
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,ON,ROTATION,LEVEL);}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntitySignalControl();}
    @Override public IBlockState getActualState(IBlockState state,IBlockAccess world,BlockPos pos){
        state=super.getActualState(state,world,pos);TileEntity tile=world.getTileEntity(pos);
        return state.withProperty(LEVEL,tile instanceof TileEntitySignalControl?((TileEntitySignalControl)tile).getStep():0);
    }
    @Override public boolean canPlaceBlockOnSide(World world,BlockPos pos,EnumFacing side){return kind.equals("control_block") || super.canPlaceBlockOnSide(world,pos,side);}
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer,EnumHand hand){
        if(kind.equals("control_block"))return getDefaultState().withProperty(FACING,placer.getHorizontalFacing().getOpposite());
        return super.getStateForPlacement(world,pos,side,x,y,z,meta,placer,hand);
    }
    @Override public void neighborChanged(IBlockState state,World world,BlockPos pos,Block block,BlockPos from){if(!kind.equals("control_block"))super.neighborChanged(state,world,pos,block,from);}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing facing,float x,float y,float z){
        if(player.isSneaking())return super.onBlockActivated(world,pos,state,player,hand,facing,x,y,z);
        if(!world.isRemote && world.getTileEntity(pos) instanceof TileEntitySignalControl){
            TileEntitySignalControl control=(TileEntitySignalControl)world.getTileEntity(pos);control.setStep((control.getStep()+1)%4);
            world.playSound(null,pos,net.minecraft.init.SoundEvents.BLOCK_LEVER_CLICK,SoundCategory.BLOCKS,.3F,.5F+control.getStep()*.1F);
        }
        return true;
    }
    @Override public int getLightValue(IBlockState state,IBlockAccess world,BlockPos pos){return 0;}
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return kind.equals("control_block")?FULL_BLOCK_AABB:NULL_AABB;}
    @Override public boolean isFullCube(IBlockState state){return kind!=null && kind.equals("control_block");}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){
        if(kind.equals("control_block"))return FULL_BLOCK_AABB;
        EnumFacing facing=state.getValue(FACING);
        int rotation=super.getActualState(state,world,pos).getValue(ROTATION);
        String mount=facing.getName();
        if(facing.getAxis()==EnumFacing.Axis.Y)mount=(facing==EnumFacing.UP?"floor":"ceiling")+(rotation%2==0?"_z":"_x");
        AxisAlignedBB box=SignalControlBounds.get(detail,kind,mount);
        return facing.getAxis()==EnumFacing.Axis.Y && rotation>=2?new AxisAlignedBB(1-box.maxX,box.minY,1-box.maxZ,1-box.minX,box.maxY,1-box.minZ):box;
    }
    @Override public ItemStack getPickBlock(IBlockState state,RayTraceResult target,World world,BlockPos pos,EntityPlayer player){
        ItemStack stack=new ItemStack(this);TileEntity tile=world.getTileEntity(pos);
        if(tile instanceof TileEntitySignalControl)stack.setTagInfo("RedstoneChannelSettings",((TileEntitySignalControl)tile).configuration());
        return stack;
    }
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,EntityLivingBase placer,ItemStack stack){
        NBTTagCompound settings=stack.getSubCompound("RedstoneChannelSettings");TileEntity tile=world.getTileEntity(pos);
        if(!world.isRemote && settings!=null && tile instanceof TileEntitySignalControl)((TileEntitySignalControl)tile).configureLimits(settings.getInteger("LowLimit"),settings.getInteger("HighLimit"));
        super.onBlockPlacedBy(world,pos,state,placer,stack);
    }
}
