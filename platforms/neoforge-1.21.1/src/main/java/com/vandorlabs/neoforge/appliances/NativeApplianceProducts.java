package com.vandorlabs.neoforge.appliances;

import com.vandorlabs.neoforge.credentials.NativeChipTypes;
import com.vandorlabs.neoforge.navigation.*;
import com.vandorlabs.neoforge.screen.NativeChipJournal;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Server-owned document products. Seed-map work is bounded and never requests terrain chunks. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeApplianceProducts {
 private static final Map<NativeApplianceEntity,MapJob> JOBS=new LinkedHashMap<>();
 private static final Map<ServerPlayer,LinkedHashMap<net.minecraft.world.level.saveddata.maps.MapId,Integer>> SENT=new WeakHashMap<>();
 private static final class MapJob {
  final NativeApplianceEntity owner;final ServerPlayer player;final ServerLevel terrain;final ItemStack chip,ink,paper;final MapItemSavedData data;final com.vandorlabs.navigation.SavedMapPixels snapshot;final long started;final byte[] pixels=new byte[16384];final com.vandorlabs.appliance.PrinterPalette palette=new com.vandorlabs.appliance.PrinterPalette(NativeApplianceDocuments.mapPalette());int at;
  MapJob(NativeApplianceEntity owner,ServerPlayer player,ServerLevel terrain,ItemStack chip,ItemStack ink,ItemStack paper,int x,int z){this.owner=owner;this.player=player;this.terrain=terrain;this.chip=chip;this.ink=ink;this.paper=paper;data=MapItemSavedData.createFresh(x,z,(byte)0,true,false,terrain.dimension());started=player.serverLevel().getGameTime();snapshot=NativeNavigationChip.savedMap(chip);}
  boolean valid(){return owner.usable(player)&&owner.mutable()&&ItemStack.matches(chip,owner.chipStack())&&ItemStack.matches(ink,owner.inkStack())&&ItemStack.matches(paper,owner.paperStack())&&player.serverLevel().getGameTime()-started<=200;}
  void sample(){int x=data.centerX-64+(at&127),z=data.centerZ-64+(at>>7);boolean original=snapshot!=null&&snapshot.contains(x,z);int color=original?snapshot.colorAt(x,z):NativeNavigationRuntime.color(terrain,x,z,1);if(original?(color>>>24)==0:color==com.vandorlabs.navigation.NavigationMap.OUTSIDE||color==com.vandorlabs.navigation.NavigationMap.ERROR){pixels[at++]=0;return;}pixels[at++]=palette.pixel(color);}
  void finish(){if(!valid()||!space(owner))return;publish(player,owner,new ItemStack(Items.FILLED_MAP),chip,ink,paper,output->{data.colors=pixels;data.setDirty();var id=terrain.getFreeMapId();terrain.setMapData(id,data);var result=new ItemStack(Items.FILLED_MAP);result.set(DataComponents.MAP_ID,id);output.setItem(result);});}
 }
 public static boolean activate(ServerPlayer player,NativeApplianceEntity owner){
  if(!player.server.isSameThread()||!owner.usable(player)||!owner.mutable())return false;
  var chip=owner.chipStack();if(!NativeNavigationChip.item(chip))return false;
  if(owner.scanner())return scan(player,owner,chip);
  if(JOBS.containsKey(owner))return false;var ink=owner.inkStack();var paper=owner.paperStack();if(!ink.is(Items.INK_SAC)||!paper.is(Items.PAPER)||!space(owner))return false;
  var type=NativeChipTypes.type(chip);
  if(type.equals("navigation")){if(!NativeNavigationChip.programmed(chip)||JOBS.size()>=8)return false;var point=NativeNavigationChip.location(chip);ResourceLocation key=ResourceLocation.tryParse(NativeNavigationChip.dimension(point));if(key==null)return false;var target=player.server.getLevel(ResourceKey.create(Registries.DIMENSION,key));if(target==null||target!=player.serverLevel())return false;JOBS.put(owner,new MapJob(owner,player,target,chip,ink,paper,point.getInt("X"),point.getInt("Z")));return true;}
  var product=type.equals("journal")?NativeApplianceDocuments.journalBook(chip):type.equals("catalog")?catalog(chip):ItemStack.EMPTY;
  if(product.isEmpty())return false;return publish(player,owner,product,chip,ink,paper);
 }
 private static boolean scan(ServerPlayer player,NativeApplianceEntity owner,ItemStack chip){var source=owner.frameStack();ItemStack result;
  if(source.is(Items.FILLED_MAP))result=NativeApplianceDocuments.scanMap(player.serverLevel(),chip,source);
  else if(source.is(Items.WRITTEN_BOOK))result=NativeApplianceDocuments.scanBook(chip,source);
  else if(source.is(Items.ENCHANTED_BOOK)){result=chip.copyWithCount(1);var words=new StringBuilder();String alphabet="abcdefghijklmnopqrstuvwxyz";for(int word=0;word<45;word++){if(word>0)words.append(' ');for(int letter=0,n=3+player.getRandom().nextInt(5);letter<n;letter++)words.append(alphabet.charAt(player.getRandom().nextInt(alphabet.length())));}NativeChipJournal.write(result,true,"Enchanted Script",new String[]{words.toString()});var tag=result.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.putString("JournalFont","enchanting");result.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
  else return false;
  return !result.isEmpty()&&ItemStack.matches(source,owner.frameStack())&&owner.replaceChip(chip,result);
 }
 private static ItemStack catalog(ItemStack chip){var data=NativeChipTypes.data(chip).getCompound("Catalog");if(data.getInt("Version")!=com.vandorlabs.credentials.CatalogChipPolicy.VERSION)return ItemStack.EMPTY;var selected=new LinkedHashSet<String>();var designs=data.getCompound("Designs");for(String machine:com.vandorlabs.credentials.CatalogChipPolicy.MACHINES){var list=designs.getList(machine,net.minecraft.nbt.Tag.TAG_STRING);if(list.size()>com.vandorlabs.credentials.CatalogChipPolicy.MAX_DESIGNS)return ItemStack.EMPTY;for(int i=0;i<list.size();i++){String id=list.getString(i);if(id.startsWith("vandorlabs:"))id=id.substring(11);selected.add(com.vandorlabs.machines.BuilderRecipeCatalog.canonical(id));}}var entries=new ArrayList<String>();for(var design:com.vandorlabs.neoforge.builders.NativeBuilderDesigns.ALL)recipe(entries,selected,design.id(),design.width(),design.height(),design.depth());for(var design:com.vandorlabs.neoforge.navigation.observer.NativeObserverDesigns.ALL)recipe(entries,selected,design.id(),design.width(),design.height(),design.depth());return NativeApplianceDocuments.book("Multiblock Catalog",com.vandorlabs.appliance.ApplianceBookText.recipes(entries));}
 private static void recipe(List<String> entries,Set<String> selected,String id,int width,int height,int depth){if(!selected.contains(id)&&!selected.contains(id+"_core"))return;int count=-1;var layout=new com.vandorlabs.machines.CoreLayout(width,height,depth,2,0,0);for(int y=0;y<height;y++)for(int x=0;x<width;x++)for(int z=0;z<depth;z++)if(layout.frame(x,y,z))count++;entries.add(id.replace('_',' ')+"\n\nMaterials: "+count+" Programmable Matter Blocks; 1 "+(id+"_core").replace('_',' ')+".\n\n1. Build a "+width+" x "+depth+" solid base.\n2. Raise four corner pillars to "+height+" blocks high.\n3. Join their tops with a Matter rim. Keep the sides, interior and roof center open.\n4. Replace one bottom corner with the core, facing outward.\n5. Right-click the core to form.");}
 private static boolean space(NativeApplianceEntity owner){if(!(owner.getLevel() instanceof ServerLevel level)||!level.hasChunkAt(owner.getBlockPos().above()))return false;var p=owner.getBlockPos().above();return level.noCollision(new net.minecraft.world.phys.AABB(p).deflate(.1));}
 private static boolean publish(ServerPlayer player,NativeApplianceEntity owner,ItemStack product,ItemStack chip,ItemStack ink,ItemStack paper){return publish(player,owner,product,chip,ink,paper,item->{});}
 private static boolean publish(ServerPlayer player,NativeApplianceEntity owner,ItemStack product,ItemStack chip,ItemStack ink,ItemStack paper,java.util.function.Consumer<net.minecraft.world.entity.item.ItemEntity> complete){if(!owner.usable(player)||!space(owner))return false;var pos=owner.getBlockPos();var output=new net.minecraft.world.entity.item.ItemEntity(player.serverLevel(),pos.getX()+.5,pos.getY()+1.15,pos.getZ()+.5,product.copy());output.setDeltaMovement(0,.18,0);output.setDefaultPickUpDelay();try{if(!player.serverLevel().addFreshEntity(output)||output.isRemoved()||!ItemStack.matches(output.getItem(),product)){output.discard();return false;}}catch(RuntimeException failedSpawn){output.discard();com.mojang.logging.LogUtils.getLogger().warn("Printer product publication refused",failedSpawn);return false;}if(!owner.consumePrintInputs(chip,ink,paper,1,1)){output.discard();return false;}complete.accept(output);return true;}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){long until=System.nanoTime()+4000000;int remaining=1024;var visited=new ArrayList<NativeApplianceEntity>();var it=JOBS.entrySet().iterator();while(it.hasNext()&&remaining>0&&System.nanoTime()<until){var entry=it.next();var job=entry.getValue();visited.add(entry.getKey());if(!job.valid()){it.remove();continue;}int slice=Math.max(64,1024/Math.max(1,JOBS.size()));try{while(job.at<job.pixels.length&&slice-->0&&remaining-->0&&System.nanoTime()<until)job.sample();if(job.at==job.pixels.length){it.remove();try{job.finish();}catch(RuntimeException publication){com.mojang.logging.LogUtils.getLogger().warn("Printer product publication failed",publication);}}}catch(RuntimeException unavailable){it.remove();com.mojang.logging.LogUtils.getLogger().warn("Printer seed map is unavailable",unavailable);}}for(var owner:visited){var job=JOBS.remove(owner);if(job!=null)JOBS.put(owner,job);}}
 @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event){JOBS.clear();SENT.clear();}
 /** A single vanilla map packet hydrates a newly tracking viewer; normal BE updates carry only MAP_ID. */
 public static void display(ServerPlayer player,NativeApplianceEntity owner){if(!(owner.getLevel() instanceof ServerLevel level)||!level.getServer().isSameThread()||player.serverLevel()!=level||!level.hasChunkAt(owner.getBlockPos())||level.getBlockEntity(owner.getBlockPos())!=owner||!level.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(owner.getBlockPos()),false).contains(player))return;var frame=owner.frameStack();var id=frame.get(DataComponents.MAP_ID);if(frame.is(Items.FILLED_MAP)&&id!=null)sendMap(player,level,id);}
 private static void sendMap(ServerPlayer player,ServerLevel level,net.minecraft.world.level.saveddata.maps.MapId id){var data=MapItem.getSavedData(id,level);if(data==null||data.colors.length!=16384)return;int hash=31*(31*Arrays.hashCode(data.colors)+data.scale)+(data.locked?1:0);var sent=SENT.computeIfAbsent(player,p->new LinkedHashMap<>());if(Objects.equals(sent.get(id),hash))return;if(sent.size()>=64)sent.remove(sent.keySet().iterator().next());sent.put(id,hash);player.connection.send(new net.minecraft.network.protocol.game.ClientboundMapItemDataPacket(id,data.scale,data.locked,(java.util.Collection<net.minecraft.world.level.saveddata.maps.MapDecoration>)null,new MapItemSavedData.MapPatch(0,0,128,128,data.colors.clone())));}
 @SubscribeEvent public static void chunkSent(net.neoforged.neoforge.event.level.ChunkWatchEvent.Sent event){if(!event.getLevel().getServer().isSameThread())return;for(var tile:event.getChunk().getBlockEntities().values())if(tile instanceof NativeApplianceEntity appliance)display(event.getPlayer(),appliance);else if(tile instanceof com.vandorlabs.neoforge.gear.NativeGearEntity arm)com.vandorlabs.neoforge.gear.NativeAppliancePayloadMaps.visit(arm,id->sendMap(event.getPlayer(),event.getLevel(),id));}
 public static boolean pending(NativeApplianceEntity owner){return JOBS.containsKey(owner);}
 private NativeApplianceProducts(){}
}
