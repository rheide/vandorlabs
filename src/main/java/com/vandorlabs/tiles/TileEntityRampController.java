package com.vandorlabs.tiles;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.ChannelData;

import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.redstone.RedstoneChannels;
import com.vandorlabs.blocks.BlockRampController;
import com.vandorlabs.blocks.BlockVandorDirectional;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.ramp.ControllerPlatform;
import com.vandorlabs.ramp.RampCanopyCoordination;
import com.vandorlabs.ramp.RampGeometry;
import com.vandorlabs.ramp.VerticalBounds;
import com.vandorlabs.persistence.NbtPrimitiveData;
import com.vandorlabs.persistence.RampControllerData;
import com.vandorlabs.persistence.SaveSchema;
import com.vandorlabs.persistence.LegacyBlockStates;
import com.vandorlabs.compat.RampStairs;
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
    private ChannelList channels=ChannelList.EMPTY;
    @Override public ChannelList getRedstoneChannels(){return channels;}

    public int startOffset,treadPixels=8;
    private int startHalfSteps, endHalfSteps=-6;
    public int startHalfSteps() { return startHalfSteps; }
    public int endHalfSteps() { return endHalfSteps; }
    public double startOffsetValue() { return startHalfSteps/2D; }
    public double endOffsetValue() { return endHalfSteps/2D; }
    public int interpolation;
    /** 0 = ordinary modes, 1 = uphill, 2 = downhill. */
    public int escalator;
    private boolean beltRunning, beltRequested;
    private double beltDistance;
    private long beltTick;
    public boolean isEscalatorRunning() { return beltRunning; }
    public boolean isEscalatorRequested() { return beltRequested; }
    public boolean vehicleFrozen;
    public double beltDistance(double partial) {
        if(vehicleFrozen || world instanceof com.vandorlabs.vehicle.VehicleWorld || world!=null && !world.isRemote)return beltDistance;
        return beltDistance+(beltRunning?(world.getTotalWorldTime()-beltTick+partial)*beltDelta():0);
    }
    private double beltDelta() {
        return com.vandorlabs.ramp.EscalatorGeometry.rate(speed)*(endOffsetValue()>=0?1:-1)*(escalator==1?1:-1);
    }
    public void refreshEscalatorActivity() {
        if(vehicleFrozen || world instanceof com.vandorlabs.vehicle.VehicleWorld)return;
        if(world==null || world.isRemote || escalator==0 || !attached() || moving || error)return;
        boolean near=false;
        double radius=beltRunning?40:32;
        for(EntityPlayer player:world.playerEntities) {
            if(player.isDead || player.isSpectator())continue;
            for(BlockPos source:sources) {
                double along=source.getX()*facing.getFrontOffsetX()+source.getZ()*facing.getFrontOffsetZ()-minAlong;
                double y=source.getY()+high+endOffsetValue()*along/Math.max(1,length-1);
                double dx=player.posX-source.getX()-.5,dy=player.posY-y,dz=player.posZ-source.getZ()-.5;
                if(dx*dx+dy*dy+dz*dz<=radius*radius){near=true;break;}
            }
            if(near)break;
        }
        setBeltRunning(beltRequested && near);
    }
    private void setBeltRunning(boolean run) {
        if(run==beltRunning)return;
        // Commit only processed motion; elapsed unloaded time never transports riders.
        beltRunning=run;beltTick=world.getTotalWorldTime();lastStepTick=beltTick;
        status=run?"Escalator running":beltRequested?"Escalator sleeping (no nearby player)":"Escalator stopped";
        syncParts();sync();if(run)schedule();
    }
    public boolean matchTextures=true;
    // Keep the legacy magnitude/sign fields for old saves and integrations.
    public int endOffset() { return top?-drop:drop; }
    public int drop=3,segments=2,speed=1;
    public boolean top=true,activateOnPower=true,slow,elevator,extendSegments;
    public boolean moat;
    private boolean verticalMoatSources;
    private int moatGeometryRevision=2;
    public int travelAxis=RampGeometry.VERTICAL;
    public String status="Retracted";
    public boolean error;
    public int clientUpdates;
    private final List<BlockPos> sources=new ArrayList<>();
    private final Map<BlockPos,NBTTagCompound> sourceTiles=new LinkedHashMap<>();
    private final Set<BlockPos> cells=new HashSet<>();
    private IBlockState original=Blocks.AIR.getDefaultState();
    public boolean analogDeployment;
    private double targetPose=1;
    private boolean open,moving,changing,latched,signalKnown,needsLoadCheck,recoveryPending,legacy;
    private double startPose,low,high=1;
    private long startTick,lastStepTick;
    private int length=1,minAlong,duration=60;
    private EnumFacing facing=EnumFacing.NORTH;
    private EnumFacing configuredFacing;
    private UUID owner;
    private int redstoneChannel;
    private boolean channelSignal,lifecycleDetached;
    // Unregister synchronously notifies the removed channel member. Teardown must not start new movement.
    private boolean acceptsWorldCommand(){return world!=null&&!world.isRemote&&!isInvalid()&&!lifecycleDetached&&!com.vandorlabs.vehicle.VehicleTransfer.isMutating(world)&&world.getTileEntity(pos)==this;}
    private boolean waitingForCanopy, canopyCommand, canopyRetry, requestingDeploy;
    private final Set<BlockPos> canopyBlockers=new HashSet<>();
    public boolean isWaitingForCanopy(){return waitingForCanopy;}
    /** Called only by a changed reservation owner or the one-shot load check. */
    public void retryCanopyCommand(){if(waitingForCanopy && world!=null && !world.isRemote){canopyRetry=true;schedule();}}
    private void clearCanopyCommand(){
        boolean previous=waitingForCanopy;
        waitingForCanopy=false;canopyRetry=false;canopyBlockers.clear();
        RampCanopyCoordination.clearRampWait(this);
        if(previous)RampCanopyCoordination.changed(world,pos);
    }
    private boolean waitForCanopies(Set<BlockPos> blockers){
        waitingForCanopy=true;canopyCommand=requestingDeploy;canopyBlockers.clear();canopyBlockers.addAll(blockers);
        error=false;status="Waiting for canopy to open";
        RampCanopyCoordination.waitingRamp(this,canopyBlockers);sync();return false;
    }
    private TileEntityCanopy cooperativeCanopy(BlockPos cell){
        TileEntityCanopy part=TileEntityCanopy.at(world,cell);
        TileEntityCanopy canopy=part==null?null:part.owner();
        if(canopy==null || canopy.removing || !canopy.opening() || part.anchor==null || cell.equals(part.anchor))return null;
        boolean shared=!channels.intersect(canopy.getRedstoneChannels()).isEmpty();
        boolean expected=shared && (requestingDeploy ? activateOnPower==canopy.large.activateOnPower : activateOnPower!=canopy.large.activateOnPower);
        if(!canopy.targetOpen && !expected)return null;
        if(canopy.progress>=1)for(AxisAlignedBB box:canopy.boxes())if(box.intersects(new AxisAlignedBB(cell)))return null;
        return canopy;
    }


    @Override public TileEntity channelTile() { return this; }
    @Override public int getRedstoneChannel() { return redstoneChannel; }
    @Override public int localSignalLevel(int channel){return com.vandorlabs.redstone.LoadedRedstonePower.level(world,pos);}
    @Override public boolean hasLocalRedstoneSignal() {
        return com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world, pos);
    }
    @Override public void setChannelLevel(int level){
        boolean same=channelSignal==(level>0);setChannelSignal(level>0);
        if(same&&analogDeployment&&world!=null&&!world.isRemote)evaluateSignal(false);
    }
    @Override public void setChannelSignal(boolean powered) {
        if(!acceptsWorldCommand())return;
        if (channelSignal == powered) return;
        channelSignal = powered;
        if (world != null && !world.isRemote) evaluateSignal(false);
    }
    @Override public void setRedstoneChannel(int value) {setRedstoneChannels(ChannelList.of(Math.max(0,value)));}
    @Override public void setRedstoneChannels(ChannelList next) {
        if (channels.equals(next)) return;
        ChannelList old = channels;
        channels=next;redstoneChannel=next.first();
        markDirty();
        RedstoneChannels.channelChanged(this, old);
        if (world != null && !world.isRemote) evaluateSignal(true);
    }

    public boolean attached() { return !sources.isEmpty(); }
    @Override public boolean shouldRefresh(net.minecraft.world.World world,BlockPos pos,IBlockState before,IBlockState after) {
        // The on/off model flag must never replace the tile or discard its journal.
        return before.getBlock()!=after.getBlock();
    }
    /** Reserved geometry and recovery sources, including disconnected extended sections. */
    public java.util.Set<BlockPos> vehicleCells(){return java.util.Collections.unmodifiableSet(cells);}
    public java.util.List<BlockPos> vehicleSources(){return java.util.Collections.unmodifiableList(sources);}
    public int area() { return sources.size(); }
    public boolean isOpen() { return open; }
    public boolean activeForModel(){return escalator!=0?beltRequested:open;}
    public boolean isMoving() { return moving; }
    public int durationTicks() { return duration; }
    public EnumFacing rampDirection() {
        if (configuredFacing!=null) return configuredFacing;
        IBlockState state=world==null?null:world.getBlockState(pos);
        return state!=null && state.getBlock() instanceof BlockRampController
                ?state.getValue(BlockVandorDirectional.FACING):facing;
    }
    public void setPlacementDirection(EnumFacing direction){if(direction!=null&&direction.getAxis().isHorizontal()&&configuredFacing==null){configuredFacing=direction;markDirty();}}
    public void setOwner(EntityPlayer player) { owner=player.getUniqueID();WorldActionAuthorization.authorize(this,player); markDirty(); }
    public double pose(double partial) {
        return moving?ControllerPlatform.pose(startPose,targetPose,world.getTotalWorldTime()-startTick+partial,duration):startPose;
    }
    private BlockPos reserved(BlockPos source,int distance) { return source.up(top?-distance:distance); }
    public boolean owns(TileEntityControlledRamp part) {
        return cells.contains(part.getPos()) && !part.origins.isEmpty()
                && sources.containsAll(part.origins);
    }
    public boolean usable(EntityPlayer player) {
        return world.getTileEntity(pos)==this && com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos);
    }
    private boolean fail(String reason) { clearCanopyCommand();error=true; status=reason; sync(); return false; }
    private boolean hold(String reason) {
        if (moving) startPose=ControllerPlatform.pose(startPose,targetPose,lastStepTick-startTick,duration);
        moving=false; syncParts(); releaseExcept(occupied(startPose,startPose));
        return fail(reason);
    }
    private void sync() {
        markDirty();
        IBlockState s=world.getBlockState(pos);
        if (s.getBlock() instanceof BlockRampController && s.getValue(BlockRampController.ACTIVE)!=(escalator!=0?beltRequested:open)) {
            s=s.withProperty(BlockRampController.ACTIVE,escalator!=0?beltRequested:open);
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
        return configureOffsets(player,0,upper?-height:height,treadCount,powerOn,slower,lift,direction);
    }
    public boolean configureOffsets(EntityPlayer player,int start,int end,int treadCount,
            boolean powerOn,boolean slower,boolean lift,EnumFacing direction) {
        if (treadCount!=2 && treadCount!=8) return fail("Choose a valid legacy tread count");
        return configureTreads(player,start,end,16/treadCount,powerOn,slower,lift,direction);
    }
    public boolean configureTreads(EntityPlayer player,int start,int end,int pixels,
            boolean powerOn,boolean slower,boolean lift,EnumFacing direction) {
        return configureTreads(player,start,end,pixels,powerOn,slower,lift,direction,
                RampGeometry.VERTICAL,false);
    }
    public boolean configureTreads(EntityPlayer player,int start,int end,int pixels,
            boolean powerOn,boolean slower,boolean lift,EnumFacing direction,int travel,boolean extend) {
        return configureTreads(player,start,end,pixels,powerOn,slower,lift,direction,
                travel,extend,slower?2:1);
    }
    public boolean configureTreads(EntityPlayer player,int start,int end,int pixels,
            boolean powerOn,boolean slower,boolean lift,EnumFacing direction,int travel,
            boolean extend,int selectedSpeed) {
        return configureTreads(player,start,end,pixels,powerOn,slower,lift,direction,travel,extend,selectedSpeed,matchTextures);
    }
    public boolean configureTreads(EntityPlayer player,int start,int end,int pixels,
            boolean powerOn,boolean slower,boolean lift,EnumFacing direction,int travel,
            boolean extend,int selectedSpeed,boolean matchTextures) {
        return configureHalfOffsets(player,start*2,end*2,pixels,powerOn,slower,lift,
                direction,travel,extend,selectedSpeed,matchTextures);
    }
    public boolean configureHalfOffsets(EntityPlayer player,int startHalf,int endHalf,int pixels,
            boolean powerOn,boolean slower,boolean lift,EnumFacing direction,int travel,
            boolean extend,int selectedSpeed,boolean matchTextures) {
        return configureHalfOffsets(player,startHalf,endHalf,pixels,powerOn,slower,lift,
                direction,travel,extend,selectedSpeed,matchTextures,false);
    }
    public boolean configureHalfOffsets(EntityPlayer player,int startHalf,int endHalf,int pixels,
            boolean powerOn,boolean slower,boolean lift,EnumFacing direction,int travel,
            boolean extend,int selectedSpeed,boolean matchTextures,boolean connectedCopy) {
        return configureHalfOffsets(player,startHalf,endHalf,pixels,powerOn,slower,lift,direction,
                travel,extend,selectedSpeed,matchTextures,connectedCopy,interpolation);
    }
    public boolean configureHalfOffsets(EntityPlayer player,int startHalf,int endHalf,int pixels,
            boolean powerOn,boolean slower,boolean lift,EnumFacing direction,int travel,
            boolean extend,int selectedSpeed,boolean matchTextures,boolean connectedCopy,int interpolation) {
        return configureHalfOffsets(player,startHalf,endHalf,pixels,powerOn,slower,lift,direction,travel,
                extend,selectedSpeed,matchTextures,connectedCopy,interpolation,0);
    }
    public boolean configureHalfOffsets(EntityPlayer player,int startHalf,int endHalf,int pixels,
            boolean powerOn,boolean slower,boolean lift,EnumFacing direction,int travel,
            boolean extend,int selectedSpeed,boolean matchTextures,boolean connectedCopy,int interpolation,int escalator) {
        return configureHalfOffsets(player,startHalf,endHalf,pixels,powerOn,slower,lift,direction,travel,extend,selectedSpeed,matchTextures,connectedCopy,interpolation,escalator,moat);
    }
    public boolean configureHalfOffsets(EntityPlayer player,int startHalf,int endHalf,int pixels,
            boolean powerOn,boolean slower,boolean lift,EnumFacing direction,int travel,
            boolean extend,int selectedSpeed,boolean matchTextures,boolean connectedCopy,int interpolation,int escalator,boolean moat) {
        if(moat&&(lift||extend||escalator!=0||travel!=RampGeometry.VERTICAL))return fail("Moat mode uses vertical offsets without lift, fill or escalator travel");
        if(escalator<0 || escalator>2)return fail("Choose a valid escalator mode");
        if(escalator!=0 && (travel!=RampGeometry.VERTICAL || lift || extend))
            return fail("Escalators need vertical travel without lift or fill");
        if(interpolation<0 || interpolation>2) return fail("Choose a valid interpolation style");
        boolean copyAllowed = connectedCopy && player != null && world.isBlockLoaded(pos)
                && world.getTileEntity(pos)==this
                && player.getHeldItemMainhand().getItem()==com.vandorlabs.items.ModItems.DUPLIFIER
                && com.vandorlabs.items.DuplifierApplyOptions.connected(player.getHeldItemMainhand())
                && player.canPlayerEdit(pos,EnumFacing.UP,player.getHeldItemMainhand())
                && world.isBlockModifiable(player,pos);
        if (world.isRemote || !(copyAllowed || usable(player))) return false;
        if (direction==null || !direction.getAxis().isHorizontal()) return fail("Choose a horizontal ramp direction");
        if (travel<RampGeometry.VERTICAL || travel>RampGeometry.RIGHT) return fail("Choose a travel direction");
        if (selectedSpeed<0 || selectedSpeed>2) return fail("Choose a valid speed");
        if (Math.abs((long)startHalf)>ControllerPlatform.MAX_OFFSET_HALF_STEPS
                || Math.abs((long)endHalf)>ControllerPlatform.MAX_OFFSET_HALF_STEPS)
            return fail("Offsets must be -16 to 16 blocks");
        if (!ControllerPlatform.validTreadPixels(pixels)) return fail("Tread size must be 1, 2, 4, 8 or 16 pixels");
        // Reset using the old geometry and journal before installing new settings.
        if (attached()) {
            for (BlockPos source:sources) {
                if (!world.isBlockLoaded(source)) return hold("Reset waits for platform chunks");
                TileEntity te=world.getTileEntity(source);
                if (!world.isAirBlock(source) && !(te instanceof TileEntityControlledRamp
                        && ((TileEntityControlledRamp)te).belongsTo(pos)))
                    return hold("Reset blocked: clear the original platform position");
            }
            if (!carryRiders(pose(0),0,false,true)) return hold("Reset blocked: a rider is obstructed");
            if (!recover(false)) return false;
        }
        if(escalator!=0)startHalf=0;
        startHalfSteps=startHalf; endHalfSteps=endHalf;
        startOffset=startHalf/2; drop=(Math.abs(endHalf)+1)/2;
        treadPixels=pixels; segments=ControllerPlatform.treadCount(pixels); top=endHalf<0;
        activateOnPower=powerOn; slow=slower; elevator=lift;
        travelAxis=travel; extendSegments=extend; elevator=lift;
        speed=selectedSpeed; slow=speed==2;
        this.matchTextures=matchTextures;this.interpolation=interpolation;this.escalator=escalator;this.moat=moat;
        beltDistance=0;beltRunning=false;beltRequested=false;
        if(escalator!=0)com.vandorlabs.ramp.EscalatorActivity.register(this);else com.vandorlabs.ramp.EscalatorActivity.unregister(this);
        configuredFacing=direction;
        owner=player.getUniqueID();WorldActionAuthorization.authorize(this,player);
        sync();
        return evaluateSignal(true);
    }
    public void updatePower() {
        if(vehicleFrozen || world instanceof com.vandorlabs.vehicle.VehicleWorld)return;
        if(com.vandorlabs.vehicle.VehicleTransfer.isMutating(world))return;
        if (!world.isRemote && !changing) {
            RedstoneChannels.inputChanged(this);
            evaluateSignal(false);
        }
    }
    private boolean evaluateSignal(boolean force) {
        if(!acceptsWorldCommand())return false;
        if(analogDeployment&&escalator==0){
            int level=com.vandorlabs.redstone.LoadedRedstonePower.level(world,pos);
            for(int i=0;i<channels.size();i++){int channel=channels.get(i);if(channel>0)level=Math.max(level,RedstoneChannels.level(world,pos,channel));}
            double desiredPose=(activateOnPower?level:15-level)/15D;
            if(!force&&signalKnown&&Math.abs(targetPose-desiredPose)<1e-8)return !error;
            signalKnown=true;latched=desiredPose>0;return requestPose(desiredPose);
        }
        boolean desired=(hasLocalRedstoneSignal() || channelSignal)==activateOnPower;
        if (!force && signalKnown && desired==latched) return !error;
        signalKnown=true; latched=desired;
        if(escalator!=0) {
            beltRequested=desired;
            if(!attached() || moving) { boolean ok=request(true); if(!ok)return false; }
            String problem=validateParts();
            if(problem!=null){setBeltRunning(false);return fail(problem);}
            if(!moving && !reserveStep(1,1))return false;
            error=false;
            refreshEscalatorActivity();
            if(!beltRunning){status=desired?"Escalator sleeping (no nearby player)":"Escalator stopped";sync();}
            return true;
        }
        return request(desired);
    }

    /** Public for the block event adapter and live-world regression checks. */
    public boolean request(boolean deploy) {return requestPose(deploy?1:0);}
    public void setAnalogDeployment(boolean value){if(analogDeployment!=value){analogDeployment=value;signalKnown=false;evaluateSignal(true);sync();}}
    private boolean requestPose(double requestedPose) {
        boolean deploy=requestedPose>0;
        if(!acceptsWorldCommand())return false;
        if(vehicleFrozen || world instanceof com.vandorlabs.vehicle.VehicleWorld)return false;
        if (world.isRemote || changing) return false;
        if(escalator!=0){deploy=true;requestedPose=1;}
        clearCanopyCommand();requestingDeploy=deploy;
        if (recoveryPending && !recover(false)) return false;
        if (!attached()) {
            if (!deploy && startHalfSteps==0) { targetPose=requestedPose;open=false; moving=false; error=false; status="Retracted"; sync(); return true; }
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
        targetPose=requestedPose;open=deploy; moving=Math.abs(startPose-targetPose)>1e-8;
        duration=ControllerPlatform.duration(length,
                (int)Math.ceil(Math.abs(endOffsetValue()-startOffsetValue())),speed);
        error=false; status=moving?(deploy?"Deploying":"Retracting"):(deploy?"Deployed":"Retracted");
        syncParts(); sync();
        if (!deploy && !moving && startHalfSteps==0) return recover(false);
        if (moving && !reserveStep(startPose,pose(1))) {
            moving=false; syncParts(); sync(); return false;
        }
        if (moving) schedule();
        return true;
    }

    /** Moat sources occupy the upright plane directly adjacent to the controller. */
    private Set<ControllerPlatform.Cell> discoverPlatform(ControllerPlatform.Cell seed,
            RampGeometry.Direction direction,java.util.function.Predicate<ControllerPlatform.Cell> matches) {
        return moat?ControllerPlatform.discoverVertical(seed,direction,matches)
                :ControllerPlatform.discover(seed,direction,matches);
    }
    private int sourceBaseY() {
        return sources.stream().mapToInt(BlockPos::getY).min().orElse(pos.getY());
    }
    public boolean usesVerticalMoatSources(){return moat&&verticalMoatSources;}
    public boolean usesNativeMoatGeometry(){return usesVerticalMoatSources()&&moatGeometryRevision>=1;}
    public int moatGeometryRevision(){return moatGeometryRevision;}

    private boolean capture() {
        // Old saves retain their full journal until restored; only new deployments are capped.
        drop=Math.min(drop,ControllerPlatform.MAX_DROP);
        EnumFacing scanFacing=BlockRampController.scanFacing(world.getBlockState(pos));
        facing=rampDirection();
        BlockPos seed=pos.offset(scanFacing);
        if (!world.isBlockLoaded(seed)) return fail("Load all platform chunks first");
        IBlockState material=world.getBlockState(seed);
        boolean programmable = material.getBlock() == ModBlocks.PROGRAMMABLE_BLOCK
                || material.getBlock() == ModBlocks.PROGRAMMABLE_SLAB || material.getBlock()==ModBlocks.PROGRAMMABLE_STAIRS;
        boolean ieSlab = com.vandorlabs.compat.RampMaterials.isIESlab(world, seed);
        if ((material.getBlock().hasTileEntity(material) && !programmable && !ieSlab)
                || material.getBlockHardness(world,seed)<0
                || (!programmable && !ieSlab && !(material.getBlock() instanceof BlockSlab)
                        && !material.isFullCube() && !com.vandorlabs.compat.RampMaterials.isScaffolding(material) && !com.vandorlabs.compat.RampMaterials.isFullGlass(material) && !RampStairs.supported(material)))
            return fail("Front block must be stairs, a slab, full glass, Programmable Block, or an ordinary solid block");
        boolean[] loaded={true};
        Set<ControllerPlatform.Cell> platform=discoverPlatform(
                new ControllerPlatform.Cell(seed.getX(),seed.getY(),seed.getZ()),rampDirection(facing),c->{
            BlockPos p=new BlockPos(c.x,c.y,c.z);
            if (!world.isBlockLoaded(p)) { loaded[0]=false; return false; }
            return com.vandorlabs.compat.RampMaterials.matches(world, seed, p, matchTextures);
        });
        if (!loaded[0]) return fail("Load all platform chunks first");
        if (platform.isEmpty()) return fail("No platform blocks found");
        if(escalator!=0) {
            int x0=Integer.MAX_VALUE,x1=Integer.MIN_VALUE,z0=x0,z1=x1;
            for(ControllerPlatform.Cell c:platform){x0=Math.min(x0,c.x);x1=Math.max(x1,c.x);z0=Math.min(z0,c.z);z1=Math.max(z1,c.z);}
            int span=(facing.getAxis()==EnumFacing.Axis.X?x1-x0:z1-z0)+1;
            if(platform.size()!=(x1-x0+1)*(z1-z0+1) || span*16<=treadPixels)
                return fail("Escalators need a rectangular platform with at least two treads");
        }
        EntityPlayer actor=null;if(world instanceof net.minecraft.world.WorldServer)try{actor=WorldActionAuthorization.permissionActor(this);}catch(IllegalArgumentException denied){return fail(denied.getMessage());}
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        List<BlockPos> selected=new ArrayList<>();
        for (ControllerPlatform.Cell c:platform) {
            BlockPos source=new BlockPos(c.x,c.y,c.z);
            selected.add(source);
            int along=c.x*facing.getFrontOffsetX()+c.z*facing.getFrontOffsetZ();
            min=Math.min(min,along); max=Math.max(max,along);
        }
        Map<BlockPos,NBTTagCompound> capturedTiles=new LinkedHashMap<>();
        if (programmable || ieSlab) for (BlockPos source:selected) {
            TileEntity sourceTile=world.getTileEntity(source);
            if (sourceTile == null || programmable && !(sourceTile instanceof TileEntityAnimatedScreenSelector))
                return fail("Programmable source tile is missing");
            capturedTiles.put(source,sourceTile.writeToNBT(new NBTTagCompound()));
        }
        if(RampStairs.supported(material))for(BlockPos source:selected)capturedTiles.put(source,RampStairs.capture(world,source));
        AxisAlignedBB bounds=material.getBoundingBox(world,seed);
        if (bounds.minX!=0 || bounds.maxX!=1 || bounds.minZ!=0 || bounds.maxZ!=1)
            return fail("Only full-width slabs and cubes are supported");
        // Journal the whole transaction before replacing its first source.
        sources.addAll(selected);sourceTiles.clear();sourceTiles.putAll(capturedTiles); verticalMoatSources=moat;moatGeometryRevision=2; original=material; low=bounds.minY; high=bounds.maxY;
        length=moat?selected.stream().mapToInt(BlockPos::getY).max().getAsInt()-sourceBaseY()+1:max-min+1; minAlong=min; duration=ControllerPlatform.duration(length,
                (int)Math.ceil(Math.abs(endOffsetValue()-startOffsetValue())),speed);
        Set<BlockPos> canopyObstacles=new HashSet<>();
        for (BlockPos p:occupied(0,1)) {
            if (!VerticalBounds.legacy(world.getHeight()).contains(p.getY())) { sources.clear();sourceTiles.clear(); return fail("Travel exceeds world height"); }
            if (!world.isBlockLoaded(p)) { sources.clear();sourceTiles.clear(); return fail("Load all platform chunks first"); }
            if (actor!=null && (!actor.canPlayerEdit(p,EnumFacing.UP,actor.getHeldItemMainhand())
                    || !world.isBlockModifiable(actor,p))) { sources.clear();sourceTiles.clear(); return fail("No permission to move this platform"); }
            if (!selected.contains(p) && !world.isAirBlock(p)) {
                TileEntityCanopy canopy=cooperativeCanopy(p);
                if(canopy==null){sources.clear();sourceTiles.clear();return fail("Movement path is obstructed");}
                canopyObstacles.add(canopy.getPos());
            }
        }
        // Preserve full-column clearance, using the source's actual thickness.
        // A top slab moved down 2.5 blocks touches the ground in the third
        // cell below without entering it; rounding the offset alone rejects it.
        if (!moat && travelAxis==RampGeometry.VERTICAL) for (BlockPos source:selected)
            for (int y=RampGeometry.firstOccupiedY(source.getY(),low,
                        Math.min(0,startOffsetValue()),Math.min(0,endOffsetValue()));
                 y<=RampGeometry.lastOccupiedY(source.getY(),high,
                        Math.max(0,startOffsetValue()),Math.max(0,endOffsetValue()));y++) {
                BlockPos p=new BlockPos(source.getX(),y,source.getZ());
                if (!VerticalBounds.legacy(world.getHeight()).contains(y)) { sources.clear();sourceTiles.clear(); return fail("Travel exceeds world height"); }
                if (!world.isBlockLoaded(p)) { sources.clear();sourceTiles.clear(); return fail("Load all platform chunks first"); }
                if (actor!=null && (!actor.canPlayerEdit(p,EnumFacing.UP,actor.getHeldItemMainhand())
                        || !world.isBlockModifiable(actor,p))) { sources.clear();sourceTiles.clear(); return fail("No permission to move this platform"); }
                if (!selected.contains(p) && !world.isAirBlock(p)) { sources.clear();sourceTiles.clear(); return fail("Movement path is obstructed"); }
            }
        if(!canopyObstacles.isEmpty()){sources.clear();sourceTiles.clear();return waitForCanopies(canopyObstacles);}
        startPose=0; moving=false; open=false; startTick=world.getTotalWorldTime();
        changing=true; markDirty();
        try {
            for (BlockPos source:sources) install(source);
            if (!reserveStep(0,0) || !carryRiders(0,0,true,false)) {
                recover(false);
                return fail("Starting position blocked");
            }
            releaseExcept(occupied(0,0));
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
        configurePart(te,p);
    }
    private void configurePart(TileEntityControlledRamp te,BlockPos p){
        te.verticalMoatSources=verticalMoatSources;te.moatGeometryRevision=moatGeometryRevision;te.controller=pos; te.source=original; te.sourceY=sourceBaseY();
        te.row=p.getX()*facing.getFrontOffsetX()+p.getZ()*facing.getFrontOffsetZ()-minAlong;
        te.treadPixels=treadPixels; te.startOffset=startOffset;
        te.setHalfOffsets(startHalfSteps,endHalfSteps);
        te.interpolation=interpolation; te.length=length; te.drop=drop; te.segments=segments; te.top=top; te.elevator=elevator;
        te.low=low; te.high=high; te.travelAxis=travelAxis; te.extendSegments=extendSegments;
        te.speed=speed;te.escalator=escalator;te.moat=moat;te.beltDistance=beltDistance;te.beltTick=beltTick;te.beltRunning=beltRunning;
        te.origins.clear();
        te.sourceTileTags.clear();
        for (BlockPos source:sources) if (escalator!=0?(facing.getAxis()==EnumFacing.Axis.X?source.getZ()==p.getZ():source.getX()==p.getX()):intersects(source,p,0,1)) {
            te.origins.add(source);
            NBTTagCompound saved=sourceTiles.get(source);
            if (saved!=null) te.sourceTileTags.put(source,saved.copy());
        }
        te.move(open,targetPose,startPose,startTick,duration,moving);
    }

    /** Build an endpoint in an isolated VehicleWorld; no placed blocks are written. */
    public java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> vehicleEndpoint(boolean deploy){return vehicleEndpoint(deploy?1D:0D);}
    public java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> vehicleEndpoint(double deployment){
        if(!Double.isFinite(deployment)||deployment<0||deployment>1)throw new IllegalArgumentException("Invalid carried ramp deployment");
        boolean deploy=deployment>0;
        if(!(world instanceof com.vandorlabs.vehicle.VehicleWorld))throw new IllegalStateException("Vehicle endpoint requires an isolated world");
        if(escalator!=0)throw new IllegalArgumentException("Escalator geometry remains frozen in vehicle mode");
        if(usesNativeMoatGeometry())moatGeometryRevision=2;
        if(sources.isEmpty()){
            facing=rampDirection();BlockPos seed=pos.offset(BlockRampController.scanFacing(world.getBlockState(pos)));
            IBlockState material=world.getBlockState(seed);
            boolean programmable=material.getBlock()==ModBlocks.PROGRAMMABLE_BLOCK||material.getBlock()==ModBlocks.PROGRAMMABLE_SLAB||material.getBlock()==ModBlocks.PROGRAMMABLE_STAIRS;
            boolean ieSlab=com.vandorlabs.compat.RampMaterials.isIESlab(world,seed);
            if(material.getBlock()==Blocks.AIR || material.getBlockHardness(world,seed)<0 || material.getBlock().hasTileEntity(material)&&!programmable&&!ieSlab
                    || !programmable&&!ieSlab&&!(material.getBlock() instanceof BlockSlab)&&!material.isFullCube()&&!com.vandorlabs.compat.RampMaterials.isScaffolding(material)&&!com.vandorlabs.compat.RampMaterials.isFullGlass(material)&&!RampStairs.supported(material))throw new IllegalArgumentException("No supported ramp platform in front of controller");
            Set<ControllerPlatform.Cell> platform=discoverPlatform(new ControllerPlatform.Cell(seed.getX(),seed.getY(),seed.getZ()),rampDirection(facing),c->com.vandorlabs.compat.RampMaterials.matches(world,seed,new BlockPos(c.x,c.y,c.z),matchTextures));
            if(platform.isEmpty())throw new IllegalArgumentException("No ramp platform found");
            int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
            for(ControllerPlatform.Cell c:platform){BlockPos p=new BlockPos(c.x,c.y,c.z);sources.add(p);int along=c.x*facing.getFrontOffsetX()+c.z*facing.getFrontOffsetZ();min=Math.min(min,along);max=Math.max(max,along);
                TileEntity tile=world.getTileEntity(p);if(RampStairs.supported(material))sourceTiles.put(p,RampStairs.capture(world,p));else if(tile!=null)sourceTiles.put(p,tile.writeToNBT(new NBTTagCompound()));}
            AxisAlignedBB box=material.getBoundingBox(world,seed);
            if(box.minX!=0||box.maxX!=1||box.minZ!=0||box.maxZ!=1)throw new IllegalArgumentException("Ramp platform must use full-width slabs or cubes");
            verticalMoatSources=moat;moatGeometryRevision=2;original=material;low=box.minY;high=box.maxY;length=moat?sources.stream().mapToInt(BlockPos::getY).max().getAsInt()-sourceBaseY()+1:max-min+1;minAlong=min;
        }
        open=deploy;moving=false;targetPose=deployment;startPose=targetPose;startTick=world.getTotalWorldTime();
        latched=deploy;signalKnown=true;error=false;recoveryPending=false;status=deployment>0&&deployment<1?"Deployment: "+Math.round(deployment*100)+"%":deploy?"Deployed":"Retracted";vehicleFrozen=true;
        cells.clear();cells.addAll(occupied(startPose,startPose));
        java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> result=new java.util.ArrayList<>();
        for(BlockPos p:cells){TileEntityControlledRamp part=new TileEntityControlledRamp();part.setWorld(world);part.setPos(p);configurePart(part,p);part.vehicleFrozen=true;
            result.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(p,ModBlocks.CONTROLLED_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing),part.writeToNBT(new NBTTagCompound())));}
        return result;
    }

    /** Full hinge arc for an atomic carried endpoint change, in vehicle-local coordinates. */
    public java.util.List<AxisAlignedBB> vehicleMoatSweep(){
        java.util.List<AxisAlignedBB> result=new java.util.ArrayList<>();
        if(moat)for(BlockPos source:sources)for(RampGeometry.Box box:usesNativeMoatGeometry()?moatSweeps(source,0,1):java.util.Collections.singletonList(treadBox(source,0,0,0,1)))result.add(new AxisAlignedBB(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ));
        return result;
    }

    /** Only geometry touched by this animation step is examined; nothing runs while idle. */
    protected IBlockState capturedSourceState(BlockPos source){return RampStairs.state(original,sourceTiles.get(source));}
    private List<RampGeometry.Box> moatSweeps(BlockPos source,double from,double to){
        return RampStairs.supported(original)?RampStairs.moatSweeps(world,capturedSourceState(source),rampDirection(facing),sourceBaseY(),length,treadPixels,startOffsetValue(),endOffsetValue(),from,to,speed==0,interpolation,source)
                :com.vandorlabs.ramp.MoatGeometry.sweeps(rampDirection(facing),sourceBaseY(),length,treadPixels,low,high,startOffsetValue(),endOffsetValue(),from,to,speed==0,interpolation,source.getX(),source.getY(),source.getZ());
    }
    private Set<BlockPos> occupied(double from,double to) {
        Set<BlockPos> result=new HashSet<>();
        for (BlockPos source:sources) {
            int row=source.getX()*facing.getFrontOffsetX()+source.getZ()*facing.getFrontOffsetZ()-minAlong;
            for (int step=0;step<(moat||elevator?1:ControllerPlatform.treadCount(treadPixels));step++) {
                for(RampGeometry.Box box:usesNativeMoatGeometry()?moatSweeps(source,from,to):java.util.Collections.singletonList(treadBox(source,row,step,from,to)))
                for (int x=(int)Math.floor(box.minX+1e-8);x<(int)Math.ceil(box.maxX-1e-8);x++)
                    for (int y=(int)Math.floor(box.minY+1e-8);y<(int)Math.ceil(box.maxY-1e-8);y++)
                        for (int z=(int)Math.floor(box.minZ+1e-8);z<(int)Math.ceil(box.maxZ-1e-8);z++)
                            result.add(new BlockPos(x,y,z));
            }
        }
        // The solid anchor remains support and occludes any backward-leaning
        // material inside its own cell. Never replace it with a reservation.
        if(usesVerticalMoatSources())result.remove(pos);
        return result;
    }

    private RampGeometry.Box treadBox(BlockPos source,int row,int step,double from,double to) {
        if(moat&&verticalMoatSources)return com.vandorlabs.ramp.RigidMoatGeometry.swept(rampDirection(facing),minAlong,sourceBaseY(),length,treadPixels,low,high,startOffsetValue(),endOffsetValue(),from,to,speed==0,source.getX(),source.getY(),source.getZ());
        if(moat && !verticalMoatSources)return com.vandorlabs.ramp.LegacyMoatGeometry.swept(rampDirection(facing),minAlong,sourceBaseY(),low,high,startOffsetValue(),endOffsetValue(),from,to,speed==0,source.getX(),source.getY(),source.getZ());

        if(escalator!=0 && from>=1 && to>=1) {
            double pitch=treadPixels/16D;
            RampGeometry.Box fp=RampGeometry.footprintPixels(rampDirection(facing),step,treadPixels,low,high);
            double a=com.vandorlabs.ramp.EscalatorGeometry.height(row+step*pitch,length,treadPixels,endOffsetValue(),interpolation);
            double b=com.vandorlabs.ramp.EscalatorGeometry.height(row+(step+1)*pitch,length,treadPixels,endOffsetValue(),interpolation);
            double extra=row==length-1 && step==ControllerPlatform.treadCount(treadPixels)-1?0:pitch;
            return new RampGeometry.Box(source.getX()+fp.minX+(facing.getFrontOffsetX()<0?-extra:0),source.getY()+low+Math.min(a,b),
                    source.getZ()+fp.minZ+(facing.getFrontOffsetZ()<0?-extra:0),
                    source.getX()+fp.maxX+(facing.getFrontOffsetX()>0?extra:0),source.getY()+high+Math.max(a,b),
                    source.getZ()+fp.maxZ+(facing.getFrontOffsetZ()>0?extra:0));
        }
        return RampGeometry.movingTread(rampDirection(facing),source.getX(),source.getY(),source.getZ(),
                low,high,row,length,startOffsetValue(),endOffsetValue(),treadPixels,step,from,to,elevator,
                travelAxis,extendSegments,speed==0,interpolation);
    }

    private boolean intersects(BlockPos source,BlockPos target,double from,double to) {
        int row=source.getX()*facing.getFrontOffsetX()+source.getZ()*facing.getFrontOffsetZ()-minAlong;
        for (int step=0;step<(moat||elevator?1:ControllerPlatform.treadCount(treadPixels));step++)
            for(RampGeometry.Box box:usesNativeMoatGeometry()?moatSweeps(source,from,to):java.util.Collections.singletonList(treadBox(source,row,step,from,to))) if (RampGeometry.clip(box,target.getX(),target.getY(),target.getZ())!=null)
                return true;
        return false;
    }

    private static RampGeometry.Direction rampDirection(EnumFacing facing) {
        return RampGeometry.Direction.valueOf(
                facing.getName().toUpperCase(java.util.Locale.ROOT));
    }

    private boolean reserveStep(double from,double to) {
        Set<BlockPos> needed=occupied(from,to);
        Set<BlockPos> canopyObstacles=new HashSet<>();
        EntityPlayer actor=null;if(world instanceof net.minecraft.world.WorldServer)try{actor=WorldActionAuthorization.permissionActor(this);}catch(IllegalArgumentException denied){return fail(denied.getMessage());}
        for (BlockPos p:needed) {
            if (!world.isBlockLoaded(p)) return fail("Movement waits for platform chunks");
            if (cells.contains(p)) {
                TileEntity te=world.getTileEntity(p);
                if (!(te instanceof TileEntityControlledRamp) || !((TileEntityControlledRamp)te).belongsTo(pos))
                    return fail("Platform was changed; remove controller to recover");
                continue;
            }
            if (!world.isAirBlock(p)) {
                TileEntityCanopy canopy=cooperativeCanopy(p);
                if(canopy==null)return fail("Movement path is obstructed");
                canopyObstacles.add(canopy.getPos());
            }
            if (actor!=null && (!actor.canPlayerEdit(p,EnumFacing.UP,actor.getHeldItemMainhand())
                    || !world.isBlockModifiable(actor,p))) return fail("No permission to move this platform");
        }
        if(!canopyObstacles.isEmpty())return waitForCanopies(canopyObstacles);
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
        boolean released=false;
        changing=true;
        try {
            for (BlockPos p:new HashSet<>(cells)) if (!keep.contains(p)) {
                if (!world.isBlockLoaded(p)) continue;
                TileEntity te=world.getTileEntity(p);
                if (te instanceof TileEntityControlledRamp && ((TileEntityControlledRamp)te).belongsTo(pos))
                    world.setBlockState(p,Blocks.AIR.getDefaultState(),3);
                cells.remove(p);released=true;
            }
        } finally { changing=false; markDirty(); }
        if(released)RampCanopyCoordination.changed(world,pos);
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
                    { TileEntityControlledRamp part=(TileEntityControlledRamp)te;
                    part.escalator=escalator;part.beltDistance=beltDistance;part.beltTick=beltTick;part.beltRunning=beltRunning;
                    part.move(open,targetPose,startPose,startTick,duration,moving); }
        }
    }
    private void schedule() { if(!vehicleFrozen && !(world instanceof com.vandorlabs.vehicle.VehicleWorld))world.scheduleUpdate(pos,world.getBlockState(pos).getBlock(),1); }
    @Override public void onLoad() {com.vandorlabs.tiles.WorldActionAuthorization.resumed(this);
        lifecycleDetached=false;super.onLoad();
        if (!world.isRemote && !vehicleFrozen && !(world instanceof com.vandorlabs.vehicle.VehicleWorld)) {
            needsLoadCheck=true;
            DeferredTileLoad.schedule(this, () -> {
                RedstoneChannels.register(this);
                if(escalator!=0)com.vandorlabs.ramp.EscalatorActivity.register(this);else com.vandorlabs.ramp.EscalatorActivity.unregister(this);
                if(waitingForCanopy){RampCanopyCoordination.waitingRamp(this,canopyBlockers);canopyRetry=true;}
                RampCanopyCoordination.changed(world,pos);
                schedule();
            });
        }
    }
    @Override public void invalidate() { WorldActionAuthorization.revoke(this); lifecycleDetached=true;com.vandorlabs.ramp.ControllerRecovery.controllerChanged(world,pos);RampCanopyCoordination.clearRampWait(this);RampCanopyCoordination.changed(world,pos); RedstoneChannels.unregister(this); com.vandorlabs.ramp.EscalatorActivity.unregister(this); super.invalidate(); }
    @Override public void onChunkUnload() { WorldActionAuthorization.unloaded(this);
        lifecycleDetached=true;RampCanopyCoordination.clearRampWait(this);
        if(world!=null && !world.isRemote && beltRunning){beltRunning=false;beltTick=world.getTotalWorldTime();syncParts();}
        RedstoneChannels.unregister(this);com.vandorlabs.ramp.EscalatorActivity.unregister(this);super.onChunkUnload();
    }
    public void resumeAfterLoad() {
        if (!world.isRemote && (moving || beltRunning || escalator!=0 || recoveryPending || legacy || waitingForCanopy || usesNativeMoatGeometry()&&moatGeometryRevision==1)) schedule();
    }
    /** A scheduled tick is animation work or a one-shot load recovery, never idle polling. */
    public void scheduledTick() {
        if(vehicleFrozen || world instanceof com.vandorlabs.vehicle.VehicleWorld || com.vandorlabs.vehicle.VehicleTransfer.isMutating(world))return;
        if (!acceptsWorldCommand() || changing) return;
        if (recoveryPending) {
            if (recover(false)) evaluateSignal(true);
            return;
        }
        if (needsLoadCheck) {
            needsLoadCheck=false;
            if (legacy) { legacy=false; if (!recover(false)) return; }
            if (recoveryPending && !recover(false)) return;
            if(usesNativeMoatGeometry()&&moatGeometryRevision==1&&attached()){
                for(BlockPos cell:cells)if(!world.isBlockLoaded(cell)){needsLoadCheck=true;return;}
                double current=pose(0);startPose=current;startTick=world.getTotalWorldTime();lastStepTick=startTick;
                if(!reserveStep(current,current)){recover(false);fail("Recovered earlier Moat sections; activate again after clearing the path");return;}
                moatGeometryRevision=2;releaseExcept(occupied(current,current));
                for(BlockPos cell:cells)configurePart((TileEntityControlledRamp)world.getTileEntity(cell),cell);
                sync();
            }
            if(escalator!=0){signalKnown=false;syncParts();}
            updatePower();
            // Old settled Moat saves may retain a journal at the native inactive endpoint.
            // Release it once so players can edit the source selection before the next edge.
            if(moat && attached() && !moving && !open && startHalfSteps==0)recover(false);
            // One-shot migration also releases old full-column locks on stationary saves.
            if (attached() && !moving) releaseExcept(occupied(startPose,startPose));
        }
        if(canopyRetry && waitingForCanopy){
            canopyRetry=false;
            boolean desired=(hasLocalRedstoneSignal()||channelSignal)==activateOnPower;
            if(desired!=canopyCommand){clearCanopyCommand();request(desired);}
            else {
                boolean loaded=true;for(BlockPos blocker:canopyBlockers)if(!world.isBlockLoaded(blocker))loaded=false;
                if(loaded)request(canopyCommand);
            }
        }
        if(escalator!=0 && !moving && attached()) {
            if(beltRunning)stepEscalator();
            return;
        }
        if (!moving || !attached()) return;
        if (world.getTotalWorldTime()==lastStepTick) { schedule(); return; }
        requestingDeploy=open;
        double previous=ControllerPlatform.pose(startPose,targetPose,lastStepTick-startTick,duration);
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
        if (Math.abs(current-targetPose)<1e-8) {
            startPose=targetPose; moving=false; syncParts();
            if (!open && startHalfSteps==0) recover(false);
            else {
                // Completion is a single validation boundary, not a per-tick world scan.
                String problem=validateParts();
                if (problem!=null) { recover(false); fail(problem); }
                else {
                    if(escalator!=0 && !reserveStep(1,1)){sync();return;}
                    error=false; status=open?"Deployed":"Retracted"; sync();
                    if(escalator!=0)refreshEscalatorActivity();
                }
            }
        } else schedule();
    }
    private boolean resetEscalatorRiders() {
        java.util.List<AxisAlignedBB> surfaces=beltSurfaces(beltDistance);
        AxisAlignedBB bounds=surfaces.get(0);for(AxisAlignedBB b:surfaces)bounds=bounds.union(b);
        Map<Entity,Double> targets=new LinkedHashMap<>();
        for(Entity entity:world.getEntitiesWithinAABB(Entity.class,bounds.grow(0,.6,0))) {
            if(entity.isDead || entity.noClip || entity.isRiding() || entity.motionY>.1
                    || entity instanceof EntityPlayer && ((EntityPlayer)entity).capabilities.isFlying)continue;
            AxisAlignedBB body=entity.getEntityBoundingBox();
            if(Math.abs(body.minY-support(surfaces,body))>(entity instanceof EntityPlayer?.5:.15))continue;
            double target=RampStairs.supported(original)?support(stairOriginalSurfaces(),body):sourceBaseY()+high;
            if(!Double.isFinite(target))continue;
            double delta=target-body.minY,allowed=delta;
            for(AxisAlignedBB obstacle:com.vandorlabs.blocks.BlockControlledRamp.riderObstacles(world,pos,entity,body.expand(0,delta,0).grow(1e-7)))
                allowed=obstacle.calculateYOffset(body,allowed);
            if(Math.abs(allowed-delta)>1e-6)return false;
            targets.put(entity,target);
        }
        for(Map.Entry<Entity,Double> entry:targets.entrySet()) {
            Entity entity=entry.getKey();double before=entity.getEntityBoundingBox().minY;
            com.vandorlabs.motion.PlatformRiders.move(entity,0,entry.getValue()-before,0);
        }
        return true;
    }

    private java.util.List<AxisAlignedBB> beltSurfaces(double distance) {
        java.util.List<AxisAlignedBB> result=new ArrayList<>();
        for(BlockPos source:sources) {
            int row=source.getX()*facing.getFrontOffsetX()+source.getZ()*facing.getFrontOffsetZ()-minAlong;
            List<RampGeometry.Box> geometry=RampStairs.supported(original)
                    ?RampStairs.escalator(world,RampStairs.state(original,sourceTiles.get(source)),rampDirection(facing),source,row,length,treadPixels,endOffsetValue(),distance,interpolation)
                    :com.vandorlabs.ramp.EscalatorGeometry.treads(rampDirection(facing),source.getX(),source.getY(),source.getZ(),low,high,row,length,treadPixels,endOffsetValue(),distance,interpolation);
            for(RampGeometry.Box b:geometry)
                result.add(new AxisAlignedBB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));
        }
        return result;
    }
    private static double support(java.util.List<AxisAlignedBB> surfaces,AxisAlignedBB body) {
        double top=Double.NEGATIVE_INFINITY;
        for(AxisAlignedBB b:surfaces)
            if(body.maxX>b.minX+1e-7 && body.minX<b.maxX-1e-7 && body.maxZ>b.minZ+1e-7 && body.minZ<b.maxZ-1e-7)
                top=Math.max(top,b.maxY);
        return top;
    }
    private final Map<java.util.UUID,net.minecraft.util.math.Vec3d> beltRiderPositions=new java.util.HashMap<>();
    /** Let a walking passenger step onto a fixed lower landing before belt correction. */
    private double lowerLandingStep(Entity entity,AxisAlignedBB body,AxisAlignedBB platform,double delta){
        if(!(entity instanceof EntityPlayer) || Math.abs(endOffsetValue())<1e-7)return 0;
        net.minecraft.util.math.Vec3d previous=beltRiderPositions.get(entity.getUniqueID());
        double walkX=previous==null?entity.motionX:entity.posX-previous.x,walkZ=previous==null?entity.motionZ:entity.posZ-previous.z;
        if((walkX*facing.getFrontOffsetX()+walkZ*facing.getFrontOffsetZ())*delta>=-1e-6)return 0;
        int sign=endOffsetValue()>=0?-1:1;double x=facing.getFrontOffsetX()*sign,z=facing.getFrontOffsetZ()*sign;
        double centerX=(body.minX+body.maxX)*.5,centerZ=(body.minZ+body.maxZ)*.5;
        double edge=x<0?centerX-platform.minX:x>0?platform.maxX-centerX:z<0?centerZ-platform.minZ:platform.maxZ-centerZ;
        if(edge>.65)return 0;
        AxisAlignedBB toward=body.offset(x*.2,0,z*.2);double top=body.minY;
        for(AxisAlignedBB box:com.vandorlabs.blocks.BlockControlledRamp.riderObstacles(world,pos,entity,body.expand(x*.4,entity.stepHeight,z*.4)))
            if(toward.maxX>box.minX && toward.minX<box.maxX && toward.maxZ>box.minZ && toward.minZ<box.maxZ && box.maxY>top && box.maxY-body.minY<=entity.stepHeight+1e-7)top=box.maxY;
        double rise=top-body.minY;if(rise<=0)return 0;
        for(AxisAlignedBB obstacle:com.vandorlabs.blocks.BlockControlledRamp.riderObstacles(world,pos,entity,body.offset(0,rise,0)))if(obstacle.intersects(body.offset(0,rise,0)))return 0;
        return rise;
    }
    private void stepEscalator() {
        long now=world.getTotalWorldTime();
        if(now==lastStepTick){schedule();return;}
        // Do not catch up unloaded or stalled world time in one large displacement.
        for(BlockPos cell:cells)if(!world.isBlockLoaded(cell)){
            setBeltRunning(false);fail("Escalator waits for platform chunks");return;
        }
        double delta=beltDelta(),next=beltDistance+delta;
        BlockPos first=sources.get(0);
        AxisAlignedBB bounds=new AxisAlignedBB(first);
        for(BlockPos source:sources)bounds=bounds.union(new AxisAlignedBB(source));
        bounds=new AxisAlignedBB(bounds.minX,first.getY()+low+Math.min(0,endOffsetValue()),bounds.minZ,
                bounds.maxX,first.getY()+high+Math.max(0,endOffsetValue())+.6,bounds.maxZ);
        java.util.List<Entity> riders=new ArrayList<>(world.getEntitiesWithinAABB(Entity.class,bounds));
        riders.removeIf(entity->entity.isDead || entity.noClip && !(entity instanceof net.minecraft.entity.item.EntityItem) || entity.isRiding() || entity.motionY>.1
                && !(entity instanceof net.minecraft.entity.item.EntityItem && beltRiderPositions.containsKey(entity.getUniqueID()))
                || entity instanceof EntityPlayer && (((EntityPlayer)entity).capabilities.isFlying || ((EntityPlayer)entity).isSpectator()));
        // Empty escalators advance only their clock; no tread geometry or obstacle scans.
        java.util.List<AxisAlignedBB> before=riders.isEmpty()?java.util.Collections.emptyList():beltSurfaces(beltDistance);
        java.util.List<AxisAlignedBB> after=riders.isEmpty()?java.util.Collections.emptyList():beltSurfaces(next);
        Map<Entity,double[]> moves=new LinkedHashMap<>();
        Set<java.util.UUID> present=new HashSet<>();for(Entity rider:riders)present.add(rider.getUniqueID());beltRiderPositions.keySet().retainAll(present);
        for(Entity entity:riders) {
            AxisAlignedBB body=entity.getEntityBoundingBox();
            if(entity instanceof net.minecraft.entity.item.EntityItem) {
                // Vanilla item ticks run after scheduled block ticks. Their
                // push-out impulse can dislodge an item from the tread between
                // belt updates, especially when a fine riser crosses its box.
                // Restore the last supported belt pose before advancing it;
                // never acquire airborne/thrown items through this path.
                net.minecraft.util.math.Vec3d previous=beltRiderPositions.get(entity.getUniqueID());
                if(previous!=null && entity.getPositionVector().squareDistanceTo(previous)<.5625) {
                    AxisAlignedBB anchored=body.offset(previous.x-entity.posX,previous.y-entity.posY,previous.z-entity.posZ);
                    boolean clear=true;
                    for(AxisAlignedBB obstacle:com.vandorlabs.blocks.BlockControlledRamp.riderObstacles(world,pos,entity,anchored))if(obstacle.intersects(anchored)){clear=false;break;}
                    if(clear && Math.abs(anchored.minY-support(before,anchored))<1e-6) {
                        entity.setPosition(previous.x,previous.y,previous.z);
                        body=anchored;entity.motionX=entity.motionY=entity.motionZ=0;
                    }
                }
            }
            double top=support(before,body);
            if(Math.abs(body.minY-top)>(entity instanceof EntityPlayer?.5:.15)) {
                beltRiderPositions.remove(entity.getUniqueID());continue;
            }
            double dx=facing.getFrontOffsetX()*delta,dz=facing.getFrontOffsetZ()*delta;
            double nextTop=support(after,body.offset(dx,0,dz));
            // Once beyond the destination, detach rather than following a wrapped tread.
            double dy=Double.isInfinite(nextTop)?0:nextTop-body.minY;
            double landingStep=lowerLandingStep(entity,body,bounds,delta);if(landingStep>0)dy=Math.max(dy,landingStep);
            AxisAlignedBB moved=body;
            double ax=dx,ay=dy,az=dz;
            java.util.List<AxisAlignedBB> obstacles=com.vandorlabs.blocks.BlockControlledRamp.riderObstacles(
                    world,pos,entity,body.expand(dx,dy,dz).grow(1e-7));
            for(AxisAlignedBB obstacle:obstacles)ay=obstacle.calculateYOffset(moved,ay);
            // At an entrance/exit a rider can straddle a stationary landing and
            // a lower moving tread. Keep the landing's support rather than
            // treating its floor as an obstruction to the whole escalator.
            if(dy<0 && ay>dy)dy=ay;
            moved=moved.offset(0,ay,0);
            for(AxisAlignedBB obstacle:obstacles)ax=obstacle.calculateXOffset(moved,ax);
            moved=moved.offset(ax,0,0);
            for(AxisAlignedBB obstacle:obstacles)az=obstacle.calculateZOffset(moved,az);
            if(Math.abs(ay-dy)>1e-6) {
                setBeltRunning(false);fail("Escalator stopped: a rider is obstructed");return;
            }
            // A side wall or the lip of a landing can hold one passenger back.
            // Use the collision-clipped motion without disabling the belt.
            moves.put(entity,new double[]{ax,ay,az});
        }
        for(Map.Entry<Entity,double[]> entry:moves.entrySet()) {
            Entity entity=entry.getKey();double[] d=entry.getValue();
            double feet=entity.getEntityBoundingBox().minY;
            com.vandorlabs.motion.PlatformRiders.move(entity,d[0],d[1],d[2]);
            beltRiderPositions.put(entity.getUniqueID(),entity.getPositionVector());
            if(entity instanceof net.minecraft.entity.item.EntityItem) {
                // Item push-out impulses and its slow normal tracking interval
                // otherwise fight the belt, especially on fine moving treads.
                entity.noClip=false;entity.motionX=entity.motionZ=0;
                if(world instanceof net.minecraft.world.WorldServer)((net.minecraft.world.WorldServer)world).getEntityTracker().sendToTracking(entity,
                        new net.minecraft.network.play.server.SPacketEntityTeleport(entity));
                entity.velocityChanged=true;
            }
        }
        beltDistance=com.vandorlabs.ramp.EscalatorGeometry.phase(next,length*16);
        beltTick=now;lastStepTick=now;markDirty();schedule();
    }

    private boolean carryRiders(double previous,double current) {
        return carryRiders(previous,current,false,false);
    }
    private boolean carryRiders(double previous,double current,boolean fromOriginal,boolean toOriginal) {
        if(moat)return carryMoat(previous,current,fromOriginal,toOriginal);
        if(escalator!=0 && !moving && toOriginal)return resetEscalatorRiders();
        if (extendSegments) return true;
        if(RampStairs.supported(original))return carryStairRiders(previous,current,fromOriginal,toOriginal);
        if (travelAxis!=RampGeometry.VERTICAL) return carrySideways(previous,current);
        Map<Entity,Double> targets=new LinkedHashMap<>();
        for (BlockPos source:sources) {
            int row=source.getX()*facing.getFrontOffsetX()+source.getZ()*facing.getFrontOffsetZ()-minAlong;
            double minY=source.getY()+high+Math.min(0,Math.min(startOffsetValue(),endOffsetValue()))-0.2,maxY=source.getY()+high+Math.max(0,Math.max(startOffsetValue(),endOffsetValue()))+0.2;
            for (Entity entity:world.getEntitiesWithinAABB(Entity.class,
                    new AxisAlignedBB(source.getX(),minY,source.getZ(),source.getX()+1,maxY,source.getZ()+1))) {
                if (entity.isDead || entity.noClip || entity.isRiding() || entity.motionY>0.1
                        || (entity instanceof EntityPlayer && ((EntityPlayer)entity).capabilities.isFlying)) continue;
                AxisAlignedBB body=entity.getEntityBoundingBox();
                // A rider straddling treads is supported by the highest overlapping surface.
                for (int step=0;step<(moat||elevator?1:ControllerPlatform.treadCount(treadPixels));step++) {
                RampGeometry.Box local=RampGeometry.footprintPixels(rampDirection(facing),step,
                        elevator?16:treadPixels,minY,maxY);
                AxisAlignedBB tread=new AxisAlignedBB(source.getX()+local.minX,local.minY,
                        source.getZ()+local.minZ,source.getX()+local.maxX,local.maxY,
                        source.getZ()+local.maxZ);
                if (!body.shrink(1e-7).intersects(tread)) continue;
                double before=source.getY()+high+(fromOriginal?0:ControllerPlatform.offsetPixels(row,step,length,treadPixels,startOffsetValue(),endOffsetValue(),previous,elevator,speed==0,interpolation));
                double after=source.getY()+high+(toOriginal?0:ControllerPlatform.offsetPixels(row,step,length,treadPixels,startOffsetValue(),endOffsetValue(),current,elevator,speed==0,interpolation));
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
                com.vandorlabs.motion.PlatformRiders.move(entity,0,entry.getValue()-before,0);
        }
        return true;
    }

    private List<AxisAlignedBB> stairOriginalSurfaces(){
        List<AxisAlignedBB> result=new ArrayList<>();
        for(BlockPos source:sources)for(RampGeometry.Box b:RampStairs.boxes(world,RampStairs.state(original,sourceTiles.get(source))))
            result.add(new AxisAlignedBB(source.getX()+b.minX,source.getY()+b.minY,source.getZ()+b.minZ,source.getX()+b.maxX,source.getY()+b.maxY,source.getZ()+b.maxZ));
        return result;
    }
    private boolean carryStairRiders(double previous,double current,boolean fromOriginal,boolean toOriginal){
        Map<Entity,net.minecraft.util.math.Vec3d> targets=new LinkedHashMap<>();
        for(BlockPos source:sources){
            int row=source.getX()*facing.getFrontOffsetX()+source.getZ()*facing.getFrontOffsetZ()-minAlong;
            IBlockState state=RampStairs.state(original,sourceTiles.get(source));
            List<RampGeometry.Box> before=RampStairs.treads(world,state,rampDirection(facing),source,row,length,fromOriginal?0:startOffsetValue(),fromOriginal?0:endOffsetValue(),treadPixels,previous,elevator,travelAxis,false,speed==0,interpolation);
            List<RampGeometry.Box> after=RampStairs.treads(world,state,rampDirection(facing),source,row,length,toOriginal?0:startOffsetValue(),toOriginal?0:endOffsetValue(),treadPixels,current,elevator,travelAxis,false,speed==0,interpolation);
            for(int i=0;i<before.size();i++){
                RampGeometry.Box a=before.get(i),b=after.get(i);AxisAlignedBB tread=new AxisAlignedBB(a.minX,a.minY,a.minZ,a.maxX,a.maxY,a.maxZ);
                for(Entity entity:world.getEntitiesWithinAABB(Entity.class,tread.grow(0,.2,0))){
                    if(entity.isDead||entity.noClip||entity.isRiding()||entity.motionY>.1||entity instanceof EntityPlayer&&((EntityPlayer)entity).capabilities.isFlying)continue;
                    AxisAlignedBB body=entity.getEntityBoundingBox();
                    if(body.maxX<=a.minX+1e-7||body.minX>=a.maxX-1e-7||body.maxZ<=a.minZ+1e-7||body.minZ>=a.maxZ-1e-7||Math.abs(body.minY-a.maxY)>.15)continue;
                    net.minecraft.util.math.Vec3d delta=new net.minecraft.util.math.Vec3d(b.minX-a.minX,b.maxY-body.minY,b.minZ-a.minZ);
                    net.minecraft.util.math.Vec3d existing=targets.get(entity);if(existing==null||delta.y>existing.y)targets.put(entity,delta);
                }
            }
        }
        for(Map.Entry<Entity,net.minecraft.util.math.Vec3d> entry:targets.entrySet()){
            AxisAlignedBB body=entry.getKey().getEntityBoundingBox();net.minecraft.util.math.Vec3d d=entry.getValue();double dx=d.x,dy=d.y,dz=d.z;
            List<AxisAlignedBB> obstacles=com.vandorlabs.blocks.BlockControlledRamp.riderObstacles(world,pos,entry.getKey(),body.expand(dx,dy,dz).grow(1e-7));
            for(AxisAlignedBB obstacle:obstacles)dy=obstacle.calculateYOffset(body,dy);body=body.offset(0,dy,0);
            for(AxisAlignedBB obstacle:obstacles)dx=obstacle.calculateXOffset(body,dx);body=body.offset(dx,0,0);
            for(AxisAlignedBB obstacle:obstacles)dz=obstacle.calculateZOffset(body,dz);
            if(Math.abs(dx-d.x)>1e-6||Math.abs(dy-d.y)>1e-6||Math.abs(dz-d.z)>1e-6)return false;
        }
        for(Map.Entry<Entity,net.minecraft.util.math.Vec3d> entry:targets.entrySet()){
            Entity e=entry.getKey();net.minecraft.util.math.Vec3d d=entry.getValue();double feet=e.getEntityBoundingBox().minY;
            com.vandorlabs.motion.PlatformRiders.move(e,d.x,d.y,d.z);
        }
        return true;
    }

    private List<RampGeometry.Box> moatBoxes(BlockPos source,double pose) {
        return verticalMoatSources?com.vandorlabs.ramp.RigidMoatGeometry.boxes(rampDirection(facing),minAlong,sourceBaseY(),length,treadPixels,low,high,startOffsetValue(),endOffsetValue(),pose,speed==0,source.getX(),source.getY(),source.getZ())
                :com.vandorlabs.ramp.LegacyMoatGeometry.boxes(rampDirection(facing),minAlong,sourceBaseY(),low,high,startOffsetValue(),endOffsetValue(),pose,speed==0,source.getX(),source.getY(),source.getZ());
    }
    private boolean carryTreadMoat(double previous,double current,boolean fromOriginal,boolean toOriginal){
        List<AxisAlignedBB> before=new ArrayList<>(),after=new ArrayList<>(),swept=new ArrayList<>();
        RampGeometry.Direction direction=rampDirection(facing);
        for(BlockPos origin:sources){
            List<RampGeometry.Box> shapes=RampStairs.supported(original)?RampStairs.boxes(world,RampStairs.state(original,sourceTiles.get(origin))):java.util.Collections.singletonList(new RampGeometry.Box(0,low,0,1,high,1));
            for(RampGeometry.Box shape:shapes)for(RampGeometry.Box piece:com.vandorlabs.ramp.MoatGeometry.pieces(direction,shape,treadPixels)){
                int step=com.vandorlabs.ramp.MoatGeometry.step(direction,piece,treadPixels);RampGeometry.Box nativeBox=com.vandorlabs.ramp.MoatGeometry.world(piece,origin.getX(),origin.getY(),origin.getZ());
                RampGeometry.Box storedBox=com.vandorlabs.ramp.MoatGeometry.storedSection(direction,nativeBox,com.vandorlabs.ramp.MoatGeometry.world(shape,origin.getX(),origin.getY(),origin.getZ()));
                RampGeometry.Box a=fromOriginal?storedBox:com.vandorlabs.ramp.MoatGeometry.transform(direction,sourceBaseY(),length,treadPixels,startOffsetValue(),endOffsetValue(),previous,speed==0,interpolation,origin.getY(),step,nativeBox,com.vandorlabs.ramp.MoatGeometry.world(shape,origin.getX(),origin.getY(),origin.getZ()));
                RampGeometry.Box b=toOriginal?storedBox:com.vandorlabs.ramp.MoatGeometry.transform(direction,sourceBaseY(),length,treadPixels,startOffsetValue(),endOffsetValue(),current,speed==0,interpolation,origin.getY(),step,nativeBox,com.vandorlabs.ramp.MoatGeometry.world(shape,origin.getX(),origin.getY(),origin.getZ()));
                AxisAlignedBB first=new AxisAlignedBB(a.minX,a.minY,a.minZ,a.maxX,a.maxY,a.maxZ),last=new AxisAlignedBB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);before.add(first);after.add(last);
                RampGeometry.Box sweep=com.vandorlabs.ramp.MoatGeometry.sweep(direction,sourceBaseY(),length,treadPixels,startOffsetValue(),endOffsetValue(),previous,current,speed==0,interpolation,origin.getY(),step,nativeBox,com.vandorlabs.ramp.MoatGeometry.world(shape,origin.getX(),origin.getY(),origin.getZ()));
                swept.add(fromOriginal||toOriginal?first.union(last):new AxisAlignedBB(sweep.minX,sweep.minY,sweep.minZ,sweep.maxX,sweep.maxY,sweep.maxZ));
            }
        }
        if(before.isEmpty())return true;AxisAlignedBB area=before.get(0);for(AxisAlignedBB box:swept)area=area.union(box);Map<Entity,net.minecraft.util.math.Vec3d> targets=new LinkedHashMap<>();
        for(Entity entity:world.getEntitiesWithinAABB(Entity.class,area.grow(0,.6,0))){
            if(entity.isDead||!entity.canBeCollidedWith()||entity.noClip||entity.isRiding()||entity instanceof EntityPlayer&&((EntityPlayer)entity).capabilities.isFlying)continue;
            AxisAlignedBB body=entity.getEntityBoundingBox();int support=-1;double distance=Double.POSITIVE_INFINITY;
            for(int i=0;i<before.size();i++){AxisAlignedBB box=before.get(i);double dy=Math.abs(body.minY-box.maxY);if(body.maxX>box.minX+1e-7&&body.minX<box.maxX-1e-7&&body.maxZ>box.minZ+1e-7&&body.minZ<box.maxZ-1e-7&&dy<=(entity instanceof EntityPlayer?.5:.15)&&dy<distance){support=i;distance=dy;}}
            if(support<0){for(AxisAlignedBB box:swept)if(box.grow(-1e-6).intersects(body))return false;continue;}
            AxisAlignedBB a=before.get(support),b=after.get(support);net.minecraft.util.math.Vec3d delta=new net.minecraft.util.math.Vec3d(b.minX-a.minX,b.maxY-a.maxY,b.minZ-a.minZ);
            for(AxisAlignedBB obstacle:com.vandorlabs.blocks.BlockControlledRamp.riderObstacles(world,pos,entity,body.expand(delta.x,delta.y,delta.z).grow(1e-7))){double y=obstacle.calculateYOffset(body,delta.y);AxisAlignedBB moved=body.offset(0,y,0);double x=obstacle.calculateXOffset(moved,delta.x);moved=moved.offset(x,0,0);double z=obstacle.calculateZOffset(moved,delta.z);if(Math.abs(x-delta.x)>1e-6||Math.abs(y-delta.y)>1e-6||Math.abs(z-delta.z)>1e-6)return false;}
            AxisAlignedBB destination=body.offset(delta.x,delta.y,delta.z).grow(-1e-5);for(AxisAlignedBB box:after)if(box.intersects(destination))return false;targets.put(entity,delta);
        }
        for(Map.Entry<Entity,net.minecraft.util.math.Vec3d> entry:targets.entrySet()){Entity e=entry.getKey();net.minecraft.util.math.Vec3d d=entry.getValue();com.vandorlabs.motion.PlatformRiders.move(e,d.x,d.y,d.z);}
        return true;
    }
    private boolean carryMoat(double previous,double current,boolean fromOriginal,boolean toOriginal){
        if(usesNativeMoatGeometry())return carryTreadMoat(previous,current,fromOriginal,toOriginal);
        Map<Entity,net.minecraft.util.math.Vec3d> targets=new LinkedHashMap<>();List<AxisAlignedBB> before=new ArrayList<>(),after=new ArrayList<>(),sweep=new ArrayList<>();
        for(BlockPos source:sources){
            if(RampStairs.supported(original)){
                IBlockState state=RampStairs.state(original,sourceTiles.get(source));
                List<RampGeometry.Box> nativeShape=RampStairs.boxes(world,state);
                for(int endpoint=0;endpoint<2;endpoint++){
                    boolean flat=endpoint==0?fromOriginal:toOriginal;
                    List<RampGeometry.Box> shape=flat?nativeShape:!verticalMoatSources?RampStairs.legacyMoat(world,state,rampDirection(facing),minAlong,sourceBaseY(),high,startOffsetValue(),endOffsetValue(),endpoint==0?previous:current,speed==0,source):RampStairs.rigidMoat(world,state,rampDirection(facing),minAlong,sourceBaseY(),length,treadPixels,startOffsetValue(),endOffsetValue(),endpoint==0?previous:current,speed==0,source);
                    for(RampGeometry.Box b:shape)(endpoint==0?before:after).add(new AxisAlignedBB(b.minX+(flat?source.getX():0),b.minY+(flat?source.getY():0),b.minZ+(flat?source.getZ():0),b.maxX+(flat?source.getX():0),b.maxY+(flat?source.getY():0),b.maxZ+(flat?source.getZ():0)));
                }
                RampGeometry.Box b=treadBox(source,0,0,previous,current);sweep.add(new AxisAlignedBB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));continue;
            }
            if(fromOriginal)before.add(new AxisAlignedBB(source.getX(),source.getY()+low,source.getZ(),source.getX()+1,source.getY()+high,source.getZ()+1));
            else for(RampGeometry.Box b:moatBoxes(source,previous))before.add(new AxisAlignedBB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));
            if(toOriginal)after.add(new AxisAlignedBB(source.getX(),source.getY()+low,source.getZ(),source.getX()+1,source.getY()+high,source.getZ()+1));else for(RampGeometry.Box next:moatBoxes(source,current))after.add(new AxisAlignedBB(next.minX,next.minY,next.minZ,next.maxX,next.maxY,next.maxZ));
            RampGeometry.Box b=treadBox(source,0,0,previous,current);sweep.add(new AxisAlignedBB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));
        }
        if(before.isEmpty())return true;AxisAlignedBB area=before.get(0);for(AxisAlignedBB b:before)area=area.union(b);for(AxisAlignedBB b:sweep)area=area.union(b);
        for(Entity entity:world.getEntitiesWithinAABB(Entity.class,area.grow(0,.6,0))){
            if(entity.isDead||entity.noClip||entity.isRiding()||entity instanceof EntityPlayer&&((EntityPlayer)entity).capabilities.isFlying)continue;
            AxisAlignedBB body=entity.getEntityBoundingBox();boolean rider=false;for(AxisAlignedBB b:before)if(body.maxX>b.minX+1e-7&&body.minX<b.maxX-1e-7&&body.maxZ>b.minZ+1e-7&&body.minZ<b.maxZ-1e-7&&Math.abs(body.minY-b.maxY)<=(entity instanceof EntityPlayer?.5:.15)){rider=true;break;}
            if(!rider){for(AxisAlignedBB b:sweep)if(b.grow(-1e-6).intersects(body))return false;continue;}
            double[] source=fromOriginal?new double[]{entity.posX,body.minY,entity.posZ}:verticalMoatSources?com.vandorlabs.ramp.RigidMoatGeometry.inverse(rampDirection(facing),minAlong,sourceBaseY(),length,startOffsetValue(),endOffsetValue(),previous,speed==0,entity.posX,body.minY,entity.posZ):com.vandorlabs.ramp.LegacyMoatGeometry.inverse(rampDirection(facing),minAlong,sourceBaseY(),high,startOffsetValue(),endOffsetValue(),previous,speed==0,entity.posX,body.minY,entity.posZ);
            double[] next=toOriginal?source:verticalMoatSources?com.vandorlabs.ramp.RigidMoatGeometry.vertex(rampDirection(facing),minAlong,sourceBaseY(),length,startOffsetValue(),endOffsetValue(),current,speed==0,source[0],source[1],source[2]):com.vandorlabs.ramp.LegacyMoatGeometry.vertex(rampDirection(facing),minAlong,sourceBaseY(),high,startOffsetValue(),endOffsetValue(),current,speed==0,source[0],source[1],source[2]);
            net.minecraft.util.math.Vec3d delta=new net.minecraft.util.math.Vec3d(next[0]-entity.posX,next[1]-body.minY,next[2]-entity.posZ);
            for(AxisAlignedBB obstacle:com.vandorlabs.blocks.BlockControlledRamp.riderObstacles(world,pos,entity,body.expand(delta.x,delta.y,delta.z).grow(1e-7))){
                double y=obstacle.calculateYOffset(body,delta.y);AxisAlignedBB moved=body.offset(0,y,0);double x=obstacle.calculateXOffset(moved,delta.x);moved=moved.offset(x,0,0);double z=obstacle.calculateZOffset(moved,delta.z);if(Math.abs(x-delta.x)>1e-6||Math.abs(y-delta.y)>1e-6||Math.abs(z-delta.z)>1e-6)return false;
            }
            AxisAlignedBB destination=body.offset(delta.x,delta.y,delta.z).grow(-1e-5);for(AxisAlignedBB candidateBox:after)if(candidateBox.intersects(destination))return false;
            targets.put(entity,delta);
        }
        for(Map.Entry<Entity,net.minecraft.util.math.Vec3d> entry:targets.entrySet()){Entity e=entry.getKey();net.minecraft.util.math.Vec3d d=entry.getValue();com.vandorlabs.motion.PlatformRiders.move(e,d.x,d.y,d.z);}
        return true;
    }
    private boolean carrySideways(double previous,double current) {
        Map<Entity,Double> targets=new LinkedHashMap<>();
        EnumFacing side=travelAxis==RampGeometry.LEFT?facing.rotateYCCW():facing.rotateY();
        for (BlockPos source:sources) {
            int row=source.getX()*facing.getFrontOffsetX()+source.getZ()*facing.getFrontOffsetZ()-minAlong;
            for (int step=0;step<(moat||elevator?1:ControllerPlatform.treadCount(treadPixels));step++) {
                double before=ControllerPlatform.offsetPixels(row,step,length,treadPixels,startOffsetValue(),endOffsetValue(),previous,elevator,speed==0,interpolation);
                double after=ControllerPlatform.offsetPixels(row,step,length,treadPixels,startOffsetValue(),endOffsetValue(),current,elevator,speed==0,interpolation);
                RampGeometry.Box box=treadBox(source,row,step,previous,previous);
                AxisAlignedBB tread=new AxisAlignedBB(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ);
                for (Entity entity:world.getEntitiesWithinAABB(Entity.class,tread.grow(0,.2,0))) {
                    if (entity.isDead || entity.noClip || entity.isRiding()
                            || (entity instanceof EntityPlayer && ((EntityPlayer)entity).capabilities.isFlying)) continue;
                    AxisAlignedBB body=entity.getEntityBoundingBox();
                    if (Math.abs(body.minY-box.maxY)>.25 || !body.intersects(tread.grow(0,.25,0))) continue;
                    double delta=after-before;
                    if (Math.abs(delta)>1e-9) targets.put(entity,delta);
                }
            }
        }
        for (Map.Entry<Entity,Double> entry:targets.entrySet()) {
            AxisAlignedBB body=entry.getKey().getEntityBoundingBox();
            double dx=side.getFrontOffsetX()*entry.getValue(),dz=side.getFrontOffsetZ()*entry.getValue();
            for (AxisAlignedBB obstacle:com.vandorlabs.blocks.BlockControlledRamp.riderObstacles(
                    world,pos,entry.getKey(),body.expand(dx,0,dz).grow(1e-7))) {
                if (dx!=0) dx=obstacle.calculateXOffset(body,dx);
                if (dz!=0) dz=obstacle.calculateZOffset(body,dz);
            }
            if (Math.abs(dx-side.getFrontOffsetX()*entry.getValue())>1e-6
                    || Math.abs(dz-side.getFrontOffsetZ()*entry.getValue())>1e-6) return false;
        }
        for (Map.Entry<Entity,Double> entry:targets.entrySet()) {
            Entity entity=entry.getKey();
            double x=entity.posX+side.getFrontOffsetX()*entry.getValue();
            double z=entity.posZ+side.getFrontOffsetZ()*entry.getValue();
            com.vandorlabs.motion.PlatformRiders.move(entity,x-entity.posX,0,z-entity.posZ);
        }
        return true;
    }

    /** Restore originals and erase only cells owned by this controller.
     * Unexpected foreign blocks are preserved; displaced originals are refunded once. */
    public boolean recover(boolean forceLoad) {
        clearCanopyCommand();
        if (world.isRemote) return false;
        for (BlockPos cell:cells) {
            if (forceLoad) world.getChunkFromBlockCoords(cell);
            else if (!world.isBlockLoaded(cell)) { recoveryPending=true; return fail("Recovery waits for platform chunks"); }
        }
        for (BlockPos source:sources) {
            if (forceLoad) world.getChunkFromBlockCoords(source);
            else if (!world.isBlockLoaded(source)) { recoveryPending=true; return fail("Recovery waits for platform chunks"); }
        }
        changing=true; recoveryPending=true; moving=false;beltRunning=false;
        List<BlockPos> changed=new ArrayList<>();
        try {
            for (BlockPos p:new HashSet<>(cells)) {
                TileEntity te=world.getTileEntity(p);
                boolean owned=te instanceof TileEntityControlledRamp && ((TileEntityControlledRamp)te).belongsTo(pos);
                boolean unfinished=world.getBlockState(p).getBlock()==ModBlocks.CONTROLLED_RAMP
                        && te instanceof TileEntityControlledRamp
                        && ((TileEntityControlledRamp)te).controller.equals(BlockPos.ORIGIN)
                        && !pos.equals(BlockPos.ORIGIN);
                if (owned || unfinished) {
                    world.setBlockState(p,Blocks.AIR.getDefaultState(),2);
                    changed.add(p);
                }
            }
            for (BlockPos source:sources) {
                IBlockState saved=RampStairs.state(original,sourceTiles.get(source));
                TileEntity sourceTile=world.getTileEntity(source);
                if (saved.getBlock()==Blocks.AIR && sourceTile instanceof TileEntityControlledRamp
                        && ((TileEntityControlledRamp)sourceTile).belongsTo(pos))
                    saved=((TileEntityControlledRamp)sourceTile).source;
                if (!world.getBlockState(source).equals(saved)) {
                    if (world.isAirBlock(source)) {
                        world.setBlockState(source,saved,2);
                        TileEntityControlledRamp.restoreSourceTile(world,source,sourceTiles.get(source));
                        changed.add(source);
                    }
                    else if (saved.getBlock()!=Blocks.AIR)
                        Block.spawnAsEntity(world,pos.up(),new ItemStack(saved.getBlock(),1,saved.getBlock().damageDropped(saved)));
                }
            }
            sources.clear(); cells.clear(); sourceTiles.clear();beltRiderPositions.clear();
            recoveryPending=false; open=false; startPose=0;
            error=false; status="Retracted"; sync();
            for (BlockPos p:changed) world.notifyNeighborsOfStateChange(p,world.getBlockState(p).getBlock(),false);
            RampCanopyCoordination.changed(world,pos);
            return true;
        } finally { changing=false; markDirty(); }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        tag.setBoolean("RampCanopyWaiting",waitingForCanopy);tag.setBoolean("RampCanopyCommand",canopyCommand);
        NBTTagList blockers=new NBTTagList();for(BlockPos blocker:canopyBlockers)blockers.appendTag(writePosition(blocker));tag.setTag("RampCanopyBlockers",blockers);
        tag.setLong(com.vandorlabs.ramp.RampCopyData.ORIGIN,pos.toLong());
        if(vehicleFrozen)tag.setBoolean("VehicleRampFrozen",true);
        if(analogDeployment){tag.setBoolean("RampAnalogDeployment",true);tag.setDouble("RampTargetPose",targetPose);}else {tag.removeTag("RampAnalogDeployment");tag.removeTag("RampTargetPose");}
        super.writeToNBT(tag);tag.setInteger("MoatGeometryRevision",moatGeometryRevision);tag.setBoolean("MoatVerticalSources",verticalMoatSources);tag.setBoolean("MoatMode",moat);tag.setInteger("RampInterpolation",interpolation);
        tag.setInteger("EscalatorMode",escalator);tag.setDouble("EscalatorDistance",beltDistance);
        tag.setBoolean("EscalatorRequested",beltRequested);
        tag.setBoolean("EscalatorRunning",beltRunning);tag.setLong("EscalatorTick",beltTick);ChannelData.write(tag,channels);
        new RampControllerData(SaveSchema.Ramp.CONTROLLER_VERSION,drop,segments,status,
                top,activateOnPower,slow,elevator,error,open,moving,startPose,startTick,
                lastStepTick,duration,length,minAlong,facing.getHorizontalIndex(),
                configuredFacing!=null,configuredFacing==null?0:configuredFacing.getHorizontalIndex(),
                low,high,latched,signalKnown,recoveryPending,redstoneChannel,channelSignal,startOffset,endOffset(),treadPixels,
                travelAxis,extendSegments,speed,matchTextures)
                .write(new NbtPrimitiveData(tag));
        tag.setInteger(SaveSchema.Ramp.START_OFFSET_HALF_STEPS,startHalfSteps);
        tag.setInteger(SaveSchema.Ramp.END_OFFSET_HALF_STEPS,endHalfSteps);
        tag.setTag(SaveSchema.Ramp.ORIGINAL,NBTUtil.writeBlockState(new NBTTagCompound(),original));
        tag.setString(SaveSchema.Ramp.ORIGINAL_STATE,LegacyBlockStates.encode(original));
        if (owner!=null) {
            tag.setUniqueId(SaveSchema.Ramp.OWNER,owner);
            tag.setString(SaveSchema.Ramp.OWNER_ID,owner.toString());
        }
        NBTTagList list=new NBTTagList();
        for (BlockPos p:sources) list.appendTag(writePosition(p));
        tag.setTag(SaveSchema.Ramp.SOURCES,list);
        NBTTagList tileList=new NBTTagList();
        for (Map.Entry<BlockPos,NBTTagCompound> entry:sourceTiles.entrySet()) {
            NBTTagCompound saved=writePosition(entry.getKey());
            saved.setTag("Tile",entry.getValue().copy());
            tileList.appendTag(saved);
        }
        tag.setTag("RampSourceTiles",tileList);
        NBTTagList reservations=new NBTTagList();
        for (BlockPos p:cells) reservations.appendTag(writePosition(p));
        tag.setTag(SaveSchema.Ramp.CELLS,reservations);
        return com.vandorlabs.ramp.RampCopyData.canonicalizeSets(tag);
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        tag=com.vandorlabs.ramp.RampCopyData.relocated(tag);
        ChannelList previousChannels=channels;
        int oldChannel=redstoneChannel;
        WorldActionAuthorization.reading(this);super.readFromNBT(tag);
        RampCanopyCoordination.clearRampWait(this);waitingForCanopy=tag.getBoolean("RampCanopyWaiting");canopyCommand=tag.getBoolean("RampCanopyCommand");requestingDeploy=canopyCommand;canopyRetry=false;
        canopyBlockers.clear();NBTTagList blockers=tag.getTagList("RampCanopyBlockers",10);for(int i=0;i<Math.min(2176,blockers.tagCount());i++)canopyBlockers.add(readPosition(blockers.getCompoundTagAt(i)));
        moatGeometryRevision=Math.max(0,Math.min(2,tag.getInteger("MoatGeometryRevision")));verticalMoatSources=tag.getBoolean("MoatVerticalSources");vehicleFrozen=tag.getBoolean("VehicleRampFrozen");moat=tag.getBoolean("MoatMode");
        escalator=Math.max(0,Math.min(2,tag.getInteger("EscalatorMode")));
        beltDistance=tag.getDouble("EscalatorDistance");if(!Double.isFinite(beltDistance))beltDistance=0;
        beltRequested=tag.getBoolean("EscalatorRequested");
        // Atomic transfer rollback restores a live world snapshot, including its
        // running clock. Ordinary chunk loads still reevaluate nearby players.
        beltRunning=world!=null && (world.isRemote || com.vandorlabs.vehicle.VehicleTransfer.isMutating(world))
                && !vehicleFrozen && tag.getBoolean("EscalatorRunning");
        beltTick=tag.getLong("EscalatorTick");
        interpolation=Math.max(0,Math.min(2,tag.getInteger("RampInterpolation")));
        RampControllerData data=RampControllerData.read(new NbtPrimitiveData(tag));
        legacy=data.savedVersion<2; drop=data.drop; segments=data.segments;
        top=data.top; activateOnPower=data.activateOnPower; slow=data.slow;
        elevator=data.elevator; error=data.error; status=data.status;
        open=data.open; moving=data.moving; startPose=data.startPose;
        startTick=data.startTick; lastStepTick=data.lastStepTick; duration=data.duration;
        length=data.length; minAlong=data.minAlong; facing=EnumFacing.getHorizontal(data.facing);
        configuredFacing=data.hasDirection?EnumFacing.getHorizontal(data.direction):null;
        analogDeployment=tag.getBoolean("RampAnalogDeployment");targetPose=tag.hasKey("RampTargetPose",6)&&Double.isFinite(tag.getDouble("RampTargetPose"))?Math.max(0,Math.min(1,tag.getDouble("RampTargetPose"))):(data.open?1:0);
        low=data.low; high=data.high; latched=data.latched; signalKnown=data.signalKnown;
        recoveryPending=data.recoveryPending; redstoneChannel=data.redstoneChannel;
        channels=ChannelData.read(tag,redstoneChannel);redstoneChannel=channels.first();
        channelSignal=data.channelSignal;
        treadPixels=data.treadPixels; segments=ControllerPlatform.treadCount(treadPixels);
        startOffset=data.travelAxis==RampGeometry.LEFT?-data.startOffset:data.startOffset;
        int signedEnd=data.travelAxis==RampGeometry.LEFT?-data.endOffset:data.endOffset;
        drop=Math.abs(signedEnd); top=signedEnd<0;
        startHalfSteps=tag.hasKey(SaveSchema.Ramp.START_OFFSET_HALF_STEPS,3)
                ?tag.getInteger(SaveSchema.Ramp.START_OFFSET_HALF_STEPS):startOffset*2;
        endHalfSteps=tag.hasKey(SaveSchema.Ramp.END_OFFSET_HALF_STEPS,3)
                ?tag.getInteger(SaveSchema.Ramp.END_OFFSET_HALF_STEPS):signedEnd*2;
        if (data.travelAxis==RampGeometry.LEFT) {
            if (tag.hasKey(SaveSchema.Ramp.START_OFFSET_HALF_STEPS,3)) startHalfSteps=-startHalfSteps;
            if (tag.hasKey(SaveSchema.Ramp.END_OFFSET_HALF_STEPS,3)) endHalfSteps=-endHalfSteps;
        }
        startHalfSteps=Math.max(-ControllerPlatform.MAX_OFFSET_HALF_STEPS,
                Math.min(ControllerPlatform.MAX_OFFSET_HALF_STEPS,startHalfSteps));
        endHalfSteps=Math.max(-ControllerPlatform.MAX_OFFSET_HALF_STEPS,
                Math.min(ControllerPlatform.MAX_OFFSET_HALF_STEPS,endHalfSteps));
        startOffset=startHalfSteps/2; drop=(Math.abs(endHalfSteps)+1)/2; top=endHalfSteps<0;
        travelAxis=data.travelAxis==RampGeometry.LEFT?RampGeometry.RIGHT:data.travelAxis;
        extendSegments=data.extendSegments; matchTextures=data.matchTextures; speed=data.speed; slow=speed==2;
        if (world!=null && !world.isRemote && !previousChannels.equals(channels))
            DeferredTileLoad.schedule(this, () -> RedstoneChannels.channelChanged(this,oldChannel));
        original=tag.hasKey(SaveSchema.Ramp.ORIGINAL_STATE)
                ?LegacyBlockStates.decode(tag.getString(SaveSchema.Ramp.ORIGINAL_STATE)):null;
        if (original==null) original=tag.hasKey(SaveSchema.Ramp.ORIGINAL)
                ?NBTUtil.readBlockState(tag.getCompoundTag(SaveSchema.Ramp.ORIGINAL))
                :Blocks.AIR.getDefaultState();
        owner=readOwner(tag);
        sources.clear(); NBTTagList list=tag.getTagList(SaveSchema.Ramp.SOURCES,10);
        for (int i=0;i<Math.min(128,list.tagCount());i++) sources.add(readPosition(list.getCompoundTagAt(i)));
        sourceTiles.clear();
        NBTTagList tileList=tag.getTagList("RampSourceTiles",10);
        for (int i=0;i<Math.min(128,tileList.tagCount());i++) {
            NBTTagCompound entry=tileList.getCompoundTagAt(i);
            if (entry.hasKey("Tile",10)) sourceTiles.put(readPosition(entry),
                    entry.getCompoundTag("Tile").copy());
        }
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
        if(world!=null)world.markBlockRangeForRenderUpdate(pos,pos);
    }
}
