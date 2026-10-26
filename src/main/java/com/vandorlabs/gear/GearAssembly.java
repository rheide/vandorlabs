package com.vandorlabs.gear;

import com.vandorlabs.gear.core.GearTopology;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Bounded, server-only discovery. No method requests a chunk to be loaded. */
public final class GearAssembly {
    private static final EnumFacing[] HORIZONTAL = {
            EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST
    };

    private GearAssembly() { }

    public static final class Scan {
        public final GearTopology.Assembly assembly;
        public final EnumFacing footFacing;
        public final String error;
        public final boolean temporary;
        public final java.util.List<BlockPos> columns;
        public final BlockPos primary;

        private Scan(GearTopology.Assembly assembly, EnumFacing facing, String error, boolean temporary) {
            this.assembly = assembly;
            this.footFacing = facing;
            this.error = error;
            this.temporary = temporary;
            this.columns=java.util.Collections.emptyList();this.primary=null;
        }

        private Scan(GearTopology.Assembly assembly,EnumFacing facing,java.util.List<BlockPos> columns){
            this.assembly=assembly;this.footFacing=facing;this.columns=java.util.Collections.unmodifiableList(columns);this.primary=columns.get(0);this.error="";this.temporary=false;
        }

        public boolean valid() { return assembly != null; }
    }

    private static Scan fail(String message) { return new Scan(null, EnumFacing.NORTH, message, false); }

    private static Scan unavailable(String message) { return new Scan(null, EnumFacing.NORTH, message, true); }

    public static boolean loaded(World world, BlockPos pos) {
        return pos.getY() >= 0 && pos.getY() < world.getHeight() && world.isBlockLoaded(pos, false);
    }

    public static boolean isExtender(IBlockState state) {
        return state.getBlock() instanceof GearBlock && (((GearBlock)state.getBlock()).kind()==GearBlock.Kind.EXTENDER || ((GearBlock)state.getBlock()).kind()==GearBlock.Kind.MOUNT || isSwingArm(state));
    }

    public static boolean isSwingArm(IBlockState state) {
        return state.getBlock() instanceof GearBlock && ((GearBlock)state.getBlock()).kind()==GearBlock.Kind.SWING_ARM;
    }

    public static boolean isFoot(IBlockState state) {
        return state.getBlock() instanceof GearBlock && !isExtender(state);
    }

    /** Returns null for an unloaded or oversized column. */
    @Nullable
    public static BlockPos topOfColumn(World world, BlockPos start) {
        if (!loaded(world, start) || !isExtender(world.getBlockState(start))) return null;
        BlockPos top = ((GearBlock)world.getBlockState(start).getBlock()).componentRoot(start,world.getBlockState(start));
        int count = 1;
        while (true) {
            BlockPos above = top.offset(mountDirection(world,top).getOpposite());
            if (!loaded(world, above)) return null;
            if (!isExtender(world.getBlockState(above)) || mountDirection(world,above)!=mountDirection(world,top)) return top;
            if (++count > GearTopology.MAX_COLUMN_HEIGHT) return null;
            top = above;
        }
    }

