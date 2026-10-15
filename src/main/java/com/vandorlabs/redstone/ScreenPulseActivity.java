package com.vandorlabs.redstone;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.RedstoneScreenContents;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.*;

/** Only currently pulsing placed screens receive an expiry callback. */
@Mod.EventBusSubscriber(modid=VandorLabs.MODID)
public final class ScreenPulseActivity {
    private static final Set<RedstoneScreenContents> ACTIVE=Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<RedstoneScreenContents,Boolean>()));
    private ScreenPulseActivity(){}
    public static void activate(RedstoneScreenContents contents){if(contents.tile().getWorld()!=null&&!contents.tile().getWorld().isRemote&&!(contents.tile().getWorld() instanceof VehicleWorld))ACTIVE.add(contents);}
    public static void remove(RedstoneScreenContents contents){ACTIVE.remove(contents);}
    @SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){for(RedstoneScreenContents contents:new ArrayList<>(ACTIVE))if(contents.tile().getWorld()==event.getWorld())ACTIVE.remove(contents);}
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent event){
        if(event.phase!=TickEvent.Phase.END||event.world.isRemote||ACTIVE.isEmpty())return;
        for(RedstoneScreenContents contents:new ArrayList<>(ACTIVE))if(contents.tile().getWorld()==event.world){
            if(contents.tile().isInvalid()||!event.world.isBlockLoaded(contents.getPos())||event.world.getTileEntity(contents.getPos())!=contents.tile()||!contents.enabled()||!contents.expirePulses(event.world.getTotalWorldTime()))ACTIVE.remove(contents);
        }
    }
}
