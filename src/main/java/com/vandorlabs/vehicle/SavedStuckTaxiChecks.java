package com.vandorlabs.vehicle;

import com.vandorlabs.gear.*;
import java.io.InputStream;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Exact entity, intermediate arm state and terrain from a saved grounded craft. */
public final class SavedStuckTaxiChecks {
    private SavedStuckTaxiChecks(){}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Saved stuck Taxi: "+why);}
    private static List<GearTileEntity> roots(EntityGroundVehicle craft){List<GearTileEntity> result=new ArrayList<>();for(TileEntity tile:craft.view.tiles.values())if(tile instanceof GearTileEntity&&((GearTileEntity)tile).isController()&&((GearTileEntity)tile).isLandingAssembly())result.add((GearTileEntity)tile);return result;}
    private static double[] feet(EntityGroundVehicle craft){return roots(craft).stream().mapToDouble(g->{double low=Double.POSITIVE_INFINITY;for(AxisAlignedBB box:GearGeometry.boxes(g,g.progress()))low=Math.min(low,box.minY);return Math.rint((low+craft.posY)*100000)/100000;}).sorted().toArray();}
    private static void diagnostic(EntityGroundVehicle craft){
        Map<GearTileEntity,Double> previous=new LinkedHashMap<>();List<AxisAlignedBB> old=VehicleCollision.capture(craft.view).boxes;
        try{
            System.out.println("[vandorlabs][reprolab] saved-stuck-taxi geometry active="+craft.collision.boxes.size()+" captured="+old.size());
            int offset=0;for(VehicleStructure.Cell cell:craft.structure.cells){List<AxisAlignedBB> part=new ArrayList<>();cell.state.addCollisionBoxToList(craft.view,cell.pos,craft.structure.bounds.grow(16),part,null,false);TileEntity tile=craft.view.getTileEntity(cell.pos);GearTileEntity controller=tile instanceof GearTileEntity?((GearTileEntity)tile).loadedController():null;if(!part.isEmpty())System.out.println("[vandorlabs][reprolab] saved-stuck-taxi part="+cell.pos+" block="+cell.state.getBlock().getRegistryName()+" indices="+offset+".."+(offset+part.size()-1)+" controller="+(controller==null?"none":controller.getPos())+" minY="+part.stream().mapToDouble(v->v.minY).min().orElse(0));offset+=part.size();}
            for(GearTileEntity root:roots(craft)){previous.put(root,root.progress());double level=root.taxiLevel()+.05,maximum=root.pose(0).footOffsetY(),smooth=Math.max(0,Math.min(1,1-level/maximum));root.captureProgress(.5-Math.sin(Math.asin(1-2*smooth)/3));}
            List<AxisAlignedBB> next=VehicleCollision.capture(craft.view).boxes;VehiclePose a=new VehiclePose(craft.posX,craft.posY,craft.posZ,craft.rotationYaw),b=new VehiclePose(craft.posX,craft.posY-.05,craft.posZ,craft.rotationYaw);
            int reported=0;for(int i=0;i<Math.min(old.size(),next.size());i++){AxisAlignedBB from=a.world(old.get(i),craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2),to=b.world(next.get(i),craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2),sweep=from.union(to).shrink(1e-6);List<AxisAlignedBB> hit=craft.world.getCollisionBoxes(craft,sweep);if(!hit.isEmpty()&&reported++<5)System.out.println("[vandorlabs][reprolab] saved-stuck-taxi obstruction box="+i+" from="+from+" to="+to+" hits="+hit);}
        }finally{for(Map.Entry<GearTileEntity,Double> entry:previous.entrySet())entry.getKey().captureProgress(entry.getValue());}
    }
    private static String motionState(EntityGroundVehicle craft){VehicleCollision.Raw raw=VehicleCollision.capture(craft.view);return "y="+craft.posY+" motionY="+craft.motionY+" active-min="+craft.collision.boxes.stream().mapToDouble(v->v.minY+craft.posY).min().orElse(0)+" raw-min="+raw.boxes.stream().mapToDouble(v->v.minY+craft.posY).min().orElse(0)+" roots="+roots(craft).stream().map(g->g.getPos()+" progress="+g.progress()+" state="+g.taxiState()).collect(java.util.stream.Collectors.joining(";"));}
    private static void clearPose(EntityGroundVehicle craft,String stage){VehiclePose pose=new VehiclePose(craft.posX,craft.posY,craft.posZ,craft.rotationYaw);int index=0;for(AxisAlignedBB box:VehicleCollision.capture(craft.view).boxes){AxisAlignedBB at=pose.world(box,craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2).shrink(1e-6);List<AxisAlignedBB> hits=craft.world.getCollisionBoxes(craft,at);if(!hits.isEmpty())require(false,stage+" introduces terrain penetration index="+index+" box="+at+" hits="+hits+" "+motionState(craft));index++;}}
    private static void control(EntityGroundVehicle craft,boolean on){for(TileEntity tile:craft.view.tiles.values())if(tile instanceof com.vandorlabs.tiles.TileEntityCockpit){com.vandorlabs.tiles.TileEntityCockpit.Control control=((com.vandorlabs.tiles.TileEntityCockpit)tile).control("screen_left/0");require(control!=null&&control.getRedstoneChannels().contains(776),"exact saved cockpit gear channel");craft.signals.activateCockpit(control,on?15:0);craft.signals.tick();return;}throw new IllegalStateException("Saved cockpit missing");}
    private static void settled(EntityGroundVehicle craft,EntityPlayerMP actor,boolean expanded,int sequence)throws Exception{for(int tick=0;tick<600;tick++){String before=motionState(craft);craft.input(actor,sequence+tick,(byte)0,(byte)0,true);craft.onUpdate();clearPose(craft,"normal full entity stroke expanded="+expanded+" tick="+tick+" BEFORE["+before+"] AFTER");if(roots(craft).stream().allMatch(g->expanded?g.taxiPhase()<0:g.taxiPhase()==1))return;Thread.sleep(2);}throw new IllegalStateException("Saved normal stroke failed expanded="+expanded+" y="+craft.posY);}
    private static void park(WorldServer world,EntityPlayerMP actor,EntityGroundVehicle craft)throws Exception{if(actor.getRidingEntity()==craft)actor.dismountRidingEntity();craft.stopForParking();Exception[] error={null};VehicleTransfer.submit(world,()->{VehicleRotation.Plan plan=VehicleRotation.plan(craft);VehicleTransfer.park(world,craft,plan.origin,actor,false);},failure->error[0]=failure);VehicleTransferRuntimeChecks.drain(world);require(error[0]==null&&craft.isDead,"real journal parking after support recovery: "+error[0]);}
    private static void refusedRecovery(WorldServer world,NBTTagCompound fixture,String kind){
        EntityGroundVehicle craft=new EntityGroundVehicle(world);BlockPos obstruction=null;net.minecraft.block.state.IBlockState original=null;NBTTagCompound originalTile=null;
        try{
            craft.readFromNBT(fixture.getCompoundTag("Entity").copy());require(craft.structure!=null&&craft.loadedTaxiSupportCheck,"negative saved entity installs: "+kind);
            VehiclePose pose=new VehiclePose(craft.posX,craft.posY,craft.posZ,craft.rotationYaw);AxisAlignedBB highest=null;
            for(AxisAlignedBB local:VehicleCollision.capture(craft.view).boxes){AxisAlignedBB box=pose.world(local,craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2);if(highest==null||box.maxY>highest.maxY)highest=box;}
            require(highest!=null,"negative geometry exists");
            if(!kind.equals("fresh")){
                double y=kind.equals("ceiling")?Math.ceil((highest.maxY-1e-6)*2)/2:(highest.minY+highest.maxY)/2;
                obstruction=new BlockPos((highest.minX+highest.maxX)/2,y,(highest.minZ+highest.maxZ)/2);boolean upperSlab=kind.equals("ceiling")&&Math.abs(y-Math.floor(y)-.5)<1e-6;AxisAlignedBB obstacle=new AxisAlignedBB(obstruction);if(upperSlab)obstacle=new AxisAlignedBB(obstruction.getX(),obstruction.getY()+.5,obstruction.getZ(),obstruction.getX()+1,obstruction.getY()+1,obstruction.getZ()+1);
                if(kind.equals("ceiling")){require(!obstacle.intersects(highest.shrink(1e-6))&&obstacle.intersects(highest.offset(0,.875,0)),"ceiling fixture must obstruct only recovery sweep");}
                else require(obstacle.intersects(highest.shrink(1e-6)),"hull fixture must overlap a non-foot cell");
                original=world.getBlockState(obstruction);TileEntity tile=world.getTileEntity(obstruction);if(tile!=null)originalTile=tile.writeToNBT(new NBTTagCompound());
                world.setBlockState(obstruction,upperSlab?net.minecraft.init.Blocks.STONE_SLAB.getDefaultState().withProperty(net.minecraft.block.BlockSlab.HALF,net.minecraft.block.BlockSlab.EnumBlockHalf.TOP):net.minecraft.init.Blocks.STONE.getDefaultState(),2);
            }else craft.loadedTaxiSupportCheck=false;
            NBTTagCompound snapshot=craft.structure.write();double x=craft.posX,y=craft.posY,z=craft.posZ,hover=craft.hoverTarget;
            require(TaxiModularGear.tick(craft),"unsafe pose must retain adjustment ownership: "+kind);
            require(craft.posX==x&&craft.posY==y&&craft.posZ==z&&craft.hoverTarget==hover,"refused recovery moved craft: "+kind);
            require(snapshot.equals(craft.structure.write()),"refused recovery changed saved Craft NBT: "+kind);
            String expected=kind.equals("ceiling")?"obstructed above":kind.equals("hull")?"outside its feet":"obstructed";
            if(!kind.equals("fresh"))require(roots(craft).stream().allMatch(g->g.error().contains(expected)),"negative recovery did not identify "+kind+": "+roots(craft).stream().map(GearTileEntity::error).collect(java.util.stream.Collectors.joining(";")));
            else require(!craft.loadedTaxiSupportCheck,"fresh stroke enabled saved recovery");
        }finally{
            if(obstruction!=null&&original!=null){world.setBlockState(obstruction,original,2);if(originalTile!=null){TileEntity tile=world.getTileEntity(obstruction);require(tile!=null,"negative fixture terrain tile restoration");tile.readFromNBT(originalTile);}}
            craft.signals.close();world.removeEntityDangerously(craft);
        }
    }
    public static void run(WorldServer world,EntityPlayerMP actor)throws Exception{
        NBTTagCompound fixture;try(InputStream in=SavedStuckTaxiChecks.class.getResourceAsStream("/assets/vandorlabs/fixtures/followups4-stuck-taxi.nbt")){require(in!=null,"fixture exists");fixture=CompressedStreamTools.readCompressed(in);}
        int[] min=fixture.getIntArray("Min"),max=fixture.getIntArray("Max");
        for(BlockPos at:BlockPos.getAllInBox(new BlockPos(min[0],min[1],min[2]),new BlockPos(max[0],max[1],max[2]))){world.getChunkFromBlockCoords(at);world.setBlockToAir(at);}
        NBTTagList cells=fixture.getTagList("Cells",10);for(int n=0;n<cells.tagCount();n++){NBTTagCompound cell=cells.getCompoundTagAt(n);Block block=Block.getBlockFromName(cell.getString("Name"));require(block!=null,"saved terrain block");world.setBlockState(new BlockPos(cell.getInteger("X"),cell.getInteger("Y"),cell.getInteger("Z")),block.getStateFromMeta(cell.getInteger("Meta")),2);}
        refusedRecovery(world,fixture,"ceiling");refusedRecovery(world,fixture,"hull");refusedRecovery(world,fixture,"fresh");
        System.out.println("[vandorlabs][reprolab] saved-stuck-taxi-negative PASS ceiling hull-overlap fresh-no-rescue unchanged-position unchanged-Craft-NBT");
        EntityGroundVehicle craft=new EntityGroundVehicle(world);double ax=actor.posX,ay=actor.posY,az=actor.posZ;
        try{
            craft.readFromNBT(fixture.getCompoundTag("Entity").copy());require(craft.structure!=null&&craft.view!=null,"exact saved entity installs");require(craft.structure.write().equals(fixture.getCompoundTag("Entity").getCompoundTag("Craft")),"snapshot loads without rewriting saved own fields");
            require(world.spawnEntity(craft)&&roots(craft).size()==3,"exact saved vehicle spawns with three supports");
            require(roots(craft).stream().allMatch(g->g.taxiPhase()==2&&Math.abs(g.taxiLevel()-.4)<1e-6),"exact saved expanding phase and fractional level");
            double initial=craft.posY;double[] grounded=feet(craft);require(craft.board(actor),"actor boards saved cockpit");boolean done=false;
            for(int tick=0;tick<600;tick++){
                craft.input(actor,tick,(byte)0,(byte)0,true);craft.onUpdate();
                done=roots(craft).stream().allMatch(g->g.taxiPhase()==1);if(done)break;
                if(tick==40)diagnostic(craft);Thread.sleep(2);
            }
            require(done,"retraction remains blocked at y="+craft.posY+" initial="+initial+" roots="+roots(craft).stream().map(g->g.getPos()+" "+g.taxiState()+" "+g.error()).collect(java.util.stream.Collectors.joining(";")));
            require(Math.abs(craft.posY-82)<1e-5&&Arrays.stream(feet(craft)).allMatch(v->Math.abs(v-83)<1e-5),"saved embedded tracks recover above concrete and finish remaining stroke");
            clearPose(craft,"saved support recovery");
            require(roots(craft).stream().allMatch(g->g.hasTaxiSections()&&ArmStoredSections.retained(g.taxiState().getCompoundTag("Sections")).size()==1),"every root retains its one removed native section");
            control(craft,true);settled(craft,actor,true,1000);require(Math.abs(craft.posY-83)<1e-5,"raising returns to original full native height");
            control(craft,false);settled(craft,actor,false,2000);require(Math.abs(craft.posY-82)<1e-5,"normal lowering never sinks tracks into concrete");
            control(craft,true);for(int tick=0;tick<8;tick++){craft.input(actor,3000+tick,(byte)0,(byte)0,true);craft.onUpdate();clearPose(craft,"reversing stroke");Thread.sleep(2);}control(craft,false);settled(craft,actor,false,3100);
            VehicleRotation.Plan nativePlan=VehicleRotation.plan(craft);park(world,actor,craft);
            VehicleScan scan=new VehicleScan(world,nativePlan.origin.add(nativePlan.structure.seat));while(!scan.step(256)){}require(scan.result!=null&&scan.error==null,"rescued craft recaptures native layout: "+scan.error);
            final EntityGroundVehicle[] recaptured={null};Exception[] failure={null};VehicleTransfer.submit(world,scan.result,scan.origin,()->recaptured[0]=VehicleTransfer.assemble(world,scan.result,scan.origin,actor),error->failure[0]=error);VehicleTransferRuntimeChecks.drain(world);require(failure[0]==null&&recaptured[0]!=null,"native recapture journals complete: "+failure[0]);craft=recaptured[0];require(craft.board(actor),"reboard recaptured native craft");
            control(craft,true);settled(craft,actor,true,0);control(craft,false);settled(craft,actor,false,1000);clearPose(craft,"native capture normal roundtrip");park(world,actor,craft);
            System.out.println("[vandorlabs][reprolab] saved-stuck-taxi PASS exact-entity exact-terrain phase2-reversal full-entity-tick recovered-grounded-tracks retained-sections raise-lower-reverse journal-park native-recapture-no-sink");
        }finally{if(actor.getRidingEntity()==craft)actor.dismountRidingEntity();craft.signals.close();world.removeEntityDangerously(craft);actor.setPosition(ax,ay,az);for(BlockPos at:BlockPos.getAllInBox(new BlockPos(min[0],min[1],min[2]),new BlockPos(max[0],max[1],max[2])))world.removeTileEntity(at);for(BlockPos at:BlockPos.getAllInBox(new BlockPos(min[0],min[1],min[2]),new BlockPos(max[0],max[1],max[2])))world.setBlockToAir(at);}
    }
}
