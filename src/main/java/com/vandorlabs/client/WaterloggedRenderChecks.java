package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.fluids.*;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** Compare the optimized renderer against the pre-batching renderer in the same GL context. */
final class WaterloggedRenderChecks {
    private static final String[] IDS={"programmable_slab","programmable_stairs","programmable_wall","programmable_porthole_wall","programmable_glass","programmable_diagonal_wall","programmable_diagonal_porthole","programmable_diagonal_glass","programmable_door","programmable_trapdoor","programmable_diagonal_trapdoor"};
    static void run(Minecraft mc,java.io.File output)throws Exception{
        BlockPos pos=new BlockPos(358,48,146);int cases=0;
        net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(128,128,true);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-.9,.9,-.9,.9,-10,10);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();
        java.util.List<String> report=new ArrayList<>();report.add("block,reference_us,batched_us,reference_bytes,batched_bytes,reference_cpu_us,batched_cpu_us");
        try{
            frame.bindFramebuffer(true);GlStateManager.enableDepth();GlStateManager.disableFog();GlStateManager.rotate(25,1,0,0);GlStateManager.rotate(145,0,1,0);GlStateManager.translate(-.5,-.5,-.5);
            for(String id:IDS){
                Block block=Block.getBlockFromName("vandorlabs:"+id);boolean diagonal=block instanceof BlockProgrammableWall&&((BlockProgrammableWall)block).isDiagonalShape();
                boolean opening=OpeningFluids.supports(block.getDefaultState());
                for(int shape=0;shape<(diagonal?6:opening?2:1);shape++){
                    IBlockState state=block.getDefaultState();if(diagonal)state=state.withProperty(BlockProgrammableWall.INVERTED,shape%2==1);
                    if(opening){if(block instanceof BlockConfigurableSpaceDoor)state=state.withProperty(BlockVandorDoor.OPEN,shape==1);else state=state.withProperty(net.minecraft.block.BlockTrapDoor.OPEN,shape==1);}
                    mc.world.setBlockState(pos,state,2);
                    if(block instanceof BlockConfigurableSpaceDoor)mc.world.setBlockState(pos.up(),state.withProperty(BlockVandorDoor.HALF,net.minecraft.block.BlockDoor.EnumDoorHalf.UPPER),2);
                    TileEntity tile=mc.world.getTileEntity(pos);
                    if(diagonal)((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile).setDiagonalGeometry(shape/2,0);
                    ContainedFluids data=WaterloggedParts.data(tile);
                    for(int fill=0;fill<5;fill++){
                        data.set(0,fill==0||fill==2?null:new FluidStack(FluidRegistry.WATER,1000));
                        data.set(1,fill<2?null:new FluidStack(fill==4?FluidRegistry.LAVA:FluidRegistry.WATER,1000));
                        WaterloggedSurfaceMesh.Entry entry=WaterloggedSurfaceMesh.entry(tile);
                        require(entry==WaterloggedSurfaceMesh.entry(tile),"warm mesh cache reuse "+id);
                        for(int side=0;side<2;side++){FluidStack expected=WaterloggedParts.fluid(mc.world,pos,side),actual=entry.fluids[side];require(expected==null?actual==null:actual!=null&&expected.isFluidEqual(actual),"cache fluid invalidation "+id);}
                        int[] before=pixels(frame,tile,false),after=pixels(frame,tile,true);
                        require(Arrays.equals(before,after),"fluid rendering pixels changed: "+id+" shape="+shape+" fill="+fill);
                        if(!entry.mesh.isEmpty())require(!Arrays.equals(after,pixels(frame,null,true)),"nonempty fluid rendering "+id);
                        cases++;
                    }
                    if(shape==0){
                        for(int i=0;i<1000;i++){Reference.render(tile,0,0,0);WaterloggedRenderer.render(tile,0,0,0);}
                        double[][] ref=new double[9][],fast=new double[9][];
                        for(int i=0;i<9;i++){if(i%2==0){ref[i]=measure(tile,false);fast[i]=measure(tile,true);}else{fast[i]=measure(tile,true);ref[i]=measure(tile,false);}}
                        report.add(id+","+median(ref,0)+","+median(fast,0)+","+median(ref,1)+","+median(fast,1)+","+median(ref,2)+","+median(fast,2));
                    }
                    mc.world.setBlockToAir(pos.up());mc.world.setBlockToAir(pos);
                }
            }
            java.nio.file.Files.write(new java.io.File(output,"waterlogged-render-performance.csv").toPath(),report,java.nio.charset.StandardCharsets.UTF_8);
        }finally{mc.world.setBlockToAir(pos.up());mc.world.setBlockToAir(pos);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.enableCull();GlStateManager.color(1,1,1,1);}
        System.out.println("[vandorlabs][reprolab] waterlogged-render-performance PASS pixel-identical cases="+cases+" all eleven families five fills all diagonal geometries/inversions; timing/allocation CSV written");
    }
    private static int[] pixels(net.minecraft.client.shader.Framebuffer frame,TileEntity tile,boolean optimized){
        GlStateManager.clearColor(.16F,.18F,.2F,1);GlStateManager.depthMask(true);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        if(tile!=null){if(optimized)WaterloggedRenderer.render(tile,0,0,0);else Reference.render(tile,0,0,0);}
        java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(128,128,frame);return image.getRGB(0,0,128,128,null,0,128);
    }
    private static double[] measure(TileEntity tile,boolean optimized){
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        boolean allocated=bean.isThreadAllocatedMemorySupported();if(allocated&&!bean.isThreadAllocatedMemoryEnabled())bean.setThreadAllocatedMemoryEnabled(true);
        long thread=Thread.currentThread().getId();GL11.glFinish();long bytes=allocated?bean.getThreadAllocatedBytes(thread):0,cpu=bean.getCurrentThreadCpuTime(),start=System.nanoTime();
        for(int i=0;i<1024;i++){if(optimized)WaterloggedRenderer.render(tile,0,0,0);else Reference.render(tile,0,0,0);}
        GL11.glFinish();long elapsed=System.nanoTime()-start;return new double[]{elapsed/1024000D,allocated?(bean.getThreadAllocatedBytes(thread)-bytes)/1024D:-1,(bean.getCurrentThreadCpuTime()-cpu)/1024000D};
    }
    private static double median(double[][] samples,int column){double[] values=new double[samples.length];for(int i=0;i<values.length;i++)values[i]=samples[i][column];Arrays.sort(values);return values[values.length/2];}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    // Reference preserves per-side draws, fluid decoding and triangle allocation so the
    // live release suite detects changes to vertex order, tint, lighting or transparency.
static final class Reference {
    static TextureAtlasSprite sprite(FluidStack stack){return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(stack.getFluid().getStill(stack).toString());}
    static void render(TileEntity tile,double x,double y,double z){
        ContainedFluids data=WaterloggedParts.data(tile);if(data==null||!WaterloggedParts.wet(tile.getWorld(),tile.getPos()))return;
        java.util.List<WaterloggedSurfaceMesh.Surface> surfaces=WaterloggedSurfaceMesh.get(tile);
        float oldU=OpenGlHelper.lastBrightnessX,oldV=OpenGlHelper.lastBrightnessY;
        boolean depthMask=org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,z);
        try{
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.depthMask(false);GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableLighting();GlStateManager.enableCull();GlStateManager.color(1,1,1,1);
            for(int side=0;side<2;side++){
                FluidStack stack=WaterloggedParts.fluid(tile.getWorld(),tile.getPos(),side);if(stack==null)continue;Fluid fluid=stack.getFluid();
                TextureAtlasSprite still=sprite(stack);
                int color=fluid==FluidRegistry.WATER?0xFF000000|net.minecraft.world.biome.BiomeColorHelper.getWaterColorAtPos(tile.getWorld(),tile.getPos()):fluid.getColor(stack);
                Tessellator tess=Tessellator.getInstance();BufferBuilder buffer=tess.getBuffer();buffer.begin(7,BlockSurfaceFormat.get());
                for(WaterloggedSurfaceMesh.Surface face:surfaces)if(face.side==side){
                    Vec3d normal=face.normal;
                    float shade=(float)(normal.x*normal.x*.6+normal.y*normal.y*(normal.y<0?.5:1)+normal.z*normal.z*.8);
                    net.minecraft.util.math.BlockPos sample=face.boundary==null||face.boundary==net.minecraft.util.EnumFacing.UP?tile.getPos():tile.getPos().offset(face.boundary);
                    int light=fluid.getBlock()==null?tile.getWorld().getCombinedLight(sample,fluid.getLuminosity(stack)):fluid.getBlock().getDefaultState().getPackedLightmapCoords(tile.getWorld(),sample);
                    TextureAtlasSprite sprite=still;
                    if(face.vertices.length==4)for(Vec3d vertex:face.vertices)vertex(buffer,sprite,face.axis,vertex,color,shade,light,normal);
                    else for(int i=1;i+1<face.vertices.length;i++)for(Vec3d vertex:new Vec3d[]{face.vertices[0],face.vertices[i],face.vertices[i+1],face.vertices[i+1]})vertex(buffer,sprite,face.axis,vertex,color,shade,light,normal);
                    // Exterior reference retains only the original two-sided free top.
                    if(face.boundary==net.minecraft.util.EnumFacing.UP){
                        Vec3d reverse=normal.scale(-1);
                        if(face.vertices.length==4)for(int i=3;i>=0;i--)vertex(buffer,sprite,face.axis,face.vertices[i],color,shade,light,reverse);
                        else for(int i=1;i+1<face.vertices.length;i++)for(Vec3d vertex:new Vec3d[]{face.vertices[0],face.vertices[i+1],face.vertices[i],face.vertices[i]})vertex(buffer,sprite,face.axis,vertex,color,shade,light,reverse);
                    }

                }
                tess.draw();
            }
        }finally{GlStateManager.depthMask(depthMask);GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.disableBlend();GlStateManager.resetColor();GlStateManager.color(1,1,1,1);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldU,oldV);GlStateManager.popMatrix();}
    }
    private static void vertex(BufferBuilder buffer,TextureAtlasSprite sprite,int axis,Vec3d p,int color,float shade,int light,Vec3d normal){
        double u=axis==0?p.z:p.x,v=axis==1?p.z:1-p.y;
        double scale=16;
        buffer.pos(p.x,p.y,p.z).color((color>>16&255)*shade/255F,(color>>8&255)*shade/255F,(color&255)*shade/255F,(color>>>24)/255F).tex(sprite.getInterpolatedU(u*scale),sprite.getInterpolatedV(v*scale)).lightmap(light>>16&65535,light&65535).normal((float)normal.x,(float)normal.y,(float)normal.z).endVertex();
    }
}

}
