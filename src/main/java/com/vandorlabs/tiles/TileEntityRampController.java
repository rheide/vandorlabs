package com.vandorlabs.tiles;

import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.redstone.RedstoneChannels;
import com.vandorlabs.blocks.BlockRampController;
import com.vandorlabs.blocks.BlockVandorDirectional;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.ramp.ControllerPlatform;
import com.vandorlabs.ramp.RampGeometry;
import com.vandorlabs.ramp.VerticalBounds;
import com.vandorlabs.persistence.NbtPrimitiveData;
import com.vandorlabs.persistence.RampControllerData;
import com.vandorlabs.persistence.SaveSchema;
import com.vandorlabs.persistence.LegacyBlockStates;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;

/** Event-driven controller. Only active animation schedules subsequent block ticks. */
public class TileEntityRampController extends TileEntity implements RedstoneChannelMember {
    public int drop=3,segments=2;
    public boolean top=true,activateOnPower=true,slow,elevator;
    public String status="Retracted";
    public boolean error;
    public int clientUpdates;
    private final List<BlockPos> sources=new ArrayList<>();
    private final Set<BlockPos> cells=new HashSet<>();
    private IBlockState original=Blocks.AIR.getDefaultState();
    private boolean open,moving,changing,latched,signalKnown,needsLoadCheck,recoveryPending,legacy;
    private double startPose,low,high=1;
    private long startTick,lastStepTick;
    private int length=1,minAlong,duration=60;
    private EnumFacing facing=EnumFacing.NORTH;
    private EnumFacing configuredFacing;
    private UUID owner;
    private int redstoneChannel;
    private boolean channelSignal;

    @Override public TileEntity channelTile() { return this; }
    @Override public int getRedstoneChannel() { return redstoneChannel; }
    @Override public boolean hasLocalRedstoneSignal() { return world != null && world.isBlockPowered(pos); }
    @Override public void setChannelSignal(boolean powered) {
        if (channelSignal == powered) return;
        channelSignal = powered;
        if (world != null && !world.isRemote) evaluateSignal(false);
    }
    @Override public void setRedstoneChannel(int value) {
        int next = Math.max(0, value);
        if (next == redstoneChannel) return;
        int old = redstoneChannel;
        redstoneChannel = next;
        markDirty();
        RedstoneChannels.channelChanged(this, old);
        if (world != null && !world.isRemote) evaluateSignal(true);
    }

