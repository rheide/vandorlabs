package com.vandorlabs.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

@Mod(VandorLabsModern.MODID)
public final class VandorLabsModern {
    public static final String MODID = "vandorlabs";
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MODID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredHolder<Block, ProgrammableBlock> PROGRAMMABLE_BLOCK = BLOCKS.register("programmable_block",
        () -> new ProgrammableBlock(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(5, 6).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Block, Block> MATTER_BLOCK = BLOCKS.register("programmable_matter_block",
        () -> new Block(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item, BlockItem> MATTER_BLOCK_ITEM = ITEMS.register("programmable_matter_block",
        () -> new BlockItem(MATTER_BLOCK.get(),new Item.Properties()));
    public static final DeferredHolder<Item, Item> MATTER_INGOT = ITEMS.register("programmable_matter_ingot", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> ALLOY_INGOT = ITEMS.register("industrial_alloy_ingot", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> CONFIGURIZER = ITEMS.register("configurizer", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<ProgrammableMenu>> PROGRAMMABLE_MENU = MENUS.register("programmable_block", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id, inventory, buffer) -> new ProgrammableMenu(id,inventory,buffer.readBlockPos())));
    public static final DeferredHolder<Item, BlockItem> PROGRAMMABLE_ITEM = ITEMS.register("programmable_block",
        () -> new BlockItem(PROGRAMMABLE_BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProgrammableBlockEntity>> PROGRAMMABLE_ENTITY = ENTITIES.register("programmable_viewscreen", () -> BlockEntityType.Builder.of(ProgrammableBlockEntity::new, PROGRAMMABLE_BLOCK.get()).build(null));
    static {
        TABS.register("vandorlabs", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.vandorlabs"))
            .icon(() -> PROGRAMMABLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> { output.accept(PROGRAMMABLE_ITEM.get());output.accept(CONFIGURIZER.get());output.accept(MATTER_INGOT.get());output.accept(MATTER_BLOCK_ITEM.get());output.accept(ALLOY_INGOT.get());com.vandorlabs.neoforge.screen.ScreenRegistration.creativeItems().forEach(output::accept);output.accept(com.vandorlabs.neoforge.redstone.WirelessContent.TRANSMITTER_ITEM.get());output.accept(com.vandorlabs.neoforge.redstone.WirelessContent.RECEIVER_ITEM.get()); }).build());
    }
    public VandorLabsModern(IEventBus bus) {
        FilesystemMaterials.initialize();
        com.vandorlabs.neoforge.redstone.WirelessContent.initialize();
        bus.addListener(com.vandorlabs.neoforge.redstone.WirelessPayload::register);
        com.vandorlabs.neoforge.screen.ScreenRegistration.install(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        bus.addListener(FinishPayload::register);
    }
}
