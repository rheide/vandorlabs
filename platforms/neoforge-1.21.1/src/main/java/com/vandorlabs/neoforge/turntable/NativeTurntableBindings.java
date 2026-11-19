package com.vandorlabs.neoforge.turntable;

import net.minecraft.core.registries.Registries;
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
    }

    private NativeTurntableBindings() {}
}
