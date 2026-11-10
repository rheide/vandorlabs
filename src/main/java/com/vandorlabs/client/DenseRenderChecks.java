package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.io.*;
import java.util.*;
import java.awt.image.BufferedImage;
import org.lwjgl.opengl.GL11;

/** Dense submissions compared in one client/context. Timings are CPU diagnostics, not hardware FPS. */
final class DenseRenderChecks {
    private static final com.sun.management.ThreadMXBean ALLOC=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
    private static void require(boolean b,String message){if(!b)throw new IllegalStateException("dense-render: "+message);}
    private static long[] sample(Runnable draw){
        for(int i=0;i<4;i++)draw.run();long[] ns=new long[7],bytes=new long[7];
        for(int i=0;i<ns.length;i++){GL11.glFinish();long a=ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId()),start=System.nanoTime();draw.run();ns[i]=System.nanoTime()-start;bytes[i]=ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId())-a;}
        Arrays.sort(ns);Arrays.sort(bytes);return new long[]{ns[3],bytes[3]};
    }
    private static BufferedImage image(Minecraft mc,Framebuffer f,Runnable draw){f.bindFramebuffer(true);GlStateManager.clearColor(.15F,.2F,.3F,1);GlStateManager.depthMask(true);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);draw.run();return net.minecraft.util.ScreenShotHelper.createScreenshot(f.framebufferWidth,f.framebufferHeight,f);}
    private static int difference(BufferedImage a,BufferedImage b){int n=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if((a.getRGB(x,y)&0xffffff)!=(b.getRGB(x,y)&0xffffff))n++;return n;}
    private static void floor(Minecraft mc,File out,PrintWriter csv)throws Exception{
        IBlockState state=Block.getBlockFromName("vandorlabs:turntable_surface").getDefaultState();List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(int x=0;x<29;x++)for(int z=0;z<29;z++){BlockPos p=new BlockPos(x,0,z);TileEntityTurntableSurface t=new TileEntityTurntableSurface();t.setPos(p);cells.add(new VehicleStructure.Cell(p,state,t.writeToNBT(new NBTTagCompound())));}
        VehicleWorld world=new VehicleWorld(mc.world,VehicleStructure.display(cells));world.origin=new BlockPos(0,80,0);
        TETurntableSurface renderer=new TETurntableSurface();renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);
        List<TileEntityTurntableSurface> tiles=new ArrayList<>();for(net.minecraft.tileentity.TileEntity tile:world.tiles.values())tiles.add((TileEntityTurntableSurface)tile);
        require(tiles.size()==841,"incomplete floor");TileEntityRendererDispatcher dispatcher=TileEntityRendererDispatcher.instance;net.minecraft.world.World old=dispatcher.world;dispatcher.world=world;
        Framebuffer f=new Framebuffer(320,320,true);
        try(TurntableRenderState saved=new TurntableRenderState()){
            GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-16,16,-16,16,-100,100);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.rotate(90,1,0,0);
            mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableDepth();GlStateManager.depthMask(true);
            Runnable before=()->{for(TileEntityTurntableSurface t:tiles)renderer.render(t,t.getPos().getX()-14,0,t.getPos().getZ()-14,1,-1,1);};
            Runnable after=()->{mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.disableBlend();GlStateManager.enableDepth();GlStateManager.depthMask(true);BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);b.begin(GL11.GL_QUADS,net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK);for(TileEntityTurntableSurface t:tiles)renderer.renderTileEntityFast(t,t.getPos().getX()-14,0,t.getPos().getZ()-14,1,-1,1,b);require(b.getVertexCount()>0,"empty floor batch");Tessellator.getInstance().draw();};
            BufferedImage a=image(mc,f,before),b=image(mc,f,after);int different=difference(a,b);
            javax.imageio.ImageIO.write(a,"png",new File(out,"density-floor-before.png"));javax.imageio.ImageIO.write(b,"png",new File(out,"density-floor-after.png"));
            require(different==0,"floor pixels changed="+different);
            long[] baseline=sample(before),batched=sample(after);csv.println("floor,841,1682,1,"+baseline[0]+","+batched[0]+","+baseline[1]+","+batched[1]+","+different);csv.flush();
            // Real client-world tile checks exercise dispatcher selection and all six render bounds.
            for(EnumFacing face:EnumFacing.values()){
                BlockPos p=mc.player.getPosition().up(25);IBlockState placed=state.withProperty(com.vandorlabs.blocks.BlockTurntableSurface.FACING,face);
                mc.world.setBlockState(p,placed,2);TileEntityTurntableSurface nativeTile=(TileEntityTurntableSurface)mc.world.getTileEntity(p);nativeTile.handleUpdateTag(nativeTile.writeToNBT(new NBTTagCompound()));
                require(nativeTile.hasFastRenderer(),"native unowned tile missing fast renderer face="+face);
                net.minecraft.util.math.AxisAlignedBB box=nativeTile.getRenderBoundingBox();require(box.maxX-box.minX<1.02&&box.maxY-box.minY<1.02&&box.maxZ-box.minZ<1.02,"oversized single surface bounds "+face);
                nativeTile.owner=p.down();nativeTile.ownerKey=UUID.randomUUID();require(!nativeTile.hasFastRenderer(),"claimed surface entered single batch");mc.world.setBlockToAir(p);
            }
            System.out.println("[vandorlabs][reprolab] dense-floor PASS blocks=841 calls=1682->1 pixel-difference="+different+" before-ns="+baseline[0]+" after-ns="+batched[0]+" all-six-bounds claimed-fallback");
        }finally{GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);dispatcher.world=old;f.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static int significantDifference(BufferedImage a,BufferedImage b){int n=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++){int p=a.getRGB(x,y),q=b.getRGB(x,y);if(Math.abs((p>>16&255)-(q>>16&255))>3||Math.abs((p>>8&255)-(q>>8&255))>3||Math.abs((p&255)-(q&255))>3)n++;}return n;}
    private static void glass(Minecraft mc,File out,PrintWriter csv)throws Exception{
        int pass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();TileEntityRendererDispatcher dispatcher=TileEntityRendererDispatcher.instance;net.minecraft.world.World old=dispatcher.world;
        double oldX=dispatcher.entityX,oldY=dispatcher.entityY,oldZ=dispatcher.entityZ;boolean oldBatch=GlazingRenderOrder.batchPanes;
        Framebuffer f=new Framebuffer(320,320,true);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-7,7,-7,7,-100,100);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            mc.entityRenderer.disableLightmap();GlStateManager.disableFog();GlStateManager.enableTexture2D();GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.disableLighting();
            int screen=-1;for(int i=0;i<ScreenHousingTextures.BUILTIN_COUNT;i++)if(ScreenHousingTextures.visible(i)&&"Screens".equals(ScreenHousingTextures.category(i))){screen=i;break;}
            require(screen>=0,"missing static screen fixture");
            int copied=CustomBlockMaterials.identifier(net.minecraft.init.Blocks.IRON_BLOCK,0);
            for(String profile:new String[]{"stock","material","screen","mixed"})for(boolean rotated:new boolean[]{false,true})for(int depth=0;depth<=2;depth++){
                String scene=profile+"-"+rotated+"-"+depth;
                IBlockState state=Block.getBlockFromName("vandorlabs:programmable_glass").getDefaultState().withProperty(com.vandorlabs.blocks.BlockGlassWall.ROTATED,rotated).withProperty(com.vandorlabs.blocks.BlockGlassWall.DEPTH,depth);
                List<VehicleStructure.Cell> cells=new ArrayList<>();
                for(int layer=0;layer<3;layer++)for(int x=0;x<6;x++)for(int y=0;y<12;y++){
                    BlockPos p=rotated?new BlockPos(layer*2,y,x):new BlockPos(x,y,layer*2);TileEntityProgrammableGlass t=new TileEntityProgrammableGlass();t.setPos(p);t.setAppearance(layer%3,false);
                    t.setShade((x+6*y+7*layer)%GlassTextures.IDS.length);t.setTextureLayout(depth);
                    int kind=profile.equals("mixed")?layer:profile.equals("material")?1:profile.equals("screen")?2:0;
                    if(kind!=0)t.setMaterial(kind==1?copied:screen);
                    t.glassPlayback.set(1,1);t.setPanelFacing(rotated?(layer%2==0?EnumFacing.WEST:EnumFacing.EAST):(layer%2==0?EnumFacing.NORTH:EnumFacing.SOUTH));
                    cells.add(new VehicleStructure.Cell(p,state,t.writeToNBT(new NBTTagCompound())));
                }
                VehicleWorld world=new VehicleWorld(mc.world,VehicleStructure.display(cells));world.origin=new BlockPos(0,80,0);dispatcher.world=world;dispatcher.entityX=rotated?-6:3;dispatcher.entityY=6;dispatcher.entityZ=rotated?3:-6;
                GlStateManager.loadIdentity();GlStateManager.rotate(8,1,0,0);if(rotated)GlStateManager.rotate(90,0,1,0);GlStateManager.translate(-3,-6,-3);
                List<net.minecraft.tileentity.TileEntity> tiles=new ArrayList<>(world.tiles.values());
                Runnable draw=()->{
                    GlazingRenderOrder.begin(world);
                    for(int rp=0;rp<2;rp++){net.minecraftforge.client.ForgeHooksClient.setRenderPass(rp);for(net.minecraft.tileentity.TileEntity tile:tiles)if(tile.shouldRenderInPass(rp))dispatcher.render(tile,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ(),1,-1,1);}
                    GlazingRenderOrder.end();
                };
                GlazingRenderOrder.batchPanes=false;BufferedImage before=image(mc,f,draw);long[] baseline=sample(draw);
                GlazingRenderOrder.batchPanes=true;BufferedImage after=image(mc,f,draw);int difference=significantDifference(before,after);require(difference==0,"glass batching changed pixels scene="+scene+" difference="+difference);
                require(GlassPaneBatch.panes==216&&GlassPaneBatch.draws==1,"glass did not batch actual ordered dispatcher scene="+scene+" panes="+GlassPaneBatch.panes+" draws="+GlassPaneBatch.draws);
                long[] batched=sample(draw);csv.println("glass-"+scene+",216,216,1,"+baseline[0]+","+batched[0]+","+baseline[1]+","+batched[1]+","+difference);csv.flush();
                javax.imageio.ImageIO.write(before,"png",new File(out,"density-glass-"+scene+"-before.png"));javax.imageio.ImageIO.write(after,"png",new File(out,"density-glass-"+scene+"-after.png"));
                System.out.println("[vandorlabs][reprolab] dense-glass PASS scene="+scene+" calls=216->1 pixel-difference="+difference+" before-ns="+baseline[0]+" after-ns="+batched[0]);
                TEAnimatedScreenSelector.unloadGroups(world);GlassSurfaceGroups.unload(world);
            }
        }finally{GlazingRenderOrder.batchPanes=oldBatch;GlazingRenderOrder.end();net.minecraftforge.client.ForgeHooksClient.setRenderPass(pass);dispatcher.world=old;dispatcher.entityX=oldX;dispatcher.entityY=oldY;dispatcher.entityZ=oldZ;GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);f.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static void panelsAndRamps(Minecraft mc,File out,PrintWriter csv)throws Exception{
        NBTTagCompound saved;try(InputStream in=DenseRenderChecks.class.getResourceAsStream("/assets/vandorlabs/fixtures/dense-panels-ramps-2.7.nbt")){saved=net.minecraft.nbt.CompressedStreamTools.readCompressed(in);}
        net.minecraft.nbt.NBTTagList list=saved.getTagList("Cells",10);List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(int i=0;i<list.tagCount();i++){NBTTagCompound c=list.getCompoundTagAt(i);Block b=Block.getBlockFromName(c.getString("Block"));cells.add(new VehicleStructure.Cell(new BlockPos(c.getInteger("x"),c.getInteger("y"),c.getInteger("z")),b.getStateFromMeta(c.getInteger("Meta")),c.getCompoundTag("Tile")));}
        VehicleWorld world=new VehicleWorld(mc.world,VehicleStructure.display(cells));world.origin=new BlockPos(0,80,0);
        List<TileEntityAnimatedScreenSelector> panels=new ArrayList<>();List<TileEntityControlledRamp> ramps=new ArrayList<>();
        for(net.minecraft.tileentity.TileEntity tile:world.tiles.values())if(tile instanceof TileEntityAnimatedScreenSelector)panels.add((TileEntityAnimatedScreenSelector)tile);else if(tile instanceof TileEntityControlledRamp)ramps.add((TileEntityControlledRamp)tile);
        require(panels.size()>400&&ramps.size()==78,"incomplete saved material fixture panels="+panels.size()+" ramps="+ramps.size());
        TEAnimatedScreenSelector panelRenderer=new TEAnimatedScreenSelector();TEControlledRamp rampRenderer=new TEControlledRamp();TileEntityRendererDispatcher dispatcher=TileEntityRendererDispatcher.instance;net.minecraft.world.World previous=dispatcher.world;dispatcher.world=world;
        panelRenderer.setRendererDispatcher(dispatcher);rampRenderer.setRendererDispatcher(dispatcher);int previousPass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();net.minecraftforge.client.ForgeHooksClient.setRenderPass(0);
        Framebuffer f=new Framebuffer(480,480,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-34,34,-34,34,-200,200);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){
            GlStateManager.loadIdentity();GlStateManager.rotate(30,1,0,0);GlStateManager.rotate(30,0,1,0);GlStateManager.translate(-10,-6,-26);mc.entityRenderer.disableLightmap();GlStateManager.disableFog();GlStateManager.enableTexture2D();GlStateManager.enableDepth();GlStateManager.depthMask(true);
            Runnable panelBefore=()->{for(TileEntityAnimatedScreenSelector tile:panels)panelRenderer.render(tile,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ(),1,-1,1);};
            Runnable panelAfter=()->{mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.disableBlend();BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);b.begin(GL11.GL_QUADS,net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK);for(TileEntityAnimatedScreenSelector tile:panels){require(tile.hasFastRenderer(),"saved diagonal missing fast path");panelRenderer.renderTileEntityFast(tile,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ(),1,-1,1,b);}Tessellator.getInstance().draw();};
            BufferedImage a=image(mc,f,panelBefore),b=image(mc,f,panelAfter);int delta=significantDifference(a,b);javax.imageio.ImageIO.write(a,"png",new File(out,"density-panels-before.png"));javax.imageio.ImageIO.write(b,"png",new File(out,"density-panels-after.png"));require(delta<100,"saved diagonal panel pixels changed="+delta);
            long[] before=sample(panelBefore),after=sample(panelAfter);csv.println("panels,"+panels.size()+","+panels.size()+",1,"+before[0]+","+after[0]+","+before[1]+","+after[1]+","+delta);csv.flush();
            System.out.println("[vandorlabs][reprolab] dense-panels PASS blocks="+panels.size()+" calls="+panels.size()+"->1 pixels="+delta+" before-ns="+before[0]+" after-ns="+after[0]);
            Runnable rampDraw=()->{for(TileEntityControlledRamp tile:ramps)rampRenderer.render(tile,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ(),1,-1,1);};
            RampRenderMeshes.enabled=false;a=image(mc,f,rampDraw);before=sample(rampDraw);RampRenderMeshes.enabled=true;b=image(mc,f,rampDraw);after=sample(rampDraw);delta=significantDifference(a,b);
            javax.imageio.ImageIO.write(a,"png",new File(out,"density-ramps-before.png"));javax.imageio.ImageIO.write(b,"png",new File(out,"density-ramps-after.png"));require(delta<100,"saved ramp cached pixels changed="+delta);
            csv.println("ramps,"+ramps.size()+","+ramps.size()+","+ramps.size()+","+before[0]+","+after[0]+","+before[1]+","+after[1]+","+delta);csv.flush();
            for(TileEntityControlledRamp tile:ramps){require(RampRenderMeshes.eligible(tile,1),"saved static ramp not cached");tile.markDirty();}
            BufferedImage changed=image(mc,f,rampDraw);require(significantDifference(b,changed)==0,"revision invalidation changed ramp");RampRenderMeshes.clear();image(mc,f,rampDraw);require(significantDifference(b,image(mc,f,rampDraw))==0,"reload invalidation changed ramp");
            System.out.println("[vandorlabs][reprolab] dense-ramps PASS blocks="+ramps.size()+" pixels="+delta+" before-ns="+before[0]+" after-ns="+after[0]+" revision resource invalidation");
        }finally{RampRenderMeshes.enabled=true;RampRenderMeshes.clear();net.minecraftforge.client.ForgeHooksClient.setRenderPass(previousPass);dispatcher.world=previous;GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);f.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);TEAnimatedScreenSelector.unloadGroups(world);}
    }
    static void run(Minecraft mc,File out)throws Exception{
        if(!ALLOC.isThreadAllocatedMemoryEnabled())ALLOC.setThreadAllocatedMemoryEnabled(true);
        try(PrintWriter csv=new PrintWriter(new File(out,"dense-render-costs.csv"))){csv.println("kind,blocks,before_calls,after_calls,before_ns,after_ns,before_bytes,after_bytes,pixel_difference");floor(mc,out,csv);glass(mc,out,csv);CollisionOwnerChecks.run(mc,out);panelsAndRamps(mc,out,csv);CanopyWeatherChecks.run(mc,out);}
        System.out.println("[vandorlabs][reprolab] dense-render PASS");
    }
    private DenseRenderChecks(){}
}
