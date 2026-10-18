package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityTurntableController;
import com.vandorlabs.turntable.TurntableGeometry;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

/** Its horizontal facing points at the square of Turntable blocks it controls. */
public class BlockTurntableController extends BlockVandorDirectional {
    public static final net.minecraft.block.properties.PropertyBool ACTIVE =
            net.minecraft.block.properties.PropertyBool.create("active");

    public BlockTurntableController() {
        super("turntable_controller");
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH)
                .withProperty(ACTIVE, false));
    }

    @Override protected net.minecraft.block.state.BlockStateContainer createBlockState() {
        return new net.minecraft.block.state.BlockStateContainer(this, FACING, ACTIVE);
    }

    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3))
                .withProperty(ACTIVE, (meta & 4) != 0);
    }

    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(ACTIVE) ? 4 : 0);
    }

    @Override public IBlockState getActualState(IBlockState state, net.minecraft.world.IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        boolean running = tile instanceof TileEntityTurntableController
                && ((TileEntityTurntableController) tile).isActive();
        return state.withProperty(ACTIVE, running);
    }

    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float hitX,
            float hitY, float hitZ, int meta, net.minecraft.entity.EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing());
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }

    @Override public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityTurntableController();
    }

    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND
                || !com.vandorlabs.items.ConfigurationAccess.canEditDialog(player, world, pos)) return false;
        if (!world.isRemote) player.openGui(VandorLabs.instance, GuiHandler.GUI_TURNTABLE_CONTROLLER,
                world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityTurntableController)
            ((TileEntityTurntableController) tile).updatePower();
    }

    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof TileEntityTurntableController)
            ((TileEntityTurntableController) tile).recover(true);
        super.breakBlock(world, pos, state);
    }

    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer,
            ItemStack stack) {
        TileEntity tile = world.getTileEntity(pos);
        if (world.isRemote || !(tile instanceof TileEntityTurntableController)) return;
        TileEntityTurntableController controller = (TileEntityTurntableController) tile;
        if (placer instanceof EntityPlayer) controller.owner = placer.getUniqueID();
        NBTTagCompound settings = stack.hasTagCompound()
                && stack.getTagCompound().hasKey("TurntableSettings", 10)
                ? stack.getTagCompound().getCompoundTag("TurntableSettings") : null;
        if (settings != null) {
            controller.trigger = Math.max(0, Math.min(2, settings.getInteger("Trigger")));
            controller.height = TurntableGeometry.clampHeight(settings.getInteger("Height"));
            controller.speed = TurntableGeometry.clampSpeed(settings.getDouble("Speed"));
            controller.rotationDirection = settings.getInteger("Direction") == TurntableGeometry.COUNTER_CLOCKWISE
                    ? TurntableGeometry.COUNTER_CLOCKWISE : TurntableGeometry.CLOCKWISE;
            controller.setRedstoneChannels(com.vandorlabs.redstone.ChannelData.read(settings,
                    settings.getInteger("Channel")));
        }
        controller.updatePower();
    }

    @Override public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos,
            EntityPlayer player) {
        ItemStack stack = new ItemStack(this);
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityTurntableController) {
            TileEntityTurntableController controller = (TileEntityTurntableController) tile;
            NBTTagCompound settings = new NBTTagCompound();
            settings.setInteger("Trigger", controller.trigger);
            settings.setInteger("Height", controller.height);
            settings.setDouble("Speed", controller.speed);
            settings.setInteger("Direction", controller.rotationDirection);
            settings.setInteger("Channel", controller.getRedstoneChannel());
            com.vandorlabs.redstone.ChannelData.write(settings, controller.getRedstoneChannels());
            stack.setTagInfo("TurntableSettings", settings);
        }
        return stack;
    }

    @Override public void updateTick(World world, BlockPos pos, IBlockState state, java.util.Random random) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityTurntableController)
            ((TileEntityTurntableController) tile).scheduledTick();
    }
}
