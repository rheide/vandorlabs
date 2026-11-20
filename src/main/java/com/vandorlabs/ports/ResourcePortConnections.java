package com.vandorlabs.ports;

import java.lang.invoke.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import java.lang.reflect.*;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

/** Read-only optional connection observation; resource transfer stays on standard Forge handlers. */
public final class ResourcePortConnections {
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
    /** Native transporter insertion is a transit request, not an inventory cast.
     * The adjacent source coordinate is required by Mekanism's sided path finder. */
    public static IItemHandler itemHandler(TileEntity tile,EnumFacing side){return itemHandler(tile,side,tile==null||side==null?null:tile.getPos().offset(side));}
    public static IItemHandler itemHandler(TileEntity tile,EnumFacing side,BlockPos source){
        if(tile==null||side==null||source==null||!source.equals(tile.getPos().offset(side)))return null;
        IItemHandler standard=tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,side);if(standard!=null)return standard;
        Object transporter=itemPipe(tile,side);if(transporter==null||!(tile.getWorld() instanceof WorldServer))return null;
        TransitMethods methods=TRANSIT.get(transporter.getClass());return methods==null?null:new TransitHandler(tile,side,source.toImmutable(),transporter,methods);
    }
    private static final class TransitMethods{
        final Constructor<?> coord;final Method request,insert,rejected,color;
        TransitMethods(Class<?> type)throws ReflectiveOperationException{
            ClassLoader loader=type.getClassLoader();Class<?> coordType=Class.forName("mekanism.api.Coord4D",false,loader),requestType=Class.forName("mekanism.common.content.transporter.TransitRequest",false,loader),colorType=Class.forName("mekanism.api.EnumColor",false,loader);
            coord=coordType.getConstructor(BlockPos.class,net.minecraft.world.World.class);request=requestType.getMethod("getFromStack",ItemStack.class);
            insert=type.getMethod("insert",coordType,requestType,colorType,boolean.class,int.class);rejected=insert.getReturnType().getMethod("getRejected",ItemStack.class);color=type.getMethod("getColor");
        }
    }
    private static final ClassValue<TransitMethods> TRANSIT=new ClassValue<TransitMethods>(){
        @Override protected TransitMethods computeValue(Class<?> type){try{return new TransitMethods(type);}catch(ReflectiveOperationException|LinkageError unsupported){return null;}}
    };
    private static final class TransitHandler implements IItemHandler{
        final TileEntity tile;final EnumFacing side;final BlockPos source;final Object transporter;final TransitMethods methods;
        TransitHandler(TileEntity tile,EnumFacing side,BlockPos source,Object transporter,TransitMethods methods){this.tile=tile;this.side=side;this.source=source;this.transporter=transporter;this.methods=methods;}
        private boolean current(){
            if(!(tile.getWorld() instanceof WorldServer)||tile.isInvalid())return false;WorldServer world=(WorldServer)tile.getWorld();
            return world.getMinecraftServer().isCallingFromMinecraftThread()&&world.isBlockLoaded(tile.getPos(),false)&&world.isBlockLoaded(source,false)&&world.getTileEntity(tile.getPos())==tile&&connected(tile,side)&&itemPipe(tile,side)==transporter;
        }
        private void slot(int slot){if(slot!=0)throw new IndexOutOfBoundsException("Transporter insertion slot");}
        @Override public int getSlots(){return current()?1:0;}
        @Override public ItemStack getStackInSlot(int slot){slot(slot);return ItemStack.EMPTY;}
        @Override public int getSlotLimit(int slot){slot(slot);return 64;}
        @Override public ItemStack extractItem(int slot,int amount,boolean simulate){slot(slot);return ItemStack.EMPTY;}
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){
            slot(slot);if(stack.isEmpty()||!current())return stack;
            try{
                ItemStack offered=stack.copy();offered.setCount(Math.min(64,stack.getCount()));int count=offered.getCount();Object from=methods.coord.newInstance(source,tile.getWorld()),request=methods.request.invoke(null,offered);
                Object response=methods.insert.invoke(transporter,from,request,methods.color.invoke(transporter),!simulate,1);
                ItemStack remainder=(ItemStack)methods.rejected.invoke(response,offered);
                if(remainder==null||!remainder.isEmpty()&&(!ItemStack.areItemsEqual(remainder,stack)||!ItemStack.areItemStackTagsEqual(remainder,stack)||remainder.getCount()>count))throw new IllegalStateException("Invalid native transporter response");
                ItemStack result=stack.copy();result.shrink(count-remainder.getCount());return result;
            }catch(InvocationTargetException failure){Throwable cause=failure.getCause();if(cause instanceof RuntimeException)throw (RuntimeException)cause;if(cause instanceof Error)throw (Error)cause;throw new IllegalStateException("Native transporter insertion failed",cause);}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("Native transporter adapter changed",failure);}
        }
    }
    private ResourcePortConnections(){}
}
