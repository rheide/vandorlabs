package com.vandorlabs.blocks;

import com.vandorlabs.VandorLabs;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Thin observation-glass wall whose perimeter frame joins in one wall plane. */
public class BlockGlassWall extends Block {
    /** True means that edge is exposed and its frame should be rendered. */
    public static final PropertyBool TOP = PropertyBool.create("top");
    public static final PropertyBool BOTTOM = PropertyBool.create("bottom");
    public static final PropertyBool LEFT = PropertyBool.create("left");
    public static final PropertyBool RIGHT = PropertyBool.create("right");
    public static final PropertyBool INNER_TL = PropertyBool.create("inner_tl");
    public static final PropertyBool INNER_TR = PropertyBool.create("inner_tr");
    public static final PropertyBool INNER_BL = PropertyBool.create("inner_bl");
    public static final PropertyBool INNER_BR = PropertyBool.create("inner_br");
    /** False spans world X/Y; true spans world Z/Y. */
    public static final PropertyBool ROTATED = PropertyBool.create("rotated");

    private static final AxisAlignedBB XY_BOX =
            new AxisAlignedBB(0.0D, 0.0D, 6.0D / 16.0D,
                    1.0D, 1.0D, 10.0D / 16.0D);
    private static final AxisAlignedBB ZY_BOX =
            new AxisAlignedBB(6.0D / 16.0D, 0.0D, 0.0D,
                    10.0D / 16.0D, 1.0D, 1.0D);

    public BlockGlassWall(String name) {
        super(Material.GLASS);
        setRegistryName(name);
        setUnlocalizedName(VandorLabs.MODID + "." + name);
        setCreativeTab(VandorLabs.VANDOR_LABS_TAB);
        setHardness(0.5F);
        setResistance(2.0F);
        setSoundType(SoundType.GLASS);
        setLightOpacity(0);
        useNeighborBrightness = true;
        setDefaultState(blockState.getBaseState()
                .withProperty(ROTATED, false)
                .withProperty(TOP, true)
                .withProperty(BOTTOM, true)
                .withProperty(LEFT, true)
                .withProperty(RIGHT, true)
                .withProperty(INNER_TL, false)
                .withProperty(INNER_TR, false)
                .withProperty(INNER_BL, false)
                .withProperty(INNER_BR, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, ROTATED, TOP, BOTTOM, LEFT, RIGHT,
                INNER_TL, INNER_TR, INNER_BL, INNER_BR);
    }

    /** Connection flags are calculated; only the wall plane is persisted. */
    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(ROTATED) ? 1 : 0;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(ROTATED, (meta & 1) != 0);
    }

    @Override
    public int damageDropped(IBlockState state) {
        return 0;
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos,
            EnumFacing side, float hitX, float hitY, float hitZ, int meta,
            EntityLivingBase placer, EnumHand hand) {
        // Extending a panel inherits its plane, including placement against
        // the narrow edge or above/below the existing panel.
        IBlockState clicked = world.getBlockState(pos.offset(side.getOpposite()));
        if (clicked.getBlock() == this) {
            return getDefaultState().withProperty(
                    ROTATED, clicked.getValue(ROTATED));
        }
        // A support beside the intended position exposes a face that lies in
        // the desired wall plane, so its axis would turn the panel 90 degrees.
        // Face the panel toward the placer unless an existing panel explicitly
        // supplied the plane.
        EnumFacing direction = placer.getHorizontalFacing();
        return getDefaultState().withProperty(
                ROTATED, direction.getAxis() == EnumFacing.Axis.X);
    }

    private boolean connects(IBlockAccess world, BlockPos pos,
            IBlockState state) {
        IBlockState other = world.getBlockState(pos);
        return other.getBlock() == this
                && other.getValue(ROTATED).equals(state.getValue(ROTATED));
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world,
            BlockPos pos) {
        EnumFacing left = state.getValue(ROTATED)
                ? EnumFacing.NORTH : EnumFacing.WEST;
        EnumFacing right = left.getOpposite();
        boolean top = connects(world, pos.up(), state);
        boolean bottom = connects(world, pos.down(), state);
        boolean leftConnected = connects(world, pos.offset(left), state);
        boolean rightConnected = connects(world, pos.offset(right), state);
        return state.withProperty(TOP, !top)
                .withProperty(BOTTOM, !bottom)
                .withProperty(LEFT, !leftConnected)
                .withProperty(RIGHT, !rightConnected)
                .withProperty(INNER_TL, top && leftConnected
                        && !connects(world, pos.up().offset(left), state))
                .withProperty(INNER_TR, top && rightConnected
                        && !connects(world, pos.up().offset(right), state))
                .withProperty(INNER_BL, bottom && leftConnected
                        && !connects(world, pos.down().offset(left), state))
                .withProperty(INNER_BR, bottom && rightConnected
                        && !connects(world, pos.down().offset(right), state));
    }

    private void refresh(World world, BlockPos pos) {
        world.markBlockRangeForRenderUpdate(
                pos.add(-1, -1, -1), pos.add(1, 1, 1));
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        refresh(world, pos);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
            Block changedBlock, BlockPos fromPos) {
        refresh(world, pos);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        super.breakBlock(world, pos, state);
        refresh(world, pos);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world,
            BlockPos pos) {
        return state.getValue(ROTATED) ? ZY_BOX : XY_BOX;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state,
            IBlockAccess world, BlockPos pos) {
        return getBoundingBox(state, world, pos);
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world,
            IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.TRANSLUCENT;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world,
            BlockPos pos, EnumFacing side) {
        EnumFacing.Axis normal = state.getValue(ROTATED)
                ? EnumFacing.Axis.X : EnumFacing.Axis.Z;
        if (side.getAxis() != normal
                && connects(world, pos.offset(side), state)) {
            return false;
        }
        return super.shouldSideBeRendered(state, world, pos, side);
    }
}
