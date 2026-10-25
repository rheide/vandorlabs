package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.gear.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Complete parked child craft retain their own controls and inventories inside the lower capture. */
public final class VehicleStackChecks {
    private static int checks;
    private static void require(boolean value,String reason){checks++;if(!value)throw new IllegalStateException("Stacked vehicles: "+reason);}
    private static void clear(WorldServer world,BlockPos base){for(BlockPos at:BlockPos.getAllInBox(base.add(-1,-4,-1),base.add(8,9,4))){world.removeTileEntity(at);world.setBlockToAir(at);}}
    private static BlockPos controller(BlockPos deck){return deck.add(2,1,1);}
    private static BlockPos seat(BlockPos deck){return deck.add(1,1,1);}
    private static BlockPos autopilot(BlockPos deck){return deck.add(5,1,1);}
    private static void build(WorldServer world,EntityPlayerMP actor,BlockPos deck,boolean auto,int channel){
        for(int x=0;x<7;x++)for(int z=0;z<3;z++)world.setBlockState(deck.add(x,0,z),Blocks.IRON_BLOCK.getDefaultState(),2);
        for(int x:new int[]{0,3,6}){
            BlockPos root=deck.add(x,-1,0);world.setBlockState(root,GearRegistry.GEAR_EXTENDER.getDefaultState(),2);world.setBlockState(root.down(),GearRegistry.LANDING_WHEEL.getDefaultState(),2);GearTileEntity gear=(GearTileEntity)world.getTileEntity(root);gear.operator(actor);require(gear.rebuild()&&gear.landingDeployed(),"complete native wheel support");
        }
        world.setBlockState(seat(deck),ModBlocks.PILOT_SEAT.getDefaultState(),2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,seat(deck),world.getBlockState(seat(deck)),actor,new ItemStack(ModBlocks.PILOT_SEAT));
        world.setBlockState(controller(deck),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);ModBlocks.VEHICLE_CONTROLLER.onBlockPlacedBy(world,controller(deck),world.getBlockState(controller(deck)),actor,new ItemStack(ModBlocks.VEHICLE_CONTROLLER));
        TileEntityVehicleController control=(TileEntityVehicleController)world.getTileEntity(controller(deck));control.setModeChannels(1,ChannelList.of(channel));control.initializeControls();
        Block thrust=Block.getBlockFromName("vandorlabs:impulse_engine");world.setBlockState(deck.add(0,1,2),thrust.getDefaultState().withProperty(BlockPropulsionLight.FACING,EnumFacing.NORTH),2);
        if(auto){world.setBlockState(autopilot(deck),Block.getBlockFromName("vandorlabs:autopilot").getDefaultState(),2);((TileEntityAutopilot)world.getTileEntity(autopilot(deck))).setRedstoneChannels(ChannelList.of(channel+1));}
        world.setBlockState(deck.add(4,1,1),Blocks.CHEST.getDefaultState(),2);TileEntityChest chest=(TileEntityChest)world.getTileEntity(deck.add(4,1,1));chest.setCustomName("Parked cargo "+channel);chest.setInventorySlotContents(0,new ItemStack(Items.DIAMOND,channel==1201?3:7));
        DeferredTileLoad.flush(world);
    }
    private static VehicleScan scan(WorldServer world,BlockPos seed){VehicleScan scan=new VehicleScan(world,seed);for(int step=0;step<1000&&!scan.step(128);step++){}return scan;}
    public static void run(WorldServer world,EntityPlayerMP actor)throws Exception{
        checks=0;BlockPos base=new BlockPos(880,130,352);double px=actor.posX,py=actor.posY,pz=actor.posZ;boolean creative=actor.capabilities.isCreativeMode;
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getChunkFromChunkCoords((base.getX()>>4)+x,(base.getZ()>>4)+z);
        try{actor.capabilities.isCreativeMode=true;actor.setPosition(base.getX()+2,base.getY()+2,base.getZ()+4);
            for(boolean outerAuto:new boolean[]{false,true}){
                clear(world,base);build(world,actor,base,true,1201);VehicleScan single=scan(world,controller(base));require(single.result!=null&&single.supportCount()==3,"unstacked complete craft retains normal support rules: "+single.error);
                clear(world,base);build(world,actor,base,outerAuto,1201);BlockPos child=base.up(5);build(world,actor,child,true,2201);
                for(int x:new int[]{0,3,6})for(int y=1;y<=2;y++)world.setBlockState(base.add(x,y,0),Blocks.IRON_BLOCK.getDefaultState(),2);
                for(int x=0;x<7;x++)for(int z=0;z<3;z++)world.setBlockState(base.add(x,-3,z),Blocks.STONE.getDefaultState(),2);
                Map<BlockPos,NBTTagCompound> childNBT=new LinkedHashMap<>();for(BlockPos at:BlockPos.getAllInBox(child.add(0,-2,0),child.add(6,2,2))){TileEntity tile=world.getTileEntity(at);if(tile!=null)childNBT.put(at,tile.writeToNBT(new NBTTagCompound()));}
                for(BlockPos selected:new BlockPos[]{controller(base),seat(base)}){
                    VehicleScan scan=scan(world,selected);require(scan.result!=null,"complete parked child captured from lower selected control: "+scan.error);require(scan.supportCount()==3&&scan.result.gearCount==3,"only lowest outer supports count");
                    require(scan.result.controller().pos.equals(controller(base).subtract(scan.origin))&&scan.result.seat.equals(seat(base).subtract(scan.origin)),"child controls never replace requested outer Pilot or Controller");
                    for(Map.Entry<BlockPos,NBTTagCompound> entry:childNBT.entrySet()){VehicleStructure.Cell cell=scan.result.at(entry.getKey().subtract(scan.origin));require(cell!=null&&VehicleTileData.translated(entry.getValue(),BlockPos.ORIGIN.subtract(scan.origin)).equals(cell.tileData()),"every child settings/inventory tile remains exact");}
                    require(scan.result.at(base.down(3).subtract(scan.origin))==null,"terrain below outer supports excluded");
                    VehicleStructure decoded=VehicleStructure.read(scan.result.write());EntityGroundVehicle test=new EntityGroundVehicle(world);test.install(decoded);BlockPos expectedAuto=outerAuto?autopilot(base).subtract(scan.origin):null;TileEntityAutopilot chosen=test.autopilot.controller();require(expectedAuto==null?chosen==null:chosen!=null&&chosen.getPos().equals(expectedAuto),"child Autopilot does not control outer craft when main Autopilot absent/present");
                    activeCargo(world,test,base,child,scan.origin);
                    if(chosen!=null){test.view.tiles.remove(expectedAuto);test.view.tiles.put(expectedAuto,chosen);test.view.tileRevision++;require(test.autopilot.controller()==chosen,"mutable tile map order cannot promote child Autopilot");}test.setDead();
                    if(selected.equals(controller(base))){EntityGroundVehicle craft=null;try{craft=VehicleTransfer.assemble(world,scan.result,scan.origin,actor);require(craft.structure.controller().pos.equals(controller(base).subtract(scan.origin))&&world.isAirBlock(child.add(4,1,1)),"actual lower conversion removes complete child into one entity");require(((TileEntityChest)craft.view.getTileEntity(child.add(4,1,1).subtract(scan.origin))).getStackInSlot(0).getCount()==7,"child cargo inventory retained exactly once in moving craft");VehicleTransfer.park(world,craft,scan.origin,actor,false);require(world.getTileEntity(child.add(4,1,1)) instanceof TileEntityChest&&((TileEntityChest)world.getTileEntity(child.add(4,1,1))).getStackInSlot(0).getCount()==7,"parking restores child cargo once");}finally{if(craft!=null){craft.signals.close();craft.setDead();world.removeEntityDangerously(craft);}}}
                }
                world.setBlockToAir(controller(child));VehicleScan incomplete=scan(world,controller(base));require(incomplete.result==null,"extra child Pilot without complete child controller refused");
            }
        }finally{clear(world,base);actor.capabilities.isCreativeMode=creative;actor.setPosition(px,py,pz);}
        boundaryParity(world);
        System.out.println("[vandorlabs][reprolab] stacked-vehicle-boundaries PASS checks="+checks+" complete lower+child actual assemble/park outer-only supports controls inventories terrain normal-unstacked incomplete-child refusal Autopilot none/present stable tile updates");
    }
    private static void activeCargo(WorldServer world,EntityGroundVehicle craft,BlockPos outer,BlockPos child,BlockPos origin){
        for(VehicleStructure.Cell cell:craft.structure.cells){boolean raised=cell.pos.getY()>=child.getY()-2-origin.getY();require(craft.structure.isActive(cell.pos)!=raised,"every raised child cell is cargo, every lower hull cell is active: "+cell.pos);}
        BlockPos outerEngine=outer.add(0,1,2).subtract(origin),childEngine=child.add(0,1,2).subtract(origin);
        craft.view.propulsionLevel=15;craft.view.propulsionMotion=new net.minecraft.util.math.Vec3d(0,0,1);craft.view.hover=true;
        require(craft.view.propulsionBrightness(outerEngine,15)==15&&craft.view.propulsionBrightness(childEngine,15)==0,"outer exhaust runs while child exhaust has zero brightness");
        require(!craft.view.getBlockState(childEngine).getValue(BlockPropulsionLight.POWERED),"child native thrust state remains OFF in moving view");
        List<VehicleStructure.Cell> childOnlyThrust=new ArrayList<>();for(VehicleStructure.Cell cell:craft.structure.cells)if(!cell.pos.equals(outerEngine))childOnlyThrust.add(cell);
        VehicleStructure noOuterEngine=new VehicleStructure(childOnlyThrust,craft.structure.seat,craft.structure.facing,craft.structure.gearCount);new VehicleWorld(world,noOuterEngine);
        boolean refused=false;try{VehicleFlight.requireThruster(noOuterEngine);}catch(IllegalArgumentException expected){refused=true;}require(refused,"child propulsion cannot satisfy outer Taxi requirement");
        require(new VehicleSteering().profile(craft.view).wheels==3,"Taxi wheel profile counts only three active outer wheel assemblies");
        List<VehicleStructure.Cell> childLift=new ArrayList<>(craft.structure.cells);Block lift=Block.getBlockFromName("vandorlabs:antigravity_plate");require(lift!=Blocks.AIR,"native lift fixture exists");
        for(int x=1;x<=3;x++)childLift.add(new VehicleStructure.Cell(child.add(x,2,2).subtract(origin),lift.getDefaultState(),null));
        VehicleStructure onlyChildLift=new VehicleStructure(childLift,craft.structure.seat,craft.structure.facing,craft.structure.gearCount);new VehicleWorld(world,onlyChildLift);require(!VehicleFlight.canHover(onlyChildLift),"three child lift blocks contribute zero outer Hover lift");
        for(int x=1;x<=3;x++)childLift.add(new VehicleStructure.Cell(outer.add(x,1,2).subtract(origin),lift.getDefaultState(),null));
        VehicleStructure outerLift=new VehicleStructure(childLift,craft.structure.seat,craft.structure.facing,craft.structure.gearCount);new VehicleWorld(world,outerLift);require(VehicleFlight.canHover(outerLift),"three active outer lift blocks still enable Hover");
        Map<BlockPos,NBTTagCompound> saved=new LinkedHashMap<>();
        for(net.minecraft.tileentity.TileEntity tile:craft.view.tiles.values())if(!craft.structure.isActive(tile.getPos())&&tile instanceof GearTileEntity){GearTileEntity gear=(GearTileEntity)tile;if(gear.isController())gear.target(false);saved.put(tile.getPos(),tile.writeToNBT(new NBTTagCompound()));}
        craft.hover=true;for(int tick=0;tick<8;tick++)ModularGearVehicles.tick(craft);
        for(Map.Entry<BlockPos,NBTTagCompound> entry:saved.entrySet())require(entry.getValue().equals(craft.view.getTileEntity(entry.getKey()).writeToNBT(new NBTTagCompound())),"inactive child arm target/progress/input NBT remains exact through Hover ticks");
        VehicleStructure roundtrip=VehicleStructure.read(craft.structure.write());new VehicleWorld(world,roundtrip);require(roundtrip.isActive(outerEngine)&&!roundtrip.isActive(childEngine),"save/reload rederives identical ownership membership");
        // A connected section is still active while OFF cannot yet be parked safely.
        List<VehicleStructure.Cell> coupled=new ArrayList<>(craft.structure.cells);BlockPos own=outer.add(6,0,2).subtract(origin),first=own.east(),second=first.east(),section=second.east();
        net.minecraft.block.Block coupler=net.minecraft.block.Block.getBlockFromName("vandorlabs:square_coupler");require(coupler instanceof com.vandorlabs.coupler.BlockCoupler,"native Coupler fixture exists");
        coupled.add(new VehicleStructure.Cell(first,coupler.getDefaultState().withProperty(com.vandorlabs.coupler.BlockCoupler.FACING,EnumFacing.EAST),null));
        coupled.add(new VehicleStructure.Cell(second,coupler.getDefaultState().withProperty(com.vandorlabs.coupler.BlockCoupler.FACING,EnumFacing.WEST),null));
        coupled.add(new VehicleStructure.Cell(section,ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null));
        coupled.add(new VehicleStructure.Cell(section.up(7),net.minecraft.block.Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),null));
        for(int y=1;y<7;y++)coupled.add(new VehicleStructure.Cell(section.up(y),Blocks.IRON_BLOCK.getDefaultState(),null));
        Block sideWheel=Block.getBlockFromName("vandorlabs:wheel_medium");require(sideWheel instanceof BlockWheel,"native medium side wheel fixture exists");
        coupled.add(new VehicleStructure.Cell(section.down(),Blocks.IRON_BLOCK.getDefaultState(),null));coupled.add(new VehicleStructure.Cell(section.down(2),sideWheel.getDefaultState(),null));
        VehicleStructure combined=new VehicleStructure(coupled,craft.structure.seat,craft.structure.facing,craft.structure.gearCount);new VehicleWorld(world,combined);
        VehicleSteering.Profile profile=new VehicleSteering().profile(new VehicleWorld(world,combined));require(combined.isActive(section.down(2))&&profile.wheels==4&&Math.abs(profile.meanKindRadius-.5)<1e-8,"owned same-level medium side wheel joins three outer regular wheels; raised child wheels remain excluded");
        require(VehicleControls.yawChange(1,.2,false,false,profile,true)!=0,"same-level coupled physical running gear participates in actual Taxi steering calculation");
        require(combined.isActive(section)&&combined.isActive(section.up(7))&&!combined.isActive(childEngine),"same-level owned Coupler section and its elevated engine remain active, raised child stays cargo even with released channels");
    }
    private static void boundaryParity(WorldServer world){
        for(boolean large:new boolean[]{false,true}){
            List<VehicleStructure.Cell> cells=new ArrayList<>();for(int x=0;x<=4;x++)cells.add(new VehicleStructure.Cell(new BlockPos(x,0,0),Blocks.IRON_BLOCK.getDefaultState(),null));
            BlockPos outer=new BlockPos(0,1,0),contact=new BlockPos(3,1,0),child=new BlockPos(3,4,0);
            cells.add(new VehicleStructure.Cell(outer,ModBlocks.PILOT_SEAT.getDefaultState(),null));cells.add(new VehicleStructure.Cell(outer.up(),ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.UPPER,true),null));cells.add(new VehicleStructure.Cell(outer.east(),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null));
            List<BlockPos> members=new ArrayList<>();
            if(large){Block block=Block.getBlockFromName("vandorlabs:wheel_large");require(block instanceof BlockWheel,"native Large Wheel fixture exists");BlockWheel wheel=(BlockWheel)block;members.addAll(wheel.members(contact,wheel.getDefaultState()));for(int n=0;n<members.size();n++)cells.add(new VehicleStructure.Cell(members.get(n),wheel.getDefaultState().withProperty(BlockWheel.PART,n),null));}
            else{members.add(contact);cells.add(new VehicleStructure.Cell(contact,Block.getBlockFromName("vandorlabs:vertical_hover_thruster").getDefaultState().withProperty(BlockPropulsionLight.FACING,EnumFacing.DOWN),null));cells.add(new VehicleStructure.Cell(contact.up(),Blocks.IRON_BLOCK.getDefaultState(),null));}
            cells.add(new VehicleStructure.Cell(contact.up(2),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null));cells.add(new VehicleStructure.Cell(child,ModBlocks.PILOT_SEAT.getDefaultState(),null));cells.add(new VehicleStructure.Cell(child.up(),ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.UPPER,true),null));
            VehicleStructure lower=new VehicleStructure(cells,outer,EnumFacing.NORTH,3);new VehicleWorld(world,lower);require(!lower.isActive(child),"native downward support cuts child from lower hull: LargeWheel="+large);for(BlockPos member:members)require(!lower.isActive(member),"all child support members inactive from lower hull");
            VehicleStructure upper=new VehicleStructure(cells,child,EnumFacing.NORTH,3);new VehicleWorld(world,upper);for(BlockPos member:members)require(upper.isActive(member),"all native support parts stay internally connected to their child root: LargeWheel="+large+" member="+member);require(!upper.isActive(outer),"selecting child component leaves lower hull inactive");
        }
    }
    private VehicleStackChecks(){}
}
