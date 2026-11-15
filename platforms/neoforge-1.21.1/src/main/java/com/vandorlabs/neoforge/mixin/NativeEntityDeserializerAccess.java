package com.vandorlabs.neoforge.mixin;

import java.util.concurrent.Executor;
import net.minecraft.util.thread.ProcessorMailbox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** One native entity-deserializer work item; never the MinecraftServer task queue. */
@Mixin(ProcessorMailbox.class)
public interface NativeEntityDeserializerAccess {
    @Accessor("dispatcher") Executor vandorlabs$entityDispatcher();
    @Invoker("pollTask") boolean vandorlabs$pollEntityDeserializer();
}
