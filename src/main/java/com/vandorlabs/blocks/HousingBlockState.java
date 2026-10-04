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

/** Compact mesh snapshots, materializing Forge property maps only when requested. */
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
        if(faces!=null) {
            if(state instanceof DirectSnapshot) {
                DirectSnapshot previous=(DirectSnapshot)state;
                return previous.matches(finish,side,faces,tileSides,visible,light)?state
                        :new DirectSnapshot(state,previous.schema,finish,side,faces,tileSides,visible,light);
            }
            ImmutableMap<IUnlistedProperty<?>,Optional<?>> schema=state.getUnlistedProperties();
            if(commonSchema(schema)) {
                if(Objects.equals(schema.get(ProgrammableHousingState.FINISH).orElse(null),finish)
                        && Objects.equals(schema.get(ProgrammableHousingState.SIDE_FINISH).orElse(null),side)
                        && Objects.equals(schema.get(ProgrammableHousingState.FACES).orElse(null),faces)
                        && Objects.equals(schema.get(ProgrammableHousingState.TILE_SIDES).orElse(null),tileSides)
                        && (schema.size()==4 || Objects.equals(schema.get(ProgrammableHousingState.VISIBLE).orElse(null),visible)
                        && Objects.equals(schema.get(ProgrammableHousingState.LIGHT).orElse(null),light)))return state;
                return new DirectSnapshot(state,schema,finish,side,faces,tileSides,visible,light);
            }
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

    private static boolean commonSchema(ImmutableMap<IUnlistedProperty<?>,Optional<?>> schema) {
        return (schema.size()==4 || schema.size()==6
                && schema.containsKey(ProgrammableHousingState.VISIBLE) && schema.containsKey(ProgrammableHousingState.LIGHT))
                && schema.containsKey(ProgrammableHousingState.FINISH) && schema.containsKey(ProgrammableHousingState.SIDE_FINISH)
                && schema.containsKey(ProgrammableHousingState.FACES) && schema.containsKey(ProgrammableHousingState.TILE_SIDES);
    }

    /** Normal meshing reads these final fields without allocating Optional/map entries. */
    private static final class DirectSnapshot extends Snapshot {
        private final ImmutableMap<IUnlistedProperty<?>,Optional<?>> schema;
        private final Integer finish,side,tileSides,visible,light;
        private final FaceTextures faces;
        private final boolean lighting;
        private volatile Snapshot materialized;
        DirectSnapshot(IExtendedBlockState source,ImmutableMap<IUnlistedProperty<?>,Optional<?>> schema,
                int finish,int side,FaceTextures faces,int tileSides,int visible,int light) {
            super(source,schema);
            this.schema=schema;this.finish=finish;this.side=side;this.faces=faces;
            this.tileSides=tileSides;this.visible=visible;this.light=light;lighting=schema.size()==6;
        }
        private DirectSnapshot(DirectSnapshot source) {
            super(source,source.schema);
            schema=source.schema;finish=source.finish;side=source.side;faces=source.faces;
            tileSides=source.tileSides;visible=source.visible;light=source.light;lighting=source.lighting;
        }
        boolean matches(int finish,int side,FaceTextures faces,int tileSides,int visible,int light) {
            return this.finish==finish && this.side==side && this.faces.equals(faces) && this.tileSides==tileSides
                    && (!lighting || this.visible==visible && this.light==light);
        }
        @Override public <V> V getValue(IUnlistedProperty<V> property) {
            if(property==ProgrammableHousingState.FINISH)return property.getType().cast(finish);
            if(property==ProgrammableHousingState.SIDE_FINISH)return property.getType().cast(side);
            if(property==ProgrammableHousingState.FACES)return property.getType().cast(faces);
            if(property==ProgrammableHousingState.TILE_SIDES)return property.getType().cast(tileSides);
            if(lighting && property==ProgrammableHousingState.VISIBLE)return property.getType().cast(visible);
            if(lighting && property==ProgrammableHousingState.LIGHT)return property.getType().cast(light);
            return full().getValue(property);
        }
        @Override public ImmutableMap<IUnlistedProperty<?>,Optional<?>> getUnlistedProperties() {
            return full().getUnlistedProperties();
        }
        @Override public <V> IExtendedBlockState withProperty(IUnlistedProperty<V> property,V value) {
            Snapshot full=full();IExtendedBlockState result=full.withProperty(property,value);
            return result==full?this:result;
        }
        @Override public <T extends Comparable<T>,V extends T> IBlockState withProperty(IProperty<T> property,V value) {
            // Forge returns a new extended state even for an identical listed value.
            // Stairs perform this during actual-state resolution on every mesh build.
            if(value!=null && getProperties().get(property)==value)return new DirectSnapshot(this);
            Snapshot full=full();IBlockState result=full.withProperty(property,value);
            return result==full?this:result;
        }
        private Snapshot full() {
            Snapshot snapshot=materialized;
            if(snapshot==null)synchronized(this) {
                snapshot=materialized;
                if(snapshot==null) {
                    ImmutableMap.Builder<IUnlistedProperty<?>,Optional<?>> values=ImmutableMap.builder();
                    for(IUnlistedProperty<?> property:schema.keySet())values.put(property,Optional.of(getValue(property)));
                    snapshot=new Snapshot(this,values.build());materialized=snapshot;
                }
            }
            return snapshot;
        }
    }

    /** Inherit Forge's listed/unlisted transitions and canonical clean-state handling. */
    private static class Snapshot extends ExtendedStateImplementation {
        Snapshot(IExtendedBlockState state,ImmutableMap<IUnlistedProperty<?>,Optional<?>> values) {
            super(state.getBlock(),state.getProperties(),values,
                    ((StateImplementation)state).getPropertyValueTable(),state.getClean());
        }
    }
}
