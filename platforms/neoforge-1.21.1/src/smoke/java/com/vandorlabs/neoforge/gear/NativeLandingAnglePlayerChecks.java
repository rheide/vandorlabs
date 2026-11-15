package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.client.NativeIntegerSlider;
import com.vandorlabs.neoforge.copifier.*;
import com.vandorlabs.neoforge.gear.client.NativeGearScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Real widgets, bounded C2S and ordinary held-tool/block interaction, one physical scene. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeLandingAnglePlayerChecks {
 private static int stage,ticks,checks,wait;private static String shot;private static boolean terminal,visualOracle,observing;private static double previousRendered=Double.NaN;private static int renderedFrames;
 private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Native landing-angle client stage="+stage+": "+why);}
 private static void mode(int operation){PacketDistributor.sendToServer(new NativeLandingAnglePlayerFixture.Mode(operation));}
 private static boolean ack(Minecraft mc,int op){return mc.level.getBlockState(NativeLandingAnglePlayerFixture.ACK.east(op)).is(Blocks.STONE);}
 private static void use(Minecraft mc,BlockPos pos,Direction face){var point=Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));need(mc.player.getEyePosition().distanceTo(point)<=mc.player.blockInteractionRange()+1e-6,"actual interaction reach "+pos+" eye="+mc.player.getEyePosition());mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(point,face,pos,false));}
 private static void click(NativeGearScreen screen,int id,int button){var control=screen.button(id);need(control!=null&&control.active&&screen.mouseClicked(control.getX()+control.getWidth()/2D,control.getY()+control.getHeight()/2D,button),"actual option "+id);}
 private static NativeIntegerSlider slider(NativeGearScreen screen,String label){return screen.children().stream().filter(child->child instanceof NativeIntegerSlider value&&value.getMessage().getString().startsWith(label+":")).map(child->(NativeIntegerSlider)child).findFirst().orElseThrow(()->new AssertionError("Missing landing slider "+label));}
 private static void choose(NativeGearScreen screen,String label,int degrees){var control=slider(screen,label);double x=control.getX()+4+(control.getWidth()-8)*(degrees/15D+12)/24,y=control.getY()+control.getHeight()/2D;need(control.active&&screen.mouseClicked(x,y,0),"actual "+label+" mouse click");screen.mouseReleased(x,y,0);need(control.getIntValue()*15==degrees,"selected "+label+"="+degrees);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!NativeLandingAnglePlayerFixture.enabled()||terminal)return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}if(stage==25&&mc.level==null){terminal=true;System.out.println("[vandorlabs][landing-angle-player] client PASS checks="+checks+" actualPlacement/Configurator/sharedLayout/sliders/copy/nativeFoot/reversal/reload");mc.stop();return;}if(mc.level==null||mc.player==null||mc.gameMode==null)return;
 if(!visualOracle){com.vandorlabs.neoforge.gear.client.NativeLandingAngleVisualChecks.run();visualOracle=true;}var root=NativeGearFormation.at(mc.level,NativeLandingAnglePlayerFixture.ROOT);var held=mc.player.getMainHandItem();var menu=mc.screen instanceof NativeGearScreen screen?screen:null;need(++ticks<1800,"timeout ticks="+ticks+" mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+held+" pos="+mc.player.position()+" screen="+mc.screen+" root="+(root==null?"null":root.progress()+"/target="+root.target()+"/settings="+root.settings()+"/adjust="+root.adjustingStart()+"/error="+root.error()));mc.getToasts().clear();mc.gui.getChat().clearMessages(true);
 switch(stage){
 case 0->{if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||held.getCount()!=2||!held.is(NativeGearBindings.ITEMS.get(NativeGearDefinition.ROTATING.id))||mc.player.position().distanceToSqr(2.5,96,-2)>.01)return;use(mc,NativeLandingAnglePlayerFixture.ROOT.above(),Direction.DOWN);stage=1;}
 case 1->{if(root==null||held.getCount()!=1)return;use(mc,NativeLandingAnglePlayerFixture.ROOT,Direction.DOWN);stage=2;}
 case 2->{if(root==null||!root.controller()||root.assembly().columnHeight()!=2||!held.isEmpty())return;mode(0);stage=3;}
 case 3->{if(!ack(mc,0)||!held.is(NativeGearBindings.ITEMS.get(NativeGearDefinition.PLATE.id)))return;use(mc,NativeLandingAnglePlayerFixture.ROOT.below(),Direction.DOWN);stage=4;}
 case 4->{if(root==null||root.assembly()==null||root.assembly().feet().size()!=1||!held.isEmpty())return;mode(1);stage=5;}
 case 5->{if(!ack(mc,1)||!held.is(NativeCopifierRegistrations.ITEM.get())||root.settings().startAngle()!=45||root.settings().activeAngle()!=135)return;need(root.settings().landingAnglesVersion()==-1&&root.pose(1).angleDegrees()==0&&root.pose(0).angleDegrees()==90,"client old raw values retain physical0/90");mc.options.keyShift.setDown(true);wait=0;stage=6;}
 case 6->{if(++wait<5||!mc.player.isShiftKeyDown())return;use(mc,NativeLandingAnglePlayerFixture.ROOT,Direction.EAST);stage=7;}
 case 7->{if(NativeCopifierClipboard.settings(NativeCopifierItem.data(held))==null)return;mc.options.keyShift.setDown(false);mode(2);stage=8;}
 case 8->{if(!ack(mc,2)||!held.is(VandorLabsBootstrap.CONFIGURIZER.get())||mc.player.isShiftKeyDown())return;use(mc,NativeLandingAnglePlayerFixture.ROOT,Direction.EAST);stage=9;}
 case 9->{if(menu==null)return;need(slider(menu,"Start").getIntValue()==0&&slider(menu,"End").getIntValue()==6,"landing controls display effective0/90 rather than ignored raw values");need(menu.button(9)==null,"landing omits unrelated join controls");shot="legacy-dialog";stage=90;}
 case 90->{if(shot!=null||menu==null)return;click(menu,21,0);stage=10;}
 case 10->{if(menu==null||menu.waitingForReply()||menu.page()!=1)return;need(menu.channelField()!=null&&menu.button(0)!=null&&menu.button(9)==null,"standard Redstone tab owns trigger and channels");menu.channelField().setValue("7");menu.apply();stage=11;}
 case 11->{if(menu==null||menu.waitingForReply()||!root.settings().channels().toString().equals("7"))return;mode(3);stage=12;}
 case 12->{if(!ack(mc,3)||menu==null)return;click(menu,20,0);stage=13;}
 case 13->{if(menu==null||menu.waitingForReply()||menu.page()!=0)return;choose(menu,"End",0);stage=14;}
 case 14->{if(menu==null||menu.waitingForReply()||root.settings().landingAnglesVersion()!=1||root.settings().activeAngle()!=0)return;mode(4);stage=15;}
 case 15->{if(!ack(mc,4)||menu==null||menu.waitingForReply())return;previousRendered=Double.NaN;observing=true;choose(menu,"Start",45);stage=16;}
 case 16->{if(root==null||root.adjustingStart()||root.progress()!=1||root.settings().startAngle()!=45||root.settings().activeAngle()!=0||menu==null||menu.waitingForReply())return;if(!menu.button(2).getMessage().getString().equals("Target: Start"))return;need(root.pose(1).angleDegrees()==45&&root.landingEndpoints().nativeAt(0)&&!root.landingEndpoints().nativeAt(1),"client completed physical45 with only END0 native and matching target label");need(renderedFrames>10,"actual rendered adjustment frames="+renderedFrames);observing=false;mode(5);stage=17;}
 case 17->{if(!ack(mc,5)||menu==null)return;shot="configured-dialog";stage=91;}
 case 91->{if(shot!=null||menu==null)return;choose(menu,"End",30);stage=18;}
 case 18->{if(menu==null||menu.waitingForReply()||menu.error().isEmpty())return;need(menu.editingSettings().activeAngle()==0&&root.settings().activeAngle()==0,"both-invalid choices unchanged draft="+menu.editingSettings()+" tile="+root.settings());need(menu.error().contains("grid-aligned"),"specific refusal text="+menu.error());mode(6);stage=19;}
 case 19->{if(!ack(mc,6)||menu!=null||!held.isEmpty())return;use(mc,NativeLandingAnglePlayerFixture.ROOT,Direction.EAST);stage=20;}
 case 20->{if(root.progress()!=0||root.target())return;need(!root.blocked()&&root.landingEndpoints().uprightAt(0),"ordinary native physical stroke reaches phase0-only support endpoint");mode(7);stage=21;}
 case 21->{if(!ack(mc,7))return;use(mc,NativeLandingAnglePlayerFixture.ROOT,Direction.EAST);stage=22;}
 case 22->{if(root.progress()!=1||!root.target())return;need(!root.blocked(),"unobstructed ordinary reversal");mode(8);stage=23;}
 case 23->{if(!ack(mc,8)||shot!=null)return;mc.level.disconnect();mc.disconnect();stage=25;}
 default->{}
 }}
 @SubscribeEvent public static void capture(RenderFrameEvent.Post event)throws Exception{if(!NativeLandingAnglePlayerFixture.enabled()||terminal)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.getOverlay()!=null)return;if(observing){var root=NativeGearFormation.at(mc.level,NativeLandingAnglePlayerFixture.ROOT);if(root!=null){double phase=com.vandorlabs.neoforge.gear.client.NativeGearVisualClock.pose(root),angle=root.pose(phase).angleDegrees();if(Double.isFinite(previousRendered))need(Math.abs(angle-previousRendered)<8,"actual rendered angle continuity before="+previousRendered+" after="+angle+" phase="+phase+" authority="+root.progress()+" adjusting="+root.adjustingStart());previousRendered=angle;renderedFrames++;if(!root.adjustingStart()&&root.settings().startAngle()==45&&root.progress()==1)need(Math.abs(angle-45)<1e-9,"rendered completion agrees with physical45: "+angle);}}if(shot==null)return;try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){var path=mc.gameDirectory.toPath().resolve("screenshots/native-landing-angle-"+shot+".png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);}shot=null;}
}
