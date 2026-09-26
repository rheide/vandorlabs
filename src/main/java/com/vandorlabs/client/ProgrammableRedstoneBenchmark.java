package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Measures synchronous channel edges in the disposable integrated server only. */
final class ProgrammableRedstoneBenchmark {
    private ProgrammableRedstoneBenchmark() { }
    static void run(Minecraft client, File output) throws Exception {
        client.getIntegratedServer().addScheduledTask(() -> {
            World world = client.getIntegratedServer().getWorld(0);
            try (PrintWriter csv = new PrintWriter(new File(output,"redstone-benchmark.csv"))) {
                csv.println("family,members,register_ms,pulse_pair_p50_ms,pulse_pair_p95_ms,pulse_pair_allocated_bytes_p50");
                for (boolean joined : new boolean[]{false,true})
                    for (int count : new int[]{16,64,256}) measure(world,csv,joined,count);
            } catch (Exception exception) {
                throw new IllegalStateException("redstone benchmark failed",exception);
            }
        }).get();
        System.out.println("[vandorlabs][reprolab] redstone-benchmark PASS");
    }

    private static void measure(World world, PrintWriter csv, boolean joined, int count) {
        BlockPos root = new BlockPos(-8,130,0), power = root.north();
        List<TileEntityAnimatedScreenSelector> tiles = new ArrayList<>();
        List<BlockPos> positions = new ArrayList<>();
        try {
            long registration = System.nanoTime();
            for (int i=0; i<count; i++) {
                BlockPos pos=root.add(i%16,i/16,0);
                if (!world.isBlockLoaded(pos) || !world.isAirBlock(pos))
                    throw new IllegalStateException("benchmark requires loaded empty fixture cells");
                positions.add(pos);
                world.setBlockState(pos,(joined ? ModBlocks.PROGRAMMABLE_LIGHT
                        : ModBlocks.ANIMATED_SCREEN_SELECTOR).getDefaultState(),2);
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
                tiles.add(tile);
                if (joined) ((TileEntityProgrammableLight)tile).configure(0,0,true,742001,0,1);
                else {
                    tile.setRedstoneEnabled(true);
                    tile.setDisplayMode(TileEntityAnimatedScreenSelector.MODE_STATIC);
                    tile.setRedstoneChannel(742001);
                }
            }
            double registerMs=(System.nanoTime()-registration)/1E6;
            double[] samples=new double[15];
            long[] allocated=new long[15];
            for (int i=-5; i<samples.length; i++) {
                long bytesBefore=BenchmarkAllocations.currentThreadBytes();
                long start=System.nanoTime();
                world.setBlockState(power,Blocks.REDSTONE_BLOCK.getDefaultState(),3);
                long on=System.nanoTime()-start;
                long onBytes=BenchmarkAllocations.currentThreadBytes()-bytesBefore;
                for (TileEntityAnimatedScreenSelector tile:tiles)
                    require(joined ? ((TileEntityProgrammableLight)tile).isOn()
                            : tile.getEffectiveMode()==TileEntityAnimatedScreenSelector.MODE_STATIC,
                            "channel did not turn every receiver on");
                long offBytesBefore=BenchmarkAllocations.currentThreadBytes();
                start=System.nanoTime();
                world.setBlockToAir(power);
                long off=System.nanoTime()-start;
                long offBytes=BenchmarkAllocations.currentThreadBytes()-offBytesBefore;
                for (TileEntityAnimatedScreenSelector tile:tiles)
                    require(joined ? !((TileEntityProgrammableLight)tile).isOn()
                            : tile.getEffectiveMode()==TileEntityAnimatedScreenSelector.MODE_OFF,
                            "channel did not turn every receiver off");
                if (i>=0) {
                    samples[i]=(on+off)/1E6;
                    allocated[i]=bytesBefore<0 ? -1 : onBytes+offBytes;
                }
            }
            Arrays.sort(samples);
            Arrays.sort(allocated);
            csv.printf(Locale.ROOT,"%s,%d,%.6f,%.6f,%.6f,%d%n",joined?"joined_lights":"screens",
                    count,registerMs,samples[7],samples[14],allocated[7]);
            csv.flush();
        } finally {
            world.setBlockToAir(power);
            // Unlink first so cleanup does not repeatedly notify the remaining channel.
            for (TileEntityAnimatedScreenSelector tile:tiles) tile.setRedstoneChannel(0);
            for (BlockPos pos:positions) world.setBlockToAir(pos);
        }
    }
    private static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
