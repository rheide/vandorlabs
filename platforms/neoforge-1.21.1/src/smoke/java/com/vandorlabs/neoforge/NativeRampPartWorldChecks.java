package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.neoforge.client.NativeRampPartBlockRenderer;
import com.vandorlabs.neoforge.ramp.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Real placed private Parts/dispatcher/atlas, with strict disabled/reload controls and cost records. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeRampPartWorldChecks {
    private static int stage,ticks,wait,frames,checks,costFrames;
    private static String capture;
    private static volatile boolean captured;
    private static Object savedRenderers;
    private static java.lang.reflect.Field rendererField;
    private static boolean reloadCheck,measuring;
    private static NativeRampPartEntity measured;
    private static NativeRampPartData original;
    private static long costBytes,costPeak,costNanos,costBuilds,startBuilds,motionRevision;
    private static final PoseStack POSE=new PoseStack();
    private static final Discard DISCARD=new Discard();
    private static final MultiBufferSource BUFFERS=ignored->DISCARD;
    private static final class Discard implements VertexConsumer {
        long vertices;
        public VertexConsumer addVertex(float x,float y,float z){vertices++;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv(float u,float v){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Ramp world stage="+stage+": "+why);}
    private static NativeRampPartEntity at(Minecraft mc,int index){return mc.level.getBlockEntity(RampPartWorldFixture.pos(index)) instanceof NativeRampPartEntity tile?tile:null;}
    private static NativeRampPartBlockRenderer bridge(Minecraft mc,NativeRampPartEntity tile){return (NativeRampPartBlockRenderer)mc.getBlockEntityRenderDispatcher().getRenderer(tile);}
    private static void capture(String name){capture=name;captured=false;frames=0;}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Binding {
        @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){if(RampPartWorldFixture.enabled())event.registerBlockEntityRenderer(RampPartWorldFixture.TYPE,NativeRampPartBlockRenderer::new);}
        @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)public static void completed(ModelEvent.BakingCompleted event){var level=Minecraft.getInstance().level;if(RampPartWorldFixture.enabled()&&reloadCheck&&level!=null){need(NativeRampPartBlockRenderer.retainedBytes(level)==0,"reload clears ALL presentations including unseen part");reloadCheck=false;}}
    }
    @SuppressWarnings("unchecked")private static void renderer(Minecraft mc,boolean enabled){try{if(rendererField==null){rendererField=net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher.class.getDeclaredField("renderers");rendererField.setAccessible(true);}var dispatcher=mc.getBlockEntityRenderDispatcher();if(!enabled){savedRenderers=rendererField.get(dispatcher);var copy=new HashMap<>((Map<Object,Object>)savedRenderers);copy.remove(RampPartWorldFixture.TYPE);rendererField.set(dispatcher,copy);}else{rendererField.set(dispatcher,savedRenderers);savedRenderers=null;}}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
    private static void stationaryCost(Minecraft mc){
        var tile=at(mc,0);var bridge=bridge(mc,tile);for(int i=0;i<16000;i++){bridge.getRenderBoundingBox(tile);bridge.render(tile,1,POSE,BUFFERS,0xf000f0,0);}
        long single=DISCARD.vertices;bridge.render(tile,1,POSE,BUFFERS,0xf000f0,0);single=DISCARD.vertices-single;need(single>0,"real stationary prepared vertices");
        var meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId(),before=meter.getThreadAllocatedBytes(thread),builds=NativeRampPartRenderer.currentBake().builds();DISCARD.vertices=0;
        for(int i=0;i<4096;i++){bridge.getRenderBoundingBox(tile);bridge.render(tile,1,POSE,BUFFERS,0xf000f0,0);}
        long allocated=meter.getThreadAllocatedBytes(thread)-before;need(allocated==0&&DISCARD.vertices==4096*single&&NativeRampPartRenderer.currentBake().builds()==builds,"stationary bridge allocation="+allocated+" vertices="+DISCARD.vertices+" newMeshes="+(NativeRampPartRenderer.currentBake().builds()-builds));
        System.out.println("[vandorlabs][ramp-part-world] stationary PASS calls4096 vertices="+DISCARD.vertices+" allocated="+allocated+" newMeshes=0");
        bridge(mc,at(mc,9)).render(at(mc,9),1,POSE,BUFFERS,0xf000f0,0);need(NativeRampPartBlockRenderer.retainedBytes(mc.level)>0,"unseen part presentation primed before reload");
    }
    private static void deliver(Minecraft mc,NativeRampPartEntity owner,NativeRampPartData data,long revision,long tick){var tag=owner.getUpdateTag(mc.level.registryAccess());tag.putLong("NativeRampPublicationRevision",revision);tag.putLong("NativeRampObservationTick",tick);tag.put("NativeRampPartData",data.write());var bytes=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),mc.level.registryAccess());try{var packet=ClientboundBlockEntityDataPacket.create(owner,(tile,registries)->tag);ClientboundBlockEntityDataPacket.STREAM_CODEC.encode(bytes,packet);mc.getConnection().handleBlockEntityData(ClientboundBlockEntityDataPacket.STREAM_CODEC.decode(bytes));need(!bytes.isReadable(),"motion packet complete");}finally{bytes.release();}}
    private static void startCost(Minecraft mc,boolean belt){
        measured=at(mc,belt?3:0);original=measured.data();motionRevision=measured.publication().revision()+10;
        if(belt){deliver(mc,measured,original.withBelt(new NativeRampPartData.Belt(1,0,.3,0,true,.3)),motionRevision,1000);}
        else{var reset=original.withMotion(new NativeRampPartData.Motion(200,0,0,0,128,false,0));deliver(mc,measured,reset,motionRevision,1000);bridge(mc,measured).render(measured,1,POSE,BUFFERS,0xf000f0,0);deliver(mc,measured,original.withMotion(new NativeRampPartData.Motion(201,0,0,1,128,true,0)),motionRevision+1,1001);}
        costFrames=0;costBytes=costPeak=costNanos=0;startBuilds=NativeRampPartRenderer.currentBake().builds();measuring=true;
    }
    private static void endCost(Minecraft mc,boolean belt){
        measuring=false;costBuilds=NativeRampPartRenderer.currentBake().builds()-startBuilds;
        need(costFrames>=60&&costBuilds>10&&costPeak<1024*1024,"moving preparation cost frames="+costFrames+" meshes="+costBuilds+" peak="+costPeak);
        need(NativeRampPartBlockRenderer.retainedBytes(mc.level)<=8*1024*1024&&NativeRampPartRenderer.currentBake().retainedBytes()<=8*1024*1024,"both geometry caches byte bounded");
        System.out.println("[vandorlabs][ramp-part-world] moving PASS belt="+belt+" frames="+costFrames+" builds="+costBuilds+" bytes="+costBytes+" peakBytes="+costPeak+" cpuNanos="+costNanos+" adapterRetained="+NativeRampPartBlockRenderer.retainedBytes(mc.level)+" backendRetained="+NativeRampPartRenderer.currentBake().retainedBytes());
        deliver(mc,measured,original,motionRevision+2,2000);NativeRampPartVisualClock.clear(mc.level);measured=null;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!RampPartWorldFixture.enabled())return;var mc=Minecraft.getInstance();if(stage<12&&mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen)throw new AssertionError("Private Ramp server disconnected: inspect retained server log");if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}
        if(stage==12&&mc.level==null){need(NativeRampPartRenderer.currentBake().retainedBytes()==0,"backend retired on unload");System.out.println("[vandorlabs][ramp-part-world] client PASS checks="+checks+" placed hosts/actual packets/dispatcher/disabled/reload/unseen retirement/low update rate/moving cost/unload");mc.stop();stage=13;return;}
        if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<1800,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" position="+mc.player.position()+" screen="+mc.screen);
        switch(stage){
            case 0->{if(ticks<100||mc.gameMode.getPlayerMode()!=GameType.CREATIVE||mc.player.getInventory().selected!=0||!mc.player.getMainHandItem().isEmpty()||Math.abs(mc.player.getZ()+9)>.01||at(mc,9)==null)return;for(int i=0;i<10;i++)if(at(mc,i)==null||at(mc,i).publication()==null)return;for(int i=0;i<10;i++){var tile=at(mc,i);if(mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,tile.getBlockPos())!=15){need(ticks<600,"settled opacity-zero owner sky light i="+i+" packed="+LevelRenderer.getLightColor(mc.level,tile.getBlockPos())+" lightBlock="+tile.getBlockState().getLightBlock(mc.level,tile.getBlockPos()));return;}}System.out.println("[vandorlabs][ramp-part-world] actual client PID="+ProcessHandle.current().pid());need(bridge(mc,at(mc,0))!=null,"actual private dispatcher renderer");NativeRampPartPacketChecks.run(mc);stationaryCost(mc);mc.options.hideGui=true;mc.options.fov().set(55);mc.options.bobView().set(false);mc.options.cloudStatus().set(CloudStatus.OFF);wait=ticks;stage=1;}
            case 1->{if(ticks<wait+25)return;capture("ramp-part-world-positive.png");stage=2;}
            case 2->{if(!captured)return;renderer(mc,false);need(mc.getBlockEntityRenderDispatcher().getRenderer(at(mc,0))==null,"actual BER disabled control");capture("ramp-part-world-disabled.png");stage=3;}
            case 3->{if(!captured)return;renderer(mc,true);reloadCheck=true;mc.reloadResourcePacks();wait=ticks;stage=4;}
            case 4->{if(mc.getOverlay()!=null||ticks<wait+100||reloadCheck)return;need(bridge(mc,at(mc,0))!=null,"real dispatcher rebound after reload");capture("ramp-part-world-reloaded.png");stage=5;}
            case 5->{if(!captured)return;startCost(mc,false);stage=6;}
            case 6->{if(costFrames<60)return;endCost(mc,false);startCost(mc,true);stage=7;}
            case 7->{if(costFrames<60)return;endCost(mc,true);wait=ticks;stage=8;}
            case 8->{if(ticks<wait+20)return;capture("ramp-part-world-restored.png");stage=9;}
            case 9->{if(!captured)return;mc.level.disconnect();mc.disconnect();stage=12;}
        }
    }
    @SubscribeEvent public static void prepare(RenderLevelStageEvent event){if(!RampPartWorldFixture.enabled()||!measuring||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_SKY)return;var mc=Minecraft.getInstance();var bridge=bridge(mc,measured);var meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId(),before=meter.getThreadAllocatedBytes(thread),start=System.nanoTime();bridge.render(measured,1,POSE,BUFFERS,0xf000f0,0);long nanos=System.nanoTime()-start,bytes=meter.getThreadAllocatedBytes(thread)-before;costFrames++;costBytes+=bytes;costPeak=Math.max(costPeak,bytes);costNanos+=nanos;}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(!RampPartWorldFixture.enabled()||capture==null)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null){frames=0;return;}if(++frames<12)return;String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->captured=true);}
}