    public boolean attached() { return !sources.isEmpty(); }
    @Override public boolean shouldRefresh(net.minecraft.world.World world,BlockPos pos,IBlockState before,IBlockState after) {
        // The on/off model flag must never replace the tile or discard its journal.
        return before.getBlock()!=after.getBlock();
    }
    public int area() { return sources.size(); }
    public boolean isOpen() { return open; }
    public boolean isMoving() { return moving; }
    public int durationTicks() { return duration; }
    public EnumFacing rampDirection() {
        if (configuredFacing!=null) return configuredFacing;
        IBlockState state=world==null?null:world.getBlockState(pos);
        return state!=null && state.getBlock() instanceof BlockRampController
                ?state.getValue(BlockVandorDirectional.FACING):facing;
    }
    public void setOwner(EntityPlayer player) { owner=player.getUniqueID(); markDirty(); }
    public double pose(double partial) {
        return moving?ControllerPlatform.pose(startPose,open,world.getTotalWorldTime()-startTick+partial,duration):startPose;
    }
    private BlockPos reserved(BlockPos source,int distance) { return source.up(top?-distance:distance); }
    public boolean owns(TileEntityControlledRamp part) {
        int distance=top?part.sourceY-part.getPos().getY():part.getPos().getY()-part.sourceY;
        return cells.contains(part.getPos()) && sources.contains(new BlockPos(part.getPos().getX(),part.sourceY,part.getPos().getZ()))
                && distance>=0 && distance<=drop;
    }
    public boolean usable(EntityPlayer player) {
        return world.getTileEntity(pos)==this && player.getDistanceSq(pos)<=64
                && player.canPlayerEdit(pos,EnumFacing.UP,player.getHeldItemMainhand())
                && world.isBlockModifiable(player,pos);
    }
    private boolean fail(String reason) { error=true; status=reason; sync(); return false; }
    private boolean hold(String reason) {
        if (moving) startPose=ControllerPlatform.pose(startPose,open,lastStepTick-startTick,duration);
        moving=false; syncParts(); releaseExcept(occupied(startPose,startPose));
        return fail(reason);
    }
    private void sync() {
        markDirty();
        IBlockState s=world.getBlockState(pos);
        if (s.getBlock() instanceof BlockRampController && s.getValue(BlockRampController.ACTIVE)!=open) {
            s=s.withProperty(BlockRampController.ACTIVE,open);
            world.setBlockState(pos,s,2);
        }
        world.notifyBlockUpdate(pos,s,s,2);
    }
    public boolean configure(EntityPlayer player,int height,int treadCount,boolean upper,
            boolean powerOn,boolean slower,boolean lift) {
        return configure(player,height,treadCount,upper,powerOn,slower,lift,rampDirection());
    }
    public boolean configure(EntityPlayer player,int height,int treadCount,boolean upper,
            boolean powerOn,boolean slower,boolean lift,EnumFacing direction) {
        if (world.isRemote || !usable(player)) return false;
        if (direction==null || !direction.getAxis().isHorizontal()) return fail("Choose a horizontal ramp direction");
        if (height<1 || height>ControllerPlatform.MAX_DROP || (treadCount!=2 && treadCount!=8)) return fail("Travel must be 1–8 blocks");
        // Reset using the old geometry and journal before installing new settings.
        if (attached()) {
            for (BlockPos source:sources) {
                if (!world.isBlockLoaded(source)) return hold("Reset waits for platform chunks");
                TileEntity te=world.getTileEntity(source);
                if (!world.isAirBlock(source) && !(te instanceof TileEntityControlledRamp
                        && ((TileEntityControlledRamp)te).belongsTo(pos)))
                    return hold("Reset blocked: clear the original platform position");
            }
            if (!carryRiders(pose(0),0)) return hold("Reset blocked: a rider is obstructed");
            if (!recover(false)) return false;
        }
        drop=height; segments=treadCount; top=upper; activateOnPower=powerOn; slow=slower; elevator=lift;
        configuredFacing=direction;
        owner=player.getUniqueID();
        sync();
        return evaluateSignal(true);
    }
    public void updatePower() {
        if (!world.isRemote && !changing) {
            RedstoneChannels.inputChanged(this);
            evaluateSignal(false);
        }
    }
    private boolean evaluateSignal(boolean force) {
        boolean desired=(world.isBlockPowered(pos) || channelSignal)==activateOnPower;
        if (!force && signalKnown && desired==latched) return !error;
        signalKnown=true; latched=desired;
        return request(desired);
    }

    /** Public for the block event adapter and live-world regression checks. */
    public boolean request(boolean deploy) {
        if (world.isRemote || changing) return false;
        if (recoveryPending && !recover(false)) return false;
        if (!attached()) {
            if (!deploy) { open=false; moving=false; error=false; status="Retracted"; sync(); return true; }
            if (!capture()) return false;
        }
        String problem=validateParts();
        if (problem!=null) {
            double current=pose(0);
            moving=false; startPose=current; syncParts();
            if (!problem.equals("Load all platform chunks first")) recover(false);
            return fail(problem);
        }
        startPose=pose(0); startTick=world.getTotalWorldTime(); lastStepTick=startTick;
        open=deploy; moving=Math.abs(startPose-(deploy?1:0))>1e-8;
        duration=ControllerPlatform.duration(length,drop,slow);
        error=false; status=moving?(deploy?"Deploying":"Retracting"):(deploy?"Deployed":"Retracted");
        syncParts(); sync();
        if (!deploy && !moving) return recover(false);
        if (moving && !reserveStep(startPose,pose(1))) {
            moving=false; syncParts(); sync(); return false;
        }
        if (moving) schedule();
        return true;
    }

