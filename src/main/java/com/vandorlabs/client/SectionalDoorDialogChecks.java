package com.vandorlabs.client;

import com.vandorlabs.garage.*;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import org.lwjgl.opengl.GL11;

final class SectionalDoorDialogChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("sectional dialog: "+why);}
    private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static GuiButton button(GuiGarageDoor gui,int id)throws Exception{@SuppressWarnings("unchecked") List<GuiButton> buttons=(List<GuiButton>)net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.client.gui.GuiScreen.class,"buttonList","field_146292_n").get(gui);for(GuiButton b:buttons)if(b.id==id)return b;throw new IllegalStateException("missing sectional button "+id);}
    private static void click(GuiGarageDoor gui,int id,int mouse)throws Exception{GuiButton b=button(gui,id);gui.mouseClicked(b.x+b.width/2,b.y+b.height/2,mouse);}
    static void run(Minecraft mc,GuiGarageDoor gui,File output)throws Exception{
        int height=gui.getYSize(),width=gui.getXSize(),left=gui.getGuiLeft(),top=gui.getGuiTop();click(gui,6,1);require(((GarageDoorSettings)field(gui,"settings")).direction==3,"right click previous direction");click(gui,6,0);click(gui,5,1);require((Integer)field(gui,"ticks")==5,"right click previous speed");click(gui,5,0);save(mc,gui,output,"mechanism");
        click(gui,92,0);HousingTextureList list=(HousingTextureList)field(gui,"textures");@SuppressWarnings("unchecked") Map<String,?> groups=(Map<String,?>)field(list,"groups");require(groups.containsKey("Frame")||groups.containsKey("Frames"),"Frame material category");require(field(list,"customConsumer")!=null,"shared Custom inventory option");click(gui,9,0);require(((GarageDoorSettings)field(gui,"settings")).window,"window segment selector");click(gui,10,1);require(((GarageDoorSettings)field(gui,"settings")).shape==3,"previous porthole style");click(gui,11,0);
        list=(HousingTextureList)field(gui,"textures");list.customInventory();InventorySamplePicker picker=(InventorySamplePicker)field(list,"inventoryPicker");gui.mouseClicked(picker.x+2*18+8,picker.y+16+8,0);require((Integer)field(gui,"texture")==CustomBlockMaterials.choice(mc.player.inventory.getStackInSlot(2)),"real inventory custom texture sample");save(mc,gui,output,"door_window_custom");
        for(int page:new int[]{93,91,90,92}){click(gui,page,0);require(gui.getYSize()==height&&gui.getXSize()==width&&gui.getGuiLeft()==left&&gui.getGuiTop()==top,"tab changed dialog bounds");save(mc,gui,output,page==93?"glass":page==91?"redstone":page==90?"mechanism":"door");}
        System.out.println("[vandorlabs][reprolab] sectional-dialog PASS fixed bounds four tabs Frame/Custom inventory sample window styles join right-click direction/speed shared widgets");
    }
    private static void save(Minecraft mc,GuiGarageDoor gui,File output,String name)throws Exception{
        Framebuffer frame=new Framebuffer(gui.width,gui.height,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,gui.width,gui.height,0,-1000,1000);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.enableTexture2D();GlStateManager.disableLighting();GlStateManager.disableDepth();gui.drawScreen(-1,-1,0);GL11.glFinish();javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(gui.width,gui.height,frame),"png",new File(output,"shot_sectional_dialog_"+name+".png"));}
        finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private SectionalDoorDialogChecks(){}
}
