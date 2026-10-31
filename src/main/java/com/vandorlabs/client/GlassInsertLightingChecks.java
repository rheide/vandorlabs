package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import org.lwjgl.opengl.GL11;

/** Actual TESR pixel comparison with a controlled block-light ramp and no skylight contribution. */
final class GlassInsertLightingChecks {
    static void run(Minecraft mc,File output)throws Exception {
        int material=ScreenHousingTextures.screenIndex("light_alloy_hull"),cases=0;
        require(material>=0,"solid material fixture");
        for(String color:new String[]{"cyan","amber"}) {
            int choice=ScreenHousingTextures.screenIndex("frames/frames-door-window/lights-"+color+"-1");
            FrameAppearance frame=new FrameAppearance();frame.front=choice;require(choice>=0&&frame.light()>0,"light frame fixture");
            for(int tint=0;tint<4;tint++)for(boolean framed:new boolean[]{false,true})for(int selected:new int[]{-1,material}){
                int expected=framed&&tint==3&&selected>=0?240:32;
                require(GlassInsertLighting.block(32,selected,tint,framed,frame)==expected,"lighting eligibility");
            }
            for(boolean diagonal:new boolean[]{false,true})for(int n:new int[]{1,2}){
                Block block=diagonal?ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS:Block.getBlockFromName("vandorlabs:programmable_glass");
                List<VehicleStructure.Cell> cells=new ArrayList<>();
                for(int y=0;y<n;y++)for(int x=0;x<n;x++){
                    TileEntity seed;
                    if(diagonal){TileEntityAnimatedScreenSelector t=new TileEntityAnimatedScreenSelector();t.setGlassMaterial(material);t.setGlassAppearance(3,true);t.setGlassLayout(false,true);t.setDiagonalGeometry(1,0);seed=t;}
                    else{TileEntityProgrammableGlass t=new TileEntityProgrammableGlass();t.setMaterial(material);t.setAppearance(3,true);t.setJoin(true);seed=t;}
                    seed.setPos(new BlockPos(x,y,diagonal?y:0));FrameAppearance.of(seed).front=choice;
                    cells.add(new VehicleStructure.Cell(seed.getPos(),block.getDefaultState(),seed.writeToNBT(new NBTTagCompound())));
                }
                VehicleWorld world=new VehicleWorld(mc.world,VehicleStructure.display(cells));
                try {
                    GlassSurfaceGroups.Layout group=GlassSurfaceGroups.layout(world.tiles.values().iterator().next());require(group.columns==n&&group.rows==n,"joined insert fixture "+group.key());
                    BufferedImage lit=capture(mc,world,n);
                    for(TileEntity tile:world.tiles.values()){FrameAppearance.of(tile).front=-1;FrameAppearance.of(tile).interior=-1;FrameAppearance.of(tile).exterior=-1;}
                    GlassMaterials.changed();BufferedImage unlit=capture(mc,world,n);
                    String name="glass-insert-"+color+"-"+(diagonal?"diagonal":"regular")+"-"+n+"x"+n;
                    ImageIO.write(lit,"png",new File(output,name+"-lit.png"));ImageIO.write(unlit,"png",new File(output,name+"-unlit.png"));
                    double a=brightness(lit,n),b=brightness(unlit,n);
                    require(a>b+30,"solid insert not backlit: "+name+" lit="+a+" unlit="+b);cases++;
                } finally {GlassSurfaceGroups.unload(world);TEAnimatedScreenSelector.unloadGroups(world);}
            }
        }
        System.out.println("[vandorlabs][reprolab] glass-insert-lighting PASS rendered="+cases+" cyan amber regular diagonal single joined opaque-material only native-glass translucent bare negative-controls");
    }
    private static double brightness(BufferedImage image,int n){double sum=0;int count=0;for(int y=24;y<40;y++)for(int x=24;x<40;x++){int c=image.getRGB(x,y);sum+=(c>>16&255)+(c>>8&255)+(c&255);count+=3;}return sum/count;}
    private static BufferedImage capture(Minecraft mc,VehicleWorld world,int n){
        int pixels=n*64;Framebuffer target=new Framebuffer(pixels,pixels,true);int previousPass=MinecraftForgeClient.getRenderPass();
        DynamicTexture lightmap=new DynamicTexture(16,16);int[] values=lightmap.getTextureData();for(int y=0;y<16;y++)for(int x=0;x<16;x++)values[y*16+x]=0xff000000|x*17*0x010101;lightmap.updateDynamicTexture();
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,n,0,n,-32,32);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            target.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(0,0,0,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableFog();GlStateManager.disableLighting();GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.color(1,1,1,1);Tessellator.getInstance().getBuffer().setTranslation(0,0,0);
            mc.entityRenderer.enableLightmap();GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);GlStateManager.bindTexture(lightmap.getGlTextureId());GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);ForgeHooksClient.setRenderPass(0);
            TEProgrammableGlass regular=new TEProgrammableGlass();regular.setRendererDispatcher(TileEntityRendererDispatcher.instance);TEAnimatedScreenSelector diagonal=new TEAnimatedScreenSelector();diagonal.setRendererDispatcher(TileEntityRendererDispatcher.instance);
            for(TileEntity tile:world.tiles.values()){
                BlockPos p=tile.getPos();if(tile instanceof TileEntityProgrammableGlass)regular.render((TileEntityProgrammableGlass)tile,p.getX(),p.getY(),p.getZ(),0,-1,1);
                else diagonal.render((TileEntityAnimatedScreenSelector)tile,p.getX(),p.getY(),p.getZ(),0,-1,1);
            }
            GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(pixels,pixels,target);
        } finally {ForgeHooksClient.setRenderPass(previousPass);mc.entityRenderer.disableLightmap();GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);GlStateManager.bindTexture(0);GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);lightmap.deleteGlTexture();GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException("Glass insert lighting: "+why);}
}
