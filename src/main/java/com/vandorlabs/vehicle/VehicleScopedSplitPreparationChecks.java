package com.vandorlabs.vehicle;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;

/** Actual shared-worker journal failure before any ownership publication. Repro only. */
public final class VehicleScopedSplitPreparationChecks {
    private static WorldServer world;private static UUID id;private static Path directory;private static CountDownLatch release;private static Future<?> blocker;private static boolean observing,failed;private static UUID beforeScope;private static int checks,ticks;
    public static boolean active(){return "1".equals(System.getenv("VANDOR_LABS_COUPLER_SCOPED_SPLIT_PREJOURNAL"));}
    private static void need(boolean ok,String why){checks++;if(!ok){if(release!=null)release.countDown();throw new IllegalStateException("Scoped split preparation: "+why);}}
    private static Object field(Object owner,String name)throws Exception{java.lang.reflect.Field f=(owner instanceof Class?(Class<?>)owner:owner.getClass()).getDeclaredField(name);f.setAccessible(true);return f.get(owner instanceof Class?null:owner);}
    public static void arm(WorldServer w,UUID uuid)throws Exception{
        need(active()&&System.getProperty("vandorlabs.reprolab")!=null,"repro-only real input");world=w;id=uuid;CountDownLatch entered=new CountDownLatch(1);release=new CountDownLatch(1);blocker=TransferJournalIO.submit(()->{entered.countDown();release.await();});need(entered.await(5,TimeUnit.SECONDS),"existing asynchronous journal worker blocked");
        directory=w.getSaveHandler().getWorldDirectory().toPath().resolve("data").resolve("vandorlabs-couplings-"+w.provider.getDimension());if(Files.isDirectory(directory)){try(java.nio.file.DirectoryStream<Path> entries=Files.newDirectoryStream(directory)){need(!entries.iterator().hasNext(),"no existing journals changed by fixture");}Files.delete(directory);}need(!Files.exists(directory),"fresh disposable journal location");Files.write(directory,new byte[]{0x46,0x41,0x49,0x4c});
    }
    @SuppressWarnings("unchecked") public static boolean tick()throws Exception{
        need(++ticks<500,"bounded actual preparation failure");if(failed){need(VehicleCouplingTransfer.pendingCount(world)==0,"failed preparation creates no live WAL owner");EntityGroundVehicle c=(EntityGroundVehicle)world.getEntityFromUuid(id);need(c!=null&&!c.transferInProgress&&!c.ownershipBlocked&&Objects.equals(c.couplerAssociationGeneration(),beforeScope)&&c.couplerScopeReady(),"original exact-scope owner remains usable");int owners=0;for(Entity e:world.loadedEntityList)if(e instanceof EntityGroundVehicle&&!e.isDead)owners++;need(owners==1,"no second owner before journal publication");Files.delete(directory);Files.createDirectories(directory);blocker.get();System.out.println("[vandorlabs][reprolab] scoped-split-prejournal PASS checks="+checks+" realSharedWorker/filesystemFailure/exactPreparedBefore/originalScope/oneOwner");return true;}
        if(observing)return false;ArrayDeque<?> queue=(ArrayDeque<?>)((Map<?,?>)field(VehicleCouplingTransfer.class,"QUEUES")).get(world);if(queue==null||queue.isEmpty())return false;Object work=queue.peek(),hold=field(work,"hold");if(hold==null)return false;Future<?> write=(Future<?>)field(work,"journal");need(write!=null&&!write.isDone(),"real pending disk operation is not gameplay rejection");NBTTagCompound record=((NBTTagCompound)field(hold,"record")).copy(),before=record.getCompoundTag("Before");EntityGroundVehicle c=(EntityGroundVehicle)world.getEntityFromUuid(id);beforeScope=VehicleScopedSplitRecoveryChecks.originalScope(before,c);need(c.structure.write().equals(before.getCompoundTag("Craft"))&&Objects.equals(c.couplerAssociationGeneration(),beforeScope)&&c.couplerScopeReady(),"exact frozen original prepared before-image and scope");
        java.lang.reflect.Field callback=work.getClass().getDeclaredField("failed");callback.setAccessible(true);Consumer<Exception> previous=(Consumer<Exception>)callback.get(work);callback.set(work,(Consumer<Exception>)(error->{try{Throwable root=error;while(root.getCause()!=null)root=root.getCause();need(root instanceof java.io.IOException,"real filesystem I/O failure propagates: "+error);EntityGroundVehicle actual=(EntityGroundVehicle)world.getEntityFromUuid(id);need(actual!=null&&actual.structure.write().equals(before.getCompoundTag("Craft"))&&Objects.equals(actual.couplerAssociationGeneration(),beforeScope),"disk failure leaves exact Source and original scope unchanged");failed=true;}finally{previous.accept(error);}}));observing=true;release.countDown();return false;
    }
    private VehicleScopedSplitPreparationChecks(){}
}
