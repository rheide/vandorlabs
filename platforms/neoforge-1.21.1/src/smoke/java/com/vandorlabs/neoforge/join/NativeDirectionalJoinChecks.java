package com.vandorlabs.neoforge.join;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.copifier.*;
import com.vandorlabs.neoforge.glass.*;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.surface.TextureJoinOwner;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
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

/** Actual host publications, production quad emitters and normal shifted/nonshifted use packets. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeDirectionalJoinChecks {
    private static int stage,age,family,checks;private static boolean depthsChecked;private static List<List<String>> before;
    private static void need(boolean yes,String text){checks++;if(!yes)throw new IllegalStateException("Native directional joins family="+family+" stage="+stage+" age="+age+": "+text);}
    private static void next(int value){stage=value;age=0;}
    private static void mode(int offset){PacketDistributor.sendToServer(new NativeDirectionalJoinFixture.Mode(family*3+offset));}
    private static boolean ack(Minecraft mc,int offset){return mc.level.getBlockState(NativeDirectionalJoinFixture.ACK.east(family*3+offset)).is(Blocks.STONE);}
    private static boolean policy(Minecraft mc,BlockPos pos,int expected){return mc.level.getBlockEntity(pos) instanceof TextureJoinOwner tile&&tile.textureJoin().direction==expected;}
    private static boolean ready(Minecraft mc,boolean joined){for(int i=0;i<3;i++){var tile=mc.level.getBlockEntity(NativeDirectionalJoinFixture.target(family).east(i));if(tile instanceof NativeGlassEntity glass){if(glass.visual().layout().columns()!=(joined?3:1)||glass.visual().frameMask()!=(joined?(i==0?13:i==2?14:12):15))return false;}else if(tile instanceof NativeWallEntity wall){if(wall.apertureLayout().pane().columns()!=(joined?3:1)||!joined&&(wall.apertureLayout().hideLeft()||wall.apertureLayout().hideRight()))return false;}else return false;}return true;}
    private static void use(Minecraft mc,BlockPos pos){double z=pos.getZ()+(family==1?1:.625);var delta=new Vec3(pos.getX()+.12,pos.getY()+.12,z).subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))));mc.gameRenderer.pick(1);need(mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos),"fresh vanilla ray expected="+pos+" actual="+mc.hitResult);need(mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,(BlockHitResult)mc.hitResult).consumesAction(),"normal real Duplifier packet");}
    @SuppressWarnings("unchecked")
    private static List<String> records(Minecraft mc,BlockPos pos)throws Exception {
        var tile=mc.level.getBlockEntity(pos);var state=mc.level.getBlockState(pos);List<BakedQuad> quads=new ArrayList<>();var model=mc.getBlockRenderer().getBlockModel(state);var data=tile.getModelData();var random=net.minecraft.util.RandomSource.create(0);
        for(var layer:model.getRenderTypes(state,random,data))for(Direction face:new Direction[]{null,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})quads.addAll(model.getQuads(state,face,net.minecraft.util.RandomSource.create(0),data,layer));
        if(tile instanceof NativeGlassEntity glass){var bake=NativeFlatGlassRenderer.currentBake();need(bake!=null,"current production Glass bake");var draw=bake.getClass().getDeclaredMethod("draw",NativeFlatGlassVisual.class);draw.setAccessible(true);var result=draw.invoke(bake,glass.visual());var accessor=result.getClass().getDeclaredMethod("frame");accessor.setAccessible(true);quads.addAll((List<BakedQuad>)accessor.invoke(result));}
        List<String> out=new ArrayList<>();for(var quad:quads)out.add(quad.getSprite().contents().name()+":"+quad.getDirection()+":"+Arrays.toString(quad.getVertices()));Collections.sort(out);need(!out.isEmpty(),"nonempty actual production frame/aperture records");return out;
    }
    private static boolean depthPairsReady(Minecraft mc){for(int kind=0;kind<4;kind++)for(int endpoint=0;endpoint<2;endpoint++)if(!policy(mc,NativeDirectionalJoinFixture.depthPair(kind).above(endpoint),kind==3?0:2))return false;return true;}
    private static void checkDepthPairs(Minecraft mc)throws Exception{
        for(int kind=0;kind<4;kind++)for(int endpoint=0;endpoint<2;endpoint++){
            BlockPos pos=NativeDirectionalJoinFixture.depthPair(kind).above(endpoint);var tile=(NativeWallEntity)mc.level.getBlockEntity(pos);int expected=kind<2?2:1;
            need(tile.apertureLayout().pane().rows()==expected,"legacy depth pair kind="+kind+" endpoint="+endpoint+" actual="+tile.apertureLayout());
            if(kind==0||kind==2)need(records(mc,pos).equals(records(mc,NativeDirectionalJoinFixture.depthPair(kind+1).above(endpoint))),"full ignores unused depth / thin keeps distinct planes kind="+kind+" endpoint="+endpoint);
        }System.out.println("[vandorlabs][directional-joins] legacy-depth PASS full1/0-vs0/0/thin1/0-unjoined/emittedRecords");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        if(!NativeDirectionalJoinFixture.enabled()||stage==99)return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}
        if(stage==90&&mc.level==null){System.out.println("[vandorlabs][directional-joins] client PASS checks="+checks+" actualSurvivalCapture/connectedApply/threeOriginalMembers/mutualPublications/defaultSelectedFrames/flatFullApertures/warmCacheEmittedRecords");stage=99;mc.stop();return;}
        if(mc.level==null||mc.player==null||mc.gameMode==null||mc.getOverlay()!=null)return;
        need(++age<300,"bounded mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position()+" shift="+mc.player.isShiftKeyDown());
        if(age%80==0)System.out.println("[vandorlabs][directional-joins] progress family="+family+" stage="+stage+" age="+age+" pos="+mc.player.position());
        switch(stage) {
            case 0->{if(mc.screen!=null||mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!mc.player.getMainHandItem().is(NativeCopifierRegistrations.ITEM.get())||mc.player.position().distanceToSqr(NativeDirectionalJoinFixture.source(family).getX()+.5,96,3)>.04||!policy(mc,NativeDirectionalJoinFixture.source(family),4)||!ready(mc,true))return;if(!depthsChecked){if(!depthPairsReady(mc)||age<25)return;checkDepthPairs(mc);depthsChecked=true;}before=new ArrayList<>();for(int i=0;i<3;i++)before.add(records(mc,NativeDirectionalJoinFixture.target(family).east(i)));mc.options.keyShift.setDown(true);next(1);}
            case 1->{if(!mc.player.isShiftKeyDown()||age<6)return;use(mc,NativeDirectionalJoinFixture.source(family));next(2);}
            case 2->{var copied=NativeCopifierClipboard.settings(NativeCopifierItem.data(mc.player.getMainHandItem()));if(copied.getCompound("texture_join_policy").getInt("Direction")!=4)return;mode(0);mc.options.keyShift.setDown(false);next(3);}
            case 3->{if(!ack(mc,0)||mc.player.isShiftKeyDown()||age<8||mc.player.position().distanceToSqr(NativeDirectionalJoinFixture.target(family).getX()+1.5,96,3)>.04)return;use(mc,NativeDirectionalJoinFixture.target(family));next(4);}
            case 4->{for(int i=0;i<3;i++)if(!policy(mc,NativeDirectionalJoinFixture.target(family).east(i),4))return;if(age<25||!ready(mc,false))return;var single=records(mc,NativeDirectionalJoinFixture.source(family));for(int i=0;i<3;i++){var actual=records(mc,NativeDirectionalJoinFixture.target(family).east(i));need(actual.equals(single),"actual postapply emitted unit geometry cell="+i);need(!actual.equals(before.get(i)),"warm joined emitted geometry invalidated cell="+i);}mode(1);next(5);}
            case 5->{if(!ack(mc,1))return;mode(2);next(6);}
            case 6->{if(!ack(mc,2))return;System.out.println("[vandorlabs][directional-joins] client family PASS "+family);if(++family==NativeDirectionalJoinFixture.IDS.length){mc.level.disconnect();mc.disconnect();next(90);}else next(0);}
            default->{}
        }
    }
    private NativeDirectionalJoinChecks() { }
}
