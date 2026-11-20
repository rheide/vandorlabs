package com.vandorlabs.gear;

import com.vandorlabs.gear.core.GearTopology;
import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Bounded native-foot group adoption. Every source and destination belongs to one journal. */
final class ArmLandingPlan {
    static final class Foot {
        final VehicleStructure.Cell original;final BlockPos target;final IBlockState state;final TileEntity prepared;
        Foot(VehicleStructure.Cell original,BlockPos target,IBlockState state,TileEntity prepared){this.original=original;this.target=target;this.state=state;this.prepared=prepared;}
    }
    final List<Foot> feet=new ArrayList<>();final List<GearTopology.Cell> offsets=new ArrayList<>();final Map<BlockPos,IBlockState> states=new LinkedHashMap<>();final Set<BlockPos> changed=new LinkedHashSet<>();
    GearTopology.FootKind kind;EnumFacing facing;
    static ArmLandingPlan capture(GearTileEntity arm,ArmPayload.Endpoint endpoint){
        WorldServer world=(WorldServer)arm.getWorld();int height=arm.assembly().columnHeight();BlockPos home=arm.localCell(0,-height,0),first=endpoint.placed(home);requireLoaded(world,first);IBlockState seed=world.getBlockState(first);
        if(!GearAssembly.isFoot(seed))return null;
        if(!ArmLooseGearData.foot(seed))return null;
        if(arm.activeAngle()!=90||arm.physicalStartAngle()!=0)throw new IllegalArgumentException("Use a zero-degree Start and 90-degree End before attaching landing gear");
        GearBlock block=(GearBlock)seed.getBlock();if(block.kind()==GearBlock.Kind.WHEEL&&block.sizeClass()!=arm.armSize())throw new IllegalArgumentException("Wheel and mounting arm sizes must match");
        ArmLandingPlan plan=new ArmLandingPlan();plan.kind=GearTopology.FootKind.valueOf(block.kind().name());EnumFacing normal=endpoint.mappedFacing(arm.mountDirection());ArrayDeque<BlockPos> pending=new ArrayDeque<>();Set<BlockPos> visited=new HashSet<>();pending.add(first);
        EntityPlayerMP actor=com.vandorlabs.tiles.WorldActionAuthorization.actor(arm);for(BlockPos member:arm.members())VehicleService.permission(actor,member);
        while(!pending.isEmpty()){
            BlockPos at=pending.removeFirst();if(!visited.add(at))continue;if(visited.size()>GearTopology.MAX_FEET)throw new IllegalArgumentException("A landing group may contain at most 64 feet");requireLoaded(world,at);IBlockState nativeState=world.getBlockState(at);
            if(nativeState.getBlock()!=block)throw new IllegalArgumentException("Use one foot type in a connected landing group");if(block instanceof LargeGearBlock&&!block.componentIntact(world,block.componentRoot(world,at,nativeState)))throw new IllegalArgumentException("Keep all four Large Wheel cells intact before attaching it");TileEntity raw=world.getTileEntity(at);if(raw==null)throw new IllegalArgumentException("Landing foot data is still loading");VehicleStructure.Cell original=new VehicleStructure.Cell(at,nativeState,raw.writeToNBT(new NBTTagCompound()));if(!ArmLooseGearData.admitted(original))throw new IllegalArgumentException("A landing foot already belongs to another assembly");VehicleService.permission(actor,at);
            if(com.vandorlabs.world.ProtectionProbe.refused(new net.minecraftforge.event.world.BlockEvent.BreakEvent(world,at,nativeState,actor)))throw new IllegalArgumentException("A protection rule prevented attaching the landing group");
            BlockPos target=endpoint.construction(at);BlockPos local=ArmOrientation.inverse(arm.mountDirection(),target.subtract(arm.getPos()));if(local.getY()!=-height||Math.abs(local.getX())>64||Math.abs(local.getZ())>64)throw new IllegalArgumentException("The landing group is outside the arm's construction plane");
            IBlockState state=nativeState;NBTTagCompound data=original.tileData();IBlockState mapped=endpoint.constructionState(original);NBTTagCompound mappedData=mapped==null?null:endpoint.constructionData(original);if(mappedData!=null&&arm.mountDirection().name().equals(mappedData.getString("ArmMountDirection"))){state=mapped;data=mappedData;}
            if(block instanceof LargeGearBlock&&(mappedData==null||!arm.mountDirection().name().equals(mappedData.getString("ArmMountDirection"))||ArmFootComponentFrame.frame(original)!=normal))throw new IllegalArgumentException("Place the complete Large Wheel in the active socket plane");data.setString("ArmMountDirection",arm.mountDirection().name());ArmFootComponentFrame.writeFoot(data,state,arm.mountDirection());data=VehicleTileData.translated(data,target.subtract(at));TileEntity prepared=TileEntity.create(world,data);if(!(prepared instanceof GearTileEntity))throw new IllegalArgumentException("Landing foot cannot be restored");prepared.setWorld(world);prepared.setPos(target);NBTTagCompound own=data.copy(),round=prepared.writeToNBT(new NBTTagCompound());own.removeTag("ForgeCaps");round.removeTag("ForgeCaps");if(!own.equals(round))throw new IllegalArgumentException("Landing foot own data cannot round-trip");
            EnumFacing targetFacing=state.getValue(GearBlock.FACING);if(plan.facing==null)plan.facing=targetFacing;else if(plan.kind!=GearTopology.FootKind.PLATE&&plan.facing!=targetFacing)throw new IllegalArgumentException("Align every Ski or Wheel in the landing group");if(plan.states.put(target,state)!=null)throw new IllegalArgumentException("Landing foot mapping overlaps itself");plan.offsets.add(new GearTopology.Cell(local.getX(),local.getY(),local.getZ()));plan.feet.add(new Foot(original,target,state,prepared));plan.changed.add(at);plan.changed.add(target);
            for(EnumFacing side:EnumFacing.values())if(side.getAxis()!=normal.getAxis()){BlockPos next=at.offset(side);requireLoaded(world,next);if(GearAssembly.isFoot(world.getBlockState(next)))pending.add(next);}
        }
        GearTopology.Result topology=GearTopology.validate(height,plan.kind,plan.offsets);if(!topology.isValid())throw new IllegalArgumentException(topology.error());for(BlockPos column:arm.columns()){BlockPos contact=arm.getPos().add(column).offset(arm.mountDirection(),height);if(!plan.states.containsKey(contact))throw new IllegalArgumentException("Every joined column needs a connected landing foot");}
        Set<BlockPos> sources=new HashSet<>();for(Foot foot:plan.feet)sources.add(foot.original.pos);for(Foot foot:plan.feet){requireLoaded(world,foot.target);if(!sources.contains(foot.target)&&!world.isAirBlock(foot.target))throw new IllegalArgumentException("Clear every landing construction cell before attaching the group");BlockPos above=foot.target.offset(arm.mountDirection().getOpposite());requireLoaded(world,above);if(GearAssembly.isExtender(world.getBlockState(above))&&!arm.members().contains(above))throw new IllegalArgumentException("The landing group touches another arm assembly");VehicleService.permission(actor,foot.target);}
        if(block instanceof LargeGearBlock){for(Foot foot:plan.feet){for(BlockPos sourceCell:block.componentCells(world,block.componentRoot(world,foot.original.pos,foot.original.state)))if(!sources.contains(sourceCell))throw new IllegalArgumentException("Every Large Wheel cell must share the socket plane");int part=foot.state.getValue(LargeGearBlock.PART);BlockPos anchor=foot.target.subtract(ArmFootComponentFrame.offset(arm.mountDirection(),foot.state.getValue(GearBlock.FACING),part&1,part>>1));for(int n=0;n<4;n++){IBlockState member=plan.states.get(anchor.add(ArmFootComponentFrame.offset(arm.mountDirection(),foot.state.getValue(GearBlock.FACING),n&1,n>>1)));if(member==null||member.getBlock()!=block||member.getValue(LargeGearBlock.PART)!=n||member.getValue(GearBlock.FACING)!=foot.state.getValue(GearBlock.FACING))throw new IllegalArgumentException("The construction endpoint cannot retain the complete Large Wheel");}}for(BlockPos column:arm.columns()){IBlockState wheel=plan.states.get(arm.getPos().add(column).offset(arm.mountDirection(),height));if(wheel.getValue(LargeGearBlock.PART)!=0||wheel.getValue(GearBlock.FACING)!=world.getBlockState(arm.getPos().add(column)).getValue(GearBlock.FACING))throw new IllegalArgumentException("Align the Large Wheel anchors with their Large arms");}}
        List<VehicleStructure.Cell> originals=new ArrayList<>();for(Foot foot:plan.feet)originals.add(foot.original);if(!GearGeometry.landingClear(arm,arm.landingBoxes(plan.kind,plan.facing,plan.offsets,plan.states),originals))throw new IllegalArgumentException("The attached landing group would collide; native feet retained");return plan;
    }
    private static void requireLoaded(WorldServer world,BlockPos at){if(!GearAssembly.loaded(world,at))throw new IllegalArgumentException("Load the active landing foot group and its boundary first");}
    private ArmLandingPlan(){}
}
