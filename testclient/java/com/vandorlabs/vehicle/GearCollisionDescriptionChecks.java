package com.vandorlabs.vehicle;

import com.vandorlabs.gear.*;
import com.vandorlabs.gear.core.GearMotion;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.*;

/** Exercises the actual cache descriptor, including an installed divergent capability serializer. */
public final class GearCollisionDescriptionChecks {
    private static int serializations;
    private static volatile Object sink;
    private static com.vandorlabs.blocks.BlockPilotSeat seatBlock;
    private static final BlockPos SEAT=new BlockPos(20,0,0);
    private static VehicleStructure structure(VehicleStructure.Cell gear){return new VehicleStructure(Arrays.asList(gear,new VehicleStructure.Cell(SEAT,seatBlock.getDefaultState(),null)),SEAT,EnumFacing.NORTH,1); }
    private static final class Provider implements ICapabilitySerializable<NBTTagCompound>{
        public boolean hasCapability(Capability<?> c,EnumFacing f){return false;}public <T>T getCapability(Capability<T> c,EnumFacing f){return null;}
        public NBTTagCompound serializeNBT(){serializations++;NBTTagCompound tag=new NBTTagCompound();tag.setString("DifferentLiveRepresentation","do not replace original");return tag;}public void deserializeNBT(NBTTagCompound tag){}
    }
    public static void main(String[] args)throws Exception{
        boolean baseline=args.length>0&&Boolean.parseBoolean(args[0]);net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(GearTileEntity.class,new ResourceLocation("vandorlabs:modular_gear"));net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(GearRegistry.GEAR_EXTENDER);
        seatBlock=new com.vandorlabs.blocks.BlockPilotSeat();net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(seatBlock);
        Constructor<?> wc=Class.forName("com.vandorlabs.client.NonRenderingChecks$MemoryWorld").getDeclaredConstructor(boolean.class);wc.setAccessible(true);World parent=(World)wc.newInstance(false);Constructor<ArmPayload> pc=ArmPayload.class.getDeclaredConstructor(VehicleStructure.class,BlockPos.class,boolean.class,BlockPos.class);pc.setAccessible(true);Method tagger=ArmCouplerHeadChecks.class.getDeclaredMethod("gearTag",BlockPos.class,EnumFacing.class,GearMotion.Mode.class,double.class,NBTTagCompound.class);tagger.setAccessible(true);Method describe=VehicleCollisionCache.class.getDeclaredMethod("describe",VehicleWorld.class);describe.setAccessible(true);Field snapshot=Class.forName("com.vandorlabs.vehicle.VehicleCollisionCache$Description").getDeclaredField("snapshot");snapshot.setAccessible(true);Field capabilities=TileEntity.class.getDeclaredField("capabilities");capabilities.setAccessible(true);
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        for(int count:new int[]{4,256,4096}){
            List<VehicleStructure.Cell> payloadCells=new ArrayList<>();for(int i=0;i<count;i++)payloadCells.add(new VehicleStructure.Cell(new BlockPos(i%16,(i/16)%16,i/256),Blocks.STONE.getDefaultState(),null));ArmPayload payload=pc.newInstance(VehicleStructure.display(payloadCells),BlockPos.ORIGIN,false,BlockPos.ORIGIN);NBTTagCompound root=(NBTTagCompound)tagger.invoke(null,BlockPos.ORIGIN,EnumFacing.UP,GearMotion.Mode.EXTEND,.5D,payload.write());NBTTagCompound originalCaps=new NBTTagCompound();originalCaps.setByteArray("OriginalOpaque",new byte[32768]);root.setTag("ForgeCaps",originalCaps);
            VehicleStructure initial=structure(new VehicleStructure.Cell(BlockPos.ORIGIN,GearRegistry.GEAR_EXTENDER.getDefaultState(),root));VehicleWorld view=new VehicleWorld(parent,initial);view.tiles.remove(SEAT);GearTileEntity gear=(GearTileEntity)view.tiles.get(BlockPos.ORIGIN);VehicleStructure.Cell canonical=new VehicleStructure.Cell(BlockPos.ORIGIN,GearRegistry.GEAR_EXTENDER.getDefaultState(),gear.currentData(root));view.structure=structure(canonical);capabilities.set(gear,new CapabilityDispatcher(Collections.singletonMap(new ResourceLocation("probe:external"),new Provider()),null));VehicleCollisionCache cache=new VehicleCollisionCache();for(int i=0;i<30;i++)sink=describe.invoke(cache,view);int before=serializations;long allocated=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();for(int i=0;i<100;i++)sink=describe.invoke(cache,view);long bytes=bean.getThreadAllocatedBytes(thread)-allocated;int calls=serializations-before;VehicleStructure prepared=(VehicleStructure)snapshot.get(sink);boolean preserved=prepared.at(BlockPos.ORIGIN).tileData().getCompoundTag("ForgeCaps").equals(originalCaps);if(baseline?(calls!=100||preserved):(calls!=0||!preserved))throw new AssertionError("Descriptor caps baseline="+baseline+" calls="+calls+" preserved="+preserved);
            System.out.println("Gear actual description cells="+count+" baseline="+baseline+" calls=100 allocatedBytes="+bytes+" nanos="+(System.nanoTime()-start)+" externalSerializations="+calls+" originalCaps="+preserved);
            if(!baseline){gear.captureProgress(.25);VehicleStructure.Cell pose=canonical.withGearPose(gear);view.structure=structure(pose);VehicleStructure next=(VehicleStructure)snapshot.get(describe.invoke(cache,view));if(!next.at(BlockPos.ORIGIN).tileData().equals(gear.currentData(canonical.tileData())))throw new AssertionError("Live descriptor pose differs from current typed writer");cache.clear();if(describe.invoke(cache,view)==null)throw new AssertionError("Cleared descriptor did not rebuild");}
        }
        System.out.println("Gear actual description PASS baseline="+baseline+" actual installed divergent capabilities/current pose/reset; geometry and complete tick separate");
    }
}
