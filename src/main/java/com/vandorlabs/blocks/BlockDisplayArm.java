package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.tiles.TileEntityDisplayArm;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import java.util.List;
import javax.annotation.Nullable;

/** One screen/console block with a six-face mounting base and an independent display pose. */
public final class BlockDisplayArm extends BlockProgrammableConsole {
    public static final String NAME = "programmable_display_arm";

    public BlockDisplayArm() { super(NAME); setLightOpacity(0); }

    @Override public TileEntity createNewTileEntity(World world, int metadata) {
        TileEntityDisplayArm tile = new TileEntityDisplayArm();
        tile.setHousingTexture(com.vandorlabs.tiles.ScreenHousingTextures.screenIndex("door_inner"));
        tile.initializeDisplayArtwork(this);
        return tile;
    }

    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float x, float y, float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override public IBlockState getStateFromMeta(int meta) {
        EnumFacing face = EnumFacing.getFront(meta & 7);
        return getDefaultState().withProperty(FACING, face.getAxis().isHorizontal() ? face : EnumFacing.NORTH);
    }

    public static boolean openSettings(EntityPlayer player, World world, BlockPos pos) {
        if (!ConfigurationAccess.canConfigure(player) || !ConfigurationAccess.canEditDialog(player, world, pos)
                || !(world.getTileEntity(pos) instanceof TileEntityDisplayArm)) return false;
        if (!world.isRemote) player.openGui(VandorLabs.instance, GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR,
                world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
            EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (hand != EnumHand.MAIN_HAND) return false;
        if (player.getHeldItem(hand).getItem() == com.vandorlabs.items.ModItems.CONFIGURIZER
                || player.isSneaking()) return openSettings(player, world, pos);
        if (RedstoneScreenInteractions.activate(world, pos, state, player, hand)) return true;
        return openSettings(player, world, pos);
    }

    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos,
            net.minecraft.block.Block changed, BlockPos from) {
        super.neighborChanged(state, world, pos, changed, from);
        if (world.isRemote) return;
        TileEntity raw = world.getTileEntity(pos);
        if (!(raw instanceof TileEntityDisplayArm)) return;
        if (!((TileEntityDisplayArm) raw).hasMount()) return;
        BlockPos support = pos.offset(((TileEntityDisplayArm) raw).getMountFace().getOpposite());
        if (support.equals(from) && world.isBlockLoaded(support) && world.isAirBlock(support)) {
            spawnAsEntity(world, pos, createConfiguredDrop(raw));
            world.setBlockToAir(pos);
        }
    }

    private DisplayArmShape shape(IBlockAccess world, BlockPos pos, IBlockState state) {
        TileEntity raw = world.getTileEntity(pos);
        return raw instanceof TileEntityDisplayArm ? ((TileEntityDisplayArm) raw).shape()
                : new DisplayArmShape(com.vandorlabs.display.DisplayArmGeometry.create(
                        EnumFacing.UP.getIndex(), state.getValue(FACING).getIndex(),
                        com.vandorlabs.display.DisplayArmSettings.DEFAULT));
    }

    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return shape(world, pos, state).bounds;
    }

    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return shape(world, pos, state).bounds;
    }

    @Override public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
            AxisAlignedBB query, List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actual) {
        shape(world, pos, state).addCollisions(pos, query, boxes);
    }

    @Override public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos,
            Vec3d start, Vec3d end) {
        return shape(world, pos, state).trace(pos, start, end);
    }

    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state,
            BlockPos pos, EnumFacing side) { return BlockFaceShape.UNDEFINED; }

    @Override public ItemStack createConfiguredDrop(@Nullable TileEntity raw) {
        ItemStack stack = super.createConfiguredDrop(raw);
        if (raw instanceof TileEntityDisplayArm) {
            NBTTagCompound data = stack.getSubCompound("BlockEntityTag");
            data.setInteger(TileEntityDisplayArm.ITEM_FACING, ((TileEntityDisplayArm) raw).getScreenFacing().getIndex());
            // Placement chooses a new support face, even for a mined/configured item.
            data.getCompoundTag(TileEntityDisplayArm.SETTINGS).removeTag("Mount");
        }
        return stack;
    }
}
