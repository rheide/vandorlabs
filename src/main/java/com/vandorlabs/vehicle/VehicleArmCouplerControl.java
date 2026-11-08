package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.*;
import com.vandorlabs.gear.*;
import com.vandorlabs.redstone.*;
import java.util.*;
import net.minecraft.entity.player.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** A stable captured address, never a nested tile or a capability-bearing simulation. */
public final class VehicleArmCouplerControl implements CouplerControl {
    public static final String IDENTITY="CouplerControlIdentity";
    public final EntityGroundVehicle craft;public final VehicleArmCouplers.Address address;private UUID lifetime,observedLifetime;
    private static final String[] HEADER={"id","RedstoneChannels","CouplerControlIdentityMost","CouplerControlIdentityLeast","CouplerArticulation","CouplerInverted","CouplerReceived","CouplerRequested","CouplerManualOverride","CouplerManualRequested","CouplerActorMost","CouplerActorLeast","CouplerStatus"};
    private final CouplerInput input=new CouplerInput();
    private VehicleStructure.Cell readCell;private NBTTagCompound fields;private ChannelList channels;
    VehicleArmCouplerControl(EntityGroundVehicle craft,VehicleArmCouplers.Address address){this.craft=craft;this.address=address;refresh();lifetime=observedLifetime;}
    public static VehicleArmCouplerControl resolve(EntityGroundVehicle craft,BlockPos root,BlockPos cell){
        if(!VehicleArmCouplers.runtime()||craft==null||craft.isDead||craft.view==null||craft.structure==null)return null;
        if(!craft.world.isRemote&&(!(craft.world instanceof net.minecraft.world.WorldServer)||!((net.minecraft.world.WorldServer)craft.world).getMinecraftServer().isCallingFromMinecraftThread()))return null;
        return craft.articulation.capturedControls().resolve(root,cell);
    }
    boolean current(){try{refresh();return lifetime==null||lifetime.equals(observedLifetime);}catch(IllegalArgumentException unavailable){return false;}}
    private boolean channelPower(ChannelList selected){if(selected.isEmpty())return false;BlockPos at=new BlockPos(center());for(int i=0;i<selected.size();i++)if(RedstoneChannels.level(craft.world,at,selected.get(i))>0)return true;return false;}
    public void toggleManual(EntityPlayer actor){
        require(actor);authorize(actor);VehicleArmCouplers.Address partner=craft.articulation.capturedPartner(address);boolean closed=partner!=null&&VehicleArmCouplers.locked(craft,address)&&VehicleArmCouplers.locked(craft,partner);input.manual(closed?false:!requested());
        NBTTagCompound next=data();next.setBoolean("CouplerManualOverride",true);next.setBoolean("CouplerManualRequested",input.manualRequested());boolean received=channelPower(channels);Boolean local=received?Boolean.TRUE:arm().payload().snapshotCouplerPower(arm(),address.cell);next.setBoolean("CouplerReceived",received);
        if(local!=null){boolean desired=input.sample(received||local,inverted());next.setBoolean("CouplerRequested",desired);if(!desired&&partner!=null)next.setString("CouplerStatus","Arm-mounted release pending; section remains attached");}else next.setString("CouplerStatus","Waiting for captured local redstone producer");
        if(!input.overridden()){next.removeTag("CouplerManualOverride");next.removeTag("CouplerManualRequested");}publish(next);if(closed&&!requested())VehicleCouplings.requestRelease(craft,address);VehicleCouplings.wake(craft);
    }
    void sample(){
        if(craft.world.isRemote||!(craft.world instanceof net.minecraft.world.WorldServer)||!((net.minecraft.world.WorldServer)craft.world).getMinecraftServer().isCallingFromMinecraftThread()||!current())return;ensureIdentity();GearTileEntity arm=arm();ArmPayload payload=arm.payload();boolean received=false;
        received=channelPower(channels);
        Boolean local=received?Boolean.TRUE:payload.snapshotCouplerPower(arm,address.cell);if(local==null){status("Waiting for captured local redstone producer");return;}
        boolean desired=input.sample(received||local,fields.getBoolean("CouplerInverted"));boolean manual=input.overridden();
        if(fields.getBoolean("CouplerReceived")==received&&requested()==desired&&fields.getBoolean("CouplerManualOverride")==manual&&(!manual||fields.getBoolean("CouplerManualRequested")==input.manualRequested())&&!physicalMismatch(desired))return;
        NBTTagCompound next=data();next.setBoolean("CouplerReceived",received);next.setBoolean("CouplerRequested",desired);if(desired&&"Arm-mounted release pending; section remains attached".equals(fields.getString("CouplerStatus")))next.setString("CouplerStatus","Ready");if(manual){next.setBoolean("CouplerManualOverride",true);next.setBoolean("CouplerManualRequested",input.manualRequested());}else{next.removeTag("CouplerManualOverride");next.removeTag("CouplerManualRequested");}if(!desired&&craft.articulation.capturedPartner(address)!=null)next.setString("CouplerStatus","Arm-mounted release pending; section remains attached");publish(next);if(!desired&&craft.articulation.capturedPartner(address)!=null)VehicleCouplings.requestRelease(craft,address);VehicleCouplings.wake(craft);
    }
    private GearTileEntity arm(){TileEntity raw=craft.view==null?null:craft.view.getTileEntity(address.arm);return raw instanceof GearTileEntity?(GearTileEntity)raw:null;}
    private VehicleStructure.Cell cell(){GearTileEntity arm=arm();ArmPayload payload=arm==null?null:arm.payload();return payload==null||payload.materialized()||payload.hasOwnershipJournal()?null:payload.blocks.at(address.cell);}
    private static UUID identity(NBTTagCompound data){boolean present=data.hasKey(IDENTITY+"Most")||data.hasKey(IDENTITY+"Least");if(present&&!data.hasUniqueId(IDENTITY))throw new IllegalArgumentException("Malformed captured Coupler lifetime");return present?data.getUniqueId(IDENTITY):null;}
    private NBTTagCompound data(){refresh();return cell().tileData();}
    private void refresh(){VehicleStructure.Cell current=cell();if(current==null||!(current.state.getBlock() instanceof BlockCoupler))throw new IllegalArgumentException("Captured Coupler owner changed");if(current!=readCell){NBTTagCompound data=current.tileFields(HEADER);if(data==null||!"vandorlabs:square_coupler".equals(data.getString("id")))throw new IllegalArgumentException("Captured Coupler identity changed");observedLifetime=identity(data);fields=data;channels=ChannelData.read(data,0);fields.removeTag("RedstoneChannels");input.restoreOverride(fields.getBoolean("CouplerManualOverride"),fields.getBoolean("CouplerManualRequested"));readCell=current;}}
    public boolean valid(EntityPlayer actor){
        if(!craft.world.isRemote&&(!(craft.world instanceof net.minecraft.world.WorldServer)||!((net.minecraft.world.WorldServer)craft.world).getMinecraftServer().isCallingFromMinecraftThread()))return false;
        if(actor==null||actor.isDead||actor.isSpectator()||craft.isDead||craft.ownershipBlocked||craft.transferInProgress||craft.world!=actor.world||craft.view==null||craft.view.craft!=craft||craft.view.structure!=craft.structure||craft.world.getEntityByID(craft.getEntityId())!=craft)return false;
        try{refresh();if(lifetime!=null&&!lifetime.equals(observedLifetime))return false;Vec3d center=center();BlockPos at=new BlockPos(center);return actor.getDistanceSq(center.x,center.y,center.z)<=64&&actor.world.isBlockLoaded(at)&&actor.world.isBlockModifiable(actor,at)&&actor.canPlayerEdit(at,EnumFacing.UP,actor.getHeldItemMainhand());}catch(IllegalArgumentException unavailable){return false;}
    }
    public UUID lifetime(){refresh();return observedLifetime;}
    public BlockPos alias(){GearTileEntity gear=arm();ArmPayload payload=gear.payload();VehicleStructure.Cell selected=cell();int index=payload.blocks.cells.indexOf(selected);if(index<0)throw new IllegalArgumentException("Captured head correspondence changed");return payload.placedCellPosition(gear,index);}
    public Vec3d center(){GearTileEntity arm=arm();ArmPayload payload=arm.payload();Vec3d local=payload.transform(arm,arm.progress(),address.cell.getX()+.5,address.cell.getY()+.5,address.cell.getZ()+.5).add(new Vec3d(address.arm));return craft.toWorld(local,address.arm);}
    /** Called only after server authorization, before opening the existing Forge GUI. */
    public void initialize(EntityPlayer actor){require(actor);ensureIdentity();}
    private void ensureIdentity(){if(lifetime==null){if(craft.world.isRemote||!(craft.world instanceof net.minecraft.world.WorldServer)||!((net.minecraft.world.WorldServer)craft.world).getMinecraftServer().isCallingFromMinecraftThread())throw new IllegalStateException("Captured identity initialization requires authoritative thread");NBTTagCompound next=data();lifetime=UUID.randomUUID();next.setUniqueId(IDENTITY,lifetime);publish(next);}}
    private void require(EntityPlayer actor){if(craft.world.isRemote||!(craft.world instanceof net.minecraft.world.WorldServer)||!((net.minecraft.world.WorldServer)craft.world).getMinecraftServer().isCallingFromMinecraftThread()||!valid(actor))throw new IllegalArgumentException("Captured Coupler authorization changed");}
    private void mutationThread(){if(craft.world.isRemote||!(craft.world instanceof net.minecraft.world.WorldServer)||!((net.minecraft.world.WorldServer)craft.world).getMinecraftServer().isCallingFromMinecraftThread()||craft.isDead||craft.ownershipBlocked||craft.transferInProgress||craft.world.getEntityByID(craft.getEntityId())!=craft)throw new IllegalArgumentException("Captured control mutation requires installed authoritative owner");}
    private boolean physicalMismatch(boolean desired){VehicleStructure.Cell head=cell();return head!=null&&head.tileBoolean("VehicleArmCouplerReleased")&&head.state.getValue(BlockCoupler.LOCKED)!=desired&&craft.articulation.capturedPartner(address)==null;}
    private void publish(NBTTagCompound next){
        mutationThread();refresh();boolean desired=next.hasKey("CouplerRequested",1)?next.getBoolean("CouplerRequested"):next.getBoolean("CouplerReceived")!=next.getBoolean("CouplerInverted");boolean physical=physicalMismatch(desired);if(next.equals(cell().tileData())&&!physical)return;GearTileEntity gear=arm();ArmPayload before=gear.payload(),after=physical?before.releaseCoupler(address.cell,desired,next):before.withCouplerData(address.cell,next);VehicleStructure old=craft.structure;VehicleStructure.Cell original=old.at(address.arm);NBTTagCompound data=gear.currentData(original.tileData());data.setTag("ArmPayload",after.write());
        java.util.List<VehicleStructure.Cell> cells=new java.util.ArrayList<>();for(VehicleStructure.Cell cell:old.cells)cells.add(cell.pos.equals(address.arm)?new VehicleStructure.Cell(cell.pos,cell.state,data):cell);VehicleStructure candidate=new VehicleStructure(cells,old.seat,old.facing,old.gearCount);
        try{candidate.encode();}catch(java.io.IOException tooLarge){throw new IllegalArgumentException("Captured control snapshot exceeds safe bounds",tooLarge);}
        if(gear.payload()!=before||craft.structure!=old||craft.view.getTileEntity(address.arm)!=gear)throw new IllegalArgumentException("Captured control owner changed during preparation");
        gear.installCapturedCoupler(before,after);try{craft.updateCanopies(candidate,physical);}catch(RuntimeException|LinkageError failure){gear.installCapturedCoupler(after,before);craft.updateCanopies(old,physical);throw failure;}
        readCell=null;refresh();craft.signals.changed(address.arm);craft.signals.persist();
    }
    @Override public ChannelList getRedstoneChannels(){refresh();return channels;}
    @Override public boolean inverted(){refresh();return fields.getBoolean("CouplerInverted");}
    @Override public boolean articulation(){refresh();return fields.getBoolean("CouplerArticulation");}
    @Override public boolean requested(){refresh();return fields.hasKey("CouplerRequested",1)?fields.getBoolean("CouplerRequested"):fields.getBoolean("CouplerReceived")!=fields.getBoolean("CouplerInverted");}
    @Override public String status(){refresh();return fields.hasKey("CouplerStatus",8)?fields.getString("CouplerStatus"):"Ready";}
    @Override public void status(String value){mutationThread();refresh();if(Objects.equals(status(),value))return;NBTTagCompound next=data();next.setString("CouplerStatus",value);publish(next);}
    @Override public void authorize(EntityPlayer actor){require(actor);initialize(actor);craft.recordCouplingActor(actor);if(fields.hasUniqueId("CouplerActor")&&fields.getUniqueId("CouplerActor").equals(actor.getUniqueID()))return;NBTTagCompound next=data();next.setUniqueId("CouplerActor",actor.getUniqueID());publish(next);}
    @Override public void configure(ChannelList value,boolean invert,boolean articulated){
        mutationThread();refresh();boolean hadOverride=input.overridden();input.clearOverride();boolean received=channelPower(value);Boolean local=received?Boolean.TRUE:arm().payload().snapshotCouplerPower(arm(),address.cell);boolean desired=local==null?requested():input.sample(received||local,invert);
        boolean changed=hadOverride||!channels.equals(value)||inverted()!=invert||articulation()!=articulated||fields.getBoolean("CouplerReceived")!=received||requested()!=desired;
        if(local==null&&!"Waiting for captured local redstone producer".equals(status()))changed=true;if(!changed)return;
        NBTTagCompound next=data();next.removeTag("CouplerManualOverride");next.removeTag("CouplerManualRequested");ChannelData.write(next,value);next.setBoolean("CouplerInverted",invert);if(articulated)next.setBoolean("CouplerArticulation",true);else next.removeTag("CouplerArticulation");next.setBoolean("CouplerReceived",received);next.setBoolean("CouplerRequested",desired);
        if(local==null)next.setString("CouplerStatus","Waiting for captured local redstone producer");else if(!desired&&craft.articulation.capturedPartner(address)!=null)next.setString("CouplerStatus","Arm-mounted release pending; section remains attached");else if("Arm-mounted release pending; section remains attached".equals(status()))next.setString("CouplerStatus","Ready");publish(next);VehicleCouplings.wake(craft);
    }
}
