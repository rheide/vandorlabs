package com.vandorlabs.blocks;

import com.vandorlabs.entity.EntityChairSeat;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Single-cell pilot seat. Vehicle assembly is deliberately separate from seating. */
public final class BlockPilotSeat extends BlockVandorDirectional {
    public static final double SEAT_HEIGHT = 4.25D / 16D;
    private static final AxisAlignedBB BOUNDS = new AxisAlignedBB(.125, 0, .1875, .875, .6875, .875);

    public BlockPilotSeat() {
        super("pilot_seat");
        setLightOpacity(0);
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return PanelPlacement.rotateFromNorth(BOUNDS, state.getValue(FACING));
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
            EntityPlayer player, EnumHand hand, EnumFacing face, float x, float y, float z) {
        if (player.isSneaking() || hand != EnumHand.MAIN_HAND) return false;
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
        if (!world.isRemote)
            for (EntityChairSeat seat : world.getEntitiesWithinAABB(EntityChairSeat.class,
                    new AxisAlignedBB(pos).grow(.25, 1, .25)))
                if (pos.equals(seat.getChairPos())) { seat.removePassengers(); seat.setDead(); }
        super.breakBlock(world, pos, state);
    }
}
