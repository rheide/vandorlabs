package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import java.io.*;
import java.util.*;
import java.awt.image.BufferedImage;

/** Paired actual TESR draws isolate identifier allocations without changing geometry. */
final class GeneralRenderBenchmark {
    private static final BlockPos ORIGIN=new BlockPos(-8,80,-8);
    private static volatile Object consumed;
    private GeneralRenderBenchmark(){}
    static void run(Minecraft mc,File output)throws Exception{
        if(!Boolean.getBoolean("vandorlabs.renderBenchmark"))return;
        try(PrintWriter csv=new PrintWriter(new File(output,"general-render-benchmark.csv"));PrintWriter ids=new PrintWriter(new File(output,"render-identifier-benchmark.csv"))){
            csv.println("case,count,identifiers,submit_p50_ms,submit_p95_ms,complete_p50_ms,allocated_bytes_p50,pixels_equal");
            ids.println("identifiers,iterations,elapsed_ms,allocated_bytes,values_equal");identifierChecks(ids);
            for(int count:new int[]{16,64}){
                for(int size=0;size<com.vandorlabs.blocks.BlockTelescopicLandingGear.SIZES.length;size++)measure(mc,csv,output,"landing_gear",size,count);
                for(String block:new String[]{"programmable_diagonal_screen","programmable_half_input","programmable_input","programmable_half_console","programmable_console"})
                    for(int mode:new int[]{2,3})measure(mc,csv,output,block,mode,count);
                for(String block:new String[]{"programmable_half_input","programmable_half_console"})for(int mode:new int[]{0,1})measure(mc,csv,output,block,mode,count);
            }
        }finally{RenderIdentifierCache.reference=false;}
        System.out.println("[vandorlabs][reprolab] general-render-benchmark PASS paired-identifiers pixel-equality landing-gear redstone-status input-static-off");
    }
    private static void identifierChecks(PrintWriter out){
        String[] panels={"engineering_input","navigation_input","tactical_input"};
        for(String panel:panels)for(boolean off:new boolean[]{false,true}){RenderIdentifierCache.reference=true;ResourceLocation old=RenderIdentifierCache.input(panel,off);RenderIdentifierCache.reference=false;require(old.equals(RenderIdentifierCache.input(panel,off)),"input identifier equality");}
        for(int size=0;size<4;size++)for(boolean wheel:new boolean[]{false,true}){RenderIdentifierCache.reference=true;ResourceLocation old=RenderIdentifierCache.gear(size,wheel);RenderIdentifierCache.reference=false;require(old.equals(RenderIdentifierCache.gear(size,wheel)),"gear model identifier equality");}
        RenderIdentifierCache.reference=true;ResourceLocation old=RenderIdentifierCache.white();RenderIdentifierCache.reference=false;require(old.equals(RenderIdentifierCache.white()),"screen white identifier equality");
        for(boolean reference:new boolean[]{true,false}){RenderIdentifierCache.reference=reference;for(int i=0;i<1000;i++)identifiers(i,panels);long before=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();for(int i=0;i<20000;i++)identifiers(i,panels);out.printf(Locale.ROOT,"%s,20000,%.6f,%d,true%n",reference?"reference":"cached",(System.nanoTime()-start)/1E6,BenchmarkAllocations.currentThreadBytes()-before);out.flush();}
        RenderIdentifierCache.reference=false;
    }
    private static void identifiers(int i,String[] panels){consumed=RenderIdentifierCache.input(panels[i%panels.length],(i&1)==0);consumed=RenderIdentifierCache.gear(i&3,(i&1)==0);consumed=RenderIdentifierCache.white();}
    private static void measure(Minecraft mc,PrintWriter csv,File output,String name,int mode,int count)throws Exception{
        List<TileEntity> tiles=new ArrayList<>();List<BlockPos> positions=new ArrayList<>();Block block=null;
        if(name.equals("landing_gear")){for(Block candidate:Block.REGISTRY)if(candidate instanceof BlockTelescopicLandingGear){block=candidate;break;}}else block=Block.getBlockFromName("vandorlabs:"+name);
        require(block!=null,"benchmark block "+name);
        String label=name+"_"+(name.equals("landing_gear")?"size"+mode:new String[]{"static","off","redstone","status"}[mode]);
        try{
            int side=count==16?4:8;
            for(int i=0;i<count;i++){BlockPos at=ORIGIN.add((i%side)*2,(i/side)*2,0);require(mc.world.isBlockLoaded(at)&&mc.world.isAirBlock(at),"loaded empty general render fixture "+at);positions.add(at);mc.world.setBlockState(at,block.getDefaultState(),2);TileEntity tile=mc.world.getTileEntity(at);require(tile!=null,"fixture tile "+label);
                if(tile instanceof TileEntityLandingGear){net.minecraft.nbt.NBTTagCompound tag=tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound());tag.setInteger("GearSize",mode);tile.readFromNBT(tag);}
                else {TileEntityAnimatedScreenSelector screen=(TileEntityAnimatedScreenSelector)tile;
                    screen.setDisplayMode(mode==1?TileEntityAnimatedScreenSelector.MODE_OFF:TileEntityAnimatedScreenSelector.MODE_STATIC);
                    for(int slot=0;slot<2;slot++)if(com.vandorlabs.blocks.RedstoneScreenInteractions.supportsSlot(block,slot)){
                        if(mode==2){RedstoneScreenContents rows=screen.redstoneScreen(slot);int n=rows.maxRows();List<String> labels=new ArrayList<>();List<ChannelList> channels=new ArrayList<>();for(int row=0;row<n;row++){labels.add("Control "+(row+1));channels.add(ChannelList.EMPTY);}require(rows.configureRows(labels,channels,screen.getHousingTexture()),"row fixture");}
                        else if(mode==3)screen.setSurfaceTexture(slot,TileEntityAnimatedScreenSelector.VEHICLE_SURFACE);
                    }
                }tiles.add(tile);
            }
            BufferedImage referenceImage=null;
            for(boolean reference:new boolean[]{true,false}){
                RenderIdentifierCache.reference=reference;double[] submit=new double[15],complete=new double[15];long[] allocated=new long[15];
                for(int sample=-7;sample<15;sample++){
                    GlStateManager.clearColor(0,0,0,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GlStateManager.disableLighting();GlStateManager.enableCull();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);GL11.glFinish();
                    long bytes=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
                    for(TileEntity tile:tiles){BlockPos at=tile.getPos();TileEntitySpecialRenderer renderer=TileEntityRendererDispatcher.instance.getRenderer(tile);require(renderer!=null,"actual TESR "+label);renderer.render(tile,at.getX()-ORIGIN.getX(),at.getY()-ORIGIN.getY(),0,.5F,-1,1);}
                    long sent=System.nanoTime(),used=BenchmarkAllocations.currentThreadBytes()-bytes;GL11.glFinish();if(sample>=0){submit[sample]=(sent-start)/1E6;complete[sample]=(System.nanoTime()-start)/1E6;allocated[sample]=used;}
                    int error=GL11.glGetError();require(error==GL11.GL_NO_ERROR,"GL error "+error+" general "+label);
                }
                BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                if(reference)referenceImage=image;else {require(equal(referenceImage,image),"cached identifiers preserve rendered pixels "+label);if(count==64)javax.imageio.ImageIO.write(image,"png",new File(output,"benchmark-general-"+label+".png"));}
                Arrays.sort(submit);Arrays.sort(complete);Arrays.sort(allocated);csv.printf(Locale.ROOT,"%s,%d,%s,%.6f,%.6f,%.6f,%d,true%n",label,count,reference?"reference":"cached",submit[7],submit[14],complete[7],allocated[7]);csv.flush();
            }
        }finally{RenderIdentifierCache.reference=false;for(BlockPos at:positions)mc.world.setBlockToAir(at);}
    }
    private static boolean equal(BufferedImage a,BufferedImage b){if(a.getWidth()!=b.getWidth()||a.getHeight()!=b.getHeight())return false;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))return false;return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
