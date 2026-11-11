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
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/** Compact mesh snapshots, materializing Forge property maps only when requested. */
public final class HousingBlockState extends ExtendedBlockState {
    // A snapshot may later materialize its complete Forge map. Charge for that
    // larger representation up front; admission is bounded even after such reads.
    static final int MAX_COMMON_SNAPSHOTS=2048;
    static final long COMMON_SNAPSHOT_BYTES=1024;
    private final Set<IBlockState> commonSources;
    private final Map<SampleKey,DirectSnapshot> commonSnapshots=new ConcurrentHashMap<>();
    private volatile int commonCount;
    private static final ThreadLocal<SampleLookup> SAMPLE_LOOKUP=ThreadLocal.withInitial(SampleLookup::new);

    public HousingBlockState(Block block,IProperty<?>[] listed,IUnlistedProperty<?>[] unlisted) {
        super(block,listed,unlisted);
        Set<IBlockState> sources=Collections.newSetFromMap(new IdentityHashMap<IBlockState,Boolean>());
        if(block.getClass()==BlockProgrammableBlock.class||block.getClass()==BlockProgrammableSlab.class)sources.addAll(getValidStates());
        commonSources=Collections.unmodifiableSet(sources);
    }

    private static final class SampleKey {
        final IExtendedBlockState source;final int finish,side,layout,visible,hash;
        SampleKey(SampleLookup lookup){source=lookup.source;finish=lookup.finish;side=lookup.side;layout=lookup.layout;visible=lookup.visible;hash=lookup.hash;}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){
            if(other instanceof SampleLookup)return other.equals(this);
            if(!(other instanceof SampleKey))return false;SampleKey key=(SampleKey)other;
            return source==key.source&&finish==key.finish&&side==key.side&&layout==key.layout&&visible==key.visible;
        }
    }
    /** Never published; release the source after probing so a worker retains no block container. */
    private static final class SampleLookup {
        IExtendedBlockState source;int finish,side,layout,visible,hash;
        void set(IExtendedBlockState source,int finish,int side,int layout,int visible){
            this.source=source;this.finish=finish;this.side=side;this.layout=layout;this.visible=visible;
            hash=31*(31*(31*(31*System.identityHashCode(source)+finish)+side)+layout)+visible;
        }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){
            if(!(other instanceof SampleKey))return false;SampleKey key=(SampleKey)other;
            return source==key.source&&finish==key.finish&&side==key.side&&layout==key.layout&&visible==key.visible;
        }
    }
    private IExtendedBlockState commonSnapshot(IExtendedBlockState source,int finish,int side,int layout,int visible){
        if(!commonSources.contains(source))return null;
        SampleLookup lookup=SAMPLE_LOOKUP.get();
        try{
            lookup.set(source,finish,side,layout,visible);
            DirectSnapshot cached=commonSnapshots.get(lookup);if(cached!=null)return cached;
            if(commonCount>=MAX_COMMON_SNAPSHOTS)return null;
            DirectSnapshot built=new DirectSnapshot(source,source.getUnlistedProperties(),finish,side,FaceTextures.DEFAULT,layout,visible,0);
            synchronized(commonSnapshots){
                cached=commonSnapshots.get(lookup);if(cached!=null)return cached;
                if(commonCount<MAX_COMMON_SNAPSHOTS){commonSnapshots.put(new SampleKey(lookup),built);commonCount++;}
            }
            return built;
        }finally{lookup.source=null;}
    }
    int cachedCommonSnapshots(){return commonCount;}
    long cachedCommonBytes(){return commonCount*COMMON_SNAPSHOT_BYTES;}

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
        // Neighbours are still read afresh by extend(). Only the resulting immutable
        // values are shared across equal native cube/slab states, never world/position data.
        if(faces==FaceTextures.DEFAULT&&light==0&&state.getBlock().getBlockState() instanceof HousingBlockState){
            IExtendedBlockState common=((HousingBlockState)state.getBlock().getBlockState()).commonSnapshot(state,finish,side,tileSides,visible);
            if(common!=null)return common;
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
                        && (!schema.containsKey(ProgrammableHousingState.LIGHT) || Objects.equals(schema.get(ProgrammableHousingState.VISIBLE).orElse(null),visible)
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
        return (schema.size()==4 || schema.size()==5&&schema.containsKey(ProgrammableHousingState.STAIR_GEOMETRY) || (schema.size()==6 || schema.size()==7&&schema.containsKey(ProgrammableHousingState.JOINED))
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
        private final Integer stairGeometry;
        private volatile Snapshot materialized;
        DirectSnapshot(IExtendedBlockState source,ImmutableMap<IUnlistedProperty<?>,Optional<?>> schema,
                int finish,int side,FaceTextures faces,int tileSides,int visible,int light) {
            super(source,schema);
            this.schema=schema;this.finish=finish;this.side=side;this.faces=faces;
            this.tileSides=tileSides;this.visible=visible;this.light=light;lighting=schema.containsKey(ProgrammableHousingState.LIGHT);
            stairGeometry=schema.containsKey(ProgrammableHousingState.STAIR_GEOMETRY)?source.getValue(ProgrammableHousingState.STAIR_GEOMETRY):null;
        }
        private DirectSnapshot(DirectSnapshot source) {this(source,source.stairGeometry);}
        private DirectSnapshot(DirectSnapshot source,Integer geometry) {
            super(source,source.schema);
            schema=source.schema;finish=source.finish;side=source.side;faces=source.faces;
            tileSides=source.tileSides;visible=source.visible;light=source.light;lighting=source.lighting;stairGeometry=geometry;
        }
        boolean matches(int finish,int side,FaceTextures faces,int tileSides,int visible,int light) {
            return this.finish==finish && this.side==side && this.faces.equals(faces) && this.tileSides==tileSides
                    && (!lighting || this.visible==visible && this.light==light);
        }
        @Override public <V> V getValue(IUnlistedProperty<V> property) {
            if(property==ProgrammableHousingState.STAIR_GEOMETRY && schema.containsKey(property))return property.getType().cast(stairGeometry);
            if(property==ProgrammableHousingState.FINISH)return property.getType().cast(finish);
            if(property==ProgrammableHousingState.SIDE_FINISH)return property.getType().cast(side);
            if(property==ProgrammableHousingState.FACES)return property.getType().cast(faces);
            if(property==ProgrammableHousingState.TILE_SIDES)return property.getType().cast(tileSides);
            if(lighting && property==ProgrammableHousingState.VISIBLE)return property.getType().cast(visible);
            if(lighting && property==ProgrammableHousingState.LIGHT)return property.getType().cast(light);
            if(property==ProgrammableHousingState.JOINED && schema.containsKey(property))return property.getType().cast(com.vandorlabs.blocks.HousingFaceLayouts.NONE);
            return full().getValue(property);
        }
        @Override public ImmutableMap<IUnlistedProperty<?>,Optional<?>> getUnlistedProperties() {
            return full().getUnlistedProperties();
        }
        @Override public <V> IExtendedBlockState withProperty(IUnlistedProperty<V> property,V value) {
            if(property==ProgrammableHousingState.STAIR_GEOMETRY && schema.containsKey(property) && value!=null && property.isValid(value)) {
                if(Objects.equals(stairGeometry,value))return this;
                return new DirectSnapshot(this,(Integer)value);
            }
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
                    for(IUnlistedProperty<?> property:schema.keySet())values.put(property,Optional.ofNullable(getValue(property)));
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
