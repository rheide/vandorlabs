package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockTelescopicLandingGear;
import com.vandorlabs.tiles.TileEntityLandingGear;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Restore fully extended gear as part of the checked, atomic parking footprint. */
public final class VehicleLandingGear {
    private VehicleLandingGear(){}
    private static final Map<EntityGroundVehicle,Map<BlockPos,Float>> corrections=new WeakHashMap<>();
    static void correct(EntityGroundVehicle craft,BlockPos pos,float progress){corrections.computeIfAbsent(craft,k->new LinkedHashMap<>()).put(pos,progress);}
    public static boolean extended(EntityGroundVehicle craft){
        if(!ModularGearVehicles.extended(craft))return false;
        if(craft.view==null)return false;
        for(net.minecraft.tileentity.TileEntity raw:craft.view.tiles.values())if(raw instanceof TileEntityLandingGear&&craft.structure.isActive(raw.getPos())){TileEntityLandingGear tile=(TileEntityLandingGear)raw;
            if(tile.isRoot()&&(!craft.view.getBlockState(tile.getPos()).getValue(BlockTelescopicLandingGear.EXTENDED)||Math.abs(tile.progress-tile.getExtensionPixels()/16F)>.0001F))return false;}
        return true;
    }
    public static boolean setExtended(EntityGroundVehicle craft,BlockPos pos,boolean extend){
        if(!craft.structure.isActive(pos)||craft.world.isRemote||craft.transferInProgress||craft.ownershipBlocked)return false;
        net.minecraft.tileentity.TileEntity raw=craft.view.getTileEntity(pos);if(!(raw instanceof TileEntityLandingGear)||!((TileEntityLandingGear)raw).isRoot())return false;
        boolean grounded=!craft.hover&&(!extend||((TileEntityLandingGear)raw).taxiGrounded);
        if(grounded&&!TaxiTelescopicGear.canTarget(craft,extend))return false;
        Set<BlockPos> targets=new HashSet<>();targets.add(pos);if(grounded)for(TileEntityLandingGear gear:TaxiTelescopicGear.supports(craft))targets.add(gear.getPos());
        List<VehicleStructure.Cell> cells=new ArrayList<>();boolean changed=false;
        for(VehicleStructure.Cell cell:craft.structure.cells){if(targets.contains(cell.pos)&&(cell.state.getValue(BlockTelescopicLandingGear.EXTENDED)!=extend||grounded&&!((TileEntityLandingGear)craft.view.getTileEntity(cell.pos)).taxiGrounded)){NBTTagCompound tag=craft.view.getTileEntity(cell.pos).writeToNBT(new NBTTagCompound());if(grounded){tag.setBoolean("TaxiGroundedGear",true);((TileEntityLandingGear)craft.view.getTileEntity(cell.pos)).taxiGrounded=true;}cells.add(new VehicleStructure.Cell(cell.pos,cell.state.withProperty(BlockTelescopicLandingGear.EXTENDED,extend),tag));changed=true;}else cells.add(cell);}
        if(changed){craft.updateCanopies(new VehicleStructure(cells,craft.structure.seat,craft.structure.facing,craft.structure.gearCount),false);for(BlockPos target:targets)craft.signals.changed(target);}
        return true;
    }
    public static void refreshSignals(EntityGroundVehicle craft){
        if(craft.world.isRemote||craft.view==null)return;
        for(net.minecraft.tileentity.TileEntity raw:craft.view.tiles.values())if(raw instanceof TileEntityLandingGear&&craft.structure.isActive(raw.getPos())){TileEntityLandingGear gear=(TileEntityLandingGear)raw;
            if(!gear.isRoot()||gear.getMode()==0||gear.getRedstoneChannels().isEmpty())continue;
            boolean powered=false;for(int i=0;i<gear.getRedstoneChannels().size();i++)powered|=com.vandorlabs.redstone.RedstoneChannels.level(craft.view,gear.getPos(),gear.getRedstoneChannels().get(i))>0;
            setExtended(craft,gear.getPos(),gear.getMode()==1?powered:!powered);
        }
    }
    public static void tick(EntityGroundVehicle craft){
        VehicleGearCovers.tick(craft);
        ModularGearVehicles.tick(craft);
        VehicleGearCovers.tick(craft);
        if(craft.view==null||craft.ownershipBlocked||craft.transferInProgress)return;
        Map<BlockPos,Float> desired=new LinkedHashMap<>();
        if(craft.world.isRemote){Map<BlockPos,Float> pending=corrections.get(craft);if(pending!=null)for(Map.Entry<BlockPos,Float> entry:pending.entrySet())if(craft.structure.isActive(entry.getKey()))desired.put(entry.getKey(),entry.getValue());}
        for(net.minecraft.tileentity.TileEntity raw:craft.view.tiles.values())if(raw instanceof TileEntityLandingGear&&craft.structure.isActive(raw.getPos())){
            TileEntityLandingGear tile=(TileEntityLandingGear)raw;if(!tile.isRoot())continue;tile.previous=tile.progress;
            if(craft.world.isRemote)continue;
            boolean extend=craft.view.getBlockState(tile.getPos()).getValue(BlockTelescopicLandingGear.EXTENDED);
            float target=extend?tile.getExtensionPixels()/16F:0;
            if(!craft.hover&&!tile.taxiGrounded&&target<tile.progress)continue;
            float next=tile.progress<target?Math.min(target,tile.progress+.05F):Math.max(target,tile.progress-.05F);
            if(next==tile.progress)continue;
            if((craft.hover||!tile.taxiGrounded)&&!craft.world.isRemote&&next>tile.progress){
                float before=tile.progress;tile.progress=next;boolean blocked=false;
                try{VehiclePose pose=new VehiclePose(craft.posX,craft.posY,craft.posZ,craft.rotationYaw);
                    for(net.minecraft.util.math.AxisAlignedBB box:((BlockTelescopicLandingGear)tile.getBlockType()).vehicleBoxes(craft.view,tile.getPos()))
                        if(!craft.world.getCollisionBoxes(craft,pose.world(box,craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2).shrink(1e-6)).isEmpty()){blocked=true;break;}
                }finally{tile.progress=before;}
                if(blocked)continue;
            }
            desired.put(tile.getPos(),next);
        }
        boolean groundedMotion=false;for(BlockPos at:desired.keySet())groundedMotion|=((TileEntityLandingGear)craft.view.getTileEntity(at)).taxiGrounded;
        double chassisShift=0;
        if(groundedMotion&&!craft.hover&&!craft.world.isRemote&&!desired.isEmpty()){chassisShift=TaxiTelescopicGear.shift(craft,desired);if(!Double.isFinite(chassisShift))return;}
        if(desired.isEmpty()||!craft.collisionCache.readyForCanopyPose(craft.view,desired))return;
        for(Map.Entry<BlockPos,Float> entry:desired.entrySet())((TileEntityLandingGear)craft.view.getTileEntity(entry.getKey())).progress=entry.getValue();
        if(craft.world.isRemote)corrections.remove(craft);
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(VehicleStructure.Cell cell:craft.structure.cells)cells.add(desired.containsKey(cell.pos)?new VehicleStructure.Cell(cell.pos,cell.state,craft.view.getTileEntity(cell.pos).writeToNBT(new NBTTagCompound())):cell);
        VehicleStructure next=new VehicleStructure(cells,craft.structure.seat,craft.structure.facing,craft.structure.gearCount);
        boolean complete=false;
        if(groundedMotion&&!craft.hover&&!craft.world.isRemote){complete=true;for(BlockPos at:desired.keySet()){TileEntityLandingGear gear=(TileEntityLandingGear)craft.view.getTileEntity(at);complete&=Math.abs(gear.progress-(craft.view.getBlockState(at).getValue(BlockTelescopicLandingGear.EXTENDED)?gear.getExtensionPixels()/16F:0))<1e-5;}if(complete)next=parking(next,BlockPos.ORIGIN).structure;}
        if(complete){craft.install(next);craft.signals.open();}else craft.updateCanopies(next,true);
        if(chassisShift!=0)craft.setPosition(craft.posX,craft.posY+chassisShift,craft.posZ);
        if(!craft.world.isRemote)for(BlockPos pos:desired.keySet())craft.signals.changed(pos);
        if(complete)for(net.minecraft.entity.player.EntityPlayer player:craft.world.playerEntities)if(player instanceof net.minecraft.entity.player.EntityPlayerMP&&player.getDistanceSq(craft)<256*256)VehicleNetwork.send((net.minecraft.entity.player.EntityPlayerMP)player,craft.structure,craft.getEntityId(),craft.getUniqueID(),new BlockPos(craft),false,false);
    }
    public static VehicleRotation.Plan parking(VehicleStructure source,BlockPos origin){
        Map<BlockPos,VehicleStructure.Cell> cells=new LinkedHashMap<>();
        // Rebuild reservations from the restored roots. Rotated 2x2 footprints
        // and old, longer reservations must not leave unused invisible cells.
        for(VehicleStructure.Cell cell:source.cells)if(!(cell.state.getBlock() instanceof BlockTelescopicLandingGear)
                || !cell.state.getValue(BlockTelescopicLandingGear.LOWER))cells.put(cell.pos,cell);
        int minX=0,minY=0,minZ=0;
        for(VehicleStructure.Cell cell:source.cells){
            if(!(cell.state.getBlock() instanceof BlockTelescopicLandingGear) || cell.state.getValue(BlockTelescopicLandingGear.LOWER))continue;
            VehicleStructure.Cell below=source.at(cell.pos.down());
            if(cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockNonWheeledLandingGear && ((com.vandorlabs.blocks.BlockNonWheeledLandingGear)cell.state.getBlock()).ski && below!=null && below.state.getBlock()==cell.state.getBlock()){
                BlockPos belowRoot=below.pos;NBTTagCompound owner=below.tileData();
                if(below.state.getValue(BlockTelescopicLandingGear.LOWER) && owner!=null)belowRoot=below.pos.add(owner.getInteger("GearOwnerX"),owner.getInteger("GearOwnerDistance"),owner.getInteger("GearOwnerZ"));
                if(belowRoot.getY()<cell.pos.getY())continue;
            }
            NBTTagCompound tag=cell.tileData();if(tag==null)throw new IllegalArgumentException("Landing gear is missing its configuration.");
            int size=tag.getInteger("GearSize"),pixels=tag.hasKey("ExtensionPixels")?tag.getInteger("ExtensionPixels"):16;
            boolean grounded=tag.getBoolean("TaxiGroundedGear");boolean extend=!grounded||cell.state.getValue(BlockTelescopicLandingGear.EXTENDED);float extension=extend?pixels/16F:0;
            int count=(int)Math.ceil(extension)+(size>=3?1:0),radius=size>=3?1:0,minimum=size==4?0:-radius;
            tag.setFloat("Progress",extension);
            cells.put(cell.pos,new VehicleStructure.Cell(cell.pos,cell.state.withProperty(BlockTelescopicLandingGear.EXTENDED,extend),tag));
            for(int y=0;y<=count;y++)for(int x=minimum;x<=radius;x++)for(int z=minimum;z<=radius;z++){
                if(x==0&&y==0&&z==0)continue;
                BlockPos p=cell.pos.add(x,-y,z);VehicleStructure.Cell existing=cells.get(p);
                if(existing!=null){
                    NBTTagCompound owner=existing.tileData();
                    if(existing.state.getBlock()!=cell.state.getBlock() || !existing.state.getValue(BlockTelescopicLandingGear.LOWER) || owner==null
                            || !p.add(owner.getInteger("GearOwnerX"),owner.getInteger("GearOwnerDistance"),owner.getInteger("GearOwnerZ")).equals(cell.pos))
                        throw new IllegalArgumentException("Extended landing gear overlaps another craft block.");
                }
                TileEntityLandingGear part=(TileEntityLandingGear)cell.state.getBlock().createTileEntity(null,cell.state.withProperty(BlockTelescopicLandingGear.LOWER,true));part.setPos(p);part.setOwner(cell.pos);
                cells.put(p,new VehicleStructure.Cell(p,cell.state.withProperty(BlockTelescopicLandingGear.LOWER,true).withProperty(BlockTelescopicLandingGear.EXTENDED,true),part.writeToNBT(new NBTTagCompound())));
                minX=Math.min(minX,p.getX());minY=Math.min(minY,p.getY());minZ=Math.min(minZ,p.getZ());
            }
        }
        BlockPos shift=new BlockPos(-minX,-minY,-minZ);List<VehicleStructure.Cell> result=new ArrayList<>();
        for(VehicleStructure.Cell cell:cells.values())result.add(new VehicleStructure.Cell(cell.pos.add(shift),cell.state,cell.hasTileData()?VehicleTileData.translated(cell.tileData(),shift):null));
        return new VehicleRotation.Plan(new VehicleStructure(result,source.seat.add(shift),source.facing,source.gearCount),origin.subtract(shift));
    }
}
