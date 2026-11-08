package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.*;
import com.vandorlabs.gear.*;
import com.vandorlabs.gear.core.GearMotion;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Real entity load/save admission, independent of joint-enable and local tile defaults. */
public final class ArmCouplerClaimEntityChecks {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Coupler entity claims: "+why);}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.logger=org.apache.logging.log4j.LogManager.getLogger("coupler-claims");com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(com.vandorlabs.tiles.TileEntityPilotSeat.class,new ResourceLocation("vandorlabs:pilot_seat"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(com.vandorlabs.tiles.TileEntityVehicleController.class,new ResourceLocation("vandorlabs:vehicle_controller"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileCoupler.class,new ResourceLocation("vandorlabs:square_coupler"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(GearTileEntity.class,new ResourceLocation("vandorlabs:modular_gear"));
        com.vandorlabs.blocks.BlockPilotSeat seat=new com.vandorlabs.blocks.BlockPilotSeat();com.vandorlabs.blocks.BlockVehicleController controller=new com.vandorlabs.blocks.BlockVehicleController();BlockCoupler head=new BlockCoupler();for(net.minecraft.block.Block block:new net.minecraft.block.Block[]{seat,controller,head,GearRegistry.GEAR_EXTENDER})net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);
        java.lang.reflect.Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)unsafeField.get(null);net.minecraft.server.MinecraftServer server=(net.minecraft.server.MinecraftServer)unsafe.allocateInstance(net.minecraft.server.dedicated.DedicatedServer.class);for(java.lang.reflect.Field field:net.minecraft.server.MinecraftServer.class.getDeclaredFields())if(field.getType()==Thread.class){field.setAccessible(true);field.set(server,Thread.currentThread());}
        java.lang.reflect.Constructor<?> parent=Class.forName("com.vandorlabs.client.ArmParkingSourceChecks$Parent").getDeclaredConstructor(net.minecraft.server.MinecraftServer.class);parent.setAccessible(true);net.minecraft.world.World world=(net.minecraft.world.World)parent.newInstance(server);
        UUID owner=new UUID(45,46),generation=new UUID(47,48),headId=new UUID(49,50);BlockPos seatAt=new BlockPos(1,1,0),controlAt=new BlockPos(0,1,0),headAt=new BlockPos(2,1,0),armAt=new BlockPos(2,2,0);
        List<VehicleStructure.Cell> base=new ArrayList<>();for(int x=0;x<3;x++)base.add(new VehicleStructure.Cell(new BlockPos(x,0,0),net.minecraft.init.Blocks.STONE.getDefaultState(),null));com.vandorlabs.tiles.TileEntityPilotSeat seatTile=new com.vandorlabs.tiles.TileEntityPilotSeat();seatTile.setPos(seatAt);com.vandorlabs.tiles.TileEntityVehicleController controlTile=new com.vandorlabs.tiles.TileEntityVehicleController();controlTile.setPos(controlAt);base.add(new VehicleStructure.Cell(seatAt,seat.getDefaultState(),seatTile.writeToNBT(new NBTTagCompound())));base.add(new VehicleStructure.Cell(seatAt.up(),seat.getDefaultState().withProperty(com.vandorlabs.blocks.BlockPilotSeat.UPPER,true),null));base.add(new VehicleStructure.Cell(controlAt,controller.getDefaultState(),controlTile.writeToNBT(new NBTTagCompound())));
        java.lang.reflect.Method gearTag=ArmCouplerHeadChecks.class.getDeclaredMethod("gearTag",BlockPos.class,EnumFacing.class,GearMotion.Mode.class,double.class,NBTTagCompound.class);gearTag.setAccessible(true);
        for(boolean nested:new boolean[]{false,true})for(int variant=0;variant<6;variant++){
            NBTTagCompound own=new NBTTagCompound();own.setString("id","vandorlabs:square_coupler");own.setUniqueId("CouplerControlIdentity",headId);own.setBoolean("CouplerArticulation",false);own.setString("FutureOwn","unchanged");NBTTagCompound caps=new NBTTagCompound();caps.setString(CouplerAssociationData.KEY,"provider-owned opaque field");caps.setByteArray("UnknownProvider",new byte[]{4,9,-1});own.setTag("ForgeCaps",caps);
            if(variant>0)CouplerAssociationData.write(own,new CouplerAssociation(CouplerAssociation.UNPAIRED,headId,owner,generation));if(variant==4)own.getCompoundTag(CouplerAssociationData.KEY).setInteger("Version",99);
            List<VehicleStructure.Cell> cells=new ArrayList<>(base);if(!nested)cells.add(new VehicleStructure.Cell(headAt,head.getDefaultState(),own));else{
                VehicleStructure body=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,head.getDefaultState(),own)));NBTTagCompound payload=new NBTTagCompound();payload.setTag("Blocks",body.write());payload.setLong("Offset",new BlockPos(0,-1,0).toLong());payload.setLong("PlacedOffset",BlockPos.ORIGIN.toLong());payload.setBoolean("Materialized",false);NBTTagCompound root=(NBTTagCompound)gearTag.invoke(null,armAt,EnumFacing.DOWN,GearMotion.Mode.EXTEND,.5,payload);cells.add(new VehicleStructure.Cell(armAt,GearRegistry.GEAR_EXTENDER.getDefaultState(),root));
            }
            VehicleStructure source=new VehicleStructure(cells,seatAt,EnumFacing.NORTH,3);NBTTagCompound input=new NBTTagCompound();input.setTag("Craft",source.write());if(variant!=2)input.setUniqueId(EntityGroundVehicle.COUPLER_GENERATION,variant==3?new UUID(51,52):generation);if(variant==5)input.setString(EntityGroundVehicle.COUPLER_GENERATION+"Most","retain wrong type");
            EntityGroundVehicle entity=new EntityGroundVehicle(world);entity.setUniqueId(owner);try{
                entity.readEntityFromNBT(input);NBTTagCompound saved=new NBTTagCompound();entity.writeEntityToNBT(saved);boolean valid=variant==0||variant==1;
                need(valid?entity.structure!=null&&entity.view!=null&&entity.collision!=null:entity.structure==null&&entity.view==null&&entity.collision==null,"actual entity usable/inert nested="+nested+" variant="+variant);
                if(!valid){need(saved.getCompoundTag("Craft").equals(input.getCompoundTag("Craft")),"failed load retains exact source/caps nested="+nested+" variant="+variant);for(String suffix:new String[]{"Most","Least"}){String key=EntityGroundVehicle.COUPLER_GENERATION+suffix;need(Objects.equals(saved.getTag(key),input.getTag(key)),"failed load retains raw external scope "+key);}}
            }finally{entity.setDead();}
        }
        System.out.println("Coupler actual entity claim admission PASS checks="+checks+" native/nested/nonarticulated/missing/wrong/future scope/inert exact Craft+scope+caps; proof-free known-scope baseline remains usable");
    }
}
