package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.light.NativeLightBlock;
import com.vandorlabs.neoforge.door.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.EntityBlock;

/** Item-only copied housing edits. No world, registry lookup of clipboard IDs or physical publication. */
public final class NativeCopifierCraftingItems {
    public static ItemStack apply(ItemStack input,CompoundTag values,HolderLookup.Provider registries){
        if(!(input.getItem() instanceof BlockItem item)||input.isEmpty())return ItemStack.EMPTY;
        var id=BuiltInRegistries.BLOCK.getKey(item.getBlock());
        if(!id.getNamespace().equals("vandorlabs"))return ItemStack.EMPTY;
        NativeCopifierHousingCapture.Family family=null;
        try{family=NativeCopifierHousingCapture.Family.of(id.getPath());}catch(IllegalArgumentException unsupported){if(!(item.getBlock() instanceof NativeLightBlock)&&!(item.getBlock() instanceof NativeProgrammableDoorBlock)&&!(item.getBlock() instanceof NativeLargeDoorBlock))return ItemStack.EMPTY;}
        if(!(item.getBlock() instanceof EntityBlock factory))return ItemStack.EMPTY;
        var state=item.getBlock().defaultBlockState();var properties=input.get(DataComponents.BLOCK_STATE);
        if(properties!=null)state=properties.apply(state);
        var draft=factory.newBlockEntity(BlockPos.ZERO,state);if(draft==null)return ItemStack.EMPTY;
        var current=draft.saveWithoutMetadata(registries);
        var root=input.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        var supplied=input.get(DataComponents.BLOCK_ENTITY_DATA);
        if(supplied!=null)current=supplied.copyTag();
        else if(root.contains("BlockEntityTag")){if(!root.contains("BlockEntityTag",Tag.TAG_COMPOUND))return ItemStack.EMPTY;current=root.getCompound("BlockEntityTag").copy();}
        if(root.contains("SpaceDoorSettings")||root.contains("DoorCredentialRecovery"))return ItemStack.EMPTY;
        final CompoundTag fields;
        try{
            if(item.getBlock() instanceof NativeProgrammableDoorBlock||item.getBlock() instanceof NativeLargeDoorBlock){
                var plan=NativeCopifierDoorEdit.prepare(new NativeCopifierDoorEdit.State(NativeDoorSettings.read(current),NativeCopifierDoorEdit.read(id.getPath(),current).channels()),item.getBlock() instanceof NativeLargeDoorBlock,values);if(!plan.applicable())return ItemStack.EMPTY;
                var next=plan.state();fields=new CompoundTag();next.settings().write(fields);fields.putInt("RedstoneChannel",next.channels().first());fields.putIntArray("RedstoneChannels",next.channels().toArray());
                var outer=fields.copy();outer.putInt("SpaceDoorChannel",next.channels().first());root.put("SpaceDoorSettings",outer);
            }
            else if(item.getBlock() instanceof NativeLightBlock light){var plan=NativeCopifierLightEdit.prepare(light.kind(),current,values);if(!plan.applicable())return ItemStack.EMPTY;fields=plan.fields();}
            else {var plan=NativeCopifierHousingEdit.prepare(family,current,values);if(!plan.applicable())return ItemStack.EMPTY;fields=plan.fields();}
        }catch(IllegalArgumentException unsupported){return ItemStack.EMPTY;}
        current.merge(fields);for(String key:new String[]{"id","x","y","z"})current.remove(key);
        root.put("BlockEntityTag",current);
        var output=input.copyWithCount(1);output.remove(DataComponents.BLOCK_ENTITY_DATA);output.set(DataComponents.CUSTOM_DATA,CustomData.of(root));return output;
    }
    private NativeCopifierCraftingItems(){}
}
