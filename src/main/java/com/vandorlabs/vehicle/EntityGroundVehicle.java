package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockPilotSeat;
import com.vandorlabs.entity.EntityChairSeat;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.physics.FlightEnvelope;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** One persistent, ground craft. All movement decisions are server authoritative. */
public final class EntityGroundVehicle extends Entity implements net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData {
    private static final net.minecraft.network.datasync.DataParameter<Boolean> TRANSFERRING=
            net.minecraft.network.datasync.EntityDataManager.createKey(EntityGroundVehicle.class,net.minecraft.network.datasync.DataSerializers.BOOLEAN);
    private static final net.minecraft.network.datasync.DataParameter<Boolean> HOVER_EXHAUST=
            net.minecraft.network.datasync.EntityDataManager.createKey(EntityGroundVehicle.class,net.minecraft.network.datasync.DataSerializers.BOOLEAN);
    private static final net.minecraft.network.datasync.DataParameter<NBTTagCompound> PILOT_SEATS=
            net.minecraft.network.datasync.EntityDataManager.createKey(EntityGroundVehicle.class,net.minecraft.network.datasync.DataSerializers.COMPOUND_TAG);
    NBTTagCompound occupiedSeatAssignments(){return dataManager.get(PILOT_SEATS).copy();}
    void restoreSeatAssignments(NBTTagCompound assignments){dataManager.set(PILOT_SEATS,assignments.copy());}
    public BlockPos seatForPassenger(Entity passenger){
        NBTTagCompound seats=dataManager.get(PILOT_SEATS);String key=passenger.getUniqueID().toString();
        return seats.hasKey(key,4)?BlockPos.fromLong(seats.getLong(key)):structure.seat;
    }
    void assignSeat(Entity passenger,BlockPos seat){
        NBTTagCompound assignments=dataManager.get(PILOT_SEATS).copy();String key=passenger.getUniqueID().toString();
        if(seat==null)assignments.removeTag(key);else assignments.setLong(key,seat.toLong());
        dataManager.set(PILOT_SEATS,assignments);
    }
    public final VehicleDiagnostics diagnostics=new VehicleDiagnostics();
    private final VehicleSteering taxiSteering=new VehicleSteering();
    VehicleSteering.Profile taxiSteeringProfile(){return taxiSteering.profile(view,articulation.active()?articulation.rootCells():null);}
    public VehicleStructure structure;
    public VehicleWorld view;
    public VehicleCollision collision;
    public final VehicleArticulation articulation=new VehicleArticulation(this);
    public final VehicleCollisionCache collisionCache=new VehicleCollisionCache();
    public final VehicleSignals signals=new VehicleSignals(this);
    public long transferEpoch;
    // Entity provenance is outside portable block snapshots and cannot be copied by the Copy Controller.
    java.util.UUID couplingActor;
    public java.util.UUID couplingActor(){return couplingActor;}
    void recordCouplingActor(net.minecraft.entity.player.EntityPlayer actor){if(actor==null)return;if(world.isRemote||actor.world!=world||actor.isDead||actor.isSpectator())throw new IllegalArgumentException("Coupling authorizer must be present in this world");if(actor.getUniqueID().equals(couplingActor))return;couplingActor=actor.getUniqueID();if(isAddedToWorld()&&world.isBlockLoaded(getPosition(),false))world.getChunkFromBlockCoords(getPosition()).markDirty();}
    private void recordCurrentCouplingDriver(){if(world.isRemote||transferInProgress||ownershipBlocked)return;Entity driver=getControllingPassenger();if(driver instanceof EntityPlayerMP&&!driver.isDead&&!((EntityPlayerMP)driver).isSpectator()&&driver.world==world)recordCouplingActor((EntityPlayerMP)driver);}
    public void authorizeCouplingGui(net.minecraft.entity.player.EntityPlayerMP actor,com.vandorlabs.coupler.TileCoupler tile){
        if(tile==null||view==null||tile.getWorld()!=view||view.getTileEntity(tile.getPos())!=tile||!new com.vandorlabs.coupler.ContainerCoupler(tile).canInteractWith(actor))throw new IllegalArgumentException("Open this vehicle's authorized Coupler dialog to enable automation");
        recordCouplingActor(actor);
    }
    public boolean ownershipBlocked;
    public boolean transferInProgress;
    public void setTransferInProgress(boolean value){
        if(value&&!transferInProgress&&world!=null&&!world.isRemote)VehicleChests.close(this);
        transferInProgress=value;dataManager.set(TRANSFERRING,value);
        if(value){pendingInputs.clear();serverInputs.clear();nextPrediction=null;motionX=motionY=motionZ=0;}
    }
    @Override public void notifyDataManagerChange(net.minecraft.network.datasync.DataParameter<?> key){
        super.notifyDataManagerChange(key);
        if(TRANSFERRING.equals(key)){transferInProgress=dataManager.get(TRANSFERRING);if(transferInProgress){pendingInputs.clear();nextPrediction=null;motionX=motionY=motionZ=0;}}
    }
    public boolean taxiReturnPending;
    public java.util.UUID taxiReturnActor;
    public BlockPos taxiReturnSeat;
    public long taxiNextAttempt;
    public long taxiNextWarning;
    private byte forward, strafe;
    private boolean brake;
    private long lastInput=-100;
    private int interpolation;
    private final VehicleControls serverInputs=new VehicleControls();
    private Control nextPrediction;
    private long lastServerTick=Long.MIN_VALUE;
    private VehiclePose previousVisual,currentVisual;
    private float correctionYaw;
    private int lastAcknowledgement=-1;
    private double targetX,targetY,targetZ;
    private float targetYaw;
    boolean loadedTaxiSupportCheck;
    public double driveSpeed;
    public float steering;
    public boolean localDriver;
    public boolean hover;
    public double hoverTarget;
    private static final int NO_FLIGHT=0, TAKEOFF_ROLL=1, POWERED_FLIGHT=2, RECOVER_FLIGHT=3, RETURN_TAXI=4, RETURN_HOVER=5;
    private int flightPhase;
    private boolean landedFromFlight;
    private boolean cruisingRecovery;
    private int initialClimb;
    private float flightPitch,targetPitch;
    private final VehicleFlightReadout.Sampler flightSampler=new VehicleFlightReadout.Sampler();
    private VehicleFlightReadout flightReadout=VehicleFlightReadout.EMPTY;
    private final VehicleFlightMotion flightMotion=new VehicleFlightMotion(this);
    public final VehicleCruise cruise=new VehicleCruise(this);

    public boolean flying(){return flightPhase>=POWERED_FLIGHT;}
    public boolean poweredFlight(){return flightPhase==TAKEOFF_ROLL||flightPhase==POWERED_FLIGHT;}
    public boolean takeoffRoll(){return flightPhase==TAKEOFF_ROLL;}
    public boolean descendingFromFlight(){return flightPhase>=RECOVER_FLIGHT;}
    public boolean descendingToFlightLimit(){return poweredFlight()&&flying()&&flightAltitude()>flightReadout.maximumAltitude()+.02;}
    public boolean flightLanded(){return descendingFromFlight()&&landedFromFlight;}
    public float flightPitch(){return flightPitch;}
    int flightPhase(){return flightPhase;}
    boolean flightLandedState(){return landedFromFlight;}
    public double flightAltitude(){return cruise.altitude();}
    public int vehicleMode(){return flying()?3:hover?2:1;}
    double networkDriveSpeed(){return flying()?Math.sqrt(motionX*motionX+motionY*motionY+motionZ*motionZ):driveSpeed;}
    public VehicleFlightReadout flightReadout(){return flightReadout;}
    void flightPayloadChanged(){if(structure!=null&&!structure.maxwellCells.isEmpty())flightSampler.inventoryDirty(world.getTotalWorldTime());}

    void requestFlight(EntityPlayerMP pilot){
        if(flying()||takeoffRoll())return;
        if(getControllingPassenger()!=pilot)throw new IllegalArgumentException("Sit in the Pilot Seat before selecting Flight.");
        if(autopilot.enabled())throw new IllegalArgumentException("Turn off Autopilot before selecting Flight.");
        if(VehicleCouplingTransfer.busy(this)||VehicleCouplings.preparingArmRelease(this))throw new IllegalArgumentException("Wait for the vehicle coupling transfer.");
        refreshFlightReadout();
        if(flightReadout.drives==0)throw new IllegalArgumentException("Flight requires a Maxwell Drive.");
        if(flightReadout.thrust<=0)throw new IllegalArgumentException("Fuel and enable a Maxwell Drive before selecting Flight.");
        if(!flightReadout.canFly())throw new IllegalArgumentException("Effective weight exceeds the available Maxwell takeoff limit.");
        if(!hover&&(!hasGroundContact()||!VehicleLandingGear.extended(this)||!VehicleSkiTraction.allowed(this)))
            throw new IllegalArgumentException("Takeoff requires ground contact and extended Taxi landing gear.");
        flightPhase=hover?POWERED_FLIGHT:TAKEOFF_ROLL;
        flightPitch=0;landedFromFlight=false;cruisingRecovery=false;initialClimb=0;driveSpeed=Math.max(0,driveSpeed);
        serverInputs.clear();forward=strafe=0;brake=false;
        taxiReturnPending=false;
        if(hover)VehicleLandingGear.refreshSignals(this);
    }
    private boolean hasGroundContact(){return onGround||VehicleFlight.clearance(this)<=.001;}

