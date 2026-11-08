package com.vandorlabs.segmented;

import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.DeferredTileLoad;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** One cached top owner per column; topology changes and power reads are deferred. */
public final class SegmentedDoorTile extends TileEntity implements net.minecraft.util.ITickable,RedstoneChannelMember {
    public static final int MAX_HEIGHT=32,MAX_COLUMNS=32;
    public final com.vandorlabs.blocks.ContainedFluids fluids=new com.vandorlabs.blocks.ContainedFluids(this);
    public int waterOutlets;
    public UUID motionIdentity;
    public int topOffset,height=1,ticksPerSection=10,texture=-1;
    public int column,columns=1;
    public SegmentedCellArtwork cellArtwork=SegmentedCellArtwork.INHERITED;
    private net.minecraft.nbt.NBTBase opaqueCellArtwork;
    public int effectiveTexture(){return cellArtwork.effective(texture);}
    private void readCellArtwork(NBTTagCompound tag){SegmentedCellArtwork next=tag.hasKey(SegmentedCellArtwork.FIELD)?SegmentedCellArtwork.explicit(requireCellTexture(tag),SegmentedDoorTile::validTexture):SegmentedCellArtwork.INHERITED;cellArtwork=next;opaqueCellArtwork=null;}
    private static int requireCellTexture(NBTTagCompound tag){if(!tag.hasKey(SegmentedCellArtwork.FIELD,3))throw new IllegalArgumentException("Invalid segment artwork type");return tag.getInteger(SegmentedCellArtwork.FIELD);}
    private void readSavedCellArtwork(NBTTagCompound tag){try{readCellArtwork(tag);}catch(IllegalArgumentException unsupported){cellArtwork=SegmentedCellArtwork.INHERITED;opaqueCellArtwork=tag.getTag(SegmentedCellArtwork.FIELD).copy();}}
    private void writeCellArtwork(NBTTagCompound tag){if(opaqueCellArtwork!=null&&!cellArtwork.overridden)tag.setTag(SegmentedCellArtwork.FIELD,opaqueCellArtwork.copy());else if(cellArtwork.overridden)tag.setInteger(SegmentedCellArtwork.FIELD,cellArtwork.choice);else tag.removeTag(SegmentedCellArtwork.FIELD);}
    public SegmentedDoorSettings settings=SegmentedDoorSettings.DEFAULT;
    public boolean targetOpen,localRedstone=true,signalOpens=true,formed,configured;
    public double from;
    public long started,sequence;
    public int duration=1;
    public UUID identity=UUID.randomUUID();
    private ChannelList channels=ChannelList.EMPTY;
    private boolean channelSignal,pending,powerPending,configuring,ready;
    private long powerRevision,powerHandledRevision=-1;
    /** Client publication flags; never saved or authoritative. */
    public volatile boolean clientBaked;
    public boolean clientSuppressed,clientAwaitingChunk;
    public boolean ready(){return !isInvalid()&&world!=null&&(!world.isRemote||ready);}
    public boolean isRoot(){return topOffset==0;}
    public SegmentedDoorTile root(){SegmentedDoorTile tile=topOffset==0?this:at(world,pos.up(topOffset));return tile==null?this:tile;}
    public static SegmentedDoorTile at(World world,BlockPos pos){if(world==null||!world.isBlockLoaded(pos))return null;TileEntity tile=world.getTileEntity(pos);return tile instanceof SegmentedDoorTile?(SegmentedDoorTile)tile:null;}
    public EnumFacing facing(){net.minecraft.block.state.IBlockState state=world==null?null:world.getBlockState(pos);return state!=null&&state.getBlock() instanceof SegmentedDoorBlock?state.getValue(SegmentedDoorBlock.FACING):EnumFacing.NORTH;}
    private SegmentedDoorTile neighbor(EnumFacing face){SegmentedDoorTile tile=at(world,pos.offset(face));return tile!=null&&tile.facing()==facing()?tile:null;}
    public double pose(double partial){double t=Math.max(0,Math.min(1,(world.getTotalWorldTime()-started+partial)/Math.max(1,duration)));return from+((targetOpen?1:0)-from)*t;}
    public boolean closed(){SegmentedDoorTile root=root();return !root.targetOpen&&root.pose(0)<=0;}
    public void scheduleRefresh(){if(world==null||world.isRemote||pending)return;pending=true;DeferredTileLoad.schedule(this,()->{pending=false;refreshColumn();requestPower();});}
    private void requestPower(){SegmentedDoorTile owner=root();if(world==null||world.isRemote)return;owner.powerRevision++;if(owner.powerPending||owner.configuring)return;owner.powerPending=true;DeferredTileLoad.schedule(owner,()->{owner.powerPending=false;owner.refreshPower();});}
    public void refreshColumn(){
        if(world==null||world.isRemote)return;
        SegmentedDoorTile top=this,next;int count=1;
        while((next=top.neighbor(EnumFacing.UP))!=null){if(++count>MAX_HEIGHT)return;top=next;}
        List<SegmentedDoorTile> members=new ArrayList<>();SegmentedDoorTile source=top;
        for(SegmentedDoorTile member=top;member!=null;member=member.neighbor(EnumFacing.DOWN)){
            if(members.size()>=MAX_HEIGHT)return;members.add(member);
            if(member.formed&&(!source.formed||member.configured&&!source.configured||member.height>source.height))source=member;
        }
        boolean topology=false;for(int i=0;i<members.size();i++)if(!members.get(i).formed||members.get(i).topOffset!=i||members.get(i).height!=members.size())topology=true;
        if(!topology){top.refreshSpan();return;}
        double current=source.pose(0);boolean target=source.targetOpen;long nextSequence=source.sequence+1;UUID id=source.identity;ChannelList selected=source.channels;int speed=source.ticksPerSection;boolean local=source.localRedstone,invert=source.signalOpens,custom=source.configured;
        for(int i=0;i<members.size();i++){
            SegmentedDoorTile member=members.get(i);boolean wasFormed=member.formed,wasConfigured=member.configured;ChannelList old=member.channels;member.topOffset=i;member.height=members.size();member.formed=true;member.from=current;member.targetOpen=target;member.started=world.getTotalWorldTime();member.duration=Math.max(1,(int)Math.ceil(member.height*speed*Math.abs((target?1:0)-current)));member.sequence=nextSequence;member.identity=id;member.channels=selected;member.ticksPerSection=speed;member.localRedstone=local;member.signalOpens=invert;member.configured=custom||wasConfigured;member.texture=source.texture;member.settings=wasFormed||wasConfigured?member.settings.shared(source.settings):source.settings;member.markDirty();member.sync();RedstoneChannels.channelChanged(member,old);
        }
        top.refreshSpan();
    }
    private void refreshSpan(){
        SegmentedDoorWindows.invalidate(world);
        List<SegmentedDoorTile> roots;try{roots=columns();}catch(IllegalArgumentException incomplete){return;}EnumFacing right=facing().rotateY();roots.sort(java.util.Comparator.comparingInt(t->t.pos.getX()*right.getFrontOffsetX()+t.pos.getZ()*right.getFrontOffsetZ()));
        for(int index=0;index<roots.size();index++){SegmentedDoorTile root=roots.get(index);for(int row=0;row<root.height;row++){SegmentedDoorTile member=at(world,root.pos.down(row));if(member!=null&&(member.column!=index||member.columns!=roots.size())){member.column=index;member.columns=roots.size();member.sync();}}}
    }
    public SegmentedDoorTile panel(int row){SegmentedDoorTile member=at(world,pos.down(row));return member!=null&&member.topOffset==row&&member.height==height?member:this;}
    public boolean hasGlass(){for(int row=0;row<height;row++)if(panel(row).settings.window||panel(row).frameTexture())return true;return false;}
    public boolean frameTexture(){int selected=effectiveTexture();String category=selected<0?"":com.vandorlabs.tiles.ScreenHousingTextures.category(selected);return "Frame".equals(category)||"Frames".equals(category);}
    private boolean uniformArtwork=true;
    /** Sampled by canBake's existing bounded row pass, avoiding a second warm lookup pass. */
    public boolean uniformArtwork(){return uniformArtwork;}
    public boolean canBake(){if(settings.treadPixels!=16||settings.position!=0||settings.direction!=0||settings.straight)return false;uniformArtwork=true;int first=effectiveTexture();for(int row=0;row<height;row++){SegmentedDoorTile member=panel(row);if(SegmentedArtwork.complex(member.effectiveTexture())||!member.settings.nativePanel()||member.effectiveTexture()>=com.vandorlabs.tiles.ScreenHousingTextures.BUILTIN_COUNT||member.frameTexture())return false;if(member.effectiveTexture()!=first)uniformArtwork=false;}return true;}
    public List<SegmentedDoorTile> columns(){
        SegmentedDoorTile root=root();if(!root.formed)throw new IllegalArgumentException("Complete the segmented column (maximum 32 sections)");LinkedHashSet<SegmentedDoorTile> result=new LinkedHashSet<>();ArrayDeque<SegmentedDoorTile> queue=new ArrayDeque<>();result.add(root);queue.add(root);
        while(!queue.isEmpty()){
            SegmentedDoorTile current=queue.removeFirst();EnumFacing right=current.facing().rotateY();
            for(EnumFacing side:new EnumFacing[]{right,right.getOpposite()}){
                if(!world.isBlockLoaded(current.pos.offset(side)))throw new IllegalArgumentException("Load the complete segmented door before changing it");SegmentedDoorTile other=current.neighbor(side);if(other==null||!other.isRoot()||other.height!=root.height||result.contains(other))continue;
                if(result.size()>=MAX_COLUMNS)throw new IllegalArgumentException("Segmented door supports at most 32 linked columns");result.add(other);queue.add(other);
            }
        }
        return new ArrayList<>(result);
    }
    private List<SegmentedDoorTile> members(List<SegmentedDoorTile> roots){List<SegmentedDoorTile> result=new ArrayList<>();for(SegmentedDoorTile root:roots)for(int row=0;row<root.height;row++){SegmentedDoorTile member=at(world,root.pos.down(row));if(member==null||member.facing()!=root.facing()||member.height!=root.height||member.topOffset!=row)throw new IllegalArgumentException("Load the complete segmented door before changing it");result.add(member);}return result;}
    private void permissions(EntityPlayer player,List<SegmentedDoorTile> members){for(SegmentedDoorTile tile:members)if(!world.isBlockModifiable(player,tile.pos)||!player.canPlayerEdit(tile.pos,EnumFacing.UP,player.getHeldItemMainhand()))throw new IllegalArgumentException("Segmented door is protected");}
    public void toggle(EntityPlayer player){if(root().settings.activation>0)throw new IllegalArgumentException("This door is controlled by redstone");List<SegmentedDoorTile> roots=columns();permissions(player,members(roots));boolean open=!root().targetOpen;moveTogether(roots,open);}
    public void operate(EntityPlayer player,boolean open){if(root().settings.activation>0)throw new IllegalArgumentException("This door is controlled by redstone");List<SegmentedDoorTile> roots=columns();permissions(player,members(roots));moveTogether(roots,open);}
    public void configure(EntityPlayer player,ChannelList next,int ticks,boolean local,boolean opens){
        if(ticks<5||ticks>30||next==null)throw new IllegalArgumentException("Invalid segmented door settings");
        List<SegmentedDoorTile> roots=columns(),members=members(roots);permissions(player,members);for(SegmentedDoorTile root:roots)root.configuring=true;
        try{for(SegmentedDoorTile tile:members){ChannelList old=tile.channels;tile.channels=next;tile.ticksPerSection=ticks;tile.localRedstone=local;tile.signalOpens=opens;tile.settings=tile.settings.activation(-1);tile.configured=true;tile.markDirty();tile.sync();RedstoneChannels.channelChanged(tile,old);}}
        finally{for(SegmentedDoorTile root:roots)root.configuring=false;}
        root().powerRevision++;root().refreshPower();
    }
    public static boolean validTexture(int choice){return choice==-1||com.vandorlabs.tiles.CustomBlockMaterials.isCustom(choice)||com.vandorlabs.tiles.ScreenHousingTextures.validChoice(choice)&&com.vandorlabs.tiles.ScreenHousingTextures.visible(choice)&&(com.vandorlabs.tiles.ScreenHousingTextures.generalTexture(choice)||com.vandorlabs.tiles.HousingCategoryPolicy.door(com.vandorlabs.tiles.ScreenHousingTextures.category(choice))||com.vandorlabs.tiles.HousingCategoryPolicy.doubleDoor(com.vandorlabs.tiles.ScreenHousingTextures.category(choice))||"Frames".equals(com.vandorlabs.tiles.ScreenHousingTextures.category(choice))||"Frame".equals(com.vandorlabs.tiles.ScreenHousingTextures.category(choice)));}
    public void configureTexture(EntityPlayer player,int choice){
        if(!validTexture(choice))throw new IllegalArgumentException("Invalid Segmented Door texture");
        List<SegmentedDoorTile> all=members(columns());permissions(player,all);for(SegmentedDoorTile tile:all){tile.texture=choice;tile.cellArtwork=SegmentedCellArtwork.INHERITED;tile.opaqueCellArtwork=null;tile.configured=true;tile.markDirty();tile.sync();}
    }
    public void configureCellTexture(EntityPlayer player,int choice,boolean inherit){SegmentedCellArtwork next=inherit?SegmentedCellArtwork.INHERITED:SegmentedCellArtwork.explicit(choice,SegmentedDoorTile::validTexture);List<SegmentedDoorTile> all=members(columns());permissions(player,all);cellArtwork=next;opaqueCellArtwork=null;configured=true;markDirty();sync();}
    /** Portable configuration only; identities, motion, spans and stored fluids stay with their owner. */
    public NBTTagCompound configuration(){NBTTagCompound tag=new NBTTagCompound();settings.write(tag);writeCellArtwork(tag);tag.setInteger("SegmentedTexture",texture);tag.setInteger("SegmentedSpeed",ticksPerSection);tag.setBoolean("SegmentedLocalRedstone",localRedstone);tag.setBoolean("SegmentedSignalOpens",signalOpens);tag.setBoolean("SegmentedConfigured",true);return tag;}
    public NBTTagCompound itemSettings(){NBTTagCompound tag=configuration();ChannelData.write(tag,channels);return tag;}
    public void applyItemConfiguration(NBTTagCompound tag,ChannelList selected){readCellArtwork(tag);settings=SegmentedDoorSettings.read(tag);int choice=tag.getInteger("SegmentedTexture");texture=tag.hasKey("SegmentedTexture",3)&&validTexture(choice)?choice:-1;ticksPerSection=Math.max(5,Math.min(30,tag.getInteger("SegmentedSpeed")));localRedstone=tag.getBoolean("SegmentedLocalRedstone");signalOpens=tag.getBoolean("SegmentedSignalOpens");channels=selected;configured=true;}
    public void configurePart(EntityPlayer player,ChannelList channels,int ticks,boolean local,boolean opens,int texture,SegmentedDoorSettings settings){configureSettings(player,channels,ticks,local,opens,texture,settings,true);}
    public void configureAll(EntityPlayer player,ChannelList channels,int ticks,boolean local,boolean opens,int texture,SegmentedDoorSettings settings){configureSettings(player,channels,ticks,local,opens,texture,settings,false);}
    public void configurePartArtwork(EntityPlayer player,ChannelList channels,int ticks,boolean local,boolean opens,int texture,SegmentedDoorSettings settings,SegmentedCellArtwork artwork){if(artwork==null)throw new IllegalArgumentException("Missing segment artwork");configureSettings(player,channels,ticks,local,opens,texture,settings,true,artwork);}
    private void configureSettings(EntityPlayer player,ChannelList channels,int ticks,boolean local,boolean opens,int texture,SegmentedDoorSettings settings,boolean part){configureSettings(player,channels,ticks,local,opens,texture,settings,part,null);}
    private void configureSettings(EntityPlayer player,ChannelList channels,int ticks,boolean local,boolean opens,int texture,SegmentedDoorSettings settings,boolean part,SegmentedCellArtwork artwork){
        if(artwork!=null&&artwork.overridden&&!validTexture(artwork.choice))throw new IllegalArgumentException("Invalid segment artwork");
        SegmentedDoorWindows.invalidate(world);
        if(channels==null||ticks<5||ticks>30||!validTexture(texture)||settings==null)throw new IllegalArgumentException("Invalid Segmented Door settings");
        List<SegmentedDoorTile> roots=columns(),all=members(roots);permissions(player,all);for(SegmentedDoorTile root:roots)root.configuring=true;
        try{for(SegmentedDoorTile tile:all){SegmentedDoorSettings selected=part&&tile!=this?tile.settings.shared(settings):settings;if(tile.configured&&tile.channels.equals(channels)&&tile.ticksPerSection==ticks&&tile.localRedstone==local&&tile.signalOpens==opens&&tile.texture==texture&&tile.settings.equals(selected)&&(tile!=this||artwork==null||tile.cellArtwork.equals(artwork)))continue;ChannelList old=tile.channels;tile.channels=channels;tile.ticksPerSection=ticks;tile.localRedstone=local;tile.signalOpens=opens;tile.texture=texture;if(tile==this&&artwork!=null){tile.cellArtwork=artwork;tile.opaqueCellArtwork=null;}tile.settings=selected;tile.fluids.invalidateLocalGeometry();tile.configured=true;tile.markDirty();tile.sync();RedstoneChannels.channelChanged(tile,old);}}finally{for(SegmentedDoorTile root:roots)root.configuring=false;}
        root().powerRevision++;root().refreshPower();
    }
    private void refreshPower(){
        if(world==null||world.isRemote||configuring)return;
        List<SegmentedDoorTile> roots;
        try{roots=columns();}catch(IllegalArgumentException tooWide){return;}
        boolean changed=false;for(SegmentedDoorTile root:roots)changed|=root.powerHandledRevision!=root.powerRevision;if(!changed)return;
        boolean signal=false,enabled=false;
        List<SegmentedDoorTile> loaded;try{loaded=members(roots);}catch(IllegalArgumentException incomplete){return;}
        for(SegmentedDoorTile member:loaded){enabled|=member.localRedstone||!member.channels.isEmpty();signal|=member.channelSignal||member.localRedstone&&world.isBlockPowered(member.pos);}
        for(SegmentedDoorTile root:roots)root.powerHandledRevision=root.powerRevision;
        int activation=root().settings.activation;if(activation==0)return;
        if(enabled||activation>0){boolean open=activation<0?signal==root().signalOpens:activation==1?signal:!signal;moveTogether(roots,open);}
    }
    private static void moveTogether(List<SegmentedDoorTile> roots,boolean open){
        boolean changing=false;for(SegmentedDoorTile root:roots)changing|=root.targetOpen!=open;if(!changing)return;
        SegmentedDoorTile leader=roots.get(0);double current=leader.pose(0);long started=leader.world.getTotalWorldTime();int ticks=leader.ticksPerSection,duration=Math.max(1,(int)Math.ceil((leader.settings.direction<2?leader.height:leader.columns)*ticks*Math.abs((open?1:0)-current)));UUID motion=UUID.randomUUID();
        for(SegmentedDoorTile root:roots)root.move(open,motion,current,started,duration,ticks);
    }
    private void move(boolean open,UUID motion,double current,long start,int length,int ticks){
        motionIdentity=motion;targetOpen=open;from=current;started=start;duration=length;ticksPerSection=ticks;sequence++;
        mirror();sync();
    }
    private void mirror(){for(int row=0;row<height;row++){SegmentedDoorTile member=at(world,pos.down(row));if(member==null)continue;member.motionIdentity=motionIdentity;member.ticksPerSection=ticksPerSection;member.from=from;member.targetOpen=targetOpen;member.started=started;member.duration=duration;member.sequence=sequence;member.fluids.invalidateLocalGeometry();if(member.fluids.any()||member.waterOutlets!=0)com.vandorlabs.blocks.DoorWaterGush.schedule(world,member.pos);member.markDirty();}}
    @Override public void update(){if(world==null||world.isRemote||!isRoot()||from==(targetOpen?1:0))return;if(world.getTotalWorldTime()-started>=duration){from=targetOpen?1:0;mirror();sync();}}
    @Override public void onLoad(){super.onLoad();SegmentedDoorInteractions.INDEX.add(this);com.vandorlabs.VandorLabs.proxy.segmentedAppearanceChanged(this);scheduleRefresh();DeferredTileLoad.schedule(this,()->{RedstoneChannels.register(this);if(fluids.any()||waterOutlets!=0)com.vandorlabs.blocks.DoorWaterGush.schedule(world,pos);});}
    @Override public void updateContainingBlockInfo(){super.updateContainingBlockInfo();if(com.vandorlabs.VandorLabs.proxy!=null)com.vandorlabs.VandorLabs.proxy.segmentedAppearanceChanged(this);}
    @Override public void invalidate(){SegmentedDoorWindows.invalidate(world);SegmentedDoorInteractions.INDEX.remove(this);SegmentedDoorCollisions.remove(this);RedstoneChannels.unregister(this);super.invalidate();if(com.vandorlabs.VandorLabs.proxy!=null)com.vandorlabs.VandorLabs.proxy.segmentedAppearanceChanged(this);}
    @Override public void onChunkUnload(){SegmentedDoorWindows.invalidate(world);SegmentedDoorInteractions.INDEX.remove(this);SegmentedDoorCollisions.remove(this);RedstoneChannels.unregister(this);super.onChunkUnload();if(com.vandorlabs.VandorLabs.proxy!=null)com.vandorlabs.VandorLabs.proxy.segmentedAppearanceChanged(this);}
    @Override public TileEntity channelTile(){return this;}
    @Override public int getRedstoneChannel(){return channels.first();}
    @Override public ChannelList getRedstoneChannels(){return channels;}
    @Override public void setRedstoneChannel(int channel){setRedstoneChannels(ChannelList.of(channel));}
    @Override public void setRedstoneChannels(ChannelList next){ChannelList old=channels;channels=next;RedstoneChannels.channelChanged(this,old);markDirty();sync();requestPower();}
    @Override public boolean hasLocalRedstoneSignal(){return false;}
    @Override public void setChannelSignal(boolean powered){if(channelSignal==powered)return;channelSignal=powered;requestPower();}
    private void sync(){if(world!=null&&!world.isRemote)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
    private boolean physicalClient(){World physical=world;while(physical instanceof com.vandorlabs.vehicle.VehicleWorld)physical=((com.vandorlabs.vehicle.VehicleWorld)physical).parent;return physical!=null&&physical.isRemote;}
    private boolean wetColumn(){for(int row=0;row<height;row++){SegmentedDoorTile member=panel(row);if(member.fluids.any()||com.vandorlabs.blocks.OpeningFluids.adjacentWater(world,member.pos))return true;}return false;}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);if(fluids.any())fluids.write(tag);if(waterOutlets!=0)tag.setInteger("SegmentedWaterOutlets",waterOutlets);if(motionIdentity!=null)tag.setUniqueId("SegmentedMotion",motionIdentity);settings.write(tag);if(!settings.equals(SegmentedDoorSettings.LEGACY)){tag.setInteger("SegmentedColumn",column);tag.setInteger("SegmentedColumns",columns);}writeCellArtwork(tag);if(texture>=0)tag.setInteger("SegmentedTexture",texture);ChannelData.write(tag,channels);tag.setInteger("SegmentedTopOffset",topOffset);tag.setInteger("SegmentedHeight",height);tag.setInteger("SegmentedSpeed",ticksPerSection);tag.setBoolean("SegmentedOpen",targetOpen);tag.setBoolean("SegmentedLocalRedstone",localRedstone);tag.setBoolean("SegmentedSignalOpens",signalOpens);tag.setBoolean("SegmentedFormed",formed);tag.setBoolean("SegmentedConfigured",configured);tag.setDouble("SegmentedFrom",from);tag.setLong("SegmentedStarted",started);tag.setLong("SegmentedSequence",sequence);tag.setInteger("SegmentedDuration",duration);tag.setUniqueId("SegmentedIdentity",identity);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){boolean oldOpen=targetOpen;double oldFrom=from;int oldPosition=settings.position,oldDirection=settings.direction;super.readFromNBT(tag);SegmentedDoorWindows.invalidate(world);fluids.read(tag);waterOutlets=tag.getInteger("SegmentedWaterOutlets")&15;motionIdentity=tag.hasUniqueId("SegmentedMotion")?tag.getUniqueId("SegmentedMotion"):null;readSavedCellArtwork(tag);settings=SegmentedDoorSettings.read(tag);column=Math.max(0,Math.min(MAX_COLUMNS-1,tag.getInteger("SegmentedColumn")));columns=Math.max(1,Math.min(MAX_COLUMNS,tag.getInteger("SegmentedColumns")));texture=tag.hasKey("SegmentedTexture",3)&&validTexture(tag.getInteger("SegmentedTexture"))?tag.getInteger("SegmentedTexture"):-1;ready=true;channels=ChannelData.read(tag,0);topOffset=Math.max(0,Math.min(MAX_HEIGHT-1,tag.getInteger("SegmentedTopOffset")));height=Math.max(1,Math.min(MAX_HEIGHT,tag.getInteger("SegmentedHeight")));ticksPerSection=Math.max(5,Math.min(30,tag.hasKey("SegmentedSpeed")?tag.getInteger("SegmentedSpeed"):10));targetOpen=tag.getBoolean("SegmentedOpen");localRedstone=!tag.hasKey("SegmentedLocalRedstone")||tag.getBoolean("SegmentedLocalRedstone");signalOpens=!tag.hasKey("SegmentedSignalOpens")||tag.getBoolean("SegmentedSignalOpens");formed=tag.getBoolean("SegmentedFormed");configured=tag.getBoolean("SegmentedConfigured");from=tag.getDouble("SegmentedFrom");if(!Double.isFinite(from))from=0;from=Math.max(0,Math.min(1,from));started=tag.getLong("SegmentedStarted");sequence=Math.max(0,tag.getLong("SegmentedSequence"));duration=Math.max(1,Math.min(960,tag.getInteger("SegmentedDuration")));if(tag.hasUniqueId("SegmentedIdentity"))identity=tag.getUniqueId("SegmentedIdentity");if(isRoot()&&physicalClient()&&(oldOpen!=targetOpen||oldFrom!=from||oldPosition!=settings.position||oldDirection!=settings.direction)&&wetColumn())fluids.invalidateGeometry();com.vandorlabs.VandorLabs.proxy.segmentedAppearanceChanged(this);if(world!=null&&!world.isRemote)scheduleRefresh();}
    @Override public NBTTagCompound getUpdateTag(){NBTTagCompound tag=writeToNBT(new NBTTagCompound());tag.setInteger("SegmentedColumn",column);tag.setInteger("SegmentedColumns",columns);return tag;}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){NBTTagCompound tag=packet.getNbtCompound();if(tag.hasUniqueId("SegmentedIdentity")&&identity.equals(tag.getUniqueId("SegmentedIdentity"))&&tag.getLong("SegmentedSequence")<sequence)return;handleUpdateTag(tag);}
    @Override public void handleUpdateTag(NBTTagCompound tag){if(ready&&tag.hasUniqueId("SegmentedIdentity")&&identity.equals(tag.getUniqueId("SegmentedIdentity"))&&tag.getLong("SegmentedSequence")<sequence)return;readFromNBT(tag);if(world!=null)world.markBlockRangeForRenderUpdate(pos,pos);}
    @Override public boolean hasFastRenderer(){return !com.vandorlabs.blocks.RenderFluidPresence.wet(this);}
    @Override public boolean shouldRenderInPass(int pass){if(!ready())return false;if(pass==0)return isRoot()&&!clientSuppressed;if(pass!=1)return false;if(com.vandorlabs.blocks.RenderFluidPresence.wet(this))return true;if(!isRoot())return false;int flags=com.vandorlabs.VandorLabs.proxy==null?-1:com.vandorlabs.VandorLabs.proxy.segmentedRenderFlags(this);return flags<0?hasGlass():(flags&2)!=0;}
    @Override public AxisAlignedBB getRenderBoundingBox(){
        if(settings.direction==0&&!settings.straight&&settings.position==0){double minX=-.01,maxX=1.01,minZ=.20,maxZ=height+1.01;switch(facing()){case SOUTH:minZ=-height-.01;maxZ=.80;break;case WEST:minX=.20;maxX=height+1.01;minZ=-.01;maxZ=1.01;break;case EAST:minX=-height-.01;maxX=.80;minZ=-.01;maxZ=1.01;break;default:break;}return new AxisAlignedBB(pos.getX()+minX,pos.getY()+1-height-.01,pos.getZ()+minZ,pos.getX()+maxX,pos.getY()+1.51,pos.getZ()+maxZ);}
        double span=settings.direction<2?height:columns;
        double reach=span+1.01;return new AxisAlignedBB(pos.getX()-reach,pos.getY()-height-reach,pos.getZ()-reach,pos.getX()+reach+1,pos.getY()+reach+1,pos.getZ()+reach+1);
    }
    @Override public double getMaxRenderDistanceSquared(){return 65536;}
    @Override public boolean shouldRefresh(World world,BlockPos pos,net.minecraft.block.state.IBlockState before,net.minecraft.block.state.IBlockState after){return before.getBlock()!=after.getBlock();}
}
