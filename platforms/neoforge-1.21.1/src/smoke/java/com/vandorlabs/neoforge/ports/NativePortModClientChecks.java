package com.vandorlabs.neoforge.ports;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Only the four requested networks, with actual installed client transmitter branches. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativePortModClientChecks {
    private static int fixture=NativePortModClientFixture.START,stage,ticks,checks;private static boolean capture;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Native optional port client case="+NativePortModClientFixture.CASES[Math.min(fixture,3)]+" stage="+stage+" tick="+ticks+": "+why);}
    private static boolean marker(Minecraft mc,int operation){return mc.level.getBlockState(NativePortModClientFixture.marker(fixture,operation)).is(Blocks.GOLD_BLOCK);}
    private static void mode(int operation){PacketDistributor.sendToServer(new NativePortModClientFixture.Mode(fixture,operation));}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!NativePortModClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}if(stage==5&&mc.level==null){System.out.println("[vandorlabs][port-mod-client] client PASS checks="+checks+" selected="+NativePortModClientFixture.START+".."+NativePortModClientFixture.END+" nativeBranches/nativeArtwork/actualTransfers");stage=6;mc.stop();return;}if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<900,"bounded optional network phase mode="+mc.gameMode.getPlayerMode()+" pos="+mc.player.position());mc.getToasts().clear();mc.gui.getChat().clearMessages(true);var pos=NativePortModClientFixture.at(fixture);var owner=mc.level.getBlockEntity(pos) instanceof NativeResourcePortEntity port?port:null;
        if(stage==0){if(owner==null||mc.gameMode.getPlayerMode()!=GameType.CREATIVE||!mc.player.getAbilities().flying||mc.player.position().distanceToSqr(5.5,99,pos.getZ()-6.5)>.05||ticks<30)return;for(var id:new String[]{"mekanism","rftoolspower","mcjtylib","rftoolsbase"})need(ModList.get().isLoaded(id),"client optional mod loaded "+id);if(fixture!=3){need(!NativePortModClientFixture.branch(mc.level,fixture,Direction.EAST),"disabled source has no native client acceptor branch");if(NativePortModClientFixture.fair(fixture))need(!NativePortModClientFixture.branch(mc.level,fixture,Direction.WEST),"disabled west native client branch");}mode(0);stage=1;ticks=0;return;}
        if(stage==1){if(owner==null||!marker(mc,0))return;boolean east=NativePortModClientFixture.branch(mc.level,fixture,Direction.EAST),west=!NativePortModClientFixture.fair(fixture)||NativePortModClientFixture.branch(mc.level,fixture,Direction.WEST);int expected=(1<<5)|(NativePortModClientFixture.fair(fixture)?1<<4:0);need(ticks<160||east&&west&&(owner.visual().lit()&expected)==expected,"native branch/art readiness east="+east+" west="+west+" lit="+owner.visual().lit()+" expected="+expected+" peer="+(mc.level.getBlockEntity(pos.east())==null?"missing":mc.level.getBlockEntity(pos.east()).getClass().getName()));if(ticks<20||!east||!west||(owner.visual().lit()&expected)!=expected)return;mode(1);stage=2;ticks=0;return;}
        if(stage==2){if(!marker(mc,3))return;need(NativePortModClientFixture.branch(mc.level,fixture,Direction.EAST),"actual client branch retained after delivery");if(NativePortModClientFixture.fair(fixture))need(NativePortModClientFixture.branch(mc.level,fixture,Direction.WEST),"both actual cable branches retained");mc.options.hideGui=true;capture=true;stage=3;return;}
        if(stage==3){if(capture)return;mode(2);stage=4;return;}
        if(stage==4){if(!marker(mc,2))return;if(fixture==NativePortModClientFixture.END-1){mc.level.disconnect();mc.disconnect();stage=5;}else{fixture++;stage=0;ticks=0;}}
    }
    @SubscribeEvent public static void screenshot(RenderFrameEvent.Post event)throws Exception{if(!NativePortModClientFixture.enabled()||!capture)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.getOverlay()!=null)return;try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){var path=mc.gameDirectory.toPath().resolve("screenshots/native-port-"+NativePortModClientFixture.CASES[fixture].toLowerCase(java.util.Locale.ROOT)+".png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);}mc.options.hideGui=false;capture=false;}
}
