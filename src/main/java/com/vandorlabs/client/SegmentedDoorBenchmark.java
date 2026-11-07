package com.vandorlabs.client;

import com.vandorlabs.segmented.*;
import com.vandorlabs.client.segmented.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import java.io.*;
import java.awt.image.BufferedImage;
import java.util.*;
import org.lwjgl.opengl.GL11;

/** Same-client native renderer comparison. Reference JARs are explicit optional test inputs. */
final class SegmentedDoorBenchmark {
    private static final com.sun.management.ThreadMXBean ALLOC=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
    private static void require(boolean b,String s){if(!b)throw new IllegalStateException("segmented benchmark: "+s);}
    static void contracts(Minecraft mc,File output)throws Exception{
        require(mc.getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/segmented/segmented_panel")!=mc.getTextureMapBlocks().getMissingSprite(),"segmented panel atlas sprite missing");
        for(int op=0;op<3;op++){
            io.netty.buffer.ByteBuf encoded=io.netty.buffer.Unpooled.buffer();new SegmentedDoorMessage(7,op,10,true,true,ChannelList.of(17,27,37)).toBytes(encoded);require(encoded.readableBytes()==20,"unexpected settings packet size");byte[] bytes=new byte[encoded.readableBytes()];encoded.getBytes(0,bytes);SegmentedDoorMessage decoded=new SegmentedDoorMessage();decoded.fromBytes(encoded);io.netty.buffer.ByteBuf round=io.netty.buffer.Unpooled.buffer();decoded.toBytes(round);require(Arrays.equals(bytes,io.netty.buffer.ByteBufUtil.getBytes(round)),"settings packet did not round trip");encoded.release();round.release();
            for(int field:new int[]{4,5,6,7,20}){
                byte[] malformed=Arrays.copyOf(bytes,field==20?21:20);if(field<20)malformed[field]=(byte)(field==4?3:field==5?4:255);boolean rejected=false;try{new SegmentedDoorMessage().fromBytes(io.netty.buffer.Unpooled.wrappedBuffer(malformed));}catch(IllegalArgumentException expected){rejected=true;}require(rejected,"malformed settings field accepted "+field);
            }
        }
        io.netty.buffer.ByteBuf texturePacket=io.netty.buffer.Unpooled.buffer();new SegmentedDoorMessage(7,0,10,true,true,ChannelList.of(17,27,37)).texture(0).toBytes(texturePacket);require(texturePacket.readableBytes()==24,"texture extension byte count");SegmentedDoorMessage textureDecoded=new SegmentedDoorMessage();textureDecoded.fromBytes(texturePacket);io.netty.buffer.ByteBuf textureRound=io.netty.buffer.Unpooled.buffer();textureDecoded.toBytes(textureRound);require(java.util.Arrays.equals(io.netty.buffer.ByteBufUtil.getBytes(texturePacket,0,24),io.netty.buffer.ByteBufUtil.getBytes(textureRound)),"texture settings round trip");texturePacket.release();textureRound.release();
        SegmentedDoorTile tile=new SegmentedDoorTile();tile.setWorld(mc.world);tile.setPos(new BlockPos(8000,180,8000));tile.height=5;tile.duration=50;tile.started=mc.world.getTotalWorldTime();
        require(SegmentedDoorVisuals.sample(tile,0)==0,"initial closed pose");tile.targetOpen=true;tile.sequence=1;require(SegmentedDoorVisuals.sample(tile,0)==0,"new target jumped");double first=SegmentedDoorVisuals.sample(tile,10);require(first>.19&&first<.21,"local clock did not advance");tile.started-=100000;tile.from=1;require(Math.abs(SegmentedDoorVisuals.sample(tile,20)-.4)<1e-6,"repeated target or corrected server time restarted motion");tile.sequence=2;tile.targetOpen=false;require(Math.abs(SegmentedDoorVisuals.sample(tile,20)-.4)<1e-6,"visual reversal jumped");require(SegmentedDoorVisuals.sample(tile,25)<.4,"reverse did not advance");tile.sequence=1;tile.targetOpen=true;require(SegmentedDoorVisuals.sample(tile,30)<.3,"out-of-order target reversed motion");tile.sequence=3;tile.targetOpen=false;NBTTagCompound stale=tile.writeToNBT(new NBTTagCompound());stale.setLong("SegmentedSequence",2);stale.setBoolean("SegmentedOpen",true);tile.onDataPacket(null,new net.minecraft.network.play.server.SPacketUpdateTileEntity(tile.getPos(),0,stale));require(!tile.targetOpen&&tile.sequence==3,"stale packet installed");
        com.vandorlabs.animation.VisualClock clock=new com.vandorlabs.animation.VisualClock();clock.sample(1,false);clock.sample(1000000001L,true);clock.sample(2000000001L,false);require(clock.sample(2050000001L,false)==1,"pause/resume advanced during pause");
        cache(mc);bounds(mc);icon(mc,output);
        System.out.println("[vandorlabs][reprolab] segmented-contracts PASS packet-bytes=20 malformed-lengths settings-roundtrip repeated-delayed-out-of-order reversed-local-clock pause");
    }
    private static final class Scene implements AutoCloseable {
        final VehicleWorld world;final List<TileEntity> tiles=new ArrayList<>();final boolean vandor;final Minecraft mc;VertexBuffer headers;VertexFormat format;int roots;final List<Object> timers=new ArrayList<>();java.lang.reflect.Method setTimer;
        Scene(Minecraft mc,Block block,boolean vandor)throws Exception{this(mc,block,vandor,EnumFacing.NORTH,1);}
        Scene(Minecraft mc,Block block,boolean vandor,EnumFacing facing)throws Exception{this(mc,block,vandor,facing,1);}
        Scene(Minecraft mc,Block block,boolean vandor,EnumFacing facing,int count)throws Exception{
            this.mc=mc;this.vandor=vandor;IBlockState state=block.getDefaultState();if(vandor)state=state.withProperty(SegmentedDoorBlock.FACING,facing);
            List<VehicleStructure.Cell> cells=new ArrayList<>();for(int g=0;g<count;g++)for(int x=0;x<9;x++)for(int y=0;y<5;y++)cells.add(new VehicleStructure.Cell(new BlockPos(g%8*12+x,y,g/8*8),state,null));world=new VehicleWorld(mc.world,VehicleStructure.display(cells));world.origin=new BlockPos(0,80,0);
            for(VehicleStructure.Cell cell:world.structure.cells){TileEntity tile=block.createTileEntity(world,cell.state);require(tile!=null,"reference tile factory unavailable");tile.setWorld(world);tile.setPos(cell.pos);
                if(vandor){SegmentedDoorTile door=(SegmentedDoorTile)tile;door.topOffset=4-cell.pos.getY();door.height=5;door.formed=true;door.readFromNBT(door.writeToNBT(new NBTTagCompound()));}
                world.tiles.put(cell.pos,tile);tiles.add(tile);
            }
        }
        private void terrain(){
            if(headers!=null)headers.deleteGlBuffers();net.minecraft.util.BlockRenderLayer layer=net.minecraftforge.client.MinecraftForgeClient.getRenderLayer();
            try{net.minecraftforge.client.ForgeHooksClient.setRenderLayer(BlockRenderLayer.SOLID);BufferBuilder b=new BufferBuilder(16384);format=new VertexFormat(DefaultVertexFormats.BLOCK);b.begin(GL11.GL_QUADS,format);for(VehicleStructure.Cell cell:world.structure.cells)mc.getBlockRendererDispatcher().renderBlock(cell.state.getActualState(world,cell.pos),cell.pos,world,b);b.finishDrawing();headers=new VertexBuffer(format);headers.bufferData(b.getByteBuffer());}finally{net.minecraftforge.client.ForgeHooksClient.setRenderLayer(layer);}
        }
        @SuppressWarnings({"rawtypes","unchecked"}) void phase(String name)throws Exception{
            if(vandor){for(TileEntity raw:tiles){SegmentedDoorTile tile=(SegmentedDoorTile)raw;tile.from=name.equals("closed")?0:name.equals("open")?1:.5;tile.targetOpen=!name.equals("closed");tile.started=world.getTotalWorldTime();tile.duration=100000;tile.clientBaked=name.equals("closed");tile.clientSuppressed=tile.clientBaked;}terrain();return;}
            timers.clear();Class<? extends Enum> state=(Class<? extends Enum>)Class.forName("net.malisis.doors.DoorState");Object value=Enum.valueOf(state,name.equals("closed")?"CLOSED":name.equals("open")?"OPENED":"OPENING");
            for(TileEntity tile:tiles){tile.getClass().getMethod("setState",state).invoke(tile,value);Object timer=tile.getClass().getMethod("getTimer").invoke(tile);setTimer=timer.getClass().getMethod("setRelativeStart",long.class);setTimer.invoke(timer,name.equals("moving")?-1250L:-100000L);if(tile.getPos().getY()==4)timers.add(timer);}
        }
        void motion(double progress)throws Exception{
            if(vandor){for(TileEntity raw:tiles)if(((SegmentedDoorTile)raw).isRoot())((SegmentedDoorTile)raw).from=progress;}
            else for(Object timer:timers)setTimer.invoke(timer,-(long)(2500*progress));
        }
        void draw(Minecraft mc){
            mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableDepth();GlStateManager.depthMask(true);
            if(headers!=null)BlockMeshVbo.draw(headers,format,BlockRenderLayer.SOLID);
            TileEntityRendererDispatcher d=TileEntityRendererDispatcher.instance;d.preDrawBatch();for(TileEntity tile:tiles)if(tile.shouldRenderInPass(0))d.render(tile,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ(),1,-1,1);d.drawBatch(0);
        }
        public void close(){if(headers!=null)headers.deleteGlBuffers();}
    }
    private static void cache(Minecraft mc)throws Exception{
        boolean authored=SegmentedDoorVisuals.authored;SegmentedDoorVisuals.authored=true;SegmentedDoorRenderer renderer=new SegmentedDoorRenderer();int checks=0;
        try{for(EnumFacing facing:EnumFacing.HORIZONTALS)try(Scene scene=new Scene(mc,Block.getBlockFromName("vandorlabs:segmented_door"),true,facing)){
            SegmentedDoorTile tile=(SegmentedDoorTile)scene.world.getTileEntity(new BlockPos(0,4,0));
            for(int height:new int[]{1,5,32})for(double pose:new double[]{0,.5,1}){
                tile.height=height;tile.from=pose;tile.targetOpen=pose>0;tile.started=scene.world.getTotalWorldTime();tile.duration=100000;
                BufferBuilder reference=new BufferBuilder(4096),cached=new BufferBuilder(4096);reference.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);renderer.renderUncached(tile,.25,4,.75,0,reference);reference.finishDrawing();cached.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);renderer.renderTileEntityFast(tile,.25,4,.75,0,-1,1,cached);cached.finishDrawing();require(reference.getVertexCount()==cached.getVertexCount(),"cached topology changed");
                int stride=DefaultVertexFormats.BLOCK.getNextOffset();for(int v=0;v<reference.getVertexCount();v++)for(int byteAt=0;byteAt<stride;byteAt+=4){int at=v*stride+byteAt;if(byteAt<12)require(Math.abs(reference.getByteBuffer().getFloat(at)-cached.getByteBuffer().getFloat(at))<1e-5,"cached position changed");else require(reference.getByteBuffer().getInt(at)==cached.getByteBuffer().getInt(at),"cached vertex payload changed facing="+facing+" height="+height+" pose="+pose+" vertex="+v+" byte="+byteAt+" expected="+Integer.toHexString(reference.getByteBuffer().getInt(at))+" actual="+Integer.toHexString(cached.getByteBuffer().getInt(at)));}
                long samples=SegmentedDoorRenderer.lightSamples();BufferBuilder again=new BufferBuilder(4096);again.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);renderer.renderTileEntityFast(tile,.25,4,.75,0,-1,1,again);again.finishDrawing();require(samples==SegmentedDoorRenderer.lightSamples(),"unchanged cached column resampled light");
                mc.world.notifyLightSet(mc.player.getPosition());BufferBuilder relit=new BufferBuilder(4096);relit.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);renderer.renderTileEntityFast(tile,.25,4,.75,0,-1,1,relit);relit.finishDrawing();require(SegmentedDoorRenderer.lightSamples()-samples==height,"light update did not invalidate column");checks++;
            }
        }}finally{SegmentedDoorVisuals.authored=authored;}
        require(SegmentedDoorRenderer.retainedBytes()<=18*1024*1024,"segmented caches exceeded retained byte budget");
        System.out.println("[vandorlabs][reprolab] segmented-cache PASS cases="+checks+" all-facings heights=1,5,32 exact-vertex-payloads repeated-light-reads=0 light-event-invalidation bounded-memory");
    }
    private static void bounds(Minecraft mc)throws Exception{
        boolean authored=SegmentedDoorVisuals.authored;SegmentedDoorVisuals.authored=true;SegmentedDoorRenderer renderer=new SegmentedDoorRenderer();int cases=0;
        try{for(EnumFacing facing:EnumFacing.HORIZONTALS)try(Scene scene=new Scene(mc,Block.getBlockFromName("vandorlabs:segmented_door"),true,facing)){
            SegmentedDoorTile tile=(SegmentedDoorTile)scene.world.getTileEntity(new BlockPos(0,4,0));AxisAlignedBB bounds=tile.getRenderBoundingBox();
            for(int frame=0;frame<=20;frame++){tile.from=frame/20D;tile.targetOpen=true;tile.started=scene.world.getTotalWorldTime();tile.duration=100000;BufferBuilder b=new BufferBuilder(4096);b.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);renderer.renderTileEntityFast(tile,0,4,0,0,-1,1,b);b.finishDrawing();int stride=DefaultVertexFormats.BLOCK.getNextOffset();for(int v=0;v<b.getVertexCount();v++){int p=v*stride;require(bounds.contains(new Vec3d(b.getByteBuffer().getFloat(p),b.getByteBuffer().getFloat(p+4),b.getByteBuffer().getFloat(p+8))),"render bounds lost section facing="+facing+" frame="+frame);}cases++;}
        }}finally{SegmentedDoorVisuals.authored=authored;}
        System.out.println("[vandorlabs][reprolab] segmented-bounds PASS poses="+cases+" four-facings exact-emitted-vertices");
    }
    static void icon(Minecraft mc,File output)throws Exception{
        Framebuffer frame=new Framebuffer(32,32,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,32,32,0,1000,3000);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.translate(0,0,-2000);
        try(TurntableRenderState saved=new TurntableRenderState()){frame.bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clearColor(.12549F,.188235F,.25098F,1);GlStateManager.clear(16640);RenderHelper.enableGUIStandardItemLighting();mc.getRenderItem().renderItemAndEffectIntoGUI(new net.minecraft.item.ItemStack(Block.getBlockFromName("vandorlabs:segmented_door")),8,8);RenderHelper.disableStandardItemLighting();BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(32,32,frame);int background=image.getRGB(0,0),ink=0;for(int x=0;x<16;x++)for(int y=0;y<16;y++)if(image.getRGB(8+x,8+y)!=background){ink++;require(x>0&&x<15&&y>0&&y<15,"segmented icon touches inventory slot edge");}require(ink>20,"empty segmented icon");javax.imageio.ImageIO.write(image,"png",new File(output,"segmented-inventory-icon.png"));System.out.println("[vandorlabs][reprolab] segmented-icon PASS padded-inventory-slot");}
        finally{GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static long[] sample(Runnable work){for(int i=0;i<8;i++)work.run();long[] ns=new long[7],alloc=new long[7];for(int i=0;i<7;i++){GL11.glFinish();long before=ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId()),start=System.nanoTime();for(int k=0;k<4;k++)work.run();ns[i]=(System.nanoTime()-start)/4;alloc[i]=(ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId())-before)/4;}Arrays.sort(ns);Arrays.sort(alloc);return new long[]{ns[3],alloc[3]};}
    private static long[] animated(Scene scene,Minecraft mc)throws Exception{
        long[] ns=new long[21],alloc=new long[21];for(int frame=0;frame<33;frame++){
            scene.motion(.2+frame*.015);GL11.glFinish();long bytes=ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId()),start=System.nanoTime();scene.draw(mc);if(frame>=12){ns[frame-12]=System.nanoTime()-start;alloc[frame-12]=ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId())-bytes;}
        }Arrays.sort(ns);Arrays.sort(alloc);return new long[]{ns[10],alloc[10]};
    }
    static void run(Minecraft mc,File output)throws Exception{
        String referenceId=System.getProperty("vandorlabs.referenceDoorBlock","");Block reference=Block.getBlockFromName(referenceId);require(reference!=null&&!referenceId.isEmpty(),"Supply vandorlabs.referenceDoorBlock for the installed comparison mod");if(!ALLOC.isThreadAllocatedMemoryEnabled())ALLOC.setThreadAllocatedMemoryEnabled(true);
        TileEntityRendererDispatcher d=TileEntityRendererDispatcher.instance;net.minecraft.world.World previous=d.world;double oldX=d.entityX,oldY=d.entityY,oldZ=d.entityZ;int pass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();boolean authored=SegmentedDoorVisuals.authored;
        Framebuffer f=new Framebuffer(480,320,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-6,6,-4,4,-100,100);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(PrintWriter csv=new PrintWriter(new File(output,"segmented-render-comparison.csv"));TurntableRenderState saved=new TurntableRenderState()){
            SegmentedDoorVisuals.authored=true;net.minecraftforge.client.ForgeHooksClient.setRenderPass(0);d.entityX=4.5;d.entityY=2.5;d.entityZ=-10;f.bindFramebuffer(true);mc.entityRenderer.disableLightmap();GlStateManager.disableFog();
            csv.println("phase,blocks,malisis_ns,vandor_ns,speedup,malisis_alloc_bytes,vandor_alloc_bytes");
            for(int count:new int[]{1,64})try(Scene malisis=new Scene(mc,reference,false,EnumFacing.NORTH,count);Scene ours=new Scene(mc,Block.getBlockFromName("vandorlabs:segmented_door"),true,EnumFacing.NORTH,count)){
            for(String phase:new String[]{"closed","moving","open","animating"}){
                malisis.phase(phase.equals("animating")?"moving":phase);ours.phase(phase.equals("animating")?"moving":phase);GlStateManager.loadIdentity();if(count==64){GlStateManager.scale(.095,.095,.095);GlStateManager.rotate(25,1,0,0);GlStateManager.rotate(12,0,1,0);GlStateManager.translate(-46,-3,-28);}else{GlStateManager.rotate(15,1,0,0);GlStateManager.rotate(12,0,1,0);GlStateManager.translate(-4.5,-2.5,-1);}
                long[][] result=new long[2][];Scene[] scenes={malisis,ours};for(int i=0;i<scenes.length;i++){
                    final Scene scene=scenes[i];if(phase.equals("animating"))scene.motion(.35);d.world=scene.world;mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);f.bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clearColor(.1F,.16F,.23F,1);GlStateManager.clear(16640);scene.draw(mc);BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(480,320,f);int ink=0,bg=image.getRGB(0,0);for(int rgb:image.getRGB(0,0,480,320,null,0,480))if(rgb!=bg)ink++;require(ink>100,"empty segmented capture "+phase+" vandor="+scene.vandor);javax.imageio.ImageIO.write(image,"png",new File(output,"segmented-"+(scene.vandor?"vandor":"malisis")+"-"+count+"-"+phase+".png"));result[i]=phase.equals("animating")?animated(scene,mc):sample(()->scene.draw(mc));
                }
                double speedup=result[0][0]/(double)result[1][0];csv.println(phase+","+(count*45)+","+result[0][0]+","+result[1][0]+","+speedup+","+result[0][1]+","+result[1][1]);csv.flush();require(speedup>1,"segmented render regressed in "+phase+" count="+count+": "+speedup);System.out.println("[vandorlabs][reprolab] segmented-render PASS phase="+phase+" blocks="+(count*45)+" speedup="+speedup+" malisis-ns="+result[0][0]+" vandor-ns="+result[1][0]+" allocations="+result[0][1]+"->"+result[1][1]);
            }}
        }finally{SegmentedDoorVisuals.authored=authored;net.minecraftforge.client.ForgeHooksClient.setRenderPass(pass);d.world=previous;d.entityX=oldX;d.entityY=oldY;d.entityZ=oldZ;GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);f.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private SegmentedDoorBenchmark(){}
}
