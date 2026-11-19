package com.vandorlabs.neoforge.copier;

import com.vandorlabs.neoforge.compat.jei.NativeMachineJeiPlugin;
import com.vandorlabs.neoforge.legacylanding.NativeLegacyLandingBindings;
import com.vandorlabs.neoforge.legacylanding.NativeLegacyLandingKind;
import com.vandorlabs.neoforge.surface.NativeSurfaceBindings;
import mezz.jei.api.constants.VanillaTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.fml.ModList;

import static com.vandorlabs.neoforge.copier.NativeCopyPlayerFixture.require;

/** Loaded only by the optional installed-JEI fixture. */
final class NativeCopyCatalogJeiChecks {
    static boolean check() {
        require(ModList.get().isLoaded("jei"), "optional catalog fixture has a running JEI mod");
        var runtime = NativeMachineJeiPlugin.runtime();
        if (runtime == null) return false;
        var ingredients = runtime.getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK);
        var copy = NativeCopyBindings.BLOCK.asItem();
        var surface = NativeSurfaceBindings.BLOCK.asItem();
        require(ingredients.stream().filter(stack -> stack.is(copy)).count() == 1, "JEI registers one public Copy Controller ingredient");
        require(ingredients.stream().filter(stack -> stack.is(surface)).count() == 1, "JEI registers one public Programmable Surface ingredient");
        var filter = runtime.getIngredientFilter();
        var originalFilter = filter.getFilterText();
        try {
            filter.setFilterText("Copy Controller");
            require(filter.getFilteredIngredients(VanillaTypes.ITEM_STACK).stream().filter(stack -> stack.is(copy)).count() == 1,
                "JEI search exposes the Copy Controller");
            require(NativeLegacyLandingBindings.ITEMS.size() == NativeLegacyLandingKind.values().length,
                "all intentionally hidden compatibility items exist");
            var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.parse("vandorlabs:vandorlabs"));
            for (var entry : NativeLegacyLandingBindings.ITEMS.entrySet()) {
                var item = entry.getValue();
                var id = ResourceLocation.fromNamespaceAndPath("vandorlabs", entry.getKey().id);
                require(BuiltInRegistries.ITEM.getOptional(id).orElse(null) == item,
                    "compatibility item retains its registry identity " + id);
                require(tab.getDisplayItems().stream().noneMatch(stack -> stack.is(item))
                    && CreativeModeTabs.searchTab().getDisplayItems().stream().noneMatch(stack -> stack.is(item)),
                    "registered compatibility item stays outside creative and search tabs");
                require(ingredients.stream().noneMatch(stack -> stack.is(item)),
                    "default JEI ingredient registry omits the compatibility item");
            }
        } finally {
            filter.setFilterText(originalFilter);
        }
        System.out.println("[vandorlabs][copy-player] JEI catalog PASS publicCopy/surface/browserSearch/hiddenCompatibility");
        return true;
    }

    private NativeCopyCatalogJeiChecks() {}
}
