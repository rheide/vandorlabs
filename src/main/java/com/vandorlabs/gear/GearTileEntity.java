package com.vandorlabs.gear;

import com.vandorlabs.gear.core.ArmResource;

import com.vandorlabs.gear.core.GearMotion;
import com.vandorlabs.gear.core.ArmHinge;
import com.vandorlabs.gear.core.GearPoseCache;
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
public class GearTileEntity extends TileEntity implements ITickable,com.vandorlabs.redstone.RedstoneChannelMember {
    public static final int ANIMATION_TICKS = 24;

    @Nullable private BlockPos owner;
    @Nullable private GearTopology.Assembly assembly;
    private final List<BlockPos> columns = new ArrayList<BlockPos>();
    private final Set<BlockPos> members = new HashSet<BlockPos>();
    private EnumFacing footFacing = EnumFacing.NORTH,sourceFootFacing=EnumFacing.NORTH;
    private boolean sourceFootFacingExplicit;
    private EnumFacing configuredFootFacing;
    private boolean middleHinge;
    private ArmHinge.Mode hingeMode=ArmHinge.Mode.SINGLE;
    @Nullable private GearMotion.Direction configuredKink;
    private GearPoseCache poseCache;
    public ArmHinge.Mode hingeMode(){return hingeMode;}
    public GearMotion.Direction kinkDirection(){return configuredKink==null?direction:configuredKink;}
    public boolean explicitKink(){return configuredKink!=null;}
    private GearPoseCache poseCache(){if(poseCache==null)poseCache=new GearPoseCache();return poseCache;}
    public boolean middleHinge(){return middleHinge;}
    /** Orientation within the arm mounting plane, independent of the folding direction. */
    public boolean canConfigureFootFacing(){return assembly!=null&&assembly.kind().configurableFacing();}
    private ArmPayload payload;
    private boolean payloadPreparationPending,clientGeometryPending;
    GearGeometry.MovePlan riderPlan;
    private boolean joinEnabled,carryLoad,joinedSettingsPresent;
    private ArmResource resourceTransfer=ArmResource.DISABLED;
    private ArmResourceTransfer resourceConnection;

