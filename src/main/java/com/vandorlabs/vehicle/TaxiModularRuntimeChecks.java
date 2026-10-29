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
    private static final class Source extends TileEntity implements com.vandorlabs.redstone.RedstoneChannelMember {
        boolean on=true;public TileEntity channelTile(){return this;}public boolean isChannelMemberActive(){return world!=null;}public int getRedstoneChannel(){return 28742;}public void setRedstoneChannel(int value){}public boolean hasLocalRedstoneSignal(){return on;}public void setChannelSignal(boolean value){}
        void set(boolean value){on=value;com.vandorlabs.redstone.RedstoneChannels.inputChanged(this);}
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Taxi modular arms: "+message);}
    private static List<GearTileEntity> roots(EntityGroundVehicle craft){List<GearTileEntity> roots=new ArrayList<>();for(TileEntity tile:craft.view.tiles.values())if(tile instanceof GearTileEntity&&((GearTileEntity)tile).isController()&&((GearTileEntity)tile).isLandingAssembly())roots.add((GearTileEntity)tile);return roots;}
    private static double foot(GearTileEntity gear,double y){double low=Double.POSITIVE_INFINITY;for(AxisAlignedBB box:GearGeometry.boxes(gear,gear.progress()))low=Math.min(low,box.minY);return Math.rint((low+y)*1000000)/1000000;}
    private static double[] feet(EntityGroundVehicle craft){return roots(craft).stream().mapToDouble(g->foot(g,craft.posY)).sorted().toArray();}
    private static void settle(EntityGroundVehicle craft,boolean expanded)throws Exception{
        boolean reloaded=false;
        boolean moving=craft.motionX!=0||craft.motionZ!=0;
        for(int tick=0;tick<1500;tick++){
            if(moving)craft.setPosition(craft.posX+craft.motionX,craft.posY,craft.posZ+craft.motionZ);
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
    private static void driveHeight(EntityGroundVehicle craft,EntityPlayerMP actor,boolean expanded)throws Exception{
        double x=craft.posX,z=craft.posZ;boolean moved=false;
        for(int tick=0;tick<1500;tick++){
            // Oscillate on the same flat support pad while exercising the full
            // server entity order: gear, signals, steering, gravity and clipping.
            byte forward=(byte)((tick/8)%2==0?1:-1);craft.driveSpeed=forward*.04;
            craft.input(actor,(expanded?2000:0)+tick,forward,(byte)0,false);craft.onUpdate();
            moved|=Math.hypot(craft.posX-x,craft.posZ-z)>.05;
            require(craft.structure!=null,"driven height adjustment became unavailable");
            boolean done=true;for(GearTileEntity gear:roots(craft)){int phase=gear.taxiPhase();done&=expanded?phase<0:phase==1;}
            if(done){require(moved,"full entity ticks never translated the driven craft");return;}Thread.sleep(1);
        }
        throw new IllegalStateException("Driven Taxi height timed out xyz="+craft.posX+","+craft.posY+","+craft.posZ+" roots="+roots(craft).stream().map(g->g.error()+" "+g.taxiState()).collect(java.util.stream.Collectors.joining(";")));
    }
    public static void run(WorldServer world,EntityPlayerMP actor)throws Exception{
        com.vandorlabs.network.GearTaxiPacketChecks.run();
        BlockPos origin=new BlockPos(1216,190,1215);double px=actor.posX,py=actor.posY,pz=actor.posZ;ItemStack held=actor.getHeldItemMainhand();
        for(BlockPos at:BlockPos.getAllInBox(origin.add(-8,-2,-8),origin.add(18,10,18)))world.getChunkFromBlockCoords(at);
        try{for(boolean large:new boolean[]{false,true})for(int turns=0;turns<4;turns++){
            EntityGroundVehicle craft=null;java.util.UUID paintingId=null;
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
                net.minecraft.entity.item.EntityPainting painting=new net.minecraft.entity.item.EntityPainting(world,origin.add(4,5,0),EnumFacing.NORTH);
                painting.art=net.minecraft.entity.item.EntityPainting.EnumArt.KEBAB;painting.readFromNBT(painting.writeToNBT(new NBTTagCompound()));
                require(painting.onValidSurface()&&world.spawnEntity(painting),"native painting supported by vehicle side");paintingId=painting.getUniqueID();
                VehicleScan scan=new VehicleScan(world,seat);while(!scan.step(256)){}require(scan.result!=null&&scan.error==null,"native previous-layout scan "+scan.error);
                VehicleWorld oldView=new VehicleWorld(world,scan.result);for(VehicleStructure.Cell cell:scan.result.cells)MovingBlockSupport.requireExactTileData(cell,oldView.getTileEntity(cell.pos));
                actor.setPosition(origin.getX()-2,origin.getY()+6,origin.getZ()-2);
                String recovery=System.getProperty("vandorlabs.armRecoveryPhase","");
                if(!large&&turns==0){
                    if(recovery.contains("painting-"))VehiclePaintingRecoveryChecks.prepare(world,scan.result,scan.origin,painting,recovery);
                    final EntityGroundVehicle[] built={null};final Exception[] failure={null};final java.util.UUID nativeId=paintingId;
                    VehicleTransferRuntimeChecks.slowDisk(world,()->VehicleTransfer.submit(world,scan.result,scan.origin,()->{VehicleTransfer.geometry(world,scan.result);built[0]=VehicleTransfer.assemble(world,scan.result,scan.origin,actor);},e->failure[0]=e),()->world.getEntityFromUuid(nativeId)==painting&&!painting.isDead&&world.getBlockState(seat).getBlock()==ModBlocks.PILOT_SEAT);
                    require(failure[0]==null&&built[0]!=null,"queued painting assembly completes after durable preparation: "+failure[0]);craft=built[0];
                    if(recovery.contains("painting-park")){VehiclePaintingRecoveryChecks.parking(world,recovery);final EntityGroundVehicle parkedCraft=craft;VehicleRotation.Plan target=VehicleRotation.plan(craft);VehicleTransfer.submit(world,null,target.origin,()->VehicleTransfer.park(world,parkedCraft,target.origin,actor,false),e->failure[0]=e);VehicleTransferRuntimeChecks.drain(world);throw new IllegalStateException("Painting crash barrier was not reached");}
                }else craft=VehicleTransfer.assemble(world,scan.result,scan.origin,actor);craft.rotationYaw=turns*90;craft.view.origin=scan.origin;require(world.getEntityFromUuid(paintingId)==null&&VehiclePaintings.entries(craft.structure).tagCount()==1,"assembly has one captured painting and no native duplicate");
                double initial=craft.posY;double[] feet=feet(craft);int count=craft.structure.cells.size();
                if(!large&&turns==0){
                    List<VehicleStructure.Cell> asymmetric=new ArrayList<>(craft.structure.cells);asymmetric.add(new VehicleStructure.Cell(new BlockPos((int)craft.structure.bounds.maxX,5,5),Blocks.IRON_BLOCK.getDefaultState(),null));
                    EntityGroundVehicle probe=new EntityGroundVehicle(world);probe.install(new VehicleStructure(asymmetric,craft.structure.seat,craft.structure.facing,craft.structure.gearCount));probe.setPosition(craft.posX,craft.posY,craft.posZ);probe.rotationYaw=90;
                    double sourceX=craft.posX,sourceY=craft.posY,sourceZ=craft.posZ;
                    // The real assembled owner is a physical collision obstacle.
                    // Isolate the preview on its support pad before moving it.
                    craft.setPosition(sourceX+64,sourceY,sourceZ);
                    try{
                        probe.rotationYaw=17;probe.setPosition(probe.posX+.25,probe.posY+.25,probe.posZ+.375);probe.motionX=.005;probe.motionZ=.003;
                        double before=probe.posY;double[] grounded=feet(probe);int originalCount=probe.structure.cells.size();
                        roots(probe).get(0).target(false);settle(probe,false);
                        require(Math.abs(probe.posY-before+2)<1e-6&&Arrays.equals(grounded,feet(probe)),"moving noncardinal craft lowers exactly two blocks with grounded feet");
                        require(probe.structure.cells.size()==originalCount-6,"moving contraction retains all removed native arm sections");
                        roots(probe).get(0).target(true);settle(probe,true);
                        require(Math.abs(probe.posY-before)<1e-6&&Arrays.equals(grounded,feet(probe))&&probe.structure.cells.size()==originalCount,"moving expansion restores height and every native arm section");
                        probe.setPosition(sourceX+.25,sourceY,sourceZ+.375);probe.motionX=probe.motionY=probe.motionZ=0;
                        double drivenY=probe.posY;double[] drivenFeet=feet(probe);require(probe.board(actor),"driver boards full entity height probe");
                        try{
                            roots(probe).get(0).target(false);driveHeight(probe,actor,false);
                            require(Math.abs(probe.posY-drivenY+2)<1e-6&&Arrays.equals(drivenFeet,feet(probe)),"full entity motion lowers exactly two blocks while grounded");
                            roots(probe).get(0).target(true);driveHeight(probe,actor,true);
                            require(Math.abs(probe.posY-drivenY)<1e-6&&Arrays.equals(drivenFeet,feet(probe))&&probe.structure.cells.size()==originalCount,"full entity motion raises and restores every retained section");
                        }finally{if(actor.getRidingEntity()==probe)actor.dismountRidingEntity();}

                    }finally{probe.signals.close();probe.setDead();craft.setPosition(sourceX,sourceY,sourceZ);}
                }
                if(!large&&turns==0){BlockPos obstacle=origin.add(5,3,5);world.setBlockState(obstacle,Blocks.STONE.getDefaultState(),2);NBTTagCompound unchanged=craft.structure.write();roots(craft).get(0).target(false);ModularGearVehicles.tick(craft);require(craft.posY==initial&&unchanged.equals(craft.structure.write()),"occupied final footprint changed the original owner");world.setBlockToAir(obstacle);}
                if(!large&&turns==0){
                    Source source=new Source();source.setWorld(world);source.setPos(new BlockPos(craft).west(3));
                    net.minecraft.item.ItemStack module=new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.REDSTONE_TRANSMITTER,1,3);com.vandorlabs.items.ItemRedstoneTransmitter.configure(module,com.vandorlabs.redstone.ChannelList.of(28742));com.vandorlabs.redstone.TransmitterSlots.set(source,module);
                    try{
                        for(GearTileEntity gear:roots(craft)){NBTTagCompound settings=gear.settings();settings.setInteger("Trigger",1);settings.setString("Channels","28742");gear.configure(settings);}
                        source.set(false);settle(craft,false);
                        for(GearTileEntity gear:roots(craft)){NBTTagCompound settings=gear.settings();settings.setInteger("Trigger",0);gear.configure(settings);}
                    }finally{com.vandorlabs.redstone.RedstoneChannels.unregister(source);}
                }else{roots(craft).get(0).target(false);settle(craft,false);}require(Math.abs(craft.posY-(initial-2))<1e-6&&Arrays.equals(feet,feet(craft)),"three-to-one lowering keeps every foot anchored");
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
                require(world.getEntityFromUuid(paintingId) instanceof net.minecraft.entity.item.EntityPainting,"parking restores native painting UUID exactly once");
                for(VehicleStructure.Cell cell:parked.structure.cells){BlockPos at=parked.origin.add(cell.pos);require(world.getBlockState(at)==cell.state,"short native state materializes exactly");if(world.getTileEntity(at) instanceof GearTileEntity){GearTileEntity gear=(GearTileEntity)world.getTileEntity(at);if(gear.isController()){require(gear.hasTaxiSections(),"parked root retains unused material");double before=gear.progress();gear.update();require(gear.progress()==before,"ordinary native tick does not consume retained sections");}}}
                for(VehicleStructure.Cell cell:parked.structure.cells){BlockPos at=parked.origin.add(cell.pos);TileEntity raw=world.getTileEntity(at);if(raw instanceof GearTileEntity){GearTileEntity gear=(GearTileEntity)raw;GearTileEntity controller=gear.loadedController();if(controller!=null&&controller.hasTaxiSections()){
                    require(!actor.interactionManager.tryHarvestBlock(at)&&world.getTileEntity(at)==raw,"retained native assembly member cannot publish an unjournaled recovery drop");
                    require(!cell.state.getBlock().canEntityDestroy(cell.state,world,at,actor)&&cell.state.getBlock().getExplosionResistance(world,at,actor,null)>=6000000,"retained member is protected from entity/explosion destruction");
                }}}
                BlockPos newSeat=parked.origin.add(parked.structure.seat);VehicleScan recapture=new VehicleScan(world,newSeat);while(!recapture.step(256)){}require(recapture.result!=null&&recapture.error==null,"short native craft recaptures "+recapture.error);
                craft=VehicleTransfer.assemble(world,recapture.result,recapture.origin,actor);require(world.getEntityFromUuid(paintingId)==null&&VehiclePaintings.entries(craft.structure).tagCount()==1,"painting survives native recapture without duplication");double[] grounded=feet(craft);double shortY=craft.posY;roots(craft).get(0).target(true);settle(craft,true);
                require(Arrays.equals(grounded,feet(craft)),"recaptured raising keeps physical wheels anchored");require(craft.structure.cells.size()==count,"raising restores every retained native arm section exactly once");
                for(GearTileEntity gear:roots(craft))require(gear.assembly().columnHeight()==3&&!gear.hasRetainedPayload(),"expanded root restores original height without a retained duplicate");
                require(craft.posY>=shortY-1e-6,"raising never lowers the chassis after recapture rebasing");
                VehicleRotation.Plan end=VehicleRotation.plan(craft);VehicleTransfer.park(world,craft,end.origin,actor,false);craft=null;
                for(VehicleStructure.Cell cell:end.structure.cells){BlockPos at=end.origin.add(cell.pos);TileEntity tile=world.getTileEntity(at);if(tile instanceof GearTileEntity&&((GearTileEntity)tile).isController()){require(actor.interactionManager.tryHarvestBlock(at)&&world.isAirBlock(at),"raised native controller returns to ordinary mining");break;}}
                System.out.println("[vandorlabs][reprolab] taxi-modular case PASS large="+large+" heading="+turns*90+" native-park-recapture raise original-material-count");
            }finally{if(craft!=null){craft.signals.close();craft.setDead();}if(VehicleTransfer.pending(world))VehicleTransfer.recover(world);if(paintingId!=null){net.minecraft.entity.Entity painting=world.getEntityFromUuid(paintingId);if(painting!=null)world.removeEntityDangerously(painting);}}
        }}finally{actor.setHeldItem(EnumHand.MAIN_HAND,held);actor.setPosition(px,py,pz);for(BlockPos at:BlockPos.getAllInBox(origin.add(-5,-2,-5),origin.add(15,9,15))){world.removeTileEntity(at);world.setBlockToAir(at);}}
        System.out.println("[vandorlabs][reprolab] taxi-modular PASS regular-Large four-headings native-three-to-one anchored-feet preflight-refusal saved-rotation real-park-recapture reversible-section-ownership moving-noncardinal-fractional-pose");
    }
}
