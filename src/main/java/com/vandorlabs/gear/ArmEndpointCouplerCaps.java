package com.vandorlabs.gear;

import com.vandorlabs.coupler.TileCoupler;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Focus-only attached capability proves synthetic serialization is not an authoritative snapshot. */
public final class ArmEndpointCouplerCaps {
    private static boolean enabled;
    public static void enable(){if(!enabled){enabled=true;net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(ArmEndpointCouplerCaps.class);}}
    public static final ResourceLocation KEY=new ResourceLocation("vandorlabs","endpoint_capture_probe");
    @SubscribeEvent public static void attach(AttachCapabilitiesEvent<TileEntity> event){if(enabled&&event.getObject() instanceof TileCoupler)event.addCapability(KEY,new Probe(event.getObject()));}
    private static final class Probe implements ICapabilitySerializable<NBTTagCompound>{
        final TileEntity tile;Probe(TileEntity tile){this.tile=tile;}
        public boolean hasCapability(Capability<?> capability,EnumFacing face){return false;}
        public <T>T getCapability(Capability<T> capability,EnumFacing face){return null;}
        public NBTTagCompound serializeNBT(){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("OpaqueRepresentation",tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld?47:22);tag.setString("OriginalOwner","endpoint-native");return tag;}
        public void deserializeNBT(NBTTagCompound tag){}
    }
    private ArmEndpointCouplerCaps(){}
}
