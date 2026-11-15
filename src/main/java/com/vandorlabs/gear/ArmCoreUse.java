package com.vandorlabs.gear;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Explicit configuration-only actions on a complete arm-owned machine snapshot. */
public final class ArmCoreUse {
    public static final int GUI_BASE=65536;
    private static final String RETURN="Return the arm to a native block pose before transferring its chip or dismantling this machine.";
    static final String[] CONFIG={"NavigationTexture","NavigationAnimated","NavigationSpeed","NavigationRedstone","NavigationSecurity","NavigationScreenOn","MachineSurfaceTextures","RedstoneChannels","RedstoneChannel","LatchedLevels","LatchedChannels","DriveSignalLevels","DriveInverted","DriveParticles","DriveParticleThreshold","DriveLevel"};
    public static boolean gui(int id){return id>=GUI_BASE&&id<GUI_BASE+ArmPayload.MAX_BLOCKS;}
    static TileEntityConstructor owner(ArmPayload payload,GearTileEntity arm,BlockPos selected){
        VehicleWorld view=payload.view(arm);TileEntity tile=view.getTileEntity(selected);
        if(tile instanceof TileEntityConstructorPart)tile=view.getTileEntity(((TileEntityConstructorPart)tile).owner());
        if(!(tile instanceof TileEntityConstructor)||!((TileEntityConstructor)tile).formed())return null;
        return (TileEntityConstructor)tile;
    }
    static Vec3d local(GearTileEntity arm,ArmPayload payload,Vec3d point){
        Vec3d origin=payload.transform(arm,arm.progress(),0,0,0).add(new Vec3d(arm.getPos())),v=point.subtract(origin);
        Vec3d a=payload.transform(arm,arm.progress(),1,0,0).add(new Vec3d(arm.getPos())).subtract(origin),b=payload.transform(arm,arm.progress(),0,1,0).add(new Vec3d(arm.getPos())).subtract(origin),c=payload.transform(arm,arm.progress(),0,0,1).add(new Vec3d(arm.getPos())).subtract(origin);
        return new Vec3d(v.dotProduct(a),v.dotProduct(b),v.dotProduct(c));
    }
    static boolean available(EntityPlayer actor,GearTileEntity arm,ArmPayload expected,TileEntityConstructor core){
        World world=arm.getWorld();if(actor==null||actor.isDead||actor.isSpectator()||world==null||actor.world!=world||arm.isInvalid()||!world.isBlockLoaded(arm.getPos())||world.getTileEntity(arm.getPos())!=arm||!arm.isOperational()||arm.payload()!=expected||expected==null||expected.materialized()||expected.hasOwnershipJournal()||arm.hasFailedArmSnapshot()||ArmTransactions.locked(world,arm.getPos()))return false;
        Vec3d eye=local(arm,expected,actor.getPositionEyes(1));AxisAlignedBB box=core.getRenderBoundingBox();double dx=Math.max(box.minX-eye.x,Math.max(0,eye.x-box.maxX)),dy=Math.max(box.minY-eye.y,Math.max(0,eye.y-box.maxY)),dz=Math.max(box.minZ-eye.z,Math.max(0,eye.z-box.maxZ));double reach=actor.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue()+.5;return dx*dx+dy*dy+dz*dz<=reach*reach;
    }
    static void permission(EntityPlayerMP actor,GearTileEntity arm,BlockPos core){
        if(!actor.getServerWorld().getMinecraftServer().isCallingFromMinecraftThread())throw new IllegalArgumentException("Machine settings require the server thread");
        for(BlockPos at:arm.members())VehicleService.permission(actor,at);
        VehicleService.permission(actor,arm.getPos().add(arm.payload().offset).add(core));
        Vec3d visible=arm.payload().transform(arm,arm.progress(),core.getX()+.5,core.getY()+.5,core.getZ()+.5).add(new Vec3d(arm.getPos()));VehicleService.permission(actor,new BlockPos(visible));
    }
    /** Returns true for every recognized machine click, including a refused inventory action. */
    static boolean use(EntityPlayerMP actor,GearTileEntity arm,RayTraceResult hit){
        if(!(hit.hitInfo instanceof ArmPayloadHarvest.Target)||arm.payload()==null)return false;
        ArmPayloadHarvest.Target target=(ArmPayloadHarvest.Target)hit.hitInfo;if(!target.root.equals(arm.getPos()))return false;
        VehicleStructure.Cell selected=arm.payload().blocks.at(target.cell);if(selected==null||!ArmCorePayload.machine(selected.state))return false;
        ArmPayload payload=arm.payload();ArmCorePayload.validate(payload.blocks);TileEntityConstructor machine=owner(payload,arm,target.cell);
        if(machine==null||!available(actor,arm,payload,machine))throw new IllegalArgumentException("The carried machine is busy or no longer available");permission(actor,arm,machine.getPos());
        ItemStack held=actor.getHeldItemMainhand();
        if(held.getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER){int index=-1;for(int i=0;i<payload.blocks.cells.size();i++)if(payload.blocks.cells.get(i).pos.equals(machine.getPos())){index=i;break;}if(index<0)throw new IllegalArgumentException("Carried machine identity changed");actor.openGui(com.vandorlabs.VandorLabs.instance,GUI_BASE+index,actor.world,arm.getPos().getX(),arm.getPos().getY(),arm.getPos().getZ());return true;}
        MachineMountSurface mount=machine.mount();Vec3d start=actor.getPositionEyes(1),end=start.add(actor.getLook(1).scale(actor.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue()));
        if(actor.isSneaking()||machine.accepts(held)||mount!=null&&mount.hit(local(arm,payload,start),local(arm,payload,end))){actor.sendStatusMessage(new net.minecraft.util.text.TextComponentString(RETURN),true);return true;}
        if(machine.core().specification().state_models!=null){NBTTagCompound data=payload.blocks.at(machine.getPos()).tileData();int activity=(machine.activity()+1)%3;data.setInteger("MachineActivity",activity);arm.installCoreSettings(payload,payload.withCoreData(machine.getPos(),data,true));actor.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Activity: "+machine.core().specification().stateLabel(activity)),true);}return true;
    }
    private ArmCoreUse(){}
}
