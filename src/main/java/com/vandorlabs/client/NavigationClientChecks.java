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
    private static int stage,ticks,expectedSpeed,machineScreen,machineKeyboard;private static String pending;private static boolean captured,machineDialogRequested;private static File output;
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
            ProgrammableDialogLayout panel=(ProgrammableDialogLayout)field(gui,"panel");int before=(Integer)field(gui,"speed");assertDataOnlyControls(gui);
            gui.mouseClicked(panel.controlsX+8,panel.top+gui.controlY(4)+6,1);require((Integer)field(gui,"speed")==Math.floorMod(before-1,3),"right-click previous speed");
            gui.mouseClicked(panel.controlsX+8,panel.top+gui.controlY(4)+6,0);expectedSpeed=before;require((Integer)field(gui,"speed")==before,"left-click next speed");
            gui.mouseClicked(panel.controlsX+8,panel.top+gui.controlY(1)+6,0);
            // A selected native option must survive scrolling and scrollbar dragging.
            int choice=picker.selected();picker.wheel(panel.listX+2,panel.top+35,-120);picker.click(panel.listX+panel.listWidth+2,panel.top+36,0);require(picker.drag(panel.top+100),"shared scrollbar drag");picker.release();require(picker.selected()==choice,"scrolling preserves selection");
            stage=4;ticks=0;return false;
        }
        if(stage==4){
            if(ticks<15)return false;
            mc.getIntegratedServer().addScheduledTask(()->{TileEntityNavigationTable tile=root(world(mc));NavigationScreenSettings s=tile.screenSettings();require(s.redstone&&!s.security&&s.speed==expectedSpeed,"authorized dialog settings round trip");require(!tile.screenOn(),"dialog enables actual server gates");}).get();
            pending="shot_navigation_display_dialog.png";captured=false;stage=5;return false;
        }
        if(stage==5&&captured){
            mc.player.closeScreen();System.out.println("[vandorlabs] navigation-display-client PASS rendered opposing rows native reference complete artwork picker previous/next speed wheel drag authorized settings round trip");
            mc.getIntegratedServer().addScheduledTask(()->{
                BlockPos at=POS.east(9);FrameMachineDesign spec=FrameMachineDesign.get("longburn_drive");BridgeMachineChecks.frame(world(mc),at,spec,0);BridgeMachineChecks.frame(world(mc),at,spec,2);TileEntityConstructor tile=(TileEntityConstructor)world(mc).getTileEntity(at);require(tile.assemble(),"machine dialog fixture");EntityPlayerMP p=player(mc);p.connection.setPlayerLocation(at.getX()+.5,at.getY()+1,at.getZ()-2,0,0);
            }).get();stage=6;ticks=0;captured=false;return false;
        }
        if(stage==6){
            if(!machineDialogRequested){
                net.minecraft.tileentity.TileEntity raw=mc.world.getTileEntity(POS.east(9));if(ticks<10||!(raw instanceof TileEntityConstructor)||!((TileEntityConstructor)raw).formed())return false;
                mc.getIntegratedServer().addScheduledTask(()->{BlockPos at=POS.east(9);player(mc).openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_TOKEN_SLOT,world(mc),at.getX(),at.getY(),at.getZ());}).get();machineDialogRequested=true;return false;
            }
            if(!(mc.currentScreen instanceof GuiNavigationTable)||!(field(mc.currentScreen,"tile") instanceof TileEntityConstructor))return false;
            GuiNavigationTable gui=(GuiNavigationTable)mc.currentScreen;require(((int[])field(gui,"choices")).length==2,"screen and keyboard tabs");machineScreen=NavigationRuntimeChecks.artwork();machineKeyboard=-1;
            for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(ScreenTextureList.artwork(i,true)){machineKeyboard=ScreenHousingTextures.choiceAt(i);break;}require(machineKeyboard>=0,"keyboard artwork exists");
            paint(gui,machineScreen);ProgrammableDialogLayout panel=(ProgrammableDialogLayout)field(gui,"panel");GuiButton keyboard=button(gui,21);gui.mouseClicked(keyboard.x+keyboard.width/2,keyboard.y+keyboard.height/2,0);paint(gui,machineKeyboard);assertDataOnlyControls(gui);
            gui.mouseClicked(panel.controlsX+6,panel.top+gui.controlY(1)+6,0);stage=7;ticks=0;return false;
        }
        if(stage==7){
            if(ticks<15)return false;
            mc.getIntegratedServer().addScheduledTask(()->{
                TileEntityConstructor tile=(TileEntityConstructor)world(mc).getTileEntity(POS.east(9));require(tile.surfaceChoice(0)==machineScreen&&tile.surfaceChoice(1)==machineKeyboard,"independent surface packet round trips");require(!tile.screenSettings().security&&tile.screenSettings().redstone&&tile.mount()!=null&&!tile.screenOn(),"redstone-only machine gate and always-visible mount");
                EntityPlayerMP p=player(mc);MachineMountSurface mount=tile.mount();Vec3d eye=mount.center.add(mount.normal.scale(2.5));Vec3d delta=mount.center.subtract(eye);p.capabilities.isCreativeMode=false;p.setSneaking(false);p.connection.setPlayerLocation(eye.x,eye.y-p.getEyeHeight(),eye.z,(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),0);p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP,2,4));
                BlockPos clicked=new BlockPos(mount.center);net.minecraft.block.state.IBlockState state=world(mc).getBlockState(clicked);require(state.getBlock() instanceof BlockConstructorPart,"reader click reaches reserved module");require(state.getBlock().onBlockActivated(world(mc),clicked,state,p,EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,0),"real multiblock reader activation");require(p.getHeldItemMainhand().getCount()==1&&!tile.isEmpty(),"real retained chip insertion consumes one item");world(mc).setBlockState(tile.getPos().down(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);require(tile.screenOn()&&tile.getOutputLevel()==0,"real redstone activates independently of retained chip without output");
            }).get();pending="shot_machine_display_dialog.png";captured=false;stage=8;ticks=0;return false;
        }
        if(stage==8&&captured&&ticks>=30){
            mc.getIntegratedServer().addScheduledTask(()->{TileEntityConstructor tile=(TileEntityConstructor)world(mc).getTileEntity(POS.east(9));require(!tile.isEmpty()&&tile.screenOn()&&tile.behavior==0,"inserted chip remains after the animation deadline");}).get();
            mc.player.closeScreen();System.out.println("[vandorlabs] machine-display-client PASS independent screen/keyboard tabs complete artwork choices authorized packets reserved-part insertion retained chip redstone gates zero output");return true;
        }
        return false;
    }
    private static GuiButton button(GuiNavigationTable gui,int id){
        java.util.List<GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");for(GuiButton button:buttons)if(button.id==id)return button;throw new IllegalStateException("Missing display control "+id);
    }
    private static void assertDataOnlyControls(GuiNavigationTable gui){
        java.util.List<GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");for(GuiButton button:buttons)if(button.visible){String label=button.displayString.toLowerCase(java.util.Locale.ROOT);require(button.id!=2&&!label.contains("security")&&!label.contains("company")&&!label.contains("return after"),"integrated display has no Security/company/return controls");}
    }
    private static void paint(GuiNavigationTable gui,int choice)throws Exception{
        HousingTextureList picker=(HousingTextureList)field(gui,"textures");picker.setSelected(choice);java.util.List<?> rows=(java.util.List<?>)field(picker,"rows");int scroll=(Integer)field(picker,"scroll"),row=rows.indexOf(choice)-scroll;require(row>=0,"selected artwork is visible");ProgrammableDialogLayout panel=(ProgrammableDialogLayout)field(gui,"panel");gui.mouseClicked(panel.listX+30,panel.top+52+row*HousingTextureList.ROW_HEIGHT+10,0);
    }
    static void render(Minecraft mc){if(pending==null)return;try{javax.imageio.ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,pending));pending=null;captured=true;}catch(java.io.IOException e){throw new IllegalStateException(e);}}
}
