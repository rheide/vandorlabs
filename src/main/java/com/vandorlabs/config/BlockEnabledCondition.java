package com.vandorlabs.config;

import com.google.gson.JsonObject;
import java.util.function.BooleanSupplier;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.IConditionFactory;
import net.minecraftforge.common.crafting.JsonContext;

/** Loaded through _factories.json after Forge resets crafting factories on recipe reload. */
public final class BlockEnabledCondition implements IConditionFactory {
    @Override
    public BooleanSupplier parse(JsonContext context, JsonObject json) {
        ResourceLocation id = new ResourceLocation(context.appendModId(JsonUtils.getString(json, "block")));
        return () -> BlockAvailability.recipeEnabled(id);
    }
}
