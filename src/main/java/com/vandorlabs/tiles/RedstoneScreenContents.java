package com.vandorlabs.tiles;

import com.vandorlabs.redstone.*;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import java.util.*;

/** Event-driven row controls. Each row is an independent member of its channel bank. */
public final class RedstoneScreenContents {
    private final TileEntityAnimatedScreenSelector owner;
    private final int slot;
    private boolean enabled;
    public RedstoneScreenContents(TileEntityAnimatedScreenSelector owner,int slot){this.owner=owner;this.slot=slot;}
    public TileEntityAnimatedScreenSelector tile(){return owner;}
    public int slot(){return slot;}
    public boolean enabled(){return enabled;}
    public void setEnabled(boolean value){if(enabled==value)return;enabled=value;if(value)refreshRows();else unregisterRows();changed();}
    public net.minecraft.block.Block getBlockType(){return owner.getBlockType();}
    public net.minecraft.util.math.BlockPos getPos(){return owner.getPos();}
    public int getHousingTexture(){return owner.getHousingTexture();}
    private void setHousingTexture(int value){owner.setHousingTexture(value);}
    private void markDirty(){owner.markDirty();}
    public static final int SLIDER_LABEL=8,SLIDER_LEFT=42,SLIDER_RIGHT=120;
    public static final int MAX_ROWS=8,MAX_LABEL=24,MAX_TITLE=32;
    public static final String DEFAULT_TITLE="REDSTONE CONTROL";
    private String title=DEFAULT_TITLE;
    public String title(){return title;}
    public static boolean validTitle(String title){return validText(title,MAX_TITLE);}
    private static boolean validText(String text,int limit){
        if(text==null || text.length()>limit)return false;
        for(int i=0;i<text.length();i++)if(Character.isISOControl(text.charAt(i)) || text.charAt(i)=='\u00a7')return false;
        return true;
    }
    private List<Row> rows=new ArrayList<>();
    private List<Row> registered=new ArrayList<>();
    private List<Row> view=Collections.unmodifiableList(rows);
    public List<Row> rows(){return view;}
    public int maxRows(){return com.vandorlabs.blocks.RedstoneScreenInteractions.half(owner.getBlockType(),slot)?4:MAX_ROWS;}
    private void limitRows(){if(rows.size()>maxRows()){rows=new ArrayList<>(rows.subList(0,maxRows()));view=Collections.unmodifiableList(rows);markDirty();}}
    public static boolean validLabel(String label){
        return validText(label,MAX_LABEL) && !label.trim().isEmpty();
    }
    public boolean configureRows(List<String> labels,List<ChannelList> channels,int housing) {
        return configure(title,labels,channels,housing);
    }
    public boolean configure(String title,List<String> labels,List<ChannelList> channels,int housing) {
        return configure(title,labels,channels,housing,true);
    }
    private boolean configure(String title,List<String> labels,List<ChannelList> channels,int housing,boolean enable){
        if(!validTitle(title))return false;
        if(labels.size()>maxRows() || labels.size()!=channels.size() || !ScreenHousingTextures.validChoice(housing))return false;
        for(int i=0;i<labels.size();i++)if(!validLabel(labels.get(i)) || channels.get(i)==null)return false;
        List<Row> next=new ArrayList<>();
        for(int i=0;i<labels.size();i++) {
            Row row=new Row(labels.get(i).trim(),channels.get(i));
            if(i<rows.size()){row.latched=rows.get(i).latched.intersect(row.channels);row.levels.putAll(rows.get(i).levels);row.slider=rows.get(i).slider;row.min=rows.get(i).min;row.max=rows.get(i).max;}
            next.add(row);
        }
        enabled=enable;this.title=title.trim();rows=next;view=Collections.unmodifiableList(rows);setHousingTexture(housing);if(enabled)refreshRows();else unregisterRows();changed();return true;
    }
    public NBTTagList rowConfiguration(){
        NBTTagList list=new NBTTagList();
        for(Row row:rows){NBTTagCompound entry=new NBTTagCompound();entry.setString("Label",row.label);ChannelData.write(entry,row.channels);row.writeOptions(entry);list.appendTag(entry);}
        return list;
    }
    public boolean applyRowConfiguration(NBTTagList list){
        return applyRowConfiguration(title,list);
    }
    public boolean applyRowConfiguration(String title,NBTTagList list){
        if(list.tagCount()>MAX_ROWS)return false;
        List<String> labels=new ArrayList<>();List<ChannelList> channels=new ArrayList<>();
        for(int i=0;i<list.tagCount();i++){NBTTagCompound entry=list.getCompoundTagAt(i);labels.add(entry.getString("Label"));channels.add(ChannelData.read(entry,0));}
        if(!validRows(list))return false;
        boolean result=configure(title,labels,channels,getHousingTexture());
        if(result)applyOptions(list);return result;
    }
    public NBTTagCompound configuration(){NBTTagCompound tag=new NBTTagCompound();tag.setBoolean("Enabled",enabled);tag.setString("Title",title);tag.setTag("Rows",rowConfiguration());return tag;}
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
        boolean result=configure(tag.getString("Title"),labels,channels,getHousingTexture(),tag.getBoolean("Enabled") && com.vandorlabs.blocks.RedstoneScreenInteractions.supportsSlot(owner.getBlockType(),slot));
        if(result)applyOptions(list);return result;
    }
    public static boolean validRows(NBTTagList list){
        for(int i=0;i<list.tagCount();i++){
            NBTTagCompound row=list.getCompoundTagAt(i);
            if(row.getBoolean("Slider") && (row.getString("Label").length()>SLIDER_LABEL || row.getInteger("Min")<0 || row.getInteger("Max")>15 || row.getInteger("Min")>row.getInteger("Max")))return false;
        }
        return true;
    }
    private void applyOptions(NBTTagList list){
        for(int i=0;i<rows.size();i++){rows.get(i).readOptions(list.getCompoundTagAt(i));if(owner.getWorld()!=null && !owner.getWorld().isRemote)rows.get(i).setChannelSignal(false);}
        changed();
    }
    public void selectSegment(int rowIndex,int segment){
        if(owner.getWorld()==null || owner.getWorld().isRemote || !enabled || rowIndex<0 || rowIndex>=rows.size())return;
        Row row=rows.get(rowIndex);if(!row.slider || segment<0 || segment>=row.segments())return;
        RedstoneChannels.latchLevelChanged(row,row.segmentValue(segment));
    }
    public void toggleRow(int index) {
        if(owner.getWorld()==null || owner.getWorld().isRemote || !enabled || index<0 || index>=rows.size())return;
        Row row=rows.get(index);if(row.channels.isEmpty() || row.slider)return;
        RedstoneChannels.latchChanged(row,!RedstoneChannels.allPowered(owner.getWorld(),row.channels));
    }
    public void finishLoading(){limitRows();if(enabled)refreshRows();else unregisterRows();}
    private void refreshRows(){
        if(owner.getWorld()==null || owner.getWorld().isRemote || !enabled)return;
        // Register replacements before removing old members so an unchanged bank
        // never briefly loses its only source while its label or order is edited.
        for(Row row:rows)RedstoneChannels.register(row);
        for(Row row:registered)if(!rows.contains(row))RedstoneChannels.unregister(row);
        registered=new ArrayList<>(rows);
        for(Row row:rows)row.setChannelSignal(false);
    }
    private void unregisterRows(){for(Row row:registered)RedstoneChannels.unregister(row);registered.clear();}
    public void invalidate(){unregisterRows();}
    public void onChunkUnload(){unregisterRows();}
    private void changed(){
        markDirty();
        if(owner.getWorld()==null || owner.getWorld().isRemote)return;
        if(SignalUpdateBatch.isActive())SignalUpdateBatch.afterSignals(owner,this::syncRows);else syncRows();
    }
    private void syncRows(){if(owner.getWorld()!=null && !owner.getWorld().isRemote && !owner.isInvalid()){
        net.minecraft.block.state.IBlockState state=owner.getWorld().getBlockState(owner.getPos());owner.getWorld().notifyBlockUpdate(owner.getPos(),state,state,2);
    }}
    public NBTTagCompound writeToNBT(NBTTagCompound tag){
        tag.setBoolean("Enabled",enabled);NBTTagList list=new NBTTagList();
        for(Row row:rows){NBTTagCompound entry=new NBTTagCompound();entry.setString("Label",row.label);ChannelData.write(entry,row.channels);row.writeOptions(entry);
            row.writeLevels(entry);entry.setIntArray("LatchedChannels",row.latched.toArray());entry.setBoolean("Active",row.active);list.appendTag(entry);}
        tag.setString("Title",title);tag.setTag("Rows",list);return tag;
    }
    public void readFromNBT(NBTTagCompound tag){
        enabled=tag.getBoolean("Enabled");title=tag.hasKey("Title",8) && validTitle(tag.getString("Title"))?tag.getString("Title"):DEFAULT_TITLE;List<Row> next=new ArrayList<>();NBTTagList list=tag.getTagList("Rows",10);
        for(int i=0;i<Math.min(MAX_ROWS,list.tagCount());i++){
            NBTTagCompound entry=list.getCompoundTagAt(i);String label=entry.getString("Label");if(!validLabel(label))continue;
            Row row=new Row(label,ChannelData.read(entry,0));
            row.readOptions(entry);row.readLevels(entry);
            row.latched=ChannelData.read(entry,"LatchedChannels",ChannelList.EMPTY).intersect(row.channels);row.active=entry.getBoolean("Active");next.add(row);
        }
        rows=next;view=Collections.unmodifiableList(rows);if(owner.getWorld()!=null)limitRows();
    }
    public final class Row implements RedstoneChannelLatch {
        public final String label;
        public final ChannelList channels;
        private ChannelList latched=ChannelList.EMPTY;
        private boolean active;
        public boolean slider;
        public int min=5,max=15;
        private int level;
        private final Map<Integer,Integer> levels=new HashMap<>();
        public int level(){return level;}
        public int segments(){return 4;}
        public int segmentValue(int index){return TileEntitySignalControl.levelForStep(index,min,max);}
        public int selectedSegment(){return TileEntitySignalControl.stepForLevel(level,min,max);}
        void writeOptions(NBTTagCompound tag){tag.setBoolean("Slider",slider);tag.setInteger("Min",min);tag.setInteger("Max",max);}
        void readOptions(NBTTagCompound tag){
            slider=tag.getBoolean("Slider");max=tag.hasKey("Max",3)?Math.max(3,Math.min(15,tag.getInteger("Max"))):15;
            // Older range sliders included Off in Min and allowed collapsed ranges.
            int savedLow=tag.getInteger("Min");min=Math.max(1,Math.min(max-2,savedLow==0?5:savedLow));
        }
        void writeLevels(NBTTagCompound tag){int[] saved=new int[channels.size()];for(int i=0;i<saved.length;i++)saved[i]=latchedLevel(channels.get(i));tag.setIntArray("Levels",saved);tag.setInteger("Level",level);}
        void readLevels(NBTTagCompound tag){int[] saved=tag.getIntArray("Levels");for(int i=0;i<Math.min(saved.length,channels.size());i++)levels.put(channels.get(i),Math.max(0,Math.min(15,saved[i])));level=Math.max(0,Math.min(15,tag.getInteger("Level")));}
        public int latchedLevel(int channel){return levels.getOrDefault(channel,latched.contains(channel)?15:0);}
        public int localSignalLevel(int channel){return latchedLevel(channel);}
        public void applyLinkedLevels(Map<Integer,Integer> value){levels.clear();levels.putAll(value);RedstoneChannelLatch.super.applyLinkedLevels(value);}

        Row(String label,ChannelList channels){this.label=label;this.channels=channels;}
        public boolean active(){return active;}
        public TileEntity channelTile(){return owner;}
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
        public void setChannelSignal(boolean ignored){
            int nextLevel=0;for(int i=0;i<channels.size();i++)nextLevel=Math.max(nextLevel,RedstoneChannels.level(owner.getWorld(),channels.get(i)));
            if(slider && level!=nextLevel){level=nextLevel;changed();}
            boolean next=RedstoneChannels.allPowered(owner.getWorld(),channels);if(active!=next){active=next;changed();}}
    }
}
