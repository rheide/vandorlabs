package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockBattery;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.*;

/** Forge Energy buffer with an input front and an output rear. */
public final class TileEntityBattery extends TileEntity implements ITickable {
    public static final int CAPACITY=100000, RATE=1000;
    private int energy;
    private int housingTexture=-1;
    public boolean hasCustomHousingTexture(){return housingTexture>=0;}
    public int getHousingTexture(){return Math.max(0,housingTexture);}
    public void setHousingTexture(int choice){int next=ScreenHousingTextures.clamp(choice);if(housingTexture!=next){housingTexture=next;markDirty();sync();}}
    private boolean blockShape=true,energyDirty;
    private int syncTicks;
    public boolean isBlockShape(){return blockShape;}
    public float chargeFraction(){return energy/(float)CAPACITY;}
    public void setBlockShape(boolean block){if(blockShape!=block){blockShape=block;markDirty();sync();}}
    private void sync(){if(world!=null&&!world.isRemote){net.minecraft.block.state.IBlockState state=world.getBlockState(pos);if(state.getBlock() instanceof BlockBattery)world.notifyBlockUpdate(pos,state,state,3);}}
    private void energyChanged(){markDirty();energyDirty=true;}
    private final IEnergyStorage input=new Port(true,false),output=new Port(false,true),internal=new Port(true,true);
    public EnumFacing inputSide(){return world==null||!(world.getBlockState(pos).getBlock() instanceof BlockBattery)?EnumFacing.NORTH:world.getBlockState(pos).getValue(BlockBattery.FACING);}
    private final class Port implements IEnergyStorage {
        private final boolean receive,extract;
        Port(boolean receive,boolean extract){this.receive=receive;this.extract=extract;}
        public int receiveEnergy(int max,boolean simulate){int amount=receive?Math.min(RATE,Math.min(Math.max(0,max),CAPACITY-energy)):0;if(!simulate && amount>0){energy+=amount;energyChanged();}return amount;}
        public int extractEnergy(int max,boolean simulate){int amount=extract?Math.min(RATE,Math.min(Math.max(0,max),energy)):0;if(!simulate && amount>0){energy-=amount;energyChanged();}return amount;}
        public int getEnergyStored(){return energy;}public int getMaxEnergyStored(){return CAPACITY;}
        public boolean canExtract(){return extract;}public boolean canReceive(){return receive;}
    }
    @Override public void update(){
        if(world==null || world.isRemote)return;
        if(energy>0){EnumFacing side=inputSide().getOpposite();
            if(world.isBlockLoaded(pos.offset(side))){TileEntity neighbor=world.getTileEntity(pos.offset(side));
                IEnergyStorage target=neighbor==null?null:neighbor.getCapability(CapabilityEnergy.ENERGY,side.getOpposite());
                if(target!=null && target.canReceive())output.extractEnergy(target.receiveEnergy(Math.min(RATE,energy),false),false);
            }
        }
        // Coalesce RF transfers into at most four visual updates per second.
        if(++syncTicks>=5){syncTicks=0;if(energyDirty){energyDirty=false;sync();}}
    }
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket(){return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(net.minecraft.network.NetworkManager manager,net.minecraft.network.play.server.SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setInteger("Energy",energy);if(housingTexture>=0)tag.setInteger("HousingTexture",housingTexture);tag.setBoolean("BlockShape",blockShape);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);housingTexture=tag.hasKey("HousingTexture")?ScreenHousingTextures.clamp(tag.getInteger("HousingTexture")):-1;energy=Math.max(0,Math.min(CAPACITY,tag.getInteger("Energy")));blockShape=!tag.hasKey("BlockShape")||tag.getBoolean("BlockShape");}
    @Override public boolean hasCapability(Capability<?> cap,EnumFacing side){return cap==CapabilityEnergy.ENERGY?(side==null || side==inputSide() || side==inputSide().getOpposite()):super.hasCapability(cap,side);}
    @Override public <T>T getCapability(Capability<T> cap,EnumFacing side){if(cap==CapabilityEnergy.ENERGY)return hasCapability(cap,side)?CapabilityEnergy.ENERGY.cast(side==null?internal:side==inputSide()?input:output):null;return super.getCapability(cap,side);}
}
