package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockPilotSeat;
import com.vandorlabs.entity.EntityChairSeat;
import com.vandorlabs.items.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** One persistent, ground craft. All movement decisions are server authoritative. */
public final class EntityGroundVehicle extends Entity implements net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData {
    public final VehicleDiagnostics diagnostics=new VehicleDiagnostics();
    public VehicleStructure structure;
    public VehicleWorld view;
    public VehicleCollision collision;
    public final VehicleSignals signals=new VehicleSignals(this);
    public long transferEpoch;
    public boolean ownershipBlocked;
    public boolean transferInProgress;
    public boolean taxiReturnPending;
    public java.util.UUID taxiReturnActor;
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
    public double driveSpeed;
    public float steering;
    public boolean localDriver;
    public boolean hover;
    public double hoverTarget;
    public boolean flightDismount;
    public void exitFlight(EntityPlayerMP player){
        if(getControllingPassenger()!=player)return;
        if(hover && !autopilot.enabled())holdHoverPosition();
        flightDismount=true;try{player.dismountRidingEntity();}finally{flightDismount=false;}
    }
    private byte vertical;
    private long verticalAt=-100,clearanceAt=-100;
    private double clearance=Double.POSITIVE_INFINITY;
    public final VehicleAutopilot autopilot=new VehicleAutopilot(this);
    public double groundClearance(){if(world.getTotalWorldTime()-clearanceAt>=5){clearance=VehicleFlight.clearance(this);clearanceAt=world.getTotalWorldTime();}return clearance;}
    public boolean setHover(boolean value){
        if(value==hover)return true;
        if(value && (!VehicleFlight.canHover(structure)))return false;
        if(value)try{VehicleFlight.requireThruster(structure);}catch(IllegalArgumentException unavailable){return false;}
        if(!value){clearance=VehicleFlight.clearance(this);clearanceAt=world.getTotalWorldTime();if(clearance>=2)return false;}
        hover=value;hoverTarget=posY;motionY=0;vertical=0;taxiReturnPending=false;
        if(view!=null)view.hover=hover;
        return true;
    }
    public void flightInput(EntityPlayerMP player,byte direction,boolean toggle){
        if(getControllingPassenger()!=player || direction< -1 || direction>1 || autopilot.enabled())return;
        if(toggle && !setHover(!hover))VehicleNetwork.error(player,hover?"Descend to less than two blocks above solid ground before selecting Taxi.":"Hover requires three Antigravity Plates, Repulsor Arrays or Vertical Hover Thrusters.");
        vertical=direction;verticalAt=world.getTotalWorldTime();
    }
    public void autopilotControls(byte f,byte s,boolean b){forward=f;strafe=s;brake=b;}