    private static ArmResource readResource(NBTTagCompound tag,String key){
        return ArmResource.read(tag.hasKey(key),tag.hasKey(key,3),tag.getInteger(key));
    }
    public ArmResource resourceTransfer(){return resourceTransfer;}
    public boolean canTransferResources(){return assembly!=null&&assembly.kind()==GearTopology.FootKind.NONE;}
    boolean resourceEndpointAvailable(){
        return !firstServerTick&&isOperational()&&canTransferResources()&&!hasFailedArmSnapshot()&&!payloadPreparationPending
                &&!adjustingStart&&!parkingHoldNeedsGeometry&&taxiState==null&&progress==(target()?1:0)
                &&(payload==null||payload.materialized());
    }
    ArmResourceTransfer resourceConnection(){
        if(resourceConnection==null)resourceConnection=new ArmResourceTransfer(this);
        return resourceConnection;
    }
    @Override public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> capability,EnumFacing side){
        return ArmResourceTransfer.input(this,side,capability)!=null||super.hasCapability(capability,side);
    }
    @Override public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability,EnumFacing side){
        T input=ArmResourceTransfer.input(this,side,capability);
        return input!=null?input:super.getCapability(capability,side);
    }
    private int joinRange=32,loadHeight=1;
    public boolean joinEnabled(){return joinEnabled;}
    public int joinRange(){return joinRange;}
    public boolean carryLoad(){return carryLoad;}
    public int loadHeight(){return loadHeight;}
    public boolean canCarryLoad(){return !isSwingArmController()&&!isFixedMount()&&mountDirection.getAxis()==EnumFacing.Axis.Y;}
    void awaitPayloadJournal(){payloadPreparationPending=true;}
    void preparationRejected(String reason){payloadPreparationPending=false;if(progress==0||progress==1)deployedTarget=progress==1;motionBlocked=false;lastError=reason;sync();}
    private NBTTagCompound failedPayload,failedStoredSections;
    ArmStoredSections storedSections;
    public boolean hasStoredSections(){return storedSections!=null||failedStoredSections!=null;}
    public java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> storedSectionCells(){return storedSections==null?java.util.Collections.emptyList():storedSections.cells;}
    public boolean validVacantStoredSections(){
        if(storedSections==null||hasFailedArmSnapshot()||payload!=null||progress!=0||mode!=GearMotion.Mode.EXTEND
                ||!isOperational()||isLandingAssembly()||loadedController()!=this||world==null)return false;
        for(com.vandorlabs.vehicle.VehicleStructure.Cell section:storedSections.cells){
            BlockPos at=pos.add(section.pos);
            if(section.state.getBlock()!=getBlockType()||!members.contains(at)||!GearAssembly.loaded(world,at)
                    ||!world.isAirBlock(at)||world.getTileEntity(at)!=null)return false;
        }
        return true;
    }
    private java.util.UUID operator;
    private boolean recoveryDropped;
    public boolean hasFailedArmSnapshot(){return failedPayload!=null||failedStoredSections!=null||parkingQuarantine!=null;}
    public boolean hasRetainedPayload(){return parkingQuarantine!=null||taxiState!=null||hasStoredSections()||failedPayload!=null||payload!=null&&!payload.materialized();}
    public boolean recoveryDropped(){return recoveryDropped;}
    public void recoverOnBreak(net.minecraft.block.Block block){
        if(world==null||world.isRemote||!hasRetainedPayload()||recoveryDropped)return;
        if(block instanceof com.vandorlabs.gear.track.TrackBlock)block=com.vandorlabs.gear.track.TrackRegistry.COMPACT;
        net.minecraft.item.ItemStack item=new net.minecraft.item.ItemStack(block);NBTTagCompound recovery=writeToNBT(new NBTTagCompound());if(payload!=null&&payload.materialized())recovery.removeTag("ArmPayload");item.setTagInfo("ArmRecovery",recovery);
        net.minecraft.block.Block.spawnAsEntity(world,pos,item);recoveryDropped=true;payload=null;failedPayload=null;storedSections=null;failedStoredSections=null;
    }
    public void quarantinePayload(){if(payload!=null){failedPayload=payload.write();payload=null;}operational=false;motionBlocked=true;lastError="Attachment unavailable; original snapshot retained";sync();}
    public ArmPayload payload(){return payload;}
    /** Installed carried-control publication, without nested tile/capability construction. */
    public void installCapturedCoupler(ArmPayload expected,ArmPayload next){
        if(!(world instanceof com.vandorlabs.vehicle.VehicleWorld)||((com.vandorlabs.vehicle.VehicleWorld)world).parent.isRemote||world.getTileEntity(pos)!=this||payload!=expected||next==null||next.materialized())throw new IllegalArgumentException("Captured arm owner changed");payload=next;
    }

    /** Configuration-only publication: the installed physical root retains sole attachment ownership. */
    void installCoreSettings(ArmPayload expected,ArmPayload next){
        if(world==null||world.isRemote||!(world instanceof net.minecraft.world.WorldServer)||!world.getMinecraftServer().isCallingFromMinecraftThread()||world.getTileEntity(pos)!=this||!isOperational()||payload!=expected||expected==null||expected.materialized()||expected.hasOwnershipJournal()||ArmTransactions.locked(world,pos)||next==null||next.materialized())throw new IllegalArgumentException("Carried machine ownership changed");
        payload=next;sync();
    }

    void removePayloadCell(BlockPos cell){if(payload==null||payload.materialized())throw new IllegalArgumentException("Payload is no longer captured");payload=payload.without(cell);sync();GearGeometry.register(this);}
    public void operator(net.minecraft.entity.player.EntityPlayer player){if(player!=null){operator=player.getUniqueID();com.vandorlabs.tiles.WorldActionAuthorization.authorize(this,player);markDirty();}}
    public boolean preparePayloadMotion(){
        payloadPreparationPending=false;
        if(assembly==null||assembly.kind()!=GearTopology.FootKind.NONE)return true;
        if(payload!=null&&payload.hasOwnershipJournal()){lastError="Original attachment cells need recovery before this snapshot can move";return false;}
        if(failedPayload!=null){lastError="Attachment snapshot could not be loaded; original data retained";return false;}
        try{
            if(!ArmActiveLanding.prepare(this))return false;
            refreshMaterializedPayload();
            if(failedStoredSections!=null)throw new IllegalArgumentException("Stored arm sections are unavailable; recovery retained");
            // The original attachment stays owned by this arm in push mode.
            // Only blocks met during the stroke are left in the native world.
            if(mode==GearMotion.Mode.EXTEND&&(progress==0||progress==1)&&(payload==null||(joinEnabled&&columns.size()>1||payload.isSurfaceLayer())&&payload.materialized())&&!ArmTransactions.locked(world,pos)){
                net.minecraft.util.math.Vec3d shift=ArmOrientation.vector(mountDirection,0,pose(progress).footOffsetY(),0);
                if(Math.abs(shift.x-Math.rint(shift.x))+Math.abs(shift.y-Math.rint(shift.y))+Math.abs(shift.z-Math.rint(shift.z))<1e-8){payload=ArmPayload.discover(this,com.vandorlabs.tiles.WorldActionAuthorization.actor(this),new BlockPos(Math.rint(shift.x),Math.rint(shift.y),Math.rint(shift.z)));sync();
                    if(payload==null){if(!ArmStoredSections.restoreEmpty(this)){payloadPreparationPending=true;return false;}return true;}}
            }
            if(payload==null&&storedSections!=null){if(!ArmStoredSections.restoreEmpty(this)){payloadPreparationPending=true;return false;}}
            if(isSwingArmController()&&(progress==0||progress==1)&&!ArmTransactions.locked(world,pos)
                    &&(payload==null||payload.materialized()&&(joinEnabled&&columns.size()>1||payload.blocks.cells.size()==1||payload.isSurfaceLayer()))){
                ArmPayload.Endpoint endpoint=ArmPayload.endpoint(this,progress);
                if(endpoint!=null){payload=endpoint.capture(com.vandorlabs.tiles.WorldActionAuthorization.actor(this));sync();if(payload==null)return true;}
            }
            if(payload==null){
                BlockPos attachment=localCell(0,-assembly.columnHeight(),0);
                if(!GearAssembly.loaded(world,attachment)){lastError="Load the attachment first";return false;}
                if(world.isAirBlock(attachment)){if(adjustingStart&&progress==1&&!GearGeometry.startClear(this,null))throw new IllegalArgumentException("Starting angle is blocked; previous position retained");return true;}
                net.minecraft.entity.player.EntityPlayer actor=com.vandorlabs.tiles.WorldActionAuthorization.actor(this);
                if(!(actor instanceof net.minecraft.entity.player.EntityPlayerMP)){lastError="Use the Configurizer before moving this attachment";return false;}
                payload=ArmPayload.discover(this,(net.minecraft.entity.player.EntityPlayerMP)actor);sync();
            }
            if(adjustingStart&&progress==1){
                ArmPayload original=payload;
                // Simulate owned native cells as captured geometry, excluding only
                // their exact native collision boxes from foreign obstacles.
                try{if(original!=null&&original.materialized())payload=original.capturedCopy();clearPoses();
                    if(!GearGeometry.startClear(this,original))throw new IllegalArgumentException("Starting angle is blocked; previous position retained");
                }finally{payload=original;clearPoses();}
            }
            GearPushing.preflight(this);
            if(payload!=null&&payload.materialized()){if(!payload.matchesPlaced(this)){net.minecraft.entity.player.EntityPlayer actor=com.vandorlabs.tiles.WorldActionAuthorization.actor(this);payload=payload.refreshPlaced(this,actor instanceof net.minecraft.entity.player.EntityPlayerMP?(net.minecraft.entity.player.EntityPlayerMP)actor:null);}if(!payload.detach(this))return false;sync();}
            if(ArmTransactions.blocked(this)){payloadPreparationPending=true;return false;}
            return true;
        }catch(RuntimeException | LinkageError denied){if(denied instanceof com.vandorlabs.tiles.WorldActionAuthorization.AwaitingPlayer)payloadPreparationPending=true;lastError=denied.getMessage();if(payload!=null&&payload.hasOwnershipJournal()){operational=false;sync();}return false;}
    }
    private void refreshMaterializedPayload(){
        if(payload==null||!payload.materialized()||world==null||world.isRemote)return;
        ArmPayload remaining=payload.pruneRemoved(this);if(remaining!=payload){payload=remaining;if(remaining==null)releaseParkingHold();sync();GearGeometry.register(this);}
    }
    private void finishStartAdjustment(){
        if(!adjustingStart)return;
        com.vandorlabs.gear.core.ArmAngleAdjustment.Completion completed=com.vandorlabs.gear.core.ArmAngleAdjustment.retained(startAngle,activeAngle,true,savedEndAngle).complete(progress,deployedTarget);
        if(!completed.finished)return;
        startAngle=completed.angles.physicalStart;activeAngle=completed.angles.physicalEnd;progress=previousProgress=completed.phase;deployedTarget=completed.target;adjustingStart=false;signalKnown=false;boxesAt=Double.NaN;
        if(authoritativeAngleHost())for(BlockPos at:members){TileEntity tile=world.getTileEntity(at);if(tile instanceof GearTileEntity&&tile!=this){GearTileEntity member=(GearTileEntity)tile;if(!pos.equals(member.owner))continue;member.startAngle=startAngle;member.activeAngle=activeAngle;member.landingAnglesVersion=landingAnglesVersion;member.adjustingStart=false;member.progress=member.previousProgress=progress;member.deployedTarget=deployedTarget;member.signalKnown=false;member.sync();}}
        sync();
    }
    private void restorePayloadEndpoint(){if(progress!=(deployedTarget?1:0))return;try{if(payload!=null&&(progress==0||progress==1)&&!payload.materialized()&&payload.restore(this,progress))sync();}catch(RuntimeException | LinkageError failure){motionBlocked=true;lastError="Attachment snapshot retained: "+failure.getMessage();if(payload!=null&&payload.hasOwnershipJournal()){operational=false;sync();}else markDirty();}}
    private EnumFacing mountDirection=EnumFacing.DOWN;
    private EnumFacing componentFrame=EnumFacing.DOWN,sourceFootComponentFrame=EnumFacing.DOWN;
    private boolean componentFrameExplicit;
    private NBTTagCompound taxiState;
    private boolean taxiClearPending;
    private void validateTaxiState(NBTTagCompound state){GearTaxiState.validate(state);int expected=state.getInteger("Height")-(state.getInteger("Phase")==1?state.getInteger("Amount"):0);if(assembly==null||assembly.columnHeight()!=expected||!GearTaxiState.supportsFoot(assembly.kind())||mode!=GearMotion.Mode.EXTEND||mountDirection!=EnumFacing.DOWN||columns.size()!=1)throw new IllegalArgumentException("Taxi arm topology does not match its retained sections");}
    public boolean validTaxiState(){if(taxiState==null||failedPayload!=null||failedStoredSections!=null||storedSections!=null||payload!=null||!isOperational())return false;try{validateTaxiState(taxiState);return true;}catch(RuntimeException invalid){return false;}}
    public boolean hasTaxiSections(){if(taxiState==null||taxiState.getInteger("Phase")!=1)return false;try{GearTaxiState.validate(taxiState);return true;}catch(RuntimeException invalid){return false;}}
    public int taxiHeight(){return taxiState==null?0:taxiState.getInteger("Height");}
    public double taxiLevel(){return taxiState==null?0:taxiState.getDouble("Level");}
    public boolean consumeTaxiClear(){boolean clear=taxiClearPending;taxiClearPending=false;return clear;}
    public boolean hasTaxiData(){return taxiState!=null;}
    public int taxiPhase(){return taxiState==null?-1:taxiState.getInteger("Phase");}
    public int taxiAmount(){return taxiState==null?0:taxiState.getInteger("Amount");}
    public NBTTagCompound taxiState(){return taxiState==null?null:taxiState.copy();}
    public void clearTaxiError(){motionBlocked=false;lastError="";markDirty();}
    public void taxiState(NBTTagCompound next){if(next!=null)validateTaxiState(next);if(next==null&&taxiState!=null)taxiClearPending=true;taxiState=next==null?null:next.copy();markDirty();}
    private boolean pushBlocks;
    public boolean pushBlocks(){return pushBlocks;}
    public boolean canPushBlocks(){return mountDirection.getAxis().isHorizontal()&&mode==GearMotion.Mode.EXTEND&&!isLandingAssembly();}
    private boolean rotatePayload,trackExtensionStroke,trackRetractingStroke,armRetractingStroke,formingActiveTrack,formingActiveFoot;
    public boolean isExtendedTrackStroke(){return trackExtensionStroke&&assembly!=null&&assembly.kind()==GearTopology.FootKind.TRACK;}
    public boolean landingDeployed(){if(taxiState!=null)return taxiState.getInteger("Phase")==1&&progress==1&&validTaxiState();if(isSwingArmController()&&isLandingAssembly()&&landingAnglesVersion==1)return !adjustingStart&&landingEndpoints().uprightAt(progress)&&progress==(deployedTarget?1:0);return fullyDeployed()||isExtendedTrackStroke()&&!deployedTarget&&progress==0;}
    public int taxiSafeTarget(){return mountDirection!=EnumFacing.DOWN||!isLandingAssembly()?-1:isSwingArmController()&&landingAnglesVersion==1?landingEndpoints().safeTarget(deployedTarget):1;}
    private boolean authoritativeAngleHost(){World physical=world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).parent:world;return physical!=null&&!physical.isRemote;}
    public boolean finishCapturedAngleAdjustment(){if(!(world instanceof com.vandorlabs.vehicle.VehicleWorld)||!authoritativeAngleHost())return false;boolean before=adjustingStart;finishStartAdjustment();return before&&!adjustingStart;}
    public void showError(String message){lastError=message==null?"Arm operation unavailable":message;sync();}
    public boolean formTrackAtActivePose(){if(assembly==null||assembly.kind()!=GearTopology.FootKind.NONE&&!isExtendedTrackStroke()||payload!=null||failedPayload!=null)return false;formingActiveTrack=true;boolean previousStroke=trackExtensionStroke,previousRetracting=trackRetractingStroke;trackRetractingStroke=activeExtension()<0;trackExtensionStroke=true;try{boolean formed=rebuild();if(!formed){trackExtensionStroke=previousStroke;trackRetractingStroke=previousRetracting;}return formed;}finally{formingActiveTrack=false;}}
    boolean formLandingAtActivePose(){formingActiveFoot=true;try{return rebuild();}finally{formingActiveFoot=false;}}
    java.util.List<AxisAlignedBB> landingBoxes(GearTopology.FootKind kind,EnumFacing facing,java.util.List<GearTopology.Cell> feet,java.util.Map<BlockPos,IBlockState> states){GearTopology.Assembly previous=assembly;EnumFacing old=footFacing;try{assembly=GearTopology.validate(previous.columnHeight(),kind,feet).assembly();footFacing=configuredFootFacing==null?facing:configuredFootFacing;return GearGeometry.landingBoxes(this,states);}finally{assembly=previous;footFacing=old;}}
    private double activeAngle=90,activeExtension=1,startAngle,savedEndAngle;
    private boolean adjustingStart;
    private int landingAnglesVersion=-1;
    private final com.vandorlabs.gear.core.LandingAnglePolicy.Cache landingAngleCache=new com.vandorlabs.gear.core.LandingAnglePolicy.Cache();
    private static int readLandingAngleVersion(NBTTagCompound tag,boolean saved){String key=saved?com.vandorlabs.gear.core.ArmLandingAngles.SAVED:com.vandorlabs.gear.core.ArmLandingAngles.CHOICE;return com.vandorlabs.gear.core.ArmLandingAngles.version(tag.hasKey(key),tag.hasKey(key,3),tag.getInteger(key));}
    public int landingAnglesVersion(){return landingAnglesVersion;}
    private double physicalEndAngle(){return isLandingAssembly()&&landingAnglesVersion!=1?90:activeAngle;}
    public com.vandorlabs.gear.core.LandingAnglePolicy.Result landingEndpoints(){if(!isLandingAssembly())throw new IllegalStateException("Landing layout unavailable");return landingAngleCache.get(mode,direction,assembly.columnHeight(),armSize(),stowedFootY(),hingeDrop(),footMountOffset(),hingeMode,kinkDirection(),landingAnglesVersion==1?startAngle():0,landingAnglesVersion==1?configuredEndAngle():90,assembly.feet());}
    private com.vandorlabs.gear.core.LandingAnglePolicy.Result proposedLandingEndpoints(double start,double end,int direction,boolean rotate,com.vandorlabs.gear.core.ArmHinge.Mode hinge,GearMotion.Direction kink){GearMotion.Mode motion=rotate?GearMotion.Mode.ROTATE:GearMotion.Mode.ROTATE_LEVEL;double drop=hinge==com.vandorlabs.gear.core.ArmHinge.Mode.SINGLE&&motion==GearMotion.Mode.ROTATE_LEVEL&&armSize()==2&&assembly.kind()==GearTopology.FootKind.WHEEL?17/128D:0;return com.vandorlabs.gear.core.LandingAnglePolicy.evaluate(motion,GearMotion.Direction.values()[direction],assembly.columnHeight(),armSize(),GearMotion.RETRACTED_FOOT_Y,drop,footMountOffset(),hinge,kink,new com.vandorlabs.gear.core.ArmLandingAngles(1,start,end),assembly.feet());}
    public NBTTagCompound copiedSettings(){NBTTagCompound tag=settings();com.vandorlabs.gear.core.ArmLandingAngles angles=new com.vandorlabs.gear.core.ArmLandingAngles(landingAnglesVersion,startAngle(),configuredEndAngle()).copied(isLandingAssembly());tag.setInteger(com.vandorlabs.gear.core.ArmLandingAngles.CHOICE,angles.version);tag.setDouble("StartAngle",angles.rawStart);tag.setDouble("ActiveAngle",angles.rawEnd);return tag;}

    public double startAngle(){return adjustingStart?activeAngle:startAngle;}
    public double configuredEndAngle(){return adjustingStart?savedEndAngle:activeAngle;}
    public double physicalStartAngle(){return isLandingAssembly()&&landingAnglesVersion!=1?0:startAngle;}
    public EnumFacing mountDirection(){return mountDirection;}
    public EnumFacing componentFrame(){return componentFrame;}
    void componentFrame(EnumFacing frame){java.util.Objects.requireNonNull(frame);boolean explicit=frame!=EnumFacing.DOWN||!(getBlockType() instanceof LargeGearBlock);if(componentFrame!=frame||componentFrameExplicit!=explicit){componentFrame=frame;componentFrameExplicit=explicit;clearPoses();GearGeometry.register(this);sync();}}
    public void setMountDirection(EnumFacing face){if(mountDirection!=face){mountDirection=face;scheduleRebuild();sync();}}
    public BlockPos localCell(int x,int y,int z){return pos.add(ArmOrientation.vector(mountDirection,new BlockPos(x,y,z)));}
    public BlockPos localCell(GearTopology.Cell cell){return localCell(cell.x,cell.y,cell.z);}
    public boolean isLandingAssembly(){return assembly!=null&&assembly.kind()!=GearTopology.FootKind.NONE;}
    public boolean rotatePayload(){return rotatePayload;}
    public double activeAngle(){return activeAngle;}
    /** Raw configured geometry; unlike activeExtension(), these never depend on the current phase. */
    public double configuredActiveExtension(){return activeExtension;}
    public boolean armRetractionStroke(){return armRetractingStroke;}
    public boolean trackRetractionStroke(){return trackRetractingStroke;}
    boolean sameJoinConfiguration(GearTileEntity other){return triggerMode==other.triggerMode&&direction==other.direction
            &&hingeMode==other.hingeMode&&kinkDirection()==other.kinkDirection()&&pushBlocks==other.pushBlocks&&rotatePayload==other.rotatePayload&&activeAngle==other.activeAngle&&startAngle==other.startAngle&&activeExtension==other.activeExtension
            &&joinEnabled==other.joinEnabled&&joinRange==other.joinRange&&carryLoad==other.carryLoad&&loadHeight==other.loadHeight
            &&legacyAnyChannels==other.legacyAnyChannels&&channels.equals(other.channels);}
    public double minimumExtension(){return assembly==null||assembly.columnHeight()==1?-.5:1-assembly.columnHeight();}
    /** Preserve the native first-retraction curve before forecasting or committing a carried step. */
    public boolean latchRetractionStroke(){
        if(assembly!=null&&progress==1&&!deployedTarget&&assembly.kind()==GearTopology.FootKind.NONE&&!armRetractingStroke){armRetractingStroke=true;sync();return true;}return false;
    }
    public double activeExtension(){if(assembly!=null&&!isSwingArmController()&&!isFixedMount()){if(isExtendedTrackStroke()&&!trackRetractingStroke)return assembly.columnHeight();if(assembly.kind()==GearTopology.FootKind.NONE)return progress==1||armRetractingStroke?minimumExtension():assembly.columnHeight();if(isExtendedTrackStroke())return minimumExtension();}return activeExtension;}

    private GearMotion.Mode mode = GearMotion.Mode.EXTEND;
    private GearMotion.Direction direction = GearMotion.Direction.NORTH;
    private boolean deployedTarget = true;
    private boolean couplerPassive;
    public boolean couplerPassive(){return couplerPassive;}
    /** Accepted coupled phase only; preparation/cosmetic samples must never call this. */
    public void acceptCouplerPassive(){
        if(!(world instanceof com.vandorlabs.vehicle.VehicleWorld))throw new IllegalStateException("Passive arm commit requires installed carried owner");
        com.vandorlabs.vehicle.VehicleWorld view=(com.vandorlabs.vehicle.VehicleWorld)world;com.vandorlabs.vehicle.EntityGroundVehicle craft=view.craft;
        if(view.parent.isRemote||view.parent.getMinecraftServer()==null||!view.parent.getMinecraftServer().isCallingFromMinecraftThread()||craft==null||craft.view!=view||craft.structure!=view.structure||!craft.couplerScopeReady()||view.tiles.get(pos)!=this||craft.isDead||craft.transferInProgress||craft.ownershipBlocked||view.parent.getEntityByID(craft.getEntityId())!=craft||!isOperational()||isFixedMount()||isLandingAssembly())throw new IllegalStateException("Passive arm commit requires authoritative live owner");
        if(!couplerPassive){motionRevision=Math.addExact(motionRevision,1);couplerPassive=true;markDirty();}
    }
    private boolean clearCouplerPassive(){
        if(!couplerPassive)return false;
        if(world instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.VehicleWorld view=(com.vandorlabs.vehicle.VehicleWorld)world;if(!view.parent.isRemote&&view.craft!=null&&view.craft.view==view&&view.tiles.get(pos)==this)motionRevision=Math.addExact(motionRevision,1);}
        couplerPassive=false;markDirty();return true;
    }
    private boolean signalKnown,lastSignal,legacyAnyChannels;
    public boolean usesLegacyInput(){return legacyAnyChannels;}
    public double footMountOffset(){if(assembly!=null&&assembly.kind()==GearTopology.FootKind.NONE)return 13/16D;if(assembly!=null&&assembly.kind()==GearTopology.FootKind.TRACK)return 11/16D;return assembly!=null&&assembly.kind()==GearTopology.FootKind.WHEEL?(armSize()==2?11/16D:9/16D):0;}
    private void upgradeHingeAtRest(){
        if(hingeMode!=ArmHinge.Mode.LEGACY_CENTER||!isSwingArmController()||assembly==null||owner!=null||progress!=1||!deployedTarget||startAngle!=0||adjustingStart||world instanceof com.vandorlabs.vehicle.VehicleWorld||hasFailedArmSnapshot()||storedSections!=null||payload!=null&&(!payload.materialized()||payload.hasOwnershipJournal())||ArmTransactions.locked(world,pos)||!cachedMembersIntact())return;
        for(BlockPos at:members){TileEntity raw=world.getTileEntity(at);if(!(raw instanceof GearTileEntity))return;}
        hingeMode=ArmHinge.Mode.DOUBLE;boxesAt=Double.NaN;
        for(BlockPos at:members){GearTileEntity member=(GearTileEntity)world.getTileEntity(at);member.hingeMode=hingeMode;member.middleHinge=true;member.boxesAt=Double.NaN;member.sync();}
        sync();
    }
    private double stowedFootY(){
        if(assembly.kind()==GearTopology.FootKind.NONE||isExtendedTrackStroke())return -assembly.columnHeight()-activeExtension();
        if(mode==GearMotion.Mode.EXTEND)return footMountOffset()>0?6/16D-1/64D-(3/16D+footMountOffset()):GearMotion.RETRACTED_FOOT_Y-1/64D;
        return GearMotion.RETRACTED_FOOT_Y;
    }
    private double hingeDrop(){return !middleHinge&&mode==GearMotion.Mode.ROTATE_LEVEL&&armSize()==2&&assembly.kind()==GearTopology.FootKind.WHEEL?17/128D:0;}
    public GearMotion.Pose pose(double progress){return poseCache().get(mode,direction,assembly.columnHeight(),progress,stowedFootY(),hingeDrop(),footMountOffset(),physicalEndAngle(),physicalStartAngle(),hingeMode,kinkDirection(),armSize());}
    /** Same descriptor keeps sweep probes and frame poses in one bounded cache. */
    public GearMotion.Pose poseAtAngle(double angle){return poseCache().get(mode,direction,assembly.columnHeight(),1,stowedFootY(),hingeDrop(),footMountOffset(),angle,angle,hingeMode,kinkDirection(),armSize());}

    private double progress = 1.0;
    private boolean surfaceDepartureActive=true,surfaceDepartureSaved=true,surfaceDepartureTarget=true;
    /** Only reaching an endpoint changes the surface appearance, including on reversal. */
    public transient Object surfaceVisualHandoff;
    public boolean surfaceActive(){rememberSurfaceEndpoint();return surfaceDepartureActive;}
    private void rememberSurfaceEndpoint(){if(surfaceDepartureTarget!=deployedTarget){surfaceDepartureTarget=deployedTarget;surfaceDepartureSaved=true;}if(progress==0||progress==1){surfaceDepartureActive=progress==1;surfaceDepartureSaved=true;}}
    private double previousProgress = 1.0;
    private int motionRevision;
    public transient int clientUpdates;
    private long lastMotionTick=Long.MIN_VALUE;
    public int motionRevision(){return motionRevision;}
    private boolean parkingHold,parkingHoldNeedsGeometry;
    private NBTTagCompound parkingOriginal,parkingQuarantine;
    private boolean parkingSignalBaseline;
    public boolean parkingHeld(){return parkingHold&&parkingQuarantine==null;}
    public boolean parkingQuarantined(){return parkingQuarantine!=null;}
    public boolean motionBlocked(){return motionBlocked;}
    private void releaseParkingHold(){parkingHold=false;parkingHoldNeedsGeometry=false;parkingSignalBaseline=false;}
    private static NBTTagCompound exactInto(NBTTagCompound target,NBTTagCompound source){for(String key:new java.util.ArrayList<String>(target.getKeySet()))target.removeTag(key);for(String key:source.getKeySet())target.setTag(key,source.getTag(key).copy());return target;}
    private void quarantineParking(NBTTagCompound original){parkingQuarantine=original.copy();parkingHold=false;parkingHoldNeedsGeometry=false;parkingSignalBaseline=false;operational=false;assembly=null;owner=null;payload=null;storedSections=null;failedPayload=null;failedStoredSections=null;taxiState=null;members.clear();columns.clear();clearPoses();GearGeometry.remove(this);lastError="Arm parking snapshot retained; incompatible stationary ownership";}
    /** Installed main-thread preflight; load callbacks never invoke this query. */
    public boolean validateParkingHoldInstalled(){return validateParkingHoldInstalled(false);}
    /** Final publication rechecks native source receipts without invalidating a pending prototype. */
    public boolean validateParkingHoldInstalled(boolean requireNativeReceipt){
        if(world==null||parkingQuarantine!=null)return false;
        if(!parkingHold&&!parkingHoldNeedsGeometry)return true;
        if(ArmTransactions.blocked(this)||com.vandorlabs.tiles.WorldActionAuthorization.awaitingLoad(this)||!GearAssembly.loaded(world,pos))return false;
        TileEntity installed=world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).tiles.get(pos):world.getChunkFromBlockCoords(pos).getTileEntityMap().get(pos);
        if(installed!=this||!(world.getBlockState(pos).getBlock() instanceof GearBlock))return false;
        if(!parkingHoldNeedsGeometry&&!requireNativeReceipt)return true;
        if(payload!=null&&payload.materialized())for(int i=0;i<payload.blocks.cells.size();i++){
            BlockPos at=payload.placedCellPosition(this,i);if(!GearAssembly.loaded(world,at))return false;
            com.vandorlabs.vehicle.VehicleStructure.Cell nativeCell=world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).structure.at(at):null;
            if(world instanceof com.vandorlabs.vehicle.VehicleWorld&&nativeCell==null)return false;
            IBlockState actual=nativeCell==null?world.getBlockState(at):nativeCell.state,expected=payload.placedCellState(i);
            if(expected.getBlock() instanceof com.vandorlabs.coupler.BlockCoupler&&actual.getBlock()==expected.getBlock())expected=expected.withProperty(com.vandorlabs.coupler.BlockCoupler.LOCKED,actual.getValue(com.vandorlabs.coupler.BlockCoupler.LOCKED));
            if(actual!=expected)return false; // A partial native publication is pending, never a mining instruction.
            if(payload.blocks.cells.get(i).hasTileData()&&(world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).tiles.get(at):world.getChunkFromBlockCoords(at).getTileEntityMap().get(at))==null)return false;
        }
        return requireNativeReceipt?parkingGeometryCompatible():validateParkingGeometry();
    }
    private boolean validateParkingGeometry(){
        if(!parkingHoldNeedsGeometry)return parkingQuarantine==null;
        if(!parkingGeometryCompatible()){quarantineParking(parkingOriginal);return false;}
        parkingHoldNeedsGeometry=false;
        // The validated live payload owns these bytes; keep only the retained root envelope.
        parkingOriginal.removeTag("ArmPayload");parkingOriginal.removeTag("ArmStoredSections");return true;
    }
    private boolean parkingGeometryCompatible(){
        if(!parkingHold||!isOperational()||payload==null||!payload.materialized()&&!(world instanceof com.vandorlabs.vehicle.VehicleWorld))return false;
        ArmGridMapping mapping=payload.gridMapping(this,progress);if(mapping==null)return false;
        for(int i=0;i<payload.blocks.cells.size();i++){com.vandorlabs.vehicle.VehicleStructure.Cell cell=payload.blocks.cells.get(i);if(!mapping.cell(cell.pos).equals(payload.placedCellPosition(this,i))||mapping.state(cell)!=payload.placedCellState(i))return false;}
        return true;
    }
    public boolean captureBlocked(boolean value){boolean changed=motionBlocked!=value;motionBlocked=value;return changed;}
    public boolean acceptMotion(int revision,long tick,boolean target,boolean blocked){
        if(revision!=motionRevision||tick<=lastMotionTick||!isOperational())return false;
        lastMotionTick=tick;deployedTarget=isFixedMount()||target;motionBlocked=blocked;return true;
    }
    /** Scalar STATE correction after matching layout/scope admission; this never changes payload ownership. */
    public boolean canAcceptCouplerFrame(long revision,long tick,double phase){
        if(!(world instanceof com.vandorlabs.vehicle.VehicleWorld))return false;
        com.vandorlabs.vehicle.VehicleWorld view=(com.vandorlabs.vehicle.VehicleWorld)world;com.vandorlabs.vehicle.EntityGroundVehicle craft=view.craft;
        return view.parent.isRemote&&craft!=null&&craft.view==view&&craft.structure==view.structure&&view.tiles.get(pos)==this&&craft.couplerScopeReady()&&isOperational()&&revision>=motionRevision&&revision<=Integer.MAX_VALUE&&tick>=lastMotionTick&&Double.isFinite(phase)&&phase>=0&&phase<=1;
    }
    public boolean acceptCouplerFrame(long revision,long tick,boolean target,boolean passive,double phase){
        if(!canAcceptCouplerFrame(revision,tick,phase))return false;
        motionRevision=(int)revision;lastMotionTick=tick;deployedTarget=isFixedMount()||target;couplerPassive=passive;correctProgress(phase);return true;
    }
    public void correctProgress(double value){rememberSurfaceEndpoint();if(value!=progress)surfaceDepartureSaved=true;previousProgress=progress;progress=isFixedMount()?1:Math.max(0,Math.min(1,value));boxesAt=Double.NaN;}
    // A cached group may be temporarily suspended while required chunks are
    // absent. Its saved membership, target and exact progress are preserved.
    private boolean operational = true,motionBlocked,configured;
    private double boxesAt=Double.NaN;
    private final java.util.LinkedHashMap<Double,java.util.List<AxisAlignedBB>> poses=new java.util.LinkedHashMap<>(32,.75F,true);
    private int poseBoxes;
    java.util.List<AxisAlignedBB> boxesAt(double value){
        java.util.List<AxisAlignedBB> found=poses.get(value);if(found!=null)return found;
        found=GearGeometry.computeBoxes(this,value);if(found.size()<=8192){poses.put(value,found);poseBoxes+=found.size();
            while(poses.size()>32||poseBoxes>8192){java.util.Iterator<java.util.List<AxisAlignedBB>> i=poses.values().iterator();poseBoxes-=i.next().size();i.remove();}}
        return found;
    }
    private void clearPoses(){poses.clear();poseBoxes=0;boxesAt=Double.NaN;}
    private java.util.List<AxisAlignedBB> cachedBoxes=java.util.Collections.emptyList();
    public java.util.List<AxisAlignedBB> collisionBoxes(){if(!isOperational())return java.util.Collections.emptyList();if(boxesAt!=progress){cachedBoxes=GearGeometry.boxes(this,progress);boxesAt=progress;}return cachedBoxes;}
    public void captureProgress(double next){rememberSurfaceEndpoint();if(next!=progress)surfaceDepartureSaved=true;previousProgress=progress;progress=isFixedMount()?1:Math.max(0,Math.min(1,next));boxesAt=Double.NaN;}

    private int rebuildDelay = -1;
    private boolean retryUnavailable;
    private boolean firstServerTick = true;
    private boolean releasing;
    private com.vandorlabs.redstone.ChannelList channels=com.vandorlabs.redstone.ChannelList.EMPTY;
    public int triggerMode;
    public String error(){return motionBlocked&&(lastError==null||lastError.isEmpty())?"Motion blocked; clear the stroke":lastError==null?"Arm motion unavailable":lastError;}
    public java.util.Set<BlockPos> members(){return java.util.Collections.unmodifiableSet(members);}
    public java.util.List<BlockPos> columns(){return java.util.Collections.unmodifiableList(columns);}
    public double progress(){return progress;}
    public boolean target(){return isFixedMount()||deployedTarget;}
    public net.minecraft.tileentity.TileEntity channelTile(){return this;}
    public int getRedstoneChannel(){return channels.first();}
    public com.vandorlabs.redstone.ChannelList getRedstoneChannels(){return channels;}
    public void setRedstoneChannel(int channel){setRedstoneChannels(com.vandorlabs.redstone.ChannelList.of(channel));}
    public void setRedstoneChannels(com.vandorlabs.redstone.ChannelList value){if(parkingQuarantine!=null||parkingHoldNeedsGeometry&&!validateParkingHoldInstalled())return;if(!channels.equals(value)){clearCouplerPassive();releaseParkingHold();signalKnown=false;}channels=value;sync();}
    public boolean hasLocalRedstoneSignal(){return false;}
    public void setChannelSignal(boolean powered){}
    public void target(boolean value){if(parkingQuarantine!=null||parkingHoldNeedsGeometry&&!validateParkingHoldInstalled())return;boolean held=parkingHold,passive=clearCouplerPassive();releaseParkingHold();if(isFixedMount())return;if(deployedTarget==value){if(held||passive)sync();return;}deployedTarget=value;sync();if(world!=null)com.vandorlabs.tiles.LandingGearCovers.refresh(world,pos);}
    private boolean localPower(){
        java.util.List<BlockPos> roots=columns.isEmpty()?java.util.Collections.singletonList(BlockPos.ORIGIN):columns;
        for(BlockPos column:roots){BlockPos at=pos.add(column);if(!GearAssembly.loaded(world,at))continue;net.minecraft.block.state.IBlockState state=world.getBlockState(at);
            if(state.getBlock() instanceof GearBlock){for(BlockPos cell:((GearBlock)state.getBlock()).componentCells(world,at))if(GearAssembly.loaded(world,cell)&&com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world,cell))return true;}
            else if(com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world,at))return true;
        }return false;
    }
    public void input(){if(parkingQuarantine!=null||parkingHoldNeedsGeometry&&!validateParkingHoldInstalled()||adjustingStart)return;if(triggerMode!=0&&world!=null){
        // Detached preflight preserves saved fields; only a live input sample arms the fresh baseline.
        if(parkingSignalBaseline){signalKnown=false;parkingSignalBaseline=false;}
        net.minecraft.world.World bus=world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).parent:world;
        boolean powered;if(legacyAnyChannels){powered=localPower();for(int i=0;i<channels.size();i++)powered|=com.vandorlabs.redstone.RedstoneChannels.level(world,pos,channels.get(i))>0;}else powered=channels.isEmpty()?localPower():com.vandorlabs.redstone.RedstoneChannels.allPowered(world,pos,channels);
        if(parkingHold&&!signalKnown){signalKnown=true;lastSignal=powered;return;}
        if(!signalKnown||powered!=lastSignal){signalKnown=true;lastSignal=powered;boolean on=triggerMode==1?powered:!powered;/* Unformed legacy tiles retain deployment input semantics until topology explicitly selects a generic arm. */target(assembly!=null&&(assembly.kind()==GearTopology.FootKind.NONE||isExtendedTrackStroke())?!on:on);}
    }}

    private String lastError = "The gear has not been assembled yet.";

    public boolean isController() { return assembly != null; }
    public boolean isOperational() { return assembly != null && operational && (payload==null||!payload.hasOwnershipJournal()); }
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
    public int armSize(){return world!=null&&world.getBlockState(pos).getBlock() instanceof GearBlock?((GearBlock)world.getBlockState(pos).getBlock()).sizeClass():1;}
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

    // TileEntity.create calls this before capability deserialization; vanilla's
    // default hook is empty, even when the caller supplies a physical world.
    @Override protected void setWorldCreate(World world){setWorld(world);}
    @Override public void setWorld(World world){super.setWorld(world);rebuildMemberCache();clearPoses();}

    @Override
    public void onLoad() {com.vandorlabs.tiles.WorldActionAuthorization.resumed(this); firstServerTick=true;if(world!=null&&!world.isRemote)com.vandorlabs.tiles.DeferredTileLoad.schedule(this,()->{rebuildMemberCache();GearGeometry.register(this);ArmSurfaceActivity.discover(this);});else clientGeometryPending=true; }
    @Override public void invalidate(){com.vandorlabs.tiles.WorldActionAuthorization.revoke(this);GearGeometry.remove(this);super.invalidate();}
    @Override public void onChunkUnload(){com.vandorlabs.tiles.WorldActionAuthorization.unloaded(this);GearGeometry.remove(this);super.onChunkUnload();}

    public void scheduleRebuild() {
        clearPoses();
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
        if (world == null||parkingQuarantine!=null) return;
        if(clientGeometryPending&&world.isRemote&&world.isBlockLoaded(pos)&&world.getTileEntity(pos)==this){clientGeometryPending=false;rebuildMemberCache();GearGeometry.register(this);ArmSurfaceActivity.discover(this);}
        if(isFixedMount()){deployedTarget=true;progress=1;}
        if(failedStoredSections!=null)return;
        if(ArmRecoveryRuntimeChecks.paused(this)||ArmTransactions.blocked(this)||com.vandorlabs.tiles.WorldActionAuthorization.awaitingLoad(this))return;
        if(!validateParkingHoldInstalled())return;
        previousProgress = progress;
        if (world.isRemote) {
            // Collision follows received authority. Cosmetic interpolation belongs to
            // GearVisualMotion; predicting physics here makes riders fall ahead of a slow server.
            return;
        }
        refreshMaterializedPayload();
        if(payload!=null&&payload.hasOwnershipJournal()){
            net.minecraft.entity.player.EntityPlayer actor;try{actor=com.vandorlabs.tiles.WorldActionAuthorization.actor(this);}catch(IllegalArgumentException denied){lastError=denied.getMessage();return;}
            int before=payload.ownershipJournalSize();
            try{boolean reconciled=payload.reconcileOwnership(world,actor instanceof net.minecraft.entity.player.EntityPlayerMP?(net.minecraft.entity.player.EntityPlayerMP)actor:null);if(before!=payload.ownershipJournalSize())sync();if(!reconciled){lastError="Original attachment cells need recovery; snapshot retained";return;}operational=true;sync();}catch(RuntimeException | LinkageError failure){if(before!=payload.ownershipJournalSize())sync();lastError="Attachment ownership recovery blocked; snapshot retained";return;}
        }
        if(payload!=null&&!payload.materialized()&&!operational&&deployedTarget&&assembly!=null){
            try{if(payload.restore(this,1)){progress=previousProgress=1;operational=true;scheduleRebuild();sync();}}catch(RuntimeException | LinkageError failure){lastError="Attachment recovery blocked; snapshot retained";return;}
        }
        if (firstServerTick) {
            IBlockState initial=world.getBlockState(pos);
            if(initial.getBlock() instanceof LargeGearBlock&&!((LargeGearBlock)initial.getBlock()).migrateCompact(world,pos,initial))return;
            if(world.getTileEntity(pos)!=this)return;
            firstServerTick = false;
            rebuildMemberCache();
            requestNearbyRebuild(world, pos);
        }
        if (rebuildDelay >= 0 && --rebuildDelay < 0) rebuild();

        upgradeHingeAtRest();

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

        // A shortened Taxi assembly retains its unused sections until captured
        // again. Native block mode must not consume that retained ownership.
        if(taxiState!=null){input();return;}
        if (isOperational()) {
            if(joinEnabled&&columns.size()>1){for(BlockPos member:members)if(!GearAssembly.loaded(world,member)){operational=false;retryUnavailable=true;lastError="Load every joined arm to resume";sync();return;}}
            // Previous versions could save a blocked, partially applied Start.
            // Return along the already traversed path before clearing the setting.
            if(adjustingStart&&motionBlocked&&progress>0&&progress<1&&!deployedTarget){deployedTarget=true;sync();}
            finishStartAdjustment();
            restorePayloadEndpoint();
            input();
            if(parkingHold){motionBlocked=false;return;}
            // Older in-flight snapshots finish their original stroke before adopting retraction.
            latchRetractionStroke();
            double before = progress;
            double next=GearMotion.advance(progress,deployedTarget,1,ANIMATION_TICKS);
            boolean hadPayload=payload!=null,wasMaterialized=hadPayload&&payload.materialized();
            boolean prepared=next==before||preparePayloadMotion();
            if(prepared&&next!=before)try{prepared=GearPushing.prepare(this,next);}catch(RuntimeException|LinkageError refused){lastError=refused.getMessage();prepared=false;}
            // Leave the requested target intact while the ownership record reaches disk.
            // Treating this as an obstruction reverses the request before detach can run.
            if(!prepared&&payloadPreparationPending)return;
            boolean blocked=next!=before&&(!prepared||!GearGeometry.canMove(this,before,next));
            if(blocked&&(before==0||before==1)&&assembly.kind()==GearTopology.FootKind.NONE){
                String reason=lastError;
                boolean restored=payload==null||payload.materialized()||hadPayload&&!wasMaterialized;
                if(!restored&&payload!=null&&!payload.hasOwnershipJournal())try{restored=payload.restore(this,before);}catch(RuntimeException | LinkageError failure){lastError="Attachment restore blocked; original snapshot retained";}
                deployedTarget=before==1;
                if(restored){finishStartAdjustment();motionBlocked=false;lastError=prepared?"Motion blocked; returned to the previous position":reason;sync();com.vandorlabs.network.MessageGearMotion.send(this);return;}
                motionBlocked=true;lastError="Motion blocked; attachment snapshot retained for recovery";sync();com.vandorlabs.network.MessageGearMotion.send(this);return;
            }
            if(blocked!=motionBlocked){motionBlocked=blocked;sync();com.vandorlabs.network.MessageGearMotion.send(this);}
            if(!blocked&&next!=before){lastError="";boolean riders=riderPlan!=null&&!riderPlan.riders.isEmpty();rememberSurfaceEndpoint();surfaceDepartureSaved=true;progress=next;rememberSurfaceEndpoint();boxesAt=Double.NaN;if(riders)com.vandorlabs.network.MessageGearMotion.send(this);GearGeometry.pushEntities(this);markDirty();if(progress==0||progress==1){if(progress==1)scheduleRebuild();restorePayloadEndpoint();finishStartAdjustment();com.vandorlabs.network.MessageGearMotion.send(this);if(world!=null)com.vandorlabs.tiles.LandingGearCovers.refresh(world,pos);}else if(!riders&&(before==0||before==1||world.getTotalWorldTime()%3==0))com.vandorlabs.network.MessageGearMotion.send(this);}
        }
    }

    private boolean cachedMembersIntact() {
        if (assembly == null) return false;
        IBlockState rootState = world.getBlockState(pos);
        for (BlockPos member : members) {
            if (!GearAssembly.loaded(world, member)) return false;
            if(storedSections!=null&&storedSections.at(member.subtract(pos))!=null)continue;
            IBlockState state = world.getBlockState(member);
            if(GearAssembly.isExtender(state)){
                if(state.getBlock()!=rootState.getBlock())return false;
                GearBlock block=(GearBlock)state.getBlock();if(!block.componentIntact(world,block.componentRoot(world,member,state)))return false;
            }else{
                if(!GearAssembly.isFoot(state)||!((GearBlock)state.getBlock()).kind().name().equals(assembly.kind().name()))return false;
                if(assembly.kind()!=GearTopology.FootKind.PLATE&&state.getValue(GearBlock.FACING)!=sourceFootFacing)return false;
                if(assembly.kind()==GearTopology.FootKind.WHEEL && ((GearBlock)state.getBlock()).sizeClass()!=armSize())return false;
                GearBlock block=(GearBlock)state.getBlock();if(!block.componentIntact(world,block.componentRoot(world,member,state)))return false;
            }
            TileEntity tile = world.getTileEntity(member);
            if (!(tile instanceof GearTileEntity) || !pos.equals(((GearTileEntity) tile).owner)) return false;
            if (!state.getValue(GearBlock.ASSEMBLED)) return false;
        }
        return true;
    }

    /** Called only after placement/load/neighbor changes or an explicit interaction. */
    public boolean rebuild() {
        if (world == null || world.isRemote || releasing || ArmTransactions.blocked(this)) return false;
        if(failedPayload!=null||payload!=null&&payload.hasOwnershipJournal()){operational=false;lastError="Attachment snapshot unavailable; original data retained";return false;}
        rebuildDelay = -1;
        GearTileEntity existing=loadedController();
        if(existing!=null&&existing.hasStoredSections()){
            if(existing!=this)return existing.rebuild();
            operational=failedStoredSections==null&&cachedMembersIntact();retryUnavailable=!operational;
            if(!operational)lastError="Load every joined arm before extending";
            return operational;
        }
        if (!GearAssembly.loaded(world, pos) || !GearAssembly.isExtender(world.getBlockState(pos))) return false;
        if(assembly!=null&&assembly.kind()==GearTopology.FootKind.NONE&&payload!=null&&payload.materialized()&&progress==1&&payload.singleLandingMount()!=null&&payload.singleLandingMount()!=mountDirection&&cachedMembersIntact()){lastError="";return true;}
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
        // A controller can become an internal section when another arm is added above it.
        // Preflight every prospective member, not only the new column roots and feet.
        Set<BlockPos> prospective=new HashSet<>();for(BlockPos column:scan.columns)for(int n=0;n<scan.assembly.columnHeight();n++)prospective.add(column.offset(mountDirection,n));
        if(scan.assembly.kind()!=GearTopology.FootKind.NONE)for(GearTopology.Cell foot:scan.assembly.feet())prospective.add(localCell(foot));
        for(BlockPos member:prospective){TileEntity raw=world.getTileEntity(member);if(!(raw instanceof GearTileEntity))continue;GearTileEntity former=((GearTileEntity)raw).loadedController();if(former!=null&&former!=this&&former.hasRetainedPayload()&&(!former.fullyDeployed()||!former.releaseAssembly())){lastError="Restore the existing arm attachment before merging its controller";return false;}}
        if(assembly==null)for(GearTopology.Cell foot:scan.assembly.feet()){TileEntity raw=world.getTileEntity(localCell(foot));GearTileEntity former=raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;if(former!=null&&former!=this){if(!former.fullyDeployed()){lastError="Deploy the connected group before changing its controller";return false;}configure(former.settings(),!former.couplerPassive);couplerPassive=former.couplerPassive;if(couplerPassive){signalKnown=former.signalKnown;lastSignal=former.lastSignal;}break;}}
        boolean genericBefore=assembly!=null&&assembly.kind()==GearTopology.FootKind.NONE;
        GearMotion.Mode previousMode = mode;
        if (isSwingArmController()) {
            if (mode != GearMotion.Mode.ROTATE_LEVEL&&!configured) {
                // A newly placed arm bows toward its fold. Match
                // the visible static block's yaw on its first formation.
                direction = GearMotion.Direction.valueOf(world.getBlockState(pos)
                        .getValue(GearBlock.FACING).name());
            }
            mode = rotatePayload?GearMotion.Mode.ROTATE:GearMotion.Mode.ROTATE_LEVEL;
        }
        else mode = world.getBlockState(pos).getBlock() instanceof GearBlock && ((GearBlock)world.getBlockState(pos).getBlock()).kind()==GearBlock.Kind.MOUNT?GearMotion.Mode.STATIC:GearMotion.Mode.EXTEND;
        EnumFacing scannedFoot=configuredFootFacing==null?scan.footFacing:configuredFootFacing;
        boolean unchanged = assembly != null && assembly.columnHeight() == scan.assembly.columnHeight()
                && assembly.kind() == scan.assembly.kind() && footFacing == scannedFoot
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
            boolean stationary=progress==0||progress==1;
            boolean sameColumns=assembly!=null&&assembly.columnHeight()==scan.assembly.columnHeight()&&columns.equals(relativeColumns(scan.columns));
            boolean footRemesh=sameColumns&&stationary&&payload==null&&failedPayload==null
                    &&(assembly.kind()==GearTopology.FootKind.TRACK&&(scan.assembly.kind()==GearTopology.FootKind.TRACK||trackExtensionStroke&&scan.assembly.kind()==GearTopology.FootKind.NONE)
                    ||formingActiveFoot&&assembly.kind()!=GearTopology.FootKind.NONE&&assembly.kind()==scan.assembly.kind());
            if(footRemesh||formingActiveFoot&&genericBefore&&sameColumns&&scan.assembly.kind()!=GearTopology.FootKind.NONE||formingActiveTrack&&(genericBefore||isExtendedTrackStroke())&&scan.assembly.kind()==GearTopology.FootKind.TRACK&&scan.columns.size()==1&&scan.columns.get(0).equals(pos)){
                // Track editing changes the foot mesh, not the stationary column's pose.
                // Release departed helpers before rebinding the surviving component.
                for(BlockPos member:new ArrayList<>(members))if(!prospective.contains(member)&&GearAssembly.loaded(world,member)){
                    TileEntity old=world.getTileEntity(member);if(old instanceof GearTileEntity&&pos.equals(((GearTileEntity)old).owner))((GearTileEntity)old).clearBinding();
                }
                assembly=null;GearGeometry.remove(this);members.clear();owner=null;
            }
            else{
                for(BlockPos column:scan.columns){TileEntity raw=world.getTileEntity(column);if(raw instanceof GearTileEntity&&((GearTileEntity)raw).assembly!=null&&!((GearTileEntity)raw).fullyDeployed()){lastError="Deploy the connected group before changing its topology";return false;}}
                if(!releaseAssembly())return false;
                progress = previousProgress = 1.0;deployedTarget = configured?desiredTarget:true;
            }
        }
        for(BlockPos column:scan.columns){TileEntity raw=world.getTileEntity(column);if(raw instanceof GearTileEntity&&raw!=this&&((GearTileEntity)raw).assembly!=null&&!((GearTileEntity)raw).releaseAssembly())return false;}
        if(isFixedMount()){deployedTarget=true;progress=previousProgress=1;}
        assembly = scan.assembly;
        if(genericBefore!=(assembly.kind()==GearTopology.FootKind.NONE))signalKnown=false;
        columns.clear();columns.addAll(relativeColumns(scan.columns));
        operational = true;
        footFacing = scannedFoot;sourceFootFacing=scan.footFacing;
        sourceFootComponentFrame=scan.assembly.kind()!=GearTopology.FootKind.NONE&&!scan.assembly.feet().isEmpty()?ArmFootComponentFrame.frame(world,localCell(scan.assembly.feet().get(0))):EnumFacing.DOWN;
        owner = pos.toImmutable();
        rebuildMemberCache();
        lastError = "";
        for (BlockPos member : members) {
            GearTileEntity tile = (GearTileEntity) world.getTileEntity(member);
            tile.owner = pos.toImmutable();
            if (tile != this) {
                tile.direction=direction;tile.middleHinge=middleHinge;tile.hingeMode=hingeMode;tile.configuredKink=configuredKink;tile.pushBlocks=pushBlocks;tile.rotatePayload=rotatePayload;tile.activeAngle=activeAngle;tile.startAngle=startAngle;tile.assembly = null;GearGeometry.remove(tile);
                tile.members.clear();
                tile.rebuildDelay = -1;
                tile.retryUnavailable = false;
                tile.operational = true;
            }
            tile.setAssembled(true);
            tile.sync();
        }
        boxesAt=Double.NaN;GearGeometry.register(this);ArmSurfaceActivity.discover(this);sync();
        return true;
    }

    private List<BlockPos> relativeColumns(List<BlockPos> roots){List<BlockPos> result=new ArrayList<>();for(BlockPos root:roots)result.add(root.subtract(pos));return result;}

    private void rebuildMemberCache() {
        members.clear();
        if (assembly == null) return;
        // setWorld/readFromNBT can run before the loading chunk is published.
        // Looking up a state there must never recursively request that chunk.
        for(BlockPos column:columns)for(int i=0;i<assembly.columnHeight();i++){BlockPos at=pos.add(column).offset(mountDirection,i);if(world!=null&&GearAssembly.loaded(world,at)&&world.getBlockState(at).getBlock() instanceof GearBlock)members.addAll(((GearBlock)world.getBlockState(at).getBlock()).componentCells(world,at));else members.add(at);}
        if(assembly.kind()!=GearTopology.FootKind.NONE)for (GearTopology.Cell foot : assembly.feet()) members.add(localCell(foot));
    }

    /**
     * Detaches only metadata/ownership. No block is destroyed, moved, replaced
     * with air, or force-loaded, including when the controller is broken.
     */
    public boolean releaseAssembly() {
        if (world == null || world.isRemote || releasing || ArmTransactions.blocked(this)) return false;
        GearTileEntity root = loadedController();
        if (root != null && root != this) {
            return root.releaseAssembly();
        }
        if(hasStoredSections()){lastError="Extend the arms before changing their sections or joining";return false;}
        if (assembly == null) {
            if(hasRetainedPayload())return false;
            if (owner != null) clearBinding();
            return true;
        }
        try{if(payload!=null&&!payload.materialized()&&!payload.restore(this,1)){lastError="Clear the attachment construction position before dismantling this arm";operational=false;sync();return false;}}catch(RuntimeException | LinkageError failure){lastError="Attachment restore failed; original snapshot retained";operational=false;sync();return false;}
        if(ArmTransactions.blocked(this))return false;
        payload=null;
        releasing = true;
        NBTTagCompound retainedSettings=settings();boolean retainedPassive=couplerPassive,retainedKnown=signalKnown,retainedSignal=lastSignal;
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
            if (pos.equals(tile.owner)){tile.configure(retainedSettings,!retainedPassive);tile.couplerPassive=retainedPassive;if(retainedPassive){tile.signalKnown=retainedKnown;tile.lastSignal=retainedSignal;}tile.clearBinding();
                // Distant joined columns receive no neighbour event when a
                // section is removed. Re-form them after the removal settles.
                tile.scheduleRebuild();
            }
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
        return true;
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

    public NBTTagCompound settings(){NBTTagCompound tag=new NBTTagCompound();if(landingAnglesVersion>=0)tag.setInteger(com.vandorlabs.gear.core.ArmLandingAngles.CHOICE,landingAnglesVersion);tag.setInteger(ArmResource.CHOICE,resourceTransfer.ordinal());tag.setInteger("Trigger",triggerMode);tag.setInteger("FootFacing",footFacing.getHorizontalIndex());tag.setInteger("Direction",direction.ordinal());tag.setBoolean("Deployed",deployedTarget);tag.setString("Channels",channels.toString());tag.setBoolean("LegacyAnyChannels",legacyAnyChannels);tag.setBoolean("PushBlocks",pushBlocks);tag.setBoolean("RotatePayload",rotatePayload);tag.setBoolean("MiddleHinge",middleHinge);tag.setInteger("HingeMode",hingeMode.ordinal());tag.setInteger("KinkDirection",kinkDirection().ordinal());tag.setBoolean("JoinArms",joinEnabled);tag.setInteger("JoinRange",joinRange);tag.setBoolean("CarryLoad",carryLoad);tag.setInteger("LoadHeight",loadHeight);tag.setDouble("ActiveAngle",configuredEndAngle());tag.setDouble("StartAngle",startAngle());tag.setDouble("ActiveExtension",activeExtension);int trackStyle=com.vandorlabs.gear.track.TrackStyles.style(this);if(trackStyle>=0)tag.setInteger("TrackStyle",trackStyle);return tag;}
    public void configure(NBTTagCompound tag){configure(tag,true);}
    private void configure(NBTTagCompound tag,boolean command){
        if(parkingQuarantine!=null||parkingHoldNeedsGeometry&&!validateParkingHoldInstalled())return;
        ArmResource resource=tag.hasKey(ArmResource.CHOICE)?readResource(tag,ArmResource.CHOICE):resourceTransfer;
        boolean resourceChanged=resource!=resourceTransfer;
        int trigger=tag.getInteger("Trigger"),dir=tag.getInteger("Direction");
        com.vandorlabs.redstone.ChannelList values=com.vandorlabs.redstone.ChannelList.parse(tag.getString("Channels"));
        if(trigger<0||trigger>2||dir<0||dir>3||values==null)throw new IllegalArgumentException("Invalid gear settings");
        int incomingLandingVersion=readLandingAngleVersion(tag,false);boolean explicitAngles=incomingLandingVersion==1;boolean adoptLegacyLanding=isLandingAssembly()&&landingAnglesVersion!=1&&explicitAngles;
        boolean retainLandingAngles=isLandingAssembly()&&!explicitAngles;
        int nextLandingVersion=retainLandingAngles?landingAnglesVersion:incomingLandingVersion>=0?incomingLandingVersion:landingAnglesVersion;
        double requestedStart=retainLandingAngles?startAngle():tag.hasKey("StartAngle",6)?tag.getDouble("StartAngle"):startAngle();
        if(!Double.isFinite(requestedStart)||requestedStart < -180||requestedStart > 180||Math.rint(requestedStart/15)*15!=requestedStart)throw new IllegalArgumentException("Starting angle must use 15 degree steps within -180 to 180");
        boolean changeStart=requestedStart!=(adoptLegacyLanding?0:startAngle());
        boolean installStart=changeStart&&(assembly==null||releasing);
        if(installStart)changeStart=false;
        if(changeStart&&(!isSwingArmController()||!fullyDeployed()||adjustingStart))throw new IllegalArgumentException("Return the Rotating Arm to its starting position before changing its start angle");
        double angle=retainLandingAngles?configuredEndAngle():tag.hasKey("ActiveAngle",6)?tag.getDouble("ActiveAngle"):configuredEndAngle();
        if(angle!=configuredEndAngle()&&Math.rint(angle/15)*15!=angle)throw new IllegalArgumentException("End angle must use 15 degree steps");
        if(adjustingStart){if(angle!=savedEndAngle)throw new IllegalArgumentException("Wait for the starting angle adjustment before changing the end angle");angle=activeAngle;}
        double extension=tag.hasKey("ActiveExtension",6)?tag.getDouble("ActiveExtension"):activeExtension;
        boolean rotate=tag.hasKey("RotatePayload",1)?tag.getBoolean("RotatePayload"):rotatePayload;
        boolean push=tag.hasKey("PushBlocks",1)?tag.getBoolean("PushBlocks"):pushBlocks;
        boolean middle=tag.hasKey("MiddleHinge",1)?tag.getBoolean("MiddleHinge"):middleHinge;
        if(tag.hasKey("HingeMode")&&!tag.hasKey("HingeMode",3)||tag.hasKey("KinkDirection")&&!tag.hasKey("KinkDirection",3))throw new IllegalArgumentException("Malformed hinge settings");
        ArmHinge.Mode hinge=ArmHinge.readChoice(middle,tag.hasKey("HingeMode",3)?tag.getInteger("HingeMode"):null);
        GearMotion.Direction kink=configuredKink;
        if(tag.hasKey("KinkDirection",3)){int value=tag.getInteger("KinkDirection");if(value<0||value>3)throw new IllegalArgumentException("Invalid kink direction");GearMotion.Direction selected=GearMotion.Direction.values()[value];if(configuredKink!=null||selected!=direction||dir!=direction.ordinal())kink=selected;}
        if(isLandingAssembly()&&isSwingArmController()&&nextLandingVersion==1&&!proposedLandingEndpoints(requestedStart,adjustingStart?savedEndAngle:angle,dir,rotate,hinge,kink==null?GearMotion.Direction.values()[dir]:kink).valid())throw new IllegalArgumentException(com.vandorlabs.gear.core.LandingAnglePolicy.ERROR);
        boolean hingeChanged=hinge!=hingeMode||(kink==null?direction:kink)!=kinkDirection();
        if(hingeChanged&&assembly!=null&&(!isSwingArmController()||!fullyDeployed()||physicalStartAngle()!=0||adjustingStart))throw new IllegalArgumentException("Return the Rotating Arm to a zero-degree Start before changing its hinge");
        boolean pushChanged=push!=pushBlocks;
        if(push!=pushBlocks&&(progress!=0&&progress!=1||payload!=null&&!payload.materialized()))throw new IllegalArgumentException("Return to rest before changing pushing");
        boolean join=tag.hasKey("JoinArms",1)?tag.getBoolean("JoinArms"):joinEnabled,load=tag.hasKey("CarryLoad",1)?tag.getBoolean("CarryLoad"):carryLoad;
        int range=tag.hasKey("JoinRange",3)?tag.getInteger("JoinRange"):joinRange,height=tag.hasKey("LoadHeight",3)?tag.getInteger("LoadHeight"):loadHeight;
        if(range<1||range>32||height<1||height>32)throw new IllegalArgumentException("Platform ranges must be from 1 to 32 blocks");
        if(load&&!canCarryLoad())throw new IllegalArgumentException("Carry load requires a vertically moving Extender Arm");
        EnumFacing requestedFoot=tag.hasKey("FootFacing",3)?EnumFacing.getHorizontal(tag.getInteger("FootFacing")):footFacing;
        if(tag.hasKey("FootFacing",3)&&(tag.getInteger("FootFacing")<0||tag.getInteger("FootFacing")>3))throw new IllegalArgumentException("Invalid landing foot direction");
        boolean changeFoot=requestedFoot!=footFacing;
        if(changeFoot&&assembly!=null&&(!canConfigureFootFacing()||!fullyDeployed()))throw new IllegalArgumentException("Deploy the landing gear assembly before changing its direction");
        if(changeFoot&&assembly!=null&&world!=null&&!(world instanceof com.vandorlabs.vehicle.VehicleWorld)){
            EnumFacing previousFoot=footFacing;footFacing=requestedFoot;
            try{for(AxisAlignedBB box:GearGeometry.computeBoxes(this,progress))if(!GearGeometry.foreign(world,this,box.shrink(.0001)).isEmpty())throw new IllegalArgumentException("The selected landing foot direction is obstructed");}
            finally{footFacing=previousFoot;}
        }
        boolean groupChanged=nextLandingVersion!=landingAnglesVersion||hingeChanged||join!=joinEnabled||range!=joinRange||load!=carryLoad||height!=loadHeight||angle!=activeAngle||rotate!=rotatePayload||dir!=direction.ordinal();
        if(groupChanged&&!fullyDeployed())throw new IllegalArgumentException("Extend fully before changing joined platform settings");
        if(!Double.isFinite(angle)||angle < -180||angle > 180||!Double.isFinite(extension)||extension < -8||extension > 8)throw new IllegalArgumentException("Arm stroke is out of range");
        if((dir!=direction.ordinal()||angle!=activeAngle||rotate!=rotatePayload)&&!fullyDeployed())throw new IllegalArgumentException("Deploy fully before changing the fold direction");
        if(tag.hasKey("TrackStyle",3)){if(assembly!=null&&!fullyDeployed()&&com.vandorlabs.gear.track.TrackStyles.style(this)!=tag.getInteger("TrackStyle"))throw new IllegalArgumentException("Deploy fully before changing track style");com.vandorlabs.gear.track.TrackStyles.apply(this,tag.getInteger("TrackStyle"));}
        // An unformed/releasing arm accepts its starting angle directly, but a
        // later validation failure must still leave every setting unchanged.
        boolean commandChanged=hingeChanged||changeFoot||triggerMode!=trigger||!channels.equals(values)||dir!=direction.ordinal()||push!=pushBlocks||join!=joinEnabled||range!=joinRange||load!=carryLoad||height!=loadHeight||rotate!=rotatePayload||angle!=activeAngle||extension!=activeExtension||requestedStart!=startAngle()||tag.hasKey("LegacyAnyChannels",1)&&legacyAnyChannels!=tag.getBoolean("LegacyAnyChannels")||trigger==0&&deployedTarget!=(isFixedMount()||tag.getBoolean("Deployed"));
        if(command&&commandChanged)clearCouplerPassive();
        if(adoptLegacyLanding)startAngle=0;
        if(installStart)startAngle=requestedStart;
        landingAnglesVersion=nextLandingVersion;
        if(changeFoot){configuredFootFacing=requestedFoot;footFacing=requestedFoot;}
        if(triggerMode!=trigger||!channels.equals(values))signalKnown=false;if(tag.hasKey("LegacyAnyChannels",1)&&legacyAnyChannels!=tag.getBoolean("LegacyAnyChannels")){legacyAnyChannels=tag.getBoolean("LegacyAnyChannels");signalKnown=false;}releaseParkingHold();configured=true;middleHinge=middle;hingeMode=hinge;configuredKink=kink;pushBlocks=push;resourceTransfer=resource;joinEnabled=join;joinRange=range;carryLoad=load;loadHeight=height;rotatePayload=rotate;activeAngle=angle;activeExtension=extension;if(changeStart){savedEndAngle=angle;activeAngle=requestedStart;adjustingStart=true;}if(isSwingArmController())mode=rotate?GearMotion.Mode.ROTATE:GearMotion.Mode.ROTATE_LEVEL;triggerMode=trigger;channels=values;direction=GearMotion.Direction.values()[dir];boxesAt=Double.NaN;if(triggerMode==0)deployedTarget=isFixedMount()||tag.getBoolean("Deployed");if(changeStart)deployedTarget=false;if(command)input();sync();if(world!=null)com.vandorlabs.tiles.LandingGearCovers.refresh(world,pos);
        if((groupChanged||pushChanged)&&world!=null&&!world.isRemote){for(BlockPos column:new ArrayList<>(columns)){TileEntity raw=world.getTileEntity(pos.add(column));if(raw instanceof GearTileEntity&&raw!=this){GearTileEntity member=(GearTileEntity)raw;member.joinEnabled=join;member.joinRange=range;member.carryLoad=load;member.loadHeight=height;member.direction=direction;member.middleHinge=middleHinge;member.hingeMode=hingeMode;member.configuredKink=configuredKink;member.pushBlocks=pushBlocks;member.rotatePayload=rotatePayload;member.activeAngle=activeAngle;member.startAngle=startAngle;member.landingAnglesVersion=landingAnglesVersion;member.activeExtension=activeExtension;member.mode=mode;member.sync();}}if(groupChanged)scheduleRebuild();}
        if(resourceChanged&&world instanceof net.minecraft.world.WorldServer)for(BlockPos column:columns){
            BlockPos at=pos.add(column);
            if(!GearAssembly.loaded(world,at))continue;
            TileEntity raw=world.getTileEntity(at);
            if(raw instanceof GearTileEntity&&raw!=this&&pos.equals(((GearTileEntity)raw).owner)){
                GearTileEntity member=(GearTileEntity)raw;
                member.resourceTransfer=resource;
                member.sync();
            }
            world.notifyNeighborsOfStateChange(at,world.getBlockState(at).getBlock(),false);
        }
    }

    public void sync() {
        rememberSurfaceEndpoint();ArmSurfaceActivity.refresh(this);
        clearPoses();if(payload!=null)GearGeometry.register(this);
        markDirty();
        if (world != null && !world.isRemote && GearAssembly.loaded(world, pos)) {
            motionRevision++;
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {if(parkingQuarantine!=null)return exactInto(tag,parkingQuarantine);if(parkingOriginal!=null)return exactInto(tag,currentData(parkingOriginal));super.writeToNBT(tag);return writeOwnData(tag);}
    /** Merge only known arm fields into the retained physical snapshot; never serialize its capabilities. */
    public NBTTagCompound currentData(NBTTagCompound original){
        if(parkingQuarantine!=null)return parkingQuarantine.copy();
        net.minecraft.util.ResourceLocation expected=TileEntity.getKey(GearTileEntity.class);if(original==null||expected==null||!expected.toString().equals(original.getString("id")))throw new IllegalArgumentException("Original arm identity unavailable");
        NBTTagCompound result=original.copy();for(String key:new String[]{"GearParkingHold","GearCouplerPassive","ArmOperatorMost","ArmOperatorLeast","ArmStoredSections","ArmPayload",GearTaxiState.KEY,"ArmJoinEnabled","ArmJoinRange","ArmCarryLoad","ArmLoadHeight","TrackRetractingStroke","ArmRetractingStroke","GearOwnerOffset","GearHeight","GearKind","GearFootFacing","GearSourceFootFacing","GearFeet","GearTrackContact"})result.removeTag(key);
        result.setInteger("x",pos.getX());result.setInteger("y",pos.getY());result.setInteger("z",pos.getZ());return writeOwnData(result);
    }
    /** Small current pose projection; retained payloads and capability trees are deliberately absent. */
    public NBTTagCompound currentPoseData(){
        NBTTagCompound tag=new NBTTagCompound();if(couplerPassive)tag.setBoolean("GearCouplerPassive",true);if(parkingHold)tag.setBoolean("GearParkingHold",true);tag.setInteger("GearRevision",motionRevision);tag.setBoolean("GearBlocked",motionBlocked);tag.setBoolean("GearTargetDeployed",deployedTarget);tag.setBoolean("GearSignalKnown",signalKnown);tag.setBoolean("GearLastSignal",lastSignal);tag.setDouble("GearProgress",progress);
        rememberSurfaceEndpoint();if(surfaceDepartureSaved&&progress>0&&progress<1)tag.setBoolean("SurfaceDepartureActive",surfaceDepartureActive);return tag;
    }
    private NBTTagCompound writeOwnData(NBTTagCompound tag){
        if(componentFrameExplicit)tag.setString(ArmFootComponentFrame.KEY,componentFrame.name());else ArmFootComponentFrame.write(tag,componentFrame);
        if(assembly!=null&&assembly.kind()!=GearTopology.FootKind.NONE&&sourceFootComponentFrame!=EnumFacing.DOWN)tag.setString(ArmFootComponentFrame.SOURCE_KEY,sourceFootComponentFrame.name());else tag.removeTag(ArmFootComponentFrame.SOURCE_KEY);
        if(couplerPassive)tag.setBoolean("GearCouplerPassive",true);else tag.removeTag("GearCouplerPassive");
        if(parkingHold)tag.setBoolean("GearParkingHold",true);else tag.removeTag("GearParkingHold");
        if(operator!=null)tag.setUniqueId("ArmOperator",operator);
        if(failedStoredSections!=null)tag.setTag("ArmStoredSections",failedStoredSections.copy());else if(storedSections!=null)tag.setTag("ArmStoredSections",storedSections.write());
        if(failedPayload!=null)tag.setTag("ArmPayload",failedPayload.copy());else if(payload!=null)tag.setTag("ArmPayload",payload.write());
        if(taxiState!=null)tag.setTag(GearTaxiState.KEY,taxiState.copy());
        if(resourceTransfer!=ArmResource.DISABLED)tag.setInteger(ArmResource.SAVED,resourceTransfer.ordinal());
        else tag.removeTag(ArmResource.SAVED);
        tag.setInteger("GearRevision",motionRevision);
        if(configuredFootFacing!=null)tag.setInteger("ArmFootFacing",configuredFootFacing.getHorizontalIndex());else tag.removeTag("ArmFootFacing");
        if(joinedSettingsPresent||joinEnabled||carryLoad||joinRange!=32||loadHeight!=1){tag.setBoolean("ArmJoinEnabled",joinEnabled);tag.setInteger("ArmJoinRange",joinRange);tag.setBoolean("ArmCarryLoad",carryLoad);tag.setInteger("ArmLoadHeight",loadHeight);}
        if(landingAnglesVersion>=0)tag.setInteger(com.vandorlabs.gear.core.ArmLandingAngles.SAVED,landingAnglesVersion);else tag.removeTag(com.vandorlabs.gear.core.ArmLandingAngles.SAVED);tag.setBoolean("TrackExtensionStroke",trackExtensionStroke);if(trackRetractingStroke)tag.setBoolean("TrackRetractingStroke",true);if(armRetractingStroke)tag.setBoolean("ArmRetractingStroke",true);tag.setString("ArmMountDirection",mountDirection.name());if(pushBlocks)tag.setBoolean("ArmPushBlocks",true);else tag.removeTag("ArmPushBlocks");tag.setBoolean("ArmRotatePayload",rotatePayload);if(middleHinge)tag.setBoolean("ArmMiddleHinge",true);else tag.removeTag("ArmMiddleHinge");if(hingeMode==ArmHinge.Mode.DOUBLE)tag.setInteger("ArmHingeMode",hingeMode.ordinal());else tag.removeTag("ArmHingeMode");if(configuredKink!=null)tag.setString("ArmKinkDirection",configuredKink.name());else tag.removeTag("ArmKinkDirection");tag.setDouble("ArmActiveAngle",activeAngle);if(startAngle!=0)tag.setDouble("ArmStartAngle",startAngle);else tag.removeTag("ArmStartAngle");if(adjustingStart){tag.setBoolean("ArmAdjustingStart",true);tag.setDouble("ArmSavedEndAngle",savedEndAngle);}else{tag.removeTag("ArmAdjustingStart");tag.removeTag("ArmSavedEndAngle");}tag.setDouble("ArmActiveExtension",activeExtension);
        if (owner != null) tag.setLong("GearOwnerOffset", owner.subtract(pos).toLong());
        tag.setInteger("GearTrigger",triggerMode);tag.setString("GearChannels",channels.toString());
        NBTTagList columnList=new NBTTagList();
        // The reader's legacy empty-list fallback is the controller's own column.
        // Emit that canonical value even before the first assembly has formed.
        for(BlockPos column:columns.isEmpty()?java.util.Collections.singletonList(BlockPos.ORIGIN):columns)columnList.appendTag(new NBTTagIntArray(new int[]{column.getX(),column.getY(),column.getZ()}));tag.setTag("GearColumns",columnList);
        tag.setBoolean("GearConfigured",configured);tag.setBoolean("GearBlocked",motionBlocked);tag.setString("GearError",lastError==null?"":lastError);
        tag.setString("GearMode", mode.name());
        tag.setString("GearDirection", direction.name());
        tag.setBoolean("GearTargetDeployed", deployedTarget);tag.setBoolean("GearSignalKnown",signalKnown);tag.setBoolean("GearLastSignal",lastSignal);tag.setBoolean("GearLegacyAnyChannels",legacyAnyChannels);
        tag.setDouble("GearProgress", progress);
        rememberSurfaceEndpoint();if(surfaceDepartureSaved&&progress>0&&progress<1)tag.setBoolean("SurfaceDepartureActive",surfaceDepartureActive);else tag.removeTag("SurfaceDepartureActive");
        tag.setBoolean("GearOperational", operational);
        tag.setBoolean("GearController", assembly != null);
        if (assembly != null) {
            tag.setInteger("GearHeight", assembly.columnHeight());
            tag.setString("GearKind", assembly.kind().name());
            tag.setInteger("GearFootFacing", footFacing.getHorizontalIndex());
            if(assembly.kind()!=GearTopology.FootKind.NONE&&(sourceFootFacingExplicit||sourceFootFacing!=footFacing))tag.setInteger("GearSourceFootFacing",sourceFootFacing.getHorizontalIndex());else tag.removeTag("GearSourceFootFacing");
            NBTTagList feet = new NBTTagList();
            for (GearTopology.Cell foot : assembly.feet()) {
                feet.appendTag(new NBTTagIntArray(new int[] { foot.x, foot.y, foot.z }));
            }
            tag.setTag("GearFeet", feet);
            GearTopology.Cell contact=assembly.anchorFoot();if(contact.x!=0||contact.z!=0)tag.setIntArray("GearTrackContact",new int[]{contact.x,contact.y,contact.z});else tag.removeTag("GearTrackContact");
        }
        return tag;
    }

    private static ArmHinge.Mode readHinge(NBTTagCompound tag){
        if(tag.hasKey("ArmHingeMode")&&!tag.hasKey("ArmHingeMode",3)||tag.hasKey("ArmKinkDirection")&&!tag.hasKey("ArmKinkDirection",8))throw new IllegalArgumentException("Malformed saved hinge settings");
        if(tag.hasKey("ArmKinkDirection",8))GearMotion.Direction.valueOf(tag.getString("ArmKinkDirection"));
        return ArmHinge.readSaved(tag.getBoolean("ArmMiddleHinge"),tag.hasKey("ArmHingeMode",3)?tag.getInteger("ArmHingeMode"):null);
    }
    @Override
    public void readFromNBT(NBTTagCompound tag) {
        if(parkingQuarantine!=null){lastError="Incoming arm snapshot refused; retained parking recovery requires replacement";return;}
        try{readHinge(tag);readLandingAngleVersion(tag,true);}catch(IllegalArgumentException invalid){if(payload!=null||storedSections!=null||taxiState!=null){lastError="Incoming hinge settings refused; prior ownership retained";return;}quarantineParking(tag);return;}
        if(!ArmFootComponentFrame.valid(tag)){if(payload!=null||storedSections!=null||taxiState!=null){lastError="Incoming component frame refused; prior ownership retained";return;}quarantineParking(tag);return;}
        if(tag.hasKey("GearCouplerPassive")&&(!tag.hasKey("GearCouplerPassive",1)||tag.getByte("GearCouplerPassive")<0||tag.getByte("GearCouplerPassive")>1)){
            if(payload!=null||storedSections!=null||taxiState!=null){lastError="Incoming passive arm state refused; prior ownership retained";return;}
            quarantineParking(tag);return;
        }
        boolean heldIncoming=tag.hasKey("GearParkingHold");
        if(!heldIncoming){parkingHold=false;parkingHoldNeedsGeometry=false;parkingSignalBaseline=false;parkingOriginal=null;parkingQuarantine=null;readGearData(tag);return;}
        NBTTagCompound previous=(payload!=null||storedSections!=null||parkingQuarantine!=null)?writeToNBT(new NBTTagCompound()):null;
        NBTTagCompound original=tag.copy();
        try{
            if(!tag.hasKey("GearParkingHold",1))throw new IllegalArgumentException("Invalid parking hold type");
            if(tag.getBoolean("GearParkingHold")&&(!tag.hasKey("GearProgress",6)||!tag.hasKey("GearRevision",3)||!tag.hasKey("GearTargetDeployed",1)||!tag.hasKey("GearController",1)||!tag.getBoolean("GearController")||!tag.hasKey("GearOperational",1)||!tag.getBoolean("GearOperational")||!tag.hasKey("GearMode",8)||tag.hasKey("ArmStoredSections")&&!tag.hasKey("ArmStoredSections",10)))throw new IllegalArgumentException("Incomplete parking hold envelope");
            parkingQuarantine=null;parkingHold=false;readGearData(tag);
            if(tag.getBoolean("GearParkingHold")){
                if(!operational||mode==GearMotion.Mode.STATIC||assembly==null||assembly.kind()!=GearTopology.FootKind.NONE||payload==null||payload.hasOwnershipJournal()||hasFailedArmSnapshot()||taxiState!=null||adjustingStart||!Double.isFinite(tag.getDouble("GearProgress"))||tag.getDouble("GearProgress")<0||tag.getDouble("GearProgress")>1)throw new IllegalArgumentException("Incompatible parking hold");
                parkingHold=true;parkingHoldNeedsGeometry=true;parkingOriginal=original;parkingSignalBaseline=true;previousProgress=progress;
            }else{parkingOriginal=null;parkingHoldNeedsGeometry=false;parkingSignalBaseline=false;}
        }catch(RuntimeException|LinkageError failure){
            if(previous!=null){parkingQuarantine=null;parkingHold=false;parkingOriginal=null;parkingHoldNeedsGeometry=false;readFromNBT(previous);lastError="Incoming parking snapshot refused; prior owned attachment retained";}
            else quarantineParking(original);
        }
    }
    public ArmCoreRenderTransfer coreRenderTransfer;public Object coreVisualHandoff;
    private void readGearData(NBTTagCompound tag) {
        ArmPayload previousVisualPayload=payload;
        com.vandorlabs.tiles.WorldActionAuthorization.reading(this);super.readFromNBT(tag);
        storedSections=null;failedStoredSections=null;if(tag.hasKey("ArmStoredSections",10))try{storedSections=ArmStoredSections.read(tag.getCompoundTag("ArmStoredSections"));}catch(RuntimeException|LinkageError invalid){failedStoredSections=tag.getCompoundTag("ArmStoredSections").copy();}
        clearPoses();
        operator=tag.hasUniqueId("ArmOperator")?tag.getUniqueId("ArmOperator"):null;
        payload=null;failedPayload=null;
        if(tag.hasKey("ArmPayload",10))try{payload=ArmPayload.read(tag.getCompoundTag("ArmPayload"));}catch(RuntimeException | LinkageError failure){failedPayload=tag.getCompoundTag("ArmPayload").copy();}
        motionRevision=tag.getInteger("GearRevision");lastMotionTick=Long.MIN_VALUE;
        mountDirection=parseEnum(EnumFacing.class,tag.getString("ArmMountDirection"),EnumFacing.DOWN);
        componentFrame=tag.hasKey(ArmFootComponentFrame.KEY,8)?EnumFacing.valueOf(tag.getString(ArmFootComponentFrame.KEY)):EnumFacing.DOWN;
        componentFrameExplicit=tag.hasKey(ArmFootComponentFrame.KEY,8);
        sourceFootComponentFrame=tag.hasKey(ArmFootComponentFrame.SOURCE_KEY,8)?EnumFacing.valueOf(tag.getString(ArmFootComponentFrame.SOURCE_KEY)):EnumFacing.DOWN;
        configuredFootFacing=tag.hasKey("ArmFootFacing",3)?EnumFacing.getHorizontal(tag.getInteger("ArmFootFacing")):null;
        resourceTransfer=readResource(tag,ArmResource.SAVED);
        joinedSettingsPresent=tag.hasKey("ArmJoinEnabled")||tag.hasKey("ArmJoinRange")||tag.hasKey("ArmCarryLoad")||tag.hasKey("ArmLoadHeight");
        joinEnabled=tag.getBoolean("ArmJoinEnabled");joinRange=tag.hasKey("ArmJoinRange",3)?Math.max(1,Math.min(32,tag.getInteger("ArmJoinRange"))):32;carryLoad=tag.getBoolean("ArmCarryLoad");loadHeight=tag.hasKey("ArmLoadHeight",3)?Math.max(1,Math.min(32,tag.getInteger("ArmLoadHeight"))):1;
        middleHinge=tag.getBoolean("ArmMiddleHinge");hingeMode=readHinge(tag);configuredKink=tag.hasKey("ArmKinkDirection",8)?GearMotion.Direction.valueOf(tag.getString("ArmKinkDirection")):null;pushBlocks=tag.getBoolean("ArmPushBlocks");rotatePayload=tag.getBoolean("ArmRotatePayload");trackExtensionStroke=tag.getBoolean("TrackExtensionStroke");trackRetractingStroke=tag.getBoolean("TrackRetractingStroke");armRetractingStroke=tag.getBoolean("ArmRetractingStroke");
        landingAnglesVersion=readLandingAngleVersion(tag,true);
        startAngle=tag.hasKey("ArmStartAngle",6)?tag.getDouble("ArmStartAngle"):0;
        adjustingStart=tag.getBoolean("ArmAdjustingStart");savedEndAngle=tag.getDouble("ArmSavedEndAngle");
        if(!Double.isFinite(startAngle)||Math.abs(startAngle)>180||adjustingStart&&(!Double.isFinite(savedEndAngle)||Math.abs(savedEndAngle)>180))throw new IllegalArgumentException("Invalid saved rotating-arm endpoints");
        activeAngle=tag.hasKey("ArmActiveAngle",6)&&Double.isFinite(tag.getDouble("ArmActiveAngle"))?Math.max(-180,Math.min(180,tag.getDouble("ArmActiveAngle"))):90;
        activeExtension=tag.hasKey("ArmActiveExtension",6)&&Double.isFinite(tag.getDouble("ArmActiveExtension"))?Math.max(-8,Math.min(8,tag.getDouble("ArmActiveExtension"))):1;
        configured=tag.getBoolean("GearConfigured");motionBlocked=tag.getBoolean("GearBlocked");lastError=tag.getString("GearError");if(lastError.length()>512)lastError=lastError.substring(0,512);boxesAt=Double.NaN;
        owner=tag.hasKey("GearOwnerOffset",4)?pos.add(BlockPos.fromLong(tag.getLong("GearOwnerOffset"))):null;
        triggerMode=Math.max(0,Math.min(2,tag.getInteger("GearTrigger")));com.vandorlabs.redstone.ChannelList read=com.vandorlabs.redstone.ChannelList.parse(tag.getString("GearChannels"));channels=read==null?com.vandorlabs.redstone.ChannelList.EMPTY:read;
        columns.clear();NBTTagList columnList=tag.getTagList("GearColumns",11);if(columnList.tagCount()<=64)for(int i=0;i<columnList.tagCount();i++){int[] xyz=columnList.getIntArrayAt(i);if(xyz.length==3&&Math.abs((long)xyz[0])<=64&&Math.abs((long)xyz[1])<=64&&Math.abs((long)xyz[2])<=64)columns.add(new BlockPos(xyz[0],xyz[1],xyz[2]));}
        if(columns.isEmpty())columns.add(BlockPos.ORIGIN);
        mode = parseEnum(GearMotion.Mode.class, tag.getString("GearMode"), GearMotion.Mode.EXTEND);
        direction = parseEnum(GearMotion.Direction.class, tag.getString("GearDirection"), GearMotion.Direction.NORTH);
        couplerPassive=tag.getBoolean("GearCouplerPassive");
        deployedTarget = !tag.hasKey("GearTargetDeployed") || tag.getBoolean("GearTargetDeployed");signalKnown=tag.getBoolean("GearSignalKnown");lastSignal=tag.getBoolean("GearLastSignal");legacyAnyChannels=tag.getBoolean("GearLegacyAnyChannels");
        double savedProgress = tag.hasKey("GearProgress") ? tag.getDouble("GearProgress") : 1.0;
        progress = previousProgress = Double.isFinite(savedProgress) ? Math.max(0, Math.min(1, savedProgress)) : 1.0;
        surfaceDepartureTarget=deployedTarget;surfaceDepartureSaved=tag.hasKey("SurfaceDepartureActive",1);surfaceDepartureActive=surfaceDepartureSaved?tag.getBoolean("SurfaceDepartureActive"):progress>=1||progress>0&&!deployedTarget;rememberSurfaceEndpoint();
        operational = !tag.hasKey("GearOperational") || tag.getBoolean("GearOperational");
        assembly = null;sourceFootFacing=EnumFacing.NORTH;sourceFootFacingExplicit=tag.hasKey("GearSourceFootFacing",3);
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
                int[] contact=tag.getIntArray("GearTrackContact");GearTopology.Result result=kind==GearTopology.FootKind.TRACK&&contact.length==3?GearTopology.validateTrackContact(tag.getInteger("GearHeight"),feet,new GearTopology.Cell(contact[0],contact[1],contact[2])):GearTopology.validate(tag.getInteger("GearHeight"),kind,feet);
                if (result.isValid() && feet.size() == rawFeet.tagCount()) {
                    assembly = result.assembly();
                    owner = pos.toImmutable();
                    footFacing = EnumFacing.getHorizontal(tag.getInteger("GearFootFacing") & 3);
                    sourceFootFacing=tag.hasKey("GearSourceFootFacing",3)?EnumFacing.getHorizontal(tag.getInteger("GearSourceFootFacing")):footFacing;
                }
            }
        }
        taxiClearPending=false;taxiState=tag.hasKey(GearTaxiState.KEY,10)?tag.getCompoundTag(GearTaxiState.KEY).copy():null;
        if(taxiState!=null)try{validateTaxiState(taxiState);}catch(RuntimeException invalid){operational=false;lastError="Taxi arm recovery data retained: "+invalid.getMessage();}
        if(failedStoredSections!=null||failedPayload!=null||payload!=null&&payload.hasOwnershipJournal())operational=false;
        rebuildMemberCache();GearGeometry.register(this);if(world!=null&&world.isRemote)clientGeometryPending=true;
        if(world!=null&&world.isRemote&&com.vandorlabs.VandorLabs.proxy!=null)com.vandorlabs.VandorLabs.proxy.armPayloadAppearanceChanged(this,previousVisualPayload);
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value, T fallback) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException exception) { return fallback; }
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        if(parkingQuarantine==null){NBTTagCompound tag=writeToNBT(new NBTTagCompound());if(coreRenderTransfer!=null)tag.setTag(ArmCoreRenderTransfer.KEY,coreRenderTransfer.write());return tag;}
        NBTTagCompound shell=new NBTTagCompound();net.minecraft.util.ResourceLocation id=TileEntity.getKey(GearTileEntity.class);if(id!=null)shell.setString("id",id.toString());shell.setInteger("x",pos.getX());shell.setInteger("y",pos.getY());shell.setInteger("z",pos.getZ());shell.setBoolean("GearOperational",false);shell.setBoolean("GearController",false);shell.setString("GearError","Arm parking snapshot retained for recovery");return shell;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) { readFromNBT(tag);readCoreRenderTransfer(tag);clientUpdates++; }

    private void readCoreRenderTransfer(NBTTagCompound tag){coreRenderTransfer=tag.hasKey(ArmCoreRenderTransfer.KEY,10)?ArmCoreRenderTransfer.read(tag.getCompoundTag(ArmCoreRenderTransfer.KEY),payload,pos):null;if(com.vandorlabs.VandorLabs.proxy!=null)com.vandorlabs.VandorLabs.proxy.coreRenderTransferChanged(this);}

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }

    @Override
    public void onDataPacket(NetworkManager manager, SPacketUpdateTileEntity packet) {
        double previous=progress;
        readFromNBT(packet.getNbtCompound());readCoreRenderTransfer(packet.getNbtCompound());clientUpdates++;
        previousProgress=previous;
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    @Override
    public boolean hasFastRenderer() { return payload==null; }

    @Override
    public double getMaxRenderDistanceSquared() { return 256.0 * 256.0; }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        if (assembly == null) {
            if(armSize()==2&&world!=null&&componentFrame!=EnumFacing.DOWN){java.util.List<AxisAlignedBB> loose=GearGeometry.looseWheelBoxes(this);if(!loose.isEmpty()){AxisAlignedBB extent=new AxisAlignedBB(pos);for(AxisAlignedBB box:loose)extent=extent.union(box);return extent;}}
            if(armSize()==2&&world!=null){AxisAlignedBB extent=new AxisAlignedBB(pos);GearBlock block=(GearBlock)world.getBlockState(pos).getBlock();for(BlockPos cell:block.componentCells(world,pos))extent=extent.union(new AxisAlignedBB(cell));extent=extent.grow(block.kind()==GearBlock.Kind.WHEEL?.5:.125,0,block.kind()==GearBlock.Kind.WHEEL?.5:.125);return isSwingArmController()||block.kind()==GearBlock.Kind.WHEEL?extent.union(extent.offset(0,-1,0)):extent;}
            // The unformed swing arm already reaches into the foot cell.
            return isSwingArmController()
                    ? new AxisAlignedBB(pos.getX(), pos.getY() - 1, pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)
                    : new AxisAlignedBB(pos);
        }
        double rx = Math.max(Math.abs(assembly.minX() - 0.5), Math.abs(assembly.maxX() + 0.5));
        double rz = Math.max(Math.abs(assembly.minZ() - 0.5), Math.abs(assembly.maxZ() + 0.5));
        double ry = assembly.columnHeight() + GearMotion.PIVOT_Y;
        if((assembly.kind()==GearTopology.FootKind.NONE||isExtendedTrackStroke())&&!isSwingArmController()&&!isFixedMount())ry+=Math.max(0,activeExtension());
        double radius = Math.sqrt(rx * rx + ry * ry + rz * rz) + 1.0;
        if(payload!=null){AxisAlignedBB b=payload.blocks.bounds;double px=Math.max(Math.abs(payload.offset.getX()-.5),Math.abs(payload.offset.getX()+b.maxX-.5)),py=Math.max(Math.abs(payload.offset.getY()-.5),Math.abs(payload.offset.getY()+b.maxY-.5)),pz=Math.max(Math.abs(payload.offset.getZ()-.5),Math.abs(payload.offset.getZ()+b.maxZ-.5));radius=Math.max(radius,Math.sqrt(px*px+py*py+pz*pz)+assembly.columnHeight()+Math.abs(activeExtension())+2);}

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
