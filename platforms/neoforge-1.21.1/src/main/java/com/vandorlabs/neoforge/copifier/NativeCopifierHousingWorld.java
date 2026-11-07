package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;

/** Server adapter. The caller separately authenticates the exact held Copifier session. */
public final class NativeCopifierHousingWorld {
    interface Target {
        net.minecraft.world.level.block.entity.BlockEntity tile();
        CompoundTag capture();
        default CompoundTag undo(){return capture();}
        default CompoundTag configuration(boolean ignore){var result=capture();if(ignore)com.vandorlabs.items.CopiedDirectionality.strip(result::remove);return result;}
        boolean apply(ServerLevel level,BlockPos pos,CompoundTag values,ServerPlayer actor);
    }
    private record Control(net.minecraft.world.level.block.entity.BlockEntity tile) implements Target {
        public CompoundTag capture(){return NativeCopifierControlEdit.capture(tile);}
        public boolean apply(ServerLevel level,BlockPos pos,CompoundTag values,ServerPlayer actor){
            if(!NativeCopifierControlEdit.applicable(tile,values))return false;
            NativeCopifierUndo.before(actor,level,pos);
            com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.batch(level,()->NativeCopifierControlEdit.apply(tile,values));return true;
        }
    }
    private record Light(com.vandorlabs.neoforge.light.NativeLightEntity tile) implements Target {
        public CompoundTag capture(){return NativeCopifierLightEdit.capture(tile.kind(),tile.copiedSettings());}
        public boolean apply(ServerLevel level,BlockPos pos,CompoundTag values,ServerPlayer actor){
            try{if(!NativeCopifierLightEdit.prepare(tile.kind(),tile.copiedSettings(),values).applicable())return false;}catch(IllegalArgumentException malformed){return false;}
            NativeCopifierUndo.before(actor,level,pos);return tile.configureCopied(values);
        }
    }
    private record Storage(com.vandorlabs.neoforge.storage.NativeStorageEntity tile) implements Target {
        private static final NativeCopifierHousingCapture.Family FAMILY=NativeCopifierHousingCapture.Family.CUBE;
        private CompoundTag saved(){return tile.copiedConfiguration();}
        public CompoundTag capture(){return NativeCopifierHousingCapture.capture(FAMILY,saved());}
        public CompoundTag undo(){return NativeCopifierHousingCapture.undo(FAMILY,saved());}
        public CompoundTag configuration(boolean ignore){return NativeCopifierHousingCapture.connected(FAMILY,saved(),ignore);}
        public boolean apply(ServerLevel level,BlockPos pos,CompoundTag values,ServerPlayer actor){
            final NativeCopifierHousingEdit.Plan plan;try{plan=NativeCopifierHousingEdit.prepare(FAMILY,saved(),values);}catch(IllegalArgumentException unsupported){return false;}
            if(!plan.applicable()||!plan.appearance().valid()||!com.vandorlabs.neoforge.NativeCopiedHousingMetadata.valid(plan.frames(),plan.channels(),plan.transmissionRange(),plan.transmittedChannels()))return false;
            NativeCopifierUndo.before(actor,level,pos);
            return tile.configureCopied(new com.vandorlabs.neoforge.storage.NativeStorageSettings(plan.housingSettings()),plan.frames(),plan.channels(),plan.transmissionRange(),plan.localRedstone(),plan.transmittedChannels());
        }
    }
    private record Housing(ProgrammableBlockEntity tile,NativeCopifierHousingCapture.Family family) implements Target {
        public CompoundTag capture(){return NativeCopifierHousingCapture.capture(family,tile.saveWithoutMetadata(tile.getLevel().registryAccess()));}
        public CompoundTag undo(){return NativeCopifierHousingCapture.undo(family,tile.saveWithoutMetadata(tile.getLevel().registryAccess()));}
        public CompoundTag configuration(boolean ignore){return NativeCopifierHousingCapture.connected(family,tile.saveWithoutMetadata(tile.getLevel().registryAccess()),ignore);}
        public boolean apply(ServerLevel level,BlockPos pos,CompoundTag values,ServerPlayer actor){return applyHousing(level,pos,this,values,actor);}
    }
    private record Glass(com.vandorlabs.neoforge.glass.NativeGlassEntity tile) implements Target {
        private com.vandorlabs.neoforge.glass.NativeGlassSettings current(){return tile.settings().resolveLegacySize(tile.getBlockState().getValue(com.vandorlabs.neoforge.glass.NativeGlassBlock.SIZE));}
        public CompoundTag capture(){return NativeCopifierGlassEdit.capture(current());}
        public boolean apply(ServerLevel level,BlockPos pos,CompoundTag values,ServerPlayer actor){
            final NativeCopifierGlassEdit.Plan plan;var before=current();try{plan=NativeCopifierGlassEdit.prepare(before,values);}catch(IllegalArgumentException malformed){return false;}
            if(!plan.applicable()||!com.vandorlabs.neoforge.glass.NativeGlassService.valid(plan.settings()))return false;
            if(before.same(plan.settings()))return true;
            NativeCopifierUndo.before(actor,level,pos);
            return tile.configure(plan.settings(),tile.getBlockState().getValue(com.vandorlabs.neoforge.glass.NativeGlassBlock.SIZE));
        }
    }
    static Target installed(ServerLevel level,BlockPos pos){
        if(!level.getServer().isSameThread()||!level.hasChunkAt(pos))return null;var tile=level.getBlockEntity(pos);
        if(tile==null||tile.isRemoved()||tile.getLevel()!=level||tile.getBlockState()!=level.getBlockState(pos))return null;
        var id=BuiltInRegistries.BLOCK.getKey(tile.getBlockState().getBlock());if(!id.getNamespace().equals("vandorlabs"))return null;
        if(tile instanceof com.vandorlabs.neoforge.light.NativeLightEntity light&&java.util.Set.of("programmable_light","programmable_light_frame","programmable_light_slab").contains(id.getPath()))return light.available()&&com.vandorlabs.neoforge.light.NativeLightService.installed(light)?new Light(light):null;
        if(tile instanceof com.vandorlabs.neoforge.storage.NativeStorageEntity storage&&id.getPath().equals("programmable_storage"))return storage.available()?new Storage(storage):null;
        if(tile instanceof com.vandorlabs.neoforge.NativeBinaryControlBlockEntity control&&control.isChannelMemberActive()||tile instanceof com.vandorlabs.neoforge.NativeWirelessRedstoneBlockEntity wireless&&wireless.isChannelMemberActive())return new Control(tile);
        if(tile instanceof com.vandorlabs.neoforge.glass.NativeGlassEntity glass&&id.getPath().equals("programmable_glass"))return glass.available()&&com.vandorlabs.neoforge.fluid.NativeFluidWorld.installed(glass)?new Glass(glass):null;
        if(tile instanceof ProgrammableBlockEntity housing)try{return new Housing(housing,NativeCopifierHousingCapture.Family.of(id.getPath()));}catch(IllegalArgumentException unsupported){return null;}
        return null;
    }
    private static Target target(ServerLevel level,BlockPos pos,ServerPlayer actor,Direction face){return target(level,pos,actor,face,true);}
    static Target target(ServerLevel level,BlockPos pos,ServerPlayer actor,Direction face,boolean requireReach){
        if(!level.getServer().isSameThread()||actor==null||face==null||!(requireReach?NativeConfigurationAccess.canEditDialog(actor,level,pos,face):actor.isAlive()&&!actor.isSpectator()&&actor.level()==level&&level.hasChunkAt(pos)&&level.mayInteract(actor,pos)&&actor.mayUseItemAt(pos,face,actor.getMainHandItem())))return null;
        return installed(level,pos);
    }
    public static CompoundTag capture(ServerLevel level,BlockPos pos,ServerPlayer actor,Direction face){
        var target=target(level,pos,actor,face);if(target==null)return null;
        try{return target.capture();}catch(IllegalArgumentException unsupported){return null;}
    }
    /** Single installed target only. Caller authenticates its held tool; connected/undo routes are separate. */
    public static boolean applySingle(ServerLevel level,BlockPos pos,ServerPlayer actor,Direction face,CompoundTag values){
        if(values==null||values.isEmpty())return false;var target=target(level,pos,actor,face);if(target==null)return false;
        return apply(level,pos,target,values,actor);
    }
    static boolean apply(ServerLevel level,BlockPos pos,Target target,CompoundTag values,ServerPlayer actor){
        if(values==null||values.isEmpty())return false;
        return target.apply(level,pos,values,actor);
    }
    private static boolean applyHousing(ServerLevel level,BlockPos pos,Housing target,CompoundTag values,ServerPlayer actor){
        var tile=target.tile;final NativeCopifierHousingEdit.Plan plan;
        try{plan=NativeCopifierHousingEdit.prepare(target.family,tile.saveWithoutMetadata(level.registryAccess()),values);}catch(IllegalArgumentException unsupported){return false;}
        if(!plan.applicable()||!plan.appearance().valid()||!com.vandorlabs.neoforge.shape.NativeWallEntity.valid(plan.frames())||!com.vandorlabs.neoforge.shape.NativeWallEntity.valid(plan.walls())||!com.vandorlabs.neoforge.shape.NativeWallEntity.validPanelGlass(plan.glass()))return false;
        NativeCopifierUndo.before(actor,level,pos);
        // All target field validation completes before the existing installed-host setters publish.
        if(tile instanceof com.vandorlabs.neoforge.shape.NativeWallEntity wall){
            if(!wall.configure(plan.appearance(),plan.walls(),plan.frames()))return false;
            if((wall.isPorthole()||wall.isGlass())&&!wall.configurePanelGlass(plan.glass(),plan.playback(),plan.walls().joinPortholes,plan.walls().portholeShape))throw new IllegalStateException("Validated copied panel refused");
        }else if(tile instanceof com.vandorlabs.neoforge.shape.ProgrammableShapeEntity shape){if(!shape.applyShape(plan.appearance()))return false;}
        else tile.apply(plan.appearance().housing());
        if(!tile.configureCopiedMetadata(plan.frames(),plan.channels(),plan.transmissionRange(),plan.localRedstone(),plan.transmittedChannels()))throw new IllegalStateException("Validated copied metadata refused");
        level.sendBlockUpdated(pos,tile.getBlockState(),tile.getBlockState(),2);return true;
    }
    private NativeCopifierHousingWorld(){}
}
