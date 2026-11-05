package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.redstone.*;
import com.vandorlabs.persistence.TriggerState;
import com.vandorlabs.redstone.ChannelList;
import java.util.Arrays;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Actual native Trigger adapters, byte bounds, ordinary world chunk unload and independent JVM persistence. */
@EventBusSubscriber(modid="vandorlabs")
public final class TriggerRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(2100,80,2100),REMOTE=new BlockPos(80000,80,80000),CHEST=BASE.above(5);
    private static final int CHANNEL=65441;
    private static int checks,ticks,stage;
    private static NativeTriggerBlockEntity unloaded;
    private static ServerLevel world;
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("Native Trigger: "+message);}
    private static TriggerState settings(boolean bands,int exact,boolean front){return new TriggerState(4,bands,exact,2,3,front,5,new int[]{2,0,1,2},new int[]{2,0,1,2});}
    private static NativeTriggerBlockEntity place(BlockPos pos){world.getChunkAt(pos);world.setBlock(pos,VandorLabsBootstrap.TRIGGER_BLOCK.get().defaultBlockState(),3);return (NativeTriggerBlockEntity)world.getBlockEntity(pos);}
    private static void expectVisual(NativeTriggerBlockEntity tile,int main,boolean front){var visual=tile.getModelData().get(ProgrammableBlockEntity.SETTINGS);require(visual!=null&&visual.mainTexture==main&&visual.facesEnabled==front,"immutable visual projection main="+main);if(front)for(int face=0;face<6;face++)require(visual.face(face)==(face==2?main:5),"front-only sides projection face="+face);}
    @SubscribeEvent public static void started(ServerStartedEvent event){
        String phase=System.getProperty("vandorlabs.transmitterPhase","");if(!phase.startsWith("trigger-"))return;
        checks=0;ticks=0;stage=0;world=event.getServer().overworld();world.getChunkAt(BASE);
        if(phase.equals("trigger-write")){
            var tile=place(BASE);require(tile.getBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)&&tile.getBlockState().is(net.minecraft.tags.BlockTags.NEEDS_STONE_TOOL)&&new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(tile.getBlockState())&&!new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(tile.getBlockState()),"native Trigger harvest tiers");require(tile.saveWithId(world.registryAccess()).getString("id").equals("vandorlabs:programmable_trigger_block"),"previous-release block entity registry ID");
            require(tile.configure(1,settings(true,-1,true),ChannelList.EMPTY),"configure current Trigger fields");
            tile.tickLocalInput();
            for(int signal=0;signal<=15;signal++){tile.setChannelLevel(signal);expectVisual(tile,signal==0?1:signal<=5?2:signal<=10?3:4,true);}
            require(tile.configure(1,settings(true,8,false),ChannelList.EMPTY),"exact match configure");tile.setChannelLevel(7);expectVisual(tile,1,false);tile.setChannelLevel(8);expectVisual(tile,3,false);
            tile.configure(1,settings(true,-1,true),ChannelList.EMPTY);tile.setChannelLevel(3);
            world.setBlock(BASE.below(2),Blocks.STONE.defaultBlockState(),3);
            for(int power:new int[]{1,8,15}){world.setBlock(BASE.below(),Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState().setValue(WeightedPressurePlateBlock.POWER,power),3);tile.localInputChanged();tile.tickLocalInput();require(tile.signalLevel()==Math.max(3,power),"actual vanilla analog local/channel max="+power);}
            world.removeBlock(BASE.below(),false);tile.localInputChanged();tile.tickLocalInput();require(tile.signalLevel()==3,"local withdrawal retains channel component");
            CompoundTag saved=tile.saveWithoutMetadata(world.registryAccess());saved.putString("FutureConfiguration","retained");CompoundTag opaque=new CompoundTag();opaque.putInt("foreign",23);saved.put("ForgeCaps",opaque);tile.loadWithComponents(saved,world.registryAccess());require(Arrays.equals(tile.triggerState().artworkModes(),new int[]{2,0,1,2}),"dormant playback retained without animation");
            transport(tile);tile=(NativeTriggerBlockEntity)world.getBlockEntity(BASE);tile.configure(1,settings(true,-1,true),ChannelList.of(CHANNEL));
            NativeRedstoneChannels.flush(world);
            var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(world);player.setPos(BASE.getX()+.5,BASE.getY(),BASE.getZ()+.5);player.setGameMode(GameType.CREATIVE);
            ItemStack transmitter=NativeTransmitterItem.variant(VandorLabsBootstrap.REDSTONE_TRANSMITTER.get(),3);NativeTransmitterItem.configure(transmitter,ChannelList.of(CHANNEL));player.setItemInHand(InteractionHand.MAIN_HAND,transmitter);transmitter.getItem().use(world,player,InteractionHand.MAIN_HAND);require(tile.signalLevel()==15,"real handheld publishes to actual Trigger consumer");
            var picked=VandorLabsBootstrap.TRIGGER_BLOCK.get().getCloneItemStack(tile.getBlockState(),new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(BASE),Direction.NORTH,BASE,false),world,BASE,player);verifyItem(picked);
            var drops=Block.getDrops(tile.getBlockState(),world,BASE,tile,null,new ItemStack(Items.IRON_PICKAXE));require(drops.size()==1,"configured Trigger loot count");verifyItem(drops.getFirst());
            world.setBlock(CHEST,Blocks.CHEST.defaultBlockState(),3);((net.minecraft.world.level.block.entity.ChestBlockEntity)world.getBlockEntity(CHEST)).setItem(0,picked);
            var stale=place(BASE.east(4));world.removeBlock(BASE.east(4),false);var replacement=place(BASE.east(4));replacement.configure(1,settings(true,-1,false),ChannelList.of(CHANNEL));NativeRedstoneChannels.flush(world);require(!stale.isChannelMemberActive()&&replacement.signalLevel()==15,"replacement before deferred flush registers only installed identity");
            unloaded=place(REMOTE);unloaded.configure(1,settings(true,-1,true),ChannelList.of(CHANNEL));NativeRedstoneChannels.flush(world);require(unloaded.signalLevel()==15,"remote actual Trigger active before unload");
            world.getServer().saveEverything(true,true,true);stage=1;
            System.out.println("[vandorlabs][trigger] unload WAIT ordinary chunk ticket expiry");
        }else if(phase.equals("trigger-read")){
            world.getChunkAt(REMOTE);var tile=(NativeTriggerBlockEntity)world.getBlockEntity(REMOTE);require(tile!=null,"second JVM actual Trigger exists");NativeRedstoneChannels.flush(world);tile.tickLocalInput();require(tile.signalLevel()==15,"second JVM discovers saved manual source");
            require(tile.triggerState().frontOnly&&tile.triggerState().bands&&tile.triggerState().sides==5&&Arrays.equals(tile.triggerState().artworkModes(),new int[]{2,0,1,2}),"second JVM face/bands/dormant fields");
            world.getChunkAt(CHEST);var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)world.getBlockEntity(CHEST);require(chest!=null,"configured item inventory persisted");verifyItem(chest.getItem(0));
            finish("read");
        }
    }
    private static void verifyItem(ItemStack item){
        require(item.is(VandorLabsBootstrap.TRIGGER_ITEM.get()),"configured item registry identity");var data=item.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);require(data!=null,"configured item data component");CompoundTag tag=data.copyTag();
        require(tag.getString("FutureConfiguration").equals("retained")&&tag.getCompound("ForgeCaps").getInt("foreign")==23,"configured item opaque previous fields");
        require(!tag.contains("ChannelLevel")&&!tag.contains("TriggerLocalLevel")&&!tag.contains("ChannelSignal"),"configured item excludes live signal fields");
        var restored=new NativeTriggerBlockEntity(BASE,VandorLabsBootstrap.TRIGGER_BLOCK.get().defaultBlockState());restored.loadWithComponents(tag,world.registryAccess());require(restored.triggerState().frontOnly&&restored.triggerState().sides==5&&Arrays.equals(restored.triggerState().artworkModes(),new int[]{2,0,1,2}),"configured item fields and dormant playback");
    }
    private static void transport(NativeTriggerBlockEntity tile){
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(world);player.setGameMode(GameType.CREATIVE);player.setPos(BASE.getX()+.5,BASE.getY()+1,BASE.getZ()+.5);player.containerMenu=new TriggerMenu(41,player.getInventory(),BASE);
        TriggerPayload packet=new TriggerPayload(BASE,41,1,settings(true,-1,true),ChannelList.of(CHANNEL,CHANNEL+1));var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),world.registryAccess());TriggerPayload.CODEC.encode(buffer,packet);require(buffer.readableBytes()==79,"bounded header71 plus2channels bytes");byte[] bytes=new byte[buffer.readableBytes()];buffer.getBytes(0,bytes);var decoded=TriggerPayload.CODEC.decode(buffer);require(decoded.channels().equals(packet.channels())&&decoded.state().frontOnly,"packet roundtrip complete current fields");buffer.release();
        for(int length=0;length<=bytes.length+1;length++)if(length!=bytes.length){var malformed=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(Arrays.copyOf(bytes,length)),world.registryAccess());try{TriggerPayload.CODEC.decode(malformed);throw new AssertionError("accepted malformed Trigger length="+length);}catch(IllegalArgumentException expected){}finally{malformed.release();}}
        for(int offset:new int[]{36,37,70}){byte[] bad=bytes.clone();bad[offset]=(byte)255;var malformed=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(bad),world.registryAccess());try{TriggerPayload.CODEC.decode(malformed);throw new AssertionError("accepted malformed Trigger flag/count");}catch(IllegalArgumentException expected){}finally{malformed.release();}}
        for(int offset:new int[]{12,20,38,54}){byte[] bad=bytes.clone();java.nio.ByteBuffer.wrap(bad).putInt(offset,offset==12?-1:99);var malformed=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(bad),world.registryAccess());try{TriggerPayload.CODEC.decode(malformed);throw new AssertionError("accepted invalid Trigger field at="+offset);}catch(IllegalArgumentException expected){}finally{malformed.release();}}
        require(TriggerPayload.apply(player,packet),"creative current menu edit");require(!TriggerPayload.apply(player,new TriggerPayload(BASE,42,1,settings(true,-1,true),ChannelList.EMPTY)),"wrong window rejected");
        player.setGameMode(GameType.SURVIVAL);require(!TriggerPayload.apply(player,packet),"survival missing tool denied");player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));require(TriggerPayload.apply(player,packet),"main hand tool permitted");player.setGameMode(GameType.SPECTATOR);require(!TriggerPayload.apply(player,packet),"spectator denied");player.setGameMode(GameType.CREATIVE);
        player.setPos(BASE.getX()+100,BASE.getY(),BASE.getZ());require(!TriggerPayload.apply(player,packet),"distant menu edit denied");player.setPos(BASE.getX()+.5,BASE.getY()+1,BASE.getZ()+.5);
        CompoundTag retained=tile.saveWithoutMetadata(world.registryAccess());world.removeBlock(BASE,false);var replacement=place(BASE);replacement.loadWithComponents(retained,world.registryAccess());require(!TriggerPayload.apply(player,packet),"stale replaced tile menu rejected");
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(stage!=1)return;if(++ticks>800)throw new IllegalStateException("Native Trigger: ordinary chunk unload timed out remote="+world.hasChunkAt(REMOTE)+" origin="+world.hasChunkAt(BASE));
        if(world.hasChunkAt(REMOTE)||world.hasChunkAt(BASE))return;
        require(!registered(unloaded),"actual unload removes network subscription, not just inactive filtering");
        require(!unloaded.isChannelMemberActive(),"actual chunk unload invalidates old receiver identity");require(NativeRedstoneChannels.level(world,REMOTE,CHANNEL)==15,"saved manual source remains active with origin and receiver chunks unloaded");
        world.getChunkAt(REMOTE);var restored=(NativeTriggerBlockEntity)world.getBlockEntity(REMOTE);require(restored!=null&&restored!=unloaded,"actual unload/reload created new saved Trigger instance");NativeRedstoneChannels.flush(world);restored.tickLocalInput();require(restored.signalLevel()==15,"reloaded Trigger rejoins retained stationary source");stage=2;
        System.out.println("[vandorlabs][trigger] actual-chunk-unload-reload PASS ticks="+ticks);finish("write");
    }
    private static boolean registered(NativeTriggerBlockEntity tile){
        try{var networks=NativeRedstoneChannels.class.getDeclaredField("NETWORKS");networks.setAccessible(true);Object network=((java.util.Map<?,?>)networks.get(null)).get(world);if(network==null)return false;var subscriptions=network.getClass().getDeclaredField("subscriptions");subscriptions.setAccessible(true);return ((java.util.Map<?,?>)subscriptions.get(network)).containsKey(tile);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Native Trigger: fixture subscription inspection",failure);}
    }
    private static void finish(String phase){world.getServer().saveEverything(true,true,true);System.out.println("[vandorlabs][trigger] server-"+phase+" PASS checks="+checks+" local analog/channel exact bands fronts dormant items auth bounds disk persistence");world.getServer().halt(false);}
}
