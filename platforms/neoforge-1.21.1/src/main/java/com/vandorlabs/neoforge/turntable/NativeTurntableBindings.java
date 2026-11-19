package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Development binding for the Turntable ownership protocol. */
@EventBusSubscriber(modid = "vandorlabs", bus = EventBusSubscriber.Bus.MOD)
public final class NativeTurntableBindings {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vandorlabs", "turntable_controller");
    static NativeTurntableBlock block;
    static BlockEntityType<NativeTurntableEntity> entity;
    static MenuType<NativeTurntableMenu> menu;

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (!Boolean.getBoolean("vandorlabs.turntableControllers")) return;
        event.register(Registries.BLOCK, entries -> {
            block = new NativeTurntableBlock(BlockBehaviour.Properties.of().strength(5, 6)
                    .sound(SoundType.METAL).requiresCorrectToolForDrops());
            entries.register(ID, block);
        });
        event.register(Registries.ITEM, entries -> entries.register(ID, new BlockItem(block, new Item.Properties())));
        event.register(Registries.BLOCK_ENTITY_TYPE, entries -> {
            entity = BlockEntityType.Builder.of(NativeTurntableEntity::new, block).build(null);
            entries.register(ID, entity);
            NativeTurntableTransfers.register();
        });
        event.register(Registries.MENU, entries -> {
            menu = net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (window, inventory, buffer) -> new NativeTurntableMenu(window, inventory, buffer.readBlockPos(), buffer.readUUID()));
            entries.register(ID, menu);
        });
    }

    @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        if (!Boolean.getBoolean("vandorlabs.turntableControllers")) return;
        NativeTurntableAnglePayload.register(event);
        NativeTurntableSignalsPayload.register(event);
    }

    static boolean open(Player player, Level world, BlockPos pos) {
        if (menu == null || !world.hasChunkAt(pos) || !(world.getBlockEntity(pos) instanceof NativeTurntableEntity tile)
                || !tile.available() || !NativeConfigurationAccess.canEditDialog(player, tile)) return false;
        if (player instanceof ServerPlayer server) {
            var container = new SimpleMenuProvider(
                    (window, inventory, ignored) -> new NativeTurntableMenu(window, inventory, pos, tile.key()),
                    block.getName());
            server.openMenu(container, buffer -> {
                buffer.writeBlockPos(pos);
                buffer.writeUUID(tile.key());
            });
        }
        return true;
    }

    @EventBusSubscriber(modid = "vandorlabs", bus = EventBusSubscriber.Bus.MOD,
            value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Client {
        @SubscribeEvent public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
            if (menu != null) event.register(menu, NativeTurntableScreen::new);
        }

        @SubscribeEvent public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
            if (entity != null) event.registerBlockEntityRenderer(entity, NativeTurntableRenderer::new);
        }

        @SubscribeEvent public static void reloaded(net.neoforged.neoforge.client.event.ModelEvent.BakingCompleted event) {
            NativeTurntableRenderer.clear();
        }
    }

    private NativeTurntableBindings() {}
}
