package com.vandorlabs.blocks;

import com.vandorlabs.entity.EntityChairSeat;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

/** Pilot seat with an invisible upper cell reserving the backrest. */
public final class BlockPilotSeat extends BlockVandorDirectional {
    public static final PropertyBool UPPER = BlockBridgeChair.UPPER;
    public static final double SEAT_HEIGHT = 11D / 16D;
    private static final AxisAlignedBB BOUNDS = new AxisAlignedBB(-.125, 0, 0, 1.125, 1, 1.125);
    private static final AxisAlignedBB BACK = new AxisAlignedBB(-.125, 0, 0, 1.125, .75, 1.125);

    public BlockPilotSeat() {
        super("pilot_seat");
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(UPPER, false));
        setLightOpacity(0);
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING, UPPER); }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3)).withProperty(UPPER, (meta & 4) != 0);
    }
    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(UPPER) ? 4 : 0);
    }
    @Override public boolean hasTileEntity(IBlockState state){return !state.getValue(UPPER);}
    @Override public net.minecraft.tileentity.TileEntity createTileEntity(World world,IBlockState state){return new com.vandorlabs.tiles.TileEntityPilotSeat();}
    @Override public int damageDropped(IBlockState state) { return 0; }
    @Override public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return pos.getY() < world.getHeight() - 1 && super.canPlaceBlockAt(world, pos)
                && world.getBlockState(pos.up()).getBlock().isReplaceable(world, pos.up());
    }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote) {
            world.setBlockState(pos.up(), state.withProperty(UPPER, true), 3);
            net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);
            if(raw instanceof com.vandorlabs.tiles.TileEntityPilotSeat && placer instanceof EntityPlayer){((com.vandorlabs.tiles.TileEntityPilotSeat)raw).owner=placer.getUniqueID();raw.markDirty();}
        }
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return PanelPlacement.rotateFromNorth(state.getValue(UPPER) ? BACK : BOUNDS, state.getValue(FACING));
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
            EntityPlayer player, EnumHand hand, EnumFacing face, float x, float y, float z) {
        if (hand != EnumHand.MAIN_HAND) return false;
        if(player.isSneaking()) {
            BlockPos lower=state.getValue(UPPER)?pos.down():pos;
            if(!world.isRemote && !player.isSpectator() && world.isBlockModifiable(player,lower) && player.canPlayerEdit(lower,face,player.getHeldItem(hand))) {
                com.vandorlabs.tiles.TileEntityPilotSeat tile=(com.vandorlabs.tiles.TileEntityPilotSeat)world.getTileEntity(lower);
                if(tile!=null){tile.owner=player.getUniqueID();tile.markDirty();player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_REDSTONE_CHANNEL,world,lower.getX(),lower.getY(),lower.getZ());}
            }
            return true;
        }
        if (state.getValue(UPPER)) return onBlockActivated(world, pos.down(), state.withProperty(UPPER, false), player, hand, face, x, y, z);
        if (world.isRemote) return true;
        for (EntityChairSeat seat : world.getEntitiesWithinAABB(EntityChairSeat.class,
                new AxisAlignedBB(pos).grow(.25, 1, .25))) {
            if (seat.isDead || !pos.equals(seat.getChairPos())) continue;
            if (seat.getPassengers().isEmpty()) player.startRiding(seat, true);
            return true;
        }
        EntityChairSeat seat = new EntityChairSeat(world, pos, SEAT_HEIGHT);
        seat.rotationYaw = state.getValue(FACING).getHorizontalAngle();
        if (world.spawnEntity(seat) && !player.startRiding(seat, true)) seat.setDead();
        return true;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        BlockPos lower = state.getValue(UPPER) ? pos.down() : pos;
        if (!world.isRemote)
            for (EntityChairSeat seat : world.getEntitiesWithinAABB(EntityChairSeat.class,
                    new AxisAlignedBB(lower).grow(.25, 1, .25)))
                if (lower.equals(seat.getChairPos())) { seat.removePassengers(); seat.setDead(); }
        BlockPos other = state.getValue(UPPER) ? pos.down() : pos.up();
        IBlockState partner = world.getBlockState(other);
        if (partner.getBlock() == this && partner.getValue(UPPER) != state.getValue(UPPER)) world.setBlockToAir(other);
        super.breakBlock(world, pos, state);
    }
}