    void leaveFlight(int mode){
        if(VehicleStatus.highModeLocked(this,mode))
            throw new IllegalArgumentException("Descend to Y=256 or below before selecting Taxi or Hover.");
        if(takeoffRoll()){
            flightPhase=NO_FLIGHT;
            if(mode==2&&!setHover(true))throw new IllegalArgumentException("This vehicle needs lift blocks and a Vehicle Thruster for Hover.");
            return;
        }
        if(!flying())return;
        if(mode==2&&!canResumeHover())throw new IllegalArgumentException("This vehicle needs lift blocks and a Vehicle Thruster for Hover.");
        beginFlightRecovery(mode==1?RETURN_TAXI:RETURN_HOVER);
    }

    private void refreshFlightReadout(){
        if(structure.maxwellCells.isEmpty()){flightReadout=VehicleFlightReadout.EMPTY;return;}
        net.minecraft.tileentity.TileEntity raw=view.getTileEntity(VehicleControlSettings.position(structure));
        com.vandorlabs.tiles.TileEntityVehicleController controller=raw instanceof com.vandorlabs.tiles.TileEntityVehicleController?(com.vandorlabs.tiles.TileEntityVehicleController)raw:null;
        double prior=controller==null?0:Math.max(0,controller.vehicleWeight);
        flightReadout=flightSampler.sample(view,world.getTotalWorldTime(),prior,controller!=null&&controller.vehicleWeightIncomplete);
    }

    private boolean canResumeHover(){return VehicleFlight.canHover(structure)&&structure.activeThruster();}
    private void beginFlightRecovery(int phase){
        flightPhase=phase;
        cruisingRecovery|=cruise.active()||flightAltitude()>FlightEnvelope.WORLD_CEILING;
    }
    VehiclePose flightPose(){return new VehiclePose(posX,posY,posZ,rotationYaw,flightPitch,structure==null?EnumFacing.NORTH:structure.facing);}
    void applyFlightPose(VehiclePose pose){
        rotationYaw=pose.yaw;flightPitch=pose.pitch;
        setPosition(pose.x,pose.y,pose.z);view.origin=new BlockPos(pose.x,pose.y,pose.z);
        velocityChanged=true;fallDistance=0;
    }
    void writeFlightMode(NBTTagCompound tag){
        if(flightPhase!=NO_FLIGHT){tag.setByte("MaxwellFlightPhase",(byte)flightPhase);tag.setDouble("MaxwellFlightSpeed",driveSpeed);tag.setFloat("MaxwellFlightPitch",flightPitch);tag.setBoolean("MaxwellFlightLanded",landedFromFlight);tag.setBoolean("MaxwellCruiseRecovery",cruisingRecovery);}
    }
    void readFlightMode(NBTTagCompound tag){
        flightPhase=MathHelper.clamp(tag.getByte("MaxwellFlightPhase"),NO_FLIGHT,RETURN_HOVER);
        if(flightPhase==NO_FLIGHT){flightPitch=0;landedFromFlight=cruisingRecovery=false;return;}
        double savedSpeed=tag.getDouble("MaxwellFlightSpeed");float savedPitch=tag.getFloat("MaxwellFlightPitch");
        driveSpeed=Double.isFinite(savedSpeed)?MathHelper.clamp(savedSpeed,0,FlightEnvelope.cruiseSpeed(FlightEnvelope.MAX_ALTITUDE)):0;
        flightPitch=Float.isFinite(savedPitch)?MathHelper.clamp(savedPitch,-FlightEnvelope.MAX_PITCH,FlightEnvelope.MAX_PITCH):0;
        landedFromFlight=tag.getBoolean("MaxwellFlightLanded");
        cruisingRecovery=tag.getBoolean("MaxwellCruiseRecovery");
    }
    public boolean flightDismount;
    public void exitFlight(EntityPlayerMP player){
        if(!isPassenger(player)||flying())return;
        if(getControllingPassenger()==player && hover && !autopilot.enabled())holdHoverPosition();
        flightDismount=true;try{player.dismountRidingEntity();}finally{flightDismount=false;}
    }
    private byte vertical;
    private boolean flightSprint;
    private int exhaustCursor,liftExhaustCursor;
    public static final double CRUISE_SPEED=.4,UNPOWERED_TAXI_SPEED=.3;
    public static final double HOVER_CRUISE_SPEED=.8;
    private static final double HOVER_SPRINT_MULTIPLIER=1.25;
    public static final double HOVER_SPRINT_SPEED=HOVER_CRUISE_SPEED*HOVER_SPRINT_MULTIPLIER;
    public void predictFlight(boolean sprint){if(!world.isRemote)return;flightSprint=sprint;verticalAt=world.getTotalWorldTime();}
    private long verticalAt=-100,clearanceAt=-100;
    private double clearance=Double.POSITIVE_INFINITY;
    public final VehicleAutopilot autopilot=new VehicleAutopilot(this);
    public double groundClearance(){if(flightAltitude()>=FlightEnvelope.WORLD_CEILING)return Double.POSITIVE_INFINITY;if(world.getTotalWorldTime()-clearanceAt>=5){clearance=VehicleFlight.clearance(this);clearanceAt=world.getTotalWorldTime();}return clearance;}
    public boolean setHover(boolean value){
        if(flying()||takeoffRoll())return false;
        if(view!=null&&structure!=null&&structure.controller()!=null&&!VehicleMounts.allowed(view,structure.controller().pos))return false;
        if(!value && structure!=null && !VehicleSkiTraction.allowed(this))return false;
        if(value && (!VehicleFlight.canHover(structure)))return false;
        if(value==hover)return true;
        if(value)try{VehicleFlight.requireThruster(structure);}catch(IllegalArgumentException unavailable){return false;}
        if(!value){clearance=VehicleFlight.clearance(this);clearanceAt=world.getTotalWorldTime();if(clearance>3 || !VehicleLandingGear.extended(this))return false;}
        hover=value;serverInputs.clear();hoverTarget=posY;motionY=0;vertical=0;taxiReturnPending=false;
        if(view!=null)view.hover=hover;
        if(value)VehicleLandingGear.refreshSignals(this);
        return true;
    }
    public void flightInput(EntityPlayerMP player,byte direction,boolean toggle){flightInput(player,direction,toggle,false);}
    public void flightInput(EntityPlayerMP player,byte direction,boolean toggle,boolean sprint){
        if(getControllingPassenger()!=player || direction< -1 || direction>1 || autopilot.enabled()||flying()||takeoffRoll())return;
        if(toggle||direction!=vertical||sprint!=flightSprint)VehicleCouplings.releaseControlsChanged(this);
        recordCurrentCouplingDriver();
        if(toggle&&!structure.hasDedicatedController()){VehicleNetwork.error(player,"Vehicle Controller missing");return;}
        if(toggle && !setHover(!hover))VehicleNetwork.error(player,hover?(structure.activeNonWheeledGear()?"Taxi requires wheels, or only skis above ice; otherwise select Park near the ground.":"Extend the landing gear fully and descend to within three blocks of solid ground before selecting Taxi."):VehicleFlight.hoverRequirement(structure));
        vertical=direction;flightSprint=sprint;verticalAt=world.getTotalWorldTime();
    }
    public void autopilotControls(byte f,byte s,boolean b){couplingInput(f,s,b);forward=f;strafe=s;brake=b;}

