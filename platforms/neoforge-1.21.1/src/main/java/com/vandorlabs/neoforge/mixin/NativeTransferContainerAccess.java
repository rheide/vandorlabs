package com.vandorlabs.neoforge.mixin;

import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CompoundContainer.class)
public interface NativeTransferContainerAccess {
    @Accessor("container1")
    Container vandorlabs$firstTransferContainer();

    @Accessor("container2")
    Container vandorlabs$secondTransferContainer();
}
