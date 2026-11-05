package com.vandorlabs.copychecks;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.client.NativeBinaryControlScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;

/** Same mounted screen and inline widget, with power-only Size12 and real server packets. */
public final class PowerLeverGuiClientChecks {
    private static int ticks,stage,at,family;private static boolean capture;private static volatile boolean captured;
    private static void require(boolean value,String message){PowerLeverGuiServerChecks.require(value,"family="+family+" stage="+stage+" "+message);}
    private static BlockPos pos(){return family==0?PowerLeverGuiServerChecks.POS:PowerLeverGuiServerChecks.TWIN;}
    private static NativeBinaryControlBlockEntity tile(Minecraft mc){return (NativeBinaryControlBlockEntity)mc.level.getBlockEntity(pos());}
    private static NativeBinaryControlScreen ui(Minecraft mc){require(mc.screen instanceof NativeBinaryControlScreen,"shared mounted screen missing");return (NativeBinaryControlScreen)mc.screen;}
    private static void next(){stage++;at=ticks;}
    private static void click(NativeBinaryControlScreen s,double x,double y,int button){s.mouseClicked(x,y,button);s.mouseReleased(x,y,button);}
    private static void option(NativeBinaryControlScreen s,int id,int button){var b=s.mountButton(id);require(b!=null,"missing option "+id);click(s,b.getX()+3,b.getY()+3,button);}
    private static void tab(NativeBinaryControlScreen s,int page){click(s,s.panelLeft()+(page==0?20:124),s.panelTop()+36,0);}
    private static void open(Minecraft mc){var p=pos();var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(p.getX()+.5,p.getY()+.5,p.getZ()+.96),Direction.NORTH,p,false));require(result.consumesAction(),"actual Configurizer interaction refused");}
    private static void scale(Minecraft mc,int value){mc.options.guiScale().set(value);mc.resizeDisplay();var s=ui(mc);require(s.panelTop()>=0&&s.panelTop()+232<=s.height,"scaled fixed panel clipped");}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!PowerLeverGuiServerChecks.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen s){s.onClose();return;}if(mc.player==null||mc.level==null||mc.gameMode==null)return;
        require(++ticks<2200,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position());
        if(stage==0){if(ticks<80||mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())||!(mc.level.getBlockEntity(pos()) instanceof NativeBinaryControlBlockEntity))return;open(mc);next();return;}
        if(ticks<at+12)return;
        if(stage==1){var s=ui(mc);require(s.panelWidth()==280&&s.panelHeight()==232,"same mounted panel");require(s.mountButton(12).getMessage().getString().equals(family==0?"Size: Industrial":"Size: Small"),"canonical initial size label");option(s,12,1);require(s.sizeDraft()==family&&tile(mc).getPowerLeverSize()==1-family,"right previous must remain draft");require(s.mountButton(12).getMessage().getString().equals(family==0?"Size: Compact":"Size: Large"),"canonical alternate size label");s.keyPressed(256,0,0);next();}
        else if(stage==2){if(mc.screen!=null)return;require(tile(mc).getPowerLeverSize()==1-family,"Escape applied size draft");open(mc);next();}
        else if(stage==3){var s=ui(mc);option(s,12,0);option(s,9,0);tab(s,1);var field=s.inlineWidget().channelField();click(s,field.getX()+3,field.getY()+5,0);field.setValue("201, 202");s.keyPressed(257,0,0);require(mc.screen==s&&tile(mc).getPowerLeverSize()==1-family,"inline Enter committed/closed General draft");tab(s,0);scale(mc,3);require(s.sizeDraft()==family&&s.mountDraft().height==1,"tab/scale lost size/mount drafts");if(family==0){click(s,s.panelLeft()+120,s.panelTop()+212,0);next();}else {s.keyPressed(257,0,0);next();}}
        else if(stage==4){if(mc.screen!=null||tile(mc).getPowerLeverSize()!=family||tile(mc).mountSettings().height!=1)return;open(mc);next();}
        else if(stage==5){var s=ui(mc);require(s.sizeDraft()==family&&s.mountButton(12).getMessage().getString().equals(family==0?"Size: Compact":"Size: Large"),"reopened persisted size");option(s,12,0);option(s,9,0);tab(s,1);tab(s,0);require(s.sizeDraft()==1-family,"left next cycle/tab draft");s.keyPressed(256,0,0);next();}
        else if(stage==6){if(mc.screen!=null)return;require(tile(mc).getPowerLeverSize()==family&&tile(mc).mountSettings().height==1,"Escape overwrote persisted size/mount");if(family==0){family=1;stage=0;at=ticks;}else {open(mc);next();}}
        else if(stage==7){scale(mc,2);capture=true;next();}
        else if(stage==8){if(!captured)return;ui(mc).keyPressed(256,0,0);next();}
        else if(stage==9){if(mc.screen!=null||!PowerLeverSceneCapture.capture(mc))return;System.out.println("[vandorlabs][power-gui] client PASS both canonical families shared mounted screen Size12 left-right Done GeneralEnter inlineEnter Escape resize tabs");mc.stop();stage=10;}
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){if(!PowerLeverGuiServerChecks.enabled()||!capture)return;capture=false;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"power-lever-dialog-native.png",mc.getMainRenderTarget(),message->{captured=true;});}
}
