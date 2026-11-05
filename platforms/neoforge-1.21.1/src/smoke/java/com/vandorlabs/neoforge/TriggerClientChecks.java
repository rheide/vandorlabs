package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.client.NativeTriggerScreen;
import com.vandorlabs.neoforge.client.TransmitterScreen;
import com.vandorlabs.neoforge.redstone.NativeTransmitterItem;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.TriggerDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Actual production menus, input handlers and packets on a disposable native dedicated server. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class TriggerClientChecks {
    private static int ticks,stage,wait;private static boolean capture;private static String captureName="trigger-native-dialog.png";
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Trigger client stage="+stage+": "+message);}
    private static NativeTriggerBlockEntity tile(Minecraft mc){return (NativeTriggerBlockEntity)mc.level.getBlockEntity(TriggerClientFixture.POS);}
    private static void mode(int operation){PacketDistributor.sendToServer(new TriggerClientFixture.Mode(operation));wait=ticks;}
    private static void shift(Minecraft mc,boolean value){mc.player.setShiftKeyDown(value);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,value?net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));}
    private static void use(Minecraft mc,InteractionHand hand,boolean chest){var pos=chest?TriggerClientFixture.CHEST:TriggerClientFixture.POS;mc.gameMode.useItemOn(mc.player,hand,new BlockHitResult(Vec3.atCenterOf(pos).add(0,0,-.5),Direction.NORTH,pos,false));wait=ticks;}
    private static void click(NativeTriggerScreen screen,int id,int button){for(var c:TriggerDialog.controls(screen.panel(),screen.editingState(),"Done"))if(c.id==id){screen.mouseClicked(c.x+c.width/2.,c.y+c.height/2.,button);return;}throw new IllegalStateException("Missing Trigger control "+id);}
    private static void chooseRow(NativeTriggerScreen screen,int choice){
        var picker=screen.activePicker();picker.searchField().setValue(ModernFinishCatalog.option(choice).label());int row=picker.rows().indexOf(choice)-picker.scroll();require(row>=0&&row<picker.visible(),"searched choice not visible "+choice+" row="+row+" rows="+picker.rows()+" scroll="+picker.scroll()+" visible="+picker.visible()+" query="+picker.searchField().getValue()+" page="+screen.editingState().page+" tab="+screen.editingState().textureTab);
        screen.mouseClicked(screen.panel().listX+40,picker.top()+row*22+8,0);require(picker.selected()==choice,"actual material row selection "+choice);
    }
    private static void pickerChecks(Minecraft mc,NativeTriggerScreen screen){
        String[] drafts=new String[5];int[] scrolls=new int[5];
        for(int view=0;view<5;view++){
            click(screen,view==4?TriggerDialog.SIDES:TriggerDialog.FIRST_ARTWORK+view,0);
            chooseRow(screen,new int[]{9,10,11,14,13}[view]);var picker=screen.activePicker();drafts[view]=picker.searchField().getValue();
        }
        for(int view=0;view<5;view++){
            click(screen,view==4?TriggerDialog.SIDES:TriggerDialog.FIRST_ARTWORK+view,0);var picker=screen.activePicker();require(picker.searchField().getValue().equals(drafts[view]),"independent search draft "+view);
            picker.searchField().setValue("");screen.mouseScrolled(screen.panel().listX+20,picker.top()+8,0,-1);scrolls[view]=picker.scroll();
        }
        for(int view=0;view<5;view++){click(screen,view==4?TriggerDialog.SIDES:TriggerDialog.FIRST_ARTWORK+view,0);require(screen.activePicker().scroll()==scrolls[view],"independent list scroll "+view);}
        click(screen,TriggerDialog.FIRST_ARTWORK,0);int display=java.util.stream.IntStream.range(0,ModernFinishCatalog.ENTRIES.size()).filter(id->ModernFinishCatalog.option(id).visible()&&ModernFinishCatalog.option(id).displaySelectable()).findFirst().orElseThrow();chooseRow(screen,display);
        click(screen,TriggerDialog.SIDES,0);screen.activePicker().searchField().setValue(ModernFinishCatalog.option(display).label());require(!screen.activePicker().rows().contains(display),"Sides included display-only artwork");
        click(screen,TriggerDialog.FIRST_ARTWORK,0);var picker=screen.activePicker();picker.searchField().setValue("__inventory_only__");require(picker.rows().size()==1,"custom action row filtered out");screen.mouseClicked(screen.panel().listX+20,picker.top()+8,0);
        var inventory=picker.inventoryPicker();require(inventory!=null,"inline sample grid did not open");var layout=inventory.layout;int before=mc.player.getInventory().getItem(7).getCount(),selected=picker.selected();
        screen.mouseClicked(layout.x+layout.width-2,layout.y+15,0);require(picker.inventoryPicker()==inventory&&picker.selected()==selected,"blank header gap hit hidden material row");
        screen.mouseClicked(layout.cellX(7)+8,layout.cellY(7)+8,1);require(picker.inventoryPicker()==inventory&&picker.selected()==selected,"right inventory click selected artwork");
        screen.mouseClicked(layout.cellX(7)+8,layout.cellY(7)+8,0);require(picker.inventoryPicker()==null&&screen.editingState().texture(0)==com.vandorlabs.tiles.CustomMaterialIdentity.flattenedBlockIdentifier("minecraft:stone"),"actual inline stone sample");require(mc.player.getInventory().getItem(7).getCount()==before,"sample consumed inventory");
        click(screen,TriggerDialog.REDSTONE,0);require(screen.channelField().getValue().equals("31, 32"),"channel draft lost across five views");click(screen,TriggerDialog.FIRST_ARTWORK,0);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!TriggerClientFixture.enabled())return;var mc=Minecraft.getInstance();
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}
        if(stage==99&&mc.level==null){System.out.println("[vandorlabs][trigger] client PASS actual dialog/options/payload roundtrip and main/offhand transmitter chest priority");mc.stop();stage=100;return;}
        if(mc.player==null||mc.level==null||mc.gameMode==null)return;
        require(++ticks<3000,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" screen="+mc.screen);
        if(stage==0){if(ticks<80||!(mc.level.getBlockEntity(TriggerClientFixture.POS) instanceof NativeTriggerBlockEntity))return;
            if(mc.gameMode.getPlayerMode()!=GameType.CREATIVE||mc.player.getInventory().selected!=0||!mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()))return;
            use(mc,InteractionHand.MAIN_HAND,false);stage=1;
        }else if(stage==1){if(!(mc.screen instanceof NativeTriggerScreen s))return;
            require(java.util.Arrays.equals(s.editingState().modes(),new int[]{0,2,1,2})&&java.util.Arrays.equals(s.editingState().speeds(),new int[]{0,2,1,0}),"initial dormant playback arrays");
            require(s.editingState().texture(0)==8&&s.editingState().texture(3)==9,"initial off/on drafts");
            click(s,TriggerDialog.REDSTONE,0);click(s,TriggerDialog.BANDS,0);require(s.editingState().states,"band switch");
            click(s,TriggerDialog.EXACT,1);require(s.editingState().exact==15,"right click wraps to previous exact level");click(s,TriggerDialog.EXACT,0);require(s.editingState().exact==-1,"left click wraps to any");
            click(s,TriggerDialog.FRONT_ONLY,0);require(s.editingState().frontOnly,"front only");
            s.channelField().setValue("31,");click(s,TriggerDialog.EXACT,0);require(s.editingState().exact==0,"invalid draft retained option locally");stage=2;wait=ticks;
        }else if(stage==2&&ticks>wait+20){require(tile(mc).triggerState().exact==-1,"invalid channel draft sent partial edit");var s=(NativeTriggerScreen)mc.screen;s.channelField().setValue("31, 32");click(s,TriggerDialog.EXACT,1);
            for(int slot=0;slot<4;slot++){click(s,TriggerDialog.FIRST_ARTWORK+slot,0);require(s.editingState().textureTab==slot,"artwork tab");}
            click(s,TriggerDialog.SIDES,0);require(s.editingState().page==TriggerDialog.SIDES_PAGE,"side picker tab");
            click(s,TriggerDialog.FIRST_ARTWORK,0);int before=s.activePicker().scroll();s.mouseScrolled(s.panel().listX+20,TriggerDialog.listY(s.panel())+50,0,-1);require(s.activePicker().scroll()>before,"picker wheel");
            int top=TriggerDialog.listY(s.panel())+22,height=(TriggerDialog.listRows(s.panel())-1)*22;double x=s.panel().listX+s.panel().listWidth+3;
            s.mouseClicked(x,top+height-2,0);int bottom=s.activePicker().scroll();s.mouseDragged(x,top+2,0,0,-height);s.mouseReleased(x,top+2,0);require(s.activePicker().scroll()<bottom,"picker scrollbar drag");
            pickerChecks(mc,s);s.activePicker().searchField().setValue("");s.activePicker().select(s.editingState().texture(0),true);mc.getToasts().clear();mc.gui.getChat().clearMessages(true);capture=true;stage=3;wait=ticks;
        }else if(stage==3&&ticks>wait+25&&!capture){click((NativeTriggerScreen)mc.screen,TriggerDialog.REDSTONE,0);captureName="trigger-native-redstone.png";capture=true;stage=30;wait=ticks;
        }else if(stage==30&&ticks>wait+15&&!capture){require(tile(mc).triggerState().bands&&tile(mc).triggerState().frontOnly&&tile(mc).triggerState().exact==-1&&tile(mc).getRedstoneChannels().equals(ChannelList.of(31,32)),"authoritative settings roundtrip");mode(23);mc.player.closeContainer();mode(1);stage=4;
        }else if(stage==4&&ticks>wait+15){if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||!mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()))return;use(mc,InteractionHand.MAIN_HAND,false);stage=5;
        }else if(stage==5&&ticks>wait+15){require(mc.screen instanceof NativeTriggerScreen,"survival Configurizer dedicated screen");var reopened=(NativeTriggerScreen)mc.screen;require(java.util.Arrays.equals(reopened.editingState().modes(),new int[]{0,2,1,2})&&java.util.Arrays.equals(reopened.editingState().speeds(),new int[]{0,2,1,0}),"reopen dormant playback arrays");require(reopened.editingState().texture(0)==com.vandorlabs.tiles.CustomMaterialIdentity.flattenedBlockIdentifier("minecraft:stone")&&reopened.editingState().texture(1)==10&&reopened.editingState().texture(2)==11&&reopened.editingState().texture(3)==14&&reopened.editingState().sidesTexture==13,"five picker selections did not survive reopen");mc.player.closeContainer();mode(2);stage=6;
        }else if(stage==6&&ticks>wait+15){if(mc.gameMode.getPlayerMode()!=GameType.CREATIVE||!mc.player.getMainHandItem().isEmpty())return;shift(mc,true);use(mc,InteractionHand.MAIN_HAND,false);stage=7;
        }else if(stage==7&&ticks>wait+15){require(mc.screen instanceof NativeTriggerScreen,"creative shift click same dedicated screen");mc.player.closeContainer();shift(mc,false);mode(3);stage=8;
        }else if(stage==8&&ticks>wait+15){if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||!NativeTransmitterItem.valid(mc.player.getMainHandItem()))return;shift(mc,true);use(mc,InteractionHand.MAIN_HAND,true);stage=9;
        }else if(stage==9&&ticks>wait+20){require(mc.screen instanceof TransmitterScreen,"chest took priority over transmitter editor");mode(22);
            var field=mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.EditBox).map(c->(net.minecraft.client.gui.components.EditBox)c).findFirst().orElseThrow();field.setValue("31, 32");mc.screen.keyPressed(257,0,0);shift(mc,false);stage=10;wait=ticks;
        }else if(stage==10&&ticks>wait+20){require(mc.screen==null&&NativeTransmitterItem.channels(mc.player.getMainHandItem()).equals(ChannelList.of(31,32)),"transmitter editor save");mode(24);use(mc,InteractionHand.MAIN_HAND,true);stage=11;
        }else if(stage==11&&ticks>wait+20){require(mc.screen==null&&tile(mc).signalLevel()==15,"main hand transmitter chest click did not power Trigger");mode(20);use(mc,InteractionHand.MAIN_HAND,true);stage=12;
        }else if(stage==12&&ticks>wait+20){require(mc.screen==null&&tile(mc).signalLevel()==0,"second transmitter click did not turn off");mode(21);mode(4);stage=13;
        }else if(stage==13&&ticks>wait+15){if(!NativeTransmitterItem.valid(mc.player.getOffhandItem())||!mc.player.getMainHandItem().isEmpty())return;mc.gameMode.useItem(mc.player,InteractionHand.OFF_HAND);stage=14;wait=ticks;
        }else if(stage==14&&ticks>wait+20){require(tile(mc).signalLevel()==15,"offhand ordinary use");mc.gameMode.useItem(mc.player,InteractionHand.OFF_HAND);stage=15;wait=ticks;
        }else if(stage==15&&ticks>wait+20){require(tile(mc).signalLevel()==0,"offhand withdrawal");mc.disconnect();stage=99;}
    }
    @SubscribeEvent public static void capture(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event){if(capture){capture=false;var mc=Minecraft.getInstance();net.minecraft.client.Screenshot.grab(mc.gameDirectory,captureName,mc.getMainRenderTarget(),message->{});}}
}
