package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.*;

/** Client-only luminous item probes use OptiFine's ordinary dropped-item lighting path. */
final class VehicleDynamicLights {
    private static final int MAX_PROBES=32;
    private static final Method ENABLED=enabledMethod();
    private static WorldClient world;
    private static long discoveryTick=Long.MIN_VALUE;
    private static final Map<Source,Probe> probes=new HashMap<>();
    private static final ItemStack[] LIGHT_ITEMS=new ItemStack[16];
    private static int nextId=-1900000000;
    private static Method enabledMethod(){try{return Class.forName("Config").getMethod("isDynamicLights");}catch(ReflectiveOperationException|LinkageError absent){return null;}}
    private static boolean enabled(){if(ENABLED==null)return false;try{return (Boolean)ENABLED.invoke(null);}catch(ReflectiveOperationException|LinkageError unavailable){return false;}}
    private static ItemStack item(int level){
        if(LIGHT_ITEMS[level]==null){net.minecraft.block.Block block=new net.minecraft.block.Block(net.minecraft.block.material.Material.AIR){};block.setLightLevel(level/15F);LIGHT_ITEMS[level]=new ItemStack(new ItemBlock(block));}
        return LIGHT_ITEMS[level];
    }
    private static final class Source {
        final EntityGroundVehicle craft;final BlockPos pos;final int level;double distance;
        Source(EntityGroundVehicle craft,BlockPos pos,int level){this.craft=craft;this.pos=pos;this.level=level;}
        Vec3d position(float partial){return craft.visualPose(partial).world(new Vec3d(pos).addVector(.5,.5,.5),craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2);}
        @Override public int hashCode(){return System.identityHashCode(craft)*31+pos.hashCode()+level;}
        @Override public boolean equals(Object other){if(!(other instanceof Source))return false;Source source=(Source)other;return source.craft==craft&&source.pos.equals(pos)&&source.level==level;}
    }
    static final class Probe extends EntityItem {
        Probe(WorldClient world,int level){super(world);setEntityId(nextId--);setItem(item(level));setSize(0,0);setNoGravity(true);noClip=true;}
        @Override public void onUpdate(){} // No physics, pickup, merging, aging or server traffic.
        @Override public void onCollideWithPlayer(net.minecraft.entity.player.EntityPlayer player){}
        @Override public boolean canBeCollidedWith(){return false;}
        @Override public boolean canBePushed(){return false;}
        @Override public boolean attackEntityFrom(net.minecraft.util.DamageSource source,float amount){return false;}
        @Override public boolean writeToNBTOptional(net.minecraft.nbt.NBTTagCompound tag){return false;}
    }
    static void frame(Minecraft mc,float partial){
        if(world!=mc.world){clear();world=mc.world;discoveryTick=Long.MIN_VALUE;}
        if(world==null||mc.player==null||!enabled()){clear();return;}
        if(mc.isGamePaused())return;
        long now=world.getTotalWorldTime();
        if(discoveryTick==Long.MIN_VALUE||now-discoveryTick>=5||now<discoveryTick){
            discoveryTick=now;List<Source> candidates=new ArrayList<>();
            for(EntityGroundVehicle craft:VehicleLookup.loaded(world))if(!craft.isDead&&craft.view!=null&&craft.structure!=null){
                for(Map.Entry<BlockPos,Integer> light:craft.view.lightSources().entrySet()){
                    Source source=new Source(craft,light.getKey(),light.getValue());source.distance=source.position(partial).squareDistanceTo(mc.player.getPositionEyes(partial));if(source.distance<64*64)candidates.add(source);
                }
            }
            candidates.sort(Comparator.comparingDouble(source->source.distance));
            Set<Source> desired=new HashSet<>(candidates.subList(0,Math.min(MAX_PROBES,candidates.size())));
            Iterator<Map.Entry<Source,Probe>> iterator=probes.entrySet().iterator();
            while(iterator.hasNext()){Map.Entry<Source,Probe> entry=iterator.next();if(!desired.contains(entry.getKey())){world.removeEntityFromWorld(entry.getValue().getEntityId());iterator.remove();}}
            for(Source source:desired)if(!probes.containsKey(source)){
                Probe probe=new Probe(world,source.level);Vec3d position=source.position(partial);probe.setPosition(position.x,position.y,position.z);
                world.addEntityToWorld(probe.getEntityId(),probe);probes.put(source,probe);
            }
            if(!probeRendererRegistered){
            java.util.Map<Class<? extends net.minecraft.entity.Entity>,Render<? extends net.minecraft.entity.Entity>> renderers=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.renderer.entity.RenderManager.class,mc.getRenderManager(),"entityRenderMap","field_78729_o");
            renderers.put(Probe.class,new Render<Probe>(mc.getRenderManager()){
                @Override protected ResourceLocation getEntityTexture(Probe entity){return null;}
                @Override public void doRender(Probe entity,double x,double y,double z,float yaw,float partial){}
            });probeRendererRegistered=true;
            }
        }
        for(Map.Entry<Source,Probe> entry:probes.entrySet()){
            Vec3d position=entry.getKey().position(partial);Probe probe=entry.getValue();probe.setPosition(position.x,position.y,position.z);probe.prevPosX=probe.lastTickPosX=position.x;probe.prevPosY=probe.lastTickPosY=position.y;probe.prevPosZ=probe.lastTickPosZ=position.z;
        }
    }
    static void runChecks(Minecraft mc)throws Exception{
        VehicleCombinedLightChecks.runClient(mc);
        Class<?> lighting;
        try{lighting=Class.forName("net.optifine.DynamicLights");}
        catch(ClassNotFoundException absent){System.out.println("[vandorlabs][reprolab] vehicle-dynamic-light SKIP OptiFine absent; snapshot lighting checked separately");return;}
        Method level=lighting.getMethod("getLightLevel",Entity.class);
        for(int intensity:new int[]{3,7,14,15}){
            Probe probe=new Probe(mc.world,intensity);probe.setPosition(mc.player.posX,mc.player.posY,mc.player.posZ);
            try{
                mc.world.addEntityToWorld(probe.getEntityId(),probe);
                if(mc.world.getEntityByID(probe.getEntityId())!=probe||(Integer)level.invoke(null,probe)!=intensity)throw new IllegalStateException("OptiFine did not recognize vehicle light intensity "+intensity);
                if(probe.canBeCollidedWith()||probe.canBePushed())throw new IllegalStateException("Vehicle light probe affects interaction");
            }finally{mc.world.removeEntityFromWorld(probe.getEntityId());}
            if(mc.world.getEntityByID(probe.getEntityId())!=null)throw new IllegalStateException("Vehicle light probe survived removal");
        }
        System.out.println("[vandorlabs][reprolab] vehicle-dynamic-light PASS OptiFine actual item-light intensities=3,7,14,15 client-only add/remove no-pickup no-collision bounded="+MAX_PROBES);
    }
    private static boolean probeRendererRegistered;
    private static void clear(){if(world!=null)for(Probe probe:probes.values())world.removeEntityFromWorld(probe.getEntityId());probes.clear();}
    private VehicleDynamicLights(){}
}
