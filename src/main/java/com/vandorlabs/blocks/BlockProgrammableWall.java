package com.vandorlabs.blocks;

import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/** Programmable panels with the four-pixel depth of Programmable Glass. */
public class BlockProgrammableWall extends BlockAnimatedScreenSelector {
    public enum Shape { PLAIN, PORTHOLE, DIAGONAL }

    public static final PropertyDirection FACING = PropertyDirection.create(
            "facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyBool INVERTED = PropertyBool.create("inverted");
    public static final PropertyInteger DEPTH = PropertyInteger.create("depth", 0, 2);
    private final Shape shape;

    public BlockProgrammableWall(String name, Shape shape) {
        super(name);
        this.shape = shape;
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(INVERTED, false)
                .withProperty(DEPTH, 0));
        setLightLevel(0);
        useNeighborBrightness = true;
    }

    public Shape getShape() { return shape; }

    private boolean isDiagonalShape() {
        return shape == Shape.DIAGONAL;
    }

    @Override protected IProperty<EnumFacing> facingProperty() { return FACING; }

    @Override protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, INVERTED, DEPTH);
    }

    @Override public IBlockState getStateForPlacement(World world, BlockPos pos,
            EnumFacing side, float hitX, float hitY, float hitZ, int meta,
            EntityLivingBase placer) {
        boolean inverted = isDiagonalShape() && (side == EnumFacing.DOWN
                || (side.getAxis().isHorizontal() && hitY > .5F));
        EnumFacing facing = isDiagonalShape() ? placer.getHorizontalFacing().getOpposite()
                : placementFacing(world, pos, side, placer);
        float normalHit;
        switch (facing) {
            case SOUTH: normalHit = 1F - hitZ; break;
            case EAST: normalHit = 1F - hitX; break;
            case WEST: normalHit = hitX; break;
            default: normalHit = hitZ;
        }
        return getDefaultState()
                .withProperty(FACING, facing)
                .withProperty(INVERTED, inverted)
                .withProperty(DEPTH, isDiagonalShape() ? 0 : PanelDepth.fromHit(normalHit));
    }

    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3))
                .withProperty(INVERTED, (meta & 4) != 0 && isDiagonalShape())
                .withProperty(DEPTH, isDiagonalShape() ? 0 : (meta >> 2) < 3 ? meta >> 2 : 0);
    }

    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex()
                | (isDiagonalShape() ? (state.getValue(INVERTED) ? 4 : 0)
                        : state.getValue(DEPTH) << 2);
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return 0;
    }

    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world,
            BlockPos pos) {
        Corner corner = corner(state, world, pos);
        return isDiagonalShape() ? rotate(new AxisAlignedBB(0, 0, 0,
                1, 1, corner != null && corner.backRight != null ? 1 : 10 / 16D),
                state.getValue(FACING))
                : rotate(new AxisAlignedBB(0, 0,
                        PanelDepth.start(state.getValue(DEPTH)) / 16D,
                        1, 1, (PanelDepth.start(state.getValue(DEPTH)) + 4) / 16D),
                        state.getValue(FACING));
    }

    @Override public RayTraceResult collisionRayTrace(IBlockState state, World world,
            BlockPos pos, Vec3d start, Vec3d end) {
        if (!isDiagonalShape()) return super.collisionRayTrace(state, world, pos, start, end);
        List<AxisAlignedBB> boxes = new java.util.ArrayList<>();
        addCollisionBoxToList(state, world, pos, new AxisAlignedBB(pos).grow(2),
                boxes, null, false);
        RayTraceResult nearest = null;
        for (AxisAlignedBB box : boxes) {
            RayTraceResult hit = rayTrace(pos, start, end, box.offset(-pos.getX(),
                    -pos.getY(), -pos.getZ()));
            if (hit != null && (nearest == null || start.squareDistanceTo(hit.hitVec)
                    < start.squareDistanceTo(nearest.hitVec))) nearest = hit;
        }
        return nearest;
    }

    /** Perpendicular neighbors can join either end, or both ends. */
    public static final class Corner {
        @Nullable public final Boolean frontRight;
        @Nullable public final Boolean backRight;
        public final boolean straightLeft;
        public final boolean straightRight;
        Corner(@Nullable Boolean frontRight, @Nullable Boolean backRight,
                boolean straightLeft, boolean straightRight) {
            this.frontRight = frontRight;
            this.backRight = backRight;
            this.straightLeft = straightLeft;
            this.straightRight = straightRight;
        }
        public double left(double near) {
            if (straightLeft) return 0;
            // Without a straight neighbor, the free leg ends on the same
            // side as the perpendicular wall, rather than crossing past it.
            if (!straightRight && (Boolean.FALSE.equals(frontRight)
                    || Boolean.FALSE.equals(backRight))) return 0;
            double edge = 16;
            if (frontRight != null) edge = Math.min(edge, armLeft(near, frontRight));
            if (backRight != null) edge = Math.min(edge, armLeft(near, backRight));
            return edge;
        }
        public double right(double near) {
            if (straightRight) return 16;
            if (!straightLeft && (Boolean.TRUE.equals(frontRight)
                    || Boolean.TRUE.equals(backRight))) return 16;
            double edge = 0;
            if (frontRight != null) edge = Math.max(edge, armLeft(near, frontRight) + 4);
            if (backRight != null) edge = Math.max(edge, armLeft(near, backRight) + 4);
            return edge;
        }
        public double armLeft(double near, boolean right) {
            return right ? 12 - near : near;
        }
    }

    @Nullable public Corner corner(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!isDiagonalShape()) return null;
        EnumFacing facing = state.getValue(FACING);
        Boolean frontRight = null, backRight = null;
        for (boolean front : new boolean[] {true, false}) {
            BlockPos neighborPos = pos.offset(front ? facing : facing.getOpposite());
            if (world instanceof World && !((World) world).isBlockLoaded(neighborPos))
                continue;
            IBlockState neighbor = world.getBlockState(neighborPos);
            if (neighbor.getBlock() != this
                    || neighbor.getValue(INVERTED) != state.getValue(INVERTED)) continue;
            EnumFacing neighborFacing = neighbor.getValue(FACING);
            Boolean right = neighborFacing == facing.rotateY() ? Boolean.TRUE
                    : neighborFacing == facing.rotateYCCW() ? Boolean.FALSE : null;
            if (front) frontRight = right;
            else backRight = right;
        }
        if (frontRight == null && backRight == null) return null;
        boolean straightLeft = straightNeighbor(state, world, pos,
                facing.rotateYCCW());
        boolean straightRight = straightNeighbor(state, world, pos,
                facing.rotateY());
        return new Corner(frontRight, backRight, straightLeft, straightRight);
    }

    private boolean straightNeighbor(IBlockState state, IBlockAccess world,
            BlockPos pos, EnumFacing side) {
        BlockPos neighborPos = pos.offset(side);
        if (world instanceof World && !((World) world).isBlockLoaded(neighborPos))
            return false;
        IBlockState neighbor = world.getBlockState(neighborPos);
        return neighbor.getBlock() == this
                && neighbor.getValue(FACING) == state.getValue(FACING)
                && neighbor.getValue(INVERTED) == state.getValue(INVERTED);
    }

    @Override public void addCollisionBoxToList(IBlockState state, World world,
            BlockPos pos, AxisAlignedBB entityBox, List<AxisAlignedBB> boxes,
            @Nullable Entity entity, boolean isActualState) {
        if (!isDiagonalShape()) {
            addCollisionBoxToList(pos, entityBox, boxes,
                    getBoundingBox(state, world, pos));
            return;
        }
        Corner corner = corner(state, world, pos);
        // Thin vertical slices follow the rendered slope and its short arm.
        for (int slice = 0; slice < 16; slice++) {
            double a = 6D * slice / 16D, b = 6D * (slice + 1) / 16D;
            double near0 = state.getValue(INVERTED) ? 6D - b : a;
            double near1 = state.getValue(INVERTED) ? 6D - a : b;
            double left = corner == null ? 0 : Math.min(corner.left(near0),
                    corner.left(near1));
            double right = corner == null ? 16 : Math.max(corner.right(near0),
                    corner.right(near1));
            AxisAlignedBB box = new AxisAlignedBB(left / 16D, slice / 16D,
                    near0 / 16D, right / 16D,
                    (slice + 1) / 16D, (near1 + 4D) / 16D);
            addCollisionBoxToList(pos, entityBox, boxes,
                    rotate(box, state.getValue(FACING)));
            if (corner != null) {
                if (corner.frontRight != null)
                    addCornerArm(pos, entityBox, boxes, state, corner.frontRight,
                            true, slice, near0, near1);
                if (corner.backRight != null)
                    addCornerArm(pos, entityBox, boxes, state, corner.backRight,
                            false, slice, near0, near1);
            }
        }
    }

    private void addCornerArm(BlockPos pos, AxisAlignedBB entityBox,
            List<AxisAlignedBB> boxes, IBlockState state, boolean right,
            boolean front, int slice, double near0, double near1) {
        double x0 = right ? 12D - near1 : near0;
        double x1 = right ? 16D - near0 : near1 + 4D;
        double z0 = front ? 0 : near0;
        double z1 = front ? near1 + 4D : 16D;
        addCollisionBoxToList(pos, entityBox, boxes,
                rotate(new AxisAlignedBB(x0 / 16D, slice / 16D,
                        z0 / 16D, x1 / 16D, (slice + 1) / 16D,
                        z1 / 16D), state.getValue(FACING)));
    }

    private static AxisAlignedBB rotate(AxisAlignedBB box, EnumFacing facing) {
        switch (facing) {
            case SOUTH: return new AxisAlignedBB(1 - box.maxX, box.minY, 1 - box.maxZ,
                    1 - box.minX, box.maxY, 1 - box.minZ);
            case EAST: return new AxisAlignedBB(1 - box.maxZ, box.minY, box.minX,
                    1 - box.minZ, box.maxY, box.maxX);
            case WEST: return new AxisAlignedBB(box.minZ, box.minY, 1 - box.maxX,
                    box.maxZ, box.maxY, 1 - box.minX);
            default: return box;
        }
    }
}
