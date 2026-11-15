package com.vandorlabs.neoforge.reservoir;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.ports.*;
import com.vandorlabs.neoforge.redstone.*;
import com.vandorlabs.ports.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.capabilities.Capabilities;

/** One 100k energy owner with the regular Power Port policy and retained Battery body/legacy saves. */
public final class NativeBatteryEntity extends NativeBufferEntity implements NativePortOwner,NativeChannelMember {
 public static final int CAPACITY=100000,RATE=1000;
 private int energy,legacyRate,lit,connected;
 private NativePortData.Configuration portConfig;
 private final long[] capacities=new long[6],grants=new long[6];
 private final NativePortRuntime runtime;
 private final NativePowerTransfer transfer;
 public NativeBatteryEntity(BlockPos pos,BlockState state){
  super(NativeBufferBindings.BATTERY_ENTITY,pos,state,true);portConfig=NativePortData.placed(facing().get3DDataValue());
  runtime=new NativePortRuntime(new NativePortRuntime.Host(){
   public BlockEntity tile(){return NativeBatteryEntity.this;}public NativePortData.Configuration configuration(){return portConfig;}
   public boolean installed(){return NativeBatteryEntity.this.installed();}public boolean inert(){return NativeBatteryEntity.this.inert();}
   public int storedUnits(){return energy;}public int simulateCapacity(int side,int max){return transfer.simulateCapacity(side,max);}
   public Object resolve(BlockPos pos,Direction side){return level.getCapability(Capabilities.EnergyStorage.BLOCK,pos,side);}
   public void transferTick(){transfer.tick();}public void appearance(int next,int adjacent){connected=adjacent;if(next>=0){lit=next;contentsChanged();}}
  },capacities,grants);
  transfer=new NativePowerTransfer(runtime,new NativePowerTransfer.Buffer(){public int amount(){return energy;}public int capacity(){return CAPACITY;}public int legacyRate(){return legacyRate;}public boolean inert(){return NativeBatteryEntity.this.inert();}public void change(int delta){energy+=delta;contentsChanged();}},capacities,grants);
 }
 public IEnergyStorage energy(Direction side){return transfer.energy(side,true);}
 public int amount(){return inert()?0:energy;}public int capacity(){return CAPACITY;}
 public ResourcePortSettings portSettings(){return portConfig.settings();}public int appearanceMask(){return lit;}public int connectedMask(){return connected;}
 @Override public BlockEntity portBlockEntity(){return this;}@Override public NativePortKind portKind(){return NativePortKind.POWER;}@Override public NativePortData.Configuration configuration(){return portConfig;}
 @Override public boolean configure(NativePortData.Configuration next){
  if(next==null||!installed()||next.settings().housingChoice!= -1&&!ModernFinishCatalog.validChoice(next.settings().housingChoice))return false;if(next.equals(portConfig))return true;
  var old=portConfig.settings();var policy=next.settings();if(old.throughput!=policy.throughput||old.pullInputs!=policy.pullInputs||!java.util.Arrays.equals(old.faces(),policy.faces()))legacyRate=0;
  portConfig=next;installCasing(next.settings().housingChoice,1);runtime.configurationChanged(this);changed();level.invalidateCapabilities(worldPosition);level.updateNeighborsAt(worldPosition,getBlockState().getBlock());return true;
 }
 @Override protected void casingConfigured(int choice){portConfig=new NativePortData.Configuration(portConfig.settings().withHousing(choice),portConfig.redstoneMode(),portConfig.channels());}
 public void neighborChanged(){runtime.neighborChanged();}
 public static void tick(Level level,BlockPos pos,BlockState state,NativeBatteryEntity owner){owner.runtime.tick();owner.serverTick();}
 @Override public void onLoad(){super.onLoad();runtime.onLoad(this);}@Override public void beforeChannelRegistration(){runtime.beforeChannelRegistration();}
 @Override public void onChunkUnloaded(){runtime.unload(this);super.onChunkUnloaded();}@Override public void setRemoved(){runtime.unload(this);super.setRemoved();}
 @Override public BlockEntity channelBlockEntity(){return this;}@Override public ChannelList getRedstoneChannels(){return inert()?ChannelList.EMPTY:portConfig.channels();}@Override public void setChannelLevel(int value){runtime.signal(value);}
 protected void readContents(CompoundTag tag,HolderLookup.Provider registries){
  if(tag.contains("Energy")&&!tag.contains("Energy",Tag.TAG_INT))throw new IllegalArgumentException("Invalid battery contents");energy=tag.getInt("Energy");if(energy<0||energy>CAPACITY)throw new IllegalArgumentException("Oversized battery contents");
  if(tag.contains("PortEnergy")||tag.contains("PortFluid")||tag.contains("PortItems"))throw new IllegalArgumentException("Battery contains another buffer owner");
  if(tag.contains("PortFaces")){
   portConfig=NativePortData.read(tag);legacyRate=tag.getInt("BatteryLegacyRate");if(tag.contains("BatteryLegacyRate")&&(!tag.contains("BatteryLegacyRate",Tag.TAG_INT)||legacyRate!=0&&legacyRate!=RATE))throw new IllegalArgumentException("Unknown battery legacy rate");
  }else{
   for(String key:new String[]{"PortThroughput","PortPullInputs","PortOnTexture","PortRedstoneMode","PortHousing"})if(tag.contains(key))throw new IllegalArgumentException("Incomplete Battery port policy");
   boolean saved=tag.contains("Energy")||tag.contains("BlockShape")||tag.contains("NativeBufferSchema");legacyRate=saved?RATE:0;
   var settings=ResourcePortSettings.placed(facing().get3DDataValue()).withTransfer(saved?RATE:0,false,ResourcePortSettings.OnTexture.TRANSFERRING).withHousing(casing());portConfig=new NativePortData.Configuration(settings,0,ChannelList.EMPTY);
  }
  installCasing(portConfig.settings().housingChoice,1);lit=tag.getInt("PortLitFaces")&63;runtime.loadedData();
 }
 protected void writeContents(CompoundTag tag,HolderLookup.Provider registries){tag.putInt("Energy",energy);NativePortData.write(tag,portConfig);if(legacyRate>0)tag.putInt("BatteryLegacyRate",legacyRate);else tag.remove("BatteryLegacyRate");tag.putInt("PortLitFaces",lit);}
}
