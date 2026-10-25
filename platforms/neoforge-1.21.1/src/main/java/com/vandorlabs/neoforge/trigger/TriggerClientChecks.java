package com.vandorlabs.neoforge.trigger;

import com.vandorlabs.neoforge.ModernClientRuntimeChecks;
import com.vandorlabs.neoforge.client.NativeMaterialPicker;
import com.vandorlabs.ui.ProgrammableDialogLayout;
import com.vandorlabs.ui.TriggerDialog;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Real four-list selections, immediate settings/channel typing and server-driven visible bands. */
public final class TriggerClientChecks {
    private static final BlockPos POS=new BlockPos(112,80,8);
    private static int stage,started,band;
    private static final int[] LEVELS={0,3,7,13},CHOICES={1,2,3,4};
    private TriggerClientChecks(){}
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Native trigger client: "+message+" stage="+stage);}
    private static Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static TriggerEntity tile(Minecraft mc){return mc.level.getBlockEntity(POS) instanceof TriggerEntity tile?tile:null;}
    private static void capture(String name){ModernClientRuntimeChecks.capture("modern-trigger-"+name+".png");}
    private static boolean captured(String name){return ModernClientRuntimeChecks.captured("modern-trigger-"+name+".png");}
    private static void option(TriggerScreen gui,int id,int button){var panel=(ProgrammableDialogLayout)field(gui,"panel");var state=(TriggerDialog.State)field(gui,"state");var control=TriggerDialog.controls(panel,state).stream().filter(c->c.bounds.id==id).findFirst().orElseThrow();need(control.visible&&control.enabled,"control unavailable "+id);var b=control.bounds;gui.mouseClicked(b.x+b.width/2D,b.y+b.height/2D,button);}
    private static void choice(TriggerScreen gui,int tab,int finish){var panel=(ProgrammableDialogLayout)field(gui,"panel");var picker=((NativeMaterialPicker[])field(gui,"pickers"))[tab];picker.setSelected(finish);int row=picker.rows().indexOf(finish)-picker.scroll();need(row>=0&&row<panel.rows(true),"material row not visible");gui.mouseClicked(panel.listX+12,panel.top+54+row*22+11,0);}
    private static void itemArtwork(Minecraft mc){
        var item=new net.minecraft.world.item.ItemStack(TriggerRegistration.ITEM.get());var tag=new net.minecraft.nbt.CompoundTag();tag.putInt(com.vandorlabs.ui.ProgrammableBlockDialog.MAIN_TEXTURE_KEY,1);tag.putInt("HousingTextureVersion",1);tag.putInt("MainTextureLayout",0);tag.putBoolean("FaceTexturesEnabled",true);tag.putIntArray("FaceTextures",new int[]{5,5,5,5,5,5});tag.putInt("TriggerOnTexture",4);tag.putInt("TriggerExactLevel",0);item.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(tag));var model=mc.getItemRenderer().getModel(item,mc.level,mc.player,0);var expected=net.minecraft.resources.ResourceLocation.parse(com.vandorlabs.neoforge.ModernFinishCatalog.entry(1).texture());int faces=0;for(var pass:model.getRenderPasses(item,false))for(net.minecraft.core.Direction side:new net.minecraft.core.Direction[]{null,net.minecraft.core.Direction.UP,net.minecraft.core.Direction.DOWN,net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.WEST})for(var quad:pass.getQuads(null,side,net.minecraft.util.RandomSource.create(0))){need(quad.getSprite().contents().name().equals(expected),"actual trigger item must show uniformOFF despite exact0/face overrides");faces++;}need(faces==6,"actual trigger item must emit sixOFFfaces");need(item.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA).copyTag().getBoolean("FaceTexturesEnabled"),"item preview must retain stored independent face data");
    }
    private static void aim(Minecraft mc){Vec3 target=Vec3.atCenterOf(POS),delta=target.subtract(mc.player.getEyePosition());float yaw=(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90,pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(),mc.player.getY(),mc.player.getZ(),yaw,pitch,true));}
    public static boolean tick(Minecraft mc,int tick){
        if(mc.player==null||mc.level==null||mc.gameMode==null)return false;
        if(stage==0){mc.player.closeContainer();mc.player.setShiftKeyDown(false);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));Vec3 destination=new Vec3(112.5,80,mc.player.getX()<111?-3:5),delta=destination.subtract(mc.player.position());if(delta.length()>4){if(tick-started<5)return false;Vec3 next=mc.player.position().add(delta.normalize().scale(4));mc.player.setPos(next);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(next.x,next.y,next.z,true));started=tick;return false;}mc.player.setPos(destination);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(destination.x,destination.y,destination.z,true));if(destination.z==-3){started=tick;return false;}stage=1;started=tick;return false;}
        need(tick-started<600,"native settings or visible source timed out");
        if(stage==1){if(tick-started<10||tile(mc)==null)return false;aim(mc);stage=2;started=tick;return false;}
        if(stage==2){if(tick-started<10)return false;need(mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(POS),"actual pick did not select trigger");mc.player.setShiftKeyDown(true);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,(BlockHitResult)mc.hitResult);stage=3;started=tick;return false;}
        if(stage==3){if(!(mc.screen instanceof TriggerScreen gui))return false;itemArtwork(mc);var state=(TriggerDialog.State)field(gui,"state");if(state.bands)option(gui,101,0);mc.options.guiScale().set(3);mc.resizeDisplay();stage=4;started=tick;return false;}
        if(stage==4){if(tick-started<10)return false;capture("gui3");stage=5;return false;}
        if(stage==5){if(!captured("gui3"))return false;mc.options.guiScale().set(2);mc.resizeDisplay();stage=6;started=tick;return false;}
        if(stage==6){if(tick-started<10)return false;capture("gui2");stage=7;return false;}
        if(stage==7){if(!captured("gui2"))return false;var gui=(TriggerScreen)mc.screen;option(gui,101,0);for(int tab=0;tab<4;tab++){if(tab!=0)option(gui,90+tab,0);choice(gui,tab,CHOICES[tab]);}option(gui,102,1);need(((TriggerDialog.State)field(gui,"state")).exact==15,"previous exact level wrapsAny to15");capture("bands-exact");stage=8;started=tick;return false;}
        if(stage==8){if(!captured("bands-exact")||tile(mc).state().exact!=15||tile(mc).state().low!=2||tile(mc).state().medium!=3||tile(mc).state().on!=4||tile(mc).settings().mainTexture!=1)return false;var gui=(TriggerScreen)mc.screen;option(gui,102,0);option(gui,91,0);option(gui,101,1);need(((TriggerDialog.State)field(gui,"state")).tab==3,"disabled bands must fall back fromLow toHigh");option(gui,101,0);var channel=(EditBox)field(gui,"channels");channel.setValue("");gui.setFocused(channel);channel.setFocused(true);gui.charTyped('7',0);gui.charTyped('0',0);gui.charTyped('1',0);stage=9;started=tick;return false;}
        if(stage==9){if(!tile(mc).getRedstoneChannels().toString().equals("701")||!tile(mc).state().bands||tile(mc).state().exact!=-1)return false;var gui=(TriggerScreen)mc.screen;option(gui,90,0);var panel=(ProgrammableDialogLayout)field(gui,"panel");var picker=((NativeMaterialPicker[])field(gui,"pickers"))[0];int old=picker.scroll();gui.mouseScrolled(panel.listX+12,panel.top+65,0,-1);need(picker.scroll()>old,"wheel must scroll activeOff list");int scrolled=picker.scroll();option(gui,91,0);option(gui,90,0);need(((NativeMaterialPicker[])field(gui,"pickers"))[0].scroll()==scrolled,"four lists must retain independent scroll");int listHeight=panel.rows(true)*22;gui.mouseClicked(panel.listX+panel.listWidth+3,panel.top+54+listHeight-2,0);gui.mouseDragged(panel.listX+panel.listWidth+3,panel.top+54+listHeight+80,0,0,80);gui.mouseReleased(panel.listX+panel.listWidth+3,panel.top+54+listHeight+80,0);need(picker.scroll()>scrolled,"drag scrollbar must advance active list");option(gui,100,0);mc.player.setShiftKeyDown(false);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));stage=10;started=tick;return false;}
        if(stage==10){if(tick-started<10)return false;aim(mc);stage=11;started=tick;return false;}
        if(stage==11){if(tile(mc).signalLevel()!=LEVELS[band]||tile(mc).visibleFinish()!=CHOICES[band])return false;capture("band-"+LEVELS[band]);stage=12;return false;}
        if(stage==12){if(!captured("band-"+LEVELS[band]))return false;band++;if(band<4){stage=11;started=tick;return false;}System.out.println("[vandorlabs][modern] trigger-client PASS exact fourlistGUI3/2, previous-cycle, autosend/channeltyping, independentwheel/drag andactual0/3/7/13 consumer artwork");stage=13;}
        return stage==13;
    }
}
