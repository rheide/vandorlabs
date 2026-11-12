package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import com.vandorlabs.vehicle.VehicleStructure;
import com.vandorlabs.vehicle.VehicleWorld;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Real connected-light discovery, with an independent pre-change reference and no GL context. */
public final class LightGroupCacheChecks {
    private static final class World extends NonRenderingChecks.MemoryWorld {
        int tileReads;boolean tracking;
        final Set<BlockPos> observed=new LinkedHashSet<>();
        World(){super(true);}
        @Override public IBlockState getBlockState(BlockPos pos){observe(pos);return super.getBlockState(pos);}
        @Override public TileEntity getTileEntity(BlockPos pos){observe(pos);tileReads++;return super.getTileEntity(pos);}
        @Override public boolean isBlockLoaded(BlockPos pos){observe(pos);return super.isBlockLoaded(pos);}
        private void observe(BlockPos pos){if(tracking)observed.add(pos.toImmutable());}
        void resetReads(){stateReads=tileReads=0;}
    }
    private static BlockProgrammableLight light,frame,slab;
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static TileEntityProgrammableLight put(World world,BlockPos pos,IBlockState state,boolean join,boolean on,boolean small) {
        TileEntityProgrammableLight tile=new TileEntityProgrammableLight();
        tile.configure(0,15,join,0);tile.setOn(on);tile.setWorld(world);tile.setPos(pos);
        world.states.put(pos,state);world.tiles.put(pos,tile);tile.setSmallInput(small);return tile;
    }
    private static TEAnimatedScreenSelector.LightGroup group(TileEntityProgrammableLight tile,IBlockState state){return TEAnimatedScreenSelector.lightGroup(tile,state);}
    private static void same(TEAnimatedScreenSelector.LightGroup a,TEAnimatedScreenSelector.LightGroup b,BlockPos pos) {
        require(a!=null&&b!=null,"missing light layout");
        require(a.minAxis==b.minAxis&&a.minRow==b.minRow&&a.columns==b.columns&&a.rows==b.rows&&a.right==b.right&&a.up==b.up,"light layout changed");
        require(a.left(pos)==b.left(pos)&&a.right(pos)==b.right(pos)&&a.top(pos)==b.top(pos)&&a.bottom(pos)==b.bottom(pos),"light UVs changed");
    }
    private static void parity(TileEntityProgrammableLight tile,IBlockState state){ReferenceLightGroups.clear();same(ReferenceLightGroups.lightGroup(tile,state),group(tile,state),tile.getPos());}
    private static int faces(TileEntityProgrammableLight tile,EnumFacing facing) {
        int expected=ReferenceLightGroups.visibleFaces(tile,facing),actual=TEAnimatedScreenSelector.lightVisibleFaces(tile,facing);
        require(actual==expected,"light visibility differs for "+tile.getPos()+" facing="+facing+" expected="+expected+" actual="+actual);return actual;
    }
    private static void close(World world){LightGroupCache.unload(world);ReferenceLightGroups.clear();}

