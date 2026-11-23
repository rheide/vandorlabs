package com.vandorlabs.vehicle;

import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.tiles.TileEntitySignalControl;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.*;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.Future;

/** Two cold boots of a supplied save. Keep the caller's original WAL as external fixture input. */
@SideOnly(Side.CLIENT)
public final class ParkedOwnershipChecks {
    private static Future<?> result;
    private static int ticks;
    public static boolean tick(Minecraft mc)throws Exception{
        if(ticks++<40)return false;
        if(result==null){
            result=mc.getIntegratedServer().callFromMainThread(()->{
                EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                check(actor.getServerWorld(),actor);return null;
            });
        }
        if(!result.isDone())return false;
        result.get();
        System.out.println("[vandorlabs][reprolab] utility26-focused PASS saved parked owner "+System.getProperty("vandorlabs.parkedOwnershipPhase"));
        return true;
    }
    private static void need(boolean ok,String detail){if(!ok)throw new IllegalStateException("Parked ownership: "+detail);}
    private static void check(WorldServer world,EntityPlayerMP actor)throws Exception{
        actor.capabilities.isCreativeMode=true;
        Path dir=world.getSaveHandler().getWorldDirectory().toPath().resolve("data");
        Path input=Paths.get(System.getProperty("vandorlabs.parkedOwnershipRecord"));
        NBTTagCompound journal;
        try(InputStream in=Files.newInputStream(input)){journal=CompressedStreamTools.readCompressed(in);}
        VehicleStructure shape=VehicleStructure.read(journal.getCompoundTag("ParkedCraft"));
        BlockPos origin=BlockPos.fromLong(journal.getLong("Origin")),controller=origin.add(shape.controller().pos);
        Path primary=dir.resolve("vandorlabs-vehicle-0.nbt");
        Path retained=dir.resolve("vandorlabs-vehicle-conflict-0-"+journal.getUniqueId("Entity")+".nbt");
        BlockPos fuel=new BlockPos(-112,76,74),lever=new BlockPos(-108,76,73);
        TileEntityConstructor drive=(TileEntityConstructor)world.getTileEntity(fuel);
        need(drive!=null&&drive.maxwellFuelContents()!=null&&drive.maxwellFuelContents().amount==361,"latest saved fuel (361 mB)");
        need(world.getTileEntity(lever) instanceof TileEntitySignalControl
                &&!world.getBlockState(lever).getValue(com.vandorlabs.blocks.BlockVandorSwitch.ON),"saved lever Off");
        need(!Files.exists(primary)&&world.getEntityFromUuid(journal.getUniqueId("Entity"))==null,"single parked native owner");
        if("fluid".equals(System.getProperty("vandorlabs.parkedOwnershipPhase"))){
            fuelRoundTrip(world,actor,shape,origin,controller,drive);
            return;
        }
        VehicleStructure.Cell stone=shape.cells.stream().filter(cell->cell.state.getBlock()==Blocks.STONE).findFirst().get();
        BlockPos marker=origin.add(stone.pos);
        if("write".equals(System.getProperty("vandorlabs.parkedOwnershipPhase"))){
            need(!Files.exists(retained),"complete park accepted without creating a conflict");
            need(world.getBlockState(marker)==stone.state,"saved hull present");
            world.setBlockState(marker,Blocks.OBSIDIAN.getDefaultState(),2);
            Files.copy(input,primary);
            VehicleTransfer.recover(world);
            need(!Files.exists(primary)&&Arrays.equals(Files.readAllBytes(input),Files.readAllBytes(retained)),"exact conflict WAL retained");
            boolean refused=false;
            try{SavedTaxiFollowupChecks.assemble(world,actor,controller,null,1);}
            catch(IllegalStateException expected){if(!expected.toString().contains("overlaps a vehicle transfer"))throw expected;refused=true;}
            need(refused&&world.getBlockState(marker).getBlock()==Blocks.OBSIDIAN,"overlap refused without touching edit");
            EntityGroundVehicle other=SavedTaxiFollowupChecks.assemble(world,actor,new BlockPos(-124,76,59),null,2);
            need(other!=null&&other.hover&&!other.isDead,"independent nearby Hover conversion completed");
            need(Arrays.equals(Files.readAllBytes(input),Files.readAllBytes(retained)),"disjoint transfer leaves competing WAL exact");
        }else{
            need(Files.exists(retained)&&Arrays.equals(Files.readAllBytes(input),Files.readAllBytes(retained))
                    &&world.getBlockState(marker).getBlock()==Blocks.OBSIDIAN,"retained conflict and edited hull survive restart");
            world.setBlockState(marker,stone.state,2);
            VehicleTransfer.recover(world);
            need(!Files.exists(retained)&&!Files.exists(primary),"resolved local conflict retired");
            EntityGroundVehicle craft=SavedTaxiFollowupChecks.assemble(world,actor,controller,null,1);
            need(craft!=null&&!craft.hover&&!craft.isDead,"original controller converts to Taxi");
        }
        System.out.println("[vandorlabs] parked-ownership PASS "+System.getProperty("vandorlabs.parkedOwnershipPhase")
                +" fuel=361 lever=Off nativeCells="+shape.cells.size()+" wheels="+shape.gearCount);
    }
    private static void fuelRoundTrip(WorldServer world,EntityPlayerMP actor,VehicleStructure source,BlockPos origin,
                                      BlockPos controller,TileEntityConstructor drive)throws Exception{
        net.minecraftforge.fluids.FluidStack fuel=drive.maxwellFuelContents();
        drive.purgeMaxwell();
        for(VehicleStructure.Cell cell:source.cells) {
            net.minecraft.tileentity.TileEntity tile=world.getTileEntity(origin.add(cell.pos));
            if(tile instanceof com.vandorlabs.tiles.TileEntityHexTank)((com.vandorlabs.tiles.TileEntityHexTank)tile).tank.setFluid(null);
        }
        BlockPos sourceTank=new BlockPos(-114,76,71);
        com.vandorlabs.tiles.TileEntityHexTank tank=(com.vandorlabs.tiles.TileEntityHexTank)world.getTileEntity(sourceTank);
        fuel.amount=16000;
        need(tank.fill(fuel,true)==16000,"real joined tank filled with 16,000 mB");
        // Give the existing network a known, nonzero share before freezing all native tiles.
        net.minecraft.tileentity.TileEntity duct=world.getTileEntity(new BlockPos(-113,76,71));
        for(Object unit:(Iterable<?>)duct.getClass().getMethod("getDuctUnits").invoke(duct)){
            if(!unit.getClass().getName().startsWith("cofh.thermaldynamics.duct.fluid."))continue;
            Object grid=unit.getClass().getMethod("getGrid").invoke(unit);
            net.minecraftforge.fluids.IFluidTank buffer=(net.minecraftforge.fluids.IFluidTank)grid.getClass().getField("myTank").get(grid);
            need(buffer.getFluidAmount()==0&&buffer.fill(tank.drain(1000,true),true)==1000,"native duct precharge");
        }
        EntityGroundVehicle craft=SavedTaxiFollowupChecks.assemble(world,actor,controller,null,1);
        long time=world.getTotalWorldTime();
        try{
            for(int tick=0;tick<24;tick++){world.getWorldInfo().setWorldTotalTime(time+tick+1);craft.signals.resources.tick();}
        }finally{world.getWorldInfo().setWorldTotalTime(time);}
        need(fuelAmounts(craft.view,source,BlockPos.ORIGIN).equals("15000/1000/0"),"carried tank, frozen duct and inlet: "+fuelAmounts(craft.view,source,BlockPos.ORIGIN));
        actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));
        VehicleSafePark.submit(actor,craft,craft.structure.controller().pos);
        VehicleTransferRuntimeChecks.drain(world);
        need(craft.isDead,"actual Safe Park publishes saved machine");
        String restored=fuelAmounts(world,source,origin);
        need(restored.equals("15000/1000/0"),"Safe Park preserves each owner: "+restored);
        System.out.println("[vandorlabs] parked-ownership PASS fluid saved Star II 16000 mB before assembly, carried and Safe Park tank/duct/drive="+restored);
    }
    private static String fuelAmounts(net.minecraft.world.World world,VehicleStructure shape,BlockPos origin) {
        int stored=0,duct=0,engine=0;
        for(VehicleStructure.Cell cell:shape.cells){
            net.minecraft.tileentity.TileEntity tile=world.getTileEntity(origin.add(cell.pos));
            if(tile instanceof com.vandorlabs.tiles.TileEntityHexTank)stored+=((com.vandorlabs.tiles.TileEntityHexTank)tile).tank.getFluidAmount();
            else if(tile instanceof TileEntityConstructor)engine+=((TileEntityConstructor)tile).maxwellFuel();
            else if(TransferNetworkCompat.component(tile))duct+=TransferNetworkCompat.write(tile).getCompoundTag("Fluid").getInteger("Amount");
        }
        return stored+"/"+duct+"/"+engine;
    }

    private ParkedOwnershipChecks(){}
}
