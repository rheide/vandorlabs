package com.vandorlabs.blocks;

import com.google.common.collect.ImmutableMap;
import com.vandorlabs.tiles.FaceTextures;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer.StateImplementation;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Forge-compatible snapshots built in one map pass instead of four/six copies. */
public final class HousingBlockState extends ExtendedBlockState {
    public HousingBlockState(Block block,IProperty<?>[] listed,IUnlistedProperty<?>[] unlisted) {
        super(block,listed,unlisted);
    }

    static IExtendedBlockState sample(IExtendedBlockState state,int finish,int side,FaceTextures faces,
            int tileSides,int visible,int light) {
        // Keep compatibility with an external IExtendedBlockState implementation.
        if(!(state instanceof StateImplementation)) {
            IExtendedBlockState result=state.withProperty(ProgrammableHousingState.FINISH,finish)
                    .withProperty(ProgrammableHousingState.SIDE_FINISH,side)
                    .withProperty(ProgrammableHousingState.FACES,faces)
                    .withProperty(ProgrammableHousingState.TILE_SIDES,tileSides);
            return state.getUnlistedNames().contains(ProgrammableHousingState.VISIBLE)
                    ?result.withProperty(ProgrammableHousingState.VISIBLE,visible).withProperty(ProgrammableHousingState.LIGHT,light):result;
        }
        ImmutableMap.Builder<IUnlistedProperty<?>,Optional<?>> values=ImmutableMap.builder();
        boolean changed=false;
        for(Map.Entry<IUnlistedProperty<?>,Optional<?>> entry:state.getUnlistedProperties().entrySet()) {
            IUnlistedProperty<?> property=entry.getKey();Object value;
            if(property==ProgrammableHousingState.FINISH)value=finish;
            else if(property==ProgrammableHousingState.SIDE_FINISH)value=side;
            else if(property==ProgrammableHousingState.FACES)value=faces;
            else if(property==ProgrammableHousingState.TILE_SIDES)value=tileSides;
            else if(property==ProgrammableHousingState.VISIBLE)value=visible;
            else if(property==ProgrammableHousingState.LIGHT)value=light;
            else {values.put(property,entry.getValue());continue;}
            boolean same=Objects.equals(entry.getValue().orElse(null),value);
            changed|=!same;values.put(property,same?entry.getValue():Optional.of(value));
        }
        return changed?new Snapshot(state,values.build()):state;
    }

    /** Inherit Forge's listed/unlisted transitions and canonical clean-state handling. */
    private static final class Snapshot extends ExtendedStateImplementation {
        Snapshot(IExtendedBlockState state,ImmutableMap<IUnlistedProperty<?>,Optional<?>> values) {
            super(state.getBlock(),state.getProperties(),values,
                    ((StateImplementation)state).getPropertyValueTable(),state.getClean());
        }
    }
}
