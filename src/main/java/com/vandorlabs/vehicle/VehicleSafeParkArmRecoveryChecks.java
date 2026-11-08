package com.vandorlabs.vehicle;

import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.items.*;
import com.vandorlabs.redstone.ChannelList;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Actual fractional arm Safe Park, partial destination save and two cold verifications. */
public final class VehicleSafeParkArmRecoveryChecks {
 private static final BlockPos BASE=new BlockPos(600,101,600);
 private static int stage,ticks,checks;private static long createdAt;private static EntityGroundVehicle craft;private static BlockPos armPos;
 private static CountDownLatch release;private static Future<?> blocker;private static TransferCheckpoint checkpoint;
 private static final Map<UUID,NBTTagCompound> JOINED=new HashMap<>();
 private static void need(boolean ok,String why){checks++;if(!ok){if(release!=null)release.countDown();throw new IllegalStateException("Safe Park arm cold: "+why+" stage="+stage+" ticks="+ticks);}}
 private static File metadata(WorldServer w){return new File(w.getSaveHandler().getWorldDirectory(),"safe-park-arm-fixture.nbt");}
 private static NBTTagCompound read(File f)throws IOException{try(InputStream in=new FileInputStream(f)){return CompressedStreamTools.readCompressed(in);}}
 private static void write(File f,NBTTagCompound n)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();CompressedStreamTools.writeCompressed(n,bytes);try(FileOutputStream out=new FileOutputStream(f)){out.write(bytes.toByteArray());out.getFD().sync();}}
 private static Object field(Object owner,String key)throws Exception{java.lang.reflect.Field f=(owner instanceof Class?(Class<?>)owner:owner.getClass()).getDeclaredField(key);f.setAccessible(true);return f.get(owner instanceof Class?null:owner);}
 private static Object work(WorldServer w)throws Exception{ArrayDeque<?> q=(ArrayDeque<?>)((Map<?,?>)field(VehicleTransfer.class,"WORK")).get(w);return q==null?null:q.peek();}
 /** Registered before world launch; observation only, never changes the incoming owner. */
 @SubscribeEvent public void joined(EntityJoinWorldEvent e){if(!e.getWorld().isRemote&&e.getEntity() instanceof EntityGroundVehicle){EntityGroundVehicle c=(EntityGroundVehicle)e.getEntity();if(c.structure!=null&&JOINED.size()<8)JOINED.putIfAbsent(c.getUniqueID(),c.writeToNBT(new NBTTagCompound()));}}
 private static GearTileEntity arm(){return (GearTileEntity)craft.view.getTileEntity(armPos);}
 private static Set<Chunk> chunks(WorldServer w){Set<Chunk> result=new LinkedHashSet<>();for(int x=(BASE.getX()-20)>>4;x<=(BASE.getX()+25)>>4;x++)for(int z=(BASE.getZ()-20)>>4;z<=(BASE.getZ()+20)>>4;z++)result.add(w.getChunkFromBlockCoords(new BlockPos(x<<4,BASE.getY(),z<<4)));return result;}
 public static boolean run(WorldServer w,EntityPlayerMP p,String phase)throws Exception{
  need(++ticks<1800,"bounded asynchronous recovery");if(ticks%20==1)System.out.println("[vandorlabs] cold-arm-server phase="+phase+" stage="+stage+" tick="+ticks+(craft!=null&&armPos!=null?" phase="+arm().progress()+" target="+arm().target():""));if(phase.startsWith("verify"))return verify(w,phase.startsWith("verify2-"));
  if(stage==0){craft=VehicleSafeParkFixture.create(w,p,BASE,VehicleSafeParkArmFixture::place);craft.rotationYaw=0;for(VehicleStructure.Cell c:craft.structure.cells)if(craft.view.getTileEntity(c.pos) instanceof GearTileEntity)armPos=c.pos;need(armPos!=null,"actual placed arm captured");
   for(BlockPos at:BlockPos.getAllInBox(BASE.add(-20,-1,-20),BASE.add(25,-1,20)))w.setBlockToAir(at);
   for(BlockPos at:BlockPos.getAllInBox(BASE.add(14,-1,-8),BASE.add(25,-1,8)))w.setBlockState(at,Blocks.STONE.getDefaultState(),2);
   Vec3d control=craft.toWorld(new Vec3d(craft.structure.controller().pos).addVector(.5,.5,.5));p.setPositionAndUpdate(control.x,control.y,control.z-3);createdAt=w.getTotalWorldTime();stage=-1;return false;}
  if(stage==-1){if(w.getTotalWorldTime()-createdAt<10)return false;ItemStack transmitter=new ItemStack(ModItems.REDSTONE_TRANSMITTER,1,3);ItemRedstoneTransmitter.configure(transmitter,ChannelList.of(VehicleSafeParkArmFixture.CHANNEL));p.setHeldItem(EnumHand.MAIN_HAND,transmitter);transmitter.getItem().onItemRightClick(w,p,EnumHand.MAIN_HAND);stage=1;return false;}
  if(stage==1){if(arm().target()||arm().progress()>=.85||arm().progress()<=.2)return false;need(arm().payload()!=null&&!arm().payload().materialized()&&arm().payload().gridMapping(arm(),arm().progress())==null,"genuine moving fractional captured payload");p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));Vec3d at=craft.toWorld(new Vec3d(craft.structure.controller().pos).addVector(.5,.5,.5));p.setPositionAndUpdate(at.x,at.y,at.z-3);
   CountDownLatch entered=new CountDownLatch(1);release=new CountDownLatch(1);blocker=((ExecutorService)field(VehicleTransfer.class,"IO")).submit(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});need(entered.await(5,TimeUnit.SECONDS),"existing transfer worker blocked");VehicleSafePark.submit(p,craft,craft.structure.controller().pos);Object submitted=work(w);need(submitted!=null,"actual service queued");java.lang.reflect.Field failure=submitted.getClass().getDeclaredField("failed");failure.setAccessible(true);java.util.function.Consumer<Exception> previous=(java.util.function.Consumer<Exception>)failure.get(submitted);failure.set(submitted,(java.util.function.Consumer<Exception>)(error->{System.out.println("[vandorlabs] cold-arm-service-refusal "+error+" actor="+p.getPositionVector()+" controller="+craft.toWorld(new Vec3d(craft.structure.controller().pos).addVector(.5,.5,.5))+" flying="+p.capabilities.isFlying);previous.accept(error);}));stage=2;return false;}
  if(stage==2){Object operation=work(w);if(operation==null||field(operation,"held")!=craft||field(operation,"beforeStructure")==null)return false;VehicleStructure before=(VehicleStructure)field(operation,"beforeStructure");need(before==craft.structure&&arm().progress()>0&&arm().progress()<1,"server preflight source identity fractional");NBTTagCompound data=new NBTTagCompound();data.setTag("Expected",craft.writeToNBT(new NBTTagCompound()));data.setTag("HeldCraft",before.write());write(metadata(w),data);checkpoint=new TransferCheckpoint(w,chunks(w));stage=3;return false;}
  if(stage==3){if(!checkpoint.step())return false;checkpoint.close();checkpoint=null;VehicleTransfer.testFault=point->{if(point.equals("painting-parked"))try{barrier(w,phase);}catch(Exception e){throw new IllegalStateException(e);}};release.countDown();blocker.get();stage=4;}
  if(stage==4)need(work(w)!=null,"actual Safe Park ended before crash barrier; inspect service refusal");return false;
 }
 private static void barrier(WorldServer w,String phase)throws Exception{
  NBTTagCompound data=read(metadata(w)),record=read(new File(w.getSaveHandler().getWorldDirectory(),"data/vandorlabs-vehicle-"+w.provider.getDimension()+".nbt"));VehicleStructure parked=VehicleStructure.read(record.getCompoundTag("ParkedCraft"));BlockPos origin=BlockPos.fromLong(record.getLong("Origin"));Set<Chunk> targets=new LinkedHashSet<>();
  for(VehicleStructure.Cell c:parked.cells){BlockPos at=origin.add(c.pos);need((at.getX()>>4)!=(craft.getPosition().getX()>>4),"destination chunk distinct from old held entity");targets.add(w.getChunkFromBlockCoords(at));}data.setTag("ParkedCraft",parked.write());data.setLong("Destination",origin.toLong());write(metadata(w),data);
  try(TransferCheckpoint partial=new TransferCheckpoint(w,targets)){long until=System.nanoTime()+45_000_000_000L;while(!partial.step()){if(System.nanoTime()>until)throw new IOException("Partial arm destination checkpoint timeout");Thread.sleep(25);}}
  System.out.println("[vandorlabs] arm-durable-crash "+phase+" held fractional source checkpoint/newer chosen native arm+cargo destination");System.out.flush();try(FileOutputStream out=new FileOutputStream(new File(w.getSaveHandler().getWorldDirectory(),"arm-crash-ready.txt"))){out.write(java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0].getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.getFD().sync();}new CountDownLatch(1).await();
 }
 private static boolean verify(WorldServer w,boolean second)throws Exception{
  NBTTagCompound data=read(metadata(w)),expected=data.getCompoundTag("Expected");UUID id=expected.getUniqueId("UUID");NBTTagList pos=expected.getTagList("Pos",6);w.getChunkFromBlockCoords(new BlockPos(pos.getDoubleAt(0),pos.getDoubleAt(1),pos.getDoubleAt(2)));if(VehicleTransfer.pending(w))return false;Entity entity=w.getEntityFromUuid(id);if(!(entity instanceof EntityGroundVehicle))return false;craft=(EntityGroundVehicle)entity;
  if(stage==0){NBTTagCompound joined=JOINED.get(id);need(joined!=null,"prelaunch read-only join observed owner before motion");if(!second)need(joined.getCompoundTag("Craft").equals(data.getCompoundTag("HeldCraft")),"exact original fractional held source at recovered join BEFORE motion");need(!craft.isDead&&!craft.ownershipBlocked&&!craft.transferInProgress&&craft.structure!=null,"recovered usable source");need(craft.hover&&craft.hoverTarget==expected.getDouble("HoverTarget"),"Hover target retained");stage=1;}
  int owners=0;for(Entity e:w.loadedEntityList)if(!e.isDead&&e.getUniqueID().equals(id))owners++;need(owners==1,"one live moving owner");VehicleStructure parked=VehicleStructure.read(data.getCompoundTag("ParkedCraft"));BlockPos origin=BlockPos.fromLong(data.getLong("Destination"));for(VehicleStructure.Cell c:parked.cells){BlockPos at=origin.add(c.pos);w.getChunkFromBlockCoords(at);need(w.isAirBlock(at)&&w.getTileEntity(at)==null,"newer chosen native owner removed "+at);}
  int diamonds=0,emeralds=0;for(net.minecraft.tileentity.TileEntity tile:craft.view.tiles.values()){if(tile instanceof TileEntityChest){TileEntityChest c=(TileEntityChest)tile;if(c.getName().equals("Safe Park cargo"))diamonds+=c.getStackInSlot(7).getCount();}if(tile instanceof GearTileEntity){GearTileEntity a=(GearTileEntity)tile;if(a.payload()!=null){net.minecraft.tileentity.TileEntity raw=a.payload().view(a).getTileEntity(BlockPos.ORIGIN);if(raw instanceof TileEntityChest)emeralds+=((TileEntityChest)raw).getStackInSlot(3).getCount();}}}need(diamonds==11&&emeralds==7,"both original cargo owners exact once");need(w.getEntitiesWithinAABB(EntityItem.class,new AxisAlignedBB(BASE).grow(40),e->e.getItem().getItem()==Items.DIAMOND||e.getItem().getItem()==Items.EMERALD).isEmpty(),"no loose duplicate cargo");
  if(checkpoint==null)checkpoint=new TransferCheckpoint(w,chunks(w));if(!checkpoint.step())return false;checkpoint.close();System.out.println("[vandorlabs] arm-durable-recovery PASS "+(second?"verify2-":"verify-")+"safe-park-arm-partial checks="+checks+(second?" stable-second-restart":" exact-pre-motion-held-source")+" chosen-native-retired cargo11+7 once stable-checkpoint");return true;
 }
 public VehicleSafeParkArmRecoveryChecks(){}
}
