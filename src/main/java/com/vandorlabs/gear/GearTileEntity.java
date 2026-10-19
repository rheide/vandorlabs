package com.vandorlabs.gear;

import com.vandorlabs.gear.core.GearMotion;
import com.vandorlabs.gear.core.GearTopology;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

/**
 * Persistent controller/member state. Every part owns a small tile so it can
 * recover after an unloaded or destroyed controller without deleting blocks.
 * Only a controller advances motion and draws an assembled group.
 */
public final class GearTileEntity extends TileEntity implements ITickable,com.vandorlabs.redstone.RedstoneChannelMember {
    public static final int ANIMATION_TICKS = 24;

    @Nullable private BlockPos owner;
    @Nullable private GearTopology.Assembly assembly;
    private final List<BlockPos> columns = new ArrayList<BlockPos>();
    private final Set<BlockPos> members = new HashSet<BlockPos>();
    private EnumFacing footFacing = EnumFacing.NORTH;
    private GearMotion.Mode mode = GearMotion.Mode.EXTEND;
    private GearMotion.Direction direction = GearMotion.Direction.NORTH;
    private boolean deployedTarget = true;
    private boolean signalKnown,lastSignal,legacyAnyChannels;
    public boolean usesLegacyInput(){return legacyAnyChannels;}
    public double footMountOffset(){return assembly!=null&&assembly.kind()==GearTopology.FootKind.WHEEL&&(mode==GearMotion.Mode.EXTEND||mode==GearMotion.Mode.STATIC)?(armWidth()==2?11/16D:9/16D):0;}
    public GearMotion.Pose pose(double progress){
        double stowed=GearMotion.RETRACTED_FOOT_Y;
        // Leave daylight beneath the collar instead of coincident runner faces.
        if(mode==GearMotion.Mode.EXTEND)stowed=footMountOffset()>0?6/16D-1/64D-(3/16D+footMountOffset()):stowed-1/64D;
        double hingeDrop=mode==GearMotion.Mode.ROTATE_LEVEL && armWidth()==2 && assembly.kind()==GearTopology.FootKind.WHEEL?9/128D:0;
        return GearMotion.pose(mode,direction,assembly.columnHeight(),progress,stowed,hingeDrop);
    }

    private double progress = 1.0;
    private double previousProgress = 1.0;
    // A cached group may be temporarily suspended while required chunks are
    // absent. Its saved membership, target and exact progress are preserved.
    private boolean operational = true,motionBlocked,configured;
    private double boxesAt=Double.NaN;
    private java.util.List<AxisAlignedBB> cachedBoxes=java.util.Collections.emptyList();
    public java.util.List<AxisAlignedBB> collisionBoxes(){if(!isOperational())return java.util.Collections.emptyList();if(boxesAt!=progress){cachedBoxes=GearGeometry.boxes(this,progress);boxesAt=progress;}return cachedBoxes;}
    public void captureProgress(double next){previousProgress=progress;progress=isFixedMount()?1:Math.max(0,Math.min(1,next));boxesAt=Double.NaN;}

