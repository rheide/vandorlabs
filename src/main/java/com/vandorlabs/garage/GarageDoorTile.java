package com.vandorlabs.garage;

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
public final class GarageDoorTile extends TileEntity implements net.minecraft.util.ITickable,RedstoneChannelMember {
    public static final int MAX_HEIGHT=32,MAX_COLUMNS=32;
    public int topOffset,height=1,ticksPerSection=10,texture=-1;
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
    public GarageDoorTile root(){GarageDoorTile tile=topOffset==0?this:at(world,pos.up(topOffset));return tile==null?this:tile;}
    public static GarageDoorTile at(World world,BlockPos pos){if(world==null||!world.isBlockLoaded(pos))return null;TileEntity tile=world.getTileEntity(pos);return tile instanceof GarageDoorTile?(GarageDoorTile)tile:null;}
    public EnumFacing facing(){return world.getBlockState(pos).getValue(GarageDoorBlock.FACING);}
    private GarageDoorTile neighbor(EnumFacing face){GarageDoorTile tile=at(world,pos.offset(face));return tile!=null&&tile.facing()==facing()?tile:null;}
    public double pose(double partial){double t=Math.max(0,Math.min(1,(world.getTotalWorldTime()-started+partial)/Math.max(1,duration)));return from+((targetOpen?1:0)-from)*t;}
    public boolean closed(){GarageDoorTile root=root();return !root.targetOpen&&root.pose(0)<=0;}
    public void scheduleRefresh(){if(world==null||world.isRemote||pending)return;pending=true;DeferredTileLoad.schedule(this,()->{pending=false;refreshColumn();requestPower();});}
    private void requestPower(){GarageDoorTile owner=root();if(world==null||world.isRemote)return;owner.powerRevision++;if(owner.powerPending||owner.configuring)return;owner.powerPending=true;DeferredTileLoad.schedule(owner,()->{owner.powerPending=false;owner.refreshPower();});}
    public void refreshColumn(){
        if(world==null||world.isRemote)return;
        GarageDoorTile top=this,next;int count=1;
        while((next=top.neighbor(EnumFacing.UP))!=null){if(++count>MAX_HEIGHT)return;top=next;}
        List<GarageDoorTile> members=new ArrayList<>();GarageDoorTile source=top;
        for(GarageDoorTile member=top;member!=null;member=member.neighbor(EnumFacing.DOWN)){
            if(members.size()>=MAX_HEIGHT)return;members.add(member);
            if(member.formed&&(!source.formed||member.configured&&!source.configured||member.height>source.height))source=member;
        }
        boolean topology=false;for(int i=0;i<members.size();i++)if(!members.get(i).formed||members.get(i).topOffset!=i||members.get(i).height!=members.size())topology=true;
        if(!topology)return;
        double current=source.pose(0);boolean target=source.targetOpen;long nextSequence=source.sequence+1;UUID id=source.identity;ChannelList selected=source.channels;int speed=source.ticksPerSection;boolean local=source.localRedstone,invert=source.signalOpens,custom=source.configured;
        for(int i=0;i<members.size();i++){
            GarageDoorTile member=members.get(i);ChannelList old=member.channels;member.topOffset=i;member.height=members.size();member.formed=true;member.from=current;member.targetOpen=target;member.started=world.getTotalWorldTime();member.duration=Math.max(1,(int)Math.ceil(member.height*speed*Math.abs((target?1:0)-current)));member.sequence=nextSequence;member.identity=id;member.channels=selected;member.ticksPerSection=speed;member.localRedstone=local;member.signalOpens=invert;member.configured=custom;member.texture=source.texture;member.markDirty();member.sync();RedstoneChannels.channelChanged(member,old);
        }
    }
    public List<GarageDoorTile> columns(){
        GarageDoorTile root=root();if(!root.formed)throw new IllegalArgumentException("Complete the garage column (maximum 32 sections)");LinkedHashSet<GarageDoorTile> result=new LinkedHashSet<>();ArrayDeque<GarageDoorTile> queue=new ArrayDeque<>();result.add(root);queue.add(root);
        while(!queue.isEmpty()){
            GarageDoorTile current=queue.removeFirst();EnumFacing right=current.facing().rotateY();
            for(EnumFacing side:new EnumFacing[]{right,right.getOpposite()}){
                if(!world.isBlockLoaded(current.pos.offset(side)))throw new IllegalArgumentException("Load the complete garage door before changing it");GarageDoorTile other=current.neighbor(side);if(other==null||!other.isRoot()||other.height!=root.height||result.contains(other))continue;
                if(result.size()>=MAX_COLUMNS)throw new IllegalArgumentException("Garage door supports at most 32 linked columns");result.add(other);queue.add(other);
            }
        }
        return new ArrayList<>(result);
    }
    private List<GarageDoorTile> members(List<GarageDoorTile> roots){List<GarageDoorTile> result=new ArrayList<>();for(GarageDoorTile root:roots)for(int row=0;row<root.height;row++){GarageDoorTile member=at(world,root.pos.down(row));if(member==null||member.facing()!=root.facing()||member.height!=root.height||member.topOffset!=row)throw new IllegalArgumentException("Load the complete garage door before changing it");result.add(member);}return result;}
    private void permissions(EntityPlayer player,List<GarageDoorTile> members){for(GarageDoorTile tile:members)if(!world.isBlockModifiable(player,tile.pos)||!player.canPlayerEdit(tile.pos,EnumFacing.UP,player.getHeldItemMainhand()))throw new IllegalArgumentException("Garage door is protected");}
    public void toggle(EntityPlayer player){List<GarageDoorTile> roots=columns();permissions(player,members(roots));boolean open=!root().targetOpen;for(GarageDoorTile root:roots)root.move(open);}
    public void operate(EntityPlayer player,boolean open){List<GarageDoorTile> roots=columns();permissions(player,members(roots));for(GarageDoorTile root:roots)root.move(open);}
    public void configure(EntityPlayer player,ChannelList next,int ticks,boolean local,boolean opens){
        if(ticks<5||ticks>30||next==null)throw new IllegalArgumentException("Invalid garage door settings");
        List<GarageDoorTile> roots=columns(),members=members(roots);permissions(player,members);for(GarageDoorTile root:roots)root.configuring=true;
        try{for(GarageDoorTile tile:members){ChannelList old=tile.channels;tile.channels=next;tile.ticksPerSection=ticks;tile.localRedstone=local;tile.signalOpens=opens;tile.configured=true;tile.markDirty();tile.sync();RedstoneChannels.channelChanged(tile,old);}}
        finally{for(GarageDoorTile root:roots)root.configuring=false;}
        root().powerRevision++;root().refreshPower();
    }
    public static boolean validTexture(int choice){return choice==-1||com.vandorlabs.tiles.ScreenHousingTextures.validChoice(choice)&&com.vandorlabs.tiles.ScreenHousingTextures.visible(choice)&&(com.vandorlabs.tiles.ScreenHousingTextures.generalTexture(choice)||"Doors".equals(com.vandorlabs.tiles.ScreenHousingTextures.category(choice))||"Double Doors".equals(com.vandorlabs.tiles.ScreenHousingTextures.category(choice)));}
    public void configureTexture(EntityPlayer player,int choice){
        if(!validTexture(choice))throw new IllegalArgumentException("Invalid Sectional Door texture");
        List<GarageDoorTile> all=members(columns());permissions(player,all);for(GarageDoorTile tile:all){tile.texture=choice;tile.configured=true;tile.markDirty();tile.sync();}
    }
    private void refreshPower(){
        if(world==null||world.isRemote||configuring)return;
        List<GarageDoorTile> roots;
        try{roots=columns();}catch(IllegalArgumentException tooWide){return;}
        boolean changed=false;for(GarageDoorTile root:roots)changed|=root.powerHandledRevision!=root.powerRevision;if(!changed)return;
        boolean signal=false,enabled=false;
        List<GarageDoorTile> loaded;try{loaded=members(roots);}catch(IllegalArgumentException incomplete){return;}
        for(GarageDoorTile member:loaded){enabled|=member.localRedstone||!member.channels.isEmpty();signal|=member.channelSignal||member.localRedstone&&world.isBlockPowered(member.pos);}
        for(GarageDoorTile root:roots)root.powerHandledRevision=root.powerRevision;
        if(enabled){boolean open=signal==root().signalOpens;for(GarageDoorTile root:roots)root.move(open);}
    }
    private void move(boolean open){
        GarageDoorTile root=root();if(root!=this){root.move(open);return;}if(targetOpen==open)return;
        double current=pose(0);targetOpen=open;from=current;started=world.getTotalWorldTime();duration=Math.max(1,(int)Math.ceil(height*ticksPerSection*Math.abs((open?1:0)-current)));sequence++;
        mirror();sync();
    }
    private void mirror(){for(int row=0;row<height;row++){GarageDoorTile member=at(world,pos.down(row));if(member==null)continue;member.from=from;member.targetOpen=targetOpen;member.started=started;member.duration=duration;member.sequence=sequence;member.markDirty();}}
    @Override public void update(){if(world==null||world.isRemote||!isRoot()||from==(targetOpen?1:0))return;if(world.getTotalWorldTime()-started>=duration){from=targetOpen?1:0;mirror();sync();}}
    @Override public void onLoad(){super.onLoad();com.vandorlabs.VandorLabs.proxy.garageAppearanceChanged(this);scheduleRefresh();DeferredTileLoad.schedule(this,()->RedstoneChannels.register(this));}
    @Override public void invalidate(){RedstoneChannels.unregister(this);super.invalidate();}
    @Override public void onChunkUnload(){RedstoneChannels.unregister(this);super.onChunkUnload();}
    @Override public TileEntity channelTile(){return this;}
    @Override public int getRedstoneChannel(){return channels.first();}
    @Override public ChannelList getRedstoneChannels(){return channels;}
    @Override public void setRedstoneChannel(int channel){setRedstoneChannels(ChannelList.of(channel));}
    @Override public void setRedstoneChannels(ChannelList next){ChannelList old=channels;channels=next;RedstoneChannels.channelChanged(this,old);markDirty();sync();requestPower();}
    @Override public boolean hasLocalRedstoneSignal(){return false;}
    @Override public void setChannelSignal(boolean powered){if(channelSignal==powered)return;channelSignal=powered;requestPower();}
    private void sync(){if(world!=null&&!world.isRemote)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);if(texture>=0)tag.setInteger("GarageTexture",texture);ChannelData.write(tag,channels);tag.setInteger("GarageTopOffset",topOffset);tag.setInteger("GarageHeight",height);tag.setInteger("GarageSpeed",ticksPerSection);tag.setBoolean("GarageOpen",targetOpen);tag.setBoolean("GarageLocalRedstone",localRedstone);tag.setBoolean("GarageSignalOpens",signalOpens);tag.setBoolean("GarageFormed",formed);tag.setBoolean("GarageConfigured",configured);tag.setDouble("GarageFrom",from);tag.setLong("GarageStarted",started);tag.setLong("GarageSequence",sequence);tag.setInteger("GarageDuration",duration);tag.setUniqueId("GarageIdentity",identity);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);texture=tag.hasKey("GarageTexture",3)&&validTexture(tag.getInteger("GarageTexture"))?tag.getInteger("GarageTexture"):-1;ready=true;channels=ChannelData.read(tag,0);topOffset=Math.max(0,Math.min(MAX_HEIGHT-1,tag.getInteger("GarageTopOffset")));height=Math.max(1,Math.min(MAX_HEIGHT,tag.getInteger("GarageHeight")));ticksPerSection=Math.max(5,Math.min(30,tag.hasKey("GarageSpeed")?tag.getInteger("GarageSpeed"):10));targetOpen=tag.getBoolean("GarageOpen");localRedstone=!tag.hasKey("GarageLocalRedstone")||tag.getBoolean("GarageLocalRedstone");signalOpens=!tag.hasKey("GarageSignalOpens")||tag.getBoolean("GarageSignalOpens");formed=tag.getBoolean("GarageFormed");configured=tag.getBoolean("GarageConfigured");from=tag.getDouble("GarageFrom");if(!Double.isFinite(from))from=0;from=Math.max(0,Math.min(1,from));started=tag.getLong("GarageStarted");sequence=Math.max(0,tag.getLong("GarageSequence"));duration=Math.max(1,Math.min(960,tag.getInteger("GarageDuration")));if(tag.hasUniqueId("GarageIdentity"))identity=tag.getUniqueId("GarageIdentity");com.vandorlabs.VandorLabs.proxy.garageAppearanceChanged(this);}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){NBTTagCompound tag=packet.getNbtCompound();if(tag.hasUniqueId("GarageIdentity")&&identity.equals(tag.getUniqueId("GarageIdentity"))&&tag.getLong("GarageSequence")<sequence)return;handleUpdateTag(tag);}
    @Override public void handleUpdateTag(NBTTagCompound tag){if(ready&&tag.hasUniqueId("GarageIdentity")&&identity.equals(tag.getUniqueId("GarageIdentity"))&&tag.getLong("GarageSequence")<sequence)return;readFromNBT(tag);if(world!=null)world.markBlockRangeForRenderUpdate(pos,pos);}
    @Override public boolean hasFastRenderer(){return true;}
    @Override public boolean shouldRenderInPass(int pass){return pass==0&&isRoot()&&ready()&&!clientSuppressed;}
    @Override public AxisAlignedBB getRenderBoundingBox(){
        double minX=-.01,maxX=1.01,minZ=.20,maxZ=height+1.01;
        switch(facing()){case SOUTH:minZ=-height-.01;maxZ=.80;break;case WEST:minX=.20;maxX=height+1.01;minZ=-.01;maxZ=1.01;break;case EAST:minX=-height-.01;maxX=.80;minZ=-.01;maxZ=1.01;break;default:break;}
        return new AxisAlignedBB(pos.getX()+minX,pos.getY()+1-height-.01,pos.getZ()+minZ,pos.getX()+maxX,pos.getY()+1.51,pos.getZ()+maxZ);
    }
    @Override public double getMaxRenderDistanceSquared(){return 65536;}
    @Override public boolean shouldRefresh(World world,BlockPos pos,net.minecraft.block.state.IBlockState before,net.minecraft.block.state.IBlockState after){return before.getBlock()!=after.getBlock();}
}
