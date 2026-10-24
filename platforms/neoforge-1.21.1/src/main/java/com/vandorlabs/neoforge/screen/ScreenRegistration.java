package com.vandorlabs.neoforge.screen;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The programmable viewscreen adapter has isolated registries and runtime state. */
public final class ScreenRegistration {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,"vandorlabs");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"vandorlabs");
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"vandorlabs");
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,"vandorlabs");
    public static final DeferredHolder<Block,ProgrammableScreenBlock> BLOCK=BLOCKS.register("programmable_viewscreen",()->new ProgrammableScreenBlock(BlockBehaviour.Properties.of().strength(5,10).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item,BlockItem> ITEM=ITEMS.register("programmable_viewscreen",()->new BlockItem(BLOCK.get(),new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ProgrammableScreenEntity>> TILE=TILES.register("programmable_screen",()->BlockEntityType.Builder.of(ProgrammableScreenEntity::new,BLOCK.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<ScreenMenu>> MENU=MENUS.register("screen_selector",()->IMenuTypeExtension.create((id,inventory,data)->new ScreenMenu(id,inventory,data.readBlockPos(),data.readBoolean())));
    private ScreenRegistration(){}
    public static void install(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TILES.register(bus);MENUS.register(bus);bus.addListener(ScreenPayload::register);bus.addListener(RedstoneConfigurationPayload::register);}
}
