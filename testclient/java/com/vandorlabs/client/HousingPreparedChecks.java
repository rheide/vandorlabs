package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;

/** Incremental parity and preparation costs against the delivered 43-file performance patch. */
public final class HousingPreparedChecks {
    private static final com.sun.management.ThreadMXBean METER=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static volatile Object consumed;
    private static void need(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    private static long bytes(){return METER.getThreadAllocatedBytes(Thread.currentThread().getId());}
    private static final class Atlas implements Function<String,TextureAtlasSprite> {
        final Map<String,TextureAtlasSprite> sprites=new ConcurrentHashMap<>();
        public TextureAtlasSprite apply(String name){return sprites.computeIfAbsent(name,key->{TextureAtlasSprite sprite=new TextureAtlasSprite(key){};sprite.setIconWidth(16);sprite.setIconHeight(16);int cell=Math.floorMod(key.hashCode(),128);sprite.initSprite(256,256,cell%16*16,cell/16*16,false);return sprite;});}
    }
    private static IBakedModel delegate(Function<String,TextureAtlasSprite> atlas){return new IBakedModel(){
        public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long random){return Collections.emptyList();}
        public boolean isAmbientOcclusion(){return true;}public boolean isGui3d(){return true;}public boolean isBuiltInRenderer(){return false;}
        public TextureAtlasSprite getParticleTexture(){return atlas.apply("particle");}
        public ItemCameraTransforms getItemCameraTransforms(){return ItemCameraTransforms.DEFAULT;}
        public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
    };}
    private static IExtendedBlockState state(IBlockState source,int finish,int layout,int visible){return HousingPreparedStateChecks.sample(source,finish,-1,FaceTextures.DEFAULT,layout,visible);}
    private static void same(List<BakedQuad> expected,List<BakedQuad> actual){
        need(expected.size()==actual.size(),"prepared quad count changed");
        for(int i=0;i<expected.size();i++){
            BakedQuad a=expected.get(i),b=actual.get(i);
            need(a.getFace()==b.getFace()&&a.getSprite()==b.getSprite()&&a.getTintIndex()==b.getTintIndex()&&a.shouldApplyDiffuseLighting()==b.shouldApplyDiffuseLighting()
                    &&a.getFormat().equals(b.getFormat())&&Arrays.equals(a.getVertexData(),b.getVertexData()),"prepared housing geometry/UV/AO/format changed at quad "+i);
        }
    }
    private static void modelMatrix(){
        Atlas atlas=new Atlas();IBakedModel delegate=delegate(atlas);int comparisons=0;
        for(int kind=0;kind<3;kind++){
            boolean slab=kind==1,storage=kind==2;Block block=slab?ModBlocks.PROGRAMMABLE_SLAB:ModBlocks.PROGRAMMABLE_BLOCK;
            ProgrammableHousingModel current=new ProgrammableHousingModel(delegate,slab,storage,atlas);
            ReferencePreparedHousingModel previous=new ReferencePreparedHousingModel(delegate,slab,storage,atlas);
            List<BakedQuad> pinned=null;IBlockState pinnedState=null;
            for(IBlockState source:block.getBlockState().getValidStates())for(int finish:new int[]{0,2,ScreenHousingTextures.DEFAULT_STORAGE})for(int layout=0;layout<3;layout++)for(int mask=0;mask<64;mask++){
                IBlockState input=state(source,finish,layout,mask);List<BakedQuad> actual=current.getQuads(input,null,0);same(previous.getQuads(input,null,0),actual);comparisons++;
                need(actual==current.getQuads(input,null,45),"prepared visibility view identity changed");
                if(pinned==null&&mask==63){pinned=actual;pinnedState=input;}
                if(mask!=0){try{actual.set(0,actual.get(0));throw new AssertionError("prepared view is mutable");}catch(UnsupportedOperationException expected){}}
            }
            need(current.getQuads(pinnedState,null,0)==pinned,"other visibility masks displaced a prepared family");
            need(current.cachedUniformBytes()<=ProgrammableHousingModel.MAX_UNIFORM_BYTES,"uniform family byte budget exceeded");
            need(current.cachedFaceLists()==0,"ordinary uniform family fell back to per-mask LRU lists");
            need(current.getQuads(null,null,0).isEmpty(),"inventory delegation changed");
            for(EnumFacing side:EnumFacing.values())need(current.getQuads(block.getDefaultState(),side,0).isEmpty(),"side-specific culling changed");
        }
        System.out.println("housing-prepared geometry PASS all facings/halves/layouts/visibility masks/storage mappings, exact vertex/AO/UV records and immutable family views; comparisons="+comparisons);
    }
    private static IBlockState[] varied(int families,boolean masks){
        IBlockState[] result=new IBlockState[families*(masks?64:1)];IBlockState base=ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState();
        for(int family=0;family<families;family++){
            IBlockState facing=base.withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.getFront(family%6));
            for(int mask=0;mask<(masks?64:1);mask++)result[family*(masks?64:1)+mask]=state(facing,family/18+1,(family/6)%3,masks?mask:63);
        }
        return result;
    }
    private static void overflowAndConcurrent()throws Exception {
        Atlas atlas=new Atlas();IBakedModel delegate=delegate(atlas);ProgrammableHousingModel current=new ProgrammableHousingModel(delegate,false,false,atlas);
        ReferencePreparedHousingModel previous=new ReferencePreparedHousingModel(delegate,false,false,atlas);IBlockState[] inputs=varied(900,false);
        List<BakedQuad> pinned=current.getQuads(inputs[0],null,0);
        for(IBlockState input:inputs)same(previous.getQuads(input,null,0),current.getQuads(input,null,0));
        int count=current.cachedUniformFamilies();long retained=current.cachedUniformBytes();
        need(count>0&&count<inputs.length,"uniform-family overflow fixture did not exceed admission budget");
        need(retained<=ProgrammableHousingModel.MAX_UNIFORM_BYTES,"overflow exceeded retained uniform byte budget");
        for(IBlockState input:inputs)same(previous.getQuads(input,null,0),current.getQuads(input,null,0));
        need(current.cachedUniformFamilies()==count&&current.cachedUniformBytes()==retained,"overflow continually recreated admitted families");
        need(current.getQuads(inputs[0],null,0)==pinned,"overflow evicted an admitted uniform family");
        ProgrammableHousingModel raced=new ProgrammableHousingModel(delegate,false,false,atlas);ExecutorService workers=Executors.newFixedThreadPool(8);
        List<List<BakedQuad>> expected=new ArrayList<>();for(IBlockState input:inputs)expected.add(previous.getQuads(input,null,0));
        try{
            CountDownLatch start=new CountDownLatch(1);List<Future<?>> jobs=new ArrayList<>();
            for(int worker=0;worker<8;worker++){final int seed=worker;jobs.add(workers.submit(()->{start.await();Random random=new Random(seed);for(int n=0;n<3000;n++){int at=random.nextInt(inputs.length);same(expected.get(at),raced.getQuads(inputs[at],null,n));}return null;}));}
            start.countDown();for(Future<?> job:jobs)job.get();
        }finally{workers.shutdownNow();}
        need(raced.cachedUniformBytes()<=ProgrammableHousingModel.MAX_UNIFORM_BYTES,"concurrent admissions exceeded family budget");
        // Race distinct masks of the same newly admitted family: readers must never
        // see a view before all of its write-once quad slots have been published.
        IBlockState[] masks=varied(12,true);List<List<BakedQuad>> maskExpected=new ArrayList<>();
        for(IBlockState input:masks)maskExpected.add(previous.getQuads(input,null,0));
        ProgrammableHousingModel partial=new ProgrammableHousingModel(delegate,false,false,atlas);ExecutorService maskWorkers=Executors.newFixedThreadPool(8);
        try{
            CountDownLatch start=new CountDownLatch(1);List<Future<?>> jobs=new ArrayList<>();
            for(int worker=0;worker<8;worker++){final int seed=worker+17;jobs.add(maskWorkers.submit(()->{start.await();Random random=new Random(seed);for(int n=0;n<3000;n++){int at=random.nextInt(masks.length);same(maskExpected.get(at),partial.getQuads(masks[at],null,n));}return null;}));}
            start.countDown();for(Future<?> job:jobs)job.get();
        }finally{maskWorkers.shutdownNow();}
        need(partial.cachedUniformBytes()<=ProgrammableHousingModel.MAX_UNIFORM_BYTES,"lazy masks exceeded reserved family budget");
        System.out.println("housing-prepared retention PASS saturated fallback, stable retained families, concurrent cold/colliding probes and lazy mask publication; admitted="+count+" bytes="+retained);
    }
    private static VertexFormat expanded(){return new VertexFormat(DefaultVertexFormats.BLOCK).addElement(DefaultVertexFormats.NORMAL_3B).addElement(DefaultVertexFormats.PADDING_1B)
            .addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.GENERIC,4));}
    private static void formatGeneration()throws Exception {
        Atlas atlas=new Atlas();IBakedModel delegate=delegate(atlas);AtomicReference<VertexFormat> live=new AtomicReference<>(DefaultVertexFormats.BLOCK);
        CountDownLatch building=new CountDownLatch(1),resume=new CountDownLatch(1);AtomicBoolean pause=new AtomicBoolean(true);
        Function<String,TextureAtlasSprite> delayed=name->{if(Thread.currentThread().getName().equals("housing-prepared-old-format")&&pause.compareAndSet(true,false)){
            building.countDown();try{need(resume.await(10,TimeUnit.SECONDS),"old prepared-family worker did not resume");}catch(InterruptedException failure){throw new AssertionError(failure);}}
            return atlas.apply(name);};
        ProgrammableHousingModel current=new ProgrammableHousingModel(delegate,false,false,delayed,live::get);
        ReferencePreparedHousingModel previous=new ReferencePreparedHousingModel(delegate,false,false,atlas,live::get);
        IBlockState input=state(ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),2,1,1<<EnumFacing.UP.getIndex());
        ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"housing-prepared-old-format"));
        Future<List<BakedQuad>> pending=worker.submit(()->current.getQuads(input,null,0));
        try{
            need(building.await(10,TimeUnit.SECONDS),"old family did not reach build");live.set(expanded());
            List<BakedQuad> wide=current.getQuads(input,null,0);same(previous.getQuads(input,null,0),wide);resume.countDown();List<BakedQuad> narrow=pending.get(10,TimeUnit.SECONDS);
            for(BakedQuad quad:narrow)need(quad.getVertexData().length==4*DefaultVertexFormats.BLOCK.getIntegerSize(),"retired uniform family mixed formats");
            need(current.getQuads(input,null,0)==wide,"retired worker repopulated current prepared families");
            live.set(DefaultVertexFormats.BLOCK);List<BakedQuad> restored=current.getQuads(input,null,0);same(previous.getQuads(input,null,0),restored);need(restored!=wide&&restored!=narrow,"format restoration reused a retired family");
        }finally{resume.countDown();worker.shutdownNow();}
        System.out.println("housing-prepared format PASS uniform-family shader expansion/restoration and in-flight retirement");
    }
    private static void stateChanges(){
        int cases=0;BlockPos pos=new BlockPos(8,90,8);
        for(Block block:new Block[]{new BlockProgrammableBlock(),new BlockProgrammableSlab()})for(IBlockState base:block.getBlockState().getValidStates()){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);world.setBlockState(pos,base,2);
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);tile.setMainTextureLayout(1);
            IExtendedBlockState initial=(IExtendedBlockState)block.getExtendedState(base,world,pos);
            for(int mask=0;mask<64;mask++){
                for(EnumFacing face:EnumFacing.values()){BlockPos next=pos.offset(face);if((mask&(1<<face.getIndex()))!=0)world.states.put(next,Blocks.STONE.getDefaultState());else world.states.remove(next);}
                world.stateReads=0;IExtendedBlockState actual=(IExtendedBlockState)block.getExtendedState(base,world,pos);
                need(world.stateReads==6,"snapshot reuse skipped live neighbour reads");
                HousingPreparedStateChecks.same((IExtendedBlockState)ReferencePreparedHousingSampler.extend(base,world,pos,block instanceof BlockProgrammableSlab),actual);cases++;
            }
            world.states.clear();world.states.put(pos,base);
            need(block.getExtendedState(base,world,pos)==initial,"removed neighbours did not restore the original shared state");
            tile.setHousingTexture(4);tile.setSideTexture(6);
            HousingPreparedStateChecks.same((IExtendedBlockState)ReferencePreparedHousingSampler.extend(base,world,pos,block instanceof BlockProgrammableSlab),(IExtendedBlockState)block.getExtendedState(base,world,pos));
            world.chunkLimit=true;BlockPos edge=new BlockPos(15,90,15);world.states.put(edge,base);tile.setPos(edge);world.tiles.remove(pos);world.tiles.put(edge,tile);
            HousingPreparedStateChecks.same((IExtendedBlockState)ReferencePreparedHousingSampler.extend(base,world,edge,block instanceof BlockProgrammableSlab),(IExtendedBlockState)block.getExtendedState(base,world,edge));
        }
        System.out.println("housing-prepared live-values PASS placement/removal masks, material edits, all metadata and loaded-chunk edges; cases="+cases);
    }
    private interface Work {void run(int count);}
    private static long[] measure(String profile,int calls,Work old,Work current,boolean requireReduction){
        Work[] work={old,current};long[][] wall=new long[2][21],cpu=new long[2][21],allocation=new long[2][21];
        for(int batch=-25;batch<21;batch++)for(int order=0;order<2;order++){
            int algorithm=(batch+order)&1;long memory=bytes(),thread=METER.getCurrentThreadCpuTime(),start=System.nanoTime();work[algorithm].run(calls);
            long elapsed=System.nanoTime()-start,used=METER.getCurrentThreadCpuTime()-thread,allocated=bytes()-memory;
            if(batch>=0){wall[algorithm][batch]=elapsed;cpu[algorithm][batch]=used;allocation[algorithm][batch]=allocated;}
        }
        for(int algorithm=0;algorithm<2;algorithm++){
            Arrays.sort(wall[algorithm]);Arrays.sort(cpu[algorithm]);Arrays.sort(allocation[algorithm]);
            System.out.printf(Locale.ROOT,"housing-prepared profile=%s algorithm=%s calls=%d wall-median-ns=%d cpu-median-ns=%d allocated-median-bytes=%d%n",profile,algorithm==0?"delivered-performance-patch":"prepared",calls,wall[algorithm][10],cpu[algorithm][10],allocation[algorithm][10]);
        }
        if(requireReduction)need(allocation[1][10]<allocation[0][10],"prepared common path did not reduce allocation: "+profile);
        return new long[]{allocation[0][10],allocation[1][10]};
    }
    private static void modelCosts(){
        for(String profile:new String[]{"default-uniform","varied-visibility","saturated-uniform"}){
            Atlas atlas=new Atlas();IBakedModel delegate=delegate(atlas);
            ProgrammableHousingModel current=new ProgrammableHousingModel(delegate,false,false,atlas);ReferencePreparedHousingModel previous=new ReferencePreparedHousingModel(delegate,false,false,atlas);
            IBlockState[] inputs=profile.equals("default-uniform")?new IBlockState[]{state(ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),0,1,63)}:varied(profile.equals("varied-visibility")?24:900,profile.equals("varied-visibility"));
            for(IBlockState input:inputs){previous.getQuads(input,null,0);current.getQuads(input,null,0);}
            long[] allocation=measure(profile,8192,n->{for(int i=0;i<n;i++)consumed=previous.getQuads(inputs[i%inputs.length],null,i);},n->{for(int i=0;i<n;i++)consumed=current.getQuads(inputs[i%inputs.length],null,i);},true);
            need(allocation[1]<=allocation[0],"saturated prepared-family admission increased allocation");
        }
    }
    private static void coldAndConsumeCosts(){
        Atlas atlas=new Atlas();IBakedModel delegate=delegate(atlas);IBlockState[] partial=new IBlockState[64];
        for(int i=0;i<partial.length;i++)partial[i]=state(ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),i+1,1,1<<EnumFacing.UP.getIndex());
        measure("first-use-single-face-families",64,n->{ReferencePreparedHousingModel model=new ReferencePreparedHousingModel(delegate,false,false,atlas);for(int i=0;i<n;i++)consumed=model.getQuads(partial[i],null,i);},
                n->{ProgrammableHousingModel model=new ProgrammableHousingModel(delegate,false,false,atlas);for(int i=0;i<n;i++)consumed=model.getQuads(partial[i],null,i);},false);
        IBlockState input=state(ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),0,1,63);
        ProgrammableHousingModel current=new ProgrammableHousingModel(delegate,false,false,atlas);ReferencePreparedHousingModel previous=new ReferencePreparedHousingModel(delegate,false,false,atlas);
        measure("uniform-with-quad-consumption",8192,n->{long sum=0;for(int i=0;i<n;i++){List<BakedQuad> quads=previous.getQuads(input,null,i);for(int q=0;q<quads.size();q++)sum+=quads.get(q).getVertexData().length;}consumed=sum;},
                n->{long sum=0;for(int i=0;i<n;i++){List<BakedQuad> quads=current.getQuads(input,null,i);for(int q=0;q<quads.size();q++)sum+=quads.get(q).getVertexData().length;}consumed=sum;},true);
    }
    private static void stateCosts(){
        for(boolean slab:new boolean[]{false,true}){
            Block block=slab?new BlockProgrammableSlab():new BlockProgrammableBlock();NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
            BlockPos[] positions=new BlockPos[128];IBlockState[] states=new IBlockState[128];
            for(int i=0;i<positions.length;i++){
                BlockPos pos=positions[i]=new BlockPos((i%16)*4+8,90,(i/16)*4+8);IBlockState state=states[i]=block.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.getFront(i%6));world.setBlockState(pos,state,2);
                for(EnumFacing face:EnumFacing.values())if((i&(1<<face.getIndex()))!=0)world.states.put(pos.offset(face),Blocks.STONE.getDefaultState());
            }
            measure(slab?"default-slab-snapshot":"default-block-snapshot",4096,n->{for(int i=0;i<n;i++)consumed=ReferencePreparedHousingSampler.extend(states[i&127],world,positions[i&127],slab);},n->{for(int i=0;i<n;i++)consumed=block.getExtendedState(states[i&127],world,positions[i&127]);},true);
        }
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();METER.setThreadAllocatedMemoryEnabled(true);METER.setThreadCpuTimeEnabled(true);
        HousingPreparedStateChecks.run();modelMatrix();overflowAndConcurrent();formatGeneration();stateChanges();modelCosts();stateCosts();coldAndConsumeCosts();
        System.out.println("housing-prepared PASS incremental preparation evidence; timings are Java CPU/allocation costs, not hardware FPS or shader acceptance");
    }
}
