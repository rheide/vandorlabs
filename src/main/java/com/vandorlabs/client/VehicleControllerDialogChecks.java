package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.TileEntityVehicleController;
import com.vandorlabs.vehicle.*;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Render the controller's actual dialog without starting unrelated gameplay matrices. */
final class VehicleControllerDialogChecks {
    static void run(Minecraft mc,File output)throws Exception{
        GuiScreen previous=mc.currentScreen;net.minecraft.inventory.Container container=mc.player.openContainer;
        try{
            for(boolean complete:new boolean[]{false,true})for(int[] size:new int[][]{{320,240},{460,340}}){
                GuiScreen gui=create(complete);mc.displayGuiScreen(gui);gui.setWorldAndResolution(mc,size[0],size[1]);
                require((int)field(gui,"page")==0,"new controller dialog must open its leftmost tab");
                GuiTextField channels=(GuiTextField)field(gui,"channels");require(!channels.getVisible(),"receive channels leaked onto Vehicle page");
                for(int page=0;page<2;page++){
                    Method action=net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(gui.getClass(),"actionPerformed","func_146284_a",GuiButton.class);action.invoke(gui,new GuiButton(90+page,0,0,"Page"));
                    require(channels.getText().equals(((GuiTextField)field(gui,"channels")).getText()),"switching tabs lost channel draft");
                    channels=(GuiTextField)field(gui,"channels");require(channels.getVisible()==(page==1),"receive channels on wrong tab");
                    java.util.List<GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");
                    for(GuiButton a:buttons)if(a.visible){require(a.x>=0&&a.y>=0&&a.x+a.width<=size[0]&&a.y+a.height<=size[1],"control outside viewport: "+a.id);for(GuiButton b:buttons)if(a!=b&&b.visible)require(a.x>=b.x+b.width||b.x>=a.x+a.width||a.y>=b.y+b.height||b.y>=a.y+a.height,"overlapping controls: "+a.id+"/"+b.id);}
                    for(GuiButton b:buttons)if(b.id>=10&&b.id<=12)require(b.visible==(page==0)&&(!b.enabled||complete),"mode action visibility or incomplete-craft guard");
                    capture(mc,gui,new File(output,"shot_vehicle_controller_"+(complete?"ready":"incomplete")+"_"+page+"_"+size[0]+".png"));
                }
            }
        }finally{mc.displayGuiScreen(previous);mc.player.openContainer=container;}
        System.out.println("[vandorlabs][reprolab] vehicle-controller-dialog PASS compact320 regular460 centered two-tabs leftmost-open incomplete-mode-guards");
    }
    private static GuiScreen create(boolean complete)throws Exception{
        VehicleNetwork.Packet packet=new VehicleNetwork.Packet(VehicleNetwork.INSPECTION);packet.forward=1;packet.entity=-1;packet.sequence=complete?1:-1;packet.token=UUID.randomUUID();packet.origin=BlockPos.ORIGIN;packet.text="Connect a Pilot Seat and at least three wheel supports before assembling.";
        Class<?> incoming=Class.forName(VehicleClient.class.getName()+"$Incoming");Constructor<?> make=incoming.getDeclaredConstructor(VehicleNetwork.Packet.class);make.setAccessible(true);Object data=make.newInstance(packet);
        NBTTagCompound tag=new TileEntityVehicleController().writeToNBT(new NBTTagCompound());
        if(complete){VehicleStructure structure=VehicleStructure.display(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,Block.getBlockFromName("vandorlabs:vehicle_controller").getDefaultState(),tag),new VehicleStructure.Cell(BlockPos.ORIGIN.east(),ModBlocks.PILOT_SEAT.getDefaultState(),new NBTTagCompound())));set(data,"structure",structure);}else set(data,"controlData",tag);
        Class<?> type=Class.forName(VehicleClient.class.getName()+"$Preview");Constructor<?> dialog=type.getDeclaredConstructor(incoming);dialog.setAccessible(true);return (GuiScreen)dialog.newInstance(data);
    }
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static void set(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private static void capture(Minecraft mc,GuiScreen gui,File file)throws Exception{
        Framebuffer frame=new Framebuffer(gui.width,gui.height,true);GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,gui.width,gui.height,0,-1000,1000);GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.translate(0,0,-100);GlStateManager.clearColor(0,0,0,1);GlStateManager.clear(16640);GlStateManager.disableDepth();GlStateManager.disableLighting();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);gui.drawScreen(-1,-1,0);org.lwjgl.opengl.GL11.glFinish();ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(gui.width,gui.height,frame),"png",file);}finally{GlStateManager.popMatrix();GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static void require(boolean yes,String message){if(!yes)throw new IllegalStateException("Vehicle Controller dialog: "+message);}
    private VehicleControllerDialogChecks(){}
}
