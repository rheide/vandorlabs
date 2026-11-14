package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.FloatBuffer;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Real sorter capture and batch-state reuse, including edits between sorted draws. */
public final class GlassPanePreparationChecks {
    private static final BlockPos POS=new BlockPos(8,80,8);
    private static BlockProgrammableGlass glass;
    private static Object sink;
    private static final class World extends NonRenderingChecks.MemoryWorld {
        int tileReads;World(){super(true);}
        @Override public TileEntity getTileEntity(BlockPos pos){tileReads++;return super.getTileEntity(pos);}
        void reset(){stateReads=tileReads=0;}
    }
    private static void need(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    private static IBlockState state(EnumFacing facing,int depth){return glass.getDefaultState().withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X).withProperty(BlockGlassWall.DEPTH,depth);}
    private static TileEntityProgrammableGlass tile(net.minecraft.world.World world,BlockPos pos,EnumFacing facing){
        return tile(world,pos,facing,true);
    }
    private static TileEntityProgrammableGlass tile(net.minecraft.world.World world,BlockPos pos,EnumFacing facing,boolean joined){
        TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();NBTTagCompound data=new NBTTagCompound();
        data.setInteger("GlassPanelFacing",facing.getHorizontalIndex());data.setBoolean("GlassJoin",joined);tile.readFromNBT(data);tile.setWorld(world);tile.setPos(pos);return tile;
    }
    private static TileEntityProgrammableGlass put(World world,BlockPos pos,EnumFacing facing,int depth){
        TileEntityProgrammableGlass tile=tile(world,pos,facing);world.states.put(pos,state(facing,depth));world.tiles.put(pos,tile);return tile;
    }
    private static void prepare(TileEntityProgrammableGlass tile,GlassPanePreparation prepared){
        prepared.clear();long before=GlassSurfaceGroups.mutation();IBlockState state=tile.getWorld().getBlockState(tile.getPos());
        if(state.getBlock() instanceof BlockProgrammableGlass&&GlassSurfaceGroups.flatCorner(tile,state)==null)prepared.capture(tile,state,before);
    }
    private static void fallback(World world,TileEntityProgrammableGlass tile,GlassPanePreparation prepared,IBlockState expected,String reason){
        world.reset();need(GlassPanePreparation.flatState(tile,prepared)==expected,reason+" changed geometry result");need(world.stateReads>0,reason+" reused stale sorter state");
    }
    private static void changes(){
        World world=new World();GlassPanePreparation prepared=new GlassPanePreparation();
        try{
            TileEntityProgrammableGlass root=put(world,POS,EnumFacing.NORTH,0);IBlockState base=world.states.get(POS);
            prepare(root,prepared);fallback(world,root,prepared,base,"listener registration");
            prepare(root,prepared);world.reset();need(GlassPanePreparation.flatState(root,prepared)==base,"warm state");need(world.stateReads==0&&world.tileReads==0,"warm reuse queried the world");
            IBlockState changed=base.withProperty(BlockGlassWall.DEPTH,2);world.states.put(POS,changed);world.notifyBlockUpdate(POS,base,changed,3);
            fallback(world,root,prepared,changed,"state update");prepare(root,prepared);
            put(world,POS.north(),EnumFacing.EAST,2);world.notifyBlockUpdate(POS.north(),Blocks.AIR.getDefaultState(),world.states.get(POS.north()),3);
            fallback(world,root,prepared,null,"joined-corner insertion");
            world.states.remove(POS.north());world.tiles.remove(POS.north());world.markBlockRangeForRenderUpdate(POS.north(),POS.north());prepare(root,prepared);
            GlassSurfaceGroups.invalidate(world,POS.getX(),POS.getY(),POS.getZ(),POS.getX(),POS.getY(),POS.getZ());fallback(world,root,prepared,changed,"explicit local invalidation");
            prepare(root,prepared);TileEntityProgrammableGlass replacement=put(world,POS,EnumFacing.NORTH,2);fallback(world,replacement,prepared,changed,"same-state replacement");
            root=replacement;prepare(root,prepared);NBTTagCompound data=root.writeToNBT(new NBTTagCompound());data.setBoolean("GlassJoin",false);root.readFromNBT(data);
            fallback(world,root,prepared,changed,"owner join edit");prepare(root,prepared);data.setInteger("GlassPanelFacing",EnumFacing.SOUTH.getHorizontalIndex());root.readFromNBT(data);
            fallback(world,root,prepared,changed,"owner facing edit");prepare(root,prepared);root.setPos(POS.east(4));world.states.put(root.getPos(),changed);world.tiles.put(root.getPos(),root);
            fallback(world,root,prepared,changed,"owner move");prepare(root,prepared);root.invalidate();fallback(world,root,prepared,changed,"invalid owner");root.validate();
            prepare(root,prepared);GlassSurfaceGroups.unload(world);fallback(world,root,prepared,changed,"world unload");
            prepare(root,prepared);GlassSurfaceGroups.invalidate(world);fallback(world,root,prepared,changed,"full cache invalidation");
            prepare(root,prepared);prepared.clear();fallback(world,root,prepared,changed,"end-of-flush clear");
        }finally{prepared.clear();GlassSurfaceGroups.unload(world);}
    }
    private static void carried()throws Exception{
        World parent=new World();IBlockState state=state(EnumFacing.NORTH,0);BlockPos front=POS.north();
        VehicleWorld world=new VehicleWorld(parent,VehicleStructure.display(Arrays.asList(new VehicleStructure.Cell(POS,state,null),new VehicleStructure.Cell(front,state(EnumFacing.EAST,0),null))));
        TileEntityProgrammableGlass root=tile(world,POS,EnumFacing.NORTH);world.tiles.put(POS,root);world.tiles.put(front,tile(world,front,EnumFacing.EAST,false));GlassPanePreparation prepared=new GlassPanePreparation();
        Method current=GlassPanePreparation.class.getDeclaredMethod("current",TileEntityProgrammableGlass.class);current.setAccessible(true);
        try{
            prepare(root,prepared);prepare(root,prepared);need(current.invoke(prepared,root)==state,"carried preparation");
            world.tiles.put(front,tile(world,front,EnumFacing.EAST));world.tileRevision++;
            need(current.invoke(prepared,root)==null,"carried tile revision retained snapshot");need(GlassPanePreparation.flatState(root,prepared)==null,"carried corner insertion missed");
            world.tiles.put(front,tile(world,front,EnumFacing.EAST,false));world.tileRevision++;prepare(root,prepared);prepare(root,prepared);
            world.origin=POS.east(32);need(current.invoke(prepared,root)==null,"carried origin change retained snapshot");prepare(root,prepared);
            world.structure=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(POS,state,null)));
            need(current.invoke(prepared,root)==null,"carried structure replacement retained snapshot");need(GlassPanePreparation.flatState(root,prepared)==state,"carried structure fallback changed pane");
        }finally{prepared.clear();GlassSurfaceGroups.unload(world);}
    }
    private static Field field(Class<?> type,String name)throws Exception{Field value=type.getDeclaredField(name);value.setAccessible(true);return value;}
    @SuppressWarnings("unchecked") private static void actualSorter()throws Exception{
        Class<?> type=Class.forName("com.vandorlabs.client.GlazingRenderOrder$Draw");Constructor<?> constructor=type.getDeclaredConstructor();constructor.setAccessible(true);Object draw=constructor.newInstance();
        Method depth=GlazingRenderOrder.class.getDeclaredMethod("depth",type,double.class,double.class,double.class,FloatBuffer.class);depth.setAccessible(true);
        FloatBuffer matrix=FloatBuffer.allocate(16);matrix.put(0,1);matrix.put(5,1);matrix.put(10,1);matrix.put(15,1);
        World world=new World();
        try{
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int inset=0;inset<3;inset++){
                TileEntityProgrammableGlass tile=put(world,POS,facing,inset);IBlockState state=world.states.get(POS);GlassSurfaceGroups.flatCorner(tile,state);
                field(type,"tile").set(draw,tile);double result=(Double)depth.invoke(null,draw,3D,2D,1D,matrix);
                GlassPanePreparation prepared=(GlassPanePreparation)field(type,"glass").get(draw);
                boolean rotated=facing.getAxis()==EnumFacing.Axis.X;double plane=(rotated?POS.getX():POS.getZ())+.5+(rotated?-1:1)*PanelDepth.offset(inset)/16D;
                need(result==-Math.abs(plane-(rotated?3D:1D)),"sorter depth changed");world.reset();
                need(GlassPanePreparation.flatState(tile,prepared)==state&&world.stateReads==0,"sorter did not publish reusable flat state");
            }
            List<Object> ordered=(List<Object>)field(GlazingRenderOrder.class,"ordered").get(null);ordered.add(draw);GlazingRenderOrder.end();
            GlassPanePreparation prepared=(GlassPanePreparation)field(type,"glass").get(draw);
            for(String name:new String[]{"owner","world","state","structure"})need(field(GlassPanePreparation.class,name).get(prepared)==null,"pool retained "+name);
            need(field(type,"tile").get(draw)==null,"draw pool retained tile");
        }finally{GlazingRenderOrder.end();GlassSurfaceGroups.unload(world);}
    }
    private static void pass(World world,TileEntityProgrammableGlass[] tiles,GlassPanePreparation[] plans,boolean prepared,boolean edit){
        // Both modes deliberately use the same topology cache: isolate the extra
        // state/discovery calls in the delivered depth()+append() control flow.
        for(int i=0;i<tiles.length;i++){
            TileEntityProgrammableGlass tile=tiles[i];
            if(prepared)prepare(tile,plans[i]);
            else{IBlockState state=world.getBlockState(tile.getPos());sink=GlassSurfaceGroups.flatCorner(tile,state);}
        }
        if(edit){BlockPos at=tiles[0].getPos();world.notifyBlockUpdate(at,world.states.get(at),world.states.get(at),3);}
        for(int i=0;i<tiles.length;i++)sink=GlassPanePreparation.flatState(tiles[i],prepared?plans[i]:null);
    }
    private static void cost(int count,boolean edit){
        World world=new World();TileEntityProgrammableGlass[] tiles=new TileEntityProgrammableGlass[count];GlassPanePreparation[] plans=new GlassPanePreparation[count];
        try{
            for(int i=0;i<count;i++){tiles[i]=put(world,new BlockPos(i*4,80,8),EnumFacing.NORTH,0);plans[i]=new GlassPanePreparation();}
            int warm=Math.max(12,131072/count);
            for(int round=0;round<warm;round++){pass(world,tiles,plans,false,edit);pass(world,tiles,plans,true,edit);}
            com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
            long[][] nanos=new long[2][7],bytes=new long[2][7];int[] states=new int[2],neighbors=new int[2];
            for(int round=0;round<7;round++)for(int order=0;order<2;order++){
                int mode=(round+order)&1;world.reset();long allocated=meter.getThreadAllocatedBytes(thread),started=System.nanoTime();pass(world,tiles,plans,mode==1,edit);
                nanos[mode][round]=System.nanoTime()-started;bytes[mode][round]=meter.getThreadAllocatedBytes(thread)-allocated;states[mode]=world.stateReads;neighbors[mode]=world.tileReads;
            }
            for(int mode=0;mode<2;mode++){Arrays.sort(nanos[mode]);Arrays.sort(bytes[mode]);}
            need(edit?states[1]==states[0]:states[1]*2==states[0],"same-flush state reads "+Arrays.toString(states));
            need(edit?neighbors[1]==neighbors[0]:neighbors[1]*2==neighbors[0],"same-flush neighbour reads "+Arrays.toString(neighbors));
            need(bytes[1][3]<=bytes[0][3]+1024,"same-flush preparation added allocations");
            System.out.println("Glass same-flush panes="+count+" editBetweenPhases="+edit+" previous/current medianNs="+nanos[0][3]+"/"+nanos[1][3]+" allocated="+bytes[0][3]+"/"+bytes[1][3]+" stateReads="+Arrays.toString(states)+" tileReads="+Arrays.toString(neighbors)+" warmCallsPerMode="+(warm*count));
        }finally{for(GlassPanePreparation plan:plans)if(plan!=null)plan.clear();GlassSurfaceGroups.unload(world);}
    }
    public static void main(String[] args)throws Exception{
        Field environment=net.minecraftforge.fml.relauncher.CoreModManager.class.getDeclaredField("deobfuscatedEnvironment");environment.setAccessible(true);
        boolean previous=environment.getBoolean(null);environment.setBoolean(null,true);
        try{
            PropulsionPlacementLegacyOracle.initialize();for(net.minecraft.block.Block block:ModBlocks.BLOCKS)if(block instanceof BlockProgrammableGlass)glass=(BlockProgrammableGlass)block;need(glass!=null,"glass block");
            net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableGlass.class,new net.minecraft.util.ResourceLocation("vandorlabs","programmable_glass"));
            changes();carried();actualSorter();cost(256,false);cost(GlassSurfaceGroups.MAX_LAYOUTS*2,false);cost(4096,true);
            System.out.println("Glass pane preparation PASS real sorter depth/clear, block/range/local/global/unload edits, owner replacement/move/settings, carried revision/structure/origin and dense/edit-heavy work");
        }finally{environment.setBoolean(null,previous);}
    }
}
