package com.vandorlabs.neoforge.wall;

import com.vandorlabs.neoforge.ModernClientRuntimeChecks;
import com.vandorlabs.neoforge.client.NativeMaterialPicker;
import com.vandorlabs.ui.ProgrammableDialogLayout;
import com.vandorlabs.ui.WallDialog;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Real flat-wall menus: every edit must receive a new authoritative server settings snapshot. */
public final class WallClientChecks {
    private static final String[] KINDS={"plain","porthole","full"};
    private static int family,stage,started,route,originalSlot=-1,originalScale;
    private static boolean originalFlight;
    private static long stableFrame,receipt;
    private WallClientChecks(){}
    public static String[] captures(){List<String> out=new ArrayList<>();for(String kind:KINDS){for(String suffix:new String[]{"world","main-scale3","main-scale2","configured"})out.add("modern-wall-"+kind+"-"+suffix+".png");if(!kind.equals("plain"))for(String suffix:new String[]{"side-default-scale3","side-scale3","side-scale2","stock-glass-scale3","stock-glass-scale2","custom-glass-scale3","custom-glass-scale2"})out.add("modern-wall-"+kind+"-"+suffix+".png");}return out.toArray(new String[0]);}
    private static BlockPos pos(){return new BlockPos(320+family*4,80,8);}
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Wall client "+KINDS[family]+" stage="+stage+": "+message);}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static WallEntity tile(Minecraft mc){return mc.level.getBlockEntity(pos()) instanceof WallEntity tile?tile:null;}
    private static WallDialog.State state(WallScreen gui){return (WallDialog.State)field(gui,"state");}
    private static ProgrammableDialogLayout panel(WallScreen gui){return (ProgrammableDialogLayout)field(gui,"panel");}
    private static NativeMaterialPicker picker(WallScreen gui){return (NativeMaterialPicker)field(gui,"picker");}
    private static void capture(String suffix){ModernClientRuntimeChecks.capture("modern-wall-"+KINDS[family]+"-"+suffix+".png");}
    private static boolean captured(String suffix){return ModernClientRuntimeChecks.captured("modern-wall-"+KINDS[family]+"-"+suffix+".png");}
    private static void begin(int tick){started=tick;stableFrame=ModernClientRuntimeChecks.renderedFrames();}
    private static boolean settle(int tick){return tick-started>=10&&ModernClientRuntimeChecks.renderedFrames()-stableFrame>=3;}
    private static void expectEcho(Minecraft mc){receipt=tile(mc).receivedSettingsRevision();}
    private static boolean acknowledged(Minecraft mc,int tick,Predicate<WallDialog.State> expected){return settle(tick)&&tile(mc).receivedSettingsRevision()>receipt&&expected.test(tile(mc).wallSettings());}
    private static void crouch(Minecraft mc,boolean on){mc.options.keyShift.setDown(on);mc.player.setShiftKeyDown(on);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,on?ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));}
    private static void aim(Minecraft mc){BlockPos p=pos();var box=mc.level.getBlockState(p).getShape(mc.level,p).bounds();Vec3 d=new Vec3(p.getX()+(box.minX+box.maxX)/2,p.getY()+(box.minY+box.maxY)/2,p.getZ()+(box.minZ+box.maxZ)/2).subtract(mc.player.getEyePosition());float yaw=(float)Math.toDegrees(Math.atan2(d.z,d.x))-90,pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(),mc.player.getY(),mc.player.getZ(),yaw,pitch,!mc.player.getAbilities().flying));}
    private static WallDialog.Control control(WallScreen gui,int id){return WallDialog.controls(panel(gui),gui.getMenu().tile.profile(),state(gui),"Done").stream().filter(c->c.bounds.id==id).findFirst().orElseThrow();}
    private static void option(WallScreen gui,int id,int button){var c=control(gui,id);need(c.enabled,"disabled option "+id);var b=c.bounds;gui.mouseClicked(b.x+b.width/2D,b.y+b.height/2D,button);}
    private static void choose(WallScreen gui,int choice){var p=panel(gui);var picker=picker(gui);picker.setSelected(choice);int row=picker.rows().indexOf(choice)-picker.scroll();need(row>=0&&row<WallDialog.rows(p,gui.getMenu().tile.profile()),"material visible after centering");gui.mouseClicked(p.listX+12,WallDialog.listY(p,gui.getMenu().tile.profile())+row*22+11,0);}
    private static void scroll(WallScreen gui){var p=panel(gui);var picker=picker(gui);int old=picker.scroll(),y=WallDialog.listY(p,gui.getMenu().tile.profile()),height=WallDialog.rows(p,gui.getMenu().tile.profile())*22;gui.mouseScrolled(p.listX+12,y+11,0,-1);need(picker.scroll()>old,"wheel scroll");int wheel=picker.scroll();gui.mouseClicked(p.listX+p.listWidth+3,y+height-2,0);gui.mouseDragged(p.listX+p.listWidth+3,y+height+80,0,0,80);gui.mouseReleased(p.listX+p.listWidth+3,y+height+80,0);need(picker.scroll()>wheel,"scrollbar drag");}
    private static boolean fresh(WallDialog.State s){return s.main==0&&s.side==-1&&s.layout==0&&s.appearance.shade==2&&s.appearance.tint==1&&s.appearance.material==-1&&s.appearance.framed&&!s.appearance.stretch&&s.appearance.textureJoin&&s.appearance.shape==0&&s.appearance.textureLayout()==1&&s.appearance.join==(family!=0);}
    private static boolean configured(WallDialog.State s){return s.main==1&&s.layout==1&&(family==0||s.side==2&&s.appearance.shape==3&&s.appearance.join&&s.appearance.material==0&&s.appearance.tint==0&&s.appearance.textureLayout()==0);}
    public static boolean tick(Minecraft mc,int tick){
        if(mc.player==null||mc.level==null||mc.gameMode==null)return false;if(family==3)return true;
        if(stage==0){
            if(originalSlot<0){originalSlot=mc.player.getInventory().selected;originalScale=mc.options.guiScale().get();originalFlight=mc.player.getAbilities().flying;int selected=-1;for(int slot=0;slot<9;slot++)if(mc.player.getInventory().getItem(slot).is(com.vandorlabs.neoforge.VandorLabsModern.CONFIGURIZER.get())){selected=slot;break;}if(selected<0)for(int slot=0;slot<9;slot++)if(mc.player.getInventory().getItem(slot).isEmpty()){selected=slot;break;}need(selected>=0,"empty hand or Configurizer fixture slot");mc.player.getInventory().selected=selected;mc.player.connection.send(new ServerboundSetCarriedItemPacket(selected));}
            mc.player.closeContainer();crouch(mc,false);need(mc.player.getAbilities().mayfly,"creative flight fixture");mc.player.setDeltaMovement(Vec3.ZERO);mc.player.setOnGround(false);if(!mc.player.getAbilities().flying){mc.player.getAbilities().flying=true;mc.player.onUpdateAbilities();}if(tick-started<5)return false;
            Vec3 destination=switch(route){case 0->new Vec3(mc.player.getX(),82,mc.player.getZ());case 1->new Vec3(mc.player.getX(),82,-3);case 2->new Vec3(pos().getX()+.5,82,-3);case 3->new Vec3(pos().getX()+.5,82,5);default->new Vec3(pos().getX()+.5,80,5);};Vec3 delta=destination.subtract(mc.player.position()),next=delta.length()>4?mc.player.position().add(delta.normalize().scale(4)):destination;mc.player.setPos(next);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(next.x,next.y,next.z,false));started=tick;if(delta.length()>4)return false;if(++route<5)return false;stage=1;begin(tick);return false;
        }
        need(tick-started<600,"timeout awaiting server echo or actual rendered capture");
        switch(stage){
            case 1:if(!settle(tick)||tile(mc)==null)return false;need(fresh(tile(mc).wallSettings()),"fresh mainStretch/glassTile/profile defaults");if(family==0){if(!WallModelChecks.ready(mc))return false;WallModelChecks.run(mc);}aim(mc);stage=2;begin(tick);break;
            case 2:if(!settle(tick))return false;capture("world");stage=3;break;
            case 3:if(!captured("world"))return false;need(mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos()),"native wall ray target");crouch(mc,true);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,(BlockHitResult)mc.hitResult);stage=4;begin(tick);break;
            case 4:if(!(mc.screen instanceof WallScreen))return false;mc.options.guiScale().set(3);mc.resizeDisplay();stage=5;begin(tick);break;
            case 5:if(!settle(tick))return false;capture("main-scale3");stage=6;break;
            case 6:if(!captured("main-scale3"))return false;mc.options.guiScale().set(2);mc.resizeDisplay();stage=7;begin(tick);break;
            case 7:if(!settle(tick))return false;capture("main-scale2");stage=8;break;
            case 8:{if(!captured("main-scale2"))return false;var gui=(WallScreen)mc.screen;expectEcho(mc);option(gui,103,1);need(state(gui).layout==2,"Stretch previous is Fit");option(gui,103,0);need(state(gui).layout==0,"Fit next is Stretch");if(family!=0){option(gui,104,1);need(state(gui).appearance.shape==3,"Hexagon previous is Round");option(gui,102,1);need(!state(gui).appearance.join,"reverse Join toggles off");option(gui,104,0);option(gui,102,0);}stage=9;begin(tick);break;}
            case 9:{if(!acknowledged(mc,tick,WallClientChecks::fresh))return false;var gui=(WallScreen)mc.screen;if(family==0){scroll(gui);expectEcho(mc);choose(gui,1);option(gui,103,0);stage=30;begin(tick);break;}mc.options.guiScale().set(3);mc.resizeDisplay();option(gui,127,0);stage=10;begin(tick);break;}
            case 10:if(!settle(tick))return false;need(!control((WallScreen)mc.screen,128).enabled,"unselected inherited side reset disabled");capture("side-default-scale3");stage=11;break;
            case 11:if(!captured("side-default-scale3"))return false;expectEcho(mc);choose((WallScreen)mc.screen,1);stage=12;begin(tick);break;
            case 12:if(!acknowledged(mc,tick,s->s.side==1&&s.main==0&&s.layout==0))return false;capture("side-scale3");stage=13;break;
            case 13:if(!captured("side-scale3"))return false;mc.options.guiScale().set(2);mc.resizeDisplay();stage=14;begin(tick);break;
            case 14:if(!settle(tick))return false;capture("side-scale2");stage=15;break;
            case 15:if(!captured("side-scale2"))return false;expectEcho(mc);option((WallScreen)mc.screen,128,0);stage=16;begin(tick);break;
            case 16:if(!acknowledged(mc,tick,s->s.side==-1))return false;need(!control((WallScreen)mc.screen,128).enabled,"server-acknowledged default side reset");option((WallScreen)mc.screen,129,0);mc.options.guiScale().set(3);mc.resizeDisplay();stage=17;begin(tick);break;
            case 17:if(!settle(tick))return false;expectEcho(mc);choose((WallScreen)mc.screen,WallDialog.GLASS_BASE+18);option((WallScreen)mc.screen,113,1);need(state((WallScreen)mc.screen).appearance.tint==0,"Normal tint previous is Mild");stage=18;begin(tick);break;
            case 18:if(!acknowledged(mc,tick,s->s.appearance.material==-1&&s.appearance.shade==18&&s.appearance.tint==0))return false;capture("stock-glass-scale3");stage=19;break;
            case 19:if(!captured("stock-glass-scale3"))return false;mc.options.guiScale().set(2);mc.resizeDisplay();stage=20;begin(tick);break;
            case 20:if(!settle(tick))return false;capture("stock-glass-scale2");stage=21;break;
            case 21:if(!captured("stock-glass-scale2"))return false;expectEcho(mc);choose((WallScreen)mc.screen,0);option((WallScreen)mc.screen,115,1);need(state((WallScreen)mc.screen).appearance.textureLayout()==0,"glass Tile previous is Stretch");stage=22;begin(tick);break;
            case 22:if(!acknowledged(mc,tick,s->s.appearance.material==0&&s.appearance.tint==0&&s.appearance.textureLayout()==0))return false;capture("custom-glass-scale2");stage=23;break;
            case 23:if(!captured("custom-glass-scale2"))return false;mc.options.guiScale().set(3);mc.resizeDisplay();stage=24;begin(tick);break;
            case 24:if(!settle(tick))return false;capture("custom-glass-scale3");stage=25;break;
            case 25:{if(!captured("custom-glass-scale3"))return false;var gui=(WallScreen)mc.screen;scroll(gui);expectEcho(mc);option(gui,120,0);choose(gui,1);option(gui,127,0);choose(gui,2);option(gui,120,0);option(gui,104,1);option(gui,103,0);stage=30;begin(tick);break;}
            case 30:if(!acknowledged(mc,tick,WallClientChecks::configured))return false;var gui=(WallScreen)mc.screen;option(gui,100,1);need(mc.screen==gui,"right Done inert");option(gui,100,0);crouch(mc,false);stage=31;begin(tick);break;
            case 31:if(!settle(tick))return false;need(!(mc.screen instanceof WallScreen),"left Done closes");aim(mc);stage=32;begin(tick);break;
            case 32:if(!settle(tick))return false;need(configured(tile(mc).wallSettings()),"configured state survives close");capture("configured");stage=33;break;
            case 33:if(!captured("configured"))return false;family++;stage=0;route=0;started=tick;if(family==3){mc.player.getInventory().selected=originalSlot;mc.player.connection.send(new ServerboundSetCarriedItemPacket(originalSlot));mc.options.guiScale().set(originalScale);mc.resizeDisplay();if(originalFlight){mc.player.setDeltaMovement(Vec3.ZERO);mc.player.setOnGround(false);}if(mc.player.getAbilities().flying!=originalFlight){mc.player.getAbilities().flying=originalFlight;mc.player.onUpdateAbilities();}System.out.println("[vandorlabs][modern] wall-client PASS 3profiles GUI3/2 Main/Side/stock/customGlass previous/reset/scroll/drag/Done with actual authoritative packet receipts");return true;}break;
            default:throw new IllegalStateException("Wall client stage "+stage);
        }
        return false;
    }
}
