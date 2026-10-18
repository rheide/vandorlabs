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
        return state.getBlock() instanceof GearBlock && (((GearBlock)state.getBlock()).kind()==GearBlock.Kind.EXTENDER || isSwingArm(state));
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
            BlockPos above = top.up();
            if (!loaded(world, above)) return null;
            if (!isExtender(world.getBlockState(above))) return top;
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
        BlockPos top=topOfColumn(world,requested);
        if(top==null)return unavailable("Load the complete strut column first.");
        IBlockState topState=world.getBlockState(top);boolean swing=isSwingArm(topState);
        int height=0;BlockPos first=top;
        while(loaded(world,first)&&isExtender(world.getBlockState(first))){
            if(world.getBlockState(first).getBlock()!=topState.getBlock())return fail("Do not mix straight extenders and swing arms in a column.");
            if(++height>GearTopology.MAX_COLUMN_HEIGHT)return fail("A strut column may contain at most eight sections.");
            first=first.down();
        }
        if(!loaded(world,first))return unavailable("Load the feet and their boundary first.");
        IBlockState footState=world.getBlockState(first);
        if(!isFoot(footState))return fail("Place a landing plate, ski or wheel beneath the strut.");
        EnumFacing facing=footState.getValue(GearBlock.FACING);
        Set<BlockPos> feet=new HashSet<>(),columns=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(first);feet.add(first);
        while(!queue.isEmpty()){
            BlockPos foot=queue.removeFirst();
            if(!((GearBlock)footState.getBlock()).componentIntact(world,((GearBlock)footState.getBlock()).componentRoot(foot,world.getBlockState(foot))))return fail("Incomplete wheel footprint.");
            if(!(world.getTileEntity(foot) instanceof GearTileEntity))return unavailable("A foot tile is still loading.");
            BlockPos above=foot.up();if(!loaded(world,above))return unavailable("Load every mounting column.");
            if(isExtender(world.getBlockState(above))){
                BlockPos column=topOfColumn(world,above);if(column==null)return unavailable("Load the complete connected strut group.");
                if(column.getY()-foot.getY()!=height||world.getBlockState(column).getBlock()!=topState.getBlock())return fail("Connected struts must have the same type and height.");
                for(int n=0;n<height;n++){
                    BlockPos cell=column.down(n);IBlockState state=world.getBlockState(cell);
                    if(state.getBlock()!=topState.getBlock()||!(world.getTileEntity(cell) instanceof GearTileEntity))return fail("Incomplete or mixed strut column.");
                    if(!((GearBlock)state.getBlock()).componentIntact(world,cell))return fail("Incomplete 2x2 arm footprint.");
                    if((swing||((GearBlock)topState.getBlock()).width()==2)&&state.getValue(GearBlock.FACING)!=topState.getValue(GearBlock.FACING))return fail("Align connected swing arms in the same direction.");
                }
                BlockPos support=column.up();if(!loaded(world,support))return unavailable("Load all mounting blocks.");
                IBlockState mount=world.getBlockState(support);if(mount.getBlock() instanceof GearBlock||!mount.isSideSolid(world,support,EnumFacing.DOWN))return fail("Each strut must attach below a block with a solid underside.");
                for(BlockPos mountCell:((GearBlock)topState.getBlock()).componentCells(world,column)){BlockPos at=mountCell.up();if(!loaded(world,at))return unavailable("Load all 2x2 mounting blocks.");if(world.getBlockState(at).getBlock() instanceof GearBlock||!world.getBlockState(at).isSideSolid(world,at,EnumFacing.DOWN))return fail("Support the full 2x2 mounting cap.");}
                columns.add(column);
            }
            for(EnumFacing side:HORIZONTAL){BlockPos next=foot.offset(side);if(!loaded(world,next))return unavailable("The entire foot group and boundary must be loaded.");IBlockState state=world.getBlockState(next);if(!isFoot(state))continue;
                if(state.getBlock()!=footState.getBlock()||((GearBlock)footState.getBlock()).kind()!=GearBlock.Kind.PLATE&&state.getValue(GearBlock.FACING)!=facing)return fail("Use one foot type and align connected skis.");
                if(feet.add(next)){if(feet.size()>GearTopology.MAX_FEET)return fail("A connected group can contain at most 64 feet.");queue.addLast(next);}
            }
        }
        java.util.List<BlockPos> ordered=new java.util.ArrayList<>(columns);java.util.Collections.sort(ordered);BlockPos primary=ordered.get(0);
        java.util.List<GearTopology.Cell> offsets=new java.util.ArrayList<>();for(BlockPos foot:feet)offsets.add(new GearTopology.Cell(foot.getX()-primary.getX(),-height,foot.getZ()-primary.getZ()));
        GearTopology.Result result=GearTopology.validate(height,GearTopology.FootKind.valueOf(((GearBlock)footState.getBlock()).kind().name()),offsets);
        return result.isValid()?new Scan(result.assembly(),facing,ordered):fail(result.error());
    }

    /**
     * On placement/load of an unowned foot, find nearby columns without
     * following an unbounded floor. This only schedules work; scan() remains
     * responsible for rejecting an incomplete/oversized connected group.
     */
    public static void requestControllersNearFoot(World world, BlockPos start) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<BlockPos>();
        Set<BlockPos> visited = new HashSet<BlockPos>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty() && visited.size() <= GearTopology.MAX_FEET + 1) {
            BlockPos foot = queue.removeFirst();
            BlockPos above = foot.up();
            if (loaded(world, above) && isExtender(world.getBlockState(above))) {
                BlockPos top = topOfColumn(world, above);
                if (top != null) {
                    TileEntity tile = world.getTileEntity(top);
                    if (tile instanceof GearTileEntity) ((GearTileEntity) tile).scheduleRebuild();
                }
            }
            for (EnumFacing side : HORIZONTAL) {
                BlockPos next = foot.offset(side);
                if (loaded(world, next) && isFoot(world.getBlockState(next)) && visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }
    }
}
