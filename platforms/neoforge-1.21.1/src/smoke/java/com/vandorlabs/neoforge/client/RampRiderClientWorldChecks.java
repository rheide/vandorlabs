package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.ramp.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Actual private TCP corrections, render snapshots, independent pick and camera-boom controls. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class RampRiderClientWorldChecks {
    private static int stage,ticks,received,checks,renderScopes;
    private static long receivedNanos;
    private static boolean firstSeen,thirdSeen,firstShot,thirdShot,firstQueued,thirdQueued;
    private static LocalPlayer framePlayer,retiredPlayer;
    private static Vec3 physical,retiredPhysical;
    private static AABB bounds;
    private static BlockPos block;
    private static ChunkPos chunk;
    private static final double[] HISTORY=new double[6],UNLOAD_HISTORY=new double[6];
    private static final Camera REFERENCE=new Camera(),RAW_CAMERA=new Camera();
    private static java.lang.reflect.Field eyeHeight,oldEyeHeight;
    private static void need(boolean yes,String text){checks++;if(!yes)throw new AssertionError("Rider client stage="+stage+": "+text);}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Binding {
        @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){if(RampRiderClientWorldFixture.enabled())event.enqueueWork(()->RampRiderClientWorldFixture.client(RampRiderClientWorldChecks::receive));}
    }
    private static void receive(NativeRampMotionPayload message){need(NativeRampRiderClient.receive(message),"real server support accepted seq="+message.sequence());received++;receivedNanos=System.nanoTime();System.out.println("[vandorlabs][ramp-rider-client] received PASS sequence="+message.sequence()+" supportY="+message.verticalDisplacement());}
    private static boolean elapsed(){return System.nanoTime()-receivedNanos>=350000000L;}
    private static void action(int step){PacketDistributor.sendToServer(new RampRiderClientWorldFixture.Action(step));}
    private static boolean actor(Minecraft mc){return mc.gameMode.getPlayerMode()==GameType.SURVIVAL&&mc.player.getInventory().selected==0&&mc.player.getMainHandItem().isEmpty()&&mc.player.getOffhandItem().isEmpty()&&!mc.player.getAbilities().flying;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!RampRiderClientWorldFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(stage<9&&mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen)throw new AssertionError("Rider server disconnected; inspect retained log");
        if(stage==9&&mc.level==null){need(!NativeRampRiderClient.rendering(),"world unload restores render scope");need(retiredPlayer!=null&&Math.abs(retiredPlayer.position().y-retiredPlayer.getBoundingBox().minY)<1e-9&&Math.abs(retiredPlayer.position().x-(retiredPlayer.getBoundingBox().minX+retiredPlayer.getBoundingBox().maxX)/2)<1e-9&&Math.abs(retiredPlayer.position().z-(retiredPlayer.getBoundingBox().minZ+retiredPlayer.getBoundingBox().maxZ)/2)<1e-9&&NativeRampRiderClient.offsetX()==0&&NativeRampRiderClient.offsetY()==0&&NativeRampRiderClient.offsetZ()==0,"actual unload restores physical coordinates and clears offsets despite legitimate nested disconnect ticks");System.out.println("[vandorlabs][ramp-rider-client] client PASS checks="+checks+" actual TCP5TPS/physics/history/pick/nearwall/bounds/cache/sequence/roles/worker/replacement/pause/allocation/unload");mc.stop();stage=10;return;}
        if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<1200,"timeout received="+received+" mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position()+" screen="+mc.screen);
        switch(stage){
            case 0->{if(ticks<100||!actor(mc)||Math.abs(mc.player.getX()-.5)>.01||Math.abs(mc.player.getZ()-.5)>.01||Math.abs(mc.player.getY()-80)>.01)return;mc.options.hideGui=false;mc.options.fov().set(70);mc.options.bobView().set(false);mc.options.cloudStatus().set(CloudStatus.OFF);System.out.println("[vandorlabs][ramp-rider-client] actual client PID="+ProcessHandle.current().pid());action(0);stage=1;}
            case 1->{if(received<1||!elapsed())return;action(1);stage=2;}
            case 2->{if(received<2||!elapsed())return;action(2);stage=3;}
            case 3->{if(!firstShot||Math.abs(NativeRampRiderClient.offsetY())>1e-9)return;mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);action(3);stage=4;}
            case 4->{if(!actor(mc)||Math.abs(mc.player.getY()-80)>.01)return;action(4);stage=5;}
            case 5->{if(received<4||!elapsed())return;action(5);stage=6;}
            case 6->{if(received<5||!elapsed())return;action(6);stage=7;}
            case 7->{if(!thirdShot||Math.abs(NativeRampRiderClient.offsetY())>1e-9)return;mc.options.setCameraType(CameraType.FIRST_PERSON);numeric(mc);action(7);stage=8;}
            case 8->{if(received<6||!actor(mc)||!mc.level.getBlockState(new BlockPos(4,80,0)).is(net.minecraft.world.level.block.Blocks.EMERALD_BLOCK))return;need(NativeRampRiderClient.receive(packet(mc,3000,mc.player.getY(),mc.player.getY()+.1,0,0)),"unload with active accepted offset");retiredPlayer=mc.player;retiredPhysical=mc.player.position();UNLOAD_HISTORY[0]=mc.player.xo;UNLOAD_HISTORY[1]=mc.player.yo;UNLOAD_HISTORY[2]=mc.player.zo;UNLOAD_HISTORY[3]=mc.player.xOld;UNLOAD_HISTORY[4]=mc.player.yOld;UNLOAD_HISTORY[5]=mc.player.zOld;NativeRampRiderClient.begin(NativeRampRiderClient.acceptedAt());need(NativeRampRiderClient.rendering(),"unload active render scope prepared");mc.level.disconnect();mc.disconnect();stage=9;}
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void before(RenderFrameEvent.Pre event){if(!RampRiderClientWorldFixture.enabled())return;var p=Minecraft.getInstance().player;framePlayer=p;if(p==null)return;physical=stage==8&&p==retiredPlayer&&NativeRampRiderClient.rendering()?retiredPhysical:p.position();bounds=p.getBoundingBox();block=p.blockPosition();chunk=p.chunkPosition();HISTORY[0]=p.xo;HISTORY[1]=p.yo;HISTORY[2]=p.zo;HISTORY[3]=p.xOld;HISTORY[4]=p.yOld;HISTORY[5]=p.zOld;if(stage==8&&p==retiredPlayer&&NativeRampRiderClient.rendering())System.arraycopy(UNLOAD_HISTORY,0,HISTORY,0,6); }
    @SubscribeEvent(priority=EventPriority.LOWEST)public static void after(RenderFrameEvent.Post event){
        if(!RampRiderClientWorldFixture.enabled())return;var mc=Minecraft.getInstance();if(framePlayer!=null&&framePlayer==mc.player){need(framePlayer.position()==physical&&framePlayer.getBoundingBox()==bounds&&framePlayer.blockPosition()==block&&framePlayer.chunkPosition()==chunk,"END restores exact physical pointer/bounds/caches");need(framePlayer.xo==HISTORY[0]&&framePlayer.yo==HISTORY[1]&&framePlayer.zo==HISTORY[2]&&framePlayer.xOld==HISTORY[3]&&framePlayer.yOld==HISTORY[4]&&framePlayer.zOld==HISTORY[5],"END restores all six history fields");}framePlayer=null;
        if(mc.getOverlay()!=null)return;
        if(firstSeen&&!firstQueued){firstQueued=true;Screenshot.grab(mc.gameDirectory,"ramp-rider-first-person.png",mc.getMainRenderTarget(),message->firstShot=true);}
        if(thirdSeen&&!thirdQueued){thirdQueued=true;Screenshot.grab(mc.gameDirectory,"ramp-rider-third-person-wall.png",mc.getMainRenderTarget(),message->thirdShot=true);}
    }
    private static void copyEyes(Camera from,Camera to){try{if(eyeHeight==null){eyeHeight=Camera.class.getDeclaredField("eyeHeight");oldEyeHeight=Camera.class.getDeclaredField("eyeHeightOld");eyeHeight.setAccessible(true);oldEyeHeight.setAccessible(true);}eyeHeight.setFloat(to,eyeHeight.getFloat(from));oldEyeHeight.setFloat(to,oldEyeHeight.getFloat(from));}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
    @SubscribeEvent public static void view(RenderLevelStageEvent event){
        if(!RampRiderClientWorldFixture.enabled()||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_SKY||!NativeRampRiderClient.rendering())return;var mc=Minecraft.getInstance();var p=mc.player;
        need(p.getBoundingBox()==bounds&&p.blockPosition()==block&&p.chunkPosition()==chunk,"render scope never moves physical bounds or cached cell");renderScopes++;
        if(NativeRampRiderClient.offsetY()>=-.12)return;
        if(stage==3&&received>=3&&!firstSeen){
            var camera=mc.gameRenderer.getMainCamera();var look=p.getViewVector(camera.getPartialTickTime());var rawEye=physical.add(0,p.getEyeHeight(),0);var raw=mc.level.clip(new ClipContext(rawEye,rawEye.add(look.scale(4.5)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));var shown=mc.level.clip(new ClipContext(camera.getPosition(),camera.getPosition().add(look.scale(4.5)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
            need(mc.hitResult instanceof BlockHitResult picked&&picked.getBlockPos().equals(new BlockPos(0,81,3)),"actual GameRenderer mouseover follows visible lower row hit="+mc.hitResult);
            need(shown.getBlockPos().equals(new BlockPos(0,81,3))&&raw.getBlockPos().equals(new BlockPos(0,82,3)),"independent physical-ray negative picks different upper row");
            firstSeen=true;System.out.println("[vandorlabs][ramp-rider-client] first-person PASS physicalRay="+raw.getBlockPos()+" visiblePick="+shown.getBlockPos()+" offset="+NativeRampRiderClient.offsetY());
        }
        if(stage==7&&received>=6&&!thirdSeen){
            var camera=mc.gameRenderer.getMainCamera();double time=NativeRampRiderClient.frameTime();var eye=p.getEyePosition(camera.getPartialTickTime());copyEyes(camera,REFERENCE);REFERENCE.setup(mc.level,p,true,false,camera.getPartialTickTime());need(REFERENCE.getPosition().distanceToSqr(camera.getPosition())<1e-9,"actual third-person camera matches Source-position vanilla boom");double shown=eye.distanceTo(camera.getPosition());
            NativeRampRiderClient.restore();copyEyes(camera,RAW_CAMERA);RAW_CAMERA.setup(mc.level,p,true,false,camera.getPartialTickTime());double raw=p.getEyePosition(camera.getPartialTickTime()).distanceTo(RAW_CAMERA.getPosition());NativeRampRiderClient.begin(time);
            need(shown<2.25&&raw>3.5,"low wall clips visible boom; physics-only CameraTAIL negative tunnels shown="+shown+" raw="+raw);thirdSeen=true;System.out.println("[vandorlabs][ramp-rider-client] third-person PASS visibleWallZoom="+shown+" physicalOnlyZoom="+raw);
        }
    }
    private static NativeRampMotionPayload packet(Minecraft mc,long sequence,double before,double after,double dx,double dz){var p=mc.player;return new NativeRampMotionPayload(mc.level.dimension().location(),sequence,p.getX(),p.getZ(),before,after,dx,dz);}
    private static NativeRampMotionPayload roundtrip(Minecraft mc,NativeRampMotionPayload packet){var bytes=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),mc.level.registryAccess());try{NativeRampMotionPayload.CODEC.encode(bytes,packet);int size=bytes.readableBytes();var copy=NativeRampMotionPayload.CODEC.decode(bytes);need(!bytes.isReadable()&&copy.equals(packet)&&size==76,"actual codec76byte roundtrip");return copy;}finally{bytes.release();}}
    private static void numeric(Minecraft mc){
        var p=mc.player;Vec3 saved=p.position(),velocity=p.getDeltaMovement();float fall=p.fallDistance;boolean flying=p.getAbilities().flying;double feet=p.getBoundingBox().minY;NativeRampRiderClient.clear();p.setDeltaMovement(.13,-.05,-.09);p.fallDistance=4;
        var move=roundtrip(mc,packet(mc,1000,feet,feet+.1,1.25,0));need(NativeRampRiderClient.receive(move),"accepted correction");need(p.onGround()&&p.fallDistance==0&&p.getDeltaMovement().x==.13&&p.getDeltaMovement().z==-.09&&p.getDeltaMovement().y==0,"ground/fall/horizontal momentum preserved");
        var physical=p.position();var box=p.getBoundingBox();var cell=p.blockPosition();var chunk=p.chunkPosition();double now=NativeRampRiderClient.acceptedAt();NativeRampRiderClient.begin(now);need(p.position()!=physical&&p.getBoundingBox()==box&&p.blockPosition()==cell&&p.chunkPosition()==chunk,"raw visual vector crosses cells without physical cache changes");NativeRampRiderClient.restore();need(p.position()==physical,"exact pointer restoration");
        var duplicate=move;need(!NativeRampRiderClient.receive(duplicate),"duplicate sequence rejected");need(!NativeRampRiderClient.receive(packet(mc,999,p.getY(),p.getY(),0,0)),"old sequence rejected");
        var invalid=new NativeRampMotionPayload(mc.level.dimension().location(),1001,p.getX()+2,p.getZ(),p.getY(),p.getY(),0,0);need(!NativeRampRiderClient.receive(invalid),"fresh far correction refused");need(!NativeRampRiderClient.receive(packet(mc,1001,p.getY(),p.getY(),0,0)),"refused fresh correction still consumes sequence");
        var wrong=new NativeRampMotionPayload(ResourceLocation.parse("minecraft:the_nether"),1002,p.getX(),p.getZ(),p.getY(),p.getY(),0,0);need(!NativeRampRiderClient.receive(wrong),"wrong dimension refused");
        var workerPacket=packet(mc,2000,feet,feet,0,0);try{java.util.concurrent.CompletableFuture.runAsync(()->NativeRampRiderClient.receive(workerPacket)).join();throw new AssertionError("worker accepted rider update");}catch(java.util.concurrent.CompletionException expected){need(expected.getCause() instanceof IllegalStateException,"worker rejected before world/sequence mutation");}
        // Zero-offset and active allocation are measured separately.
        NativeRampRiderClient.clear();var meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();for(int i=0;i<20000;i++){NativeRampRiderClient.begin(now);NativeRampRiderClient.restore();}long before=meter.getThreadAllocatedBytes(thread);for(int i=0;i<100000;i++){NativeRampRiderClient.begin(now);NativeRampRiderClient.restore();}long zero=meter.getThreadAllocatedBytes(thread)-before;need(zero==0,"zero-offset allocation="+zero);
        p.setPos(saved.x,saved.y,saved.z);p.setDeltaMovement(Vec3.ZERO);need(NativeRampRiderClient.receive(packet(mc,2001,p.getY(),p.getY()+.1,0,0)),"active cost correction");now=NativeRampRiderClient.acceptedAt();for(int i=0;i<20000;i++){NativeRampRiderClient.begin(now);NativeRampRiderClient.restore();}before=meter.getThreadAllocatedBytes(thread);for(int i=0;i<100000;i++){NativeRampRiderClient.begin(now);NativeRampRiderClient.restore();}long active=meter.getThreadAllocatedBytes(thread)-before;need(active<=64L*100000,"active immutable vector allocation bound="+active);
        NativeRampRiderClient.begin(now);p.setPos(saved.x+2,saved.y,saved.z);var teleport=p.position();NativeRampRiderClient.restore();need(p.position()==teleport,"stale render restore never overwrites external physical teleport");
        p.setPos(saved.x,saved.y,saved.z);p.setDeltaMovement(Vec3.ZERO);NativeRampRiderClient.clear();need(NativeRampRiderClient.receive(packet(mc,2002,p.getY(),p.getY()+.1,0,0)),"eligibility correction");p.getAbilities().flying=true;NativeRampRiderClient.begin(NativeRampRiderClient.acceptedAt());need(!NativeRampRiderClient.rendering(),"flight clears cosmetic correction");p.getAbilities().flying=false;
        p.setDeltaMovement(Vec3.ZERO);need(NativeRampRiderClient.receive(packet(mc,2003,p.getY(),p.getY()+.1,0,0)),"jump reset correction");p.setDeltaMovement(0,.2,0);NativeRampRiderClient.begin(NativeRampRiderClient.acceptedAt());need(!NativeRampRiderClient.rendering()&&!NativeRampRiderClient.receive(packet(mc,2004,p.getY(),p.getY(),0,0)),"jump clears camera and refuses correction");p.setDeltaMovement(Vec3.ZERO);
        need(NativeRampRiderClient.receive(packet(mc,2005,p.getY(),p.getY()+.1,0,0)),"noPhysics reset correction");p.noPhysics=true;NativeRampRiderClient.begin(NativeRampRiderClient.acceptedAt());need(!NativeRampRiderClient.rendering()&&!NativeRampRiderClient.receive(packet(mc,2006,p.getY(),p.getY(),0,0)),"noPhysics clears camera and refuses correction");p.noPhysics=false;
        need(NativeRampRiderClient.receive(packet(mc,2007,p.getY(),p.getY()+.1,0,0)),"passenger reset correction");var boat=new net.minecraft.world.entity.vehicle.Boat(mc.level,p.getX(),p.getY(),p.getZ());need(p.startRiding(boat,true),"vanilla passenger eligibility fixture");NativeRampRiderClient.begin(NativeRampRiderClient.acceptedAt());need(!NativeRampRiderClient.rendering()&&!NativeRampRiderClient.receive(packet(mc,2008,p.getY(),p.getY(),0,0)),"passenger clears camera and refuses correction");p.stopRiding();boat.discard();p.setPos(saved.x,saved.y,saved.z);p.setDeltaMovement(Vec3.ZERO);
        need(NativeRampRiderClient.receive(packet(mc,2009,p.getY(),p.getY()+.1,0,0)),"death reset correction");float health=p.getHealth();p.setHealth(0);NativeRampRiderClient.begin(NativeRampRiderClient.acceptedAt());need(NativeRampRiderClient.rendering(),"health-zero animation is not Source entity removal");NativeRampRiderClient.restore();p.setHealth(health);
        var replacement=new LocalPlayer(mc,mc.level,mc.getConnection(),p.getStats(),p.getRecipeBook(),false,false);replacement.setId(p.getId());replacement.setPos(saved.x,saved.y,saved.z);replacement.setDeltaMovement(Vec3.ZERO);mc.player=replacement;NativeRampRiderClient.begin(now);need(!NativeRampRiderClient.rendering(),"replacement player cannot inherit offset");need(NativeRampRiderClient.receive(packet(mc,1,replacement.getY(),replacement.getY(),0,0)),"new player identity does not inherit old numeric-ID sequence");need(NativeRampRiderClient.receive(packet(mc,2,replacement.getY(),replacement.getY()+.1,0,0)),"replacement removal correction");replacement.discard();NativeRampRiderClient.begin(NativeRampRiderClient.acceptedAt());need(!NativeRampRiderClient.rendering(),"removed entity clears cosmetic offset");mc.player=p;NativeRampRiderClient.clear();
        mc.player=null;try{NativeRampRiderClient.begin(now);need(!NativeRampRiderClient.rendering()&&!NativeRampRiderClient.receive(move),"unavailable player refuses correction");}finally{mc.player=p;}
        var world=mc.level;mc.level=null;try{need(!NativeRampRiderClient.receive(move),"unavailable current world refuses correction");}finally{mc.level=world;}
        var clock=new com.vandorlabs.animation.VisualClock();clock.sample(0,false);double paused=clock.sample(50000000,false);need(clock.sample(1000000000,true)==paused&&clock.sample(2000000000,false)==paused,"shared pause clock freezes and resumes without jump");
        NativeRampRiderClient.clear();p.setPos(saved.x,saved.y,saved.z);p.xo=p.xOld=saved.x;p.yo=p.yOld=saved.y;p.zo=p.zOld=saved.z;p.setDeltaMovement(velocity);p.fallDistance=fall;p.getAbilities().flying=flying;
        System.out.println("[vandorlabs][ramp-rider-client] numeric PASS zero100000="+zero+" active100000="+active+" actualrawvector/restoration/sequence/worker/playeridentity/worldboundary/pause");
    }
}
