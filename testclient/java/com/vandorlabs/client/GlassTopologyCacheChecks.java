package com.vandorlabs.client;

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

/** Compare real neighbour discovery with cached topology, including negative hits and bounded admission. */
public final class GlassTopologyCacheChecks {
    private static final BlockPos POS=new BlockPos(8,80,8);
    private static BlockProgrammableGlass glass;
    private static Object sink;
    private static final class World extends NonRenderingChecks.MemoryWorld {
        int tileReads;
        World(){super(true);}
        @Override public TileEntity getTileEntity(BlockPos pos){tileReads++;return super.getTileEntity(pos);}
        void resetReads(){stateReads=tileReads=0;}
    }
    private static void need(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static IBlockState state(EnumFacing facing,int depth){return glass.getDefaultState().withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X).withProperty(BlockGlassWall.DEPTH,depth);}
    private static TileEntityProgrammableGlass tile(net.minecraft.world.World world,BlockPos pos,EnumFacing facing,boolean joined){
        TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();NBTTagCompound data=new NBTTagCompound();
        data.setInteger("GlassPanelFacing",facing.getHorizontalIndex());data.setBoolean("GlassJoin",joined);
        tile.readFromNBT(data);tile.setWorld(world);tile.setPos(pos);return tile;
    }
    private static TileEntityProgrammableGlass put(World world,BlockPos pos,EnumFacing facing,int depth){
        TileEntityProgrammableGlass tile=tile(world,pos,facing,true);world.states.put(pos,state(facing,depth));world.tiles.put(pos,tile);return tile;
    }
    private static void same(BlockProgrammableWall.FlatCorner expected,BlockProgrammableWall.FlatCorner actual){
        need((expected==null)==(actual==null),"corner presence changed");if(expected==null)return;
        need(Objects.equals(expected.frontArm,actual.frontArm)&&Objects.equals(expected.backArm,actual.backArm)
                &&Objects.equals(expected.frontRight,actual.frontRight)&&Objects.equals(expected.backRight,actual.backRight)
                &&expected.straightLeft==actual.straightLeft&&expected.straightRight==actual.straightRight
                &&expected.left()==actual.left()&&expected.right()==actual.right(),"corner coordinates changed");
    }
    private static BlockProgrammableWall.FlatCorner check(TileEntityProgrammableGlass tile,IBlockState state){
        BlockProgrammableWall.FlatCorner expected=PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos());
        BlockProgrammableWall.FlatCorner actual=GlassSurfaceGroups.flatCorner(tile,state);same(expected,actual);return actual;
    }
    private static int matrix(){
        World world=new World();int cases=0;
        try{
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int depth=0;depth<3;depth++)for(int front=0;front<7;front++)for(int back=0;back<7;back++)for(int straight=0;straight<4;straight++){
                world.clear();GlassSurfaceGroups.invalidate(world);TileEntityProgrammableGlass tile=put(world,POS,facing,depth);
                for(int end=0;end<2;end++){int variant=end==0?front:back;if(variant>0)put(world,POS.offset(end==0?facing:facing.getOpposite()),variant<=3?facing.rotateY():facing.rotateYCCW(),(variant-1)%3);}
                for(int side=0;side<2;side++)if((straight&(1<<side))!=0)put(world,POS.offset(side==0?facing.rotateYCCW():facing.rotateY()),facing,depth);
                IBlockState state=world.states.get(POS);BlockProgrammableWall.FlatCorner expected=check(tile,state);world.resetReads();
                need(GlassSurfaceGroups.flatCorner(tile,state)==expected,"warm corner identity");
                need(world.stateReads==0&&world.tileReads==0,"warm topology queried neighbours");cases++;
            }
        }finally{GlassSurfaceGroups.unload(world);}
        return cases;
    }
    private static void invalidation(){
        World world=new World();IBlockState state=state(EnumFacing.NORTH,0);BlockPos front=POS.north();
        try{
            TileEntityProgrammableGlass root=put(world,POS,EnumFacing.NORTH,0);need(check(root,state)==null,"isolated pane");
            put(world,front,EnumFacing.EAST,0);world.notifyBlockUpdate(front,Blocks.AIR.getDefaultState(),world.states.get(front),3);
            need(check(root,state)!=null,"block insertion kept negative hit");
            world.tiles.put(front,tile(world,front,EnumFacing.EAST,false));world.notifyBlockUpdate(front,world.states.get(front),world.states.get(front),3);
            need(check(root,state)==null,"neighbour replacement kept old corner");
            world.tiles.put(front,tile(world,front,EnumFacing.EAST,true));world.markBlockRangeForRenderUpdate(front,front);
            need(check(root,state)!=null,"range update kept old corner");
            NBTTagCompound changed=root.writeToNBT(new NBTTagCompound());changed.setBoolean("GlassJoin",false);root.readFromNBT(changed);
            need(check(root,state)==null,"owner join edit without notification");
            changed.setBoolean("GlassJoin",true);changed.setInteger("GlassPanelFacing",EnumFacing.SOUTH.getHorizontalIndex());root.readFromNBT(changed);
            BlockProgrammableWall.FlatCorner reversed=check(root,state);need(reversed!=null&&reversed.frontArm==null&&reversed.backArm!=null,"owner facing edit without notification");
            IBlockState moved=state.withProperty(BlockGlassWall.DEPTH,2);world.states.put(POS,moved);check(root,moved);
            root=tile(world,POS,EnumFacing.SOUTH,true);world.tiles.put(POS,root);world.resetReads();GlassSurfaceGroups.flatCorner(root,moved);
            need(world.stateReads>0&&world.tileReads>0,"same-state owner replacement reused old entry");
            TileEntityProgrammableGlass edited=tile(world,front,EnumFacing.EAST,false);world.tiles.put(front,edited);
            GlassSurfaceGroups.changed(edited);need(check(root,moved)==null,"neighbour settings notification ignored");
            world.tiles.put(front,tile(world,front,EnumFacing.EAST,true));GlassSurfaceGroups.invalidate(world);
            BlockProgrammableWall.FlatCorner before=check(root,moved);GlassSurfaceGroups.invalidate(world);
            need(check(root,moved)!=before,"explicit reload invalidation retained entry");
            GlassSurfaceGroups.unload(world);need(!caches().containsKey(world),"world retained after unload");
            world.resetReads();GlassSurfaceGroups.flatCorner(root,moved);need(world.stateReads>0,"unloaded world reused topology");
        }finally{GlassSurfaceGroups.unload(world);}
    }
    private static void chunkBoundary(){
        World world=new World();BlockPos pos=new BlockPos(15,80,8),front=pos.east();
        try{
            TileEntityProgrammableGlass root=put(world,pos,EnumFacing.EAST,0);put(world,front,EnumFacing.NORTH,0);IBlockState state=world.states.get(pos);
            world.chunkLimit=true;need(check(root,state)==null,"unloaded neighbour queried");
            world.chunkLimit=false;net.minecraft.world.chunk.Chunk chunk=world.getChunkFromChunkCoords(1,0);
            DiagonalWallMeshCache.Events events=new DiagonalWallMeshCache.Events();events.chunkLoaded(new net.minecraftforge.event.world.ChunkEvent.Load(chunk));
            need(check(root,state)!=null,"chunk load kept negative hit");
            world.chunkLimit=true;events.chunkUnloaded(new net.minecraftforge.event.world.ChunkEvent.Unload(chunk));
            need(check(root,state)==null,"chunk unload retained neighbour");
        }finally{GlassSurfaceGroups.unload(world);}
    }
    private static void carried(){
        World parent=new World();IBlockState state=state(EnumFacing.NORTH,0);BlockPos front=POS.north();
        VehicleWorld world=new VehicleWorld(parent,VehicleStructure.display(Arrays.asList(new VehicleStructure.Cell(POS,state,null),new VehicleStructure.Cell(front,state(EnumFacing.EAST,0),null))));
        TileEntityProgrammableGlass root=tile(world,POS,EnumFacing.NORTH,true);world.tiles.put(POS,root);world.tiles.put(front,tile(world,front,EnumFacing.EAST,true));
        try{
            need(check(root,state)!=null,"carried corner fixture");world.tiles.put(front,tile(world,front,EnumFacing.EAST,false));world.tileRevision++;
            need(check(root,state)==null,"carried replacement revision ignored");
            world.tiles.put(front,tile(world,front,EnumFacing.EAST,true));world.tileRevision++;
            need(check(root,state)!=null,"carried restored corner missing");
        }finally{GlassSurfaceGroups.unload(world);}
    }
    @SuppressWarnings("unchecked") private static Map<net.minecraft.world.World,?> caches(){
        try{Field field=GlassSurfaceGroups.class.getDeclaredField("CACHES");field.setAccessible(true);return (Map<net.minecraft.world.World,?>)field.get(null);}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    private static int entries(World world){
        Object cache=caches().get(world);if(cache==null)return 0;
        try{Field field=cache.getClass().getDeclaredField("corners");field.setAccessible(true);return ((Map<?,?>)field.get(cache)).size();}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    private static void bound(){
        World world=new World();IBlockState state=state(EnumFacing.NORTH,0);TileEntityProgrammableGlass first=null;
        try{
            for(int i=0;i<=GlassSurfaceGroups.MAX_LAYOUTS;i++){
                TileEntityProgrammableGlass tile=put(world,new BlockPos(i*4,80,8),EnumFacing.NORTH,0);if(i==0)first=tile;
                GlassSurfaceGroups.flatCorner(tile,state);need(entries(world)<=GlassSurfaceGroups.MAX_LAYOUTS,"topology cache exceeded bound");
            }
            world.resetReads();GlassSurfaceGroups.flatCorner(first,state);need(world.stateReads==0&&world.tileReads==0,"overflow evicted the admitted working set");
        }finally{GlassSurfaceGroups.unload(world);}
    }
    /** The previous delivered patch's LRU policy, retained as a negative control. */
    private static final class PreviousCorner {
        final java.lang.ref.WeakReference<TileEntityProgrammableGlass> owner;
        final IBlockState state;final boolean joined;final EnumFacing facing;final BlockProgrammableWall.FlatCorner value;
        PreviousCorner(TileEntityProgrammableGlass tile,IBlockState state,EnumFacing facing){
            owner=new java.lang.ref.WeakReference<>(tile);this.state=state;joined=tile.isJoin();this.facing=facing;
            value=PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos());
        }
    }
    private static final class PreviousLru {
        final Map<BlockPos,PreviousCorner> entries=new LinkedHashMap<BlockPos,PreviousCorner>(128,.75F,true){
            @Override protected boolean removeEldestEntry(Map.Entry<BlockPos,PreviousCorner> entry){return size()>GlassSurfaceGroups.MAX_LAYOUTS;}
        };
        BlockProgrammableWall.FlatCorner get(TileEntityProgrammableGlass tile,IBlockState state){
            EnumFacing facing=tile.getPanelFacing(PanelJoins.facing(state));PreviousCorner old=entries.get(tile.getPos());
            if(old!=null&&old.owner.get()==tile&&old.state==state&&old.joined==tile.isJoin()&&old.facing==facing)return old.value;
            PreviousCorner next=new PreviousCorner(tile,state,facing);entries.put(tile.getPos().toImmutable(),next);return next.value;
        }
    }
    private static void oversized(int count){
        World world=new World();IBlockState state=state(EnumFacing.NORTH,0);TileEntityProgrammableGlass[] tiles=new TileEntityProgrammableGlass[count];PreviousLru previous=new PreviousLru();
        com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        try{
            for(int i=0;i<count;i++)tiles[i]=put(world,new BlockPos(i*4,80,8),EnumFacing.NORTH,0);
            for(int round=0;round<4;round++)for(TileEntityProgrammableGlass tile:tiles){sink=previous.get(tile,state);sink=GlassSurfaceGroups.flatCorner(tile,state);}
            final int rounds=4,calls=count*rounds;long[] bytes=new long[3];int[] states=new int[3],tileReads=new int[3];
            // Uncached original, previous LRU patch, and bounded admission all
            // traverse the same cyclic view after warming it beyond capacity.
            for(int mode=0;mode<3;mode++){
                world.resetReads();long start=meter.getThreadAllocatedBytes(thread);
                for(int round=0;round<rounds;round++)for(TileEntityProgrammableGlass tile:tiles){
                    sink=mode==0?PanelJoins.flatCorner(state,world,tile.getPos()):mode==1?previous.get(tile,state):GlassSurfaceGroups.flatCorner(tile,state);
                }
                bytes[mode]=meter.getThreadAllocatedBytes(thread)-start;states[mode]=world.stateReads;tileReads[mode]=world.tileReads;
            }
            int overflow=count-GlassSurfaceGroups.MAX_LAYOUTS;
            need(states[1]==states[0]&&tileReads[1]==tileReads[0],"previous LRU did not reproduce full-view scan cliff");
            need(bytes[1]>bytes[0],"previous LRU did not reproduce extra cache-entry allocation");
            need(states[2]==states[0]/count*overflow&&tileReads[2]==tileReads[0]/count*overflow,"overflow retired warm panes or cached excess positions");
            need(bytes[2]<=bytes[0]/count*overflow+4096,"overflow fallback allocated cache entries beyond uncached discovery");
            need(entries(world)==GlassSurfaceGroups.MAX_LAYOUTS,"admission exceeded or unexpectedly emptied its bound");
            System.out.println("Glass topology oversized panes="+count+" calls="+calls+" original/LRU/admission stateReads="+Arrays.toString(states)+" tileReads="+Arrays.toString(tileReads)+" allocated="+Arrays.toString(bytes));

            // A full cache still refreshes an existing position, and an overflow
            // position always reads current neighbours without retaining it.
            TileEntityProgrammableGlass replacement=put(world,tiles[0].getPos(),EnumFacing.SOUTH,0);world.resetReads();check(replacement,state);
            need(world.stateReads>0&&world.tileReads>0,"full cache ignored same-position owner replacement");world.resetReads();GlassSurfaceGroups.flatCorner(replacement,state);
            need(world.stateReads==0&&world.tileReads==0,"replacement at an admitted position was not cached");
            NBTTagCompound changed=replacement.writeToNBT(new NBTTagCompound());changed.setBoolean("GlassJoin",false);replacement.readFromNBT(changed);check(replacement,state);
            world.resetReads();GlassSurfaceGroups.flatCorner(replacement,state);need(world.stateReads==0&&world.tileReads==0,"full cache ignored same-owner settings replacement");
            TileEntityProgrammableGlass outside=tiles[count-1];BlockPos neighbor=outside.getPos().north();put(world,neighbor,EnumFacing.EAST,0);
            need(check(outside,state)!=null,"overflow fallback missed a new neighbour");world.states.remove(neighbor);world.tiles.remove(neighbor);
            need(check(outside,state)==null,"overflow fallback retained removed neighbour");need(entries(world)==GlassSurfaceGroups.MAX_LAYOUTS,"fallback admitted a new position");
            GlassSurfaceGroups.invalidate(world);check(outside,state);need(entries(world)==1,"invalidation did not reopen admission");world.resetReads();GlassSurfaceGroups.flatCorner(outside,state);
            need(world.stateReads==0&&world.tileReads==0,"newly admitted pane did not retain a negative hit");
        }finally{GlassSurfaceGroups.unload(world);}
    }
    private static void cost(){
        World world=new World();IBlockState state=state(EnumFacing.NORTH,0);TileEntityProgrammableGlass[] tiles=new TileEntityProgrammableGlass[1024];
        try{
            for(int i=0;i<tiles.length;i++)tiles[i]=put(world,new BlockPos(8+4*(i&31),80,8+4*(i>>>5)),EnumFacing.NORTH,0);
            for(int i=0;i<32768;i++){TileEntityProgrammableGlass tile=tiles[i&1023];sink=PanelJoins.flatCorner(state,world,tile.getPos());sink=GlassSurfaceGroups.flatCorner(tile,state);}
            com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
            world.resetReads();long start=meter.getThreadAllocatedBytes(thread);
            for(int i=0;i<8192;i++)sink=PanelJoins.flatCorner(state,world,tiles[i&1023].getPos());
            long baselineBytes=meter.getThreadAllocatedBytes(thread)-start;int baselineStates=world.stateReads,baselineTiles=world.tileReads;
            world.resetReads();start=meter.getThreadAllocatedBytes(thread);
            for(int i=0;i<8192;i++)sink=GlassSurfaceGroups.flatCorner(tiles[i&1023],state);
            long cachedBytes=meter.getThreadAllocatedBytes(thread)-start;
            need(baselineStates>0&&baselineTiles>0,"baseline did not exercise neighbour discovery");
            need(world.stateReads==0&&world.tileReads==0,"cached topology still queried the world");
            need(cachedBytes==0,"warmed topology allocated "+cachedBytes);need(baselineBytes>=cachedBytes,"cached allocation exceeded baseline");
            System.out.println("Glass topology calls8192 panes1024 stateReads="+baselineStates+"->"+world.stateReads+" tileReads="+baselineTiles+"->"+world.tileReads+" allocated="+baselineBytes+"->"+cachedBytes);
        }finally{GlassSurfaceGroups.unload(world);}
    }
    public static void main(String[] args)throws Exception{
        PropulsionPlacementLegacyOracle.initialize();for(net.minecraft.block.Block block:ModBlocks.BLOCKS)if(block instanceof BlockProgrammableGlass)glass=(BlockProgrammableGlass)block;need(glass!=null,"glass block");
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableGlass.class,new net.minecraft.util.ResourceLocation("vandorlabs","programmable_glass"));
        int cases=matrix();invalidation();chunkBoundary();carried();bound();cost();oversized(GlassSurfaceGroups.MAX_LAYOUTS+1);oversized(GlassSurfaceGroups.MAX_LAYOUTS*2);
        System.out.println("Glass topology cache PASS profiles="+cases+" null/corner parity edits replacement block/range/chunk invalidation carried revision unload bounded admission zero warm reads/allocation and over-capacity negative control");
    }
}
