package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.navigation.*;
import com.vandorlabs.network.MessageNavigationImage;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import java.io.*;
import java.util.*;

/** Same native tile-render path before/after; exclusive software-rendered diagnostic. */
final class NavigationPerformanceChecks {
    private static final BlockPos POS=new BlockPos(24,100,24);
    private static final com.sun.management.ThreadMXBean ALLOC=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
    private static int ticks;
    private static Object field(Object o,String name)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static boolean tick(Minecraft mc,File out)throws Exception{
        if(++ticks==1){mc.player.closeScreen();mc.getIntegratedServer().addScheduledTask(()->{net.minecraft.entity.player.EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);p.connection.setPlayerLocation(31,103,31,0,70);}).get();return false;}
        if(ticks<35)return false;
        Framebuffer fb=new Framebuffer(512,512,true);TENavigationTable renderer=new TENavigationTable();renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);
        try(PrintWriter csv=new PrintWriter(new File(out,"navigation-performance.csv"));TurntableRenderState saved=new TurntableRenderState()){
            csv.println("kind,width,depth,tiles,median_cpu_ns,p95_cpu_ns,median_frame_ns,median_alloc_bytes");
            GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-10,10,-10,10,-100,100);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.rotate(65,1,0,0);
            for(String kind:new String[]{"flat","angled"})for(int size:new int[]{1,4,8,16}){
                int w=size,d=kind.equals("flat")?size:1;List<TileEntityNavigationTable> tiles=new ArrayList<>();Block block=Block.getBlockFromName("vandorlabs:navigation_table_"+kind);
                for(BlockPos at:BlockPos.getAllInBox(POS,POS.add(15,0,15)))if(mc.world.getTileEntity(at) instanceof TileEntityNavigationTable)mc.world.setBlockToAir(at);
                for(int x=0;x<w;x++)for(int z=0;z<d;z++){BlockPos at=POS.add(x,0,z);mc.world.setBlockState(at,block.getDefaultState(),2);TileEntityNavigationTable tile=(TileEntityNavigationTable)mc.world.getTileEntity(at);int mask=(z>0?1:0)|(x<w-1?2:0)|(z<d-1?4:0)|(x>0?8:0);tile.layout(mask,POS,true,POS,w,d,EnumFacing.NORTH);tiles.add(tile);}
                TileEntityNavigationTable root=tiles.get(0);NavigationMapRenderer.bind(root,0,w/(double)d);
                @SuppressWarnings("unchecked") Map<BlockPos,?> entries=(Map<BlockPos,?>)staticField(NavigationMapRenderer.class,"IMAGES");Object entry=entries.get(POS);int serial=(Integer)field(entry,"serial");int[] pixels=new int[96*96];for(int i=0;i<pixels.length;i++)pixels[i]=(i%96<48?0xff28659b:0xff568b51);
                MessageNavigationImage packet=new MessageNavigationImage(POS,0,serial,24,24,4,Math.max(8,Math.min(64,(int)Math.round(16.0*w/d))),false,ItemStack.EMPTY,true,0,pixels);NavigationMapRenderer.receive(packet);
                Runnable draw=()->{fb.bindFramebuffer(true);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);for(TileEntityNavigationTable tile:tiles)renderer.render(tile,tile.getPos().getX()-POS.getX()-w/2.0,0,tile.getPos().getZ()-POS.getZ()-d/2.0,0,-1,1);};
                for(int n=0;n<12;n++){draw.run();GL11.glFinish();}
                long[] cpu=new long[31],frame=new long[31],bytes=new long[31];for(int n=0;n<cpu.length;n++){GL11.glFinish();long allocated=ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId()),start=System.nanoTime();draw.run();cpu[n]=System.nanoTime()-start;GL11.glFinish();frame[n]=System.nanoTime()-start;bytes[n]=ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId())-allocated;}
                Arrays.sort(cpu);Arrays.sort(frame);Arrays.sort(bytes);csv.println(kind+","+w+","+d+","+tiles.size()+","+cpu[15]+","+cpu[29]+","+frame[15]+","+bytes[15]);csv.flush();
                javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(512,512,fb),"png",new File(out,"navigation-performance-"+kind+"-"+size+".png"));
            }
        }finally{GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);fb.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
        System.out.println("[vandorlabs] navigation-performance PASS exclusive actual TESR CPU allocation frame intervals sizes=1,4,8,16 software renderer");return true;
    }
    private static Object staticField(Class<?> type,String name)throws Exception{java.lang.reflect.Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
}
