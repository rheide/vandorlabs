package com.vandorlabs.ports;
import java.lang.invoke.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.energy.CapabilityEnergy;
/** Presence only: Forge Energy first, then the common legacy RF connection contract when needed. */
public final class EnergyEndpointPresence {
 private static final ClassValue<MethodHandle> LEGACY=new ClassValue<MethodHandle>(){protected MethodHandle computeValue(Class<?> type){try{return MethodHandles.publicLookup().findVirtual(type,"canConnectEnergy",MethodType.methodType(boolean.class,EnumFacing.class)).asType(MethodType.methodType(boolean.class,Object.class,EnumFacing.class));}catch(ReflectiveOperationException|LinkageError unavailable){return null;}}};
 public static boolean connected(TileEntity tile,EnumFacing side){if(tile==null||tile.isInvalid())return false;if(tile.getCapability(CapabilityEnergy.ENERGY,side)!=null)return true;MethodHandle legacy=LEGACY.get(tile.getClass());if(legacy==null)return false;try{return (boolean)legacy.invokeExact((Object)tile,side);}catch(Throwable unavailable){if(unavailable instanceof Error&&!(unavailable instanceof LinkageError))throw (Error)unavailable;return false;}}
 private EnergyEndpointPresence(){}
}
