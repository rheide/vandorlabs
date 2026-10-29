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
        final EntityGroundVehicle craft;final BlockPos pos;final int level;double distance;long order;
        Source(EntityGroundVehicle craft,BlockPos pos,int level){this.craft=craft;this.pos=pos;this.level=level;}
        Vec3d position(float partial){return craft.visualPose(partial).world(new Vec3d(pos).addVector(.5,.5,.5),craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2);}
        @Override public int hashCode(){return System.identityHashCode(craft)*31+pos.hashCode()+level;}
        @Override public boolean equals(Object other){if(!(other instanceof Source))return false;Source source=(Source)other;return source.craft==craft&&source.pos.equals(pos)&&source.level==level;}
    }
    private static final Comparator<Source> NEAREST=Comparator.comparingDouble((Source source)->source.distance).thenComparingLong(source->source.order);
    private static boolean retain(PriorityQueue<Source> nearest,EntityGroundVehicle craft,BlockPos pos,int level,double distance,long order){
        if(!(distance<64*64)||nearest.size()==MAX_PROBES&&distance>=nearest.peek().distance)return false;
        Source source=new Source(craft,pos,level);source.distance=distance;source.order=order;
        if(nearest.size()==MAX_PROBES)nearest.poll();nearest.add(source);return true;
    }
    private static void selectionChecks(){
        long[] comparisons=new long[2];
        Comparator<Source> oldOrder=(a,b)->{comparisons[0]++;return NEAREST.compare(a,b);};
        Comparator<Source> heapOrder=(a,b)->{comparisons[1]++;return NEAREST.compare(b,a);};
        PriorityQueue<Source> nearest=new PriorityQueue<>(MAX_PROBES,heapOrder);List<Source> all=new ArrayList<>();
        Random random=new Random(27);int allocated=0;
        for(int i=0;i<32768;i++){
            double distance=random.nextInt(5000);Source source=new Source(null,new BlockPos(i,0,0),15);source.distance=distance;source.order=i;
            if(distance<64*64)all.add(source);
            if(retain(nearest,null,source.pos,15,distance,i))allocated++;
            if(nearest.size()>MAX_PROBES)throw new IllegalStateException("Unbounded nearest light selection");
        }
        all.sort(oldOrder);List<Source> selected=new ArrayList<>(nearest);selected.sort(NEAREST);
        for(int i=0;i<MAX_PROBES;i++)if(selected.get(i).order!=all.get(i).order)throw new IllegalStateException("Nearest light selection changed stable ordering at "+i);
        System.out.println("[vandorlabs][reprolab] vehicle-light-selection PASS candidates=32768 old-source-allocations=32768 new-source-allocations="+allocated+" retained="+nearest.size()+" old-sort-comparisons="+comparisons[0]+" heap-comparisons="+comparisons[1]+" stable-ties range-boundary");
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
            discoveryTick=now;
            // Keep only the nearest probes; stable ties retain discovery order.
            PriorityQueue<Source> candidates=new PriorityQueue<>(MAX_PROBES,NEAREST.reversed());
            Vec3d eye=mc.player.getPositionEyes(partial);long order=0;
            for(EntityGroundVehicle craft:VehicleLookup.loaded(world))if(!craft.isDead&&craft.view!=null&&craft.structure!=null){
                VehiclePose pose=craft.visualPose(partial);double radians=Math.toRadians(pose.yaw),c=Math.cos(radians),sin=Math.sin(radians);
                double px=craft.structure.bounds.maxX/2,pz=craft.structure.bounds.maxZ/2;
                for(Map.Entry<BlockPos,Integer> light:craft.view.lightSources().entrySet()){
                    BlockPos pos=light.getKey();double lx=pos.getX()+.5-px,lz=pos.getZ()+.5-pz;
                    double dx=pose.x+px+c*lx-sin*lz-eye.x,dy=pose.y+pos.getY()+.5-eye.y,dz=pose.z+pz+sin*lx+c*lz-eye.z;
                    double distance=dx*dx+dy*dy+dz*dz;long sourceOrder=order++;
                    retain(candidates,craft,pos,light.getValue(),distance,sourceOrder);
                }
            }
            Set<Source> desired=new HashSet<>(candidates);
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
        updateProbes(partial);
    }
    private static void updateProbes(float partial){
        Iterator<Map.Entry<Source,Probe>> active=probes.entrySet().iterator();
        while(active.hasNext()){
            Map.Entry<Source,Probe> entry=active.next();
            EntityGroundVehicle craft=entry.getKey().craft;
            if(craft.isDead||craft.world!=world||craft.structure==null||craft.view==null){
                world.removeEntityFromWorld(entry.getValue().getEntityId());active.remove();continue;
            }
            Vec3d position=entry.getKey().position(partial);Probe probe=entry.getValue();probe.setPosition(position.x,position.y,position.z);probe.prevPosX=probe.lastTickPosX=position.x;probe.prevPosY=probe.lastTickPosY=position.y;probe.prevPosZ=probe.lastTickPosZ=position.z;
        }
    }
    static void runChecks(Minecraft mc)throws Exception{
        selectionChecks();
        VehicleCombinedLightChecks.runClient(mc);
        Class<?> lighting;
        try{lighting=Class.forName("net.optifine.DynamicLights");}
        catch(ClassNotFoundException absent){System.out.println("[vandorlabs][reprolab] vehicle-dynamic-light SKIP OptiFine absent; snapshot lighting checked separately");return;}
        // A cached source can become unavailable between the five-tick discovery scans.
        clear();world=mc.world;
        EntityGroundVehicle unavailable=new EntityGroundVehicle(mc.world);
        Source stale=new Source(unavailable,BlockPos.ORIGIN,15);
        Probe staleProbe=new Probe(mc.world,15);
        world.addEntityToWorld(staleProbe.getEntityId(),staleProbe);probes.put(stale,staleProbe);
        updateProbes(0);
        if(!probes.isEmpty()||world.getEntityByID(staleProbe.getEntityId())!=null)throw new IllegalStateException("Unavailable vehicle retained a light probe");
        discoveryTick=Long.MIN_VALUE;
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
