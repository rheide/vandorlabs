package com.vandorlabs.neoforge.screen;

import com.vandorlabs.persistence.SaveSchema;
import com.vandorlabs.screen.ScreenFamily;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Configured Screen choices: retain private data, withdraw active row ownership on inventory copies. */
public final class NativeScreenItemSettings {
    public static final String CONFIGURATION="BlockEntityTag",RECOVERY="ScreenRecoverySnapshot",PLACEMENT_RECOVERY="ScreenPlacementRecovery";
    private NativeScreenItemSettings(){}
    public static CompoundTag configuration(ItemStack stack){
        var data=stack.get(DataComponents.BLOCK_ENTITY_DATA);if(data!=null)return data.copyTag();
        var custom=stack.get(DataComponents.CUSTOM_DATA);if(custom==null)return null;var root=custom.copyTag();
        if(root.contains(CONFIGURATION)&&!root.contains(CONFIGURATION,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Malformed Screen item configuration");
        return root.contains(CONFIGURATION,Tag.TAG_COMPOUND)?root.getCompound(CONFIGURATION).copy():null;
    }
    public static CompoundTag choices(NativeScreenEntity owner,HolderLookup.Provider registries){
        var tag=owner.saveWithoutMetadata(registries);
        if(owner.inert()){var recovery=new CompoundTag();recovery.put(RECOVERY,tag);return recovery;}
        for(String key:new String[]{"id","x","y","z","ChannelLevel",SaveSchema.Redstone.SIGNAL,"JoinedSignalLevel","LightGroupPowered","NativeScreenPower","NativeScreenGeometryRevision","NativeScreenInert"})tag.remove(key);
        for(int slot=0;slot<2;slot++){String key=slot==0?"RedstonePrimary":"RedstoneSecondary";if(tag.contains(key,Tag.TAG_COMPOUND))tag.put(key,owner.controls(slot).configuration());}
        return tag;
    }
    public static ItemStack pick(Item item,NativeScreenEntity owner,HolderLookup.Provider registries){
        var stack=new ItemStack(item);if(owner!=null){var root=new CompoundTag();root.put(CONFIGURATION,choices(owner,registries));stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));}return stack;
    }
    public static final class Prepared {
        private final CompoundTag snapshot;private final HolderLookup.Provider registries;private final ScreenFamily family;
        private Prepared(NativeScreenEntity owner,HolderLookup.Provider registries){snapshot=owner.saveWithoutMetadata(registries).copy();this.registries=registries;family=owner.family();}
        public CompoundTag snapshot(){return snapshot.copy();}
    }
    public static Prepared prepare(BlockEntityType<?> type,BlockPos pos,BlockState state,ScreenFamily family,CompoundTag raw,HolderLookup.Provider registries){
        var draft=new NativeScreenEntity(type,pos,state,family,NativeScreenCatalog.INSTANCE);var merged=draft.saveWithoutMetadata(registries);
        if(raw!=null){if(raw.contains(RECOVERY)||raw.contains(PLACEMENT_RECOVERY))throw new IllegalArgumentException("Recovery Screen cannot be placed");merged.merge(raw.copy());}
        for(String key:new String[]{"id","x","y","z","ChannelLevel",SaveSchema.Redstone.SIGNAL,"JoinedSignalLevel","LightGroupPowered","NativeScreenPower","NativeScreenGeometryRevision","NativeScreenInert"})merged.remove(key);
        // Configuration copies cannot import a pending pulse or an independent active output source.
        for(int slot=0;slot<2;slot++){String key=slot==0?"RedstonePrimary":"RedstoneSecondary";if(merged.contains(key,Tag.TAG_COMPOUND))merged.put(key,NativeScreenControls.read(merged.getCompound(key),family.standaloneControlRows(slot)).configuration());}
        draft.loadWithComponents(merged.copy(),registries);if(draft.inert())throw new IllegalArgumentException("Unsupported Screen item ownership");
        return new Prepared(draft,registries);
    }
    public static void install(NativeScreenEntity owner,Prepared prepared){
        if(owner.family()!=prepared.family)throw new IllegalArgumentException("Screen item family mismatch");
        owner.loadWithComponents(prepared.snapshot.copy(),prepared.registries);if(owner.inert())throw new IllegalArgumentException("Prepared Screen platform data failed installation");
    }
}
