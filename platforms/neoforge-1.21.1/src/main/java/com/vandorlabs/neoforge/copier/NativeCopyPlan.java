package com.vandorlabs.neoforge.copier;

import com.vandorlabs.copier.core.CopyGeometry;
import com.vandorlabs.copier.core.CopySettings;
import com.vandorlabs.neoforge.surface.NativeSurfaceLayout;
import com.vandorlabs.neoforge.transfer.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.*;

/** Complete, immutable input/output preflight. Construction chips never create a vehicle or grant driving authority. */
final class NativeCopyPlan {
    static final NativeTransferSnapshot.Limits LIMITS=new NativeTransferSnapshot.Limits(16384,2*1024*1024,8*1024*1024,32*1024*1024);
    final NativeTransferSnapshot input,before,result,costSource;
    final NativeCopySupply supply;
    final List<NativeCopyMaterials.Need> materials;
    final NativeSurfacePrism sourceVolume,targetVolume;
    final List<BlockPos> held;
    final CopySettings settings;
    final UUID owner,actor;final boolean creative;
    private final ItemStack chip;private final BlockPos chipMount;
    private NativeCopyPlan(NativeTransferSnapshot input,NativeTransferSnapshot before,NativeTransferSnapshot result,NativeTransferSnapshot costSource,NativeCopySupply supply,List<NativeCopyMaterials.Need> materials,NativeSurfacePrism sourceVolume,NativeSurfacePrism targetVolume,List<BlockPos> held,NativeCopyControllerEntity root,ServerPlayer actor,ItemStack chip,BlockPos chipMount){this.input=input;this.before=before;this.result=result;this.costSource=costSource;this.supply=supply;this.materials=List.copyOf(materials);this.sourceVolume=sourceVolume;this.targetVolume=targetVolume;this.held=List.copyOf(held);settings=root.settings();owner=root.key();this.actor=actor.getUUID();creative=actor.isCreative();this.chip=chip.copy();this.chipMount=chipMount;}
    static NativeCopyPlan prepare(NativeCopyControllerEntity root,ServerPlayer actor) {
        if(!(root.getLevel() instanceof ServerLevel world)||!world.getServer().isSameThread())throw new IllegalArgumentException("Copy requires its physical server world");authorize(world,actor);var settings=root.settings();if(root.output()==null||!settings.chip&&root.input()==null)throw new IllegalArgumentException("Complete the paired rectangular pads first");
        var targetVolume=NativeSurfacePrism.of(root.output(),settings.height);var sourceVolume=settings.chip?null:NativeSurfacePrism.of(root.input(),settings.height);targetVolume.requireLoaded(world);if(sourceVolume!=null){sourceVolume.requireLoaded(world);if(sourceVolume.bounds().intersects(targetVolume.bounds()))throw new IllegalArgumentException("Input and output scan prisms overlap");}
        var inputCells=new ArrayList<NativeRawCell>();var source=new ArrayList<NativeRawCell>();var staged=new ArrayList<BlockEntity>();ItemStack chip=ItemStack.EMPTY;BlockPos chipMount=null;
        if(settings.chip){var reader=chipMount(world,root.getBlockPos());if(reader==null)throw new IllegalArgumentException("Attach an outward-facing Data Chip Mount containing a written Vehicle Chip");chip=reader.getItem(0).copy();chipMount=reader.getBlockPos().immutable();var design=NativeBlueprintDesign.read(chip,world.registryAccess());for(var cell:design.cells){var raw=cell.tile();if(raw!=null){raw.putInt("x",cell.offset.getX());raw.putInt("y",cell.offset.getY());raw.putInt("z",cell.offset.getZ());}source.add(new NativeRawCell(cell.offset,cell.state,raw,LIMITS.tileBytes()));}}
        else for(var pos:sourceVolume.cells()){
            permission(world,actor,pos);var state=world.getBlockState(pos);if(state.isAir()){inputCells.add(new NativeRawCell(pos,state,null,LIMITS.tileBytes()));continue;}
            if(pos.equals(root.getBlockPos())||pos.equals(root.chest()))throw new IllegalArgumentException("Keep the controller and material chest outside the input prism");
            if(state.getBlock() instanceof com.vandorlabs.neoforge.navigation.observer.NativeObserverCoreBlock)NativeCopyObservers.requireIdle(world,pos);
            if(com.vandorlabs.neoforge.gear.NativeObserverCapture.breakBlocked(actor,world,pos,state))throw new IllegalArgumentException("A protection rule prevents reading the input block");
            linked(world,sourceVolume,pos,state);var tile=world.getBlockEntity(pos);if(state.hasBlockEntity()&&tile==null)throw new IllegalArgumentException("An input tile is still loading");var cell=new NativeRawCell(pos,state,tile==null?null:tile.saveWithFullMetadata(world.registryAccess()),LIMITS.tileBytes());inputCells.add(cell);source.add(cell);
        }
        if(source.isEmpty())throw new IllegalArgumentException("The input contains no blocks");
        NativeCopyTiles.requireCompleteChests(source);
        NativeCopyObservers.validate(source);
        NativeCopyNavigationTable.requireComplete(source);
        if(sourceVolume!=null)NativeCopyNavigationTable.requireIsolated(world,NativeCopyNavigationTable.boundary(source));
        var screenInputBoundary=NativeCopyScreens.boundary(source,settings.chip);
        NativeCopyScreens.requireIsolated(world,screenInputBoundary);
        for(var pos:screenInputBoundary)permission(world,actor,pos);
        int[] sourceBounds={Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE};for(var cell:source){var pos=settings.chip?cell.pos:cell.pos.subtract(sourceVolume.min());int[] p={pos.getX(),pos.getY(),pos.getZ()};for(int axis=0;axis<3;axis++){sourceBounds[axis]=Math.min(sourceBounds[axis],p[axis]);sourceBounds[axis+3]=Math.max(sourceBounds[axis+3],p[axis]);}}
        int[] targetBounds={targetVolume.min().getX(),targetVolume.min().getY(),targetVolume.min().getZ(),targetVolume.max().getX(),targetVolume.max().getY(),targetVolume.max().getZ()};int[] shift=CopyGeometry.center(sourceBounds,targetBounds,root.output().normal.getAxis()==Direction.Axis.X?0:root.output().normal.getAxis()==Direction.Axis.Y?1:2);
        var targets=new LinkedHashMap<BlockPos,NativeRawCell>();var originals=new ArrayList<NativeRawCell>();for(var pos:targetVolume.cells()){permission(world,actor,pos);var state=world.getBlockState(pos);if(!state.isAir()||world.getBlockEntity(pos)!=null)throw new IllegalArgumentException("Output occupied at "+pos.toShortString());var empty=new NativeRawCell(pos,state,null,LIMITS.tileBytes());originals.add(empty);targets.put(pos,empty);}if(!world.getEntities(null,targetVolume.bounds()).isEmpty())throw new IllegalArgumentException("Clear players and entities from the output prism");
        var costCells=new ArrayList<NativeRawCell>();for(var cell:source){var relative=settings.chip?cell.pos:cell.pos.subtract(sourceVolume.min());var destination=targetVolume.min().offset(relative).offset(shift[0],shift[1],shift[2]);if(!targetVolume.contains(destination))throw new IllegalArgumentException("Construction design exceeds output prism");var material=NativeCopyTiles.at(cell,destination,root.key());var next=material;if(!actor.isCreative()&&!next.state.getFluidState().isEmpty())throw new IllegalArgumentException("Drain fluids before Survival copying");if(settings.increment!=0&&next.hasTile())next=NativeCopyChannels.increment(next,settings.increment);NativeCopyTiles.stage(world,next);targets.put(destination,next);costCells.add(material);staged.add(NativeCopyTiles.stage(world,material));}
        NativeCopyNavigationTable.requireComplete(costCells);
        var tableBoundary=NativeCopyNavigationTable.boundary(costCells);
        NativeCopyNavigationTable.requireIsolated(world,tableBoundary);
        for(var pos:tableBoundary)permission(world,actor,pos);
        var screenOutputBoundary=NativeCopyScreens.boundary(costCells,false);
        NativeCopyScreens.requireIsolated(world,screenOutputBoundary);
        for(var pos:screenOutputBoundary)permission(world,actor,pos);
        var materials=NativeCopyMaterials.prepare(world,costCells,staged,actor.isCreative());var supply=actor.isCreative()?null:NativeCopySupply.prepare(world,actor,root.chest(),materials);
        var held=new LinkedHashSet<BlockPos>();held.add(root.getBlockPos());held.addAll(root.output().cells);if(root.input()!=null)held.addAll(root.input().cells);held.addAll(targetVolume.cells());if(sourceVolume!=null)held.addAll(sourceVolume.cells());if(supply!=null)held.addAll(supply.positions());if(chipMount!=null)held.add(chipMount);held.addAll(tableBoundary);held.addAll(screenInputBoundary);held.addAll(screenOutputBoundary);
        return new NativeCopyPlan(sourceVolume==null?null:new NativeTransferSnapshot(inputCells,LIMITS),new NativeTransferSnapshot(originals,LIMITS),new NativeTransferSnapshot(targets.values(),LIMITS),new NativeTransferSnapshot(costCells,LIMITS),supply,materials,sourceVolume,targetVolume,List.copyOf(held),root,actor,chip,chipMount);
    }
    void validate(NativeCopyControllerEntity root,ServerPlayer player){var world=(ServerLevel)root.getLevel();authorize(world,player);if(!root.installed()||!root.key().equals(owner)||!player.getUUID().equals(actor)||player.isCreative()!=creative||!settings.equals(root.settings()))throw new IllegalArgumentException("Copy Controller settings or actor changed");targetVolume.requireLoaded(world);if(input!=null){sourceVolume.requireLoaded(world);if(!input.matches(world,NativeCopyTiles::same))throw new IllegalArgumentException("Input blocks or private contents changed");}else{var reader=chipMount(world,root.getBlockPos());if(reader==null||!reader.getBlockPos().equals(chipMount)||!ItemStack.matches(reader.getItem(0),chip))throw new IllegalArgumentException("Construction chip or mount changed");}if(!before.matches(world,NativeCopyTiles::same)||!world.getEntities(null,targetVolume.bounds()).isEmpty())throw new IllegalArgumentException("Output space changed");if(supply!=null)supply.validate(world);}
    static com.vandorlabs.neoforge.credentials.NativeCredentialReaderEntity chipMount(ServerLevel world,BlockPos root){com.vandorlabs.neoforge.credentials.NativeCredentialReaderEntity found=null;for(Direction side:Direction.values()){var at=root.relative(side);if(world.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4)==null)continue;if(world.getBlockEntity(at) instanceof com.vandorlabs.neoforge.credentials.NativeCredentialReaderEntity reader&&reader.chip()&&!reader.inert()&&reader.getBlockState().getValue(com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock.FACING)==side){if(found!=null)throw new IllegalArgumentException("Attach only one construction Data Chip Mount");found=reader;}}return found;}
    private static void linked(ServerLevel world,NativeSurfacePrism volume,BlockPos pos,net.minecraft.world.level.block.state.BlockState state){if(state.getBlock() instanceof DoorBlock||state.getBlock() instanceof DoublePlantBlock){var other=pos.relative(state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER?Direction.UP:Direction.DOWN);if(!volume.contains(other)||world.getBlockState(other).getBlock()!=state.getBlock())throw new IllegalArgumentException("Capture both halves of the input block");}if(state.getBlock() instanceof BedBlock){var direction=state.getValue(BedBlock.FACING);var other=pos.relative(state.getValue(BedBlock.PART)==BedPart.FOOT?direction:direction.getOpposite());if(!volume.contains(other)||world.getBlockState(other).getBlock()!=state.getBlock())throw new IllegalArgumentException("Capture both bed halves");}}
    ItemStack chip(){return chip.copy();}
    BlockPos chipPosition(){return chipMount;}
    private static void authorize(ServerLevel world,ServerPlayer actor){if(!world.getServer().isSameThread()||actor==null||actor.serverLevel()!=world||!actor.isAlive()||actor.isSpectator())throw new IllegalArgumentException("Copy actor unavailable");}
    static void permission(ServerLevel world,ServerPlayer actor,BlockPos pos){if(!world.mayInteract(actor,pos)||!actor.mayUseItemAt(pos,Direction.UP,actor.getMainHandItem())||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(world,pos))throw new IllegalArgumentException("Copy permission or ownership refused");}
}
