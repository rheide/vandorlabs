package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.items.CopiedSettingsRecipeSlots;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Shared recipe admission and vanilla consumption; typed item conversion is supplied by the family adapter. */
public final class NativeCopifierCraftingRecipe extends CustomRecipe {
    @FunctionalInterface public interface Converter {
        ItemStack apply(ItemStack input,CompoundTag selected,HolderLookup.Provider registries);
    }
    private final Supplier<RecipeSerializer<?>> serializer;
    private final Converter converter;
    public NativeCopifierCraftingRecipe(CraftingBookCategory category,Supplier<RecipeSerializer<?>> serializer,Converter converter){
        super(category);this.serializer=Objects.requireNonNull(serializer);this.converter=Objects.requireNonNull(converter);
    }
    private static CopiedSettingsRecipeSlots.Selection slots(CraftingInput input){
        return CopiedSettingsRecipeSlots.find(input.size(),i->{
            var stack=input.getItem(i);
            if(stack.isEmpty())return CopiedSettingsRecipeSlots.EMPTY;
            if(stack.getItem() instanceof NativeCopifierItem)return CopiedSettingsRecipeSlots.TOOL;
            return stack.getItem() instanceof BlockItem?CopiedSettingsRecipeSlots.INPUT:CopiedSettingsRecipeSlots.OTHER;
        });
    }
    @Override public boolean matches(CraftingInput input,Level level){return !assemble(input,level.registryAccess()).isEmpty();}
    @Override public ItemStack assemble(CraftingInput input,HolderLookup.Provider registries){
        var selection=slots(input);if(selection==null)return ItemStack.EMPTY;
        var root=NativeCopifierItem.data(input.getItem(selection.tool));
        if(!NativeCopifierClipboard.hasCopy(root))return ItemStack.EMPTY;
        var values=NativeCopifierOptions.selected(NativeCopifierClipboard.settings(root),NativeCopifierOptions.maskWords(root));
        return converter.apply(input.getItem(selection.input).copy(),values,registries);
    }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input){
        var result=NonNullList.withSize(input.size(),ItemStack.EMPTY);
        for(int i=0;i<input.size();i++)if(input.getItem(i).getItem() instanceof NativeCopifierItem)result.set(i,input.getItem(i).copy());
        return result;
    }
    @Override public boolean canCraftInDimensions(int width,int height){return width*height>=2;}
    @Override public RecipeSerializer<?> getSerializer(){return serializer.get();}
}