    private double correctionX,correctionY,correctionZ;
    private final java.util.ArrayDeque<Control> pendingInputs=new java.util.ArrayDeque<>();
    private static final class Control {
        int sequence;byte f,s;boolean brake;
        Control(int n,byte f,byte s,boolean b){sequence=n;this.f=f;this.s=s;brake=b;}
    }
    public Vec3d toWorld(Vec3d local) {
        double px=structure.bounds.maxX/2,pz=structure.bounds.maxZ/2,c=Math.cos(Math.toRadians(rotationYaw)),s=Math.sin(Math.toRadians(rotationYaw));
        double x=local.x-px,z=local.z-pz;return new Vec3d(posX+px+c*x-s*z,posY+local.y,posZ+pz+s*x+c*z);
    }
    public Vec3d toLocal(Vec3d absolute) {
        double px=structure.bounds.maxX/2,pz=structure.bounds.maxZ/2,c=Math.cos(Math.toRadians(rotationYaw)),s=Math.sin(Math.toRadians(rotationYaw));
        double x=absolute.x-posX-px,z=absolute.z-posZ-pz;return new Vec3d(px+c*x+s*z,absolute.y-posY,pz-s*x+c*z);
    }
    public void setLocalDriver(boolean value) {
        if(localDriver!=value){localDriver=value;pendingInputs.clear();lastAcknowledgement=-1;lastServerTick=Long.MIN_VALUE;nextPrediction=null;correctionX=correctionY=correctionZ=0;correctionYaw=0;previousVisual=currentVisual=null;}
    }
    public void predict(int sequence,byte f,byte s,boolean b) {
        nextPrediction=new Control(sequence,f,s,b);
        forward=f;strafe=s;brake=b;
    }
    public void authoritative(VehicleNetwork.Packet p) {
        if(p.serverTick<=lastServerTick)return;
        hover=p.flag;if(view!=null)view.hover=hover;hoverTarget=p.y;
        lastServerTick=p.serverTick;
        lastAcknowledgement=p.sequence;
        if(!localDriver){driveSpeed=p.speed;steering=p.steering;motionY=p.dy;targetX=p.x;targetY=p.y;targetZ=p.z;targetYaw=p.yaw;interpolation=3;return;}
        double oldX=posX+correctionX,oldY=posY+correctionY,oldZ=posZ+correctionZ;
        float oldYaw=rotationYaw+correctionYaw;
        pendingInputs.removeIf(c->c.sequence<=p.sequence);
        rotationYaw=p.yaw;driveSpeed=p.speed;steering=p.steering;motionY=p.dy;
        setPosition(p.x,p.y,p.z);
        for(Control input:pendingInputs){forward=input.f;strafe=input.s;brake=input.brake;advance(true);}
        double error=(oldX-posX)*(oldX-posX)+(oldY-posY)*(oldY-posY)+(oldZ-posZ)*(oldZ-posZ);
        correctionX=error<16?oldX-posX:0;correctionY=error<16?oldY-posY:0;correctionZ=error<16?oldZ-posZ:0;
        diagnostics.correction(MathHelper.wrapDegrees(oldYaw-rotationYaw),pendingInputs.size());
        correctionYaw=error<16?MathHelper.wrapDegrees(oldYaw-rotationYaw):0;
        if(error>=16)previousVisual=currentVisual=null;
    }
    public Vec3d renderCorrection(){return new Vec3d(correctionX,correctionY,correctionZ);}
    public VehiclePose visualPose(float partial) {
        if(currentVisual==null)return new VehiclePose(posX,posY,posZ,rotationYaw);
        return previousVisual.interpolate(currentVisual,partial);
    }
    public Vec3d seatPosition(VehiclePose pose,Entity passenger) {
        return pose.world(new Vec3d(structure.seat.getX()+.5,structure.seat.getY()+BlockPilotSeat.seatHeight(view,structure.seat)+.35-EntityChairSeat.RIDER_PELVIS_OFFSET+passenger.getYOffset(),structure.seat.getZ()+.5),structure.bounds.maxX/2,structure.bounds.maxZ/2);
    }
    @Override public AxisAlignedBB getRenderBoundingBox() {
        if(structure==null)return getEntityBoundingBox();
        double x=structure.bounds.maxX/2,z=structure.bounds.maxZ/2,r=Math.hypot(x,z)+2;
        AxisAlignedBB local=new AxisAlignedBB(x-r,-2,z-r,x+r,structure.bounds.maxY+2,z+r);
        return local.offset(posX+correctionX,posY+correctionY,posZ+correctionZ).union(local.offset(prevPosX,prevPosY,prevPosZ)).union(getEntityBoundingBox().grow(1));
    }
    public int acknowledgedInput(){return serverInputs.acknowledged();}
    private byte[] encoded;
    private VehicleStructure rotationSource,rotatedSnapshot;private int rotationTurns;
    public VehicleStructure rotatedSnapshot(int turns) {
        if(turns==0)return structure;
        if(rotationSource!=structure || rotationTurns!=turns){rotatedSnapshot=VehicleRotation.rotate(structure,view,turns);rotationSource=structure;rotationTurns=turns;}
        return rotatedSnapshot;
    }
    private float propulsion;
    private NBTTagCompound unavailable;
    public EntityGroundVehicle(World world){super(world);setSize(1,1);isImmuneToFire=true;preventEntitySpawning=true;}
    public void install(VehicleStructure snapshot) {
        structure=snapshot;if(view==null)view=new VehicleWorld(world,snapshot);else view.update(snapshot);collision=new VehicleCollision(view);encoded=null;
        view.craft=this;view.hover=hover;if(!world.isRemote)view.controlUpdates=signals::changed;
        setPosition(posX,posY,posZ);
    }
    /** Preserve live canopy interpolation while publishing an immutable save/parking snapshot. */
    public void updateCanopies(VehicleStructure snapshot,boolean moved){
        structure=snapshot;view.structure=snapshot;encoded=null;
        if(moved){collision=new VehicleCollision(view);setPosition(posX,posY,posZ);}
    }
    public void updateSettings(VehicleStructure snapshot){
        NBTTagCompound oldSeat=structure.at(structure.seat).tileData(),newSeat=snapshot.at(snapshot.seat).tileData();
        boolean heightChanged=(oldSeat==null?0:oldSeat.getInteger("PilotHeight"))!=(newSeat==null?0:newSeat.getInteger("PilotHeight"));
        structure=snapshot;view.update(snapshot);encoded=null;
        if(heightChanged){collision=new VehicleCollision(view);setPosition(posX,posY,posZ);for(Entity passenger:getPassengers())updatePassenger(passenger);}
    }
    public byte[] snapshotBytes() throws java.io.IOException {if(encoded==null)encoded=structure.encode();return encoded;}
    @Override protected void entityInit(){}
    @Override public void writeSpawnData(io.netty.buffer.ByteBuf buffer){buffer.writeInt(VehicleStructure.SCHEMA);}
    @Override public void readSpawnData(io.netty.buffer.ByteBuf buffer){if(buffer.readInt()==VehicleStructure.SCHEMA)VehicleNetwork.request(this);}
    @Override protected void writeEntityToNBT(NBTTagCompound tag){signals.persist();tag.setTag("AutopilotNavigation",autopilot.write());tag.setBoolean("HoverMode",hover);tag.setDouble("HoverTarget",hoverTarget);tag.setLong("TransferEpoch",transferEpoch);tag.setBoolean("TaxiReturnPending",taxiReturnPending);if(taxiReturnActor!=null)tag.setUniqueId("TaxiReturnActor",taxiReturnActor);if(structure!=null)tag.setTag("Craft",structure.write());else if(unavailable!=null)tag.setTag("Craft",unavailable.copy());}
    @Override protected void readEntityFromNBT(NBTTagCompound tag){hover=tag.getBoolean("HoverMode");hoverTarget=tag.getDouble("HoverTarget");transferEpoch=tag.getLong("TransferEpoch");taxiReturnPending=tag.getBoolean("TaxiReturnPending");taxiReturnActor=tag.hasUniqueId("TaxiReturnActor")?tag.getUniqueId("TaxiReturnActor"):null;try{install(VehicleStructure.read(tag.getCompoundTag("Craft")));autopilot.read(tag.getCompoundTag("AutopilotNavigation"));net.minecraft.tileentity.TileEntity pilot=view.getTileEntity(structure.seat);if(pilot instanceof com.vandorlabs.tiles.TileEntityPilotSeat)((com.vandorlabs.tiles.TileEntityPilotSeat)pilot).resumeAfterLoad(world);}catch(Exception e){unavailable=tag.getCompoundTag("Craft").copy();com.vandorlabs.VandorLabs.logger.error("Vehicle data retained but unavailable: "+getUniqueID(),e);}motionX=motionY=motionZ=0;}
    @Override public void setPosition(double x,double y,double z){super.setPosition(x,y,z);if(collision!=null)setEntityBoundingBox(collision.bounds(rotationYaw).offset(x,y,z));VehicleLookup.update(this);}
    public void input(EntityPlayerMP player,int sequence,byte forward,byte strafe,boolean brake) {
        if(getControllingPassenger()!=player || !serverInputs.offer(sequence,forward,strafe,brake))return;
        lastInput=world.getTotalWorldTime();
    }
    @Override public Entity getControllingPassenger(){return getPassengers().isEmpty()?null:getPassengers().get(0);}
    // Vanilla otherwise sends client vehicle positions, overriding the server controller.
    @Override public boolean canPassengerSteer(){return false;}
    @Override protected boolean canFitPassenger(Entity passenger){return getPassengers().isEmpty();}
    @Override public boolean processInitialInteract(EntityPlayer player,EnumHand hand) {
        if(hand!=EnumHand.MAIN_HAND)return false;
        if(player.getHeldItem(hand).getItem()==ModItems.CONFIGURIZER) {
            if(!world.isRemote)VehicleService.previewParking((EntityPlayerMP)player,this);
            return true;
        }
        VehicleInteraction.Target target=VehicleInteraction.pointed(player);
        return target!=null && target.craft==this && VehicleInteraction.activate(player,target);
    }
    public boolean board(EntityPlayer player) {
        if(world.isRemote || isDead || ownershipBlocked || structure==null || player.isSpectator()
                || player.isDead || player.world!=world || !getPassengers().isEmpty())return false;
        if(!player.startRiding(this))return false;
        taxiReturnPending=false;
        serverInputs.clear();forward=strafe=0;brake=false;lastInput=-100;
        pendingInputs.clear();lastAcknowledgement=-1;
        return true;
    }
    @Override public void onAddedToWorld(){super.onAddedToWorld();signals.open();VehicleLookup.update(this);}
    @Override public void onRemovedFromWorld(){signals.close();VehicleLookup.remove(this);super.onRemovedFromWorld();}
    @Override public void setDead(){signals.close();VehicleLookup.remove(this);super.setDead();}
    @Override public void updatePassenger(Entity passenger) {
        if(isPassenger(passenger) && structure!=null) {
            Vec3d at=seatPosition(world.isRemote?visualPose(1):new VehiclePose(posX,posY,posZ,rotationYaw),passenger);
            passenger.setPosition(at.x,at.y,at.z);
            if(world.isRemote) {
                Vec3d before=seatPosition(visualPose(0),passenger);
                passenger.prevPosX=passenger.lastTickPosX=before.x;
                passenger.prevPosY=passenger.lastTickPosY=before.y;
                passenger.prevPosZ=passenger.lastTickPosZ=before.z;
            }
        }
    }
    @Override public double getMountedYOffset(){return 0;}
    @Override protected void removePassenger(Entity passenger) {
        boolean wasDriver=getControllingPassenger()==passenger;
        super.removePassenger(passenger);
        if(world.isRemote || collision==null)return;
        if(wasDriver && hover && !autopilot.enabled())holdHoverPosition();
        com.vandorlabs.entity.SafeDismount.move(passenger,com.vandorlabs.entity.SafeDismount.find(world,passenger,getEntityBoundingBox()),getEntityBoundingBox());
        if(!transferInProgress && !isDead && passenger instanceof EntityPlayerMP && !hover && !autopilot.enabled() && VehicleTaxiMode.enabled(structure))
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
        if(localDriver)return;
        // Custom STATE is the single observer interpolation stream after its first packet.
        if(lastServerTick!=Long.MIN_VALUE && !teleport)return;
        targetX=x;targetY=y;targetZ=z;targetYaw=yaw;interpolation=teleport?1:Math.max(1,steps);
        if(teleport){previousVisual=currentVisual=null;correctionX=correctionY=correctionZ=0;correctionYaw=0;}
    }
    public boolean stopped(){return motionX*motionX+motionY*motionY+motionZ*motionZ<.0001;}
    @Override public void onUpdate() {
        super.onUpdate();rotationPitch=prevRotationPitch=0;
        VehicleCanopies.tick(this);
        signals.tick();VehicleLandingGear.tick(this);
        if(world.isRemote) {
            previousVisual=currentVisual==null?new VehiclePose(posX,posY,posZ,rotationYaw):currentVisual;
            correctionX*=.65;correctionY*=.65;correctionZ*=.65;correctionYaw*=.65;
            if(structure==null && ticksExisted%40==1)VehicleNetwork.request(this);
            if(localDriver && structure!=null) {
                if(nextPrediction!=null) {
                    if(pendingInputs.size()>=VehicleControls.LIMIT)pendingInputs.removeFirst();
                    pendingInputs.addLast(nextPrediction);forward=nextPrediction.f;strafe=nextPrediction.s;brake=nextPrediction.brake;nextPrediction=null;
                }
                advance(true);
            }
            else if(interpolation>0){rotationYaw+=MathHelper.wrapDegrees(targetYaw-rotationYaw)/interpolation;setPosition(posX+(targetX-posX)/interpolation,posY+(targetY-posY)/interpolation,posZ+(targetZ-posZ)/interpolation);interpolation--;}
            currentVisual=new VehiclePose(posX+correctionX,posY+correctionY,posZ+correctionZ,rotationYaw+correctionYaw);
            if(view!=null){view.origin=new BlockPos(posX,posY,posZ);
                double speed=Math.hypot(posX-prevPosX,posZ-prevPosZ);
                propulsion=(float)approach(propulsion,speed>.002?15:0,1.5);view.propulsionLevel=Math.round(propulsion);view.hover=hover;
                if(hover && ticksExisted%8==0 && groundClearance()>1)
                    for(VehicleStructure.Cell cell:structure.cells)if(VehicleFlight.lift(cell.state.getBlock())) {
                        EnumFacing face=cell.state.getValue(com.vandorlabs.blocks.BlockPropulsionLight.FACING);
                        Vec3d center=toWorld(new Vec3d(cell.pos).addVector(.5,.5,.5));
                        int style=com.vandorlabs.tiles.TileEntityRedstoneLight.particleStyle(cell.state.getBlock().getRegistryName().getResourcePath());
                        com.vandorlabs.VandorLabs.proxy.spawnRotatedThrusterParticle(world,center.x,center.y,center.z,face,
                                com.vandorlabs.tiles.TileEntityRedstoneLight.particleColor(style),style,
                                com.vandorlabs.tiles.TileEntityRedstoneLight.particleSpeed(style),1F,rotationYaw);
                    }}
            return;
        }
        if(structure==null || ownershipBlocked){motionX=motionY=motionZ=0;return;}
        if(taxiReturnPending && getPassengers().isEmpty()){stopForParking();if(ticksExisted%3==0)VehicleNetwork.state(this);return;}
        net.minecraft.tileentity.TileEntity seat=view.getTileEntity(structure.seat);
        if(seat instanceof com.vandorlabs.tiles.TileEntityPilotSeat) {
            com.vandorlabs.tiles.TileEntityPilotSeat pilot=(com.vandorlabs.tiles.TileEntityPilotSeat)seat;int requested=pilot.pollMode(world);if(requested>=0)VehicleService.modeSignal(pilot,this,requested);int edge=pilot.poll(world);
            if(edge>=0 || requested>=0) {
                java.util.List<VehicleStructure.Cell> updated=new java.util.ArrayList<>();
                for(VehicleStructure.Cell cell:structure.cells)updated.add(cell.pos.equals(structure.seat)?new VehicleStructure.Cell(cell.pos,cell.state,pilot.writeToNBT(new NBTTagCompound())):cell);
                structure=new VehicleStructure(updated,structure.seat,structure.facing,structure.gearCount);view.update(structure);encoded=null;
                if(edge==1)VehicleService.signal(pilot,this);
            }
        }
        boolean automated=autopilot.tick();if(isDead)return;
        VehicleControls.Command command=serverInputs.poll();
        if(command!=null && !automated){forward=command.forward;strafe=command.strafe;brake=command.brake;}
        advance(automated || getControllingPassenger() instanceof EntityPlayer && world.getTotalWorldTime()-lastInput<=10);
        if(hover && groundClearance()>2)VehicleLandingGear.retract(this);
        if(ticksExisted%2==0)VehicleNetwork.state(this);
    }
    private void advance(boolean active) {
        long started=VehicleDiagnostics.ENABLED?System.nanoTime():0;float before=rotationYaw;
        collision.begin(world,this);
        try { advanceController(active); } finally { collision.end();if(VehicleDiagnostics.ENABLED)diagnostics.step(System.nanoTime()-started,collision.terrainQueries,MathHelper.wrapDegrees(rotationYaw-before)); }
    }
    private void advanceController(boolean active) {
        driveSpeed=approach(driveSpeed,active&&(!brake || hover)?forward*.28:0,brake||!active?.045:forward==0?.008:.018);
        steering=(float)approach(steering,active?strafe:0,.18);
        // Hover can yaw in place; the route controller owns its own turns.
        // Taxi steering falls off with speed and reverses when backing up.
        float change=hover?(autopilot.enabled()?0:steering*1.7F):(float)(steering*driveSpeed*11*(1-.45*Math.abs(driveSpeed)/.28));
        rotationYaw=collision.turn(world,this,posX,posY,posZ,rotationYaw,change);
        EnumFacing front=structure.facing;double angle=Math.toRadians(rotationYaw),c=Math.cos(angle),s=Math.sin(angle);
        motionX=(front.getFrontOffsetX()*c-front.getFrontOffsetZ()*s)*driveSpeed;
        motionZ=(front.getFrontOffsetX()*s+front.getFrontOffsetZ()*c)*driveSpeed;
        if(hover){
            if(!autopilot.enabled() && active && world.getTotalWorldTime()-verticalAt<=10)hoverTarget+=vertical*.18;
            hoverTarget=MathHelper.clamp(hoverTarget,1,world.getHeight()-structure.bounds.maxY);
            motionY=MathHelper.clamp((hoverTarget-posY)*.3,-.18,.18);
        }else motionY=Math.max(-.5,motionY-.04);
        if(!collision.loaded(world,posX,posY,posZ,motionX,motionY,motionZ,rotationYaw)){motionX=motionY=motionZ=0;return;}
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
        if(x!=motionX || z!=motionZ)driveSpeed=0;
        if(x!=motionX)motionX=0;if(z!=motionZ)motionZ=0;if(y!=motionY)motionY=0;
        setPosition(posX+x,posY+y,posZ+z);view.origin=new BlockPos(posX,posY,posZ);
        velocityChanged=true;fallDistance=0;
    }
    private static double approach(double current,double target,double amount){return current<target?Math.min(target,current+amount):Math.max(target,current-amount);}
}
