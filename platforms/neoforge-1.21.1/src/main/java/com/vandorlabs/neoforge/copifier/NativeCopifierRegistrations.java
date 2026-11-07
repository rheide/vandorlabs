package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.registries.*;

/** Canonical tool, dedicated options window and vanilla copied-settings consumption. */
public final class NativeCopifierRegistrations {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(Registries.RECIPE_SERIALIZER,"vandorlabs");
    public static final DeferredHolder<Item,NativeCopifierItem> ITEM=VandorLabsBootstrap.ITEMS.register("duplifier",()->new NativeCopifierItem(new Item.Properties(),()->NativeCopifierRegistrations.MENU.get()));
    public static final DeferredHolder<MenuType<?>,MenuType<NativeCopifierMenu>> MENU=VandorLabsBootstrap.MENUS.register("duplifier",()->new MenuType<>((window,inventory)->new NativeCopifierMenu(NativeCopifierRegistrations.MENU.get(),window,inventory),net.minecraft.world.flag.FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<RecipeSerializer<?>,SimpleCraftingRecipeSerializer<NativeCopifierCraftingRecipe>> COPIED_SETTINGS=SERIALIZERS.register("copied_settings",()->new SimpleCraftingRecipeSerializer<>(NativeCopifierRegistrations::recipe));
    public static NativeCopifierCraftingRecipe recipe(CraftingBookCategory category){return new NativeCopifierCraftingRecipe(category,()->COPIED_SETTINGS.get(),NativeCopifierCraftingItems::apply);}
    public static void register(net.neoforged.bus.api.IEventBus bus){SERIALIZERS.register(bus);bus.addListener(NativeCopifierPayload::register);bus.addListener(NativeCopifierUndoPayload::register);}
    private NativeCopifierRegistrations(){}
}
