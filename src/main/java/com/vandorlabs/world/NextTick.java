package com.vandorlabs.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Work that must run after the current block change completes. MinecraftServer's
 * addScheduledTask runs immediately on the server thread, so it cannot defer.
 */
public final class NextTick {
    private static final Map<World,List<Runnable>> PENDING=new WeakHashMap<>();
    private NextTick(){}
    /** Run {@code task} at the end of {@code world}'s next server tick. */
    public static synchronized void run(World world,Runnable task){PENDING.computeIfAbsent(world,w->new ArrayList<>()).add(task);}
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent event){
        if(event.phase!=TickEvent.Phase.END||event.world.isRemote)return;
        List<Runnable> tasks;
        synchronized(NextTick.class){tasks=PENDING.remove(event.world);}
        if(tasks!=null)for(Runnable task:tasks)task.run();
    }
}