    private static void geometry() {
        int scenes=0,positions=0;
        for(Block block:new Block[]{light,frame,slab})for(EnumFacing facing:EnumFacing.values())
            for(int shape=0;shape<4;shape++)for(boolean join:new boolean[]{false,true})for(boolean on:new boolean[]{false,true}) {
                World world=new World();IBlockState state=block.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,facing);
                if(block==slab&&shape%2==1)state=state.withProperty(BlockProgrammableLightSlab.HALF,net.minecraft.block.BlockSlab.EnumBlockHalf.TOP);
                PanelPlane plane=PanelPlane.of(facing);List<TileEntityProgrammableLight> tiles=new ArrayList<>();BlockPos origin=new BlockPos(8,80,8);
                try {
                    for(int row=0;row<4;row++)for(int col=0;col<5;col++) {
                        if(shape==1&&col==2&&row==1||shape==2&&col+row>4)continue;
                        TileEntityProgrammableLight tile=put(world,origin.offset(plane.right,col).offset(plane.up,row),state,join,on,shape==3);
                        if(shape==3&&col==2)tile.setFaceTexture(1);
                        tiles.add(tile);
                    }
                    for(TileEntityProgrammableLight tile:tiles){parity(tile,state);faces(tile,facing);positions++;}
                    // Every lookup/eligibility/loaded boundary observed by a cold
                    // discovery must invalidate that component when it changes.
                    LightGroupCache.unload(world);world.observed.clear();world.tracking=true;
                    TileEntityProgrammableLight root=tiles.get(0);TEAnimatedScreenSelector.LightGroup before=group(root,state);world.tracking=false;
                    List<BlockPos> dependencies=new ArrayList<>(world.observed);
                    for(BlockPos pos:dependencies) {
                        LightGroupCache.invalidate(world,pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ());
                        TEAnimatedScreenSelector.LightGroup after=group(root,state);require(after!=before,"missed consulted dependency "+pos);same(before,after,root.getPos());before=after;
                    }
                    scenes++;
                }finally{close(world);}
            }
        System.out.println("light-group geometry PASS scenes="+scenes+" member-layouts="+positions+" all-six-facings full/frame/slab holes small/mixed artwork on/off joined/unjoined observed-dependencies exact-UV-parity");
    }

    private static void visibilityMatrix() {
        int cases=0;BlockPos pos=new BlockPos(8,80,8);
        for(Block block:new Block[]{light,frame,slab})for(int mask=0;mask<64;mask++) {
            World world=new World();IBlockState state=block.getDefaultState();
            try {
                TileEntityProgrammableLight root=put(world,pos,state,false,true,false);
                for(EnumFacing side:EnumFacing.values())if((mask&(1<<side.getIndex()))!=0)put(world,pos.offset(side),state,false,true,false);
                // Deliberately reuse one tile/state with different facing arguments:
                // callers' local orientations must form part of the cache key.
                for(EnumFacing facing:EnumFacing.values()){faces(root,facing);cases++;}
                if(block==frame) {
                    TileEntityProgrammableLight neighbor=put(world,pos.east(),state,false,true,false);neighbor.onLoad();faces(root,EnumFacing.NORTH);
                    neighbor.setSmallInput(true);faces(root,EnumFacing.NORTH);
                    NBTTagCompound tag=neighbor.writeToNBT(new NBTTagCompound());tag.setBoolean("smallInput",false);neighbor.readFromNBT(tag);faces(root,EnumFacing.NORTH);
                    root.setSmallInput(true);require(faces(root,EnumFacing.NORTH)==63,"small owner must keep all faces");root.setSmallInput(false);faces(root,EnumFacing.NORTH);
                }
                if(block==slab) {
                    BlockPos next=pos.east();IBlockState upper=state.withProperty(BlockProgrammableLightSlab.HALF,net.minecraft.block.BlockSlab.EnumBlockHalf.TOP);
                    world.states.put(next,upper);world.notifyBlockUpdate(next,state,upper,2);faces(root,EnumFacing.NORTH);
                }
                TEAnimatedScreenSelector.lightVisibleFaces(root,EnumFacing.NORTH);world.resetReads();
                for(int i=0;i<32;i++)TEAnimatedScreenSelector.lightVisibleFaces(root,EnumFacing.NORTH);
                require(world.stateReads==0&&world.tileReads==0,"warm visibility performs neighbor reads");
            }finally{close(world);}
        }
        System.out.println("light-visibility parity PASS cases="+cases+" full/frame/slab all64-neighbor-subsets all6-facing-arguments small-owner/neighbor/NBT slab-half-edits zero-warm-world-reads");
    }

    private static void visibilityCosts(World world,List<TileEntityProgrammableLight> tiles,IBlockState state) {
        EnumFacing facing=state.getValue(BlockAnimatedScreenSelector.FACING);
        for(TileEntityProgrammableLight tile:tiles)faces(tile,facing);
        long beforeStates=0,beforeTiles=0,afterStates=0,afterTiles=0,beforeBytes=0,afterBytes=0,beforeNanos=0,afterNanos=0;
        for(int frame=0;frame<200;frame++) {
            world.time++;world.resetReads();long allocated=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
            for(TileEntityProgrammableLight tile:tiles)ReferenceLightGroups.visibleFaces(tile,facing);
            beforeNanos+=System.nanoTime()-start;beforeBytes+=BenchmarkAllocations.currentThreadBytes()-allocated;beforeStates+=world.stateReads;beforeTiles+=world.tileReads;
            world.resetReads();allocated=BenchmarkAllocations.currentThreadBytes();start=System.nanoTime();
            for(TileEntityProgrammableLight tile:tiles)TEAnimatedScreenSelector.lightVisibleFaces(tile,facing);
            afterNanos+=System.nanoTime()-start;afterBytes+=BenchmarkAllocations.currentThreadBytes()-allocated;afterStates+=world.stateReads;afterTiles+=world.tileReads;
        }
        require(beforeStates>0&&beforeTiles>0&&afterStates==0&&afterTiles==0,"warm visibility still scans neighbors");
        System.out.println("light-visibility stable lights=1024 frames=200 before/after stateReads="+beforeStates+"/"+afterStates+" tileReads="+beforeTiles+"/"+afterTiles+" ns="+beforeNanos+"/"+afterNanos+" allocatedBytes="+beforeBytes+"/"+afterBytes);
    }

    private static void stableAndEdits() {
        World world=new World(),other=new World();IBlockState state=light.getDefaultState();List<TileEntityProgrammableLight> tiles=new ArrayList<>();
        try {
            for(int component=0;component<32;component++)for(int row=0;row<4;row++)for(int col=0;col<8;col++)
                tiles.add(put(world,new BlockPos(8+component*64+col,64+row,8),state,true,true,false));
            TileEntityProgrammableLight foreign=put(other,new BlockPos(8,64,8),state,true,true,false);
            for(TileEntityProgrammableLight tile:tiles){ReferenceLightGroups.lightGroup(tile,state);group(tile,state);}
            long beforeStates=0,beforeTiles=0,afterStates=0,afterTiles=0;
            long beforeNanos=0,afterNanos=0,beforeBytes=0,afterBytes=0;
            for(int tick=0;tick<200;tick++) {
                world.time++;world.resetReads();long allocated=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
                for(TileEntityProgrammableLight tile:tiles)ReferenceLightGroups.lightGroup(tile,state);
                beforeNanos+=System.nanoTime()-start;beforeBytes+=BenchmarkAllocations.currentThreadBytes()-allocated;beforeStates+=world.stateReads;beforeTiles+=world.tileReads;
                world.resetReads();allocated=BenchmarkAllocations.currentThreadBytes();start=System.nanoTime();
                for(TileEntityProgrammableLight tile:tiles)group(tile,state);
                afterNanos+=System.nanoTime()-start;afterBytes+=BenchmarkAllocations.currentThreadBytes()-allocated;afterStates+=world.stateReads;afterTiles+=world.tileReads;
            }
            require(beforeStates>0&&beforeTiles>0&&afterStates==0&&afterTiles==0,"stable frames still rediscover light topology");
            System.out.println("light-group stable lights=1024 components=32 ticks=200 before/after stateReads="+beforeStates+"/"+afterStates+" tileReads="+beforeTiles+"/"+afterTiles+" ns="+beforeNanos+"/"+afterNanos+" allocatedBytes="+beforeBytes+"/"+afterBytes);
            visibilityCosts(world,tiles,state);
            TEAnimatedScreenSelector.LightGroup distant=group(tiles.get(tiles.size()-1),state);
            for(int kind=0;kind<5;kind++) {
                for(int turn=0;turn<8;turn++) {
                    if(kind==0)world.notifyBlockUpdate(new BlockPos(4096,64,4096),state,state,2);
                    if(kind==1)world.markBlockRangeForRenderUpdate(10,65,8,8,64,8);
                    if(kind==2)tiles.get(0).setOn(!tiles.get(0).isManualOn());
                    if(kind==3)foreign.setOn(!foreign.isManualOn());
                    if(kind==4){group(foreign,state);world.time++;other.time++;}
                    world.resetReads();for(TileEntityProgrammableLight tile:tiles)group(tile,state);
                    if(kind==0||kind==3||kind==4)require(world.stateReads==0&&world.tileReads==0,"unrelated edit/world switch rebuilt lights");
                    else require(world.stateReads>0&&world.stateReads<beforeStates/200/4,"local edit did not isolate connected component");
                    require(group(tiles.get(tiles.size()-1),state)==distant,"distant light component was discarded");
                    ReferenceLightGroups.clear();for(TileEntityProgrammableLight tile:tiles)same(ReferenceLightGroups.lightGroup(tile,state),group(tile,state),tile.getPos());
                }
            }
            LightGroupCache.clear();require(!LightGroupCache.contains(world)&&!LightGroupCache.contains(other),"resource reload retained worlds");
            group(tiles.get(0),state);close(world);require(!LightGroupCache.contains(world),"world unload retained light cache");
            System.out.println("light-group locality PASS tick/world-switch/distant-edits preserve caches; local power/range updates isolate component; reload/unload release");
        }finally{close(world);close(other);}
    }

    private static void boundaryAndLifecycle() {
        World world=new World();IBlockState state=light.getDefaultState();BlockPos pos=new BlockPos(8,80,8);
        try {
            TileEntityProgrammableLight root=put(world,pos,state,true,true,false),last=put(world,pos.east(2),state,true,true,false);
            require(group(root,state).columns==1,"bridge fixture");TileEntityProgrammableLight bridge=put(world,pos.east(),state,true,true,false);
            world.tileLoadGuard=true;bridge.onLoad();world.tileLoadGuard=false;
            require(group(root,state).columns==3,"local-only onLoad omitted rejected bridge");parity(root,state);faces(root,EnumFacing.NORTH);
            bridge.onChunkUnload();require(group(root,state).columns==1,"unloading tile stayed eligible");
            bridge.onLoad();require(group(root,state).columns==3,"reloaded tile failed to rejoin");
            NBTTagCompound saved=bridge.writeToNBT(new NBTTagCompound());saved.setBoolean("LightJoin",false);bridge.readFromNBT(saved);
            require(group(root,state).columns==1,"NBT read did not split group");
            bridge.configure(0,15,true,0);require(group(root,state).columns==3,"settings did not rejoin group");
            world.tiles.remove(bridge.getPos());world.states.remove(bridge.getPos());bridge.invalidate();require(group(root,state).columns==1,"removed bridge remained joined");faces(root,EnumFacing.NORTH);
            TEAnimatedScreenSelector.LightGroup old=group(last,state);world.markBlockRangeForRenderUpdate(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE);
            require(group(last,state)!=old,"reversed extreme invalidation missed layout");
        }finally{world.tileLoadGuard=false;close(world);}
        World edge=new World();pos=new BlockPos(15,80,8);
        try {
            TileEntityProgrammableLight root=put(edge,pos,state,true,true,false);put(edge,pos.east(),state,true,true,false);edge.chunkLimit=true;
            require(group(root,state).columns==1,"unloaded boundary fixture");faces(root,EnumFacing.NORTH);edge.chunkLimit=false;
            net.minecraft.world.chunk.Chunk chunk=edge.getChunkFromChunkCoords(1,0);DiagonalWallMeshCache.Events events=new DiagonalWallMeshCache.Events();
            events.chunkLoaded(new net.minecraftforge.event.world.ChunkEvent.Load(chunk));require(group(root,state).columns==2,"chunk load omitted rejected boundary");faces(root,EnumFacing.NORTH);
            edge.chunkLimit=true;events.chunkUnloaded(new net.minecraftforge.event.world.ChunkEvent.Unload(chunk));require(group(root,state).columns==1,"chunk unload kept joined layout");faces(root,EnumFacing.NORTH);
        }finally{close(edge);}
        System.out.println("light-group lifecycle PASS bridge add/remove; unloaded candidate; local-only onLoad; unload/reload/settings/read; chunk load/unload; extreme ranges");
    }

    private static void carried() {
        World parent=new World();IBlockState state=light.getDefaultState();BlockPos pos=new BlockPos(8,80,8);
        TileEntityProgrammableLight root=put(parent,pos,state,true,true,false),neighbor=put(parent,pos.east(),state,true,true,false);
        List<VehicleStructure.Cell> cells=Arrays.asList(new VehicleStructure.Cell(pos,state,null),new VehicleStructure.Cell(pos.east(),state,null));
        VehicleWorld view=new VehicleWorld(parent,VehicleStructure.display(cells));root.setWorld(view);neighbor.setWorld(view);view.tiles.put(pos,root);view.tiles.put(pos.east(),neighbor);
        try {
            TEAnimatedScreenSelector.LightGroup initial=group(root,state);require(initial.columns==2,"carried pair fixture");
            VehicleStructure saved=view.structure;int revision=view.tileRevision;
            neighbor.setOn(false);require(group(root,state).columns==1,"carried power edit did not invalidate");
            require(view.structure==saved&&view.tileRevision==revision,"power fixture relied on snapshot rebuild");
            neighbor.setOn(true);require(group(root,state).columns==2,"carried power restore did not rejoin");
            TEAnimatedScreenSelector.LightGroup before=group(root,state);NBTTagCompound tag=neighbor.writeToNBT(new NBTTagCompound());neighbor.readFromNBT(tag);require(group(root,state)!=before,"carried NBT retained prior layout");
            before=group(root,state);view.tileRevision++;require(group(root,state)!=before,"vehicle tile replacement did not invalidate");
            before=group(root,state);view.structure=VehicleStructure.display(cells);require(group(root,state)!=before,"vehicle structure replacement did not invalidate");
            before=group(root,state);view.origin=new BlockPos(1000,80,1000);require(group(root,state)==before,"moving vehicle unnecessarily invalidated local topology");
            System.out.println("light-group carried PASS local settings/NBT, tile/structure replacement, translation reuse");
        }finally{LightGroupCache.unload(view);close(parent);}
    }

    private static void capacity() {
        IBlockState state=light.getDefaultState();World world=new World();List<TileEntityProgrammableLight> stable=new ArrayList<>(),overflow=new ArrayList<>();
        try {
            for(int i=0;i<LightGroupCache.MAX_POSITIONS;i++)stable.add(put(world,new BlockPos(8+i*8,80,8),state,true,true,false));
            for(int i=0;i<64;i++)overflow.add(put(world,new BlockPos(8+i,80,64),state,true,true,false));
            for(TileEntityProgrammableLight tile:stable){group(tile,state);faces(tile,EnumFacing.NORTH);}
            for(TileEntityProgrammableLight tile:overflow)faces(tile,EnumFacing.NORTH);
            require(LightGroupCache.faceEntries(world)==LightGroupCache.MAX_POSITIONS,"visibility cache exceeded stable admission budget");
            TEAnimatedScreenSelector.LightGroup first=group(stable.get(0),state),extra=group(overflow.get(0),state);
            require(LightGroupCache.entries(world)==8192+64,"bounded overflow admission");
            for(int pass=0;pass<4;pass++){world.resetReads();for(TileEntityProgrammableLight tile:overflow)group(tile,state);require(world.stateReads==0&&world.tileReads==0,"overflow member rescanned whole component");}
            world.notifyBlockUpdate(overflow.get(0).getPos(),state,state,2);require(LightGroupCache.entries(world)==8192&&group(stable.get(0),state)==first,"overflow change flushed admitted components");
            require(group(overflow.get(0),state)!=extra,"overflow dependency not invalidated");
            require(LightGroupCache.entries(world)<=LightGroupCache.MAX_POSITIONS+LoadedPlaneConnections.LIMIT,"light entry budget exceeded");
        }finally{close(world);}
        World capped=new World();List<TileEntityProgrammableLight> row=new ArrayList<>();
        try {
            for(int i=0;i<LoadedPlaneConnections.LIMIT+16;i++)row.add(put(capped,new BlockPos(i,80,8),state,true,true,false));
            require(group(row.get(0),state).columns==1,"scan cap must keep previous single-light fallback");parity(row.get(0),state);
            TileEntityProgrammableLight removed=row.get(2000);capped.tiles.remove(removed.getPos());capped.states.remove(removed.getPos());removed.invalidate();
            require(group(row.get(0),state).columns==2000,"capped traversal lost inspected dependencies");parity(row.get(0),state);
        }finally{close(capped);}
        System.out.println("light-group capacity PASS 8192 admitted plus bounded overflow; unchanged 4096 scan cap; capped boundary split");
    }
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register();
        com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy(){@Override public void lightAppearanceChanged(TileEntityProgrammableLight tile){LightGroupCache.changed(tile);}};
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableLight.class,new net.minecraft.util.ResourceLocation("minecraft:light_cache_checks"));
        light=new BlockProgrammableLight();frame=new BlockProgrammableLightFrame();slab=new BlockProgrammableLightSlab();
        geometry();visibilityMatrix();stableAndEdits();boundaryAndLifecycle();carried();capacity();System.out.println("light-group-cache PASS");
    }
    private LightGroupCacheChecks() { }
}
