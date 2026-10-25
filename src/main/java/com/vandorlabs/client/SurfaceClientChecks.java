package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.turntable.TurntableTextures;
import com.vandorlabs.container.ContainerSurface;
import java.io.File;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Render the actual surface TESR and exercise its compact player-facing picker. */
final class SurfaceClientChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Surface client: "+message);}
    static void run(Minecraft mc,File output)throws Exception{
        SurfaceRenderChecks.run(mc);java.util.Set<Integer> signatures=new java.util.HashSet<>();
        for(int i=6;i<TurntableTextures.IDS.length;i++)for(int mode:new int[]{1,2}){
            BufferedImage image=render(mc,TurntableTextures.IDS[i],mode,false);if(i==6&&mode==1){BufferedImage dirty=render(mc,TurntableTextures.IDS[i],mode,true);require(java.util.Arrays.equals(image.getRGB(0,0,256,256,null,0,256),dirty.getRGB(0,0,256,256,null,0,256)),"actual TESR pixels survive incoming alpha/depth/texture/translation contamination");}javax.imageio.ImageIO.write(image,"png",new File(output,"shot_surface_"+TurntableTextures.IDS[i]+"_"+(mode==1?"tile":"fit")+".png"));
            int count=0,hash=1;for(int y=0;y<256;y++)for(int x=0;x<256;x++){int rgb=image.getRGB(x,y)&0xffffff;if(rgb!=0x203040)count++;hash=31*hash+rgb;}
            require(count>40000,"actual multi-cell selected artwork visibly fills pad "+TurntableTextures.IDS[i]+" pixels="+count);signatures.add(hash);
        }
        require(signatures.size()>=12,"native selected artwork and Tile/Fit produce distinct actual frames");
        TileEntityTurntableSurface tile=new TileEntityTurntableSurface();Field picker=GuiProgrammableSurface.class.getDeclaredField("textures"),layout=GuiProgrammableSurface.class.getDeclaredField("layout"),on=GuiProgrammableSurface.class.getDeclaredField("on"),off=GuiProgrammableSurface.class.getDeclaredField("off"),active=GuiProgrammableSurface.class.getDeclaredField("active");for(Field field:new Field[]{picker,layout,on,off,active})field.setAccessible(true);
        net.minecraft.inventory.Container previous=mc.player.openContainer;
        try{for(int[] size:new int[][]{{320,240},{460,340}}){GuiProgrammableSurface gui=new GuiProgrammableSurface(new ContainerSurface(tile));gui.inventorySlots.windowId=-91;gui.setWorldAndResolution(mc,size[0],size[1]);ProgrammableDialogLayout positions=new ProgrammableDialogLayout(size[0],size[1]);require(gui.getGuiTop()>=0&&gui.getGuiLeft()>=0&&gui.getGuiTop()+gui.getYSize()<=size[1],"dialog fits compact viewport");
            HousingTextureList list=(HousingTextureList)picker.get(gui);require(list.wheel(positions.listX+4,positions.top+42,-120),"wheel addresses surface picker");require(list.click(positions.listX+positions.listWidth+3,positions.top+55,0),"surface scrollbar hit region");require(list.drag(positions.top+positions.height-40),"surface scrollbar drag");list.release();
            gui.mouseClicked(positions.controlsX+3,positions.top+58,1);require((Integer)layout.get(gui)==0,"right click selects previous layout");gui.mouseClicked(positions.controlsX+3,positions.top+58,0);require((Integer)layout.get(gui)==1,"left click selects next layout");
            list.setSelected(ScreenHousingTextures.DEFAULT_TRAPDOOR);require(list.selected()==ScreenHousingTextures.DEFAULT_TRAPDOOR,"main trapdoor artwork selectable on surface");String oldOn=(String)on.get(gui);gui.choose(TurntableTextures.materialChoice("landing"));require(off.get(gui).equals(TurntableTextures.fromMaterialChoice(TurntableTextures.materialChoice("landing")))&&on.get(gui).equals(oldOn),"inactive selection preserves active artwork");String oldOff=(String)off.get(gui);gui.mouseClicked(positions.controlsX+3,positions.top+33,1);require((Boolean)active.get(gui),"right click cycles active/inactive state");gui.choose(TurntableTextures.materialChoice("copier-output"));require(off.get(gui).equals(oldOff)&&on.get(gui).equals(TurntableTextures.fromMaterialChoice(TurntableTextures.materialChoice("copier-output"))),"active selection preserves inactive artwork");
            javax.imageio.ImageIO.write(DialogRenderCapture.image(mc,gui,size[0],size[1]),"png",new File(output,"shot_surface_dialog_"+size[0]+".png"));
        }}finally{mc.player.openContainer=previous;org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
        System.out.println("[vandorlabs][reprolab] surface-client PASS 16 actual native-artwork multi-cell Tile/Fit FBO frames compact GUI320/460 wheel/drag previous/next independent state selection");
    }
    private static BufferedImage render(Minecraft mc,String texture,int mode,boolean dirty){
        TileEntityTurntableSurface tile=new TileEntityTurntableSurface();tile.setWorld(mc.world);tile.setPos(new BlockPos(540,110,180));NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());tag.setString("SurfaceOff",texture);tag.setInteger("SurfaceLayout",mode);tag.setInteger("SurfaceGroupSize",4);tile.readFromNBT(tag);require(tile.appearanceLayout().size==4&&tile.appearanceLayout().root.equals(tile.getPos()),"FBO fixture invokes actual grouped root renderer");
        require(mc.getTextureMapBlocks().getAtlasSprite(TurntableTextures.texture(texture,true)).getIconWidth()==512,"actual native Surface atlas retains512 master "+texture);
        Framebuffer frame=new Framebuffer(256,256,true);GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-2.2,2.2,-2.2,2.2,-10,10);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.alphaFunc(org.lwjgl.opengl.GL11.GL_GREATER,.1F);GlStateManager.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);GlStateManager.colorMask(true,true,true,true);mc.entityRenderer.disableLightmap();GlStateManager.rotate(90,1,0,0);
            if(dirty){GlStateManager.alphaFunc(org.lwjgl.opengl.GL11.GL_GREATER,1);GlStateManager.depthFunc(org.lwjgl.opengl.GL11.GL_GREATER);GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);GlStateManager.disableTexture2D();Tessellator.getInstance().getBuffer().setTranslation(99,99,99);}
            TETurntableSurface renderer=new TETurntableSurface();renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);renderer.render(tile,-2,-1,-2,0,-1,1);org.lwjgl.opengl.GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(256,256,frame);
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private SurfaceClientChecks(){}
}
