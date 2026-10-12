package com.vandorlabs.ramp;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityRampController;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.ArrayList;

/** One proximity pass per world-second; sleeping controllers schedule no block ticks. */
@Mod.EventBusSubscriber(modid=VandorLabs.MODID)
public final class EscalatorActivity {
    private static final Set<TileEntityRampController> LOADED=Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private EscalatorActivity() { }
    public static void register(TileEntityRampController tile) { LOADED.add(tile); }
    public static void unregister(TileEntityRampController tile) { LOADED.remove(tile); }
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || event.world.isRemote || event.world.getTotalWorldTime()%20!=0)return;
        for(TileEntityRampController tile:new ArrayList<>(LOADED))
            if(tile.getWorld()==event.world && !tile.isInvalid())tile.refreshEscalatorActivity();
    }
}
