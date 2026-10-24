package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.ModernClientRuntimeChecks;
import com.vandorlabs.neoforge.client.NativeMaterialPicker;
import com.vandorlabs.render.ScreenFamilyPolicy;
import com.vandorlabs.ui.ProgrammableDialogLayout;
import com.vandorlabs.ui.RedstoneDialogLayout;
import com.vandorlabs.ui.ScreenSelectorDialog;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Every registered screen family is opened, rendered and edited through its native session. */
public final class ScreenFamilyClientChecks {
    private static int family,stage,started,slot;
    private ScreenFamilyClientChecks(){}
    private static void require(boolean result,String message){if(!result)throw new IllegalStateException("Modern screen family client: "+message+" family="+family+" stage="+stage);}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static BlockPos pos(){return new BlockPos(40+family*4,80,0);}
    private static ProgrammableScreenEntity tile(Minecraft mc){return mc.level.getBlockEntity(pos()) instanceof ProgrammableScreenEntity screen?screen:null;}
    private static String imageName(String suffix){return "modern-family-"+family+"-"+suffix+".png";}
    private static void capture(String suffix){ModernClientRuntimeChecks.capture(imageName(suffix));}
    private static boolean captured(String suffix){return ModernClientRuntimeChecks.captured(imageName(suffix));}
    private static void scale(Minecraft mc,int scale){mc.options.guiScale().set(scale);mc.resizeDisplay();}
    private static void selectorClick(ScreenClient.Selector gui,int id,int button){var panel=(ProgrammableDialogLayout)field(gui,"panel");var state=(ScreenSelectorDialog.State)field(gui,"state");var profile=(ScreenSelectorDialog.Profile)field(gui,"profile");for(var control:ScreenSelectorDialog.controls(panel,state,profile,key->key))if(control.bounds.id==id){require(control.enabled,"disabled selector control "+id);var b=control.bounds;gui.mouseClicked(b.x+b.width/2D,b.y+b.height/2D,button);return;}throw new IllegalStateException("Missing family selector control "+id);}
    @SuppressWarnings("unchecked") private static void editorClick(RedstoneConfigurationScreen gui,int id){for(var control:(java.util.List<RedstoneDialogLayout.Control>)field(gui,"controls"))if(control.bounds.id==id){require(control.visible&&control.enabled,"disabled editor control "+id);var b=control.bounds;gui.mouseClicked(b.x+b.width/2D,b.y+b.height/2D,0);return;}throw new IllegalStateException("Missing family editor control "+id);}
    private static Vec3 aim(Minecraft mc,double u,double v){
        var screen=tile(mc);var q=ScreenFamilyPolicy.surface(screen.family(),slot,screen.keyboard(),screen.upper(),screen.geometryInverted(),screen.ceilingMounted(),screen.wallPosition(),screen.data().smallInput,screen.ceilingPosition());
        // Fixtures face north, so these authored coordinates are also world-local coordinates.
        double height=ScreenFamilyPolicy.displayHeight(screen.family(),slot);Vec3 target=new Vec3(pos().getX()+(q.topRight.x+(q.topLeft.x-q.topRight.x)*u/128)/16,pos().getY()+(q.topRight.y+(q.bottomRight.y-q.topRight.y)*v/height)/16,pos().getZ()+(q.topRight.z+(q.bottomRight.z-q.topRight.z)*v/height)/16);
        Vec3 direction=target.subtract(mc.player.getEyePosition());float yaw=(float)Math.toDegrees(Math.atan2(direction.z,direction.x))-90,pitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z)));mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(),mc.player.getY(),mc.player.getZ(),yaw,pitch,true));return target;
    }
    private static void readableView(Minecraft mc){
        if(family>=4){var screen=tile(mc);var q=ScreenFamilyPolicy.surface(screen.family(),slot,screen.keyboard(),screen.upper(),screen.geometryInverted(),screen.ceilingMounted(),screen.wallPosition(),screen.data().smallInput,screen.ceilingPosition());
            Vec3 target=new Vec3(pos().getX()+(q.topLeft.x+q.bottomRight.x)/32,pos().getY()+(q.topLeft.y+q.bottomRight.y)/32,pos().getZ()+(q.topLeft.z+q.bottomRight.z)/32);
            Vec3 eye=target.add(q.nx*2.5,q.ny*2.5,q.nz*2.5);double feet=Math.max(pos().getY(),eye.y-mc.player.getEyeHeight());mc.player.setPos(eye.x,feet,eye.z);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(eye.x,feet,eye.z,true));
        }
        aim(mc,64,ScreenFamilyPolicy.displayHeight(tile(mc).family(),slot)/2D);
    }
    private static void use(Minecraft mc,boolean sneak,double u,double v){Vec3 target=aim(mc,u,v);if(!sneak)require(RedstoneScreenInteraction.hit(tile(mc),mc.player,slot).row==0,"native current-view hit must select first row");mc.player.setShiftKeyDown(sneak);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,sneak?ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(target,Direction.NORTH,pos(),false));}
    public static boolean tick(Minecraft mc,int tick){
        if(mc.player==null||mc.level==null||mc.gameMode==null)return false;if(family==7)return true;
        if(stage==-1){if(tick-started<5)return false;stage=0;}
        if(stage==0){require(mc.player.getAbilities().mayfly,"creative flight permission required for stable surface cameras");mc.player.getAbilities().flying=true;mc.player.onUpdateAbilities();Vec3 destination=new Vec3(pos().getX()+.5,80,-3),delta=destination.subtract(mc.player.position());if(delta.length()>4){Vec3 next=mc.player.position().add(delta.normalize().scale(4));mc.player.setPos(next);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(next.x,next.y,next.z,true));stage=-1;started=tick;return false;}mc.player.closeContainer();int empty=-1;for(int hotbar=0;hotbar<9;hotbar++)if(mc.player.getInventory().getItem(hotbar).isEmpty()){empty=hotbar;break;}require(empty>=0,"no empty hotbar slot for ordinary overlay clicks");mc.player.getInventory().selected=empty;mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(empty));mc.player.setPos(pos().getX()+.5,80,-3);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(pos().getX()+.5,80,-3,true));stage=1;started=tick;return false;}
        require(tick-started<400,"native session or settings timed out");
        if(stage==1){if(tick-started<10||tile(mc)==null)return false;slot=0;use(mc,true,64,ScreenFamilyPolicy.displayHeight(tile(mc).family(),0)/2D);stage=2;started=tick;return false;}
        if(stage==2){if(!(mc.screen instanceof ScreenClient.Selector))return false;scale(mc,3);stage=3;started=tick;return false;}
        if(stage==3){if(tick-started<10)return false;var gui=(ScreenClient.Selector)mc.screen;require(field(gui,"profile")==tile(mc).dialogProfile(),"wrong family dialog profile");var panel=(ProgrammableDialogLayout)field(gui,"panel");require(panel.width==Math.min(460,gui.width-8)&&panel.height==Math.min(332,gui.height-8),"shared family panel bounds");capture("scale3");stage=31;started=tick;return false;}
        if(stage==31){if(!captured("scale3"))return false;scale(mc,2);stage=4;started=tick;return false;}
        if(stage==4){if(tick-started<10)return false;capture("scale2");stage=41;started=tick;return false;}
        if(stage==41){if(!captured("scale2"))return false;var gui=(ScreenClient.Selector)mc.screen;if(tile(mc).supportsSlot(1)){selectorClick(gui,91,0);slot=1;}stage=5;started=tick;return false;}
        if(stage==5){if(tick-started<10)return false;var gui=(ScreenClient.Selector)mc.screen;capture("surface-dialog");stage=51;started=tick;return false;}
        if(stage==51){if(!captured("surface-dialog"))return false;mc.player.closeContainer();mc.player.setShiftKeyDown(false);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));stage=6;started=tick;return false;}
        if(stage==6){if(tick-started<10)return false;readableView(mc);stage=62;started=tick;return false;}
        if(stage==62){if(tick-started<10)return false;capture("art-world");stage=61;started=tick;return false;}
        if(stage==61){if(!captured("art-world"))return false;if(family==1){float yaw=mc.player.getYRot()+12;mc.player.setYRot(yaw);mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(),mc.player.getY(),mc.player.getZ(),yaw,mc.player.getXRot(),mc.player.onGround()));stage=63;started=tick;return false;}use(mc,true,64,ScreenFamilyPolicy.displayHeight(tile(mc).family(),slot)/2D);stage=7;started=tick;return false;}
        if(stage==63){if(tick-started<10)return false;require(!(mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos())),"off-target art proof still selects diagonal housing");capture("art-off-target");stage=64;started=tick;return false;}
        if(stage==64){if(!captured("art-off-target"))return false;aim(mc,64,64);stage=65;started=tick;return false;}
        if(stage==65){if(tick-started<10)return false;use(mc,true,64,64);stage=7;started=tick;return false;}
        if(stage==7){if(!(mc.screen instanceof ScreenClient.Selector gui))return false;if(slot==1)selectorClick(gui,91,0);var picker=(NativeMaterialPicker)field(gui,"picker");picker.setSelected(-100001);var panel=(ProgrammableDialogLayout)field(gui,"panel");int row=picker.rows().indexOf(-100001)-picker.scroll();require(row>=0&&row<panel.rows(true),"Redstone picker row not visible");gui.mouseClicked(panel.listX+12,panel.top+54+row*22+11,0);stage=8;started=tick;return false;}
        if(stage==8){if(!(mc.screen instanceof RedstoneConfigurationScreen gui))return false;require(gui.getMenu().slot==slot,"secondary editor session lost selected slot");require(tile(mc).redstone(slot).rows().size()==4,"four configured fixture rows missing");scale(mc,3);stage=9;started=tick;return false;}
        if(stage==9){if(tick-started<10)return false;capture("editor-scale3");stage=91;started=tick;return false;}
        if(stage==91){if(!captured("editor-scale3"))return false;scale(mc,2);stage=10;started=tick;return false;}
        if(stage==10){if(tick-started<10)return false;capture("editor-scale2");stage=101;started=tick;return false;}
        if(stage==101){if(!captured("editor-scale2"))return false;editorClick((RedstoneConfigurationScreen)mc.screen,4);mc.player.setShiftKeyDown(false);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));stage=11;started=tick;return false;}
        if(stage==11){if(tick-started<10||mc.screen!=null||!tile(mc).redstone(slot).enabled())return false;double y=ScreenFamilyPolicy.rowTop(tile(mc).family(),slot)+5;use(mc,false,110,y);stage=12;started=tick;return false;}
        if(stage==12){if(!tile(mc).redstone(slot).rows().get(0).active())return false;require(tile(mc).getBlockState().getLightEmission(mc.level,pos())==(((ProgrammableScreenBlock)tile(mc).getBlockState().getBlock()).emitsLight()?11:0),"family overlay light");aim(mc,64,ScreenFamilyPolicy.displayHeight(tile(mc).family(),slot)/2D);stage=13;started=tick;return false;}
        if(stage==13){if(tick-started<10)return false;capture("overlay-world");stage=131;started=tick;return false;}
        if(stage==131){if(!captured("overlay-world"))return false;family++;stage=0;if(family==7)System.out.println("[vandorlabs][modern] screen-families-client PASS seven real menus, both console slots, native artwork/overlay render, GUI scales3/2 and actual shared-hit toggles");return false;}
        return false;
    }
}
