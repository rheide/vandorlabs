package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.vandorlabs.tiles.TileEntitySlidingDoor;

import java.util.Random;

public class BlockVandorDoor extends BlockVandorDirectional {

    public static final PropertyEnum<BlockDoor.EnumDoorHalf> HALF = PropertyEnum.create("half", BlockDoor.EnumDoorHalf.class);
    public static final PropertyBool OPEN = PropertyBool.create("open");
    public static final PropertyEnum<BlockDoor.EnumHingePosition> HINGE = PropertyEnum.create("hinge", BlockDoor.EnumHingePosition.class);
    /** Last seen redstone power (upper-half meta bit). Lets neighbor updates
     * react to power EDGES only, so placing a block next to a hand-opened
     * door never slams it shut. */
    public static final PropertyBool POWERED = PropertyBool.create("powered");

    public enum DoorMotion {
        HINGED("door", false),
        HINGED_SPLIT("split_door", true),
        SLIDING("sliding_door", true),
        SLIDING_SPLIT("sliding_split_door", true);

        private final String id;
        private final boolean clearsOpening;

        DoorMotion(String id, boolean clearsOpening) {
            this.id = id;
            this.clearsOpening = clearsOpening;
        }

        public static DoorMotion fromId(String id) {
            for (DoorMotion value : values()) {
                if (value.id.equals(id)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("unknown door motion: " + id);
        }

        public boolean clearsOpening() {
            return clearsOpening;
        }
    }

    public DoorMotion getDoorMotion() {
        return motion;
    }

    /** Geometry/interaction mode; all current doors animate through a TESR. */
    private final DoorMotion motion;
    /** Cutout look (seethrough glass regions): renders in the cutout layer. */
    protected final boolean cutout;

    private static final AxisAlignedBB CLOSED_CENTER_NS = new AxisAlignedBB(0.0D, 0.0D, 0.4375D, 1.0D, 1.0D, 0.5625D);
    private static final AxisAlignedBB CLOSED_CENTER_EW = new AxisAlignedBB(0.4375D, 0.0D, 0.0D, 0.5625D, 1.0D, 1.0D);
    // Closed single doors sit flush with the block's near edge (observed
    // vanilla placement: the panel hugs the placer's side of the frame).
    private static final AxisAlignedBB CLOSED_N_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.125D);
    private static final AxisAlignedBB CLOSED_S_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.875D, 1.0D, 1.0D, 1.0D);
    private static final AxisAlignedBB CLOSED_E_AABB = new AxisAlignedBB(0.875D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    private static final AxisAlignedBB CLOSED_W_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 0.125D, 1.0D, 1.0D);
    // Open-leaf edge boxes, keyed by the side of the doorway the leaf rests on.
    private static final AxisAlignedBB LEAF_W_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 0.125D, 1.0D, 1.0D);
    private static final AxisAlignedBB LEAF_E_AABB = new AxisAlignedBB(0.875D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    private static final AxisAlignedBB LEAF_N_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.125D);
    private static final AxisAlignedBB LEAF_S_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.875D, 1.0D, 1.0D, 1.0D);

    public BlockVandorDoor(String name, DoorMotion motion, boolean cutout) {
        super(name);
        this.motion = motion;
        this.cutout = cutout;
        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(HALF, BlockDoor.EnumDoorHalf.LOWER).withProperty(OPEN, false).withProperty(HINGE, BlockDoor.EnumHingePosition.LEFT).withProperty(POWERED, false));
        setLightOpacity(0);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, HALF, OPEN, HINGE, POWERED);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntitySlidingDoor();
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
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getBlockLayer() {
        return cutout ? BlockRenderLayer.CUTOUT : super.getBlockLayer();
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        // Doors whose leaf clears the doorway keep full-block selection so
        // the open mechanism stays easy to target.
        if (motion.clearsOpening && state.getValue(OPEN)) {
            return FULL_BLOCK_AABB;
        }
        if (state.getValue(OPEN)) {
            return openLeafBox(getActualState(state, source, pos));
        }
        // The upper half stores no FACING in its own meta (it defaults to
        // NORTH), so every closed box must read the facing from the actual
        // state or the top half of any non-north door would report a box
        // rotated 90 degrees off from the rendered panel.
        EnumFacing facing = getActualState(state, source, pos).getValue(FACING);
        if (motion.clearsOpening) {
            return (facing == EnumFacing.NORTH || facing == EnumFacing.SOUTH) ? CLOSED_CENTER_NS : CLOSED_CENTER_EW;
        }
        switch (facing) {
            case SOUTH: return CLOSED_S_AABB;
            case EAST: return CLOSED_E_AABB;
            case WEST: return CLOSED_W_AABB;
            case NORTH:
            default: return CLOSED_N_AABB;
        }
    }

    /** Open leaf rests on the hinge side (vanilla door parity). */
    private static AxisAlignedBB openLeafBox(IBlockState state) {
        boolean left = state.getValue(HINGE) == BlockDoor.EnumHingePosition.LEFT;
        switch (state.getValue(FACING)) {
            case SOUTH: return left ? LEAF_E_AABB : LEAF_W_AABB;
            case EAST: return left ? LEAF_N_AABB : LEAF_S_AABB;
            case WEST: return left ? LEAF_S_AABB : LEAF_N_AABB;
            case NORTH:
            default: return left ? LEAF_W_AABB : LEAF_E_AABB;
        }
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        // Split door opens the middle of the doorway: walk straight through.
        if (motion.clearsOpening && state.getValue(OPEN)) {
            return NULL_AABB;
        }
        if (state.getValue(OPEN)) {
            return openLeafBox(getActualState(state, worldIn, pos));
        }
        return getBoundingBox(state, worldIn, pos);
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos)
                && world.getBlockState(pos.up()).getBlock().isReplaceable(world, pos.up())
                && world.isSideSolid(pos.down(), EnumFacing.UP);
    }

    @Override
    public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getStateForPlacement(worldIn, pos, facing, hitX, hitY, hitZ, meta, placer, EnumHand.MAIN_HAND);
    }

    @Override
    public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        // Door faces the placer; the hinge goes away from a neighboring door
        // (paired doorways part in the middle like vanilla), otherwise the
        // click's left/right half (from the placer's view) picks the side.
        EnumFacing f = placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, f).withProperty(HALF, BlockDoor.EnumDoorHalf.LOWER).withProperty(OPEN, false).withProperty(HINGE, getHingeForPlacement(worldIn, pos, f, hitX, hitZ));
    }

    private BlockDoor.EnumHingePosition getHingeForPlacement(World world, BlockPos pos,
            EnumFacing facing, float hitX, float hitZ) {
        EnumFacing leftDir = facing.rotateY();
        boolean leftDoor = compatibleDoorAt(world, pos.offset(leftDir), facing) != null;
        boolean rightDoor = compatibleDoorAt(world,
                pos.offset(leftDir.getOpposite()), facing) != null;
        if (leftDoor && !rightDoor) {
            return BlockDoor.EnumHingePosition.LEFT;
        }
        if (rightDoor && !leftDoor) {
            return BlockDoor.EnumHingePosition.RIGHT;
        }
        return getHingeForClick(facing, hitX, hitZ);
    }

    // Clicking the visual-left half must rest the leaf on the visual-left
    // side. Vanilla hinge labels are mirrored from viewer intuition (its
    // hinge=LEFT rests the leaf on the viewer's right), so visual-left maps
    // to RIGHT here; leaf, trim and box tables all agree on that parity.
    private static BlockDoor.EnumHingePosition getHingeForClick(EnumFacing facing, float hitX, float hitZ) {
        boolean visualLeft;
        switch (facing) {
            case NORTH: visualLeft = hitX > 0.5F; break; // placer looks south, left = +x
            case SOUTH: visualLeft = hitX < 0.5F; break; // placer looks north, left = -x
            case EAST: visualLeft = hitZ > 0.5F; break;  // placer looks west, left = +z
            case WEST: visualLeft = hitZ < 0.5F; break;  // placer looks east, left = -z
            default: visualLeft = true;
        }
        return visualLeft ? BlockDoor.EnumHingePosition.RIGHT : BlockDoor.EnumHingePosition.LEFT;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        world.setBlockState(pos.up(), state.withProperty(HALF, BlockDoor.EnumDoorHalf.UPPER), 3);
    }

    // Metadata budget (4 bits): the lower half stores facing + open; the upper
    // half stores half + open + hinge + power memory (vanilla keeps hinge in
    // the same spare bit). The missing halves are filled in via getActualState
    // from the partner block in the world.
    @Override
    public IBlockState getStateFromMeta(int meta) {
        if ((meta & 8) == 0) {
            return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3)).withProperty(OPEN, (meta & 4) != 0).withProperty(HALF, BlockDoor.EnumDoorHalf.LOWER).withProperty(HINGE, BlockDoor.EnumHingePosition.LEFT).withProperty(POWERED, false);
        }
        return getDefaultState().withProperty(FACING, EnumFacing.NORTH).withProperty(OPEN, (meta & 4) != 0).withProperty(HALF, BlockDoor.EnumDoorHalf.UPPER).withProperty(HINGE, (meta & 1) != 0 ? BlockDoor.EnumHingePosition.RIGHT : BlockDoor.EnumHingePosition.LEFT).withProperty(POWERED, (meta & 2) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        if (state.getValue(HALF) == BlockDoor.EnumDoorHalf.LOWER) {
            return state.getValue(FACING).getHorizontalIndex() | (state.getValue(OPEN) ? 4 : 0);
        }
        return 8 | (state.getValue(OPEN) ? 4 : 0) | (state.getValue(HINGE) == BlockDoor.EnumHingePosition.RIGHT ? 1 : 0) | (state.getValue(POWERED) ? 2 : 0);
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (state.getValue(HALF) == BlockDoor.EnumDoorHalf.LOWER) {
            IBlockState upper = world.getBlockState(pos.up());
            if (upper.getBlock() == this) {
                state = state.withProperty(HINGE, upper.getValue(HINGE)).withProperty(POWERED, upper.getValue(POWERED));
            }
        } else {
            IBlockState lower = world.getBlockState(pos.down());
            if (lower.getBlock() == this) {
                state = state.withProperty(FACING, lower.getValue(FACING));
            }
        }
        return state;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        BlockPos other = state.getValue(HALF) == BlockDoor.EnumDoorHalf.LOWER ? pos.up() : pos.down();
        if (world.getBlockState(other).getBlock() == this) {
            world.setBlockToAir(other);
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (state.getValue(HALF) == BlockDoor.EnumDoorHalf.UPPER) {
            if (!(world.getBlockState(pos.down()).getBlock() == this && world.getBlockState(pos.down()).getValue(HALF) == BlockDoor.EnumDoorHalf.LOWER)) {
                world.setBlockToAir(pos);
                return;
            }
        } else if (!world.isSideSolid(pos.down(), EnumFacing.UP)) {
            world.destroyBlock(pos, true);
            return;
        }
        if (!world.isRemote) {
            BlockPos lowerPos = lowerPos(state, pos);
            TileEntity tile = world.getTileEntity(lowerPos);
            if (tile instanceof TileEntitySlidingDoor) ((TileEntitySlidingDoor) tile).localInputChanged();
            updateRedstoneState(world, pos, state);
            // Any other neighbor change (block placed nearby, etc.) leaves a
            // hand-set door exactly as it was.
        }
    }

    public void updateRedstoneState(World world, BlockPos pos, IBlockState state) {
        BlockPos lowerPos = lowerPos(state, pos);
        TileEntity tile = world.getTileEntity(lowerPos);
        boolean channelPowered = tile instanceof TileEntitySlidingDoor
                && ((TileEntitySlidingDoor) tile).isChannelSignalPowered();
        boolean powered = world.isBlockPowered(lowerPos) || world.isBlockPowered(lowerPos.up())
                || channelPowered;
        boolean wasPowered = getActualState(state, world, pos).getValue(POWERED);
        if (powered != wasPowered) setPowered(world, pos, state, powered);
    }

    private void setPowered(World world, BlockPos pos, IBlockState state, boolean powered) {
        boolean changed = false;
        BlockPos lowerPos = state.getValue(HALF) == BlockDoor.EnumDoorHalf.LOWER ? pos : pos.down();
        BlockPos upperPos = lowerPos.up();
        IBlockState lower = world.getBlockState(lowerPos);
        if (lower.getBlock() == this
                && (lower.getValue(OPEN) != powered || getActualState(lower, world, lowerPos).getValue(POWERED) != powered)) {
            world.setBlockState(lowerPos, lower.withProperty(OPEN, powered).withProperty(POWERED, powered), 2);
            changed = true;
        }
        IBlockState upper = world.getBlockState(upperPos);
        if (upper.getBlock() == this
                && (upper.getValue(OPEN) != powered || upper.getValue(POWERED) != powered)) {
            world.setBlockState(upperPos, upper.withProperty(OPEN, powered).withProperty(POWERED, powered), 2);
            changed = true;
        }
        if (changed) {
            world.playSound(null, pos, powered ? SoundEvents.BLOCK_IRON_DOOR_OPEN : SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) {
            BlockPos lower = lowerPos(state, pos);
            if (!world.isRemote && world.getTileEntity(lower) instanceof TileEntitySlidingDoor)
                player.openGui(VandorLabs.instance, GuiHandler.GUI_REDSTONE_CHANNEL,
                        world, lower.getX(), lower.getY(), lower.getZ());
            return true;
        }
        if (!world.isRemote) {
            boolean open = !state.getValue(OPEN);
            setOpen(world, pos, state, open);
            // Paired doorway: a click toggles width-neighbors too (one level,
            // no recursion, redstone path untouched so power stays per-door).
            // Only toggle if neighbor has opposite hinge (proper double-door pair).
            // Facing via actual state: upper halves don't store it in meta.
            EnumFacing doorFacing = getActualState(state, world, pos).getValue(FACING);
            BlockDoor.EnumHingePosition myHinge = getActualState(state, world, pos).getValue(HINGE);
            BlockPos lowerPos = lowerPos(state, pos);
            for (EnumFacing side : new EnumFacing[] { doorFacing.rotateY(), doorFacing.rotateYCCW() }) {
                BlockPos npos = lowerPos.offset(side);
                BlockVandorDoor neighbor = compatibleDoorAt(world, npos, doorFacing);
                if (neighbor != null) {
                    IBlockState nstate = world.getBlockState(npos);
                    BlockDoor.EnumHingePosition neighborHinge = neighbor
                            .getActualState(nstate, world, npos).getValue(HINGE);
                    if (neighborHinge != myHinge && nstate.getValue(OPEN) != open) {
                        neighbor.setOpen(world, npos, nstate, open);
                    }
                }
            }
        }
        return true;
    }

    private void setOpen(World world, BlockPos pos, IBlockState state, boolean open) {
        boolean changed = false;
        BlockPos lowerPos = lowerPos(state, pos);
        BlockPos upperPos = lowerPos.up();
        IBlockState lower = world.getBlockState(lowerPos);
        if (lower.getBlock() == this && lower.getValue(OPEN) != open) {
            // Flag 2 (no neighbor notify): keeps toggle traffic tight and lets
            // the edge-triggered power memory below stay authoritative.
            world.setBlockState(lowerPos, lower.withProperty(OPEN, open), 2);
            changed = true;
        }
        IBlockState upper = world.getBlockState(upperPos);
        if (upper.getBlock() == this && upper.getValue(OPEN) != open) {
            world.setBlockState(upperPos, upper.withProperty(OPEN, open), 2);
            changed = true;
        }
        if (changed) {
            world.playSound(null, pos, open ? SoundEvents.BLOCK_IRON_DOOR_OPEN : SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
    }

    private static BlockPos lowerPos(IBlockState state, BlockPos pos) {
        return state.getValue(HALF) == BlockDoor.EnumDoorHalf.LOWER ? pos : pos.down();
    }

    /** A pair must be level, complete, parallel, and use the same mechanism. */
    private BlockVandorDoor compatibleDoorAt(World world, BlockPos candidate,
            EnumFacing facing) {
        IBlockState lower = world.getBlockState(candidate);
        if (!(lower.getBlock() instanceof BlockVandorDoor)
                || lower.getValue(HALF) != BlockDoor.EnumDoorHalf.LOWER) {
            return null;
        }
        BlockVandorDoor door = (BlockVandorDoor) lower.getBlock();
        IBlockState upper = world.getBlockState(candidate.up());
        if (!canPairWith(door) || !door.canPairWith(this) || upper.getBlock() != door
                || upper.getValue(HALF) != BlockDoor.EnumDoorHalf.UPPER
                || lower.getValue(FACING) != facing) {
            return null;
        }
        return door;
    }

    protected boolean canPairWith(BlockVandorDoor other) {
        return other.motion == motion;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return state.getValue(HALF) == BlockDoor.EnumDoorHalf.LOWER ? Item.getItemFromBlock(this) : null;
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return new ItemStack(this);
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirrorIn) {
        return state.withRotation(mirrorIn.toRotation(state.getValue(FACING)));
    }
}
