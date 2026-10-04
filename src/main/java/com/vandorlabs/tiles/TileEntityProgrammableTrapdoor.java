package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockProgrammableTrapdoor;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.redstone.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Settings and pair links; event-driven power, no idle tile ticks. */
public class TileEntityProgrammableTrapdoor extends TileEntity implements RedstoneChannelMember {
    protected int texture=ScreenHousingTextures.DEFAULT_TRAPDOOR,position,channel,trigger=SpaceDoorData.TRIGGER_REDSTONE_ON;
    protected boolean sliding,slideIntoWall,slideOverSurface,channelSignal,powerKnown,lastPower,configuring;
    private boolean tileTexture=true;
    public boolean isTileTexture(){return tileTexture;}
    public void setTileTexture(boolean value){tileTexture=value;sync();}
    protected BlockPos partner,squareOrigin;
    // Immutable membership can be shared by loaded leaves after exact validation.
    protected java.util.List<BlockPos> assembly=java.util.Collections.emptyList();
    protected double assemblyHinge=1/16D,assemblyTravel=15/16D;
    public double motionHinge(){return assembly.isEmpty()?com.vandorlabs.render.TrapdoorGeometry.OPEN_HINGE:assemblyHinge;}
    public double motionTravel(){return assembly.isEmpty()?15/16D:assemblyTravel;}
    public int getHousingTexture(){return texture;}
    public int getPosition(){return position;}
    private boolean cover;
    private EnumFacing coverFacing=EnumFacing.NORTH;
    public EnumFacing coverFacing(){if(world!=null && world.getBlockState(pos).getBlock() instanceof BlockProgrammableTrapdoor)coverFacing=world.getBlockState(pos).getValue(BlockProgrammableTrapdoor.FACING);return coverFacing;}
    public void setCoverFacing(EnumFacing value){coverFacing=value;if(cover && world!=null)world.setBlockState(pos,world.getBlockState(pos).withProperty(BlockProgrammableTrapdoor.FACING,value),2);sync();}
    public void setHingeFacing(EnumFacing value){
        if(!canOffsetClosedLeaf() || value==null || !value.getAxis().isHorizontal())return;
        coverFacing=value;
        if(world!=null)world.setBlockState(pos,world.getBlockState(pos).withProperty(BlockProgrammableTrapdoor.FACING,value),2);
        sync();
    }
    public boolean isCover(){return cover;}
    /** Offset mounts require an individual leaf, including when linked chunks are unloaded. */
    public boolean canOffsetClosedLeaf(){return !(this instanceof TileEntityProgrammableDiagonalTrapdoor) && partner==null && squareOrigin==null && assembly.isEmpty();}
    public void setCover(boolean value){
        if(value && !canOffsetClosedLeaf())return;
        if(cover==value)return;
        cover=value;sync();if(world!=null&&!world.isRemote)evaluatePower(true);
    }
    public double[][] corners(net.minecraft.block.state.IBlockState state,double pose){int turns=BlockProgrammableTrapdoor.quarterTurns(state.getValue(BlockProgrammableTrapdoor.FACING));return cover?com.vandorlabs.render.TrapdoorGeometry.coverCorners(position,sliding,turns,pose,coverOverhang()):sliding && slideOverSurface?com.vandorlabs.render.TrapdoorGeometry.surfaceCorners(position,turns,pose,motionHinge(),motionTravel()):com.vandorlabs.render.TrapdoorGeometry.corners(position,sliding,turns,pose,motionHinge(),motionTravel());}
    /** Opposing covers meet at the two-cell opening's seam without overshooting it. */
    public double coverOverhang() {
        if(cover && world!=null) {
            BlockPos opposite=pos.offset(coverFacing(),3);
            if(world.isBlockLoaded(opposite)) {
                TileEntity raw=world.getTileEntity(opposite);
                if(raw instanceof TileEntityProgrammableTrapdoor) {
                    TileEntityProgrammableTrapdoor other=(TileEntityProgrammableTrapdoor)raw;
                    if(other.cover && other.getClass()==getClass() && other.position==position && other.coverFacing()==coverFacing().getOpposite())return 0;
                }
            }
        }
        return com.vandorlabs.render.TrapdoorGeometry.COVER_OVERHANG;
    }
    public double coverOffset(){return (sliding?-1:1)*coverOverhang();}
    public boolean isSliding(){return sliding;}
    public boolean isSlideOverSurface(){return slideOverSurface;}
    public void setSlideOverSurface(boolean value){slideOverSurface=!(this instanceof TileEntityProgrammableDiagonalTrapdoor) && value;sync();}
    public boolean isSlideIntoWall(){return slideIntoWall;}
    public void setSlideIntoWall(boolean value){slideIntoWall=this instanceof TileEntityProgrammableDiagonalTrapdoor && value;sync();}
    public int getTrigger(){return trigger;}
    @Override public int getRedstoneChannel(){return channel;}
    @Override public TileEntity channelTile(){return this;}
    @Override public boolean hasLocalRedstoneSignal(){return LoadedRedstonePower.isPowered(world,pos);}
    public static boolean valid(int texture,int position,int trigger,int channel) {
        return texture>=0 && ScreenHousingTextures.validChoice(texture) && position>=0 && position<=2
                && SpaceDoorData.validTrigger(trigger) && channel>=0;
    }
    public boolean usable(EntityPlayer player) {
        return world!=null && world.isBlockLoaded(pos) && world.getTileEntity(pos)==this
                && player.getDistanceSq(pos)<=64 && player.canPlayerEdit(pos,EnumFacing.UP,player.getHeldItemMainhand())
                && world.isBlockModifiable(player,pos);
    }
    public TileEntityProgrammableTrapdoor mate() {
        if(world==null || partner==null || !world.isBlockLoaded(partner))return null;
        TileEntity raw=world.getTileEntity(partner);
        if(!(raw instanceof TileEntityProgrammableTrapdoor))return null;
        TileEntityProgrammableTrapdoor other=(TileEntityProgrammableTrapdoor)raw;
        return pos.equals(other.partner) && pos.distanceSq(partner)==1 ? other : null;
    }
    public boolean hasPairLink(){return partner!=null;}
    public boolean compatible(TileEntityProgrammableTrapdoor other){return !cover && !other.cover && other.getClass()==getClass() && position==other.position;}
    protected boolean groupCompatible(TileEntityProgrammableTrapdoor other){return compatible(other);}
    public void pairWith(TileEntityProgrammableTrapdoor other) {
        if(world==null || world.isRemote || other.world!=world || partner!=null || other.partner!=null
                || pos.getY()!=other.pos.getY() || pos.distanceSq(other.pos)!=1 || !compatible(other))return;
        partner=other.pos.toImmutable();other.partner=pos.toImmutable();
        // The placed neighbour joins the existing trapdoor's motion/channel.
        sliding=other.sliding;slideIntoWall=other.slideIntoWall;slideOverSurface=other.slideOverSurface; trigger=other.trigger;
        setRedstoneChannel(other.channel);
        EnumFacing toward=EnumFacing.getFacingFromVector(pos.getX()-other.pos.getX(),0,pos.getZ()-other.pos.getZ());
        world.setBlockState(pos,world.getBlockState(pos).withProperty(BlockProgrammableTrapdoor.FACING,toward),2);
        world.setBlockState(other.pos,world.getBlockState(other.pos).withProperty(BlockProgrammableTrapdoor.FACING,toward.getOpposite()),2);
        requestOpen(world.getBlockState(other.pos).getValue(BlockProgrammableTrapdoor.OPEN));
        sync();other.sync(); evaluatePower(true);
    }
    protected java.util.List<BlockPos> squareCells(BlockPos base) {
        return java.util.Arrays.asList(base,base.east(),base.south(),base.east().south());
    }
    /** A stable four-leaf group, otherwise the surviving pair or single leaf. */
    public java.util.List<TileEntityProgrammableTrapdoor> group() {
        java.util.List<TileEntityProgrammableTrapdoor> result=new java.util.ArrayList<>();
        if(cover){result.add(this);return result;}
        if(!assembly.isEmpty() && assembly.contains(pos) && world!=null) {
            for(BlockPos cell:assembly) {
                if(!world.isBlockLoaded(cell)){result.clear();break;}
                TileEntity raw=world.getTileEntity(cell);
                if(!(raw instanceof TileEntityProgrammableTrapdoor)){result.clear();break;}
                TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)raw;
                if(!assembly.equals(leaf.assembly) || !groupCompatible(leaf)){result.clear();break;}
                // Equal saved lists become one immutable instance. Subsequent
                // renders still validate loaded tiles, but not N squared positions.
                leaf.assembly=assembly;
                result.add(leaf);
            }
            if(result.size()==assembly.size())return result;
        }
        if(squareOrigin!=null && world!=null && squareCells(squareOrigin).contains(pos)) {
            for(BlockPos cell:squareCells(squareOrigin)) {
                if(!world.isBlockLoaded(cell)){result.clear();break;}
                TileEntity raw=world.getTileEntity(cell);
                if(!(raw instanceof TileEntityProgrammableTrapdoor)){result.clear();break;}
                TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)raw;
                if(!squareOrigin.equals(leaf.squareOrigin) || !groupCompatible(leaf) || leaf.sliding!=sliding) {
                    result.clear();break;
                }
                result.add(leaf);
            }
            if(result.size()==4)return result;
        }
        result.clear();result.add(this);TileEntityProgrammableTrapdoor other=mate();if(other!=null)result.add(other);
        return result;
    }
    /** Complete a square in any placement order; mirror the two rows/columns. */
    public void completeSquare(EntityPlayer player,net.minecraft.item.ItemStack stack) {
        if(world==null || world.isRemote || cover)return;
        repairLinks();if(TrapdoorAssemblies.complete(this,player,stack))return;
        if(squareOrigin!=null)return;
        for(int dx=-1;dx<=0;dx++)for(int dz=-1;dz<=0;dz++) {
            BlockPos base=pos.add(dx,0,dz);java.util.List<BlockPos> cells=squareCells(base);
            java.util.List<TileEntityProgrammableTrapdoor> leaves=new java.util.ArrayList<>();
            for(BlockPos cell:cells) {
                if(!world.isBlockLoaded(cell))break;
                TileEntity raw=world.getTileEntity(cell);
                if(!(raw instanceof TileEntityProgrammableTrapdoor))break;
                TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)raw;
                leaf.repairLinks();
                if(leaf.squareOrigin!=null || !compatible(leaf) || leaf.partner!=null && !cells.contains(leaf.partner))break;
                if(player!=null && (!player.canPlayerEdit(cell,EnumFacing.UP,stack) || !world.isBlockModifiable(player,cell)))break;
                leaves.add(leaf);
            }
            if(leaves.size()!=4)continue;
            TileEntityProgrammableTrapdoor reference=leaves.get(0);
            for(TileEntityProgrammableTrapdoor leaf:leaves)if(leaf!=this && leaf.partner!=null){reference=leaf;break;}
            boolean alongX=world.getBlockState(reference.pos).getValue(BlockProgrammableTrapdoor.FACING).getAxis()==EnumFacing.Axis.X;
            boolean motion=reference.sliding;int linked=reference.channel,activation=reference.trigger;
            boolean opened=world.getBlockState(reference.pos).getValue(BlockProgrammableTrapdoor.OPEN);
            for(TileEntityProgrammableTrapdoor leaf:leaves) {
                leaf.partner=null;leaf.sliding=motion;leaf.slideOverSurface=reference.slideOverSurface;leaf.trigger=activation;
                leaf.squareOrigin=base.toImmutable();leaf.setRedstoneChannel(linked);
            }
            if(alongX){leaves.get(1).pairWith(leaves.get(0));leaves.get(3).pairWith(leaves.get(2));}
            else {leaves.get(2).pairWith(leaves.get(0));leaves.get(3).pairWith(leaves.get(1));}
            requestOpen(opened);for(TileEntityProgrammableTrapdoor leaf:leaves)leaf.sync();evaluatePower(true);return;
        }
    }
    private void clearSquare() {
        BlockPos old=squareOrigin;if(old==null || world==null)return;
        for(BlockPos cell:squareCells(old))if(world.isBlockLoaded(cell)) {
            TileEntity raw=world.getTileEntity(cell);
            if(raw instanceof TileEntityProgrammableTrapdoor) {
                TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)raw;
                if(old.equals(leaf.squareOrigin)){leaf.squareOrigin=null;leaf.sync();}
            }
        }
        squareOrigin=null;
    }
    protected void clearAssembly() {
        java.util.List<BlockPos> old=assembly;assembly=java.util.Collections.emptyList();
        if(world==null)return;
        for(BlockPos cell:old)if(world.isBlockLoaded(cell)) {
            TileEntity raw=world.getTileEntity(cell);
            if(raw instanceof TileEntityProgrammableTrapdoor) {
                TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)raw;
                if(leaf.assembly.equals(old)){leaf.assembly=java.util.Collections.emptyList();leaf.sync();}
            }
        }
    }
    /** Repair loaded stale links; defer decisions about unloaded chunks. */
    public void repairLinks() {
        if(world==null || world.isRemote)return;
        if(partner!=null && world.isBlockLoaded(partner) && mate()==null){partner=null;sync();}
        for(BlockPos cell:assembly)if(world.isBlockLoaded(cell)) {
            TileEntity raw=world.getTileEntity(cell);
            if(!(raw instanceof TileEntityProgrammableTrapdoor) || !assembly.equals(((TileEntityProgrammableTrapdoor)raw).assembly)) {clearAssembly();break;}
        }
        if(squareOrigin==null)return;
        if(!squareCells(squareOrigin).contains(pos)){squareOrigin=null;sync();return;}
        for(BlockPos cell:squareCells(squareOrigin)) {
            if(!world.isBlockLoaded(cell))continue;
            TileEntity raw=world.getTileEntity(cell);
            if(!(raw instanceof TileEntityProgrammableTrapdoor)
                    || !squareOrigin.equals(((TileEntityProgrammableTrapdoor)raw).squareOrigin)) {
                clearSquare();return;
            }
        }
    }
    public void unpair() {
        clearAssembly();
        clearSquare();
        TileEntityProgrammableTrapdoor other=mate();partner=null;sync();
        if(other!=null){other.partner=null;other.sync();other.evaluatePower(true);}
    }
    /** Apply every member before publishing updates or evaluating group power. */
    public static void configureGroup(java.util.List<TileEntityProgrammableTrapdoor> leaves,Runnable changes) {
        for(TileEntityProgrammableTrapdoor leaf:leaves)leaf.configuring=true;
        try{changes.run();}finally{
            for(TileEntityProgrammableTrapdoor leaf:leaves)leaf.configuring=false;
            for(TileEntityProgrammableTrapdoor leaf:leaves)leaf.sync();
            for(TileEntityProgrammableTrapdoor leaf:leaves)leaf.evaluatePower(true);
        }
    }
    public void configureGroup(int texture,int position,boolean sliding,int trigger,int channel,boolean inverted,boolean cover,boolean tileTexture,EnumFacing facing) {
        configureGroup(texture,position,sliding,trigger,channel,inverted,cover,tileTexture,facing,slideIntoWall);
    }
    public void configureGroup(int texture,int position,boolean sliding,int trigger,int channel,boolean inverted,boolean cover,boolean tileTexture,EnumFacing facing,boolean intoWall) {
        configureGroup(texture,position,sliding,trigger,channel,inverted,cover,tileTexture,facing,intoWall,slideOverSurface);
    }
    public void configureGroup(int texture,int position,boolean sliding,int trigger,int channel,boolean inverted,boolean cover,boolean tileTexture,EnumFacing facing,boolean intoWall,boolean overSurface) {
        if(!valid(texture,position,trigger,channel))return;
        java.util.List<TileEntityProgrammableTrapdoor> leaves=new java.util.ArrayList<>(group());
        boolean flip=this instanceof TileEntityProgrammableDiagonalTrapdoor && ((TileEntityProgrammableDiagonalTrapdoor)this).isInverted()!=inverted;
        configureGroup(leaves,()->{
            for(TileEntityProgrammableTrapdoor leaf:leaves){
                leaf.setCover(cover);leaf.setTileTexture(tileTexture);leaf.setSlideIntoWall(intoWall);leaf.setSlideOverSurface(overSurface);leaf.configure(texture,position,sliding,trigger,channel);
                if(flip && leaf instanceof TileEntityProgrammableDiagonalTrapdoor){TileEntityProgrammableDiagonalTrapdoor diagonal=(TileEntityProgrammableDiagonalTrapdoor)leaf;diagonal.setInverted(!diagonal.isInverted());}
                if(facing!=null)leaf.setHingeFacing(facing);
            }
        });
    }
    public void configure(int texture,int position,boolean sliding,int trigger,int channel) {
        if(!valid(texture,position,trigger,channel))return;
        this.texture=texture;this.position=position;this.sliding=sliding;this.trigger=trigger;
        setRedstoneChannel(channel);sync();
        if(world!=null && !world.isRemote)evaluatePower(true);
    }
    @Override public void setRedstoneChannel(int value) {
        int next=Math.max(0,value);if(channel==next)return;
        int old=channel;channel=next;markDirty();RedstoneChannels.channelChanged(this,old);sync();
        if(world!=null && !world.isRemote)evaluatePower(true);
    }
    @Override public void setChannelSignal(boolean powered) {
        if(channelSignal==powered)return;channelSignal=powered;
        if(world!=null && !world.isRemote)evaluatePower(false);
    }
    public void localInputChanged() {
        if(world==null || world.isRemote)return;
        repairLinks();RedstoneChannels.inputChanged(this);evaluatePower(false);
    }
    public void evaluatePower(boolean force) {
        if(configuring || world==null || world.isRemote)return;
        if(cover && LandingGearCovers.needsOpen(this)){requestOpen(true);return;}
        java.util.List<TileEntityProgrammableTrapdoor> members=group();
        boolean powered=false;
        for(TileEntityProgrammableTrapdoor leaf:members)powered|=leaf.channelSignal || leaf.hasLocalRedstoneSignal();
        boolean changed=!powerKnown || lastPower!=powered;
        powerKnown=true;lastPower=powered;
        for(TileEntityProgrammableTrapdoor leaf:members){leaf.powerKnown=true;leaf.lastPower=powered;leaf.markDirty();}
        markDirty();
        if(trigger!=SpaceDoorData.TRIGGER_DISABLED && (force || changed))
            requestOpen(trigger==SpaceDoorData.TRIGGER_REDSTONE_ON?powered:!powered);
    }
    public void requestOpen(boolean open) {
        if(configuring || world==null || world.isRemote)return;
        if(cover && LandingGearCovers.needsOpen(this))open=true;
        for(TileEntityProgrammableTrapdoor leaf:group())leaf.setOpen(open);
    }
    private void setOpen(boolean open) {
        IBlockState state=world.getBlockState(pos);
        if(!(state.getBlock() instanceof BlockProgrammableTrapdoor) || state.getValue(BlockProgrammableTrapdoor.OPEN)==open)return;
        world.setBlockState(pos,state.withProperty(BlockProgrammableTrapdoor.OPEN,open),2);
        world.playEvent(null,open?1037:1036,pos,0);markDirty();
    }
    protected void sync() {
        markDirty();if(configuring || world==null)return;
        IBlockState state=world.getBlockState(pos);
        // HALF keeps vanilla metadata and ladder behavior consistent with the chosen position.
        if(state.getBlock() instanceof BlockProgrammableTrapdoor) {
            IBlockState updated=state.withProperty(BlockProgrammableTrapdoor.HALF,position==2
                    ?net.minecraft.block.BlockTrapDoor.DoorHalf.TOP:net.minecraft.block.BlockTrapDoor.DoorHalf.BOTTOM);
            if(!state.equals(updated)){world.setBlockState(pos,updated,2);state=updated;}
        }
        world.notifyBlockUpdate(pos,state,state,2);
    }
    /** Inventory settings deliberately exclude pair links, power and world coordinates. */
    public NBTTagCompound itemSettings() {
        NBTTagCompound tag=new NBTTagCompound();
        tag.setInteger("housingTexture",texture);tag.setInteger("TrapdoorPosition",position);
        if(canOffsetClosedLeaf())tag.setInteger("TrapdoorCoverFacing",coverFacing().getHorizontalIndex());
        tag.setBoolean("TrapdoorTileTexture",tileTexture);tag.setBoolean("TrapdoorCover",cover);tag.setBoolean("TrapdoorSliding",sliding);tag.setBoolean("TrapdoorSlideIntoWall",slideIntoWall);tag.setBoolean("TrapdoorSlideOverSurface",slideOverSurface);tag.setInteger("TrapdoorTrigger",trigger);
        tag.setInteger("RedstoneChannel",channel);return tag;
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);tag.merge(itemSettings());
        tag.setInteger("TrapdoorSchema",1);tag.setBoolean("ChannelSignal",channelSignal);
        tag.setBoolean("TrapdoorPowerKnown",powerKnown);tag.setBoolean("TrapdoorLastPower",lastPower);
        if(partner!=null)tag.setLong("TrapdoorPartner",partner.toLong());
        if(squareOrigin!=null)tag.setLong("TrapdoorSquare",squareOrigin.toLong());
        net.minecraft.nbt.NBTTagList members=new net.minecraft.nbt.NBTTagList();
        for(BlockPos cell:assembly)members.appendTag(new net.minecraft.nbt.NBTTagLong(cell.toLong()));
        tag.setTag("TrapdoorAssembly",members);tag.setDouble("TrapdoorHinge",assemblyHinge);tag.setDouble("TrapdoorTravel",assemblyTravel);return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        int old=channel;super.readFromNBT(tag);
        texture=tag.hasKey("housingTexture",3)?ScreenHousingTextures.clamp(tag.getInteger("housingTexture")):ScreenHousingTextures.DEFAULT_TRAPDOOR;
        position=Math.max(0,Math.min(2,tag.getInteger("TrapdoorPosition")));
        tileTexture=!tag.hasKey("TrapdoorTileTexture",1) || tag.getBoolean("TrapdoorTileTexture");
        cover=tag.getBoolean("TrapdoorCover") && !(this instanceof TileEntityProgrammableDiagonalTrapdoor);
        coverFacing=EnumFacing.getHorizontal(tag.getInteger("TrapdoorCoverFacing"));
        sliding=tag.getBoolean("TrapdoorSliding");
        slideIntoWall=this instanceof TileEntityProgrammableDiagonalTrapdoor && tag.getBoolean("TrapdoorSlideIntoWall");
        slideOverSurface=!(this instanceof TileEntityProgrammableDiagonalTrapdoor) && tag.getBoolean("TrapdoorSlideOverSurface");
        int saved=tag.hasKey("TrapdoorTrigger",3)?tag.getInteger("TrapdoorTrigger"):SpaceDoorData.TRIGGER_REDSTONE_ON;
        trigger=SpaceDoorData.validTrigger(saved)?saved:SpaceDoorData.TRIGGER_REDSTONE_ON;
        channel=Math.max(0,tag.getInteger("RedstoneChannel"));channelSignal=tag.getBoolean("ChannelSignal");
        powerKnown=tag.getBoolean("TrapdoorPowerKnown");lastPower=tag.getBoolean("TrapdoorLastPower");
        partner=tag.hasKey("TrapdoorPartner",4)?BlockPos.fromLong(tag.getLong("TrapdoorPartner")):null;
        squareOrigin=tag.hasKey("TrapdoorSquare",4)?BlockPos.fromLong(tag.getLong("TrapdoorSquare")):null;
        java.util.List<BlockPos> savedAssembly=new java.util.ArrayList<>();net.minecraft.nbt.NBTTagList members=tag.getTagList("TrapdoorAssembly",4);
        if(members.tagCount()<=64)for(int i=0;i<members.tagCount();i++)savedAssembly.add(BlockPos.fromLong(((net.minecraft.nbt.NBTTagLong)members.get(i)).getLong()));
        assembly=java.util.Collections.unmodifiableList(savedAssembly);
        assemblyHinge=Math.max(-8,Math.min(8,tag.getDouble("TrapdoorHinge")));
        assemblyTravel=Math.max(15/16D,Math.min(8,tag.getDouble("TrapdoorTravel")));
        if(world!=null && !world.isRemote && old!=channel)
            DeferredTileLoad.schedule(this,()->RedstoneChannels.channelChanged(this,old));
        if(world!=null && world.isRemote)world.markBlockRangeForRenderUpdate(pos,pos);
    }
    protected void reconnectLoadedGroup() { }
    @Override public void onLoad() {
        super.onLoad();DeferredTileLoad.schedule(this,()->{
            repairLinks();reconnectLoadedGroup();
            RedstoneChannels.register(this);evaluatePower(false);
        });
    }
    @Override public void invalidate(){RedstoneChannels.unregister(this);super.invalidate();}
    @Override public void onChunkUnload(){RedstoneChannels.unregister(this);super.onChunkUnload();}
    @Override public boolean shouldRefresh(World world,BlockPos p,IBlockState before,IBlockState after){return before.getBlock()!=after.getBlock();}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager net,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());}
    @Override public AxisAlignedBB getRenderBoundingBox(){return new AxisAlignedBB(pos.add(-8,-8,-8),pos.add(9,9,9));}
    @Override public double getMaxRenderDistanceSquared(){return Double.MAX_VALUE;}
}
