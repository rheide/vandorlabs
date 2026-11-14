package com.vandorlabs.neoforge.ports;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class NativeFluidPortEntity extends NativeResourcePortEntity {
    public static final int CAPACITY=64000;
    private FluidStack fluid=FluidStack.EMPTY;
    private final IFluidHandler[] faces=new IFluidHandler[6];
    public NativeFluidPortEntity(BlockPos pos,BlockState state){super(pos,state,NativePortKind.FLUID);for(int i=0;i<6;i++)faces[i]=new Face(i);}
    public FluidStack stored(){return fluid.copy();}public IFluidHandler fluid(Direction side){return side!=null&&settings().face(side.get3DDataValue())!=com.vandorlabs.ports.ResourcePortSettings.Face.DISABLED?faces[side.get3DDataValue()]:null;}
    @Override protected int storedUnits(){return fluid.getAmount();}
    private boolean accepts(FluidStack stack){return !stack.isEmpty()&&(fluid.isEmpty()||FluidStack.isSameFluidSameComponents(fluid,stack));}
    private void add(FluidStack stack,int amount){if(fluid.isEmpty())fluid=stack.copyWithAmount(amount);else fluid.grow(amount);}
    private void remove(int amount){fluid.shrink(amount);if(fluid.isEmpty())fluid=FluidStack.EMPTY;}
    @Override protected Object resolve(BlockPos pos,Direction face){return level.getCapability(Capabilities.FluidHandler.BLOCK,pos,face);}
    @Override protected boolean compatible(Object raw,boolean receiving){return raw instanceof IFluidHandler handler&&handler.getTanks()>0;}
    @Override protected int simulateCapacity(int side,int max){if(fluid.isEmpty()||max<=0)return 0;var raw=neighbor(side);return raw instanceof IFluidHandler handler?Math.min(max,Math.max(0,handler.fill(fluid.copyWithAmount(max),IFluidHandler.FluidAction.SIMULATE))):0;}
    @Override protected void transferTick(){
        if(settings().pullInputs&&fluid.getAmount()<CAPACITY)for(int i=0;i<6&&fluid.getAmount()<CAPACITY;i++)if(input(i)){var raw=neighbor(i);if(!(raw instanceof IFluidHandler source))continue;int room=CAPACITY-fluid.getAmount();var offer=fluid.isEmpty()?source.drain(room,IFluidHandler.FluidAction.SIMULATE):source.drain(fluid.copyWithAmount(room),IFluidHandler.FluidAction.SIMULATE);if(!accepts(offer))continue;var actual=source.drain(offer.copyWithAmount(Math.min(room,offer.getAmount())),IFluidHandler.FluidAction.EXECUTE);if(actual.isEmpty())continue;if(!accepts(actual)||actual.getAmount()>room){source.fill(actual,IFluidHandler.FluidAction.EXECUTE);continue;}add(actual,actual.getAmount());transferred(i,actual.getAmount(),false);}
        if(fluid.isEmpty()||availableOutput()==0)return;int offer=boundedOutput(fluid.getAmount());for(int i=0;i<6;i++){capacities[i]=0;if(output(i)){var raw=neighbor(i);if(raw instanceof IFluidHandler handler)capacities[i]=Math.min(offer,Math.max(0,handler.fill(fluid.copyWithAmount(offer),IFluidHandler.FluidAction.SIMULATE)));}}allocate(fluid.getAmount());
        for(int i=0;i<6;i++)if(grants[i]>0&&!fluid.isEmpty()){var raw=neighbor(i);if(!(raw instanceof IFluidHandler handler))continue;int count=(int)Math.min(grants[i],boundedOutput(fluid.getAmount()));if(count<=0)continue;int accepted=Math.min(count,Math.max(0,handler.fill(fluid.copyWithAmount(count),IFluidHandler.FluidAction.EXECUTE)));if(accepted>0){remove(accepted);transferred(i,accepted,true);}}
    }
    @Override protected void writeContents(CompoundTag tag,HolderLookup.Provider registries){if(!fluid.isEmpty())tag.put("PortFluid",fluid.save(registries));else tag.remove("PortFluid");}
    @Override protected void readContents(CompoundTag tag,HolderLookup.Provider registries){if(tag.get("PortFluid") instanceof CompoundTag old&&old.contains("FluidName")){
        if(tag.contains("NativePortSchema")||!old.contains("FluidName",Tag.TAG_STRING)||!old.contains("Amount",Tag.TAG_INT)||old.getInt("Amount")<=0)throw new IllegalArgumentException("Invalid legacy fluid port contents");
        for(String key:old.getAllKeys())if(!java.util.Set.of("FluidName","Amount","Tag").contains(key))throw new IllegalArgumentException("Unknown legacy fluid fields");
        if(old.contains("Tag")&&(!(old.get("Tag") instanceof CompoundTag extra)||!extra.isEmpty()))throw new IllegalArgumentException("Unsupported legacy fluid metadata");
        var id=net.minecraft.resources.ResourceLocation.tryParse(old.getString("FluidName"));if(id==null||!net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(id))throw new IllegalArgumentException("Unknown legacy fluid identity");var type=net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id);if(type==net.minecraft.world.level.material.Fluids.EMPTY)throw new IllegalArgumentException("Empty legacy fluid identity");fluid=new FluidStack(type,old.getInt("Amount"));
    }else fluid=tag.contains("PortFluid")?FluidStack.parse(registries,tag.get("PortFluid")).orElseThrow(()->new IllegalArgumentException("Unknown fluid port contents")):FluidStack.EMPTY;if(fluid.getAmount()>CAPACITY)throw new IllegalArgumentException("Oversized fluid port contents");}
    private final class Face implements IFluidHandler {
        final int side;Face(int side){this.side=side;}
        public int getTanks(){return 1;}public FluidStack getFluidInTank(int tank){check(tank);return stored();}public int getTankCapacity(int tank){check(tank);return CAPACITY;}public boolean isFluidValid(int tank,FluidStack stack){check(tank);return input(side)&&accepts(stack);}
        private void check(int tank){if(tank!=0)throw new IllegalArgumentException("Invalid fluid port tank");}
        public int fill(FluidStack stack,FluidAction action){if(!input(side)||!transferAllowed()||!accepts(stack))return 0;int count=Math.min(stack.getAmount(),CAPACITY-fluid.getAmount());if(action.execute()&&count>0){add(stack,count);transferred(side,count,false);}return count;}
        public FluidStack drain(FluidStack stack,FluidAction action){if(stack.isEmpty()||fluid.isEmpty()||!FluidStack.isSameFluidSameComponents(stack,fluid))return FluidStack.EMPTY;return drain(stack.getAmount(),action);}
        public FluidStack drain(int max,FluidAction action){if(max<=0||!output(side)||!transferAllowed()||fluid.isEmpty())return FluidStack.EMPTY;int count=Math.min(fluid.getAmount(),boundedOutput(side,max));if(count<=0)return FluidStack.EMPTY;var result=fluid.copyWithAmount(count);if(action.execute()){remove(count);transferred(side,count,true);}return result;}
    }
}
