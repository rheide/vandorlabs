package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.gear.client.NativeGearMechanismSelection;
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

@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeGearStowedFootClientChecks {
    private static int stage,ticks,checks;private static String shot;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Native stopped Ski client stage="+stage+" ticks="+ticks+": "+why);}
    private static void mode(int operation){PacketDistributor.sendToServer(new NativeGearStowedFootClientFixture.Mode(operation));}
    private static boolean ack(Minecraft mc,int operation){return mc.level.getBlockState(NativeGearStowedFootClientFixture.ACK.east(operation)).is(Blocks.STONE);}
    private static void place(Minecraft mc,BlockPos support,Direction face){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),face,support,false));}
    private static String status(Minecraft mc){var root=NativeGearFormation.at(mc.level,NativeGearStowedFootClientFixture.ROOT);return root==null?" rootMissing":" phase="+root.progress()+" target="+root.target()+" blocked="+root.blocked()+" error="+root.error();}
    private static void use(Minecraft mc){mc.gameRenderer.pick(1);var input=new InputEvent.InteractionKeyMappingTriggered(1,mc.options.keyUse,InteractionHand.MAIN_HAND);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(input);need(input.isCanceled(),"real use input reaches dedicated mechanism C2S");}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!NativeGearStowedFootClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}if(stage==18&&mc.level==null){System.out.println("[vandorlabs][gear-stowed-foot-client] client PASS checks="+checks+" actualSurvivalPlacement/observedCrouchThenServerReaim/realShiftUseAndOrdinaryPlacementPackets/oneItemEach/samePhase0/clientNativeOwner/singleRenderer/reversal");stage=19;mc.stop();return;}if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<1200,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" position="+mc.player.position()+" yaw="+mc.player.getYRot()+" pitch="+mc.player.getXRot()+" crouch="+mc.player.isCrouching()+" screen="+mc.screen+status(mc));mc.getToasts().clear();mc.gui.getChat().clearMessages(true);var root=NativeGearFormation.at(mc.level,NativeGearStowedFootClientFixture.ROOT);var held=mc.player.getMainHandItem();
        switch(stage){
            case 0->{if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!held.is(NativeGearBindings.ITEMS.get(NativeGearDefinition.ROTATING.id))||held.getCount()!=2||mc.player.position().distanceToSqr(.5,96,-3)>.01)return;place(mc,NativeGearStowedFootClientFixture.ROOT.above(),Direction.DOWN);stage++;}
            case 1->{if(root==null||held.getCount()!=1)return;place(mc,NativeGearStowedFootClientFixture.ROOT,Direction.DOWN);stage++;}
            case 2->{if(root==null||root.assembly()==null||root.assembly().columnHeight()!=2||!held.isEmpty())return;mode(0);stage++;}
            case 3->{if(!held.is(NativeGearBindings.ITEMS.get(NativeGearDefinition.SKI.id)))return;place(mc,NativeGearStowedFootClientFixture.ROOT.below(),Direction.DOWN);stage++;}
            case 4->{if(root.assembly().kind()!=com.vandorlabs.gear.core.GearTopology.FootKind.SKI||root.assembly().feet().size()!=1||!held.isEmpty())return;mode(1);stage++;}
            case 5->{if(!ack(mc,1)||!held.isEmpty())return;place(mc,NativeGearStowedFootClientFixture.ROOT,Direction.NORTH);stage++;}
            case 6->{if(root.progress()!=0)return;need(!root.blocked(),"initial actual stow");mode(2);stage++;}
            case 7->{if(!ack(mc,2)||!held.is(NativeGearBindings.ITEMS.get(NativeGearDefinition.SKI.id))||held.getCount()!=2||mc.player.position().distanceToSqr(3.5,96,.5)>.01)return;mc.options.keyShift.setDown(true);stage++;}
            case 8->{if(!mc.player.isShiftKeyDown()||!mc.player.isCrouching())return;mode(3);stage++;}
            case 9->{if(!ack(mc,3)||!mc.player.isCrouching())return;mc.gameRenderer.pick(1);var hit=NativeGearMechanismSelection.refreshPick(1);if(hit==null||hit.owner()!=root||!hit.source().equals(NativeGearStowedFootClientFixture.FOOT)||hit.worldFace()!=Direction.EAST)return;use(mc);stage++;}
            case 10->{if(root.assembly().feet().size()!=2||held.getCount()!=1)return;need(root.progress()==0&&!root.target()&&!root.blocked(),"client stays at stopped target");int controllers=0;for(var at:root.members()){var member=NativeGearFormation.at(mc.level,at);if(member==null||member.controllerEntity()!=root)return;need(member.mount()==Direction.DOWN,"client mounting frame");if(member.controller())controllers++;}need(controllers==1,"one assembly renderer owner after native append");stage++;}
            case 11->{if(shot!=null)return;mc.options.keyShift.setDown(false);if(mc.player.isCrouching()||mc.player.isShiftKeyDown())return;mode(4);stage++;}
            case 12->{if(!ack(mc,4)||held.getCount()!=1||mc.player.isCrouching())return;mc.gameRenderer.pick(1);var hit=NativeGearMechanismSelection.refreshPick(1);if(hit==null||hit.owner()!=root||!hit.source().equals(NativeGearStowedFootClientFixture.FOOT)||hit.worldFace()!=Direction.DOWN)return;place(mc,NativeGearStowedFootClientFixture.FOOT,Direction.DOWN);stage++;}
            case 13->{if(root.assembly().feet().size()!=3||!held.isEmpty())return;need(root.progress()==0&&!root.target()&&!root.blocked(),"ordinary placement packet keeps stopped phase");int controllers=0;for(var at:root.members()){var member=NativeGearFormation.at(mc.level,at);if(member==null||member.controllerEntity()!=root)return;need(member.mount()==Direction.DOWN,"ordinary append client native frame");if(member.controller())controllers++;}need(controllers==1,"single client renderer owner after both append routes");mode(5);stage++;}
            case 14->{if(!ack(mc,5))return;mc.options.hideGui=true;shot="stowed-appended";stage++;}
            case 15->{if(shot!=null||!ack(mc,5)||!held.isEmpty()||mc.player.isCrouching())return;place(mc,NativeGearStowedFootClientFixture.ROOT,Direction.NORTH);stage++;}
            case 16->{if(root.progress()!=1)return;need(!root.blocked()&&root.assembly().feet().size()==3,"actual reverse keeps all three native feet");mode(6);stage++;}
            case 17->{if(!ack(mc,6))return;mc.disconnect();stage++;}
        }
    }
    @SubscribeEvent public static void capture(RenderFrameEvent.Post event)throws Exception{if(!NativeGearStowedFootClientFixture.enabled()||shot==null)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.getOverlay()!=null)return;try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){var path=mc.gameDirectory.toPath().resolve("screenshots/native-"+shot+".png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);}mc.options.hideGui=false;shot=null;}
}
