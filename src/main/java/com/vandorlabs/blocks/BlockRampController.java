package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityRampController;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Its facing is the scan direction, away from the player on placement. */
public class BlockRampController extends BlockVandorDirectional {
    public static final net.minecraft.block.properties.PropertyBool ACTIVE=net.minecraft.block.properties.PropertyBool.create("active");
    public BlockRampController() {
        super("programmable_ramp");
        setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(ACTIVE,false));
    }
    @Override protected net.minecraft.block.state.BlockStateContainer createBlockState() {
        return new net.minecraft.block.state.BlockStateContainer(this,FACING,ACTIVE);
    }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING,EnumFacing.getHorizontal(meta&3)).withProperty(ACTIVE,(meta&4)!=0);
    }
    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(ACTIVE)?4:0);
    }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileEntityRampController(); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, int meta, net.minecraft.entity.EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING,placer.getHorizontalFacing());
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND || !player.isSneaking()
                || !player.capabilities.isCreativeMode) return false;
        if (!world.isRemote) player.openGui(VandorLabs.instance,
                GuiHandler.GUI_RAMP_CONTROLLER,world,pos.getX(),pos.getY(),pos.getZ());
        return true;
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        TileEntity te=world.getTileEntity(pos);
        if (te instanceof TileEntityRampController) ((TileEntityRampController)te).updatePower();
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te=world.getTileEntity(pos);
        if (!world.isRemote && te instanceof TileEntityRampController) ((TileEntityRampController)te).recover(true);
        super.breakBlock(world,pos,state);
    }
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,
            net.minecraft.entity.EntityLivingBase placer,net.minecraft.item.ItemStack stack) {
        TileEntity te=world.getTileEntity(pos);
        if (!world.isRemote && te instanceof TileEntityRampController) {
            TileEntityRampController ramp = (TileEntityRampController) te;
            if (placer instanceof EntityPlayer) ramp.setOwner((EntityPlayer) placer);
            NBTTagCompound settings = stack.hasTagCompound()
                    && stack.getTagCompound().hasKey("RampSettings", 10)
                    ? stack.getTagCompound().getCompoundTag("RampSettings") : null;
            if (settings != null && placer instanceof EntityPlayer) {
                ramp.configureTreads((EntityPlayer) placer,
                        settings.getInteger("StartOffset"), settings.getInteger("EndOffset"),
                        settings.getInteger("TreadPixels"), settings.getBoolean("PowerOn"),
                        settings.getInteger("Speed") == 2, settings.getBoolean("Elevator"),
                        EnumFacing.getHorizontal(settings.getInteger("Direction") & 3),
                        settings.getInteger("TravelAxis"),
                        settings.getBoolean("ExtendSegments"), settings.getInteger("Speed"));
                ramp.setRedstoneChannel(settings.getInteger("Channel"));
            } else ramp.updatePower();
        }
    }
    @Override public ItemStack getPickBlock(IBlockState state, RayTraceResult target,
            World world, BlockPos pos, EntityPlayer player) {
        ItemStack stack = new ItemStack(this);
        TileEntity raw = world.getTileEntity(pos);
        if (raw instanceof TileEntityRampController) {
            TileEntityRampController ramp = (TileEntityRampController) raw;
            NBTTagCompound settings = new NBTTagCompound();
            settings.setInteger("StartOffset", ramp.startOffset);
            settings.setInteger("EndOffset", ramp.endOffset());
            settings.setInteger("TreadPixels", ramp.treadPixels);
            settings.setBoolean("PowerOn", ramp.activateOnPower);
            settings.setBoolean("Elevator", ramp.elevator);
            settings.setInteger("Direction", ramp.rampDirection().getHorizontalIndex());
            settings.setInteger("TravelAxis", ramp.travelAxis);
            settings.setBoolean("ExtendSegments", ramp.extendSegments);
            settings.setInteger("Speed", ramp.speed);
            settings.setInteger("Channel", ramp.getRedstoneChannel());
            stack.setTagInfo("RampSettings", settings);
        }
        return stack;
    }

    @Override public void updateTick(World world,BlockPos pos,IBlockState state,java.util.Random random) {
        TileEntity te=world.getTileEntity(pos);
        if (te instanceof TileEntityRampController) ((TileEntityRampController)te).scheduledTick();
    }
}
