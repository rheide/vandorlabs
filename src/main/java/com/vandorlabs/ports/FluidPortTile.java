package com.vandorlabs.ports;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.*;

/** One bounded fluid buffer; rejected output stays owned here. */
public final class FluidPortTile extends ResourcePortTile {
    public static final int CAPACITY=64000;
    private FluidStack fluid;
    private final IFluidHandler[] faces=new IFluidHandler[6];
    public FluidPortTile(){for(int i=0;i<6;i++)faces[i]=new Face(i);}
    public FluidStack stored(){return fluid==null?null:fluid.copy();}
    public int returnCarriedFluid(FluidStack resource){
        if(!accepts(resource)||!com.vandorlabs.vehicle.VehicleResources.live(this))return 0;
        int count=Math.min(resource.amount,CAPACITY-amount());if(count>0){add(resource,count);markDirty();}return count;
    }
    private int amount(){return fluid==null?0:fluid.amount;}
    @Override protected int storedUnits(){return amount();}
    @Override protected int simulateOutputCapacity(int side,int maximum){if(fluid==null||maximum<=0)return 0;Object raw=neighbor(side);return raw instanceof IFluidHandler?Math.min(maximum,Math.max(0,((IFluidHandler)raw).fill(portion(fluid,maximum),false))):0;}
    private boolean accepts(FluidStack stack){return stack!=null&&stack.amount>0&&(fluid==null||fluid.isFluidEqual(stack));}
    private void add(FluidStack stack,int amount){if(fluid==null){fluid=stack.copy();fluid.amount=amount;}else fluid.amount+=amount;}
    private void remove(int amount){fluid.amount-=amount;if(fluid.amount==0)fluid=null;}
    private static FluidStack portion(FluidStack fluid,int amount){FluidStack part=fluid.copy();part.amount=amount;return part;}
    @Override protected Object findHandler(TileEntity tile,EnumFacing side){return world instanceof com.vandorlabs.vehicle.VehicleWorld?com.vandorlabs.vehicle.VehicleResources.endpoint(tile,side,CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY):tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,side);}
    @Override protected boolean compatible(Object handler,boolean receiving){
        if(!(handler instanceof IFluidHandler))return false;IFluidTankProperties[] properties=((IFluidHandler)handler).getTankProperties();if(properties==null)return false;
        for(IFluidTankProperties tank:properties)if(tank!=null&&(receiving?tank.canFill():tank.canDrain()))return true;return false;
    }
    @Override protected void transferTick(){
        if(settings().pullInputs&&amount()<CAPACITY)for(int i=0;i<6&&amount()<CAPACITY;i++)if(input(i)){
            Object raw=neighbor(i);if(!(raw instanceof IFluidHandler))continue;IFluidHandler source=(IFluidHandler)raw;int room=CAPACITY-amount();
            FluidStack offer=fluid==null?source.drain(room,false):source.drain(portion(fluid,room),false);if(!accepts(offer))continue;int request=Math.min(room,offer.amount);
            FluidStack actual=source.drain(portion(offer,request),true);if(actual==null||actual.amount<=0)continue;
            if(!accepts(actual)||actual.amount>room){source.fill(actual,true);continue;}
            add(actual,actual.amount);transferred(i,actual.amount,false);
        }
        if(fluid==null||availableOutput()==0)return;int offer=boundedOutput(fluid.amount);
        for(int i=0;i<6;i++){capacities[i]=0;if(output(i)){Object raw=neighbor(i);if(raw instanceof IFluidHandler)capacities[i]=Math.min(offer,Math.max(0,((IFluidHandler)raw).fill(portion(fluid,offer),false)));}}
        allocate(fluid.amount);
        for(int i=0;i<6;i++)if(grants[i]>0&&fluid!=null){Object raw=neighbor(i);if(!(raw instanceof IFluidHandler))continue;int count=(int)Math.min(grants[i],boundedOutput(fluid.amount));if(count<=0)continue;
            int accepted=Math.min(count,Math.max(0,((IFluidHandler)raw).fill(portion(fluid,count),true)));if(accepted>0){remove(accepted);transferred(i,accepted,true);}
        }
    }
    @Override protected void writeContents(NBTTagCompound tag){if(fluid!=null)tag.setTag("PortFluid",fluid.writeToNBT(new NBTTagCompound()));}
    @Override protected void readContents(NBTTagCompound tag){
        if(tag.hasKey("PortEnergy")||tag.hasKey("Items"))throw new IllegalArgumentException("Mixed fluid port resource owners");
        fluid=null;if(!tag.hasKey("PortFluid"))return;
        if(!tag.hasKey("PortFluid",10))throw new IllegalArgumentException("Invalid saved fluid port contents");
        NBTTagCompound raw=tag.getCompoundTag("PortFluid");FluidStack decoded=FluidStack.loadFluidStackFromNBT(raw);
        if(decoded==null||decoded.amount<=0||decoded.amount>CAPACITY||!decoded.writeToNBT(new NBTTagCompound()).equals(raw))throw new IllegalArgumentException("Unknown saved fluid port contents");
        fluid=decoded;
    }
    @Override public boolean hasCapability(Capability<?> cap,EnumFacing side){return cap==CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY?!inert()&&side!=null&&settings().face(side.getIndex())!=ResourcePortSettings.Face.DISABLED:super.hasCapability(cap,side);}
    @Override public <T>T getCapability(Capability<T> cap,EnumFacing side){if(cap==CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)return hasCapability(cap,side)?CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(faces[side.getIndex()]):null;return super.getCapability(cap,side);}
    private final class Face implements IFluidHandler {
        final int side;private final IFluidTankProperties[] properties;
        Face(int side){this.side=side;properties=new IFluidTankProperties[]{new IFluidTankProperties(){
            public FluidStack getContents(){return stored();}public int getCapacity(){return CAPACITY;}public boolean canFill(){return input(side);}public boolean canDrain(){return output(side);}
            public boolean canFillFluidType(FluidStack resource){return input(side)&&accepts(resource);}public boolean canDrainFluidType(FluidStack resource){return output(side)&&fluid!=null&&resource!=null&&fluid.isFluidEqual(resource);}
        }};}
        @Override public IFluidTankProperties[] getTankProperties(){return properties.clone();}
        @Override public int fill(FluidStack resource,boolean commit){if(!input(side)||!transferAllowed()||!accepts(resource))return 0;int count=Math.min(resource.amount,CAPACITY-amount());if(commit&&count>0){add(resource,count);transferred(side,count,false);}return count;}
        @Override public FluidStack drain(FluidStack resource,boolean commit){if(resource==null||fluid==null||!resource.isFluidEqual(fluid))return null;return drain(resource.amount,commit);}
        @Override public FluidStack drain(int max,boolean commit){if(max<=0||!output(side)||!transferAllowed()||fluid==null)return null;int count=Math.min(fluid.amount,boundedOutput(side,max));if(count==0)return null;FluidStack result=portion(fluid,count);if(commit){remove(count);transferred(side,count,true);}return result;}
    }
}
