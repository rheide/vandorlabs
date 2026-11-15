package com.vandorlabs.neoforge.plate;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.persistence.PressurePlateSettings;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Focused physical placement/source policy and actual saved-state read; no Vehicle adapter. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativePlateRuntimeChecks {
    private static final BlockPos POS=new BlockPos(144,96,8);private static final int CHANNEL=65431;
    private static ServerLevel world;private static net.neoforged.neoforge.common.util.FakePlayer actor;private static int ticks,checks;private static boolean running;
    private static String phase(){return System.getProperty("vandorlabs.platePhase","");}
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Native plate tick="+ticks+": "+why);}
    private static NativePlateEntity tile(){return (NativePlateEntity)world.getBlockEntity(POS);}
    @SubscribeEvent public static void start(ServerStartedEvent event){if(phase().isEmpty())return;world=event.getServer().overworld();world.setChunkForced(POS.getX()>>4,POS.getZ()>>4,true);world.getChunkAt(POS);actor=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(world);actor.setGameMode(GameType.SURVIVAL);actor.setPos(144.5,96,5);running=true;}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(!running)return;try{if(++ticks<15)return;if(phase().equals("read")){need(world.getBlockState(POS).is(NativePlateBindings.BLOCK)&&tile()!=null&&!tile().inert(),"saved installed healthy plate");need(tile().mode()==2&&tile().sensitivity()==2&&tile().getRedstoneChannels().equals(ChannelList.of(CHANNEL)),"saved choices/channels exact");need(tile().output()==15&&NativeRedstoneChannels.level(world,POS,CHANNEL)==15,"released independent source re-registered after cold load");finish();return;}
        write();finish();
    }catch(Throwable failure){running=false;failure.printStackTrace();System.out.println("[vandorlabs][plate-runtime] "+phase()+" FAIL "+failure);world.getServer().halt(false);}}
    private static void write(){world.setBlock(POS.below(),Blocks.STONE.defaultBlockState(),3);world.setBlock(POS,Blocks.AIR.defaultBlockState(),3);var item=new ItemStack(NativePlateBindings.ITEM);actor.setItemInHand(InteractionHand.MAIN_HAND,item);
        need(actor.gameMode.useItemOn(actor,world,item,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(POS.below()).add(0,.5,0),Direction.UP,POS.below(),false)).consumesAction()&&item.isEmpty(),"actual Survival native placement consumed one");
        var tile=tile();need(tile!=null&&!tile.inert()&&tile.mode()==1&&tile.sensitivity()==0,"Source defaults");need(tile.configureTransmission(actor,ChannelList.of(CHANNEL),16,true),"standard inline transmission settings");
        var spawned=new ArrayList<ItemEntity>();
        try{for(int sensitivity=0;sensitivity<3;sensitivity++)for(int count:new int[]{0,1,2,15,16,149,150,151}){
            for(var entity:spawned){entity.moveTo(160,100,0);entity.discard();}spawned.clear();
            for(int i=0;i<count;i++){var entity=new ItemEntity(world,POS.getX()+.5,POS.getY()+.02,POS.getZ()+.5,new ItemStack(Items.IRON_INGOT,64));entity.setNoGravity(true);entity.setDeltaMovement(Vec3.ZERO);need(world.addFreshEntity(entity),"real native entity fixture");spawned.add(entity);}
            need(tile.configure(actor,NativePlateEntity.DEFAULT_TEXTURE,1,sensitivity),"native plate configure");
            int expected=PressurePlateSettings.measure(sensitivity,count,count>0);need(world.getBlockState(POS).getValue(NativePlateBlock.POWER)==expected&&tile.output()==expected,"Source entity-count float-ceil kind="+sensitivity+" count="+count);
            need(NativeRedstoneChannels.level(world,POS,CHANNEL)==expected,"independent numeric channel source");for(var direction:Direction.values()){need(world.getBlockState(POS).getSignal(world,POS,direction)==expected,"native weak physical output");need(world.getBlockState(POS).getDirectSignal(world,POS,direction)==(direction==Direction.UP?expected:0),"native strong physical output");}
            need(NativePlateBindings.BLOCK.releaseTicks(world,POS)==(sensitivity==0?20:10),"Source release cadence");
        }}finally{for(var entity:spawned){entity.moveTo(160,100,0);entity.discard();}}
        need(tile.configure(actor,NativePlateEntity.DEFAULT_TEXTURE,2,2),"released mode selection");need(tile.output()==15&&world.getBlockState(POS).getValue(NativePlateBlock.POWER)==0,"channel inverse does not depress physical plate");
        var saved=tile.saveWithoutMetadata(world.registryAccess());var copy=new NativePlateEntity(POS,world.getBlockState(POS));copy.loadWithComponents(saved,world.registryAccess());need(copy.configuration().equals(tile.configuration()),"native own settings roundtrip");
        var bad=saved.copy();bad.putString("PlateMode","unsupported");var opaque=new CompoundTag();opaque.putString("thirdParty","opaque");bad.put("ForgeCaps",opaque);var inert=new NativePlateEntity(POS,world.getBlockState(POS));inert.loadWithComponents(bad,world.registryAccess());need(inert.inert()&&inert.saveWithoutMetadata(world.registryAccess()).equals(bad),"unsupported own NBT retained inert/exact incl opaque Caps");
        var publicTag=tile.getUpdateTag(world.registryAccess());need(!publicTag.contains("ForgeCaps")&&!publicTag.contains("ForgeData")&&!publicTag.contains("neoforge:attachments"),"bounded public projection excludes private envelopes");
        var bytes=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),world.registryAccess());try{var message=new NativePlatePayload(17,NativePlateEntity.DEFAULT_TEXTURE,2,2,ChannelList.of(CHANNEL),16,true,true);NativePlatePayload.CODEC.encode(bytes,message);int size=bytes.readableBytes();need(size==20&&NativePlatePayload.CODEC.decode(bytes).equals(message)&&!bytes.isReadable(),"20-byte one-channel edit codec roundtrip");}finally{bytes.release();}
        System.out.println("[vandorlabs][plate-runtime] packet one-channel=20B bounded64/private-envelope-free");
    }
    private static void finish(){running=false;world.getServer().saveEverything(true,true,true);System.out.println("[vandorlabs][plate-runtime] "+phase()+" PASS checks="+checks+" nativeSurvival/threeSensingModes24Counts/physicalAnalog/channelSource/releasedMode/ownNBT/inertOpaque/packet/restart");world.getServer().halt(false);}
    private NativePlateRuntimeChecks(){}
}
