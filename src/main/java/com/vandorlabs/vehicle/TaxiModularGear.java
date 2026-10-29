package com.vandorlabs.vehicle;

import com.vandorlabs.gear.*;
import com.vandorlabs.gear.core.GearMotion;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Matched vertical wheel/track arms change chassis height while keeping their physical feet anchored. */
final class TaxiModularGear {
    private TaxiModularGear(){}
    static void requireParkable(EntityGroundVehicle craft){for(TileEntity tile:craft.view.tiles.values())if(tile instanceof GearTileEntity){int phase=((GearTileEntity)tile).taxiPhase();if(phase>=0&&phase!=1)throw new IllegalArgumentException("Wait for the Taxi arm height adjustment to settle before parking");}}
    private static boolean integral(double value){return Math.abs(value-Math.rint(value))<1e-6;}
    private static List<GearTileEntity> roots(EntityGroundVehicle craft){
        List<GearTileEntity> result=new ArrayList<>();
        for(TileEntity tile:craft.view.tiles.values())if(craft.structure.isActive(tile.getPos())){
            if(tile instanceof com.vandorlabs.tiles.TileEntityLandingGear&&((com.vandorlabs.tiles.TileEntityLandingGear)tile).isRoot())return Collections.emptyList();
            if(tile instanceof GearTileEntity){GearTileEntity gear=(GearTileEntity)tile;if(gear.isController()&&gear.isLandingAssembly()){
                if(!gear.isOperational()||gear.mode()!=GearMotion.Mode.EXTEND||gear.mountDirection()!=EnumFacing.DOWN||gear.columns().size()!=1||!GearTaxiState.supportsFoot(gear.assembly().kind()))return Collections.emptyList();
                result.add(gear);
            }}
        }
        result.sort(Comparator.comparingLong(g->g.getPos().toLong()));return result;
    }
    private static NBTTagCompound saved(GearTileEntity gear,VehicleStructure structure){NBTTagCompound data=gear.writeToNBT(new NBTTagCompound());VehicleStructure.Cell source=structure.at(gear.getPos());NBTTagCompound original=source==null?null:source.tileData();if(original!=null&&original.hasKey("ForgeCaps"))data.setTag("ForgeCaps",original.getTag("ForgeCaps").copy());return data;}
    private static NBTTagCompound state(GearTileEntity root,int amount){NBTTagCompound state=new NBTTagCompound();state.setInteger("Schema",1);state.setInteger("Phase",0);state.setInteger("Height",root.assembly().columnHeight());state.setInteger("Amount",amount);state.setDouble("Level",0);state.setBoolean("Expand",false);return state;}
    private static double progress(GearTileEntity root,double rise){
        double maximum=root.pose(0).footOffsetY();if(maximum<=0||rise>maximum+1e-6)throw new IllegalArgumentException("Arm cannot reach an integral grounded endpoint");
        double smooth=Math.max(0,Math.min(1,1-rise/maximum));return .5-Math.sin(Math.asin(1-2*smooth)/3);
    }
    private static void feet(NBTTagCompound tag,int shift){NBTTagList old=tag.getTagList("GearFeet",11),next=new NBTTagList();for(int n=0;n<old.tagCount();n++){int[] cell=old.getIntArrayAt(n);if(cell.length!=3)throw new IllegalArgumentException("Invalid wheel topology");next.appendTag(new NBTTagIntArray(new int[]{cell[0],cell[1]+shift,cell[2]}));}tag.setTag("GearFeet",next);if(tag.hasKey("GearTrackContact",11)){int[] p=tag.getIntArray("GearTrackContact");if(p.length!=3)throw new IllegalArgumentException("Invalid wheel contact");p[1]+=shift;tag.setIntArray("GearTrackContact",p);}}
    private static final class Plan {final VehicleStructure structure;final int shift;Plan(VehicleStructure s,int dy){structure=s;shift=dy;}}
    /** Transfer only removed arm-section material to/from the owning root's bounded inert snapshot. */
    private static Plan endpoint(EntityGroundVehicle craft,List<GearTileEntity> roots,boolean expand,int amount){
        Map<BlockPos,VehicleStructure.Cell> cells=new LinkedHashMap<>();for(VehicleStructure.Cell cell:craft.structure.cells)cells.put(cell.pos,cell);
        Map<BlockPos,VehicleStructure.Cell> added=new LinkedHashMap<>();
        for(GearTileEntity gear:roots){
            NBTTagCompound saved=gear.taxiState();int full=saved==null?gear.assembly().columnHeight():saved.getInteger("Height"),shortHeight=full-amount;
            Set<BlockPos> column=new HashSet<>();for(int n=0;n<gear.assembly().columnHeight();n++)column.add(gear.getPos().down(n));
            List<VehicleStructure.Cell> sections=new ArrayList<>();
            if(!expand){
                for(int n=shortHeight;n<full;n++){
                    BlockPos at=gear.getPos().down(n);VehicleStructure.Cell cell=cells.remove(at);
                    if(cell==null||!(cell.state.getBlock() instanceof GearBlock)||((GearBlock)cell.state.getBlock()).kind()!=GearBlock.Kind.EXTENDER||!cell.hasTileData())throw new IllegalArgumentException("Arm sections changed before Taxi contraction");
                    sections.add(new VehicleStructure.Cell(at.subtract(gear.getPos()),cell.state,VehicleTileData.translated(cell.tileData(),BlockPos.ORIGIN.subtract(gear.getPos()))));
                }
            }else{
                if(saved==null||saved.getInteger("Phase")!=1)throw new IllegalArgumentException("Missing retained native arm sections");
                for(VehicleStructure.Cell cell:ArmStoredSections.retained(saved.getCompoundTag("Sections"))){if(cell.state.getBlock()!=gear.getBlockType())throw new IllegalArgumentException("Retained arm sections do not match their controller");BlockPos at=gear.getPos().add(cell.pos);VehicleStructure.Cell material=new VehicleStructure.Cell(at,cell.state,VehicleTileData.translated(cell.tileData(),gear.getPos()));if(added.put(at,material)!=null)throw new IllegalArgumentException("Arm section footprints overlap");}
            }
            int delta=expand?-amount:amount;
            for(BlockPos at:gear.members())if(!column.contains(at)){
                VehicleStructure.Cell cell=cells.remove(at);if(cell==null||!(cell.state.getBlock() instanceof GearBlock))throw new IllegalArgumentException("Wheel membership changed before Taxi adjustment");
                BlockPos target=at.up(delta);NBTTagCompound data=cell.hasTileData()?VehicleTileData.translated(cell.tileData(),new BlockPos(0,delta,0)):null;
                if(data!=null&&data.hasKey("GearOwnerOffset",4))data.setLong("GearOwnerOffset",gear.getPos().subtract(target).toLong());
                if(added.put(target,new VehicleStructure.Cell(target,cell.state,data))!=null)throw new IllegalArgumentException("Wheel footprints overlap");
            }
            NBTTagCompound data=saved(gear,craft.structure);data.setInteger("GearHeight",expand?full:shortHeight);feet(data,delta);data.setDouble("GearProgress",1);data.setInteger("GearRevision",gear.motionRevision()+1);data.setBoolean("GearBlocked",false);data.setString("GearError","");
            if(expand){data.removeTag(GearTaxiState.KEY);data.setBoolean("GearTargetDeployed",true);}
            else {NBTTagCompound retained=state(gear,amount);retained.setInteger("Height",full);retained.setInteger("Phase",1);retained.setDouble("Level",amount);retained.setTag("Sections",ArmStoredSections.retain(sections));GearTaxiState.validate(retained);data.setTag(GearTaxiState.KEY,retained);data.setBoolean("GearTargetDeployed",false);}
            VehicleStructure.Cell root=cells.get(gear.getPos());if(root==null)throw new IllegalArgumentException("Missing native arm root");cells.put(root.pos,new VehicleStructure.Cell(root.pos,root.state,data));
        }
        for(Map.Entry<BlockPos,VehicleStructure.Cell> entry:added.entrySet()){if(cells.putIfAbsent(entry.getKey(),entry.getValue())!=null)throw new IllegalArgumentException("Adjusted landing gear overlaps the craft");}
        return normalized(craft,new ArrayList<>(cells.values()));
    }
    private static Plan normalized(EntityGroundVehicle craft,List<VehicleStructure.Cell> cells){int minY=0;for(VehicleStructure.Cell cell:cells)minY=Math.min(minY,cell.pos.getY());int shift=-minY;List<VehicleStructure.Cell> normalized=new ArrayList<>();BlockPos delta=new BlockPos(0,shift,0);for(VehicleStructure.Cell cell:cells)normalized.add(new VehicleStructure.Cell(cell.pos.add(delta),cell.state,cell.hasTileData()?VehicleTileData.translated(cell.tileData(),delta):null));return new Plan(new VehicleStructure(normalized,craft.structure.seat.add(delta),craft.structure.facing,craft.structure.gearCount),shift);}
    private static VehicleCollision validate(EntityGroundVehicle craft,Plan plan,double y,boolean nativeCells){
        VehicleWorld view=new VehicleWorld(craft.world,plan.structure);for(VehicleStructure.Cell cell:plan.structure.cells)if(cell.hasTileData()&&!view.tiles.containsKey(cell.pos))throw new IllegalArgumentException("Landing gear block data cannot be installed");
        VehicleRotation.validate(plan.structure,view);try{plan.structure.encode();}catch(java.io.IOException failure){throw new IllegalArgumentException("Adjusted craft cannot be saved",failure);}
        VehicleCollision geometry=new VehicleCollision(view);if(!geometry.loaded(craft.world,craft.posX,y,craft.posZ,0,0,0,craft.rotationYaw))throw new IllegalArgumentException("Landing adjustment reaches unloaded terrain");
        VehiclePose pose=new VehiclePose(craft.posX,y,craft.posZ,craft.rotationYaw);
        for(AxisAlignedBB box:geometry.boxes){AxisAlignedBB at=pose.world(box,plan.structure.bounds.maxX/2,plan.structure.bounds.maxZ/2).shrink(1e-6);if(!craft.world.getCollisionBoxes(craft,at).isEmpty())throw new IllegalArgumentException("Landing adjustment is obstructed");}
        if(nativeCells){VehicleStructure rotated=VehicleRotation.rotate(plan.structure,view,VehicleRotation.turns(craft.rotationYaw));double cx=craft.posX+plan.structure.bounds.maxX/2,cz=craft.posZ+plan.structure.bounds.maxZ/2;if(!integral(cx-rotated.bounds.maxX/2)||!integral(cz-rotated.bounds.maxZ/2)||!integral(y))throw new IllegalArgumentException("Adjusted native parking footprint is not aligned to the block grid");BlockPos origin=new BlockPos(Math.round(cx-rotated.bounds.maxX/2),Math.round(y),Math.round(cz-rotated.bounds.maxZ/2));for(VehicleStructure.Cell cell:rotated.cells){BlockPos at=origin.add(cell.pos);if(at.getY()<0||at.getY()>=craft.world.getHeight()||!craft.world.getWorldBorder().contains(at)||!craft.world.isBlockLoaded(at)||!craft.world.isAirBlock(at))throw new IllegalArgumentException("The adjusted native parking footprint is occupied or unavailable");}}
        return geometry;
    }
    private static void publish(EntityGroundVehicle craft,Plan next,double y,VehicleCollision geometry){VehicleStructure original=craft.structure;double oldY=craft.posY,oldHover=craft.hoverTarget;try{craft.install(next.structure,geometry);craft.setPosition(craft.posX,y,craft.posZ);craft.view.origin=new BlockPos(craft.posX,y,craft.posZ);craft.hoverTarget+=y-oldY;craft.signals.open();}catch(RuntimeException|LinkageError failure){try{craft.install(original);}catch(RuntimeException|LinkageError rollback){failure.addSuppressed(rollback);craft.retainUnavailableSnapshot(original);}craft.setPosition(craft.posX,oldY,craft.posZ);if(craft.view!=null)craft.view.origin=new BlockPos(craft);craft.hoverTarget=oldHover;throw failure;}for(net.minecraft.entity.player.EntityPlayer player:craft.world.playerEntities)if(player instanceof net.minecraft.entity.player.EntityPlayerMP&&player.getDistanceSq(craft)<256*256)VehicleNetwork.send((net.minecraft.entity.player.EntityPlayerMP)player,craft.structure,craft.getEntityId(),craft.getUniqueID(),new BlockPos(craft),false,false);}
    private static void error(EntityGroundVehicle craft,GearTileEntity gear,String message){
        if(java.util.Objects.equals(gear.error(),message))return;
        gear.showError(message);com.vandorlabs.VandorLabs.logger.info("Taxi landing adjustment refused at {}: {}",gear.getPos(),message);
        java.util.UUID owner=craft.couplingActor();net.minecraft.entity.player.EntityPlayer player=owner==null?null:craft.world.getPlayerEntityByUUID(owner);
        if(player!=null)player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(message),true);
    }
    /** Return true while this grouped adjustment owns the gear step. */
    static boolean tick(EntityGroundVehicle craft){
        if(craft.hover||craft.world.isRemote)return false;List<GearTileEntity> roots=roots(craft);if(roots.size()<3){
            for(TileEntity tile:craft.view.tiles.values())if(tile instanceof GearTileEntity&&craft.structure.isActive(tile.getPos())){
                GearTileEntity gear=(GearTileEntity)tile;if(gear.isController()&&gear.isLandingAssembly()){gear.input();if(!gear.target())error(craft,gear,"Taxi height adjustment requires at least three matching vertical wheel or track arms");}
            }
            return false;
        }
        for(GearTileEntity gear:roots)gear.input();NBTTagCompound first=roots.get(0).taxiState();boolean retained=first!=null;
        boolean expand=retained?first.getBoolean("Expand"):true;
        for(GearTileEntity gear:roots)if(gear.target()!=expand){expand=gear.target();break;}
        if(!retained&&expand)return false;
        if(!craft.stopped()||!integral(craft.rotationYaw/90D)||!integral(craft.posX)||!integral(craft.posZ)){for(GearTileEntity gear:roots)error(craft,gear,"Stop and align the craft before changing Taxi arm height");return true;}
        int amount=retained?first.getInteger("Amount"):7;double level=retained?first.getDouble("Level"):0;
        try{
            for(GearTileEntity gear:roots){NBTTagCompound state=gear.taxiState();if(retained&&(state==null||state.getInteger("Amount")!=amount||Math.abs(state.getDouble("Level")-level)>1e-6||state.getInteger("Phase")!=first.getInteger("Phase")))throw new IllegalArgumentException("Taxi landing arms must share one height adjustment");if(!retained){if(gear.progress()!=1||state!=null)throw new IllegalArgumentException("Return every landing arm to rest first");amount=Math.min(amount,gear.assembly().columnHeight()-1);}}
            if(amount<1)throw new IllegalArgumentException("Landing arms already have their shortest native height");
            if(!retained){if(!integral(craft.posY))throw new IllegalArgumentException("Taxi height must start on the block grid");Plan endpoint=endpoint(craft,roots,false,amount);validate(craft,endpoint,craft.posY-amount-endpoint.shift,true);for(GearTileEntity gear:roots){gear.target(false);gear.taxiState(state(gear,amount));}expand=false;first=roots.get(0).taxiState();retained=true;}
            if(first.getInteger("Phase")==1){
                if(!expand)return false;
                Plan full=endpoint(craft,roots,true,amount);validate(craft,full,craft.posY+amount-full.shift,true);
                VehicleWorld preview=new VehicleWorld(craft.world,full.structure);List<VehicleStructure.Cell> cells=new ArrayList<>();
                for(VehicleStructure.Cell cell:full.structure.cells){TileEntity raw=preview.getTileEntity(cell.pos);if(raw instanceof GearTileEntity&&((GearTileEntity)raw).isController()&&((GearTileEntity)raw).isLandingAssembly()){
                    GearTileEntity projected=(GearTileEntity)raw;NBTTagCompound data=cell.tileData(),motion=state(projected,amount);motion.setInteger("Phase",2);motion.setDouble("Level",amount);motion.setBoolean("Expand",true);data.setTag(GearTaxiState.KEY,motion);data.setDouble("GearProgress",progress(projected,amount));cells.add(new VehicleStructure.Cell(cell.pos,cell.state,data));
                }else cells.add(cell);}
                Plan moving=new Plan(new VehicleStructure(cells,full.structure.seat,full.structure.facing,full.structure.gearCount),full.shift);VehicleCollision geometry=validate(craft,moving,craft.posY-full.shift,false);publish(craft,moving,craft.posY-full.shift,geometry);return true;
            }
            double next=expand?Math.max(0,level-1/20D):Math.min(amount,level+1/20D),delta=next-level;Map<GearTileEntity,Double> before=new LinkedHashMap<>();VehiclePose oldPose=new VehiclePose(craft.posX,craft.posY,craft.posZ,craft.rotationYaw),newPose=new VehiclePose(craft.posX,craft.posY-delta,craft.posZ,craft.rotationYaw);
            Map<BlockPos,Float> desired=new LinkedHashMap<>();for(GearTileEntity gear:roots)desired.put(gear.getPos(),(float)progress(gear,next));
            if(!craft.collisionCache.readyForCanopyPose(craft.view,desired))return true;
            List<AxisAlignedBB> old=VehicleCollision.capture(craft.view).boxes;
            try{
                for(GearTileEntity gear:roots){before.put(gear,gear.progress());gear.captureProgress(progress(gear,next));}
                for(GearTileEntity gear:roots){List<AxisAlignedBB> from=GearGeometry.boxes(gear,before.get(gear)),to=GearGeometry.boxes(gear,gear.progress());if(from.size()!=to.size())throw new IllegalArgumentException("Landing geometry changed during the stroke");for(int i=0;i<to.size();i++)if(!GearGeometry.foreign(craft.view,gear,GearGeometry.swept(gear,i,from.get(i),to.get(i),before.get(gear),gear.progress())).isEmpty())throw new IllegalArgumentException("Landing arm movement intersects the craft");}
                VehicleCollision.Raw changed=VehicleCollision.capture(craft.view);if(!changed.complete||old.size()!=changed.boxes.size())throw new IllegalArgumentException("Landing geometry is unavailable");
                for(int i=0;i<old.size();i++){AxisAlignedBB a=oldPose.world(old.get(i),craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2),b=newPose.world(changed.boxes.get(i),craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2),sweep=a.union(b).shrink(1e-6);if(!craft.world.isAreaLoaded(new BlockPos(sweep.minX,sweep.minY,sweep.minZ),new BlockPos(sweep.maxX,sweep.maxY,sweep.maxZ),false)||!craft.world.getCollisionBoxes(craft,sweep).isEmpty())throw new IllegalArgumentException("Taxi arm height movement is obstructed");}
            }finally{for(Map.Entry<GearTileEntity,Double> entry:before.entrySet())entry.getKey().captureProgress(entry.getValue());}
            boolean complete=next==0||Math.abs(next-amount)<1e-8;
            // Let pause-aware client interpolation settle in the original pose
            // basis before replacing it with the equivalent shorter native arm.
            int settling=0;
            if(!expand&&Math.abs(next-amount)<1e-8){settling=level<amount-1e-8?12:first.getInteger("Settle");if(settling>0){settling--;complete=false;}}

            if(complete&&next>0){Plan endpoint=endpoint(craft,roots,false,amount);VehicleCollision geometry=validate(craft,endpoint,craft.posY-delta-endpoint.shift,true);publish(craft,endpoint,craft.posY-delta-endpoint.shift,geometry);return true;}
            VehicleStructure original=craft.structure;double oldY=craft.posY,oldHover=craft.hoverTarget;Map<GearTileEntity,NBTTagCompound> originalRoots=new LinkedHashMap<>();for(GearTileEntity gear:roots)originalRoots.put(gear,saved(gear,original));
            try{
            List<VehicleStructure.Cell> cells=new ArrayList<>();Map<BlockPos,NBTTagCompound> updates=new HashMap<>();
            for(GearTileEntity gear:roots){gear.target(expand);gear.captureProgress(progress(gear,next));NBTTagCompound motion=gear.taxiState();if(complete){gear.taxiState(null);gear.captureProgress(1);}else{motion.setDouble("Level",next);motion.setBoolean("Expand",expand);if(!expand&&Math.abs(next-amount)<1e-8)motion.setInteger("Settle",settling);else motion.removeTag("Settle");gear.taxiState(motion);}gear.clearTaxiError();updates.put(gear.getPos(),saved(gear,craft.structure));}
            for(VehicleStructure.Cell cell:craft.structure.cells)cells.add(updates.containsKey(cell.pos)?new VehicleStructure.Cell(cell.pos,cell.state,updates.get(cell.pos)):cell);
            craft.updateCanopies(new VehicleStructure(cells,craft.structure.seat,craft.structure.facing,craft.structure.gearCount),true);craft.setPosition(craft.posX,craft.posY-delta,craft.posZ);craft.view.origin=new BlockPos(craft);craft.hoverTarget-=delta;com.vandorlabs.network.MessageGearMotion.send(craft,updates.keySet());return true;
            }catch(RuntimeException|LinkageError failure){
                try{for(Map.Entry<GearTileEntity,NBTTagCompound> entry:originalRoots.entrySet())entry.getKey().readFromNBT(entry.getValue());craft.updateCanopies(original,true);}catch(RuntimeException|LinkageError rollback){failure.addSuppressed(rollback);craft.retainUnavailableSnapshot(original);}craft.setPosition(craft.posX,oldY,craft.posZ);if(craft.view!=null)craft.view.origin=new BlockPos(craft);craft.hoverTarget=oldHover;throw failure;
            }
        }catch(RuntimeException|LinkageError denied){for(GearTileEntity gear:roots)error(craft,gear,denied.getMessage()==null?"Taxi arm height adjustment refused; snapshot retained":denied.getMessage());return true;}
    }
}
