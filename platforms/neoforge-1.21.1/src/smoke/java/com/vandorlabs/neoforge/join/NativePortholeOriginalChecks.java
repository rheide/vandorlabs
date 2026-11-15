package com.vandorlabs.neoforge.join;
import com.vandorlabs.neoforge.copifier.*;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.surface.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Checks real published model data and emitted records on unchanged original members. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativePortholeOriginalChecks {
 private static int stage,age,checks;private static boolean done;private static List<String> oldTerminal;
 private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Native Porthole originals stage="+stage+" age="+age+": "+why);}
 private static void next(int n){stage=n;age=0;}
 private static void mode(int n){PacketDistributor.sendToServer(new NativePortholeOriginalFixture.Mode(n));}
 private static boolean ack(Minecraft mc,int n){return mc.level.getBlockState(NativePortholeOriginalFixture.ACK.east(n)).is(Blocks.STONE);}
 private static NativeWallEntity wall(Minecraft mc,BlockPos pos){return mc.level.getBlockEntity(pos) instanceof NativeWallEntity w?w:null;}
 private static boolean rows(Minecraft mc,BlockPos pos,int rows){return wall(mc,pos)!=null&&wall(mc,pos).apertureLayout().pane().rows()==rows;}
 private static boolean ready(Minecraft mc,BlockPos source,boolean connected){return mc.screen==null&&mc.gameMode.getPlayerMode()==GameType.SURVIVAL&&mc.player.getInventory().selected==0&&mc.player.getMainHandItem().is(NativeCopifierRegistrations.ITEM.get())&&NativeCopifierOptions.connected(NativeCopifierItem.data(mc.player.getMainHandItem()))==connected&&mc.player.position().distanceToSqr(-164.5,96,source.getZ()+.5)<.04;}
 private static void use(Minecraft mc,BlockPos pos){var state=mc.level.getBlockState(pos);var bounds=state.getShape(mc.level,pos).bounds();double side=mc.player.getX()<pos.getX()+.5?bounds.minX:bounds.maxX;var delta=new Vec3(pos.getX()+side,pos.getY()+.12,pos.getZ()+.12).subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))));mc.gameRenderer.pick(1);need(mc.hitResult instanceof BlockHitResult h&&h.getBlockPos().equals(pos),"ordinary outline pick expected="+pos+" actual="+mc.hitResult);need(mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,(BlockHitResult)mc.hitResult).consumesAction(),"actual held Duplifier C2S");}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{if(!NativePortholeOriginalFixture.enabled()||done)return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen s){s.onClose();return;}if(stage==90&&mc.level==null){done=true;System.out.println("[vandorlabs][porthole-originals] client PASS checks="+checks+" actualMixedPolicy/unchangedOriginal/modelData/emittedRecords/sixFullCells");mc.stop();return;}if(mc.level==null||mc.player==null||mc.gameMode==null||mc.getOverlay()!=null)return;need(++age<360,"bounded actual state wait pos="+mc.player.position()+" held="+mc.player.getMainHandItem());switch(stage){
 case 0->{if(!ready(mc,NativePortholeOriginalFixture.ALLSOURCE,false)||!rows(mc,NativePortholeOriginalFixture.MIX,4)||!rows(mc,NativePortholeOriginalFixture.MIX.above(3),4)||!rows(mc,NativePortholeOriginalFixture.MIXREF,5)||age<30)return;oldTerminal=NativeDirectionalJoinChecks.records(mc,NativePortholeOriginalFixture.MIX.above(3));mc.options.keyShift.setDown(true);next(1);}
 case 1->{if(!mc.player.isShiftKeyDown()||age<6)return;use(mc,NativePortholeOriginalFixture.ALLSOURCE);next(2);}
 case 2->{var copied=NativeCopifierClipboard.settings(NativeCopifierItem.data(mc.player.getMainHandItem()));if(copied.getCompound("texture_join_policy").getInt("Direction")!=1)return;mode(0);mc.options.keyShift.setDown(false);next(3);}
 case 3->{if(!ack(mc,0)||mc.player.isShiftKeyDown()||age<8||mc.player.position().distanceToSqr(-158.5,96,NativePortholeOriginalFixture.MIX.getZ()+.5)>.04)return;use(mc,NativePortholeOriginalFixture.MIX.above(4));next(4);}
 case 4->{if(wall(mc,NativePortholeOriginalFixture.MIX.above(4)).textureJoin().direction!=1||age<30)return;System.out.println("[vandorlabs][porthole-originals] mixed client lower="+wall(mc,NativePortholeOriginalFixture.MIX).apertureLayout()+" upper="+wall(mc,NativePortholeOriginalFixture.MIX.above(4)).apertureLayout());need(rows(mc,NativePortholeOriginalFixture.MIX,5)&&rows(mc,NativePortholeOriginalFixture.MIX.above(4),5),"unchanged far original refreshes after rejected upper endpoint becomes All");for(int row=0;row<5;row++)need(NativeDirectionalJoinChecks.records(mc,NativePortholeOriginalFixture.MIX.above(row)).equals(NativeDirectionalJoinChecks.records(mc,NativePortholeOriginalFixture.MIXREF.above(row))),"actual original emitted records equal fresh mixed-policy reference row="+row);need(!oldTerminal.equals(NativeDirectionalJoinChecks.records(mc,NativePortholeOriginalFixture.MIX.above(3))),"former four-cell terminal closing rim retired");mode(1);next(5);}
 case 5->{if(!ack(mc,1))return;mode(2);next(6);}
 case 6->{if(!ack(mc,2)||!ready(mc,NativePortholeOriginalFixture.SOURCE,true)||age<10)return;mc.options.keyShift.setDown(true);next(7);}
 case 7->{if(!mc.player.isShiftKeyDown()||age<6)return;use(mc,NativePortholeOriginalFixture.SOURCE);next(8);}
 case 8->{var copied=NativeCopifierClipboard.settings(NativeCopifierItem.data(mc.player.getMainHandItem()));if(copied.getCompound("texture_join_policy").getInt("Direction")!=2)return;mode(3);mc.options.keyShift.setDown(false);next(9);}
 case 9->{if(!ack(mc,3)||mc.player.isShiftKeyDown()||age<8||mc.player.position().distanceToSqr(-158.5,96,NativePortholeOriginalFixture.CLICK.getZ()+.5)>.04)return;use(mc,NativePortholeOriginalFixture.CLICK);next(10);}
 case 10->{if(age<30)return;mode(4);next(11);}
 case 11->{if(!ack(mc,4))return;for(int col=0;col<3;col++)for(int row=0;row<2;row++){BlockPos pos=NativePortholeOriginalFixture.BASE.south(col).above(row);need(wall(mc,pos).textureJoin().direction==2&&rows(mc,pos,2),"all six original settings/modeldata updated "+pos);need(NativeDirectionalJoinChecks.records(mc,pos).equals(NativeDirectionalJoinChecks.records(mc,NativePortholeOriginalFixture.REF.above(row))),"six original emitted frame/rim/pane records match reference "+pos);}mc.level.disconnect();mc.disconnect();next(90);}
 default->{} }}
}