    private double correctionX,correctionY,correctionZ;
    private final java.util.ArrayDeque<Control> pendingInputs=new java.util.ArrayDeque<>();
    private static final class Control {
        int sequence;byte f,s;boolean brake,sprint;
        Control(int n,byte f,byte s,boolean b,boolean sprint){sequence=n;this.f=f;this.s=s;brake=b;this.sprint=sprint;}
    }
    public Vec3d toWorld(Vec3d local) {
        return toWorld(local,new BlockPos(local));
    }
    public Vec3d toWorld(Vec3d local,BlockPos owner){
        double px=structure.bounds.maxX/2,pz=structure.bounds.maxZ/2,c=Math.cos(Math.toRadians(rotationYaw)),s=Math.sin(Math.toRadians(rotationYaw));
        if(articulation.active())local=articulation.point(local,owner,1);
        if(flightPitch!=0)return flightPose().world(local,px,pz);
        double x=local.x-px,z=local.z-pz;return new Vec3d(posX+px+c*x-s*z,posY+local.y,posZ+pz+s*x+c*z);
    }
    public Vec3d toLocal(Vec3d absolute) {
        double px=structure.bounds.maxX/2,pz=structure.bounds.maxZ/2,c=Math.cos(Math.toRadians(rotationYaw)),s=Math.sin(Math.toRadians(rotationYaw));
        if(flightPitch!=0)return flightPose().local(absolute,px,pz);
        double x=absolute.x-posX-px,z=absolute.z-posZ-pz;return new Vec3d(px+c*x+s*z,absolute.y-posY,pz-s*x+c*z);
    }
    public Vec3d toLocal(Vec3d absolute,BlockPos owner){Vec3d local=toLocal(absolute);return articulation.active()?articulation.inverse(local,owner):local;}
    public void setLocalDriver(boolean value) {
        if(localDriver!=value){localDriver=value;pendingInputs.clear();lastAcknowledgement=-1;lastServerTick=Long.MIN_VALUE;nextPrediction=null;correctionX=correctionY=correctionZ=0;correctionYaw=0;previousVisual=currentVisual=null;}
    }
    public void predict(int sequence,byte f,byte s,boolean b) {
        if(transferInProgress)return;
        nextPrediction=new Control(sequence,f,s,b,flightSprint);
        forward=f;strafe=s;brake=b;
    }
    public void authoritative(VehicleNetwork.Packet p) {
        if(p.serverTick<=lastServerTick)return;
        if(!admitCouplerScope(p.associationGeneration,p.serverTick))return;
        if(view!=null&&!couplerScopeReady())return;
        if(p.armMotion.arms.isEmpty()||articulation.receiveArmMotion(p.armMotion,p.bodyPose,p.serverTick))receiveGeneralPose(p.bodyPose);
        if(p.jointAngles.length>0&&(generalBodyPose==null||!generalBodyPose.usesGeneral()))articulation.receive(p.jointTopology,p.jointAngles,localDriver);
        hover=p.flag;if(view!=null)view.hover=hover;hoverTarget=p.y;
        int oldPhase=flightPhase;
        flightPhase=p.flightPhase;flightReadout=p.flightReadout;landedFromFlight=p.flightLanded;
        if(oldPhase!=flightPhase){pendingInputs.clear();nextPrediction=null;}
        lastServerTick=p.serverTick;
        lastAcknowledgement=p.sequence;
        if(longPositionChange(p.x,p.y,p.z)){
            flightPitch=targetPitch=p.flightPitch;driveSpeed=p.speed;steering=p.steering;motionY=p.dy;
            snapPosition(p.x,p.y,p.z,p.yaw);
            return;
        }
        if(!localDriver||transferInProgress||flying()||takeoffRoll()){pendingInputs.clear();nextPrediction=null;driveSpeed=p.speed;steering=p.steering;motionY=p.dy;targetX=p.x;targetY=p.y;targetZ=p.z;targetYaw=p.yaw;targetPitch=p.flightPitch;interpolation=3;return;}
        double oldX=posX+correctionX,oldY=posY+correctionY,oldZ=posZ+correctionZ;
        float oldYaw=rotationYaw+correctionYaw;
        pendingInputs.removeIf(c->c.sequence<=p.sequence);
        rotationYaw=p.yaw;flightPitch=targetPitch=p.flightPitch;driveSpeed=p.speed;steering=p.steering;motionY=p.dy;
        setPosition(p.x,p.y,p.z);
        if(!pendingInputs.isEmpty())collision.withTerrainCache(world,this,()->{for(Control input:pendingInputs){forward=input.f;strafe=input.s;brake=input.brake;predictFlight(input.sprint);advance(true);}});
        double error=(oldX-posX)*(oldX-posX)+(oldY-posY)*(oldY-posY)+(oldZ-posZ)*(oldZ-posZ);
        correctionX=error<16?oldX-posX:0;correctionY=error<16?oldY-posY:0;correctionZ=error<16?oldZ-posZ:0;
        diagnostics.correction(MathHelper.wrapDegrees(oldYaw-rotationYaw),pendingInputs.size());
        correctionYaw=error<16?MathHelper.wrapDegrees(oldYaw-rotationYaw):0;
        if(error>=16)previousVisual=currentVisual=null;
    }
    public Vec3d renderCorrection(){return new Vec3d(correctionX,correctionY,correctionZ);}
    public VehiclePose visualPose(float partial) {
        if(currentVisual==null)return flightPose();
        return previousVisual.interpolate(currentVisual,partial);
    }
    public Vec3d seatPosition(VehiclePose pose,Entity passenger) {return seatPosition(pose,passenger,0);}
    public Vec3d seatPosition(VehiclePose pose,Entity passenger,float partial) {
        BlockPos seat=seatForPassenger(passenger);
        if(view.getTileEntity(seat) instanceof com.vandorlabs.tiles.TileEntityCockpit){com.vandorlabs.tiles.TileEntityCockpit cockpit=(com.vandorlabs.tiles.TileEntityCockpit)view.getTileEntity(seat);Vec3d at=com.vandorlabs.cockpit.CockpitData.rotate(cockpit.seatLocation(partial),cockpit.facing());return seatWorld(pose,new Vec3d(seat.getX()+at.x,seat.getY()+cockpit.seatHeight()+.35-EntityChairSeat.RIDER_PELVIS_OFFSET+passenger.getYOffset(),seat.getZ()+at.z),seat,partial,structure.bounds.maxX/2,structure.bounds.maxZ/2);}
        return seatWorld(pose,new Vec3d(seat.getX()+.5,seat.getY()+VehicleSeats.height(view,seat)+.35-EntityChairSeat.RIDER_PELVIS_OFFSET+passenger.getYOffset(),seat.getZ()+.5),seat,partial,structure.bounds.maxX/2,structure.bounds.maxZ/2);
    }
    private Vec3d seatWorld(VehiclePose pose,Vec3d local,BlockPos owner,float partial,double px,double pz){return pose.world(articulation.active()?articulation.point(local,owner,partial<0?0:partial):local,px,pz);}
    @Override public AxisAlignedBB getRenderBoundingBox() {
        if(structure==null)return getEntityBoundingBox();
        double x=structure.bounds.maxX/2,z=structure.bounds.maxZ/2,r=Math.hypot(x,z)+2;
        if(articulation.active()){r=articulation.renderRadius();VehiclePose a=visualPose(0),b=visualPose(1);return new AxisAlignedBB(a.x+x-r,a.y-r,a.z+z-r,a.x+x+r,a.y+r,a.z+z+r).union(new AxisAlignedBB(b.x+x-r,b.y-r,b.z+z-r,b.x+x+r,b.y+r,b.z+z+r));}
        AxisAlignedBB local=new AxisAlignedBB(x-r,-2,z-r,x+r,structure.bounds.maxY+2,z+r);
        return local.offset(posX+correctionX,posY+correctionY,posZ+correctionZ).union(local.offset(prevPosX,prevPosY,prevPosZ)).union(getEntityBoundingBox().grow(1));
    }
    public int acknowledgedInput(){return serverInputs.acknowledged();}
    private byte[] encoded;
    private VehicleStructure rotationSource,rotatedSnapshot;private int rotationTurns;
    VehicleArmParkingCurrent armParkingPreview;
    static final String COUPLER_GENERATION="CouplerAssociationGeneration";
    private java.util.UUID couplerAssociationGeneration;
    private long associationScopeTick=Long.MIN_VALUE,installedCouplerSourceTick=Long.MIN_VALUE;
    private boolean ownershipSourcePending;
    private long ownershipSourceRetryAt;
    /** Keep this dirty until a complete source succeeds; an early request may hit server rate limiting. */
    public void requestOwnershipSource(){
        if(world==null||!world.isRemote||world instanceof VehicleWorld||isDead)return;
        if(!ownershipSourcePending){ownershipSourcePending=true;ownershipSourceRetryAt=System.nanoTime();}
        retryOwnershipSource();
    }
    private void retryOwnershipSource(){
        if(!ownershipSourcePending||world==null||!world.isRemote||world instanceof VehicleWorld||isDead)return;
        long now=System.nanoTime();boolean decoder=com.vandorlabs.VandorLabs.proxy!=null&&com.vandorlabs.VandorLabs.proxy.vehicleSnapshotPending(getUniqueID());
        if(com.vandorlabs.vehicle.core.OwnershipSourceRetry.due(true,ownershipSourceRetryAt,now,decoder)){
            ownershipSourceRetryAt=com.vandorlabs.vehicle.core.OwnershipSourceRetry.after(now);VehicleNetwork.request(this);
        }
    }
    private NBTTagCompound retainedCouplerAssociation;
    public java.util.UUID couplerAssociationGeneration(){return couplerAssociationGeneration;}
    /** Last admitted external scope/source clock; observation only, never scope renewal. */
    public long couplerAssociationScopeTick(){return associationScopeTick;}
    /** Timestamp of the source actually installed, not the newest STATE observation. */
    public long installedCouplerSourceTick(){return installedCouplerSourceTick;}
    private boolean admitCouplerScope(java.util.UUID generation,long tick){
        if(generation==null)return couplerAssociationGeneration==null;
        if(generation.getMostSignificantBits()==0&&generation.getLeastSignificantBits()==0)return false;
        if(tick<associationScopeTick&&!generation.equals(couplerAssociationGeneration))return false;
        if(tick>=associationScopeTick){couplerAssociationGeneration=generation;associationScopeTick=tick;retainedCouplerAssociation=null;}
        return generation.equals(couplerAssociationGeneration);
    }
    /** Scope arrives with the source, before any component/role admission; old different scopes cannot replace it. */
    public boolean installScopedSnapshot(VehicleStructure next,java.util.UUID owner,java.util.UUID generation,long tick){
        if(world==null||!world.isRemote||generation!=null&&!getUniqueID().equals(owner))return false;
        VehicleWorld.CouplerScope installed=view==null?null:view.couplerScope();
        if(generation!=null&&installed!=null&&generation.equals(installed.generation)&&getUniqueID().equals(installed.owner)&&tick<installedCouplerSourceTick)return false;
        java.util.UUID previous=couplerAssociationGeneration;long previousTick=associationScopeTick,previousSourceTick=installedCouplerSourceTick;NBTTagCompound previousRetained=retainedCouplerAssociation;VehicleStructure before=structure;
        if(!admitCouplerScope(generation,tick))return false;
        try{install(next);installedCouplerSourceTick=tick;ownershipSourcePending=false;return true;}catch(RuntimeException|LinkageError failure){
            couplerAssociationGeneration=previous;associationScopeTick=previousTick;installedCouplerSourceTick=previousSourceTick;retainedCouplerAssociation=previousRetained;
            try{if(before!=null)install(before);else{signals.close();structure=null;view=null;collision=null;encoded=null;collisionCache.clear();VehicleLookup.remove(this);}}
            catch(RuntimeException|LinkageError rollback){failure.addSuppressed(rollback);if(before!=null)retainUnavailableSnapshot(before);}
            com.vandorlabs.VandorLabs.logger.warn("Rejected vehicle snapshot; prior client source retained",failure);return false;
        }
    }

