package com.vandorlabs.neoforge.mixin;

import com.vandorlabs.neoforge.redstone.NativeTransmitterItem;
import java.util.Optional;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla cache keys omit original grid origin; only transmitter pairs need uncached matching. */
@Mixin(RecipeCache.class)
public abstract class RecipeCacheMixin {
    @Inject(method="get",at=@At("HEAD"),cancellable=true)
    private void vandorlabs$matchOriginalGrid(Level level,CraftingInput input,CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> callback){
        if(input.width()!=2||input.height()!=1||input.ingredientCount()!=2)return;
        if(!(input.getItem(0).getItem() instanceof NativeTransmitterItem)||!(input.getItem(1).getItem() instanceof NativeTransmitterItem))return;
        callback.setReturnValue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level));
    }
}
