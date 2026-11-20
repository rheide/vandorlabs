package com.vandorlabs.neoforge.navigation;

import com.vandorlabs.navigation.NavigationSurface;
import com.vandorlabs.neoforge.navigation.observer.NativeNavigationSources;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.SavedData;

final class NativeNavigationSurfaceData extends SavedData {
    private static final Map<ServerLevel,NativeNavigationSurfaceData> ACTIVE=new LinkedHashMap<>();
    private static final Factory<NativeNavigationSurfaceData> FACTORY=
            new Factory<>(NativeNavigationSurfaceData::create,NativeNavigationSurfaceData::load,null);
    private static final int[] COLORS=new int[256];
    static {
        for(int i=4;i<COLORS.length;i++){
            int abgr=MapColor.getColorFromPackedId(i);
            COLORS[i]=(abgr&0xff00ff00)|((abgr>>>16)&255)|((abgr&255)<<16);
        }
    }
    final NavigationSurface.Atlas atlas=new NavigationSurface.Atlas();
    private final NavigationSurface.Scanner scanner=new NavigationSurface.Scanner();
    private final BlockPos.MutableBlockPos probe=new BlockPos.MutableBlockPos();

    private static NativeNavigationSurfaceData create(){
        var data=new NativeNavigationSurfaceData();data.setDirty();return data;
    }
    static NativeNavigationSurfaceData get(ServerLevel world){
        return world.getDataStorage().computeIfAbsent(FACTORY,NavigationSurface.STORAGE);
    }
    void observe(ServerLevel world,NativeNavigationSources.Area area){
        var source=area.stationary();
        if(world.dimensionType().hasCeiling()||source==null||area.radarRadius()==0)return;
        scanner.request(area.x(),area.z(),area.radarRadius(),world.getGameTime(),()->{
            var pos=source.getBlockPos();
            var chunk=world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            return chunk!=null&&chunk.getBlockEntity(pos)==source&&!source.isRemoved()&&!source.inert()
                    &&source.formed()&&!source.ownsRecovery()&&source.scanning();
        });
        ACTIVE.putIfAbsent(world,this);
    }
    static int argb(int color){return COLORS[color];}
    static void tick(long navigationDeadline){
        if(ACTIVE.isEmpty())return;
        long deadline=Math.min(navigationDeadline,System.nanoTime()+500000);
        int remaining=NavigationSurface.MAX_SAMPLES_PER_TICK;
        Iterator<Map.Entry<ServerLevel,NativeNavigationSurfaceData>> it=ACTIVE.entrySet().iterator();
        Map.Entry<ServerLevel,NativeNavigationSurfaceData> first=null;
        while(it.hasNext()&&remaining>0&&System.nanoTime()<deadline){
            var entry=it.next();
            var data=entry.getValue();
            var world=entry.getKey();
            long revision=data.atlas.revision();
            remaining-=data.scanner.tick(data.atlas,world.getGameTime(),remaining,deadline,(x,z)->data.sample(world,x,z));
            if(data.atlas.revision()!=revision)data.setDirty();
            if(!data.scanner.active())it.remove();
            else if(first==null)first=entry;
        }
        if(first!=null){var world=first.getKey();ACTIVE.put(world,ACTIVE.remove(world));}
    }
    static void unload(ServerLevel world){ACTIVE.remove(world);}
    private int sample(ServerLevel world,int x,int z){
        var chunk=world.getChunkSource().getChunkNow(x>>4,z>>4);
        if(chunk==null)return 0;
        int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15);
        var minimum=world.getMinBuildHeight();
        for(int floor=Math.max(minimum,y-24);y>=floor;y--){
            probe.set(x,y,z);
            var state=chunk.getBlockState(probe);
            MapColor color;
            try{color=state.getMapColor(chunk,probe);}
            catch(RuntimeException unavailable){return 0;}
            if(color==MapColor.NONE)continue;
            int shade=1;
            if(color==MapColor.WATER||state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)){
                color=MapColor.WATER;
                int depth=0;
                while(depth<8&&y-depth>minimum&&chunk.getFluidState(probe.set(x,y-depth-1,z)).is(net.minecraft.tags.FluidTags.WATER))depth++;
                if(depth<2)shade=2;else if(depth>5)shade=0;
            }else if((z&15)!=0){
                int slope=y-chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,(z-1)&15);
                if(slope>1)shade=2;else if(slope<0)shade=0;
            }
            return color.id*4+shade;
        }
        return 0;
    }
    private static NativeNavigationSurfaceData load(CompoundTag root,HolderLookup.Provider registries){
        var data=new NativeNavigationSurfaceData();
        if(root.getInt("Version")!=1)return data;
        if(root.contains("AtlasMost",Tag.TAG_LONG)&&root.contains("AtlasLeast",Tag.TAG_LONG))
            data.atlas.setIdentity(new java.util.UUID(root.getLong("AtlasMost"),root.getLong("AtlasLeast")));
        data.atlas.restore(root.getByteArray("Tiles"));
        return data;
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider registries){
        root.putInt("Version",1);root.putLong("AtlasMost",atlas.identity().getMostSignificantBits());root.putLong("AtlasLeast",atlas.identity().getLeastSignificantBits());root.putByteArray("Tiles",atlas.serialize());return root;
    }
}
