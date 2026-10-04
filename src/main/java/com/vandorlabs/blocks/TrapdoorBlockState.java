package com.vandorlabs.blocks;

import com.google.common.collect.ImmutableMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IUnlistedProperty;
import java.util.Optional;
import javax.annotation.Nullable;

/** Canonical vanilla trapdoor states with direct access to their three fixed properties. */
final class TrapdoorBlockState extends BlockStateContainer {
    TrapdoorBlockState(Block block){super(block,BlockTrapDoor.FACING,BlockTrapDoor.OPEN,BlockTrapDoor.HALF);}
    @Override protected StateImplementation createState(Block block,ImmutableMap<IProperty<?>,Comparable<?>> values,
            @Nullable ImmutableMap<IUnlistedProperty<?>,Optional<?>> unlisted) {
        return new State(block,values);
    }
    private static final class State extends StateImplementation {
        private final EnumFacing facing;
        private final BlockTrapDoor.DoorHalf half;
        private final Boolean open;
        State(Block block,ImmutableMap<IProperty<?>,Comparable<?>> values) {
            super(block,values);
            facing=(EnumFacing)values.get(BlockTrapDoor.FACING);
            half=(BlockTrapDoor.DoorHalf)values.get(BlockTrapDoor.HALF);
            open=(Boolean)values.get(BlockTrapDoor.OPEN);
        }
        @Override public <T extends Comparable<T>> T getValue(IProperty<T> property) {
            if(property==BlockTrapDoor.FACING)return property.getValueClass().cast(facing);
            if(property==BlockTrapDoor.HALF)return property.getValueClass().cast(half);
            if(property==BlockTrapDoor.OPEN)return property.getValueClass().cast(open);
            // Equivalent external properties and missing-property errors use vanilla's path.
            return super.getValue(property);
        }
    }
}
