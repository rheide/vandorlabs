package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.neoforge.client.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Actual title widgets; authorizing placed sessions are tested separately. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class CanopyScreenChecks {
 private static final String[] KINDS={"canopy","long_canopy","front_canopy_glass","large_front_canopy_glass","regular_canopy_glass","angled_front_1x1","angled_front_1x2","space_visor_2x1","space_visor_3x1","space_visor_5x1","space_visor_5x1_double_height"};
 private static MenuType<NativeCanopyMenu> type;private static NativeCanopyScreen screen;private static int ticks,stage,checks,index,frames;private static boolean captured;private static String capture;
 private static final List<CanopySettingsPayload> edits=new ArrayList<>();private static java.util.concurrent.CompletableFuture<Void> reload;
 private static boolean enabled(){return Boolean.getBoolean("vandorlabs.canopyScreenCheck");}
 @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)public static final class Registration {
  @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(enabled())event.register(Registries.MENU,h->{type=new MenuType<>((id,inventory)->{throw new UnsupportedOperationException("Title only");},net.minecraft.world.flag.FeatureFlags.VANILLA_SET);h.register(ResourceLocation.parse("vandorlabs:canopy_screen_fixture"),type);});}
 }
 private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Canopy widgets stage="+stage+" kind="+index+": "+why);}
 private static void click(int id,int button){var b=screen.button(id);need(b!=null&&b.active,"available button "+id);need(screen.mouseClicked(b.getX()+b.getWidth()/2D,b.getY()+10,button),"actual click "+id);}
 private static void create(Minecraft mc){var block=CanopyHostChecks.block(KINDS[index]);var owner=(NativeCanopyEntity)block.newBlockEntity(BlockPos.ZERO,block.defaultBlockState());edits.clear();screen=new NativeCanopyScreen(new NativeCanopyMenu(type,37,owner),new Inventory(null),Component.literal(KINDS[index]),edits::add);screen.init(mc,640,360);need(screen.page()==0,"Source Canopy first page");click(2,1);need(screen.editingSettings().data().tint==0,"reverse tint previous");click(2,0);need(screen.editingSettings().data().tint==1,"forward tint restore");click(12,1);need(screen.editingSettings().data().textureLayout()==2,"reverse texture wraps Fit aspect");click(12,0);
  boolean opening=index<2,resizable=index==2||index==3;
  if(opening){click(3,1);need(screen.editingSettings().data().motion==1&&screen.button(11)==null,"rear hinge removes slide control");click(3,0);click(4,1);need(screen.editingSettings().data().angle==0,"reverse front");}
  if(resizable){click(6,1);need(screen.editingSettings().data().hinge==1,"reverse hinge");var slider=screen.children().stream().filter(v->v instanceof NativeIntegerSlider).map(v->(NativeIntegerSlider)v).findFirst().orElseThrow();edits.clear();need(screen.mouseClicked(slider.getX()+slider.getWidth()-1,slider.getY()+10,0),"slider press");screen.mouseDragged(slider.getX()+slider.getWidth()-1,slider.getY()+10,0,100,0);screen.advanceEditor();need(edits.size()==1&&screen.editingSettings().data().depth==80,"drag debounce exact maximum");screen.mouseReleased(0,0,0);int count=edits.size();screen.advanceEditor();need(edits.size()==count,"release unchanged no duplicate");}
  screen.choose(-1003);need(screen.editingSettings().data().shade==3&&screen.editingSettings().data().material==-1,"glass shade sentinel stays UI only");
  if(opening||resizable){click(91,0);screen.channelField().setValue("31,");int count=edits.size();click(7,1);need(edits.size()==count&&screen.channelField().getValue().equals("31,"),"invalid channel draft refuses edit");screen.channelField().setValue("31, 32");need(edits.size()==count+1&&edits.getLast().edit().settings().data().channels.size()==2,"valid normalized bank publishes once");click(90,0);}
  screen.activePicker().searchField().setValue("a");var picker=screen.activePicker();need(picker.rows().size()>picker.visible(),"scrollable material query");need(screen.mouseScrolled(screen.panel().listX+12,picker.top()+10,0,-1)&&picker.scroll()>0,"actual material wheel");need(screen.mouseClicked(screen.panel().listX+screen.panel().listWidth+3,picker.top()+picker.visible()*22-1,0),"actual scrollbar press");need(screen.mouseDragged(screen.panel().listX+screen.panel().listWidth+3,picker.top()+1,0,0,-100),"actual scrollbar drag");screen.mouseReleased(0,0,0);screen.activePicker().searchField().setValue("metal");need(!screen.activePicker().rows().isEmpty(),"material query");screen.activePicker().searchField().setValue("");var last=edits.getLast().edit();var authoritative=NativeCanopySettings.fresh();screen.acknowledge(new CanopySettingsPayload.Result(new NativeCanopyEdit(last.pos(),last.window(),Math.max(0,last.request()-1),authoritative),false));need(screen.error().isEmpty(),"stale acknowledgement ignored");screen.acknowledge(new CanopySettingsPayload.Result(new NativeCanopyEdit(last.pos(),last.window(),last.request(),authoritative),false));need(!screen.error().isEmpty()&&screen.editingSettings().data().shade==0,"current rejected edit restored authoritative values");screen.init(mc,320,240);need(screen.panel().width==312,"compact shared dialog");screen.init(mc,640,360);capture="canopy-editor-"+index+".png";captured=false;frames=0;
 }
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!enabled()||stage==9)return;var mc=Minecraft.getInstance();try{if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}need(++ticks<2400,"title timeout");if(ticks<80||mc.getOverlay()!=null||!(mc.screen instanceof TitleScreen))return;need(mc.level==null&&mc.getSingleplayerServer()==null,"no world claim");if(stage==0){mc.getWindow().setWindowed(1280,720);mc.options.guiScale().set(2);mc.resizeDisplay();create(mc);stage=1;}else if(stage==1&&captured){if(++index<KINDS.length)create(mc);else{reload=mc.reloadResourcePacks();stage=2;}}else if(stage==2&&reload.isDone()){reload.join();index=2;create(mc);stage=3;}else if(stage==3&&captured){screen.init(mc,320,240);capture="canopy-editor-compact.png";captured=false;frames=0;stage=4;}else if(stage==4&&captured){System.out.println("[vandorlabs][canopy-screen] title PASS checks="+checks+" roles=11 reverseCycles sliderDebounce invalidDraft rejectedAck picker resize reload");stage=9;mc.stop();}}catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][canopy-screen] FAIL "+failure);stage=9;mc.stop();}}
 @SubscribeEvent public static void draw(ScreenEvent.Render.Post event){if(!enabled()||screen==null||!(event.getScreen() instanceof TitleScreen)||Minecraft.getInstance().getOverlay()!=null)return;var g=event.getGuiGraphics();g.flush();g.pose().pushPose();try{g.pose().translate(0,0,300);g.fill(0,0,640,360,0xFF101012);screen.render(g,0,0,0);g.flush();frames++;}finally{g.pose().popPose();}}
 @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(!enabled()||capture==null||frames<6)return;String name=capture;capture=null;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),ignored->captured=true);}
}
