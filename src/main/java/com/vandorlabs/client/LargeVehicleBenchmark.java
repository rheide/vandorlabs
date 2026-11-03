package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import java.io.*;
import java.util.*;

/** Opt-in benchmark of an extracted save. All gameplay actions use its integrated server. */
public final class LargeVehicleBenchmark {
    private static final boolean ENABLED=Boolean.getBoolean("vandorlabs.largeVehicleBenchmark");
    private static final BlockPos CONTROLLER=new BlockPos(1347,75,-12142);
    private static volatile int stage,scene;private static int ticks,frames;private static long settled,start,cpu,bytes,lastFrame;
    private static double interval,moveX,moveZ,sampledPath;
    private static boolean reverse;
    private static volatile net.minecraft.server.MinecraftServer benchmarkServer;
    private static volatile double serverPath;
    private static double previousServerX,previousServerZ;
    private static boolean tracking;
    private static volatile int movingTicks;
    private static int sampledTicks;
    private static PrintWriter csv;private static File output;private static boolean requested,boarded;
    private static final List<double[]> samples=new ArrayList<>();
    private static final int SAMPLES=Integer.getInteger("vandorlabs.largeBenchmarkSamples",60),WARM_FRAMES=Integer.getInteger("vandorlabs.largeBenchmarkWarmFrames",50);
    private static final boolean COMPARE=Boolean.getBoolean("vandorlabs.largeBenchmarkCompare");
    private static final String[] NAMES=COMPARE?new String[]{"native_north","native_controller","taxi_stopped_reference","taxi_stopped","taxi_moving"}:new String[]{"native_north","native_controller","taxi_stopped","taxi_moving"};
    private static final com.sun.management.ThreadMXBean BEAN=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
    public static boolean enabled(){return ENABLED;}
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static EntityGroundVehicle craft(net.minecraft.world.World world){for(EntityGroundVehicle e:VehicleLookup.loaded(world))if(!e.isDead&&e.structure!=null)return e;return null;}
    public static void tick(Minecraft mc,File directory){
        if(!ENABLED)return;try{
            output=directory;mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.enableVsync=false;mc.gameSettings.limitFramerate=260;mc.gameSettings.renderDistanceChunks=6;mc.gameSettings.fovSetting=70;mc.gameSettings.hideGUI=true;mc.gameSettings.viewBobbing=false;
            if(mc.world==null){if(stage==0&&++ticks>40&&mc.getIntegratedServer()==null){stage=1;mc.launchIntegratedServer("benchmark-world","Large vehicle benchmark",null);}return;}
            if(mc.player==null)return;if(mc.currentScreen!=null){mc.displayGuiScreen(null);mc.setIngameFocus();}
            if(stage==1){benchmarkServer=mc.getIntegratedServer();csv=new PrintWriter(new File(output,"large-vehicle.csv"));csv.println("scene,frames,complete_p50_ms,complete_p95_ms,render_cpu_p50_ms,allocated_p50_bytes,server_tick_ms,tiles,interval_p50_ms,travel_blocks,measured_path_blocks,moving_server_ticks");BEAN.setThreadAllocatedMemoryEnabled(true);BEAN.setThreadCpuTimeEnabled(true);position(mc);stage=2;}
            if(stage==4){if(++scene==NAMES.length){if(Boolean.getBoolean("vandorlabs.largeBenchmarkTerrain"))mc.getIntegratedServer().addScheduledTask(()->VehicleTerrainBenchmark.run(actor(mc).world,craft(actor(mc).world),output)).get();csv.close();com.vandorlabs.canopy.CanopyInteractions.localCollisionBounds=true;VehicleCollision.legacyTerrainGrid=false;System.out.println("[vandorlabs] large-vehicle-benchmark PASS");mc.shutdown();stage=5;return;}requested=false;position(mc);stage=2;}
            if(stage==2&&scene==2){
                if(!requested){mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=actor(mc);p.setGameType(GameType.CREATIVE);p.capabilities.isFlying=true;p.sendPlayerAbilities();p.connection.setPlayerLocation(1347.5,77,-12139.5,180,0);p.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));p.sendContainerToPlayer(p.inventoryContainer);VehicleService.screenMode(p,CONTROLLER,null,1);}).get();requested=true;}
                EntityGroundVehicle seen=craft(mc.world);if(seen==null||seen.transferInProgress){if(++ticks>3600)throw new IllegalStateException("Saved craft did not assemble; inspect controller/transfer messages in benchmark log");return;}
                if(!boarded){
                    if(seen.world!=mc.world||seen.view.parent!=mc.world)throw new IllegalStateException("Client vehicle uses a foreign physical world");
                    System.out.println("[vandorlabs] large-vehicle client-world="+seen.world.getClass().getName()+" parent="+seen.view.parent.getClass().getName()+" hull="+seen.collision.bounds);
                    mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle e=craft(actor(mc).world);if(e==null||!e.board(actor(mc)))throw new IllegalStateException("Could not board actual saved pilot seat");try{net.minecraft.nbt.CompressedStreamTools.writeCompressed(e.structure.write(),new FileOutputStream(new File(output,"captured-craft.nbt")));}catch(IOException ex){throw new IllegalStateException(ex);}}).get();boarded=true;moveX=seen.posX;moveZ=seen.posZ;settled=System.nanoTime();frames=0;}
            }
            if(scene<2){double x=scene==0?1313:1330,y=scene==0?69:78,z=scene==0?-12098:-12124;mc.player.setLocationAndAngles(x,y,z,180,scene==0?-5:5);mc.player.motionX=mc.player.motionY=mc.player.motionZ=0;}
            else {mc.player.rotationYaw=mc.player.prevRotationYaw=180;mc.player.rotationPitch=mc.player.prevRotationPitch=0;EntityGroundVehicle e=craft(mc.world);boolean moving=scene==NAMES.length-1;
                if(moving&&e!=null){double yaw=Math.toRadians(e.rotationYaw),fx=e.structure.facing.getFrontOffsetX(),fz=e.structure.facing.getFrontOffsetZ();
                    double along=(e.posX-moveX)*(fx*Math.cos(yaw)-fz*Math.sin(yaw))+(e.posZ-moveZ)*(fx*Math.sin(yaw)+fz*Math.cos(yaw));
                    if(!reverse&&along>3.5)reverse=true;else if(reverse&&along<2.5)reverse=false;
                }
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),moving&&!reverse);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),moving&&reverse);}
        }catch(Exception e){throw new IllegalStateException("Large saved vehicle benchmark scene="+scene+" stage="+stage,e);}
    }
    private static void position(Minecraft mc)throws Exception{
        com.vandorlabs.canopy.CanopyInteractions.localCollisionBounds=!(COMPARE&&scene==2);
        VehicleCollision.legacyTerrainGrid=COMPARE&&scene==2;
        samples.clear();ticks=frames=0;reverse=false;serverPath=0;movingTicks=0;lastFrame=0;settled=System.nanoTime();
        EntityGroundVehicle moving=craft(mc.world);if(moving!=null){moveX=moving.posX;moveZ=moving.posZ;}
        if(scene<2)mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=actor(mc);p.setGameType(GameType.SPECTATOR);p.connection.setPlayerLocation(scene==0?1313:1330,scene==0?69:78,scene==0?-12098:-12124,180,0);p.world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");p.world.getGameRules().setOrCreateGameRule("doWeatherCycle","false");p.world.getGameRules().setOrCreateGameRule("doMobSpawning","false");p.world.setWorldTime(6000);p.world.setRainStrength(0);}).get();
        System.out.println("[vandorlabs] large-vehicle settling "+NAMES[scene]);
    }
    public static void render(TickEvent.RenderTickEvent event){
        if(!ENABLED||stage<2||stage>3)return;Minecraft mc=Minecraft.getMinecraft();if(mc.world==null||mc.player==null||scene>=2&&(!boarded||craft(mc.world)==null))return;
        if(event.phase==TickEvent.Phase.START){GL11.glFinish();start=System.nanoTime();interval=lastFrame==0?0:(start-lastFrame)/1E6;lastFrame=start;cpu=BEAN.getCurrentThreadCpuTime();bytes=BEAN.getThreadAllocatedBytes(Thread.currentThread().getId());return;}
        long elapsedCpu=BEAN.getCurrentThreadCpuTime()-cpu,allocated=BEAN.getThreadAllocatedBytes(Thread.currentThread().getId())-bytes;GL11.glFinish();long complete=System.nanoTime()-start;
        if(stage==2){if(++frames<WARM_FRAMES||System.nanoTime()-settled<8_000_000_000L||!mc.world.isBlockLoaded(CONTROLLER))return;sampledPath=serverPath;sampledTicks=movingTicks;stage=3;VehicleRenderProfile.begin();System.out.println("[vandorlabs] large-vehicle measuring "+NAMES[scene]);return;}
        samples.add(new double[]{complete/1E6,elapsedCpu/1E6,allocated,interval});if(samples.size()<(scene==NAMES.length-1?Integer.getInteger("vandorlabs.largeBenchmarkMovingSamples",SAMPLES):SAMPLES))return;
        double server=0;for(long n:mc.getIntegratedServer().tickTimeArray)server+=n/1E6;server/=mc.getIntegratedServer().tickTimeArray.length;EntityGroundVehicle e=craft(mc.world);
        double travel=e==null?0:Math.hypot(e.posX-moveX,e.posZ-moveZ);
        double measured=scene==NAMES.length-1?serverPath-sampledPath:0;int active=scene==NAMES.length-1?movingTicks-sampledTicks:0;
        if(scene==NAMES.length-1&&(measured<.5||active<5))throw new IllegalStateException("Taxi sampling did not sustain authoritative movement: measured-path="+measured+" moving-ticks="+active+" travel="+travel+" ridden="+mc.player.isRiding()+" craft="+(e==null?"missing":e.posX+","+e.posY+","+e.posZ));
        csv.printf(Locale.ROOT,"%s,%d,%.4f,%.4f,%.4f,%.0f,%.4f,%d,%.4f,%.4f,%.4f,%d%n",NAMES[scene],samples.size(),pct(0,.5),pct(0,.95),pct(1,.5),pct(2,.5),server,e==null?mc.world.loadedTileEntityList.size():e.view.tiles.size(),pct(3,.5),travel,measured,active);csv.flush();
        try{VehicleRenderProfile.write(output,NAMES[scene],samples.size());}catch(IOException failure){throw new IllegalStateException(failure);}
        net.minecraft.util.ScreenShotHelper.saveScreenshot(output,"large-"+NAMES[scene]+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        System.out.println("[vandorlabs] large-vehicle result "+NAMES[scene]+" ms="+pct(0,.5)+" cpu="+pct(1,.5)+" bytes="+pct(2,.5)+" controls="+(e==null?"none":e.diagnostics.summary())+" renderer="+RenderGroundVehicle.profile());stage=4;
    }
    /** Measure authoritative movement after normal input/prediction processing. */
    public static void serverTick(TickEvent.ServerTickEvent event){
        if(!ENABLED||event.phase!=TickEvent.Phase.END)return;
        net.minecraft.server.MinecraftServer server=benchmarkServer;
        if(server==null||scene!=NAMES.length-1||stage<2||stage>3){tracking=false;return;}
        EntityGroundVehicle e=craft(server.getWorld(0));if(e==null){tracking=false;return;}
        if(tracking){double distance=Math.hypot(e.posX-previousServerX,e.posZ-previousServerZ);serverPath+=distance;if(distance>.001)movingTicks++;}
        previousServerX=e.posX;previousServerZ=e.posZ;tracking=true;
    }
    private static double pct(int column,double quantile){double[] a=new double[samples.size()];for(int i=0;i<a.length;i++)a[i]=samples.get(i)[column];Arrays.sort(a);return a[(int)((a.length-1)*quantile)];}
    private LargeVehicleBenchmark(){}
}
