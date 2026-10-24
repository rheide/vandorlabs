package com.vandorlabs.tiles;

import com.vandorlabs.redstone.*;
import com.vandorlabs.persistence.NbtPrimitiveData;
import com.vandorlabs.persistence.PrimitiveData;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import java.util.*;

/** Forge owner/lifecycle and NBT adapter for the shared row controls. */
public final class RedstoneScreenContents {
    private final TileEntityAnimatedScreenSelector owner;
    private final int slot;
    private final RedstoneScreenModel<Row> model;
    public RedstoneScreenContents(TileEntityAnimatedScreenSelector owner,int slot){this.owner=owner;this.slot=slot;model=new RedstoneScreenModel<>(new ModelOwner());}
    public TileEntityAnimatedScreenSelector tile(){return owner;}
    public int slot(){return slot;}
    public boolean enabled(){return model.enabled();}
    public void setEnabled(boolean value){model.setEnabled(value);}
    public net.minecraft.block.Block getBlockType(){return owner.getBlockType();}
    public net.minecraft.util.math.BlockPos getPos(){return owner.getPos();}
    public int getHousingTexture(){return owner.getHousingTexture();}
    public static final int SLIDER_LABEL=RedstoneScreenPolicy.SLIDER_LABEL,SLIDER_LABEL_WIDTH=RedstoneScreenPolicy.SLIDER_LABEL_WIDTH,SLIDER_LEFT=RedstoneScreenPolicy.SLIDER_LEFT,SLIDER_RIGHT=RedstoneScreenPolicy.SLIDER_RIGHT;
    public static final int MAX_ROWS=RedstoneScreenPolicy.MAX_ROWS,MAX_LABEL=RedstoneScreenPolicy.MAX_LABEL,MAX_TITLE=RedstoneScreenPolicy.MAX_TITLE;
    public static final String DEFAULT_TITLE=RedstoneScreenPolicy.DEFAULT_TITLE;
    public String title(){return model.title();}
    public static boolean validTitle(String title){return RedstoneScreenPolicy.validTitle(title);}
    public static boolean validLabel(String label){return RedstoneScreenPolicy.validLabel(label);}
    public List<Row> rows(){return model.rows();}
    public int maxRows(){return com.vandorlabs.blocks.RedstoneScreenInteractions.half(owner.getBlockType(),slot)?4:MAX_ROWS;}
    public boolean configureRows(List<String> labels,List<ChannelList> channels,int housing){return configure(title(),labels,channels,housing);}
    public boolean configure(String title,List<String> labels,List<ChannelList> channels,int housing){return model.configure(title,labels,channels,housing,true);}
    public NBTTagList rowConfiguration(){
        NBTTagList list=new NBTTagList();
        for(Row row:rows()){NBTTagCompound entry=new NBTTagCompound();row.writeConfiguration(new NbtPrimitiveData(entry));ChannelData.write(entry,row.channels);list.appendTag(entry);}
        return list;
    }
    public boolean applyRowConfiguration(NBTTagList list){return applyRowConfiguration(title(),list);}
    public boolean applyRowConfiguration(String title,NBTTagList list){
        if(list.tagCount()>MAX_ROWS)return false;
        List<String> labels=new ArrayList<>();List<ChannelList> channels=new ArrayList<>();
        for(int i=0;i<list.tagCount();i++){NBTTagCompound entry=list.getCompoundTagAt(i);labels.add(entry.getString("Label"));channels.add(ChannelData.read(entry,0));}
        if(!validRows(list))return false;
        boolean result=configure(title,labels,channels,getHousingTexture());if(result)model.applyOptions(options(list));return result;
    }
    public NBTTagCompound configuration(){NBTTagCompound tag=new NBTTagCompound();model.writeMetadata(new NbtPrimitiveData(tag));tag.setTag("Rows",rowConfiguration());return tag;}
    public static boolean validConfiguration(NBTTagCompound tag){
        if(!validTitle(tag.getString("Title")))return false;
        NBTTagList list=tag.getTagList("Rows",10);if(list.tagCount()>MAX_ROWS)return false;
        for(int i=0;i<list.tagCount();i++)if(!validLabel(list.getCompoundTagAt(i).getString("Label")))return false;
        return validRows(list);
    }
    public boolean applyConfiguration(NBTTagCompound tag){
        if(!validConfiguration(tag))return false;
        NBTTagList list=tag.getTagList("Rows",10);List<String> labels=new ArrayList<>();List<ChannelList> channels=new ArrayList<>();
        for(int i=0;i<Math.min(maxRows(),list.tagCount());i++){NBTTagCompound row=list.getCompoundTagAt(i);labels.add(row.getString("Label"));channels.add(ChannelData.read(row,0));}
        boolean result=model.configure(tag.getString("Title"),labels,channels,getHousingTexture(),tag.getBoolean("Enabled")&&com.vandorlabs.blocks.RedstoneScreenInteractions.supportsSlot(owner.getBlockType(),slot));
        if(result)model.applyOptions(options(list));return result;
    }
    public static boolean validRows(NBTTagList list){
        for(int i=0;i<list.tagCount();i++){
            NBTTagCompound row=list.getCompoundTagAt(i);
            if(!RedstoneScreenPolicy.validOptions(row.getString("Label"),row.getBoolean("Slider"),row.getBoolean("Pulse"),row.getInteger("Min"),row.getInteger("Max")))return false;
        }
        return true;
    }
    private static List<PrimitiveData> options(NBTTagList list){List<PrimitiveData> result=new ArrayList<>();for(int i=0;i<list.tagCount();i++)result.add(new NbtPrimitiveData(list.getCompoundTagAt(i)));return result;}
    public void selectSegment(int row,int segment){model.selectSegment(row,segment);}
    public void toggleRow(int row){model.toggleRow(row);}
    public void pulseRow(int row){model.pulseRow(row);}
    public boolean expirePulses(long now){return model.expirePulses(now);}
    public boolean hasPulses(){return model.hasPulses();}
    public void finishLoading(){model.finishLoading();}
    public void invalidate(){model.unregisterRows();ScreenPulseActivity.remove(this);}
    public void onChunkUnload(){model.unregisterRows();ScreenPulseActivity.remove(this);}
    private net.minecraft.world.World serverWorld(){return owner.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)owner.getWorld()).parent:owner.getWorld();}
    private void changed(){
        owner.markDirty();
        if(owner.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld){net.minecraft.block.state.IBlockState state=owner.getWorld().getBlockState(owner.getPos());owner.getWorld().notifyBlockUpdate(owner.getPos(),state,state,2);return;}
        if(owner.getWorld()==null||owner.getWorld().isRemote)return;
        if(SignalUpdateBatch.isActive())SignalUpdateBatch.afterSignals(owner,this::syncRows);else syncRows();
    }
    private void syncRows(){if(owner.getWorld()!=null&&!owner.getWorld().isRemote&&!owner.isInvalid()){
        net.minecraft.block.state.IBlockState state=owner.getWorld().getBlockState(owner.getPos());owner.getWorld().notifyBlockUpdate(owner.getPos(),state,state,2);
    }}
    public NBTTagCompound writeToNBT(NBTTagCompound tag){
        model.writeMetadata(new NbtPrimitiveData(tag));NBTTagList list=new NBTTagList();
        for(Row row:rows()){NBTTagCompound entry=new NBTTagCompound();row.writeState(new NbtPrimitiveData(entry));ChannelData.write(entry,row.channels);
            entry.setIntArray(RedstoneControlRow.LEVELS,row.savedLevels());entry.setIntArray(RedstoneControlRow.LATCHED_CHANNELS,row.latchedChannels().toArray());list.appendTag(entry);}
        tag.setTag("Rows",list);return tag;
    }
    public void readFromNBT(NBTTagCompound tag){
        List<Row> next=new ArrayList<>();NBTTagList list=tag.getTagList("Rows",10);
        for(int i=0;i<Math.min(MAX_ROWS,list.tagCount());i++){
            NBTTagCompound entry=list.getCompoundTagAt(i);String label=entry.getString("Label");if(!validLabel(label))continue;
            Row row=new Row(label,ChannelData.read(entry,0));row.readState(new NbtPrimitiveData(entry),entry.getIntArray(RedstoneControlRow.LEVELS),ChannelData.read(entry,RedstoneControlRow.LATCHED_CHANNELS,ChannelList.EMPTY));next.add(row);
        }
        model.restore(tag.getBoolean("Enabled"),tag.hasKey("Title",8)?tag.getString("Title"):null,next);
    }
    private final RedstoneControlRow.Owner rowOwner=new RedstoneControlRow.Owner(){
        public boolean installed(){return !owner.isInvalid()&&owner.getWorld()!=null&&owner.getWorld().isBlockLoaded(owner.getPos())&&owner.getWorld().getTileEntity(owner.getPos())==owner;}
        public boolean hasWorld(){return owner.getWorld()!=null;}
        public long time(){return owner.getWorld().getTotalWorldTime();}
        public int channelLevel(int channel){return RedstoneChannels.level(owner.getWorld(),channel);}
        public boolean allPowered(ChannelList channels){return RedstoneChannels.allPowered(owner.getWorld(),channels);}
        public void dirty(){owner.markDirty();}
        public void changed(){RedstoneScreenContents.this.changed();}
    };
    private final class ModelOwner implements RedstoneScreenModel.Owner<Row>{
        public Row createRow(String label,ChannelList channels){return new Row(label,channels);}
        public int maxRows(){return RedstoneScreenContents.this.maxRows();}
        public boolean validHousing(int choice){return ScreenHousingTextures.validChoice(choice);}
        public int housing(){return getHousingTexture();}
        public void housing(int choice){owner.setHousingTexture(choice);}
        public boolean hasWorld(){return owner.getWorld()!=null;}
        public boolean authoritative(){return owner.getWorld()!=null&&!owner.getWorld().isRemote;}
        public boolean canPulse(){return serverWorld()!=null&&!serverWorld().isRemote;}
        public boolean publishedLocally(){return !(owner.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld);}
        public long serverTime(){return serverWorld().getTotalWorldTime();}
        public void dirty(){owner.markDirty();}
        public void changed(){RedstoneScreenContents.this.changed();}
        public void register(Row row){RedstoneChannels.register(row);}
        public void unregister(Row row){RedstoneChannels.unregister(row);}
        public void inputChanged(Row row){RedstoneChannels.inputChanged(row);}
        public void latchLevelChanged(Row row,int level){RedstoneChannels.latchLevelChanged(row,level);}
        public boolean allPowered(ChannelList channels){return RedstoneChannels.allPowered(owner.getWorld(),channels);}
        public void activatePulses(){ScreenPulseActivity.activate(RedstoneScreenContents.this);}
    }
    public final class Row extends RedstoneControlRow implements RedstoneChannelLatch{
        Row(String label,ChannelList channels){super(rowOwner,label,channels);}
        void writeOptions(NBTTagCompound tag){super.writeOptions(new NbtPrimitiveData(tag));}
        void readOptions(NBTTagCompound tag){super.readOptions(new NbtPrimitiveData(tag));}
        void writeLevels(NBTTagCompound tag){tag.setIntArray("Levels",savedLevels());tag.setInteger("Level",level());}
        void readLevels(NBTTagCompound tag){super.readLevels(tag.getIntArray("Levels"),tag.getInteger("Level"));}
        public TileEntity channelTile(){return owner;}
        public int getRedstoneChannel(){return channels.first();}
        public void setRedstoneChannel(int ignored){throw new UnsupportedOperationException("Configure the screen rows");}
        public boolean hasLocalRedstoneSignal(){return pulse?active():!latchedChannels().isEmpty();}
        public boolean hasLocalRedstoneSignal(int channel){return pulse?active():latchedChannels().contains(channel);}
        public void setChannelSignal(boolean ignored){settle();}
    }
}
