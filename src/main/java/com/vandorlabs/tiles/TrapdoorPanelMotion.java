package com.vandorlabs.tiles;

import com.vandorlabs.blocks.*;
import com.vandorlabs.render.*;
import net.minecraft.block.state.IBlockState;
import java.util.*;

/** Shared aperture coordinates for joined panels, including sideways movement across bends. */
public final class TrapdoorPanelMotion {
    private TrapdoorPanelMotion(){}
    public static double[][] closed(TileEntityProgrammableTrapdoor tile,IBlockState state) {
        if(tile instanceof TileEntityProgrammableDiagonalTrapdoor)
            return BlockProgrammableDiagonalTrapdoor.corners(state,(TileEntityProgrammableDiagonalTrapdoor)tile,0,Collections.emptyList());
        return tile.corners(state,0);
    }
    public static PanelMotion create(TileEntityProgrammableTrapdoor tile,IBlockState state,List<TileEntityProgrammableTrapdoor> leaves) {
        List<TileEntityProgrammableTrapdoor> group=loadedLeaves(tile,state,leaves);
        group.sort(Comparator.comparingLong(leaf->leaf.getPos().toLong()));
        TileEntityProgrammableTrapdoor reference=group.isEmpty()?tile:group.get(0);
        double[][] base=closed(reference,state(reference,tile,state));
        boolean tall=reference instanceof TileEntityProgrammableDiagonalTrapdoor && reference.getPosition()!=2;
        double[] u=axis(base,1),v=axis(base,tall?2:4),normal=PanelPolyhedron.cross(u,v);
        double[] center=center(base);
        double plane=normal[0]*(reference.getPos().getX()+center[0])+normal[1]*(reference.getPos().getY()+center[1])+normal[2]*(reference.getPos().getZ()+center[2]);
        boolean coplanar=true;
        for(TileEntityProgrammableTrapdoor leaf:group) {
            double[][] corners=closed(leaf,state(leaf,tile,state));
            double[] leafCenter=center(corners);
            double projected=normal[0]*(leaf.getPos().getX()+leafCenter[0])+normal[1]*(leaf.getPos().getY()+leafCenter[1])+normal[2]*(leaf.getPos().getZ()+leafCenter[2]);
            boolean leafTall=leaf instanceof TileEntityProgrammableDiagonalTrapdoor && leaf.getPosition()!=2;
            if(Math.abs(projected-plane)>1e-5 || Math.abs(PanelPolyhedron.dot(normal,axis(corners,1)))>1e-5 || Math.abs(PanelPolyhedron.dot(normal,axis(corners,leafTall?2:4)))>1e-5){coplanar=false;break;}
        }
        boolean sharedSideways=tile.getSlideMode()==4 || tile.getSlideMode()==5 || tile.getSlideMode()==6;
        if(!coplanar && !sharedSideways){group=Collections.singletonList(tile);base=closed(tile,state);tall=tile instanceof TileEntityProgrammableDiagonalTrapdoor && tile.getPosition()!=2;u=axis(base,1);v=axis(base,tall?2:4);}
        if(group.isEmpty())group=Collections.singletonList(tile);
        // Diagonal corner order runs leftward when viewed from the facing side.
        // Use the same outward-facing right axis for all members, even reversed rows.
        if(reference instanceof TileEntityProgrammableDiagonalTrapdoor) {
            net.minecraft.util.EnumFacing right=((TileEntityProgrammableDiagonalTrapdoor)reference).facing().rotateYCCW();
            u=new double[]{right.getFrontOffsetX(),0,right.getFrontOffsetZ()};
        }
        double minU=Double.POSITIVE_INFINITY,minV=minU,maxU=Double.NEGATIVE_INFINITY,maxV=maxU;
        for(TileEntityProgrammableTrapdoor leaf:group) {
            double[][] corners=closed(leaf,state(leaf,tile,state));
            for(double[] point:corners) {
                double[] local={point[0]+leaf.getPos().getX()-tile.getPos().getX(),point[1]+leaf.getPos().getY()-tile.getPos().getY(),point[2]+leaf.getPos().getZ()-tile.getPos().getZ()};
                double a=PanelPolyhedron.dot(local,u),b=PanelPolyhedron.dot(local,v);
                minU=Math.min(minU,a);maxU=Math.max(maxU,a);minV=Math.min(minV,b);maxV=Math.max(maxV,b);
            }
        }
        // Leave the original edge-clearance reveal outside the opening at full travel.
        double margin=TrapdoorGeometry.EDGE_CLEARANCE*2;
        return new PanelMotion(u,v,minU-margin,minV-margin,maxU-minU+2*margin,maxV-minV+2*margin,tile.getSlideMode());
    }
    static List<TileEntityProgrammableTrapdoor> loadedLeaves(TileEntityProgrammableTrapdoor tile,IBlockState state,List<TileEntityProgrammableTrapdoor> leaves) {
        List<TileEntityProgrammableTrapdoor> result=new ArrayList<>();
        for(TileEntityProgrammableTrapdoor leaf:leaves)if(leaf==tile || leaf.getWorld()!=null && leaf.getWorld().isBlockLoaded(leaf.getPos()) && leaf.getWorld().getBlockState(leaf.getPos()).getBlock() instanceof BlockProgrammableTrapdoor)result.add(leaf);
        if(!result.contains(tile))result.add(tile);return result;
    }
    static IBlockState state(TileEntityProgrammableTrapdoor leaf,TileEntityProgrammableTrapdoor owner,IBlockState state){return leaf==owner || leaf.getWorld()==null?state:leaf.getWorld().getBlockState(leaf.getPos());}
    private static double[] center(double[][] corners){double[] result=new double[3];for(double[] point:corners)for(int i=0;i<3;i++)result[i]+=point[i]/8;return result;}
    private static double[] axis(double[][] corners,int end){return PanelPolyhedron.unit(new double[]{corners[end][0]-corners[0][0],corners[end][1]-corners[0][1],corners[end][2]-corners[0][2]});}
}
