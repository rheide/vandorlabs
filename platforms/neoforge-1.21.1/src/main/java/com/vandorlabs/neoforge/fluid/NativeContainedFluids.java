package com.vandorlabs.neoforge.fluid;

import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Two opaque saved fluid compounds, resolved separately from their lossless stored identity. */
public final class NativeContainedFluids {
    public static final String KEY="ContainedFluids";
    private final ContainedFluidHost host;
    private volatile NativeFluidRenderSnapshot renderSnapshot=NativeFluidRenderSnapshot.EMPTY;
    private volatile Snapshot snapshot=new Snapshot(0,new Slot(null,FluidStack.EMPTY),new Slot(null,FluidStack.EMPTY));
    public NativeContainedFluids(ContainedFluidHost host){this.host=Objects.requireNonNull(host);}
    public NativeFluidRenderSnapshot renderSnapshot(){return renderSnapshot;}
    public void publishRenderSnapshot(NativeFluidRenderSnapshot next){if(renderSnapshot.equals(next))return;renderSnapshot=Objects.requireNonNull(next);var owner=host.fluidOwner();owner.requestModelDataUpdate();var level=owner.getLevel();if(level!=null&&level.isClientSide)level.setBlocksDirty(owner.getBlockPos(),owner.getBlockState(),owner.getBlockState());}
    public long revision(){return snapshot.revision;}
    public boolean any(){return snapshot.first.raw!=null||snapshot.second.raw!=null;}
    public boolean occupied(int side){return slot(side).raw!=null;}
    /** A missing registry entry remains occupied and its original compound is retained. */
    public FluidStack get(int side){return slot(side).fluid.copy();}
    public CompoundTag raw(int side){var raw=slot(side).raw;return raw==null?null:raw.copy();}
    public Snapshot snapshot(){return snapshot;}
    private Slot slot(int side){if(side<0||side>1)throw new IllegalArgumentException("Contained fluid side "+side);var value=snapshot;return side==0?value.first:value.second;}
    public void set(int side,FluidStack fluid){
        if(side<0||side>1)throw new IllegalArgumentException("Contained fluid side "+side);
        var owner=host.fluidOwner();var level=owner.getLevel();if(level==null)throw new IllegalStateException("Fluid mutation requires an installed owner");if(!level.isClientSide&&!level.getServer().isSameThread())throw new IllegalStateException("Contained fluid mutation requires the server thread");
        CompoundTag raw=null;if(fluid!=null&&!fluid.isEmpty())raw=encode(fluid,level.registryAccess());
        var before=snapshot;if(Objects.equals(side==0?before.first.raw:before.second.raw,raw))return;
        Slot next=new Slot(raw,fluid==null?FluidStack.EMPTY:fluid.copy());snapshot=side==0?new Snapshot(before.revision+1,next,before.second):new Snapshot(before.revision+1,before.first,next);
        host.containedFluidsChanged();NativeFluidWorld.changed(host);
    }
    /** Loading is local-only: no registration, neighbour reads, light changes or gameplay work. */
    public void read(CompoundTag root,HolderLookup.Provider registries){
        CompoundTag data=root.getCompound(KEY);CompoundTag first=data.contains("Side0",10)?data.getCompound("Side0").copy():null,second=data.contains("Side1",10)?data.getCompound("Side1").copy():null;
        if(!root.contains(KEY,10)&&root.getBoolean("Waterlogged")){first=new CompoundTag();first.putString("FluidName","water");first.putInt("Amount",1000);}
        var before=snapshot;long revision=before.revision+(Objects.equals(before.first.raw,first)&&Objects.equals(before.second.raw,second)?0:1);
        snapshot=new Snapshot(revision,new Slot(first,resolve(first,registries)),new Slot(second,resolve(second,registries)));NativeFluidWorld.queue(host);
    }
    public void write(CompoundTag root){var value=snapshot;CompoundTag data=new CompoundTag();if(value.first.raw!=null)data.put("Side0",value.first.raw.copy());if(value.second.raw!=null)data.put("Side1",value.second.raw.copy());root.put(KEY,data);}
    public void invalidateGeometry(){NativeFluidWorld.queue(host);}
    public void onLoad(){NativeFluidWorld.queue(host);}
    public void removed(){NativeFluidWorld.removed(host);}
    public void onChunkUnloaded(){NativeFluidWorld.unloaded(host);}
    private static CompoundTag encode(FluidStack fluid,HolderLookup.Provider registries){return (CompoundTag)FluidStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),fluid).getOrThrow();}
    private static FluidStack resolve(CompoundTag raw,HolderLookup.Provider registries){
        if(raw==null)return FluidStack.EMPTY;
        if(raw.contains("id",8))return FluidStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),raw).result().orElse(FluidStack.EMPTY);
        if(!raw.contains("FluidName",8)||!raw.contains("Amount",3)||raw.getInt("Amount")<=0)return FluidStack.EMPTY;
        ResourceLocation id=ResourceLocation.tryParse(raw.getString("FluidName"));if(id==null||!BuiltInRegistries.FLUID.containsKey(id))return FluidStack.EMPTY;var fluid=BuiltInRegistries.FLUID.get(id);if(fluid==Fluids.EMPTY)return FluidStack.EMPTY;
        var result=new FluidStack(fluid,raw.getInt("Amount"));if(raw.contains("Tag",10))result.set(DataComponents.CUSTOM_DATA,CustomData.of(raw.getCompound("Tag").copy()));return result;
    }
    public static final class Slot {
        private final CompoundTag raw;private final FluidStack fluid;
        private Slot(CompoundTag raw,FluidStack fluid){this.raw=raw;this.fluid=fluid;}
        public boolean occupied(){return raw!=null;}
        public FluidStack fluid(){return fluid.copy();}
        public net.neoforged.neoforge.fluids.FluidType fluidType(){return fluid.isEmpty()?null:fluid.getFluidType();}
        public CompoundTag raw(){return raw==null?null:raw.copy();}
    }
    public static final class Snapshot {
        public final long revision;private final Slot first,second;
        private Snapshot(long revision,Slot first,Slot second){this.revision=revision;this.first=first;this.second=second;}
        public Slot side(int side){if(side<0||side>1)throw new IllegalArgumentException("Contained fluid side "+side);return side==0?first:second;}
    }
}
