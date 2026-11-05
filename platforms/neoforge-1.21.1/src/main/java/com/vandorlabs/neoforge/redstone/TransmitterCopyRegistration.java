package com.vandorlabs.neoforge.redstone;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class TransmitterCopyRegistration {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(Registries.RECIPE_SERIALIZER,"vandorlabs");
    public static final DeferredHolder<RecipeSerializer<?>,SimpleCraftingRecipeSerializer<TransmitterCopyRecipe>> SERIALIZER=SERIALIZERS.register("redstone_transmitter_copy",()->new SimpleCraftingRecipeSerializer<>(TransmitterCopyRecipe::new));
    public static void register(IEventBus bus){SERIALIZERS.register(bus);}
    private TransmitterCopyRegistration(){}
}
