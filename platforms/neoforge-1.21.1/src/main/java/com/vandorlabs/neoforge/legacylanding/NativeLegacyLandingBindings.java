package com.vandorlabs.neoforge.legacylanding;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
/** Compatibility IDs are private until the stationary fallback and typed conversion are accepted. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class NativeLegacyLandingBindings {
 public static final Map<NativeLegacyLandingKind,NativeLegacyLandingBlock> BLOCKS=new EnumMap<>(NativeLegacyLandingKind.class);
 public static final Map<NativeLegacyLandingKind,NativeLegacyLandingItem> ITEMS=new EnumMap<>(NativeLegacyLandingKind.class);
 private static final Map<NativeLegacyLandingKind,BlockEntityType<NativeLegacyLandingEntity>> TYPES=new EnumMap<>(NativeLegacyLandingKind.class);
 public static Collection<BlockEntityType<NativeLegacyLandingEntity>> types(){return Set.copyOf(TYPES.values());}
 public static net.minecraft.world.inventory.MenuType<NativeLegacyLandingMenu> MENU;
 public static boolean open(net.minecraft.world.entity.player.Player actor,net.minecraft.world.level.Level world,net.minecraft.core.BlockPos pos){return NativeLegacyLandingConfiguration.open(actor,world,pos);}
 public static boolean enabled(){return Boolean.getBoolean("vandorlabs.telescopicLanding") || NativeLegacyAutomaticConversion.ENABLED;}
 static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("vandorlabs",path);}
 public static BlockEntityType<NativeLegacyLandingEntity> type(BlockState state){return TYPES.get(((NativeLegacyLandingBlock)state.getBlock()).kind);}
 @SubscribeEvent public static void register(RegisterEvent event){if(!enabled())return;
  event.register(Registries.BLOCK,h->{for(var kind:NativeLegacyLandingKind.values()){var block=new NativeLegacyLandingBlock(kind,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape());BLOCKS.put(kind,block);h.register(id(kind.id),block);}});
  event.register(Registries.ITEM,h->{for(var kind:NativeLegacyLandingKind.values()){var item=new NativeLegacyLandingItem(BLOCKS.get(kind),new Item.Properties());ITEMS.put(kind,item);h.register(id(kind.id),item);}});
  event.register(Registries.BLOCK_ENTITY_TYPE,h->{for(boolean ski:new boolean[]{false,true}){var kinds=Arrays.stream(NativeLegacyLandingKind.values()).filter(kind->kind.ski==ski).toList();var type=BlockEntityType.Builder.of(NativeLegacyLandingEntity::new,kinds.stream().map(BLOCKS::get).toArray(Block[]::new)).build(null);for(var kind:kinds)TYPES.put(kind,type);h.register(id(ski?"ski_landing_gear":"landing_gear"),type);}});
  event.register(Registries.MENU,h->{MENU=net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((window,inventory,buffer)->new NativeLegacyLandingMenu(window,buffer.readBlockPos(),buffer.readUUID(),inventory.player));h.register(id("landing_gear"),MENU);});
 }
 @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){NativeLegacyLandingPayload.register(event);}
 // Source retires all four items from creative/search; registration does not add a tab entry.
 private NativeLegacyLandingBindings(){}
}
