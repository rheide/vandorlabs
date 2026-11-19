package com.vandorlabs.client;

import com.vandorlabs.navigation.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.network.*;
import com.vandorlabs.blocks.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import io.netty.buffer.*;
import java.io.File;
import java.awt.image.BufferedImage;
import java.util.*;
import org.lwjgl.opengl.GL11;

/** Feature assertions on the actual player table plus exact renderer pixels, not gallery-only captures. */
final class NavigationEnhancementChecks {
    private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("navigation enhancements: "+why);}
    private static Object field(Object o,String name)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void resetAction(NavigationService.View view){
        try{set(view,"lastAction",-100L);}catch(Exception failure){throw new IllegalStateException(failure);}
    }
    private static void set(Object o,String name,Object value)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    static void run(Minecraft mc,File out,TileEntityNavigationTable root)throws Exception{
        mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);WorldServer w=player.getServerWorld();TileEntityNavigationTable t=((TileEntityNavigationTable)w.getTileEntity(root.getPos())).reader();NavigationService.View v=NavigationService.view(t);ItemStack pin=t.getStackInSlot(0).copy();int oldX=v.x,oldZ=v.z,oldZoom=v.zoom;
            v.x+=500;NavigationService.action(player,t,0,8);need(v.x==NavigationChip.value(pin,"X")&&v.z==NavigationChip.value(pin,"Z"),"CTR returns to saved pin");
            t.removeStackFromSlot(0);resetAction(v);NavigationService.action(player,t,0,8);need(v.x==Math.round(player.posX)&&v.z==Math.round(player.posZ),"CTR without pin returns to authoritative player");
            t.setInventorySlotContents(0,pin);v=NavigationService.view(t);NavigationCanvas canvas=NavigationViewport.canvas(t,0);int coarse=canvas.gridWidth(v.zoom);for(int i=0;i<4;i++){resetAction(v);NavigationService.action(player,t,0,5);}need(canvas.gridWidth(v.zoom)>coarse,"deep zoom increases raster detail");
            v.x=oldX;v.z=oldZ;v.zoom=oldZoom;
            int chunks=w.getChunkProvider().getLoadedChunkCount();for(int x=0;x<16;x++)for(int z=0;z<16;z++)NavigationService.color(w,12000000+x,12000000+z,true);need(w.getChunkProvider().getLoadedChunkCount()==chunks,"fine seed sampling never loads chunks");
            long hits=NavigationService.rasterHits,samples=NavigationService.sampledPixels;NavigationService.request(player,t.getPos(),0,1,16,0);need(NavigationService.rasterHits>hits&&NavigationService.sampledPixels==samples,"unchanged view reuses completed server raster");
            BlockPos shared=t.getPos().north(4);net.minecraft.block.Block sharedBlock=net.minecraft.block.Block.getBlockFromName("vandorlabs:navigation_table_flat");for(int x=0;x<2;x++)for(int z=0;z<2;z++)w.setBlockState(shared.add(x,0,z),sharedBlock.getDefaultState(),2);NavigationAssemblies.rebuild(w,shared);TileEntityNavigationTable other=((TileEntityNavigationTable)w.getTileEntity(shared)).reader();other.configureDisplay(t.screenSettings(),true,127,0,t.getRedstoneChannels());other.setInventorySlotContents(0,pin);NavigationService.View otherView=NavigationService.view(other);otherView.x=oldX;otherView.z=oldZ;otherView.zoom=oldZoom;long terrain=NavigationService.terrainSamples(),sharedHits=NavigationService.rasterHits;NavigationService.request(player,other.getPos(),0,1234,16,0);need(NavigationService.rasterHits>sharedHits&&NavigationService.terrainSamples()==terrain,"another table reuses the same geographic view without generating terrain");other.removeStackFromSlot(0);for(int x=0;x<2;x++)for(int z=0;z<2;z++)w.setBlockToAir(shared.add(x,0,z));
            try{java.lang.reflect.Field f=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.world.WorldProvider.class,"biomeProvider","field_76578_c");Object provider=f.get(w.provider);try{f.set(w.provider,new net.minecraft.world.biome.BiomeProvider(){@Override public net.minecraft.world.biome.Biome[] getBiomes(net.minecraft.world.biome.Biome[] a,int x,int z,int width,int height,boolean cache){return new net.minecraft.world.biome.Biome[]{(x&1)==0?net.minecraft.init.Biomes.PLAINS:net.minecraft.init.Biomes.OCEAN};}});need(NavigationService.color(w,14000001,14000000,true)!=NavigationService.color(w,14000002,14000000,true),"deep sampling retains one-block biome boundaries");need(NavigationService.color(w,14001001,14001000)==NavigationService.color(w,14001002,14001000),"default sampling retains coarse density");}finally{f.set(w.provider,provider);}}catch(IllegalAccessException failure){throw new IllegalStateException(failure);}
            BlockPos extra=t.getPos().east(8).south(4);net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:navigation_table_flat");w.setBlockState(extra.east(),block.getDefaultState(),2);NavigationAssemblies.rebuild(w,extra.east());TileEntityNavigationTable old=(TileEntityNavigationTable)w.getTileEntity(extra.east());old.setInventorySlotContents(0,pin);NavigationService.View browsed=NavigationService.view(old);browsed.x=3456;browsed.z=-789;browsed.zoom=2;w.setBlockState(extra,block.getDefaultState(),2);NavigationAssemblies.rebuild(w,extra);TileEntityNavigationTable grown=((TileEntityNavigationTable)w.getTileEntity(extra)).reader();NavigationService.View retained=NavigationService.view(grown);need(retained.x==3456&&retained.z==-789&&retained.zoom==2,"expanding preserves view center and zoom when owner moves");grown.removeStackFromSlot(0);w.setBlockToAir(extra);w.setBlockToAir(extra.east());
        }).get();
        NavigationMapRenderer.bind(root,0,1);@SuppressWarnings("unchecked") Map<BlockPos,?> entries=(Map<BlockPos,?>)staticField(NavigationMapRenderer.class,"IMAGES");Object e=entries.get(root.getPos());MessageNavigationImage original=(MessageNavigationImage)field(e,"data");need(original!=null,"actual RPC image available");
        DynamicBackup backup=new DynamicBackup(e,original);int serial=(Integer)field(e,"serial");MessageNavigationImage mock=new MessageNavigationImage(root.getPos(),0,serial,(int)Math.round(mc.player.posX),(int)Math.round(mc.player.posZ),0,16,false,ItemStack.EMPTY,true,0,new int[96*96]);Arrays.fill(mock.pixels,0xff568b51);mock.hasChip=true;mock.marker=true;mock.chipX=mock.x+12;mock.chipZ=mock.z+6;
        mock.overlay=new com.vandorlabs.navigation.NavigationOverlay(new int[]{mock.x-25,mock.z,-18,mock.x+25,mock.z,18},new int[]{mock.x-20,mock.z-14});
        NavigationMapRenderer.receive(mock);NavigationMapRenderer.changed(root.getPos(),2);need((Boolean)field(e,"interactionPending")&&(Boolean)field(e,"pending"),"local click starts loading before server reply");need((NavigationMotion.pulse(20)&255)==88,"local loading starts at bright phase");set(e,"waiting",true);double clock=(Double)staticField(NavigationMapRenderer.class,"visualTicks");com.vandorlabs.navigation.NavigationMotion motion=(com.vandorlabs.navigation.NavigationMotion)field(e,"motion");motion.snap(mock.x,mock.z,NavigationViewport.canvas(root,0).scale(mock.zoom));motion.move(mock.x+32,mock.z,NavigationViewport.canvas(root,0).scale(mock.zoom),0);setStatic(NavigationMapRenderer.class,"visualTicks",0D);
        BufferedImage a=render(mc,root),b;setStatic(NavigationMapRenderer.class,"visualTicks",2D);b=render(mc,root);setStatic(NavigationMapRenderer.class,"visualTicks",clock);
        int cyan=0,yellow=0,diff=0,green=0,markers=0,arrayRing=0,dishRing=0;for(int y=0;y<512;y++)for(int x=0;x<512;x++){int color=a.getRGB(x,y),r=color>>16&255,g=color>>8&255,blue=color&255;if(g>r*1.25&&g>blue*1.25&&g>70)green++;if(r<110&&g>185&&blue>185)cyan++;if(r>210&&g>160&&blue<110)yellow++;if(r>210&&g<195&&blue>220)markers++;if(r>130&&r>g*1.6&&r>blue*1.6)arrayRing++;if(g>110&&g>r*1.5&&blue>r*1.6&&Math.abs(g-blue)<40)dishRing++;if(a.getRGB(x,y)!=b.getRGB(x,y))diff++;}
        border(a,e,root,true);border(a,e,root,false);
        need(green>1000,"loading backdrop cannot occlude textured terrain");need(cyan>12,"cyan player marker visible");need(yellow>12,"saved pin remains distinct");need(markers>8&&arrayRing>12&&dishRing>12,"live overlay renders remote player marker and observer outline "+markers+"/"+arrayRing+"/"+dishRing);need(diff>8,"map pans over pulsing unloaded space without re-uploading terrain");javax.imageio.ImageIO.write(a,"png",new File(out,"navigation-enhancements-scanning-a.png"));javax.imageio.ImageIO.write(b,"png",new File(out,"navigation-enhancements-scanning-b.png"));
        backup.restore(e,(Integer)field(e,"serial"));protocol(root.getPos());
        NavigationQueuedCacheChecks.run(mc,root);
        NavigationMapRenderer.bind(root,0,1);Object restored=entries.get(root.getPos());backup.restore(restored,(Integer)field(restored,"serial"));
        System.out.println("[vandorlabs] navigation-enhancements PASS CTR pin/player fixed pixel scale fine no-chunk sampling raster cache player-pixels="+cyan+" pin-pixels="+yellow+" busy-pixel-difference="+diff);
    }
    private static void border(BufferedImage image,Object entry,TileEntityNavigationTable root,boolean top)throws Exception{
        NavigationCanvas canvas=(NavigationCanvas)field(entry,"canvas");double x=canvas.width-3,y=top?3:canvas.height-3;
        double wx=(Double)field(entry,"ox")+(Double)field(entry,"ux")*x+(Double)field(entry,"vx")*y,wz=(Double)field(entry,"oz")+(Double)field(entry,"uz")*x+(Double)field(entry,"vz")*y;
        NavigationLayout.Screen s=root.navigationPlane();double minX=Math.min(s.topLeft.x,s.topRight.x)-root.getPos().getX(),maxX=Math.max(s.topLeft.x,s.topRight.x)-root.getPos().getX(),minZ=Math.min(s.topLeft.z,s.bottomLeft.z)-root.getPos().getZ(),maxZ=Math.max(s.topLeft.z,s.bottomLeft.z)-root.getPos().getZ();
        int px=(int)((wx-minX+.125)/(maxX-minX+.25)*512),py=(int)((wz-minZ+.125)/(maxZ-minZ+.25)*512),color=image.getRGB(px,py);int r=color>>16&255,g=color>>8&255,b=color&255;
        need(r>=24&&r<=88&&Math.abs(r-g)<2&&Math.abs(g-b)<2,"loading pulse covers "+(top?"header":"footer")+" background: "+Integer.toHexString(color)+" at "+px+","+py);
    }
    private static final class DynamicBackup {
        final MessageNavigationImage packet;final int[] pixels;
        DynamicBackup(Object e,MessageNavigationImage original)throws Exception{packet=new MessageNavigationImage();for(java.lang.reflect.Field f:MessageNavigationImage.class.getFields())if(!java.lang.reflect.Modifier.isStatic(f.getModifiers()))f.set(packet,f.get(original));pixels=((net.minecraft.client.renderer.texture.DynamicTexture)field(e,"texture")).getTextureData().clone();packet.pixels=pixels;}
        void restore(Object e,int serial){packet.serial=serial;NavigationMapRenderer.receive(packet);}
    }
    private static BufferedImage render(Minecraft mc,TileEntityNavigationTable root){
        Framebuffer f=new Framebuffer(512,512,true);NavigationLayout.Screen s=root.navigationPlane();double minX=Math.min(s.topLeft.x,s.topRight.x)-root.getPos().getX(),maxX=Math.max(s.topLeft.x,s.topRight.x)-root.getPos().getX(),minZ=Math.min(s.topLeft.z,s.bottomLeft.z)-root.getPos().getZ(),maxZ=Math.max(s.topLeft.z,s.bottomLeft.z)-root.getPos().getZ();
        try(TurntableRenderState saved=new TurntableRenderState()){
            f.bindFramebuffer(true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(minX-.125,maxX+.125,-maxZ-.125,-minZ+.125,-100,100);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.rotate(90,1,0,0);GlStateManager.clearColor(.08F,.1F,.12F,1);GlStateManager.depthMask(true);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);Tessellator.getInstance().getBuffer().setTranslation(127,0,-31);NavigationMapRenderer.table(root);return net.minecraft.util.ScreenShotHelper.createScreenshot(512,512,f);
        }finally{GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);f.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static void protocol(BlockPos pos){
        int[] colors=new int[96*96];for(int i=0;i<colors.length;i++)colors[i]=0xff28659b+(i%16);MessageNavigationImage m=new MessageNavigationImage(pos,0,123,0,0,4,16,false,ItemStack.EMPTY,true,0,colors);ByteBuf a=Unpooled.buffer(),b=Unpooled.buffer();
        try{m.toBytes(a);int palette=a.readableBytes();MessageNavigationImage copy=new MessageNavigationImage();ByteBuf input=a.copy();try{copy.fromBytes(input);}finally{input.release();}copy.toBytes(b);need(a.equals(b),"palette exact round trip");a.clear();b.clear();m.pixels=null;m.revision=1;m.toBytes(a);need(a.readableBytes()==MessageNavigationImage.HEADER,"unchanged acknowledgement has no raster");copy=new MessageNavigationImage();copy.fromBytes(a);need(copy.pixels==null,"ack decoding");a.clear();
        m.overlay=new com.vandorlabs.navigation.NavigationOverlay(new int[]{12,-8,-625},new int[]{2,3},new int[]{12,-8,com.vandorlabs.navigation.NavigationRange.SENTINEL_RADAR_RADIUS});
        m.toBytes(a);need(a.readableBytes()==MessageNavigationImage.HEADER+35,"one range, radar and player add only 35 bytes to a raster ACK");
        copy.fromBytes(a);need(Arrays.equals(copy.overlay.circles,m.overlay.circles)&&Arrays.equals(copy.overlay.players,m.overlay.players)&&Arrays.equals(copy.overlay.radars,m.overlay.radars),"overlay exact round trip");
        m.overlay=com.vandorlabs.navigation.NavigationOverlay.EMPTY;a.clear();m.pixels=new int[96*96];for(int i=0;i<m.pixels.length;i++)m.pixels[i]=i;m.toBytes(a);copy=new MessageNavigationImage();copy.fromBytes(a);need(Arrays.equals(copy.pixels,m.pixels),"raw high-color fallback exact");a.clear();new MessageNavigationRequest(pos,0,1,16,1).toBytes(a);need(a.readableBytes()==18,"bounded request size");new MessageNavigationRequest().fromBytes(a);System.out.println("[vandorlabs] navigation-packet-cost PASS old-raster=36899 palette16="+palette+" unchanged="+MessageNavigationImage.HEADER+" request=18 round-trips");}finally{a.release();b.release();}
    }
    private static Object staticField(Class<?> c,String name)throws Exception{java.lang.reflect.Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private static void setStatic(Class<?> c,String name,Object value)throws Exception{java.lang.reflect.Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
}
