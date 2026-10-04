package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Opt-in benchmark of an existing disposable save; never runs during ordinary play. */
public final class WorldSceneBenchmark {
    private static final boolean ENABLED=Boolean.getBoolean("vandorlabs.worldBenchmark");
    private static final int SAMPLES=240;
    // Camera feet coordinates, yaw, pitch; all look toward the supplied structure.
    private static final double[][] CAMERAS={{36,77,100,90,8},{-8,77,100,270,8},{45,94,142,143.57,18}};
    private static final String[] NAMES={"east_wall","west_wall","overview"};
    private static int ticks,camera=-1,stage,frames;
    private static long startWall,startCpu,startBytes,settleStart;
    private static final List<double[]> samples=new ArrayList<>();
    private static File output;
    private static PrintWriter results;
    private static final com.sun.management.ThreadMXBean BEAN=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    public static boolean enabled(){return ENABLED;}
    public static void tick(Minecraft mc,File directory) {
        if(!ENABLED)return;
        try {
            output=directory;mc.gameSettings.pauseOnLostFocus=false;
            mc.gameSettings.enableVsync=false;mc.gameSettings.limitFramerate=260;
            mc.gameSettings.renderDistanceChunks=6;mc.gameSettings.fovSetting=70;mc.gameSettings.hideGUI=true;
            mc.gameSettings.viewBobbing=false;
            if(mc.world==null) {
                if(stage==0 && ++ticks>60 && mc.getIntegratedServer()==null) {
                    stage=1;mc.launchIntegratedServer("benchmark-world","Vandor Labs benchmark",null);
                }
                return;
            }
            if(mc.player==null)return;
            if(mc.currentScreen!=null){mc.displayGuiScreen(null);mc.setIngameFocus();}
            if(stage==1) {
                results=new PrintWriter(new File(output,"world-render.csv"));
                results.println("camera,samples,submit_p50_ms,submit_p95_ms,complete_p50_ms,complete_p95_ms,render_cpu_p50_ms,allocated_p50_bytes,server_tick_mean_ms");
                if(BEAN.isThreadCpuTimeSupported())BEAN.setThreadCpuTimeEnabled(true);
                if(BEAN.isThreadAllocatedMemorySupported())BEAN.setThreadAllocatedMemoryEnabled(true);
                camera=0;place(mc);stage=2;
                mc.getIntegratedServer().addScheduledTask(()-> {
                    net.minecraft.world.World world=mc.getIntegratedServer().getWorld(0);
                    world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");
                    world.getGameRules().setOrCreateGameRule("doWeatherCycle","false");
                    world.getGameRules().setOrCreateGameRule("doMobSpawning","false");
                    world.setWorldTime(6000);world.getWorldInfo().setRaining(false);world.getWorldInfo().setThundering(false);
                });
            }
            if(stage==4) {
                if(++camera==CAMERAS.length){results.close();stage=5;System.out.println("[vandorlabs] world-benchmark PASS");mc.shutdown();return;}
                place(mc);stage=2;
            }
            if(stage==2 || stage==3) {
                double[] c=CAMERAS[camera];mc.player.setLocationAndAngles(c[0],c[1],c[2],(float)c[3],(float)c[4]);
                mc.player.motionX=mc.player.motionY=mc.player.motionZ=0;
                mc.playerController.setGameType(GameType.SPECTATOR);
            }
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    private static void place(Minecraft mc) {
        double[] c=CAMERAS[camera];
        mc.getIntegratedServer().addScheduledTask(()-> {
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUsername(mc.player.getName());
            if(player!=null){player.setGameType(GameType.SPECTATOR);player.connection.setPlayerLocation(c[0],c[1],c[2],(float)c[3],(float)c[4]);}
        });
        samples.clear();frames=0;settleStart=System.nanoTime();
        System.out.println("[vandorlabs] world-benchmark settling "+NAMES[camera]);
    }
    public static void render(TickEvent.RenderTickEvent event) {
        if(!ENABLED || stage<2 || stage>3)return;
        Minecraft mc=Minecraft.getMinecraft();
        if(event.phase==TickEvent.Phase.START) {
            GL11.glFinish();startBytes=bytes();startCpu=BEAN.getCurrentThreadCpuTime();startWall=System.nanoTime();return;
        }
        long submitted=System.nanoTime(),cpu=BEAN.getCurrentThreadCpuTime()-startCpu,allocated=bytes()-startBytes;
        GL11.glFinish();long completed=System.nanoTime();
        if(stage==2) {
            if(++frames>=90 && completed-settleStart>15_000_000_000L && mc.world.isAreaLoaded(new BlockPos(-16,60,48),new BlockPos(64,104,160),false)) {
                inventory(mc);stage=3;System.out.println("[vandorlabs] world-benchmark measuring "+NAMES[camera]);
            }
            if(completed-settleStart>180_000_000_000L)throw new IllegalStateException("Benchmark chunks did not settle");
            return;
        }
        samples.add(new double[]{(submitted-startWall)/1E6,(completed-startWall)/1E6,cpu/1E6,allocated});
        if(samples.size()<SAMPLES)return;
        double server=0;for(long t:mc.getIntegratedServer().tickTimeArray)server+=t/1E6;
        server/=mc.getIntegratedServer().tickTimeArray.length;
        results.printf(Locale.ROOT,"%s,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.0f,%.6f%n",NAMES[camera],samples.size(),
                percentile(0,.5),percentile(0,.95),percentile(1,.5),percentile(1,.95),percentile(2,.5),percentile(3,.5),server);
        results.flush();
        net.minecraft.util.ScreenShotHelper.saveScreenshot(output,"world-"+NAMES[camera]+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        stage=4;
    }
    private static void inventory(Minecraft mc) {
        if(camera!=0)return;
        Map<String,Integer> counts=new TreeMap<>();
        for(BlockPos p:BlockPos.getAllInBox(new BlockPos(-16,60,48),new BlockPos(64,104,160))) {
            net.minecraft.block.Block block=mc.world.getBlockState(p).getBlock();
            String id=String.valueOf(block.getRegistryName());
            if(id.startsWith("vandorlabs:"))counts.put(id,counts.getOrDefault(id,0)+1);
        }
        try(PrintWriter out=new PrintWriter(new File(output,"world-block-counts.csv"))) {
            out.println("block,count");counts.forEach((id,count)->out.println(id+","+count));
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    private static double percentile(int column,double p) {
        double[] sorted=new double[samples.size()];for(int i=0;i<sorted.length;i++)sorted[i]=samples.get(i)[column];
        Arrays.sort(sorted);return sorted[(int)Math.floor((sorted.length-1)*p)];
    }
    private static long bytes(){return BEAN.isThreadAllocatedMemorySupported()?BEAN.getThreadAllocatedBytes(Thread.currentThread().getId()):0;}
    private WorldSceneBenchmark(){}
}
