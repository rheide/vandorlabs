package com.vandorlabs.blocks;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import com.vandorlabs.render.ConnectedSquare;
import net.minecraft.block.properties.PropertyHelper;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** Square propulsion fixture whose render state joins complete square assemblies. */
public class BlockConnectedPropulsionLight extends BlockPropulsionLight {
    public static final int MAX_CONNECTED_SIZE = 8;
    public static final PropertyConnectedPart PART = PropertyConnectedPart.create("part");
    private final int maxConnectedSize;

    public static final class ConnectedPart
            implements Comparable<ConnectedPart>, IStringSerializable {
        private static final ConnectedPart[][][] PARTS =
                new ConnectedPart[MAX_CONNECTED_SIZE + 1][MAX_CONNECTED_SIZE][MAX_CONNECTED_SIZE];
        private static final ImmutableList<ConnectedPart> VALUES;
        public static final ConnectedPart SINGLE;
        public static final ConnectedPart BOTTOM_LEFT;
        public static final ConnectedPart BOTTOM_RIGHT;
        public static final ConnectedPart TOP_LEFT;
        public static final ConnectedPart TOP_RIGHT;

        static {
            ImmutableList.Builder<ConnectedPart> values = ImmutableList.builder();
            int ordinal = 0;
            SINGLE = new ConnectedPart(1, 0, 0, "single", ordinal++);
            PARTS[1][0][0] = SINGLE;
            values.add(SINGLE);
            for (int size = 2; size <= MAX_CONNECTED_SIZE; size++) {
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        String name = size == 2 ? twoByTwoName(x, y)
                                : "s" + size + "_x" + x + "_y" + y;
                        ConnectedPart part = new ConnectedPart(size, x, y, name, ordinal++);
                        PARTS[size][x][y] = part;
                        values.add(part);
                    }
                }
            }
            VALUES = values.build();
            BOTTOM_LEFT = PARTS[2][0][0];
            BOTTOM_RIGHT = PARTS[2][1][0];
            TOP_LEFT = PARTS[2][0][1];
            TOP_RIGHT = PARTS[2][1][1];
        }

        public final int size;
        public final int x;
        public final int y;
        private final String name;
        private final int ordinal;

        private ConnectedPart(int size, int x, int y, String name, int ordinal) {
            this.size = size;
            this.x = x;
            this.y = y;
            this.name = name;
            this.ordinal = ordinal;
        }

        public static ConnectedPart at(int size, int x, int y) {
            if (size < 1 || size > MAX_CONNECTED_SIZE || x < 0 || y < 0
                    || x >= size || y >= size) return SINGLE;
            return PARTS[size][x][y];
        }

        private static String twoByTwoName(int x, int y) {
            if (y == 0) return x == 0 ? "bottom_left" : "bottom_right";
            return x == 0 ? "top_left" : "top_right";
        }

        @Override public String getName() { return name; }
        @Override public String toString() { return name; }
        @Override public int compareTo(ConnectedPart other) {
            return Integer.compare(ordinal, other.ordinal);
        }
    }

    public static final class PropertyConnectedPart extends PropertyHelper<ConnectedPart> {
        private final ImmutableList<ConnectedPart> values;
        private final Map<String, ConnectedPart> byName;

        private PropertyConnectedPart(String name, int maxSize) {
            super(name, ConnectedPart.class);
            ImmutableList.Builder<ConnectedPart> allowed = ImmutableList.builder();
            byName = new LinkedHashMap<>();
            for (ConnectedPart part : ConnectedPart.VALUES)
                if (part.size <= maxSize) {
                    allowed.add(part);
                    byName.put(part.getName(), part);
                }
            values = allowed.build();
        }

        public static PropertyConnectedPart create(String name) {
            return new PropertyConnectedPart(name, MAX_CONNECTED_SIZE);
        }

        public static PropertyConnectedPart create(String name, int maxSize) {
            return new PropertyConnectedPart(name, maxSize);
        }

        @Override public Collection<ConnectedPart> getAllowedValues() { return values; }
        @Override public Optional<ConnectedPart> parseValue(String value) {
            return Optional.fromNullable(byName.get(value));
        }
        @Override public String getName(ConnectedPart value) { return value.getName(); }
    }

    public BlockConnectedPropulsionLight(String name) {
        this(name, false, 1.0F, MAX_CONNECTED_SIZE);
    }

    public BlockConnectedPropulsionLight(String name, boolean pointsUp, float depth) {
        this(name, pointsUp, depth, MAX_CONNECTED_SIZE);
    }

    public BlockConnectedPropulsionLight(String name, boolean pointsUp, float depth,
            int maxConnectedSize) {
        super(name, pointsUp, depth);
        this.maxConnectedSize = Math.max(1, Math.min(MAX_CONNECTED_SIZE, maxConnectedSize));
        setDefaultState(getDefaultState().withProperty(partProperty(), ConnectedPart.SINGLE));
    }

    public PropertyConnectedPart partProperty() { return PART; }

    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this,
                new IProperty<?>[]{FACING, POWERED, PARTICLES, partProperty()},
                new IUnlistedProperty<?>[]{SIDE_TEXTURE});
    }

    @Override public IBlockState getActualState(IBlockState state, IBlockAccess source,
            BlockPos pos) {
        state = withParticleState(state, source, pos);
        net.minecraft.tileentity.TileEntity self = source.getTileEntity(pos);
        if (self instanceof TileEntityRedstoneLight
                && !((TileEntityRedstoneLight) self).isJoin())
            return state.withProperty(partProperty(), ConnectedPart.SINGLE);
        EnumFacing facing = state.getValue(FACING);
        EnumFacing right = localRight(facing);
        EnumFacing up = localUp(facing);
        int mode = renderMode(source, pos);
        final IBlockState expected=state;
        ConnectedSquare.Part part=ConnectedSquare.find(maxConnectedSize,(x,y)->
                matches(source,move(move(pos,right,x),up,y),expected,mode));
        return state.withProperty(partProperty(),ConnectedPart.at(part.size,part.x,part.y));
    }

    private boolean matches(IBlockAccess source, BlockPos pos, IBlockState expected, int mode) {
        IBlockState found = source.getBlockState(pos);
        return found.getBlock() == this
                && source.getTileEntity(pos) instanceof TileEntityRedstoneLight
                && ((TileEntityRedstoneLight) source.getTileEntity(pos)).isJoin()
                && found.getValue(FACING) == expected.getValue(FACING)
                && renderMode(source, pos) == mode;
    }

    private static int renderMode(IBlockAccess source, BlockPos pos) {
        IBlockState state = source.getBlockState(pos);
        if (!state.getValue(POWERED)) return 0;
        net.minecraft.tileentity.TileEntity tile = source.getTileEntity(pos);
        return tile instanceof TileEntityRedstoneLight
                && ((TileEntityRedstoneLight) tile).isParticleStreamSelected() ? 2 : 1;
    }

    private static BlockPos move(BlockPos pos, EnumFacing direction, int distance) {
        Vec3i vector = direction.getDirectionVec();
        return pos.add(vector.getX() * distance, vector.getY() * distance,
                vector.getZ() * distance);
    }

    private static EnumFacing localRight(EnumFacing facing) {
        return PanelPlane.of(facing).right;
    }

    private static EnumFacing localUp(EnumFacing facing) {
        return PanelPlane.of(facing).up;
    }

    /** Exact world-space center shared by every member of a connected assembly. */
    public static Vec3d particleCenter(BlockPos member, EnumFacing facing,
            ConnectedPart part) {
        EnumFacing right = localRight(facing);
        EnumFacing up = localUp(facing);
        BlockPos anchor = move(move(member, right, -part.x), up, -part.y);
        double halfSpan = (part.size - 1) * .5D;
        Vec3i rightVector = right.getDirectionVec();
        Vec3i upVector = up.getDirectionVec();
        return new Vec3d(anchor.getX() + .5D
                        + (rightVector.getX() + upVector.getX()) * halfSpan,
                anchor.getY() + .5D
                        + (rightVector.getY() + upVector.getY()) * halfSpan,
                anchor.getZ() + .5D
                        + (rightVector.getZ() + upVector.getZ()) * halfSpan);
    }

    private List<TileEntityRedstoneLight> assemblyTiles(World world, BlockPos pos,
            IBlockState state) {
        List<TileEntityRedstoneLight> result = new ArrayList<>();
        ConnectedPart part = getActualState(state, world, pos).getValue(partProperty());
        if (part == ConnectedPart.SINGLE) {
            net.minecraft.tileentity.TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityRedstoneLight)
                result.add((TileEntityRedstoneLight) tile);
            return result;
        }
        EnumFacing right = localRight(state.getValue(FACING));
        EnumFacing up = localUp(state.getValue(FACING));
        BlockPos anchor = move(move(pos, right, -part.x), up, -part.y);
        for (int x = 0; x < part.size; x++) {
            for (int y = 0; y < part.size; y++) {
                net.minecraft.tileentity.TileEntity tile = world.getTileEntity(
                        move(move(anchor, right, x), up, y));
                if (!(tile instanceof TileEntityRedstoneLight)) return new ArrayList<>();
                result.add((TileEntityRedstoneLight) tile);
            }
        }
        return result;
    }

    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
            EntityPlayer player, EnumHand hand, EnumFacing facing,
            float hitX, float hitY, float hitZ) {
        if (player.isSneaking())
            return super.onBlockActivated(world, pos, state, player, hand, facing,
                    hitX, hitY, hitZ);
        if (world.isRemote) return true;
        List<TileEntityRedstoneLight> tiles = assemblyTiles(world, pos, state);
        if (tiles.size() <= 1)
            return super.onBlockActivated(world, pos, state, player, hand, facing,
                    hitX, hitY, hitZ);
        for (TileEntityRedstoneLight tile : tiles)
            if (tile.getRedstoneChannel() > 0) return true;
        int nextMode = (tiles.get(0).getManualMode() + 1) % 3;
        for (TileEntityRedstoneLight tile : tiles) tile.setManualMode(nextMode, false);
        refreshConnectedModels(world, pos, state.getValue(FACING));
        return true;
    }

    public void configureAssembly(World world, BlockPos pos, int channel,
            boolean updateParticles, boolean particles) {
        configureAssembly(world, pos, channel, updateParticles, particles, false, true);
    }

    public void configureAssembly(World world, BlockPos pos, int channel,
            boolean updateParticles, boolean particles, boolean updateJoin, boolean join) {
        configureAssembly(world, pos, channel, updateParticles, particles,
                updateJoin, join, false, 0);
    }

    public void configureAssembly(World world, BlockPos pos, int channel,
            boolean updateParticles, boolean particles, boolean updateJoin, boolean join,
            boolean updateSide, int sideTexture) {
        IBlockState state = world.getBlockState(pos);
        List<TileEntityRedstoneLight> tiles = assemblyTiles(world, pos, state);
        for (TileEntityRedstoneLight tile : tiles) tile.setRedstoneChannel(channel);
        if (updateParticles)
            for (TileEntityRedstoneLight tile : tiles)
                tile.setParticleStreamSelected(particles, false);
        if (updateJoin)
            for (TileEntityRedstoneLight tile : tiles) tile.setJoin(join);
        if (updateSide)
            for (TileEntityRedstoneLight tile : tiles) tile.setSideTexture(sideTexture);
        refreshConnectedModels(world, pos, state.getValue(FACING));
    }

    @Override public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        refreshConnectedModels(world, pos, state.getValue(FACING));
    }

    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        super.breakBlock(world, pos, state);
        refreshConnectedModels(world, pos, state.getValue(FACING));
    }

    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos,
            net.minecraft.block.Block block, BlockPos fromPos) {
        super.neighborChanged(state, world, pos, block, fromPos);
        refreshConnectedModels(world, pos, state.getValue(FACING));
    }

    public void refreshConnectedModels(World world, BlockPos center, EnumFacing facing) {
        EnumFacing right = localRight(facing);
        EnumFacing up = localUp(facing);
        BlockPos first = move(move(center, right, -maxConnectedSize), up,
                -maxConnectedSize);
        BlockPos last = move(move(center, right, maxConnectedSize), up,
                maxConnectedSize);
        if (world.isRemote) {
            world.markBlockRangeForRenderUpdate(
                    new BlockPos(Math.min(first.getX(), last.getX()),
                            Math.min(first.getY(), last.getY()), Math.min(first.getZ(), last.getZ())),
                    new BlockPos(Math.max(first.getX(), last.getX()),
                            Math.max(first.getY(), last.getY()), Math.max(first.getZ(), last.getZ())));
            return;
        }
        for (int x = -maxConnectedSize; x <= maxConnectedSize; x++) {
            for (int y = -maxConnectedSize; y <= maxConnectedSize; y++) {
                BlockPos target = move(move(center, right, x), up, y);
                if (!world.isBlockLoaded(target)) continue;
                IBlockState nearby = world.getBlockState(target);
                if (nearby.getBlock() == this)
                    world.notifyBlockUpdate(target, nearby, nearby, 2);
            }
        }
    }
}
