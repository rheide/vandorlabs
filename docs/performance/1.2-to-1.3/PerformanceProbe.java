package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;
import java.lang.reflect.Field;

/** CPU diagnostics for the report. No GL, real chunks, network, or light propagation. */
public final class PerformanceProbe {
    private static volatile Object sink;
    private static final class ProbeWorld extends World {
        final Map<BlockPos,IBlockState> states=new HashMap<>();
        final Map<BlockPos,TileEntity> tiles=new HashMap<>();
        ProbeWorld() { super(null,new net.minecraft.world.storage.WorldInfo(new NBTTagCompound()),
                new net.minecraft.world.WorldProviderSurface(),new net.minecraft.profiler.Profiler(),false); }
        @Override protected net.minecraft.world.chunk.IChunkProvider createChunkProvider(){return null;}
        @Override protected boolean isChunkLoaded(int x,int z,boolean empty){return true;}
        @Override public IBlockState getBlockState(BlockPos p){return states.getOrDefault(p,Blocks.AIR.getDefaultState());}
        @Override public TileEntity getTileEntity(BlockPos p){return tiles.get(p);}
        @Override public boolean setBlockState(BlockPos p,IBlockState s,int flags){states.put(p,s);return true;}
        @Override public void markChunkDirty(BlockPos p,TileEntity t){}
        @Override public void notifyBlockUpdate(BlockPos p,IBlockState a,IBlockState b,int flags){}
        @Override public void playEvent(net.minecraft.entity.player.EntityPlayer p,int id,BlockPos at,int data){}
        @Override public void markBlockRangeForRenderUpdate(BlockPos a,BlockPos b){}
    }
    private static void measure(String name,int units,Runnable action) {
        // Amortized samples, not frame percentiles. Retain outputs to prevent elimination.
        for(int i=0;i<1000;i++)action.run();
        double[] ns=new double[21],bytes=new double[21];
        for(int s=0;s<ns.length;s++) {
            long a=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
            for(int i=0;i<100;i++)action.run();
            ns[s]=(System.nanoTime()-start)/100.0;
            bytes[s]=(BenchmarkAllocations.currentThreadBytes()-a)/100.0;
        }
        Arrays.sort(ns);Arrays.sort(bytes);
        System.out.printf(Locale.ROOT,"PROBE,%s,%d,%.3f,%.3f,%.1f%n",name,units,ns[10]/1000,ns[19]/1000,bytes[10]);
    }
    private static void flatPortholes(boolean current,net.minecraft.client.renderer.BufferBuilder buffer,
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite) {
        buffer.begin(7,net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX);
        for(int i=0;i<64;i++)for(int z:new int[]{6,10}) {
            if(current)DiagonalPortholeMesh.quad(buffer,sprite,true,null,
                    new double[]{0,0,z,0,16},new double[]{16,0,z,16,16},
                    new double[]{16,16,z,16,0},new double[]{0,16,z,0,0});
            else {
                // Exact direct-emission pattern in 0b8ac9dd panelRect/panelQuad.
                buffer.pos(0,0,z).tex(sprite.getInterpolatedU(0),sprite.getInterpolatedV(16)).endVertex();
                buffer.pos(16,0,z).tex(sprite.getInterpolatedU(16),sprite.getInterpolatedV(16)).endVertex();
                buffer.pos(16,16,z).tex(sprite.getInterpolatedU(16),sprite.getInterpolatedV(0)).endVertex();
                buffer.pos(0,16,z).tex(sprite.getInterpolatedU(0),sprite.getInterpolatedV(0)).endVertex();
            }
        }
        if(buffer.getVertexCount()!=512)throw new AssertionError("Wrong vertex count");
        sink=buffer;buffer.finishDrawing();buffer.reset();
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.init.Bootstrap.register();
        System.out.println("PROBE,case,units,p50_us,p95_us,allocated_bytes");
        ProbeWorld empty=new ProbeWorld();
        Vec3d start=new Vec3d(.5,100.5,.5),straight=start.addVector(0,0,5),diagonal=start.addVector(5/Math.sqrt(3),5/Math.sqrt(3),5/Math.sqrt(3));
        measure("empty_trace_straight",1,()->sink=OffsetTrapdoorInteractions.trace(empty,start,straight));
        measure("empty_trace_diagonal",1,()->sink=OffsetTrapdoorInteractions.trace(empty,start,diagonal));
        measure("vanilla_empty_trace_straight",1,()->sink=empty.rayTraceBlocks(start,straight));
        AxisAlignedBB query=new AxisAlignedBB(.2,100,.2,.8,101.8,.8);
        measure("empty_collision_query",1,()->{List<AxisAlignedBB> boxes=new ArrayList<>();OffsetTrapdoorInteractions.addCollisions(empty,query,boxes);sink=boxes;});
        Field assembly=TileEntityProgrammableTrapdoor.class.getDeclaredField("assembly");assembly.setAccessible(true);
        Field position=TileEntityProgrammableTrapdoor.class.getDeclaredField("position");position.setAccessible(true);
        for(boolean diagonalLeaf:new boolean[]{false,true})for(int size:new int[]{1,8,16,64}) {
            ProbeWorld world=new ProbeWorld();
            Block block=diagonalLeaf?new BlockProgrammableDiagonalTrapdoor():new BlockProgrammableTrapdoor();
            List<TileEntityProgrammableTrapdoor> all=new ArrayList<>();
            for(int base=0;base<64;base+=size) {
                List<BlockPos> members=new ArrayList<>();
                for(int i=0;i<size;i++)members.add(new BlockPos(base*3+i%8,100+(diagonalLeaf?i/8:0),diagonalLeaf?0:i/8));
                for(BlockPos p:members) {
                    TileEntityProgrammableTrapdoor tile=diagonalLeaf?new TileEntityProgrammableDiagonalTrapdoor():new TileEntityProgrammableTrapdoor();
                    tile.setWorld(world);tile.setPos(p);if(diagonalLeaf)position.setInt(tile,1);
                    if(size>1)assembly.set(tile,new ArrayList<>(members));
                    world.states.put(p,block.getDefaultState());world.tiles.put(p,tile);all.add(tile);
                }
            }
            String suffix=(diagonalLeaf?"diagonal":"regular")+"_group"+size;
            for(TileEntityProgrammableTrapdoor t:all)if(t.group().size()!=size)throw new AssertionError("Invalid group");
            measure("group_"+suffix,64,()->{for(TileEntityProgrammableTrapdoor t:all)sink=t.group();});
            measure("uv_"+suffix,64,()->{for(TileEntityProgrammableTrapdoor t:all)sink=TEProgrammableTrapdoor.materialCoordinates(t,world.getBlockState(t.getPos()));});
            measure("signal_pair_"+suffix,64,()->{for(TileEntityProgrammableTrapdoor t:all)t.setChannelSignal(true);for(TileEntityProgrammableTrapdoor t:all)t.setChannelSignal(false);});
        }
        ProbeWorld world=new ProbeWorld();BlockProgrammableDiagonalTrapdoor block=new BlockProgrammableDiagonalTrapdoor();
        BlockPos p=new BlockPos(0,100,0);TileEntityProgrammableDiagonalTrapdoor tile=new TileEntityProgrammableDiagonalTrapdoor();
        tile.setWorld(world);tile.setPos(p);world.tiles.put(p,tile);
        AxisAlignedBB broad=new AxisAlignedBB(-8,90,-8,8,110,8);
        for(boolean open:new boolean[]{false,true}) {
            IBlockState state=block.getDefaultState().withProperty(BlockProgrammableTrapdoor.OPEN,open);world.states.put(p,state);
            measure("diagonal_collision_"+(open?"open":"closed"),1,()->{List<AxisAlignedBB> boxes=new ArrayList<>();block.addCollisionBoxToList(state,world,p,broad,boxes,null,true);sink=boxes;});
        }
        for(Block control:new Block[]{Blocks.STONE,Blocks.TRAPDOOR}) {
            IBlockState state=control.getDefaultState();world.states.put(p,state);
            measure("vanilla_collision_"+(control==Blocks.STONE?"stone":"trapdoor"),1,()->{List<AxisAlignedBB> boxes=new ArrayList<>();control.addCollisionBoxToList(state,world,p,broad,boxes,null,true);sink=boxes;});
        }
        double[][] quad={{0,0,0,0,0},{16,0,0,16,0},{16,16,0,16,16},{0,16,0,0,16}};
        double[] clear={-32,-32,-32,48,48,48};
        double[] obstacle={-32,-32,-32,48,48,48,8,-16,-16,32,32,16};
        measure("clip_clear_quad",1,()->sink=com.vandorlabs.render.DiagonalMeshClip.quads(clear,quad));
        measure("clip_obstacle_quad",1,()->sink=com.vandorlabs.render.DiagonalMeshClip.quads(obstacle,quad));
        net.minecraft.client.renderer.BufferBuilder buffer=new net.minecraft.client.renderer.BufferBuilder(32768);
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=new net.minecraft.client.renderer.texture.TextureAtlasSprite("probe"){};
        sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(256,256,0,0,false);
        measure("flat_porthole_1_2_emission_control",64,()->flatPortholes(false,buffer,sprite));
        measure("flat_porthole_current_emission",64,()->flatPortholes(true,buffer,sprite));
    }
}