    VehicleWorld.CouplerScope currentCouplerScope(){return couplerAssociationGeneration==null?null:VehicleWorld.CouplerScope.external(getUniqueID(),couplerAssociationGeneration);}
    /** A newer scope may precede its source on the client; freeze old-source consumers meanwhile. */
    public boolean couplerScopeReady(){if(view==null)return false;VehicleWorld.CouplerScope scope=view.couplerScope();return couplerAssociationGeneration==null?scope==null:scope!=null&&getUniqueID().equals(scope.owner)&&couplerAssociationGeneration.equals(scope.generation);}

    public boolean acceptsCouplerUpdate(java.util.UUID generation){return couplerScopeReady()&&java.util.Objects.equals(generation,couplerAssociationGeneration);}
    /** A later STATE is not a source replacement and cannot obsolete a matching motion correction. */
    public boolean acceptsCouplerMotion(java.util.UUID generation,long tick){return acceptsCouplerUpdate(generation)&&(generation==null||tick>=installedCouplerSourceTick);}

    static final class CouplerScopeSnapshot {
        private final EntityGroundVehicle owner;private final java.util.UUID generation;private final NBTTagCompound retained;
        private CouplerScopeSnapshot(EntityGroundVehicle owner){this.owner=owner;generation=owner.couplerAssociationGeneration;retained=owner.retainedCouplerAssociation==null?null:owner.retainedCouplerAssociation.copy();}
    }
    CouplerScopeSnapshot captureCouplerAssociationScope(){return new CouplerScopeSnapshot(this);}
    /** Restore only the scope, including after a failed source installation has become inert. */
    void restoreCouplerAssociationScope(java.util.UUID expected,CouplerScopeSnapshot original){
        if(original==null||original.owner!=this||world==null||world.isRemote||world.getMinecraftServer()==null||!world.getMinecraftServer().isCallingFromMinecraftThread()||!java.util.Objects.equals(expected,couplerAssociationGeneration))throw new IllegalStateException("Coupler scope rollback owner changed");
        couplerAssociationGeneration=original.generation;retainedCouplerAssociation=original.retained==null?null:original.retained.copy();
    }

    /** The authenticated ownership publisher supplies the detached prospective generation. */
    void commitCouplerAssociationGeneration(java.util.UUID expected,java.util.UUID next){
        if(world==null||world.isRemote||world.getMinecraftServer()==null||!world.getMinecraftServer().isCallingFromMinecraftThread()||structure==null||ownershipBlocked||!java.util.Objects.equals(expected,couplerAssociationGeneration)||next==null||next.getMostSignificantBits()==0&&next.getLeastSignificantBits()==0)throw new IllegalStateException("Accepted Coupler ownership generation required");
        couplerAssociationGeneration=next;retainedCouplerAssociation=null;
    }
    private static NBTTagCompound retainedCouplerScope(NBTTagCompound tag){
        if(!tag.hasKey(COUPLER_GENERATION+"Most")&&!tag.hasKey(COUPLER_GENERATION+"Least"))return null;
        NBTTagCompound raw=new NBTTagCompound();for(String suffix:new String[]{"Most","Least"}){String key=COUPLER_GENERATION+suffix;if(tag.hasKey(key))raw.setTag(key,tag.getTag(key).copy());}return raw;
    }
    static java.util.UUID readCouplerGeneration(NBTTagCompound tag){
        if(!tag.hasKey(COUPLER_GENERATION+"Most")&&!tag.hasKey(COUPLER_GENERATION+"Least"))return null;
        if(!tag.hasKey(COUPLER_GENERATION+"Most",4)||!tag.hasKey(COUPLER_GENERATION+"Least",4))throw new IllegalArgumentException("Malformed Coupler ownership generation");
        java.util.UUID result=tag.getUniqueId(COUPLER_GENERATION);if(result.getMostSignificantBits()==0&&result.getLeastSignificantBits()==0)throw new IllegalArgumentException("Empty Coupler ownership generation");return result;
    }
    void writeCouplerOwnershipMetadata(NBTTagCompound tag){
        writeGeneralPose(tag);tag.removeTag(COUPLER_GENERATION+"Most");tag.removeTag(COUPLER_GENERATION+"Least");
        if(retainedCouplerAssociation!=null)for(String key:retainedCouplerAssociation.getKeySet())tag.setTag(key,retainedCouplerAssociation.getTag(key).copy());
        else if(couplerAssociationGeneration!=null)tag.setUniqueId(COUPLER_GENERATION,couplerAssociationGeneration);
    }
    /** Retain before decoding, so even malformed metadata survives a refused recovery. */
    void retainCouplerOwnershipMetadata(NBTTagCompound tag){
        generalBodyPose=VehicleBodyPoseState.retain(tag.getTag(VehicleBodyPoseFrame.NBT_KEY));retainedCouplerAssociation=retainedCouplerScope(tag);couplerAssociationGeneration=readCouplerGeneration(tag);
    }
    void restoreCouplerOwnershipMetadata(NBTTagCompound tag){
        retainCouplerOwnershipMetadata(tag);
        if(generalBodyPose!=null){generalBodyPose.readRetained();if(generalBodyPose.usesGeneral()&&!applyPendingGeneralPose()&&!world.isRemote){if(structure!=null)retainUnavailableSnapshot(structure);if(tag.hasKey("Craft",10))unavailable=tag.getCompoundTag("Craft").copy();throw new IllegalArgumentException("Saved general body pose has no matching runtime layout");}}
        retainedCouplerAssociation=null;
    }
    static void validateCouplerOwnershipMetadata(NBTTagCompound tag){
        readCouplerGeneration(tag);
        if(tag.hasKey(VehicleBodyPoseFrame.NBT_KEY)){
            if(!tag.hasKey(VehicleBodyPoseFrame.NBT_KEY,10))throw new IllegalArgumentException("Invalid general body envelope type");
            VehicleBodyPoseFrame.readNBT(tag.getCompoundTag(VehicleBodyPoseFrame.NBT_KEY));
        }
    }
    private VehicleBodyPoseState generalBodyPose;
    private VehicleBodyPoseState.Receiver generalBodyPoseReceiver;
    VehicleBodyPoseFrame currentGeneralPose(){return generalBodyPose==null?VehicleBodyPoseFrame.NONE:generalBodyPose.current();}
    boolean generalPoseReady(){return generalBodyPose==null||generalBodyPose.ready();}
    /** Runtime calls only after the complete layout/pose has passed its ordinary collision admission. */
    VehicleBodyPoseFrame commitGeneralPose(long layout,java.util.List<VehicleBodyPoseFrame.Body> bodies){
        if(world==null||world.isRemote||world.getMinecraftServer()==null||!world.getMinecraftServer().isCallingFromMinecraftThread()||structure==null||ownershipBlocked)throw new IllegalStateException("Accepted server body pose required");
        if(generalBodyPose==null&&layout==0&&bodies!=null&&bodies.isEmpty())return VehicleBodyPoseFrame.NONE;
        VehicleBodyPoseState state=generalBodyPose==null?new VehicleBodyPoseState():generalBodyPose;
        VehicleBodyPoseFrame frame=state.commit(layout,bodies);generalBodyPose=state;return frame;
    }
    void generalPoseReceiver(VehicleBodyPoseState.Receiver receiver){generalBodyPoseReceiver=receiver;applyPendingGeneralPose();}
    boolean applyPendingGeneralPose(){return generalBodyPose!=null&&generalBodyPose.applyPending(generalBodyPoseReceiver,localDriver);}
    private void receiveGeneralPose(VehicleBodyPoseFrame frame){
        if(!frame.present())return;if(generalBodyPose==null)generalBodyPose=new VehicleBodyPoseState();
        generalBodyPose.receive(frame,generalBodyPoseReceiver,localDriver);
    }
    private void writeGeneralPose(NBTTagCompound tag){if(generalBodyPose==null)tag.removeTag(VehicleBodyPoseFrame.NBT_KEY);else generalBodyPose.write(tag);}

