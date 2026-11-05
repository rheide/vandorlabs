package com.vandorlabs.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Small experimental native content slice; no programmable blocks or world conversion. */
@Mod("vandorlabs")
public final class VandorLabsBootstrap {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "vandorlabs");
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "vandorlabs");
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "vandorlabs");
    // Legacy setResistance(10) yields effective explosion resistance 10 * 3 / 5.
    public static final DeferredHolder<Block, Block> MATTER_BLOCK = BLOCKS.register("programmable_matter_block",
        () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5, 6)
            .sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item, BlockItem> MATTER_BLOCK_ITEM = ITEMS.register("programmable_matter_block",
        () -> new BlockItem(MATTER_BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> MATTER_INGOT = ITEMS.register("programmable_matter_ingot", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> ALLOY_INGOT = ITEMS.register("industrial_alloy_ingot", () -> new Item(new Item.Properties()));
    static {
        TABS.register("vandorlabs", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.vandorlabs"))
            .icon(() -> MATTER_BLOCK_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(MATTER_INGOT.get()); output.accept(MATTER_BLOCK_ITEM.get()); output.accept(ALLOY_INGOT.get());
            }).build());
    }
    public VandorLabsBootstrap(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); TABS.register(bus);
    }
}
