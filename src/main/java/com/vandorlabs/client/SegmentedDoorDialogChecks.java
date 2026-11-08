package com.vandorlabs.client;

import com.vandorlabs.segmented.*;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import org.lwjgl.opengl.GL11;

final class SegmentedDoorDialogChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("segmented dialog: "+why);}
    private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static GuiButton button(GuiSegmentedDoor gui,int id)throws Exception{@SuppressWarnings("unchecked") List<GuiButton> buttons=(List<GuiButton>)net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.client.gui.GuiScreen.class,"buttonList","field_146292_n").get(gui);for(GuiButton b:buttons)if(b.id==id)return b;throw new IllegalStateException("missing segmented button "+id);}
    private static void click(GuiSegmentedDoor gui,int id,int mouse)throws Exception{GuiButton b=button(gui,id);gui.mouseClicked(b.x+b.width/2,b.y+b.height/2,mouse);}
    static void run(Minecraft mc,GuiSegmentedDoor gui,File output)throws Exception{
        @SuppressWarnings("unchecked") List<GuiButton> controls=(List<GuiButton>)net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.client.gui.GuiScreen.class,"buttonList","field_146292_n").get(gui);for(GuiButton b:controls){require(b.id!=3&&b.id!=4&&b.id!=90,"no obsolete mechanism/open/close buttons");if(b.id<90){require(b.width<=154,"compact controls do not stretch vanilla button UVs");require(b.y+b.height<=gui.getGuiTop()+gui.getYSize(),"control within short dialog");}}
        HousingTextureList initial=(HousingTextureList)field(gui,"textures");require((Integer)field(initial,"x")==gui.getGuiLeft()+12&&button(gui,6).x>=(Integer)field(initial,"x")+(Integer)field(initial,"width")+12,"texture list left and controls right without overlap");
        for(GuiButton control:controls)require(control.id!=15&&control.id!=16,"artwork scope and reset removed; Copifier handles sharing");
        click(gui,17,0);require(((SegmentedDoorSettings)field(gui,"settings")).treadPixels==8,"Ramp-style minus halves tread size");click(gui,18,0);require(((SegmentedDoorSettings)field(gui,"settings")).treadPixels==16,"Ramp-style plus restores tread size");
        click(gui,14,1);require(((SegmentedDoorSettings)field(gui,"settings")).position==2,"right click previous placement");click(gui,14,0);
        int height=gui.getYSize(),width=gui.getXSize(),left=gui.getGuiLeft(),top=gui.getGuiTop();click(gui,6,1);require(((SegmentedDoorSettings)field(gui,"settings")).direction==3,"right click previous direction");click(gui,6,0);click(gui,5,1);require((Integer)field(gui,"ticks")==20,"right click previous speed");click(gui,5,0);save(mc,gui,output,"door");
        click(gui,92,0);HousingTextureList list=(HousingTextureList)field(gui,"textures");@SuppressWarnings("unchecked") Map<String,?> groups=(Map<String,?>)field(list,"groups");require(groups.containsKey("Frame")||groups.containsKey("Frames"),"Frame material category");require(field(list,"customConsumer")!=null,"shared Custom inventory option");click(gui,9,0);require(((SegmentedDoorSettings)field(gui,"settings")).window,"window segment selector");click(gui,10,1);require(((SegmentedDoorSettings)field(gui,"settings")).shape==3,"previous porthole style");click(gui,11,0);
        list=(HousingTextureList)field(gui,"textures");list.customInventory();InventorySamplePicker picker=(InventorySamplePicker)field(list,"inventoryPicker");gui.mouseClicked(picker.x+2*18+8,picker.y+16+8,0);require((Integer)field(gui,"cellTexture")==CustomBlockMaterials.choice(mc.player.inventory.getStackInSlot(2)),"real inventory custom texture sample");save(mc,gui,output,"door_window_custom");
        for(int page:new int[]{93,91,92}){click(gui,page,0);require(gui.getYSize()==height&&gui.getXSize()==width&&gui.getGuiLeft()==left&&gui.getGuiTop()==top,"tab changed dialog bounds");save(mc,gui,output,page==93?"glass":page==91?"redstone":page==90?"door":"door");}
        System.out.println("[vandorlabs][reprolab] segmented-dialog PASS fixed bounds three tabs Frame/Custom inventory sample window styles join right-click direction/speed shared widgets");
    }
    static void save(Minecraft mc,GuiSegmentedDoor gui,File output,String name)throws Exception{
        Framebuffer frame=new Framebuffer(gui.width,gui.height,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,gui.width,gui.height,0,-1000,1000);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.enableTexture2D();GlStateManager.disableLighting();GlStateManager.disableDepth();gui.drawScreen(-1,-1,0);GL11.glFinish();javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(gui.width,gui.height,frame),"png",new File(output,"shot_segmented_dialog_"+name+".png"));}
        finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private SegmentedDoorDialogChecks(){}
}
