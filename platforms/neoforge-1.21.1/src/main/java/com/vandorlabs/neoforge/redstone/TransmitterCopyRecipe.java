package com.vandorlabs.neoforge.redstone;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Original 3x3 center source plus exactly one horizontal target; only channel configuration copies. */
public final class TransmitterCopyRecipe extends CustomRecipe {
    public TransmitterCopyRecipe(CraftingBookCategory category){super(category);}
    private static int source(CraftingInput input){
        if(!(input instanceof TransmitterCraftingContext context)||input.width()!=2||input.height()!=1||input.ingredientCount()!=2)return -1;
        int source=context.vandorlabs$copySource()-1;
        return source>=0&&source<=1&&NativeTransmitterItem.valid(input.getItem(0))&&NativeTransmitterItem.valid(input.getItem(1))?source:-1;
    }
    @Override public boolean matches(CraftingInput input,Level level){return source(input)>=0;}
    @Override public ItemStack assemble(CraftingInput input,HolderLookup.Provider registries){int source=source(input);if(source<0)return ItemStack.EMPTY;ItemStack target=input.getItem(1-source).copyWithCount(1);NativeTransmitterItem.configure(target,NativeTransmitterItem.channels(input.getItem(source)));return target;}
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input){var result=NonNullList.withSize(input.size(),ItemStack.EMPTY);int source=source(input);if(source>=0)result.set(source,input.getItem(source).copyWithCount(1));return result;}
    @Override public boolean canCraftInDimensions(int width,int height){return width==3&&height==3;}
    @Override public RecipeSerializer<?> getSerializer(){return TransmitterCopyRegistration.SERIALIZER.get();}
}
