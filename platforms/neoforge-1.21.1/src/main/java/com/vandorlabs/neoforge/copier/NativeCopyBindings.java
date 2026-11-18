package com.vandorlabs.neoforge.copier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Standalone Copy Controller registration. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class NativeCopyBindings {
    public static NativeCopyControllerBlock BLOCK;public static BlockEntityType<NativeCopyControllerEntity> ENTITY;public static MenuType<NativeCopyMenu> MENU;
    public static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("vandorlabs",path);}
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(Registries.BLOCK,helper->{BLOCK=new NativeCopyControllerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).requiresCorrectToolForDrops().sound(SoundType.METAL));helper.register(id("copy_controller"),BLOCK);});
        event.register(Registries.ITEM,helper->helper.register(id("copy_controller"),new BlockItem(BLOCK,new Item.Properties())));
        event.register(Registries.BLOCK_ENTITY_TYPE,helper->{ENTITY=BlockEntityType.Builder.of((pos,state)->new NativeCopyControllerEntity(ENTITY,pos,state),BLOCK).build(null);helper.register(id("copy_controller"),ENTITY);NativeCopyService.register();});
        event.register(Registries.MENU,helper->{MENU=net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((window,inventory,buffer)->new NativeCopyMenu(MENU,window,inventory,buffer.readBlockPos(),buffer.readUUID()));helper.register(id("copy_controller"),MENU);});
    }
    @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event){if(event.getTabKey().location().equals(id("vandorlabs")))event.accept(BLOCK);}
    @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){NativeCopySettingsPayload.register(event);}
    public static boolean open(Player actor,Level world,BlockPos pos){if(MENU==null||!world.hasChunkAt(pos)||!(world.getBlockEntity(pos) instanceof NativeCopyControllerEntity tile)||tile.inert()||!com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(actor,tile))return false;if(actor instanceof ServerPlayer player){if(!player.getServer().isSameThread())return false;tile.authorize(player);player.openMenu(new SimpleMenuProvider((window,inventory,ignored)->new NativeCopyMenu(MENU,window,tile),tile.getBlockState().getBlock().getName()),buffer->{buffer.writeBlockPos(pos);buffer.writeUUID(tile.key());});}return true;}
    private NativeCopyBindings(){}
}
