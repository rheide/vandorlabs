package com.vandorlabs.neoforge.reservoir;
import net.minecraft.core.*;import net.minecraft.nbt.*;import net.minecraft.world.level.Level;import net.minecraft.world.level.block.state.BlockState;import net.neoforged.neoforge.energy.IEnergyStorage;import net.neoforged.neoforge.capabilities.Capabilities;
/** Source-compatible 100k RF cell with front input and rear output, each limited to 1000 RF per call. */
public final class NativeBatteryEntity extends NativeBufferEntity {
 public static final int CAPACITY=100000,RATE=1000;private int energy;
 private final IEnergyStorage input=new Port(true,false),output=new Port(false,true),both=new Port(true,true);
 public NativeBatteryEntity(BlockPos pos,BlockState state){super(NativeBufferBindings.BATTERY_ENTITY,pos,state,true);}
 public IEnergyStorage energy(Direction side){return side==null?both:side==facing()?input:side==facing().getOpposite()?output:null;}
 public int amount(){return inert()?0:energy;}public int capacity(){return CAPACITY;}
 private void resourceChanged(){contentsChanged();}
 private final class Port implements IEnergyStorage {final boolean receive,extract;Port(boolean r,boolean e){receive=r;extract=e;}
  public int receiveEnergy(int max,boolean simulate){int n=installed()&&receive?Math.min(RATE,Math.min(Math.max(0,max),CAPACITY-energy)):0;if(!simulate&&n>0){energy+=n;resourceChanged();}return n;}
  public int extractEnergy(int max,boolean simulate){int n=installed()&&extract?Math.min(RATE,Math.min(Math.max(0,max),energy)):0;if(!simulate&&n>0){energy-=n;resourceChanged();}return n;}
  public int getEnergyStored(){return amount();}public int getMaxEnergyStored(){return CAPACITY;}public boolean canReceive(){return receive&&!inert();}public boolean canExtract(){return extract&&!inert();}
 }
 public static void tick(Level level,BlockPos pos,BlockState state,NativeBatteryEntity owner){if(!owner.installed())return;var side=owner.facing().getOpposite();var next=pos.relative(side);if(owner.energy>0&&level.hasChunkAt(next)){var target=level.getCapability(Capabilities.EnergyStorage.BLOCK,next,side.getOpposite());if(target!=null&&target.canReceive()){int accepted=Math.max(0,Math.min(Math.min(RATE,owner.energy),target.receiveEnergy(Math.min(RATE,owner.energy),false)));owner.output.extractEnergy(accepted,false);}}owner.serverTick();}
 protected void readContents(CompoundTag tag,HolderLookup.Provider registries){if(tag.contains("Energy")&&!tag.contains("Energy",Tag.TAG_INT))throw new IllegalArgumentException("Invalid battery contents");energy=tag.getInt("Energy");if(energy<0||energy>CAPACITY)throw new IllegalArgumentException("Oversized battery contents");}
 protected void writeContents(CompoundTag tag,HolderLookup.Provider registries){tag.putInt("Energy",energy);}
}
