package com.vandorlabs.gear;

import com.vandorlabs.gear.core.GearTopology;
import net.minecraft.block.state.IBlockState;
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
            if(root.isInvalid()||!root.isOperational()||!world.isBlockLoaded(root.getPos())||world.getTileEntity(root.getPos())!=root)continue;
            if(!root.getRenderBoundingBox().intersects(new AxisAlignedBB(start.x,start.y,start.z,end.x,end.y,end.z).grow(.001)))continue;
            java.util.List<AxisAlignedBB> boxes=root.collisionBoxes();int nativeCount=boxes.size()-(root.payload()==null?0:root.payload().collisionBoxCount(root));int perFoot=root.assembly().kind()==GearTopology.FootKind.SKI?2:1,feetStart=root.isLandingAssembly()?boxes.size()-root.assembly().feet().size()*perFoot:boxes.size();
            for(int i=0;i<nativeCount;i++){
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
            RayTraceResult payload=ArmPayloadHarvest.trace(root,start,end);if(payload!=null&&start.squareDistanceTo(payload.hitVec)<=distance){nearest=payload;distance=start.squareDistanceTo(payload.hitVec);}
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
    /** Ordinary native item placement at a stopped posed tip. Motion subsequently uses the existing arm journal. */
    public static boolean attachLandingFoot(EntityPlayerMP player,GearTileEntity root,RayTraceResult hit){
        World world=root.getWorld();net.minecraft.item.ItemStack held=player.getHeldItemMainhand();
        if(world==null||world.isRemote||player.world!=world||player.isDead||player.isSpectator()||root.isInvalid()||world.getTileEntity(root.getPos())!=root)throw new IllegalArgumentException("The arm is no longer available");
        if(root.isLandingAssembly())return appendLandingFoot(player,root,hit);
        if(root.assembly()==null||root.assembly().kind()!=GearTopology.FootKind.NONE||!root.isSwingArmController()||root.columns().size()!=1||root.payload()!=null||root.hasRetainedPayload()||root.hasFailedArmSnapshot())throw new IllegalArgumentException("Attach landing gear to one clear Rotating Arm tip");
        if(root.progress()!=0||root.target()||ArmTransactions.locked(world,root.getPos()))throw new IllegalArgumentException("Stop the Rotating Arm at its active endpoint before attaching landing gear");
        if(!(held.getItem() instanceof net.minecraft.item.ItemBlock))throw new IllegalArgumentException("Hold a landing foot item");net.minecraft.block.Block block=((net.minecraft.item.ItemBlock)held.getItem()).getBlock();if(!ArmLooseGearData.foot(block.getDefaultState())||held.hasTagCompound()&&(held.getTagCompound().hasKey("ArmRecovery",10)||held.getTagCompound().hasKey("BlockEntityTag",10)))throw new IllegalArgumentException("Use an ordinary Plate, Ski or matching Wheel item");if(((GearBlock)block).kind()==GearBlock.Kind.WHEEL&&((GearBlock)block).sizeClass()!=root.armSize())throw new IllegalArgumentException("Wheel and mounting arm sizes must match");
        ArmPayload.Endpoint endpoint=ArmPayload.endpoint(root,0);if(endpoint==null)throw new IllegalArgumentException("Align the arm to a whole-block endpoint before attaching landing gear");
        java.util.List<AxisAlignedBB> boxes=root.collisionBoxes();AxisAlignedBB socket=boxes.get(boxes.size()-1);if(!socket.grow(.025).contains(hit.hitVec))throw new IllegalArgumentException("Click the visible end socket to attach landing gear");
        com.vandorlabs.gear.core.GearMotion.Pose pose=root.pose(0);com.vandorlabs.gear.core.GearMotion.Vec a=pose.transformFootVertex(0,0,0),b=pose.transformFootVertex(0,1,0);Vec3d normal=ArmOrientation.vector(root.mountDirection(),a.x-b.x,a.y-b.y,a.z-b.z);net.minecraft.util.EnumFacing mount=net.minecraft.util.EnumFacing.getFacingFromVector((float)normal.x,(float)normal.y,(float)normal.z);if(hit.sideHit!=mount)throw new IllegalArgumentException("Click the outward face of the end socket");
        BlockPos target=endpoint.placed(root.localCell(0,-root.assembly().columnHeight(),0));if(!GearAssembly.loaded(world,target)||!world.getWorldBorder().contains(target)||!world.isAirBlock(target))throw new IllegalArgumentException("Clear the active landing gear cell first");for(BlockPos member:root.members())com.vandorlabs.vehicle.VehicleService.permission(player,member);com.vandorlabs.vehicle.VehicleService.permission(player,target);
        net.minecraft.util.EnumFacing facing=net.minecraft.util.EnumFacing.NORTH;
        if(block instanceof LargeGearBlock){net.minecraft.nbt.NBTTagCompound template=new net.minecraft.nbt.NBTTagCompound();template.setString("id",TileEntity.getKey(GearTileEntity.class).toString());template.setString("ArmMountDirection",root.mountDirection().name());ArmFootComponentFrame.write(template,root.mountDirection());IBlockState canonical=block.getDefaultState().withProperty(GearBlock.FACING,world.getBlockState(root.getPos()).getValue(GearBlock.FACING));IBlockState projected=endpoint.placedState(new com.vandorlabs.vehicle.VehicleStructure.Cell(root.localCell(0,-root.assembly().columnHeight(),0),canonical,template));if(projected==null)throw new IllegalArgumentException("The Large Wheel cannot retain its complete socket frame");facing=projected.getValue(GearBlock.FACING);}
        net.minecraft.util.EnumActionResult result;try(ArmFootComponentFrame.Placement placement=ArmFootComponentFrame.placing(mount,facing)){result=held.onItemUse(player,world,target,net.minecraft.util.EnumHand.MAIN_HAND,mount,.5F,.5F,.5F);}if(result!=net.minecraft.util.EnumActionResult.SUCCESS||world.getBlockState(target).getBlock()!=block)return false;
        TileEntity placed=world.getTileEntity(target);if(!(placed instanceof GearTileEntity))throw new IllegalArgumentException("Landing foot data is still loading");((GearTileEntity)placed).setMountDirection(mount);root.operator(player);player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();return true;
    }
    /** Add an ordinary item to the existing logical mesh; its renderer retains the stopped pose. */
    private static boolean appendLandingFoot(EntityPlayerMP player,GearTileEntity root,RayTraceResult hit){
        World world=root.getWorld();net.minecraft.item.ItemStack held=player.getHeldItemMainhand();
        if(!root.isSwingArmController()||root.progress()!=0||root.target()||root.payload()!=null||root.hasRetainedPayload()||root.hasFailedArmSnapshot()||ArmTransactions.locked(world,root.getPos()))throw new IllegalArgumentException("Stop the Rotating Arm before extending its landing gear");
        if(!root.members().contains(hit.getBlockPos())||!GearAssembly.isFoot(world.getBlockState(hit.getBlockPos())))throw new IllegalArgumentException("Click the side of the existing landing gear");
        if(!(held.getItem() instanceof net.minecraft.item.ItemBlock))throw new IllegalArgumentException("Hold a matching landing foot item");
        GearBlock block=world.getBlockState(hit.getBlockPos()).getBlock() instanceof GearBlock?(GearBlock)world.getBlockState(hit.getBlockPos()).getBlock():null;
        if(block==null||((net.minecraft.item.ItemBlock)held.getItem()).getBlock()!=block||block.kind()==GearBlock.Kind.TRACK||block.sizeClass()==2&&block.kind()==GearBlock.Kind.WHEEL||held.hasTagCompound()&&(held.getTagCompound().hasKey("ArmRecovery",10)||held.getTagCompound().hasKey("BlockEntityTag",10)))throw new IllegalArgumentException("Use a matching ordinary Plate, Ski or regular Wheel item");
        BlockPos normal=ArmOrientation.inverse(root.mountDirection(),new BlockPos(hit.sideHit.getDirectionVec()));
        GearTopology.Cell step=com.vandorlabs.gear.core.GearFootPlacement.neighbor(root.pose(0),normal.getX(),normal.getY(),normal.getZ());
        BlockPos offset=ArmOrientation.vector(root.mountDirection(),new BlockPos(step.x,0,step.z)),target=hit.getBlockPos().add(offset);
        net.minecraft.util.EnumFacing side=net.minecraft.util.EnumFacing.getFacingFromVector(offset.getX(),offset.getY(),offset.getZ());
        if(!GearAssembly.loaded(world,target)||!world.getWorldBorder().contains(target)||!world.isAirBlock(target))throw new IllegalArgumentException("Clear the landing gear's rest cell before extending it");
        for(BlockPos member:root.members())com.vandorlabs.vehicle.VehicleService.permission(player,member);
        com.vandorlabs.vehicle.VehicleService.permission(player,target);
        BlockPos local=ArmOrientation.inverse(root.mountDirection(),target.subtract(root.getPos()));
        java.util.List<GearTopology.Cell> feet=new java.util.ArrayList<>(root.assembly().feet());feet.add(new GearTopology.Cell(local.getX(),local.getY(),local.getZ()));
        GearTopology.Result topology=GearTopology.validate(root.assembly().columnHeight(),root.assembly().kind(),feet);
        if(!topology.isValid())throw new IllegalArgumentException(topology.error());
        IBlockState state=world.getBlockState(hit.getBlockPos()).withProperty(GearBlock.ASSEMBLED,false);
        java.util.Map<BlockPos,IBlockState> preview=java.util.Collections.singletonMap(target,state);
        for(AxisAlignedBB bounds:root.landingBoxes(root.assembly().kind(),root.footFacing(),feet,preview)){
            AxisAlignedBB inside=bounds.shrink(.0001);if(inside.getAverageEdgeLength()<=0)continue;
            for(BlockPos cell:BlockPos.getAllInBox(new BlockPos(inside.minX,inside.minY,inside.minZ),new BlockPos(inside.maxX,inside.maxY,inside.maxZ))){if(!GearAssembly.loaded(world,cell))throw new IllegalArgumentException("Load the visible landing gear area first");com.vandorlabs.vehicle.VehicleService.permission(player,cell);}
            if(!GearGeometry.foreign(world,root,inside).isEmpty()||!world.checkNoEntityCollision(inside,player))throw new IllegalArgumentException("Clear the visible landing gear position first");
        }
        net.minecraftforge.common.util.BlockSnapshot before=net.minecraftforge.common.util.BlockSnapshot.getBlockSnapshot(world,target);
        net.minecraft.nbt.NBTTagCompound saved=root.writeToNBT(new net.minecraft.nbt.NBTTagCompound());int count=held.getCount();boolean placed=false;
        try{
            net.minecraft.util.EnumActionResult result;
            try(ArmFootComponentFrame.Placement placement=ArmFootComponentFrame.placing(root.mountDirection())){result=held.onItemUse(player,world,target,net.minecraft.util.EnumHand.MAIN_HAND,side,.5F,.5F,.5F);}
            placed=world.getBlockState(target).getBlock()==block;
            if(result!=net.minecraft.util.EnumActionResult.SUCCESS||!placed)return false;
            TileEntity tile=world.getTileEntity(target);if(!(tile instanceof GearTileEntity))throw new IllegalArgumentException("Landing gear data is still loading");
            ((GearTileEntity)tile).setMountDirection(root.mountDirection());root.operator(player);
            if(!root.formLandingAtActivePose())throw new IllegalArgumentException(root.error().isEmpty()?"The landing gear could not join":root.error());
            player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();return true;
        }catch(RuntimeException failure){
            if(placed&&world.getBlockState(target).getBlock()==block&&before.restore(true,false)){
                root.readFromNBT(saved);root.operator(player);root.sync();if(!player.capabilities.isCreativeMode&&held.getCount()==count-1)held.grow(1);
            }
            player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();throw failure;
        }
    }
    public static void use(EntityPlayerMP player,BlockPos source){
        if(player.isSpectator()||player.isDead)return;Vec3d start=player.getPositionEyes(1),end=start.add(Vec3d.fromPitchYaw(player.rotationPitch,player.rotationYaw).scale(player.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue()+.5));
        RayTraceResult hit=trace(player.world,start,end);if(hit==null||!hit.getBlockPos().equals(source))return;
        TileEntity raw=player.world.getTileEntity(source);GearTileEntity root=raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;if(root==null)return;
        for(BlockPos member:root.members())com.vandorlabs.vehicle.VehicleService.permission(player,member);
        if(player.getHeldItemMainhand().getItem() instanceof net.minecraft.item.ItemBlock&&((net.minecraft.item.ItemBlock)player.getHeldItemMainhand().getItem()).getBlock() instanceof com.vandorlabs.gear.track.TrackBlock){attachTrack(player,root,hit);return;}
        if(player.getHeldItemMainhand().getItem() instanceof net.minecraft.item.ItemBlock&&ArmLooseGearData.foot(((net.minecraft.item.ItemBlock)player.getHeldItemMainhand().getItem()).getBlock().getDefaultState())){try{attachLandingFoot(player,root,hit);}catch(IllegalArgumentException denied){root.showError(denied.getMessage());throw denied;}return;}
        if(player.getHeldItemMainhand().getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_MODULAR_GEAR,player.world,root.getPos().getX(),root.getPos().getY(),root.getPos().getZ());
        else if(player.getHeldItemMainhand().isEmpty()){root.operator(player);root.target(!root.target());}
    }
}
