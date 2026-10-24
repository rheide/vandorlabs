package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.navigation.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.io.File;
import java.util.*;

/** Rendered opposing rows and actual authorized configuration packet round trips. */
final class NavigationClientChecks {
    private static final BlockPos POS=new BlockPos(24,80,24);
    private static int stage,ticks,expectedSpeed;private static String pending;private static boolean captured;private static File output;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Navigation client: "+why);}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUsername(mc.player.getName());}
    private static TileEntityNavigationTable root(WorldServer world){return ((TileEntityNavigationTable)world.getTileEntity(POS)).reader();}
    private static Object field(Object value,String name)throws Exception{java.lang.reflect.Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    static boolean tick(Minecraft mc,File directory)throws Exception{
        output=directory;
        if(stage==0){
            mc.getIntegratedServer().addScheduledTask(()->{
                WorldServer world=world(mc);
                for(BlockPos p:BlockPos.getAllInBox(POS.add(-3,-1,-4),POS.add(10,2,7)))world.setBlockState(p,p.getY()==79?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
                for(String kind:new String[]{"flat","angled"}){
                    BlockNavigationTable block=(BlockNavigationTable)Block.getBlockFromName("vandorlabs:navigation_table_"+kind);BlockPos base=kind.equals("flat")?POS:POS.east(5);
                    for(int x=0;x<2;x++)for(int z=0;z<(kind.equals("flat")?2:1);z++)world.setBlockState(base.east(x).south(z),block.getDefaultState().withProperty(BlockVandorDirectional.FACING,z==0?EnumFacing.NORTH:EnumFacing.SOUTH),2);
                    NavigationAssemblies.rebuild(world,base);TileEntityNavigationTable tile=((TileEntityNavigationTable)world.getTileEntity(base)).reader();tile.configureDisplay(new NavigationScreenSettings(NavigationRuntimeChecks.artwork(),true,1,false,false),false,16,0,ChannelList.EMPTY);
                }
                // An unscaled standalone reference sits beside the two integrated mounts.
                Block reference=Block.getBlockFromName("vandorlabs:data_chip_slot");world.setBlockState(POS.west(2),reference.getDefaultState().withProperty(BlockVandorSwitch.FACING,EnumFacing.NORTH),2);world.setBlockState(POS.west(2).south(),Blocks.STONE.getDefaultState(),2);
                Vec3d target=new Vec3d(POS).addVector(2.8,.6,.7),eye=target.addVector(-4.6,4,-6);Vec3d delta=target.subtract(eye);
                EntityPlayerMP p=player(mc);p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();p.connection.setPlayerLocation(eye.x,eye.y-p.getEyeHeight(),eye.z,(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));p.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);p.motionX=p.motionY=p.motionZ=0;
            }).get();mc.displayGuiScreen(null);mc.gameSettings.hideGUI=true;mc.gameSettings.thirdPersonView=0;mc.setIngameFocus();stage=1;ticks=0;return false;
        }
        require(++ticks<400,"bounded client synchronization");
        if(stage==1){
            if(ticks<40||!(mc.world.getTileEntity(POS) instanceof TileEntityNavigationTable))return false;
            TileEntityNavigationTable tile=(TileEntityNavigationTable)mc.world.getTileEntity(POS);if(!tile.formed()||!tile.screenOn())return false;
            pending="shot_navigation_opposing_rows.png";captured=false;stage=2;return false;
        }
        if(stage==2){if(!captured)return false;mc.gameSettings.hideGUI=false;mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=player(mc);TileEntityNavigationTable tile=root(world(mc));p.setPosition(tile.getPos().getX()+.5,tile.getPos().getY()+1,tile.getPos().getZ()-2);p.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_TOKEN_SLOT,world(mc),tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ());}).get();stage=3;ticks=0;return false;}
        if(stage==3){
            if(!(mc.currentScreen instanceof GuiNavigationTable))return false;GuiNavigationTable gui=(GuiNavigationTable)mc.currentScreen;
            HousingTextureList picker=(HousingTextureList)field(gui,"textures");Map<?,?> options=(Map<?,?>)field(picker,"options");
            for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(ScreenHousingTextures.visible(i)&&(HousingTextureList.generalTexture(i)||ScreenTextureList.artwork(i,false)))require(options.containsKey(ScreenHousingTextures.choiceAt(i)),"complete block/screen artwork catalog");
            require(options.containsKey(HousingTextureList.NAVIGATION_BLACK),"black option");require(field(picker,"customConsumer")!=null,"shared custom block material picker");
            ProgrammableDialogLayout panel=(ProgrammableDialogLayout)field(gui,"panel");int before=(Integer)field(gui,"speed");require(panel.top+gui.filterY()+18<=panel.doneY-4,"company picker fits above Done at current GUI scale");
            gui.mouseClicked(panel.controlsX+8,panel.top+gui.controlY(4)+6,1);require((Integer)field(gui,"speed")==Math.floorMod(before-1,3),"right-click previous speed");
            gui.mouseClicked(panel.controlsX+8,panel.top+gui.controlY(4)+6,0);expectedSpeed=before;require((Integer)field(gui,"speed")==before,"left-click next speed");
            gui.mouseClicked(panel.controlsX+8,panel.top+gui.controlY(1)+6,0);gui.mouseClicked(panel.controlsX+8,panel.top+gui.controlY(2)+6,0);
            // A selected native option must survive scrolling and scrollbar dragging.
            int choice=picker.selected();picker.wheel(panel.listX+2,panel.top+35,-120);picker.click(panel.listX+panel.listWidth+2,panel.top+36,0);require(picker.drag(panel.top+100),"shared scrollbar drag");picker.release();require(picker.selected()==choice,"scrolling preserves selection");
            stage=4;ticks=0;return false;
        }
        if(stage==4){
            if(ticks<15)return false;
            mc.getIntegratedServer().addScheduledTask(()->{TileEntityNavigationTable tile=root(world(mc));NavigationScreenSettings s=tile.screenSettings();require(s.redstone&&s.security&&s.speed==expectedSpeed,"authorized dialog settings round trip");require(!tile.screenOn(),"dialog enables actual server gates");}).get();
            pending="shot_navigation_display_dialog.png";captured=false;stage=5;return false;
        }
        if(stage==5&&captured){mc.player.closeScreen();System.out.println("[vandorlabs] navigation-display-client PASS rendered opposing rows native reference complete artwork picker previous/next speed wheel drag authorized settings round trip");return true;}
        return false;
    }
    static void render(Minecraft mc){if(pending==null)return;try{javax.imageio.ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,pending));pending=null;captured=true;}catch(java.io.IOException e){throw new IllegalStateException(e);}}
}
