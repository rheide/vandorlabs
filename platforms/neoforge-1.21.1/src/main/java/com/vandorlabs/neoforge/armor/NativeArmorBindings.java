package com.vandorlabs.neoforge.armor;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = "vandorlabs", bus = EventBusSubscriber.Bus.MOD)
public final class NativeArmorBindings {
    public static final Map<ArmorItem.Type, ResourceLocation> IDS = Map.of(
        ArmorItem.Type.HELMET, id("programmable_helmet"), ArmorItem.Type.CHESTPLATE, id("programmable_chestplate"),
        ArmorItem.Type.LEGGINGS, id("programmable_leggings"), ArmorItem.Type.BOOTS, id("programmable_boots"));
    private static final Map<ArmorItem.Type, NativeArmorItem> ITEMS = new EnumMap<>(ArmorItem.Type.class);
    public static MenuType<NativeArmorMenu> MENU;

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ITEM, items -> {
            for (var type : ArmorItem.Type.values()) {
                if (!IDS.containsKey(type)) continue;
                var item = new NativeArmorItem(type);
                ITEMS.put(type, item);
                items.register(IDS.get(type), item);
            }
        });
        event.register(Registries.MENU, menus -> {
            MENU = IMenuTypeExtension.create((window, inventory, buffer) -> {
                if (!buffer.readBoolean()) return new NativeArmorMenu(window, inventory, buffer.readEnum(InteractionHand.class));
                var target = inventory.player.level().getEntity(buffer.readVarInt());
                var slot = buffer.readEnum(EquipmentSlot.class);
                return new NativeArmorMenu(window, inventory, target instanceof ArmorStand stand ? stand : null, slot);
            });
            menus.register(id("programmable_armor"), MENU);
        });
    }

    @SubscribeEvent
    public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(id("vandorlabs"))) ITEMS.values().forEach(event::accept);
    }

    public record Edit(int window, int choice) implements CustomPacketPayload {
        static final Type<Edit> TYPE = new Type<>(id("programmable_armor"));
        static final StreamCodec<RegistryFriendlyByteBuf, Edit> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Edit::window, ByteBufCodecs.VAR_INT, Edit::choice, Edit::new);

        @Override
        public Type<Edit> type() { return TYPE; }
    }

    public record InventorySample(int window, int slot) implements CustomPacketPayload {
        static final Type<InventorySample> TYPE = new Type<>(id("programmable_armor_inventory_sample"));
        static final StreamCodec<RegistryFriendlyByteBuf, InventorySample> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, InventorySample::window, ByteBufCodecs.VAR_INT, InventorySample::slot, InventorySample::new);

        @Override
        public Type<InventorySample> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void packets(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Edit.TYPE, Edit.CODEC, (message, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof NativeArmorMenu menu
                && menu.containerId == message.window()) menu.configure(player, message.choice());
        }));
        registrar.playToServer(InventorySample.TYPE, InventorySample.CODEC, (message, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof NativeArmorMenu menu
                && menu.containerId == message.window()) menu.sampleInventory(player, message.slot());
        }));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("vandorlabs", path);
    }

    private NativeArmorBindings() {}
}
