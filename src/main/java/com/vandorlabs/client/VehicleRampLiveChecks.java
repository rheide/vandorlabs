package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.io.*;

/** Native saved ramp parts must pass the same exact-data gate as ordinary vehicle blocks. */
final class VehicleRampLiveChecks {
    private static boolean complete,prepared;
    private static BlockPos parkedSeat;
    private static long parkedAt;
    static boolean tick(Minecraft mc)throws Exception{
        if(complete)return true;
        mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;
        complete=mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);
            if(!prepared){run(world,mc.getIntegratedServer().getPlayerList().getPlayers().get(0));prepared=true;parkedAt=world.getTotalWorldTime();return false;}
            long elapsed=world.getTotalWorldTime()-parkedAt;
            require(elapsed<300,"parked mechanisms settle before recapture");
            if(elapsed<80)return false;
            VehicleScan again=new VehicleScan(world,parkedSeat);for(int i=0;i<1000&&!again.step(256);i++);
            if(again.error!=null&&again.error.startsWith("Wait for"))return false;
            require(again.error==null&&again.result!=null,"parked craft can enter Taxi again: "+again.error);
            System.out.println("[vandorlabs][reprolab] vehicle-ramps PASS saved native vehicle 56 ramp cells capture reload four rotations thaw exact NBT native Taxi assembly parking world ticks recapture unknown-data refusal");
            return true;
        }).get();
        return complete;
    }
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("vehicle-ramps: "+why);}
    private static void run(WorldServer world,EntityPlayerMP actor)throws Exception{
        NBTTagCompound fixture;try(InputStream in=new FileInputStream(System.getProperty("vandorlabs.vehicleRampFixture"))){fixture=CompressedStreamTools.readCompressed(in);}
        for(BlockPos p:BlockPos.getAllInBox(new BlockPos(-150,82,154),new BlockPos(-128,96,178))){world.getChunkFromBlockCoords(p);world.removeTileEntity(p);world.setBlockToAir(p);}
        NBTTagList cells=fixture.getTagList("Cells",10);
        for(int i=0;i<cells.tagCount();i++){NBTTagCompound cell=cells.getCompoundTagAt(i);BlockPos p=pos(cell);Block block=Block.getBlockFromName(cell.getString("Name"));require(block!=null,"registry "+cell.getString("Name"));world.setBlockState(p,block.getStateFromMeta(cell.getInteger("Meta")),2);}
        for(int i=0;i<cells.tagCount();i++){NBTTagCompound cell=cells.getCompoundTagAt(i);TileEntity tile=world.getTileEntity(pos(cell));if(tile!=null&&cell.hasKey("Tile",10))tile.readFromNBT(cell.getCompoundTag("Tile"));}
        BlockPos seat=new BlockPos(-138,85,166);actor.setPositionAndUpdate(-138,90,166);
        VehicleScan scan=new VehicleScan(world,seat);for(int i=0;i<1000&&!scan.step(256);i++);
        require(scan.error==null&&scan.result!=null,"saved vehicle capture: "+scan.error);
        VehicleStructure captured=scan.result;int parts=0;
        for(VehicleStructure.Cell cell:captured.cells)if(cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockControlledRamp)parts++;
        require(parts==56,"all 56 saved ramp cells captured, got "+parts);
        VehicleStructure restored=VehicleStructure.read(captured.write());
        MovingBlockSupport.validate(restored,new VehicleWorld(world,restored));
        VehicleStructure turned=restored;
        for(int i=0;i<4;i++){turned=VehicleRotation.rotate(turned,new VehicleWorld(world,turned),1);validate(turned,world,"rotation "+i);}
        require(turned.write().equals(restored.write()),"four rotations preserve saved ramp geometry and source data");
        java.util.List<VehicleStructure.Cell> thawed=new java.util.ArrayList<>();
        for(VehicleStructure.Cell cell:restored.cells){
            NBTTagCompound data=cell.tileData();
            if(data!=null&&data.getBoolean("VehicleRampFrozen")){
                NBTTagCompound original=data.copy();data=VehicleEscalators.parked(data,12345);
                require(original.equals(cell.tileData()),"thaw leaves carry snapshot immutable");
                boolean controller=cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockRampController;
                require(data.hasKey("LastStepTick")==controller,"only controllers have a step clock");
                if(controller)require(data.getLong("LastStepTick")==12345,"controller thaw clock");
                require(!data.hasKey("VehicleRampFrozen")&&data.getLong("EscalatorTick")==12345,"part and controller thaw clocks");
            }
            thawed.add(new VehicleStructure.Cell(cell.pos,cell.state,data));
        }
        VehicleStructure parked=new VehicleStructure(thawed,restored.seat,restored.facing,restored.gearCount);
        VehicleWorld parkedView=new VehicleWorld(world,parked);
        for(VehicleStructure.Cell cell:parked.cells)MovingBlockSupport.requireExactTileData(cell,parkedView.getTileEntity(cell.pos));
        for(VehicleStructure.Cell cell:restored.cells)if(cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockControlledRamp){
            NBTTagCompound extra=cell.tileData();extra.setInteger("UnknownImportantData",42);boolean rejected=false;
            try{MovingBlockSupport.requireExactTileData(new VehicleStructure.Cell(cell.pos,cell.state,extra),new VehicleWorld(world,restored).getTileEntity(cell.pos));}catch(IllegalArgumentException expected){rejected=true;}
            require(rejected,"unrecognized part data still rejected");break;
        }
        EntityGroundVehicle craft=VehicleTransfer.assemble(world,captured,scan.origin,actor);
        require(!craft.hover,"saved wheeled vehicle enters Taxi");
        for(VehicleStructure.Cell cell:captured.cells)require(world.isAirBlock(scan.origin.add(cell.pos)),"source removed only after capture");
        VehicleRotation.Plan plan=VehicleRotation.plan(craft);
        VehicleTransfer.park(world,craft,plan.origin,actor,false);
        require(craft.isDead&&!VehicleTransfer.pending(world),"parking leaves native owner and retires recovery journal");
        int nativeParts=0;for(VehicleStructure.Cell cell:plan.structure.cells){
            BlockPos at=plan.origin.add(cell.pos);
            require(world.getBlockState(at)==cell.state,"parked block state "+at);
            if(world.getTileEntity(at) instanceof TileEntityControlledRamp){nativeParts++;require(!world.getTileEntity(at).writeToNBT(new NBTTagCompound()).hasKey("LastStepTick"),"native parts retain their own schema");}
        }
        require(nativeParts==56,"parking restores every ramp section");
        parkedSeat=plan.origin.add(plan.structure.seat);
    }
    private static void validate(VehicleStructure structure,WorldServer world,String stage){
        VehicleWorld view=new VehicleWorld(world,structure);
        try{MovingBlockSupport.validate(structure,view);}catch(IllegalArgumentException failure){
            for(VehicleStructure.Cell cell:structure.cells)if(cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockControlledRamp){
                NBTTagCompound expected=cell.tileData(),actual=view.getTileEntity(cell.pos).writeToNBT(new NBTTagCompound());
                java.util.Set<String> keys=new java.util.TreeSet<>(expected.getKeySet());keys.addAll(actual.getKeySet());
                boolean mismatch=false;for(String key:keys)if(!java.util.Objects.equals(expected.getTag(key),actual.getTag(key))){System.err.println("vehicle-ramps "+stage+" mismatch "+cell.pos+" "+key+" expected="+expected.getTag(key)+" actual="+actual.getTag(key));mismatch=true;}
                if(mismatch)break;
            }throw failure;
        }
    }
    private static BlockPos pos(NBTTagCompound tag){return new BlockPos(tag.getInteger("X"),tag.getInteger("Y"),tag.getInteger("Z"));}
    private VehicleRampLiveChecks(){}
}
