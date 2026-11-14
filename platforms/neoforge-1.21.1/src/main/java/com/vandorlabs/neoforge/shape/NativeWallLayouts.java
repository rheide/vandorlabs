package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;

/** Cosmetic client groups. Descriptors live only on loaded tiles and never retain tiles or levels. */
public final class NativeWallLayouts {
    static final class Group implements com.vandorlabs.neoforge.NativeJoinDependencies.Group {
        final NativePanelGroups.Mode mode;
        Map<Long,NativePanelLayout> layouts;
        final boolean capped;
        boolean valid=true,queued;
        public int retainedCells(){return layouts.size();}
        public void invalidateDependency(){valid=false;layouts=Map.of();}
        Group(NativePanelGroups.Mode mode,Map<Long,NativePanelLayout> layouts,boolean capped){this.mode=mode;this.layouts=layouts;this.capped=capped;}
    }
    /** Local invalidation is safe during NBT loading and chunk unload; all world work stays deferred. */
    public static void invalidate(NativeWallEntity host){com.vandorlabs.neoforge.NativeJoinDependencies.changed(host.getLevel(),host.getBlockPos());if(host.textureGroup!=null)host.textureGroup.valid=false;if(host.apertureGroup!=null)host.apertureGroup.valid=false;if(host.glassGroup!=null)host.glassGroup.valid=false;}
    public static void invalidateAt(Level level,BlockPos pos){if(NativeFluidWorld.loaded(level,pos)&&level.getBlockEntity(pos) instanceof NativeWallEntity host)invalidate(host);}
    private static Group group(NativeWallEntity host,NativePanelGroups.Mode mode){return mode==NativePanelGroups.Mode.MATERIAL?host.textureGroup:host.apertureGroup;}
    private static void group(NativeWallEntity host,NativePanelGroups.Mode mode,Group group){if(mode==NativePanelGroups.Mode.MATERIAL)host.textureGroup=group;else host.apertureGroup=group;}
    static NativePanelLayout layout(NativeWallEntity host){return host.getBlockState().getBlock() instanceof NativeDiagonalWallBlock?NativeDiagonalLayouts.layout(host):query(host,NativePanelGroups.Mode.MATERIAL);}
    static NativePortholeLayout aperture(NativeWallEntity host){
        if(host.getBlockState().getBlock() instanceof NativeDiagonalWallBlock)return NativeDiagonalLayouts.aperture(host);
        int shape=host.wallSettings().portholeShape,border=host.getBlockState().getBlock() instanceof NativeFlatWallBlock block&&block.isFullDepth()?1:0;
        NativePanelLayout pane=query(host,NativePanelGroups.Mode.APERTURE);
        Level level=host.getLevel();
        if(level==null||!level.isClientSide||!host.isPorthole()||!host.wallSettings().joinPortholes)return NativePortholeLayout.unit(shape,border);
        Direction right=host.getBlockState().getValue(NativeFlatWallBlock.FACING).getClockWise();
        int mask=NativePanelGroups.neighborMask(host.getBlockPos(),right,pos->compatible(host,pos,NativePanelGroups.Mode.APERTURE)!=null&&com.vandorlabs.neoforge.NativeTextureJoins.edge(host,host.getBlockPos(),pos));
        return NativePortholeLayout.contacts(pane,shape,mask,border);
    }
    private static NativePanelLayout query(NativeWallEntity host,NativePanelGroups.Mode mode){
        Level level=host.getLevel();if(level==null||!level.isClientSide)return NativePanelLayout.UNIT;
        Group old=group(host,mode);
        if(old!=null&&old.valid&&!membershipValid(host,old))old.valid=false;
        if(old!=null&&!old.valid&&!old.queued){old.queued=true;for(long packed:old.layouts.keySet()){BlockPos pos=BlockPos.of(packed);if(NativeFluidWorld.loaded(level,pos)&&level.getBlockEntity(pos) instanceof NativeWallEntity member)NativeFluidWorld.queue(member);}}
        if(mode==NativePanelGroups.Mode.MATERIAL?host.settings().layout==0||host.textureJoin().direction==0:!host.isPorthole()||!host.wallSettings().joinPortholes){group(host,mode,null);return NativePanelLayout.UNIT;}
        if(old!=null&&old.valid)return old.layouts.getOrDefault(host.getBlockPos().asLong(),NativePanelLayout.UNIT);
        return capture(host,mode);
    }
    private static boolean membershipValid(NativeWallEntity root,Group group){
        Direction right=root.getBlockState().getValue(NativeFlatWallBlock.FACING).getClockWise();BlockPos pos=root.getBlockPos();
        for(Direction direction:new Direction[]{right,right.getOpposite(),Direction.UP,Direction.DOWN}){
            BlockPos next=pos.relative(direction);boolean contains=group.layouts.containsKey(next.asLong()),compatible=compatible(root,next,group.mode)!=null&&com.vandorlabs.neoforge.NativeTextureJoins.edge(root,pos,next);
            if(contains&&!compatible||!contains&&compatible&&!group.capped)return false;
        }
        return true;
    }
    private static NativeWallEntity compatible(NativeWallEntity root,BlockPos pos,NativePanelGroups.Mode mode){
        Level level=root.getLevel();if(!NativeFluidWorld.loaded(level,pos))return null;
        var state=level.getBlockState(pos);var base=root.getBlockState();
        if(!(base.getBlock() instanceof NativeFlatWallBlock)||state.getBlock()!=base.getBlock()||!(level.getBlockEntity(pos) instanceof NativeWallEntity other)||other.isRemoved())return null;
        if(other.textureJoin().direction==0)return null;
        if(state.getValue(NativeFlatWallBlock.FACING)!=base.getValue(NativeFlatWallBlock.FACING)||!state.getValue(NativeFlatWallBlock.DEPTH).equals(base.getValue(NativeFlatWallBlock.DEPTH)))return null;
        return mode.sameSettings(root.settings().mainTexture,root.settings().layout,root.wallSettings().joinPortholes,root.wallSettings().portholeShape,other.settings().mainTexture,other.settings().layout,other.wallSettings().joinPortholes,other.wallSettings().portholeShape)?other:null;
    }
    private static NativePanelLayout capture(NativeWallEntity root,NativePanelGroups.Mode mode){
        Direction right=root.getBlockState().getValue(NativeFlatWallBlock.FACING).getClockWise();
        var dependencies=new com.vandorlabs.neoforge.NativeJoinDependencies.Bounds();dependencies.include(root.getBlockPos());
        var captured=NativePanelGroups.capture(root.getBlockPos(),right,root,pos->{dependencies.include(pos);return compatible(root,pos,mode);},(from,next)->com.vandorlabs.neoforge.NativeTextureJoins.edge(root,from,next));
        Group group=new Group(mode,captured.layouts(),captured.capped());boolean shared=com.vandorlabs.neoforge.NativeTextureJoins.shareable(root,captured.members().values());boolean admitted=com.vandorlabs.neoforge.NativeJoinDependencies.observe(shared?captured.members().values():java.util.List.of(root),mode.ordinal(),dependencies,group);if(!admitted){group(root,mode,group);return NativePanelLayout.UNIT;}
        for(var entry:captured.members().entrySet()){
            NativeWallEntity member=entry.getValue();if(shared||member==root){group(member,mode,group);if(member!=root&&group.valid)NativeFluidWorld.queue(member);}

        }
        return captured.layouts().get(root.getBlockPos().asLong());
    }
    public static boolean joined(NativeWallEntity root,BlockPos next){
        return root.getBlockState().getBlock() instanceof NativeDiagonalWallBlock?NativeDiagonalLayouts.joined(root,next):compatible(root,next,NativePanelGroups.Mode.MATERIAL)!=null;
    }
    private NativeWallLayouts(){}
}
