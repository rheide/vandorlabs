package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.io.*;
import java.util.*;

/** Paired live-world collision-hook measurements, excluding vanilla collision work. */
final class OffsetCollisionRuntimeBenchmark {
    private OffsetCollisionRuntimeBenchmark() { }
    static void run(Minecraft client,File output) throws Exception {
        try(PrintWriter csv=new PrintWriter(new File(output,"offset-collision-benchmark.csv"))) {
            csv.println("world,fixture,algorithm,queries,median_ms,median_allocated_bytes");
            client.getIntegratedServer().addScheduledTask(()->fixtures(client.getIntegratedServer().getWorld(0),"server",csv)).get();
            fixtures(client.world,"client",csv);
        }
        System.out.println("[vandorlabs][reprolab] offset-palette-collision PASS (live insertion/removal, exact collision lists, loaded palette fast path on client/server)");
    }
    private static void fixtures(World world,String side,PrintWriter csv) {
        BlockPos root=new BlockPos(7,220,7);
        require(world.isBlockLoaded(root) && world.isAirBlock(root),"collision benchmark needs a loaded empty fixture");
        AxisAlignedBB query=new AxisAlignedBB(7.2,220.2,7.2,7.8,220.8,7.8);
        try {
            world.setBlockState(root,Blocks.STONE.getDefaultState(),2);
            require(!presence(world,query),"standard live palette did not reject a stone-only section: "+world.getClass().getName());
            measure(world,side,"stone_palette",query,csv);
            world.setBlockState(root,ModBlocks.PROGRAMMABLE_TRAPDOOR.getDefaultState(),0);
            require(presence(world,query),"new trapdoor state was rejected before tile configuration");
            TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)world.getTileEntity(root);
            leaf.configure(0,1,false,0,0);leaf.setCover(true);
            query=OffsetTrapdoorInteractions.bounds(leaf).grow(.05);
            List<AxisAlignedBB> present=new ArrayList<>();OffsetTrapdoorInteractions.addCollisions(world,query,present);
            require(!present.isEmpty(),"cover collision fixture did not exercise the ordinary scan");
            measure(world,side,"cover",query,csv);
            world.setBlockToAir(root);measure(world,side,"removed",query,csv);
        } finally {world.setBlockToAir(root);}
    }
    private static boolean presence(World world,AxisAlignedBB q) {
        return TrapdoorSectionPresence.mayContain(world,MathHelper.floor(q.minX-2),MathHelper.floor(q.minY-1),MathHelper.floor(q.minZ-2),
                MathHelper.floor(q.maxX+2),MathHelper.floor(q.maxY+1),MathHelper.floor(q.maxZ+2));
    }
    private static void measure(World world,String side,String fixture,AxisAlignedBB query,PrintWriter csv) {
        for(double shift:new double[]{0,.1,.5,1,3}) {
            AxisAlignedBB moved=query.offset(shift,shift,-shift);
            List<AxisAlignedBB> before=new ArrayList<>(),after=new ArrayList<>();
            before.add(new AxisAlignedBB(-1,-1,-1,0,0,0));after.addAll(before);
            scan(world,moved,before);OffsetTrapdoorInteractions.addCollisions(world,moved,after);
            require(before.equals(after),"palette filter changed collision order or bounds");
            scan(world,moved,before);OffsetTrapdoorInteractions.addCollisions(world,moved,after);
            require(before.equals(after),"palette filter changed duplicate suppression");
        }
        double[][] times=new double[2][15];long[][] bytes=new long[2][15];List<AxisAlignedBB> boxes=new ArrayList<>();
        // Both ordinary scan paths need equal warmup even when earlier palette
        // fixtures returned before entering the optimized method's scan loop.
        for(int i=0;i<8192;i++) {
            boxes.clear();scan(world,query,boxes);
            boxes.clear();OffsetTrapdoorInteractions.addCollisions(world,query,boxes);
        }
        for(int batch=-15;batch<15;batch++)for(int order=0;order<2;order++) {
            int algorithm=(batch+order)&1;
            long allocation=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
            for(int i=0;i<256;i++) {
                boxes.clear();
                if(algorithm==0)scan(world,query,boxes);else OffsetTrapdoorInteractions.addCollisions(world,query,boxes);
            }
            long elapsed=System.nanoTime()-start,used=BenchmarkAllocations.currentThreadBytes()-allocation;
            if(batch>=0){times[algorithm][batch]=elapsed/1E6;bytes[algorithm][batch]=used;}
        }
        for(int algorithm=0;algorithm<2;algorithm++) {
            Arrays.sort(times[algorithm]);Arrays.sort(bytes[algorithm]);
            csv.printf(Locale.ROOT,"%s,%s,%s,256,%.6f,%d%n",side,fixture,algorithm==0?"prior_scan":"palette",times[algorithm][7],bytes[algorithm][7]);
        }
    }
    /** The preceding 1.4 mutable-position scan, without palette rejection. */
    private static void scan(World world,AxisAlignedBB query,List<AxisAlignedBB> boxes) {
        for(BlockPos pos:BlockPos.getAllInBoxMutable(new BlockPos(query.minX-2,query.minY-1,query.minZ-2),new BlockPos(query.maxX+2,query.maxY+1,query.maxZ+2))) {
            TileEntityProgrammableTrapdoor leaf=leaf(world,pos);
            if(leaf==null || !leaf.isCover() && !leaf.isSlideOverSurface())continue;
            AxisAlignedBB box=OffsetTrapdoorInteractions.bounds(leaf);
            if(box.intersects(query) && !boxes.contains(box))boxes.add(box);
        }
    }
    private static TileEntityProgrammableTrapdoor leaf(World world,BlockPos pos) {
        if(!world.isBlockLoaded(pos) || !(world.getBlockState(pos).getBlock() instanceof BlockProgrammableTrapdoor))return null;
        TileEntity tile=world.getTileEntity(pos);
        return tile instanceof TileEntityProgrammableTrapdoor && (((TileEntityProgrammableTrapdoor)tile).isCover() || ((TileEntityProgrammableTrapdoor)tile).isSlideOverSurface() || tile instanceof TileEntityProgrammableDiagonalTrapdoor)?(TileEntityProgrammableTrapdoor)tile:null;
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
