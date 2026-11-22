package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class NativeVehicleBindings {
    public static NativeVehicleControllerBlock CONTROLLER;
    public static BlockEntityType<NativeVehicleControllerEntity> CONTROLLER_ENTITY;
    public static EntityType<NativeGroundVehicle> VEHICLE;
    public static MenuType<NativeVehicleMenu> MENU;

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("vandorlabs",name);
    }

    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, h -> {
            CONTROLLER = new NativeVehicleControllerBlock(BlockBehaviour.Properties.of()
                    .strength(5,6).requiresCorrectToolForDrops().sound(SoundType.METAL));
            h.register(id("vehicle_controller"),CONTROLLER);
        });
        event.register(Registries.ITEM,h -> h.register(id("vehicle_controller"),new BlockItem(CONTROLLER,new Item.Properties())));
        event.register(Registries.BLOCK_ENTITY_TYPE,h -> {
            CONTROLLER_ENTITY = BlockEntityType.Builder.of(NativeVehicleControllerEntity::new,CONTROLLER).build(null);
            h.register(id("vehicle_controller"),CONTROLLER_ENTITY);
        });
        event.register(Registries.ENTITY_TYPE,h -> {
            VEHICLE = EntityType.Builder.<NativeGroundVehicle>of(NativeGroundVehicle::new,MobCategory.MISC)
                    .sized(1,1).clientTrackingRange(12).updateInterval(1).noSummon().build("vandorlabs:ground_vehicle");
            h.register(id("ground_vehicle"),VEHICLE);
        });
        event.register(Registries.MENU,h -> {
            MENU = IMenuTypeExtension.create(NativeVehicleMenu::new);
            h.register(id("vehicle_controller"),MENU);
        });
    }

    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey().location().equals(id("vandorlabs"))) event.accept(CONTROLLER);
    }

    public static boolean open(Player player, Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof NativeVehicleControllerEntity tile)
                || !NativeConfigurationAccess.canEditDialog(player,tile)) return false;
        if (player instanceof ServerPlayer server) {
            server.openMenu(new SimpleMenuProvider((window,inventory,actor) -> new NativeVehicleMenu(window,tile,actor),
                    CONTROLLER.getName()),buffer -> {buffer.writeBoolean(false);buffer.writeBlockPos(pos);});
        }
        return true;
    }

    public static boolean open(Player player, NativeGroundVehicle craft) {
        if (!NativeVehicleMenu.near(player,craft)) return false;
        if (player instanceof ServerPlayer server) {
            server.openMenu(new SimpleMenuProvider((window,inventory,actor) -> new NativeVehicleMenu(window,craft,actor),
                    Component.translatable("block.vandorlabs.vehicle_controller")),buffer -> {
                buffer.writeBoolean(true);
                buffer.writeVarInt(craft.getId());
            });
        }
        return true;
    }

    private NativeVehicleBindings() {}
}
