package com.vandorlabs.tiles;

import com.vandorlabs.redstone.*;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import java.util.*;

/** Event-driven row controls. Each row is an independent member of its channel bank. */
public final class TileEntityRedstoneScreen extends TileEntityAnimatedScreenSelector {
    public static final int MAX_ROWS=8,MAX_LABEL=24;
    private List<Row> rows=new ArrayList<>();
    private List<Row> registered=new ArrayList<>();
    private List<Row> view=Collections.unmodifiableList(rows);
    public List<Row> rows(){return view;}
    public static boolean validLabel(String label){
        if(label==null || label.trim().isEmpty() || label.length()>MAX_LABEL)return false;
        for(int i=0;i<label.length();i++)if(Character.isISOControl(label.charAt(i)) || label.charAt(i)=='\u00a7')return false;
        return true;
    }
    public boolean configureRows(List<String> labels,List<ChannelList> channels,int housing) {
        if(labels.size()>MAX_ROWS || labels.size()!=channels.size() || !ScreenHousingTextures.validChoice(housing))return false;
        for(int i=0;i<labels.size();i++)if(!validLabel(labels.get(i)) || channels.get(i)==null)return false;
        List<Row> next=new ArrayList<>();
        for(int i=0;i<labels.size();i++) {
            Row row=new Row(labels.get(i).trim(),channels.get(i));
            if(i<rows.size())row.latched=rows.get(i).latched.intersect(row.channels);
            next.add(row);
        }
        rows=next;view=Collections.unmodifiableList(rows);setHousingTexture(housing);refreshRows();changed();return true;
    }
    public NBTTagList rowConfiguration(){
        NBTTagList list=new NBTTagList();
        for(Row row:rows){NBTTagCompound entry=new NBTTagCompound();entry.setString("Label",row.label);ChannelData.write(entry,row.channels);list.appendTag(entry);}
        return list;
    }
    public boolean applyRowConfiguration(NBTTagList list){
        if(list.tagCount()>MAX_ROWS)return false;
        List<String> labels=new ArrayList<>();List<ChannelList> channels=new ArrayList<>();
        for(int i=0;i<list.tagCount();i++){NBTTagCompound entry=list.getCompoundTagAt(i);labels.add(entry.getString("Label"));channels.add(ChannelData.read(entry,0));}
        return configureRows(labels,channels,getHousingTexture());
    }
    public void toggleRow(int index) {
        if(world==null || world.isRemote || index<0 || index>=rows.size())return;
        Row row=rows.get(index);if(row.channels.isEmpty())return;
        RedstoneChannels.latchChanged(row,!RedstoneChannels.allPowered(world,row.channels));
    }
    @Override protected void finishLoading(){super.finishLoading();refreshRows();}
    private void refreshRows(){
        if(world==null || world.isRemote)return;
        // Register replacements before removing old members so an unchanged bank
        // never briefly loses its only source while its label or order is edited.
        for(Row row:rows)RedstoneChannels.register(row);
        for(Row row:registered)if(!rows.contains(row))RedstoneChannels.unregister(row);
        registered=new ArrayList<>(rows);
        for(Row row:rows)row.setChannelSignal(false);
    }
    private void unregisterRows(){for(Row row:registered)RedstoneChannels.unregister(row);registered.clear();}
    @Override public void invalidate(){unregisterRows();super.invalidate();}
    @Override public void onChunkUnload(){unregisterRows();super.onChunkUnload();}
    private void changed(){
        markDirty();
        if(world==null || world.isRemote)return;
        if(SignalUpdateBatch.isActive())SignalUpdateBatch.afterSignals(this,this::syncRows);else syncRows();
    }
    private void syncRows(){if(world!=null && !world.isRemote && !isInvalid()){
        net.minecraft.block.state.IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,2);
    }}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){
        super.writeToNBT(tag);NBTTagList list=new NBTTagList();
        for(Row row:rows){NBTTagCompound entry=new NBTTagCompound();entry.setString("Label",row.label);ChannelData.write(entry,row.channels);
            entry.setIntArray("LatchedChannels",row.latched.toArray());entry.setBoolean("Active",row.active);list.appendTag(entry);}
        tag.setTag("RedstoneRows",list);return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag){
        super.readFromNBT(tag);List<Row> next=new ArrayList<>();NBTTagList list=tag.getTagList("RedstoneRows",10);
        for(int i=0;i<Math.min(MAX_ROWS,list.tagCount());i++){
            NBTTagCompound entry=list.getCompoundTagAt(i);String label=entry.getString("Label");if(!validLabel(label))continue;
            Row row=new Row(label,ChannelData.read(entry,0));
            row.latched=ChannelData.read(entry,"LatchedChannels",ChannelList.EMPTY).intersect(row.channels);row.active=entry.getBoolean("Active");next.add(row);
        }
        rows=next;view=Collections.unmodifiableList(rows);
        if(world!=null && !world.isRemote)DeferredTileLoad.schedule(this,this::refreshRows);
    }
    public final class Row implements RedstoneChannelLatch {
        public final String label;
        public final ChannelList channels;
        private ChannelList latched=ChannelList.EMPTY;
        private boolean active;
        Row(String label,ChannelList channels){this.label=label;this.channels=channels;}
        public boolean active(){return active;}
        public TileEntity channelTile(){return TileEntityRedstoneScreen.this;}
        public int getRedstoneChannel(){return channels.first();}
        public ChannelList getRedstoneChannels(){return channels;}
        public void setRedstoneChannel(int ignored){throw new UnsupportedOperationException("Configure the screen rows");}
        public boolean hasLocalRedstoneSignal(){return !latched.isEmpty();}
        public boolean hasLocalRedstoneSignal(int channel){return latched.contains(channel);}
        public boolean isChannelLatch(){return true;}
        public boolean latchOn(){return !channels.isEmpty() && latched.containsAll(channels);}
        public ChannelList latchedChannels(){return latched;}
        public void applyLinkedLatch(boolean on){applyLinkedChannels(on?channels:ChannelList.EMPTY);}
        public void applyLinkedChannels(ChannelList value){
            // Latch subsets are server persistence, not visible row state. The
            // settled ALL-power callback sends a packet only if the highlight changes.
            if(!latched.equals(value)){latched=value;markDirty();}
        }
        public void setChannelSignal(boolean ignored){boolean next=RedstoneChannels.allPowered(world,channels);if(active!=next){active=next;changed();}}
    }
}
