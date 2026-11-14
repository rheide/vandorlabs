package com.vandorlabs.client;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Ownership counters accompany optional GPU profiles, but are not themselves timings. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class GlassPaneDiagnostics {
    private static final boolean ENABLED=Boolean.getBoolean("vandorlabs.staticGlassDiagnostics");
    private static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!ENABLED||event.phase!=TickEvent.Phase.END||++ticks%200!=1)return;
        System.out.println("[vandorlabs] native-glass "+report());
    }
    public static String report(){return GlassPanePolicy.diagnostics()+" "+GlassPaneTerrain.diagnostics()+" "+GlassPaneRetained.diagnostics();}
    private GlassPaneDiagnostics(){}
}
