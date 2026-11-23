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
    private ParkedOwnershipChecks(){}
}
