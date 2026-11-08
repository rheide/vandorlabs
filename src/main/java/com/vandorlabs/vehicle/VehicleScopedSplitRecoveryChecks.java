package com.vandorlabs.vehicle;

import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Repro-only real detached-output cancellation; evidence never serializes a live entity. */
public final class VehicleScopedSplitRecoveryChecks {
    private static final boolean ACTIVE="1".equals(System.getenv("VANDOR_LABS_COUPLER_SCOPED_SPLIT_CAUGHT"));
    private static final VehicleScopedSplitRecoveryChecks OBSERVER=new VehicleScopedSplitRecoveryChecks();
    private static WorldServer world;private static UUID parent,child,firstScope,secondScope;private static NBTTagCompound before,restored;private static boolean armed,cancelled,rolledBack,complete;private static int checks;private static long journalTick,rollbackTick;
    public static boolean active(){return ACTIVE;}
    private static void need(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("Scoped split recovery: "+why);}
    private static Object field(Object owner,String name)throws Exception{java.lang.reflect.Field f=(owner instanceof Class?(Class<?>)owner:owner.getClass()).getDeclaredField(name);f.setAccessible(true);return f.get(owner instanceof Class?null:owner);}
    private static Object hold()throws Exception{Map<?,?> holds=(Map<?,?>)((Map<?,?>)field(VehicleCouplingTransfer.class,"HOLDS")).get(world);need(holds!=null&&holds.size()==1,"one actual journal at callback");return holds.values().iterator().next();}
    public static void arm(WorldServer w,UUID id){
        need(ACTIVE&&!armed&&System.getProperty("vandorlabs.reprolab")!=null,"single repro-only admission");world=w;parent=id;armed=true;
        MinecraftForge.EVENT_BUS.register(OBSERVER);
        VehicleCouplingTransfer.fault=point->{try{
            if(point.equals("journal")){
                NBTTagCompound record=(NBTTagCompound)field(hold(),"record");before=record.getCompoundTag("Before").copy();parent=before.getUniqueId("UUID");child=record.getCompoundTag("Detached").getUniqueId("UUID");
                firstScope=EntityGroundVehicle.readCouplerGeneration(record.getCompoundTag("After"));secondScope=EntityGroundVehicle.readCouplerGeneration(record.getCompoundTag("Detached"));
                need(EntityGroundVehicle.readCouplerGeneration(before)==null,"actual original nullable scope, not synthetic seeding");need(firstScope!=null&&secondScope!=null&&!firstScope.equals(secondScope),"two independently staged output generations");
                EntityGroundVehicle c=(EntityGroundVehicle)world.getEntityFromUuid(parent);need(c!=null&&c.structure.write().equals(before.getCompoundTag("Craft")),"journal BEFORE is exact live frozen source");journalTick=world.getTotalWorldTime();
                System.out.println("[vandorlabs][reprolab] scoped-split-journal tick="+journalTick+" parent="+parent+" child="+child+" staged="+firstScope+","+secondScope);
            }
            if(point.equals("rollback")){
                EntityGroundVehicle c=(EntityGroundVehicle)world.getEntityFromUuid(parent);need(cancelled&&c!=null,"rollback follows real detached join cancellation");restored=evidence(c);need(c.couplerAssociationGeneration()==null&&c.couplerScopeReady(),"rollback reinstalls original null scope");need(c.structure.write().equals(before.getCompoundTag("Craft")),"exact original immutable Source restored");
                net.minecraft.nbt.NBTTagList pos=before.getTagList("Pos",6),rotation=before.getTagList("Rotation",5);need(c.posX==pos.getDoubleAt(0)&&c.posY==pos.getDoubleAt(1)&&c.posZ==pos.getDoubleAt(2)&&c.rotationYaw==rotation.getFloatAt(0)&&c.rotationPitch==rotation.getFloatAt(1),"exact original pose restored");rollbackTick=world.getTotalWorldTime();rolledBack=true;
            }
        }catch(Exception failure){throw new IllegalStateException(failure);}};
    }
    @SubscribeEvent public void joined(EntityJoinWorldEvent event){if(event.getWorld()!=world||!(event.getEntity() instanceof EntityGroundVehicle)||child==null||!child.equals(event.getEntity().getUniqueID()))return;
        EntityGroundVehicle detached=(EntityGroundVehicle)event.getEntity(),retained=(EntityGroundVehicle)world.getEntityFromUuid(parent);
        need(!cancelled&&retained!=null&&firstScope.equals(retained.couplerAssociationGeneration()),"first scoped output actually installed before second join");need(secondScope.equals(detached.couplerAssociationGeneration()),"second actual output carries its independent generation");cancelled=true;event.setCanceled(true);
    }
    public static NBTTagCompound evidence(EntityGroundVehicle c){NBTTagCompound n=new NBTTagCompound();n.setUniqueId("UUID",c.getUniqueID());n.setTag("Craft",c.structure.write());n.setDouble("X",c.posX);n.setDouble("Y",c.posY);n.setDouble("Z",c.posZ);n.setFloat("Yaw",c.rotationYaw);n.setFloat("Pitch",c.rotationPitch);if(c.couplerAssociationGeneration()!=null)n.setUniqueId("Scope",c.couplerAssociationGeneration());return n;}
    public static boolean tick(){if(!rolledBack)return false;need(world.getTotalWorldTime()-rollbackTick<400,"bounded real recovery retirement");if(VehicleCouplingTransfer.pendingCount(world)!=0)return false;
        int owners=0;for(Entity entity:world.loadedEntityList)if(entity instanceof EntityGroundVehicle&&!entity.isDead&&(entity.getUniqueID().equals(parent)||entity.getUniqueID().equals(child)))owners++;
        need(owners==1&&world.getEntityFromUuid(child)==null,"cancelled split leaves exactly one usable owner");EntityGroundVehicle c=(EntityGroundVehicle)world.getEntityFromUuid(parent);need(c!=null&&!c.transferInProgress&&!c.ownershipBlocked&&c.couplerAssociationGeneration()==null&&c.couplerScopeReady(),"original nullable owner is usable after WAL retirement");
        VehicleCouplingTransfer.fault=null;MinecraftForge.EVENT_BUS.unregister(OBSERVER);complete=true;return true;
    }
    public static UUID firstScope(){return firstScope;}public static UUID secondScope(){return secondScope;}public static long journalTick(){return journalTick;}public static long rollbackTick(){return rollbackTick;}
    public static void report(){need(complete&&restored!=null,"actual rollback evidence collected");System.out.println("[vandorlabs][reprolab] scoped-split-caught PASS checks="+checks+" beforeScope=NULL journalTick="+journalTick+" rollbackTick="+rollbackTick+" exactSource/pose/oneOwner/secondOutputJoinCancelled");}
    private VehicleScopedSplitRecoveryChecks(){}
}
