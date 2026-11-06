package com.vandorlabs.neoforge.door;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Disposable real-client target fixture: production ordinary block and use transport, no Door art acceptance claim. */
@EventBusSubscriber(modid="vandorlabs")
public final class DoorTargetClientFixture {
    public static final BlockPos POS=new BlockPos(0,80,0),BARRIER=new BlockPos(-1,81,-1);
    private static NativeOrdinaryDoorBlock block;private static NativeDoorItem item;private static BlockEntityType<NativeProgrammableDoorEntity> type;private static MenuType<NativeDoorMenu> menu;
    public static boolean targeting(){return "door-target-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
    public static boolean dialog(){return "door-dialog-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
    public static boolean enabled(){return targeting()||dialog();}
    public static MenuType<NativeDoorMenu> menu(){return menu;}
    private static void need(boolean pass,String message){if(!pass)throw new IllegalStateException("Door target client server: "+message);}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){
            if(!enabled())return;var id=ResourceLocation.fromNamespaceAndPath("vandorlabs","door_target_client_fixture");
            event.register(Registries.BLOCK,h->{block=new NativeOrdinaryDoorBlock(BlockBehaviour.Properties.of().strength(2),()->type,()->menu,()->item,false);h.register(id,block);});
            event.register(Registries.ITEM,h->{item=new NativeDoorItem(block,new Item.Properties());h.register(id,item);});
            event.register(Registries.BLOCK_ENTITY_TYPE,h->{type=BlockEntityType.Builder.of((BlockPos pos,net.minecraft.world.level.block.state.BlockState state)->new NativeProgrammableDoorEntity(type,pos,state,false,false),block).build(null);h.register(id,type);});
            event.register(Registries.MENU,h->{menu=net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((window,inventory,b)->new NativeDoorMenu(menu,window,inventory,b.readBlockPos()));h.register(id,menu);});
        }
        @SubscribeEvent public static void payload(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){
            if(!enabled())return;DoorPayloads.register(event);event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException("Fixture mode length");return new Mode(b.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf b,Mode p){b.writeByte(p.operation);}},(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)mode(player,p.operation);}));
        }
    }
    public record Mode(int operation) implements CustomPacketPayload {public static final Type<Mode> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","door_target_client_mode"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
    private static void setup(ServerPlayer player){
        boolean glass=net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs","programmable_glass"));need(player.serverLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("vandorlabs","programmable_diagonal_glass")).isPresent()==glass,"diagonal glass recipe follows actual native ingredient availability");
        var level=player.serverLevel();var state=block.defaultBlockState().setValue(NativeProgrammableDoorBlock.FACING,Direction.SOUTH).setValue(NativeProgrammableDoorBlock.OPEN,true).setValue(NativeProgrammableDoorBlock.HINGE,DoorHingeSide.LEFT);
        level.setBlock(POS,state,Block.UPDATE_CLIENTS);level.setBlock(POS.above(),state.setValue(NativeProgrammableDoorBlock.HALF,DoubleBlockHalf.UPPER),Block.UPDATE_CLIENTS);var host=(NativeProgrammableDoorEntity)level.getBlockEntity(POS);host.installPlacement(NativeDoorSettings.fresh(false,false),ChannelList.EMPTY);host.publish();
        var box=NativeDoorGeometry.outline(host.settings(),Direction.SOUTH,true,true,false);need(box.minZ<0,"fixture leaf must extend outside owner voxel");double z=box.minZ*.5;player.setGameMode(GameType.SURVIVAL);player.getInventory().selected=0;player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.connection.teleport(box.minX-2.5,80,z,-90,0);player.inventoryMenu.broadcastChanges();
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;var level=player.serverLevel();for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);for(int y=80;y<84;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}setup(player);if(dialog()){player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()));player.getInventory().setItem(7,com.vandorlabs.neoforge.credentials.NativeCredentialItems.accessCard(2).copyWithCount(3));player.inventoryMenu.broadcastChanges();}System.out.println("[vandorlabs][door-target-client] fixture READY");}
    private static void mode(ServerPlayer player,int operation){var level=player.serverLevel();if(operation==10){var host=(NativeProgrammableDoorEntity)level.getBlockEntity(POS);need(host.getRedstoneChannels().equals(ChannelList.of(31,32)),"dialog channel bank");need(host.settings().securityMode()==4&&host.settings().securityAllowed()==64,"dialog security/filter");need(host.settings().door().design==15&&!host.settings().door().framed,"dialog design/frame");need(host.settings().glass().shade==18&&host.settings().glass().tint==0,"dialog independent glass");need(player.getInventory().getItem(7).getCount()==3,"filter must not consume card");System.out.println("[vandorlabs][door-dialog-client] dialog-roundtrip PASS");}else if(operation==1){need(!level.getBlockState(POS).getValue(NativeProgrammableDoorBlock.OPEN),"real use intent must close moved leaf");System.out.println("[vandorlabs][door-target-client] moved-use PASS");setup(player);level.setBlockAndUpdate(BARRIER,Blocks.STONE.defaultBlockState());}else if(operation==2){need(level.getBlockState(POS).getValue(NativeProgrammableDoorBlock.OPEN),"occluded click must leave Door open");System.out.println("[vandorlabs][door-target-client] occluded-use PASS");}else throw new IllegalArgumentException("Fixture mode");}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled())System.out.println(dialog()?"[vandorlabs][door-dialog-client] client-logout PASS":"[vandorlabs][door-target-client] client-logout PASS");}
}
