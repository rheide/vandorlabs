package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Conservative observed dependencies, independent components and edit-heavy query costs. */
public final class PortholeLocalityChecks {
    private static BlockProgrammableWall flat,diagonal;
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
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static TileEntityAnimatedScreenSelector put(World world,BlockPos pos,IBlockState state,int mode,int shape){
        TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setJoinPortholes(true);tile.setPortholeShape(shape);tile.setDiagonalGeometry(mode,0);tile.setWorld(world);tile.setPos(pos);world.states.put(pos,state);world.tiles.put(pos,tile);return tile;
    }
    private static TEAnimatedScreenSelector.PortholeGroup group(TileEntityAnimatedScreenSelector tile,IBlockState state){return TEAnimatedScreenSelector.portholeGroup(tile,state);}
    private static void same(TEAnimatedScreenSelector.PortholeGroup a,TEAnimatedScreenSelector.PortholeGroup b){need(a.minAxis==b.minAxis&&a.minY==b.minY&&a.columns==b.columns&&a.rows==b.rows&&a.right==b.right&&a.up==b.up&&a.shape==b.shape&&a.extraBorder==b.extraBorder,"component descriptor changed");}
    private static void coldParity(TileEntityAnimatedScreenSelector tile,IBlockState state){ReferencePortholeGroups.clear();same(ReferencePortholeGroups.portholeGroup(tile,state),group(tile,state));}
    private static void close(World world){PortholeGroupCache.unload(world);ReferencePortholeGroups.clear();}

