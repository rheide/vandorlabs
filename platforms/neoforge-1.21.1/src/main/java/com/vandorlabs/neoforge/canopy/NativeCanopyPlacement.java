package com.vandorlabs.neoforge.canopy;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;

/** Prepares the actual merged owner before writing a cell or consuming its configured item. */
public final class NativeCanopyPlacement {
    private NativeCanopyPlacement(){}
    public record Prepared(BlockState state,CompoundTag snapshot,List<BlockPos> cells,List<BlockPos> admission,CompoundTag initialSnapshot,NativeCanopySettings settings){
        public Prepared(BlockState state,CompoundTag snapshot,List<BlockPos> cells,List<BlockPos> admission){this(state,snapshot,cells,admission,snapshot,NativeCanopySettings.read(snapshot));}
        public Prepared{snapshot=snapshot.copy();initialSnapshot=initialSnapshot.copy();cells=cells.stream().map(BlockPos::immutable).toList();admission=admission.stream().map(BlockPos::immutable).toList();}
        @Override public CompoundTag snapshot(){return snapshot.copy();}
        @Override public CompoundTag initialSnapshot(){return initialSnapshot.copy();}
    }
    /** plan.mount has already applied clicked-face and existing-owner precedence. */
    public static Prepared prepare(NativeCanopyBlock block,BlockPos pos,BlockState state,CompoundTag generic,CompoundTag outer,int mount,HolderLookup.Provider registries){
        pos=pos.immutable();if(outer!=null&&outer.contains(NativeCanopyEntity.RECOVERY))throw new IllegalArgumentException("Retained canopy item owner");
        if(mount<0||mount>3)throw new IllegalArgumentException("Canopy placement mount");
        var candidate=(NativeCanopyEntity)block.newBlockEntity(pos,state);var merged=candidate.saveWithoutMetadata(registries);if(generic!=null)merged.merge(generic.copy());merged.putInt("x",pos.getX());merged.putInt("y",pos.getY());merged.putInt("z",pos.getZ());
        candidate.loadWithComponents(merged,registries);if(candidate.inert())throw new IllegalArgumentException("Unsupported canopy item owner");
        var configured=outer==null?new CompoundTag():outer.copy();configured.putInt("CanopyMount",mount);var settings=NativeCanopySettings.read(configured);
        // Keep the established preflight in addition to all cells the merged callback will install.
        var fresh=new CompoundTag();fresh.putBoolean("CenteredPlacement",com.vandorlabs.persistence.CanopySettingsData.supports(block.kind())||block.kind().startsWith("space_visor_"));fresh.putBoolean("HollowPlacement",com.vandorlabs.persistence.CanopySettingsData.supports(block.kind()));
        var defaults=NativeCanopyFootprint.cells(block.kind(),pos,state.getValue(NativeCanopyBlock.FACING),settings,NativeCanopyLinks.read(fresh,pos));
        candidate.placementChoices(settings);var cells=candidate.links().footprint();var checked=new LinkedHashSet<BlockPos>(defaults);checked.addAll(cells);
        return new Prepared(state,candidate.saveWithoutMetadata(registries),cells,new ArrayList<>(checked),merged,settings);
    }
}
