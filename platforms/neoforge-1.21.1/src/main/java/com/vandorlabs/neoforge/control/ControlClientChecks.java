package com.vandorlabs.neoforge.control;

import com.vandorlabs.neoforge.ModernClientRuntimeChecks;
import com.vandorlabs.ui.ControlDialog;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Actual vanilla picked clicks, authoritative pulse expiry, exact settings and configured icons. */
public final class ControlClientChecks {
    private static final BlockPos BUTTON=new BlockPos(100,80,8),ROCKER=new BlockPos(104,80,8);
    private static int stage,started,pressed;
    private static boolean retriggered;
    private ControlClientChecks(){}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Native mounted controls: "+message+" stage="+stage);}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static MountedControlEntity tile(Minecraft mc,BlockPos pos){return mc.level.getBlockEntity(pos) instanceof MountedControlEntity t?t:null;}
    private static void capture(String suffix){ModernClientRuntimeChecks.capture("modern-controls-"+suffix+".png");}
    private static boolean captured(String suffix){return ModernClientRuntimeChecks.captured("modern-controls-"+suffix+".png");}
    private static void crouch(Minecraft mc,boolean value){mc.player.setShiftKeyDown(value);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,value?ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));}
    private static void aim(Minecraft mc,BlockPos pos){var box=mc.level.getBlockState(pos).getShape(mc.level,pos).bounds();Vec3 target=new Vec3(pos.getX()+(box.minX+box.maxX)/2,pos.getY()+(box.minY+box.maxY)/2,pos.getZ()+(box.minZ+box.maxZ)/2);Vec3 delta=target.subtract(mc.player.getEyePosition());float yaw=(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90,pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(),mc.player.getY(),mc.player.getZ(),yaw,pitch,true));}
    private static void click(Minecraft mc,BlockPos pos){require(mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos),"actual crosshair did not select authored control");mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,(BlockHitResult)mc.hitResult);}
    private static void option(ControlScreen gui,int id,int mouse){var layout=(ControlDialog)field(gui,"layout");var state=(ControlDialog.State)field(gui,"state");var b=layout.controls(state).stream().filter(button->button.id==id).findFirst().orElseThrow();gui.mouseClicked(b.x+b.width/2D,b.y+b.height/2D,mouse);}
    public static boolean tick(Minecraft mc,int tick){
        if(mc.player==null||mc.level==null||mc.gameMode==null)return false;
        if(stage==0){mc.player.closeContainer();crouch(mc,false);Vec3 destination=new Vec3(100.5,80,5),delta=destination.subtract(mc.player.position());if(delta.length()>4){if(tick-started<5)return false;Vec3 next=mc.player.position().add(delta.normalize().scale(4));mc.player.setPos(next);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(next.x,next.y,next.z,true));started=tick;return false;}mc.player.setPos(destination);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(destination.x,destination.y,destination.z,true));int empty=-1;for(int i=0;i<9;i++)if(mc.player.getInventory().getItem(i).isEmpty()){empty=i;break;}require(empty>=0,"ordinary use needs an empty hand");mc.player.getInventory().selected=empty;mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(empty));stage=1;started=tick;return false;}
        require(tick-started<400,"timed out");
        if(stage==1){if(tick-started<10||tile(mc,BUTTON)==null)return false;require(!mc.player.isCrouching(),"standing eye must settle before click");aim(mc,BUTTON);stage=2;started=tick;return false;}
        if(stage==2){if(tick-started<10)return false;click(mc,BUTTON);pressed=tick;stage=3;started=tick;return false;}
        if(stage==3){if(!tile(mc,BUTTON).localOn())return false;require(tile(mc,BUTTON).outputLevel()==15&&!tile(mc,ROCKER).localOn(),"transient pulse supplies15 without changing persistent rocker bank");capture("button-on");stage=4;return false;}
        if(stage==4){if(!captured("button-on"))return false;if(!retriggered&&tick-pressed>=10){require(tile(mc,BUTTON).localOn(),"button expired before retrigger");click(mc,BUTTON);retriggered=true;}if(tick-pressed<25)return false;require(retriggered,"actual repeatedpress not exercised");require(!tile(mc,BUTTON).localOn()&&!tile(mc,ROCKER).localOn(),"real scheduled20tick pulse must expire without setting persistent rocker");capture("button-off");stage=5;started=tick;return false;}
        if(stage==5){if(!captured("button-off"))return false;mc.player.setPos(104.5,80,5);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(104.5,80,5,true));aim(mc,ROCKER);stage=6;started=tick;return false;}
        if(stage==6){if(tick-started<10)return false;click(mc,ROCKER);stage=7;started=tick;return false;}
        if(stage==7){if(!tile(mc,ROCKER).localOn())return false;capture("rocker-on");stage=8;started=tick;return false;}
        if(stage==8){if(!captured("rocker-on"))return false;crouch(mc,true);click(mc,ROCKER);stage=9;started=tick;return false;}
        if(stage==9){if(!(mc.screen instanceof ControlScreen))return false;mc.options.guiScale().set(3);mc.resizeDisplay();stage=10;started=tick;return false;}
        if(stage==10){if(tick-started<10)return false;capture("dialog-scale3");stage=11;return false;}
        if(stage==11){if(!captured("dialog-scale3"))return false;mc.options.guiScale().set(2);mc.resizeDisplay();stage=12;started=tick;return false;}
        if(stage==12){if(tick-started<10)return false;capture("dialog-scale2");stage=13;return false;}
        if(stage==13){if(!captured("dialog-scale2"))return false;var gui=(ControlScreen)mc.screen;option(gui,9,1);option(gui,10,1);option(gui,11,1);var state=(ControlDialog.State)field(gui,"state");require(state.height==3&&state.tilt==3&&state.direction==3,"right click selects previous mount value");((EditBox)field(gui,"channels")).setValue("501, 502, 503");require(tile(mc,ROCKER).baseHeight()==0,"draft options must not autosubmit");capture("dialog-edited");stage=14;return false;}
        if(stage==14){if(!captured("dialog-edited"))return false;option((ControlScreen)mc.screen,1,0);crouch(mc,false);stage=15;started=tick;return false;}
        if(stage==15){if(tick-started<10||tile(mc,ROCKER).baseHeight()!=3||tile(mc,ROCKER).baseTilt()!=3||tile(mc,ROCKER).tiltDirection()!=3)return false;require(tile(mc,ROCKER).getRedstoneChannels().toString().equals("501, 502, 503"),"settings server roundtrip lost membership");aim(mc,ROCKER);stage=16;started=tick;return false;}
        if(stage==16){if(tick-started<10)return false;capture("tilted");stage=17;return false;}
        if(stage==17){if(!captured("tilted"))return false;mc.setScreen(new Icons());stage=18;started=tick;return false;}
        if(stage==18){if(tick-started<10)return false;capture("icons");stage=19;return false;}
        if(stage==19){if(!captured("icons"))return false;mc.setScreen(null);System.out.println("[vandorlabs][modern] controls-client PASS picked button/rocker, real scheduled20tick expiry/retrigger/pulse-isolation, GUI3/2/rightcycle/draft+server settings, tilted model and128configured icons");stage=20;}
        return stage==20;
    }
    private static final class Icons extends Screen {
        private final List<ItemStack> items=new ArrayList<>();
        Icons(){super(Component.literal("Configured controls"));for(var block:new MountedControlBlock[]{ControlRegistration.BUTTON.get(),ControlRegistration.ROCKER.get()})for(int h=0;h<4;h++)for(int t=0;t<4;t++)for(int d=0;d<4;d++){ItemStack item=new ItemStack(block);var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("ControlMountVersion",2);tag.putInt("BaseHeight",h);tag.putInt("BaseTilt",t);tag.putInt("TiltDirection",d);item.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(MountedControlBlock.wrap(tag)));items.add(item);}}
        @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){}
        @Override public void render(GuiGraphics g,int x,int y,float delta){g.fill(0,0,width,height,0xFF101012);int left=(width-352)/2,top=(height-176)/2;for(int i=0;i<items.size();i++){int xx=left+i%16*22,yy=top+i/16*22;g.fill(xx,yy,xx+20,yy+20,0xFF282C32);g.renderItem(items.get(i),xx+2,yy+2);}super.render(g,x,y,delta);}
        @Override public boolean isPauseScreen(){return false;}
    }
}
