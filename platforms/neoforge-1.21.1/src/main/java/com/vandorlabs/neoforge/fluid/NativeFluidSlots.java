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

/** Two saved opaque fluid slots. Hosts own thread, capacity, placement and notification policy. */
public final class NativeFluidSlots {
    public static final String KEY="ContainedFluids";
    private volatile Snapshot snapshot=new Snapshot(0,new Slot(null,FluidStack.EMPTY),new Slot(null,FluidStack.EMPTY));
    public long revision(){return snapshot.revision;}
    public Snapshot snapshot(){return snapshot;}
    public boolean any(){return snapshot.any();}
    public boolean occupied(int side){return snapshot.side(side).occupied();}
    public FluidStack get(int side){return snapshot.side(side).fluid();}
    public CompoundTag raw(int side){return snapshot.side(side).raw();}
    /** Local storage mutation only. Deliberately replacing an opaque occupied slot is the host's decision. */
    public void set(int side,FluidStack fluid,HolderLookup.Provider registries){
        var before=snapshot;var previous=before.side(side);
        CompoundTag raw=fluid==null||fluid.isEmpty()?null:(CompoundTag)FluidStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),fluid).getOrThrow();
        if(Objects.equals(previous.raw,raw))return;
        var next=new Slot(raw,fluid==null?FluidStack.EMPTY:fluid);
        snapshot=side==0?new Snapshot(before.revision+1,next,before.second):new Snapshot(before.revision+1,before.first,next);
    }
    /** Registry resolution is separate from retained identity; registry changes alone do not advance revision. */
    public void read(CompoundTag root,HolderLookup.Provider registries){
        var data=root.getCompound(KEY);
        CompoundTag first=data.contains("Side0",10)?data.getCompound("Side0"):null,second=data.contains("Side1",10)?data.getCompound("Side1"):null;
        if(!root.contains(KEY,10)&&root.getBoolean("Waterlogged")){first=new CompoundTag();first.putString("FluidName","water");first.putInt("Amount",1000);}
        var before=snapshot;long revision=before.revision+(Objects.equals(before.first.raw,first)&&Objects.equals(before.second.raw,second)?0:1);
        snapshot=new Snapshot(revision,new Slot(first,resolve(first,registries)),new Slot(second,resolve(second,registries)));
    }
    public void write(CompoundTag root){snapshot.write(root);}
    private static FluidStack resolve(CompoundTag raw,HolderLookup.Provider registries){
        if(raw==null)return FluidStack.EMPTY;
        if(raw.contains("id",8))return FluidStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),raw).result().orElse(FluidStack.EMPTY);
        if(!raw.contains("FluidName",8)||raw.getInt("Amount")<=0)return FluidStack.EMPTY;
        ResourceLocation id=ResourceLocation.tryParse(raw.getString("FluidName"));
        if(id==null||!BuiltInRegistries.FLUID.containsKey(id))return FluidStack.EMPTY;
        var fluid=BuiltInRegistries.FLUID.get(id);if(fluid==Fluids.EMPTY)return FluidStack.EMPTY;
        var result=new FluidStack(fluid,raw.getInt("Amount"));
        // Forge getCompoundTag returns an empty compound for a present tag of another type.
        if(raw.contains("Tag"))result.set(DataComponents.CUSTOM_DATA,CustomData.of(raw.getCompound("Tag")));
        return result;
    }
    private static FluidStack copyFluid(FluidStack fluid){
        // FluidStack.copy returns the mutable global EMPTY for empty stacks. Never publish it.
        if(fluid.isEmpty())return new FluidStack(Fluids.EMPTY,0);
        var result=fluid.copy();
        for(var component:fluid.getComponents())copyMutableData(result,component);
        return result;
    }
    @SuppressWarnings("unchecked") // The component's runtime value proves its registered T is CustomData.
    private static <T> void copyMutableData(FluidStack target,net.minecraft.core.component.TypedDataComponent<T> component){
        if(component.value() instanceof CustomData data)target.set(component.type(),(T)CustomData.of(data.copyTag()));
    }
    public static final class Slot {
        private final CompoundTag raw;private final FluidStack fluid;
        private Slot(CompoundTag raw,FluidStack fluid){this.raw=raw==null?null:raw.copy();this.fluid=copyFluid(fluid);}
        public boolean occupied(){return raw!=null;}
        public FluidStack fluid(){return copyFluid(fluid);}
        public CompoundTag raw(){return raw==null?null:raw.copy();}
        public net.neoforged.neoforge.fluids.FluidType fluidType(){return fluid.isEmpty()?null:fluid.getFluidType();}
    }
    public static final class Snapshot {
        public final long revision;private final Slot first,second;
        private Snapshot(long revision,Slot first,Slot second){this.revision=revision;this.first=first;this.second=second;}
        public Slot side(int side){if(side<0||side>1)throw new IllegalArgumentException("Contained fluid side "+side);return side==0?first:second;}
        public boolean any(){return first.occupied()||second.occupied();}
        public void write(CompoundTag root){var data=new CompoundTag();if(first.raw!=null)data.put("Side0",first.raw.copy());if(second.raw!=null)data.put("Side1",second.raw.copy());root.put(KEY,data);}
    }
}
