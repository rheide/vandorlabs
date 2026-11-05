package com.vandorlabs.neoforge.mixin;

import com.vandorlabs.neoforge.redstone.NativeTransmitterItem;
import com.vandorlabs.neoforge.redstone.TransmitterCraftingContext;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve only the center-source proof that vanilla cropping otherwise discards. */
@Mixin(CraftingInput.class)
public abstract class CraftingInputMixin implements TransmitterCraftingContext {
    @Unique private int vandorlabs$copySource;
    @Override public int vandorlabs$copySource(){return vandorlabs$copySource;}
    @Override public void vandorlabs$copySource(int encodedIndex){vandorlabs$copySource=encodedIndex;}
    @Inject(method="ofPositioned",at=@At("RETURN"))
    private static void vandorlabs$captureCenter(int width,int height,List<ItemStack> items,CallbackInfoReturnable<CraftingInput.Positioned> callback){
        if(width!=3||height!=3)return;
        var positioned=callback.getReturnValue();var input=positioned.input();
        if(input.width()!=2||input.height()!=1||input.ingredientCount()!=2||positioned.top()!=1)return;
        int source=1-positioned.left();if(source<0||source>1)return;
        if(!(input.getItem(0).getItem() instanceof NativeTransmitterItem)||!(input.getItem(1).getItem() instanceof NativeTransmitterItem))return;
        ((TransmitterCraftingContext)input).vandorlabs$copySource(source+1);
    }
}
