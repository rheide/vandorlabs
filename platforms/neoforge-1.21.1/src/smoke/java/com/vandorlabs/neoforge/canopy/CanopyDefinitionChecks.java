package com.vandorlabs.neoforge.canopy;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Explicit private staging exercises real DeferredRegister definitions without a default bootstrap call. */
@EventBusSubscriber(modid="vandorlabs")
public final class CanopyDefinitionChecks {
    private static String phase(){return System.getProperty("vandorlabs.transmitterPhase","");}
    private static boolean enabled(){return phase().startsWith("canopy-definitions-");}
    private static int checks;private static boolean done;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Canopy canonical definitions: "+why);}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void construct(net.neoforged.fml.event.lifecycle.FMLConstructModEvent event){if(enabled()&&!phase().endsWith("default"))NativeCanopyRegistrations.register(net.neoforged.fml.ModList.get().getModContainerById("vandorlabs").orElseThrow().getEventBus());}
    }
    private static BlockPos position(int index){return new BlockPos(8+index*32,240,8);}
    private static void run(ServerLevel level){
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.fromString("dcdd0e5a-72cd-4d50-80f9-4adc9b4f8a21"),"CanopyDefinitions"));player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);int index=0;
        for(String kind:NativeCanopyKinds.IDS){var id=ResourceLocation.fromNamespaceAndPath("vandorlabs",kind);var block=NativeCanopyRegistrations.BLOCKS.get(kind).get();var item=NativeCanopyRegistrations.ITEMS.get(kind).get();need(BuiltInRegistries.BLOCK.get(id)==block&&BuiltInRegistries.ITEM.get(id)==item&&item.getBlock()==block,"exact retained IDs="+kind);need(block.kind().equals(kind)&&!block.defaultBlockState().requiresCorrectToolForDrops(),"Source family/tool policy="+kind);need(block.getExplosionResistance()==1.5F,"Source actual resistance="+kind);var pos=position(index++);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.getChunkAt(pos.offset(x*16,0,z*16));
            if(phase().endsWith("write")){level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),18);player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-3);var held=new ItemStack(item,2);player.setItemInHand(InteractionHand.MAIN_HAND,held);var hit=new BlockHitResult(new Vec3(pos.getX()+.5,pos.getY(),pos.getZ()+.5),Direction.UP,pos.below(),false);need(item.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction()&&held.getCount()==1,"canonical specialized Survival placement="+kind);}
            var tile=NativeCanopyLifecycle.at(level,pos);if(tile!=null)tile=NativeCanopyLifecycle.owner(tile);need(tile!=null&&tile.getType()==NativeCanopyRegistrations.ENTITY.get()&&tile.kind().equals(kind),"shared Source canopy BE identity="+kind);NativeCanopyLifecycle.prepare(tile);need(tile.available()&&tile.publication().draws(),"installed owner="+kind);need(level.getBlockState(pos).getDestroySpeed(level,pos)==1.5F&&level.getBlockState(pos).getLightBlock(level,pos)==0,"Source hardness/light="+kind);
            var recipe=level.getRecipeManager().byKey(id).orElseThrow(()->new AssertionError("canonical recipe="+kind));need(recipe.value().getResultItem(level.registryAccess()).is(item),"canonical result="+kind);need(BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.parse("vandorlabs:canopy"))==tile.getType(),"retained entity ID="+kind);
        }
        need(BuiltInRegistries.MENU.get(ResourceLocation.parse("vandorlabs:canopy"))==NativeCanopyRegistrations.MENU.get(),"canonical dedicated menu type");
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){if(!enabled()||done)return;done=true;var server=event.getServer();try{if(phase().endsWith("default")){for(String kind:NativeCanopyKinds.IDS){var id=ResourceLocation.fromNamespaceAndPath("vandorlabs",kind);need(BuiltInRegistries.BLOCK.get(id) instanceof NativeCanopyBlock&&BuiltInRegistries.ITEM.get(id) instanceof NativeCanopyItem,"default canonical family registered="+kind);need(server.getRecipeManager().byKey(id).isPresent(),"default original recipe="+kind);}need(BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(ResourceLocation.parse("vandorlabs:canopy"))&&BuiltInRegistries.MENU.containsKey(ResourceLocation.parse("vandorlabs:canopy")),"default retained owner/menu registered");}else run(server.overworld());server.saveEverything(true,true,true);System.out.println("[vandorlabs][canopy-definitions] "+phase().substring("canopy-definitions-".length())+" PASS checks="+checks+" canonical11/DeferredRegister/sharedBE/SourceProperties/recipes/placement/restart; default registered");server.halt(false);}catch(Throwable failure){failure.printStackTrace();server.halt(false);throw failure;}}
}
