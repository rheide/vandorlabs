package com.vandorlabs.neoforge.plate;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
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
import net.minecraft.world.level.material.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class NativePlateBindings {
    public static NativePlateBlock BLOCK;public static Item ITEM;public static BlockEntityType<NativePlateEntity> ENTITY;public static MenuType<NativePlateMenu> MENU;
    public static boolean enabled(){return Boolean.parseBoolean(System.getProperty("vandorlabs.pressurePlate","true"));}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("vandorlabs",path);}
    @SubscribeEvent public static void register(RegisterEvent event){if(!enabled())return;
        event.register(Registries.BLOCK,h->{BLOCK=new NativePlateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(.5F).sound(SoundType.WOOD).noCollission().pushReaction(PushReaction.DESTROY));h.register(id("programmable_pressure_plate"),BLOCK);});
        event.register(Registries.ITEM,h->{ITEM=new BlockItem(BLOCK,new Item.Properties());h.register(id("programmable_pressure_plate"),ITEM);});
        event.register(Registries.BLOCK_ENTITY_TYPE,h->{ENTITY=BlockEntityType.Builder.of(NativePlateEntity::new,BLOCK).build(null);h.register(id("programmable_pressure_plate"),ENTITY);});
        event.register(Registries.MENU,h->{MENU=net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((window,inventory,buffer)->new NativePlateMenu(MENU,window,inventory,buffer.readBlockPos()));h.register(id("programmable_pressure_plate"),MENU);});
    }
    @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){NativePlatePayload.register(event);}
    @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event){if(enabled()&&event.getTabKey().location().equals(id("vandorlabs")))event.accept(ITEM);}
    public static boolean open(Player actor,Level level,BlockPos pos){if(!enabled()||MENU==null||!NativeConfigurationAccess.canEditDialog(actor,level,pos)||!(level.getBlockEntity(pos) instanceof NativePlateEntity tile)||tile.inert())return false;if(actor instanceof ServerPlayer player){if(!player.getServer().isSameThread())return false;player.openMenu(new SimpleMenuProvider((window,inventory,ignored)->new NativePlateMenu(MENU,window,tile),tile.getBlockState().getBlock().getName()),buffer->buffer.writeBlockPos(pos));}return true;}
    private NativePlateBindings(){}
}
