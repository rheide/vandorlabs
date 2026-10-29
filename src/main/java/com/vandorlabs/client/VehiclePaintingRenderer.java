package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.nbt.*;

/** Render inert native painting models in the same transform as the carried blocks. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=com.vandorlabs.VandorLabs.MODID,value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class VehiclePaintingRenderer {
    private static final Map<java.util.UUID,Cache> CACHES=new LinkedHashMap<java.util.UUID,Cache>(16,.75F,true);
    private static long bytes;
    private static final class Cache {java.lang.ref.WeakReference<VehicleStructure> structure;NBTTagList data;long bytes;final List<EntityPainting> paintings=new ArrayList<>();}
    static void render(EntityGroundVehicle craft,float partial){
        Cache cache=CACHES.computeIfAbsent(craft.getUniqueID(),k->new Cache());
        if(cache.structure==null||cache.structure.get()!=craft.structure){
            NBTTagList data=VehiclePaintings.entries(craft.structure);cache.structure=new java.lang.ref.WeakReference<>(craft.structure);
            if(!data.equals(cache.data)){
                bytes-=cache.bytes;cache.data=data;cache.paintings.clear();
                for(int i=0;i<data.tagCount();i++){EntityPainting painting=new EntityPainting(Minecraft.getMinecraft().world);painting.readFromNBT(data.getCompoundTagAt(i));painting.world=null;cache.paintings.add(painting);}
                cache.bytes=16384+data.toString().length()*2L+cache.paintings.size()*4096L;bytes+=cache.bytes;
            }
        }
        while(CACHES.size()>64||bytes>16L*1024*1024){Map.Entry<java.util.UUID,Cache> oldest=CACHES.entrySet().iterator().next();bytes-=oldest.getValue().bytes;CACHES.remove(oldest.getKey());}
        if(cache.paintings.isEmpty())return;
        net.minecraft.client.renderer.entity.RenderManager manager=Minecraft.getMinecraft().getRenderManager();
        net.minecraft.world.World previousWorld=manager.world;
        try(TurntableRenderState saved=new TurntableRenderState()){
            // Vanilla RenderPainting samples its manager's world for each art
            // tile. Its local coordinates need the same transformed lighting
            // view as carried blocks, including lamps inside the craft.
            manager.world=craft.view;
            for(EntityPainting painting:cache.paintings){painting.world=craft.view;try{manager.renderEntity(painting,painting.posX,painting.posY,painting.posZ,painting.rotationYaw,partial,false);}finally{painting.world=null;}}
        }finally{manager.world=previousWorld;}
    }
    static void clear(){CACHES.clear();bytes=0;}
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void unloaded(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld().isRemote)Minecraft.getMinecraft().addScheduledTask(VehiclePaintingRenderer::clear);}
}
