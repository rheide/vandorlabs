package com.vandorlabs.ports;

import java.lang.invoke.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

/** Read-only optional connection observation; resource transfer stays on standard Forge handlers. */
final class ResourcePortConnections {
    private static Capability<?> logistics;
    private static boolean logisticsLookedUp;
    private static final ClassValue<MethodHandle> CONNECTIONS=new ClassValue<MethodHandle>(){
        @Override protected MethodHandle computeValue(Class<?> type){
            try{return MethodHandles.publicLookup().findVirtual(type,"getAllCurrentConnections",MethodType.methodType(byte.class)).asType(MethodType.methodType(byte.class,TileEntity.class));}
            catch(ReflectiveOperationException|LinkageError unsupported){return null;}
        }
    };
    private static boolean mekanism(TileEntity tile){return tile.getClass().getName().startsWith("mekanism.common.tile.transmitter.");}
    static boolean connected(TileEntity tile,EnumFacing side){
        if(!mekanism(tile))return true;MethodHandle method=CONNECTIONS.get(tile.getClass());if(method==null)return false;
        try{return (((byte)method.invokeExact(tile))&(1<<side.getIndex()))!=0;}catch(Throwable unsupported){if(unsupported instanceof Error&&!(unsupported instanceof LinkageError))throw (Error)unsupported;return false;}
    }
    static Object itemPipe(TileEntity tile,EnumFacing side){
        if(!mekanism(tile)||!connected(tile,side))return null;
        if(!logisticsLookedUp){logisticsLookedUp=true;try{Class<?> type=Class.forName("mekanism.common.capabilities.Capabilities",false,tile.getClass().getClassLoader());logistics=(Capability<?>)type.getField("LOGISTICAL_TRANSPORTER_CAPABILITY").get(null);}catch(ReflectiveOperationException|LinkageError unsupported){logistics=null;}}
        return logistics==null?null:tile.getCapability(logistics,side);
    }
    private ResourcePortConnections(){}
}
