package com.vandorlabs.neoforge.mixin;
import com.vandorlabs.neoforge.ramp.NativeRampHeldOwners;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=InvWrapper.class,remap=false)
public abstract class NativeRampInventoryGuard {
 private boolean vandorlabs$held(){return NativeRampHeldOwners.blocked(((InvWrapper)(Object)this).getInv());}
 @Inject(method="insertItem",at=@At("HEAD"),cancellable=true)private void vandorlabs$rampInsert(int slot,ItemStack stack,boolean simulate,CallbackInfoReturnable<ItemStack> result){if(vandorlabs$held())result.setReturnValue(stack);}
 @Inject(method="extractItem",at=@At("HEAD"),cancellable=true)private void vandorlabs$rampExtract(int slot,int amount,boolean simulate,CallbackInfoReturnable<ItemStack> result){if(vandorlabs$held())result.setReturnValue(ItemStack.EMPTY);}
 @Inject(method="setStackInSlot",at=@At("HEAD"))private void vandorlabs$rampReplace(int slot,ItemStack stack,CallbackInfo result){if(vandorlabs$held())throw new IllegalStateException("Ramp holds this inventory");}
}
