package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.neoforge.copifier.NativeCopiedSettingsValidation;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;

/** Actor-free recipe edits only the channel footer; the complete selected marker applies on placement. */
public final class NativeRampCopiedItem {
    public static ItemStack apply(ItemStack input,CompoundTag values,HolderLookup.Provider registries){
        if(!(input.getItem() instanceof BlockItem item)||!(item.getBlock() instanceof NativeRampBlock block)||!NativeCopiedSettingsValidation.valid(values))return ItemStack.EMPTY;
        var root=input.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(root.contains(NativeRampItem.RECOVERY)||root.contains(NativeRampEntity.PLACEMENT_RECOVERY))return ItemStack.EMPTY;
        var state=block.defaultBlockState();var properties=input.get(DataComponents.BLOCK_STATE);if(properties!=null)state=properties.apply(state);
        var raw=block.newBlockEntity(BlockPos.ZERO,state);if(!(raw instanceof NativeRampEntity draft))return ItemStack.EMPTY;
        var component=input.get(DataComponents.BLOCK_ENTITY_DATA);
        try {
            // Recipe conversion reads the existing tag directly; placement later merges its result.
            if(component!=null)draft.loadWithComponents(component.copyTag(),registries);
            else if(root.contains("BlockEntityTag")){if(!root.contains("BlockEntityTag",Tag.TAG_COMPOUND))return ItemStack.EMPTY;draft.loadWithComponents(root.getCompound("BlockEntityTag").copy(),registries);}
            if(draft.inert())return ItemStack.EMPTY;
            var plan=NativeRampConfiguration.copied(draft.settings(),state.getValue(NativeRampBlock.FACING).get3DDataValue(),NativeRampBlock.scanFacing(state).get3DDataValue(),values,false,false,false,false);
            var fields=draft.saveWithoutMetadata(registries);plan.result().writeChoices(fields);for(String key:new String[]{"id","x","y","z"})fields.remove(key);
            root.put("BlockEntityTag",fields);root.put(NativeRampItem.COPIED,values.copy());
            var output=input.copyWithCount(1);output.remove(DataComponents.BLOCK_ENTITY_DATA);output.set(DataComponents.CUSTOM_DATA,CustomData.of(root));return output;
        }catch(IllegalArgumentException unsupported){return ItemStack.EMPTY;}
    }
    private NativeRampCopiedItem(){}
}
