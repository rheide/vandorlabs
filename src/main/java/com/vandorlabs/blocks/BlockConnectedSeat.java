package com.vandorlabs.blocks;

import com.vandorlabs.entity.EntityChairSeat;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Automatically joined seats. An invisible upper cell reserves the backrest. */
public final class BlockConnectedSeat extends BlockVandorDirectional {
    public enum Part implements IStringSerializable {
        SINGLE, LEFT, MIDDLE, RIGHT;
        public String getName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final PropertyEnum<Part> PART = PropertyEnum.create("part", Part.class);
    private final double height;
    public BlockConnectedSeat(String name) {
        super(name);
        height = name.equals("luxury_seat") ? 1.5 : 1.25;
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH)
                .withProperty(BlockBridgeChair.UPPER, false).withProperty(PART, Part.SINGLE));
        setLightOpacity(0);
    }
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, BlockBridgeChair.UPPER, PART);
    }
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3))
                .withProperty(BlockBridgeChair.UPPER, (meta & 4) != 0);
    }
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(BlockBridgeChair.UPPER) ? 4 : 0);
    }
    private boolean joins(IBlockAccess world, BlockPos pos, IBlockState state) {
        if (world instanceof World && !((World)world).isBlockLoaded(pos)) return false;
        IBlockState other = world.getBlockState(pos);
        return other.getBlock() == this && !other.getValue(BlockBridgeChair.UPPER)
                && other.getValue(FACING) == state.getValue(FACING);
    }
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (state.getValue(BlockBridgeChair.UPPER)) return state;
        EnumFacing right = state.getValue(FACING).rotateY();
        boolean leftJoin = joins(world, pos.offset(right.getOpposite()), state);
        boolean rightJoin = joins(world, pos.offset(right), state);
        return state.withProperty(PART, leftJoin ? rightJoin ? Part.MIDDLE : Part.RIGHT
                : rightJoin ? Part.LEFT : Part.SINGLE);
    }
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos) && pos.getY() < world.getHeight() - 1
                && world.getBlockState(pos.up()).getBlock().isReplaceable(world, pos.up());
    }
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote) world.setBlockState(pos.up(), state.withProperty(BlockBridgeChair.UPPER, true), 3);
    }
    public boolean isOpaqueCube(IBlockState state) { return false; }
    public boolean isFullCube(IBlockState state) { return false; }
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(BlockBridgeChair.UPPER) ? new AxisAlignedBB(0,0,0,1,height-1,1) : FULL_BLOCK_AABB;
    }
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        BlockPos lower = state.getValue(BlockBridgeChair.UPPER) ? pos.down() : pos;
        for (EntityChairSeat seat : world.getEntitiesWithinAABB(EntityChairSeat.class, new AxisAlignedBB(lower).grow(.25,1,.25)))
            if (lower.equals(seat.getChairPos())) { seat.removePassengers(); seat.setDead(); }
        BlockPos other = state.getValue(BlockBridgeChair.UPPER) ? pos.down() : pos.up();
        if (world.getBlockState(other).getBlock() == this) world.setBlockToAir(other);
        super.breakBlock(world,pos,state);
    }
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing face, float x, float y, float z) {
        if (player.isSneaking() || hand != EnumHand.MAIN_HAND) return false;
        if (world.isRemote) return true;
        BlockPos lower = state.getValue(BlockBridgeChair.UPPER) ? pos.down() : pos;
        for (EntityChairSeat seat : world.getEntitiesWithinAABB(EntityChairSeat.class, new AxisAlignedBB(lower).grow(.25,1,.25))) {
            if (seat.isDead || !lower.equals(seat.getChairPos())) continue;
            if (seat.getPassengers().isEmpty()) player.startRiding(seat, true);
            return true;
        }
        EntityChairSeat seat = new EntityChairSeat(world, lower, 8D / 16D);
        seat.rotationYaw = state.getValue(FACING).getHorizontalAngle();
        if (world.spawnEntity(seat)) player.startRiding(seat, true);
        return true;
    }
    public int damageDropped(IBlockState state) { return 0; }
    public ItemStack getPickBlock(IBlockState state, RayTraceResult hit, World world, BlockPos pos, EntityPlayer player) {
        return new ItemStack(this);
    }
}
