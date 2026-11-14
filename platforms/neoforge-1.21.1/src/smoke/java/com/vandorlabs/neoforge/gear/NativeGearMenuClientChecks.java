package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.gear.client.NativeGearScreen;
import com.vandorlabs.neoforge.client.NativeIntegerSlider;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Small real C2S editor journey, with native construction-stage screenshots. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeGearMenuClientChecks {
    private static int stage,ticks,checks,window;private static String shot;private static java.util.UUID selectedIdentity;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Native gear menu client stage="+stage+": "+why);}
    private static void mode(int operation){PacketDistributor.sendToServer(new NativeGearMenuClientFixture.Mode(operation));}
    private static void use(Minecraft mc,BlockPos pos,Direction side){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(side.getNormal()).scale(.5)),side,pos,false));}
    private static NativeGearScreen screen(Minecraft mc){return mc.screen instanceof NativeGearScreen s?s:null;}
    private static void click(NativeGearScreen screen,int id,int button){var control=screen.button(id);need(control!=null&&control.active&&screen.mouseClicked(control.getX()+control.getWidth()/2D,control.getY()+8,button),"actual option "+id);}
    private static void slider(NativeGearScreen screen,String label,int low,int high,int value){var control=screen.children().stream().filter(child->child instanceof NativeIntegerSlider slider&&slider.getMessage().getString().startsWith(label+":")).map(child->(NativeIntegerSlider)child).findFirst().orElseThrow();double x=control.getX()+4+(control.getWidth()-8)*(value-low)/(double)(high-low),y=control.getY()+8;need(control.active&&screen.mouseClicked(x,y,0),"actual slider "+label);screen.mouseReleased(x,y,0);need(control.getIntValue()==value,"slider selected "+value);}
    private static boolean ack(Minecraft mc,int id){return mc.level.getBlockState(NativeGearMenuClientFixture.ACK.east(id)).is(Blocks.STONE);}
    private static boolean sections(NativeGearEntity tile){return tile!=null&&tile.controller()&&tile.assembly().columnHeight()==2;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!NativeGearMenuClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}if(stage==23&&mc.level==null){System.out.println("[vandorlabs][gear-menu-client] client PASS checks="+checks+" realSurvivalConstruction/Configurator/JoinPrevious/Range/Carry/Height/sameWindowRebinding/replacedMember/staleWindow/nativeStageImages");stage=24;mc.stop();return;}if(mc.level==null||mc.player==null||mc.gameMode==null)return;
        need(++ticks<1600,"timeout tick="+ticks+" mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position()+" screen="+mc.screen);
        mc.getToasts().clear();mc.gui.getChat().clearMessages(true);var first=NativeGearFormation.at(mc.level,NativeGearMenuClientFixture.PRIMARY);var second=NativeGearFormation.at(mc.level,NativeGearMenuClientFixture.SELECTED);var held=mc.player.getMainHandItem();var menu=screen(mc);
        switch(stage){
            case 0->{if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!held.is(NativeGearBindings.ITEMS.get(NativeGearDefinition.EXTENDER.id))||held.getCount()!=2||mc.player.position().distanceToSqr(.5,96,-3)>.01)return;use(mc,NativeGearMenuClientFixture.PRIMARY.above(),Direction.DOWN);stage=1;}
            case 1->{if(first==null||held.getCount()!=1)return;use(mc,NativeGearMenuClientFixture.PRIMARY,Direction.DOWN);stage=2;}
            case 2->{if(!sections(first)||!held.isEmpty())return;mode(0);stage=3;}
            case 3->{if(!ack(mc,0)||held.getCount()!=2)return;use(mc,NativeGearMenuClientFixture.SELECTED.above(),Direction.DOWN);stage=4;}
            case 4->{if(second==null||held.getCount()!=1)return;use(mc,NativeGearMenuClientFixture.SELECTED,Direction.DOWN);stage=5;}
            case 5->{if(!sections(second)||!held.isEmpty())return;need(first.controller()&&second.controller(),"two independent installed columns before enabling join");mc.options.hideGui=true;shot="columns";stage=6;}
            case 6->{if(shot!=null)return;mode(1);stage=7;}
            case 7->{if(!ack(mc,1)||!held.is(Blocks.DIAMOND_BLOCK.asItem())||held.getCount()!=4)return;use(mc,NativeGearMenuClientFixture.PRIMARY.below(),Direction.DOWN);stage=8;}
            case 8->{if(held.getCount()!=3||!mc.level.getBlockState(new BlockPos(-3,97,0)).is(Blocks.DIAMOND_BLOCK))return;use(mc,new BlockPos(-3,97,0),Direction.EAST);stage=9;}
            case 9->{if(held.getCount()!=2||!mc.level.getBlockState(new BlockPos(-2,97,0)).is(Blocks.DIAMOND_BLOCK))return;use(mc,new BlockPos(-2,97,0),Direction.EAST);stage=10;}
            case 10->{if(held.getCount()!=1||!mc.level.getBlockState(new BlockPos(-1,97,0)).is(Blocks.DIAMOND_BLOCK))return;use(mc,NativeGearMenuClientFixture.SELECTED.below(),Direction.DOWN);stage=11;}
            case 11->{if(!held.isEmpty()||!mc.level.getBlockState(new BlockPos(0,97,0)).is(Blocks.DIAMOND_BLOCK))return;mc.options.hideGui=true;shot="platform";stage=12;}
            case 12->{if(shot!=null)return;mode(2);stage=13;}
            case 13->{if(!ack(mc,2)||!held.is(VandorLabsBootstrap.CONFIGURIZER.get()))return;use(mc,NativeGearMenuClientFixture.SELECTED,Direction.NORTH);stage=14;}
            case 14->{if(menu==null)return;need(menu.getMenu().selectedPosition().equals(NativeGearMenuClientFixture.SELECTED),"clicked member retained");need(menu.button(11)!=null&&!menu.button(11).active,"load disabled until joined");need(menu.button(17)==null,"bare arm omits foot direction");window=menu.getMenu().containerId;selectedIdentity=menu.getMenu().selectedIdentity();click(menu,9,1);need(menu.editingSettings().join(),"right click previous binary join option");stage=15;}
            case 15->{if(menu==null||menu.getMenu().tile.getBlockPos().equals(NativeGearMenuClientFixture.SELECTED)||second.owner()==null||!second.owner().equals(NativeGearMenuClientFixture.PRIMARY))return;need(menu.getMenu().containerId==window&&menu.getMenu().selectedIdentity().equals(selectedIdentity),"same window and immutable member through controller handoff");slider(menu,"Range",1,32,8);stage=16;}
            case 16->{if(first.settings().joinRange()!=8||menu==null||menu.waitingForReply())return;need(menu!=null&&menu.error().isEmpty(),"valid rebound range acknowledged");click(menu,11,0);stage=17;}
            case 17->{if(!first.settings().carryLoad()||menu==null||menu.waitingForReply())return;slider(menu,"Height",1,32,2);stage=18;}
            case 18->{if(first.settings().loadHeight()!=2||menu==null||menu.waitingForReply())return;need(menu!=null&&menu.getMenu().containerId==window&&menu.error().isEmpty(),"all following choices use same window");mode(3);stage=19;}
            case 19->{if(!ack(mc,3))return;shot="options";stage=20;}
            case 20->{if(shot!=null)return;mode(4);stage=21;}
            case 21->{if(!ack(mc,4)||menu!=null)return;PacketDistributor.sendToServer(new NativeGearSettingsPayload(window,NativeGearMenuClientFixture.SELECTED,NativeGearSettings.defaults(),true,999));mode(5);stage=22;}
            case 22->{if(!ack(mc,5))return;mc.level.disconnect();mc.disconnect();stage=23;}
            default->{}
        }
    }
    @SubscribeEvent public static void capture(RenderFrameEvent.Post event)throws Exception{if(!NativeGearMenuClientFixture.enabled()||shot==null)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.getOverlay()!=null)return;try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){var path=mc.gameDirectory.toPath().resolve("screenshots/native-joined-"+shot+".png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);}mc.options.hideGui=false;shot=null;}
}
