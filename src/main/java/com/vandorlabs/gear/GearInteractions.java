package com.vandorlabs.gear;

import com.vandorlabs.gear.core.GearTopology;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Pick moving parts at their posed positions while retaining their source identity. */
public final class GearInteractions {
    public static RayTraceResult trace(World world,Vec3d start,Vec3d end){
        RayTraceResult nearest=null;double distance=start.squareDistanceTo(end);
        for(GearTileEntity root:GearGeometry.roots(world)){
            for(AxisAlignedBB box:GearGeometry.looseWheelBoxes(root)){
                RayTraceResult hit=box.calculateIntercept(start,end);
                if(hit!=null&&start.squareDistanceTo(hit.hitVec)<distance){nearest=new RayTraceResult(hit.hitVec,hit.sideHit,root.getPos());distance=start.squareDistanceTo(hit.hitVec);}
            }
            if(root.isInvalid()||!root.isOperational()||root.motionBlocked()||!world.isBlockLoaded(root.getPos())||world.getTileEntity(root.getPos())!=root)continue;
            if(!root.getRenderBoundingBox().intersects(new AxisAlignedBB(start.x,start.y,start.z,end.x,end.y,end.z).grow(.001)))continue;
            java.util.List<AxisAlignedBB> boxes=root.collisionBoxes();int perFoot=root.assembly().kind()==GearTopology.FootKind.SKI?2:1,feetStart=root.isLandingAssembly()?boxes.size()-root.assembly().feet().size()*perFoot:boxes.size();
            for(int i=0;i<boxes.size();i++){
                if(boxes.get(i).getAverageEdgeLength()==0)continue;RayTraceResult hit=boxes.get(i).calculateIntercept(start,end);if(hit==null||start.squareDistanceTo(hit.hitVec)>distance)continue;
                BlockPos source=root.getPos();if(i>=feetStart){GearTopology.Cell foot=root.assembly().feet().get((i-feetStart)/perFoot);source=root.localCell(foot);}
                else if(root.fullyDeployed()){
                    // Vanilla ItemBlock placement offsets the selected source cell by sideHit.
                    // Pick the real arm cell just inside the face, including stacked tip sections.
                    BlockPos at=new BlockPos(hit.hitVec.x-hit.sideHit.getFrontOffsetX()*1e-6,hit.hitVec.y-hit.sideHit.getFrontOffsetY()*1e-6,hit.hitVec.z-hit.sideHit.getFrontOffsetZ()*1e-6);
                    if(root.members().contains(at))source=at;
                }
                nearest=new RayTraceResult(hit.hitVec,hit.sideHit,source);distance=start.squareDistanceTo(hit.hitVec);
            }
        }
        RayTraceResult block=world.rayTraceBlocks(start,end,false,true,false);
        return nearest!=null&&(block==null||start.squareDistanceTo(block.hitVec)+.00001>=distance)?nearest:null;
    }
    /** Store a posed attachment at its logical rest cell, preserving the stationary integral stroke. */
    public static boolean attachTrack(EntityPlayerMP player,GearTileEntity root,RayTraceResult hit){
        net.minecraft.item.ItemStack held=player.getHeldItemMainhand();World world=root.getWorld();
        try{
            if(world==null||world.isRemote||player.world!=world||player.isDead||player.isSpectator()||root.isInvalid()||world.getTileEntity(root.getPos())!=root)throw new IllegalArgumentException("The arm is no longer available");for(BlockPos member:root.members())com.vandorlabs.vehicle.VehicleService.permission(player,member);
            boolean append=root.isExtendedTrackStroke()&&world.getBlockState(hit.getBlockPos()).getBlock() instanceof com.vandorlabs.gear.track.TrackBlock&&root.members().contains(hit.getBlockPos());
            if(root.assembly()==null||root.assembly().kind()!=GearTopology.FootKind.NONE&&!append||root.isSwingArmController()||root.isFixedMount()||root.hasRetainedPayload()||root.payload()!=null)throw new IllegalArgumentException("Attach a Track to a clear straight arm endpoint");
            if(root.progress()!=0||root.target()||Math.abs(root.activeExtension()-Math.rint(root.activeExtension()))>1e-8)throw new IllegalArgumentException("Stop the straight arm at a whole-block endpoint before attaching a Track");
            if(!(held.getItem() instanceof net.minecraft.item.ItemBlock)||!(((net.minecraft.item.ItemBlock)held.getItem()).getBlock() instanceof com.vandorlabs.gear.track.TrackBlock)||held.hasTagCompound()&&held.getTagCompound().hasKey("ArmRecovery",10))throw new IllegalArgumentException("Hold an ordinary Track item to attach it");
            net.minecraft.util.EnumFacing axis=root.mountDirection();if(append){if(hit.sideHit.getAxis()==axis.getAxis())throw new IllegalArgumentException("Extend the Track sideways in its current tread plane");}else{Vec3d tip=new Vec3d(root.getPos()).addVector(.5,.5,.5).add(new Vec3d(axis.getDirectionVec()).scale(root.assembly().columnHeight()-.5+root.activeExtension()));if(hit.sideHit!=axis||Math.abs(hit.hitVec.subtract(tip).dotProduct(new Vec3d(axis.getDirectionVec())))>.025)throw new IllegalArgumentException("Click the outward end of the extended arm to attach a Track");}
            BlockPos rest=append?hit.getBlockPos().offset(hit.sideHit):root.localCell(0,-root.assembly().columnHeight(),0),target=rest.offset(axis,(int)Math.rint(root.activeExtension()));
            for(BlockPos pos:new BlockPos[]{rest,target}){if(!GearAssembly.loaded(world,pos)||!world.isAirBlock(pos)&&!(pos.equals(target)&&root.members().contains(pos)))throw new IllegalArgumentException("Clear both the visible Track position and the arm's rest position first");com.vandorlabs.vehicle.VehicleService.permission(player,pos);}
            net.minecraft.item.ItemStack placedStack=held.copy();placedStack.setCount(1);net.minecraft.block.Block track=((net.minecraft.item.ItemBlock)held.getItem()).getBlock();int style=held.hasTagCompound()&&held.getTagCompound().getCompoundTag("ModularGearSettings").hasKey("TrackStyle",3)?held.getTagCompound().getCompoundTag("ModularGearSettings").getInteger("TrackStyle"):append?com.vandorlabs.gear.track.TrackBlock.style(world,hit.getBlockPos()).ordinal():((com.vandorlabs.gear.track.TrackBlock)track).style().ordinal();if(style<0||style>2)throw new IllegalArgumentException("Invalid Track style");net.minecraft.block.state.IBlockState state=track.getDefaultState().withProperty(GearBlock.FACING,world.getBlockState(append?hit.getBlockPos():root.getPos()).getValue(GearBlock.FACING));
            BlockPos canonical=ArmOrientation.inverse(axis,rest.subtract(root.getPos()));AxisAlignedBB nativeTrack=com.vandorlabs.gear.track.TrackGeometry.bounds(com.vandorlabs.gear.track.core.TrackTopology.Style.values()[style],state.getValue(GearBlock.FACING),com.vandorlabs.gear.track.TrackGeometry.onArm(com.vandorlabs.gear.track.TrackAppearance.SINGLE)).offset(canonical);com.vandorlabs.gear.core.GearMotion.Bounds moved=root.pose(0).transformFootBounds(new com.vandorlabs.gear.core.GearMotion.Bounds(nativeTrack.minX,nativeTrack.minY,nativeTrack.minZ,nativeTrack.maxX,nativeTrack.maxY,nativeTrack.maxZ));AxisAlignedBB physical=ArmOrientation.box(axis,new AxisAlignedBB(moved.minX,moved.minY,moved.minZ,moved.maxX,moved.maxY,moved.maxZ)).offset(root.getPos());if(!world.checkNoEntityCollision(physical,player))throw new IllegalArgumentException("An entity occupies the visible Track position");
            java.util.Map<BlockPos,net.minecraft.nbt.NBTTagCompound> originals=new java.util.LinkedHashMap<>();java.util.Map<BlockPos,net.minecraft.block.state.IBlockState> originalStates=new java.util.LinkedHashMap<>();for(BlockPos member:root.members()){TileEntity tile=world.getTileEntity(member);if(!(tile instanceof GearTileEntity)||!GearAssembly.isExtender(world.getBlockState(member))&&!(world.getBlockState(member).getBlock() instanceof com.vandorlabs.gear.track.TrackBlock))throw new IllegalArgumentException("The arm column changed; wait for it to rebuild");originals.put(member,tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));originalStates.put(member,world.getBlockState(member));}
            net.minecraftforge.common.util.BlockSnapshot snapshot=net.minecraftforge.common.util.BlockSnapshot.getBlockSnapshot(world,rest),physicalSnapshot=net.minecraftforge.common.util.BlockSnapshot.getBlockSnapshot(world,target);boolean inserted=false,spent=false;
            try{
                boolean written=world.setBlockState(rest,state,2);inserted=!world.isAirBlock(rest);if(inserted&&!player.capabilities.isCreativeMode){held.shrink(1);spent=true;}if(!written||world.getBlockState(rest)!=state)throw new IllegalArgumentException("The Track could not be placed");
                if((rest.equals(target)?net.minecraftforge.event.ForgeEventFactory.onPlayerBlockPlace(player,snapshot,axis,net.minecraft.util.EnumHand.MAIN_HAND):net.minecraftforge.event.ForgeEventFactory.onPlayerMultiBlockPlace(player,java.util.Arrays.asList(snapshot,physicalSnapshot),axis,net.minecraft.util.EnumHand.MAIN_HAND)).isCanceled())throw new IllegalArgumentException("A protection rule prevented attaching the Track");
                track.onBlockPlacedBy(world,rest,state,player,placedStack);TileEntity placed=world.getTileEntity(rest);if(placed instanceof GearTileEntity)((GearTileEntity)placed).setMountDirection(axis);if(placed instanceof com.vandorlabs.gear.track.TrackTile)((com.vandorlabs.gear.track.TrackTile)placed).style(style);
                if(!root.formTrackAtActivePose())throw new IllegalArgumentException(root.error().isEmpty()?"Track assembly could not form":root.error());
                root.showError("");player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();return true;
            }catch(RuntimeException failure){
                boolean removed=!inserted;if(inserted&&world.getBlockState(rest).getBlock()==track){world.removeTileEntity(rest);removed=world.setBlockToAir(rest)||world.isAirBlock(rest);}
                if(removed){for(java.util.Map.Entry<BlockPos,net.minecraft.nbt.NBTTagCompound> entry:originals.entrySet()){if(world.getBlockState(entry.getKey()).getBlock()!=originalStates.get(entry.getKey()).getBlock())continue;world.setBlockState(entry.getKey(),originalStates.get(entry.getKey()),2);TileEntity tile=world.getTileEntity(entry.getKey());if(tile!=null)tile.readFromNBT(entry.getValue());}if(spent)held.grow(1);}else root.showError("Track retained at its rest position; clear the placement before retrying");
                player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();if(!removed)throw new IllegalArgumentException("Track retained at its rest position; clear the placement before retrying",failure);throw failure;
            }
        }catch(RuntimeException failure){root.showError(failure.getMessage());throw failure instanceof IllegalArgumentException?(IllegalArgumentException)failure:new IllegalArgumentException("Track attachment failed: "+failure.getClass().getSimpleName(),failure);}
    }
    public static void use(EntityPlayerMP player,BlockPos source){
        if(player.isSpectator()||player.isDead)return;Vec3d start=player.getPositionEyes(1),end=start.add(Vec3d.fromPitchYaw(player.rotationPitch,player.rotationYaw).scale(player.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue()+.5));
        RayTraceResult hit=trace(player.world,start,end);if(hit==null||!hit.getBlockPos().equals(source))return;
        TileEntity raw=player.world.getTileEntity(source);GearTileEntity root=raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;if(root==null)return;
        for(BlockPos member:root.members())com.vandorlabs.vehicle.VehicleService.permission(player,member);
        if(player.getHeldItemMainhand().getItem() instanceof net.minecraft.item.ItemBlock&&((net.minecraft.item.ItemBlock)player.getHeldItemMainhand().getItem()).getBlock() instanceof com.vandorlabs.gear.track.TrackBlock){attachTrack(player,root,hit);return;}
        if(player.getHeldItemMainhand().getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_MODULAR_GEAR,player.world,root.getPos().getX(),root.getPos().getY(),root.getPos().getZ());
        else if(player.getHeldItemMainhand().isEmpty())root.target(!root.target());
    }
}
