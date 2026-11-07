package com.vandorlabs.neoforge.credentials;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Canonical mounted readers retain the released shared token_slot entity identity. */
public final class NativeReaderRegistrations {
 public static final String CARD_ID="access_card_slot",CHIP_ID="data_chip_slot",ENTITY_ID="token_slot";
 public static final DeferredHolder<Block,NativeCredentialReaderBlock> ACCESS_CARD_SLOT=VandorLabsBootstrap.BLOCKS.register(CARD_ID,()->block(false));
 public static final DeferredHolder<Block,NativeCredentialReaderBlock> DATA_CHIP_SLOT=VandorLabsBootstrap.BLOCKS.register(CHIP_ID,()->block(true));
 public static final DeferredHolder<Item,BlockItem> ACCESS_CARD_SLOT_ITEM=VandorLabsBootstrap.ITEMS.register(CARD_ID,()->new BlockItem(ACCESS_CARD_SLOT.get(),new Item.Properties()));
 public static final DeferredHolder<Item,BlockItem> DATA_CHIP_SLOT_ITEM=VandorLabsBootstrap.ITEMS.register(CHIP_ID,()->new BlockItem(DATA_CHIP_SLOT.get(),new Item.Properties()));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<NativeCredentialReaderEntity>> ENTITY=VandorLabsBootstrap.ENTITIES.register(ENTITY_ID,()->BlockEntityType.Builder.<NativeCredentialReaderEntity>of((pos,state)->{if(!(state.getBlock() instanceof NativeCredentialReaderBlock reader))throw new IllegalArgumentException("Reader block required");return new NativeCredentialReaderEntity(NativeReaderRegistrations.ENTITY.get(),pos,state,reader.chip());},ACCESS_CARD_SLOT.get(),DATA_CHIP_SLOT.get()).build(null));
 public static final DeferredHolder<MenuType<?>,MenuType<NativeCredentialReaderMenu>> MENU=VandorLabsBootstrap.MENUS.register(ENTITY_ID,()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new NativeCredentialReaderMenu(NativeReaderRegistrations.MENU.get(),id,inventory,buffer.readBlockPos())));
 private static NativeCredentialReaderBlock block(boolean chip){return new NativeCredentialReaderBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops(),chip,()->ENTITY.get(),()->MENU.get());}
 public static void register(net.neoforged.bus.api.IEventBus bus){bus.addListener(ReaderSettingsPayload::register);bus.addListener(ReaderTransmissionPayload::register);}
 private NativeReaderRegistrations(){}
}
