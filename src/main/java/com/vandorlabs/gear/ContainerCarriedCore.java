package com.vandorlabs.gear;

import com.vandorlabs.container.ContainerTokenSlot;
import com.vandorlabs.tiles.TileEntityConstructor;
import net.minecraft.entity.player.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** An exact arm/payload session. No slots or inventory-transfer authorization. */
public final class ContainerCarriedCore extends ContainerTokenSlot {
    private final GearTileEntity arm;private ArmPayload expected;private final BlockPos owner;private final java.util.UUID actorId;
    private ContainerCarriedCore(EntityPlayer actor,GearTileEntity arm,TileEntityConstructor machine){super(actor.inventory,machine);actorId=actor.getUniqueID();this.arm=arm;expected=arm.payload();owner=machine.getPos().toImmutable();}
    public static ContainerCarriedCore resolve(EntityPlayer actor,World world,int id,int x,int y,int z){
        if(!ArmCoreUse.gui(id)||actor.world!=world||!world.isBlockLoaded(new BlockPos(x,y,z)))return null;TileEntity raw=world.getTileEntity(new BlockPos(x,y,z));if(!(raw instanceof GearTileEntity))return null;GearTileEntity arm=(GearTileEntity)raw;ArmPayload payload=arm.payload();int index=id-ArmCoreUse.GUI_BASE;if(payload==null||index>=payload.blocks.cells.size())return null;
        ArmCorePayload.validate(payload.blocks);BlockPos owner=payload.blocks.cells.get(index).pos;TileEntityConstructor machine=ArmCoreUse.owner(payload,arm,owner);if(machine==null||!machine.getPos().equals(owner)||!ArmCoreUse.available(actor,arm,payload,machine))return null;
        if(actor instanceof EntityPlayerMP){EntityPlayerMP server=(EntityPlayerMP)actor;Vec3d eye=actor.getPositionEyes(1);RayTraceResult hit=GearInteractions.trace(world,eye,eye.add(actor.getLook(1).scale(actor.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue()+.5)));if(hit==null||!(hit.hitInfo instanceof ArmPayloadHarvest.Target)||!((ArmPayloadHarvest.Target)hit.hitInfo).root.equals(arm.getPos())||ArmCoreUse.owner(payload,arm,((ArmPayloadHarvest.Target)hit.hitInfo).cell)!=machine)return null;ArmCoreUse.permission(server,arm,owner);}
        return new ContainerCarriedCore(actor,arm,machine);
    }
    @Override public boolean carriedMachine(){return true;}
    @Override public boolean canInteractWith(EntityPlayer actor){if(actor==null||!actorId.equals(actor.getUniqueID())||arm.payload()!=expected)return false;try{TileEntityConstructor machine=ArmCoreUse.owner(expected,arm,owner);if(machine==null||!ArmCoreUse.available(actor,arm,expected,machine))return false;if(actor instanceof EntityPlayerMP)ArmCoreUse.permission((EntityPlayerMP)actor,arm,owner);return true;}catch(IllegalArgumentException unavailable){return false;}}
    @Override public void configureMachine(EntityPlayerMP actor,com.vandorlabs.navigation.NavigationScreenSettings settings,int[] surfaces,int mask,int behavior,com.vandorlabs.redstone.ChannelList channels,com.vandorlabs.drives.DriveSettings drive){
        if(actor.openContainer!=this||!canInteractWith(actor)||mask!=127||behavior!=0)return;
        TileEntityConstructor machine=ArmCoreUse.owner(expected,arm,owner);if(surfaces==null||surfaces.length!=machine.surfaceCount())return;for(int choice:surfaces)if(!TileEntityConstructor.validSurface(choice))return;if(drive!=null&&!machine.isDrive())return;
        // Proxy methods retain the established validation/activation semantics; no view has physical mutation authority.
        NBTTagCompound restore=machine.writeToNBT(new NBTTagCompound()),data;
        try{if(drive!=null)machine.configureDrive(drive);machine.configureMachine(settings,surfaces,false,mask,behavior,channels);NBTTagCompound edited=machine.writeToNBT(new NBTTagCompound());data=expected.blocks.at(owner).tileData();for(String key:ArmCoreUse.CONFIG)if(edited.hasKey(key))data.setTag(key,edited.getTag(key).copy());else data.removeTag(key);}finally{machine.readFromNBT(restore);}
        ArmPayload next=expected.withCoreData(owner,data,false);arm.installCoreSettings(expected,next);expected=next;
    }
}