    private boolean capture() {
        // Old saves retain their full journal until restored; only new deployments are capped.
        drop=Math.min(drop,ControllerPlatform.MAX_DROP);
        EnumFacing scanFacing=world.getBlockState(pos).getValue(BlockVandorDirectional.FACING);
        facing=rampDirection();
        BlockPos seed=pos.offset(scanFacing);
        if (!world.isBlockLoaded(seed)) return fail("Load all platform chunks first");
        IBlockState material=world.getBlockState(seed);
        if (material.getBlock().hasTileEntity(material) || material.getBlockHardness(world,seed)<0
                || (!(material.getBlock() instanceof BlockSlab) && !material.isFullCube()))
            return fail("Front block must be a slab or ordinary solid block");
        boolean[] loaded={true};
        Set<ControllerPlatform.Cell> platform=ControllerPlatform.discover(
                new ControllerPlatform.Cell(seed.getX(),seed.getY(),seed.getZ()),c->{
            BlockPos p=new BlockPos(c.x,c.y,c.z);
            if (!world.isBlockLoaded(p)) { loaded[0]=false; return false; }
            return world.getBlockState(p).equals(material);
        });
        if (!loaded[0]) return fail("Load all platform chunks first");
        if (platform.isEmpty() || !VerticalBounds.legacy(world.getHeight()).contains(reserved(seed,drop).getY()))
            return fail("Travel exceeds world height");
        EntityPlayer actor=owner==null?null:world.getPlayerEntityByUUID(owner);
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        List<BlockPos> selected=new ArrayList<>();
        for (ControllerPlatform.Cell c:platform) {
            BlockPos source=new BlockPos(c.x,c.y,c.z);
            selected.add(source);
            int along=c.x*facing.getFrontOffsetX()+c.z*facing.getFrontOffsetZ();
            min=Math.min(min,along); max=Math.max(max,along);
            for (int distance=0;distance<=drop;distance++) {
                BlockPos p=reserved(source,distance);
                if (!world.isBlockLoaded(p)) return fail("Load all platform chunks first");
                if (actor!=null && (!actor.canPlayerEdit(p,EnumFacing.UP,actor.getHeldItemMainhand())
                        || !world.isBlockModifiable(actor,p))) return fail("No permission to move this platform");
                if (distance>0 && !world.isAirBlock(p)) return fail("Movement path is obstructed");
            }
        }
        AxisAlignedBB bounds=material.getBoundingBox(world,seed);
        if (bounds.minX!=0 || bounds.maxX!=1 || bounds.minZ!=0 || bounds.maxZ!=1)
            return fail("Only full-width slabs and cubes are supported");
        // Journal the whole transaction before replacing its first source.
        sources.addAll(selected); original=material; low=bounds.minY; high=bounds.maxY;
        length=max-min+1; minAlong=min; duration=ControllerPlatform.duration(length,drop,slow);
        startPose=0; moving=false; open=false; startTick=world.getTotalWorldTime();
        changing=true; markDirty();
        try {
            for (BlockPos source:sources) install(source);
        } catch (RuntimeException exception) {
            recover(false);
            return fail("Deployment failed; original blocks restored");
        } finally { changing=false; }
        return true;
    }
    /** Single mutation seam permits real rollback fault injection in runtime tests. */
    protected boolean placePart(BlockPos target,IBlockState state) { return world.setBlockState(target,state,2); }

