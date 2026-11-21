package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockNavigationTable;
import com.vandorlabs.coupler.*;
import com.vandorlabs.gear.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.navigation.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.TileEntityNavigationTable;
import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/** A saved arm can decode successfully while its later synthetic view refuses a retained tile. */
public final class NavigationDiscoverySafetyChecks {
    private static int checks;
    private static final BlockPos ROOT=new BlockPos(8,80,8),TABLE=new BlockPos(10,80,8);
    private static BlockCoupler coupler;
    private static BlockNavigationTable tableBlock;
    private static void need(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}

    private static NBTTagCompound body(VehicleStructure structure){
        NBTTagCompound tag=new NBTTagCompound();tag.setTag("Blocks",structure.write());
        tag.setLong("Offset",0);tag.setLong("PlacedOffset",0);
        tag.setBoolean("Materialized",false);tag.setBoolean("GridAligned",true);
        return tag;
    }
    private static NBTTagCompound unavailable(){
        NBTTagCompound saved=new NBTTagCompound();saved.setString("id","vandorlabs:square_coupler");
        saved.setInteger("x",0);saved.setInteger("y",0);saved.setInteger("z",0);
        NBTTagCompound association=new NBTTagCompound();association.setInteger("Version",2);
        saved.setTag(CouplerAssociationData.KEY,association);
        NBTTagCompound caps=new NBTTagCompound();caps.setByteArray("OpaqueProviderData",new byte[]{1,2,3,4});
        saved.setTag("ForgeCaps",caps);saved.setString("UnknownOwnedData","retain exactly");
        TileEntity tile=TileEntity.create(null,saved.copy());
        need(tile instanceof TileCoupler&&((TileCoupler)tile).quarantined(),"Unsupported association yields a non-null retained native tile");
        NBTTagCompound result=body(VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,coupler.getDefaultState(),saved))));
        need(ArmPayload.read(result)!=null,"Saved arm admission accepts the retained tile before view construction");
        return result;
    }
    private static NBTTagCompound gear(BlockPos pos,NBTTagCompound payload){
        NBTTagCompound tag=new NBTTagCompound();tag.setString("id","vandorlabs:modular_gear");
        tag.setInteger("x",pos.getX());tag.setInteger("y",pos.getY());tag.setInteger("z",pos.getZ());
        tag.setBoolean("GearController",true);tag.setBoolean("GearOperational",true);tag.setInteger("GearHeight",1);
        tag.setString("GearKind","NONE");tag.setString("GearMode","EXTEND");tag.setString("GearDirection","NORTH");
        tag.setString("ArmMountDirection","DOWN");tag.setDouble("GearProgress",.5);
        tag.setBoolean("ArmRetractingStroke",true);tag.setDouble("ArmActiveAngle",90);
        NBTTagList feet=new NBTTagList();feet.appendTag(new NBTTagIntArray(new int[]{0,-1,0}));tag.setTag("GearFeet",feet);
        tag.setTag("ArmPayload",payload.copy());return tag;
    }
    private static void retained(GearTileEntity arm,NBTTagCompound before,String context){
        need(arm.payload()==null&&arm.hasFailedArmSnapshot()&&!arm.isOperational(),context+" becomes inert");
        need(arm.writeToNBT(new NBTTagCompound()).getCompoundTag("ArmPayload").equals(before),context+" retains exact payload, unknown fields and opaque capabilities");
    }
    private static void tableTick(){
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
        NBTTagCompound payload=unavailable();GearTileEntity arm=new GearTileEntity();arm.readFromNBT(gear(ROOT,payload));
        arm.setWorld(world);world.states.put(ROOT,GearRegistry.GEAR_EXTENDER.getDefaultState());world.tiles.put(ROOT,arm);
        GearGeometry.register(arm);
        try{
            need(arm.isOperational()&&arm.payload()!=null&&!arm.hasFailedArmSnapshot(),"Native arm reaches discovery without a failed envelope");
            boolean refused=false;try{arm.payload().view(arm);}catch(IllegalArgumentException expected){refused=true;}
            need(refused,"Synthetic view refuses the quarantined Coupler");
            TileEntityNavigationTable table=new TileEntityNavigationTable();table.setPos(TABLE);
            NBTTagCompound saved=table.writeToNBT(new NBTTagCompound());saved.setBoolean("NavigationFormed",true);
            saved.setIntArray("RedstoneChannels",new int[]{47});saved.setInteger("RedstoneChannel",47);
            saved.setTag("NavigationDataModule",new ItemStack(ModItems.NAVIGATION_DATA_MODULE).writeToNBT(new NBTTagCompound()));
            table.readFromNBT(saved);table.setWorld(world);world.states.put(TABLE,tableBlock.getDefaultState());world.tiles.put(TABLE,table);
            need(table.formed()&&table.screenSettings().texture==NavigationMap.SURFACE,"Real table tick will record its selected map");
            world.time=Math.floorMod(-TABLE.toLong(),20L);
            NBTTagCompound before=table.writeToNBT(new NBTTagCompound());
            table.update();
            retained(arm,payload,"Table discovery");
            need(before.equals(table.writeToNBT(new NBTTagCompound())),"Failed observer cannot change the table's stored coverage or inventory");
            world.time+=20;table.update();
            need(before.equals(table.writeToNBT(new NBTTagCompound())),"Later table ticks skip the retained arm");
        }finally{GearGeometry.remove(arm);}
    }
    private static void vehicle(boolean presence,int depth)throws Exception{
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
        NBTTagCompound expected=unavailable(),nested=expected;
        for(int level=1;level<depth;level++)nested=body(VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,GearRegistry.GEAR_EXTENDER.getDefaultState(),gear(BlockPos.ORIGIN,nested)))));
        VehicleStructure source=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(ROOT,GearRegistry.GEAR_EXTENDER.getDefaultState(),gear(ROOT,nested))));
        NBTTagCompound immutable=source.write();EntityGroundVehicle craft=new EntityGroundVehicle(world);
        craft.structure=source;craft.view=new VehicleWorld(world,source);craft.view.craft=craft;
        GearTileEntity leaf=(GearTileEntity)craft.view.getTileEntity(ROOT);
        for(int level=1;level<depth;level++)leaf=(GearTileEntity)leaf.payload().view(leaf).getTileEntity(BlockPos.ORIGIN);
        need(leaf.payload()!=null&&!leaf.hasFailedArmSnapshot(),"Carried leaf awaits synthetic view admission depth="+depth);
        if(presence)need(!NavigationVehicle.hasObserver(craft),"Retained payload is not a live observer depth="+depth);
        else{
            // A complete vehicle load rejects this Coupler during articulation admission.
            // Exercise only the later observer traversal here; the native table case above
            // exercises the full tick path without bypassing that ownership boundary.
            List<NavigationSources.Area> areas=new ArrayList<>();
            java.lang.reflect.Method observers=NavigationVehicle.class.getDeclaredMethod("observers",List.class,EntityGroundVehicle.class,net.minecraft.util.math.Vec3d.class,ChannelList.class,boolean.class);
            // Powered, so only the retained payload rule can leave the result empty.
            observers.setAccessible(true);observers.invoke(null,areas,craft,new net.minecraft.util.math.Vec3d(ROOT),ChannelList.of(47),true);
            need(areas.isEmpty(),"Retained payload contributes no partial traversal coverage depth="+depth);
        }
        retained(leaf,expected,"Carried "+(presence?"presence":"coverage")+" depth="+depth);
        need(source.write().equals(immutable),"Discovery never edits the immutable carried structure depth="+depth);
    }
    public static void main(String[] args)throws Exception{
        java.lang.reflect.Field environment=net.minecraftforge.fml.relauncher.CoreModManager.class.getDeclaredField("deobfuscatedEnvironment");environment.setAccessible(true);environment.setBoolean(null,true);
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.logger=org.apache.logging.log4j.LogManager.getLogger("navigation-discovery");
        com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileCoupler.class,new ResourceLocation("vandorlabs:square_coupler"));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(GearTileEntity.class,new ResourceLocation("vandorlabs:modular_gear"));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityNavigationTable.class,new ResourceLocation("vandorlabs:navigation_table"));
        coupler=new BlockCoupler();tableBlock=new BlockNavigationTable("navigation_table_flat",NavigationLayout.Kind.FLAT);
        net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(coupler);
        net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(GearRegistry.GEAR_EXTENDER);
        net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(tableBlock);
        net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(ModItems.NAVIGATION_DATA_MODULE);
        if(args.length==0||args[0].equals("table"))tableTick();
        for(int depth=1;depth<=2;depth++)for(boolean presence:new boolean[]{true,false})if(args.length==0||args[0].equals(presence?"presence":"coverage"))vehicle(presence,depth);
        System.out.println("Navigation discovery safety PASS checks="+checks+" native table ticks, carried traversal boundaries, nested arms, exact retained payloads");
    }
    private NavigationDiscoverySafetyChecks(){}
}
