package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import java.util.*;

/** Whole-pool chunk buffers and perspective captures, including moving waterline views. */
final class PoolFluidRegressionChecks {
    static void run(Minecraft mc,java.io.File output)throws Exception{
        BlockPos origin=new BlockPos(350,60,146);List<BlockPos> cells=new ArrayList<>();
        net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(640,480,true);
        net.minecraft.entity.Entity oldView=mc.getRenderViewEntity();
        net.minecraft.client.entity.EntityOtherPlayerMP probe=new net.minecraft.client.entity.EntityOtherPlayerMP(mc.world,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("902d3aee-c01d-4ae4-b74d-54925cd02692"),"PoolViewProbe"));
        java.lang.reflect.Field camera=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(ActiveRenderInfo.class,"position","field_178811_e");Object oldCamera=camera.get(null);
        int oldPass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GLU.gluPerspective(60,640F/480,.05F,100);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try{camera.set(null,net.minecraft.util.math.Vec3d.ZERO);mc.setRenderViewEntity(probe);
            for(int x=0;x<7;x++)for(int z=0;z<7;z++)for(int y=0;y<4;y++){
                BlockPos p=origin.add(x,y,z);boolean edge=x==0||x==6||z==0||z==6;
                IBlockState state=y==0?Blocks.STONE.getDefaultState():edge?Block.getBlockFromName(y==2?"vandorlabs:programmable_porthole_wall":"vandorlabs:programmable_glass").getDefaultState():Blocks.WATER.getDefaultState();
                if(edge&&(x==0||x==6)){
                    if(state.getBlock() instanceof BlockProgrammableGlass)state=state.withProperty(BlockGlassWall.ROTATED,true);
                    if(state.getBlock() instanceof BlockProgrammableWall)state=state.withProperty(BlockProgrammableWall.FACING,net.minecraft.util.EnumFacing.EAST);
                }
                mc.world.setBlockState(p,state,2);TileEntity tile=mc.world.getTileEntity(p);ContainedFluids fluid=WaterloggedParts.data(tile);if(fluid!=null){int side=WaterloggedParts.side(mc.world,p,new net.minecraft.util.math.Vec3d(x==0?.99:x==6?.01:.5,.5,z==0?.99:z==6?.01:.5));fluid.set(side,new FluidStack(FluidRegistry.WATER,1000));}cells.add(p);
            }
            for(int view=0;view<32;view++){
                double angle=Math.toRadians(view*30),cx=3.5+10*Math.sin(angle),cz=3.5+10*Math.cos(angle),cy=view%2==0?8:3.8;
                double tx=3.5,ty=2,tz=3.5;
                if(view>=12&&view<24){cx=3.5;cz=3.5;cy=new double[]{3.86,3.9,2.5,1.5}[(view-12)%4];tx=cx+Math.sin(angle)*4;tz=cz+Math.cos(angle)*4;ty=cy+(view%3==0?.12:0);}
                else if(view>=24){cy=view%2==0?3.86:3.9;ty=cy+(view%3==0?.12:0);}
                probe.setPosition(origin.getX()+cx,origin.getY()+cy,origin.getZ()+cz);probe.prevPosX=probe.posX;probe.prevPosY=probe.posY;probe.prevPosZ=probe.posZ;
                frame.bindFramebuffer(true);GlStateManager.loadIdentity();GLU.gluLookAt((float)cx,(float)cy,(float)cz,(float)tx,(float)ty,(float)tz,0,1,0);
                GlStateManager.clearColor(.16F,.18F,.2F,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.enableCull();GlStateManager.disableFog();GlStateManager.disableLighting();
                mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
                for(int pass=0;pass<2;pass++){
                    GlStateManager.depthMask(pass==0);if(pass==1){GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);}
                    BufferBuilder buffer=new BufferBuilder(65536);buffer.begin(7,DefaultVertexFormats.BLOCK);buffer.setTranslation(-origin.getX(),-origin.getY(),-origin.getZ());
                    for(BlockPos p:cells)if((mc.world.getBlockState(p).getBlock().getBlockLayer()==BlockRenderLayer.TRANSLUCENT)==(pass==1))mc.getBlockRendererDispatcher().renderBlock(mc.world.getBlockState(p),p,mc.world,buffer);
                    if(pass==1)buffer.sortVertexData((float)cx+origin.getX(),(float)cy+origin.getY(),(float)cz+origin.getZ());
                    buffer.finishDrawing();checkBounds(buffer);new WorldVertexBufferUploader().draw(buffer);
                    net.minecraftforge.client.ForgeHooksClient.setRenderPass(pass);
                    for(BlockPos p:cells){TileEntity tile=mc.world.getTileEntity(p);if(tile==null||!tile.shouldRenderInPass(pass))continue;net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer renderer=net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance.getRenderer(tile);if(renderer!=null)renderer.render(tile,p.getX()-origin.getX(),p.getY()-origin.getY(),p.getZ()-origin.getZ(),0,-1,1);}
                }
                javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(640,480,frame),"png",new java.io.File(output,"fluid-pool-view-"+view+".png"));
                if(view>=12&&view<24)continue;
                // Independent pre-regression reference: an above-water camera must
                // not acquire extra back-facing water sheets across the whole pool.
                int[][] pixels=new int[2][];
                for(int reference=0;reference<2;reference++){
                    GlStateManager.depthMask(true);GlStateManager.clear(16640);
                    for(BlockPos p:cells){TileEntity tile=mc.world.getTileEntity(p);if(WaterloggedParts.data(tile)==null)continue;
                        if(reference==0)WaterloggedRenderer.render(tile,p.getX()-origin.getX(),p.getY()-origin.getY(),p.getZ()-origin.getZ());
                        else WaterloggedRenderChecks.Reference.render(tile,p.getX()-origin.getX(),p.getY()-origin.getY(),p.getZ()-origin.getZ());
                    }
                    java.awt.image.BufferedImage capture=net.minecraft.util.ScreenShotHelper.createScreenshot(640,480,frame);
                    pixels[reference]=capture.getRGB(0,0,640,480,null,0,640);
                    javax.imageio.ImageIO.write(capture,"png",new java.io.File(output,"fluid-pool-"+(reference==0?"actual":"reference")+"-"+view+".png"));
                }
                int changed=0;for(int pixel=0;pixel<pixels[0].length;pixel++)if(pixels[0][pixel]!=pixels[1][pixel])changed++;
                if(changed!=0)throw new IllegalStateException("Exterior pool gained back-facing water sheets: view="+view+" changedPixels="+changed);
            }
        }finally{mc.setRenderViewEntity(oldView);camera.set(null,oldCamera);for(BlockPos p:cells)mc.world.setBlockToAir(p);net.minecraftforge.client.ForgeHooksClient.setRenderPass(oldPass);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.enableCull();}
        System.out.println("[vandorlabs][reprolab] fluid-pool PASS 32 above/below waterline level/upward inward/outward glass/porthole perspectives exterior reference parity bounded sorted geometry");
    }
    private static void checkBounds(BufferBuilder buffer){
        if(buffer.getVertexCount()%4!=0)throw new IllegalStateException("Incomplete pool quad");
        java.nio.ByteBuffer bytes=buffer.getByteBuffer();int stride=buffer.getVertexFormat().getNextOffset();
        for(int v=0;v<buffer.getVertexCount();v++)for(int axis=0;axis<3;axis++){float p=bytes.getFloat(v*stride+axis*4);if(!Float.isFinite(p)||p<-.01||p>(axis==1?4:7)+.01)throw new IllegalStateException("Pool vertex escaped its bounds: "+p+" axis="+axis);}
    }
}
