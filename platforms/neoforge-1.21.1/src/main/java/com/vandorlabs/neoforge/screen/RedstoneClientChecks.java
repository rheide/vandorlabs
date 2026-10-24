package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.ModernClientRuntimeChecks;
import com.vandorlabs.neoforge.client.NativeMaterialPicker;
import com.vandorlabs.ui.RedstoneDialogLayout;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in live overlay hits, linked banks, pulse timing and the actual bounded row editor. */
public final class RedstoneClientChecks {
    private static final BlockPos POS=new BlockPos(6,80,0),LINKED=new BlockPos(8,80,0);
    private static int stage,started,editorCapture;
    private RedstoneClientChecks(){}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException("Modern redstone client: "+message);}
    private static Object field(Object object,String name){try{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static ProgrammableScreenEntity tile(Minecraft mc,BlockPos pos){return (ProgrammableScreenEntity)mc.level.getBlockEntity(pos);}
    private static void aim(Minecraft mc,double u,double v){
        Vec3 target=new Vec3(POS.getX()+1-u/128,POS.getY()+1-v/128,POS.getZ()-.00625);Vec3 direction=target.subtract(mc.player.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(direction.z,direction.x))-90,pitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z)));
        mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(),mc.player.getY(),mc.player.getZ(),yaw,pitch,mc.player.onGround()));
    }
    private static void clickWorld(Minecraft mc,double u,double v,boolean sneak){
        aim(mc,u,v);mc.player.setShiftKeyDown(sneak);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,sneak?ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(POS.getX()+1-u/128,POS.getY()+1-v/128,POS.getZ()),Direction.NORTH,POS,false));
    }
    private static void clickEditor(RedstoneConfigurationScreen gui,int id,int button){
        RedstoneDialogLayout panel=(RedstoneDialogLayout)field(gui,"panel");
        for(var control:(List<RedstoneDialogLayout.Control>)field(gui,"controls"))if(control.bounds.id==id){require(control.visible&&control.enabled,"editor control disabled "+id);gui.mouseClicked(control.bounds.x+control.bounds.width/2D,control.bounds.y+control.bounds.height/2D,button);return;}
        throw new IllegalStateException("Missing redstone editor operation "+id);
    }
    public static boolean tick(Minecraft mc,int tick){
        if(mc.player==null||mc.level==null||mc.gameMode==null)return false;
        if(stage==0){mc.player.closeContainer();mc.player.setPos(6.5,80,-3);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(6.5,80,-3,true));started=tick;stage=1;return false;}
        require(tick-started<500,"stage timed out "+stage+", position="+mc.player.position()+", sliderLevel="+(tile(mc,POS).redstone().rows().size()>1?tile(mc,POS).redstone().rows().get(1).level():-1));
        if(stage==1){if(tick-started<10)return false;require(tile(mc,POS).redstone().rows().size()==3&&tile(mc,LINKED).redstone().rows().size()==1,"server overlay fixtures missing");clickWorld(mc,110,29,false);stage=2;started=tick;return false;}
        if(stage==2){if(!tile(mc,POS).redstone().rows().get(0).active()||!tile(mc,LINKED).redstone().rows().get(0).active())return false;require(!tile(mc,POS).redstone().rows().get(1).active(),"partial channel slider incorrectly ALL powered");clickWorld(mc,97.5,41,false);stage=3;started=tick;return false;}
        if(stage==3){var rows=tile(mc,POS).redstone().rows();if(rows.get(1).level()!=10||!rows.get(1).active())return false;require(rows.get(1).selectedSegment()==2,"rendered slider detent differs");aim(mc,64,64);ModernClientRuntimeChecks.capture("modern-redstone-overlay-on.png");stage=4;started=tick;return false;}
        if(stage==4){if(tick-started<10)return false;clickWorld(mc,110,29,false);stage=5;started=tick;return false;}
        if(stage==5){var rows=tile(mc,POS).redstone().rows();if(rows.get(0).active()||tile(mc,LINKED).redstone().rows().get(0).active())return false;require(rows.get(1).level()==10&&!rows.get(1).active(),"partial channel slider lost MAX level or ALL policy");clickWorld(mc,110,53,false);stage=6;started=tick;return false;}
        if(stage==6){if(!tile(mc,POS).redstone().rows().get(2).active())return false;stage=7;started=tick;return false;}
        if(stage==7){if(tick-started<25)return false;require(!tile(mc,POS).redstone().rows().get(2).active(),"pulse did not expire");clickWorld(mc,110,29,true);stage=8;started=tick;return false;}
        if(stage==8){
            if(!(mc.screen instanceof ScreenClient.Selector gui))return false;NativeMaterialPicker picker=(NativeMaterialPicker)field(gui,"picker");picker.setSelected(-100001);int row=picker.rows().indexOf(-100001)-picker.scroll();var panel=(com.vandorlabs.ui.ProgrammableDialogLayout)field(gui,"panel");require(row>=0&&row<panel.rows(true),"Redstone choice not visible after centering");gui.mouseClicked(panel.listX+20,panel.top+54+row*22+10,0);stage=9;started=tick;return false;
        }
        if(stage==9){
            if(!(mc.screen instanceof RedstoneConfigurationScreen))return false;
            if(editorCapture==0){mc.options.guiScale().set(3);mc.resizeDisplay();editorCapture=1;started=tick;return false;}
            if(tick-started<10)return false;
            if(editorCapture==1){ModernClientRuntimeChecks.capture("modern-redstone-editor-scale3.png");editorCapture=2;started=tick;return false;}
            if(editorCapture==2){mc.options.guiScale().set(2);mc.resizeDisplay();editorCapture=3;started=tick;return false;}
            if(editorCapture==3){ModernClientRuntimeChecks.capture("modern-redstone-editor-scale2.png");editorCapture=4;started=tick;return false;}
            ModernClientRuntimeChecks.capture("modern-redstone-editor.png");stage=10;started=tick;return false;
        }
        if(stage==10){
            if(tick-started<10)return false;var gui=(RedstoneConfigurationScreen)mc.screen;
            clickEditor(gui,7,1);require(((List<Integer>)field(gui,"kinds")).get(0)==2,"right editor click must select Pulse before Toggle");clickEditor(gui,7,0);require(((List<Integer>)field(gui,"kinds")).get(0)==0,"left/right control cycle mismatch");
            for(int i=3;i<8;i++)clickEditor(gui,2,0);require(((List<?>)field(gui,"labels")).size()==8,"row editor did not reach eight rows");
            var controls=(List<RedstoneDialogLayout.Control>)field(gui,"controls");require(controls.stream().filter(c->c.bounds.id==2).noneMatch(c->c.enabled),"Add remains enabled beyond eight rows");
            RedstoneDialogLayout panel=(RedstoneDialogLayout)field(gui,"panel");gui.mouseClicked(panel.left+24,panel.top+panel.rowListTop+20+8,0);clickEditor(gui,5,0);require(((List<Integer>)field(gui,"kinds")).get(0)==1&&((List<?>)field(gui,"channels")).get(0).toString().equals("77, 78"),"reorder separated row settings/channels");
            clickEditor(gui,8,1);clickEditor(gui,9,1);require(((List<Integer>)field(gui,"mins")).get(0)==4&&((List<Integer>)field(gui,"maxs")).get(0)==14,"right-click low/high range limits");
            ((EditBox)field(gui,"titleField")).setValue("PORT CONTROL");clickEditor(gui,4,0);stage=11;started=tick;return false;
        }
        if(stage==11){var contents=tile(mc,POS).redstone();if(contents.rows().size()!=8||!contents.title().equals("PORT CONTROL"))return false;require(contents.rows().get(0).slider&&contents.rows().get(0).min==4&&contents.rows().get(0).max==14,"edited options did not return from server");require(tile(mc,POS).getBlockState().getLightEmission(mc.level,POS)==11,"overlay must illuminate even with underlying display off");aim(mc,64,64);ModernClientRuntimeChecks.capture("modern-redstone-overlay-eight.png");stage=12;started=tick;return false;}
        if(stage==12){if(tick-started<15)return false;System.out.println("[vandorlabs][modern] redstone-client PASS native ray toggle/slider/20-tick pulse, linked partial banks, actual editor left/right/reorder, eight-row limit, saved options roundtrip and overlay light");return true;}
        return false;
    }
}