    public VehicleStructure rotatedSnapshot(int turns) {
        articulation.requireParkable();
        if(turns==0)return structure;
        if(rotationSource!=structure || rotationTurns!=turns){rotatedSnapshot=VehicleRotation.rotate(structure,view,turns);rotationSource=structure;rotationTurns=turns;}
        return rotatedSnapshot;
    }
    private float propulsion;
    private NBTTagCompound unavailable;
    private boolean rampGeometryUpgradePending;
    public EntityGroundVehicle(World world){super(world);setSize(1,1);isImmuneToFire=true;preventEntitySpawning=true;}
    /** Preserve the last complete owner if both a replacement and its rollback fail. */
    public void retainUnavailableSnapshot(VehicleStructure original){
        NBTTagCompound retained=original.write().copy();unavailable=retained;motionX=motionY=motionZ=0;
        try{signals.close();}finally{structure=null;view=null;collision=null;encoded=null;rampGeometryUpgradePending=false;collisionCache.clear();VehicleLookup.remove(this);}
    }
    public void install(VehicleStructure snapshot) {
        install(snapshot,null);
    }
    void install(VehicleStructure snapshot,VehicleCollision prepared) {
        VehiclePaintings.entries(snapshot);
        for(VehicleStructure.Cell cell:snapshot.cells){NBTTagCompound data=cell.tileData();if(data!=null&&data.hasKey(com.vandorlabs.gear.GearTaxiState.KEY,10))com.vandorlabs.gear.GearTaxiState.validate(data.getCompoundTag(com.vandorlabs.gear.GearTaxiState.KEY));}
        VehicleAnimationDiagnostics.selectInstall(this);long installSample=VehicleAnimationDiagnostics.start(this);
        VehicleCanopies.forget(this);
        snapshot=VehicleControlSettings.migrate(snapshot);
        if(!world.isRemote && structure!=null && !structure.seat.equals(snapshot.seat)){
            BlockPos shift=snapshot.seat.subtract(structure.seat);if(taxiReturnSeat!=null)taxiReturnSeat=taxiReturnSeat.add(shift);
            NBTTagCompound assignments=dataManager.get(PILOT_SEATS).copy();
            for(String key:assignments.getKeySet())if(assignments.hasKey(key,4))assignments.setLong(key,BlockPos.fromLong(assignments.getLong(key)).add(shift).toLong());
            dataManager.set(PILOT_SEATS,assignments);
        }
        if(world.isRemote)com.vandorlabs.VandorLabs.proxy.vehicleArmVisualSupport(this,null);
        structure=snapshot;if(view==null)view=new VehicleWorld(world,snapshot,currentCouplerScope());else view.update(snapshot,currentCouplerScope());collision=prepared==null?new VehicleCollision(view):new VehicleCollision(prepared.geometry());encoded=null;
        view.craft=this;view.hover=hover;if(!world.isRemote)view.controlUpdates=signals::changed;
        if(world.isRemote)com.vandorlabs.VandorLabs.proxy.vehicleArmVisualSupport(this,null);
        collisionCache.reset(view,collision);
        rampGeometryUpgradePending=!world.isRemote&&VehicleRamps.needsGeometryUpgrade(this);
        setPosition(posX,posY,posZ);
        VehicleAnimationDiagnostics.elapsed(this,"install-full",installSample);
    }
    /** Preserve live canopy interpolation while publishing an immutable save/parking snapshot. */
    public void updateCanopies(VehicleStructure snapshot,boolean moved){
        if(!couplerScopeReady())throw new IllegalStateException("Full vehicle installation required when ownership scope changes");
        snapshot.requireCouplerScope(view.couplerScope());
        VehicleStructure before=structure;structure=snapshot;view.structure=snapshot;
        snapshot.componentView(view);snapshot.inheritComponents(before);encoded=null;
        if(moved){
            long sampled=VehicleAnimationDiagnostics.start(this);
            collision=collisionCache.collision(view);
            VehicleAnimationDiagnostics.elapsed(this,"collision-rebuild",sampled);
            if(sampled!=0){String side=world.isRemote?"client/":"server/";StackTraceElement[] stack=Thread.currentThread().getStackTrace();String caller=stack.length>2?stack[2].getClassName()+"."+stack[2].getMethodName():"unknown";VehicleAnimationDiagnostics.value(side+"collision-rebuild-count/"+caller,1);VehicleAnimationDiagnostics.value(side+"collision-boxes-count",collision.boxes.size());}
            sampled=VehicleAnimationDiagnostics.start(this);setPosition(posX,posY,posZ);VehicleAnimationDiagnostics.elapsed(this,"bounds-and-lookup",sampled);
        }
    }
    public void updateSettings(VehicleStructure snapshot){
        collisionCache.clear();
        boolean heightChanged=false;
        for(BlockPos seat:structure.pilotSeats()){
            NBTTagCompound before=structure.at(seat).tileData(),after=snapshot.at(seat)==null?null:snapshot.at(seat).tileData();
            if((before==null?0:before.getInteger("PilotHeight"))!=(after==null?0:after.getInteger("PilotHeight")))heightChanged=true;
        }
        structure=snapshot;view.update(snapshot,currentCouplerScope());encoded=null;
        if(heightChanged){collision=new VehicleCollision(view);setPosition(posX,posY,posZ);for(Entity passenger:getPassengers())updatePassenger(passenger);}
        collisionCache.restart(view);
    }
    public byte[] snapshotBytes() throws java.io.IOException {if(encoded==null)encoded=structure.encode();return encoded;}
    @Override protected void entityInit(){dataManager.register(PILOT_SEATS,new NBTTagCompound());dataManager.register(TRANSFERRING,false);dataManager.register(HOVER_EXHAUST,false);VehicleCruise.define(this);}
    @Override public void writeSpawnData(io.netty.buffer.ByteBuf buffer){buffer.writeInt(VehicleStructure.SCHEMA);buffer.writeBoolean(transferInProgress);}
    @Override public void readSpawnData(io.netty.buffer.ByteBuf buffer){if(buffer.readInt()==VehicleStructure.SCHEMA){if(buffer.isReadable())setTransferInProgress(buffer.readBoolean());VehicleNetwork.request(this);}}
    @Override protected void writeEntityToNBT(NBTTagCompound tag){
        tag.setTag("OccupiedPilotSeats",dataManager.get(PILOT_SEATS).copy());signals.persist();articulation.write(tag);writeCouplerOwnershipMetadata(tag);
        writeFlightMode(tag);cruise.write(tag);
        if(couplingActor!=null)tag.setUniqueId("CouplingActor",couplingActor);tag.setTag("AutopilotNavigation",autopilot.write());tag.setBoolean("HoverMode",hover);tag.setDouble("HoverTarget",hoverTarget);tag.setLong("TransferEpoch",transferEpoch);tag.setBoolean("TaxiReturnPending",taxiReturnPending);
        if(taxiReturnSeat!=null)tag.setLong("TaxiReturnSeat",taxiReturnSeat.toLong());if(taxiReturnActor!=null)tag.setUniqueId("TaxiReturnActor",taxiReturnActor);if(structure!=null)tag.setTag("Craft",structure.write());else if(unavailable!=null)tag.setTag("Craft",unavailable.copy());
    }
    @Override protected void readEntityFromNBT(NBTTagCompound tag){
        generalBodyPose=VehicleBodyPoseState.retain(tag.getTag(VehicleBodyPoseFrame.NBT_KEY));retainedCouplerAssociation=retainedCouplerScope(tag);articulation.retain(tag);
        dataManager.set(PILOT_SEATS,tag.getCompoundTag("OccupiedPilotSeats").copy());couplingActor=tag.hasUniqueId("CouplingActor")?tag.getUniqueId("CouplingActor"):null;hover=tag.getBoolean("HoverMode");hoverTarget=tag.getDouble("HoverTarget");transferEpoch=tag.getLong("TransferEpoch");taxiReturnPending=tag.getBoolean("TaxiReturnPending");taxiReturnSeat=tag.hasKey("TaxiReturnSeat",4)?BlockPos.fromLong(tag.getLong("TaxiReturnSeat")):null;taxiReturnActor=tag.hasUniqueId("TaxiReturnActor")?tag.getUniqueId("TaxiReturnActor"):null;
        readFlightMode(tag);cruise.read(tag);
        try{
            couplerAssociationGeneration=readCouplerGeneration(tag);structure=null;install(VehicleStructure.read(tag.getCompoundTag("Craft")));articulation.read(tag);autopilot.read(tag.getCompoundTag("AutopilotNavigation"));restoreCouplerOwnershipMetadata(tag);
            for(BlockPos at:java.util.Collections.singletonList(VehicleControlSettings.position(structure))){net.minecraft.tileentity.TileEntity pilot=view.getTileEntity(at);if(pilot instanceof com.vandorlabs.tiles.TileEntityPilotSeat)((com.vandorlabs.tiles.TileEntityPilotSeat)pilot).resumeAfterLoad(world);}
        }catch(Exception|LinkageError failure){
            articulation.retain(tag);signals.close();structure=null;view=null;collision=null;encoded=null;rampGeometryUpgradePending=false;collisionCache.clear();VehicleLookup.remove(this);unavailable=tag.getCompoundTag("Craft").copy();com.vandorlabs.VandorLabs.logger.error("Vehicle data retained but unavailable: "+getUniqueID(),failure);
        }
        motionX=motionY=motionZ=0;loadedTaxiSupportCheck=structure!=null;
    }
    @Override public void setPosition(double x,double y,double z){super.setPosition(x,y,z);if(collision!=null)setEntityBoundingBox(flightPitch!=0?flightMotion.bounds(flightPose()):articulation!=null&&articulation.active()?articulation.worldBounds(x,y,z,rotationYaw):collision.bounds(rotationYaw).offset(x,y,z));VehicleLookup.update(this);}
    private boolean couplingInputSeen,couplingInputBrake;private byte couplingInputForward,couplingInputStrafe;
    private void couplingInput(byte f,byte s,boolean b){if(!couplingInputSeen){couplingInputSeen=true;couplingInputForward=forward;couplingInputStrafe=strafe;couplingInputBrake=brake;}if(couplingInputForward!=f||couplingInputStrafe!=s||couplingInputBrake!=b){couplingInputForward=f;couplingInputStrafe=s;couplingInputBrake=b;VehicleCouplings.releaseControlsChanged(this);}}
    public void input(EntityPlayerMP player,int sequence,byte forward,byte strafe,boolean brake) {
        if(transferInProgress || getControllingPassenger()!=player)return;
        if(!(hover||poweredFlight()||flying()?serverInputs.offerLatest(sequence,forward,strafe,brake):serverInputs.offer(sequence,forward,strafe,brake)))return;
        couplingInput(forward,strafe,brake);
        recordCurrentCouplingDriver();
        lastInput=world.getTotalWorldTime();
    }
    @Override protected void addPassenger(Entity passenger){super.addPassenger(passenger);recordCurrentCouplingDriver();}
    @Override public Entity getControllingPassenger(){if(structure==null)return null;for(Entity passenger:getPassengers())if(structure.pilotSeats().contains(seatForPassenger(passenger)))return passenger;return null;}
    // Vanilla otherwise sends client vehicle positions, overriding the server controller.
    @Override public boolean canPassengerSteer(){return false;}
    @Override protected boolean canFitPassenger(Entity passenger){return structure!=null && getPassengers().size()<structure.passengerSeats().size();}
    @Override public boolean processInitialInteract(EntityPlayer player,EnumHand hand) {
        if(hand!=EnumHand.MAIN_HAND)return false;
        VehicleInteraction.Target target=VehicleInteraction.pointed(player);
        return target!=null && target.craft==this && VehicleInteraction.activate(player,target);
    }
    public boolean board(EntityPlayer player) {return structure!=null && board(player,structure.seat);}
    public boolean board(EntityPlayer player,BlockPos seat) {
        if(world.isRemote || isDead || ownershipBlocked || structure==null || player.isSpectator()
                || player.isDead || player.world!=world || player.getRidingEntity()==this || !structure.passengerSeats().contains(seat))return false;
        for(Entity passenger:getPassengers())if(seatForPassenger(passenger).equals(seat))return false;
        boolean first=getControllingPassenger()==null&&structure.pilotSeats().contains(seat);assignSeat(player,seat);
        if(!player.startRiding(this)){assignSeat(player,null);return false;}
        taxiReturnPending=false;
        if(first){serverInputs.clear();forward=strafe=0;brake=false;lastInput=-100;
            pendingInputs.clear();lastAcknowledgement=-1;}
        return true;
    }
    @Override public void onAddedToWorld(){super.onAddedToWorld();signals.open();VehicleLookup.update(this);}
    @Override public void onRemovedFromWorld(){VehicleCanopies.forget(this);collisionCache.clear();signals.close();VehicleLookup.remove(this);super.onRemovedFromWorld();}
    @Override public void setDead(){VehicleCanopies.forget(this);collisionCache.clear();signals.close();VehicleLookup.remove(this);super.setDead();}
    @Override public void updatePassenger(Entity passenger) {
        if(isPassenger(passenger) && structure!=null) {
            Vec3d at=seatPosition(world.isRemote?visualPose(1):flightPose(),passenger,world.isRemote&&articulation.active()?1:0);
            passenger.setPosition(at.x,at.y,at.z);
            if(world.isRemote) {
                Vec3d before=seatPosition(visualPose(0),passenger,-1);
                passenger.prevPosX=passenger.lastTickPosX=before.x;
                passenger.prevPosY=passenger.lastTickPosY=before.y;
                passenger.prevPosZ=passenger.lastTickPosZ=before.z;
            }
        }
    }
    @Override public double getMountedYOffset(){return 0;}
    @Override protected void removePassenger(Entity passenger) {
        BlockPos exitSeat=structure==null?null:seatForPassenger(passenger);
        boolean wasDriver=getControllingPassenger()==passenger;
        if(wasDriver&&structure!=null)taxiReturnSeat=seatForPassenger(passenger);
        super.removePassenger(passenger);
        if(!world.isRemote){assignSeat(passenger,null);recordCurrentCouplingDriver();}
        if(wasDriver){serverInputs.clear();pendingInputs.clear();lastAcknowledgement=-1;lastInput=-100;forward=strafe=0;vertical=0;verticalAt=-100;}
        if(world.isRemote || collision==null || VehicleTransfer.isMutating(world))return;
        if(wasDriver&&flying())beginFlightRecovery(RECOVER_FLIGHT);
        else if(wasDriver && hover && !autopilot.enabled())holdHoverPosition();
        Vec3d exit=null;
        if(exitSeat!=null){Vec3d near=toWorld(new Vec3d(exitSeat).addVector(.5,0,.5));AxisAlignedBB seatArea=new AxisAlignedBB(near.x-.5,near.y,near.z-.5,near.x+.5,near.y+2,near.z+.5);exit=com.vandorlabs.entity.SafeDismount.findNear(world,passenger,seatArea,near);}
        if(exit==null)exit=com.vandorlabs.entity.SafeDismount.find(world,passenger,getEntityBoundingBox());
        com.vandorlabs.entity.SafeDismount.move(passenger,exit,getEntityBoundingBox());
        if(!isDead && passenger instanceof EntityPlayerMP && getPassengers().isEmpty() && !hover && !flying() && !takeoffRoll() && !autopilot.enabled() && VehicleTaxiMode.enabled(structure,taxiReturnSeat))
            VehicleService.taxiParking((EntityPlayerMP)passenger,this);
    }
    public void stopForParking(){forward=strafe=0;brake=true;driveSpeed=0;steering=0;motionX=motionY=motionZ=0;pendingInputs.clear();serverInputs.clear();nextPrediction=null;}
    private void holdHoverPosition(){stopForParking();vertical=0;verticalAt=-100;lastInput=-100;hoverTarget=posY;}
    @Override public boolean canBeCollidedWith(){return !isDead;}
    @Override public boolean canRiderInteract(){return true;}
    @Override public boolean canBePushed(){return false;}
    @Override public AxisAlignedBB getCollisionBoundingBox(){return null;}
    @Override public boolean attackEntityFrom(DamageSource source,float damage){return false;}
    @Override public void setPortal(BlockPos p){}
    @Override public void setPositionAndRotationDirect(double x,double y,double z,float yaw,float pitch,int steps,boolean teleport) {
        if(teleport&&longPositionChange(x,y,z)){snapPosition(x,y,z,yaw);return;}
        if(localDriver)return;
        // Custom STATE is the single observer interpolation stream after its first packet.
        if(lastServerTick!=Long.MIN_VALUE && !teleport)return;
        targetX=x;targetY=y;targetZ=z;targetYaw=yaw;interpolation=teleport?1:Math.max(1,steps);
        if(teleport){previousVisual=currentVisual=null;correctionX=correctionY=correctionZ=0;correctionYaw=0;}
    }
    private boolean longPositionChange(double x,double y,double z){return Math.abs(x-posX)>128||Math.abs(y-posY)>128||Math.abs(z-posZ)>128;}
    private void snapPosition(double x,double y,double z,float yaw){
        targetX=prevPosX=lastTickPosX=x;targetY=prevPosY=lastTickPosY=y;targetZ=prevPosZ=lastTickPosZ=z;
        targetYaw=prevRotationYaw=rotationYaw=yaw;interpolation=0;
        previousVisual=currentVisual=null;correctionX=correctionY=correctionZ=0;correctionYaw=0;
        pendingInputs.clear();nextPrediction=null;setPosition(x,y,z);
    }
    public boolean stopped(){return motionX*motionX+motionY*motionY+motionZ*motionZ<.0001;}
    @Override public void onUpdate() {
        long sampled=VehicleAnimationDiagnostics.start(this);
        try{updateAnimatedVehicle();}finally{VehicleAnimationDiagnostics.elapsed(this,"entity-full-tick",sampled);}
    }
    private void updateAnimatedVehicle() {
        retryOwnershipSource();
        if(world.isRemote&&view!=null&&!couplerScopeReady()){
            super.onUpdate();motionX=motionY=motionZ=0;
            if(ticksExisted%40==1&&(com.vandorlabs.VandorLabs.proxy==null||!com.vandorlabs.VandorLabs.proxy.vehicleSnapshotPending(getUniqueID())))VehicleNetwork.request(this);
            return;
        }
        if(!world.isRemote&&VehicleCouplings.preparingArmRelease(this)){super.onUpdate();VehicleCouplings.pollArmReleaseInputs(this);return;}
        if(transferInProgress){motionX=motionY=motionZ=0;return;}
        super.onUpdate();rotationPitch=prevRotationPitch=0;
        if(world.isRemote)articulation.clientTick();
        if(rampGeometryUpgradePending&&!world.isRemote&&structure!=null){rampGeometryUpgradePending=false;VehicleRamps.upgradeLoadedGeometry(this);rampGeometryUpgradePending=false;}
        long animationSample=VehicleAnimationDiagnostics.start(this);
        if(animationSample!=0&&view!=null){int gear=0;for(net.minecraft.tileentity.TileEntity raw:view.tiles.values())if(raw instanceof com.vandorlabs.tiles.TileEntityLandingGear){com.vandorlabs.tiles.TileEntityLandingGear tile=(com.vandorlabs.tiles.TileEntityLandingGear)raw;if(tile.progress!=tile.previous)gear++;}VehicleAnimationDiagnostics.value((world.isRemote?"client/":"server/")+"moving-gear-count",gear);}
        VehicleCanopies.tick(this);VehicleAnimationDiagnostics.elapsed(this,"canopy-tick",animationSample);
        animationSample=VehicleAnimationDiagnostics.start(this);signals.tick();VehicleAnimationDiagnostics.elapsed(this,"signals-tick",animationSample);
        animationSample=VehicleAnimationDiagnostics.start(this);VehicleLandingGear.tick(this);VehicleAnimationDiagnostics.elapsed(this,"landing-gear-tick",animationSample);
        collisionCache.tick(view);
        if(world.isRemote) {
            previousVisual=currentVisual==null?flightPose():currentVisual;
            correctionX*=.65;correctionY*=.65;correctionZ*=.65;correctionYaw*=.65;
            if(structure==null && ticksExisted%40==1)VehicleNetwork.request(this);
            if(localDriver && structure!=null && !flying() && !takeoffRoll()) {
                if(nextPrediction!=null) {
                    if(pendingInputs.size()>=VehicleControls.LIMIT)pendingInputs.removeFirst();
                    pendingInputs.addLast(nextPrediction);forward=nextPrediction.f;strafe=nextPrediction.s;brake=nextPrediction.brake;predictFlight(nextPrediction.sprint);nextPrediction=null;
                }
                advance(true);
            }
            else if(interpolation>0){rotationYaw+=MathHelper.wrapDegrees(targetYaw-rotationYaw)/interpolation;flightPitch+=(targetPitch-flightPitch)/interpolation;setPosition(posX+(targetX-posX)/interpolation,posY+(targetY-posY)/interpolation,posZ+(targetZ-posZ)/interpolation);interpolation--;}
            currentVisual=new VehiclePose(posX+correctionX,posY+correctionY,posZ+correctionZ,rotationYaw+correctionYaw,flightPitch,structure==null?EnumFacing.NORTH:structure.facing);
            if(view!=null){view.origin=new BlockPos(posX,posY,posZ);
                double speed=view.recordMotion(posX-prevPosX,posY-prevPosY,posZ-prevPosZ,rotationYaw);
                propulsion=(float)approach(propulsion,speed>.002?15:0,1.5);view.propulsionLevel=Math.round(propulsion);view.hover=hover||flying();
                if(view.hover){
                    if(ticksExisted%12==0)emitFlightParticles(true,Vec3d.ZERO);
                    if(dataManager.get(HOVER_EXHAUST)&&ticksExisted%8==0){
                        Vec3d direction=speed>.002?view.propulsionMotion:new Vec3d(structure.facing.getDirectionVec()).scale(driveSpeed<0?-1:1);
                        emitFlightParticles(false,direction);
                    }
                }}
            return;
        }
        if(structure==null || ownershipBlocked){motionX=motionY=motionZ=0;return;}
        recordCurrentCouplingDriver();
        refreshFlightReadout();
        if(taxiReturnPending && getPassengers().isEmpty()){stopForParking();if(ticksExisted%3==0)VehicleNetwork.state(this);return;}
        for(BlockPos at:java.util.Collections.singletonList(VehicleControlSettings.position(structure))){
            net.minecraft.tileentity.TileEntity seat=view.getTileEntity(at);
            if(seat instanceof com.vandorlabs.tiles.TileEntityPilotSeat) {
                com.vandorlabs.tiles.TileEntityPilotSeat pilot=(com.vandorlabs.tiles.TileEntityPilotSeat)seat;
                int requested=pilot.pollMode(world);if(requested>=0)VehicleService.modeSignal(pilot,this,requested);int edge=pilot.poll(world);
                if(edge>=0 || requested>=0) {
                    java.util.List<VehicleStructure.Cell> updated=new java.util.ArrayList<>();
                    for(VehicleStructure.Cell cell:structure.cells)updated.add(cell.pos.equals(at)?new VehicleStructure.Cell(cell.pos,cell.state,pilot.writeToNBT(new NBTTagCompound())):cell);
                    structure=new VehicleStructure(updated,structure.seat,structure.facing,structure.gearCount);view.update(structure);encoded=null;
                    if(edge==1)VehicleService.signal(pilot,this);
                }
            }
        }
        boolean automated=!flying()&&!takeoffRoll()&&autopilot.tick();if(isDead)return;
        VehicleControls.Command command=serverInputs.poll();
        if(command!=null && !automated){forward=command.forward;strafe=command.strafe;brake=command.brake;}
        boolean controlled=getControllingPassenger() instanceof EntityPlayer && world.getTotalWorldTime()-lastInput<=10;
        if(flying())advanceFlight(controlled);
        else if(takeoffRoll())advanceTakeoff(controlled);
        else advance(automated||controlled);
        if(ticksExisted%2==0)VehicleNetwork.state(this);
    }
    /** Independent round-robin budgets keep both exhaust groups continuous on large craft. */
    private void emitFlightParticles(boolean lift,Vec3d direction){
        java.util.List<VehicleStructure.Cell> cells=lift?structure.liftCells:structure.exhaustCells;
        int emitted=0;
        for(int checked=0;checked<cells.size()&&emitted<2;checked++){
            int cursor=lift?liftExhaustCursor++:exhaustCursor++;
            VehicleStructure.Cell cell=cells.get(Math.floorMod(cursor,cells.size()));
            if(!structure.isActive(cell.pos))continue;
            if(!lift&&(VehicleFlight.lift(cell.state.getBlock())||!VehicleFlight.opposes(cell.state,direction)))continue;
            emitted++;
            EnumFacing face=cell.state.getValue(com.vandorlabs.blocks.BlockPropulsionLight.FACING);
            Vec3d center=toWorld(new Vec3d(cell.pos).addVector(.5,.5,.5));
            int style=com.vandorlabs.tiles.TileEntityRedstoneLight.particleStyle(cell.state.getBlock().getRegistryName().getResourcePath());
            com.vandorlabs.VandorLabs.proxy.spawnRotatedThrusterParticle(world,center.x,center.y,center.z,face,
                    com.vandorlabs.tiles.TileEntityRedstoneLight.particleColor(style),style,
                    com.vandorlabs.tiles.TileEntityRedstoneLight.particleSpeed(style),.65F,rotationYaw);
        }
    }
    private void advance(boolean active) {
        long started=VehicleDiagnostics.ENABLED?System.nanoTime():0;float before=rotationYaw;
        collision.begin(world,this);
        boolean articulated=articulation.begin();
        try { advanceController(active); } finally { if(articulated)articulation.end();collision.end();if(VehicleDiagnostics.ENABLED)diagnostics.step(System.nanoTime()-started,collision.terrainQueries,MathHelper.wrapDegrees(rotationYaw-before)); }
    }

