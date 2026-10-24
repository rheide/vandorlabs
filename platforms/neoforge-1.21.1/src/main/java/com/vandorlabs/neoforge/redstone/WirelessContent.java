package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.neoforge.VandorLabsModern;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Native registrations retain the existing wireless endpoint registry identities. */
public final class WirelessContent {
    private WirelessContent() { }
    public static final DeferredHolder<Block, WirelessBlock> TRANSMITTER = VandorLabsModern.BLOCKS.register("redstone_transmitter",
            () -> new WirelessBlock(true, properties()));
    public static final DeferredHolder<Block, WirelessBlock> RECEIVER = VandorLabsModern.BLOCKS.register("redstone_receiver",
            () -> new WirelessBlock(false, properties()));
    public static final DeferredHolder<Item, BlockItem> TRANSMITTER_ITEM = VandorLabsModern.ITEMS.register("redstone_transmitter",
            () -> new BlockItem(TRANSMITTER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> RECEIVER_ITEM = VandorLabsModern.ITEMS.register("redstone_receiver",
            () -> new BlockItem(RECEIVER.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessBlockEntity>> ENTITY = VandorLabsModern.ENTITIES.register("wireless_redstone",
            () -> BlockEntityType.Builder.of(WirelessBlockEntity::new, TRANSMITTER.get(), RECEIVER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<WirelessMenu>> MENU = VandorLabsModern.MENUS.register("wireless_redstone",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id, inventory, buffer) -> new WirelessMenu(id, inventory, buffer.readBlockPos())));
    public static void initialize() { }
    private static BlockBehaviour.Properties properties() { return BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(5, 6).sound(SoundType.METAL).requiresCorrectToolForDrops(); }
}
