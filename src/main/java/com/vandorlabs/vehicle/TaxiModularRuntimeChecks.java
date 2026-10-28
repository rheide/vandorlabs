package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.gear.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Native placement, real transfer journals and saved shortened arm ownership across all headings. */
public final class TaxiModularRuntimeChecks {
    private TaxiModularRuntimeChecks(){}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Taxi modular arms: "+message);}
    private static List<GearTileEntity> roots(EntityGroundVehicle craft){List<GearTileEntity> roots=new ArrayList<>();for(TileEntity tile:craft.view.tiles.values())if(tile instanceof GearTileEntity&&((GearTileEntity)tile).isController()&&((GearTileEntity)tile).isLandingAssembly())roots.add((GearTileEntity)tile);return roots;}
    private static double foot(GearTileEntity gear,double y){double low=Double.POSITIVE_INFINITY;for(AxisAlignedBB box:GearGeometry.boxes(gear,gear.progress()))low=Math.min(low,box.minY);return Math.rint((low+y)*1000000)/1000000;}
    private static double[] feet(EntityGroundVehicle craft){return roots(craft).stream().mapToDouble(g->foot(g,craft.posY)).sorted().toArray();}
    private static void settle(EntityGroundVehicle craft,boolean expanded)throws Exception{
        boolean reloaded=false;
        for(int tick=0;tick<1500;tick++){
            ModularGearVehicles.tick(craft);require(craft.structure!=null,"a valid height change became unavailable");boolean done=true;
            if(!expanded&&!reloaded){NBTTagCompound state=roots(craft).get(0).taxiState();if(state!=null&&state.getInteger("Phase")==0&&state.getDouble("Level")>0&&state.getDouble("Level")<state.getInteger("Amount")){
                NBTTagCompound before=craft.structure.write();boolean refused=false;try{VehicleRotation.plan(craft);}catch(IllegalArgumentException expected){refused=true;}require(refused&&before.equals(craft.structure.write()),"in-flight native parking is refused without rewriting ownership");
                double y=craft.posY;craft.install(VehicleStructure.read(before));require(craft.posY==y&&before.equals(craft.structure.write()),"saved intermediate Taxi pose reinstalls exactly");reloaded=true;
            }}
            for(GearTileEntity gear:roots(craft)){NBTTagCompound state=gear.taxiState();if(expanded?state!=null:state==null||state.getInteger("Phase")!=1)done=false;}
            if(done)return;Thread.sleep(1);
        }
        StringBuilder status=new StringBuilder();for(GearTileEntity gear:roots(craft))status.append(gear.getPos()).append(' ').append(gear.progress()).append(' ').append(gear.error()).append(' ').append(gear.taxiState()).append(';');throw new IllegalStateException("Taxi modular height timed out y="+craft.posY+" "+status);
    }
    public static void run(WorldServer world,EntityPlayerMP actor)throws Exception{
        com.vandorlabs.network.GearTaxiPacketChecks.run();
        BlockPos origin=new BlockPos(1216,190,1216);double px=actor.posX,py=actor.posY,pz=actor.posZ;ItemStack held=actor.getHeldItemMainhand();
        for(BlockPos at:BlockPos.getAllInBox(origin.add(-8,-2,-8),origin.add(18,10,18)))world.getChunkFromBlockCoords(at);
        try{for(boolean large:new boolean[]{false,true})for(int turns=0;turns<4;turns++){
            EntityGroundVehicle craft=null;
            try{
                for(BlockPos at:BlockPos.getAllInBox(origin.add(-5,-2,-5),origin.add(15,9,15))){world.removeTileEntity(at);world.setBlockState(at,at.getY()==origin.getY()-(large?1:0)?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);}
                for(int x=1;x<=9;x++)for(int z=1;z<=9;z++)world.setBlockState(origin.add(x,5,z),Blocks.IRON_BLOCK.getDefaultState(),2);
                BlockPos seat=origin.add(4,5,4);world.setBlockState(seat,ModBlocks.PILOT_SEAT.getDefaultState(),2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,seat,world.getBlockState(seat),actor,new ItemStack(ModBlocks.PILOT_SEAT));
                world.setBlockState(origin.add(5,5,4),net.minecraft.block.Block.getBlockFromName("vandorlabs:vehicle_controller").getDefaultState(),2);
                world.setBlockState(origin.add(5,5,9),net.minecraft.block.Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState().withProperty(BlockPropulsionLight.FACING,EnumFacing.SOUTH),2);
                GearBlock arm=large?GearRegistry.LARGE_GEAR_EXTENDER:GearRegistry.GEAR_EXTENDER,wheel=large?GearRegistry.LARGE_LANDING_WHEEL:GearRegistry.LANDING_WHEEL;
                for(BlockPos local:Arrays.asList(new BlockPos(2,4,2),new BlockPos(8,4,2),new BlockPos(5,4,8))){
                    BlockPos root=origin.add(local);actor.setPosition(root.getX()+2,root.getY()+2,root.getZ()+2);actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(arm,3));
                    for(int n=0;n<3;n++)require(actor.getHeldItemMainhand().onItemUse(actor,world,root.up().down(n),EnumHand.MAIN_HAND,EnumFacing.DOWN,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary supported three-section arm placement");
                    actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(wheel));require(actor.getHeldItemMainhand().onItemUse(actor,world,root.down(2),EnumHand.MAIN_HAND,EnumFacing.DOWN,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary wheel placement");
                    GearTileEntity controller=(GearTileEntity)world.getTileEntity(root);controller.operator(actor);require(controller.rebuild()&&controller.assembly().columnHeight()==3,"native three-high wheel assembly forms");
                }
                VehicleScan scan=new VehicleScan(world,seat);while(!scan.step(256)){}require(scan.result!=null&&scan.error==null,"native previous-layout scan "+scan.error);
                VehicleWorld oldView=new VehicleWorld(world,scan.result);for(VehicleStructure.Cell cell:scan.result.cells)MovingBlockSupport.requireExactTileData(cell,oldView.getTileEntity(cell.pos));
                actor.setPosition(origin.getX()-2,origin.getY()+6,origin.getZ()-2);craft=VehicleTransfer.assemble(world,scan.result,scan.origin,actor);craft.rotationYaw=turns*90;craft.view.origin=scan.origin;
                double initial=craft.posY;double[] feet=feet(craft);int count=craft.structure.cells.size();
                if(!large&&turns==0){
                    List<VehicleStructure.Cell> asymmetric=new ArrayList<>(craft.structure.cells);asymmetric.add(new VehicleStructure.Cell(new BlockPos((int)craft.structure.bounds.maxX,5,5),Blocks.IRON_BLOCK.getDefaultState(),null));
                    EntityGroundVehicle probe=new EntityGroundVehicle(world);probe.install(new VehicleStructure(asymmetric,craft.structure.seat,craft.structure.facing,craft.structure.gearCount));probe.setPosition(craft.posX,craft.posY,craft.posZ);probe.rotationYaw=90;
                    try{NBTTagCompound unchanged=probe.structure.write();double before=probe.posY;roots(probe).get(0).target(false);ModularGearVehicles.tick(probe);require(unchanged.equals(probe.structure.write())&&probe.posY==before&&roots(probe).stream().allMatch(g->g.taxiState()==null),"asymmetric half-cell parking footprint refuses without changing its original snapshot");}finally{probe.signals.close();probe.setDead();}
                }
                if(!large&&turns==0){BlockPos obstacle=origin.add(5,3,5);world.setBlockState(obstacle,Blocks.STONE.getDefaultState(),2);NBTTagCompound unchanged=craft.structure.write();roots(craft).get(0).target(false);ModularGearVehicles.tick(craft);require(craft.posY==initial&&unchanged.equals(craft.structure.write()),"occupied final footprint changed the original owner");world.setBlockToAir(obstacle);}
                roots(craft).get(0).target(false);settle(craft,false);require(Math.abs(craft.posY-(initial-2))<1e-6&&Arrays.equals(feet,feet(craft)),"three-to-one lowering keeps every foot anchored");
                require(craft.structure.cells.size()==count-6,"exactly six removed native arm sections are retained");
                for(GearTileEntity gear:roots(craft)){require(gear.assembly().columnHeight()==1&&gear.hasTaxiSections(),"short native topology owns retained material");GearTaxiState.validate(gear.taxiState());require(ArmStoredSections.retained(gear.taxiState().getCompoundTag("Sections")).size()==2,"each root retains its two original sections");}
                VehicleStructure persisted=VehicleStructure.read(craft.structure.write());VehicleStructure cycled=persisted;for(int turn=0;turn<4;turn++)cycled=VehicleRotation.rotate(cycled,new VehicleWorld(world,cycled),1);require(cycled.write().equals(persisted.write()),"four saved rotations preserve retained arm sections and topology");
                if(!large&&turns==0){
                    List<VehicleStructure.Cell> damaged=new ArrayList<>();boolean changed=false;
                    for(VehicleStructure.Cell cell:persisted.cells){NBTTagCompound tag=cell.tileData();if(!changed&&tag!=null&&tag.hasKey(GearTaxiState.KEY,10)){tag.getCompoundTag(GearTaxiState.KEY).setBoolean("UnknownRecoveryMode",true);changed=true;damaged.add(new VehicleStructure.Cell(cell.pos,cell.state,tag));}else damaged.add(cell);}
                    VehicleStructure invalid=new VehicleStructure(damaged,persisted.seat,persisted.facing,persisted.gearCount);NBTTagCompound original=invalid.write(),entity=craft.writeToNBT(new NBTTagCompound());entity.setTag("Craft",original.copy());entity.setUniqueId("UUID",java.util.UUID.randomUUID());
                    EntityGroundVehicle failed=new EntityGroundVehicle(world);failed.readFromNBT(entity);require(failed.structure==null&&failed.view==null&&failed.writeToNBT(new NBTTagCompound()).getCompoundTag("Craft").equals(original),"unknown Taxi recovery data leaves inert entity retaining exact original snapshot");failed.setDead();
                }
                VehicleRotation.Plan parked=VehicleRotation.plan(craft);VehicleTransfer.park(world,craft,parked.origin,actor,false);require(craft.isDead&&!VehicleTransfer.pending(world),"real native parking completes durable ownership transfer");craft=null;
                for(VehicleStructure.Cell cell:parked.structure.cells){BlockPos at=parked.origin.add(cell.pos);require(world.getBlockState(at)==cell.state,"short native state materializes exactly");if(world.getTileEntity(at) instanceof GearTileEntity){GearTileEntity gear=(GearTileEntity)world.getTileEntity(at);if(gear.isController()){require(gear.hasTaxiSections(),"parked root retains unused material");double before=gear.progress();gear.update();require(gear.progress()==before,"ordinary native tick does not consume retained sections");}}}
                for(VehicleStructure.Cell cell:parked.structure.cells){BlockPos at=parked.origin.add(cell.pos);TileEntity raw=world.getTileEntity(at);if(raw instanceof GearTileEntity){GearTileEntity gear=(GearTileEntity)raw;GearTileEntity controller=gear.loadedController();if(controller!=null&&controller.hasTaxiSections()){
                    require(!actor.interactionManager.tryHarvestBlock(at)&&world.getTileEntity(at)==raw,"retained native assembly member cannot publish an unjournaled recovery drop");
                    require(!cell.state.getBlock().canEntityDestroy(cell.state,world,at,actor)&&cell.state.getBlock().getExplosionResistance(world,at,actor,null)>=6000000,"retained member is protected from entity/explosion destruction");
                }}}
                BlockPos newSeat=parked.origin.add(parked.structure.seat);VehicleScan recapture=new VehicleScan(world,newSeat);while(!recapture.step(256)){}require(recapture.result!=null&&recapture.error==null,"short native craft recaptures "+recapture.error);
                craft=VehicleTransfer.assemble(world,recapture.result,recapture.origin,actor);double[] grounded=feet(craft);double shortY=craft.posY;roots(craft).get(0).target(true);settle(craft,true);
                require(Arrays.equals(grounded,feet(craft)),"recaptured raising keeps physical wheels anchored");require(craft.structure.cells.size()==count,"raising restores every retained native arm section exactly once");
                for(GearTileEntity gear:roots(craft))require(gear.assembly().columnHeight()==3&&!gear.hasRetainedPayload(),"expanded root restores original height without a retained duplicate");
                require(craft.posY>=shortY-1e-6,"raising never lowers the chassis after recapture rebasing");
                VehicleRotation.Plan end=VehicleRotation.plan(craft);VehicleTransfer.park(world,craft,end.origin,actor,false);craft=null;
                for(VehicleStructure.Cell cell:end.structure.cells){BlockPos at=end.origin.add(cell.pos);TileEntity tile=world.getTileEntity(at);if(tile instanceof GearTileEntity&&((GearTileEntity)tile).isController()){require(actor.interactionManager.tryHarvestBlock(at)&&world.isAirBlock(at),"raised native controller returns to ordinary mining");break;}}
                System.out.println("[vandorlabs][reprolab] taxi-modular case PASS large="+large+" heading="+turns*90+" native-park-recapture raise original-material-count");
            }finally{if(craft!=null){craft.signals.close();craft.setDead();}if(VehicleTransfer.pending(world))VehicleTransfer.recover(world);}
        }}finally{actor.setHeldItem(EnumHand.MAIN_HAND,held);actor.setPosition(px,py,pz);for(BlockPos at:BlockPos.getAllInBox(origin.add(-5,-2,-5),origin.add(15,9,15))){world.removeTileEntity(at);world.setBlockToAir(at);}}
        System.out.println("[vandorlabs][reprolab] taxi-modular PASS regular-Large four-headings native-three-to-one anchored-feet preflight-refusal saved-rotation real-park-recapture reversible-section-ownership");
    }
}
