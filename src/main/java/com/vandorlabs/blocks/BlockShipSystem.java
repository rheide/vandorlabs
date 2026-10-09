package com.vandorlabs.blocks;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.shipsystems.ShipSystemMesh;
import com.vandorlabs.tiles.TileEntityShipSystem;
import net.minecraft.block.*;
import net.minecraft.block.material.*;
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

/** One item places a complete, non-pushable decorative machine footprint. */
public final class BlockShipSystem extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public final String kind;
    private final int[] size;
    public BlockShipSystem(String id) {
        super(Material.IRON);
        kind = id;
        size = ShipSystemMesh.DIMENSIONS.get(id);
        setRegistryName(VandorLabs.MODID, id);
        setUnlocalizedName(id);
        setCreativeTab(VandorLabs.VANDOR_LABS_TAB);
        setHardness(3F);
        setResistance(10F);
        setSoundType(SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }
    protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3)); }
    public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    public IBlockState withRotation(IBlockState state, Rotation r) { return state.withProperty(FACING, r.rotate(state.getValue(FACING))); }
    public IBlockState withMirror(IBlockState state, Mirror m) { return withRotation(state, m.toRotation(state.getValue(FACING))); }
    public boolean hasTileEntity(IBlockState state) { return true; }
    public TileEntity createTileEntity(World world, IBlockState state) { return new TileEntityShipSystem(); }
    public boolean isOpaqueCube(IBlockState state) { return false; }
    public boolean isFullCube(IBlockState state) { return false; }
    public BlockFaceShape getBlockFaceShape(IBlockAccess w, IBlockState s, BlockPos p, EnumFacing side) { return BlockFaceShape.UNDEFINED; }
    public EnumPushReaction getMobilityFlag(IBlockState s) { return EnumPushReaction.BLOCK; }
    public EnumBlockRenderType getRenderType(IBlockState s) { return EnumBlockRenderType.ENTITYBLOCK_ANIMATED; }
    public IBlockState getStateForPlacement(World w, BlockPos p, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase player) {
        return getDefaultState().withProperty(FACING, player.getHorizontalFacing().getOpposite());
    }
    public List<BlockPos> cells(BlockPos origin, EnumFacing facing) {
        List<BlockPos> result = new ArrayList<>();
        for (int x = 0; x < size[0]; x++) for (int z = 0; z < size[1]; z++) for (int y = 0; y < size[2]; y++)
            result.add(origin.offset(facing.rotateY(), x).offset(facing.getOpposite(), z).up(y));
        return result;
    }
    public boolean fits(World w, BlockPos origin, EnumFacing facing, EntityPlayer player, EnumFacing side, ItemStack stack) {
        for (BlockPos p : cells(origin, facing)) {
            if (p.getY() < 0 || p.getY() >= w.getHeight() || !w.getWorldBorder().contains(p)
                    || !w.isBlockLoaded(p) || !player.canPlayerEdit(p, side, stack)
                    || !w.getBlockState(p).getBlock().isReplaceable(w, p)) return false;
        }
        for (AxisAlignedBB box : ShipSystemMesh.boxes(kind, facing, origin))
            if (!w.checkNoEntityCollision(box)) return false;
        return true;
    }
    public void onBlockPlacedBy(World w, BlockPos p, IBlockState s, EntityLivingBase entity, ItemStack stack) {
        if (w.isRemote) return;
        for (BlockPos cell : cells(p, s.getValue(FACING))) {
            if (!cell.equals(p)) w.setBlockState(cell, s, 2);
            TileEntityShipSystem tile = TileEntityShipSystem.at(w, cell);
            if (tile != null) { tile.anchor = p; tile.sync(); }
        }
        for (BlockPos cell : cells(p, s.getValue(FACING))) w.notifyNeighborsOfStateChange(cell, this, false);
    }
    public void breakBlock(World w, BlockPos p, IBlockState state) {
        TileEntityShipSystem tile = TileEntityShipSystem.at(w, p);
        if (!w.isRemote && tile != null) tile.removeAssembly(state);
        super.breakBlock(w, p, state);
    }
    public void harvestBlock(World w, EntityPlayer player, BlockPos p, IBlockState state, TileEntity tile, ItemStack tool) {
        if (tile instanceof TileEntityShipSystem && !((TileEntityShipSystem)tile).removing)
            spawnAsEntity(w, p, new ItemStack(this));
    }
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess w, BlockPos p, IBlockState s, int fortune) {
        TileEntityShipSystem t = TileEntityShipSystem.at(w, p);
        if (t == null || t.anchor == null || t.anchor.equals(p)) drops.add(new ItemStack(this));
    }
    public AxisAlignedBB getCollisionBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return NULL_AABB; }
    public void addCollisionBoxToList(IBlockState s, World w, BlockPos p, AxisAlignedBB query, List<AxisAlignedBB> out, Entity entity, boolean actual) {
        for (AxisAlignedBB box : localBoxes(w, p)) if (box.intersects(query)) out.add(box);
    }
    private List<AxisAlignedBB> localBoxes(World w, BlockPos p) {
        TileEntityShipSystem t = TileEntityShipSystem.at(w, p);
        if (t == null) return Collections.emptyList();
        BlockPos owner = t.anchor == null ? p : t.anchor;
        List<AxisAlignedBB> result = new ArrayList<>();
        AxisAlignedBB cell = new AxisAlignedBB(p);
        for (AxisAlignedBB box : ShipSystemMesh.boxes(kind, w.getBlockState(p).getValue(FACING), owner))
            if (box.intersects(cell)) result.add(box.intersect(cell));
        return result;
    }
    public RayTraceResult collisionRayTrace(IBlockState s, World w, BlockPos p, Vec3d start, Vec3d end) {
        RayTraceResult nearest = null;
        for (AxisAlignedBB box : localBoxes(w, p)) {
            RayTraceResult hit = box.calculateIntercept(start, end);
            if (hit != null && (nearest == null || start.squareDistanceTo(hit.hitVec) < start.squareDistanceTo(nearest.hitVec)))
                nearest = new RayTraceResult(hit.hitVec, hit.sideHit, p);
        }
        return nearest;
    }
    public static final class SystemItem extends ItemBlock {
        public SystemItem(BlockShipSystem block) { super(block); }
        public EnumActionResult onItemUse(EntityPlayer player, World w, BlockPos p, EnumHand hand, EnumFacing side, float x, float y, float z) {
            BlockPos dest = w.getBlockState(p).getBlock().isReplaceable(w, p) ? p : p.offset(side);
            if (!((BlockShipSystem)block).fits(w, dest, player.getHorizontalFacing().getOpposite(), player, side, player.getHeldItem(hand))) return EnumActionResult.FAIL;
            return super.onItemUse(player, w, p, hand, side, x, y, z);
        }
    }
}
