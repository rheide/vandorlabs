package com.vandorlabs.neoforge.gear.track.client;

import com.vandorlabs.neoforge.gear.*;
import com.vandorlabs.neoforge.gear.track.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;

@net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs",value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class NativeTrackPlayerChecks {
    private static int stage,ticks,checks,captureFrames;
    private static String shot;private static Boolean previousHud;
    private static void shot(Minecraft mc,String name){shot=name;captureFrames=0;if(mc.screen==null){previousHud=mc.options.hideGui;mc.options.hideGui=true;}}

    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Track client stage="+stage+" tick="+ticks+": "+why);}
    private static void mode(int operation){PacketDistributor.sendToServer(new NativeTrackPlayerFixture.Mode(operation));}
    private static boolean ack(Minecraft mc,int operation){return mc.level.getBlockState(NativeTrackPlayerFixture.ACK.east(operation)).is(Blocks.STONE);}
    private static void use(Minecraft mc,BlockPos pos,Direction face){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),face,pos,false));}
    private static NativeGearEntity root(Minecraft mc){return NativeGearFormation.at(mc.level,NativeTrackPlayerFixture.ROOT);}
    @net.neoforged.bus.api.SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){if(!NativeTrackPlayerFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}if(stage==99&&mc.level==null){System.out.println("[vandorlabs][track-player] client PASS checks="+checks+" actualPlacement/join/stylePrevious/UI/stroke/reversal/mining");stage=100;mc.stop();return;}if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<1400,"timeout mode="+mc.gameMode.getPlayerMode()+" held="+mc.player.getMainHandItem()+" slot="+mc.player.getInventory().selected+" pos="+mc.player.position()+" root="+(root(mc)==null?"missing":root(mc).error()));var p=NativeTrackPlayerFixture.ROOT;var held=mc.player.getMainHandItem();mc.getToasts().clear();switch(stage){
        case 0->{if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!held.is(NativeGearBindings.ITEMS.get("gear_extender"))||mc.player.position().distanceToSqr(.5,99,-3)>.01)return;use(mc,p.above(),Direction.DOWN);stage=1;}
        case 1->{if(root(mc)==null||!held.isEmpty())return;mc.player.setYRot(0);mc.player.setXRot(12);shot(mc,"supported-arm");stage=21;}
        case 2->{if(!ack(mc,0)||!held.is(NativeGearBindings.ITEMS.get("compact_track"))||held.getCount()!=2)return;use(mc,p,Direction.DOWN);stage=3;}
        case 3->{if(!(mc.level.getBlockEntity(p.below()) instanceof NativeTrackEntity)||held.getCount()!=1)return;mode(1);stage=4;}
        case 4->{if(!ack(mc,1)||mc.player.position().distanceToSqr(.5,99,3.5)>.01||held.getCount()!=1)return;use(mc,p.below(),Direction.SOUTH);stage=5;}
        case 5->{if(!held.isEmpty()||root(mc)==null||root(mc).trackDescriptor()==null||root(mc).trackDescriptor().feet().size()!=2)return;mc.player.setYRot(180);mc.player.setXRot(20);shot(mc,"joined-tracks");stage=22;}
        case 6->{if(!ack(mc,2)||!held.is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return;use(mc,p.below().south(),Direction.SOUTH);stage=7;}
        case 7->{if(!(mc.screen instanceof com.vandorlabs.neoforge.gear.client.NativeGearScreen))return;mode(3);stage=8;}
        case 8->{if(!ack(mc,3)||!(mc.screen instanceof com.vandorlabs.neoforge.gear.client.NativeGearScreen screen))return;var button=screen.button(15);need(button!=null&&screen.mouseClicked(button.getX()+button.getWidth()/2D,button.getY()+8,1),"right previous Track style");stage=9;}
        case 9->{if(root(mc).trackDescriptor()==null||root(mc).trackDescriptor().style().ordinal()!=2)return;shot(mc,"style-options");stage=23;}
        case 10->{if(!ack(mc,4)||!(mc.screen instanceof com.vandorlabs.neoforge.gear.client.NativeGearScreen screen))return;var button=screen.button(2);need(button!=null&&screen.mouseClicked(button.getX()+button.getWidth()/2D,button.getY()+8,0),"real manual retract");stage=11;}
        case 11->{if(root(mc).progress()!=0)return;need(!root(mc).blocked(),"clear real retraction");mode(5);stage=12;}
        case 12->{if(!ack(mc,5)||!(mc.screen instanceof com.vandorlabs.neoforge.gear.client.NativeGearScreen screen))return;var button=screen.button(2);need(button!=null&&screen.mouseClicked(button.getX()+button.getWidth()/2D,button.getY()+8,0),"real deploy reversal");stage=13;}
        case 13->{if(root(mc).progress()!=1)return;need(!root(mc).blocked(),"clear real deployment");mc.player.closeContainer();mode(6);stage=14;}
        case 14->{if(!ack(mc,6)||!held.is(net.minecraft.world.item.Items.DIAMOND_PICKAXE)||mc.player.position().distanceToSqr(.5,99,3.5)>.01)return;mc.gameMode.startDestroyBlock(p.below().south(),Direction.SOUTH);stage=15;}
        case 15->{if(!mc.level.isEmptyBlock(p.below().south())){mc.gameMode.continueDestroyBlock(p.below().south(),Direction.SOUTH);return;}mc.gameMode.stopDestroyBlock();if(root(mc).trackDescriptor()==null||root(mc).trackDescriptor().feet().size()!=1)return;mode(7);stage=16;}
        case 16->{if(!ack(mc,7)||!held.is(com.vandorlabs.neoforge.landing.NativeLandingBindings.ITEMS.get("wheel_large"))||mc.player.position().distanceToSqr(8.5,99,-2)>.01)return;use(mc,new BlockPos(5,99,0),Direction.EAST);stage=17;}
        case 17->{var wheel=com.vandorlabs.neoforge.landing.NativeLandingBindings.WHEELS.get("wheel_large");if(!held.isEmpty()||!mc.level.getBlockState(new BlockPos(6,99,0)).is(wheel)||!mc.level.getBlockState(new BlockPos(6,100,1)).is(wheel))return;mc.player.setYRot(39);mc.player.setXRot(7);shot(mc,"large-side-wheel");stage=24;}
        case 18->{if(!ack(mc,8)||!held.is(net.minecraft.world.item.Items.DIAMOND_PICKAXE))return;mc.gameMode.startDestroyBlock(new BlockPos(6,100,1),Direction.EAST);stage=19;}
        case 19->{if(!mc.level.isEmptyBlock(new BlockPos(6,100,1))){mc.gameMode.continueDestroyBlock(new BlockPos(6,100,1),Direction.EAST);return;}mc.gameMode.stopDestroyBlock();mode(9);stage=20;}
        case 21->{if(shot!=null)return;mode(0);stage=2;}
        case 22->{if(shot!=null)return;mode(2);stage=6;}
        case 23->{if(shot!=null)return;mode(4);stage=10;}
        case 24->{if(shot!=null)return;mode(8);stage=18;}
        case 20->{if(!ack(mc,9))return;stage=99;mc.disconnect();}
    }}
    @net.neoforged.bus.api.SubscribeEvent public static void capture(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event)throws Exception{if(!NativeTrackPlayerFixture.enabled()||shot==null)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.getOverlay()!=null||++captureFrames<30)return;System.out.println("[vandorlabs][track-capture] "+shot+" sky="+mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,NativeTrackPlayerFixture.ROOT)+" skyFoot="+mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,NativeTrackPlayerFixture.ROOT.below())+" time="+mc.level.getDayTime()+" hud="+mc.options.hideGui);try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){var path=mc.gameDirectory.toPath().resolve("screenshots/native-track-"+shot+".png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);}finally{shot=null;if(previousHud!=null){mc.options.hideGui=previousHud;previousHud=null;}}}
    private NativeTrackPlayerChecks(){}
}
