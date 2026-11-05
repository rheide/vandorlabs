package com.vandorlabs.copychecks;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.client.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.TransmissionSettings;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;

public final class BinaryGuiClientChecks {
    private static int ticks,stage,at,rangeTarget;private static boolean capture,restoredScale;private static volatile boolean captured;private static AABB originalShape;
    private static void next(){stage++;at=ticks;}
    private static void require(boolean value,String message){BinaryGuiServerChecks.require(value,"stage="+stage+" "+message);}
    private static NativeBinaryControlBlockEntity tile(Minecraft mc){return (NativeBinaryControlBlockEntity)mc.level.getBlockEntity(BinaryGuiServerChecks.POS);}
    private static NativeBinaryControlScreen ui(Minecraft mc){require(mc.screen instanceof NativeBinaryControlScreen,"expected dedicated binary screen");return (NativeBinaryControlScreen)mc.screen;}
    private static boolean mount(NativeBinaryControlBlockEntity tile,int h,int t,int d){return tile.mountSettings().height==h&&tile.mountSettings().tilt==t&&tile.mountSettings().direction==d;}
    private static boolean channels(NativeBinaryControlBlockEntity tile,int... ids){return tile.transmissionChannels().equals(ChannelList.of(ids));}
    private static void click(NativeBinaryControlScreen screen,double x,double y,int button){screen.mouseClicked(x,y,button);screen.mouseReleased(x,y,button);}
    private static void option(NativeBinaryControlScreen screen,int id,int button){var control=screen.mountButton(id);click(screen,control.getX()+3,control.getY()+3,button);}
    private static void tab(NativeBinaryControlScreen screen,int page){click(screen,screen.panelLeft()+(page==0?20:124),screen.panelTop()+36,0);}
    private static void text(NativeBinaryControlScreen screen,String value){var field=screen.inlineWidget().channelField();click(screen,field.getX()+3,field.getY()+5,0);field.setValue(value);}
    private static void range(NativeBinaryControlScreen screen,int index,int button){var slider=screen.inlineWidget().rangeControl();double x=slider.getX()+4+(slider.getWidth()-8)*index/6.0,y=slider.getY()+10;screen.mouseClicked(x,y,button);screen.mouseDragged(x,y,button,8,0);screen.mouseReleased(x,y,button);}
    private static void local(NativeBinaryControlScreen screen,int button){var control=screen.inlineWidget().localControl();click(screen,control.getX()+4,control.getY()+6,button);}
    private static void scale(Minecraft mc,int value){mc.options.guiScale().set(value);mc.resizeDisplay();var screen=ui(mc);require(screen.width==mc.getWindow().getGuiScaledWidth()&&screen.height==mc.getWindow().getGuiScaledHeight(),"actual scaled Screen dimensions");require(screen.panelLeft()>=0&&screen.panelTop()>=0&&screen.panelLeft()+280<=screen.width&&screen.panelTop()+232<=screen.height,"fixed panel outside scaled viewport");}
    private static void shift(Minecraft mc,boolean value){mc.options.keyShift.setDown(value);mc.player.setShiftKeyDown(value);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,value?net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));}
    private static void open(Minecraft mc,BlockPos pos){var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.96),Direction.NORTH,pos,false));require(result.consumesAction(),"real configuration interaction refused");}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!BinaryGuiServerChecks.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(mc.player==null||mc.level==null||mc.gameMode==null)return;
        require(++ticks<2600,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position());
        if(stage==0){if(ticks<80||mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())||mc.player.distanceToSqr(1000.5,80,997.5)>.01||!(mc.level.getBlockEntity(BinaryGuiServerChecks.POS) instanceof NativeBinaryControlBlockEntity))return;var actorRoom=new BlockPos(1000,80,997);require(mc.level.isEmptyBlock(actorRoom)&&mc.level.isEmptyBlock(actorRoom.above())&&mc.player.isAlive(),"observed actor room/health before interaction");System.out.println("[vandorlabs][binary-gui] actor room client PASS observed survival slot0 Configurizer position="+mc.player.position()+" air feet/head");widgetProbe(mc);originalShape=mc.level.getBlockState(BinaryGuiServerChecks.POS).getShape(mc.level,BinaryGuiServerChecks.POS).bounds();open(mc,BinaryGuiServerChecks.POS);next();return;}
        if(ticks<at+10)return;
        if(stage==1){var screen=ui(mc);require(screen.panelWidth()==280&&screen.panelHeight()==232,"current exact fixed panel");require(screen.mountButton(12)==null,"power-only Size row leaked into binary dialog");option(screen,9,1);option(screen,10,0);option(screen,11,1);require(screen.mountDraft().height==3&&screen.mountDraft().tilt==1&&screen.mountDraft().direction==3&&mount(tile(mc),0,0,0),"mount options draft and previous cycling");screen.keyPressed(256,0,0);next();}
        else if(stage==2){if(mc.screen!=null)return;require(mount(tile(mc),0,0,0),"Escape sent mountdraft");open(mc,BinaryGuiServerChecks.POS);next();}
        else if(stage==3){var screen=ui(mc);option(screen,9,0);option(screen,10,0);option(screen,11,0);click(screen,screen.panelLeft()+120,screen.panelTop()+212,0);next();}
        else if(stage==4){if(mc.screen!=null||!mount(tile(mc),1,1,1))return;var pos=BinaryGuiServerChecks.POS;var state=mc.level.getBlockState(pos);var shape=state.getShape(mc.level,pos);require(!shape.bounds().equals(originalShape)&&state.getCollisionShape(mc.level,pos).bounds().equals(shape.bounds()),"actual world mount-dependent pick/collision shape remained cached");AABB bounds=shape.bounds();Vec3 start=new Vec3(pos.getX()+(bounds.minX+bounds.maxX)/2,pos.getY()+(bounds.minY+bounds.maxY)/2,pos.getZ()+bounds.minZ-1),end=new Vec3(start.x,start.y,pos.getZ()+bounds.maxZ+1);require(shape.clip(start,end,pos)!=null,"actual mounted selection ray missed");open(mc,BinaryGuiServerChecks.POS);next();}
        else if(stage==5){var screen=ui(mc);tab(screen,1);text(screen,"82, 83");next();}
        else if(stage==6){require(channels(tile(mc),81),"typing channel draft published early");ui(mc).keyPressed(257,0,0);require(mc.screen instanceof NativeBinaryControlScreen,"focused Enter closed screen");next();}
        else if(stage==7){if(!channels(tile(mc),82,83))return;require(mount(tile(mc),1,1,1),"inline Enter changed mount");rangeTarget=0;range(ui(mc),rangeTarget,0);next();}
        else if(stage==8){if(tile(mc).transmissionRange()!=TransmissionSettings.RANGES[rangeTarget])return;if(++rangeTarget<7){range(ui(mc),rangeTarget,0);at=ticks;}else {range(ui(mc),0,1);next();}}
        else if(stage==9){require(tile(mc).transmissionRange()==256&&ui(mc).inlineWidget().rangeIndex()==6,"right click changed range");local(ui(mc),1);next();}
        else if(stage==10){if(tile(mc).localRedstone())return;require(!mc.level.getBlockState(BinaryGuiServerChecks.LAMP).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT),"LocalOff did not extinguish native lamp");var screen=ui(mc);text(screen,"84,,85");range(screen,4,0);local(screen,1);tab(screen,0);option(screen,9,0);tab(screen,1);scale(mc,3);next();}
        else if(stage==11){var screen=ui(mc);require(channels(tile(mc),82,83)&&tile(mc).transmissionRange()==256&&!tile(mc).localRedstone(),"invalid channel draft leaked range/local mutation");require(screen.inlineWidget().channelField().getValue().equals("84,,85")&&screen.inlineWidget().rangeIndex()==4&&screen.inlineWidget().localRedstone(),"resize/tab lost invalid pending drafts");text(screen,"84, 85");tab(screen,0);next();}
        else if(stage==12){if(!channels(tile(mc),84,85)||tile(mc).transmissionRange()!=64||!tile(mc).localRedstone())return;require(mount(tile(mc),1,1,1),"tab flush committed pending mount");option(ui(mc),9,0);ui(mc).keyPressed(256,0,0);next();}
        else if(stage==13){if(mc.screen!=null)return;require(mount(tile(mc),1,1,1),"General Escape committed draft after inline flush");open(mc,BinaryGuiServerChecks.POS);next();}
        else if(stage==14){var screen=ui(mc);tab(screen,1);text(screen,"86, 87");range(screen,5,0);next();}
        else if(stage==15){if(!channels(tile(mc),86,87)||tile(mc).transmissionRange()!=128)return;var screen=ui(mc);text(screen,"88, 89");scale(mc,2);require(screen.inlineWidget().channelField().getValue().equals("88, 89"),"resize discarded valid channel draft");next();}
        else if(stage==16){if(!channels(tile(mc),88,89))return;text(ui(mc),"90, 91");ui(mc).keyPressed(256,0,0);next();}
        else if(stage==17){if(mc.screen!=null||!channels(tile(mc),90,91))return;require(mount(tile(mc),1,1,1),"field Escape committed mount");open(mc,BinaryGuiServerChecks.POS);next();}
        else if(stage==18){var screen=ui(mc);tab(screen,1);text(screen,"92,,93");range(screen,3,0);local(screen,1);screen.keyPressed(256,0,0);next();}
        else if(stage==19){if(mc.screen!=null)return;require(channels(tile(mc),90,91)&&tile(mc).transmissionRange()==128&&tile(mc).localRedstone(),"invalid Escape committed pending inline settings");mc.player.getInventory().selected=1;mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(1));shift(mc,true);next();}
        else if(stage==20){if(!mc.player.getMainHandItem().isEmpty())return;open(mc,BinaryGuiServerChecks.POS);next();}
        else if(stage==21){require(ui(mc).page()==0,"ordinary survival shift used another dialog");ui(mc).keyPressed(256,0,0);next();}
        else if(stage==22){if(mc.screen!=null)return;shift(mc,true);open(mc,BinaryGuiServerChecks.PUSH);next();}
        else if(stage==23){scale(mc,3);var screen=ui(mc);require(screen.height<=240,"scale3 near-minimum dialog height");option(screen,9,1);shift(mc,false);require(screen.mountDraft().height==3,"push button previous mount option");capture=true;next();}
        else if(stage==24){if(!captured)return;var screen=ui(mc);click(screen,screen.panelLeft()+120,screen.panelTop()+212,0);next();}
        else if(stage==25){if(mc.screen!=null||!(mc.level.getBlockEntity(BinaryGuiServerChecks.PUSH) instanceof NativeBinaryControlBlockEntity push)||push.mountSettings().height!=3)return;shift(mc,false);stage=26;at=ticks;}
        else if(stage==26){if(!restoredScale){mc.options.guiScale().set(1);mc.resizeDisplay();restoredScale=true;}if(BinaryControlSceneCapture.capture(mc)){System.out.println("[vandorlabs][binary-gui] client PASS actual General Done/Escape Enter inline sevenranges local draft invalid resize tabs Configurizer survivalshift bothfamilies world-hotbar-capture");mc.stop();stage=27;}}
    }
    private static void widgetProbe(Minecraft mc){
        List<String> sent=new ArrayList<>();var widget=new NativeInlineTransmissionWidget(mc.font,10,10,256,ChannelList.of(1),16,true,(channels,range,local)->sent.add(channels+"/"+range+"/"+local));
        widget.channelField().setValue("2,,3");widget.localControl().onPress();widget.rangeControl().mouseClicked(10+4+248*4/6.0,20,0);require(sent.isEmpty(),"generic invalid draft sender suppression");widget.channelField().setValue("2,3");widget.flush();require(sent.size()==1&&sent.getFirst().equals("2, 3/64/false"),"generic combined draft publishes once");widget.flush();require(sent.size()==1,"generic unchanged flush duplicate");
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(capture){capture=false;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"binary-dialog-native.png",mc.getMainRenderTarget(),message->captured=true);}}
}
