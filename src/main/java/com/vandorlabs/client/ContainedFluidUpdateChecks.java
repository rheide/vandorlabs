package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;

/** Real tile update packets must invalidate ordinary-fluid meshes across chunk/section edges. */
final class ContainedFluidUpdateChecks {
    static void run(World world){
        BlockPos pos=new BlockPos(319,47,143);int cases=0;
        class Listener extends WorldRenderListener {boolean east,west,upper;void reset(){east=west=upper=false;}@Override public void markBlockRangeForRenderUpdate(int x0,int y0,int z0,int x1,int y1,int z1){east|=inside(pos.east(),x0,y0,z0,x1,y1,z1);west|=inside(pos.west(),x0,y0,z0,x1,y1,z1);upper|=inside(pos.up(2),x0,y0,z0,x1,y1,z1);}}
        Listener listener=new Listener();world.addEventListener(listener);
        try{for(String id:new String[]{"programmable_slab","programmable_stairs","programmable_wall","programmable_porthole_wall","programmable_glass","programmable_diagonal_wall","programmable_diagonal_porthole","programmable_diagonal_glass","programmable_door","programmable_trapdoor","programmable_diagonal_trapdoor"}){
            Block block=Block.getBlockFromName("vandorlabs:"+id);world.setBlockState(pos,block.getDefaultState(),2);TileEntity tile=world.getTileEntity(pos);
            for(boolean fill:new boolean[]{true,false}){
                NBTTagCompound packet=tile.getUpdateTag(),fluids=new NBTTagCompound();if(fill)fluids.setTag("Side0",new FluidStack(FluidRegistry.WATER,1000).writeToNBT(new NBTTagCompound()));packet.setTag("ContainedFluids",fluids);listener.reset();
                tile.onDataPacket(null,new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos,0,packet));
                require(listener.east&&listener.west,"fluid packet leaves adjacent native chunk stale "+id+" fill="+fill);
                if(tile instanceof com.vandorlabs.tiles.TileEntitySpaceDoor)require(listener.upper,"door packet leaves upper adjacent section stale");
                require(WaterloggedParts.data(tile).occupied(0)==fill,"fluid packet applied "+id);cases++;
            }world.setBlockToAir(pos);
        }}finally{world.removeEventListener(listener);world.setBlockToAir(pos);}
        System.out.println("[vandorlabs][reprolab] contained-fluid-packets PASS cases="+cases+" eleven families fill/drain actual tile packets adjacent chunk and door upper-section invalidation");
    }
    private static boolean inside(BlockPos p,int x0,int y0,int z0,int x1,int y1,int z1){return p.getX()>=x0&&p.getX()<=x1&&p.getY()>=y0&&p.getY()<=y1&&p.getZ()>=z0&&p.getZ()<=z1;}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
