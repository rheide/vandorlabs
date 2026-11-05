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

/** The existing mounted screen handles both profile rows and independent inline text flushing. */
public final class SignalGuiClientChecks {
    private static int ticks,stage,at;private static boolean capture;private static volatile boolean captured;
    private static void require(boolean value,String message){SignalGuiServerChecks.require(value,"stage="+stage+" "+message);}
    private static void next(){stage++;at=ticks;}
    private static NativeSignalControlBlockEntity tile(Minecraft mc,boolean slider){return (NativeSignalControlBlockEntity)mc.level.getBlockEntity(slider?SignalGuiServerChecks.SLIDER:SignalGuiServerChecks.POS);}
    private static NativeBinaryControlScreen ui(Minecraft mc){require(mc.screen instanceof NativeBinaryControlScreen,"shared mounted screen missing");return (NativeBinaryControlScreen)mc.screen;}
    private static void click(NativeBinaryControlScreen s,double x,double y,int button){s.mouseClicked(x,y,button);s.mouseReleased(x,y,button);}
    private static void option(NativeBinaryControlScreen s,int id,int button){var control=s.mountButton(id);require(control!=null,"missing option "+id);click(s,control.getX()+3,control.getY()+3,button);}
    private static void tab(NativeBinaryControlScreen s,int page){click(s,s.panelLeft()+(page==0?20:124),s.panelTop()+36,0);}
    private static void open(Minecraft mc,boolean slider){var p=slider?SignalGuiServerChecks.SLIDER:SignalGuiServerChecks.POS;var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(p.getX()+.5,p.getY()+.5,p.getZ()+.96),Direction.NORTH,p,false));require(result.consumesAction(),"actual configuration refused");}
    private static void done(NativeBinaryControlScreen s){click(s,s.panelLeft()+120,s.panelTop()+212,0);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!SignalGuiServerChecks.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen s){s.onClose();return;}if(mc.player==null||mc.level==null||mc.gameMode==null)return;
        require(++ticks<2400,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position());
        if(stage==0){if(ticks<80||mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())||!(mc.level.getBlockEntity(SignalGuiServerChecks.POS) instanceof NativeSignalControlBlockEntity))return;open(mc,false);next();return;}if(ticks<at+12)return;
        if(stage==1){var s=ui(mc);require(s.mountButton(12)==null&&s.mountButton(13).getMessage().getString().equals("Type: Standard"),"throttle profile rows");option(s,13,1);require(s.controlTypeDraft()==2&&s.mountButton(13).getMessage().getString().equals("Type: Grip"),"previous type");tab(s,1);for(int i=0;i<5;i++)option(s,7,1);require(s.lowLimitDraft()==13,"Low previous wraps to High-2");option(s,8,0);require(s.highLimitDraft()==15,"High single value at Low13");s.keyPressed(256,0,0);next();}
        else if(stage==2){if(mc.screen!=null)return;var t=tile(mc,false);require(t.getControlType()==0&&t.getLowLimit()==5&&t.getHighLimit()==15&&t.latchedLevel(201)==4&&t.latchedLevel(202)==11,"Escape changed type/limits/bank");open(mc,false);next();}
        else if(stage==3){var s=ui(mc);option(s,13,0);option(s,9,0);tab(s,1);var field=s.inlineWidget().channelField();click(s,field.getX()+3,field.getY()+5,0);field.setValue("201, 202");s.keyPressed(257,0,0);require(mc.screen==s&&tile(mc,false).getControlType()==0&&tile(mc,false).mountSettings().height==0,"inline Enter committed General draft");tab(s,0);done(s);next();}
        else if(stage==4){var t=tile(mc,false);if(mc.screen!=null||t.getControlType()!=1||t.latchedLevel(201)!=5||t.latchedLevel(202)!=5)return;require(t.mountSettings().height==1,"Done mount");open(mc,false);next();}
        else if(stage==5){var s=ui(mc);require(s.mountButton(13).getMessage().getString().equals("Type: Twin"),"reopened Twin label");option(s,13,0);tab(s,1);option(s,7,0);option(s,8,1);require(s.lowLimitDraft()==6&&s.highLimitDraft()==14,"bounded limit drafts");mc.options.guiScale().set(3);mc.resizeDisplay();require(s.panelTop()>=0&&s.panelTop()+232<=s.height&&s.controlTypeDraft()==2&&s.lowLimitDraft()==6&&s.highLimitDraft()==14,"real scale3 retained draft rows");tab(s,0);s.keyPressed(257,0,0);next();}
        else if(stage==6){var t=tile(mc,false);if(mc.screen!=null||t.getControlType()!=2||t.getLowLimit()!=6||t.getHighLimit()!=14||t.latchedLevel(201)!=6||t.latchedLevel(202)!=6)return;open(mc,true);next();}
        else if(stage==7){var s=ui(mc);require(s.mountButton(13)==null&&s.mountButton(12)==null,"wall slider offered type/size");option(s,9,0);option(s,9,0);tab(s,1);option(s,7,1);option(s,7,1);for(int i=0;i<4;i++)option(s,8,1);require(s.lowLimitDraft()==3&&s.highLimitDraft()==11,"wall limit drafts");tab(s,0);s.keyPressed(257,0,0);next();}
        else if(stage==8){var t=tile(mc,true);if(mc.screen!=null||t.getLowLimit()!=3||t.getHighLimit()!=11||t.mountSettings().height!=2||t.latchedLevel(211)!=3||t.latchedLevel(212)!=3)return;open(mc,true);next();}
        else if(stage==9){var s=ui(mc);tab(s,1);option(s,7,0);option(s,8,1);s.keyPressed(256,0,0);next();}
        else if(stage==10){if(mc.screen!=null)return;require(tile(mc,true).getLowLimit()==3&&tile(mc,true).getHighLimit()==11,"slider Escape changed persisted limits");mc.player.getInventory().selected=1;mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(1));shift(mc,true);next();}
        else if(stage==11){if(mc.player.getInventory().selected!=1||!mc.player.getMainHandItem().isEmpty())return;open(mc,false);next();}
        else if(stage==12){shift(mc,false);var s=ui(mc);require(s.mountButton(13).getMessage().getString().equals("Type: Grip"),"reopened Grip label");tab(s,1);mc.options.guiScale().set(2);mc.resizeDisplay();capture=true;next();}
        else if(stage==13){if(!captured)return;ui(mc).keyPressed(256,0,0);next();}
        else if(stage==14){if(mc.screen!=null||!SignalControlSceneCapture.capture(mc))return;shift(mc,false);System.out.println("[vandorlabs][signal-gui] client PASS shared screen all throttle types wall slider limits left-right Done GeneralEnter inlineEnter Escape tabs actual scales Configurizer survivalshift");mc.stop();stage=15;}
    }
    private static void shift(Minecraft mc,boolean value){mc.options.keyShift.setDown(value);mc.player.setShiftKeyDown(value);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,value?net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));}
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){if(!SignalGuiServerChecks.enabled()||!capture)return;capture=false;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"signal-control-dialog-native.png",mc.getMainRenderTarget(),message->{captured=true;});}
}
