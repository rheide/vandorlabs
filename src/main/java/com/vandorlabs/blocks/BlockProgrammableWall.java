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
    public enum Shape { PLAIN, PORTHOLE, DIAGONAL, DIAGONAL_PORTHOLE }

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

    @Override public net.minecraft.tileentity.TileEntity createNewTileEntity(
            World world, int meta) {
        com.vandorlabs.tiles.TileEntityAnimatedScreenSelector tile =
                new com.vandorlabs.tiles.TileEntityAnimatedScreenSelector();
        if (isPortholeShape()) tile.setJoinPortholes(true);
        return tile;
    }

    public boolean isDiagonalShape() {
        return shape == Shape.DIAGONAL || shape == Shape.DIAGONAL_PORTHOLE;
    }

    public boolean isPortholeShape() { return shape == Shape.PORTHOLE || shape == Shape.DIAGONAL_PORTHOLE; }

    /** Pixel span of the slope; missing tile data keeps old walls at half width. */
    public static double diagonalSpan(IBlockAccess world, BlockPos pos) {
        net.minecraft.tileentity.TileEntity tile = world.getTileEntity(pos);
        return tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector
                && ((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector) tile)
                .isDiagonalFullWidth() && !((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile).isDiagonalHalfHeight() ? 16D : 8D;
    }

    public static double flatEnd(IBlockState state, IBlockAccess world, BlockPos pos, boolean upper) {
        if (halfHeight(world,pos)) return Double.NaN;
        if (world instanceof World && DiagonalPanelGeometry.coveredEnd((World)world,pos,state,upper))
            return Double.NaN;
        EnumFacing facing=state.getValue(FACING);
        double expected=com.vandorlabs.render.DiagonalWallGeometry.near(geometry(world,pos),
                state.getValue(INVERTED),upper?1:0);
        for (int offset=-1;offset<=1;offset++) {
            BlockPos next=pos.offset(upper?EnumFacing.UP:EnumFacing.DOWN).offset(facing.getOpposite(),offset);
            if (world instanceof World && !((World)world).isBlockLoaded(next)) continue;
            IBlockState other=world.getBlockState(next);
            if (!(other.getBlock() instanceof BlockProgrammableWall)
                    || ((BlockProgrammableWall)other.getBlock()).isDiagonalShape()) continue;
            EnumFacing otherFacing=other.getValue(FACING);
            if (otherFacing.getAxis()!=facing.getAxis()) continue;
            double near=PanelDepth.start(other.getValue(DEPTH))/16D;
            if (otherFacing!=facing) near=.75-near;
            near+=offset;
            if (Math.abs(near-expected)<=.1250001) return near;
        }
        return Double.NaN;
    }

    private static boolean sameDiagonalWidth(IBlockAccess world, BlockPos a,
            BlockPos b) {
        return diagonalSpan(world, a) == diagonalSpan(world, b)
                && halfHeight(world,a) == halfHeight(world,b);
    }

    public static boolean halfHeight(IBlockAccess world, BlockPos pos) {
        net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);
        return tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector
                && ((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile).isDiagonalHalfHeight();
    }
    public static int fill(IBlockAccess world, BlockPos pos) {
        net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);
        return tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector
                ? ((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile).getDiagonalFill() : 0;
    }
    private AxisAlignedBB diagonalBox(AxisAlignedBB box,IBlockState state,IBlockAccess world,BlockPos pos) {
        if (halfHeight(world,pos)) {
            double base=state.getValue(INVERTED)?.5:0;
            box=new AxisAlignedBB(box.minX,base+box.minZ,box.minY,
                    box.maxX,base+box.maxZ,box.maxY);
        }
        return rotate(box,state.getValue(FACING));
    }

    @Override protected IProperty<EnumFacing> facingProperty() { return FACING; }

    @Override protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, INVERTED, DEPTH);
    }

    @Override public IBlockState getStateForPlacement(World world, BlockPos pos,
            EnumFacing side, float hitX, float hitY, float hitZ, int meta,
            EntityLivingBase placer) {
        return getStateForPlacement(world, pos, side, hitX, hitY, hitZ, meta,
                placer, net.minecraft.util.EnumHand.MAIN_HAND);
    }

    @Override public IBlockState getStateForPlacement(World world, BlockPos pos,
            EnumFacing side, float hitX, float hitY, float hitZ, int meta,
            EntityLivingBase placer, net.minecraft.util.EnumHand hand) {
        if (!isDiagonalShape()) {
            EnumFacing facing = placementFacing(world, pos, side, placer);
            return getDefaultState().withProperty(FACING, facing)
                    .withProperty(DEPTH, PanelDepth.fromHit(facing, hitX, hitZ));
        }
        BlockPos support = pos.offset(side.getOpposite());
        int mode = placementGeometry(world, support, placer.getHeldItem(hand));
        IBlockState clicked = world.getBlockState(support);
        if (clicked.getBlock() == this && geometry(world, support) == mode) {
            // Prefer a continuation of the clicked surface, in either direction.
            for (EnumFacing facing : new EnumFacing[]{clicked.getValue(FACING),
                    clicked.getValue(FACING).getOpposite()}) {
                for (boolean inverted : new boolean[]{false, true}) {
                    IBlockState candidate = getDefaultState().withProperty(FACING, facing)
                            .withProperty(INVERTED, inverted);
                    if (DiagonalPanelGeometry.samePlane(support, clicked, pos, candidate, mode))
                        return candidate;
                }
            }
            if (side.getAxis()==EnumFacing.Axis.Y) return getDefaultState().withProperty(FACING,clicked.getValue(FACING))
                    .withProperty(INVERTED,clicked.getValue(INVERTED));
        }
        EnumFacing facing = placer.getHorizontalFacing().getOpposite();
        boolean inverted = side == EnumFacing.DOWN
                || (side.getAxis().isHorizontal() && hitY > .5F);
        if (mode != 2) {
            // Keep the player's wall axis, but anchor the panel at the clicked edge.
            float normal = facing.getAxis() == EnumFacing.Axis.X ? hitX : hitZ;
            if (normal < 1F / 3F) facing = facing.getAxis() == EnumFacing.Axis.X
                    ? EnumFacing.WEST : EnumFacing.NORTH;
            else if (normal > 2F / 3F) facing = facing.getAxis() == EnumFacing.Axis.X
                    ? EnumFacing.EAST : EnumFacing.SOUTH;
        }
        return getDefaultState().withProperty(FACING, facing).withProperty(INVERTED, inverted);
    }

    public static int geometry(IBlockAccess world, BlockPos pos) {
        net.minecraft.tileentity.TileEntity raw = world.getTileEntity(pos);
        if (!(raw instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)) return 0;
        com.vandorlabs.tiles.TileEntityAnimatedScreenSelector tile =
                (com.vandorlabs.tiles.TileEntityAnimatedScreenSelector) raw;
        return tile.isDiagonalHalfHeight() ? 2 : tile.isDiagonalFullWidth() ? 1 : 0;
    }

    int placementGeometry(World world, BlockPos support, net.minecraft.item.ItemStack stack) {
        net.minecraft.nbt.NBTTagCompound tag = stack.getSubCompound("BlockEntityTag");
        if (tag != null) return tag.getBoolean("DiagonalHalfHeight") ? 2
                : tag.getBoolean("DiagonalFullWidth") ? 1 : 0;
        return world.getBlockState(support).getBlock() == this ? geometry(world, support) : 0;
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
        if (isDiagonalShape()) {
            boolean half=halfHeight(world,pos);
            double low = -.125, high = (diagonalSpan(world,pos)+2)/16D;
            if (half) {
                double base=state.getValue(INVERTED)?.5:0;
                low+=base; high+=base;
            }
            if (fill(world,pos)!=0 || corner!=null) {
                low=Math.min(0,low); high=Math.max(1,high);
            }
            if (half) return new AxisAlignedBB(0,low,0,1,high,1);
            double left=corner==null?0:-.125, right=corner==null?1:1.125;
            return rotate(new AxisAlignedBB(left,0,low,right,1,high),state.getValue(FACING));
        }
        FlatCorner flat = flatCorner(state, world, pos);
        double near = PanelDepth.start(state.getValue(DEPTH));
        return rotate(new AxisAlignedBB((flat == null ? 0 : flat.left()) / 16D, 0,
                (flat != null && flat.frontArm != null ? 0 : near) / 16D,
                (flat == null ? 16 : flat.right()) / 16D, 1,
                (flat != null && flat.backArm != null ? 16 : near + 4) / 16D),
                state.getValue(FACING));
    }

    @Override public RayTraceResult collisionRayTrace(IBlockState state, World world,
            BlockPos pos, Vec3d start, Vec3d end) {
        if (!isDiagonalShape() && flatCorner(state, world, pos) == null)
            return super.collisionRayTrace(state, world, pos, start, end);
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

    /** A level panel bends into a perpendicular neighbor within this block. */
    public static final class FlatCorner {
        @Nullable public final Double frontArm;
        @Nullable public final Double backArm;
        @Nullable public final Boolean frontRight;
        @Nullable public final Boolean backRight;
        public final boolean straightLeft;
        public final boolean straightRight;

        FlatCorner(@Nullable Double frontArm, @Nullable Double backArm,
                @Nullable Boolean frontRight, @Nullable Boolean backRight,
                boolean straightLeft, boolean straightRight) {
            this.frontArm = frontArm;
            this.backArm = backArm;
            this.frontRight = frontRight;
            this.backRight = backRight;
            this.straightLeft = straightLeft;
            this.straightRight = straightRight;
        }
        public double left() {
            // Two perpendicular arms already define both ends of this bridge.
            // A free tail here turns a three-way meeting into a false cross.
            if (!straightLeft && !straightRight
                    && frontArm != null && backArm != null)
                return Math.min(frontArm, backArm);
            if (straightLeft || !straightRight && (Boolean.FALSE.equals(frontRight)
                    || Boolean.FALSE.equals(backRight))) return 0;
            return Math.min(frontArm == null ? 16 : frontArm,
                    backArm == null ? 16 : backArm);
        }
        public double right() {
            if (!straightLeft && !straightRight
                    && frontArm != null && backArm != null)
                return Math.max(frontArm, backArm) + 4;
            if (straightRight || !straightLeft && (Boolean.TRUE.equals(frontRight)
                    || Boolean.TRUE.equals(backRight))) return 16;
            return Math.max(frontArm == null ? 0 : frontArm + 4,
                    backArm == null ? 0 : backArm + 4);
        }
    }

    @Nullable public FlatCorner flatCorner(IBlockState state, IBlockAccess world,
            BlockPos pos) {
        if (shape != Shape.PLAIN) return null;
        EnumFacing facing = state.getValue(FACING);
        Double frontArm = null, backArm = null;
        Boolean frontRight = null, backRight = null;
        for (boolean front : new boolean[] {true, false}) {
            BlockPos neighborPos = pos.offset(front ? facing : facing.getOpposite());
            if (world instanceof World && !((World) world).isBlockLoaded(neighborPos))
                continue;
            IBlockState neighbor = world.getBlockState(neighborPos);
            if (neighbor.getBlock() != this) continue;
            EnumFacing neighborFacing = neighbor.getValue(FACING);
            Boolean right = neighborFacing == facing.rotateY() ? Boolean.TRUE
                    : neighborFacing == facing.rotateYCCW() ? Boolean.FALSE : null;
            if (right == null) continue;
            double neighborNear = PanelDepth.start(neighbor.getValue(DEPTH));
            double arm = right ? 12 - neighborNear : neighborNear;
            if (front) { frontArm = arm; frontRight = right; }
            else { backArm = arm; backRight = right; }
        }
        if (frontArm == null && backArm == null) return null;
        return new FlatCorner(frontArm, backArm, frontRight, backRight,
                straightNeighbor(state, world, pos, facing.rotateYCCW()),
                straightNeighbor(state, world, pos, facing.rotateY()));
    }

    @Nullable public Corner corner(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!isDiagonalShape() || isPortholeShape() || halfHeight(world,pos)) return null;
        EnumFacing facing = state.getValue(FACING);
        Boolean frontRight = null, backRight = null;
        for (boolean front : new boolean[] {true, false}) {
            BlockPos neighborPos = pos.offset(front ? facing : facing.getOpposite());
            if (world instanceof World && !((World) world).isBlockLoaded(neighborPos))
                continue;
            IBlockState neighbor = world.getBlockState(neighborPos);
            if (neighbor.getBlock() != this
                    || neighbor.getValue(INVERTED) != state.getValue(INVERTED)
                    || !sameDiagonalWidth(world, pos, neighborPos)) continue;
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
        if (neighbor.getBlock() != this) return false;
        EnumFacing facing = state.getValue(FACING);
        EnumFacing neighborFacing = neighbor.getValue(FACING);
        if (isDiagonalShape())
            return neighborFacing == facing
                    && neighbor.getValue(INVERTED) == state.getValue(INVERTED)
                    && sameDiagonalWidth(world, pos, neighborPos);
        // Opposite facings still occupy the same world plane when their
        // depth values mirror each other (near meets far; middle meets middle).
        return neighborFacing.getAxis() == facing.getAxis()
                && worldPlane(facing, state.getValue(DEPTH))
                == worldPlane(neighborFacing, neighbor.getValue(DEPTH));
    }

    private static int worldPlane(EnumFacing facing, int depth) {
        int near = PanelDepth.start(depth);
        return facing == EnumFacing.SOUTH || facing == EnumFacing.EAST
                ? 12 - near : near;
    }

    @Override public void addCollisionBoxToList(IBlockState state, World world,
            BlockPos pos, AxisAlignedBB entityBox, List<AxisAlignedBB> boxes,
            @Nullable Entity entity, boolean isActualState) {
        if (!isDiagonalShape()) {
            FlatCorner flat = flatCorner(state, world, pos);
            if (flat == null) {
                addCollisionBoxToList(pos, entityBox, boxes,
                        getBoundingBox(state, world, pos));
                return;
            }
            double near = PanelDepth.start(state.getValue(DEPTH));
            addFlatBox(pos, entityBox, boxes, state, flat.left(), flat.right(),
                    near, near + 4);
            if (flat.frontArm != null)
                addFlatBox(pos, entityBox, boxes, state,
                        flat.frontArm, flat.frontArm + 4, 0, near);
            if (flat.backArm != null)
                addFlatBox(pos, entityBox, boxes, state,
                        flat.backArm, flat.backArm + 4, near + 4, 16);
            return;
        }
        int firstBox=boxes.size();
        Corner corner = corner(state, world, pos);
        int mode=geometry(world,pos);
        double lower=flatEnd(state,world,pos,false), upper=flatEnd(state,world,pos,true);
        // Thin vertical slices follow the rendered slope and its short arm.
        for (int slice = 0; slice < 16; slice++) {
            double start = 16 * com.vandorlabs.render.DiagonalWallGeometry.near(mode,state.getValue(INVERTED),slice / 16D,lower,upper);
            double end = 16 * com.vandorlabs.render.DiagonalWallGeometry.near(mode,state.getValue(INVERTED),(slice + 1) / 16D,lower,upper);
            double near0 = Math.min(start,end), near1 = Math.max(start,end);
            double left = corner == null ? 0 : Math.min(corner.left(near0),
                    corner.left(near1));
            double right = corner == null ? 16 : Math.max(corner.right(near0),
                    corner.right(near1));
            AxisAlignedBB box = new AxisAlignedBB(left / 16D, slice / 16D,
                    near0 / 16D, right / 16D,
                    (slice + 1) / 16D, (near1 + 4D) / 16D);
            addCollisionBoxToList(pos, entityBox, boxes,
                    diagonalBox(box, state, world, pos));
            int fill = fill(world,pos);
            if (halfHeight(world,pos)) {
                // Fill below/above the rotated incline, including the unused half of the cell.
                double base=state.getValue(INVERTED)?.5:0;
                if ((fill&1)!=0) addCollisionBoxToList(pos,entityBox,boxes,rotate(new AxisAlignedBB(0,0,slice/16D,1,Math.max(0,base+(near1+4)/16D),(slice+1)/16D),state.getValue(FACING)));
                if ((fill&2)!=0) addCollisionBoxToList(pos,entityBox,boxes,rotate(new AxisAlignedBB(0,Math.min(1,base+near0/16D),slice/16D,1,1,(slice+1)/16D),state.getValue(FACING)));
            } else {
                if ((fill&1)!=0) addCollisionBoxToList(pos,entityBox,boxes,rotate(new AxisAlignedBB(left/16D,slice/16D,0,right/16D,(slice+1)/16D,Math.max(0,near1/16D)),state.getValue(FACING)));
                if ((fill&2)!=0) addCollisionBoxToList(pos,entityBox,boxes,rotate(new AxisAlignedBB(left/16D,slice/16D,Math.min(1,(near0+4)/16D),right/16D,(slice+1)/16D,1),state.getValue(FACING)));
            }
            if (corner != null) {
                if (corner.frontRight != null)
                    addCornerArm(pos, entityBox, boxes, state, corner.frontRight,
                            true, slice, near0, near1, world);
                if (corner.backRight != null)
                    addCornerArm(pos, entityBox, boxes, state, corner.backRight,
                            false, slice, near0, near1, world);
            }
        }
        DiagonalNeighbourBounds.clipCollision(boxes,firstBox,world,pos,state,mode);

    }

    private void addFlatBox(BlockPos pos, AxisAlignedBB entityBox,
            List<AxisAlignedBB> boxes, IBlockState state, double x0, double x1,
            double z0, double z1) {
        if (x1 - x0 < 1.0E-7 || z1 - z0 < 1.0E-7) return;
        addCollisionBoxToList(pos, entityBox, boxes,
                rotate(new AxisAlignedBB(x0 / 16D, 0, z0 / 16D,
                        x1 / 16D, 1, z1 / 16D), state.getValue(FACING)));
    }

    private void addCornerArm(BlockPos pos, AxisAlignedBB entityBox,
            List<AxisAlignedBB> boxes, IBlockState state, boolean right,
            boolean front, int slice, double near0, double near1, IBlockAccess world) {
        double x0 = right ? 12D - near1 : near0;
        double x1 = right ? 16D - near0 : near1 + 4D;
        double z0 = front ? 0 : near0;
        double z1 = front ? near1 + 4D : 16D;
        addCollisionBoxToList(pos, entityBox, boxes,
                diagonalBox(new AxisAlignedBB(x0 / 16D, slice / 16D,
                        z0 / 16D, x1 / 16D, (slice + 1) / 16D,
                        z1 / 16D), state, world, pos));
    }

    private static AxisAlignedBB rotate(AxisAlignedBB box, EnumFacing facing) {
        return PanelPlacement.rotateFromNorth(box, facing);
    }
}
