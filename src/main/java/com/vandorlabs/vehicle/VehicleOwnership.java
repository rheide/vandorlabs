package com.vandorlabs.vehicle;

import net.minecraft.nbt.*;
import net.minecraft.world.WorldServer;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Persistent transfer epochs reject stale vehicle entities when old chunks load later. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=com.vandorlabs.VandorLabs.MODID)
public final class VehicleOwnership {
    private static final Map<WorldServer,NBTTagCompound> ledgers=new WeakHashMap<>();
    private static Path path(WorldServer w){return w.getSaveHandler().getWorldDirectory().toPath().resolve("data").resolve("vandorlabs-vehicle-owners-"+w.provider.getDimension()+".nbt");}
    private static NBTTagCompound ledger(WorldServer w)throws IOException {
        NBTTagCompound ledger=ledgers.get(w);
        if(ledger==null){Path file=path(w);if(Files.exists(file))try(InputStream in=Files.newInputStream(file)){ledger=CompressedStreamTools.readCompressed(in);}else ledger=new NBTTagCompound();ledgers.put(w,ledger);}
        return ledger;
    }
    public static long update(WorldServer w,UUID id,long atLeast,boolean active)throws IOException {
        NBTTagCompound before=ledger(w),next=before.copy();String key=id.toString();
        long epoch=Math.max(atLeast,before.getCompoundTag(key).getLong("Epoch")+1);
        NBTTagCompound entry=new NBTTagCompound();entry.setLong("Epoch",epoch);entry.setBoolean("Active",active);next.setTag(key,entry);
        Path file=path(w),tmp=file.resolveSibling(file.getFileName()+".tmp");Files.createDirectories(file.getParent());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();CompressedStreamTools.writeCompressed(next,bytes);
        try(FileOutputStream out=new FileOutputStream(tmp.toFile())){out.write(bytes.toByteArray());out.getFD().sync();}
        Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);ledgers.put(w,next);return epoch;
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void joining(net.minecraftforge.event.entity.EntityJoinWorldEvent event) {
        if(event.getWorld().isRemote || !(event.getEntity() instanceof EntityGroundVehicle))return;
        EntityGroundVehicle vehicle=(EntityGroundVehicle)event.getEntity();
        try {
            NBTTagCompound ledger=ledger((WorldServer)event.getWorld());String key=vehicle.getUniqueID().toString();
            if(ledger.hasKey(key,10)){NBTTagCompound entry=ledger.getCompoundTag(key);if(!entry.getBoolean("Active") || vehicle.transferEpoch!=entry.getLong("Epoch"))event.setCanceled(true);}
        }catch(IOException error){vehicle.ownershipBlocked=true;com.vandorlabs.VandorLabs.logger.error("Vehicle ownership ledger cannot be read; entity retained without movement",error);}
    }
}
