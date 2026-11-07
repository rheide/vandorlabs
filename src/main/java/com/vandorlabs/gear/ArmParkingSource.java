package com.vandorlabs.gear;

import com.vandorlabs.vehicle.VehicleStructure;
import com.vandorlabs.vehicle.VehicleWorld;
import com.vandorlabs.gear.core.GearMotion;
import net.minecraft.util.EnumFacing;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** One detached source refresh before search. The token never owns or mutates a live world snapshot. */
public final class ArmParkingSource {
    private final VehicleStructure source;
    private final GearTileEntity owner;
    private final VehicleWorld view;
    private final ArmPayload original,payload;
    private final BlockPos position;
    private final int revision;
    private final long progress;
    private final boolean target,held,materialized;
    private final Object assembly;
    private final GearMotion.Mode mode;private final GearMotion.Direction direction;private final EnumFacing mount;
    private final long angle,start,extension;private final int size,trigger,range,height;
    private final boolean rotate,push,join,carry;private final com.vandorlabs.redstone.ChannelList channels;
    private ArmParkingSource(VehicleStructure source,GearTileEntity owner,VehicleWorld view,ArmPayload original,ArmPayload payload){
        this.source=source;this.owner=owner;this.view=view;this.original=original;this.payload=payload;
        position=owner.getPos().toImmutable();revision=owner.motionRevision();progress=Double.doubleToLongBits(owner.progress());target=owner.target();held=owner.parkingHeld();materialized=original.materialized();assembly=owner.assembly();mode=owner.mode();direction=owner.direction();mount=owner.mountDirection();size=owner.armSize();angle=Double.doubleToLongBits(owner.activeAngle());start=Double.doubleToLongBits(owner.physicalStartAngle());extension=Double.doubleToLongBits(owner.activeExtension());trigger=owner.triggerMode;channels=owner.getRedstoneChannels();rotate=owner.rotatePayload();push=owner.pushBlocks();join=owner.joinEnabled();range=owner.joinRange();carry=owner.carryLoad();height=owner.loadHeight();
    }
    static boolean serverThread(World world){
        int depth=0;while(world instanceof VehicleWorld){if(++depth>8)return false;world=((VehicleWorld)world).parent;}
        return world!=null&&!world.isRemote&&world.getMinecraftServer()!=null&&world.getMinecraftServer().isCallingFromMinecraftThread();
    }
    /** Main-thread capture from an already prepared exact view, never a tile factory or serializer. */
    public static ArmParkingSource capture(VehicleStructure source,GearTileEntity owner){
        if(source==null||owner==null||!(owner.getWorld() instanceof VehicleWorld)||!serverThread(owner.getWorld()))throw new IllegalArgumentException("Prepared server arm source required");
        VehicleWorld view=(VehicleWorld)owner.getWorld();VehicleStructure.Cell root=source.at(owner.getPos());ResourceLocation id=TileEntity.getKey(GearTileEntity.class);
        if(view.structure!=source||view.tiles.get(owner.getPos())!=owner||owner.isInvalid()||owner.owner()==null||!owner.owner().equals(owner.getPos())||!owner.isOperational()||owner.hasFailedArmSnapshot()||owner.hasTaxiData()||owner.recoveryDropped()||owner.payload()==null||owner.payload().hasOwnershipJournal()||root==null||!(root.state.getBlock() instanceof GearBlock)||!root.hasTileData()||id==null||!id.toString().equals(root.tileString("id"))||root.state!=view.getBlockState(owner.getPos()))throw new IllegalArgumentException("Arm source identity changed");
        if(owner.parkingHeld()&&!owner.validateParkingHoldInstalled())throw new IllegalArgumentException("Held arm publication is pending or incompatible");
        ArmPayload original=owner.payload(),prepared=original.materialized()?original.snapshotPlaced(owner,source):original;
        ArmParkingSource token=new ArmParkingSource(source,owner,view,original,prepared);if(!token.stillOwned())throw new IllegalArgumentException("Arm source changed during preparation");return token;
    }
    public VehicleStructure source(){return source;}
    public GearTileEntity owner(){return owner;}
    public ArmPayload originalPayload(){return original;}
    public ArmPayload payload(){return payload;}
    /** Scalar/pointer checks only: no data cloning, factories, capability queries or member scans. */
    public boolean stillOwned(){return serverThread(view)&&view.structure==source&&owner.getWorld()==view&&owner.getPos().equals(position)&&view.tiles.get(position)==owner&&!owner.isInvalid()&&owner.owner()!=null&&owner.owner().equals(position)&&owner.isOperational()&&!owner.hasFailedArmSnapshot()&&owner.payload()==original&&original.materialized()==materialized&&!original.hasOwnershipJournal()&&owner.motionRevision()==revision&&Double.doubleToLongBits(owner.progress())==progress&&owner.target()==target&&owner.parkingHeld()==held&&owner.assembly()==assembly&&owner.mode()==mode&&owner.direction()==direction&&owner.mountDirection()==mount&&owner.armSize()==size&&Double.doubleToLongBits(owner.activeAngle())==angle&&Double.doubleToLongBits(owner.physicalStartAngle())==start&&Double.doubleToLongBits(owner.activeExtension())==extension&&owner.triggerMode==trigger&&owner.getRedstoneChannels()==channels&&owner.rotatePayload()==rotate&&owner.pushBlocks()==push&&owner.joinEnabled()==join&&owner.joinRange()==range&&owner.carryLoad()==carry&&owner.loadHeight()==height;}
}
