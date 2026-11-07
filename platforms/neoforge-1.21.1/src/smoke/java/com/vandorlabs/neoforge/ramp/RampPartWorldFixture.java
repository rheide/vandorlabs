package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Private real part hosts. This fixture does not claim controller/journal ownership acceptance. */
@EventBusSubscriber(modid="vandorlabs")
public final class RampPartWorldFixture {
    public static final BlockPos BASE=new BlockPos(0,80,0);
    public static Block BLOCK;
    public static BlockEntityType<NativeRampPartEntity> TYPE;
    public static boolean enabled(){return "ramp-part-world-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
    public static BlockPos pos(int index){return index==9?BASE.east(14):BASE.offset(index%3*2,index/3*2,0);}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void payload(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;event.registrar("1").playToServer(CameraRequest.TYPE,net.minecraft.network.codec.StreamCodec.unit(new CameraRequest()),(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player){player.teleportTo(player.serverLevel(),.5,80,-3.5,0,14.3F);System.out.println("[vandorlabs][ramp-part-world] outline camera PASS actual server teleport");}}));}
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!enabled())return;var id=ResourceLocation.parse("vandorlabs:ramp_part_world_fixture");event.register(Registries.BLOCK,h->{BLOCK=new NativeRampPartBlock(BlockBehaviour.Properties.of(),()->TYPE);h.register(id,BLOCK);});event.register(Registries.BLOCK_ENTITY_TYPE,h->{TYPE=BlockEntityType.Builder.<NativeRampPartEntity>of((p,s)->new NativeRampPartEntity(TYPE,p,s),BLOCK).build(null);h.register(id,TYPE);});}
    }
    public record CameraRequest() implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final Type<CameraRequest> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:ramp_part_world_camera"));
        public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type(){return TYPE;}
    }
    private static NativeRampPartData data(int index){
        var cell=pos(index);boolean stairs=index==2,slab=index==5,belt=index==3,moat=index==6,vertical=index==7,extend=index==8;
        BlockState state=index==4?Blocks.GLASS.defaultBlockState():stairs?VandorLabsBootstrap.PROGRAMMABLE_STAIRS.get().defaultBlockState().setValue(StairBlock.SHAPE,StairsShape.INNER_LEFT):slab?Blocks.STONE_SLAB.defaultBlockState():index==0?Blocks.STONE.defaultBlockState():VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState();
        var facing=Direction.from3DDataValue(2+index%4);var origins=new ArrayList<NativeRampPartData.Origin>();
        for(int i=0;i<(belt||vertical?3:1);i++){var origin=vertical?cell.above(i):cell.relative(facing,i);boolean configured=index!=0&&index!=4&&index!=5;var material=new NativeRampPartMaterial(state,configured?64+i:-1,configured?7:-1,configured?1:-1,configured?index%4:0,stairs,index==1,configured?new int[]{64,7,64,7,64,7}:new int[]{-1,-1,-1,-1,-1,-1});origins.add(new NativeRampPartData.Origin(origin,8,material));}
        var spec=new NativeRampGeometry.Spec(cell,facing,cell.getY(),0,3,stairs?8:4,0,slab?.5:1,0,moat?-.75:.75,false,extend?1:0,extend,false,index%3,vertical,moat,stairs,origins.stream().map(NativeRampPartData.Origin::geometry).toList());
        return new NativeRampPartData(spec,state,origins,new NativeRampPartData.Motion(1,0,1,1,64,false,1),belt?new NativeRampPartData.Belt(1,1,.3,0,false,.3):NativeRampPartData.Belt.NONE);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;var world=player.serverLevel();
        for(var rule:List.of(GameRules.RULE_DOMOBSPAWNING,GameRules.RULE_DAYLIGHT,GameRules.RULE_WEATHER_CYCLE))world.getGameRules().getRule(rule).set(false,world.getServer());world.setDayTime(6000);world.setWeatherParameters(0,6000,false,false);
        for(var p:BlockPos.betweenClosed(BASE.offset(-5,-1,-13),BASE.offset(10,7,4)))world.setBlock(p,p.getY()==79?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
        for(int i=0;i<10;i++){var pos=pos(i);world.setBlock(pos,BLOCK.defaultBlockState(),18);var tile=(NativeRampPartEntity)world.getBlockEntity(pos);var tag=new CompoundTag();tag.putInt("NativeRampPartSchema",1);tag.putUUID("Operation",new UUID(0x5176,i+1));tag.putInt("RootX",pos.getX());tag.putInt("RootY",pos.getY());tag.putInt("RootZ",pos.getZ());tag.putLong("NativeRampPublicationRevision",1);tag.putLong("NativeRampObservationTick",world.getGameTime());tag.put("NativeRampPartData",data(i).write());tile.loadWithComponents(tag,world.registryAccess());if(tile.publication()==null)throw new AssertionError("Private Ramp part "+i+" refused");if(tile.getBlockState().getLightBlock(world,pos)!=0)throw new AssertionError("Source Ramp opacity must be zero");world.sendBlockUpdated(pos,tile.getBlockState(),tile.getBlockState(),3);}
        player.setGameMode(GameType.CREATIVE);player.getInventory().clearContent();player.getInventory().selected=0;player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(world,2.5,80.38,-9,0,0);
        System.out.println("[vandorlabs][ramp-part-world] setup PASS actual10hosts ordinary/material/stairs/belt/glass/slab/moat/vertical/extension");
    }
    @SubscribeEvent public static void started(net.neoforged.neoforge.event.server.ServerStartedEvent event){if(enabled())System.out.println("[vandorlabs][ramp-part-world] actual server PID="+ProcessHandle.current().pid());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled())System.out.println("[vandorlabs][ramp-part-world] logout PASS");}
}
