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
    private boolean wasRunning;
    private int lastRestore=-100;
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
    public List<AxisAlignedBB> collisionBoxes(AxisAlignedBB query){List<AxisAlignedBB> out=new ArrayList<>();if(snapshot==null)return out;Vec3d pivot=new Vec3d(posX,posY,posZ);for(AxisAlignedBB box:localBoxes)if(transformed(box,angle).intersects(query))out.addAll(TurntableCollision.boxes(box.offset(snapshot.origin),pivot,snapshot.layout.basis,angle,query));return out;}
    private boolean sweepIntersects(int index,AxisAlignedBB obstacle,double delta){int samples=Math.max(1,(int)Math.ceil(Math.abs(delta)));double extra=(snapshot.layout.size+snapshot.height)*(1-Math.cos(Math.toRadians(Math.abs(delta)/samples/2)));AxisAlignedBB query=obstacle.grow(extra).shrink(.0001);for(int n=0;n<=samples;n++)if(!TurntableCollision.boxes(localBoxes.get(index).offset(snapshot.origin),new Vec3d(posX,posY,posZ),snapshot.layout.basis,angle+delta*n/samples,query).isEmpty())return true;return false;}
    private void updateBounds(){
        if(snapshot==null){setEntityBoundingBox(new AxisAlignedBB(posX-32,posY-32,posZ-32,posX+32,posY+32,posZ+32));return;}
        AxisAlignedBB bounds=transformed(localExtent,angle);
        for(BlockPos cell:snapshot.layout.cells)bounds=bounds.union(new AxisAlignedBB(cell));setEntityBoundingBox(bounds.grow(2));
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
        if(owner==null){dataManager.set(RATE,0F);if(world.isBlockLoaded(controller) && ticksExisted%20==0)TurntableEvents.restoreAfterTick(this);return;}
        if(!getUniqueID().equals(owner.captureId)){dataManager.set(RATE,0F);return;}
        boolean running=owner.running();
        if(!running&&wasRunning&&!owner.legacyCapture()&&(lastRestore<0||ticksExisted-lastRestore>=20)){lastRestore=ticksExisted;dataManager.set(RATE,0F);TurntableEvents.restoreAfterTick(this);return;}
        if(running)wasRunning=true;
        double rate=running?(owner.clockwise?1:-1)*TurntableGeometry.degreesPerTick(owner.rpm):0;
        if(rate!=0){
            double next=TurntableGeometry.wrap(angle+rate);List<AxisAlignedBB> before=collisionBoxes(),after=boxes(next);
            AxisAlignedBB swept=getEntityBoundingBox().union(transformed(localExtent,next));
            if(!world.isAreaLoaded(new BlockPos(swept.minX,swept.minY,swept.minZ),new BlockPos(swept.maxX,swept.maxY,swept.maxZ),false))rate=0;
            else {
                List<AxisAlignedBB> obstacles=world.getCollisionBoxes(this,swept);boolean blocked=false;
                double radius=snapshot.layout.size+snapshot.height,extra=radius*(1-Math.cos(Math.toRadians(Math.abs(rate)/2)));
                for(int i=0;i<after.size() && !blocked;i++){
                    AxisAlignedBB sweep=after.get(i).union(before.get(i)).grow(snapshot.layout.normal.getFrontOffsetX()==0?extra:0,snapshot.layout.normal.getFrontOffsetY()==0?extra:0,snapshot.layout.normal.getFrontOffsetZ()==0?extra:0).shrink(.0001);
                    for(AxisAlignedBB obstacle:obstacles)if(obstacle.intersects(sweep)&&sweepIntersects(i,obstacle,rate)){blocked=true;break;}
                }
                if(blocked){rate=0;owner.status("Rotation blocked; clear the moving contents");}else carry(rate);
            }
            if(rate!=0){angle=next;worldBoxes=after;boxesAt=angle;owner.status("Rotating");}
        }else if(!wasRunning)owner.status(owner.legacyCapture()?"Old back-face capture: use Restore to return the preserved contents before scanning again":"Captured; stopped");
        dataManager.set(ANGLE,(float)angle);dataManager.set(RATE,(float)rate);updateBounds();
    }
    private void carry(double delta){
        for(Entity rider:world.getEntitiesWithinAABBExcludingEntity(this,getEntityBoundingBox().grow(1))){
            if(rider instanceof EntityTurntable||rider.isDead||rider.isRiding()||rider instanceof EntityPlayer&&((EntityPlayer)rider).capabilities.isFlying)continue;
            AxisAlignedBB body=rider.getEntityBoundingBox();boolean supported=false;
            for(AxisAlignedBB box:collisionBoxes(body.grow(.001,.13,.001)))if(Math.abs(body.minY-box.maxY)<.12&&body.maxX>box.minX+.001&&body.minX<box.maxX-.001&&body.maxZ>box.minZ+.001&&body.minZ<box.maxZ-.001){supported=true;break;}
            if(!supported&&snapshot.layout.normal==EnumFacing.UP&&Math.abs(body.minY-snapshot.layout.pivot.y)<.12){supported=snapshot.layout.onDisc(rider.posX,rider.posY,rider.posZ);}
            if(!supported)continue;
            double[] p=TurntableGeometry.rotate(rider.posX-posX,rider.posY-posY,rider.posZ-posZ,snapshot.layout.basis,delta);double dx=posX+p[0]-rider.posX,dy=posY+p[1]-rider.posY,dz=posZ+p[2]-rider.posZ;
            List<AxisAlignedBB> obstacles=TurntableEvents.foreignObstacles(world,this,rider,body.union(body.offset(dx,dy,dz)).grow(.001));
            for(AxisAlignedBB box:obstacles)dy=box.calculateYOffset(body,dy);AxisAlignedBB moved=body.offset(0,dy,0);for(AxisAlignedBB box:obstacles)dx=box.calculateXOffset(moved,dx);moved=moved.offset(dx,0,0);for(AxisAlignedBB box:obstacles)dz=box.calculateZOffset(moved,dz);
            float yaw=(float)(delta*snapshot.layout.normal.getFrontOffsetY());
            if(rider instanceof EntityPlayerMP){rider.setPosition(rider.posX+dx,rider.posY+dy,rider.posZ+dz);rider.rotationYaw+=yaw;com.vandorlabs.network.PacketHandler.INSTANCE.sendTo(new com.vandorlabs.network.MessageTurntableCarry(world.provider.getDimension(),getEntityId(),dx,dy,dz,yaw),(EntityPlayerMP)rider);}
            else{rider.setPositionAndUpdate(rider.posX+dx,rider.posY+dy,rider.posZ+dz);rider.rotationYaw+=yaw;}
            rider.fallDistance=0;rider.onGround=true;
        }
    }
    @Override public boolean processInitialInteract(EntityPlayer player,EnumHand hand){
        if(hand!=EnumHand.MAIN_HAND)return false;
        if(!world.isRemote && player instanceof EntityPlayerMP)try{TurntableTransfer.restore((WorldServer)world,this,(EntityPlayerMP)player);}catch(Exception blocked){player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(blocked.getMessage()),true);}return true;
    }
    // Precise physical collision is supplied by TurntableEvents. The broad
    // entity bounds must never intercept block picking or breaking nearby.
    @Override public boolean canBeCollidedWith(){return false;}
    @Override public boolean canBePushed(){return false;}
    @Override public AxisAlignedBB getCollisionBoundingBox(){return null;}
    @Override public boolean attackEntityFrom(DamageSource source,float amount){return false;}
    @Override public void setPortal(BlockPos pos){}
    @Override protected void writeEntityToNBT(NBTTagCompound tag){
        tag.setLong("Controller",controller.toLong());if(controllerKey!=null)tag.setUniqueId("ControllerKey",controllerKey);if(actor!=null)tag.setUniqueId("Actor",actor);tag.setDouble("Angle",angle);tag.setBoolean("WasRunning",wasRunning);
        if(snapshot!=null)tag.setTag("Snapshot",snapshot.write());else if(unavailable!=null)tag.setTag("Snapshot",unavailable.copy());
    }
    @Override protected void readEntityFromNBT(NBTTagCompound tag){
        wasRunning=tag.getBoolean("WasRunning");controller=BlockPos.fromLong(tag.getLong("Controller"));controllerKey=tag.hasUniqueId("ControllerKey")?tag.getUniqueId("ControllerKey"):null;actor=tag.hasUniqueId("Actor")?tag.getUniqueId("Actor"):null;
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
