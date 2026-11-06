package com.vandorlabs.neoforge.trapdoor;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.client.NativeTrapdoorScreen;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Actual native widgets and atlas, with test-only registered hosts and no running world. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class TrapdoorScreenChecks {
    private static final Housing[] blocks=new Housing[2];private static final BlockEntityType<?>[] types=new BlockEntityType<?>[2];
    private static final List<NativeTrapdoorEdit> edits=new ArrayList<>();private static NativeTrapdoorScreen screen;
    private static int ticks,stage,checks,frames;private static String capture;private static boolean captured;private static java.util.concurrent.CompletableFuture<Void> reload;
    private static boolean enabled(){return "true".equals(System.getenv("VANDOR_TRAPDOOR_SCREEN_CHECK"));}
    private static final class Housing extends NativeTrapdoorBlock {
        final int family;Housing(int family){super(BlockBehaviour.Properties.of().noOcclusion().dynamicShape(),family==1);this.family=family;}
        protected BlockEntityType<?> entityType(){return types[family];}protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
    }
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!enabled())return;
            event.register(Registries.BLOCK,h->{for(int i=0;i<2;i++){blocks[i]=new Housing(i);h.register(ResourceLocation.fromNamespaceAndPath("vandorlabs","trapdoor_screen_fixture_"+i),blocks[i]);}});
            event.register(Registries.BLOCK_ENTITY_TYPE,h->{for(int i=0;i<2;i++){final int family=i;types[i]=BlockEntityType.Builder.of((p,s)->new NativeTrapdoorEntity(types[family],p,s),blocks[i]).build(null);h.register(ResourceLocation.fromNamespaceAndPath("vandorlabs","trapdoor_screen_fixture_"+i),types[i]);}});
        }
    }
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Trapdoor screen stage="+stage+": "+why);}
    private static void click(int id,int mouse){var b=screen.button(id);need(b!=null&&b.active,"enabled button "+id);need(screen.mouseClicked(b.getX()+b.getWidth()/2D,b.getY()+b.getHeight()/2D,mouse),"actual button click "+id);}
    private static void open(Minecraft mc,int family){var tile=new NativeTrapdoorEntity(types[family],BlockPos.ZERO,blocks[family].defaultBlockState());screen=new NativeTrapdoorScreen(new NativeTrapdoorMenu(null,37,tile),new Inventory(null),Component.literal(family==0?"Programmable Trapdoor":"Programmable Diagonal Trapdoor"),edits::add);screen.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());}
    private static void widgets(Minecraft mc){
        open(mc,0);click(1,1);need(screen.settings().choices().cover&&screen.settings().choices().sliding,"regular reverse wraps to Slide into next block");click(1,0);need(!screen.settings().choices().cover&&!screen.settings().choices().sliding,"regular forward wraps to Rotating");
        click(2,1);need(screen.settings().choices().position==2,"regular reverse position wraps Top");int facing=screen.settings().facing();click(8,1);need(screen.settings().facing()==Math.floorMod(facing-1,4),"regular reverse hinge facing");
        click(101,0);screen.channelField().setValue("31,");need(!screen.button(4).active,"invalid channels disable Done");int sent=edits.size();click(3,1);need(edits.size()==sent+1&&edits.get(edits.size()-1).settings().channels().equals(ChannelList.EMPTY),"source invalid draft retains last valid bank while other options still apply");
        screen.channelField().setValue("31, 32");need(screen.button(4).active&&edits.get(edits.size()-1).settings().channels().equals(ChannelList.of(31,32)),"valid channel edit applies bank");int[] largeIds=java.util.stream.IntStream.range(0,64).map(i->1000000000+i).toArray();var largeBank=ChannelList.of(largeIds);screen.channelField().setValue(largeBank.toString());need(screen.channelField().getValue().equals(largeBank.toString())&&screen.edit().settings().channels().equals(largeBank)&&screen.button(4).active,"all64 large channel IDs remain intact");screen.channelField().setValue("31, 32");click(100,0);
        var picker=screen.activePicker();picker.searchField().setValue("vand");picker.searchField().setFocused(true);need(screen.keyPressed(69,0,0),"focused quicksearch handles inventory shortcut");picker.searchField().setValue("a");int before=picker.scroll();need(screen.mouseScrolled(screen.panel().listX+5,picker.top()+5,0,-1)&&picker.scroll()>before,"wheel scroll");
        int bottom=picker.top()+picker.visible()*22-1;need(screen.mouseClicked(screen.panel().listX+screen.panel().listWidth+2,bottom,0),"scrollbar press");int end=picker.scroll();need(screen.mouseDragged(screen.panel().listX+screen.panel().listWidth+2,picker.top(),0,0,-100)&&picker.scroll()<end,"scrollbar drag");screen.mouseReleased(0,0,0);
        click(101,0);click(100,0);need(screen.activePicker().searchField().getValue().equals("a"),"page changes retain query");
    }
    private static void diagonal(Minecraft mc){open(mc,1);click(2,0);need(screen.settings().choices().position==1,"full diagonal width");click(7,0);need(screen.settings().choices().position==2&&!screen.button(2).active,"half-height disables width");click(7,1);need(screen.settings().choices().position==1&&screen.button(2).active,"height restores prior full width");click(10,1);need(screen.settings().hingeSide()==2,"reverse hinge wraps Right");click(9,1);need(screen.settings().rotationFlipped(),"reverse rotation toggles");click(5,0);need(screen.edit().inverted(),"slope inversion retained in transaction");click(1,1);need(screen.settings().choices().intoWall&&!screen.button(9).active&&!screen.button(10).active,"Slide into wall disables rotating options");click(1,0);need(!screen.settings().choices().sliding&&screen.button(9).active,"return to Rotating");}
    private static void capture(String name){capture=name;captured=false;frames=0;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!enabled()||stage==9)return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}need(++ticks<2200,"title timeout");if(ticks<80||mc.getOverlay()!=null||!(mc.screen instanceof TitleScreen))return;
        need(mc.level==null&&mc.getSingleplayerServer()==null,"title-only widget boundary");
        if(stage==0){mc.getWindow().setWindowed(1280,720);mc.options.guiScale().set(2);mc.resizeDisplay();stage=1;}
        else if(stage==1){if(mc.getWindow().getGuiScaledWidth()<600)return;widgets(mc);capture("trapdoor-general.png");stage=2;}
        else if(stage==2&&captured){click(101,0);capture("trapdoor-redstone.png");stage=3;}
        else if(stage==3&&captured){diagonal(mc);capture("trapdoor-diagonal.png");stage=4;}
        else if(stage==4&&captured){screen.activePicker().searchField().setValue("panel");screen.init(mc,400,260);need(screen.activePicker().searchField().getValue().equals("panel"),"resize retains query");for(int id:new int[]{1,2,4,5,6,7,9,10}){var b=screen.button(id);need(b!=null&&b.getY()>=screen.panel().top&&b.getY()+b.getHeight()<=screen.panel().top+screen.panel().height,"small-screen bounds "+id);}reload=mc.reloadResourcePacks();stage=5;}
        else if(stage==5&&reload.isDone()){reload.join();need(screen.activePicker().searchField().getValue().equals("panel"),"reload retains query");screen.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());capture("trapdoor-reloaded.png");stage=6;}
        else if(stage==6&&captured){need(edits.stream().allMatch(e->e.window()==37),"all transactions retain menu window");System.out.println("[vandorlabs][trapdoor-screen] title PASS checks="+checks+" edits="+edits.size()+" reverse options height/width invalid drafts picker wheel drag resize reload no world");stage=9;mc.stop();}
    }
    @SubscribeEvent public static void draw(ScreenEvent.Render.Post event){if(!enabled()||screen==null||!(event.getScreen() instanceof TitleScreen)||Minecraft.getInstance().getOverlay()!=null)return;var g=event.getGuiGraphics();g.flush();g.pose().pushPose();try{g.pose().translate(0,0,300);g.fill(0,0,Minecraft.getInstance().getWindow().getGuiScaledWidth(),Minecraft.getInstance().getWindow().getGuiScaledHeight(),0xFF101012);screen.render(g,0,0,0);g.flush();}finally{g.pose().popPose();}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(!enabled()||capture==null||++frames<6)return;String name=capture;capture=null;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),ignored->{captured=true;});}
}
