package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.ModernClientRuntimeChecks;
import com.vandorlabs.ui.ProgrammableDialogLayout;
import com.vandorlabs.ui.SlabStairDialog;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Real two/eight-tab material, dormant face, inherited side and stair slider interactions. */
public final class ShapeClientChecks {
    private static int family,stage,started;private static long stableFrame;
    private static final String[] KINDS={"slab","stairs"};
    private ShapeClientChecks(){}
    public static String[] captures(){List<String> names=new ArrayList<>();for(String kind:KINDS)for(String suffix:new String[]{"world","gui3","gui2","side","side-default","faces","dormant","restored","configured"})names.add("modern-shape-"+kind+"-"+suffix+".png");for(String suffix:new String[]{"slider1","slider2","slider4","slider8","sideways","sideways-world"})names.add("modern-shape-stairs-"+suffix+".png");return names.toArray(new String[0]);}
    private static BlockPos pos(){return new BlockPos(240+family*4,80,8);}
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Shape client "+KINDS[family]+" stage="+stage+": "+message);}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static ProgrammableShapeEntity tile(Minecraft mc){return mc.level.getBlockEntity(pos()) instanceof ProgrammableShapeEntity tile?tile:null;}
    private static SlabStairDialog.State state(ShapeScreen gui){return (SlabStairDialog.State)field(gui,"state");}
    private static void capture(String name){ModernClientRuntimeChecks.capture("modern-shape-"+KINDS[family]+"-"+name+".png");}
    private static boolean captured(String name){return ModernClientRuntimeChecks.captured("modern-shape-"+KINDS[family]+"-"+name+".png");}
    private static void crouch(Minecraft mc,boolean on){mc.player.setShiftKeyDown(on);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,on?ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));}
    private static void aim(Minecraft mc){BlockPos p=pos();var b=mc.level.getBlockState(p).getShape(mc.level,p).bounds();Vec3 d=new Vec3(p.getX()+(b.minX+b.maxX)/2,p.getY()+(b.minY+b.maxY)/2,p.getZ()+(b.minZ+b.maxZ)/2).subtract(mc.player.getEyePosition());float yaw=(float)Math.toDegrees(Math.atan2(d.z,d.x))-90,pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(),mc.player.getY(),mc.player.getZ(),yaw,pitch,true));}
    private static void option(ShapeScreen gui,int id,int button){var panel=(ProgrammableDialogLayout)field(gui,"panel");var c=SlabStairDialog.controls(panel,state(gui),gui.getMenu().tile.isStair(),"Done").stream().filter(v->v.bounds.id==id).findFirst().orElseThrow();need(c.enabled,"disabled option "+id);var b=c.bounds;gui.mouseClicked(b.x+b.width/2D,b.y+b.height/2D,button);}
    private static void choose(ShapeScreen gui,int choice){var panel=(ProgrammableDialogLayout)field(gui,"panel");var picker=(com.vandorlabs.neoforge.client.NativeMaterialPicker)field(gui,"picker");picker.setSelected(choice);int row=picker.rows().indexOf(choice)-picker.scroll();need(row>=0&&row<SlabStairDialog.rows(panel),"selected material not visible");gui.mouseClicked(panel.listX+12,SlabStairDialog.listY(panel)+row*22+11,0);}
    private static void slider(ShapeScreen gui,int index){var panel=(ProgrammableDialogLayout)field(gui,"panel");var b=SlabStairDialog.controls(panel,state(gui),true,"Done").stream().filter(v->v.slider).findFirst().orElseThrow().bounds;double x=b.x+4+(b.width-8)*index/3D,y=b.y+b.height/2D;gui.mouseClicked(x,y,0);gui.mouseDragged(x,y,0,0,0);gui.mouseReleased(x,y,0);}
    private static boolean settle(int tick){return tick-started>=10&&ModernClientRuntimeChecks.renderedFrames()-stableFrame>=3;}
    private static void begin(int tick){started=tick;stableFrame=ModernClientRuntimeChecks.renderedFrames();}
    public static boolean tick(Minecraft mc,int tick){
        if(mc.player==null||mc.level==null||mc.gameMode==null)return false;if(family==2)return true;
        if(stage==0){mc.player.closeContainer();crouch(mc,false);Vec3 destination=new Vec3(pos().getX()+(family==1?2.5:.5),80,mc.player.getX()<pos().getX()-1?-3:5),d=destination.subtract(mc.player.position());if(d.length()>4){if(tick-started<5)return false;Vec3 n=mc.player.position().add(d.normalize().scale(4));mc.player.setPos(n);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(n.x,n.y,n.z,true));started=tick;return false;}mc.player.setPos(destination);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(destination.x,destination.y,destination.z,true));if(destination.z==-3){started=tick;return false;}stage=1;begin(tick);return false;}
        need(tick-started<600,"timed out awaiting exact state/capture");
        if(stage==1){if(!settle(tick)||tile(mc)==null)return false;var s=tile(mc).shapeSettings();need(s.main==0&&s.side==-1&&s.layout==1&&!s.facesEnabled&&s.stairs.packed()==8,"fresh family defaults must match legacy");aim(mc);stage=2;begin(tick);return false;}
        if(stage==2){if(!settle(tick))return false;capture("world");stage=3;return false;}
        if(stage==3){if(!captured("world"))return false;need(mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos()),"native pick must select authored shape");crouch(mc,true);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,(BlockHitResult)mc.hitResult);stage=4;begin(tick);return false;}
        if(stage==4){if(!(mc.screen instanceof ShapeScreen))return false;mc.options.guiScale().set(3);mc.resizeDisplay();stage=5;begin(tick);return false;}
        if(stage==5){if(!settle(tick))return false;capture("gui3");stage=6;return false;}
        if(stage==6){if(!captured("gui3"))return false;mc.options.guiScale().set(2);mc.resizeDisplay();stage=7;begin(tick);return false;}
        if(stage==7){if(!settle(tick))return false;capture("gui2");stage=8;return false;}
        if(stage==8){if(!captured("gui2"))return false;var gui=(ShapeScreen)mc.screen;option(gui,103,1);need(state(gui).layout==0,"right click selects previous layout");option(gui,103,0);option(gui,103,0);need(state(gui).layout==2,"layout cycles Stretch/Tile/Fit");choose(gui,1);option(gui,127,0);choose(gui,2);stage=9;begin(tick);return false;}
        if(stage==9){var s=tile(mc).shapeSettings();if(!settle(tick)||s.main!=1||s.side!=2||s.layout!=2)return false;capture("side");stage=10;return false;}
        if(stage==10){if(!captured("side"))return false;option((ShapeScreen)mc.screen,128,0);stage=11;begin(tick);return false;}
        if(stage==11){if(!settle(tick)||tile(mc).sideTexture()!=-1)return false;capture("side-default");stage=12;return false;}
        if(stage==12){if(!captured("side-default"))return false;var gui=(ShapeScreen)mc.screen;choose(gui,2);option(gui,106,0);option(gui,123,0);choose(gui,5);stage=13;begin(tick);return false;}
        if(stage==13){var s=tile(mc).shapeSettings();if(!settle(tick)||!s.facesEnabled||s.faces()[2]!=5)return false;capture("faces");stage=14;return false;}
        if(stage==14){if(!captured("faces"))return false;var gui=(ShapeScreen)mc.screen;option(gui,108,0);need(state(gui).selected()==1,"inherit face must fall back to main");choose(gui,6);option(gui,106,1);stage=15;begin(tick);return false;}
        if(stage==15){var s=tile(mc).shapeSettings();if(!settle(tick)||s.facesEnabled||s.faces()[2]!=6)return false;capture("dormant");stage=16;return false;}
        if(stage==16){if(!captured("dormant"))return false;var gui=(ShapeScreen)mc.screen;option(gui,106,0);option(gui,123,0);need(state(gui).selected()==6,"reenabling must restore dormant face choice");stage=17;begin(tick);return false;}
        if(stage==17){if(!settle(tick)||!tile(mc).shapeSettings().facesEnabled)return false;capture("restored");stage=18;return false;}
        if(stage==18){if(!captured("restored"))return false;var gui=(ShapeScreen)mc.screen;var panel=(ProgrammableDialogLayout)field(gui,"panel");var picker=(com.vandorlabs.neoforge.client.NativeMaterialPicker)field(gui,"picker");int old=picker.scroll();gui.mouseScrolled(panel.listX+12,SlabStairDialog.listY(panel)+11,0,-1);need(picker.scroll()>old,"material wheel scrolling");int wheel=picker.scroll(),height=SlabStairDialog.rows(panel)*22;gui.mouseClicked(panel.listX+panel.listWidth+3,SlabStairDialog.listY(panel)+height-2,0);gui.mouseDragged(panel.listX+panel.listWidth+3,SlabStairDialog.listY(panel)+height+80,0,0,80);gui.mouseReleased(panel.listX+panel.listWidth+3,SlabStairDialog.listY(panel)+height+80,0);need(picker.scroll()>wheel,"material scrollbar dragging");if(family==1){slider(gui,0);stage=19;begin(tick);return false;}stage=25;return false;}
        if(stage==19){if(!settle(tick)||tile(mc).stairState().tread!=1)return false;capture("slider1");stage=20;return false;}
        if(stage==20){if(!captured("slider1"))return false;slider((ShapeScreen)mc.screen,1);stage=34;begin(tick);return false;}
        if(stage==34){if(!settle(tick)||tile(mc).stairState().tread!=2)return false;capture("slider2");stage=35;return false;}
        if(stage==35){if(!captured("slider2"))return false;slider((ShapeScreen)mc.screen,2);stage=36;begin(tick);return false;}
        if(stage==36){if(!settle(tick)||tile(mc).stairState().tread!=4)return false;capture("slider4");stage=37;return false;}
        if(stage==37){if(!captured("slider4"))return false;slider((ShapeScreen)mc.screen,3);stage=21;begin(tick);return false;}
        if(stage==21){if(!settle(tick)||tile(mc).stairState().tread!=8)return false;capture("slider8");stage=22;return false;}
        if(stage==22){if(!captured("slider8"))return false;option((ShapeScreen)mc.screen,131,0);stage=23;begin(tick);return false;}
        if(stage==23){if(!settle(tick)||tile(mc).stairState().roll!=1)return false;capture("sideways");stage=24;return false;}
        if(stage==24){if(!captured("sideways"))return false;option((ShapeScreen)mc.screen,100,0);crouch(mc,false);stage=30;begin(tick);return false;}
        if(stage==30){if(!settle(tick))return false;aim(mc);stage=31;begin(tick);return false;}
        if(stage==31){if(!settle(tick))return false;capture("sideways-world");stage=32;return false;}
        if(stage==32){if(!captured("sideways-world"))return false;need(mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos()),"native pick must select rolled stair");crouch(mc,true);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,(BlockHitResult)mc.hitResult);stage=33;begin(tick);return false;}
        if(stage==33){if(!(mc.screen instanceof ShapeScreen gui))return false;need(state(gui).stairs.roll==1,"sideways roll must survive real menu reopen");option(gui,131,1);need(state(gui).stairs.roll==0,"previous orientation restores Normal");stage=25;begin(tick);return false;}
        if(stage==25){if(family==1&&(!settle(tick)||tile(mc).stairState().roll!=0))return false;option((ShapeScreen)mc.screen,100,0);crouch(mc,false);stage=26;begin(tick);return false;}
        if(stage==26){if(!settle(tick))return false;aim(mc);stage=27;begin(tick);return false;}
        if(stage==27){if(!settle(tick))return false;capture("configured");stage=28;return false;}
        if(stage==28){if(!captured("configured"))return false;family++;stage=0;started=tick;if(family==2){System.out.println("[vandorlabs][modern] shape-client PASS exactslab/stairGUI3/2, immediate Main/Side/inherit/face/dormant choices, wheel/drag, realstair1/8slider+left/rightroll andconfiguredworlds");return true;}}
        return false;
    }
}
