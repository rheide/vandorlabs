package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.persistence.JournalFiles;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Actual scheduled belt item, independently saved native entity/terrain phases, then ordinary return. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRampBeltColdChecks {
 private static final String PREFIX="[vandorlabs][ramp-belt-cold] ";
 private static final BlockPos ROOT=new BlockPos(23600,100,23600),A=ROOT.north(),B=ROOT.north(2);
 private static final UUID ITEM=new UUID(211,8101);private static ServerLevel level;private static ServerPlayer actor;
 private static CompoundTag proof;private static int stage,ticks,checks,started;private static double earlier;private static Vec3 saved;
 private static String phase(){return System.getProperty("vandorlabs.rampBeltCold","");}
 private static boolean vertical(){return phase().startsWith("vertical");}
 private static boolean reading(){return phase().endsWith("read");}
 private static boolean rootAhead(){return phase().contains("root-ahead");}
 private static NativeRampEntity owner(){return (NativeRampEntity)level.getBlockEntity(ROOT);}
 private static ItemEntity item(){return level.getEntity(ITEM) instanceof ItemEntity item?item:null;}
 private static void need(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message+" phase="+phase()+" stage="+stage+" ticks="+ticks+" runtime="+(owner()==null?"none":owner().controller().status()));}
 private static Path file(){return level.getServer().getWorldPath(LevelResource.ROOT).resolve("ramp-belt-cold-proof.nbt");}
 private static void store(){try{NbtIo.writeCompressed(proof,file());try(var channel=java.nio.channels.FileChannel.open(file(),StandardOpenOption.WRITE)){channel.force(true);}JournalFiles.syncDirectory(file().getParent());}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
 @SubscribeEvent public static void start(ServerStartedEvent event){
  if(phase().isEmpty())return;try{
   level=event.getServer().overworld();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var pos=ROOT.offset(x*16,0,z*16);level.getChunkAt(pos);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);}
   actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(new UUID(211,8102),"RampBeltCold"));actor.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);actor.setPos(ROOT.getX()+.5,ROOT.getY()+4,ROOT.getZ()+.5);actor.setNoGravity(true);
   if(reading()){proof=NbtIo.readCompressed(file(),NbtAccounter.create(1024*1024));need(owner()!=null&&!owner().inert()&&owner().controller().phase()==NativeRampController.Phase.ACTIVE,"healthy belt adopted before gameplay");stage=10;return;}
   NativeRampInactiveSettingsChecks.run(level);
   for(var pos:BlockPos.betweenClosed(ROOT.offset(-3,-3,-4),ROOT.offset(3,6,2)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);
   level.setBlock(ROOT,NativeRampBindings.BLOCK.defaultBlockState(),18);proof=new CompoundTag();var rows=new ListTag();for(var pos:vertical()?List.of(A,A.above()):List.of(A,B)){level.setBlock(pos,com.vandorlabs.neoforge.VandorLabsBootstrap.PROGRAMMABLE_SLAB.get().defaultBlockState(),18);level.getBlockEntity(pos).getPersistentData().putString("BeltColdOriginal","retain");var row=new CompoundTag();row.putLong("Pos",pos.asLong());row.put("Tile",level.getBlockEntity(pos).saveWithFullMetadata(level.registryAccess()));rows.add(row);}proof.put("Sources",rows);level.addNewPlayer(actor);stage=0;
  }catch(Throwable failure){fail(failure);}
 }
 @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(level==null)return;try{
  need(++ticks<1600,"bounded belt cold lifecycle");var runtime=owner().controller();if(stage>0)need(!runtime.status().error(),"ordinary belt remains healthy");
  if(stage==0&&ticks>=4){var choices=new NativeRampSettings(0,4,8,false,false,false,0,true,0,1,false,vertical(),0,true,2,com.vandorlabs.redstone.ChannelList.EMPTY);need(!runtime.configure(actor,choices).activationRefused(),"canonical scheduled belt configured");stage=1;return;}
  if(stage==1){if(runtime.committedPose()!=1||runtime.status().moving())return;var operation=NativeRampJournal.atRoot(level,ROOT);var surfaces=NativeRampBeltGeometry.surfaces(operation.assembly().footprint(),false,runtime.committedBeltDistance());AABB selected=null;for(var box:surfaces)if(box.getXsize()>=.26&&box.getZsize()>=.26){selected=box;break;}need(selected!=null,"actual emitted belt has item support");var stack=new ItemStack(Items.DIAMOND,17);var data=new CompoundTag();data.putString("BeltPrivate","unchanged");data.putByteArray("Opaque",new byte[257]);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));var item=new ItemEntity(level,(selected.minX+selected.maxX)*.5,selected.maxY,(selected.minZ+selected.maxZ)*.5,stack);item.setUUID(ITEM);item.setNoGravity(true);item.setNeverPickUp();item.setUnlimitedLifetime();item.setDeltaMovement(Vec3.ZERO);item.setOnGround(true);need(level.addFreshEntity(item),"one physical item installed on actual belt surface");proof.put("Stack",com.vandorlabs.neoforge.inventory.NativeInventoryItems.write(stack,level.registryAccess()));started=ticks;stage=2;return;}
  if(stage==2){var item=item();if(ticks-started<4||NativeRampSupportStamps.capture(item,NativeRampJournal.atRoot(level,ROOT))==null)return;earlier=runtime.committedBeltDistance();saved=item.position();item.setOnGround(false);if(rootAhead())saveOnlyEntities();else saveOnlyTerrain();started=ticks;stage=3;return;}
  if(stage==3){if(ticks-started<5)return;var item=item();need(runtime.committedBeltDistance()!=earlier,"ordinary scheduled belt advances between independent saves");need(NativeRampSupportStamps.capture(item,NativeRampJournal.atRoot(level,ROOT))!=null,"native physical item remains supported");if(rootAhead())saveOnlyTerrain();else{saved=item.position();item.setOnGround(false);saveOnlyEntities();}proof.putUUID("Operation",NativeRampJournal.atRoot(level,ROOT).id());proof.putDouble("RootDistance",rootAhead()?runtime.committedBeltDistance():earlier);proof.putDouble("SavedX",saved.x);proof.putDouble("SavedZ",saved.z);store();System.out.println(PREFIX+"write PASS checks="+checks+" actualScheduledBelt/independentTerrainEntityPhases/groundFalse/privateItem");System.out.flush();clearSavedTickets();Runtime.getRuntime().halt(0);}
  if(stage==10){var item=item();if(item==null)return;resources();var op=NativeRampJournal.atRoot(level,ROOT);need(op!=null&&op.id().equals(proof.getUUID("Operation")),"same durable operation adopted");need(runtime.committedBeltDistance()==proof.getDouble("RootDistance"),"sleeping cold belt retains saved terrain phase without elapsed catchup");need(item.getX()==proof.getDouble("SavedX")&&item.getZ()==proof.getDouble("SavedZ"),"independent actor horizontal position preserved without conveyor wrap");var stamp=NativeRampSupportStamps.capture(item,op);need(stamp!=null&&stamp.distance==runtime.committedBeltDistance(),"mapped native actor supported by actual current-phase collision");need(!runtime.configure(actor,NativeRampSettings.defaults()).activationRefused(),"ordinary loaded belt return admitted");stage=11;return;}
  if(stage==11){if(runtime.phase()!=NativeRampController.Phase.IDLE)return;resources();need(NativeRampJournal.atRoot(level,ROOT)==null&&!NativeRampHeldOwners.active(level),"belt actor and native source owner retired exactly once");for(var tag:proof.getList("Sources",Tag.TAG_COMPOUND)){var row=(CompoundTag)tag;var pos=BlockPos.of(row.getLong("Pos"));need(level.getBlockEntity(pos)!=null&&NativeRampJournal.sameSnapshot(row.getCompound("Tile"),level.getBlockEntity(pos).saveWithFullMetadata(level.registryAccess())),"exact native source returned");}System.out.println(PREFIX+"read PASS checks="+checks+" currentPhaseSupport/independentSavedXZ/one17DiamondOwner/ordinaryReturn");var server=level.getServer();level=null;server.halt(false);}
 }catch(Throwable failure){fail(failure);}}
 private static void resources(){need(item()!=null&&com.vandorlabs.neoforge.inventory.NativeInventoryItems.write(item().getItem(),level.registryAccess()).equals(proof.getCompound("Stack")),"exact item components/count retained");need(level.getEntitiesOfClass(ItemEntity.class,new AABB(ROOT).inflate(16)).stream().filter(e->e.getUUID().equals(ITEM)).count()==1,"exactly one usable item UUID");}
    private static void clearSavedTickets(){level.noSave=true;var center=new net.minecraft.world.level.ChunkPos(B);for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.setChunkForced(center.x+x,center.z+z,false);level.getDataStorage().save();}
    private static void saveOnlyTerrain(){var operation=NativeRampJournal.atRoot(level,ROOT);var chunks=new LinkedHashSet<net.minecraft.world.level.ChunkPos>();chunks.add(new net.minecraft.world.level.ChunkPos(ROOT));for(var pos:operation.assembly().potential())chunks.add(new net.minecraft.world.level.ChunkPos(pos));for(var chunkPos:chunks){var chunk=level.getChunkSource().getChunkNow(chunkPos.x,chunkPos.z);need(chunk!=null,"terrain-only checkpoint retains loaded assembly chunk");level.getChunkSource().chunkMap.write(chunkPos,net.minecraft.world.level.chunk.storage.ChunkSerializer.write(level,chunk)).join();}((com.vandorlabs.neoforge.mixin.NativeRampChunkStorageAccess)(Object)level.getChunkSource().chunkMap).vandorlabs$rampWorker().synchronize(true).join();}
    private static void saveOnlyEntities(){try{for(var field:ServerLevel.class.getDeclaredFields())if(net.minecraft.world.level.entity.PersistentEntitySectionManager.class.isAssignableFrom(field.getType())){field.setAccessible(true);((net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)field.get(level)).saveAll();return;}throw new IllegalStateException("Real entity section manager missing");}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
 private static void fail(Throwable failure){System.err.println(PREFIX+"FAIL "+failure);failure.printStackTrace();if(level!=null){var server=level.getServer();level=null;server.halt(false);}}
 private NativeRampBeltColdChecks(){}
}
