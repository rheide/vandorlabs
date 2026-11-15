package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.ramp.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Real GUI, normal movement packets and ordinary lever clicks on the connected controller. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class RampControllerPlayerChecks {
 private static int stage,ticks,wait,walk,checks,corrections;private static boolean opened,configured,screenShot,queuedShot;
 private static double walkFrom;private static String endpointDiagnostic="not queried";
 private static void need(boolean yes,String message){checks++;if(!yes)throw new AssertionError("Ramp player client "+message+" stage="+stage+" ticks="+ticks);}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
 public static final class Binding {
  @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(RampControllerPlayerFixture.enabled())NativeRampScreen.register(event,RampControllerPlayerFixture.MENU);}
  @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){if(RampControllerPlayerFixture.enabled())event.registerBlockEntityRenderer(RampControllerPlayerFixture.PART_TYPE,NativeRampPartBlockRenderer::new);}
  @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){if(RampControllerPlayerFixture.enabled())event.enqueueWork(()->RampControllerPlayerFixture.client(packet->{need(NativeRampRiderClient.receive(packet),"normal support packet accepted "+packet.sequence());corrections++;}));}
 }
 private static boolean synchronizedActor(Minecraft mc){return mc.gameMode.getPlayerMode()==GameType.SURVIVAL&&mc.player.getInventory().selected==0&&mc.player.getMainHandItem().isEmpty()&&mc.player.getOffhandItem().isEmpty()&&!mc.player.getAbilities().flying;}
 private static int ack(Minecraft mc){var state=mc.level.getBlockState(RampControllerPlayerFixture.ACK);return state.is(Blocks.NOTE_BLOCK)?state.getValue(NoteBlock.NOTE):-1;}
 private static void probe(int value){PacketDistributor.sendToServer(new RampControllerPlayerFixture.Probe(value));}
 private static void stop(Minecraft mc){mc.options.keyRight.setDown(false);mc.options.keyLeft.setDown(false);mc.options.keyUp.setDown(false);mc.options.keyDown.setDown(false);}
 private static void walkRight(Minecraft mc){mc.player.setYRot(180);mc.player.setXRot(0);mc.options.keyRight.setDown(true);}
 private static void click(Minecraft mc,BlockPos pos,Vec3 target){
  var eye=mc.player.getEyePosition();var delta=target.subtract(eye);mc.player.setYRot((float)-Math.toDegrees(Math.atan2(delta.x,delta.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))));
  var hit=mc.level.clip(new ClipContext(eye,target,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));String diagnostic=" target="+pos+" point="+target+" eye="+eye+" player="+mc.player.position()+" yaw="+mc.player.getYRot()+" pitch="+mc.player.getXRot()+" reach="+eye.distanceTo(target)+" hitType="+hit.getType()+" hitBlock="+hit.getBlockPos()+" face="+hit.getDirection()+" point="+hit.getLocation()+" state="+mc.level.getBlockState(hit.getBlockPos());if(mc.level.getBlockEntity(hit.getBlockPos()) instanceof NativeRampPartEntity part&&part.publication()!=null)diagnostic+=" rampBoxes="+part.publication().worldBoxes().stream().limit(4).toList();need(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos),"ordinary ray reaches"+diagnostic);System.out.println("[vandorlabs][ramp-controller-player] normal click"+diagnostic);
  mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);
 }
 static void open(Minecraft mc){click(mc,RampControllerPlayerFixture.ROOT,Vec3.atLowerCornerOf(RampControllerPlayerFixture.ROOT).add(.5,.75,.001));}
 static void lever(Minecraft mc){
  var pos=RampControllerPlayerFixture.LEVER;var eye=mc.player.getEyePosition();var boxes=mc.level.getBlockState(pos).getShape(mc.level,pos).toAabbs();need(boxes.size()<=8,"bounded vanilla lever outline");Vec3 selected=null;double distance=Double.POSITIVE_INFINITY;
  for(var box:boxes)for(double x:new double[]{.25,.5,.75})for(double z:new double[]{.25,.5,.75}){
   var point=new Vec3(pos.getX()+box.minX+(box.maxX-box.minX)*x,pos.getY()+box.maxY-1e-4,pos.getZ()+box.minZ+(box.maxZ-box.minZ)*z);
   var ray=mc.level.clip(new ClipContext(eye,point,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));double length=eye.distanceToSqr(ray.getLocation());
   if(ray.getType()==HitResult.Type.BLOCK&&ray.getBlockPos().equals(pos)&&length<=4.5*4.5&&length<distance){selected=point;distance=length;}
  }
  need(selected!=null,"an actual visible lever surface is reachable eye="+eye+" player="+mc.player.position()+" outline="+boxes);click(mc,pos,selected);
 }
 static boolean stoppedSupport(Minecraft mc){
  var body=mc.player.getBoundingBox();double nearest=Double.POSITIVE_INFINITY;net.minecraft.world.phys.AABB witness=null;long revision=-1;int parts=0;
  for(var pos:BlockPos.betweenClosed(new BlockPos(-1,98,-2),new BlockPos(1,100,-1))){
   if(!(mc.level.getBlockEntity(pos) instanceof NativeRampPartEntity part)||!part.ownershipKnown())continue;
   var value=part.publication();if(value==null||!part.controller().equals(RampControllerPlayerFixture.ROOT)||value.data().motion().moving()||value.pose()!=0){endpointDiagnostic="part="+pos+" publication="+(value==null?"missing":"revision="+value.revision()+" pose="+value.pose()+" motion="+value.data().motion());return false;}
   if(revision>=0&&revision!=value.revision()){endpointDiagnostic="parts arriving revision="+revision+" next="+value.revision()+" at="+pos;return false;}revision=value.revision();parts++;
   for(var box:value.worldBoxes())if(box.maxX>body.minX+1e-7&&box.minX<body.maxX-1e-7&&box.maxZ>body.minZ+1e-7&&box.minZ<body.maxZ-1e-7){double delta=Math.abs(body.minY-box.maxY);if(delta<nearest){nearest=delta;witness=box;}}
  }
  endpointDiagnostic="parts="+parts+" revision="+revision+" feet="+body.minY+" nearest="+nearest+" witness="+witness;if(parts==0||nearest>=1e-6)return false;
  // Same authored off-start endpoint as the server: this must converge before a normal menu click.
  need(Math.abs(body.minY-(100.5-2D/3D))<1e-6,"client/server endpoint support height "+body+" witness="+witness);
  System.out.println("[vandorlabs][ramp-controller-player] stopped endpoint client PASS pose=0 revision="+revision+" parts="+parts+" player="+mc.player.position()+" body="+body+" witness="+witness);return true;
 }
 static void offset(NativeRampScreen screen,int desired){var slider=screen.startSlider();double fraction=(desired+32)/64D,x=slider.getX()+4+fraction*(slider.getWidth()-8);screen.mouseClicked(x,slider.getY()+slider.getHeight()/2D,0);screen.mouseReleased(x,slider.getY()+slider.getHeight()/2D,0);need(screen.editingDraft().settings().startHalf()==desired,"actual slider chose "+desired);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){
  if(!RampControllerPlayerFixture.enabled())return;if(com.vandorlabs.neoforge.ramp.NativeRampBindings.enabled()){RampControllerPlayerFixture.canonical();com.vandorlabs.neoforge.ramp.NativeRampBindings.bindMotion(RampControllerPlayerFixture::receive);}var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}
  if(NativeRampColdPlayerFixture.read()){NativeRampColdPlayerChecks.tick(mc);return;}if(stage>=18)return;
  if(stage==17){if(mc.level==null){System.out.println("[vandorlabs][ramp-controller-player] client PASS checks="+checks+" corrections="+corrections);mc.stop();stage=18;}return;}
  if(NativeRampColdPlayerFixture.write()&&mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen){System.out.println("[vandorlabs][ramp-player-cold] intentional write disconnect");stage=18;mc.stop();return;}
  need(!(mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen),"unexpected disconnect");
  if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<2400,"timeout ack="+ack(mc)+" position="+mc.player.position()+" mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" screen="+mc.screen+" endpoint="+endpointDiagnostic);
  switch(stage){
   case 0->{if(ticks<100||!synchronizedActor(mc)||Math.abs(mc.player.getY()-100.5)>.01||ack(mc)!=0)return;need(mc.player.getInventory().getItem(8).getCount()==37,"actual synchronized resource inventory");open(mc);stage=1;}
   case 1->{if(!(mc.screen instanceof NativeRampScreen screen))return;offset(screen,-2);configured=true;stage=2;}
   case 2->{if(ack(mc)!=1)return;need(configured,"real C2S configured before preparation");if(mc.screen!=null)mc.screen.onClose();walkFrom=mc.player.getX();walk=0;stage=3;}
   case 3->{walkRight(mc);if(++walk<4)return;stop(mc);need(Math.abs(mc.player.getX()-walkFrom)>.04,"ordinary local movement during pending disk preparation");probe(0);stage=4;}
   case 4->{stop(mc);if(ack(mc)!=2)return;open(mc);stage=5;}
   case 5->{if(!(mc.screen instanceof NativeRampScreen screen))return;screen.action(18,1);stage=6;}
   case 6->{if(ack(mc)!=3)return;if(mc.screen!=null)mc.screen.onClose();walk=0;stage=7;}
   case 7->{walkRight(mc);if(++walk<6)return;stop(mc);probe(1);stage=8;}
   case 8->{stop(mc);if(ack(mc)!=4)return;walkFrom=mc.player.getX();walk=0;stage=9;}
   case 9->{walkRight(mc);if(ack(mc)<5||mc.player.getX()<1.35)return;stop(mc);wait=0;stage=10;}
   case 10->{stop(mc);if(++wait<12)return;lever(mc);stage=11;}
   case 11->{if(NativeRampColdPlayerFixture.write())return;if(ack(mc)!=6)return;lever(mc);stage=12;}
   case 12->{if(ack(mc)!=7)return;wait=0;stage=13;}
   case 13->{stop(mc);if(++wait<12||!stoppedSupport(mc))return;open(mc);stage=14;}
   case 14->{if(!(mc.screen instanceof NativeRampScreen screen))return;offset(screen,0);stage=15;}
   case 15->{if(ack(mc)!=8||corrections<3)return;need(mc.level.getBlockState(new BlockPos(0,100,-2)).is(com.vandorlabs.neoforge.VandorLabsBootstrap.PROGRAMMABLE_SLAB.get()),"client sees restored native source");need(Math.abs(mc.player.getY()-100.5)<.01,"client stands on restored native support");need(mc.player.getInventory().getItem(8).getCount()==37,"client resources exact");if(mc.screen!=null)mc.screen.onClose();if(!screenShot){queuedShot=true;return;}probe(2);stage=16;}
   case 16->{if(ack(mc)!=9)return;stage=17;mc.level.disconnect();mc.disconnect();}
  }
 }
 @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(!RampControllerPlayerFixture.enabled()||!queuedShot||screenShot)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;queuedShot=false;Screenshot.grab(mc.gameDirectory,"ramp-controller-player-native-return.png",mc.getMainRenderTarget(),ignored->screenShot=true);}
 private RampControllerPlayerChecks(){}
}
