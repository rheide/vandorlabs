package com.vandorlabs.tiles;

import com.vandorlabs.appliance.ApplianceAutomation;
import com.vandorlabs.blocks.BlockChipAppliance;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.LoadedRedstonePower;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;

/** Physical chip plus consumables (Printer) or one original document (Scanner). */
public final class TileEntityChipAppliance extends TileEntityTokenSlot implements ITickable, ISidedInventory {
    private static final int[] SCANNER_INPUTS={0,1}, PRINTER_INPUTS={0,1,2};
    private static final int[] SCANNER_OUTPUT={2}, PRINTER_OUTPUT={3}, NO_SLOTS={};
    private final IItemHandler[] ports=new IItemHandler[6];
    private ItemStack output=ItemStack.EMPTY;
    private int activation;
    private boolean inputsReady,signalDirty=true,channelPowered,signalKnown,lastSignal;
    private long nextExport;

    private final ItemStack[] inputs={ItemStack.EMPTY,ItemStack.EMPTY};
    private boolean scanner,busy;public long pressedUntil;public net.minecraft.world.storage.MapData mapPreview;
    public TileEntityChipAppliance(){super(true);}
    public TileEntityChipAppliance(boolean scanner){this();this.scanner=scanner;}
    public boolean scanner(){
        if(world==null)return scanner;
        net.minecraft.block.Block block=world.getBlockState(pos).getBlock();
        return block instanceof BlockChipAppliance?((BlockChipAppliance)block).scanner():scanner;
    }
    @Override public boolean chip(){return true;}
    @Override public int getOutputLevel(){return 0;}
    public int outputSlot(){return scanner()?2:3;}
    @Override public int getSizeInventory(){return outputSlot()+1;}
    @Override public boolean isEmpty(){return super.isEmpty()&&inputs[0].isEmpty()&&inputs[1].isEmpty()&&output.isEmpty();}
    public int slotLimit(int slot){return slot==0||scanner()?1:64;}
    @Override public int getInventoryStackLimit(){return 64;}
    @Override public ItemStack getStackInSlot(int slot){return slot==0?super.getStackInSlot(0):slot==outputSlot()?output:slot>0&&slot<outputSlot()?inputs[slot-1]:ItemStack.EMPTY;}
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack){if(slot==0)return super.isItemValidForSlot(slot,stack);return !stack.isEmpty()&&slot>0&&slot<outputSlot()&&(scanner()?stack.getItem()==Items.FILLED_MAP||stack.getItem()==Items.WRITTEN_BOOK||stack.getItem()==Items.ENCHANTED_BOOK:slot==1?stack.getItem()==Items.DYE&&stack.getMetadata()==0:stack.getItem()==Items.PAPER);}
    @Override public void setInventorySlotContents(int slot,ItemStack stack){if(slot==0){super.setInventorySlotContents(slot,stack);return;}if(slot==outputSlot()){if(stack.isEmpty()||world!=null&&world.isRemote){output=stack.copy();publish();}return;}if(slot<=0||slot>=outputSlot()||!stack.isEmpty()&&!isItemValidForSlot(slot,stack))return;inputs[slot-1]=stack.copy();if(!stack.isEmpty())inputs[slot-1].setCount(Math.min(scanner()?1:64,stack.getCount()));publish();}
    @Override public ItemStack decrStackSize(int slot,int count){if(slot==0)return super.decrStackSize(slot,count);if(slot==outputSlot()){ItemStack taken=output.splitStack(count);if(!taken.isEmpty())publish();return taken;}if(slot<=0||slot>=outputSlot()||count<=0)return ItemStack.EMPTY;ItemStack out=inputs[slot-1].splitStack(count);publish();return out;}
    @Override public ItemStack removeStackFromSlot(int slot){return decrStackSize(slot,64);}
    @Override public void clear(){super.clear();output=ItemStack.EMPTY;for(int i=0;i<inputs.length;i++)inputs[i]=ItemStack.EMPTY;publish();}
    public void publish(){markDirty();if(world!=null&&!world.isRemote&&world.isBlockLoaded(pos))world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),2);}
    private boolean automationAvailable(){
        return world!=null&&!world.isRemote&&!isInvalid()&&world.isBlockLoaded(pos,false)
                &&world.getTileEntity(pos)==this&&!com.vandorlabs.gear.ArmTransactions.locked(world,pos)
                &&!com.vandorlabs.vehicle.VehicleTransfer.isMutating(world);
    }
    public int activation(){return activation;}
    public void configureRedstone(int mode,ChannelList channels){
        if(!automationAvailable()||!ApplianceAutomation.valid(mode)||channels==null||mode==activation&&channels.equals(getRedstoneChannels()))return;
        activation=mode;
        setRedstoneChannels(channels);
        signalKnown=false;
        signalDirty=true;
        publish();
    }
    @Override public void onLoad(){
        super.onLoad();
        DeferredTileLoad.schedule(this,()->{inputsReady=true;signalDirty=true;});
    }
    @Override public void setChannelSignal(boolean powered){channelPowered=powered;signalDirty=true;}
    public void redstoneChanged(){signalDirty=true;}
    @Override public void update(){
        if(world==null||world.isRemote||!inputsReady||(!signalDirty&&output.isEmpty())||!automationAvailable())return;
        if(signalDirty){
            signalDirty=false;
            boolean powered=activation!=ApplianceAutomation.MANUAL&&(channelPowered||LoadedRedstonePower.level(world,pos)>0);
            boolean trigger=signalKnown&&ApplianceAutomation.triggers(activation,lastSignal,powered);
            lastSignal=powered;
            signalKnown=true;
            if(trigger)activate(null);
        }
        if(!output.isEmpty()&&world.getTotalWorldTime()>=nextExport){
            nextExport=world.getTotalWorldTime()+8;
            IItemHandler target=outputTarget();
            if(target!=null&&!busy){
                busy=true;
                try{
                    ItemStack remainder=ItemHandlerHelper.insertItemStacked(target,output.copy(),false);
                    if(remainder.getCount()!=output.getCount()){output=remainder;publish();}
                }finally{busy=false;}
            }
        }
    }
    public void activate(EntityPlayer actor){
        if(busy||!automationAvailable()||!output.isEmpty()||actor!=null&&(!isUsableByPlayer(actor)||actor.getDistanceSqToCenter(pos)>64))return;
        busy=true;
        try{
            pressedUntil=world.getTotalWorldTime()+8;
            publish();
            com.vandorlabs.appliance.ChipApplianceService.activate(this,actor);
        }finally{busy=false;}
    }
    private EnumFacing facing(){return world.getBlockState(pos).getValue(BlockChipAppliance.FACING);}
    private EnumFacing outputSide(){return scanner()?facing().getOpposite():EnumFacing.UP;}
    private IItemHandler outputTarget(){
        EnumFacing side=outputSide();
        BlockPos adjacent=pos.offset(side);
        if(!world.isBlockLoaded(adjacent,false))return null;
        TileEntity neighbor=world.getTileEntity(adjacent);
        return neighbor==null?null:neighbor.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,side.getOpposite());
    }
    public boolean connectedOutput(){return outputTarget()!=null;}
    public void bufferScan(ItemStack result){
        output=result;
        nextExport=0;
        setInventorySlotContents(0,ItemStack.EMPTY);
        publish();
    }
    public void bufferPrint(ItemStack result){
        inputs[0].shrink(1);
        inputs[1].shrink(1);
        output=result;
        nextExport=0;
        publish();
    }
    @Override public int[] getSlotsForFace(EnumFacing side){
        if(world==null||side==null||!(world.getBlockState(pos).getBlock() instanceof BlockChipAppliance))return NO_SLOTS;
        if(side==outputSide())return scanner()?SCANNER_OUTPUT:PRINTER_OUTPUT;
        if(side==facing().rotateY()||side==facing().rotateYCCW())return scanner()?SCANNER_INPUTS:PRINTER_INPUTS;
        return NO_SLOTS;
    }
    @Override public boolean canInsertItem(int slot,ItemStack stack,EnumFacing side){
        return !busy&&automationAvailable()&&slot<outputSlot()&&containsSlot(side,slot)&&isItemValidForSlot(slot,stack);
    }
    @Override public boolean canExtractItem(int slot,ItemStack stack,EnumFacing side){
        return !busy&&automationAvailable()&&slot==outputSlot()&&containsSlot(side,slot);
    }
    private boolean containsSlot(EnumFacing side,int slot){for(int candidate:getSlotsForFace(side))if(candidate==slot)return true;return false;}
    @Override public boolean hasCapability(Capability<?> cap,EnumFacing side){
        return cap==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY?getSlotsForFace(side).length!=0:super.hasCapability(cap,side);
    }
    @Override public <T>T getCapability(Capability<T> cap,EnumFacing side){
        if(cap!=CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)return super.getCapability(cap,side);
        if(getSlotsForFace(side).length==0)return null;
        if(ports[side.ordinal()]==null)ports[side.ordinal()]=new SidedInvWrapper(this,side){
            @Override public int getSlotLimit(int slot){return slotLimit(getSlot(TileEntityChipAppliance.this,slot,side));}
        };
        return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(ports[side.ordinal()]);
    }
    @Override public String getName(){return "tile.vandorlabs."+(scanner()?"scanner":"printer")+".name";}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setBoolean("Scanner",scanner());tag.setInteger("ApplianceActivation",activation);if(!output.isEmpty())tag.setTag("ApplianceOutput",output.writeToNBT(new NBTTagCompound()));tag.setLong("AppliancePressedUntil",pressedUntil);for(int i=0;i<2;i++)if(!inputs[i].isEmpty())tag.setTag("ApplianceInput"+i,inputs[i].writeToNBT(new NBTTagCompound()));return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);scanner=tag.getBoolean("Scanner");activation=tag.getInteger("ApplianceActivation");if(!ApplianceAutomation.valid(activation))activation=0;if(world==null||!world.isRemote)output=tag.hasKey("ApplianceOutput",10)?new ItemStack(tag.getCompoundTag("ApplianceOutput")):ItemStack.EMPTY;signalKnown=false;signalDirty=true;mapPreview=null;if(tag.hasKey("ApplianceMapPreview",10)){mapPreview=new net.minecraft.world.storage.MapData("map_"+tag.getInteger("ApplianceMapId"));mapPreview.readFromNBT(tag.getCompoundTag("ApplianceMapPreview"));}pressedUntil=tag.getLong("AppliancePressedUntil");for(int i=0;i<2;i++)inputs[i]=tag.hasKey("ApplianceInput"+i,10)?new ItemStack(tag.getCompoundTag("ApplianceInput"+i)):ItemStack.EMPTY;}
    @Override public NBTTagCompound getUpdateTag(){NBTTagCompound tag=super.getUpdateTag();tag.removeTag("ApplianceOutput");if(scanner()&&world!=null&&getStackInSlot(1).getItem()==Items.FILLED_MAP){int id=getStackInSlot(1).getMetadata();net.minecraft.world.storage.MapData map=(net.minecraft.world.storage.MapData)world.loadData(net.minecraft.world.storage.MapData.class,"map_"+id);if(map!=null){tag.setInteger("ApplianceMapId",id);tag.setTag("ApplianceMapPreview",map.writeToNBT(new NBTTagCompound()));}}for(int i=0;i<2;i++)if(tag.hasKey("ApplianceInput"+i,10)){ItemStack icon=new ItemStack(tag.getCompoundTag("ApplianceInput"+i));icon.setTagCompound(null);tag.setTag("ApplianceInput"+i,icon.writeToNBT(new NBTTagCompound()));}return tag;}
}
