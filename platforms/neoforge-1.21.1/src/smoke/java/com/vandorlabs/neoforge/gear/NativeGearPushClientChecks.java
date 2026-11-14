package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.gear.client.NativeGearScreen;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** One actual four-section horizontal Push menu journey, without replaying the geometry matrix. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeGearPushClientChecks {
    private static int stage,ticks,checks,lastStage=-1,stageStarted;private static boolean movingDisabled,shot;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Native Push UI client stage="+stage+" tick="+ticks+": "+why);}
    private static boolean marker(Minecraft mc,int op){return mc.level.getBlockState(NativeGearPushClientFixture.ACK.above(op)).is(Blocks.GOLD_BLOCK);}
    private static void mode(int op){PacketDistributor.sendToServer(new NativeGearPushClientFixture.Mode(op));}
    private static void use(Minecraft mc,BlockPos pos,Direction face){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),face,pos,false));}
    private static void click(NativeGearScreen screen,int id,int mouse){var button=screen.button(id);need(button!=null&&button.active&&screen.mouseClicked(button.getX()+button.getWidth()/2D,button.getY()+8,mouse),"actual Source-style option "+id);}
    private static boolean ready(Minecraft mc,BlockPos pos){var tile=NativeGearFormation.at(mc.level,pos);return tile!=null&&tile.controller()&&mc.player.position().distanceToSqr(pos.getX()+.5,99,pos.getZ()-3)<.02&&mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!NativeGearPushClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}if(stage==30&&mc.level==null){System.out.println("[vandorlabs][gear-push-ui] client PASS checks="+checks+" actualSurvival4Sections/Push14/previousRightCycle/activeEndpointRefresh/C2S/copyUndo37/configuredItems/hiddenShapes");stage=31;mc.stop();return;}if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<1800,"bounded player setup mode="+mc.gameMode.getPlayerMode()+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position()+" screen="+mc.screen);mc.getToasts().clear();mc.gui.getChat().clearMessages(true);var root=NativeGearFormation.at(mc.level,NativeGearPushClientFixture.ROOT);var gui=mc.screen instanceof NativeGearScreen screen?screen:null;var held=mc.player.getMainHandItem();
        if(stage!=lastStage){lastStage=stage;stageStarted=ticks;System.out.println("[vandorlabs][gear-push-ui] client stage="+stage+" tick="+ticks);}
        String state="stage="+stage+" age="+(ticks-stageStarted)+" mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+held+" pos="+mc.player.position()+" gui="+(gui!=null)+" reply="+(gui!=null&&gui.waitingForReply())+" guiError="+(gui==null?"none":gui.error())+" progress="+(root==null?"none":root.progress())+" push="+(root!=null&&root.settings().pushBlocks())+" rootError="+(root==null?"none":root.error())+" endpointMarker="+marker(mc,8)+" shot="+shot;
        if((ticks-stageStarted)%80==79)System.out.println("[vandorlabs][gear-push-ui] waiting "+state);
        need(ticks-stageStarted<300,"stage deadline "+state);
        switch(stage){
            case 0->{if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!held.is(NativeGearBindings.ITEMS.get(NativeGearDefinition.EXTENDER.id))||held.getCount()!=4||mc.player.position().distanceToSqr(1.5,99,-2.5)>.02)return;use(mc,NativeGearPushClientFixture.ROOT.west(),Direction.EAST);stage=1;}
            case 1->{if(root==null||held.getCount()!=3)return;use(mc,NativeGearPushClientFixture.ROOT,Direction.EAST);stage=2;}
            case 2->{if(held.getCount()!=2)return;use(mc,NativeGearPushClientFixture.ROOT.east(),Direction.EAST);stage=3;}
            case 3->{if(held.getCount()!=1)return;use(mc,NativeGearPushClientFixture.ROOT.east(2),Direction.EAST);stage=4;}
            case 4->{if(root==null||!root.controller()||root.assembly().columnHeight()!=4||!held.isEmpty())return;mode(0);stage=5;}
            case 5->{if(!marker(mc,0)||!held.is(Items.CHEST))return;use(mc,NativeGearPushClientFixture.ROOT.east(3),Direction.EAST);stage=6;}
            case 6->{if(!held.isEmpty()||!mc.level.getBlockState(NativeGearPushClientFixture.ROOT.east(4)).is(Blocks.CHEST))return;mode(1);stage=7;}
            case 7->{if(!marker(mc,1)||!held.is(VandorLabsBootstrap.CONFIGURIZER.get())||mc.player.position().distanceToSqr(1.5,99,-2.5)>.02)return;use(mc,NativeGearPushClientFixture.ROOT,Direction.NORTH);stage=8;}
            case 8->{if(gui==null)return;need(root.canPushBlocks()&&gui.button(14)!=null&&gui.button(14).active,"horizontal bare Extender shows enabled Push at rest");click(gui,14,0);stage=9;}
            case 9->{if(gui==null||gui.waitingForReply()||!root.settings().pushBlocks())return;need(gui.error().isEmpty(),"left Push enable acknowledged");click(gui,14,1);stage=10;}
            case 10->{if(gui==null||gui.waitingForReply()||root.settings().pushBlocks())return;need(gui.error().isEmpty(),"previous-right Push disable acknowledged");click(gui,2,0);stage=11;}
            case 11->{if(gui==null)return;if(root.progress()>0&&root.progress()<1&&gui.button(14)!=null&&!gui.button(14).active)movingDisabled=true;need(!root.blocked(),"ordinary payload retract remains available: "+root.error());if(root.progress()!=0||!marker(mc,8)||gui.waitingForReply())return;need(movingDisabled,"Push option disabled during real stroke");need(gui.button(14)!=null&&gui.button(14).active,"same open dialog re-enables Push at active endpoint");click(gui,14,0);stage=12;}
            case 12->{if(gui==null||gui.waitingForReply()||!root.settings().pushBlocks())return;need(root.progress()==0&&gui.error().isEmpty(),"Push change accepted at active endpoint");click(gui,14,1);stage=13;}
            case 13->{if(gui==null||gui.waitingForReply()||root.settings().pushBlocks())return;need(root.progress()==0&&gui.error().isEmpty(),"active previous-right cycle accepted");shot=true;stage=14;}
            case 14->{if(shot)return;mc.player.closeContainer();mode(2);stage=15;}
            case 15->{if(!marker(mc,2))return;mode(3);stage=16;}
            case 16->{if(!marker(mc,9)||!held.is(VandorLabsBootstrap.CONFIGURIZER.get()))return;mode(4);stage=17;}
            case 17->{if(!marker(mc,4)||!ready(mc,NativeGearPushClientFixture.VERTICAL))return;use(mc,NativeGearPushClientFixture.VERTICAL,Direction.NORTH);stage=18;}
            case 18->{if(gui==null)return;need(gui.getMenu().tile.getBlockPos().equals(NativeGearPushClientFixture.VERTICAL)&&gui.button(14)==null,"vertical Push control hidden");mode(5);stage=19;}
            case 19->{if(!marker(mc,5)||!ready(mc,NativeGearPushClientFixture.ROTATING))return;use(mc,NativeGearPushClientFixture.ROTATING,Direction.NORTH);stage=20;}
            case 20->{if(gui==null)return;need(gui.getMenu().tile.getBlockPos().equals(NativeGearPushClientFixture.ROTATING)&&gui.button(14)==null,"rotating Push control hidden");mode(6);stage=21;}
            case 21->{if(!marker(mc,6)||!ready(mc,NativeGearPushClientFixture.FEET))return;use(mc,NativeGearPushClientFixture.FEET,Direction.NORTH);stage=22;}
            case 22->{if(gui==null)return;need(gui.getMenu().tile.getBlockPos().equals(NativeGearPushClientFixture.FEET)&&gui.button(14)==null,"horizontal landing feet Push control hidden");mode(7);stage=23;}
            case 23->{if(!marker(mc,7))return;mc.level.disconnect();mc.disconnect();stage=30;}
            default->{}
        }
    }
    @SubscribeEvent public static void screenshot(RenderFrameEvent.Post event)throws Exception{if(!NativeGearPushClientFixture.enabled()||!shot)return;var mc=Minecraft.getInstance();if(!(mc.screen instanceof NativeGearScreen))return;try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){var path=mc.gameDirectory.toPath().resolve("screenshots/native-push-active-option.png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);}shot=false;}
}
