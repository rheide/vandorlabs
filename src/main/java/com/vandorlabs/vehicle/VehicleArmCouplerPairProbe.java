package com.vandorlabs.vehicle;

import com.vandorlabs.gear.*;
import java.io.*;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;

/** Bounded read-only evidence for the explicitly enabled disposable multi-pair focus. */
public final class VehicleArmCouplerPairProbe {
    private static final int[] COUNTS=new int[2];
    /** Scalar read-only query for this disposable consumer probe, not a gameplay admission API. */
    public static int headCount(VehicleStructure source,VehicleWorld view){
        if(System.getProperty("vandorlabs.reprolab")==null||!"1".equals(System.getenv("VANDOR_LABS_COUPLER_ARM_MULTI_ONLY")))throw new IllegalStateException("Read-only pair probe is not enabled");
        return VehicleArmCouplers.observe(source,view).heads.size();
    }
    public static void before(VehicleStructure source,VehicleWorld view){
        String directory=System.getProperty("vandorlabs.reprolab");
        if(directory==null||!"1".equals(System.getenv("VANDOR_LABS_COUPLER_ARM_MULTI_ONLY")))return;
        net.minecraft.world.World physical=view.parent;int depth=0;while(physical instanceof VehicleWorld){if(++depth>8)return;physical=((VehicleWorld)physical).parent;}int side=physical.isRemote?1:0;if(COUNTS[side]>=4)return;int index=++COUNTS[side];
        try{
            if(source.cells.size()>512||view.tiles.size()>256)throw new IOException("Probe scene bound");
            NBTTagCompound out=new NBTTagCompound();out.setString("Side",side==1?"client":"server");out.setString("Thread",Thread.currentThread().getName());out.setString("PhysicalWorld",physical.getClass().getName());out.setTag("Source",source.write());out.setBoolean("UsedViewMatchesSource",view.structure==source);
            if(view.craft!=null){EntityGroundVehicle c=view.craft;out.setUniqueId("UUID",c.getUniqueID());out.setBoolean("InstalledView",c.view==view);out.setBoolean("InstalledSourceMatches",c.structure==source);out.setDouble("X",c.posX);out.setDouble("Y",c.posY);out.setDouble("Z",c.posZ);out.setFloat("Yaw",c.rotationYaw);if(c.structure!=null)out.setTag("InstalledSource",c.structure.write());}
            NBTTagList gears=new NBTTagList();for(TileEntity tile:view.tiles.values())if(tile instanceof GearTileEntity){GearTileEntity a=(GearTileEntity)tile;NBTTagCompound row=new NBTTagCompound();row.setLong("Pos",a.getPos().toLong());row.setDouble("Phase",a.progress());row.setBoolean("Target",a.target());row.setString("Mode",a.mode().name());row.setString("Mount",a.mountDirection().name());row.setString("Direction",a.direction().name());row.setDouble("ConfiguredExtension",a.configuredActiveExtension());row.setBoolean("ParkingHeld",a.parkingHeld());ArmPayload payload=a.payload();if(payload!=null)row.setTag("Payload",payload.write());gears.appendTag(row);}out.setTag("LiveGears",gears);
            VehicleArmCouplers.Snapshot observations=VehicleArmCouplers.observe(source,view);NBTTagList heads=new NBTTagList();for(VehicleArmCouplers.Head h:observations.heads){NBTTagCompound row=new NBTTagCompound();if(h.address.arm!=null)row.setLong("Arm",h.address.arm.toLong());row.setLong("Cell",h.address.cell.toLong());row.setLong("Carrier",h.carrier.toLong());if(h.nativeCell!=null)row.setLong("NativeCell",h.nativeCell.toLong());row.setLong("LegacyCell",h.legacyCell.toLong());row.setString("LegacyFacing",h.legacyFacing.name());row.setDouble("LegacyHeight",h.legacyHeight);row.setBoolean("Locked",h.locked);row.setBoolean("Requested",h.requested);row.setBoolean("Articulated",h.articulated);row.setBoolean("Released",h.released);row.setDouble("X",h.x);row.setDouble("Y",h.y);row.setDouble("Z",h.z);row.setDouble("NX",h.nx);row.setDouble("NY",h.ny);row.setDouble("NZ",h.nz);heads.appendTag(row);System.out.println("[vandorlabs][reprolab] multi-pair-probe-head side="+(side==1?"client":"server")+" sample="+index+" arm="+h.address.arm+" cell="+h.address.cell+" carrier="+h.carrier+" native="+h.nativeCell+" legacy="+h.legacyCell+" point="+h.x+","+h.y+","+h.z+" normal="+h.nx+","+h.ny+","+h.nz);}out.setTag("Heads",heads);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();CompressedStreamTools.writeCompressed(out,bytes);if(bytes.size()>1024*1024)throw new IOException("Probe byte bound");File target=new File(directory,"multi-pair-probe-"+(side==1?"client":"server")+"-"+index+".nbt");try(FileOutputStream file=new FileOutputStream(target)){file.write(bytes.toByteArray());}System.out.println("[vandorlabs][reprolab] multi-pair-probe side="+(side==1?"client":"server")+" sample="+index+" sourceCells="+source.cells.size()+" heads="+observations.heads.size()+" bytes="+bytes.size()+" file="+target.getName());
        }catch(Exception failure){System.out.println("[vandorlabs][reprolab] multi-pair-probe failed "+failure);}
    }
    private VehicleArmCouplerPairProbe(){}
}
