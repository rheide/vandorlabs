package com.vandorlabs.navigation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.WorldSavedData;

public final class NavigationSurfaceData extends WorldSavedData {
    private static final Map<WorldServer,NavigationSurfaceData> ACTIVE=new LinkedHashMap<>();
    private static final int[] COLORS=com.vandorlabs.appliance.ChipApplianceService.palette();
    final NavigationSurface.Atlas atlas=new NavigationSurface.Atlas();
    private final NavigationSurface.Scanner scanner=new NavigationSurface.Scanner();
    private final BlockPos.MutableBlockPos probe=new BlockPos.MutableBlockPos();

    public NavigationSurfaceData(String name){super(name);}
    static NavigationSurfaceData get(WorldServer world){
        NavigationSurfaceData data=(NavigationSurfaceData)world.getPerWorldStorage()
                .getOrLoadData(NavigationSurfaceData.class,NavigationSurface.STORAGE);
        if(data==null){data=new NavigationSurfaceData(NavigationSurface.STORAGE);world.getPerWorldStorage().setData(NavigationSurface.STORAGE,data);data.markDirty();}
        return data;
    }
    void observe(WorldServer world,NavigationSources.Area area){
        if(world.provider.isNether()||area.stationary==null||area.radarRadius==0)return;
        scanner.request(area.x,area.z,area.radarRadius,world.getTotalWorldTime(),()->{
            if(area.stationary.isInvalid()||!world.isBlockLoaded(area.stationary.getPos(),false)
                    ||world.getTileEntity(area.stationary.getPos())!=area.stationary)return false;
            return area.stationary.formed()&&area.stationary.screenOn()&&area.stationary.activity()==2;
        });
        ACTIVE.putIfAbsent(world,this);
    }
    static int argb(int color){return color<4||MapColor.COLORS[color>>2]==null?0:COLORS[color];}
    static void tick(long navigationDeadline){
        if(ACTIVE.isEmpty())return;
        long deadline=Math.min(navigationDeadline,System.nanoTime()+500000);
        int remaining=NavigationSurface.MAX_SAMPLES_PER_TICK;
        Iterator<Map.Entry<WorldServer,NavigationSurfaceData>> it=ACTIVE.entrySet().iterator();
        Map.Entry<WorldServer,NavigationSurfaceData> first=null;
        while(it.hasNext()&&remaining>0&&System.nanoTime()<deadline){
            Map.Entry<WorldServer,NavigationSurfaceData> entry=it.next();
            NavigationSurfaceData data=entry.getValue();
            WorldServer world=entry.getKey();
            long revision=data.atlas.revision();
            remaining-=data.scanner.tick(data.atlas,world.getTotalWorldTime(),remaining,deadline,
                    (x,z)->data.sample(world,x,z));
            if(data.atlas.revision()!=revision)data.markDirty();
            if(!data.scanner.active())it.remove();
            else if(first==null)first=entry;
        }
        if(first!=null){WorldServer world=first.getKey();ACTIVE.put(world,ACTIVE.remove(world));}
    }
    static void unload(World world){ACTIVE.remove(world);}
    private int sample(WorldServer world,int x,int z){
        Chunk chunk=world.getChunkProvider().getLoadedChunk(x>>4,z>>4);
        if(chunk==null||chunk.isEmpty())return 0;
        int y=Math.min(255,chunk.getHeightValue(x&15,z&15));
        for(int floor=Math.max(0,y-24);y>=floor;y--){
            probe.setPos(x,y,z);
            IBlockState state=chunk.getBlockState(probe);
            MapColor color;
            try{
                net.minecraft.util.ResourceLocation id=state.getBlock().getRegistryName();
                color=id!=null&&"minecraft".equals(id.getResourceDomain())
                        ?state.getMapColor(world,probe):state.getMaterial().getMaterialMapColor();
            }catch(RuntimeException unavailable){return 0;}
            if(color==MapColor.AIR)continue;
            int shade=1;
            if(color==MapColor.WATER){
                int depth=0;
                while(depth<8&&y-depth>0&&chunk.getBlockState(probe.setPos(x,y-depth-1,z)).getMaterial().isLiquid())depth++;
                if(depth<2)shade=2;else if(depth>5)shade=0;
            }else if((z&15)!=0){
                int slope=y+1-chunk.getHeightValue(x&15,(z-1)&15);
                if(slope>1)shade=2;else if(slope<0)shade=0;
            }
            return color.colorIndex*4+shade;
        }
        return 0;
    }
    @Override public void readFromNBT(NBTTagCompound root){
        if(root.getInteger("Version")!=1)return;
        if(root.hasUniqueId("Atlas"))atlas.setIdentity(root.getUniqueId("Atlas"));
        atlas.restore(root.getByteArray("Tiles"));
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound root){
        root.setInteger("Version",1);root.setUniqueId("Atlas",atlas.identity());root.setByteArray("Tiles",atlas.serialize());return root;
    }
}
