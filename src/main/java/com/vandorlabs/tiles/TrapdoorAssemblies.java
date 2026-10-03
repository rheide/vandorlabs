package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockProgrammableTrapdoor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Loaded-only rectangular surface discovery. Membership is saved, never polled per tick. */
final class TrapdoorAssemblies {
    private TrapdoorAssemblies() { }
    static boolean complete(TileEntityProgrammableTrapdoor root,EntityPlayer player,ItemStack stack) {
        boolean diagonal=root instanceof TileEntityProgrammableDiagonalTrapdoor;
        if(diagonal && root.position!=1 && connectedPatch((TileEntityProgrammableDiagonalTrapdoor)root,player,stack))return true;
        EnumFacing facing=root.getWorld().getBlockState(root.getPos()).getValue(BlockProgrammableTrapdoor.FACING);
        EnumFacing across=diagonal?facing.rotateY():facing.getAxis()==EnumFacing.Axis.X?EnumFacing.EAST:EnumFacing.SOUTH;
        BlockPos u=new BlockPos(across.getDirectionVec());
        BlockPos v=diagonal?root.position==2?new BlockPos(facing.getOpposite().getDirectionVec()):new BlockPos(0,1,0)
                :new BlockPos(across.rotateY().getDirectionVec());
        if(diagonal) {
            TileEntityProgrammableDiagonalTrapdoor leaf=(TileEntityProgrammableDiagonalTrapdoor)root;
            BlockPos stagger=root.position==2?v.add(0,leaf.isInverted()?-1:1,0)
                    :v.add((leaf.isInverted()?facing:facing.getOpposite()).getDirectionVec());
            if(discover(root,player,stack,u,stagger,true))return true;
        }
        return discover(root,player,stack,u,v,false);
    }
    /** Half-size surfaces can cross both grids or leave a square incomplete. */
    private static boolean connectedPatch(TileEntityProgrammableDiagonalTrapdoor root,EntityPlayer player,ItemStack stack) {
        Map<BlockPos,TileEntityProgrammableDiagonalTrapdoor> cells=new HashMap<>();
        Set<BlockPos> visited=new HashSet<>();ArrayDeque<BlockPos> pending=new ArrayDeque<>();pending.add(root.getPos());
        int[] low={Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE},high={Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE};
        while(!pending.isEmpty()) {
            BlockPos at=pending.removeFirst();if(!visited.add(at) || !root.getWorld().isBlockLoaded(at))continue;
            TileEntity raw=root.getWorld().getTileEntity(at);
            if(!(raw instanceof TileEntityProgrammableDiagonalTrapdoor) || !root.compatible((TileEntityProgrammableTrapdoor)raw))continue;
            TileEntityProgrammableDiagonalTrapdoor leaf=(TileEntityProgrammableDiagonalTrapdoor)raw;
            if(player!=null && (!player.canPlayerEdit(at,EnumFacing.UP,stack) || !root.getWorld().isBlockModifiable(player,at)))return false;
            cells.put(at.toImmutable(),leaf);if(cells.size()>64)return false;
            int[] coordinate={at.getX(),at.getY(),at.getZ()};
            for(int axis=0;axis<3;axis++){low[axis]=Math.min(low[axis],coordinate[axis]);high[axis]=Math.max(high[axis],coordinate[axis]);if(high[axis]-low[axis]>=8)return false;}
            for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)
                if(dx!=0 || dy!=0 || dz!=0)pending.add(at.add(dx,dy,dz));
        }
        if(cells.size()<2)return false;
        // Retain the established pair/square representation on the original grid.
        EnumFacing normal=root.facing(),along=root.position==2?normal.getOpposite():EnumFacing.UP,across=normal.rotateY();
        int minCol=Integer.MAX_VALUE,maxCol=Integer.MIN_VALUE,minRow=Integer.MAX_VALUE,maxRow=Integer.MIN_VALUE;
        boolean originalPlane=true;
        for(BlockPos at:cells.keySet()) {
            BlockPos delta=at.subtract(root.getPos());
            originalPlane &= root.position==2?delta.getY()==0:com.vandorlabs.blocks.PanelPlane.axis(delta,normal)==0;
            int col=com.vandorlabs.blocks.PanelPlane.axis(delta,across),row=com.vandorlabs.blocks.PanelPlane.axis(delta,along);
            minCol=Math.min(minCol,col);maxCol=Math.max(maxCol,col);minRow=Math.min(minRow,row);maxRow=Math.max(maxRow,row);
        }
        if(originalPlane && maxCol-minCol<=1 && maxRow-minRow<=1 && cells.size()==(maxCol-minCol+1)*(maxRow-minRow+1)) {
            for(TileEntityProgrammableDiagonalTrapdoor leaf:cells.values())if(!leaf.assembly.isEmpty())leaf.clearAssembly();
            return false;
        }
        List<BlockPos> members=new ArrayList<>(cells.keySet());Collections.sort(members);
        boolean linked=true;for(TileEntityProgrammableDiagonalTrapdoor leaf:cells.values())linked &= members.equals(leaf.assembly);
        if(linked)return true;
        TileEntityProgrammableDiagonalTrapdoor reference=cells.get(members.get(0));
        across=reference.facing().rotateY();minCol=Integer.MAX_VALUE;maxCol=Integer.MIN_VALUE;
        for(BlockPos at:members){int col=com.vandorlabs.blocks.PanelPlane.axis(at,across);minCol=Math.min(minCol,col);maxCol=Math.max(maxCol,col);}
        int width=maxCol-minCol+1,split=(width+1)/2;
        boolean open=root.getWorld().getBlockState(reference.getPos()).getValue(BlockProgrammableTrapdoor.OPEN);
        for(BlockPos at:members) {
            TileEntityProgrammableDiagonalTrapdoor leaf=cells.get(at);int col=com.vandorlabs.blocks.PanelPlane.axis(at,across)-minCol;
            boolean highSide=col>=split;int distance=highSide?width-1-col:col;
            leaf.squareOrigin=null;leaf.partner=null;leaf.assembly=new ArrayList<>(members);
            leaf.sliding=reference.sliding;leaf.slideIntoWall=reference.slideIntoWall;leaf.trigger=reference.trigger;
            boolean reversed=leaf.facing().rotateY()==across?highSide:!highSide;
            leaf.setOpeningSide(reversed);leaf.assemblyHinge=reversed?1+distance-1/16D:-distance+1/16D;
            leaf.assemblyTravel=(highSide?width-split:split)-1/16D;
        }
        for(TileEntityProgrammableDiagonalTrapdoor leaf:cells.values()){leaf.setRedstoneChannel(reference.channel);leaf.sync();}
        root.requestOpen(open);root.evaluatePower(true);return true;
    }
    private static boolean discover(TileEntityProgrammableTrapdoor root,EntityPlayer player,ItemStack stack,BlockPos u,BlockPos v,boolean stagger) {
        Map<BlockPos,TileEntityProgrammableTrapdoor> cells=new HashMap<>();
        ArrayDeque<BlockPos> pending=new ArrayDeque<>();pending.add(BlockPos.ORIGIN);
        int minX=0,maxX=0,minY=0,maxY=0;
        while(!pending.isEmpty()) {
            BlockPos coordinate=pending.removeFirst();if(cells.containsKey(coordinate))continue;
            if(Math.abs(coordinate.getX())>8 || Math.abs(coordinate.getY())>8)return false;
            BlockPos at=root.getPos().add(u.getX()*coordinate.getX()+v.getX()*coordinate.getY(),
                    u.getY()*coordinate.getX()+v.getY()*coordinate.getY(),u.getZ()*coordinate.getX()+v.getZ()*coordinate.getY());
            if(!root.getWorld().isBlockLoaded(at))continue;
            TileEntity raw=root.getWorld().getTileEntity(at);
            if(!(raw instanceof TileEntityProgrammableTrapdoor))continue;
            TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)raw;
            if(!root.compatible(leaf))continue;
            if(player!=null && (!player.canPlayerEdit(at,EnumFacing.UP,stack) || !root.getWorld().isBlockModifiable(player,at)))return false;
            cells.put(coordinate,leaf);if(cells.size()>64)return false;
            minX=Math.min(minX,coordinate.getX());maxX=Math.max(maxX,coordinate.getX());
            minY=Math.min(minY,coordinate.getY());maxY=Math.max(maxY,coordinate.getY());
            pending.add(coordinate.east());pending.add(coordinate.west());pending.add(coordinate.up());pending.add(coordinate.down());
        }
        int width=maxX-minX+1,height=maxY-minY+1;
        if(stagger && height<2)return false;
        if(width>8 || height>8 || cells.size()!=width*height || cells.size()<2)return false;
        // Preserve established pair/square membership and its save representation.
        if(!stagger && width<=2 && height<=2)return false;
        List<BlockPos> members=new ArrayList<>();
        for(int row=minY;row<=maxY;row++)for(int col=minX;col<=maxX;col++)members.add(cells.get(new BlockPos(col,row,0)).getPos().toImmutable());
        TileEntityProgrammableTrapdoor reference=cells.get(new BlockPos(minX,minY,0));
        boolean open=root.getWorld().getBlockState(reference.getPos()).getValue(BlockProgrammableTrapdoor.OPEN);
        int split=(width+1)/2;
        for(Map.Entry<BlockPos,TileEntityProgrammableTrapdoor> entry:cells.entrySet()) {
            TileEntityProgrammableTrapdoor leaf=entry.getValue();int col=entry.getKey().getX()-minX;
            boolean high=col>=split;int distance=high?width-1-col:col;
            leaf.squareOrigin=null;leaf.partner=null;leaf.assembly=new ArrayList<>(members);
            leaf.sliding=reference.sliding;leaf.slideIntoWall=reference.slideIntoWall;leaf.trigger=reference.trigger;
            leaf.assemblyTravel=(high?width-split:split)-1/16D;
            if(leaf instanceof TileEntityProgrammableDiagonalTrapdoor) {
                TileEntityProgrammableDiagonalTrapdoor d=(TileEntityProgrammableDiagonalTrapdoor)leaf;
                EnumFacing localX=d.facing().rotateY();boolean reversed=localX.getDirectionVec().equals(u)?high:!high;
                d.setOpeningSide(reversed);leaf.assemblyHinge=reversed?1+distance-1/16D:-distance+1/16D;
            } else {
                EnumFacing direction=EnumFacing.getFacingFromVector(u.getX(),u.getY(),u.getZ());
                root.getWorld().setBlockState(leaf.getPos(),root.getWorld().getBlockState(leaf.getPos()).withProperty(BlockProgrammableTrapdoor.FACING,high?direction:direction.getOpposite()),2);
                leaf.assemblyHinge=-distance+com.vandorlabs.render.TrapdoorGeometry.OPEN_HINGE;
            }
        }
        for(TileEntityProgrammableTrapdoor leaf:cells.values()){leaf.setRedstoneChannel(reference.channel);leaf.sync();}
        root.requestOpen(open);root.evaluatePower(true);return true;
    }
}
