package com.vandorlabs.neoforge.fluid;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.fluids.FluidStack;

/** Owned storage envelope. Unknown representations stay occupied; edits never activate a dormant legacy water alias. */
public final class NativeSavedFluidSlots {
    private final NativeFluidSlots slots=new NativeFluidSlots();private Tag original;private NativeFluidSlots.Snapshot loaded=slots.snapshot();private long revision;
    public NativeFluidSlots storage(){return slots;}
    public void read(CompoundTag tag,HolderLookup.Provider registries){original=tag.contains(NativeFluidSlots.KEY)?tag.get(NativeFluidSlots.KEY).copy():null;CompoundTag decoded=tag;if(original!=null){var data=original instanceof CompoundTag compound?compound.copy():new CompoundTag();boolean malformed=!(original instanceof CompoundTag),changed=malformed;for(int side=0;side<2;side++)if(malformed||data.contains("Side"+side)&&!data.contains("Side"+side,Tag.TAG_COMPOUND)){var marker=new CompoundTag();marker.putBoolean("Unresolved",true);data.put("Side"+side,marker);changed=true;}if(changed){decoded=tag.copy();decoded.put(NativeFluidSlots.KEY,data);}}slots.read(decoded,registries);loaded=slots.snapshot();revision=slots.revision();}
    public void write(CompoundTag tag){if(slots.revision()==revision){if(original==null)tag.remove(NativeFluidSlots.KEY);else tag.put(NativeFluidSlots.KEY,original.copy());return;}var encoded=new CompoundTag();slots.write(encoded);var current=encoded.getCompound(NativeFluidSlots.KEY);var data=original instanceof CompoundTag compound?compound.copy():new CompoundTag();boolean migratedAlias=original==null&&loaded.any();var snapshot=slots.snapshot();for(int side=0;side<2;side++)if(migratedAlias||snapshot.side(side)!=loaded.side(side)){String key="Side"+side;data.remove(key);if(current.contains(key))data.put(key,current.get(key).copy());}tag.put(NativeFluidSlots.KEY,data);}
    public void writePublic(CompoundTag tag,HolderLookup.Provider registries){var data=new CompoundTag();var snapshot=slots.snapshot();for(int side=0;side<2;side++){var slot=snapshot.side(side);FluidStack fluid=slot.fluid();if(!fluid.isEmpty())data.put("Side"+side,FluidStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),fluid).getOrThrow());else if(slot.occupied()){var marker=new CompoundTag();marker.putBoolean("Unresolved",true);data.put("Side"+side,marker);}}if(!data.isEmpty())tag.put(NativeFluidSlots.KEY,data);}
}