    private void install(BlockPos p) {
        cells.add(p); markDirty(); // Journal before mutation, including fault-injected partial writes.
        if (!placePart(p,ModBlocks.CONTROLLED_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing)))
            throw new IllegalStateException("reservation failed");
        TileEntityControlledRamp te=(TileEntityControlledRamp)world.getTileEntity(p);
        te.controller=pos; te.source=original; te.sourceY=sources.get(0).getY();
        te.row=p.getX()*facing.getFrontOffsetX()+p.getZ()*facing.getFrontOffsetZ()-minAlong;
        te.length=length; te.drop=drop; te.segments=segments; te.top=top; te.elevator=elevator;
        te.low=low; te.high=high; te.move(open,startPose,startTick,duration,moving);
    }

    /** Only geometry touched by this animation step is examined; nothing runs while idle. */
    private Set<BlockPos> occupied(double from,double to) {
        Set<BlockPos> result=new HashSet<>();
        for (BlockPos source:sources) {
            int row=source.getX()*facing.getFrontOffsetX()+source.getZ()*facing.getFrontOffsetZ()-minAlong;
            for (int step=0;step<(elevator?1:segments);step++) {
                double a=ControllerPlatform.offset(row,step,length,segments,drop,from,top,elevator);
                double b=ControllerPlatform.offset(row,step,length,segments,drop,to,top,elevator);
                int first=RampGeometry.firstOccupiedY(source.getY(),low,a,b);
                int last=RampGeometry.lastOccupiedY(source.getY(),high,a,b);
                for (int y=first;y<=last;y++) result.add(new BlockPos(source.getX(),y,source.getZ()));
            }
        }
        return result;
    }

    private static RampGeometry.Direction rampDirection(EnumFacing facing) {
        return RampGeometry.Direction.valueOf(
                facing.getName().toUpperCase(java.util.Locale.ROOT));
    }

    private boolean reserveStep(double from,double to) {
        Set<BlockPos> needed=occupied(from,to);
        EntityPlayer actor=owner==null?null:world.getPlayerEntityByUUID(owner);
        for (BlockPos p:needed) {
            if (!world.isBlockLoaded(p)) return fail("Movement waits for platform chunks");
            if (cells.contains(p)) {
                TileEntity te=world.getTileEntity(p);
                if (!(te instanceof TileEntityControlledRamp) || !((TileEntityControlledRamp)te).belongsTo(pos))
                    return fail("Platform was changed; remove controller to recover");
                continue;
            }
            if (!world.isAirBlock(p)) return fail("Movement path is obstructed");
            if (actor!=null && (!actor.canPlayerEdit(p,EnumFacing.UP,actor.getHeldItemMainhand())
                    || !world.isBlockModifiable(actor,p))) return fail("No permission to move this platform");
        }
        Set<BlockPos> added=new HashSet<>();
        changing=true;
        try {
            for (BlockPos p:needed) if (!cells.contains(p)) { added.add(p); install(p); }
        } catch (RuntimeException exception) {
            for (BlockPos p:added) {
                TileEntity te=world.getTileEntity(p);
                if (te instanceof TileEntityControlledRamp && (((TileEntityControlledRamp)te).belongsTo(pos)
                        || ((TileEntityControlledRamp)te).controller.equals(BlockPos.ORIGIN))) world.setBlockToAir(p);
                cells.remove(p);
            }
            return fail("Movement failed; platform preserved");
        } finally { changing=false; }
        return true;
    }

    private void releaseExcept(Set<BlockPos> keep) {
        changing=true;
        try {
            for (BlockPos p:new HashSet<>(cells)) if (!keep.contains(p)) {
                if (!world.isBlockLoaded(p)) continue;
                TileEntity te=world.getTileEntity(p);
                if (te instanceof TileEntityControlledRamp && ((TileEntityControlledRamp)te).belongsTo(pos))
                    world.setBlockState(p,Blocks.AIR.getDefaultState(),3);
                cells.remove(p);
            }
        } finally { changing=false; markDirty(); }
    }

    private String validateParts() {
        for (BlockPos p:cells) {
            if (!world.isBlockLoaded(p)) return "Load all platform chunks first";
                TileEntity te=world.getTileEntity(p);
                if (!(te instanceof TileEntityControlledRamp) || !((TileEntityControlledRamp)te).belongsTo(pos)
                        || !owns((TileEntityControlledRamp)te)) return "Platform was changed; restored remaining original blocks";
        }
        return null;
    }
    private void syncParts() {
        for (BlockPos p:cells) {
            if (!world.isBlockLoaded(p)) continue;
                TileEntity te=world.getTileEntity(p);
                if (te instanceof TileEntityControlledRamp && ((TileEntityControlledRamp)te).belongsTo(pos))
                    ((TileEntityControlledRamp)te).move(open,startPose,startTick,duration,moving);
        }
    }
    private void schedule() { world.scheduleUpdate(pos,world.getBlockState(pos).getBlock(),1); }
    @Override public void onLoad() {
        super.onLoad();
        RedstoneChannels.register(this);
        if (!world.isRemote) { needsLoadCheck=true; schedule(); }
    }
    @Override public void invalidate() { RedstoneChannels.unregister(this); super.invalidate(); }
    @Override public void onChunkUnload() { RedstoneChannels.unregister(this); super.onChunkUnload(); }
    public void resumeAfterLoad() {
        if (!world.isRemote && (moving || recoveryPending || legacy)) schedule();
    }
    /** A scheduled tick is animation work or a one-shot load recovery, never idle polling. */
    public void scheduledTick() {
        if (world.isRemote || changing) return;
        if (recoveryPending) {
            if (recover(false)) evaluateSignal(true);
            return;
        }
        if (needsLoadCheck) {
            needsLoadCheck=false;
            if (legacy) { legacy=false; if (!recover(false)) return; }
            if (recoveryPending && !recover(false)) return;
            updatePower();
            // One-shot migration also releases old full-column locks on stationary saves.
            if (attached() && !moving) releaseExcept(occupied(startPose,startPose));
        }
        if (!moving || !attached()) return;
        if (world.getTotalWorldTime()==lastStepTick) { schedule(); return; }
        double previous=ControllerPlatform.pose(startPose,open,lastStepTick-startTick,duration);
        double current=pose(0);
        double next=pose(1);
        if (!reserveStep(previous,next)) {
            startPose=previous; moving=false; syncParts(); sync();
            releaseExcept(occupied(previous,previous));
            return;
        }
        if (!carryRiders(previous,current)) {
            startPose=previous; moving=false; syncParts();
            releaseExcept(occupied(previous,previous));
            fail("Movement stopped: a rider is obstructed");
            return;
        }
        lastStepTick=world.getTotalWorldTime();
        releaseExcept(occupied(current,next));
        if (Math.abs(current-(open?1:0))<1e-8) {
            startPose=open?1:0; moving=false; syncParts();
            if (!open) recover(false);
            else {
                // Completion is a single validation boundary, not a per-tick world scan.
                String problem=validateParts();
                if (problem!=null) { recover(false); fail(problem); }
                else { error=false; status="Deployed"; sync(); }
            }
        } else schedule();
    }
    private boolean carryRiders(double previous,double current) {
        Map<Entity,Double> targets=new LinkedHashMap<>();
        for (BlockPos source:sources) {
            int row=source.getX()*facing.getFrontOffsetX()+source.getZ()*facing.getFrontOffsetZ()-minAlong;
            double minY=source.getY()+high-drop-0.2,maxY=source.getY()+high+drop+0.2;
            for (Entity entity:world.getEntitiesWithinAABB(Entity.class,
                    new AxisAlignedBB(source.getX(),minY,source.getZ(),source.getX()+1,maxY,source.getZ()+1))) {
                if (entity.isDead || entity.noClip || entity.isRiding() || entity.motionY>0.1
                        || (entity instanceof EntityPlayer && ((EntityPlayer)entity).capabilities.isFlying)) continue;
                AxisAlignedBB body=entity.getEntityBoundingBox();
                // A rider straddling treads is supported by the highest overlapping surface.
                for (int step=0;step<(elevator?1:segments);step++) {
                RampGeometry.Box local=RampGeometry.footprint(rampDirection(facing),step,
                        elevator?1:segments,minY,maxY);
                AxisAlignedBB tread=new AxisAlignedBB(source.getX()+local.minX,local.minY,
                        source.getZ()+local.minZ,source.getX()+local.maxX,local.maxY,
                        source.getZ()+local.maxZ);
                if (!body.shrink(1e-7).intersects(tread)) continue;
                double before=source.getY()+high+ControllerPlatform.offset(row,step,length,segments,drop,previous,top,elevator);
                double after=source.getY()+high+ControllerPlatform.offset(row,step,length,segments,drop,current,top,elevator);
                double feet=entity.getEntityBoundingBox().minY;
                double tolerance=entity instanceof EntityPlayer?.5:.15;
                double target=RampGeometry.riderTarget(before,after,feet,tolerance);
                if (!Double.isNaN(target)) targets.merge(entity,target,Math::max);
                }
            }
        }
        // Preflight every rider before moving any: a ceiling must not leave passengers half-transported.
        for (Map.Entry<Entity,Double> entry:targets.entrySet()) {
            Entity entity=entry.getKey();
            AxisAlignedBB body=entity.getEntityBoundingBox();
            double delta=entry.getValue()-body.minY,allowed=delta;
            for (AxisAlignedBB obstacle:com.vandorlabs.blocks.BlockControlledRamp.riderObstacles(
                    world,pos,entity,body.expand(0,delta,0).grow(1e-7)))
                allowed=obstacle.calculateYOffset(body,allowed);
            if (Math.abs(allowed-delta)>1e-6) return false;
        }
        for (Map.Entry<Entity,Double> entry:targets.entrySet()) {
                Entity entity=entry.getKey();
                double before=entity.getEntityBoundingBox().minY;
                entity.setPosition(entity.posX,entity.posY+entry.getValue()-entity.getEntityBoundingBox().minY,entity.posZ);
                entity.onGround=true; entity.fallDistance=0; entity.motionY=0;
                if (entity instanceof net.minecraft.entity.player.EntityPlayerMP) {
                    net.minecraft.entity.player.EntityPlayerMP rider=(net.minecraft.entity.player.EntityPlayerMP)entity;
                    com.vandorlabs.network.PacketHandler.INSTANCE.sendTo(
                            new com.vandorlabs.network.MessagePlatformMotion(rider,before,entry.getValue()),rider);
                }
        }
        return true;
    }

    /** Restore originals and erase only cells owned by this controller.
     * Unexpected foreign blocks are preserved; displaced originals are refunded once. */
    public boolean recover(boolean forceLoad) {
        if (world.isRemote) return false;
        for (BlockPos source:sources) {
            if (forceLoad) world.getChunkFromBlockCoords(source);
            else if (!world.isBlockLoaded(source)) { recoveryPending=true; return fail("Recovery waits for platform chunks"); }
        }
        changing=true; recoveryPending=true; moving=false;
        List<BlockPos> changed=new ArrayList<>();
        try {
            for (BlockPos source:sources) {
                IBlockState saved=original;
                TileEntity sourceTile=world.getTileEntity(source);
                if (saved.getBlock()==Blocks.AIR && sourceTile instanceof TileEntityControlledRamp
                        && ((TileEntityControlledRamp)sourceTile).belongsTo(pos))
                    saved=((TileEntityControlledRamp)sourceTile).source;
                for (int d=drop;d>=0;d--) {
                    BlockPos p=reserved(source,d);
                    TileEntity te=world.getTileEntity(p);
                    boolean owned=te instanceof TileEntityControlledRamp && ((TileEntityControlledRamp)te).belongsTo(pos);
                    // An interrupted install may have created the block before its tile was initialized.
                    boolean unfinished=world.getBlockState(p).getBlock()==ModBlocks.CONTROLLED_RAMP
                            && te instanceof TileEntityControlledRamp
                            && ((TileEntityControlledRamp)te).controller.equals(BlockPos.ORIGIN)
                            && !pos.equals(BlockPos.ORIGIN);
                    if (owned || unfinished) {
                        world.setBlockState(p,d==0?saved:Blocks.AIR.getDefaultState(),2);
                        changed.add(p);
                    } else if (d==0 && !world.getBlockState(p).equals(saved)) {
                        if (world.isAirBlock(p)) { world.setBlockState(p,saved,2); changed.add(p); }
                        else if (saved.getBlock()!=Blocks.AIR)
                            Block.spawnAsEntity(world,pos.up(),new ItemStack(saved.getBlock(),1,saved.getBlock().damageDropped(saved)));
                    }
                }
            }
            sources.clear(); cells.clear(); recoveryPending=false; open=false; startPose=0;
            error=false; status="Retracted"; sync();
            for (BlockPos p:changed) world.notifyNeighborsOfStateChange(p,world.getBlockState(p).getBlock(),false);
            return true;
        } finally { changing=false; markDirty(); }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        new RampControllerData(SaveSchema.Ramp.CONTROLLER_VERSION,drop,segments,status,
                top,activateOnPower,slow,elevator,error,open,moving,startPose,startTick,
                lastStepTick,duration,length,minAlong,facing.getHorizontalIndex(),
                configuredFacing!=null,configuredFacing==null?0:configuredFacing.getHorizontalIndex(),
                low,high,latched,signalKnown,recoveryPending,redstoneChannel,channelSignal)
                .write(new NbtPrimitiveData(tag));
        tag.setTag(SaveSchema.Ramp.ORIGINAL,NBTUtil.writeBlockState(new NBTTagCompound(),original));
        tag.setString(SaveSchema.Ramp.ORIGINAL_STATE,LegacyBlockStates.encode(original));
        if (owner!=null) {
            tag.setUniqueId(SaveSchema.Ramp.OWNER,owner);
            tag.setString(SaveSchema.Ramp.OWNER_ID,owner.toString());
        }
        NBTTagList list=new NBTTagList();
        for (BlockPos p:sources) list.appendTag(writePosition(p));
        tag.setTag(SaveSchema.Ramp.SOURCES,list);
        NBTTagList reservations=new NBTTagList();
        for (BlockPos p:cells) reservations.appendTag(writePosition(p));
        tag.setTag(SaveSchema.Ramp.CELLS,reservations);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        int oldChannel=redstoneChannel;
        super.readFromNBT(tag);
        RampControllerData data=RampControllerData.read(new NbtPrimitiveData(tag));
        legacy=data.savedVersion<2; drop=data.drop; segments=data.segments;
        top=data.top; activateOnPower=data.activateOnPower; slow=data.slow;
        elevator=data.elevator; error=data.error; status=data.status;
        open=data.open; moving=data.moving; startPose=data.startPose;
        startTick=data.startTick; lastStepTick=data.lastStepTick; duration=data.duration;
        length=data.length; minAlong=data.minAlong; facing=EnumFacing.getHorizontal(data.facing);
        configuredFacing=data.hasDirection?EnumFacing.getHorizontal(data.direction):null;
        low=data.low; high=data.high; latched=data.latched; signalKnown=data.signalKnown;
        recoveryPending=data.recoveryPending; redstoneChannel=data.redstoneChannel;
        channelSignal=data.channelSignal;
        if (world!=null && !world.isRemote && oldChannel!=redstoneChannel)
            RedstoneChannels.channelChanged(this,oldChannel);
        original=tag.hasKey(SaveSchema.Ramp.ORIGINAL_STATE)
                ?LegacyBlockStates.decode(tag.getString(SaveSchema.Ramp.ORIGINAL_STATE)):null;
        if (original==null) original=tag.hasKey(SaveSchema.Ramp.ORIGINAL)
                ?NBTUtil.readBlockState(tag.getCompoundTag(SaveSchema.Ramp.ORIGINAL))
                :Blocks.AIR.getDefaultState();
        owner=readOwner(tag);
        sources.clear(); NBTTagList list=tag.getTagList(SaveSchema.Ramp.SOURCES,10);
        for (int i=0;i<Math.min(128,list.tagCount());i++) sources.add(readPosition(list.getCompoundTagAt(i)));
        cells.clear();
        if (data.savedVersion<3) {
            for (BlockPos source:sources) for (int d=0;d<=drop;d++) cells.add(reserved(source,d));
        } else {
            NBTTagList reservations=tag.getTagList(SaveSchema.Ramp.CELLS,10);
            for (int i=0;i<Math.min(2176,reservations.tagCount());i++) cells.add(readPosition(reservations.getCompoundTagAt(i)));
        }
    }
    private static NBTTagCompound writePosition(BlockPos pos) {
        NBTTagCompound tag=new NBTTagCompound();
        tag.setLong(SaveSchema.Ramp.POSITION,pos.toLong());
        tag.setInteger(SaveSchema.Ramp.X,pos.getX());
        tag.setInteger(SaveSchema.Ramp.Y,pos.getY());
        tag.setInteger(SaveSchema.Ramp.Z,pos.getZ());
        return tag;
    }
    private static BlockPos readPosition(NBTTagCompound tag) {
        return tag.hasKey(SaveSchema.Ramp.X)
                ?new BlockPos(tag.getInteger(SaveSchema.Ramp.X),tag.getInteger(SaveSchema.Ramp.Y),
                        tag.getInteger(SaveSchema.Ramp.Z))
                :BlockPos.fromLong(tag.getLong(SaveSchema.Ramp.POSITION));
    }
    private static UUID readOwner(NBTTagCompound tag) {
        if (tag.hasKey(SaveSchema.Ramp.OWNER_ID)) {
            try { return UUID.fromString(tag.getString(SaveSchema.Ramp.OWNER_ID)); }
            catch (IllegalArgumentException ignored) { }
        }
        return tag.hasUniqueId(SaveSchema.Ramp.OWNER)?tag.getUniqueId(SaveSchema.Ramp.OWNER):null;
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos,0,getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net,SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound()); clientUpdates++;
    }
}
