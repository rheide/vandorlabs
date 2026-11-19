package com.vandorlabs.neoforge.navigation;
import net.minecraft.world.item.component.CustomData;

import com.vandorlabs.navigation.*;
import com.vandorlabs.neoforge.navigation.observer.NativeNavigationSources;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Bounded cooperative seed work, always on the authoritative server thread. No chunk requests. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeNavigationRuntime {
 public static final class View {public int x,z,zoom=4;boolean initialized,pin;ItemStack module=ItemStack.EMPTY;int pinX,pinZ;long lastAction=-100;CustomData surfaceTag;NavigationSurface.Snapshot surfaceChip;}
 private static final Map<BlockEntity,View> VIEWS=new WeakHashMap<>();
 private static final Map<ServerLevel,TerrainState> TERRAIN_WORLDS=new IdentityHashMap<>();
 private static final NavigationTerrainCache TERRAIN=new NavigationTerrainCache(32*1024*1024);
 private static final LinkedHashMap<String,Job> JOBS=new LinkedHashMap<>();
 private record RasterKey(ServerLevel level,TerrainState terrain,int x,int z,int zoom,int width,int height,int mapBottom,boolean progressive,List<NativeNavigationModuleData.Area> areas,SavedMapPixels snapshot,NavigationSurface.View surface){}
 private record Raster(int revision,int[] pixels,int columns,int rows,int areas,long tick,boolean errors,
                       boolean refined,int snapshotBytes,long retryDetailAfter,long surfaceRevision){
  int bytes(){return 64+pixels.length*4+areas*48+snapshotBytes;}
  Raster delayRefinement(long until){
   return new Raster(revision,pixels,columns,rows,areas,tick,errors,refined,snapshotBytes,until,surfaceRevision);
  }
  Raster refreshed(long time,long version){
   return new Raster(revision,pixels,columns,rows,areas,time,errors,refined,snapshotBytes,0,version);
  }
 }
 private static final LinkedHashMap<RasterKey,Raster> RASTERS=new LinkedHashMap<>(32,.75f,true);
 private static final Map<UUID,long[]> RATES=new HashMap<>();private static int revision,bytes;
 public static long sampledPixels,rasterHits;public static long terrainSamples(){return TERRAIN.samples;}public static long terrainHits(){return TERRAIN.hits;}
 public static void install(){com.vandorlabs.neoforge.screen.NativeScreenNavigation.SERVER=(player,screen,slot,action)->player instanceof ServerPlayer p&&action(p,host(screen,slot),action);NativeNavigationTableHooks.TABLE=new NativeNavigationTableHooks.Table(){public boolean action(Player p,NativeNavigationTableEntity t,int a){if(p instanceof ServerPlayer actor)return NativeNavigationRuntime.action(actor,t,a);return false;}public void changed(NativeNavigationTableEntity t){}public void reparent(NativeNavigationTableEntity from,NativeNavigationTableEntity to){if(from!=to){var view=VIEWS.remove(from);if(view!=null)VIEWS.put(to,view);}}public void recordSources(NativeNavigationTableEntity t){NativeNavigationRuntime.recordSources(t);}public boolean moduleItem(ItemStack s){return NativeNavigationModuleData.item(s);}public ItemStack moduleSummary(ItemStack s){return NativeNavigationModuleData.summary(s);}public boolean chipItem(ItemStack s){return NativeNavigationChip.item(s);}};}
 private static final class Host {
 final BlockEntity tile;final int slot;Host(BlockEntity tile,int slot){this.tile=tile;this.slot=slot;}
 net.minecraft.world.level.Level getLevel(){return tile.getLevel();}BlockPos getBlockPos(){return tile.getBlockPos();}
 NavigationCanvas canvas(){return tile instanceof NativeNavigationTableEntity t?t.canvas():com.vandorlabs.neoforge.screen.NativeScreenNavigation.canvas((com.vandorlabs.neoforge.screen.NativeScreenEntity)tile,slot);}
 ItemStack chipStack(){return tile instanceof NativeNavigationTableEntity t?t.chipStack():com.vandorlabs.neoforge.screen.NativeScreenNavigation.chipStack((com.vandorlabs.neoforge.screen.NativeScreenEntity)tile);}
 com.vandorlabs.redstone.ChannelList rxChannels(){return tile instanceof NativeNavigationTableEntity t?t.rxChannels():com.vandorlabs.neoforge.screen.NativeScreenNavigation.channels((com.vandorlabs.neoforge.screen.NativeScreenEntity)tile);}
 ItemStack moduleStack(){return tile instanceof NativeNavigationTableEntity t?t.moduleStack():ItemStack.EMPTY;}
 boolean setChip(ItemStack stack){return tile instanceof NativeNavigationTableEntity t&&t.setChip(stack);}
 }
 private static Host host(BlockEntity tile,int slot){if(tile instanceof NativeNavigationTableEntity t){var reader=t.reader();return new Host(reader==null?t:reader,slot);}if(tile instanceof com.vandorlabs.neoforge.screen.NativeScreenEntity t){var owner=com.vandorlabs.neoforge.screen.NativeScreenNavigation.owner(t);return owner==null?null:new Host(owner,slot);}return null;}
 public static NativeNavigationTableEntity owner(NativeNavigationTableEntity t){var r=t.reader();return r==null?t:r;}
 private static boolean selected(Host h){return h!=null&&(h.tile instanceof NativeNavigationTableEntity t?selected(t):com.vandorlabs.neoforge.screen.NativeScreenNavigation.selected((com.vandorlabs.neoforge.screen.NativeScreenEntity)h.tile,h.slot));}
 private static boolean usable(ServerPlayer p,Host h){return h!=null&&(h.tile instanceof NativeNavigationTableEntity t?usable(p,t):com.vandorlabs.neoforge.screen.NativeScreenNavigation.usable(p,(com.vandorlabs.neoforge.screen.NativeScreenEntity)h.tile));}
 public static boolean selected(NativeNavigationTableEntity t){return t!=null&&!t.isRemoved()&&t.formed()&&t.selectedNavigation();}
 public static boolean usable(ServerPlayer p,NativeNavigationTableEntity t){if(t==null||p.level()!=t.getLevel()||!p.isAlive()||p.isSpectator()||!p.serverLevel().hasChunkAt(t.getBlockPos())||p.serverLevel().getBlockEntity(t.getBlockPos())!=t)return false;var plane=t.plane();double minX=Math.min(plane.topLeft.x,plane.bottomRight.x),maxX=Math.max(plane.topLeft.x,plane.bottomRight.x),minY=Math.min(plane.topLeft.y,plane.bottomRight.y),maxY=Math.max(plane.topLeft.y,plane.bottomRight.y),minZ=Math.min(plane.topLeft.z,plane.bottomRight.z),maxZ=Math.max(plane.topLeft.z,plane.bottomRight.z);double x=Math.clamp(p.getX(),minX,maxX),y=Math.clamp(p.getY(),minY,maxY),z=Math.clamp(p.getZ(),minZ,maxZ);return p.distanceToSqr(x,y,z)<=64;}
 private static boolean authorized(ServerPlayer p,Host h){if(h.tile instanceof com.vandorlabs.neoforge.screen.NativeScreenEntity screen)return com.vandorlabs.neoforge.screen.NativeScreenNavigation.usable(p,screen);if(!(h.tile instanceof NativeNavigationTableEntity table))return false;for(var at:BlockPos.betweenClosed(new BlockPos(p.blockPosition().getX()-8,table.getBlockPos().getY(),p.blockPosition().getZ()-8),new BlockPos(p.blockPosition().getX()+8,table.getBlockPos().getY(),p.blockPosition().getZ()+8))){if(!h.getLevel().hasChunkAt(at))continue;if(h.getLevel().getBlockEntity(at) instanceof NativeNavigationTableEntity member&&owner(member)==table&&com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(p,member))return true;}return false;}
 public static boolean recordSources(NativeNavigationTableEntity t){if(!(t.getLevel() instanceof ServerLevel level)||com.vandorlabs.neoforge.gear.NativeGearHolds.reserved(t.getLevel(),t.getBlockPos())||com.vandorlabs.neoforge.transfer.NativeTransferHolds.reserved(t.getLevel(),t.getBlockPos()))return false;var storage=t.moduleStorage();if(storage==null||!NativeNavigationModuleData.item(storage.storedModule())||com.vandorlabs.neoforge.gear.NativeGearHolds.reserved(storage.getLevel(),storage.getBlockPos())||com.vandorlabs.neoforge.transfer.NativeTransferHolds.reserved(storage.getLevel(),storage.getBlockPos()))return false;
  var h=host(t,0);
  var linked=NativeNavigationSources.matching(level,h.getBlockPos(),h.rxChannels());
  boolean changed=NativeNavigationModuleData.record(storage.storedModule(),level,toCoverage(linked));
  if(NativeNavigationModuleData.inDimension(storage.storedModule(),level)){
   int[] live=liveSurface(level,linked,true);
   if(live.length>0)changed|=NativeNavigationModuleData.recordSurface(storage.storedModule(),level,live,
           NativeNavigationSurfaceData.get(level).atlas);
  }
  if(changed)storage.changed();return changed;}
 public static SavedMapPixels overrideMapSnapshot(BlockEntity tile,int slot){var t=host(tile,slot);if(t==null)return null;var map=NativeNavigationChip.savedMap(t.chipStack());return map!=null&&map.dimension().equals(NativeNavigationChip.dimension(t.getLevel()))?map:null;}
 private static boolean snapshotContains(Host t,int x,int z){var map=overrideMapSnapshot(t.tile,t.slot);return map!=null&&map.contains(x,z);}

 private static List<NativeNavigationModuleData.Area> toCoverage(List<NativeNavigationSources.Area> linked){
  return linked.stream().map(a->new NativeNavigationModuleData.Area(a.x(),a.z(),a.radius())).toList();
 }
 private static int[] liveSurface(ServerLevel world,List<NativeNavigationSources.Area> sources,boolean register){
  int[] circles=new int[3*Math.min(sources.size(),NavigationOverlay.MAX_CIRCLES)];
  int count=0;
  if(world.dimensionType().hasCeiling())return Arrays.copyOf(circles,0);
  NativeNavigationSurfaceData data=null;
  for(var area:sources){
   if(area.stationary()==null||area.radarRadius()==0)continue;
   if(count==circles.length)break;
   if(register){
    if(data==null)data=NativeNavigationSurfaceData.get(world);
    data.observe(world,area);
   }
   circles[count++]=NavigationMap.bound(area.x());circles[count++]=NavigationMap.bound(area.z());circles[count++]=area.radarRadius();
  }
  return Arrays.copyOf(circles,count);
 }
 private static NavigationSurface.View surface(Host host,List<NativeNavigationSources.Area> linked,View view,boolean register){
  if(!(host.getLevel() instanceof ServerLevel world))return NavigationSurface.View.EMPTY;
  var stack=host.chipStack();
  var tag=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
  if(tag!=view.surfaceTag){view.surfaceTag=tag;view.surfaceChip=NativeNavigationChip.surface(stack,world);}
  int[] areas=new int[0];
  NavigationSurface.Atlas atlas=null;
  if(host.tile instanceof NativeNavigationTableEntity){
   int[] live=liveSurface(world,linked,register);
   var module=host.moduleStack();
   if(live.length>0||NativeNavigationModuleData.hasSurface(module)){
    atlas=NativeNavigationSurfaceData.get(world).atlas;
    areas=NavigationSurface.mergeAreas(NativeNavigationModuleData.surface(module,world,atlas),live);
   }
  }
  return areas.length==0&&view.surfaceChip==null?NavigationSurface.View.EMPTY:
          new NavigationSurface.View(atlas,areas,view.surfaceChip);
 }
 private static NavigationSurface.Snapshot captureSurface(Host host,int x,int z){
  if(!(host.getLevel() instanceof ServerLevel world))return null;
  return surface(host,NativeNavigationSources.matching(world,host.getBlockPos(),host.rxChannels()),view(host),false).capture(x,z);
 }
 public static List<NativeNavigationModuleData.Area> coverage(NativeNavigationTableEntity t){return coverage(host(t,0));}
 private static List<NativeNavigationModuleData.Area> coverage(Host t){return coverage(t,NativeNavigationSources.matching(t.getLevel(),t.getBlockPos(),t.rxChannels()));}
 private static List<NativeNavigationModuleData.Area> coverage(Host t,List<NativeNavigationSources.Area> linked){var result=new ArrayList<>(toCoverage(linked));var chip=t.chipStack();if(NativeNavigationChip.inDimension(chip,t.getLevel())){var point=NativeNavigationChip.location(chip);result.add(new NativeNavigationModuleData.Area(point.getInt("X"),point.getInt("Z"),1000));}result.addAll(NativeNavigationModuleData.areas(t.moduleStack(),t.getLevel()));return result.stream().distinct().sorted(Comparator.comparingInt(NativeNavigationModuleData.Area::x).thenComparingInt(NativeNavigationModuleData.Area::z).thenComparingInt(NativeNavigationModuleData.Area::radius)).toList();}
 public static View view(NativeNavigationTableEntity raw){return view(host(raw,0));}
 private static View view(Host t){var v=VIEWS.computeIfAbsent(t.tile,k->new View());if(!(t.tile instanceof NativeNavigationTableEntity table)||table.moduleStorage()!=null){var module=t.moduleStack();if(module!=v.module){if(!ItemStack.isSameItemSameComponents(module,v.module))v.initialized=false;v.module=module;}}var currentChip=t.chipStack();boolean pin=NativeNavigationChip.inDimension(currentChip,t.getLevel());var point=NativeNavigationChip.location(currentChip);int pinX=point.getInt("X"),pinZ=point.getInt("Z");if(pin&&(!v.pin||v.pinX!=pinX||v.pinZ!=pinZ)){v.initialized=false;v.zoom=4;}v.pin=pin;v.pinX=pinX;v.pinZ=pinZ;if(!v.initialized){var chip=currentChip;var areas=coverage(t);if(NativeNavigationChip.inDimension(chip,t.getLevel())){var p=NativeNavigationChip.location(chip);v.x=p.getInt("X");v.z=p.getInt("Z");}else if(!areas.isEmpty()){v.x=areas.getFirst().x();v.z=areas.getFirst().z();v.zoom=NavigationMap.initialZoomForRadius(areas.getFirst().radius());}else{v.x=t.getBlockPos().getX();v.z=t.getBlockPos().getZ();}v.initialized=!areas.isEmpty();}return v;}
 public static boolean action(ServerPlayer p,NativeNavigationTableEntity raw,int action){return action(p,host(raw,0),action);}
 private static boolean action(ServerPlayer p,Host raw,int action){if(action<1||action>8||!selected(raw)||!usable(p,raw)||!authorized(p,raw)||com.vandorlabs.neoforge.gear.NativeGearHolds.reserved(raw.getLevel(),raw.getBlockPos())||com.vandorlabs.neoforge.transfer.NativeTransferHolds.reserved(raw.getLevel(),raw.getBlockPos())||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(raw.getLevel(),raw.getBlockPos()))return false;var t=raw;var v=view(t);long now=t.getLevel().getGameTime();if(now-v.lastAction<2)return false;v.lastAction=now;var c=t.canvas();int dx=Math.max(1,(int)Math.round(c.width*c.scale(v.zoom)/4)),dz=Math.max(1,(int)Math.round((c.mapBottom-c.mapTop)*c.scale(v.zoom)/4));switch(action){case 1->v.x=NavigationMap.bound((long)v.x-dx);case 2->v.x=NavigationMap.bound((long)v.x+dx);case 3->v.z=NavigationMap.bound((long)v.z-dz);case 4->v.z=NavigationMap.bound((long)v.z+dz);case 5->v.zoom=Math.max(0,v.zoom-1);case 6->v.zoom=Math.min(NavigationMap.SPANS.length-1,v.zoom+1);case 7->{if(!(t.tile instanceof NativeNavigationTableEntity)||t.chipStack().isEmpty()||!snapshotContains(t,v.x,v.z)&&coverage(t).stream().noneMatch(a->NavigationMap.covers(v.x,v.z,a.x(),a.z(),a.radius())))return false;var chip=t.chipStack().copy();NativeNavigationChip.write(chip,t.getLevel(),v.x,v.z);NativeNavigationChip.writeSurface(chip,captureSurface(t,v.x,v.z));if(!t.setChip(chip))return false;v.pin=true;v.pinX=v.x;v.pinZ=v.z;}case 8->{var chip=t.chipStack();if(NativeNavigationChip.inDimension(chip,t.getLevel())){var point=NativeNavigationChip.location(chip);v.x=point.getInt("X");v.z=point.getInt("Z");}else{v.x=NavigationMap.bound(Math.round(p.getX()));v.z=NavigationMap.bound(Math.round(p.getZ()));}}}return true;}
 public static void request(ServerPlayer player,NativeNavigationPayload p){
  var level=player.serverLevel();
  if(!level.getServer().isSameThread()||!level.hasChunkAt(p.pos()))return;
  var raw=host(level.getBlockEntity(p.pos()),p.slot());
  if(!selected(raw)||!usable(player,raw))return;
  var t=raw;
  long now=level.getGameTime();
  var rate=RATES.computeIfAbsent(player.getUUID(),k->new long[]{now,0});
  if(now-rate[0]>=20){rate[0]=now;rate[1]=0;}
  if(rate[1]++>=8)return;
  if(p.action()!=0)action(player,raw,p.action());
  if(t.tile instanceof NativeNavigationTableEntity table)recordSources(table);
  var v=view(t);
  var c=t.canvas();
  var linked=NativeNavigationSources.matching(level,t.getBlockPos(),t.rxChannels());
  var key=new RasterKey(level,terrain(level),v.x,v.z,v.zoom,c.width,c.height,c.mapBottom,c.progressive,
          coverage(t,linked),overrideMapSnapshot(t.tile,p.slot()),surface(t,linked,v,true));
  var raster=RASTERS.get(key);
  String id=player.getUUID()+":"+p.pos().asLong()+":"+p.slot();
  var pending=JOBS.get(id);
  boolean refined=false,background=false;
  if(raster!=null&&(!raster.errors||now-raster.tick<20)){
   rasterHits++;
   respond(player,t,p,key,c,raster);
   // The client's acknowledgement means it has received the preview already.
   boolean ready=p.known()==raster.revision&&!raster.errors&&now>=raster.retryDetailAfter
           &&p.action()==0&&now-v.lastAction>=10;
   boolean upgrade=ready&&!raster.refined&&c.canRefine(v.zoom)&&(!key.areas.isEmpty()||key.snapshot!=null);
   boolean refresh=ready&&now-raster.tick>=20&&raster.surfaceRevision!=key.surface.revision();
   if(!upgrade&&!refresh){JOBS.remove(id);return;}
   refined=raster.refined||upgrade;
   background=true;
  }
  if(pending!=null&&pending.refined==refined&&pending.key.equals(key)){pending.p=p;return;}
  if(background)for(Job job:JOBS.values())if(job.background&&job.key.equals(key)){
   JOBS.remove(id);return;
  }
  if(pending==null&&(JOBS.size()>=64||JOBS.values().stream().filter(j->j.player==player).count()>=8))return;
  JOBS.put(id,new Job(player,t,p,key,c,refined,background,background&&raster.refined==refined?raster.pixels:null));
 }
 private static void respond(ServerPlayer player,Host t,NativeNavigationPayload p,RasterKey key,NavigationCanvas c,Raster r){
  var chip=t.chipStack();boolean pin=NativeNavigationChip.inDimension(chip,t.getLevel());
  var point=NativeNavigationChip.location(chip);
  PacketDistributor.sendToPlayer(player,new NativeNavigationImage(p.pos(),p.slot(),p.serial(),key.x,key.z,key.zoom,
          c.width,c.height,c.mapTop,c.mapBottom,r.columns,r.rows,r.revision,key.areas.isEmpty()&&key.snapshot==null,
          t.rxChannels().isEmpty(),c.count==8,pin,point.getInt("X"),point.getInt("Z"),
          p.known()==r.revision?null:r.pixels,overlay(player,t,key,c),NativeNavigationSources.noPower(t.getLevel(),t.getBlockPos(),t.rxChannels())));
 }
 private static NavigationOverlay overlay(ServerPlayer viewer,Host t,RasterKey key,NavigationCanvas canvas){
  if(!(t.tile instanceof NativeNavigationTableEntity))return NavigationOverlay.EMPTY;
  var sources=NativeNavigationSources.matching(t.getLevel(),t.getBlockPos(),t.rxChannels());
  if(sources.isEmpty())return NavigationOverlay.EMPTY;
  double scale=canvas.scale(key.zoom);
  int[] circles=new int[3*Math.min(sources.size(),NavigationOverlay.MAX_CIRCLES)];
  int used=0;
  for(var area:sources){
   if(used==circles.length)break;
   if(!NavigationOverlay.visible(canvas,key.x,key.z,scale,area.x(),area.z(),area.radius()))continue;
   circles[used++]=NavigationMap.bound(area.x());
   circles[used++]=NavigationMap.bound(area.z());
   circles[used++]=NavigationOverlay.encodeRadius(area.radius(),area.sentinel());
  }
  int[] radars=new int[3*Math.min(sources.size(),NavigationOverlay.MAX_CIRCLES)];
  int sweeps=0;
  for(var area:sources){
   if(sweeps==radars.length)break;
   if(area.radarRadius()==0||!NavigationOverlay.visible(canvas,key.x,key.z,scale,area.x(),area.z(),area.radarRadius()))continue;
   radars[sweeps++]=NavigationMap.bound(area.x());
   radars[sweeps++]=NavigationMap.bound(area.z());
   radars[sweeps++]=area.radarRadius();
  }
  if(sweeps==0)return new NavigationOverlay(Arrays.copyOf(circles,used),new int[0]);
  int[] points=new int[2*NavigationOverlay.MAX_PLAYERS];
  int count=0;
  for(var player:key.level.players()){
   if(count==points.length)break;
   if(player==viewer||!player.isAlive()||player.isSpectator()||player.isInvisible()||player.isInvisibleTo(viewer)
       ||!NavigationOverlay.visible(canvas,key.x,key.z,scale,player.getX(),player.getZ(),0))continue;
   for(var source:sources){
    boolean crouched=player.isShiftKeyDown()||player.getPose()==net.minecraft.world.entity.Pose.CROUCHING;
    if(!NavigationRange.detects(key.level.getSeaLevel(),player.getX(),player.getY(),player.getZ(),crouched,source.x(),source.z(),source.radarRadius()))continue;
    points[count++]=NavigationMap.bound(Math.round(player.getX()));
    points[count++]=NavigationMap.bound(Math.round(player.getZ()));
    break;
   }
  }
  return new NavigationOverlay(Arrays.copyOf(circles,used),Arrays.copyOf(points,count),Arrays.copyOf(radars,sweeps));
 }
 private static final class Job {
  final ServerPlayer player;
  final Host t;
  NativeNavigationPayload p;
  final RasterKey key;
  final NavigationCanvas canvas;
  final NavigationCanvas.RasterGrid grid;
  final NavigationCoverage coverage;
  final int[] pixels;
  final boolean refined,background,refresh,hasSurface;
  final long surfaceRevision;
  final int terrainStep;
  int at;
  boolean errors;
  Job(ServerPlayer player,Host t,NativeNavigationPayload p,RasterKey key,NavigationCanvas canvas,boolean refined,boolean background,int[] previous){
   this.player=player;this.t=t;this.p=p;this.key=key;this.canvas=canvas;this.refined=refined;this.background=background;surfaceRevision=key.surface.revision();
   grid=canvas.raster(key.x,key.z,key.zoom,refined);
   terrainStep=Math.max(4,grid.terrainStep); // This biome source already quantizes its inputs to quart coordinates.
   hasSurface=key.surface.hasSurface();
   refresh=previous!=null;
   pixels=refresh?previous.clone():new int[grid.xs.length*grid.zs.length];
   if(refresh){coverage=null;return;}
   int[] circles=new int[key.areas.size()*3];
   int i=0;
   for(var a:key.areas){circles[i++]=a.x();circles[i++]=a.z();circles[i++]=a.radius();}
   coverage=new NavigationCoverage(circles);
  }
  boolean valid(){
   if(!usable(player,t)||!selected(t)||!key.terrain.current())return false;
   if(!background)return true;
   var v=view(t);
   return v.x==key.x&&v.z==key.z&&v.zoom==key.zoom;
  }
  void sample(){
   int width=grid.xs.length,x=grid.xs[at%width],z=grid.zs[at/width];
   boolean imported=key.snapshot!=null&&key.snapshot.contains(x,z);
   int surface=imported||!hasSurface?0:NativeNavigationSurfaceData.argb(key.surface.color(x,z));
   if(refresh){if(surface!=0)pixels[at]=surface;at++;sampledPixels++;return;}
   int color=imported?key.snapshot.colorAt(x,z):surface!=0?surface:coverage.contains(x,z)
           ?key.terrain.color(x,z,terrainStep):NavigationMap.OUTSIDE;
   pixels[at++]=color;
   errors|=!imported&&color==NavigationMap.ERROR;sampledPixels++;
  }
  void finish(){
   var v=view(t);
   var current=t.canvas();
   var linked=NativeNavigationSources.matching(key.level,t.getBlockPos(),t.rxChannels());
   if(!key.terrain.current()||v.x!=key.x||v.z!=key.z||v.zoom!=key.zoom
           ||current.width!=key.width||current.height!=key.height||current.mapBottom!=key.mapBottom
           ||!coverage(t,linked).equals(key.areas)||!Objects.equals(key.snapshot,overrideMapSnapshot(t.tile,p.slot()))
           ||!key.surface.equals(surface(t,linked,v,false)))return;
   if(background&&errors){
    RASTERS.computeIfPresent(key,(k,preview)->preview.delayRefinement(key.level.getGameTime()+100));
    return;
   }
   var previous=RASTERS.get(key);
   if(background&&previous!=null&&previous.refined==refined&&Arrays.equals(previous.pixels,pixels)){
    RASTERS.put(key,previous.refreshed(key.level.getGameTime(),surfaceRevision));return;
   }
   var r=new Raster(++revision,pixels,grid.xs.length,grid.zs.length,key.areas.size(),key.level.getGameTime(),
           errors,refined,(key.snapshot==null?0:key.snapshot.retainedBytes())+key.surface.bytes(),0,surfaceRevision);
   RASTERS.put(key,r);
   if(previous!=null)bytes-=previous.bytes();
   bytes+=r.bytes();
   var it=RASTERS.values().iterator();
   while(bytes>16*1024*1024&&it.hasNext()){bytes-=it.next().bytes();it.remove();}
   respond(player,t,p,key,canvas,r);
  }
 }
 private static final class TerrainState implements NavigationTerrainCache.Sampler {
 final ServerLevel level;final net.minecraft.world.level.biome.BiomeSource provider;final net.minecraft.world.level.levelgen.RandomState random;final net.minecraft.world.level.biome.Climate.Sampler sampler;
 TerrainState(ServerLevel level){this.level=level;provider=level.getChunkSource().getGenerator().getBiomeSource();random=level.getChunkSource().randomState();sampler=random.sampler();terrainContextsCreated++;}
 boolean current(){return provider==level.getChunkSource().getGenerator().getBiomeSource()&&random==level.getChunkSource().randomState();}
 int color(int x,int z,int step){return TERRAIN.sample(this,x,z,step,this);}
 public int sample(int qx,int qz){try{var biome=provider.getNoiseBiome(Math.floorDiv(qx,4),16,Math.floorDiv(qz,4),sampler);var name=biome.unwrapKey().map(k->k.location().getPath()).orElse("");if(name.contains("ocean")||name.contains("river"))return 0xff28659b;if(name.contains("beach")||name.contains("desert"))return 0xffc7b77c;if(name.contains("snow")||name.contains("ice")||name.contains("frozen"))return 0xffcbdfe2;if(name.contains("badlands"))return 0xffb77550;if(name.contains("nether")||name.contains("basalt")||name.contains("soul"))return 0xff8c4540;if(name.contains("end"))return 0xffb0aa7a;if(name.contains("mountain")||name.contains("peak")||name.contains("hill"))return 0xff7b8281;if(name.contains("forest")||name.contains("jungle"))return 0xff366640;return 0xff427440;}catch(RuntimeException error){return NavigationMap.ERROR;}}
 }
 public static long terrainContextsCreated;
 private static TerrainState terrain(ServerLevel level){var state=TERRAIN_WORLDS.get(level);if(state==null||!state.current()){if(state!=null){TERRAIN.invalidate(state);var it=RASTERS.entrySet().iterator();while(it.hasNext()){var entry=it.next();if(entry.getKey().level==level){bytes-=entry.getValue().bytes();it.remove();}}}state=new TerrainState(level);TERRAIN_WORLDS.put(level,state);}return state;}
 public static int color(ServerLevel level,int x,int z,int step){return terrain(level).color(x,z,step);}

 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){
  long deadline=System.nanoTime()+4000000;
  if(!JOBS.isEmpty()){
   int samples=tickJobs(false,4096,deadline);
   if(samples<4096&&System.nanoTime()<deadline)tickJobs(true,4096-samples,deadline);
  }
  NativeNavigationSurfaceData.tick(deadline);
 }
 private static int tickJobs(boolean refined,int budget,long deadline){
  int count=0;
  for(Job job:JOBS.values())if(job.background==refined)count++;
  if(count==0)return 0;
  int allowance=Math.max(256,budget/count);
  int samples=0;
  var visited=new ArrayList<String>();
  var it=JOBS.entrySet().iterator();
  while(it.hasNext()&&samples<budget&&System.nanoTime()<deadline){
   var entry=it.next();
   var job=entry.getValue();
   if(job.background!=refined)continue;
   visited.add(entry.getKey());
   if(!job.valid()){it.remove();continue;}
   int slice=allowance;
   while(job.at<job.pixels.length&&slice-->0&&samples<budget&&System.nanoTime()<deadline){
    job.sample();samples++;
   }
   if(job.at==job.pixels.length){job.finish();it.remove();}
  }
  for(String id:visited){var job=JOBS.remove(id);if(job!=null)JOBS.put(id,job);}
  return samples;
 }
 @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload e){if(!(e.getLevel() instanceof ServerLevel level))return;NativeNavigationSurfaceData.unload(level);var state=TERRAIN_WORLDS.remove(level);if(state!=null)TERRAIN.invalidate(state);VIEWS.keySet().removeIf(t->t.getLevel()==level);JOBS.values().removeIf(j->j.key.level==level);var it=RASTERS.entrySet().iterator();while(it.hasNext()){var r=it.next();if(r.getKey().level==level){bytes-=r.getValue().bytes();it.remove();}}RATES.clear();}
 @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){var id=e.getEntity().getUUID();RATES.remove(id);JOBS.values().removeIf(j->j.player.getUUID().equals(id));}
 @SubscribeEvent public static void reload(net.neoforged.neoforge.event.OnDatapackSyncEvent e){if(e.getPlayer()==null){TERRAIN.clear();TERRAIN_WORLDS.clear();RASTERS.clear();bytes=0;JOBS.clear();}}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration{@SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){NativeNavigationPayload.register(e);}}
 private NativeNavigationRuntime(){}
}
