package com.vandorlabs.neoforge.control;

import com.vandorlabs.neoforge.ModernClientRuntimeChecks;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.ControlDialog;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Installed eight-family GUI drafts, exact settings round trips and readable authored models. */
public final class ExtendedControlClientChecks {
    private static final String[] IDS={"industrial_power_lever","compact_power_lever","small_power_lever","large_power_lever","thruster_lever","airliner_throttle","fighter_throttle","wall_slider"};
    private static int family,stage,started,press;private static boolean activeQueued,modelsChecked;private static ControlDialog.State initial,expected;private static ChannelList savedChannels;
    private ExtendedControlClientChecks(){}
    public static String[] captures(){String[] result=new String[IDS.length*6];int i=0;for(String id:IDS)for(String suffix:new String[]{"world","active","gui3","gui2","edited","configured"})result[i++]="modern-control-"+id+"-"+suffix+".png";return result;}
    private static BlockPos pos(){return new BlockPos(144+family*4,80,8);}
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Expanded controls "+IDS[family]+" stage"+stage+": "+message);}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static MountedControlEntity tile(Minecraft mc){return mc.level.getBlockEntity(pos()) instanceof MountedControlEntity t?t:null;}
    private static ControlDialog.State state(ControlScreen gui){return (ControlDialog.State)field(gui,"state");}
    private static void capture(String suffix){ModernClientRuntimeChecks.capture("modern-control-"+IDS[family]+"-"+suffix+".png");}
    private static boolean captured(String suffix){return ModernClientRuntimeChecks.captured("modern-control-"+IDS[family]+"-"+suffix+".png");}
    private static void crouch(Minecraft mc,boolean value){mc.player.setShiftKeyDown(value);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,value?ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));}
    private static void aim(Minecraft mc){BlockPos p=pos();var b=mc.level.getBlockState(p).getShape(mc.level,p).bounds();Vec3 delta=new Vec3(p.getX()+(b.minX+b.maxX)/2,p.getY()+(b.minY+b.maxY)/2,p.getZ()+(b.minZ+b.maxZ)/2).subtract(mc.player.getEyePosition());float yaw=(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90,pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(),mc.player.getY(),mc.player.getZ(),yaw,pitch,true));}
    private static void click(Minecraft mc){need(mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos()),"actual crosshair must select installed authored control");mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,(BlockHitResult)mc.hitResult);}
    private static void open(Minecraft mc){crouch(mc,true);click(mc);}
    private static void option(ControlScreen gui,int id,int mouse){var layout=(ControlDialog)field(gui,"layout");var b=layout.controls(state(gui)).stream().filter(v->v.id==id).findFirst().orElseThrow();gui.mouseClicked(b.x+b.width/2D,b.y+b.height/2D,mouse);}
    private static void edit(ControlScreen gui){var layout=(ControlDialog)field(gui,"layout");for(var b:layout.controls(state(gui)))if(b.id!=1)option(gui,b.id,1);}
    private static String maxChannels(){StringBuilder s=new StringBuilder();for(int i=0;i<64;i++){if(i>0)s.append(", ");s.append(Integer.MAX_VALUE-i);}return s.toString();}
    private static boolean matches(MountedControlEntity tile,ControlDialog.State s){return tile.baseHeight()==s.height&&tile.baseTilt()==s.tilt&&tile.tiltDirection()==s.direction&&(!tile.profile().signal||tile.lowLimit()==s.low&&tile.highLimit()==s.high)&&(!tile.profile().selectable||tile.controlType()==s.type)&&(!tile.profile().power||tile.powerLeverSize()==s.size);}
    public static boolean tick(Minecraft mc,int tick){
        if(mc.player==null||mc.level==null||mc.gameMode==null)return false;if(family==IDS.length)return true;
        if(stage==0){mc.player.closeContainer();crouch(mc,false);Vec3 destination=new Vec3(pos().getX()+.5,80,mc.player.getX()<pos().getX()-1?-3:5),delta=destination.subtract(mc.player.position());if(delta.length()>4){if(tick-started<5)return false;Vec3 next=mc.player.position().add(delta.normalize().scale(4));mc.player.setPos(next);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(next.x,next.y,next.z,true));started=tick;return false;}mc.player.setPos(destination);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(destination.x,destination.y,destination.z,true));if(destination.z==-3){started=tick;return false;}stage=1;started=tick;return false;}
        need(tick-started<600,"timed out awaiting actual menu/entity/capture");
        if(stage==1){if(tick-started<10||tile(mc)==null)return false;need(!mc.player.isCrouching(),"standing eye must settle before pick");if(!modelsChecked){ExpandedControlClientModelChecks.verify(mc);modelsChecked=true;}aim(mc);stage=2;started=tick;return false;}
        if(stage==2){if(tick-started<10)return false;capture("world");stage=3;return false;}
        if(stage==3){if(!captured("world"))return false;press=0;activeQueued=false;click(mc);stage=20;started=tick;return false;}
        if(stage==20){boolean signal=tile(mc).signalControl();int target=signal?(press+1)%4:press==0?1:0;if(signal?tile(mc).step()!=target:tile(mc).localOn()!=(target>0))return false;need(tile(mc).outputLevel()==(signal?new int[]{0,5,10,15}[target]:target*15),"actual source must publish exact configured detent level");if(press==0){if(tick-started<10)return false;if(!activeQueued){capture("active");activeQueued=true;return false;}if(!captured("active"))return false;}press++;if(press<(signal?4:2)){aim(mc);stage=21;started=tick;return false;}need(tile(mc).outputLevel()==0,"four detents/two toggles must restoreOFF");open(mc);stage=4;started=tick;return false;}
        if(stage==21){if(tick-started<10)return false;click(mc);stage=20;started=tick;return false;}
        if(stage==4){if(!(mc.screen instanceof ControlScreen gui))return false;initial=state(gui);savedChannels=tile(mc).getRedstoneChannels();mc.options.guiScale().set(3);mc.resizeDisplay();stage=5;started=tick;return false;}
        if(stage==5){if(tick-started<10)return false;capture("gui3");stage=6;return false;}
        if(stage==6){if(!captured("gui3"))return false;mc.options.guiScale().set(2);mc.resizeDisplay();stage=7;started=tick;return false;}
        if(stage==7){if(tick-started<10)return false;capture("gui2");stage=8;return false;}
        if(stage==8){if(!captured("gui2"))return false;var gui=(ControlScreen)mc.screen;edit(gui);((EditBox)field(gui,"channels")).setValue("901, 902");stage=9;started=tick;return false;}
        if(stage==9){if(tick-started<10)return false;need(matches(tile(mc),initial)&&tile(mc).getRedstoneChannels().equals(savedChannels),"draft changes must not autosend");((ControlScreen)mc.screen).keyPressed(256,0,0);crouch(mc,false);stage=10;started=tick;return false;}
        if(stage==10){if(tick-started<10)return false;aim(mc);stage=11;started=tick;return false;}
        if(stage==11){if(tick-started<10)return false;open(mc);stage=12;started=tick;return false;}
        if(stage==12){if(!(mc.screen instanceof ControlScreen gui))return false;need(matches(tile(mc),state(gui))&&matches(tile(mc),initial),"Escape must discard every draft option");edit(gui);expected=state(gui);need(expected.height==3&&expected.tilt==3&&expected.direction==3,"right click must wrap all mount options to previous");if(tile(mc).profile().signal)need(expected.low==4&&expected.high==14,"limits previous values must respect shared gap");((EditBox)field(gui,"channels")).setValue("1,,2");option(gui,1,0);need(mc.screen==gui,"invalid channel draft must keep dialog open");((EditBox)field(gui,"channels")).setValue(maxChannels());need(ChannelList.parse(maxChannels()).size()==64,"fixture must contain maximum valid channel count");capture("edited");stage=13;return false;}
        if(stage==13){if(!captured("edited"))return false;((ControlScreen)mc.screen).keyPressed(257,0,0);crouch(mc,false);stage=14;started=tick;return false;}
        if(stage==14){if(tick-started<10||!matches(tile(mc),expected)||!tile(mc).getRedstoneChannels().equals(ChannelList.parse(maxChannels())))return false;aim(mc);stage=15;started=tick;return false;}
        if(stage==15){if(tick-started<10)return false;capture("configured");stage=16;return false;}
        if(stage==16){if(!captured("configured"))return false;family++;stage=0;started=tick;if(family==IDS.length){System.out.println("[vandorlabs][modern] extended-controls-client PASS eight authored families, exactGUI3/2, reverse options, draft/Escape, invalid Done, maximum64channel Enter roundtrip andconfigured worldmodels");return true;}}
        return false;
    }
}
