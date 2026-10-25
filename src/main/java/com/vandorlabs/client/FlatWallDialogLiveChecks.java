package com.vandorlabs.client;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;

/** Actual flat-wall dialogs, including settings packets and native GUI-scale changes. */
final class FlatWallDialogLiveChecks {
    private static final BlockPos PLAIN=new BlockPos(20,5,20),FULL=new BlockPos(24,5,20);
    private static int ticks,stage,wait;
    private static int captures;
    private static boolean prepared;
    static boolean tick(Minecraft mc,File output)throws Exception {
        if(++ticks>1600)throw new IllegalStateException("flat wall dialog timeout stage="+stage);
        mc.gameSettings.pauseOnLostFocus=false;
        if(ticks==1){mc.displayGuiScreen(null);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.gameSettings.renderDistanceChunks=2;mc.gameSettings.hideGUI=false;wait=40;return false;}
        if(wait-->0)return false;
        switch(stage++) {
            case 0:
                if(!prepared){
                    server(mc,()->{EntityPlayerMP player=player(mc);player.setGameType(net.minecraft.world.GameType.CREATIVE);for(BlockPos p:new BlockPos[]{PLAIN,FULL}){player.world.setBlockState(p.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),3);Block block=Block.getBlockFromName(p.equals(PLAIN)?"vandorlabs:programmable_wall":"vandorlabs:programmable_porthole_block");require(block!=net.minecraft.init.Blocks.AIR,"fixture registry");player.world.setBlockState(p,block.getDefaultState(),3);}player.capabilities.allowFlying=true;player.capabilities.isFlying=true;player.sendPlayerAbilities();player.connection.setPlayerLocation(20.5,5,18.5,0,10);});
                    mc.player.capabilities.allowFlying=true;mc.player.capabilities.isFlying=true;mc.player.setPositionAndRotation(20.5,5,18.5,0,10);prepared=true;stage--;wait=40;break;
                }
                require(mc.world.getTileEntity(PLAIN) instanceof TileEntityAnimatedScreenSelector,"fixture chunk and tile arrived before opening");open(mc,PLAIN);scale(mc,3);wait=20;break;
            case 1: check(mc,false);save(mc,output,"plain-main-scale3");scale(mc,2);wait=12;break;
            case 2: check(mc,false);save(mc,output,"plain-main-scale2");click(mc,103,1);require(tile(mc,PLAIN).getMainTextureLayout()==2,"plain right click selects previous layout");wait=10;break;
            case 3: same(mc,PLAIN,2,-1,-1,1,0);click(mc,103,0);wait=10;break;
            case 4: same(mc,PLAIN,0,-1,-1,1,0);open(mc,FULL);scale(mc,3);wait=20;break;
            case 5: check(mc,true);save(mc,output,"full-main-scale3");click(mc,104,1);require(tile(mc,FULL).getPortholeShape()==3,"full right click selects Round");wait=10;break;
            case 6: require(serverInt(mc,FULL,0)==3,"full shape packet");click(mc,104,0);click(mc,103,1);wait=10;break;
            case 7: same(mc,FULL,2,-1,-1,1,0);click(mc,103,0);scale(mc,2);wait=12;break;
            case 8: check(mc,true);save(mc,output,"full-main-scale2");scale(mc,3);click(mc,127,0);wait=12;break;
            case 9: gui(mc).choose(1);wait=10;break;
            case 10: same(mc,FULL,0,1,-1,1,0);save(mc,output,"full-side-scale3");click(mc,128,0);wait=10;break;
            case 11: same(mc,FULL,0,-1,-1,1,0);require(!button(mc,128).enabled,"default side reset acknowledgement");click(mc,129,0);wait=12;break;
            case 12: gui(mc).choose(0x10000000+18);click(mc,113,1);wait=10;break;
            case 13: same(mc,FULL,0,-1,-1,0,0);require(tile(mc,FULL).getGlassShade()==18&&serverInt(mc,FULL,2)==18,"stock Black choice server acknowledgement");save(mc,output,"full-stock-glass-scale3");gui(mc).choose(0);wait=10;break;
            case 14: same(mc,FULL,0,-1,0,0,0);click(mc,115,1);wait=10;break;
            case 15: require(serverInt(mc,FULL,1)==0,"custom glass previous layout packet");save(mc,output,"full-custom-glass-scale3");scale(mc,2);wait=12;break;
            case 16: check(mc,true);save(mc,output,"full-custom-glass-scale2");click(mc,100,1);require(mc.currentScreen instanceof GuiProgrammableWall,"right Done must not close");click(mc,100,0);wait=10;break;
            case 17: require(!(mc.currentScreen instanceof GuiProgrammableWall),"left Done closes");same(mc,FULL,0,-1,0,0,0);require(captures==8,"capture count");System.out.println("[vandorlabs] flat-wall-dialog-live PASS captures=8 plain/full GUI3/2 tabs previous autosend side-reset stock/custom server-ack Done");return true;
            default:throw new IllegalStateException("flat wall dialog stage");
        }
        return false;
    }
    private static void check(Minecraft mc,boolean porthole){require(mc.currentScreen instanceof GuiProgrammableWall,"real wall GUI");ProgrammableDialogRuntimeChecks.check(mc);require(buttonOrNull(mc,120)!=null==porthole,"profile tabs");}
    private static GuiProgrammableWall gui(Minecraft mc){require(mc.currentScreen instanceof GuiProgrammableWall,"wall GUI vanished");return (GuiProgrammableWall)mc.currentScreen;}
    private static TileEntityAnimatedScreenSelector tile(Minecraft mc,BlockPos pos){return (TileEntityAnimatedScreenSelector)mc.world.getTileEntity(pos);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static void server(Minecraft mc,Runnable task)throws Exception{mc.getIntegratedServer().addScheduledTask(task).get();}
    private static void open(Minecraft mc,BlockPos pos)throws Exception {
        mc.displayGuiScreen(null);mc.player.capabilities.allowFlying=true;mc.player.capabilities.isFlying=true;mc.player.setPositionAndRotation(pos.getX()+.5,pos.getY(),pos.getZ()-1.5,0,10);
        server(mc,()->{EntityPlayerMP p=player(mc);p.closeScreen();p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.motionY=0;p.sendPlayerAbilities();p.connection.setPlayerLocation(pos.getX()+.5,pos.getY(),pos.getZ()-1.5,0,10);require(p.getDistanceSq(pos)<=64,"fixture authorized reach");p.openGui(VandorLabs.instance,GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR,p.world,pos.getX(),pos.getY(),pos.getZ());});
    }
    private static void scale(Minecraft mc,int scale){mc.gameSettings.guiScale=scale;mc.resize(mc.displayWidth,mc.displayHeight);}
    private static GuiButton buttonOrNull(Minecraft mc,int id){List<GuiButton> list=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui(mc),"buttonList","field_146292_n");for(GuiButton b:list)if(b.id==id)return b;return null;}
    private static GuiButton button(Minecraft mc,int id){GuiButton b=buttonOrNull(mc,id);require(b!=null,"missing button "+id);return b;}
    private static void click(Minecraft mc,int id,int direction)throws Exception {GuiButton b=button(mc,id);require(b.enabled&&b.visible,"inactive button "+id);Method method;try{method=GuiScreen.class.getDeclaredMethod("mouseClicked",int.class,int.class,int.class);}catch(NoSuchMethodException e){method=GuiScreen.class.getDeclaredMethod("func_73864_a",int.class,int.class,int.class);}method.setAccessible(true);method.invoke(gui(mc),b.x+b.width/2,b.y+b.height/2,direction);}
    private static int serverInt(Minecraft mc,BlockPos pos,int kind)throws Exception{final int[] value=new int[1];server(mc,()->{TileEntityAnimatedScreenSelector t=(TileEntityAnimatedScreenSelector)player(mc).world.getTileEntity(pos);value[0]=kind==0?t.getPortholeShape():kind==1?t.getGlassTextureLayout():t.getGlassShade();});return value[0];}
    private static void same(Minecraft mc,BlockPos pos,int layout,int side,int material,int tint,int shape)throws Exception{TileEntityAnimatedScreenSelector t=tile(mc,pos);require(t.getMainTextureLayout()==layout&&t.getSideTexture()==side&&t.getGlassMaterial()==material&&t.getGlassTint()==tint&&t.getPortholeShape()==shape,"client settings");server(mc,()->{TileEntityAnimatedScreenSelector s=(TileEntityAnimatedScreenSelector)player(mc).world.getTileEntity(pos);require(s.getMainTextureLayout()==layout&&s.getSideTexture()==side&&s.getGlassMaterial()==material&&s.getGlassTint()==tint&&s.getPortholeShape()==shape,"server settings acknowledgement");});}
    private static void save(Minecraft mc,File output,String name)throws Exception{ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"flat-wall-"+name+".png"));captures++;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Flat wall dialog: "+message);}
}
