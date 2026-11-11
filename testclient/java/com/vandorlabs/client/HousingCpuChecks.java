package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;

/** Exact master-model parity and CPU/allocation evidence without creating a graphics context. */
public final class HousingCpuChecks {
    private static final com.sun.management.ThreadMXBean METER=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static volatile Object consumed;
    private static final AtomicInteger comparisons=new AtomicInteger();
    private static void need(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    private static long bytes(){return METER.getThreadAllocatedBytes(Thread.currentThread().getId());}
    private static final class Atlas implements Function<String,TextureAtlasSprite> {
        final Map<String,TextureAtlasSprite> sprites=new ConcurrentHashMap<>();final int generation;
        Atlas(int generation){this.generation=generation;}
        public TextureAtlasSprite apply(String name){return sprites.computeIfAbsent(name,key->{
            TextureAtlasSprite sprite=new TextureAtlasSprite(key){};sprite.setIconWidth(16);sprite.setIconHeight(16);
            int cell=Math.floorMod(key.hashCode()+generation,128);sprite.initSprite(256,256,cell%16*16,cell/16*16,false);return sprite;
        });}
    }
    private static IBakedModel delegate(Function<String,TextureAtlasSprite> atlas){return new IBakedModel(){
        public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long random){return Collections.emptyList();}
        public boolean isAmbientOcclusion(){return true;}public boolean isGui3d(){return true;}public boolean isBuiltInRenderer(){return false;}
        public TextureAtlasSprite getParticleTexture(){return atlas.apply("particle");}
        public ItemCameraTransforms getItemCameraTransforms(){return ItemCameraTransforms.DEFAULT;}
        public ItemOverrideList getOverrides(){return new ItemOverrideList(Collections.emptyList());}
    };}
    private static HousingFaceLayouts joined(int shift){
        HousingFaceLayouts.Plane[] planes=new HousingFaceLayouts.Plane[6];
        for(EnumFacing face:EnumFacing.values()){
            int[] min={-1-shift,-2,-1},size={4+shift,5,3};int axis=face.getAxis().ordinal();min[axis]=0;size[axis]=1;
            planes[face.getIndex()]=new HousingFaceLayouts.Plane(min,size);
        }
        return new HousingFaceLayouts(planes);
    }
    private static FaceTextures faces(int kind){
        return kind==0?FaceTextures.DEFAULT:new FaceTextures(true,kind==1?new int[]{1,2,3,4,5,6}:kind==2?new int[]{-1,2,-1,4,-1,6}:new int[]{-1,-1,-1,-1,-1,-1});
    }
    private static IExtendedBlockState state(IBlockState base,int kind,int layout,int visible,HousingFaceLayouts joined){
        IExtendedBlockState result=((IExtendedBlockState)base).withProperty(ProgrammableHousingState.FINISH,ScreenHousingTextures.DEFAULT_STORAGE)
                .withProperty(ProgrammableHousingState.SIDE_FINISH,kind==2?3:-1).withProperty(ProgrammableHousingState.FACES,faces(kind))
                .withProperty(ProgrammableHousingState.TILE_SIDES,layout).withProperty(ProgrammableHousingState.VISIBLE,visible);
        return result.getUnlistedNames().contains(ProgrammableHousingState.JOINED)?result.withProperty(ProgrammableHousingState.JOINED,joined):result;
    }
    private static void same(List<BakedQuad> expected,List<BakedQuad> actual){
        need(expected.size()==actual.size(),"quad count changed");
        for(int i=0;i<expected.size();i++){
            BakedQuad a=expected.get(i),b=actual.get(i);
            need(a.getFace()==b.getFace()&&a.getSprite()==b.getSprite()&&a.getTintIndex()==b.getTintIndex()
                    &&a.shouldApplyDiffuseLighting()==b.shouldApplyDiffuseLighting()&&a.getFormat().equals(b.getFormat())
                    &&Arrays.equals(a.getVertexData(),b.getVertexData()),"complete housing vertex/sprite/lighting record changed at quad "+i);
        }
        comparisons.incrementAndGet();
    }
    private static void modelParity()throws Exception {
        Atlas atlas=new Atlas(0);IBakedModel delegate=delegate(atlas);
        for(int family=0;family<3;family++){
            boolean slab=family==1,storage=family==2;Block block=slab?ModBlocks.PROGRAMMABLE_SLAB:ModBlocks.PROGRAMMABLE_BLOCK;
            ProgrammableHousingModel current=new ProgrammableHousingModel(delegate,slab,storage,atlas);
            ReferenceHousingCpuModel old=new ReferenceHousingCpuModel(delegate,slab,storage,atlas);
            List<IBlockState> inputs=new ArrayList<>();List<List<BakedQuad>> expected=new ArrayList<>();
            for(IBlockState base:block.getBlockState().getValidStates())for(int kind=0;kind<4;kind++)for(int layout=0;layout<3;layout++)for(int visible=0;visible<64;visible++){
                IBlockState input=state(base,kind,layout,visible,HousingFaceLayouts.NONE);
                List<BakedQuad> golden=old.getQuads(input,null,0),actual=current.getQuads(input,null,0);same(golden,actual);
                need(current.getQuads(state(base,kind,layout,visible,HousingFaceLayouts.NONE),null,42)==actual,"equal immutable material values did not reuse their face list");
                if(visible==63&&layout==0){inputs.add(input);expected.add(golden);}
            }
            if(family==0)for(IBlockState base:block.getBlockState().getValidStates())for(int layout=0;layout<3;layout++)for(int shift=0;shift<2;shift++)for(int visible=1;visible<64;visible++){
                IBlockState input=state(base,1,layout,visible,joined(shift));List<BakedQuad> golden=old.getQuads(input,null,0),actual=current.getQuads(input,null,0);same(golden,actual);
                need(current.getQuads(state(base,1,layout,visible,joined(shift)),null,0)==actual,"value-equal joined bounds missed profile cache");
                if(visible==63){inputs.add(input);expected.add(golden);}
            }
            ExecutorService workers=Executors.newFixedThreadPool(4);
            try{
                List<Future<?>> jobs=new ArrayList<>();
                for(int worker=0;worker<4;worker++){final int seed=worker;jobs.add(workers.submit(()->{Random random=new Random(seed);for(int n=0;n<1000;n++){int at=random.nextInt(inputs.size());same(expected.get(at),current.getQuads(inputs.get(at),null,n));}}));}
                for(Future<?> job:jobs)job.get();
            }finally{workers.shutdownNow();}
            need(current.cachedProfileBytes()<=ProgrammableHousingModel.MAX_PROFILE_BYTES,"profile byte budget exceeded");
            need(current.cachedFaceLists()<=ProgrammableHousingModel.MAX_FACE_LISTS,"uniform fast-path budget changed");
            for(EnumFacing face:EnumFacing.values())need(current.getQuads(block.getDefaultState(),face,0).isEmpty(),"side culling path changed");
        }
        System.out.println("housing-cpu geometry PASS all visibility masks, face overrides, storage sets, slab halves, facing/layout/join values, concurrent workers; comparisons="+comparisons);
    }
    private static VertexFormat expanded(){return new VertexFormat(DefaultVertexFormats.BLOCK).addElement(DefaultVertexFormats.NORMAL_3B)
            .addElement(DefaultVertexFormats.PADDING_1B).addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.GENERIC,4));}
    private static void formatAndReload()throws Exception {
        Atlas atlas=new Atlas(0);IBakedModel delegate=delegate(atlas);AtomicReference<VertexFormat> live=new AtomicReference<>(DefaultVertexFormats.BLOCK);
        ProgrammableHousingModel current=new ProgrammableHousingModel(delegate,false,false,atlas,live::get);
        ReferenceHousingCpuModel old=new ReferenceHousingCpuModel(delegate,false,false,atlas,live::get);
        IBlockState input=state(ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),1,2,63,joined(0));
        List<BakedQuad> narrow=current.getQuads(input,null,0);same(old.getQuads(input,null,0),narrow);
        live.set(expanded());List<BakedQuad> wide=current.getQuads(input,null,0);same(old.getQuads(input,null,0),wide);
        need(wide!=narrow&&wide.get(0).getVertexData().length==4*live.get().getIntegerSize(),"format change reused a narrow profile");
        live.set(DefaultVertexFormats.BLOCK);List<BakedQuad> restored=current.getQuads(input,null,0);same(old.getQuads(input,null,0),restored);need(restored!=wide,"format retirement failed");

        Atlas replacement=new Atlas(1);ProgrammableHousingModel reloaded=new ProgrammableHousingModel(delegate(replacement),false,false,replacement);
        List<BakedQuad> fresh=reloaded.getQuads(input,null,0);
        need(fresh.get(0).getSprite()!=restored.get(0).getSprite()&&!Arrays.equals(fresh.get(0).getVertexData(),restored.get(0).getVertexData()),"new atlas/model reused retired sprite UVs");

        CountDownLatch building=new CountDownLatch(1),resume=new CountDownLatch(1);AtomicBoolean pause=new AtomicBoolean(true);
        Function<String,TextureAtlasSprite> delayed=name->{
            if(Thread.currentThread().getName().equals("housing-old-format")&&pause.compareAndSet(true,false)){
                building.countDown();try{if(!resume.await(10,TimeUnit.SECONDS))throw new AssertionError("format transition did not release old worker");}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new AssertionError(failure);}
            }
            return atlas.apply(name);
        };
        live.set(DefaultVertexFormats.BLOCK);ProgrammableHousingModel raced=new ProgrammableHousingModel(delegate,false,false,delayed,live::get);
        ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"housing-old-format"));
        Future<List<BakedQuad>> pending=worker.submit(()->raced.getQuads(input,null,0));
        try{
            need(building.await(10,TimeUnit.SECONDS),"old-format worker did not reach geometry emission");live.set(expanded());
            List<BakedQuad> next=raced.getQuads(input,null,0);resume.countDown();List<BakedQuad> previous=pending.get(10,TimeUnit.SECONDS);
            for(BakedQuad quad:previous)need(quad.getVertexData().length==4*DefaultVertexFormats.BLOCK.getIntegerSize(),"in-flight worker mixed vertex formats");
            for(BakedQuad quad:next)need(quad.getVertexData().length==4*live.get().getIntegerSize(),"new generation emitted old shader payload");
            need(raced.getQuads(input,null,0)==next,"old worker repopulated current format cache");
        }finally{resume.countDown();worker.shutdownNow();}
        System.out.println("housing-cpu invalidation PASS shader format expansion/restoration, atlas replacement and in-flight format publication");
    }
    private static void budgetAndSnapshots(){
        Atlas atlas=new Atlas(0);ProgrammableHousingModel model=new ProgrammableHousingModel(delegate(atlas),false,false,atlas);
        IExtendedBlockState base=state(ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),3,0,63,HousingFaceLayouts.NONE);
        List<BakedQuad> first=model.getQuads(base.withProperty(ProgrammableHousingState.SIDE_FINISH,0),null,0);
        for(int i=1;i<7000;i++)model.getQuads(base.withProperty(ProgrammableHousingState.SIDE_FINISH,i),null,0);
        need(model.cachedProfileBytes()<=ProgrammableHousingModel.MAX_PROFILE_BYTES&&model.cachedProfiles()<7000,"variable profile cache did not evict by bytes");
        List<BakedQuad> rebuilt=model.getQuads(base.withProperty(ProgrammableHousingState.SIDE_FINISH,0),null,0);need(rebuilt!=first,"old profile was retained beyond byte budget");same(first,rebuilt);
        int[] min={-1,-2,0},size={3,4,1},choices={1,2,3,4,5,6};HousingFaceLayouts.Plane[] planes=new HousingFaceLayouts.Plane[6];
        planes[EnumFacing.NORTH.getIndex()]=new HousingFaceLayouts.Plane(min,size);HousingFaceLayouts immutable=new HousingFaceLayouts(planes);FaceTextures faces=new FaceTextures(true,choices);
        IBlockState input=base.withProperty(ProgrammableHousingState.FACES,faces).withProperty(ProgrammableHousingState.JOINED,immutable);
        List<BakedQuad> before=model.getQuads(input,null,0);Arrays.fill(min,100);Arrays.fill(size,100);Arrays.fill(choices,0);Arrays.fill(planes,null);
        need(model.getQuads(input,null,0)==before,"caller-owned arrays mutated a cached key");
        System.out.println("housing-cpu retention PASS byte-bound eviction and immutable face/plane snapshots; retained="+model.cachedProfileBytes());
    }
    private static void benchmark(String profile,int calls)throws Exception {
        Atlas atlas=new Atlas(0);IBakedModel delegate=delegate(atlas);ProgrammableHousingModel current=new ProgrammableHousingModel(delegate,false,false,atlas);
        ReferenceHousingCpuModel old=new ReferenceHousingCpuModel(delegate,false,false,atlas);IBlockState[] inputs=new IBlockState[16];
        for(int i=0;i<inputs.length;i++)inputs[i]=state(ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),profile.equals("uniform")?0:1,i%3,63,profile.equals("joined")?joined(i%2):HousingFaceLayouts.NONE)
                .withProperty(ProgrammableHousingState.FINISH,i+1);
        IBakedModel[] models={old,current};long[][] elapsed=new long[2][21],cpu=new long[2][21],allocated=new long[2][21];
        for(int batch=-25;batch<21;batch++)for(int order=0;order<2;order++){
            int algorithm=(batch+order)&1;long memory=bytes(),thread=METER.getCurrentThreadCpuTime(),start=System.nanoTime();
            for(int n=0;n<calls;n++)consumed=models[algorithm].getQuads(inputs[n&15],null,n);
            long wall=System.nanoTime()-start,used=METER.getCurrentThreadCpuTime()-thread,alloc=bytes()-memory;
            if(batch>=0){elapsed[algorithm][batch]=wall;cpu[algorithm][batch]=used;allocated[algorithm][batch]=alloc;}
        }
        for(int algorithm=0;algorithm<2;algorithm++){Arrays.sort(elapsed[algorithm]);Arrays.sort(cpu[algorithm]);Arrays.sort(allocated[algorithm]);
            System.out.printf(Locale.ROOT,"housing-cpu profile=%s algorithm=%s calls=%d wall-median-ns=%d cpu-median-ns=%d allocated-median-bytes=%d%n",profile,algorithm==0?"master-3f25830a":"current",calls,elapsed[algorithm][10],cpu[algorithm][10],allocated[algorithm][10]);}
        if(profile.equals("uniform"))need(allocated[1][10]<=allocated[0][10]+4096,"uniform fast path gained allocation");
        else need(allocated[1][10]<allocated[0][10]&&allocated[1][10]<=calls*64L,"warm mixed/joined allocation was not reduced");
    }
    /** Frozen result path for these three baked housing families, before its eligibility reorder. */
    private static boolean oldEligible(TileEntityAnimatedScreenSelector tile,int pass){
        if(tile.fluids.any()&&pass==1)return true;
        if(tile.getWorld()!=null&&PanelJoins.flatBar(tile.getWorld().getBlockState(tile.getPos()),tile.getWorld(),tile.getPos())!=null)return pass==0||pass==1;
        Block block=tile.getBlockType();need(block.getClass()==BlockProgrammableBlock.class||block instanceof BlockProgrammableSlab||block instanceof BlockProgrammableStairs,"eligibility fixture must use baked housing");
        return false;
    }
    private static void eligibility(){
        int cases=0;long oldReads=0,newReads=0;BlockPos pos=new BlockPos(8,80,8);
        for(Block block:new Block[]{ModBlocks.PROGRAMMABLE_BLOCK,ModBlocks.PROGRAMMABLE_SLAB,new BlockProgrammableStairs()})for(IBlockState state:block.getBlockState().getValidStates()){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);world.states.put(pos,state);
            TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setWorld(world);tile.setPos(pos);world.tiles.put(pos,tile);tile.getBlockType();
            for(boolean wet:new boolean[]{false,true}){
                NBTTagCompound fluids=new NBTTagCompound();if(wet)fluids.setBoolean("Waterlogged",true);tile.fluids.read(fluids);
                for(int pass=-1;pass<=2;pass++){
                    world.stateReads=0;boolean old=oldEligible(tile,pass);oldReads+=world.stateReads;
                    world.stateReads=0;boolean now=tile.shouldRenderInPass(pass);newReads+=world.stateReads;
                    need(old==now,"baked housing eligibility changed");need(world.stateReads==0,"baked housing eligibility queried neighbour state");cases++;
                }
            }
        }
        need(oldReads>0&&newReads==0,"eligibility world-read comparison did not exercise old cost");
        System.out.println("housing-cpu eligibility PASS cases="+cases+" master-state-reads="+oldReads+" current-state-reads="+newReads+" fluid pass preserved");
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();
        METER.setThreadAllocatedMemoryEnabled(true);METER.setThreadCpuTimeEnabled(true);
        if(args.length==1&&args[0].equals("uniform-cost")){
            benchmark("uniform",32768);
            System.out.println("housing-cpu uniform-cost completed 25 warmup and 21 alternating measured batches per algorithm; timing is preparation cost, not hardware FPS");
            return;
        }
        modelParity();formatAndReload();budgetAndSnapshots();eligibility();
        benchmark("uniform",512);benchmark("mixed",512);benchmark("joined",512);
        System.out.println("housing-cpu PASS exact CPU baseline comparison; timings are Java preparation costs, not hardware FPS");
    }
}
