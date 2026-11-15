package com.vandorlabs.neoforge.join;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.copifier.*;
import com.vandorlabs.surface.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Four bounded production-host journeys; setup packets cannot apply copied settings. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeDirectionalJoinFixture {
    static final String[] IDS={"programmable_porthole_wall","programmable_porthole_block","programmable_glass","programmable_glass"};
    static final BlockPos ACK=new BlockPos(0,96,8);
    private static java.util.UUID actor;private static int operation,checks;
    public static boolean enabled(){return "directional-joins".equals(System.getProperty("vandorlabs.transmitterPhase"));}
    static BlockPos source(int family){return new BlockPos(family*8,96,0);}
    static BlockPos target(int family){return source(family).east(2);}
    private static void need(boolean yes,String why){checks++;if(!yes)throw new IllegalStateException("Directional join server op="+operation+": "+why);}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(enabled())event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException("Join fixture length");int n=b.readUnsignedByte();if(n>=12)throw new IllegalArgumentException("Join fixture operation");return new Mode(n);}public void encode(RegistryFriendlyByteBuf b,Mode p){b.writeByte(p.value());}},(p,c)->c.enqueueWork(()->advance((ServerPlayer)c.player(),p.value())));}
    }
    public record Mode(int value) implements CustomPacketPayload {
        static final Type<Mode> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","directional_join_fixture"));
        public Type<Mode> type(){return TYPE;}
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;actor=player.getUUID();var level=player.serverLevel();
        for(int x=-3;x<31;x++)for(int z=-2;z<6;z++){level.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<100;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        int selected=-1;for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)if(ModernFinishCatalog.ENTRIES.get(i).frame()!=null){selected=i;break;}need(selected>=0,"authored selected frame fixture");
        for(int family=0;family<IDS.length;family++) {
            var block=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("vandorlabs",IDS[family]));need(block!=Blocks.AIR,"canonical family "+IDS[family]);
            for(int i=-1;i<3;i++) {BlockPos pos=i<0?source(family):target(family).east(i);level.setBlock(pos,block.defaultBlockState(),3);var tile=level.getBlockEntity(pos);need(tile instanceof TextureJoinOwner,"typed join owner");if(family==3){var tag=tile.saveWithoutMetadata(level.registryAccess());tag.putInt("FrameFront",selected);tile.loadWithComponents(tag,level.registryAccess());}((TextureJoinOwner)tile).setTextureJoin(new TextureJoinSettings(i<0?4:3,0));tile.setChanged();level.sendBlockUpdated(pos,tile.getBlockState(),tile.getBlockState(),3);}
        }
        player.setGameMode(GameType.SURVIVAL);equip(player,0);System.out.println("[vandorlabs][directional-joins] setup PASS canonicalHosts/Survival/sourceNorthSouth/targetsHorizontal/selectedFrame");
    }
    private static void equip(ServerPlayer player,int family){player.getInventory().clearContent();player.getInventory().selected=0;var tool=new ItemStack(NativeCopifierRegistrations.ITEM.get());var data=new CompoundTag();data.putBoolean(NativeCopifierOptions.CONNECTED,true);data.putBoolean(NativeCopifierOptions.RESPECT_JOINS,true);tool.set(DataComponents.CUSTOM_DATA,CustomData.of(data));player.setItemInHand(InteractionHand.MAIN_HAND,tool);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,0,tool.copy()));player.inventoryMenu.broadcastChanges();BlockPos source=source(family);player.connection.teleport(source.getX()+.5,96,3,180,0);}
    private static void advance(ServerPlayer player,int op){need(player.getUUID().equals(actor)&&op==operation,"exact actor/order");int family=op/3;need(player.gameMode.getGameModeForPlayer()==GameType.SURVIVAL&&player.getInventory().selected==0&&player.getMainHandItem().is(NativeCopifierRegistrations.ITEM.get()),"actual Survival/tool/slot");var data=NativeCopifierClipboard.settings(NativeCopifierItem.data(player.getMainHandItem()));need(data.getCompound("texture_join_policy").getInt("Direction")==4,"actual captured NorthSouth clipboard");
        if(op%3==0){need(player.isShiftKeyDown(),"real shifted capture observed by server");BlockPos at=target(family);player.connection.teleport(at.getX()+1.5,96,3,180,0);}
        else if(op%3==1){need(!player.isShiftKeyDown(),"real nonshift apply");for(int i=0;i<3;i++)need(((TextureJoinOwner)player.serverLevel().getBlockEntity(target(family).east(i))).textureJoin().direction==4,"copied policy on original member "+i);System.out.println("[vandorlabs][directional-joins] applied PASS family="+family+" threeOriginalMembers");}
        else if(family+1<IDS.length)equip(player,family+1);
        player.serverLevel().setBlock(ACK.east(op),Blocks.STONE.defaultBlockState(),3);operation++;
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity().getUUID().equals(actor)){need(operation==12,"all exact journeys completed");System.out.println("[vandorlabs][directional-joins] logout PASS checks="+checks);}}
    private NativeDirectionalJoinFixture() { }
}
