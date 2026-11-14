package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.gear.client.*;
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

@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeGearInteractionClientChecks {
    private static int stage,ticks,checks;private static boolean shot,rideChecked;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Gear interaction client stage="+stage+" tick="+ticks+": "+why);}
    private static void mode(int operation){PacketDistributor.sendToServer(new NativeGearInteractionClientFixture.Mode(operation));}
    private static boolean ack(Minecraft mc,int operation){return mc.level.getBlockState(NativeGearInteractionClientFixture.ACK.east(operation)).is(Blocks.STONE);}
    private static NativeGearEntity tile(Minecraft mc,BlockPos root){return NativeGearFormation.at(mc.level,root);}
    private static void place(Minecraft mc,BlockPos support,Direction face){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)).add(0,mc.level.getBlockState(support).getBlock() instanceof NativeGearBlock block&&block.definition().kind==NativeGearDefinition.Kind.PLATE?3/32D-.5:0,0),face,support,false));}
    private static void input(Minecraft mc){mc.gameRenderer.pick(1);var event=new InputEvent.InteractionKeyMappingTriggered(1,mc.options.keyUse,InteractionHand.MAIN_HAND);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);need(event.isCanceled(),"actual client use event routed to exact mechanism packet");}
    private static void look(Minecraft mc,Vec3 point){var direction=point.subtract(mc.player.getEyePosition()).normalize();mc.player.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));mc.player.setXRot((float)-Math.toDegrees(Math.asin(direction.y)));}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!NativeGearInteractionClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}if(stage==17&&mc.level==null){System.out.println("[vandorlabs][gear-interaction-client] client PASS checks="+checks+" actualSurvivalPlacement/miningKeyInput/movingRay/RootConfigurator/physicalRider/reversal");stage=18;mc.stop();return;}if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<1800,"timeout mode="+mc.gameMode.getPlayerMode()+" held="+mc.player.getMainHandItem()+" slot="+mc.player.getInventory().selected+" pos="+mc.player.position()+" screen="+mc.screen+" attack="+mc.options.keyAttack.isDown());var root=tile(mc,NativeGearInteractionClientFixture.ROOT);var rider=tile(mc,NativeGearInteractionClientFixture.RIDER);
        switch(stage){case 0->{if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!mc.player.getMainHandItem().is(NativeGearBindings.ITEMS.get(NativeGearDefinition.ROTATING.id))||mc.player.position().distanceToSqr(.5,96,-3)>.01)return;place(mc,NativeGearInteractionClientFixture.ROOT.above(),Direction.DOWN);stage=1;}
        case 1->{if(root==null||mc.player.getMainHandItem().getCount()!=1)return;place(mc,NativeGearInteractionClientFixture.ROOT,Direction.DOWN);stage=2;}
        case 2->{if(root==null||!root.controller()||root.assembly().columnHeight()!=2||!mc.player.getMainHandItem().isEmpty())return;mode(0);stage=3;}
        case 3->{if(!mc.player.getMainHandItem().is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return;place(mc,NativeGearInteractionClientFixture.ROOT,Direction.NORTH);stage=4;}
        case 4->{if(!(mc.screen instanceof NativeGearScreen screen))return;var button=screen.button(2);need(button!=null&&screen.mouseClicked(button.getX()+button.getWidth()/2D,button.getY()+8,0),"shared dedicated target option");mc.player.closeContainer();stage=5;}
        case 5->{if(root.progress()!=0||root.payloadView()==null)return;mode(1);stage=6;}
        case 6->{if(!ack(mc,1)||!mc.player.getMainHandItem().is(Items.DIAMOND_AXE))return;var hit=NativeGearPayloadSelection.refreshPick(1);if(hit==null||hit.owner()!=root)return;var attack=new InputEvent.InteractionKeyMappingTriggered(0,mc.options.keyAttack,InteractionHand.MAIN_HAND);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(attack);need(attack.isCanceled(),"actual attack event protects Root proxy from vanilla mining");mc.options.keyAttack.setDown(true);stage=7;}
        case 7->{if(root.payloadView()!=null||!root.error().isEmpty())return;mc.options.keyAttack.setDown(false);mode(2);stage=8;}
        case 8->{if(!ack(mc,2)||!mc.player.getMainHandItem().is(NativeGearBindings.ITEMS.get(NativeGearDefinition.EXTENDER.id))||mc.player.position().distanceToSqr(8.5,96,-3)>.01)return;place(mc,NativeGearInteractionClientFixture.RIDER.above(),Direction.DOWN);stage=9;}
        case 9->{if(rider==null||mc.player.getMainHandItem().getCount()!=1)return;place(mc,NativeGearInteractionClientFixture.RIDER,Direction.DOWN);stage=10;}
        case 10->{if(rider==null||!rider.controller()||rider.assembly().columnHeight()!=2||!mc.player.getMainHandItem().isEmpty())return;mode(3);stage=11;}
        case 11->{if(!ack(mc,3)||!mc.player.getMainHandItem().is(NativeGearBindings.ITEMS.get(NativeGearDefinition.PLATE.id)))return;place(mc,NativeGearInteractionClientFixture.RIDER.below(),Direction.DOWN);stage=12;}
        case 12->{if(mc.player.getMainHandItem().getCount()!=2)return;place(mc,NativeGearInteractionClientFixture.RIDER.below(2),Direction.EAST);stage=13;}
        case 13->{if(mc.player.getMainHandItem().getCount()!=1)return;place(mc,NativeGearInteractionClientFixture.RIDER.below(2).east(),Direction.EAST);stage=14;}
        case 14->{if(!mc.player.getMainHandItem().isEmpty()||rider.assembly().feet().size()!=3)return;mode(4);stage=15;ticks=0;}
        case 15->{if(!ack(mc,4)||mc.player.position().distanceToSqr(10.5,97.1875,.5)>.03)return;var boxes=rider.interactionBoxes(rider.progress());var far=boxes.stream().filter(box->box.minX<10.5&&box.maxX>10.5).max(java.util.Comparator.comparingDouble(box->box.maxY)).orElseThrow();look(mc,far.getCenter());if(++ticks<8)return;var hit=NativeGearMechanismSelection.refreshPick(1);if(hit==null||hit.owner()!=rider)return;input(mc);stage=16;ticks=0;}
        case 16->{if(rider.blocked())throw new AssertionError("Actual player stroke blocked "+rider.error());if(rider.progress()!=0)return;if(!ack(mc,5)){if(!rideChecked){rideChecked=true;mode(5);}return;}var box=rider.interactionBoxes(rider.progress()).stream().filter(b->b.minX<10.5&&b.maxX>10.5).max(java.util.Comparator.comparingDouble(b->b.maxY)).orElseThrow();look(mc,box.getCenter());if(++ticks<10)return;input(mc);stage=19;ticks=0;}
        case 19->{if(rider.blocked())throw new AssertionError("Actual rider reversal blocked "+rider.error());if(rider.progress()!=1)return;need(Math.abs(mc.player.getY()-97.1875)<.15,"actual client returned rider support");if(!shot){shot=true;try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){var path=mc.gameDirectory.toPath().resolve("screenshots/native-gear-rider.png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);}catch(java.io.IOException failure){throw new RuntimeException(failure);}}mode(6);stage=20;}
        case 20->{if(!ack(mc,6))return;mc.level.disconnect();mc.disconnect();stage=17;}
        default->{}}
    }
}
