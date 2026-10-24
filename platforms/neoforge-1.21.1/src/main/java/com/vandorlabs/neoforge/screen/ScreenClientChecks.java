package com.vandorlabs.neoforge.screen;

import com.vandorlabs.ui.DialogInput;
import com.vandorlabs.ui.ProgrammableDialogLayout;
import com.vandorlabs.ui.ScreenSelectorDialog;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in real menu clicks and server roundtrip checks in the disposable client world. */
public final class ScreenClientChecks {
    private static int stage,started,narrowAt;
    private static boolean narrowTested,baselinePrepared,scale3Captured,scale2Ready,scale2Captured;
    private static int expectedSides;
    private static final BlockPos POS=new BlockPos(4,80,0);
    private ScreenClientChecks(){}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Modern screen client: "+message);}
    private static Object field(Object object,String name){try{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static void click(ScreenClient.Selector gui,ProgrammableDialogLayout panel,ScreenSelectorDialog.State state,int id,int mouse){
        for(var control:ScreenSelectorDialog.controls(panel,state,key->key))if(control.bounds.id==id){require(control.enabled,"requested control disabled "+id);gui.mouseClicked(control.bounds.x+control.bounds.width/2D,control.bounds.y+control.bounds.height/2D,mouse);return;}
        throw new IllegalStateException("Missing screen control "+id);
    }
    public static boolean tick(Minecraft mc,int tick){
        if(mc.player==null||mc.level==null||mc.gameMode==null)return false;
        if(tick==0)started=0;
        if(stage==0){
            mc.player.closeContainer();mc.player.setPos(4.5,81,-3);
            mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(4.5,81,-3,true));
            stage=10;started=tick;return false;
        }
        if(stage==10){
            if(tick-started<10)return false;
            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
            mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(POS).add(0,0,-.5),Direction.NORTH,POS,false));
            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));stage=1;started=tick;return false;
        }
        require(tick-started<400,"timed out waiting for actual screen menu/roundtrip at stage "+stage);
        if(stage==1){
            if(!(mc.screen instanceof ScreenClient.Selector gui))return false;
            if(!narrowTested){gui.resize(mc,320,240);var narrow=(ProgrammableDialogLayout)field(gui,"panel");require(narrow.width==312&&narrow.height==232,"actual narrow GUI dimensions");com.vandorlabs.neoforge.ModernClientRuntimeChecks.capture("modern-screen-narrow.png");narrowTested=true;narrowAt=tick;return false;}
            if(tick-narrowAt<10)return false;
            if(gui.width!=mc.getWindow().getGuiScaledWidth()||gui.height!=mc.getWindow().getGuiScaledHeight())gui.resize(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
            ProgrammableDialogLayout panel=(ProgrammableDialogLayout)field(gui,"panel");ScreenSelectorDialog.State state=(ScreenSelectorDialog.State)field(gui,"state");
            if(!baselinePrepared){
                mc.options.guiScale().set(3);mc.resizeDisplay();panel=(ProgrammableDialogLayout)field(gui,"panel");state=(ScreenSelectorDialog.State)field(gui,"state");
                if(state.mode!=1){click(gui,panel,state,2,0);state=(ScreenSelectorDialog.State)field(gui,"state");}
                if(state.speed!=1){click(gui,panel,state,11,0);state=(ScreenSelectorDialog.State)field(gui,"state");}
                while(state.trigger!=0){click(gui,panel,state,0,0);state=(ScreenSelectorDialog.State)field(gui,"state");}
                while(state.sides!=1){click(gui,panel,state,32,0);state=(ScreenSelectorDialog.State)field(gui,"state");}
                ((net.minecraft.client.gui.components.EditBox)field(gui,"channel")).setValue("0");
                try{Field centered=gui.getClass().getDeclaredField("centered");centered.setAccessible(true);centered.setBoolean(gui,false);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
                gui.resize(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
                var actualPicker=(com.vandorlabs.neoforge.client.NativeMaterialPicker)field(gui,"picker");require(actualPicker.categories().contains("Screens"),"missing authored Screens category");require(actualPicker.rows().size()>=ScreenArtwork.ENTRIES.size(),"missing canonical artwork rows");
                baselinePrepared=true;narrowAt=tick;return false;
            }
            if(!scale3Captured){if(tick-narrowAt<10)return false;com.vandorlabs.neoforge.ModernClientRuntimeChecks.capture("modern-screen-scale3.png");scale3Captured=true;narrowAt=tick;return false;}
            if(!scale2Ready){if(tick-narrowAt<10)return false;mc.options.guiScale().set(2);mc.resizeDisplay();scale2Ready=true;narrowAt=tick;return false;}
            if(!scale2Captured){if(tick-narrowAt<10)return false;com.vandorlabs.neoforge.ModernClientRuntimeChecks.capture("modern-screen-scale2.png");scale2Captured=true;narrowAt=tick;return false;}
            if(tick-narrowAt<10)return false;
            var tile=(ProgrammableScreenEntity)mc.level.getBlockEntity(POS);require(tile!=null,"client screen tile missing");
            require(mc.level.getBlockState(POS).isCollisionShapeFullBlock(mc.level,POS)&&mc.level.getFluidState(POS).isEmpty(),"full-cube screen fluid/collision parity");
            require(panel.width==Math.min(460,gui.width-8)&&panel.height==Math.min(332,gui.height-8),"real shared panel dimensions");
            if(state.mode!=2){click(gui,panel,state,3,0);state=(ScreenSelectorDialog.State)field(gui,"state");}
            if(state.speed!=0){click(gui,panel,state,10,0);state=(ScreenSelectorDialog.State)field(gui,"state");}
            int oldTrigger=state.trigger;click(gui,panel,state,0,1);state=(ScreenSelectorDialog.State)field(gui,"state");require(state.trigger==DialogInput.cycle(oldTrigger,3,-1),"actual right-click previous trigger");
            expectedSides=DialogInput.cycle(state.sides,3,-1);click(gui,panel,state,32,1);state=(ScreenSelectorDialog.State)field(gui,"state");require(state.sides==expectedSides,"actual right-click previous sides");
            int before=((com.vandorlabs.neoforge.client.NativeMaterialPicker)field(gui,"picker")).scroll();gui.mouseScrolled(panel.listX+12,panel.top+66,0,-1);int after=((com.vandorlabs.neoforge.client.NativeMaterialPicker)field(gui,"picker")).scroll();require(after>before,"wheel advances texture rows");
            int listHeight=panel.rows(true)*22;gui.mouseClicked(panel.listX+panel.listWidth+3,panel.top+54+listHeight-2,0);gui.mouseDragged(panel.listX+panel.listWidth+3,panel.top+54+listHeight+100,0,0,100);gui.mouseReleased(panel.listX+panel.listWidth+3,panel.top+54+listHeight+100,0);
            int end=((com.vandorlabs.neoforge.client.NativeMaterialPicker)field(gui,"picker")).scroll();require(end>after,"drag scrollbar reaches later texture rows");
            stage=2;started=tick;return false;
        }
        if(stage==2){
            var tile=(ProgrammableScreenEntity)mc.level.getBlockEntity(POS);
            if(tile==null||tile.data().displayMode!=2||tile.data().animationSpeedIndex!=0||tile.sides()!=expectedSides)return false;
            require(tile.getBlockState().getLightEmission(mc.level,POS)==(tile.effectiveMode()==0?0:11),"screen light follows shared effective mode");
            com.vandorlabs.neoforge.ModernClientRuntimeChecks.capture("modern-screen-dialog.png");stage=3;started=tick;return false;
        }
        if(stage==3){if(tick-started<10)return false;mc.player.closeContainer();mc.player.setYRot(0);mc.player.setXRot(31);stage=4;started=tick;return false;}
        if(stage==4){if(tick-started<20)return false;require(ScreenRenderer.framesForChecks("engineering_screen")>1,"canonical compact animation frames");com.vandorlabs.neoforge.ModernClientRuntimeChecks.capture("modern-screen-world-a.png");stage=5;started=tick;return false;}
        if(stage==5){if(tick-started<30)return false;com.vandorlabs.neoforge.ModernClientRuntimeChecks.capture("modern-screen-world-b.png");System.out.println("[vandorlabs][modern] screen-client PASS actual/narrow menus, shared mode/speed/right-click controls, wheel/drag texture rows, roundtrip settings, full-cube dry collision/light and canonical decoded animated render");stage=6;started=tick;return false;}
        if(stage==6)return tick-started>=10;
        return false;
    }
}
