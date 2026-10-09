package com.vandorlabs.blocks;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityCanopy;

import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

import java.util.*;

/** Each occupied cell points to its constituent anchor, which may join one adjacent anchor. */
public final class BlockCanopy extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public final String kind;
    public final int width, length, height;

    public BlockCanopy(String id) {
        super(Material.GLASS);
        kind = id;
        int[] size =
                com.vandorlabs.canopy.CanopyMesh.DIMENSIONS.get(
                        id.equals("regular_canopy_glass") ? "regular_1x1_00" : id);
        width = size[0];
        length = size[1];
        height = size[2];
        setRegistryName(VandorLabs.MODID, id);
        setUnlocalizedName(id);
        setCreativeTab(VandorLabs.VANDOR_LABS_TAB);
        setHardness(1.5F);
        setSoundType(SoundType.GLASS);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    public boolean opening() {
        return kind.startsWith("opening_");
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState s) {
        return s.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public boolean hasTileEntity(IBlockState s) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World w, IBlockState s) {
        return new TileEntityCanopy();
    }

    @Override
    public net.minecraft.block.material.EnumPushReaction getMobilityFlag(IBlockState s) {
        return net.minecraft.block.material.EnumPushReaction.BLOCK;
    }

    @Override
    public net.minecraft.block.state.BlockFaceShape getBlockFaceShape(
            IBlockAccess w, IBlockState s, BlockPos p, EnumFacing side) {
        return net.minecraft.block.state.BlockFaceShape.UNDEFINED;
    }

    @Override
    public boolean isOpaqueCube(IBlockState s) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState s) {
        return false;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState s) {
        return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public IBlockState getStateForPlacement(
            World w,
            BlockPos p,
            EnumFacing side,
            float x,
            float y,
            float z,
            int meta,
            EntityLivingBase player) {
        return getDefaultState().withProperty(FACING, player.getHorizontalFacing());
    }

    public List<BlockPos> cells(BlockPos p, EnumFacing f) {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = 0; x < width; x++)
            for (int z = 0; z < length; z++)
                for (int y = 0; y < height; y++)
                    cells.add(p.offset(f.rotateY(), x).offset(f.getOpposite(), z).up(y));
        return cells;
    }

    public boolean fits(World w, BlockPos p, EnumFacing f) {
        for (BlockPos cell : cells(p, f))
            if (cell.getY() < 0
                    || cell.getY() >= w.getHeight()
                    || !w.getWorldBorder().contains(cell)
                    || !w.isBlockLoaded(cell)
                    || !w.getBlockState(cell).getBlock().isReplaceable(w, cell)) return false;
        return true;
    }

    @Override
    public void onBlockPlacedBy(
            World w, BlockPos p, IBlockState s, EntityLivingBase entity, ItemStack stack) {
        if (w.isRemote) return;
        for (BlockPos cell : cells(p, s.getValue(FACING))) {
            if (!cell.equals(p)) w.setBlockState(cell, s, 2);
            TileEntityCanopy t = TileEntityCanopy.at(w, cell);
            if (t != null) {
                t.anchor = p;
                t.sync();
            }
        }
        TileEntityCanopy t = TileEntityCanopy.at(w, p);
        if (t != null) t.tryPair();
        w.notifyNeighborsOfStateChange(p, this, false);
    }

    @Override
    public boolean onBlockActivated(
            World w,
            BlockPos p,
            IBlockState s,
            EntityPlayer player,
            EnumHand hand,
            EnumFacing side,
            float x,
            float y,
            float z) {
        if (!opening()) return false;
        if (!w.isRemote) {
            TileEntityCanopy t = TileEntityCanopy.at(w, p);
            if (t != null) t.owner().toggle();
        }
        return true;
    }

    @Override
    public void breakBlock(World w, BlockPos p, IBlockState state) {
        TileEntityCanopy t = TileEntityCanopy.at(w, p);
        if (t != null && !w.isRemote) t.removeAssembly();
        super.breakBlock(w, p, state);
    }

    @Override
    public void harvestBlock(
            World w,
            EntityPlayer player,
            BlockPos p,
            IBlockState s,
            TileEntity tile,
            ItemStack tool) {
        spawnAsEntity(w, p, new ItemStack(this));
    }

    @Override
    public void getDrops(
            net.minecraft.util.NonNullList<ItemStack> drops,
            IBlockAccess w,
            BlockPos p,
            IBlockState state,
            int fortune) {
        TileEntityCanopy t =
                w.getTileEntity(p) instanceof TileEntityCanopy
                        ? (TileEntityCanopy) w.getTileEntity(p)
                        : null;
        if (t == null || t.anchor == null || p.equals(t.anchor)) drops.add(new ItemStack(this));
    }

    @Override
    public void addCollisionBoxToList(
            IBlockState s,
            World w,
            BlockPos p,
            AxisAlignedBB query,
            List<AxisAlignedBB> boxes,
            Entity e,
            boolean actual) {
        /* Loaded-chunk event supplies surfaces once per owner, including moved shells. */
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(
            IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    public boolean isPassable(IBlockAccess world, BlockPos pos) {
        TileEntityCanopy tile = TileEntityCanopy.at(world, pos);
        return opening() && tile != null && tile.owner().progress == 1;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) {
        return FULL_BLOCK_AABB;
    }

    @Override
    public RayTraceResult collisionRayTrace(
            IBlockState s, World w, BlockPos p, Vec3d start, Vec3d end) {
        TileEntityCanopy t = TileEntityCanopy.at(w, p);
        return t == null ? null : t.owner().trace(start, end);
    }

    public static final class CanopyItem extends ItemBlock {
        public CanopyItem(BlockCanopy b) {
            super(b);
        }

        @Override
        public EnumActionResult onItemUse(
                EntityPlayer player,
                World w,
                BlockPos p,
                EnumHand hand,
                EnumFacing side,
                float x,
                float y,
                float z) {
            BlockPos dest = w.getBlockState(p).getBlock().isReplaceable(w, p) ? p : p.offset(side);
            BlockCanopy b = (BlockCanopy) block;
            for (BlockPos cell : b.cells(dest, player.getHorizontalFacing()))
                if (!player.canPlayerEdit(cell, side, player.getHeldItem(hand)))
                    return EnumActionResult.FAIL;
            if (!b.fits(w, dest, player.getHorizontalFacing())) return EnumActionResult.FAIL;
            return super.onItemUse(player, w, p, hand, side, x, y, z);
        }
    }
}
