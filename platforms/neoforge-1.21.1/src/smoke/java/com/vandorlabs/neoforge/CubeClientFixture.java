package com.vandorlabs.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Disposable native scene and explicit mode synchronization protocol, never packaged. */
@EventBusSubscriber(modid="vandorlabs")
public final class CubeClientFixture {
    public static final BlockPos POS=new BlockPos(0,80,0),JOINED=new BlockPos(15,80,6);
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!System.getProperty("vandorlabs.cubePhase","").equals("client")||!(event.getEntity() instanceof ServerPlayer player))return;
        var level=player.serverLevel();for(int x=-5;x<20;x++)for(int z=-6;z<10;z++)level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(POS,VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState(),3);((ProgrammableBlockEntity)level.getBlockEntity(POS)).apply(new com.vandorlabs.ui.ProgrammableBlockDialog.State(8,1,false,new int[]{-1,-1,-1,-1,-1,-1},-1));
        for(int y=0;y<3;y++)for(int x=0;x<2;x++){BlockPos at=JOINED.offset(x,y,0);level.setBlock(at,VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState(),3);((ProgrammableBlockEntity)level.getBlockEntity(at)).apply(CubeRuntimeChecks.state(8,0,false));}
        player.setGameMode(GameType.CREATIVE);player.teleportTo(.5,80,-4);player.setYRot(0);player.setXRot(8);player.getInventory().selected=0;
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));
        player.getInventory().setItem(1,new ItemStack(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get()));
        var configured=new ItemStack(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get());((ProgrammableBlockEntity)level.getBlockEntity(POS)).saveToItem(configured,level.registryAccess());player.getInventory().setItem(2,configured);
        player.inventoryMenu.broadcastChanges();level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING).set(0,level.getServer());level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false,level.getServer());level.setWeatherParameters(0,6000,false,false);
        java.util.List<net.minecraft.world.entity.Entity> entities=new java.util.ArrayList<>();level.getAllEntities().forEach(entities::add);for(var entity:entities)if(entity instanceof net.minecraft.world.entity.Mob)entity.discard();
        level.setDayTime(6000);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());
        System.out.println("[vandorlabs][cube] client fixture READY");
    }
    public record Mode(int operation) implements CustomPacketPayload {
        static final Type<Mode> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","cube_smoke_mode"));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void payload(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){
            event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){
                public Mode decode(RegistryFriendlyByteBuf buffer){return new Mode(buffer.readUnsignedByte());}
                public void encode(RegistryFriendlyByteBuf buffer,Mode value){buffer.writeByte(value.operation);}
            },(packet,context)->context.enqueueWork(()->{
                if(!System.getProperty("vandorlabs.cubePhase","").equals("client")||!(context.player() instanceof ServerPlayer player))return;
                var tool=new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get());
                player.setGameMode(switch(packet.operation){case 1,5,15->GameType.SURVIVAL;case 2,3->GameType.ADVENTURE;case 4->GameType.SPECTATOR;default->GameType.CREATIVE;});
                if(packet.operation==3)tool.set(DataComponents.CAN_PLACE_ON,new net.minecraft.world.item.AdventureModePredicate(java.util.List.of(net.minecraft.advancements.critereon.BlockPredicate.Builder.block().of(Blocks.STONE).build()),false));
                player.setItemInHand(InteractionHand.MAIN_HAND,packet.operation==5||packet.operation==6?ItemStack.EMPTY:tool);player.inventoryMenu.broadcastChanges();
                if(packet.operation==14){var configured=new ItemStack(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get());((ProgrammableBlockEntity)player.serverLevel().getBlockEntity(POS)).saveToItem(configured,player.registryAccess());player.setItemInHand(InteractionHand.MAIN_HAND,configured);player.inventoryMenu.broadcastChanges();}
                if(packet.operation==15){player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));player.inventoryMenu.broadcastChanges();}
                BlockPos edited=JOINED.offset(1,2,0);
                if(packet.operation==8)((ProgrammableBlockEntity)player.serverLevel().getBlockEntity(edited)).apply(CubeRuntimeChecks.state(9,0,false));
                if(packet.operation==9)((ProgrammableBlockEntity)player.serverLevel().getBlockEntity(edited)).apply(CubeRuntimeChecks.state(8,0,false));
                if(packet.operation==10)player.serverLevel().setBlock(edited,Blocks.AIR.defaultBlockState(),3);
                if(packet.operation==11){player.serverLevel().setBlock(edited,VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState(),3);((ProgrammableBlockEntity)player.serverLevel().getBlockEntity(edited)).apply(CubeRuntimeChecks.state(8,0,false));}
                if(packet.operation==12||packet.operation==13)for(int y=0;y<3;y++)for(int x=0;x<2;x++)((ProgrammableBlockEntity)player.serverLevel().getBlockEntity(JOINED.offset(x,y,0))).apply(CubeRuntimeChecks.state(8,packet.operation==12?2:1,false));
                if(packet.operation==7){var tile=(ProgrammableBlockEntity)player.serverLevel().getBlockEntity(POS);CubeRuntimeChecks.require(tile.settings().facesEnabled&&tile.settings().face(2)==9&&tile.settings().layout==1,"actual GUI edits did not reach server");System.out.println("[vandorlabs][cube] server-client-actions PASS actual authenticated GUI payload roundtrip");}
            }));
        }
    }
}
