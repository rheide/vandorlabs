package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityTurntable;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Platform block of a Turntable. Its six-way FACING points at the volume the
 * controller turns, so a floor block rotates what is above it, a ceiling block
 * rotates what is below it, and a wall block turns the clock face in front.
 */
public class BlockTurntable extends BlockVandor {
    public static final net.minecraft.block.properties.PropertyDirection FACING = CachedProperties.direction("facing");

    public BlockTurntable() {
        super("turntable");
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP));
    }

    @Override protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getFront(meta & 7));
    }

    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float hitX,
            float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, side);
    }

    @Override public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }

    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileEntityTurntable(); }

    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer,
            ItemStack stack) {
        if (world.isRemote) return;
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityTurntable) ((TileEntityTurntable) tile).markDirty();
    }

    @Override public void updateTick(World world, BlockPos pos, IBlockState state, Random random) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityTurntable) ((TileEntityTurntable) tile).validateOwnership();
    }

    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityTurntable) {
                BlockPos controller = ((TileEntityTurntable) tile).controller;
                TileEntity owner = world.getTileEntity(controller);
                if (owner instanceof com.vandorlabs.tiles.TileEntityTurntableController)
                    ((com.vandorlabs.tiles.TileEntityTurntableController) owner).onPartRemoved(pos);
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return false;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntityTurntable)) return false;
        BlockPos controller = ((TileEntityTurntable) tile).controller;
        TileEntity owner = world.getTileEntity(controller);
        if (!(owner instanceof com.vandorlabs.tiles.TileEntityTurntableController)) return false;
        if (!com.vandorlabs.items.ConfigurationAccess.canEditDialog(player, world, controller)) return false;
        if (!world.isRemote) player.openGui(VandorLabs.instance, GuiHandler.GUI_TURNTABLE_CONTROLLER,
                world, controller.getX(), controller.getY(), controller.getZ());
        return true;
    }
}
