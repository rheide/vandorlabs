package com.vandorlabs.navigation;

import com.vandorlabs.core.CruiseLayerTransformer;
import com.vandorlabs.network.MessageCruiseTerrain;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.world.World;
import net.minecraft.world.biome.*;
import net.minecraft.world.gen.layer.*;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** World-local seed imagery. No worker receives a World, Chunk, or live generator. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class CruiseAtlas {
    public static final int SIZE=MessageCruiseTerrain.SIZE;
    private static final Map<World,CruiseAtlas> WORLDS=new IdentityHashMap<>();
    private final LinkedHashMap<Tile,int[]> cache=new LinkedHashMap<>(128,.75f,true);
    private final Map<Tile,Future<int[]>> pending=new HashMap<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"Vandor flight seed atlas");t.setDaemon(true);t.setPriority(Thread.NORM_PRIORITY-1);return t;
    });
    private final GenLayer layer;
    private final List<GenLayer> landLevels=new ArrayList<>();
    private boolean workerFailed;
    private final net.minecraft.world.gen.NoiseGeneratorPerlin surface;

    private final int constant;
    private final Map<Integer,Integer> colors=new HashMap<>();
    private final CruiseScratch scratch=new CruiseScratch();
    private Tile fallback;
    private int[] fallbackImage;
    private int fallbackRow;
    private long fallbackTick=Long.MIN_VALUE;
    public static int nearStep(double altitude){return Math.max(4,Integer.highestOneBit((int)altitude/64));}
    public static final class Tile {
        public final int x,z,step;
        public Tile(int x,int z,int step){this.x=x;this.z=z;this.step=step;}
        public int hashCode(){return (x*31+z)*31+step;}
        public boolean equals(Object other){
            if(!(other instanceof Tile))return false;
            Tile t=(Tile)other;return x==t.x&&z==t.z&&step==t.step;
        }
    }
    public static CruiseAtlas forWorld(World world){return WORLDS.computeIfAbsent(world,CruiseAtlas::new);}
    @SubscribeEvent public static void unload(WorldEvent.Unload event){
        if(event.getWorld().isRemote)return;
        CruiseAtlas a=WORLDS.remove(event.getWorld());if(a!=null)a.worker.shutdownNow();
    }
    private CruiseAtlas(World world){
        BiomeProvider live=world.getBiomeProvider();
        constant=live.getClass()==BiomeProviderSingle.class?NavigationService.biomeColor(live.getBiome(BlockPos.ORIGIN)):0;
        GenLayer detached=null;
        if(live.getClass()==BiomeProvider.class && CruiseLayerTransformer.installed){
            try{
                Field field=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(BiomeProvider.class,"genBiomes","field_76944_d");
                GenLayer original=(GenLayer)field.get(live);
                GenLayer replica=(GenLayer)field.get(new BiomeProvider(world.getWorldInfo()));
                Set<GenLayer> used=Collections.newSetFromMap(new IdentityHashMap<>());
                if(vanilla(original,used) && !used.contains(replica)){
                    Set<GenLayer> copy=Collections.newSetFromMap(new IdentityHashMap<>());
                    if(vanilla(replica,copy)&&Collections.disjoint(used,copy))detached=replica;
                }
            }catch(ReflectiveOperationException|RuntimeException unsupported){
                com.vandorlabs.VandorLabs.logger.debug("Flight atlas using world biome sampling",unsupported);
            }
        }
        layer=detached;
        if(layer instanceof GenLayerRiverMix)prepareLandLevels((GenLayerRiverMix)layer);
        // Reproduce the vanilla surface generator's initialization from the seed.
        // Its stone-depth variation gives exposed land a continuous, geographically stable texture.
        Random seed=new Random(world.getSeed());
        for(int octaves:new int[]{16,16,8})new net.minecraft.world.gen.NoiseGeneratorOctaves(seed,octaves);
        surface=new net.minecraft.world.gen.NoiseGeneratorPerlin(seed,4);
        for(Biome biome:Biome.REGISTRY)colors.put(Biome.getIdForBiome(biome),NavigationService.biomeColor(biome));
    }
    /** At the horizon one pixel can span many chunks. Query the land generator before its
     * final zoom passes instead of generating millions of fine cells only to discard them. */
    private void prepareLandLevels(GenLayerRiverMix mix){
        try{
            Field land=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(GenLayerRiverMix.class,"biomePatternGeneratorChain","field_75910_b");
            Field parent=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(GenLayer.class,"parent","field_75909_a");
            GenLayer node=(GenLayer)land.get(mix);
            while(node instanceof GenLayerSmooth||node instanceof GenLayerShore||node instanceof GenLayerZoom||node instanceof GenLayerAddIsland){
                boolean zoom=node instanceof GenLayerZoom;
                node=(GenLayer)parent.get(node);
                if(zoom)landLevels.add(node);
            }
        }catch(ReflectiveOperationException|RuntimeException unsupported){landLevels.clear();}
    }
    private static boolean vanilla(GenLayer node,Set<GenLayer> seen)throws IllegalAccessException{
        if(node==null||!seen.add(node))return true;
        if(!node.getClass().getName().startsWith("net.minecraft.world.gen.layer."))return false;
        for(Class<?> type=node.getClass();GenLayer.class.isAssignableFrom(type);type=type.getSuperclass())
            for(Field f:type.getDeclaredFields())if(GenLayer.class.isAssignableFrom(f.getType())){
                f.setAccessible(true);if(!vanilla((GenLayer)f.get(node),seen))return false;
            }
        return true;
    }
    private int[] sample(Tile tile){
        int[] pixels=new int[SIZE*SIZE];
        if(constant!=0){Arrays.fill(pixels,constant);return pixels;}
        // Quarter-scale biome coordinates. Batch neighbouring samples into one layer traversal.
        int zoom=tile.step<64?0:Math.min(landLevels.size(),Integer.numberOfTrailingZeros(tile.step/4));
        int cell=4<<zoom;
        GenLayer generator=zoom==0?layer:landLevels.get(zoom-1);
        int stride=tile.step/cell;
        int group=Math.max(1,Math.min(SIZE,128/stride));
        int detail=stride>=128?2:1;
        try{
            for(int row=0;row<SIZE;row+=Math.max(group,detail))for(int col=0;col<SIZE;col+=Math.max(group,detail)){
                if(Thread.currentThread().isInterrupted())throw new CancellationException();
                int width=(group-1)*stride+1;
                scratch.reset();
                int[] ids=generator.getInts(tile.x/cell+col*stride,tile.z/cell+row*stride,width,width);
                for(int j=0;j<group;j+=detail)for(int i=0;i<group;i+=detail){
                    int color=colors.getOrDefault(ids[(j*width+i)*stride],0xff427440);
                    fill(pixels,row+j,col+i,detail,color);
                }
            }
            for(int r=0;r<SIZE;r++)for(int c=0;c<SIZE;c++){
                int i=r*SIZE+c,color=pixels[i];
                if(color==0xff28659b)continue;
                int x=tile.x+c*tile.step,z=tile.z+r*tile.step;
                double n=surface.getValue(x*.0625,z*.0625);
                double broad=surface.getValue(x*.0078125,z*.0078125);
                double light=1+Math.max(-.18,Math.min(.18,n*.017+broad*.014));
                pixels[i]=0xff000000|(Math.min(255,(int)((color>>16&255)*light))<<16)
                        |(Math.min(255,(int)((color>>8&255)*light))<<8)|Math.min(255,(int)((color&255)*light));
            }
        }finally{scratch.release();}
        return pixels;
    }
    private static void fill(int[] pixels,int row,int col,int width,int color){
        for(int r=row;r<row+width;r++)Arrays.fill(pixels,r*SIZE+col,r*SIZE+col+width,color);
    }
    /** Called only on the world thread. Custom providers keep ownership of their mutable state. */
    public int[] request(World world,Tile tile){
        int[] hit=cache.get(tile);
        if(hit!=null)return hit;
        if(workerFailed||layer==null&&constant==0){
            if(fallback==null){fallback=tile;fallbackRow=0;fallbackImage=new int[SIZE*SIZE];}
            if(fallbackTick!=world.getTotalWorldTime()){
                fallbackTick=world.getTotalWorldTime();
                for(int c=0;c<SIZE;c+=8)fill(fallbackImage,fallbackRow,c,8,
                    NavigationService.cruiseColor(world,fallback.x+c*fallback.step,fallback.z+fallbackRow*fallback.step,fallback.step));
                fallbackRow+=8;
                if(fallbackRow==SIZE){retain(fallback,fallbackImage);fallback=null;fallbackImage=null;}
            }
            return cache.get(tile);
        }
        Iterator<Map.Entry<Tile,Future<int[]>>> it=pending.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry<Tile,Future<int[]>> entry=it.next();
            if(!entry.getValue().isDone())continue;
            try{retain(entry.getKey(),entry.getValue().get());}
            catch(ExecutionException e){
                workerFailed=true;
                worker.shutdownNow();
                pending.clear();
                com.vandorlabs.VandorLabs.logger.warn("Flight seed worker failed; switching to world-thread biome sampling",e.getCause());
                return null;
            }
            catch(InterruptedException e){Thread.currentThread().interrupt();}
            it.remove();
        }
        hit=cache.get(tile);
        if(hit==null&&pending.size()<4&&!pending.containsKey(tile))pending.put(tile,worker.submit(()->sample(tile)));
        return hit;
    }
    public boolean usesDetachedLayers(){return layer!=null&&!workerFailed;}
    private void retain(Tile tile,int[] pixels){
        cache.put(tile,pixels);
        if(cache.size()>4096)cache.remove(cache.keySet().iterator().next()); // 64 MiB of pixels per world
    }
}
