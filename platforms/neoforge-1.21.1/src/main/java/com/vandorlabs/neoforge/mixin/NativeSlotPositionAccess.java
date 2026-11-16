package com.vandorlabs.neoforge.mixin;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Client layout only: preserve the exact server-owned slot identities and indices on resize. */
@Mixin(Slot.class)
public interface NativeSlotPositionAccess {
 @Mutable @Accessor("x") void vandorlabs$slotX(int x);
 @Mutable @Accessor("y") void vandorlabs$slotY(int y);
}
