package com.vandorlabs.client;

import com.vandorlabs.CommonProxy;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Dependency invalidation and update-storm costs against the delivered 00e15269 cache. */
public final class GlassTopologyInvalidationChecks {
    private static BlockProgrammableGlass glass;
    private static Object sink;
    private static final class World extends NonRenderingChecks.MemoryWorld {
        int tileReads;
        World(){this(true);}
        World(boolean remote){super(remote);}
        @Override public TileEntity getTileEntity(BlockPos pos){tileReads++;return super.getTileEntity(pos);}
        void resetReads(){stateReads=tileReads=0;}
    }
    private static void need(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    private static IBlockState state(EnumFacing facing){return glass.getDefaultState().withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X).withProperty(BlockGlassWall.DEPTH,0);}
    private static TileEntityProgrammableGlass tile(net.minecraft.world.World world,BlockPos pos,EnumFacing facing){
        TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();NBTTagCompound tag=new NBTTagCompound();
        tag.setInteger("GlassPanelFacing",facing.getHorizontalIndex());tag.setBoolean("GlassJoin",true);
        tile.readFromNBT(tag);tile.setWorld(world);tile.setPos(pos);return tile;
    }
    private static TileEntityProgrammableGlass put(World world,BlockPos pos,EnumFacing facing){
        TileEntityProgrammableGlass tile=tile(world,pos,facing);world.states.put(pos,state(facing));world.tiles.put(pos,tile);return tile;
    }
    private static void same(BlockProgrammableWall.FlatCorner expected,BlockProgrammableWall.FlatCorner actual){
        need((expected==null)==(actual==null),"corner presence");if(expected==null)return;
        need(Objects.equals(expected.frontArm,actual.frontArm)&&Objects.equals(expected.backArm,actual.backArm)
                &&Objects.equals(expected.frontRight,actual.frontRight)&&Objects.equals(expected.backRight,actual.backRight)
                &&expected.straightLeft==actual.straightLeft&&expected.straightRight==actual.straightRight
                &&expected.left()==actual.left()&&expected.right()==actual.right(),"corner geometry");
    }
    private static BlockProgrammableWall.FlatCorner check(TileEntityProgrammableGlass tile,IBlockState state){
        BlockProgrammableWall.FlatCorner expected=PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos());
        BlockProgrammableWall.FlatCorner actual=GlassSurfaceGroups.flatCorner(tile,state);same(expected,actual);return actual;
    }
    @SuppressWarnings("unchecked") private static Map<net.minecraft.world.World,?> caches(){
        try{Field field=GlassSurfaceGroups.class.getDeclaredField("CACHES");field.setAccessible(true);return (Map<net.minecraft.world.World,?>)field.get(null);}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    @SuppressWarnings("unchecked") private static Map<BlockPos,?> entries(net.minecraft.world.World world){
        try{Object cache=caches().get(world);Field field=cache.getClass().getDeclaredField("corners");field.setAccessible(true);return (Map<BlockPos,?>)field.get(cache);}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    private static void localDependencies(){
        World world=new World();BlockPos changed=new BlockPos(-16,80,-16);List<TileEntityProgrammableGlass> panes=new ArrayList<>();
        try{
            for(int y=-1;y<=1;y++)for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++)panes.add(put(world,changed.add(x,y,z),EnumFacing.NORTH));
            for(TileEntityProgrammableGlass pane:panes)check(pane,state(EnumFacing.NORTH));
            Map<BlockPos,?> before=new HashMap<>(entries(world));long stamp=GlassSurfaceGroups.mutation();
            world.states.put(changed,state(EnumFacing.EAST));world.notifyBlockUpdate(changed,state(EnumFacing.NORTH),state(EnumFacing.EAST),3);
            need(GlassSurfaceGroups.mutation()!=stamp,"block event did not change capture token");
            for(TileEntityProgrammableGlass pane:panes){
                BlockPos pos=pane.getPos();int distance=Math.abs(pos.getX()-changed.getX())+Math.abs(pos.getZ()-changed.getZ());
                boolean affected=pos.getY()==changed.getY()&&distance<=1;
                need(affected?!entries(world).containsKey(pos):entries(world).get(pos)==before.get(pos),"point invalidation footprint "+pos);
                check(pane,world.states.get(pos));
            }
            // Reversed, small ranges and a huge range exercise both bounded paths.
            before=new HashMap<>(entries(world));world.markBlockRangeForRenderUpdate(-15,80,-15,-16,80,-16);
            for(TileEntityProgrammableGlass pane:panes){
                BlockPos pos=pane.getPos();boolean affected=pos.getY()==80&&pos.getX()>=-17&&pos.getX()<=-14&&pos.getZ()>=-17&&pos.getZ()<=-14;
                need(affected?!entries(world).containsKey(pos):entries(world).get(pos)==before.get(pos),"range invalidation footprint "+pos);
                check(pane,world.states.get(pos));
            }
            GlassSurfaceGroups.invalidate(world,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE);
            need(entries(world).isEmpty(),"large range did not clear affected bounded set");
            for(TileEntityProgrammableGlass pane:panes)check(pane,world.states.get(pane.getPos()));
            for(BlockPos key:entries(world).keySet())need(!(key instanceof BlockPos.MutableBlockPos),"mutable lookup cursor retained as a key");
        }finally{GlassSurfaceGroups.unload(world);}
    }
    private static void materialsAndLayouts(){
        World world=new World();BlockPos pos=new BlockPos(4,80,4);
        try{
            TileEntityProgrammableGlass root=put(world,pos,EnumFacing.NORTH),neighbor=put(world,pos.east(),EnumFacing.NORTH);
            check(root,state(EnumFacing.NORTH));Object corner=entries(world).get(pos);
            GlassSurfaceGroups.Layout joined=GlassSurfaceGroups.layout(root);need(joined.columns==2,"joined artwork fixture");
            need((GlassSurfaceGroups.frameMask(root)&2)==0,"joined frame fixture");long stamp=GlassSurfaceGroups.mutation();
            GlassMaterials.changed();need(GlassSurfaceGroups.mutation()==stamp,"material-only revision changed state token");
            need(GlassSurfaceGroups.layout(root)!=joined,"material revision retained layout");
            need(entries(world).get(pos)==corner,"layout revision discarded topology");
            world.resetReads();GlassSurfaceGroups.flatCorner(root,state(EnumFacing.NORTH));need(world.stateReads==0&&world.tileReads==0,"material revision rescanned corner");
            neighbor.setAppearance(2,true);need(GlassSurfaceGroups.layout(root).columns==1,"actual tint edit retained old artwork group");
            neighbor.frameAppearance.front=0;GlassMaterials.changed();need((GlassSurfaceGroups.frameMask(root)&2)!=0,"frame material edit retained contour join");
        }finally{GlassSurfaceGroups.unload(world);}
    }
    private static void cacheOnly(){
        World world=new World();BlockPos pos=new BlockPos(8,80,8);IBlockState state=state(EnumFacing.NORTH);
        try{
            TileEntityProgrammableGlass root=put(world,pos,EnumFacing.NORTH);world.resetReads();long stamp=GlassSurfaceGroups.mutation();
            need(!GlassSurfaceGroups.flatCached(root,state)&&!GlassSurfaceGroups.flatCached(root,glass,glass.getMetaFromState(state)),"cold topology was treated as proven flat");
            need(world.stateReads==0&&world.tileReads==0&&!caches().containsKey(world)&&GlassSurfaceGroups.mutation()==stamp,"cache-only miss discovered/admitted topology");
            need(check(root,state)==null&&GlassSurfaceGroups.flatCached(root,state)&&GlassSurfaceGroups.flatCached(root,glass,glass.getMetaFromState(state)),"warm flat proof missing");
            Object admitted=entries(world).get(pos);world.resetReads();GlassMaterials.changed();need(GlassSurfaceGroups.flatCached(root,state),"material-only change lost proof");
            need(world.stateReads==0&&world.tileReads==0&&entries(world).get(pos)==admitted,"warm proof queried world or replaced entry");
            TileEntityProgrammableGlass replacement=tile(world,pos,EnumFacing.NORTH);need(!GlassSurfaceGroups.flatCached(replacement,state)&&!GlassSurfaceGroups.flatCached(replacement,glass,glass.getMetaFromState(state)),"replacement owner reused proof");
            NBTTagCompound changed=root.writeToNBT(new NBTTagCompound());changed.setBoolean("GlassJoin",false);root.readFromNBT(changed);need(!GlassSurfaceGroups.flatCached(root,state)&&!GlassSurfaceGroups.flatCached(root,glass,glass.getMetaFromState(state)),"silent owner join change reused proof");
            changed.setBoolean("GlassJoin",true);changed.setInteger("GlassPanelFacing",EnumFacing.SOUTH.getHorizontalIndex());root.readFromNBT(changed);need(!GlassSurfaceGroups.flatCached(root,state)&&!GlassSurfaceGroups.flatCached(root,glass,glass.getMetaFromState(state)),"silent owner facing change reused proof");
            changed.setInteger("GlassPanelFacing",EnumFacing.NORTH.getHorizontalIndex());root.readFromNBT(changed);check(root,state);
            put(world,pos.north(),EnumFacing.EAST);world.notifyBlockUpdate(pos.north(),Blocks.AIR.getDefaultState(),state(EnumFacing.EAST),3);world.resetReads();
            need(!GlassSurfaceGroups.flatCached(root,state)&&world.stateReads==0&&world.tileReads==0,"expired proof rediscovered neighbours");
            need(check(root,state)!=null&&!GlassSurfaceGroups.flatCached(root,state)&&!GlassSurfaceGroups.flatCached(root,glass,glass.getMetaFromState(state)),"known corner admitted empty fast pass");
            world.tiles.remove(pos.north());world.states.remove(pos.north());world.markBlockRangeForRenderUpdate(pos,pos);world.resetReads();
            need(!GlassSurfaceGroups.flatCached(root,state)&&world.stateReads==0&&world.tileReads==0,"range-expired proof rediscovered neighbours");need(check(root,state)==null,"restored flat fixture");
            // Listed properties that metadata normalizes must cause only a safe
            // missed fast hit, never a false positive or per-query replacement.
            IBlockState raw=state.withProperty(BlockGlassWall.DEPTH,1).withProperty(BlockProgrammableGlass.SIZE,0);
            IBlockState canonical=glass.getStateFromMeta(glass.getMetaFromState(raw));need(raw!=canonical,"metadata normalization fixture");
            world.states.put(pos,raw);check(root,raw);admitted=entries(world).get(pos);world.resetReads();
            need(!GlassSurfaceGroups.flatCached(root,canonical)&&GlassSurfaceGroups.flatCached(root,raw),"metadata alias reused wrong state proof");
            need(world.stateReads==0&&world.tileReads==0&&entries(world).get(pos)==admitted,"metadata alias caused discovery/cache replacement");
            GlassSurfaceGroups.unload(world);world.resetReads();need(!GlassSurfaceGroups.flatCached(root,raw)&&world.stateReads==0&&world.tileReads==0&&!caches().containsKey(world),"unload miss recreated cache");
        }finally{GlassSurfaceGroups.unload(world);}

        World crowded=new World();TileEntityProgrammableGlass[] panes=new TileEntityProgrammableGlass[GlassSurfaceGroups.MAX_LAYOUTS+1];
        com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        try{
            for(int i=0;i<panes.length;i++){panes[i]=put(crowded,new BlockPos(i*4,80,8),EnumFacing.NORTH);GlassSurfaceGroups.flatCorner(panes[i],state);}
            for(int round=0;round<8;round++)for(TileEntityProgrammableGlass pane:panes)GlassSurfaceGroups.flatCached(pane,glass,pane.getBlockMetadata());
            crowded.resetReads();long bytes=meter.getThreadAllocatedBytes(thread);int accepted=0;
            for(int round=0;round<4;round++)for(TileEntityProgrammableGlass pane:panes)if(GlassSurfaceGroups.flatCached(pane,glass,pane.getBlockMetadata()))accepted++;
            bytes=meter.getThreadAllocatedBytes(thread)-bytes;
            need(accepted==4*GlassSurfaceGroups.MAX_LAYOUTS,"overflow query changed bounded admission");
            need(crowded.stateReads==0&&crowded.tileReads==0&&bytes==0,"overflow fast-routing query discovered/allocated "+bytes);
            need(entries(crowded).size()==GlassSurfaceGroups.MAX_LAYOUTS,"cache-only query exceeded/evicted admission");
            System.out.println("Glass cache-only eligibility panes="+panes.length+" calls="+(4*panes.length)+" stateReads=0 tileReads=0 allocated="+bytes);
        }finally{GlassSurfaceGroups.unload(crowded);}

        World parent=new World();VehicleWorld carried=new VehicleWorld(parent,VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(pos,state,null))));
        TileEntityProgrammableGlass root=tile(carried,pos,EnumFacing.NORTH);carried.tiles.put(pos,root);
        try{
            need(check(root,state)==null&&GlassSurfaceGroups.flatCached(root,state),"carried cache-only fixture");
            carried.tileRevision++;need(!GlassSurfaceGroups.flatCached(root,glass,glass.getMetaFromState(state))&&entries(carried).isEmpty(),"carried revision retained proof");check(root,state);
            carried.structure=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(pos,state,null)));
            need(!GlassSurfaceGroups.flatCached(root,glass,glass.getMetaFromState(state))&&entries(carried).isEmpty(),"same-revision snapshot retained proof");
        }finally{GlassSurfaceGroups.unload(carried);}
    }
    private static void metadataCase(World world,IBlockState state,EnumFacing facing,int shape){
        world.clear();GlassSurfaceGroups.invalidate(world);BlockPos pos=new BlockPos(8,80,8);
        TileEntityProgrammableGlass root=put(world,pos,facing);world.states.put(pos,state);
        EnumFacing effective=root.getPanelFacing(PanelJoins.facing(state));
        if(shape>0)put(world,pos.offset(effective),effective.rotateY());
        if(shape==2){put(world,pos.offset(effective.getOpposite()),effective.rotateY());put(world,pos.offset(effective.rotateY()),effective);}
        BlockProgrammableWall.FlatCorner expected=check(root,state);need((expected==null)==(shape==0),"metadata corner fixture");
        int metadata=glass.getMetaFromState(state);
        // The normalized state can drop SIZE=2 or connection flags, but neither
        // is a flat-corner dependency. Its actual geometry must remain equal.
        same(expected,PanelJoins.flatCorner(glass.getStateFromMeta(metadata),world,pos));world.resetReads();
        for(int query=0;query<16;query++)need(GlassSurfaceGroups.flatCached(root,glass,query)==(expected==null&&query==metadata),"metadata admitted stale plane or rejected valid proof");
        need(!GlassSurfaceGroups.flatCached(root,Blocks.GLASS,metadata),"foreign block reused metadata proof");
        need(world.stateReads==0&&world.tileReads==0,"metadata predicate queried owner/neighbours");
    }
    private static void metadataProofs(){
        World world=new World();int cases=0;
        try{
            // Cover the complete saved nibble, all legacy SIZE states and both
            // requested/normalized panel facings, including fallback metadata.
            for(int metadata=0;metadata<16;metadata++)for(int size=0;size<3;size++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int shape=0;shape<3;shape++){
                metadataCase(world,glass.getStateFromMeta(metadata).withProperty(BlockProgrammableGlass.SIZE,size),facing,shape);cases++;
            }
            net.minecraft.block.properties.PropertyBool[] flags={BlockGlassWall.TOP,BlockGlassWall.BOTTOM,BlockGlassWall.LEFT,BlockGlassWall.RIGHT,
                    BlockGlassWall.INNER_TL,BlockGlassWall.INNER_TR,BlockGlassWall.INNER_BL,BlockGlassWall.INNER_BR};
            for(int mask=0;mask<256;mask++)for(boolean rotated:new boolean[]{false,true})for(int depth=0;depth<3;depth++)for(int shape=0;shape<3;shape++){
                IBlockState state=glass.getDefaultState().withProperty(BlockGlassWall.ROTATED,rotated).withProperty(BlockGlassWall.DEPTH,depth);
                for(int bit=0;bit<flags.length;bit++)state=state.withProperty(flags[bit],(mask&(1<<bit))!=0);
                metadataCase(world,state,rotated?EnumFacing.EAST:EnumFacing.NORTH,shape);cases++;
            }
        }finally{GlassSurfaceGroups.unload(world);}
        System.out.println("Glass metadata proof PASS cases="+cases+" all saved nibbles/legacy sizes/facings/connection flags/flat-corner-T geometry and cache-only queries");
    }
    private static Set<?> queued(){
        try{Field field=GlassFrameChunks.class.getDeclaredField("QUEUED");field.setAccessible(true);return (Set<?>)field.get(null);}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    private static void clearQueue(){queued().clear();}
    private static void lifecycle(){
        World world=new World();BlockPos pos=new BlockPos(8,80,8),front=pos.north();CommonProxy previous=VandorLabs.proxy;
        VandorLabs.proxy=new CommonProxy(){@Override public void glassFrameAppearanceChanged(TileEntityProgrammableGlass tile){GlassFrameChunks.changed(tile);}};
        try{
            TileEntityProgrammableGlass root=put(world,pos,EnumFacing.NORTH),neighbor=put(world,front,EnumFacing.EAST),far=put(world,pos.add(64,0,0),EnumFacing.NORTH);
            need(check(root,state(EnumFacing.NORTH))!=null,"joined lifecycle fixture");check(far,state(EnumFacing.NORTH));Object distant=entries(world).get(far.getPos());
            NBTTagCompound edited=neighbor.writeToNBT(new NBTTagCompound());edited.setBoolean("GlassJoin",false);long stamp=GlassSurfaceGroups.mutation();
            world.tileLoadGuard=true;world.resetReads();neighbor.readFromNBT(edited);world.tileLoadGuard=false;
            need(world.stateReads==0&&world.tileReads==0,"NBT callback queried neighbours");need(GlassSurfaceGroups.mutation()!=stamp,"NBT callback missed capture token");
            need(check(root,state(EnumFacing.NORTH))==null,"neighbour NBT edit retained corner");need(entries(world).get(far.getPos())==distant,"nearby NBT edit retired distant pane");
            edited.setBoolean("GlassJoin",true);neighbor.readFromNBT(edited);need(check(root,state(EnumFacing.NORTH))!=null,"restored neighbour NBT kept negative corner");
            world.tileLoadGuard=true;world.resetReads();GlassFrameChunks.changed(neighbor);world.tileLoadGuard=false;
            need(world.stateReads==0&&world.tileReads==0,"deferred frame notification queried world");need(entries(world).get(far.getPos())==distant,"frame notification retired distant pane");
            // Portholes share the same adjacent topology but have their own setter/NBT route.
            IBlockState port=ModBlocks.PROGRAMMABLE_PORTHOLE_WALL.getDefaultState().withProperty(BlockProgrammableWall.FACING,EnumFacing.EAST).withProperty(BlockProgrammableWall.DEPTH,0);
            TileEntityAnimatedScreenSelector porthole=new TileEntityAnimatedScreenSelector();porthole.setWorld(world);porthole.setPos(front);world.states.put(front,port);world.tiles.put(front,porthole);
            world.notifyBlockUpdate(front,state(EnumFacing.EAST),port,3);need(check(root,state(EnumFacing.NORTH))==null,"unjoined porthole fixture");
            porthole.setJoinPortholes(true);need(check(root,state(EnumFacing.NORTH))!=null,"porthole setter missed dependency");
            NBTTagCompound portholeTag=porthole.writeToNBT(new NBTTagCompound());portholeTag.setBoolean("JoinPortholes",false);porthole.readFromNBT(portholeTag);
            need(check(root,state(EnumFacing.NORTH))==null,"porthole NBT range missed dependency");
            stamp=GlassSurfaceGroups.mutation();GlassSurfaceGroups.unload(world);need(GlassSurfaceGroups.mutation()!=stamp&&!caches().containsKey(world),"world unload retained cache/token");
            stamp=GlassSurfaceGroups.mutation();world.notifyBlockUpdate(pos,state(EnumFacing.NORTH),state(EnumFacing.NORTH),3);need(GlassSurfaceGroups.mutation()==stamp,"unloaded cache listener retained");
            GlassSurfaceGroups.flatCorner(root,state(EnumFacing.NORTH));need(GlassSurfaceGroups.mutation()!=stamp,"cache recreation reused capture token");
        }finally{world.tileLoadGuard=false;VandorLabs.proxy=previous;GlassSurfaceGroups.unload(world);clearQueue();}
    }
    private static void authoritativeCopies(){
        World server=new World(false);BlockPos pos=new BlockPos(8,80,8);IBlockState state=state(EnumFacing.NORTH);
        VehicleWorld carried=new VehicleWorld(server,VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(pos,state,null))));
        VehicleWorld nested=new VehicleWorld(carried,VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(pos,state,null))));
        need(carried.isRemote&&nested.isRemote&&!server.isRemote,"authoritative captured-view fixture");
        CommonProxy previous=VandorLabs.proxy;
        // Integrated-server code still sees the process-wide client proxy.
        VandorLabs.proxy=new CommonProxy(){@Override public void glassFrameAppearanceChanged(TileEntityProgrammableGlass tile){GlassFrameChunks.changed(tile);}};
        try{
            for(net.minecraft.world.World view:new net.minecraft.world.World[]{server,carried,nested}){
                TileEntityProgrammableGlass tile=tile(view,pos,EnumFacing.NORTH);NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());tag.setBoolean("GlassJoin",false);
                long mutation=GlassSurfaceGroups.mutation();int pending=queued().size();server.resetReads();server.tileLoadGuard=true;
                tile.readFromNBT(tag);tile.onLoad();GlassFrameChunks.changed(tile);tile.onChunkUnload();tile.invalidate();
                need(GlassSurfaceGroups.mutation()==mutation,"authoritative copy invalidated client topology");
                need(queued().size()==pending&&!caches().containsKey(view),"authoritative copy entered client publication/cache");
                need(server.stateReads==0&&server.tileReads==0,"physical-parent guard queried world data");
            }
        }finally{server.tileLoadGuard=false;VandorLabs.proxy=previous;}
        System.out.println("Glass authoritative copies PASS native/single/nested captured server views: zero world reads, unchanged client mutation and publication queue");
    }
    private static void chunksAndCarried(){
        World world=new World();BlockPos pos=new BlockPos(15,80,8),front=pos.east();
        try{
            TileEntityProgrammableGlass root=put(world,pos,EnumFacing.EAST),far=put(world,new BlockPos(-64,80,8),EnumFacing.NORTH);put(world,front,EnumFacing.NORTH);
            check(far,state(EnumFacing.NORTH));Object distant=entries(world).get(far.getPos());world.chunkLimit=true;need(check(root,state(EnumFacing.EAST))==null,"unloaded chunk fixture");
            world.chunkLimit=false;net.minecraft.world.chunk.Chunk chunk=world.getChunkFromChunkCoords(1,0);DiagonalWallMeshCache.Events events=new DiagonalWallMeshCache.Events();long stamp=GlassSurfaceGroups.mutation();
            events.chunkLoaded(new net.minecraftforge.event.world.ChunkEvent.Load(chunk));need(GlassSurfaceGroups.mutation()!=stamp,"chunk load token");
            need(check(root,state(EnumFacing.EAST))!=null,"chunk load retained missing corner");need(entries(world).get(far.getPos())==distant,"chunk load retired distant pane");
            world.chunkLimit=true;events.chunkUnloaded(new net.minecraftforge.event.world.ChunkEvent.Unload(chunk));need(check(root,state(EnumFacing.EAST))==null,"chunk unload retained corner");need(entries(world).get(far.getPos())==distant,"chunk unload retired distant pane");
        }finally{GlassSurfaceGroups.unload(world);}
        World parent=new World();IBlockState state=state(EnumFacing.EAST);
        VehicleWorld carried=new VehicleWorld(parent,VehicleStructure.display(Arrays.asList(new VehicleStructure.Cell(pos,state,null),new VehicleStructure.Cell(front,state(EnumFacing.NORTH),null))));
        TileEntityProgrammableGlass root=tile(carried,pos,EnumFacing.EAST),neighbor=tile(carried,front,EnumFacing.NORTH);carried.tiles.put(pos,root);carried.tiles.put(front,neighbor);
        CommonProxy previous=VandorLabs.proxy;VandorLabs.proxy=new CommonProxy(){@Override public void glassFrameAppearanceChanged(TileEntityProgrammableGlass tile){GlassFrameChunks.changed(tile);}};
        try{
            need(check(root,state)!=null,"carried corner fixture");NBTTagCompound tag=neighbor.writeToNBT(new NBTTagCompound());tag.setBoolean("GlassJoin",false);int revision=carried.tileRevision;long stamp=GlassSurfaceGroups.mutation();neighbor.readFromNBT(tag);
            need(carried.tileRevision==revision&&GlassSurfaceGroups.mutation()!=stamp,"carried settings callback/token");need(check(root,state)==null,"carried direct settings edit retained corner");
            tag.setBoolean("GlassJoin",true);neighbor.readFromNBT(tag);need(check(root,state)!=null,"carried corner restore");
            carried.structure=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(pos,state,null)));need(carried.tileRevision==revision,"snapshot test changed revision");
            need(check(root,state)==null,"same-revision structure replacement retained corner");
        }finally{VandorLabs.proxy=previous;GlassSurfaceGroups.unload(carried);clearQueue();}
    }
    private static final class Fixture {
        final World world=new World();final TileEntityProgrammableGlass[] panes=new TileEntityProgrammableGlass[1024];
        final IBlockState state=state(EnumFacing.NORTH),air=Blocks.AIR.getDefaultState();final BlockPos far=new BlockPos(8192,80,8192);
        final boolean reference;
        Fixture(boolean reference){this.reference=reference;for(int i=0;i<panes.length;i++)panes[i]=put(world,new BlockPos((i&31)*4,80,(i>>>5)*4),EnumFacing.NORTH);draw();}
        void draw(){for(TileEntityProgrammableGlass pane:panes)sink=reference?ReferenceGlassSurfaceGroups.flatCorner(pane,state):GlassSurfaceGroups.flatCorner(pane,state);}
        void event(int kind){
            if(kind==1)world.notifyBlockUpdate(far,air,air,3);
            else if(kind==2)world.markBlockRangeForRenderUpdate(8192,60,8192,8208,100,8208);
            else if(kind==3)GlassMaterials.changed();
            else if(kind==4)world.notifyBlockUpdate(panes[0].getPos(),state,state,3);
        }
        void close(){if(reference)ReferenceGlassSurfaceGroups.unload(world);else GlassSurfaceGroups.unload(world);}
    }
    private static final class Sample {long bytes,nanos;int states,tiles,eventStates,eventTiles;}
    private static Sample measure(Fixture fixture,int kind,int rounds,com.sun.management.ThreadMXBean meter,long thread){
        Sample sample=new Sample();fixture.world.resetReads();long bytes=meter.getThreadAllocatedBytes(thread),nanos=System.nanoTime();
        for(int round=0;round<rounds;round++){
            int states=fixture.world.stateReads,tiles=fixture.world.tileReads;fixture.event(kind);
            sample.eventStates+=fixture.world.stateReads-states;sample.eventTiles+=fixture.world.tileReads-tiles;
            fixture.draw();
        }
        sample.nanos=System.nanoTime()-nanos;sample.bytes=meter.getThreadAllocatedBytes(thread)-bytes;sample.states=fixture.world.stateReads;sample.tiles=fixture.world.tileReads;return sample;
    }
    private static void updateStorms(){
        com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        String[] labels={"quiet","far-block","far-range","screen-material-revision","one-local-edit"};final int rounds=64;
        for(int kind=0;kind<labels.length;kind++){
            Fixture previous=new Fixture(true),current=new Fixture(false);
            try{
                for(int warm=0;warm<32;warm++){previous.event(kind);previous.draw();current.event(kind);current.draw();}
                // Shared material revisions may have changed after the reference's last draw.
                previous.draw();current.draw();Sample before=measure(previous,kind,rounds,meter,thread),after=measure(current,kind,rounds,meter,thread);
                System.out.println("Glass topology invalidation "+labels[kind]+" panes1024 calls65536 delivered/current stateReads="+before.states+"/"+after.states+" tileReads="+before.tiles+"/"+after.tiles+" eventStateReads="+before.eventStates+"/"+after.eventStates+" eventTileReads="+before.eventTiles+"/"+after.eventTiles+" allocated="+before.bytes+"/"+after.bytes+" nanos="+before.nanos+"/"+after.nanos);
                if(kind==0)need(before.states==0&&before.tiles==0,"quiet delivered baseline was not warm");
                else need(before.states>0&&before.tiles>0&&before.bytes>0,"delivered update-storm regression not reproduced "+labels[kind]);
                if(kind<4)need(after.states==0&&after.tiles==0&&after.bytes<=1024,"unaffected panes rescanned or allocated "+labels[kind]);
                else{
                    // World's built-in PathWorldListener compares old/new glass
                    // collision boxes during notifyBlockUpdate. Each comparison
                    // performs its own topology reads even with no navigators.
                    // Keep that equal event cost in the reported totals, but
                    // compare the render discovery work separately.
                    need(after.eventStates==before.eventStates&&after.eventTiles==before.eventTiles,"local invalidation added notification reads");
                    need((after.states-after.eventStates)*current.panes.length==before.states-before.eventStates
                            &&(after.tiles-after.eventTiles)*current.panes.length==before.tiles-before.eventTiles,
                            "local edit rescanned unaffected pane set: stateReads="+before.states+"/"+after.states+" tileReads="+before.tiles+"/"+after.tiles);
                    need(after.bytes<before.bytes/128,"local edit allocation reduction");
                }
                need(entries(current.world).size()==current.panes.length,"storm changed admitted cache size");
            }finally{previous.close();current.close();}
        }
        long start=meter.getThreadAllocatedBytes(thread),value=0;for(int i=0;i<65536;i++)value^=GlassSurfaceGroups.mutation();long allocated=meter.getThreadAllocatedBytes(thread)-start;
        need(allocated==0,"same-flush mutation token allocated "+allocated);sink=value;
    }
    public static void main(String[] args)throws Exception{
        Field environment=net.minecraftforge.fml.relauncher.CoreModManager.class.getDeclaredField("deobfuscatedEnvironment");environment.setAccessible(true);
        boolean previous=environment.getBoolean(null);
        try{
            // JavaExec uses named Minecraft fields without Forge's launcher.
            // Match the publication fixture before initializing its real callback.
            environment.setBoolean(null,true);
            // Fail here with the original initializer cause, before cleanup can
            // obscure it with a secondary NoClassDefFoundError.
            Class.forName("com.vandorlabs.client.GlassFrameChunks");
            PropulsionPlacementLegacyOracle.initialize();for(net.minecraft.block.Block block:ModBlocks.BLOCKS)if(block instanceof BlockProgrammableGlass)glass=(BlockProgrammableGlass)block;need(glass!=null,"glass block");
            net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableGlass.class,new net.minecraft.util.ResourceLocation("vandorlabs","programmable_glass"));
            net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class,new net.minecraft.util.ResourceLocation("vandorlabs","animated_screen_selector"));
            localDependencies();materialsAndLayouts();cacheOnly();metadataProofs();lifecycle();authoritativeCopies();chunksAndCarried();updateStorms();
            System.out.println("Glass topology invalidation PASS delivered-baseline storms; local block/range/chunk dependencies; materials/layout separation; native/carried NBT and snapshot lifecycle; unload and allocation");
        }finally{environment.setBoolean(null,previous);}
    }
}
