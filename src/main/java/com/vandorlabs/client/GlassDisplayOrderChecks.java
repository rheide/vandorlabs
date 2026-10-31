package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;

/** Reported joined Normal screen in front of Strong solid-material glass, opposite tile orders. */
final class GlassDisplayOrderChecks {
    private static final BlockPos ROOT=new BlockPos(3,3,3);private static final int SIZE=320;
    private static VehicleWorld fixture(Minecraft mc,int width,boolean framed){
        List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        IBlockState state=Block.getBlockFromName("vandorlabs:programmable_glass").getDefaultState();
        for(int layer=0;layer<2;layer++)for(int x=0;x<width;x++)for(int y=0;y<2;y++){
            BlockPos at=ROOT.add(x,y,layer*2);TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.setPos(at);tile.setAppearance(layer==0?1:2,framed);tile.setTextureLayout(0);tile.setJoin(true);
            tile.setMaterial(layer==0?ScreenHousingTextures.screenIndex("company_paladyne_full_biology_static"):CustomBlockMaterials.choice(new ItemStack(Blocks.IRON_BLOCK)));
            cells.add(new VehicleStructure.Cell(at,state,tile.writeToNBT(new NBTTagCompound())));
        }
        return new VehicleWorld(mc.world,new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1));
    }
    private static void layer(VehicleWorld world,int layer,int pass){
        for(VehicleStructure.Cell cell:world.structure.cells)if(cell.pos.getZ()==ROOT.getZ()+layer*2){TileEntity tile=world.getTileEntity(cell.pos);if(tile!=null&&tile.shouldRenderInPass(pass))TileEntityRendererDispatcher.instance.render(tile,cell.pos.getX(),cell.pos.getY(),cell.pos.getZ(),1);}
    }
    /** mode 0 is the explicit rear-then-front reference; 1/2 are opposite Forge tile orders. */
    private static BufferedImage draw(Minecraft mc,VehicleWorld world,int width,boolean right,int mode){
        int previousPass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();TileEntityRendererDispatcher d=TileEntityRendererDispatcher.instance;net.minecraft.world.World old=d.world;double ox=d.entityX,oy=d.entityY,oz=d.entityZ;
        int oldFbo=GL11.glGetInteger(org.lwjgl.opengl.EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);TurntableRenderState saved=new TurntableRenderState();
        Framebuffer fbo=new Framebuffer(SIZE,SIZE,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-width*.7,width*.7,-width*.7,width*.7,-30,30);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try{
            if(mode>0)GlazingRenderOrder.begin(world);
            fbo.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(.1F,.16F,.23F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.disableCull();GlStateManager.enableTexture2D();GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();
            GlStateManager.rotate(8,1,0,0);GlStateManager.rotate(right?162:198,0,1,0);GlStateManager.translate(-ROOT.getX()-width/2D,-ROOT.getY()-1,-ROOT.getZ()-.5);
            d.world=world;d.entityX=ROOT.getX()+width/2D+(right?8:-8);d.entityY=ROOT.getY()+2;d.entityZ=ROOT.getZ()-12;
            for(int pass=0;pass<2;pass++){
                ForgeHooksClient.setRenderPass(pass);
                if(mode==1){layer(world,0,pass);layer(world,1,pass);}else{layer(world,1,pass);layer(world,0,pass);}
            }
            GL11.glFinish();return ScreenShotHelper.createScreenshot(SIZE,SIZE,fbo);
        }finally{GlazingRenderOrder.end();ForgeHooksClient.setRenderPass(previousPass);d.world=old;d.entityX=ox;d.entityY=oy;d.entityZ=oz;GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,oldFbo);saved.close();fbo.deleteFramebuffer();}
    }
    private static void animation(Minecraft mc,File output)throws Exception{
        long time=mc.world.getTotalWorldTime();VehicleWorld world=fixture(mc,3,false);
        try{
            for(VehicleStructure.Cell cell:world.structure.cells)if(cell.pos.getZ()==ROOT.getZ())((TileEntityProgrammableGlass)world.getTileEntity(cell.pos)).glassPlayback.set(2,1);
            BufferedImage[] frames=new BufferedImage[2];
            for(int phase=0;phase<2;phase++){mc.world.setTotalWorldTime(phase*12);final int sample=phase;CockpitVisualMotion.authoredFrame(()->frames[sample]=draw(mc,world,3,false,1));ImageIO.write(frames[phase],"png",new File(output,"glass-display-order_animated_phase_"+phase+".png"));}
            if(difference(frames[0],frames[1])<50)throw new IllegalStateException("Glass Display did not visibly animate");
            for(VehicleStructure.Cell cell:world.structure.cells)if(cell.pos.getZ()==ROOT.getZ())((TileEntityProgrammableGlass)world.getTileEntity(cell.pos)).glassPlayback.set(0,1);
            BufferedImage off=draw(mc,world,3,false,1);ImageIO.write(off,"png",new File(output,"glass-display-order_off.png"));if(difference(frames[0],off)<500)throw new IllegalStateException("Glass Display OFF did not change artwork");
            System.out.println("[vandorlabs][reprolab] glass-display-playback PASS joined-real-render static-mesh-remap animated-phases OFF same-frame-clock");
        }finally{mc.world.setTotalWorldTime(time);}
    }
    private static int difference(BufferedImage a,BufferedImage b){int n=0;for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){int p=a.getRGB(x,y),q=b.getRGB(x,y);if(Math.abs((p>>16&255)-(q>>16&255))>2||Math.abs((p>>8&255)-(q>>8&255))>2||Math.abs((p&255)-(q&255))>2)n++;}return n;}
    static void run(Minecraft mc,File output)throws Exception{
        for(int width:new int[]{3,5})for(boolean framed:new boolean[]{false,true})for(boolean right:new boolean[]{false,true}){
            VehicleWorld world=fixture(mc,width,framed);BufferedImage reference=draw(mc,world,width,right,0),forward=draw(mc,world,width,right,1),reverse=draw(mc,world,width,right,2);String name="w"+width+"_"+framed+"_"+right;
            ImageIO.write(reference,"png",new File(output,"glass-display-order_"+name+"_reference.png"));ImageIO.write(forward,"png",new File(output,"glass-display-order_"+name+"_forward.png"));ImageIO.write(reverse,"png",new File(output,"glass-display-order_"+name+"_reverse.png"));
            int changed=difference(reference,forward);System.out.println("[vandorlabs][reprolab] glass-display-order case="+name+" forward-mismatch="+changed+" reverse-mismatch="+difference(reference,reverse));
            if(changed>16||difference(reference,reverse)>16)throw new IllegalStateException("Glass screen behind-glass order differs: "+name+" pixels="+changed);
        }
        animation(mc,output);
        System.out.println("[vandorlabs][reprolab] glass-display-order PASS joined-Normal-screen Strong-solid-background widths=3,5 framed-bare left-right opposite-tile-orders");
    }
}