    private void advanceTakeoff(boolean controlled){
        if(!controlled||brake||!flightReadout.canFly()){
            flightPhase=NO_FLIGHT;
            advance(controlled);
            return;
        }
        double before=driveSpeed,x=posX,z=posZ;
        advance(true);
        if(!hasGroundContact()||driveSpeed>.05&&posX==x&&posZ==z||before>.05&&driveSpeed==0){
            flightPhase=NO_FLIGHT;
            return;
        }
        if(driveSpeed>=FlightEnvelope.TAKEOFF_SPEED&&forward<0){
            flightPhase=POWERED_FLIGHT;
            double length=structure.facing.getAxis()==EnumFacing.Axis.X?structure.bounds.maxX:structure.bounds.maxZ;
            initialClimb=Math.max(5,2+(int)Math.ceil(length*.5*Math.sin(Math.toRadians(3))/.17));
            applyFlightPose(new VehiclePose(posX,posY,posZ,rotationYaw,0,structure.facing));
            VehicleLandingGear.refreshSignals(this);
        }
    }

    private void advanceFlight(boolean controlled){
        if(!descendingFromFlight()&&(!flightReadout.canFly()||getControllingPassenger()==null))beginFlightRecovery(RECOVER_FLIGHT);
        cruise.preloadTerrain();
        double altitude=flightAltitude();
        double ceiling=Math.min(flightReadout.maximumAltitude(),flightMotion.ceiling(rotationYaw,flightPitch));
        if(descendingFromFlight()||altitude>ceiling+.02){advanceFlightDescent(ceiling);return;}
        landedFromFlight=false;onGround=false;
        double response=FlightEnvelope.response(flightReadout.effectiveMass,flightReadout.thrust);
        double speed=FlightEnvelope.cruiseSpeed(altitude);
        driveSpeed=approach(driveSpeed,speed,FlightEnvelope.acceleration(flightReadout.effectiveMass,flightReadout.thrust)*(1+speed/10));
        float yaw=rotationYaw+(controlled?strafe*(float)(.9+.55*response):0);
        float pitch=MathHelper.clamp(flightPitch-(controlled?forward*(float)(.6+.38*response):0),-FlightEnvelope.MAX_PITCH,FlightEnvelope.MAX_PITCH);
        // Lift clear of the runway before tilting a long fuselage.
        boolean launching=initialClimb>0;
        if(launching){pitch=initialClimb<=2?3:0;initialClimb--;}
        double radians=Math.toRadians(pitch),angle=Math.toRadians(yaw);
        EnumFacing front=structure.facing;
        double horizontal=Math.cos(radians)*driveSpeed;
        motionX=(front.getFrontOffsetX()*Math.cos(angle)-front.getFrontOffsetZ()*Math.sin(angle))*horizontal;
        motionZ=(front.getFrontOffsetX()*Math.sin(angle)+front.getFrontOffsetZ()*Math.cos(angle))*horizontal;
        ceiling=Math.min(ceiling,flightMotion.ceiling(yaw,pitch));
        motionY=Math.min(launching?.17:Math.sin(radians)*driveSpeed,Math.max(0,ceiling-altitude));
        VehicleFlightMotion.Result result=moveFlight(motionX,motionY,motionZ,yaw,pitch);
        if(result==VehicleFlightMotion.Result.MOVED)return;
        if(result==VehicleFlightMotion.Result.OBSTRUCTED){beginFlightRecovery(RECOVER_FLIGHT);driveSpeed=0;}
        motionX=motionY=motionZ=0;
    }

