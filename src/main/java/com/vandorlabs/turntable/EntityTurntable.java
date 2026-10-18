package com.vandorlabs.turntable;

import com.vandorlabs.tiles.TileEntityTurntableController;
import com.vandorlabs.vehicle.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import java.util.*;

/** An inert captured display with one authoritative rotation and preserved source contents. */
public final class EntityTurntable extends Entity implements IEntityAdditionalSpawnData {
    private static final DataParameter<Float> ANGLE=EntityDataManager.createKey(EntityTurntable.class,DataSerializers.FLOAT);
    private static final DataParameter<Float> RATE=EntityDataManager.createKey(EntityTurntable.class,DataSerializers.FLOAT);
    public TurntableSnapshot snapshot;
    public VehicleWorld view;
    public BlockPos controller=BlockPos.ORIGIN;
    public UUID controllerKey,actor;
    private NBTTagCompound unavailable;
    private double angle,previous;
    private float observedAngle;
    private List<AxisAlignedBB> localBoxes=Collections.emptyList(),worldBoxes=Collections.emptyList();
    private double boxesAt=Double.NaN;
    private AxisAlignedBB localExtent=new AxisAlignedBB(0,0,0,1,1,1);
    private byte[] wire;
    private byte[] incoming;
    private int received,nextRequest;
    private final Map<UUID,Integer> requestTicks=new HashMap<>();
    public boolean allowSnapshotRequest(EntityPlayerMP player){Integer tick=requestTicks.put(player.getUniqueID(),ticksExisted);if(requestTicks.size()>256)requestTicks.entrySet().removeIf(e->ticksExisted-e.getValue()>200);return tick==null||tick<ticksExisted;}
    public EntityTurntable(World world){super(world);setSize(.1F,.1F);setNoGravity(true);preventEntitySpawning=true;}
    @Override protected void entityInit(){dataManager.register(ANGLE,0F);dataManager.register(RATE,0F);}
    public void install(TurntableSnapshot snapshot){
        this.snapshot=snapshot;unavailable=null;wire=null;incoming=null;
        setPosition(snapshot.layout.pivot.x,snapshot.layout.pivot.y,snapshot.layout.pivot.z);
        view=new VehicleWorld(world,snapshot.blocks);view.origin=snapshot.origin;
        try{localBoxes=new VehicleCollision(view).boxes;}catch(IllegalArgumentException empty){localBoxes=Collections.emptyList();}
        List<AxisAlignedBB> all=new ArrayList<>(localBoxes);AxisAlignedBB extent=snapshot.blocks.cells.isEmpty()?null:snapshot.blocks.bounds;
        for(TurntableSnapshot.FrozenEntity frozen:snapshot.entities){Entity copy=EntityList.createEntityFromNBT(frozen.data(),view);if(copy!=null){AxisAlignedBB box=copy.getEntityBoundingBox().offset(-snapshot.origin.getX(),-snapshot.origin.getY(),-snapshot.origin.getZ());extent=extent==null?box:extent.union(box);if(copy.canBeCollidedWith())all.add(box);}}
        localBoxes=Collections.unmodifiableList(all);localExtent=extent==null?new AxisAlignedBB(0,0,0,1,1,1):extent;
        boxesAt=Double.NaN;updateBounds();
    }
    public TileEntityTurntableController owner(){
        if(!world.isBlockLoaded(controller))return null;TileEntity tile=world.getTileEntity(controller);
        return tile instanceof TileEntityTurntableController && ((TileEntityTurntableController)tile).key.equals(controllerKey)?(TileEntityTurntableController)tile:null;
    }
    public double angle(float partial){double delta=((angle-previous+540)%360)-180;return TurntableGeometry.wrap(previous+delta*partial);}
    public double angle(){return angle;}
    public void setAngle(double value){angle=previous=TurntableGeometry.wrap(value);dataManager.set(ANGLE,(float)angle);boxesAt=Double.NaN;updateBounds();}
    private AxisAlignedBB transformed(AxisAlignedBB box,double angle){
        double dx=snapshot.origin.getX()-posX,dy=snapshot.origin.getY()-posY,dz=snapshot.origin.getZ()-posZ;
        double[] bounds=TurntableGeometry.bounds(new double[]{box.minX+dx,box.minY+dy,box.minZ+dz,box.maxX+dx,box.maxY+dy,box.maxZ+dz},snapshot.layout.basis,angle);
        return new AxisAlignedBB(bounds[0]+posX,bounds[1]+posY,bounds[2]+posZ,bounds[3]+posX,bounds[4]+posY,bounds[5]+posZ);
    }
    private List<AxisAlignedBB> boxes(double at){List<AxisAlignedBB> result=new ArrayList<>(localBoxes.size());for(AxisAlignedBB box:localBoxes)result.add(transformed(box,at));return result;}
    public List<AxisAlignedBB> collisionBoxes(){if(snapshot==null)return Collections.emptyList();if(boxesAt!=angle){worldBoxes=boxes(angle);boxesAt=angle;}return worldBoxes;}
    private void updateBounds(){
        if(snapshot==null){setEntityBoundingBox(new AxisAlignedBB(posX-32,posY-32,posZ-32,posX+32,posY+32,posZ+32));return;}
        AxisAlignedBB bounds=transformed(localExtent,angle);
        setEntityBoundingBox(bounds.grow(2));
    }
    @Override public void onUpdate(){
        super.onUpdate();motionX=motionY=motionZ=0;previous=angle;
        if(world.isRemote){
            float authoritative=dataManager.get(ANGLE);if(authoritative!=observedAngle){observedAngle=authoritative;angle=authoritative;}
            else angle=TurntableGeometry.wrap(angle+dataManager.get(RATE));
            if(snapshot==null && ticksExisted>=nextRequest){nextRequest=ticksExisted+20;com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(com.vandorlabs.network.MessageTurntableSnapshot.request(getEntityId(),received));}
            updateBounds();return;
        }
        if(snapshot==null||TurntableTransfer.pending(this)){dataManager.set(RATE,0F);TileEntityTurntableController controller=owner();if(controller!=null)controller.status(snapshot==null?"Captured component unavailable; saved contents retained":"Transfer waiting for recovery; contents retained");return;}
        TileEntityTurntableController owner=owner();
        if(owner==null){dataManager.set(RATE,0F);if(world.isBlockLoaded(controller) && ticksExisted%20==0)try{TurntableTransfer.restore((WorldServer)world,this,null);}catch(Exception blocked){/* Retain the complete display until its return space is available. */}return;}
        if(!getUniqueID().equals(owner.captureId)){dataManager.set(RATE,0F);return;}
        double rate=owner.running()?(owner.clockwise?1:-1)*TurntableGeometry.degreesPerTick(owner.rpm):0;
        if(rate!=0){
            double next=TurntableGeometry.wrap(angle+rate);List<AxisAlignedBB> before=collisionBoxes(),after=boxes(next);
            AxisAlignedBB swept=getEntityBoundingBox().union(transformed(localExtent,next));
            if(!world.isAreaLoaded(new BlockPos(swept.minX,swept.minY,swept.minZ),new BlockPos(swept.maxX,swept.maxY,swept.maxZ),false))rate=0;
            else {
                List<AxisAlignedBB> obstacles=world.getCollisionBoxes(this,swept);boolean blocked=false;
                double radius=snapshot.layout.size+snapshot.height,extra=radius*(1-Math.cos(Math.toRadians(Math.abs(rate)/2)));
                for(int i=0;i<after.size() && !blocked;i++){
                    AxisAlignedBB sweep=after.get(i).union(before.get(i)).grow(snapshot.layout.normal.getFrontOffsetX()==0?extra:0,snapshot.layout.normal.getFrontOffsetY()==0?extra:0,snapshot.layout.normal.getFrontOffsetZ()==0?extra:0).shrink(.0001);
                    for(AxisAlignedBB obstacle:obstacles)if(obstacle.intersects(sweep)){blocked=true;break;}
                }
                if(blocked || !carry(before,rate)){rate=0;owner.status("Rotation blocked; clear the moving contents and riders");}
            }
            if(rate!=0){angle=next;worldBoxes=after;boxesAt=angle;owner.status("Rotating");}
        }else owner.status("Captured; stopped");
        dataManager.set(ANGLE,(float)angle);dataManager.set(RATE,(float)rate);updateBounds();
    }
    private boolean carry(List<AxisAlignedBB> before,double delta){
        List<Entity> riders=new ArrayList<>();List<Vec3d> targets=new ArrayList<>();
        for(Entity rider:world.getEntitiesWithinAABBExcludingEntity(this,getEntityBoundingBox().grow(1))){
            if(rider instanceof EntityTurntable || rider.isDead || rider.isRiding() || rider instanceof EntityPlayer && ((EntityPlayer)rider).capabilities.isFlying)continue;
            AxisAlignedBB body=rider.getEntityBoundingBox();boolean supported=false;
            for(AxisAlignedBB box:before)if(Math.abs(body.minY-box.maxY)<.12 && body.maxX>box.minX+.001 && body.minX<box.maxX-.001 && body.maxZ>box.minZ+.001 && body.minZ<box.maxZ-.001){supported=true;break;}
            if(!supported)continue;
            double[] p=TurntableGeometry.rotate(rider.posX-posX,rider.posY-posY,rider.posZ-posZ,snapshot.layout.basis,delta);
            Vec3d target=new Vec3d(posX+p[0],posY+p[1],posZ+p[2]);AxisAlignedBB moved=body.offset(target.x-rider.posX,target.y-rider.posY,target.z-rider.posZ);
            if(!TurntableEvents.foreignObstacles(world,this,rider,moved.shrink(.0001)).isEmpty())return false;
            riders.add(rider);targets.add(target);
        }
        for(int i=0;i<riders.size();i++){
            Entity rider=riders.get(i);Vec3d target=targets.get(i);float yaw=rider.rotationYaw+(float)(delta*snapshot.layout.normal.getFrontOffsetY());
            if(rider instanceof EntityPlayerMP)((EntityPlayerMP)rider).connection.setPlayerLocation(target.x,target.y,target.z,yaw,rider.rotationPitch);
            else{rider.setPositionAndUpdate(target.x,target.y,target.z);rider.rotationYaw=yaw;}
            rider.fallDistance=0;rider.onGround=true;
        }
        return true;
    }
    @Override public boolean processInitialInteract(EntityPlayer player,EnumHand hand){
        if(hand!=EnumHand.MAIN_HAND)return false;
        if(!world.isRemote && player instanceof EntityPlayerMP)try{TurntableTransfer.restore((WorldServer)world,this,(EntityPlayerMP)player);}catch(Exception blocked){player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(blocked.getMessage()),true);}return true;
    }
    @Override public boolean canBeCollidedWith(){return !isDead;}
    @Override public boolean canBePushed(){return false;}
    @Override public AxisAlignedBB getCollisionBoundingBox(){return null;}
    @Override public boolean attackEntityFrom(DamageSource source,float amount){return false;}
    @Override public void setPortal(BlockPos pos){}
    @Override protected void writeEntityToNBT(NBTTagCompound tag){
        tag.setLong("Controller",controller.toLong());if(controllerKey!=null)tag.setUniqueId("ControllerKey",controllerKey);if(actor!=null)tag.setUniqueId("Actor",actor);tag.setDouble("Angle",angle);
        if(snapshot!=null)tag.setTag("Snapshot",snapshot.write());else if(unavailable!=null)tag.setTag("Snapshot",unavailable.copy());
    }
    @Override protected void readEntityFromNBT(NBTTagCompound tag){
        controller=BlockPos.fromLong(tag.getLong("Controller"));controllerKey=tag.hasUniqueId("ControllerKey")?tag.getUniqueId("ControllerKey"):null;actor=tag.hasUniqueId("Actor")?tag.getUniqueId("Actor"):null;
        angle=previous=Double.isFinite(tag.getDouble("Angle"))?TurntableGeometry.wrap(tag.getDouble("Angle")):0;
        if(tag.hasKey("Snapshot",10)){unavailable=tag.getCompoundTag("Snapshot").copy();try{install(TurntableSnapshot.read(unavailable));}catch(RuntimeException missing){com.vandorlabs.VandorLabs.logger.warn("Turntable snapshot retained pending unavailable components",missing);}}
        dataManager.set(ANGLE,(float)angle);
    }
    public byte[] wire()throws java.io.IOException{if(wire==null && snapshot!=null)wire=snapshot.encode();return wire;}
    public void receive(int total,int offset,byte[] part)throws java.io.IOException{
        if(total<1 || total>TurntableSnapshot.MAX_BYTES || offset!=received || part.length<1 || offset+part.length>total)return;
        if(incoming==null){if(offset!=0)return;incoming=new byte[total];}
        if(incoming.length!=total)return;System.arraycopy(part,0,incoming,offset,part.length);received+=part.length;nextRequest=ticksExisted;
        if(received==total){try{TurntableSnapshot complete=TurntableSnapshot.decode(incoming);install(complete);}finally{incoming=null;received=0;nextRequest=ticksExisted+20;}}
    }
    @Override public void writeSpawnData(ByteBuf buffer){buffer.writeLong(controller.toLong());buffer.writeLong(controllerKey==null?0:controllerKey.getMostSignificantBits());buffer.writeLong(controllerKey==null?0:controllerKey.getLeastSignificantBits());buffer.writeDouble(angle);}
    @Override public void readSpawnData(ByteBuf buffer){controller=BlockPos.fromLong(buffer.readLong());controllerKey=new UUID(buffer.readLong(),buffer.readLong());angle=previous=buffer.readDouble();updateBounds();}
}
