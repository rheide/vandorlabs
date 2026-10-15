package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.File;
import java.util.*;

/** Simulates Forge's parked tile passes, retaining real meshes, textures and depth buffers. */
final class CanopyGlassOrderRuntimeChecks {
    private static final BlockPos ROOT=new BlockPos(3,3,3);
    private static final int SIZE=256;
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("canopy glass order: "+text);}
    private static void clean(String stage){int error=GL11.glGetError();require(error==GL11.GL_NO_ERROR,"OpenGL error "+error+" at "+stage);}
    private static VehicleWorld fixture(Minecraft mc,int width,int material,String background){
        List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        TileEntityCanopy canopy=new TileEntityCanopy();canopy.setPos(ROOT);canopy.anchor=ROOT;canopy.large.depth=16;canopy.large.scale=71;canopy.large.shade=14;canopy.large.tint=1;
        if(material>=0){canopy.large.material=CustomBlockMaterials.choice(new ItemStack(Blocks.GOLD_BLOCK));canopy.large.tint=material;}
        NBTTagCompound tag=canopy.writeToNBT(new NBTTagCompound());canopy.large.write(tag);
        cells.add(new VehicleStructure.Cell(ROOT,(width==3?net.minecraft.block.Block.getBlockFromName("vandorlabs:front_canopy_glass"):net.minecraft.block.Block.getBlockFromName("vandorlabs:large_front_canopy_glass")).getDefaultState().withProperty(BlockCanopy.FACING,EnumFacing.NORTH),tag));
        for(int x=0;x<width;x++)for(int y=0;y<width;y++){
            BlockPos pos=ROOT.add(x,y,2);IBlockState state;NBTTagCompound data=null;
            if(background.equals("vanilla"))state=Blocks.IRON_BLOCK.getDefaultState();
            else{state=background.equals("wall")?ModBlocks.PROGRAMMABLE_WALL.getDefaultState().withProperty(BlockProgrammableWall.FACING,EnumFacing.NORTH):ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.NORTH);TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setPos(pos);tile.setHousingTexture(CustomBlockMaterials.choice(new ItemStack(Blocks.IRON_BLOCK)));data=tile.writeToNBT(new NBTTagCompound());}
            cells.add(new VehicleStructure.Cell(pos,state,data));
        }
        return new VehicleWorld(mc.world,new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1));
    }
    private static void background(Minecraft mc,VehicleWorld world,int pass){
        GlStateManager.enableDepth();GlStateManager.disableLighting();GlStateManager.depthMask(true);GlStateManager.enableCull();GlStateManager.color(1,1,1,1);
        if(pass==0){mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);ForgeHooksClient.setRenderLayer(BlockRenderLayer.SOLID);try{for(VehicleStructure.Cell c:world.structure.cells)if(c.pos.getZ()==ROOT.getZ()+2&&c.state.getRenderType()==EnumBlockRenderType.MODEL)mc.getBlockRendererDispatcher().renderBlock(c.state,c.pos,world,b);}finally{ForgeHooksClient.setRenderLayer(null);}Tessellator.getInstance().draw();}
        for(VehicleStructure.Cell c:world.structure.cells)if(c.pos.getZ()==ROOT.getZ()+2){TileEntity t=world.getTileEntity(c.pos);if(t!=null&&t.shouldRenderInPass(pass)&&TileEntityRendererDispatcher.instance.getRenderer(t)!=null)TileEntityRendererDispatcher.instance.render(t,c.pos.getX(),c.pos.getY(),c.pos.getZ(),1);}
    }
    /** mode: 0 opaque-only baseline, 1 correct full-glass reference, 2/3 real passes opposite tile orders, 4 old broken order. */
    private static BufferedImage draw(Minecraft mc,VehicleWorld world,int width,boolean right,int mode){
        String checkpoint="width="+width+" right="+right+" mode="+mode;clean("entry "+checkpoint);
        int oldFramebuffer=GL11.glGetInteger(0x8CA6); // GL_FRAMEBUFFER_BINDING
        int oldActive=GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE),oldClient=GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_CLIENT_ACTIVE_TEXTURE),oldMatrix=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousPass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass(),oldTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT|GL11.GL_CLIENT_VERTEX_ARRAY_BIT);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        Framebuffer fbo=new Framebuffer(SIZE,SIZE,true);TileEntityRendererDispatcher d=TileEntityRendererDispatcher.instance;net.minecraft.world.World oldWorld=d.world;double ox=d.entityX,oy=d.entityY,oz=d.entityZ;
        clean("FBO creation "+checkpoint);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-width*.75,width*.75,-width*.75,width*.75,-40,40);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try{fbo.bindFramebuffer(true);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_CULL_FACE);GL11.glDepthMask(true);GlStateManager.enableDepth();GlStateManager.enableTexture2D();GlStateManager.enableCull();GlStateManager.disableLighting();GL11.glDisable(GL11.GL_LIGHTING);GlStateManager.depthMask(true);GlStateManager.clearColor(.08F,.12F,.18F,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GlStateManager.loadIdentity();GlStateManager.rotate(8,1,0,0);GlStateManager.rotate(right?162:198,0,1,0);GlStateManager.translate(-ROOT.getX()-width/2D,-ROOT.getY()-width/2D,-ROOT.getZ()-.5);
            d.world=world;d.entityX=ROOT.getX()+width/2D+(right?8:-8);d.entityY=ROOT.getY()+width/2D+2;d.entityZ=ROOT.getZ()-12;
            clean("projection setup "+checkpoint);TileEntityCanopy canopy=(TileEntityCanopy)world.getTileEntity(ROOT);TECanopy renderer=(TECanopy)(Object)d.getRenderer(canopy);require(renderer!=null,"registered canopy renderer");
            if(mode==5){
                EntityGroundVehicle craft=new EntityGroundVehicle(mc.world);craft.install(world.structure);craft.setPosition(0,0,0);
                RenderGroundVehicle vehicleRenderer=new RenderGroundVehicle(mc.getRenderManager());
                // doRender accepts camera-relative entity coordinates. Compensate
                // its translation here so this shares the direct reference matrix.
                GlStateManager.translate(d.entityX,d.entityY,d.entityZ);
                try{for(int warm=0;warm<64;warm++){GlStateManager.depthMask(true);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);RenderGroundVehicle.beginFrame();vehicleRenderer.doRender(craft,-d.entityX,-d.entityY,-d.entityZ,0,1);}}
                finally{craft.setDead();RenderGroundVehicle.cleanup(mc.world);}
            }
            else if(mode<2){for(int pass=0;pass<2;pass++){ForgeHooksClient.setRenderPass(pass);background(mc,world,pass);}renderer.renderPass(canopy,ROOT.getX(),ROOT.getY(),ROOT.getZ(),1,-1,1,mode==0?0:-1);}
            else if(mode==4){ForgeHooksClient.setRenderPass(0);renderer.renderPass(canopy,ROOT.getX(),ROOT.getY(),ROOT.getZ(),1,-1,1,-1);background(mc,world,0);ForgeHooksClient.setRenderPass(1);background(mc,world,1);}
            else for(int pass=0;pass<2;pass++){ForgeHooksClient.setRenderPass(pass);boolean legacy=Boolean.getBoolean("vandorlabs.canopyGlassLegacyOrder");if(mode==2&&(legacy?pass==0:canopy.shouldRenderInPass(pass)))renderer.renderPass(canopy,ROOT.getX(),ROOT.getY(),ROOT.getZ(),1,-1,1,legacy?-1:pass);background(mc,world,pass);if(mode==3&&(legacy?pass==0:canopy.shouldRenderInPass(pass)))renderer.renderPass(canopy,ROOT.getX(),ROOT.getY(),ROOT.getZ(),1,-1,1,legacy?-1:pass);}
            GL11.glFinish();clean("drawing "+checkpoint);BufferedImage image=ScreenShotHelper.createScreenshot(SIZE,SIZE,fbo);clean("texture readback "+checkpoint);return image;
        }finally{ForgeHooksClient.setRenderPass(previousPass);d.world=oldWorld;d.entityX=ox;d.entityY=oy;d.entityZ=oz;GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);clean("matrix cleanup "+checkpoint);GL11.glPopClientAttrib();clean("client attribute restore "+checkpoint);
            // Attribute stacks include the window's draw/read buffer selection.
            // GL_BACK/FRONT is invalid while our offscreen FBO remains bound.
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,oldFramebuffer);clean("original framebuffer restore "+checkpoint);GL11.glPopAttrib();clean("server attribute restore "+checkpoint);OpenGlHelper.setClientActiveTexture(oldClient);OpenGlHelper.setActiveTexture(oldActive);GlStateManager.setActiveTexture(oldActive);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_CULL_FACE);GL11.glEnable(GL11.GL_LIGHTING);GL11.glDepthMask(true);GL11.glColor4f(1,1,1,1);GlStateManager.enableDepth();GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.depthMask(true);GlStateManager.color(1,1,1,1);fbo.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,oldFramebuffer);GlStateManager.bindTexture(oldTexture);GlStateManager.matrixMode(oldMatrix);clean("FBO and texture cleanup "+checkpoint);}
    }
    private static int differences(BufferedImage a,BufferedImage b,int threshold){int changed=0;for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){int p=a.getRGB(x,y),q=b.getRGB(x,y);int diff=Math.max(Math.abs((p>>16&255)-(q>>16&255)),Math.max(Math.abs((p>>8&255)-(q>>8&255)),Math.abs((p&255)-(q&255))));if(diff>threshold)changed++;}return changed;}
    static void run(Minecraft mc,File output)throws Exception{
        int cases=0;for(int width:new int[]{3,5})for(int material:new int[]{-1,0,1})for(String bg:new String[]{"wall","screen","vanilla"})for(boolean right:new boolean[]{false,true}){
            VehicleWorld fixture=fixture(mc,width,material,bg);BufferedImage base=draw(mc,fixture,width,right,0),reference=draw(mc,fixture,width,right,1);String name="w"+width+"_"+(material<0?"native":material==0?"35":"65")+"_"+bg+"_"+(right?"right":"left");
            int visible=differences(base,reference,8);require(visible>100,"glass visibly changes opaque background "+name+" pixels="+visible);
            int broken=differences(reference,draw(mc,fixture,width,right,4),8);require(broken>100,"negative control catches original single-pass overwritten tint "+name+" pixels="+broken);
            if(width==3&&material<0&&!bg.equals("vanilla")){
                BufferedImage vehicle=draw(mc,fixture,width,right,5);javax.imageio.ImageIO.write(vehicle,"png",new File(output,"shot_canopy_glass_vehicle_"+name+".png"));int changed=differences(reference,vehicle,2);require(changed<=16,"actual vehicle renderer retains same glazing/background "+name+" mismatch="+changed);
            }
            BufferedImage montage=new BufferedImage(SIZE*4,SIZE,BufferedImage.TYPE_INT_RGB);Graphics2D g=montage.createGraphics();g.drawImage(base,0,0,null);g.drawImage(reference,SIZE,0,null);
            for(int mode=2;mode<4;mode++){BufferedImage actual=draw(mc,fixture,width,right,mode);g.drawImage(actual,SIZE*mode,0,null);int changed=differences(reference,actual,2);if(changed>16){g.dispose();javax.imageio.ImageIO.write(montage,"png",new File(output,"shot_canopy_glass_order_"+name+".png"));require(false,"parked Forge passes preserve tint "+name+" order="+mode+" mismatch="+changed+" visible="+visible);}}
            g.dispose();javax.imageio.ImageIO.write(montage,"png",new File(output,"shot_canopy_glass_order_"+name+".png"));cases++;
        }System.out.println("[vandorlabs][reprolab] canopy-glass-order PASS cases="+cases+" native-35-65 front3-large5 wall-screen-vanilla left-right opposite-tile-orders real-Forge-passes visible-tint pixel-reference");
    }
    private CanopyGlassOrderRuntimeChecks(){}
}
