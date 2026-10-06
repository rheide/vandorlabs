package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.render.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Real server grouping, saved settings and client-visible panel endpoints for new motion. */
final class TrapdoorMotionGallery {
    static void build(World world,String shot,int x,int y) {
        String[] bits=shot.split("_");String shape=bits[3];boolean diagonal=shape.equals("tall") || shape.equals("half") || shape.equals("shallow");
        int mode=bits[4].equals("left")?4:bits[4].equals("right")?5:bits[4].equals("horizontal")?6:bits[4].equals("vertical")?7:3;
        boolean open=bits[5].equals("open");int position=shape.equals("half")?0:shape.equals("shallow")?2:diagonal?1:0;
        BlockProgrammableTrapdoor block=(BlockProgrammableTrapdoor)(diagonal?ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR:ModBlocks.PROGRAMMABLE_TRAPDOOR);
        BlockPos anchor=new BlockPos(x,y+3,-18);List<TileEntityProgrammableTrapdoor> leaves=new ArrayList<>();
        int columns=shape.equals("pair") || shape.equals("square")?2:1,rows=shape.equals("square")?2:1;
        for(int col=0;col<columns;col++)for(int row=0;row<rows;row++) {
            BlockPos pos=anchor.add(col,0,row);IBlockState state=block.getDefaultState().withProperty(BlockTrapDoor.FACING,EnumFacing.NORTH).withProperty(BlockTrapDoor.HALF,BlockTrapDoor.DoorHalf.BOTTOM);
            world.setBlockState(pos,state,2);TileEntityProgrammableTrapdoor tile=(TileEntityProgrammableTrapdoor)world.getTileEntity(pos);
            tile.configure(ScreenHousingTextures.doorIndex(2,1),position,true,0,0);tile.setTileTexture(false);tile.setSlideMode(mode);leaves.add(tile);
        }
        TileEntityProgrammableTrapdoor root=leaves.get(0);
        if(shape.equals("pair"))leaves.get(1).pairWith(root);else if(shape.equals("square"))root.completeSquare(null,ItemStack.EMPTY);
        if(root.group().size()!=leaves.size())throw new IllegalStateException("Trapdoor panel gallery group did not form: "+shot);
        root.setSlideModeGroup(mode);root.requestOpen(open);
        for(TileEntityProgrammableTrapdoor tile:leaves) {
            IBlockState state=world.getBlockState(tile.getPos());double pose=open?1:0;
            if(tile.getSlideMode()!=mode || !tile.hasPanelMotion())throw new IllegalStateException("Trapdoor group lost new mode");
            if(tile.panelFaces(state,pose).size()!=tile.panelMotion(state).count())throw new IllegalStateException("Trapdoor panel count differs");
            TileEntityProgrammableTrapdoor restored=diagonal?new TileEntityProgrammableDiagonalTrapdoor():new TileEntityProgrammableTrapdoor();restored.readFromNBT(tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
            if(restored.getSlideMode()!=mode)throw new IllegalStateException("Trapdoor world-save motion lost");
            double[][] corners=TrapdoorPanelMotion.closed(tile,state);double[] center=new double[3];for(double[] point:corners)for(int i=0;i<3;i++)center[i]+=point[i]/8;
            double[] normal=PanelPolyhedron.unit(PanelPolyhedron.cross(delta(corners[1],corners[0]),delta(corners[diagonal && position!=2?2:4],corners[0])));
            Vec3d mid=new Vec3d(center[0]+tile.getPos().getX(),center[1]+tile.getPos().getY(),center[2]+tile.getPos().getZ());
            RayTraceResult ray=TrapdoorPanelCollision.trace(tile,state,pose,tile.getPos(),mid.addVector(normal[0]*.3,normal[1]*.3,normal[2]*.3),mid.addVector(-normal[0]*.3,-normal[1]*.3,-normal[2]*.3));
            if(open?ray!=null:ray==null)throw new IllegalStateException("Trapdoor open/closed aperture differs: "+shot);
        }
        System.out.println("[vandorlabs][reprolab] trapdoor-panel-scene PASS "+shot+" leaves="+leaves.size());
    }
    private static double[] delta(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
}