    private VehicleFlightMotion.Result moveFlight(double dx,double dy,double dz,float yaw,float pitch){
        if(cruise.active()&&cruise.move(dx,dy,dz)){
            applyFlightPose(new VehiclePose(posX,posY,posZ,yaw,pitch,structure.facing));
            return VehicleFlightMotion.Result.MOVED;
        }
        boolean enteringCruise=dimension==0&&dy>0&&posY+dy>FlightEnvelope.WORLD_CEILING;
        VehicleFlightMotion.Result result=flightMotion.move(dx,dy,dz,yaw,pitch);
        if(enteringCruise&&result==VehicleFlightMotion.Result.MOVED)cruise.move(0,0,0);
        return result;
    }

    private void advanceFlightDescent(double ceiling){
        motionX=motionZ=0;
        driveSpeed=approach(driveSpeed,0,Math.max(.09,driveSpeed/15));
        double altitude=flightAltitude();
        boolean recovery=descendingFromFlight();
        boolean resumeHover=recovery&&flightPhase!=RETURN_TAXI&&canResumeHover();
        double target=recovery?(resumeHover?(cruisingRecovery?FlightEnvelope.REENTRY_ALTITUDE:altitude):0):ceiling;
        if(cruise.active()){
            motionY=Math.max(-Math.min(3,.26+altitude/3500),target-altitude);
            moveFlight(0,motionY,0,rotationYaw,(float)approach(flightPitch,0,1.5));
            return;
        }
        if(flightPitch!=0){
            float level=(float)approach(flightPitch,0,1.5);
            motionY=0;
            if(moveFlight(0,0,0,rotationYaw,level)==VehicleFlightMotion.Result.MOVED)return;
            // A nose against terrain may need a small lift before it can level.
            if(moveFlight(0,.12,0,rotationYaw,level)==VehicleFlightMotion.Result.MOVED)return;
        }
        if(resumeHover&&altitude<=target+.01&&Math.abs(flightPitch)<.001){finishFlight(true);return;}
        if(landedFromFlight){if(VehicleLandingGear.extended(this)&&VehicleSkiTraction.allowed(this))finishFlight(false);return;}
        double dy=Math.max(-.24,target-altitude);
        if(dy>=0){motionY=0;return;}
        if(flightPitch!=0){
            if(moveFlight(0,dy,0,rotationYaw,flightPitch)==VehicleFlightMotion.Result.MOVED)return;
            return;
        }
        if(posY>=FlightEnvelope.WORLD_CEILING&&posY+collision.bounds.minY+dy>=FlightEnvelope.WORLD_CEILING){
            moveFlight(0,dy,0,rotationYaw,0);
            return;
        }
        collision.begin(world,this);
        boolean articulated=articulation.begin();
        try{
            double accepted=collision.clip(world,this,posX,posY,posZ,dy,1,rotationYaw);
            setPosition(posX,posY+accepted,posZ);view.origin=new BlockPos(posX,posY,posZ);
            onGround=accepted>dy+1e-6;motionY=onGround?0:accepted;fallDistance=0;velocityChanged=true;
        }finally{if(articulated)articulation.end();collision.end();}
        if(onGround){
            if(resumeHover){finishFlight(true);return;}
            if(VehicleLandingGear.extended(this)&&VehicleSkiTraction.allowed(this))finishFlight(false);
            else{beginFlightRecovery(RECOVER_FLIGHT);landedFromFlight=true;driveSpeed=0;}
        }
    }

