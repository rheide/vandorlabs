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
    public final EntityGroundVehicle craft;public final VehicleArmCouplers.Address address;private UUID lifetime;
    private VehicleStructure.Cell readCell;private NBTTagCompound fields;private ChannelList channels;
    private VehicleArmCouplerControl(EntityGroundVehicle craft,VehicleArmCouplers.Address address){this.craft=craft;this.address=address;refresh();lifetime=identity(fields);}
    public static VehicleArmCouplerControl resolve(EntityGroundVehicle craft,BlockPos root,BlockPos cell){
        if(!VehicleArmCouplers.runtime()||craft==null||craft.isDead||craft.view==null||craft.structure==null)return null;
        TileEntity raw=craft.view.getTileEntity(root);if(!(raw instanceof GearTileEntity))return null;ArmPayload payload=((GearTileEntity)raw).payload();if(payload==null||payload.materialized()||payload.hasOwnershipJournal())return null;
        VehicleStructure.Cell selected=payload.blocks.at(cell);if(selected==null||!(selected.state.getBlock() instanceof BlockCoupler))return null;
        return new VehicleArmCouplerControl(craft,VehicleArmCouplers.Address.payloadCell(root,cell));
    }
    private GearTileEntity arm(){TileEntity raw=craft.view==null?null:craft.view.getTileEntity(address.arm);return raw instanceof GearTileEntity?(GearTileEntity)raw:null;}
    private VehicleStructure.Cell cell(){GearTileEntity arm=arm();ArmPayload payload=arm==null?null:arm.payload();return payload==null||payload.materialized()||payload.hasOwnershipJournal()?null:payload.blocks.at(address.cell);}
    private static UUID identity(NBTTagCompound data){boolean present=data.hasKey(IDENTITY+"Most")||data.hasKey(IDENTITY+"Least");if(present&&!data.hasUniqueId(IDENTITY))throw new IllegalArgumentException("Malformed captured Coupler lifetime");return present?data.getUniqueId(IDENTITY):null;}
    private void refresh(){VehicleStructure.Cell current=cell();if(current==null||!(current.state.getBlock() instanceof BlockCoupler))throw new IllegalArgumentException("Captured Coupler owner changed");if(current!=readCell){NBTTagCompound data=current.tileData();if(data==null||!"vandorlabs:square_coupler".equals(data.getString("id")))throw new IllegalArgumentException("Captured Coupler identity changed");identity(data);fields=data;channels=ChannelData.read(data,0);readCell=current;}}
    public boolean valid(EntityPlayer actor){
        if(!craft.world.isRemote&&(!(craft.world instanceof net.minecraft.world.WorldServer)||!((net.minecraft.world.WorldServer)craft.world).getMinecraftServer().isCallingFromMinecraftThread()))return false;
        if(actor==null||actor.isDead||actor.isSpectator()||craft.isDead||craft.ownershipBlocked||craft.transferInProgress||craft.world!=actor.world||craft.view==null||craft.view.craft!=craft||craft.view.structure!=craft.structure||craft.world.getEntityByID(craft.getEntityId())!=craft)return false;
        try{refresh();if(lifetime!=null&&!lifetime.equals(identity(fields)))return false;Vec3d center=center();BlockPos at=new BlockPos(center);return actor.getDistanceSq(center.x,center.y,center.z)<=64&&actor.world.isBlockLoaded(at)&&actor.world.isBlockModifiable(actor,at)&&actor.canPlayerEdit(at,EnumFacing.UP,actor.getHeldItemMainhand());}catch(IllegalArgumentException unavailable){return false;}
    }
    public Vec3d center(){GearTileEntity arm=arm();ArmPayload payload=arm.payload();Vec3d local=payload.transform(arm,arm.progress(),address.cell.getX()+.5,address.cell.getY()+.5,address.cell.getZ()+.5).add(new Vec3d(address.arm));return craft.toWorld(local,address.arm);}
    /** Called only after server authorization, before opening the existing Forge GUI. */
    public void initialize(EntityPlayer actor){require(actor);if(lifetime==null){NBTTagCompound next=fields.copy();lifetime=UUID.randomUUID();next.setUniqueId(IDENTITY,lifetime);publish(next);}}
    private void require(EntityPlayer actor){if(craft.world.isRemote||!(craft.world instanceof net.minecraft.world.WorldServer)||!((net.minecraft.world.WorldServer)craft.world).getMinecraftServer().isCallingFromMinecraftThread()||!valid(actor))throw new IllegalArgumentException("Captured Coupler authorization changed");}
    private void publish(NBTTagCompound next){refresh();GearTileEntity gear=arm();ArmPayload before=gear.payload(),after=before.withCouplerData(address.cell,next);VehicleStructure.Cell original=craft.structure.at(address.arm);NBTTagCompound data=original.tileData();data.setTag("ArmPayload",after.write());gear.installCapturedCoupler(before,after);VehicleSignals.save(craft,address.arm,data,original.state);readCell=null;refresh();craft.signals.changed(address.arm);craft.signals.persist();}
    @Override public ChannelList getRedstoneChannels(){refresh();return channels;}
    @Override public boolean inverted(){refresh();return fields.getBoolean("CouplerInverted");}
    @Override public boolean articulation(){refresh();return fields.getBoolean("CouplerArticulation");}
    @Override public boolean requested(){refresh();return fields.hasKey("CouplerRequested",1)?fields.getBoolean("CouplerRequested"):fields.getBoolean("CouplerReceived")!=fields.getBoolean("CouplerInverted");}
    @Override public String status(){refresh();return fields.hasKey("CouplerStatus",8)?fields.getString("CouplerStatus"):"Ready";}
    @Override public void status(String value){refresh();if(Objects.equals(status(),value))return;NBTTagCompound next=fields.copy();next.setString("CouplerStatus",value);publish(next);}
    @Override public void authorize(EntityPlayer actor){require(actor);initialize(actor);craft.recordCouplingActor(actor);NBTTagCompound next=fields.copy();next.setUniqueId("CouplerActor",actor.getUniqueID());publish(next);}
    @Override public void configure(ChannelList value,boolean invert,boolean articulated){
        if(craft.world.isRemote)throw new IllegalStateException("Captured edits are server-only");refresh();NBTTagCompound next=fields.copy();next.removeTag("CouplerManualOverride");next.removeTag("CouplerManualRequested");ChannelData.write(next,value);next.setBoolean("CouplerInverted",invert);next.setBoolean("CouplerArticulation",articulated);next.setBoolean("CouplerRequested",next.getBoolean("CouplerReceived")!=invert);publish(next);VehicleCouplings.wake(craft);
    }
}
