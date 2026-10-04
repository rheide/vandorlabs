package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.properties.*;
import net.minecraft.block.state.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import java.util.*;

/** Paired canonical-state property reads, with no world or rendering overhead. */
public final class PropertyLookupBenchmark {
    private static volatile int consumed;
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register();
        IProperty<?>[][] keys={
                {PropertyDirection.create("facing",EnumFacing.Plane.HORIZONTAL),PropertyEnum.create("half",BlockDoor.EnumDoorHalf.class)},
                {CachedProperties.direction("facing",EnumFacing.Plane.HORIZONTAL),CachedProperties.enumeration("half",BlockDoor.EnumDoorHalf.class)}};
        IBlockState[][] states=new IBlockState[2][];
        for(int i=0;i<2;i++)states[i]=new BlockStateContainer(Blocks.STONE,keys[i]).getValidStates().toArray(new IBlockState[0]);
        System.out.println("family,algorithm,reads,median_ms,median_allocated_bytes");
        measure("enum_hash","cached_hash",states,keys);
        BlockProgrammableTrapdoor block=new BlockProgrammableTrapdoor();
        IProperty<?>[] trapdoor={BlockTrapDoor.FACING,BlockTrapDoor.HALF,BlockTrapDoor.OPEN};
        states[0]=new BlockStateContainer(block,trapdoor).getValidStates().toArray(new IBlockState[0]);
        states[1]=block.getBlockState().getValidStates().toArray(new IBlockState[0]);
        measure("trapdoor_state","direct",states,new IProperty<?>[][]{trapdoor,trapdoor});
    }
    private static void measure(String family,String optimized,IBlockState[][] states,IProperty<?>[][] keys) {
        double[][] times=new double[2][31];long[][] bytes=new long[2][31];
        for(int batch=-15;batch<31;batch++)for(int order=0;order<2;order++) {
            int algorithm=(batch+order)&1;IProperty<?>[] properties=keys[algorithm];int mask=states[algorithm].length-1;
            long allocation=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();int sum=0;
            for(int i=0;i<32768;i++) {
                IBlockState state=states[algorithm][i&mask];
                for(IProperty<?> property:properties)sum+=read(state,property);
            }
            consumed=sum;long elapsed=System.nanoTime()-start,used=BenchmarkAllocations.currentThreadBytes()-allocation;
            if(batch>=0){times[algorithm][batch]=elapsed/1E6;bytes[algorithm][batch]=used;}
        }
        for(int algorithm=0;algorithm<2;algorithm++) {
            Arrays.sort(times[algorithm]);Arrays.sort(bytes[algorithm]);
            System.out.printf(Locale.ROOT,"%s,%s,%d,%.6f,%d%n",family,algorithm==0?"reference":optimized,32768*keys[algorithm].length,times[algorithm][15],bytes[algorithm][15]);
        }
    }
    private static <T extends Comparable<T>> int read(IBlockState state,IProperty<T> property) {
        T value=state.getValue(property);return value instanceof Enum?((Enum<?>)value).ordinal():Boolean.TRUE.equals(value)?1:0;
    }
}
