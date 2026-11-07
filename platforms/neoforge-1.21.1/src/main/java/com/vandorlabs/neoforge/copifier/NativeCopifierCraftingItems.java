package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.light.NativeLightBlock;
import com.vandorlabs.neoforge.door.*;
import com.vandorlabs.neoforge.glass.*;
import com.vandorlabs.neoforge.storage.*;
import com.vandorlabs.neoforge.screen.*;
import com.vandorlabs.neoforge.trapdoor.*;
import com.vandorlabs.neoforge.segmented.*;
import com.vandorlabs.neoforge.NativePlatformComponents;
import com.vandorlabs.neoforge.NativeCopiedHousingMetadata;
import com.vandorlabs.neoforge.NativeTriggerBlock;
import com.vandorlabs.persistence.HousingSettings;
import com.vandorlabs.persistence.FrameSettings;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.screen.ScreenFamily;
import com.vandorlabs.persistence.ProgrammableGlassSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.EntityBlock;

/** Typed copied-settings item edits. No world, registry lookup of clipboard IDs or physical publication. */
public final class NativeCopifierCraftingItems {
    public static ItemStack apply(ItemStack input,CompoundTag values,HolderLookup.Provider registries){
        if(!(input.getItem() instanceof BlockItem item)||input.isEmpty()||com.vandorlabs.neoforge.inventory.NativeInventoryItems.containedOwnership(input))return ItemStack.EMPTY;
        var id=BuiltInRegistries.BLOCK.getKey(item.getBlock());
        if(!id.getNamespace().equals("vandorlabs"))return ItemStack.EMPTY;
        NativeCopifierHousingCapture.Family family=null;
        try{family=NativeCopifierHousingCapture.Family.of(id.getPath());}catch(IllegalArgumentException unsupported){if(item.getBlock() instanceof NativeStorageBlock)family=NativeCopifierHousingCapture.Family.CUBE;else if(!(item.getBlock() instanceof NativeLightBlock)&&!(item.getBlock() instanceof NativeProgrammableDoorBlock)&&!(item.getBlock() instanceof NativeLargeDoorBlock)&&!(item.getBlock() instanceof NativeGlassBlock)&&ScreenFamily.fromId(id.getPath())==null&&!(item.getBlock() instanceof NativeTrapdoorBlock)&&!(item.getBlock() instanceof NativeTriggerBlock)&&!(item.getBlock() instanceof NativeSegmentedDoorBlock))return ItemStack.EMPTY;}
        if(!(item.getBlock() instanceof EntityBlock factory))return ItemStack.EMPTY;
        var state=item.getBlock().defaultBlockState();var properties=input.get(DataComponents.BLOCK_STATE);
        if(properties!=null)state=properties.apply(state);
        var draft=factory.newBlockEntity(BlockPos.ZERO,state);if(draft==null)return ItemStack.EMPTY;
        var current=draft.saveWithoutMetadata(registries);
        var root=input.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        var supplied=input.get(DataComponents.BLOCK_ENTITY_DATA);
        if(supplied!=null)current=supplied.copyTag();
        else if(root.contains("BlockEntityTag")){if(!root.contains("BlockEntityTag",Tag.TAG_COMPOUND))return ItemStack.EMPTY;current=root.getCompound("BlockEntityTag").copy();}

        if(item.getBlock() instanceof NativeStorageBlock){
            // The released recipe omits inventory. Refuse a physical owner rather than silently discard it.
            if(current.contains("Items")&&(!(current.get("Items") instanceof ListTag items)||!items.isEmpty()))return ItemStack.EMPTY;
            try{NativeStorageItemSettings.prepare(draft.getType(),BlockPos.ZERO,state,current,registries);NativeStorageSettings.read(current).write(current);}catch(IllegalArgumentException unsupported){return ItemStack.EMPTY;}
        }
        final CompoundTag fields;
        try{
            if(item.getBlock() instanceof NativeSegmentedDoorBlock){
                NativePlatformComponents.requireSupported(current,registries);if(current.contains(NativeSegmentedItem.RECOVERY)||current.contains("ContainedFluids")&&(!current.contains("ContainedFluids",Tag.TAG_COMPOUND)||!current.getCompound("ContainedFluids").isEmpty()))return ItemStack.EMPTY;
                var plan=NativeCopifierSegmentedEdit.prepare(NativeCopifierSegmentedEdit.read(current),values);if(!plan.applicable())return ItemStack.EMPTY;var next=plan.state();fields=current.copy();next.settings().write(fields);fields.putInt("SegmentedTexture",next.texture());fields.putInt("SegmentedSpeed",next.ticks());fields.putBoolean("SegmentedLocalRedstone",next.local());fields.putBoolean("SegmentedSignalOpens",next.opens());fields.putBoolean("SegmentedConfigured",true);fields.putIntArray("RedstoneChannels",next.channels().toArray());fields.remove("RedstoneChannel");
            }
            else if(item.getBlock() instanceof NativeTriggerBlock){
                var plan=NativeCopifierTriggerEdit.prepare(current,values);if(!plan.applicable())return ItemStack.EMPTY;fields=current.copy();fields.merge(plan.metadata().fields());plan.metadata().frames().write(new ModernHousingTags(fields));fields.putInt(HousingSettings.MAIN,plan.off());plan.trigger().write(new ModernHousingTags(fields));
            }
            else if(item.getBlock() instanceof NativeTrapdoorBlock trapdoor){
                if(current.contains(NativeTrapdoorItem.RECOVERY)||current.contains(NativeTrapdoorItem.OPAQUE_RECOVERY))return ItemStack.EMPTY;
                var links=NativeTrapdoorLinks.read(current,trapdoor.diagonal());var offset=!trapdoor.diagonal()&&links.partner()==null&&links.square()==null&&links.assembly().isEmpty();
                if(links.partner()!=null||links.square()!=null||!links.assembly().isEmpty())return ItemStack.EMPTY;
                var previous=new NativeCopifierTrapdoorEdit.State(NativeTrapdoorSettings.read(current,trapdoor.diagonal()),offset);
                var plan=NativeCopifierTrapdoorEdit.prepare(previous,values,true);if(!plan.applicable())return ItemStack.EMPTY;fields=current.copy();fields.merge(plan.state().settings().item(offset));
            }
            else if(ScreenFamily.fromId(id.getPath()) instanceof ScreenFamily screenFamily){
                NativeScreenItemSettings.prepare(draft.getType(),BlockPos.ZERO,state,screenFamily,current,registries);
                var plan=NativeCopifierScreenEdit.prepareItem(NativeCopifierScreenEdit.readItem(screenFamily,current),values);if(!plan.applicable())return ItemStack.EMPTY;
                var next=plan.result();var tx=next.transmission();fields=NativeCopiedHousingMetadata.edit(current,next.frames(),next.settings().channels(),tx.range(),tx.local(),tx.channels());
                next.settings().write(fields);for(int slot=0;slot<2;slot++)fields.put(slot==0?"RedstonePrimary":"RedstoneSecondary",next.controls().get(slot).configuration());
            }
            else if(item.getBlock() instanceof NativeGlassBlock){
                if(current.contains(NativeGlassEntity.RECOVERY)||current.contains(NativeGlassEntity.PLACEMENT_RECOVERY)||current.contains("NativeGlassSchema")&&current.getInt("NativeGlassSchema")!=1)return ItemStack.EMPTY;
                var base=NativeGlassSettings.read(current);
                if(root.contains("ProgrammableGlassSettings")){
                    if(!root.contains("ProgrammableGlassSettings",Tag.TAG_COMPOUND))return ItemStack.EMPTY;var outer=root.getCompound("ProgrammableGlassSettings");
                    if(outer.contains(NativeGlassEntity.RECOVERY)||outer.contains(NativeGlassEntity.PLACEMENT_RECOVERY))return ItemStack.EMPTY;
                    var applied=NativeGlassPlacementPolicy.place(base,outer,state.getValue(NativeGlassBlock.SIZE),0,0).settings();var g=applied.glass();
                    base=new NativeGlassSettings(new ProgrammableGlassSettings(g.shade,g.material,g.layout,g.stretch,g.tint,g.framed,g.size,g.join,base.glass().panelFacing,false),applied.frames(),base.playback());
                }
                var plan=NativeCopifierGlassEdit.prepare(base,values);if(!plan.applicable())return ItemStack.EMPTY;fields=current.copy();plan.settings().write(fields);root.put("ProgrammableGlassSettings",NativeGlassItem.itemSettings(plan.settings(),plan.settings().glass().size));
            }
            else if(item.getBlock() instanceof NativeProgrammableDoorBlock||item.getBlock() instanceof NativeLargeDoorBlock){
                if(root.contains("DoorCredentialRecovery")||current.contains("DoorPlacementRecovery")||current.contains("DoorCredentialRecovery")||current.contains("ContainedFluids")&&(!current.contains("ContainedFluids",Tag.TAG_COMPOUND)||!current.getCompound("ContainedFluids").isEmpty()))return ItemStack.EMPTY;
                NativePlatformComponents.requireSupported(current,registries);
                var before=new NativeCopifierDoorEdit.State(NativeDoorSettings.read(current),NativeCopifierDoorEdit.read(id.getPath(),current).channels());
                if(root.contains("SpaceDoorSettings")){
                    if(!root.contains("SpaceDoorSettings",Tag.TAG_COMPOUND))return ItemStack.EMPTY;var outer=root.getCompound("SpaceDoorSettings");var channels=ChannelList.of(Math.max(0,outer.getInt("SpaceDoorChannel")));if(outer.contains("RedstoneChannels",Tag.TAG_INT_ARRAY))try{channels=ChannelList.of(outer.getIntArray("RedstoneChannels"));}catch(IllegalArgumentException malformed){}
                    before=new NativeCopifierDoorEdit.State(NativeDoorSettings.read(outer),channels);
                }
                var plan=NativeCopifierDoorEdit.prepare(before,item.getBlock() instanceof NativeLargeDoorBlock,values);if(!plan.applicable())return ItemStack.EMPTY;
                var next=plan.state();fields=current.copy();next.settings().write(fields);fields.putInt("RedstoneChannel",next.channels().first());fields.putIntArray("RedstoneChannels",next.channels().toArray());
                for(String key:new String[]{"DoorKeyReader","DoorRearKeyReader"})if(fields.contains(key)){if(!fields.contains(key,Tag.TAG_COMPOUND))return ItemStack.EMPTY;var reader=fields.getCompound(key);if(reader.contains("InsertedToken"))return ItemStack.EMPTY;reader.putBoolean("AnyToken",next.settings().securityAllowed()==127);reader.putInt("AllowedTokens",next.settings().securityAllowed());reader.putInt("TokenBehavior",NativeDoorSettings.securityBehavior(next.settings().securityMode()));}
                var outer=new CompoundTag();next.settings().write(outer);outer.putIntArray("RedstoneChannels",next.channels().toArray());outer.putInt("SpaceDoorChannel",next.channels().first());root.put("SpaceDoorSettings",outer);
            }
            else if(item.getBlock() instanceof NativeLightBlock light){var plan=NativeCopifierLightEdit.prepare(light.kind(),current,values);if(!plan.applicable())return ItemStack.EMPTY;fields=current.copy();var projection=plan.fields();fields.merge(projection);NativeScreenSettings.read(projection,NativeScreenCatalog.INSTANCE).write(fields);FrameSettings.read(new ModernHousingTags(projection),NativeScreenCatalog.INSTANCE::validFrame,NativeScreenCatalog.INSTANCE::validFrameSurface,NativeScreenCatalog.INSTANCE::validMaterial).write(new ModernHousingTags(fields));}
            else {var plan=NativeCopifierHousingEdit.prepare(family,current,values);if(!plan.applicable())return ItemStack.EMPTY;fields=current.copy();fields.merge(plan.fields());plan.frames().write(new ModernHousingTags(fields));plan.glass().write(new ModernHousingTags(fields));plan.playback().write(fields);}
        }catch(IllegalArgumentException unsupported){return ItemStack.EMPTY;}
        current=fields;for(String key:new String[]{"id","x","y","z"})current.remove(key);
        if(item.getBlock() instanceof NativeStorageBlock){current.remove("Items");current.remove(NativeStorageInventory.SCHEMA);current.remove(NativeStorageInventory.DATA_VERSION);}
        root.put("BlockEntityTag",current);
        var output=input.copyWithCount(1);output.remove(DataComponents.BLOCK_ENTITY_DATA);output.set(DataComponents.CUSTOM_DATA,CustomData.of(root));return output;
    }
    private NativeCopifierCraftingItems(){}
}
