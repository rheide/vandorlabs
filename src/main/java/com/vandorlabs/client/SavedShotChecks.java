package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;

import java.io.File;

/**
 * Screenshots of a reported world from given player poses, to reproduce visual reports in the
 * world they came from. Poses come from {@code vandorlabs.savedShots}: {@code name:x,y,z,yaw,pitch}
 * entries separated by semicolons, using the feet position, yaw and pitch shown on the F3 screen.
 * The first pose is shot twice, so its chunks are built before the named image. With
 * {@code vandorlabs.savedShotRemove} ({@code x,y,z;...}) every pose is shot again, suffixed
 * {@code -without}, after those blocks are removed from the run's copy of the world.
 */
public final class SavedShotChecks {
    private static LiveGallery gallery,without;
    private static int ticks,count;
    private static volatile boolean removed;
    private SavedShotChecks(){}

    public static boolean tick(Minecraft mc,File output)throws Exception{
        mc.gameSettings.pauseOnLostFocus=false;
        if(mc.player==null||mc.getIntegratedServer()==null||++ticks<60)return false;
        if(gallery==null){
            // Daylight on the run's copy of the world, so shots are readable.
            mc.getIntegratedServer().addScheduledTask(()->{
                net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);
                world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");world.setWorldTime(6000);
            });
            gallery=new LiveGallery("saved",BlockPos.ORIGIN);
            boolean first=true;
            for(String spec:System.getProperty("vandorlabs.savedShots","").split(";")){
                if(spec.trim().isEmpty())continue;
                int colon=spec.indexOf(':');String[] v=spec.substring(colon+1).split(",");
                if(colon<1||v.length!=5)throw new IllegalArgumentException("Saved shot must be name:x,y,z,yaw,pitch: "+spec);
                double x=Double.parseDouble(v[0]),y=Double.parseDouble(v[1]),z=Double.parseDouble(v[2]);
                float yaw=Float.parseFloat(v[3]),pitch=Float.parseFloat(v[4]);
                if(first)gallery.shot("warmup",x,y,z,yaw,pitch);
                gallery.shot(spec.substring(0,colon).trim(),x,y,z,yaw,pitch);first=false;count++;
            }
            if(count==0)throw new IllegalArgumentException("Set VANDOR_LABS_SAVED_SHOTS to name:x,y,z,yaw,pitch;...");
        }
        if(!gallery.tick(mc,output))return false;
        String remove=System.getProperty("vandorlabs.savedShotRemove","").trim();
        if(!remove.isEmpty()){
            if(without==null){
                without=new LiveGallery("saved",BlockPos.ORIGIN);
                for(String spec:System.getProperty("vandorlabs.savedShots","").split(";")){
                    if(spec.trim().isEmpty())continue;
                    int colon=spec.indexOf(':');String[] v=spec.substring(colon+1).split(",");
                    without.shot(spec.substring(0,colon).trim()+"-without",Double.parseDouble(v[0]),Double.parseDouble(v[1]),Double.parseDouble(v[2]),Float.parseFloat(v[3]),Float.parseFloat(v[4]));
                }
                mc.getIntegratedServer().addScheduledTask(()->{
                    net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);
                    for(String at:remove.split(";")){String[] c=at.trim().split(",");world.setBlockToAir(new BlockPos(Integer.parseInt(c[0]),Integer.parseInt(c[1]),Integer.parseInt(c[2])));}
                    removed=true;
                });
            }
            if(!removed||!without.tick(mc,output))return false;
        }
        System.out.println("[vandorlabs][reprolab] saved-shot PASS shots="+count);
        return true;
    }
}
