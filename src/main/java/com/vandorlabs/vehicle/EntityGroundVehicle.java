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
    public VehicleStructure structure;
    public VehicleWorld view;
    public VehicleCollision collision;
    public long transferEpoch;
    public boolean ownershipBlocked;
    private byte forward, strafe;
    private boolean brake;
    private long lastInput=-100;
    private int inputSequence=-1, interpolation;
    private int lastAcknowledgement=-1;
    private double targetX,targetY,targetZ;
    private float targetYaw;
    public double driveSpeed;
    public float steering;
    public boolean localDriver;
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
        if(localDriver!=value){localDriver=value;pendingInputs.clear();lastAcknowledgement=-1;correctionX=correctionY=correctionZ=0;}
    }
    public void predict(int sequence,byte f,byte s,boolean b) {
        if(pendingInputs.size()>=128)pendingInputs.removeFirst();pendingInputs.addLast(new Control(sequence,f,s,b));
        forward=f;strafe=s;brake=b;
    }
    public void authoritative(VehicleNetwork.Packet p) {
        if(localDriver && p.sequence<=lastAcknowledgement)return;
        lastAcknowledgement=p.sequence;
        if(!localDriver){driveSpeed=p.speed;steering=p.steering;motionY=p.dy;targetX=p.x;targetY=p.y;targetZ=p.z;targetYaw=p.yaw;interpolation=3;return;}
        double oldX=posX+correctionX,oldY=posY+correctionY,oldZ=posZ+correctionZ;
        pendingInputs.removeIf(c->c.sequence<=p.sequence);
        rotationYaw=p.yaw;driveSpeed=p.speed;steering=p.steering;motionY=p.dy;
        setPosition(p.x,p.y,p.z);
        for(Control input:pendingInputs){forward=input.f;strafe=input.s;brake=input.brake;advance(true);}
        double error=(oldX-posX)*(oldX-posX)+(oldY-posY)*(oldY-posY)+(oldZ-posZ)*(oldZ-posZ);
        correctionX=error<16?oldX-posX:0;correctionY=error<16?oldY-posY:0;correctionZ=error<16?oldZ-posZ:0;
    }
    public Vec3d renderCorrection(){return new Vec3d(correctionX,correctionY,correctionZ);}
    @Override public AxisAlignedBB getRenderBoundingBox() {
        if(structure==null)return getEntityBoundingBox();
        double x=structure.bounds.maxX/2,z=structure.bounds.maxZ/2,r=Math.hypot(x,z)+2;
        AxisAlignedBB local=new AxisAlignedBB(x-r,-2,z-r,x+r,structure.bounds.maxY+2,z+r);
        return local.offset(posX+correctionX,posY+correctionY,posZ+correctionZ).union(local.offset(prevPosX,prevPosY,prevPosZ));
    }
    public int acknowledgedInput(){return inputSequence;}
    private byte[] encoded;
    private float propulsion;
    private NBTTagCompound unavailable;
    public EntityGroundVehicle(World world){super(world);setSize(1,1);isImmuneToFire=true;preventEntitySpawning=true;}
    public void install(VehicleStructure snapshot) {
        structure=snapshot;if(view==null)view=new VehicleWorld(world,snapshot);else view.update(snapshot);collision=new VehicleCollision(view);encoded=null;
        setPosition(posX,posY,posZ);
    }
    public byte[] snapshotBytes() throws java.io.IOException {if(encoded==null)encoded=structure.encode();return encoded;}
    @Override protected void entityInit(){}
    @Override public void writeSpawnData(io.netty.buffer.ByteBuf buffer){buffer.writeInt(VehicleStructure.SCHEMA);}
    @Override public void readSpawnData(io.netty.buffer.ByteBuf buffer){if(buffer.readInt()==VehicleStructure.SCHEMA)VehicleNetwork.request(this);}
    @Override protected void writeEntityToNBT(NBTTagCompound tag){tag.setLong("TransferEpoch",transferEpoch);if(structure!=null)tag.setTag("Craft",structure.write());else if(unavailable!=null)tag.setTag("Craft",unavailable.copy());}
    @Override protected void readEntityFromNBT(NBTTagCompound tag){transferEpoch=tag.getLong("TransferEpoch");try{install(VehicleStructure.read(tag.getCompoundTag("Craft")));net.minecraft.tileentity.TileEntity pilot=view.getTileEntity(structure.seat);if(pilot instanceof com.vandorlabs.tiles.TileEntityPilotSeat)((com.vandorlabs.tiles.TileEntityPilotSeat)pilot).resumeAfterLoad(world);}catch(Exception e){unavailable=tag.getCompoundTag("Craft").copy();com.vandorlabs.VandorLabs.logger.error("Vehicle data retained but unavailable: "+getUniqueID(),e);}motionX=motionY=motionZ=0;}
    @Override public void setPosition(double x,double y,double z){super.setPosition(x,y,z);if(collision!=null)setEntityBoundingBox(collision.bounds(rotationYaw).offset(x,y,z));}
    public void input(EntityPlayerMP player,int sequence,byte forward,byte strafe,boolean brake) {
        if(getControllingPassenger()!=player || sequence<=inputSequence || forward< -1 || forward>1 || strafe< -1 || strafe>1)return;
        this.inputSequence=sequence;this.forward=forward;this.strafe=strafe;this.brake=brake;lastInput=world.getTotalWorldTime();
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
        if(VehicleDoors.interact(this,player))return true;
        if(!player.isSneaking() && structure!=null && getPassengers().isEmpty()) {
            if(!world.isRemote) {inputSequence=-1;forward=strafe=0;lastInput=-100;player.startRiding(this);}
            return true;
        }
        return false;
    }
    @Override public void updatePassenger(Entity passenger) {
        if(isPassenger(passenger) && structure!=null) {
            Vec3d at=toWorld(new Vec3d(structure.seat.getX()+.5,structure.seat.getY()+BlockPilotSeat.SEAT_HEIGHT+.35-EntityChairSeat.RIDER_PELVIS_OFFSET+passenger.getYOffset(),structure.seat.getZ()+.5));
            passenger.setPosition(at.x+correctionX,at.y+correctionY,at.z+correctionZ);
        }
    }
    @Override public double getMountedYOffset(){return 0;}
    @Override protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if(world.isRemote || collision==null)return;
        com.vandorlabs.entity.SafeDismount.move(passenger,com.vandorlabs.entity.SafeDismount.find(world,passenger,getEntityBoundingBox()),getEntityBoundingBox());
    }
    @Override public boolean canBeCollidedWith(){return !isDead;}
    @Override public boolean canRiderInteract(){return true;}
    @Override public boolean canBePushed(){return false;}
    @Override public AxisAlignedBB getCollisionBoundingBox(){return null;}
    @Override public boolean attackEntityFrom(DamageSource source,float damage){return false;}
    @Override public void setPortal(BlockPos p){}
    @Override public void setPositionAndRotationDirect(double x,double y,double z,float yaw,float pitch,int steps,boolean teleport) {
        if(localDriver)return;targetX=x;targetY=y;targetZ=z;targetYaw=yaw;interpolation=teleport?1:Math.max(1,steps);
    }
    public boolean stopped(){return motionX*motionX+motionY*motionY+motionZ*motionZ<.0001;}
    @Override public void onUpdate() {
        super.onUpdate();rotationPitch=prevRotationPitch=0;
        if(world.isRemote) {
            correctionX*=.65;correctionY*=.65;correctionZ*=.65;
            if(structure==null && ticksExisted%40==1)VehicleNetwork.request(this);
            if(localDriver && structure!=null)advance(true);
            else if(interpolation>0){rotationYaw+=MathHelper.wrapDegrees(targetYaw-rotationYaw)/interpolation;setPosition(posX+(targetX-posX)/interpolation,posY+(targetY-posY)/interpolation,posZ+(targetZ-posZ)/interpolation);interpolation--;}
            if(view!=null){view.origin=new BlockPos(posX,posY,posZ);
                double speed=Math.hypot(posX-prevPosX,posZ-prevPosZ);
                propulsion=(float)approach(propulsion,speed>.002?15:0,1.5);view.propulsionLevel=Math.round(propulsion);}
            return;
        }
        if(structure==null || ownershipBlocked){motionX=motionY=motionZ=0;return;}
        net.minecraft.tileentity.TileEntity seat=view.getTileEntity(structure.seat);
        if(seat instanceof com.vandorlabs.tiles.TileEntityPilotSeat) {
            com.vandorlabs.tiles.TileEntityPilotSeat pilot=(com.vandorlabs.tiles.TileEntityPilotSeat)seat;int edge=pilot.poll(world);
            if(edge>=0) {
                java.util.List<VehicleStructure.Cell> updated=new java.util.ArrayList<>();
                for(VehicleStructure.Cell cell:structure.cells)updated.add(cell.pos.equals(structure.seat)?new VehicleStructure.Cell(cell.pos,cell.state,pilot.writeToNBT(new NBTTagCompound())):cell);
                structure=new VehicleStructure(updated,structure.seat,structure.facing,structure.gearCount);view.update(structure);encoded=null;
                if(edge==1)VehicleService.signal(pilot,this);
            }
        }
        advance(getControllingPassenger() instanceof EntityPlayer && world.getTotalWorldTime()-lastInput<=10);
        if(ticksExisted%2==0)VehicleNetwork.state(this);
    }
    private void advance(boolean active) {
        driveSpeed=approach(driveSpeed,active&&!brake?forward*.28:0,brake||!active?.045:forward==0?.008:.018);
        steering=(float)approach(steering,active?strafe:0,.18);
        // Steering falls off with speed; reversing reverses the direction of the turn.
        float change=(float)(steering*driveSpeed*11*(1-.45*Math.abs(driveSpeed)/.28));
        rotationYaw=collision.turn(world,this,posX,posY,posZ,rotationYaw,change);
        EnumFacing front=structure.facing;double angle=Math.toRadians(rotationYaw),c=Math.cos(angle),s=Math.sin(angle);
        motionX=(front.getFrontOffsetX()*c-front.getFrontOffsetZ()*s)*driveSpeed;
        motionZ=(front.getFrontOffsetX()*s+front.getFrontOffsetZ()*c)*driveSpeed;
        motionY=Math.max(-.5,motionY-.04);
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