    private int rebuildDelay = -1;
    private boolean retryUnavailable;
    private boolean firstServerTick = true;
    private boolean releasing;
    private com.vandorlabs.redstone.ChannelList channels=com.vandorlabs.redstone.ChannelList.EMPTY;
    public int triggerMode;
    public String error(){return motionBlocked?"Motion blocked; clear the stroke":lastError;}
    public java.util.Set<BlockPos> members(){return java.util.Collections.unmodifiableSet(members);}
    public java.util.List<BlockPos> columns(){return java.util.Collections.unmodifiableList(columns);}
    public double progress(){return progress;}
    public boolean target(){return isFixedMount()||deployedTarget;}
    public net.minecraft.tileentity.TileEntity channelTile(){return this;}
    public int getRedstoneChannel(){return channels.first();}
    public com.vandorlabs.redstone.ChannelList getRedstoneChannels(){return channels;}
    public void setRedstoneChannel(int channel){setRedstoneChannels(com.vandorlabs.redstone.ChannelList.of(channel));}
    public void setRedstoneChannels(com.vandorlabs.redstone.ChannelList value){if(!channels.equals(value))signalKnown=false;channels=value;sync();}
    public boolean hasLocalRedstoneSignal(){return false;}
    public void setChannelSignal(boolean powered){}
    public void target(boolean value){if(isFixedMount())return;if(deployedTarget==value)return;deployedTarget=value;sync();if(world!=null)com.vandorlabs.tiles.LandingGearCovers.refresh(world,pos);}
    private boolean localPower(){
        java.util.List<BlockPos> roots=columns.isEmpty()?java.util.Collections.singletonList(BlockPos.ORIGIN):columns;
        for(BlockPos column:roots){BlockPos at=pos.add(column);if(!GearAssembly.loaded(world,at))continue;net.minecraft.block.state.IBlockState state=world.getBlockState(at);
            if(state.getBlock() instanceof GearBlock){for(BlockPos cell:((GearBlock)state.getBlock()).componentCells(world,at))if(GearAssembly.loaded(world,cell)&&com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world,cell))return true;}
            else if(com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world,at))return true;
        }return false;
    }
    public void input(){if(triggerMode!=0&&world!=null){
        net.minecraft.world.World bus=world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).parent:world;
        boolean powered;if(legacyAnyChannels){powered=localPower();for(int i=0;i<channels.size();i++)powered|=com.vandorlabs.redstone.RedstoneChannels.level(bus,channels.get(i))>0;}else powered=channels.isEmpty()?localPower():com.vandorlabs.redstone.RedstoneChannels.allPowered(bus,channels);
        if(!signalKnown||powered!=lastSignal){signalKnown=true;lastSignal=powered;target(triggerMode==1?powered:!powered);}
    }}

    private String lastError = "The gear has not been assembled yet.";

    public boolean isController() { return assembly != null; }
    public boolean isOperational() { return assembly != null && operational; }
    @Nullable public GearTopology.Assembly assembly() { return assembly; }
    public EnumFacing footFacing() { return footFacing; }
    public GearMotion.Mode mode() { return mode; }
    public GearMotion.Direction direction() { return direction; }
    @Nullable public BlockPos owner() { return owner; }

    public double armShiftX(){return armShift().getX()*.5;}
    public double armShiftZ(){return armShift().getZ()*.5;}
    public double armShiftX(BlockPos column){return columnShift(column).getX()*.5;}
    public double armShiftZ(BlockPos column){return columnShift(column).getZ()*.5;}
    private BlockPos columnShift(BlockPos column){IBlockState state=world.getBlockState(pos.add(column));return armWidth()==2 && state.getBlock() instanceof GearBlock?LargeGearBlock.offset(state.getValue(GearBlock.FACING),1,1):BlockPos.ORIGIN;}
    private BlockPos armShift(){return armWidth()==2?LargeGearBlock.offset(world.getBlockState(pos).getValue(GearBlock.FACING),1,1):BlockPos.ORIGIN;}
    public int armWidth(){return world!=null&&world.getBlockState(pos).getBlock() instanceof GearBlock?((GearBlock)world.getBlockState(pos).getBlock()).width():1;}
    public boolean isSwingArmController() {
        return world != null && GearAssembly.loaded(world, pos) && GearAssembly.isSwingArm(world.getBlockState(pos));
    }

    public double renderProgress(float partialTicks) {
        return GearMotion.interpolate(previousProgress, progress, partialTicks);
    }

    public boolean fullyDeployed() { return isFixedMount() || deployedTarget && progress >= 1.0; }
    public boolean isFixedMount(){return mode==GearMotion.Mode.STATIC;}

    @Nullable
    public GearTileEntity loadedController() {
        if (assembly != null) return this;
        if (owner == null || world == null || !GearAssembly.loaded(world, owner)) return null;
        TileEntity tile = world.getTileEntity(owner);
        if (!(tile instanceof GearTileEntity)) return null;
        GearTileEntity root = (GearTileEntity) tile;
        return root.assembly != null && root.members.contains(pos) ? root : null;
    }

    public boolean hasLoadedController() {
        GearTileEntity root = loadedController();
        return root != null && root.operational;
    }

    /** Unformed/orphan fallback blocks keep their visible construction collision. */
    public boolean isPartCollidable() {
        GearTileEntity root = loadedController();
        return root == null || !root.operational || root.fullyDeployed();
    }

    @Override public void setWorld(World world){super.setWorld(world);rebuildMemberCache();boxesAt=Double.NaN;}

    @Override
    public void onLoad() { firstServerTick = true;rebuildMemberCache();GearGeometry.register(this); }
    @Override public void invalidate(){GearGeometry.remove(this);super.invalidate();}
    @Override public void onChunkUnload(){GearGeometry.remove(this);super.onChunkUnload();}

    public void scheduleRebuild() {
        if (world != null && !world.isRemote && rebuildDelay < 0) rebuildDelay = 2;
    }

    public static void requestNearbyRebuild(World world, BlockPos pos) {
        if (world.isRemote || !GearAssembly.loaded(world, pos)) return;
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof GearTileEntity) {
            GearTileEntity part = (GearTileEntity) tile;
            GearTileEntity root = part.loadedController();
            if (root != null) {
                root.scheduleRebuild();
                return;
            }
            // A persisted pointer can still reach a controller which is
            // loaded but temporarily unformed after a neighboring chunk left.
            if (part.owner != null && GearAssembly.loaded(world, part.owner)) {
                TileEntity former = world.getTileEntity(part.owner);
                if (former instanceof GearTileEntity) ((GearTileEntity) former).scheduleRebuild();
            }
        }
        IBlockState state = world.getBlockState(pos);
        if (GearAssembly.isExtender(state)) {
            BlockPos top = GearAssembly.topOfColumn(world, pos);
            if (top != null) {
                TileEntity root = world.getTileEntity(top);
                if (root instanceof GearTileEntity) ((GearTileEntity) root).scheduleRebuild();
            }
        } else if (GearAssembly.isFoot(state)) {
            GearAssembly.requestControllersNearFoot(world, pos);
        }
    }

    @Override
    public void update() {
        if (world == null) return;
        if(isFixedMount()){deployedTarget=true;progress=1;}
        previousProgress = progress;
        if (world.isRemote) {
            if (isOperational()&&!motionBlocked) progress = GearMotion.advance(progress, deployedTarget, 1, ANIMATION_TICKS);
            return;
        }
        if (firstServerTick) {
            firstServerTick = false;
            rebuildMemberCache();
            requestNearbyRebuild(world, pos);
        }
        if (rebuildDelay >= 0 && --rebuildDelay < 0) rebuild();

        // This checks the cached list once per second. It is not a flood
        // fill and never runs in the render path. It catches chunk unloads,
        // externally replaced blocks and stale saved member pointers.
        if ((world.getTotalWorldTime() + (pos.toLong() & 15)) % 20 == 0) {
            if (retryUnavailable || assembly != null && (!operational || !cachedMembersIntact())) {
                // scan() distinguishes a temporary missing chunk/tile from
                // broken geometry, so a reload never silently deploys gear.
                rebuild();
            } else if (assembly == null && owner != null && GearAssembly.loaded(world, owner)
                    && loadedController() == null) {
                clearBinding();
                requestNearbyRebuild(world, pos);
            }
        }

        if (isOperational()) {
            input();
            double before = progress;
            double next=GearMotion.advance(progress,deployedTarget,1,ANIMATION_TICKS);
            boolean blocked=next!=before&&!GearGeometry.canMove(this,before,next);
            if(blocked!=motionBlocked){motionBlocked=blocked;sync();}
            if(!blocked&&next!=before){GearGeometry.pushEntities(world,GearGeometry.boxes(this,before),GearGeometry.boxes(this,next),null);progress=next;boxesAt=Double.NaN;markDirty();if(progress==0||progress==1){sync();if(world!=null)com.vandorlabs.tiles.LandingGearCovers.refresh(world,pos);}else if(world.getTotalWorldTime()%3==0)sync();}
        }
    }

    private boolean cachedMembersIntact() {
        if (assembly == null) return false;
        IBlockState rootState = world.getBlockState(pos);
        BlockPos support = pos.up();
        if (!GearAssembly.loaded(world, support)
                || world.getBlockState(support).getBlock() instanceof GearBlock
                || !world.getBlockState(support).isSideSolid(world, support, EnumFacing.DOWN)) return false;
        for(BlockPos column:columns){BlockPos mount=pos.add(column).up();if(!GearAssembly.loaded(world,mount)||world.getBlockState(mount).getBlock() instanceof GearBlock||!world.getBlockState(mount).isSideSolid(world,mount,EnumFacing.DOWN))return false;}
        for (BlockPos member : members) {
            if (!GearAssembly.loaded(world, member)) return false;
            IBlockState state = world.getBlockState(member);
            if(GearAssembly.isExtender(state)){
                if(state.getBlock()!=rootState.getBlock())return false;
                GearBlock block=(GearBlock)state.getBlock();if(!block.componentIntact(world,block.componentRoot(member,state)))return false;
            }else{
                if(!GearAssembly.isFoot(state)||!((GearBlock)state.getBlock()).kind().name().equals(assembly.kind().name()))return false;
                if(assembly.kind()!=GearTopology.FootKind.PLATE&&state.getValue(GearBlock.FACING)!=footFacing)return false;
                if(assembly.kind()==GearTopology.FootKind.WHEEL && ((GearBlock)state.getBlock()).width()!=armWidth())return false;
                GearBlock block=(GearBlock)state.getBlock();if(!block.componentIntact(world,block.componentRoot(member,state)))return false;
            }
            TileEntity tile = world.getTileEntity(member);
            if (!(tile instanceof GearTileEntity) || !pos.equals(((GearTileEntity) tile).owner)) return false;
            if (!state.getValue(GearBlock.ASSEMBLED)) return false;
        }
        return true;
    }

    /** Called only after placement/load/neighbor changes or an explicit interaction. */
    public boolean rebuild() {
        if (world == null || world.isRemote || releasing) return false;
        rebuildDelay = -1;
        if (!GearAssembly.loaded(world, pos) || !GearAssembly.isExtender(world.getBlockState(pos))) return false;
        GearAssembly.Scan scan = GearAssembly.scan(world, pos);
        if (!scan.valid()) {
            lastError = scan.error;
            retryUnavailable = scan.temporary;
            if (scan.temporary && assembly != null) {
                if (operational) {
                    operational = false;
                    sync();
                }
                return false;
            }
            releaseAssembly();
            return false;
        }
        if(!pos.equals(scan.primary)){
            TileEntity primary=world.getTileEntity(scan.primary);
            return primary instanceof GearTileEntity&&((GearTileEntity)primary).rebuild();
        }
        retryUnavailable = false;
        if(assembly==null)for(GearTopology.Cell foot:scan.assembly.feet()){TileEntity raw=world.getTileEntity(pos.add(foot.x,foot.y,foot.z));GearTileEntity former=raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;if(former!=null&&former!=this){if(!former.fullyDeployed()){lastError="Deploy the connected group before changing its controller";return false;}configure(former.settings());break;}}
        GearMotion.Mode previousMode = mode;
        if (isSwingArmController()) {
            if (mode != GearMotion.Mode.ROTATE_LEVEL&&!configured) {
                // A newly placed arm bows toward its fold. Match
                // the visible static block's yaw on its first formation.
                direction = GearMotion.Direction.valueOf(world.getBlockState(pos)
                        .getValue(GearBlock.FACING).name());
            }
            mode = GearMotion.Mode.ROTATE_LEVEL;
        }
        else mode = world.getBlockState(pos).getBlock() instanceof GearBlock && ((GearBlock)world.getBlockState(pos).getBlock()).kind()==GearBlock.Kind.MOUNT?GearMotion.Mode.STATIC:GearMotion.Mode.EXTEND;
        boolean unchanged = assembly != null && assembly.columnHeight() == scan.assembly.columnHeight()
                && assembly.kind() == scan.assembly.kind() && footFacing == scan.footFacing
                && assembly.feet().equals(scan.assembly.feet()) && columns.equals(relativeColumns(scan.columns));
        if (unchanged && cachedMembersIntact()) {
            lastError = "";
            if (!operational || mode != previousMode) {
                operational = true;
                sync();
            }
            return true;
        }
        boolean desiredTarget=deployedTarget;
        if(!unchanged){
            for(BlockPos column:scan.columns){TileEntity raw=world.getTileEntity(column);if(raw instanceof GearTileEntity&&((GearTileEntity)raw).assembly!=null&&!((GearTileEntity)raw).fullyDeployed()){lastError="Deploy the connected group before changing its topology";return false;}}
            releaseAssembly();
            progress = previousProgress = 1.0;
            deployedTarget = configured?desiredTarget:true;
        }
        for(BlockPos column:scan.columns){TileEntity raw=world.getTileEntity(column);if(raw instanceof GearTileEntity&&raw!=this&&((GearTileEntity)raw).assembly!=null)((GearTileEntity)raw).releaseAssembly();}
        if(isFixedMount()){deployedTarget=true;progress=previousProgress=1;}
        assembly = scan.assembly;
        columns.clear();columns.addAll(relativeColumns(scan.columns));
        operational = true;
        footFacing = scan.footFacing;
        owner = pos.toImmutable();
        rebuildMemberCache();
        lastError = "";
        for (BlockPos member : members) {
            GearTileEntity tile = (GearTileEntity) world.getTileEntity(member);
            tile.owner = pos.toImmutable();
            if (tile != this) {
                tile.assembly = null;GearGeometry.remove(tile);
                tile.members.clear();
                tile.rebuildDelay = -1;
                tile.retryUnavailable = false;
                tile.operational = true;
            }
            tile.setAssembled(true);
            tile.sync();
        }
        boxesAt=Double.NaN;GearGeometry.register(this);sync();
        return true;
    }

    private List<BlockPos> relativeColumns(List<BlockPos> roots){List<BlockPos> result=new ArrayList<>();for(BlockPos root:roots)result.add(root.subtract(pos));return result;}

    private void rebuildMemberCache() {
        members.clear();
        if (assembly == null) return;
        // setWorld/readFromNBT can run before the loading chunk is published.
        // Looking up a state there must never recursively request that chunk.
        for(BlockPos column:columns)for(int i=0;i<assembly.columnHeight();i++){BlockPos at=pos.add(column).down(i);if(world!=null&&GearAssembly.loaded(world,at)&&world.getBlockState(at).getBlock() instanceof GearBlock)members.addAll(((GearBlock)world.getBlockState(at).getBlock()).componentCells(world,at));else members.add(at);}
        for (GearTopology.Cell foot : assembly.feet()) members.add(pos.add(foot.x, foot.y, foot.z));
    }

    /**
     * Detaches only metadata/ownership. No block is destroyed, moved, replaced
     * with air, or force-loaded, including when the controller is broken.
     */
    public void releaseAssembly() {
        if (world == null || world.isRemote || releasing) return;
        GearTileEntity root = loadedController();
        if (root != null && root != this) {
            root.releaseAssembly();
            return;
        }
        if (assembly == null) {
            if (owner != null) clearBinding();
            return;
        }
        releasing = true;
        NBTTagCompound retainedSettings=settings();
        List<BlockPos> former = new ArrayList<BlockPos>(members);
        assembly = null;
        GearGeometry.remove(this);boxesAt=Double.NaN;
        members.clear();
        progress = previousProgress = 1.0;
        deployedTarget = true;
        for (BlockPos member : former) {
            if (!GearAssembly.loaded(world, member)) continue;
            TileEntity raw = world.getTileEntity(member);
            if (!(raw instanceof GearTileEntity)) continue;
            GearTileEntity tile = (GearTileEntity) raw;
            if (pos.equals(tile.owner)){tile.configure(retainedSettings);tile.clearBinding();}
        }
        owner = null;
        sync();
        releasing = false;
        // If a former root became an internal extender after the hull was
        // raised, a failed competing scan must not leave the new top idle.
        BlockPos newTop = GearAssembly.topOfColumn(world, pos);
        if (newTop != null && !newTop.equals(pos)) {
            TileEntity raw = world.getTileEntity(newTop);
            if (raw instanceof GearTileEntity) ((GearTileEntity) raw).scheduleRebuild();
        }
    }

    private void clearBinding() {
        owner = null;
        GearGeometry.register(this);
        setAssembled(false);
        sync();
    }

    private void setAssembled(boolean assembled) {
        if (!GearAssembly.loaded(world, pos)) return;
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof GearBlock && state.getValue(GearBlock.ASSEMBLED) != assembled) {
            // Flag 2 updates clients without creating recursive neighbor
            // notifications just because the renderer ownership changed.
            world.setBlockState(pos, state.withProperty(GearBlock.ASSEMBLED, assembled), 2);
        }
    }

    public NBTTagCompound settings(){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Trigger",triggerMode);tag.setInteger("Direction",direction.ordinal());tag.setBoolean("Deployed",deployedTarget);tag.setString("Channels",channels.toString());tag.setBoolean("LegacyAnyChannels",legacyAnyChannels);return tag;}
    public void configure(NBTTagCompound tag){
        int trigger=tag.getInteger("Trigger"),dir=tag.getInteger("Direction");
        com.vandorlabs.redstone.ChannelList values=com.vandorlabs.redstone.ChannelList.parse(tag.getString("Channels"));
        if(trigger<0||trigger>2||dir<0||dir>3||values==null)throw new IllegalArgumentException("Invalid gear settings");
        if(dir!=direction.ordinal()&&!fullyDeployed())throw new IllegalArgumentException("Deploy fully before changing the fold direction");
        if(triggerMode!=trigger||!channels.equals(values))signalKnown=false;if(tag.hasKey("LegacyAnyChannels",1)&&legacyAnyChannels!=tag.getBoolean("LegacyAnyChannels")){legacyAnyChannels=tag.getBoolean("LegacyAnyChannels");signalKnown=false;}configured=true;triggerMode=trigger;channels=values;direction=GearMotion.Direction.values()[dir];boxesAt=Double.NaN;if(triggerMode==0)deployedTarget=isFixedMount()||tag.getBoolean("Deployed");input();sync();if(world!=null)com.vandorlabs.tiles.LandingGearCovers.refresh(world,pos);
    }

    public void sync() {
        markDirty();
        if (world != null && !world.isRemote && GearAssembly.loaded(world, pos)) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (owner != null) tag.setLong("GearOwnerOffset", owner.subtract(pos).toLong());
        tag.setInteger("GearTrigger",triggerMode);tag.setString("GearChannels",channels.toString());
        NBTTagList columnList=new NBTTagList();for(BlockPos column:columns)columnList.appendTag(new NBTTagIntArray(new int[]{column.getX(),column.getY(),column.getZ()}));tag.setTag("GearColumns",columnList);
        tag.setBoolean("GearConfigured",configured);tag.setBoolean("GearBlocked",motionBlocked);
        tag.setString("GearMode", mode.name());
        tag.setString("GearDirection", direction.name());
        tag.setBoolean("GearTargetDeployed", deployedTarget);tag.setBoolean("GearSignalKnown",signalKnown);tag.setBoolean("GearLastSignal",lastSignal);tag.setBoolean("GearLegacyAnyChannels",legacyAnyChannels);
        tag.setDouble("GearProgress", progress);
        tag.setBoolean("GearOperational", operational);
        tag.setBoolean("GearController", assembly != null);
        if (assembly != null) {
            tag.setInteger("GearHeight", assembly.columnHeight());
            tag.setString("GearKind", assembly.kind().name());
            tag.setInteger("GearFootFacing", footFacing.getHorizontalIndex());
            NBTTagList feet = new NBTTagList();
            for (GearTopology.Cell foot : assembly.feet()) {
                feet.appendTag(new NBTTagIntArray(new int[] { foot.x, foot.y, foot.z }));
            }
            tag.setTag("GearFeet", feet);
        }
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        configured=tag.getBoolean("GearConfigured");motionBlocked=tag.getBoolean("GearBlocked");boxesAt=Double.NaN;
        owner=tag.hasKey("GearOwnerOffset",4)?pos.add(BlockPos.fromLong(tag.getLong("GearOwnerOffset"))):null;
        triggerMode=Math.max(0,Math.min(2,tag.getInteger("GearTrigger")));com.vandorlabs.redstone.ChannelList read=com.vandorlabs.redstone.ChannelList.parse(tag.getString("GearChannels"));channels=read==null?com.vandorlabs.redstone.ChannelList.EMPTY:read;
        columns.clear();NBTTagList columnList=tag.getTagList("GearColumns",11);if(columnList.tagCount()<=64)for(int i=0;i<columnList.tagCount();i++){int[] xyz=columnList.getIntArrayAt(i);if(xyz.length==3&&xyz[1]==0&&Math.abs((long)xyz[0])<16&&Math.abs((long)xyz[2])<16)columns.add(new BlockPos(xyz[0],0,xyz[2]));}
        if(columns.isEmpty())columns.add(BlockPos.ORIGIN);
        mode = parseEnum(GearMotion.Mode.class, tag.getString("GearMode"), GearMotion.Mode.EXTEND);
        direction = parseEnum(GearMotion.Direction.class, tag.getString("GearDirection"), GearMotion.Direction.NORTH);
        deployedTarget = !tag.hasKey("GearTargetDeployed") || tag.getBoolean("GearTargetDeployed");signalKnown=tag.getBoolean("GearSignalKnown");lastSignal=tag.getBoolean("GearLastSignal");legacyAnyChannels=tag.getBoolean("GearLegacyAnyChannels");
        double savedProgress = tag.hasKey("GearProgress") ? tag.getDouble("GearProgress") : 1.0;
        progress = previousProgress = Double.isFinite(savedProgress) ? Math.max(0, Math.min(1, savedProgress)) : 1.0;
        operational = !tag.hasKey("GearOperational") || tag.getBoolean("GearOperational");
        assembly = null;
        if (tag.getBoolean("GearController")) {
            NBTTagList rawFeet = tag.getTagList("GearFeet", 11);
            if (rawFeet.tagCount() <= GearTopology.MAX_FEET) {
                List<GearTopology.Cell> feet = new ArrayList<GearTopology.Cell>();
                for (int i = 0; i < rawFeet.tagCount(); i++) {
                    int[] xyz = rawFeet.getIntArrayAt(i);
                    if (xyz.length == 3) feet.add(new GearTopology.Cell(xyz[0], xyz[1], xyz[2]));
                }
                GearTopology.FootKind kind = parseEnum(GearTopology.FootKind.class,
                        tag.getString("GearKind"), GearTopology.FootKind.PLATE);
                GearTopology.Result result = GearTopology.validate(tag.getInteger("GearHeight"), kind, feet);
                if (result.isValid() && feet.size() == rawFeet.tagCount()) {
                    assembly = result.assembly();
                    owner = pos.toImmutable();
                    footFacing = EnumFacing.getHorizontal(tag.getInteger("GearFootFacing") & 3);
                }
            }
        }
        rebuildMemberCache();GearGeometry.register(this);
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value, T fallback) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException exception) { return fallback; }
    }

    @Override
    public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) { readFromNBT(tag); }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }

    @Override
    public void onDataPacket(NetworkManager manager, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    @Override
    public boolean hasFastRenderer() { return false; }

    @Override
    public double getMaxRenderDistanceSquared() { return 256.0 * 256.0; }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        if (assembly == null) {
            if(armWidth()==2&&world!=null){AxisAlignedBB extent=new AxisAlignedBB(pos);GearBlock block=(GearBlock)world.getBlockState(pos).getBlock();for(BlockPos cell:block.componentCells(world,pos))extent=extent.union(new AxisAlignedBB(cell));return isSwingArmController()||block.kind()==GearBlock.Kind.WHEEL?extent.union(extent.offset(0,-1,0)):extent;}
            // The unformed swing arm already reaches into the foot cell.
            return isSwingArmController()
                    ? new AxisAlignedBB(pos.getX(), pos.getY() - 1, pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)
                    : new AxisAlignedBB(pos);
        }
        double rx = Math.max(Math.abs(assembly.minX() - 0.5), Math.abs(assembly.maxX() + 0.5));
        double rz = Math.max(Math.abs(assembly.minZ() - 0.5), Math.abs(assembly.maxZ() + 0.5));
        double ry = assembly.columnHeight() + GearMotion.PIVOT_Y;
        double radius = Math.sqrt(rx * rx + ry * ry + rz * rz) + 1.0;
        AxisAlignedBB rigidBounds = new AxisAlignedBB(pos.getX() + 0.5 - radius, pos.getY() + GearMotion.PIVOT_Y - radius,
                pos.getZ() + 0.5 - radius, pos.getX() + 0.5 + radius,
                pos.getY() + GearMotion.PIVOT_Y + radius, pos.getZ() + 0.5 + radius);
        if (mode != GearMotion.Mode.ROTATE_LEVEL) return rigidBounds;
        // A level pad keeps its full horizontal extent while the ankle
        // translates. A rigid-rotation sphere alone can clip asymmetric
        // 16-wide groups, so also cover the entire ankle translation arc.
        double travel = assembly.columnHeight() + GearMotion.PIVOT_Y - GearMotion.FOOT_SOCKET_Y;
        AxisAlignedBB sweptLevelFeet = new AxisAlignedBB(pos.getX() + assembly.minX() - travel,
                pos.getY() - assembly.columnHeight(), pos.getZ() + assembly.minZ() - travel,
                pos.getX() + assembly.maxX() + 1 + travel,
                pos.getY() - assembly.columnHeight() + 1 + travel,
                pos.getZ() + assembly.maxZ() + 1 + travel);
        return rigidBounds.union(sweptLevelFeet);
    }
}
