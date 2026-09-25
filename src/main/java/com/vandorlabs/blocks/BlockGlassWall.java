package com.vandorlabs.blocks;

import com.vandorlabs.VandorLabs;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyInteger;
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
import com.vandorlabs.render.GlassConnections;

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
    public static final PropertyInteger DEPTH = PropertyInteger.create("depth", 0, 2);

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
                .withProperty(DEPTH, 0)
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
        return new BlockStateContainer(this, ROTATED, DEPTH, TOP, BOTTOM, LEFT, RIGHT,
                INNER_TL, INNER_TR, INNER_BL, INNER_BR);
    }

    /** Connection flags are calculated; only the wall plane is persisted. */
    @Override
    public int getMetaFromState(IBlockState state) {
        return (state.getValue(ROTATED) ? 1 : 0) | (state.getValue(DEPTH) << 1);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        int depth = (meta >> 1) & 3;
        return getDefaultState().withProperty(ROTATED, (meta & 1) != 0)
                .withProperty(DEPTH, depth < 3 ? depth : 0);
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
        boolean rotated;
        if (canConnectTo(clicked.getBlock())) rotated = clicked.getValue(ROTATED);
        else rotated = placer.getHorizontalFacing().getAxis() == EnumFacing.Axis.X;
        // A support beside the intended position exposes a face that lies in
        // the desired wall plane, so its axis would turn the panel 90 degrees.
        // Face the panel toward the placer unless an existing panel explicitly
        // supplied the plane.
        // The baked glass model rotates 90 degrees about Y for the X plane.
        float normalHit = rotated ? 1F - hitX : hitZ;
        return getDefaultState().withProperty(ROTATED, rotated)
                .withProperty(DEPTH, PanelDepth.fromHit(normalHit));
    }

    protected boolean canConnectTo(net.minecraft.block.Block other) { return other==this; }
    private boolean connects(IBlockAccess world, BlockPos pos,
            IBlockState state) {
        IBlockState other = world.getBlockState(pos);
        return canConnectTo(other.getBlock())
                && other.getValue(ROTATED).equals(state.getValue(ROTATED))
                && other.getValue(DEPTH).equals(state.getValue(DEPTH));
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world,
            BlockPos pos) {
        EnumFacing left = state.getValue(ROTATED)
                ? EnumFacing.NORTH : EnumFacing.WEST;
        final IBlockState expected=state;
        GlassConnections c=GlassConnections.calculate((horizontal,vertical)->
                // Portable coordinates use -1 for left and +1 for right;
                // offset(left, -horizontal) preserves that handedness.
                connects(world,pos.offset(left,-horizontal).up(vertical),expected));
        return state.withProperty(TOP,c.top).withProperty(BOTTOM,c.bottom)
                .withProperty(LEFT,c.left).withProperty(RIGHT,c.right)
                .withProperty(INNER_TL,c.innerTopLeft).withProperty(INNER_TR,c.innerTopRight)
                .withProperty(INNER_BL,c.innerBottomLeft).withProperty(INNER_BR,c.innerBottomRight);
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
        double start = PanelDepth.start(state.getValue(DEPTH)) / 16D;
        return state.getValue(ROTATED)
                ? new AxisAlignedBB(1D - start - .25D, 0, 0, 1D - start, 1, 1)
                : new AxisAlignedBB(0, 0, start, 1, 1, start + .25D);
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