    /** First patch's invalidation policy, using the unchanged current discovery.
     * This fixture stays below its 8,192-position budget, so no admission differs. */
    private static final class BroadCache extends WorldRenderListener {
        final Map<BlockPos,TEAnimatedScreenSelector.PortholeGroup> groups=new HashMap<>();
        long revision=Long.MIN_VALUE;
        static final Method DISCOVER;
        static {try{DISCOVER=TEAnimatedScreenSelector.class.getDeclaredMethod("discoverPortholes",TileEntityAnimatedScreenSelector.class,IBlockState.class,PortholeGroupCache.Dependencies.class);DISCOVER.setAccessible(true);}catch(Exception error){throw new ExceptionInInitializerError(error);}}
        @SuppressWarnings("unchecked") TEAnimatedScreenSelector.PortholeGroup get(TileEntityAnimatedScreenSelector tile,IBlockState state){
            long current=TileEntityAnimatedScreenSelector.getPortholeRevision();if(current!=revision){groups.clear();revision=current;}
            TEAnimatedScreenSelector.PortholeGroup result=groups.get(tile.getPos());if(result!=null)return result;
            try{groups.putAll((Map<BlockPos,TEAnimatedScreenSelector.PortholeGroup>)DISCOVER.invoke(null,tile,state,new PortholeGroupCache.Dependencies()));}catch(Exception error){throw new AssertionError(error);}return groups.get(tile.getPos());
        }
        @Override public void notifyBlockUpdate(net.minecraft.world.World world,BlockPos pos,IBlockState before,IBlockState after,int flags){groups.clear();}
        @Override public void markBlockRangeForRenderUpdate(int x0,int y0,int z0,int x1,int y1,int z1){groups.clear();}
    }
    private static void edits(){
        World world=new World(),other=new World();BroadCache before=new BroadCache();world.addEventListener(before);IBlockState state=flat.getDefaultState();List<TileEntityAnimatedScreenSelector> panes=new ArrayList<>();
        try{
            for(int component=0;component<32;component++)for(int y=0;y<4;y++)for(int x=0;x<8;x++)panes.add(put(world,new BlockPos(8+component*64+x,64+y,8),state,0,0));
            TileEntityAnimatedScreenSelector foreign=put(other,new BlockPos(8,64,8),state,0,0);
            for(TileEntityAnimatedScreenSelector tile:panes){before.get(tile,state);group(tile,state);}
            TEAnimatedScreenSelector.PortholeGroup distant=group(panes.get(panes.size()-1),state);
            for(int kind=0;kind<7;kind++){
                long oldStates=0,oldTiles=0,newStates=0,newTiles=0;
                for(int turn=0;turn<16;turn++){
                    if(kind==0)world.notifyBlockUpdate(new BlockPos(4096,64,4096),Blocks.AIR.getDefaultState(),Blocks.AIR.getDefaultState(),3);
                    if(kind==1)world.markBlockRangeForRenderUpdate(9,65,8,8,64,8);
                    if(kind==2)panes.get(0).setPortholeShape((panes.get(0).getPortholeShape()+1)%4);
                    if(kind==3)foreign.setPortholeShape((foreign.getPortholeShape()+1)%4);
                    if(kind==4||kind==5){int chunk=kind==4?256:0;new DiagonalWallMeshCache.Events().chunkLoaded(new net.minecraftforge.event.world.ChunkEvent.Load(world.getChunkFromChunkCoords(chunk,chunk)));before.groups.clear();}
                    if(kind==6)foreign.invalidate();
                    world.resetReads();for(TileEntityAnimatedScreenSelector tile:panes)before.get(tile,state);oldStates+=world.stateReads;oldTiles+=world.tileReads;
                    world.resetReads();for(TileEntityAnimatedScreenSelector tile:panes)same(before.get(tile,state),group(tile,state));newStates+=world.stateReads;newTiles+=world.tileReads;
                    need(group(panes.get(panes.size()-1),state)==distant,"unrelated component was discarded");
                }
                need(oldStates>0&&oldTiles>0,"broad reference did not rebuild");
                if(kind==1||kind==2||kind==5)need(newStates>0&&newStates<oldStates/4&&newTiles>0&&newTiles<oldTiles/4,"local edit did not isolate its component");
                else need(newStates==0&&newTiles==0,"unrelated edit rebuilt portholes");
                System.out.println("porthole-locality edit panes=1024 components=32 updates=16 kind="+kind+" old/new stateReads="+oldStates+"/"+newStates+" tileReads="+oldTiles+"/"+newTiles);
            }
        }finally{world.removeEventListener(before);close(world);close(other);}
    }
    private static int observedDependencies(World world,TileEntityAnimatedScreenSelector root,IBlockState state){
        world.tracking=true;world.observed.clear();TEAnimatedScreenSelector.PortholeGroup current;
        try{current=group(root,state);}finally{world.tracking=false;}
        List<BlockPos> inspected=new ArrayList<>(world.observed);need(!inspected.isEmpty(),"no cold discovery reads captured");
        for(BlockPos pos:inspected){
            PortholeGroupCache.invalidate(world,pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ());
            TEAnimatedScreenSelector.PortholeGroup next=group(root,state);need(next!=current,"read/loaded-check dependency omitted at "+pos);same(current,next);current=next;
        }
        return inspected.size();
    }
    private static void dependencies(){
        int scenes=0,reads=0;
        BlockProgrammableWall full=new BlockProgrammablePortholeBlock();
        for(BlockProgrammableWall block:new BlockProgrammableWall[]{flat,full,diagonal})for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})
            for(int mode=0;mode<(block==diagonal?3:1);mode++)for(int shape=0;shape<4;shape++)for(int holes=0;holes<3;holes++){
                World world=new World();IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);BlockPos pos=new BlockPos(8,80,8);TileEntityAnimatedScreenSelector root=null;EnumFacing right=facing.rotateY(),up=mode==2?facing.getOpposite():EnumFacing.UP;
                try{
                    for(int y=0;y<3;y++)for(int x=0;x<4;x++){if(holes==1&&x==1&&y==1||holes==2&&x+y>3)continue;TileEntityAnimatedScreenSelector tile=put(world,pos.offset(right,x).offset(up,y),state,mode,shape);if(root==null)root=tile;}
                    reads+=observedDependencies(world,root,state);coldParity(root,state);scenes++;
                }finally{close(world);}
            }
        // Automatic compound corners inspect perpendicular flat contacts and
        // straight diagonal neighbours outside the admitted porthole members.
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(int mask=0;mask<16;mask++){
            World world=new World();BlockPos pos=new BlockPos(8,80,8);IBlockState state=diagonal.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);
            try{
                TileEntityAnimatedScreenSelector root=put(world,pos,state,1,0);put(world,pos.offset(facing.rotateY()),state,1,0);
                if((mask&1)!=0)put(world,pos.offset(facing),flat.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing.rotateY()),0,0);
                if((mask&2)!=0)put(world,pos.offset(facing.getOpposite()),flat.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing.rotateYCCW()),0,0);
                if((mask&4)!=0)put(world,pos.offset(facing.rotateYCCW()),state,1,0);
                if((mask&8)!=0)put(world,pos.offset(facing.rotateY()).offset(facing),diagonal.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing.rotateY()).withProperty(BlockProgrammableWall.INVERTED,inverted),1,0);
                reads+=observedDependencies(world,root,state);coldParity(root,state);scenes++;
            }finally{close(world);}
        }
        System.out.println("porthole-locality dependencies PASS scenes="+scenes+" observed-block/tile/loaded positions="+reads+" thin/full/diagonal four shapes,holes,allfacings,inversion,compound flat/slope contacts");
    }
    private static void boundaries(){
        IBlockState state=flat.getDefaultState();World world=new World();BlockPos pos=new BlockPos(8,80,8);
        try{
            TileEntityAnimatedScreenSelector root=put(world,pos,state,0,0),last=put(world,pos.east(2),state,0,0),far=put(world,pos.east(64),state,0,0);
            TEAnimatedScreenSelector.PortholeGroup other=group(far,state);need(group(root,state).columns==1,"hole fixture");TileEntityAnimatedScreenSelector bridge=put(world,pos.east(),state,0,0);world.tileLoadGuard=true;bridge.onLoad();world.tileLoadGuard=false;
            need(group(root,state).columns==3&&group(far,state)==other,"rejected bridge dependency missed or unrelated group cleared");coldParity(root,state);
            world.tiles.remove(bridge.getPos());world.states.remove(bridge.getPos());bridge.invalidate();need(group(root,state).columns==1,"bridge removal retained joined aperture");coldParity(root,state);
            NBTTagCompound saved=last.getUpdateTag();saved.setBoolean("JoinPortholes",false);last.readFromNBT(saved);need(group(far,state)==other,"distant same-world NBT discarded unrelated component");
            TEAnimatedScreenSelector.PortholeGroup prior=group(root,state);world.markBlockRangeForRenderUpdate(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE);need(group(root,state)!=prior,"extreme reversed range missed component");
        }finally{world.tileLoadGuard=false;close(world);}
        World edge=new World();pos=new BlockPos(15,80,8);
        try{
            TileEntityAnimatedScreenSelector root=put(edge,pos,state,0,0);put(edge,pos.east(),state,0,0);edge.chunkLimit=true;
            observedDependencies(edge,root,state);need(group(root,state).columns==1,"unloaded rejected candidate fixture");edge.chunkLimit=false;
            net.minecraft.world.chunk.Chunk chunk=edge.getChunkFromChunkCoords(1,0);DiagonalWallMeshCache.Events events=new DiagonalWallMeshCache.Events();events.chunkLoaded(new net.minecraftforge.event.world.ChunkEvent.Load(chunk));need(group(root,state).columns==2,"loading boundary did not merge");edge.chunkLimit=true;events.chunkUnloaded(new net.minecraftforge.event.world.ChunkEvent.Unload(chunk));need(group(root,state).columns==1,"unloading boundary did not split");
        }finally{close(edge);}
        System.out.println("porthole-locality boundary PASS rejected bridge add/remove,local lifecycle/NBT,unloaded candidate/chunk load+unload,extreme reversed range");
    }
    private static void capacity(){
        IBlockState state=flat.getDefaultState();World world=new World();List<TileEntityAnimatedScreenSelector> stable=new ArrayList<>(),over=new ArrayList<>();
        try{
            for(int i=0;i<PortholeGroupCache.MAX_POSITIONS;i++)stable.add(put(world,new BlockPos(8+i*8,80,8),state,0,0));
            for(int i=0;i<64;i++)over.add(put(world,new BlockPos(8+i,80,64),state,0,0));
            for(TileEntityAnimatedScreenSelector tile:stable)group(tile,state);TEAnimatedScreenSelector.PortholeGroup first=group(stable.get(0),state),overflow=group(over.get(0),state);
            need(PortholeGroupCache.entries(world)==8192+64,"overflow budget fixture");world.notifyBlockUpdate(over.get(0).getPos(),state,state,3);need(PortholeGroupCache.entries(world)==8192&&group(stable.get(0),state)==first,"overflow edit evicted primary components");
            need(group(over.get(0),state)!=overflow,"overflow dependency not invalidated");
            for(int turn=0;turn<4;turn++){world.resetReads();for(TileEntityAnimatedScreenSelector tile:over)group(tile,state);need(world.stateReads==0&&world.tileReads==0,"overflow redraw rescanned component");}
            world.notifyBlockUpdate(stable.get(0).getPos(),state,state,3);need(PortholeGroupCache.entries(world)==8191+64,"single primary edit evicted unrelated entries");
            group(stable.get(0),state);need(PortholeGroupCache.entries(world)<=12288,"retained entry cap exceeded");
        }finally{close(world);}
        World limit=new World();List<TileEntityAnimatedScreenSelector> row=new ArrayList<>();
        try{
            for(int x=0;x<LoadedPlaneConnections.LIMIT+16;x++)row.add(put(limit,new BlockPos(x,80,8),state,0,0));
            TEAnimatedScreenSelector.PortholeGroup first=group(row.get(0),state);need(first.columns==1&&first.rows==1,"scan cap fallback");group(row.get(row.size()-1),state);
            need(PortholeGroupCache.entries(limit)<=12288,"overlapping capped components exceeded budget");
            BlockPos changed=row.get(2000).getPos();limit.states.remove(changed);limit.tiles.remove(changed);limit.notifyBlockUpdate(changed,state,Blocks.AIR.getDefaultState(),3);coldParity(row.get(0),state);coldParity(row.get(row.size()-1),state);
        }finally{close(limit);}
        System.out.println("porthole-locality capacity PASS 8192 admitted+64overflow,independent edit retention,4096 scan cap/overlap and bridge split");
    }
    private static void carriedFlatContact(){
        World parent=new World();BlockPos pos=new BlockPos(8,80,8);IBlockState state=diagonal.getDefaultState().withProperty(BlockProgrammableWall.FACING,EnumFacing.NORTH);
        TileEntityAnimatedScreenSelector root=put(parent,pos,state,1,0);put(parent,pos.east(),state,1,0);put(parent,pos.south(),flat.getDefaultState().withProperty(BlockProgrammableWall.FACING,EnumFacing.WEST),0,0);
        BlockProgrammableGlass glass=new BlockProgrammableGlass("porthole_locality_glass");TileEntityProgrammableGlass contact=new TileEntityProgrammableGlass();contact.setWorld(parent);contact.setPos(pos.north());parent.tiles.put(pos.north(),contact);parent.states.put(pos.north(),glass.getDefaultState().withProperty(BlockGlassWall.ROTATED,true));
        List<VehicleStructure.Cell> cells=new ArrayList<>();for(Map.Entry<BlockPos,IBlockState> entry:parent.states.entrySet())cells.add(new VehicleStructure.Cell(entry.getKey(),entry.getValue(),null));VehicleWorld view=new VehicleWorld(parent,VehicleStructure.display(cells));
        for(Map.Entry<BlockPos,TileEntity> entry:parent.tiles.entrySet()){entry.getValue().setWorld(view);view.tiles.put(entry.getKey(),entry.getValue());}
        try{
            VehicleStructure original=view.structure;int revision=view.tileRevision;need(group(root,state).columns==1,"carried compound-node fixture");TEAnimatedScreenSelector.PortholeGroup before=group(root,state);
            contact.setJoin(false);need(group(root,state).columns==2&&group(root,state)!=before,"carried flat-contact edit retained porthole component");coldParity(root,state);
            NBTTagCompound tag=contact.getUpdateTag();tag.setBoolean("GlassJoin",true);contact.readFromNBT(tag);need(group(root,state).columns==1,"carried flat-contact NBT retained joined aperture");coldParity(root,state);
            need(view.structure==original&&view.tileRevision==revision,"fixture accidentally relied on snapshot invalidation");
            System.out.println("porthole-locality carried contact PASS native Glass join/NBT changes invalidate compound apertures before snapshot replacement");
        }finally{PortholeGroupCache.unload(view);close(parent);}
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy(){@Override public void portholeGeometryChanged(TileEntityAnimatedScreenSelector tile){TEAnimatedScreenSelector.portholeChanged(tile);}@Override public void glassFrameAppearanceChanged(TileEntityProgrammableGlass tile){TEAnimatedScreenSelector.portholeChanged(tile);}};
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class,new net.minecraft.util.ResourceLocation("minecraft","porthole_locality_checks"));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableGlass.class,new net.minecraft.util.ResourceLocation("minecraft","porthole_locality_glass_checks"));
        flat=new BlockProgrammableWall("porthole_locality_flat",BlockProgrammableWall.Shape.PORTHOLE);diagonal=new BlockProgrammableWall("porthole_locality_diagonal",BlockProgrammableWall.Shape.DIAGONAL_PORTHOLE);
        edits();dependencies();boundaries();capacity();carriedFlatContact();System.out.println("porthole-locality PASS");
    }
    private PortholeLocalityChecks(){}
}
