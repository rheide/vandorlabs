package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityRampController;
import com.vandorlabs.tiles.TileEntityVehicleController;
import com.vandorlabs.tiles.TileEntityPilotSeat;
import net.minecraft.block.Block;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Prior-release reservation ordering and block palettes must survive the real snapshot reader. */
public final class VehicleUpgradeChecks {
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException("Vehicle upgrade: "+why);}
    public static void run(World parent){
        int cases=0;
        cases+=legacyTaxiController(parent);
        cases+=savedLegacyController(parent);
        for(BlockPos origin:Arrays.asList(new BlockPos(103,64,-37),new BlockPos(-217,91,118),new BlockPos(17,5,31))){
            TileEntityRampController source=new TileEntityRampController();source.setPos(origin);
            NBTTagCompound saved=source.writeToNBT(new NBTTagCompound());saved.setBoolean("VehicleRampFrozen",true);
            // 2.5 serialized these HashSets in their current coordinate-dependent order.
            NBTTagList reservations=new NBTTagList();
            for(int x=3;x>=0;x--)for(int z=3;z>=0;z--){BlockPos p=origin.add(x,0,z);NBTTagCompound entry=new NBTTagCompound();entry.setInteger("X",p.getX());entry.setInteger("Y",p.getY());entry.setInteger("Z",p.getZ());entry.setLong("Pos",p.toLong());reservations.appendTag(entry);}
            saved.setTag("Cells",reservations);saved.setTag("RampCanopyBlockers",reservations.copy());
            NBTTagCompound original=saved.copy();
            for(BlockPos shift:Arrays.asList(BlockPos.ORIGIN,new BlockPos(-100,-60,40),new BlockPos(250,10,-311))){
                NBTTagCompound local=VehicleTileData.translated(saved,shift);
                TileEntity clone=TileEntity.create(null,local);require(clone instanceof TileEntityRampController,"registered ramp factory");
                VehicleStructure.Cell cell=new VehicleStructure.Cell(origin.add(shift),ModBlocks.PROGRAMMABLE_RAMP.getDefaultState(),local);
                MovingBlockSupport.requireExactTileData(cell,clone);
                require(local.getTagList("Cells",10).tagCount()==16&&local.getTagList("RampCanopyBlockers",10).tagCount()==16,"all reservation and blocker entries retained");
                require(VehicleTileData.translated(local,BlockPos.ORIGIN.subtract(shift)).equals(new VehicleStructure.Cell(origin,cell.state,saved).tileData()),"translation and inverse preserve every saved field");
                NBTTagCompound extra=local.copy();extra.setInteger("UnknownImportantData",42);boolean rejected=false;
                try{MovingBlockSupport.requireExactTileData(new VehicleStructure.Cell(cell.pos,cell.state,extra),clone);}catch(IllegalArgumentException expected){rejected=!expected.getMessage().contains("copied");}
                require(rejected,"unknown data loss still refused with operation-neutral message");
                NBTTagCompound changed=local.copy();changed.setInteger("Drop",local.getInteger("Drop")+1);rejected=false;
                try{MovingBlockSupport.requireExactTileData(new VehicleStructure.Cell(cell.pos,cell.state,changed),clone);}catch(IllegalArgumentException expected){rejected=true;}
                require(rejected,"changed ramp settings still refused");cases++;
            }
            require(saved.equals(original),"legacy source NBT remains immutable");
        }
        for(String name:new String[]{"rocket_thruster_hexagonal","ion_drive_hexagonal","plasma_vent_hexagonal","impulse_engine_hexagonal"})for(EnumFacing facing:EnumFacing.values()){
            Block block=Block.getBlockFromName("vandorlabs:"+name);
            List<VehicleStructure.Cell> cells=Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null),new VehicleStructure.Cell(new BlockPos(1,0,0),block.getDefaultState().withProperty(BlockPropulsionLight.FACING,facing),null));
            for(boolean display:new boolean[]{false,true}){
                VehicleStructure current=display?VehicleStructure.display(cells):new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,3);
                NBTTagCompound old=current.write();NBTTagList palette=old.getTagList("Palette",10);
                for(int i=0;i<palette.tagCount();i++)if(palette.getCompoundTagAt(i).getString("Name").equals("vandorlabs:"+name))palette.getCompoundTagAt(i).getCompoundTag("Properties").removeTag("part");
                NBTTagCompound original=old.copy();VehicleStructure upgraded=display?VehicleStructure.readDisplay(old):VehicleStructure.read(old);
                require(upgraded.write().equals(current.write())&&old.equals(original),"2.5 hex palette migrates to SINGLE without editing original NBT");
                for(int i=0;i<palette.tagCount();i++)if(palette.getCompoundTagAt(i).getString("Name").equals("vandorlabs:"+name))palette.getCompoundTagAt(i).getCompoundTag("Properties").setString("part","unknown_part");
                boolean rejected=false;try{if(display)VehicleStructure.readDisplay(old);else VehicleStructure.read(old);}catch(IllegalArgumentException expected){rejected=true;}
                require(rejected,"unknown hex properties remain rejected");cases++;
            }
        }
        System.out.println("[vandorlabs][reprolab] vehicle-upgrade26 PASS cases="+cases+" legacy Taxi controller/Pilot Seat/programmable block/sign with opaque capabilities four-turn preservation and unknown-data refusal; unordered ramp reservations rebase inverse data-loss negatives; four hex families six faces vehicle/display palettes original NBT preserved");
    }
    /** Real pre-2.6 controller/seat/finish/sign data, read as native tiles before capture. */
    private static int savedLegacyController(World parent){
        String path=System.getProperty("vandorlabs.legacyVehicleFixture","");if(path.isEmpty())return 0;
        try(java.io.InputStream in=new java.io.FileInputStream(path)){
            NBTTagCompound fixture=CompressedStreamTools.readCompressed(in),original=fixture.copy();
            NBTTagList saved=fixture.getTagList("Cells",10);List<VehicleStructure.Cell> cells=new ArrayList<>();
            BlockPos delta=new BlockPos(-1269,-73,12342),seat=null;int opaque=0;
            for(int i=0;i<saved.tagCount();i++){
                NBTTagCompound entry=saved.getCompoundTagAt(i);Block block=Block.getBlockFromName(entry.getString("Name"));require(block!=null,"saved legacy registry entry");
                net.minecraft.block.state.IBlockState state=block.getStateFromMeta(entry.getInteger("Meta"));
                BlockPos pos=new BlockPos(entry.getInteger("X"),entry.getInteger("Y"),entry.getInteger("Z")).add(delta);
                NBTTagCompound data=null;
                if(entry.hasKey("Tile",10)){
                    NBTTagCompound old=VehicleTileData.translated(entry.getCompoundTag("Tile"),delta);
                    TileEntity tile=TileEntity.create(parent,old);require(tile!=null,"saved legacy tile factory");
                    data=tile.writeToNBT(new NBTTagCompound());
                    // Retain the original opaque extension payload even without its optional provider.
                    if(old.hasKey("ForgeCaps",10)){data.setTag("ForgeCaps",old.getCompoundTag("ForgeCaps").copy());opaque++;}
                }
                if(block==ModBlocks.PILOT_SEAT&&!state.getValue(BlockPilotSeat.UPPER))seat=pos;
                cells.add(new VehicleStructure.Cell(pos,state,data));
            }
            require(seat!=null&&opaque>0,"actual saved seat and capability-bearing blocks");
            VehicleStructure source=new VehicleStructure(cells,seat,EnumFacing.NORTH,3),turned=source;
            MovingBlockSupport.validate(source,new VehicleWorld(parent,source));
            for(int n=0;n<4;n++){turned=VehicleRotation.rotate(turned,new VehicleWorld(parent,turned),1);MovingBlockSupport.validate(turned,new VehicleWorld(parent,turned));}
            require(turned.write().equals(source.write())&&fixture.equals(original),"actual legacy block data survives four turns without editing source NBT");
            System.out.println("[vandorlabs] saved-legacy-controller PASS cells="+cells.size()+" opaque="+opaque+" actual native tile load preflight four rotations");
            return 1;
        }catch(java.io.IOException failure){throw new IllegalStateException("Cannot read legacy vehicle fixture",failure);}
    }
    private static int legacyTaxiController(World parent){
        BlockPos seat=new BlockPos(0,0,0),controller=seat.east(),wall=controller.east(),sign=wall.east();
        TileEntityPilotSeat pilot=new TileEntityPilotSeat();pilot.setPos(seat);pilot.setWorld(parent);
        NBTTagCompound seatSaved=pilot.writeToNBT(new NBTTagCompound());
        TileEntityVehicleController tile=new TileEntityVehicleController();tile.setPos(controller);tile.setWorld(parent);
        tile.setTaxiMode(true);
        NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());
        TileEntity block=ModBlocks.PROGRAMMABLE_BLOCK.createTileEntity(parent,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState());
        block.setPos(wall);block.setWorld(parent);NBTTagCompound wallSaved=block.writeToNBT(new NBTTagCompound());
        net.minecraft.tileentity.TileEntitySign signTile=new net.minecraft.tileentity.TileEntitySign();signTile.setPos(sign);signTile.setWorld(parent);
        signTile.signText[0]=new net.minecraft.util.text.TextComponentString("Taxi");
        NBTTagCompound signSaved=signTile.writeToNBT(new NBTTagCompound());
        NBTTagCompound radiation=new NBTTagCompound();radiation.setDouble("radiationResistance",0);
        NBTTagCompound caps=new NBTTagCompound();caps.setTag("nuclearcraft:capability_default_radiation_resistance",radiation);
        seatSaved.setTag("ForgeCaps",caps.copy());saved.setTag("ForgeCaps",caps.copy());wallSaved.setTag("ForgeCaps",caps.copy());signSaved.setTag("ForgeCaps",caps.copy());
        List<VehicleStructure.Cell> cells=Arrays.asList(
                new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),seatSaved),
                new VehicleStructure.Cell(controller,ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),saved),
                new VehicleStructure.Cell(wall,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),wallSaved),
                new VehicleStructure.Cell(sign,net.minecraft.init.Blocks.WALL_SIGN.getDefaultState(),signSaved));
        VehicleStructure source=new VehicleStructure(cells,seat,EnumFacing.NORTH,3);
        NBTTagCompound old=source.write();VehicleStructure loaded=VehicleStructure.read(old);
        VehicleRotation.validate(loaded,new VehicleWorld(parent,loaded));
        require(VehicleTaxiMode.enabled(loaded),"2.5 controller and pilot seat retain Taxi Mode");
        VehicleStructure turned=loaded;for(int i=0;i<4;i++)turned=VehicleRotation.rotate(turned,new VehicleWorld(parent,turned),1);
        require(turned.write().equals(old),"2.5 Taxi controller, seat, programmable block and sign survive four turns with opaque capability data");
        NBTTagCompound unknown=saved.copy();unknown.setInteger("UnknownImportantData",42);
        VehicleStructure.Cell unknownCell=new VehicleStructure.Cell(controller,ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),unknown);
        boolean rejected=false;try{MovingBlockSupport.requireExactTileData(unknownCell,new VehicleWorld(parent,loaded).getTileEntity(controller));}catch(IllegalArgumentException expected){rejected=true;}
        require(rejected,"non-capability data loss remains rejected on legacy controller");
        require(old.equals(source.write()),"2.5 taxi source snapshot remains unchanged");
        return 1;
    }
    private VehicleUpgradeChecks(){}
}