    /**
     * Discovers a complete horizontal connected component. Encountering a
     * 65th foot, an unloaded boundary, or a conflicting foot fails the WHOLE
     * scan before any world state or ownership is changed.
     */
    public static Scan scan(World world, BlockPos requested) {
        TileEntity raw=loaded(world,requested)?world.getTileEntity(requested):null;
        if(raw instanceof GearTileEntity){GearTileEntity member=(GearTileEntity)raw;
            if(member.owner()!=null&&!loaded(world,member.owner()))return unavailable("Load the joined controller first");
            GearTileEntity control=member.loadedController();if(control!=null&&control.joinEnabled()&&control.columns().size()>1)
                for(BlockPos at:control.members())if(!loaded(world,at))return unavailable("Load every joined arm before rebuilding");
        }
        BlockPos top=topOfColumn(world,requested);
        if(top==null)return unavailable("Load the complete strut column first.");
        IBlockState topState=world.getBlockState(top);boolean swing=isSwingArm(topState);
        EnumFacing mount=mountDirection(world,top);
        int height=0;BlockPos first=top;
        while(loaded(world,first)&&isExtender(world.getBlockState(first))&&mountDirection(world,first)==mount){
            if(world.getBlockState(first).getBlock()!=topState.getBlock())return fail("Do not mix straight extenders and swing arms in a column.");
            if(!(world.getTileEntity(first) instanceof GearTileEntity))return unavailable("An arm tile is still loading.");
            if(swing&&world.getBlockState(first).getValue(GearBlock.FACING)!=topState.getValue(GearBlock.FACING))return fail("Align connected rotating arms in the same direction.");
            if(++height>GearTopology.MAX_COLUMN_HEIGHT)return fail("A strut column may contain at most eight sections.");
            first=first.offset(mount);
        }
        if(!loaded(world,first))return unavailable("Load the feet and their boundary first.");
        IBlockState footState=world.getBlockState(first);
        if(!isFoot(footState)&&((GearBlock)topState.getBlock()).sizeClass()==2){
            // Large housings span two blocks. A surviving native Track can still
            // meet their mounting plane after the directly anchored cell is removed.
            java.util.List<BlockPos> candidates=new java.util.ArrayList<>();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)candidates.add(first.add(ArmOrientation.vector(mount,new BlockPos(x,0,z))));java.util.Collections.sort(candidates);
            for(BlockPos candidate:candidates)if(trackContact(world,top,height,candidate)){first=candidate;footState=world.getBlockState(first);break;}
        }
        if(!isFoot(footState)){
            // Arms are complete mechanisms even before a payload or landing foot is added.
            List<GearTopology.Cell> virtual=java.util.Collections.singletonList(new GearTopology.Cell(0,-height,0));
            GearTopology.Result free=GearTopology.validate(height,GearTopology.FootKind.NONE,virtual);
            try{return new Scan(free.assembly(),EnumFacing.NORTH,JoinedArms.columns(world,top,height));}catch(IllegalArgumentException denied){return fail(denied.getMessage());}
        }
        GearBlock footBlock=(GearBlock)footState.getBlock(),armBlock=(GearBlock)topState.getBlock();
        if(footBlock.kind()==GearBlock.Kind.WHEEL && footBlock.sizeClass()!=armBlock.sizeClass())return fail("Wheel and mounting arm sizes must match.");
        EnumFacing facing=footState.getValue(GearBlock.FACING);
        Set<BlockPos> feet=new HashSet<>(),columns=new HashSet<>();columns.add(top);ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(first);feet.add(first);
        while(!queue.isEmpty()){
            BlockPos foot=queue.removeFirst();
            if(!((GearBlock)footState.getBlock()).componentIntact(world,((GearBlock)footState.getBlock()).componentRoot(foot,world.getBlockState(foot))))return fail("Incomplete wheel footprint.");
            if(!(world.getTileEntity(foot) instanceof GearTileEntity))return unavailable("A foot tile is still loading.");
            BlockPos above=foot.offset(mount.getOpposite());if(!loaded(world,above))return unavailable("Load every mounting column.");
            if(isExtender(world.getBlockState(above))){
                BlockPos column=topOfColumn(world,above);if(column==null)return unavailable("Load the complete connected strut group.");
                if(!column.offset(mount,height).equals(foot)||world.getBlockState(column).getBlock()!=topState.getBlock()||mountDirection(world,column)!=mount)return fail("Connected struts must have the same type and height.");
                for(int n=0;n<height;n++){
                    BlockPos cell=column.offset(mount,n);IBlockState state=world.getBlockState(cell);
                    if(state.getBlock()!=topState.getBlock()||!(world.getTileEntity(cell) instanceof GearTileEntity))return fail("Incomplete or mixed strut column.");
                    if(!((GearBlock)state.getBlock()).componentIntact(world,cell))return fail("Incomplete arm footprint.");
                    if(swing&&state.getValue(GearBlock.FACING)!=topState.getValue(GearBlock.FACING))return fail("Align connected swing arms in the same direction.");
                }
                if(footBlock.kind()==GearBlock.Kind.WHEEL && footBlock.width()==2){
                    BlockPos anchor=column.offset(mount,height);IBlockState wheel=world.getBlockState(anchor);
                    if(wheel.getBlock()!=footBlock || !footBlock.componentRoot(anchor,wheel).equals(anchor) || wheel.getValue(GearBlock.FACING)!=topState.getValue(GearBlock.FACING))
                        return fail("Align the complete Large Wheel footprint beneath its Large arm.");
                }
                columns.add(column);
            }
            for(EnumFacing side:HORIZONTAL){BlockPos next=foot.add(ArmOrientation.vector(mount,new BlockPos(side.getDirectionVec())));if(!loaded(world,next))return unavailable("The entire foot group and boundary must be loaded.");IBlockState state=world.getBlockState(next);if(!isFoot(state))continue;
                if(footBlock.kind()==GearBlock.Kind.TRACK&&state.getBlock() instanceof com.vandorlabs.gear.track.TrackBlock){if(com.vandorlabs.gear.track.TrackBlock.style(world,next)!=com.vandorlabs.gear.track.TrackBlock.style(world,first)||state.getValue(GearBlock.FACING)!=facing)continue;}
                else if(state.getBlock()!=footState.getBlock()||((GearBlock)footState.getBlock()).kind()!=GearBlock.Kind.PLATE&&state.getValue(GearBlock.FACING)!=facing)return fail("Use one foot type and align connected skis.");
                if(feet.add(next)){if(feet.size()>GearTopology.MAX_FEET)return fail("A connected group can contain at most 64 feet.");queue.addLast(next);}
            }
        }
        java.util.List<BlockPos> ordered=new java.util.ArrayList<>(columns);java.util.Collections.sort(ordered);BlockPos primary=ordered.get(0);
        java.util.List<GearTopology.Cell> offsets=new java.util.ArrayList<>();for(BlockPos foot:feet){BlockPos local=ArmOrientation.inverse(mount,foot.subtract(primary));offsets.add(new GearTopology.Cell(local.getX(),local.getY(),local.getZ()));}
        GearTopology.Result result;
        if(footBlock.kind()==GearBlock.Kind.TRACK&&((GearBlock)world.getBlockState(primary).getBlock()).sizeClass()==2){
            java.util.List<BlockPos> contacts=new java.util.ArrayList<>(feet);java.util.Collections.sort(contacts);GearTopology.Cell contact=null;
            for(BlockPos cell:contacts)if(trackContact(world,primary,height,cell)){BlockPos local=ArmOrientation.inverse(mount,cell.subtract(primary));contact=new GearTopology.Cell(local.getX(),local.getY(),local.getZ());break;}
            result=GearTopology.validateTrackContact(height,offsets,contact);
        }else result=GearTopology.validate(height,GearTopology.FootKind.valueOf(footBlock.kind().name()),offsets);
        return result.isValid()?new Scan(result.assembly(),facing,ordered):fail(result.error());
    }

    private static boolean trackContact(World world,BlockPos column,int height,BlockPos foot){
        if(!loaded(world,foot)||!(world.getBlockState(foot).getBlock() instanceof com.vandorlabs.gear.track.TrackBlock))return false;
        EnumFacing mount=mountDirection(world,column);BlockPos local=ArmOrientation.inverse(mount,foot.subtract(column));if(local.getY()!=-height||Math.abs(local.getX())>1||Math.abs(local.getZ())>1)return false;
        net.minecraft.util.math.AxisAlignedBB track=com.vandorlabs.gear.track.TrackGeometry.bounds(com.vandorlabs.gear.track.TrackBlock.style(world,foot),world.getBlockState(foot).getValue(GearBlock.FACING),com.vandorlabs.gear.track.TrackGeometry.onArm(com.vandorlabs.gear.track.TrackAppearance.SINGLE));
        // Positive overlap on both planar axes excludes corner-only contacts.
        return Math.min(1.5,local.getX()+track.maxX)-Math.max(-.5,local.getX()+track.minX)>1e-6
                &&Math.min(1.5,local.getZ()+track.maxZ)-Math.max(-.5,local.getZ()+track.minZ)>1e-6;
    }

    /**
     * On placement/load of an unowned foot, find nearby columns without
     * following an unbounded floor. This only schedules work; scan() remains
     * responsible for rejecting an incomplete/oversized connected group.
     */
    public static EnumFacing mountDirection(World world,BlockPos pos){
        TileEntity tile=world.getTileEntity(pos);
        return tile instanceof GearTileEntity?((GearTileEntity)tile).mountDirection():EnumFacing.DOWN;
    }

    public static void requestControllersNearFoot(World world, BlockPos start) {
        ArrayDeque<BlockPos> queue=new ArrayDeque<>();Set<BlockPos> visited=new HashSet<>();queue.add(start);visited.add(start);
        while(!queue.isEmpty()&&visited.size()<=GearTopology.MAX_FEET+1){
            BlockPos foot=queue.removeFirst();
            for(EnumFacing side:EnumFacing.values()){
                BlockPos next=foot.offset(side);if(!loaded(world,next))continue;
                IBlockState state=world.getBlockState(next);
                if(isExtender(state)){
                    BlockPos top=topOfColumn(world,next);TileEntity tile=top==null?null:world.getTileEntity(top);
                    if(tile instanceof GearTileEntity)((GearTileEntity)tile).scheduleRebuild();
                }else if(isFoot(state)&&visited.add(next))queue.addLast(next);
            }
        }
    }
}
