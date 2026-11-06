package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import java.lang.management.ManagementFactory;

/** Immutable preview/model descriptors; rendering and atlas pixels have separate checks. */
public final class DoorAssetCatalogChecks {
    private static Object sink;
    public static void main(String[] args){
        int companies=0;for(var entry:ModernFinishCatalog.ENTRIES){if(entry.id().startsWith("company_door_")){companies++;if(!NativeDoorAssets.selectable(entry,false,0)||!NativeDoorAssets.selectable(entry,true,1))throw new AssertionError("Missing company Door choice "+entry.id());}if(entry.category().startsWith("Double Doors")&&NativeDoorAssets.selectable(entry,false,0))throw new AssertionError("Double Door artwork in ordinary picker "+entry.id());}if(companies==0)throw new AssertionError("Company Door fixture absent");
        for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)if(ModernFinishCatalog.ENTRIES.get(i).frame()!=null&&!NativeDoorSettings.frame(i))throw new AssertionError("Excluded authored Frame subgroup "+i);
        int changed=0;for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++){
            var before=ModernFinishCatalog.option(i);var after=NativeDoorAssets.preview(i);if(after!=NativeDoorAssets.preview(i))throw new AssertionError("Uncached immutable preview "+i);
            if(after!=before){changed++;if(!after.texture().startsWith("vandorlabs:door-artwork/")||after.u()<=0||after.u()>1||after.v()<=0||after.v()>1||after.door()!=before.door()||after.canonical()!=before.canonical())throw new AssertionError("Preview changed identity/settings "+i);}
        }
        if(changed!=30||NativeDoorAssets.models().size()!=1924||NativeDoorAssets.models()!=NativeDoorAssets.models())throw new AssertionError("Incomplete immutable door catalog "+changed);
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();if(!bean.isThreadAllocatedMemoryEnabled())bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();
        for(int i=0;i<200000;i++)sink=NativeDoorAssets.preview(i%ModernFinishCatalog.ENTRIES.size());long start=bean.getThreadAllocatedBytes(thread);for(int i=0;i<100000;i++)sink=NativeDoorAssets.preview(i%ModernFinishCatalog.ENTRIES.size());long bytes=bean.getThreadAllocatedBytes(thread)-start;if(bytes>1024)throw new AssertionError("Per-frame preview descriptors allocated "+bytes);
        System.out.println("[vandorlabs][door-catalog] CPU PASS fullArtworkPreviews="+changed+" models=1924 cachedBytesPerLookup="+(bytes/100000D)+" stable material identities; no client/world");
    }
}
