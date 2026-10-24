package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.*;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Same-process old/new CPU and allocation samples in a real client world. */
final class FluidPresenceRuntimeChecks {
    static void run(Minecraft mc,File output)throws Exception{
        BlockPos pos=new BlockPos(336,80,144);if(!mc.world.isBlockLoaded(pos))throw new IllegalStateException("Fluid presence fixture chunk");
        List<String> rows=new ArrayList<>();rows.add("scenario,queries,reference_cpu_ns,cached_cpu_ns,reference_bytes,cached_bytes");
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();boolean allocated=bean.isThreadAllocatedMemorySupported();if(allocated&&!bean.isThreadAllocatedMemoryEnabled())bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        String[] names={"dry-door","adjacent-water-door","dry-wall","stored-water-wall","dry-diagonal-wall","stored-water-diagonal-wall"};
        try{for(int scenario=0;scenario<6;scenario++){
            for(EnumFacing face:EnumFacing.values())mc.world.setBlockState(pos.offset(face),Blocks.AIR.getDefaultState(),2);
            mc.world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),2);
            Block block=Block.getBlockFromName("vandorlabs:"+(scenario<2?"programmable_door":scenario<4?"programmable_wall":"programmable_diagonal_wall"));mc.world.setBlockState(pos,block.getDefaultState(),2);TileEntity tile=mc.world.getTileEntity(pos);if(tile==null)throw new IllegalStateException("Fluid presence tile "+names[scenario]);
            if(scenario==1)mc.world.setBlockState(pos.north(),Blocks.WATER.getDefaultState(),2);
            if(scenario>=3&&scenario%2==1)WaterloggedParts.data(tile).set(0,new FluidStack(FluidRegistry.WATER,1000));
            for(int i=0;i<10000;i++){WaterloggedParts.wet(mc.world,pos);RenderFluidPresence.wet(tile);}
            long[][] cpu=new long[2][7],bytes=new long[2][7];
            for(int sample=0;sample<7;sample++)for(int order=0;order<2;order++){
                int mode=(sample+order)%2;long before=allocated?bean.getThreadAllocatedBytes(thread):0,start=bean.getCurrentThreadCpuTime();boolean answer=false;
                for(int i=0;i<5000;i++)answer=mode==0?WaterloggedParts.wet(mc.world,pos):RenderFluidPresence.wet(tile);
                cpu[mode][sample]=bean.getCurrentThreadCpuTime()-start;bytes[mode][sample]=allocated?bean.getThreadAllocatedBytes(thread)-before:-1;
                if(answer!=WaterloggedParts.wet(mc.world,pos))throw new IllegalStateException("Fluid presence live parity "+names[scenario]);
            }
            for(long[] values:cpu)Arrays.sort(values);for(long[] values:bytes)Arrays.sort(values);
            rows.add(names[scenario]+",5000,"+cpu[0][3]+","+cpu[1][3]+","+bytes[0][3]+","+bytes[1][3]);
            System.out.println("[vandorlabs] fluid-presence-client "+names[scenario]+" queries=5000 CPU-ns="+cpu[0][3]+" -> "+cpu[1][3]+" allocated-bytes="+bytes[0][3]+" -> "+bytes[1][3]);
            // Normal client world updates must invalidate immediately in the same tick.
            if(scenario==0){mc.world.setBlockState(pos.north(),Blocks.WATER.getDefaultState(),2);if(!RenderFluidPresence.wet(tile))throw new IllegalStateException("Client water placement invalidation");mc.world.setBlockToAir(pos.north());if(tile.isInvalid()||mc.world.getTileEntity(pos)!=tile||RenderFluidPresence.wet(tile))throw new IllegalStateException("Client water removal invalidation");}
        }}finally{for(EnumFacing face:EnumFacing.values())mc.world.setBlockToAir(pos.offset(face));mc.world.setBlockToAir(pos);}
        Files.write(new File(output,"fluid-presence-query-cost.csv").toPath(),rows,StandardCharsets.UTF_8);
        System.out.println("[vandorlabs] fluid-presence-client PASS actual world legacy parity normal-update invalidation six CPU/allocation comparisons");
    }
}
