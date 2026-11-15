package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.mixin.NativeRampEntityLevelAccess;
import com.vandorlabs.neoforge.mixin.NativeRampEntityManagerAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Native actor save futures and abrupt durability, independently of later harvest policy. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeGearActorCheckpointChecks {
    private static final BlockPos ROOT=new BlockPos(30008,120,30008);
    private static final UUID[] IDS={new UUID(210,501),new UUID(210,502),new UUID(210,503)};
    private static ServerLevel level;private static NativeGearActorCheckpoint checkpoint;private static int ticks,checks;private static boolean added,running;
    private static String phase(){return System.getProperty("vandorlabs.gearActorCheck","");}
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Native actor checkpoint: "+why);}
    private static void spawn(int index,Item item,int count){var entity=new ItemEntity(level,ROOT.getX()+.25+index*.2,ROOT.getY(),ROOT.getZ()+.5,new ItemStack(item,count));entity.setUUID(IDS[index]);entity.setNoGravity(true);entity.setInvulnerable(true);entity.setPickUpDelay(32767);entity.getPersistentData().putString("CheckpointSentinel","unchanged-"+index);need(level.addFreshEntity(entity),"real native ItemEntity publication "+index);}
    @SubscribeEvent public static void started(ServerStartedEvent event){if(phase().isEmpty())return;try{level=event.getServer().overworld();level.getChunkAt(ROOT);level.setChunkForced(ROOT.getX()>>4,ROOT.getZ()>>4,true);checkpoint=new NativeGearActorCheckpoint(level,List.of(new ChunkPos(ROOT)),()->{if(!running)throw new IllegalStateException("Expired actor checkpoint authority");});running=true;if(phase().equals("write")){event.getServer().saveEverything(true,true,true);spawn(0,Items.DIAMOND,7);spawn(1,Items.GOLD_INGOT,13);boolean before=level.noSave;try{level.noSave=true;need(!checkpoint.poll(),"disabled saving remains pending");}finally{level.noSave=before;}}}catch(Throwable failure){failed(failure);}}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(!running)return;try{need(++ticks<1200,"native async checkpoint timeout");if(phase().equals("read")){if(!checkpoint.loaded())return;Item[] items={Items.DIAMOND,Items.GOLD_INGOT,Items.IRON_INGOT};int[] amounts={7,13,19};for(int i=0;i<IDS.length;i++){final UUID expected=IDS[i];var manager=(NativeRampEntityManagerAccess)(Object)((NativeRampEntityLevelAccess)(Object)level).vandorlabs$rampActorManager();var entity=manager.vandorlabs$rampActorSections().getExistingSectionsInChunk(new ChunkPos(ROOT).toLong()).flatMap(section->section.getEntities()).filter(value->value.getUUID().equals(expected)).findFirst().orElse(null);need(entity instanceof ItemEntity,"exact native actor UUID restored "+i);var item=(ItemEntity)entity;need(item.getItem().is(items[i])&&item.getItem().getCount()==amounts[i],"exact native resource count "+i);need(item.getPersistentData().getString("CheckpointSentinel").equals("unchanged-"+i),"unrelated actor metadata preserved "+i);}running=false;System.out.println("[vandorlabs][gear-actor-checkpoint] read PASS checks="+checks+" actual native actor records after HALT137; unrelated newer native write retained");event.getServer().halt(false);return;}
            boolean complete=checkpoint.poll();var delegateField=NativeGearActorCheckpoint.class.getDeclaredField("checkpoint");delegateField.setAccessible(true);var delegate=delegateField.get(checkpoint);var field=delegate.getClass().getDeclaredField("index");field.setAccessible(true);if(!added&&field.getInt(delegate)>0){added=true;spawn(2,Items.IRON_INGOT,19);((PersistentEntitySectionManager<?>)((NativeRampEntityLevelAccess)(Object)level).vandorlabs$rampActorManager()).autoSave();}
            if(!complete)return;need(added,"new unrelated native actor write followed initial serialization");running=false;boolean refused=false;try{checkpoint.poll();}catch(IllegalStateException expected){refused=true;}need(refused,"expired authority refused even after durable completion");System.out.println("[vandorlabs][gear-actor-checkpoint] write PASS checks="+checks+" native actual write future and sync completed; HALT137 before any broad save");System.out.flush();Runtime.getRuntime().halt(137);
        }catch(Throwable failure){failed(failure);}}
    private static void failed(Throwable failure){running=false;failure.printStackTrace();System.err.println("[vandorlabs][gear-actor-checkpoint] FAIL "+failure);if(level!=null)level.getServer().halt(false);}
    private NativeGearActorCheckpointChecks(){}
}
