package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.client.*;
import com.vandorlabs.neoforge.credentials.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Opt-in actual widgets, both canonical reader roles; no public activation or simulated player claim. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class ReaderScreenChecks {
    private static NativeCredentialReaderScreen screen;
    private static final List<ReaderSettingsPayload> edits=new ArrayList<>();
    private static final List<ReaderTransmissionPayload> transmissions=new ArrayList<>();
    private static int ticks,stage,checks,frames;
    private static boolean captured;
    private static String capture;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static boolean enabled(){return "true".equals(System.getenv("VANDOR_READER_SCREEN_CHECK"));}
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Reader widgets stage="+stage+": "+why);}
    private static void click(int id,int button){var control=screen.button(id);need(control!=null&&control.active,"active control "+id);need(screen.mouseClicked(control.getX()+control.getWidth()/2D,control.getY()+control.getHeight()/2D,button),"actual option click "+id);}
    private static void capture(String name){capture=name;captured=false;frames=0;}
    private static void widgets(Minecraft mc,boolean chip){
        var block=StandaloneReaderGeometryChecks.fixture(chip);var owner=(NativeCredentialReaderEntity)block.newBlockEntity(BlockPos.ZERO,block.defaultBlockState());var menu=new NativeCredentialReaderMenu(StandaloneReaderGeometryChecks.MENU,37,owner);
        screen=new NativeCredentialReaderScreen(menu,new Inventory(null),Component.literal(chip?"Data Chip Reader":"Access Card Reader"),new NativeCredentialFilterPicker(company->chip?NativeCredentialItems.dataChip(company):NativeCredentialItems.accessCard(company),chip),edits::add,transmissions::add);
        screen.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());need(screen.page()==1&&screen.acceptedCompany()==127,"Source initial Security page and Any credential");
        click(4,1);click(5,1);click(6,1);need(screen.mountDraft().height==3&&screen.mountDraft().tilt==3&&screen.mountDraft().direction==3,"reverse mount cycles wrap exact source values");click(4,0);click(5,0);click(6,0);need(screen.mountDraft().height==0&&screen.mountDraft().tilt==0&&screen.mountDraft().direction==0,"forward mount cycles restore defaults");
        for(int company=1;company<=7;company++){need(screen.mouseClicked(screen.panelLeft()+12+145,screen.panelTop()+86,0),"actual company icon cycle");need(screen.acceptedCompany()==(company==7?127:1<<company),"Source company filter order "+company);}
        need(screen.mouseClicked(screen.panelLeft()+12+145,screen.panelTop()+86,1)&&screen.acceptedCompany()==64,"reverse credential filter wraps last company");need(screen.mouseClicked(screen.panelLeft()+20,screen.panelTop()+86,0)&&screen.acceptedCompany()==127,"wildcard label clears company");
        click(100,0);click(1,1);need(screen.behaviorDraft()==3,"reverse behavior wraps return pulse");click(1,0);need(screen.behaviorDraft()==0,"forward behavior restores Stay Inserted");
        screen.channelField().setValue("31,");int before=edits.size();click(1,0);need(edits.size()==before&&screen.channelField().getValue().equals("31,"),"invalid hidden legacy draft refuses source transaction");click(101,0);click(4,1);need(edits.size()==before&&screen.channelField().getValue().equals("31,"),"invalid draft survives tab and mount preview");screen.channelField().setValue("31, 32");click(4,0);need(edits.size()==before+1,"next valid option emits exactly one edit");
        click(100,0);click(90,0);need(screen.page()==2,"dedicated Redstone Transmitter path");var widget=screen.inlineWidget();widget.channelField().setValue("41,");int sent=transmissions.size();var local=widget.localControl();need(screen.mouseClicked(local.getX()+10,local.getY()+10,1),"actual reverse local toggle");need(transmissions.size()==sent&&widget.channelField().getValue().equals("41,"),"invalid transmitter draft refuses apply");widget.channelField().setValue("41, 43");need(screen.mouseClicked(local.getX()+10,local.getY()+10,0)&&transmissions.size()==sent+1,"valid transmitter edit emits once");need(transmissions.get(transmissions.size()-1).channels().equals(com.vandorlabs.redstone.ChannelList.of(41,43)),"source normalized main bank");click(90,0);need(screen.page()==0,"transmitter returns to dedicated reader editor");click(101,0);
        need(edits.stream().allMatch(edit->edit.window()==37&&edit.mount()!=null),"every option keeps menu identity and full mount extension");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled()||stage==9)return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}need(++ticks<2200,"title timeout");if(ticks<80||mc.getOverlay()!=null||!(mc.screen instanceof TitleScreen))return;need(mc.level==null&&mc.getSingleplayerServer()==null,"title widget scope has no player/world");
        if(stage==0){mc.getWindow().setWindowed(1280,720);mc.options.guiScale().set(2);mc.resizeDisplay();stage=1;}
        else if(stage==1){widgets(mc,false);capture("access-card-reader-security.png");stage=2;}
        else if(stage==2&&captured){click(100,0);capture("access-card-reader-redstone.png");stage=3;}
        else if(stage==3&&captured){widgets(mc,true);capture("data-chip-reader-security.png");stage=4;}
        else if(stage==4&&captured){screen.init(mc,300,260);need(screen.channelField().getValue().equals("31, 32"),"resize retains legacy draft");for(int id:new int[]{3,4,5,6,100,101}){var b=screen.button(id);need(b!=null&&b.getX()>=screen.panelLeft()&&b.getX()+b.getWidth()<=screen.panelLeft()+288&&b.getY()>=screen.panelTop()&&b.getY()+b.getHeight()<=screen.panelTop()+224,"small-screen control bounds "+id);}reload=mc.reloadResourcePacks();stage=5;}
        else if(stage==5&&reload.isDone()){reload.join();screen.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());need(screen.acceptedCompany()==127&&screen.channelField().getValue().equals("31, 32"),"resource reload retains reader drafts");capture("data-chip-reader-reloaded.png");stage=6;}
        else if(stage==6&&captured){System.out.println("[vandorlabs][reader-screen] title PASS checks="+checks+" edits="+edits.size()+" transmissions="+transmissions.size()+" both roles reverse cycles invalid drafts resize reload; actual player routes separate");stage=9;mc.stop();}
    }
    @SubscribeEvent public static void draw(ScreenEvent.Render.Post event){if(!enabled()||screen==null||!(event.getScreen() instanceof TitleScreen)||Minecraft.getInstance().getOverlay()!=null)return;var graphics=event.getGuiGraphics();graphics.flush();graphics.pose().pushPose();try{graphics.pose().translate(0,0,300);graphics.fill(0,0,Minecraft.getInstance().getWindow().getGuiScaledWidth(),Minecraft.getInstance().getWindow().getGuiScaledHeight(),0xFF101012);screen.render(graphics,0,0,0);graphics.flush();}finally{graphics.pose().popPose();}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(!enabled()||capture==null||++frames<6)return;String name=capture;capture=null;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),ignored->{captured=true;});}
}
