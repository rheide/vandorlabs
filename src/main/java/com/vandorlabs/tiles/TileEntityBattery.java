package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockBattery;
import com.vandorlabs.ports.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.*;

/** One 100,000 RF owner using the same face policy, balancing and transfer loop as a Power Port. */
public final class TileEntityBattery extends PowerPortTile {
    public static final int CAPACITY=100000,RATE=1000;
    private boolean blockShape=true,energyDirty,legacyMissingPolicy;
    private int legacyRate,legacyFace=-1,legacyHousing=-1;
    private ResourcePortSettings legacySettings;
    private EnumFacing loadedInputSide=EnumFacing.NORTH;
    private boolean facingQueued;
    public TileEntityBattery(){super(CAPACITY);}
    public boolean hasCustomHousingTexture(){return settings().housingChoice>=0;}
    public int getHousingTexture(){return Math.max(0,settings().housingChoice);}
    public void setHousingTexture(int choice){configure(settings().withHousing(ScreenHousingTextures.clamp(choice)),redstoneMode(),getRedstoneChannels());}
    public boolean isBlockShape(){return blockShape;}
    public float chargeFraction(){return energy/(float)CAPACITY;}
    public void setBlockShape(boolean block){if(inert())return;if(blockShape!=block){blockShape=block;markDirty();sync();}}
    public EnumFacing inputSide(){
        // Read-only carried/client views have no server load callback. This is never called by NBT decoding.
        if(world!=null&&world.isRemote){net.minecraft.block.state.IBlockState state=world.getBlockState(pos);return state.getBlock() instanceof BlockBattery?state.getValue(BlockBattery.FACING):EnumFacing.NORTH;}
        return loadedInputSide;
    }
    private void deferFacing(){
        if(facingQueued||world==null||world.isRemote)return;
        facingQueued=true;
        DeferredTileLoad.schedulePriority(this,()->{
            facingQueued=false;
            net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
            if(!(state.getBlock() instanceof BlockBattery))return;
            EnumFacing next=state.getValue(BlockBattery.FACING);boolean changed=next!=loadedInputSide;
            loadedInputSide=next;legacySettings=null;legacyFace=-1;
            if(changed&&legacyMissingPolicy){invalidateNeighbors();sync();world.notifyNeighborsOfStateChange(pos,state.getBlock(),false);}
        });
    }
    @Override public void onLoad(){super.onLoad();facingQueued=false;deferFacing();}
    @Override public void updateContainingBlockInfo(){super.updateContainingBlockInfo();deferFacing();}
    @Override public void invalidate(){facingQueued=false;super.invalidate();}
    @Override public void onChunkUnload(){facingQueued=false;super.onChunkUnload();}
    @Override public ResourcePortSettings settings(){
        if(!legacyMissingPolicy)return super.settings();
        int face=inputSide().getIndex();if(legacySettings==null||legacyFace!=face){legacyFace=face;legacySettings=legacyPolicy(face,legacyHousing);}return legacySettings;
    }
    private static ResourcePortSettings legacyPolicy(int face,int housing){return ResourcePortSettings.placed(face).withTransfer(RATE,false,ResourcePortSettings.OnTexture.TRANSFERRING).withHousing(housing);}
    @Override protected int transferLimit(int maximum){int value=Math.max(0,maximum);return legacyRate==0?value:Math.min(value,legacyRate);}
    @Override protected void energyChanged(){markDirty();energyDirty=true;}
    @Override protected boolean storedAppearanceTick(){boolean changed=energyDirty;energyDirty=false;return changed;}
    @Override protected void configurationChanged(ResourcePortSettings previous,ResourcePortSettings next,boolean signalChanged){
        if(previous.throughput!=next.throughput||previous.pullInputs!=next.pullInputs||!java.util.Arrays.equals(previous.faces(),next.faces()))legacyRate=0;
        legacyMissingPolicy=false;legacySettings=null;
    }
    @Override protected NBTTagCompound normalizedReadData(NBTTagCompound original){
        legacyMissingPolicy=!original.hasKey(ResourcePortData.FACES);legacySettings=null;legacyFace=-1;loadedInputSide=EnumFacing.NORTH;deferFacing();
        if(!legacyMissingPolicy)return original;
        // Never reinterpret a partially written new policy as an old battery.
        for(String key:new String[]{ResourcePortData.RATE,ResourcePortData.PULL,ResourcePortData.ART,ResourcePortData.MODE,ResourcePortData.HOUSING})if(original.hasKey(key))throw new IllegalArgumentException("Incomplete saved Battery face policy");
        legacyHousing=original.hasKey("HousingTexture")?original.getInteger("HousingTexture"):-1;
        if(original.hasKey("HousingTexture")&&(!original.hasKey("HousingTexture",3)||legacyHousing<0||!ScreenHousingTextures.validChoice(legacyHousing)))throw new IllegalArgumentException("Invalid saved Battery housing");
        // Chunk NBT is decoded before its tile map is installed. Looking up even our own state
        // here can recursively load this chunk (and poison LaunchWrapper's class failure cache).
        // Preserve the old raw policy omission; bind the physical facing after chunk loading.
        NBTTagCompound normalized=original.copy();ResourcePortData.write(normalized,legacyPolicy(EnumFacing.NORTH.getIndex(),legacyHousing),0,ChannelList.EMPTY);return normalized;
    }
    @Override protected void readContents(NBTTagCompound tag){
        if(tag.hasKey("PortEnergy")||tag.hasKey("PortFluid")||tag.hasKey("Items"))throw new IllegalArgumentException("Mixed Battery resource owners");
        if(tag.hasKey("Energy")&&(!tag.hasKey("Energy",3)||tag.getInteger("Energy")<0||tag.getInteger("Energy")>CAPACITY))throw new IllegalArgumentException("Invalid saved Battery charge");
        if(tag.hasKey("BlockShape")&&!tag.hasKey("BlockShape",1))throw new IllegalArgumentException("Invalid saved Battery shape");
        legacyRate=legacyMissingPolicy?RATE:tag.getInteger("BatteryLegacyRate");
        if(tag.hasKey("BatteryLegacyRate")&&(!tag.hasKey("BatteryLegacyRate",3)||legacyRate!=0&&legacyRate!=RATE))throw new IllegalArgumentException("Invalid legacy Battery rate");
        energy=tag.getInteger("Energy");blockShape=!tag.hasKey("BlockShape")||tag.getBoolean("BlockShape");energyDirty=false;
    }
    @Override protected void writeConfiguration(NBTTagCompound tag){if(!legacyMissingPolicy)super.writeConfiguration(tag);}
    @Override protected void writeContents(NBTTagCompound tag){tag.setInteger("Energy",energy);tag.setBoolean("BlockShape",blockShape);tag.removeTag("HousingTexture");tag.removeTag("BatteryLegacyRate");if(settings().housingChoice>=0)tag.setInteger("HousingTexture",settings().housingChoice);if(legacyRate!=0&&!legacyMissingPolicy)tag.setInteger("BatteryLegacyRate",legacyRate);}
    @Override protected void writePublicContents(NBTTagCompound tag){tag.setInteger("Energy",energy);tag.setBoolean("BlockShape",blockShape);}
    @Override protected void readPublicContents(NBTTagCompound tag){int value=tag.getInteger("Energy");if(!tag.hasKey("Energy",3)||value<0||value>CAPACITY||!tag.hasKey("BlockShape",1))throw new IllegalArgumentException("Invalid Battery display data");energy=value;blockShape=tag.getBoolean("BlockShape");legacyMissingPolicy=false;legacySettings=null;}
    private int first(ResourcePortSettings.Face face){for(int i=0;i<6;i++)if(settings().face(i)==face)return i;return -1;}
    /** Retain the old unsided energy adapter for storage integrations; it owns no second buffer. */
    private final IEnergyStorage internal=new IEnergyStorage(){
        public int receiveEnergy(int max,boolean simulate){int side=first(ResourcePortSettings.Face.INPUT);if(side<0||!transferAllowed())return 0;int amount=Math.min(transferLimit(max),CAPACITY-energy);if(!simulate&&amount>0){energy+=amount;energyChanged();transferred(side,amount,false);}return amount;}
        public int extractEnergy(int max,boolean simulate){int side=first(ResourcePortSettings.Face.OUTPUT);if(side<0||!transferAllowed())return 0;int amount=Math.min(energy,transferLimit(boundedOutput(side,max)));if(!simulate&&amount>0){energy-=amount;energyChanged();transferred(side,amount,true);}return amount;}
        public int getEnergyStored(){return energy;}public int getMaxEnergyStored(){return CAPACITY;}
        public boolean canExtract(){return !inert()&&first(ResourcePortSettings.Face.OUTPUT)>=0;}public boolean canReceive(){return !inert()&&first(ResourcePortSettings.Face.INPUT)>=0;}
    };
    @Override public boolean hasCapability(Capability<?> cap,EnumFacing side){return cap==CapabilityEnergy.ENERGY&&side==null?!inert():super.hasCapability(cap,side);}
    @Override public <T>T getCapability(Capability<T> cap,EnumFacing side){return cap==CapabilityEnergy.ENERGY&&side==null?!inert()?CapabilityEnergy.ENERGY.cast(internal):null:super.getCapability(cap,side);}
}