    private void finishFlight(boolean toHover) {
        flightPhase=NO_FLIGHT;landedFromFlight=false;cruisingRecovery=false;flightPitch=0;hover=toHover;view.hover=toHover;
        holdHoverPosition();
        if(toHover)VehicleLandingGear.refreshSignals(this);
    }
    public double cruiseSpeed(){
        if(hover)return HOVER_CRUISE_SPEED;
        return structure.activeThruster()?CRUISE_SPEED:UNPOWERED_TAXI_SPEED;
    }
    private void advanceController(boolean active) {
        boolean boost=hover&&structure.activeThruster()&&active&&flightSprint&&!autopilot.enabled()&&world.getTotalWorldTime()-verticalAt<=10;
        if(!world.isRemote)dataManager.set(HOVER_EXHAUST,boost);
        double cruise=cruiseSpeed();
        double limit=hover?HOVER_SPRINT_SPEED:takeoffRoll()?Math.max(cruise,FlightEnvelope.TAKEOFF_SPEED):cruise;
        driveSpeed=approach(driveSpeed,takeoffRoll()?limit:active&&(!brake || hover)?forward*(boost?limit:cruise):0,
                takeoffRoll()?FlightEnvelope.acceleration(flightReadout.effectiveMass,flightReadout.thrust):brake||!active?.045:forward==0?.008:boost?.028:.018);
        driveSpeed=MathHelper.clamp(driveSpeed,-limit,limit);
        steering=(float)approach(steering,active?strafe:0,.18);
        // Hover can yaw in place; the route controller owns its own turns.
        // Both modes reverse steering while travelling backwards.
        float change=VehicleControls.yawChange(steering,driveSpeed,hover,autopilot.enabled(),hover?VehicleSteering.DEFAULT:taxiSteeringProfile(),onGround);
        float turned=collision.turn(world,this,posX,posY,posZ,rotationYaw,change);
        if(hover||!structure.activeSkisOnly()||turned==rotationYaw||VehicleSkiTraction.onIce(this,posX,posY,posZ,turned))rotationYaw=turned;
        if(articulation.active())articulation.applyTurn();
        EnumFacing front=structure.facing;double angle=Math.toRadians(rotationYaw),c=Math.cos(angle),s=Math.sin(angle);
        motionX=(front.getFrontOffsetX()*c-front.getFrontOffsetZ()*s)*driveSpeed;
        motionZ=(front.getFrontOffsetX()*s+front.getFrontOffsetZ()*c)*driveSpeed;
        if(hover){
            if(!autopilot.enabled() && active && world.getTotalWorldTime()-verticalAt<=10)hoverTarget+=vertical*.18;
            hoverTarget=MathHelper.clamp(hoverTarget,1,world.getHeight()-structure.bounds.maxY);
            motionY=MathHelper.clamp((hoverTarget-posY)*.3,-.18,.18);
        }else motionY=Math.max(-.5,motionY-.04);
        if(!(articulation.active()?articulation.loaded(posX,posY,posZ,motionX,motionY,motionZ,rotationYaw):collision.loaded(world,posX,posY,posZ,motionX,motionY,motionZ,rotationYaw))){motionX=motionY=motionZ=0;return;}
        double y=collision.clip(world,this,posX,posY,posZ,motionY,1,rotationYaw);
        onGround=motionY<0 && y!=motionY;
        double x=collision.clip(world,this,posX,posY+y,posZ,motionX,0,rotationYaw);
        double z=collision.clip(world,this,posX+x,posY+y,posZ,motionZ,2,rotationYaw);
        // A half-block step is allowed only while grounded and with headroom.
        if(onGround && (x!=motionX || z!=motionZ)) {
            double up=collision.clip(world,this,posX,posY,posZ,.5,1,rotationYaw);
            if(up>.001) {
                double sx=collision.clip(world,this,posX,posY+up,posZ,motionX,0,rotationYaw);
                double sz=collision.clip(world,this,posX+sx,posY+up,posZ,motionZ,2,rotationYaw);
                double down=collision.clip(world,this,posX+sx,posY+up,posZ+sz,-up-.04,1,rotationYaw);
                if(sx*sx+sz*sz>x*x+z*z && down> -up-.04) {x=sx;z=sz;y=up+down;}
            }
        }
        if(!hover&&structure.activeSkisOnly()&&(x!=0||z!=0)&&!VehicleSkiTraction.onIce(this,posX+x,posY+y,posZ+z,rotationYaw)){x=z=0;driveSpeed=0;}
        if(x!=motionX || z!=motionZ)driveSpeed=0;
        if(x!=motionX)motionX=0;if(z!=motionZ)motionZ=0;if(y!=motionY)motionY=0;
        setPosition(posX+x,posY+y,posZ+z);view.origin=new BlockPos(posX,posY,posZ);
        velocityChanged=true;fallDistance=0;
    }
    private static double approach(double current,double target,double amount){return current<target?Math.min(target,current+amount):Math.max(target,current-amount);}
}
