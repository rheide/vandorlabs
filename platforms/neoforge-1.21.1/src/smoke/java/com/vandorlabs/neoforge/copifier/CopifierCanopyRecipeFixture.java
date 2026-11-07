package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.canopy.*;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Canonical item adapters in the isolated recipe runtime until production activation. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class CopifierCanopyRecipeFixture {
    private static final Map<String,NativeCanopyBlock> blocks=new LinkedHashMap<>();
    private static BlockEntityType<NativeCanopyEntity> type;
    @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){
        if(!"copifier-crafting".equals(System.getProperty("vandorlabs.transmitterPhase")))return;
        event.register(Registries.BLOCK,h->{
            for(String kind:new String[]{"angled_front_1x1","angled_front_1x2","canopy","long_canopy","regular_canopy_glass","front_canopy_glass","large_front_canopy_glass","space_visor_2x1","space_visor_3x1","space_visor_5x1","space_visor_5x1_double_height"}){
                var id=ResourceLocation.fromNamespaceAndPath("vandorlabs",kind);
                if(com.vandorlabs.neoforge.VandorLabsBootstrap.BLOCKS.getEntries().stream().anyMatch(entry->entry.getId().equals(id)))continue;
                var block=new NativeCanopyBlock(kind,()->type,BlockBehaviour.Properties.of());blocks.put(kind,block);h.register(id,block);
            }
        });
        event.register(Registries.ITEM,h->blocks.forEach((kind,block)->h.register(ResourceLocation.fromNamespaceAndPath("vandorlabs",kind),new NativeCanopyItem(block,new Item.Properties()))));
        event.register(Registries.BLOCK_ENTITY_TYPE,h->{if(blocks.isEmpty())return;type=BlockEntityType.Builder.<NativeCanopyEntity>of((pos,state)->new NativeCanopyEntity(type,pos,state,((NativeCanopyBlock)state.getBlock()).kind()),blocks.values().toArray(Block[]::new)).build(null);h.register(ResourceLocation.parse("vandorlabs:copifier_canopy_recipe_fixture"),type);});
    }
}
