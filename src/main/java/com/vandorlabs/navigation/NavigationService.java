package com.vandorlabs.navigation;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.network.*;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.biome.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.*;

/** Server-thread seed sampling. Neither observation nor source discovery requests chunks. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class NavigationService {
    private NavigationService(){}
    private static final Map<TileEntity,View> VIEWS=new WeakHashMap<>();
    private static final Map<com.vandorlabs.vehicle.EntityGroundVehicle,Map<BlockPos,View>> CARRIED_VIEWS=new WeakHashMap<>();
    private static final Map<World,TerrainWorld> TERRAIN_WORLDS=new WeakHashMap<>();
    private static final NavigationTerrainCache TERRAIN=new NavigationTerrainCache(32*1024*1024);
    private static int generatorEpoch;
    private static final class TerrainWorld implements NavigationTerrainCache.Sampler {
        final World world;final Object provider;final long seed;final String settings;final int epoch=++generatorEpoch;
        TerrainWorld(World world){this.world=world;provider=world.getBiomeProvider();seed=world.getSeed();settings=world.getWorldInfo().getGeneratorOptions();}
        boolean current(){return provider==world.getBiomeProvider()&&seed==world.getSeed()&&settings.equals(world.getWorldInfo().getGeneratorOptions());}
        public int sample(int x,int z){return biomeColor(world,x,z);}
        int sampleColor(int x,int z,int step){return TERRAIN.sample(this,x,z,step,this);}
    }
    private static TerrainWorld terrainWorld(World world){TerrainWorld state=TERRAIN_WORLDS.get(world);if(state==null||!state.current()){if(state!=null){TERRAIN.invalidate(state);discardRasters(world);}state=new TerrainWorld(world);TERRAIN_WORLDS.put(world,state);}return state;}
    private static void discardRasters(World world){Iterator<Map.Entry<RasterKey,Cached>> it=RASTERS.entrySet().iterator();while(it.hasNext()){Map.Entry<RasterKey,Cached> e=it.next();if(e.getKey().world==world){retainedBytes-=e.getValue().bytes();it.remove();}}}

    private static final Map<UUID,long[]> LAST=new HashMap<>();
    private static final LinkedHashMap<String,Job> JOBS=new LinkedHashMap<>();
    private static final LinkedHashMap<RasterKey,Cached> RASTERS=new LinkedHashMap<RasterKey,Cached>(32,.75f,true);
    private static int retainedBytes,nextRevision;
    public static long sampledPixels,rasterHits;
    public static long terrainSamples(){return TERRAIN.samples;}
    public static long terrainHits(){return TERRAIN.hits;}
    private static final class RasterKey {
        final SavedMapPixels snapshot;final NavigationSurface.View surface;final World world;final boolean writable;final int epoch,x,z,zoom,width,height,mapHeight,hash;final List<NavigationSources.Area> areas;
        RasterKey(World world,View v,NavigationCanvas c,List<NavigationSources.Area> source,SavedMapPixels snapshot,NavigationSurface.View surface){this.snapshot=snapshot;this.surface=surface;this.world=world;writable=c.count==8;epoch=terrainWorld(world).epoch;x=v.x;z=v.z;zoom=v.zoom;width=c.width;height=c.height;mapHeight=c.mapBottom-c.mapTop;
            List<NavigationSources.Area> sorted=new ArrayList<>(source);sorted.sort(java.util.Comparator.comparingInt((NavigationSources.Area p)->p.x).thenComparingInt(p->p.z).thenComparingInt(p->p.radius));areas=new ArrayList<>();for(NavigationSources.Area p:sorted){if(areas.isEmpty()){areas.add(new NavigationSources.Area(p.x,p.z,p.radius));continue;}NavigationSources.Area last=areas.get(areas.size()-1);if(last.x!=p.x||last.z!=p.z||last.radius!=p.radius)areas.add(new NavigationSources.Area(p.x,p.z,p.radius));}
            int h=System.identityHashCode(world);for(int n:new int[]{epoch,x,z,zoom,width,height,mapHeight,writable?1:0})h=31*h+n;for(NavigationSources.Area p:areas){h=31*h+p.x;h=31*h+p.z;h=31*h+p.radius;}hash=31*(31*h+(snapshot==null?0:snapshot.hashCode()))+surface.hashCode();
        }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){if(!(other instanceof RasterKey))return false;RasterKey k=(RasterKey)other;if(world!=k.world||epoch!=k.epoch||x!=k.x||z!=k.z||zoom!=k.zoom||width!=k.width||height!=k.height||mapHeight!=k.mapHeight||writable!=k.writable||areas.size()!=k.areas.size()||!java.util.Objects.equals(snapshot,k.snapshot)||!surface.equals(k.surface))return false;for(int i=0;i<areas.size();i++){NavigationSources.Area p=areas.get(i),q=k.areas.get(i);if(p.x!=q.x||p.z!=q.z||p.radius!=q.radius)return false;}return true;}
    }
    private static final class Cached {
        final int x,z,zoom,id,columns,rows;final NavigationCanvas canvas;final List<NavigationSources.Area> areas;final int[] pixels;final boolean errors,refined;long tick,surfaceRevision;final RasterKey key;long retryDetailAfter;
        Cached(Job j){key=j.key;x=j.x;z=j.z;zoom=j.zoom;canvas=j.canvas;areas=key.areas;pixels=j.pixels;columns=j.grid.xs.length;rows=j.grid.zs.length;id=++nextRevision;errors=j.errors;refined=j.refined;surfaceRevision=j.surfaceRevision;tick=j.tile.getWorld().getTotalWorldTime();}
        int bytes(){return pixels.length*4+areas.size()*48+512+key.surface.bytes()+(key.snapshot==null?0:key.snapshot.retainedBytes());}
    }
    private static void retain(Cached raster){Cached before=RASTERS.put(raster.key,raster);if(before!=null)retainedBytes-=before.bytes();retainedBytes+=raster.bytes();Iterator<Cached> it=RASTERS.values().iterator();while(retainedBytes>16*1024*1024&&it.hasNext()){retainedBytes-=it.next().bytes();it.remove();}}
    private static MessageNavigationImage response(TileEntity tile,BlockPos pos,int slot,int serial,Cached c,int known){return new MessageNavigationImage(pos,slot,serial,c.x,c.z,c.zoom,Math.max(8,Math.min(64,(int)Math.round(c.canvas.width*16.0/c.canvas.height))),c.areas.isEmpty()&&c.key.snapshot==null,chip(tile),tile instanceof TileEntityNavigationTable,tile.getWorld().provider.getDimension(),known==c.id?null:c.pixels).dimensions(c.canvas,c.columns,c.rows,c.id).linkState(channels(tile).isEmpty(),NavigationSources.noPower(tile.getWorld(),pos,channels(tile)));}

    private static void send(EntityPlayerMP player,TileEntity tile,MessageNavigationImage image){
        if(tile instanceof TileEntityNavigationTable)image.overlay=overlay(player,tile,image);
        if(NavigationVehicle.carried(tile)){com.vandorlabs.vehicle.EntityGroundVehicle craft=NavigationVehicle.craft(tile.getWorld());boolean source=NavigationVehicle.hasObserver(craft);PacketHandler.INSTANCE.sendTo(new MessageVehicleNavigationImage(craft,image,source&&!image.noPower&&(!(tile instanceof TileEntityNavigationTable)||view(tile).follow),!source&&tile instanceof TileEntityAnimatedScreenSelector),player);}else PacketHandler.INSTANCE.sendTo(image,player);}
    private static NavigationOverlay overlay(EntityPlayerMP viewer,TileEntity table,MessageNavigationImage image){
        List<NavigationSources.Area> sources=NavigationSources.matching(table.getWorld(),table.getPos(),channels(table));
        if(sources.isEmpty())return NavigationOverlay.EMPTY;
        NavigationCanvas canvas=NavigationViewport.canvas(table,0);
        double scale=canvas.scale(image.zoom);
        int[] circles=new int[3*Math.min(sources.size(),NavigationOverlay.MAX_CIRCLES)];
        int used=0;
        for(NavigationSources.Area area:sources){
            if(used==circles.length)break;
            if(!NavigationOverlay.visible(canvas,image.x,image.z,scale,area.x,area.z,area.radius))continue;
            circles[used++]=NavigationMap.bound(area.x);
            circles[used++]=NavigationMap.bound(area.z);
            circles[used++]=NavigationOverlay.encodeRadius(area.radius,area.sentinel);
        }
        int[] radars=new int[3*Math.min(sources.size(),NavigationOverlay.MAX_CIRCLES)];
        int sweeps=0;
        for(NavigationSources.Area area:sources){
            if(sweeps==radars.length)break;
            if(area.radarRadius==0||!NavigationOverlay.visible(canvas,image.x,image.z,scale,area.x,area.z,area.radarRadius))continue;
            radars[sweeps++]=NavigationMap.bound(area.x);
            radars[sweeps++]=NavigationMap.bound(area.z);
            radars[sweeps++]=area.radarRadius;
        }
        if(sweeps==0)return new NavigationOverlay(Arrays.copyOf(circles,used),new int[0]);
        int[] points=new int[2*NavigationOverlay.MAX_PLAYERS];
        int count=0;
        for(EntityPlayer player:viewer.world.playerEntities){
            if(count==points.length)break;
            if(player==viewer||player.isDead||player.isSpectator()||player.isInvisible()
                    ||!NavigationOverlay.visible(canvas,image.x,image.z,scale,player.posX,player.posZ,0))continue;
            for(NavigationSources.Area source:sources){
                if(!NavigationRange.detects(viewer.world.getSeaLevel(),player.posX,player.posY,player.posZ,player.isSneaking(),source.x,source.z,source.radarRadius))continue;
                points[count++]=NavigationMap.bound(Math.round(player.posX));
                points[count++]=NavigationMap.bound(Math.round(player.posZ));
                break;
            }
        }
        return new NavigationOverlay(Arrays.copyOf(circles,used),Arrays.copyOf(points,count),Arrays.copyOf(radars,sweeps));
    }
    public static final class View {
        public int x,z,zoom=4;ItemStack chip=ItemStack.EMPTY;boolean initialized;public boolean follow=true;long lastAction=-100;
        public double aspect=1;
        net.minecraft.nbt.NBTTagCompound surfaceTag;NavigationSurface.Snapshot surfaceChip;
    }
    public static TileEntity owner(TileEntity tile){
        if(tile instanceof TileEntityNavigationTable)return ((TileEntityNavigationTable)tile).ownsReader()?tile:((TileEntityNavigationTable)tile).reader();
        if(tile instanceof TileEntityAnimatedScreenSelector&&tile.getBlockType().getClass()==BlockAnimatedScreenSelector.class){ScreenChipGroups.Group group=ScreenChipGroups.group((TileEntityAnimatedScreenSelector)tile);if(group!=null)return group.owner;}
        return tile;
    }
    public static void moveView(TileEntity from,TileEntity to){
        if(from.getWorld()==null||from.getWorld().isRemote)return;View view=VIEWS.remove(from);if(view!=null){view.chip=to instanceof TileEntityTokenSlot?((TileEntityTokenSlot)to).getStackInSlot(0):chip(to);VIEWS.put(to,view);}
    }
    public static boolean selected(TileEntity tile,int slot){
        if(tile instanceof TileEntityNavigationTable)return ((TileEntityNavigationTable)tile).formed()&&((TileEntityNavigationTable)tile).screenSettings().texture==NavigationMap.SURFACE;
        return tile instanceof TileEntityAnimatedScreenSelector&&slot>=0&&slot<=1&&RedstoneScreenInteractions.supportsSlot(tile.getBlockType(),slot)&&((TileEntityAnimatedScreenSelector)tile).hasNavigation(slot)&&ScreenChipSupport.navigationAvailable((TileEntityAnimatedScreenSelector)tile,slot);
    }
    public static TileEntityTokenSlot reader(TileEntity tile){return tile instanceof TileEntityNavigationTable?(TileEntityTokenSlot)owner(tile):tile instanceof TileEntityAnimatedScreenSelector?ScreenChipSupport.reader((TileEntityAnimatedScreenSelector)tile):null;}
    public static ItemStack chip(TileEntity tile){TileEntityTokenSlot reader=reader(tile);return reader==null?ItemStack.EMPTY:reader.getStackInSlot(0);}
    private static ChannelList channels(TileEntity tile){return tile instanceof RedstoneChannelMember?((RedstoneChannelMember)tile).getRedstoneChannels():ChannelList.EMPTY;}
    public static void moduleChanged(TileEntityNavigationTable tile){if(tile!=null){View view=VIEWS.get(owner(tile));if(view!=null)view.initialized=false;}}
    public static boolean recordSources(TileEntityNavigationTable tile){
        if(tile==null||NavigationVehicle.physicalWorld(tile.getWorld()).isRemote)return false;
        TileEntityNavigationTable storage=tile.moduleStorage();
        if(storage==null||!NavigationModuleData.item(storage.storedModule()))return false;
        World world=tile.getWorld();
        List<NavigationSources.Area> linked=NavigationSources.matching(world,tile.getPos(),channels(tile));
        int dimension=world.provider.getDimension();
        boolean changed=NavigationModuleData.record(storage.storedModule(),dimension,linked);
        if(world instanceof WorldServer&&NavigationModuleData.bound(storage.storedModule())
                &&NavigationModuleData.dimension(storage.storedModule())==dimension){
            int[] live=liveSurface(world,linked,true);
            if(live.length>0)changed|=NavigationModuleData.recordSurface(storage.storedModule(),dimension,live,
                    NavigationSurfaceData.get((WorldServer)world).atlas);
        }
        if(changed)storage.moduleDataChanged();
        return changed;
    }
    private static int[] liveSurface(World world,List<NavigationSources.Area> sources,boolean register){
        int[] circles=new int[3*Math.min(sources.size(),NavigationOverlay.MAX_CIRCLES)];
        int count=0;
        if(!(world instanceof WorldServer)||world.provider.isNether())return Arrays.copyOf(circles,0);
        NavigationSurfaceData data=null;
        for(NavigationSources.Area area:sources){
            if(area.stationary==null||area.radarRadius==0)continue;
            if(count==circles.length)break;
            if(register){
                if(data==null)data=NavigationSurfaceData.get((WorldServer)world);
                data.observe((WorldServer)world,area);
            }
            circles[count++]=NavigationMap.bound(area.x);circles[count++]=NavigationMap.bound(area.z);circles[count++]=area.radarRadius;
        }
        return Arrays.copyOf(circles,count);
    }
    private static NavigationSurface.View surface(TileEntity tile,List<NavigationSources.Area> sources,View v,boolean register){
        if(!(tile.getWorld() instanceof WorldServer))return NavigationSurface.View.EMPTY;
        WorldServer world=(WorldServer)tile.getWorld();
        int dimension=world.provider.getDimension();
        ItemStack stack=chip(tile);
        if(v.surfaceTag!=stack.getTagCompound()){
            v.surfaceTag=stack.getTagCompound();v.surfaceChip=NavigationChip.surface(stack,dimension);
        }
        int[] areas=new int[0];
        NavigationSurface.Atlas atlas=null;
        if(tile instanceof TileEntityNavigationTable){
            int[] live=liveSurface(world,sources,register);
            ItemStack module=((TileEntityNavigationTable)tile).moduleStack();
            if(live.length>0||NavigationModuleData.hasSurface(module)){
                atlas=NavigationSurfaceData.get(world).atlas;
                areas=NavigationSurface.mergeAreas(NavigationModuleData.surface(module,dimension,atlas),live);
            }
        }
        return areas.length==0&&v.surfaceChip==null?NavigationSurface.View.EMPTY:
                new NavigationSurface.View(atlas,areas,v.surfaceChip);
    }
    private static NavigationSurface.Snapshot captureSurface(TileEntityNavigationTable table,int x,int z){
        if(!(table.getWorld() instanceof WorldServer))return null;
        return surface(table,NavigationSources.matching(table.getWorld(),table.getPos(),channels(table)),view(table),false).capture(x,z);
    }
    public static SavedMapPixels overrideMapSnapshot(TileEntity tile){SavedMapPixels map=NavigationChip.savedMap(chip(tile));return map!=null&&map.dimension().equals(Integer.toString(tile.getWorld().provider.getDimension()))?map:null;}
    public static List<NavigationSources.Area> coverage(TileEntity tile){
        List<NavigationSources.Area> result=NavigationSources.matching(tile.getWorld(),tile.getPos(),channels(tile));ItemStack chip=chip(tile);
        if(NavigationChip.inDimension(chip,tile.getWorld().provider.getDimension()))result.add(new NavigationSources.Area(NavigationChip.value(chip,"X"),NavigationChip.value(chip,"Z"),1000));
        if(tile instanceof TileEntityNavigationTable)result.addAll(NavigationModuleData.areas(((TileEntityNavigationTable)tile).moduleStack(),tile.getWorld().provider.getDimension()));
        return result;
    }
    public static View view(TileEntity tile){
        tile=owner(tile);com.vandorlabs.vehicle.EntityGroundVehicle craft=NavigationVehicle.carried(tile)?NavigationVehicle.craft(tile.getWorld()):null;View view=craft==null?VIEWS.computeIfAbsent(tile,t->new View()):CARRIED_VIEWS.computeIfAbsent(craft,c->new HashMap<>()).computeIfAbsent(tile.getPos(),p->new View());ItemStack chip=chip(tile);boolean changed=craft==null?view.chip!=chip:!ItemStack.areItemStacksEqual(view.chip,chip);view.chip=chip;
        if(!view.initialized||changed&&NavigationChip.inDimension(chip,tile.getWorld().provider.getDimension())){
            List<NavigationSources.Area> areas=coverage(tile);
            if(NavigationChip.inDimension(chip,tile.getWorld().provider.getDimension())){view.x=NavigationChip.value(chip,"X");view.z=NavigationChip.value(chip,"Z");view.zoom=4;}
            else if(!areas.isEmpty()){NavigationSources.Area area=areas.get(0);view.x=area.x;view.z=area.z;view.zoom=NavigationMap.initialZoomForRadius(area.radius);}
            else {view.x=tile.getPos().getX();view.z=tile.getPos().getZ();}
            view.initialized=!areas.isEmpty();
        }if(craft!=null&&NavigationVehicle.hasObserver(craft)&&(!(tile instanceof TileEntityNavigationTable)||view.follow)){Vec3d center=NavigationVehicle.center(craft);view.x=NavigationMap.bound(Math.round(center.x));view.z=NavigationMap.bound(Math.round(center.z));}return view;
    }
    public static boolean usable(EntityPlayer player,TileEntity tile){
        if(NavigationVehicle.carried(tile))return NavigationVehicle.usable(player,tile);
        if(tile==null||tile.isInvalid()||tile.getWorld()!=player.world||!player.world.isBlockLoaded(tile.getPos(),false)||player.world.getTileEntity(tile.getPos())!=tile||player.isDead||player.isSpectator())return false;
        if(tile instanceof TileEntityNavigationTable)return ((TileEntityNavigationTable)tile).nearDisplayMember(player);
        if(tile instanceof TileEntityAnimatedScreenSelector&&tile.getBlockType().getClass()==BlockAnimatedScreenSelector.class){ScreenChipGroups.Group group=ScreenChipGroups.group((TileEntityAnimatedScreenSelector)tile);if(group!=null){for(TileEntityAnimatedScreenSelector member:group.members)if(player.getDistanceSq(member.getPos())<=64)return true;return false;}}
        return player.getDistanceSq(tile.getPos())<=64;
    }
    public static double[] screenUV(TileEntityAnimatedScreenSelector tile,double u,double v){
        if(tile.getBlockType().getClass()!=BlockAnimatedScreenSelector.class)return new double[]{u,v};ScreenChipGroups.Group g=ScreenChipGroups.group(tile);if(g==null)return new double[]{u,v};
        long cell=DisplayJoinGeometry.basis(tile,tile.getWorld().getBlockState(tile.getPos())).cell(tile.getPos());int col=(int)(cell>>32)-g.minColumn,row=(int)cell-g.minRow;net.minecraft.util.EnumFacing face=tile.getWorld().getBlockState(tile.getPos()).getValue(BlockAnimatedScreenSelector.FACING);
        if(face==net.minecraft.util.EnumFacing.UP)col=g.columns-1-col;if(face==net.minecraft.util.EnumFacing.DOWN)row=g.rows-1-row;
        return new double[]{(g.columns-1-col+u)/g.columns,(g.rows-1-row+v)/g.rows};
    }
    public static void action(EntityPlayer player,TileEntity raw,int slot,int action){
        if(action<1||action>8||!selected(raw,slot))return;TileEntity tile=owner(raw);if(!usable(player,tile)||(NavigationVehicle.carried(tile)?!NavigationVehicle.edit(player,tile):!com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,raw.getWorld(),raw.getPos()))||com.vandorlabs.gear.ArmTransactions.locked(tile.getWorld(),tile.getPos()))return;
        View v=view(tile);if(NavigationVehicle.carried(tile)&&NavigationVehicle.hasObserver(NavigationVehicle.craft(tile.getWorld()))){if(action>=1&&action<=4){if(!(tile instanceof TileEntityNavigationTable))return;v.follow=false;}if(action==8){v.follow=true;Vec3d center=NavigationVehicle.center(NavigationVehicle.craft(tile.getWorld()));v.x=NavigationMap.bound(Math.round(center.x));v.z=NavigationMap.bound(Math.round(center.z));return;}}long now=tile.getWorld().getTotalWorldTime();if(now-v.lastAction<2)return;v.lastAction=now;
        NavigationCanvas canvas=NavigationViewport.canvas(tile,slot);int step=Math.max(1,(int)Math.round(canvas.width*canvas.scale(v.zoom)/4)),vertical=Math.max(1,(int)Math.round((canvas.mapBottom-canvas.mapTop)*canvas.scale(v.zoom)/4));
        switch(action){case 1:v.x=NavigationMap.bound((long)v.x-step);break;case 2:v.x=NavigationMap.bound((long)v.x+step);break;case 3:v.z=NavigationMap.bound((long)v.z-vertical);break;case 4:v.z=NavigationMap.bound((long)v.z+vertical);break;case 5:v.zoom=Math.max(0,v.zoom-1);break;case 6:v.zoom=Math.min(NavigationMap.SPANS.length-1,v.zoom+1);break;case 7:
            SavedMapPixels imported=overrideMapSnapshot(tile);if(!(tile instanceof TileEntityNavigationTable)||!NavigationSources.contains(coverage(tile),v.x,v.z)&&(imported==null||!imported.contains(v.x,v.z)))return;
            TileEntityTokenSlot reader=reader(tile);ItemStack stack=reader==null?ItemStack.EMPTY:reader.getStackInSlot(0);if(stack.isEmpty())return;
            ItemStack written=stack.copy();NavigationChip.write(written,tile.getWorld().provider.getDimension(),v.x,v.z);NavigationChip.writeSurface(written,captureSurface((TileEntityNavigationTable)tile,v.x,v.z));reader.setInventorySlotContents(0,written);v.chip=reader.getStackInSlot(0);break;
            case 8:ItemStack pin=chip(tile);if(NavigationChip.inDimension(pin,tile.getWorld().provider.getDimension())){v.x=NavigationChip.value(pin,"X");v.z=NavigationChip.value(pin,"Z");}else{v.x=NavigationMap.bound(Math.round(player.posX));v.z=NavigationMap.bound(Math.round(player.posZ));}break;
        }
    }
    public static void request(EntityPlayerMP player,BlockPos pos,int slot,int serial,int aspect){request(player,pos,slot,serial,aspect,0);}
    public static void request(EntityPlayerMP player,BlockPos pos,int slot,int serial,int aspect,int known){
        requestIn(player,player.world,pos,slot,serial,aspect,known);
    }
    public static void requestCarried(EntityPlayerMP player,com.vandorlabs.vehicle.EntityGroundVehicle craft,BlockPos pos,int slot,int serial,int aspect,int known){if(!NavigationVehicle.live(craft)||craft.world!=player.world||craft.view==null||craft.structure.at(pos)==null)return;requestIn(player,craft.view,pos,slot,serial,aspect,known);}
    private static void requestIn(EntityPlayerMP player,World world,BlockPos pos,int slot,int serial,int aspect,int known){
        if(slot<0||slot>1||aspect<8||aspect>64||!world.isBlockLoaded(pos,false))return;
        TileEntity raw=world.getTileEntity(pos);if(!selected(raw,slot))return;TileEntity tile=owner(raw);if(!usable(player,tile))return;
        UUID id=player.getUniqueID();long now=world.getTotalWorldTime();long[] rate=LAST.computeIfAbsent(id,k->new long[]{now,0});if(now-rate[0]>=20){rate[0]=now;rate[1]=0;}if(rate[1]++>=(world instanceof com.vandorlabs.vehicle.VehicleWorld?32:8))return;
        String key=id+":"+(world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).craft.getUniqueID().toString():"world")+":"+pos.toLong();int pending=0;for(Job j:JOBS.values())if(j.player==player)pending++;if(pending>=8&&!JOBS.containsKey(key))return;
        if(tile instanceof TileEntityNavigationTable)recordSources((TileEntityNavigationTable)tile);
        View v=view(tile);v.aspect=aspect/16.0;NavigationCanvas canvas=NavigationViewport.canvas(tile,slot);List<NavigationSources.Area> areas=coverage(tile);RasterKey rasterKey=new RasterKey(NavigationVehicle.physicalWorld(world),v,canvas,areas,overrideMapSnapshot(tile),surface(tile,areas,v,true));Cached cached=RASTERS.get(rasterKey);
        Job existing=JOBS.get(key);
        boolean refined=false,background=false;
        if(cached!=null&&(!cached.errors||now-cached.tick<20)){
            rasterHits++;
            send(player,tile,response(tile,pos,slot,serial,cached,known));
            // The client's acknowledgement means it has received the preview already.
            boolean ready=known==cached.id&&!cached.errors&&now>=cached.retryDetailAfter&&now-v.lastAction>=10;
            boolean upgrade=ready&&!cached.refined&&canvas.canRefine(v.zoom)&&(!areas.isEmpty()||rasterKey.snapshot!=null);
            boolean refresh=ready&&now-cached.tick>=20&&cached.surfaceRevision!=rasterKey.surface.revision();
            if(!upgrade&&!refresh){JOBS.remove(key);return;}
            refined=cached.refined||upgrade;
            background=true;
        }
        if(existing!=null&&existing.refined==refined&&existing.key.equals(rasterKey)){
            existing.serial=serial;existing.known=known;return;
        }
        if(background)for(Job job:JOBS.values())if(job.background&&job.key.equals(rasterKey)){
            JOBS.remove(key);return; // Another viewer is already refining this exact raster.
        }
        if(JOBS.size()>=64&&existing==null)return;
        JOBS.put(key,new Job(player,tile,pos,slot,serial,canvas,known,rasterKey,refined,background,background&&cached.refined==refined?cached.pixels:null));
    }
    private static final class Job {
        final RasterKey key;
        final EntityPlayerMP player;
        final TileEntity tile;
        final BlockPos pos;
        final int slot,x,z,zoom;
        final boolean refined,background,refresh,hasSurface;
        final long surfaceRevision;
        final NavigationCanvas canvas;
        final NavigationCanvas.RasterGrid grid;
        final NavigationCoverage coverage;
        final TerrainWorld terrain;
        final int[] pixels;
        int serial,known,index;
        boolean errors;

        Job(EntityPlayerMP player,TileEntity tile,BlockPos pos,int slot,int serial,NavigationCanvas canvas,int known,RasterKey key,boolean refined,boolean background,int[] previous){
            this.key=key;this.player=player;this.tile=tile;this.pos=pos;this.slot=slot;this.serial=serial;
            x=key.x;z=key.z;zoom=key.zoom;this.canvas=canvas;this.known=known;this.refined=refined;this.background=background;surfaceRevision=key.surface.revision();
            grid=canvas.raster(x,z,zoom,refined);
            terrain=terrainWorld(key.world);
            hasSurface=key.surface.hasSurface();
            refresh=previous!=null;
            pixels=refresh?previous.clone():new int[grid.xs.length*grid.zs.length];
            if(refresh){coverage=null;return;}
            int[] circles=new int[key.areas.size()*3];
            int n=0;
            for(NavigationSources.Area area:key.areas){circles[n++]=area.x;circles[n++]=area.z;circles[n++]=area.radius;}
            coverage=new NavigationCoverage(circles);
        }
        TileEntity current(){return NavigationVehicle.carried(tile)?owner(tile.getWorld().getTileEntity(tile.getPos())):tile;}
        boolean valid(){
            TileEntity target=current();
            if(!player.connection.netManager.isChannelOpen()||target==null||!usable(player,target)
                    ||!selected(target,slot)||!terrain.current())return false;
            if(!background)return true;
            View v=view(target);
            return v.x==x&&v.z==z&&v.zoom==zoom;
        }
        void sample(){
            int width=grid.xs.length,wx=grid.xs[index%width],wz=grid.zs[index/width];
            boolean imported=key.snapshot!=null&&key.snapshot.contains(wx,wz);
            int surface=imported||!hasSurface?0:NavigationSurfaceData.argb(key.surface.color(wx,wz));
            if(refresh){if(surface!=0)pixels[index]=surface;index++;sampledPixels++;return;}
            int color=imported?key.snapshot.colorAt(wx,wz):surface!=0?surface:coverage.contains(wx,wz)
                    ?terrain.sampleColor(wx,wz,grid.terrainStep):NavigationMap.OUTSIDE;
            pixels[index++]=color;
            errors|=!imported&&color==NavigationMap.ERROR;sampledPixels++;
        }
        void finish(){
            TileEntity target=current();
            if(target==null||!usable(player,target)||!selected(target,slot)||!terrain.current())return;
            boolean carried=NavigationVehicle.carried(target);
            View v=view(target);
            List<NavigationSources.Area> current=coverage(target);
            if(v.zoom!=zoom)return;
            if((!carried||background)&&!key.equals(new RasterKey(NavigationVehicle.physicalWorld(target.getWorld()),
                    v,NavigationViewport.canvas(target,slot),current,overrideMapSnapshot(target),surface(target,current,v,false))))return;
            if(background&&errors){
                Cached preview=RASTERS.get(key);
                if(preview!=null)preview.retryDetailAfter=target.getWorld().getTotalWorldTime()+100;
                return;
            }
            Cached previous=RASTERS.get(key);
            if(background&&previous!=null&&previous.refined==refined&&Arrays.equals(previous.pixels,pixels)){
                previous.surfaceRevision=surfaceRevision;previous.tick=target.getWorld().getTotalWorldTime();
                return;
            }
            Cached cached=new Cached(this);
            retain(cached);
            send(player,target,response(target,pos,slot,serial,cached,known));
        }
    }
    public static int color(World world,int x,int z){return color(world,x,z,false);}
    public static int color(World world,int x,int z,boolean detailed){return terrainWorld(world).sampleColor(x,z,detailed?1:4);}
    /** Widely separated cruise samples use the generator directly to avoid retaining sparse tiles. */
    public static int cruiseColor(World world,int x,int z,int spacing){
        return spacing<=64?terrainWorld(world).sampleColor(x,z,spacing):biomeColor(world,x,z);
    }
    private static int biomeColor(World world,int qx,int qz){
        try{return biomeColor(world.getBiomeProvider().getBiomes(null,qx,qz,1,1,false)[0]);}
        catch(RuntimeException failed){return NavigationMap.ERROR;}
    }
    /** Sample one vanilla map footprint directly from the generator, without loading chunks. */
    public static int[] printTerrain(World world,int minX,int minZ){
        int width=SavedMapPixels.WIDTH;
        int[] colors=new int[width*width];
        try{
            Biome[] biomes=world.getBiomeProvider().getBiomes(null,minX,minZ,width,width,false);
            Map<Biome,Integer> palette=new IdentityHashMap<>();
            for(int i=0;i<colors.length;i++){
                Biome biome=biomes[i];
                Integer color=palette.get(biome);
                if(color==null){color=biomeColor(biome);palette.put(biome,color);}
                colors[i]=color;
            }
        }catch(RuntimeException unsupported){
            // Some modded providers support single samples only.
            for(int i=0;i<colors.length;i++)colors[i]=color(world,minX+i%width,minZ+i/width,true);
        }
        return colors;
    }
    private static int biomeColor(Biome biome){
        int color;
            String name=biome.getRegistryName()==null?"":biome.getRegistryName().getResourcePath();
            if(biome instanceof BiomeOcean||biome instanceof BiomeRiver||name.contains("ocean")||name.contains("river"))color=0xff28659b;
            else if(name.contains("beach")||name.contains("desert"))color=0xffc7b77c;
            else if(name.contains("snow")||name.contains("ice")||biome.getDefaultTemperature()<.15)color=0xffcbdfe2;
            else if(name.contains("mesa"))color=0xffb77550;
            else if(name.contains("hell")||name.contains("nether"))color=0xff8c4540;
            else if(name.contains("sky")||name.contains("end"))color=0xffb0aa7a;
            else {double relief=Math.max(0,Math.min(1,biome.getBaseHeight()/2));int r=(int)(66+relief*57),g=(int)(116+relief*14),b=(int)(64+relief*65);if(biome.getRainfall()>.7){r-=12;g-=14;}color=0xff000000|(r<<16)|(g<<8)|b;}
        return color;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        long deadline=System.nanoTime()+4000000;
        if(!JOBS.isEmpty()){
            int samples=tickJobs(false,4096,deadline);
            if(samples<4096&&System.nanoTime()<deadline)tickJobs(true,4096-samples,deadline);
        }
        NavigationSurfaceData.tick(deadline);
    }
    private static int tickJobs(boolean refined,int budget,long deadline){
        int count=0;
        for(Job job:JOBS.values())if(job.background==refined)count++;
        if(count==0)return 0;
        int allowance=Math.max(256,budget/count);
        int samples=0;
        List<String> visited=new ArrayList<>();
        Iterator<Map.Entry<String,Job>> it=JOBS.entrySet().iterator();
        while(it.hasNext()&&samples<budget&&System.nanoTime()<deadline){
            Map.Entry<String,Job> entry=it.next();
            Job job=entry.getValue();
            if(job.background!=refined)continue;
            visited.add(entry.getKey());
            if(!job.valid()){it.remove();continue;}
            int slice=allowance;
            while(job.index<job.pixels.length&&slice-->0&&samples<budget&&System.nanoTime()<deadline){
                job.sample();samples++;
            }
            if(job.index==job.pixels.length){job.finish();it.remove();}
        }
        for(String key:visited){Job pending=JOBS.remove(key);if(pending!=null)JOBS.put(key,pending);}
        return samples;
    }
    @SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){World world=event.getWorld();if(world.isRemote)return;NavigationSurfaceData.unload(world);TerrainWorld terrain=TERRAIN_WORLDS.remove(world);if(terrain!=null)TERRAIN.invalidate(terrain);NavigationSources.unload(world);discardRasters(world);VIEWS.keySet().removeIf(t->t.getWorld()==world);JOBS.values().removeIf(j->j.tile.getWorld()==world);LAST.clear();CARRIED_VIEWS.clear();}
    @SubscribeEvent public static void logout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event){UUID id=event.player.getUniqueID();LAST.remove(id);JOBS.values().removeIf(j->j.player.getUniqueID().equals(id));}
}
