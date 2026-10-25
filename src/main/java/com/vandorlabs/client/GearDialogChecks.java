package com.vandorlabs.client;

import com.vandorlabs.container.ContainerModularGear;
import com.vandorlabs.gear.*;
import com.vandorlabs.gear.track.TrackRegistry;
import com.vandorlabs.vehicle.*;
import java.awt.Rectangle;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Simultaneous controls are checked at compact resolution through actual mouse and FBO drawing. */
public final class GearDialogChecks {
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException("Arm dialog: "+why);}
    public static void run(Minecraft mc,File output)throws Exception{
        GearBlock[] blocks={GearRegistry.GEAR_EXTENDER,GearRegistry.GEAR_SWING_ARM,GearRegistry.GEAR_MOUNT,GearRegistry.LARGE_GEAR_EXTENDER,GearRegistry.LARGE_GEAR_SWING_ARM,GearRegistry.LARGE_GEAR_MOUNT,TrackRegistry.COMPACT};
        Field[] fields=new Field[3];String[] names={"channels","angle","extension"};for(int i=0;i<3;i++){fields[i]=GuiModularGear.class.getDeclaredField(names[i]);fields[i].setAccessible(true);}
        GuiScreen previous=mc.currentScreen;net.minecraft.inventory.Container container=mc.player.openContainer;int cases=0;
        try{for(GearBlock block:blocks)for(int[] size:new int[][]{{320,240},{460,340}}){
            GearTileEntity tile=fixture(mc,block);GuiModularGear gui=new GuiModularGear(new ContainerModularGear(tile));gui.inventorySlots.windowId=-94;mc.displayGuiScreen(gui);gui.setWorldAndResolution(mc,size[0],size[1]);
            List<GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");List<Rectangle> occupied=new ArrayList<>();boolean rotation=false,done=false;
            for(GuiButton button:buttons){require(button.id!=6&&!button.displayString.equals("Back"),"no page-navigation controls remain");if(!button.visible)continue;require(mc.fontRenderer.getStringWidth(button.displayString)<=button.width-4,"button text fits "+button.displayString);add(occupied,new Rectangle(button.x,button.y,button.width,button.height),size);if(button.id==7)rotation=true;if(button.id==4)done="Done".equals(button.displayString);}
            require(rotation&&done,"motion/appearance and Done immediately visible");for(Field field:fields){GuiTextField text=(GuiTextField)field.get(gui);require(text.getVisible(),"all three editor fields immediately visible");add(occupied,new Rectangle(text.x,text.y,text.width,text.height),size);}
            GuiOptionCycleRuntimeChecks.check(mc);
            Framebuffer frame=new Framebuffer(size[0],size[1],true);GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,size[0],size[1],0,-1000,1000);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
            try(TurntableRenderState saved=new TurntableRenderState()){
                frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.disableDepth();GlStateManager.enableTexture2D();GlStateManager.disableLighting();gui.drawScreen(-1,-1,0);org.lwjgl.opengl.GL11.glFinish();java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(size[0],size[1],frame);javax.imageio.ImageIO.write(image,"png",new File(output,"shot_arm_dialog_"+block.getRegistryName().getResourcePath()+"_"+size[0]+".png"));int left=0,right=0;for(int y=gui.getGuiTop()+24;y<gui.getGuiTop()+178;y++)for(int x=gui.getGuiLeft()+12;x<gui.getGuiLeft()+gui.getXSize()-12;x++){int rgb=image.getRGB(x,y)&0xffffff;if(rgb==0xffffff||rgb==0xcdd7e4){if(x<size[0]/2)left++;else right++;}}require(left>40&&right>40,"both columns contain actual rendered glyphs");
            }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}cases++;
        }}finally{mc.displayGuiScreen(previous);mc.player.openContainer=container;org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
        System.out.println("[vandorlabs][reprolab] arm-single-page-dialog PASS cases="+cases+" compact320/460 both columns simultaneous numeric fields labels fit nonoverlap actual reverse/forward mouse paths and rendered glyphs");
    }
    private static GearTileEntity fixture(Minecraft mc,GearBlock block){
        net.minecraft.block.state.IBlockState state=block.getDefaultState();GearTileEntity fresh=(GearTileEntity)block.createTileEntity(mc.world,state);fresh.setPos(BlockPos.ORIGIN);NBTTagCompound data=fresh.writeToNBT(new NBTTagCompound());boolean track=block instanceof com.vandorlabs.gear.track.TrackBlock;
        if(!track){data.setBoolean("GearController",true);data.setInteger("GearHeight",1);data.setString("GearKind","NONE");data.setString("GearMode",block.kind()==GearBlock.Kind.MOUNT?"STATIC":block.kind()==GearBlock.Kind.SWING_ARM?"ROTATE":"EXTEND");data.setBoolean("GearTargetDeployed",true);data.setDouble("GearProgress",1);data.setBoolean("GearOperational",true);data.setDouble("ArmActiveExtension",1);net.minecraft.nbt.NBTTagList feet=new net.minecraft.nbt.NBTTagList();feet.appendTag(new net.minecraft.nbt.NBTTagIntArray(new int[]{0,-1,0}));data.setTag("GearFeet",feet);}
        VehicleStructure snapshot=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,state,data)));GearTileEntity result=(GearTileEntity)new VehicleWorld(mc.world,snapshot).getTileEntity(BlockPos.ORIGIN);if(!track)require(result.isOperational(),"GUI fixture has operational arm assembly");return result;
    }
    private static void add(List<Rectangle> occupied,Rectangle next,int[] size){require(next.x>=0&&next.y>=0&&next.x+next.width<=size[0]&&next.y+next.height<=size[1],"control fits viewport "+next);for(Rectangle prior:occupied)require(!prior.intersects(next),"controls do not overlap");occupied.add(next);}
    private GearDialogChecks(){}
}
