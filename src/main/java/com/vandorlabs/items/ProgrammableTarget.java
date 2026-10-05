package com.vandorlabs.items;

import com.vandorlabs.blocks.BlockVandorDoor;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Both handheld configuration tools address a door through its lower tile. */
public final class ProgrammableTarget {
    private ProgrammableTarget() { }

    public static BlockPos settingsPos(World world, BlockPos clicked) {
        IBlockState state = world.getBlockState(clicked);
        if(state.getBlock() instanceof com.vandorlabs.blocks.BlockLargeProgrammableDoor){
            com.vandorlabs.tiles.TileEntityLargeProgrammableDoor root=((com.vandorlabs.blocks.BlockLargeProgrammableDoor)state.getBlock()).root(world,clicked);
            return root==null?clicked:root.getPos();
        }
        if (state.getBlock() instanceof BlockVandorDoor
                && state.getValue(BlockVandorDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER)
            return clicked.down();
        if (state.getBlock() instanceof BlockDoor
                && state.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER)
            return clicked.down();
        if ((state.getBlock() instanceof com.vandorlabs.blocks.BlockConnectedSeat
                || state.getBlock() instanceof com.vandorlabs.blocks.BlockBridgeChair)
                && state.getValue(com.vandorlabs.blocks.BlockBridgeChair.UPPER))return clicked.down();
        if(state.getBlock() instanceof com.vandorlabs.blocks.BlockTelescopicLandingGear){
            com.vandorlabs.tiles.TileEntityLandingGear tile=((com.vandorlabs.blocks.BlockTelescopicLandingGear)state.getBlock()).root(world,clicked);
            if(tile!=null)return tile.getPos();
        }
        return clicked;
    }
}
