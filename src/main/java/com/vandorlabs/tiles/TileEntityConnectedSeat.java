package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockConnectedSeat;
import net.minecraft.nbt.NBTTagCompound;

/** Height index matches the other chair dialogs; default legs gain one pixel. */
public final class TileEntityConnectedSeat extends TileEntityProgrammableChair {
    private boolean join=true;
    public boolean isJoin(){return join;}
    public void setJoin(boolean value){if(join!=value){join=value;notifyChairChanged();}}
    @Override public int getHeightOffsetPixels(){return 1+super.getHeightOffsetPixels();}
    @Override protected double seatHeight(){
        return world!=null && world.getBlockState(pos).getBlock() instanceof BlockConnectedSeat
                ? ((BlockConnectedSeat)world.getBlockState(pos).getBlock()).seatHeight(world,pos):0;
    }
    @Override protected void notifyChairChanged(){
        super.notifyChairChanged();
        if(world!=null)world.markBlockRangeForRenderUpdate(pos.add(-1,0,-1),pos.add(1,1,1));
    }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager net,net.minecraft.network.play.server.SPacketUpdateTileEntity packet){
        super.onDataPacket(net,packet);
        if(world!=null)world.markBlockRangeForRenderUpdate(pos.add(-1,0,-1),pos.add(1,1,1));
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setBoolean("SeatJoin",join);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);join=!tag.hasKey("SeatJoin")||tag.getBoolean("SeatJoin");}
}
