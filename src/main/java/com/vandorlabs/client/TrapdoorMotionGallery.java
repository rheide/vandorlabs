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
        String[] bits=shot.split("_");String shape=bits[3];boolean joined=shape.startsWith("bent") || shape.startsWith("joined");boolean bent=shape.startsWith("bent");boolean diagonal=joined || shape.equals("tall") || shape.equals("half") || shape.equals("shallow");
        int mode=bits[4].equals("left")?4:bits[4].equals("right")?5:bits[4].equals("horizontal")?6:bits[4].equals("vertical")?7:3;
        boolean open=bits[5].equals("open");int position=shape.equals("half")?0:shape.equals("shallow")?2:diagonal?1:0;
        BlockProgrammableTrapdoor block=(BlockProgrammableTrapdoor)(diagonal?ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR:ModBlocks.PROGRAMMABLE_TRAPDOOR);
        BlockPos anchor=new BlockPos(x,y+3,-18);List<TileEntityProgrammableTrapdoor> leaves=new ArrayList<>();
        int columns=joined || shape.equals("pair") || shape.equals("square")?2:1,rows=joined || shape.equals("square")?2:1;
        EnumFacing facing=shape.endsWith("south")?EnumFacing.SOUTH:shape.endsWith("east")?EnumFacing.EAST:shape.endsWith("west")?EnumFacing.WEST:EnumFacing.NORTH;
        for(int col=0;col<columns;col++)for(int row=0;row<rows;row++) {
            BlockPos pos=joined?anchor.offset(facing.rotateY(),col).up(row).offset(facing.getOpposite(),bent?0:row):anchor.add(col,0,row);IBlockState state=block.getDefaultState().withProperty(BlockTrapDoor.FACING,bent && row==1?facing.getOpposite():facing).withProperty(BlockTrapDoor.HALF,BlockTrapDoor.DoorHalf.BOTTOM);
            world.setBlockState(pos,state,2);TileEntityProgrammableTrapdoor tile=(TileEntityProgrammableTrapdoor)world.getTileEntity(pos);
            tile.configure(ScreenHousingTextures.doorIndex(2,1),position,true,0,0);tile.setTileTexture(false);tile.setSlideMode(mode);leaves.add(tile);
        }
        TileEntityProgrammableTrapdoor root=leaves.get(0);
        if(shape.equals("pair"))leaves.get(1).pairWith(root);else if(joined || shape.equals("square"))root.completeSquare(null,ItemStack.EMPTY);
        if(root.group().size()!=leaves.size())throw new IllegalStateException("Trapdoor panel gallery group did not form: "+shot);
        root.setSlideModeGroup(mode);root.requestOpen(open);
        for(TileEntityProgrammableTrapdoor tile:leaves) {
            IBlockState state=world.getBlockState(tile.getPos());double pose=open?1:0;
            if(tile.getSlideMode()!=mode || !tile.hasPanelMotion())throw new IllegalStateException("Trapdoor group lost new mode");
            if(tile.panelFaces(state,pose).size()!=tile.panelMotion(state).count())throw new IllegalStateException("Trapdoor panel count differs");
            TileEntityProgrammableTrapdoor restored=diagonal?new TileEntityProgrammableDiagonalTrapdoor():new TileEntityProgrammableTrapdoor();restored.readFromNBT(tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
            if(restored.getSlideMode()!=mode)throw new IllegalStateException("Trapdoor world-save motion lost");
            if(joined && (mode==4 || mode==5 || mode==6)) {
                PanelMotion motion=tile.panelMotion(state);
                if(Math.abs(motion.width-2-2*TrapdoorGeometry.EDGE_CLEARANCE)>1e-6)throw new IllegalStateException("Joined diagonal width differs: "+shot+" width="+motion.width);
                EnumFacing right=facing.rotateYCCW();
                if(motion.u[0]!=right.getFrontOffsetX() || motion.u[2]!=right.getFrontOffsetZ())throw new IllegalStateException("Joined diagonal direction differs: "+shot);
                double[] shift=motion.shift(0,1),rootShift=root.panelMotion(world.getBlockState(root.getPos())).shift(0,1);
                for(int axis=0;axis<3;axis++)if(Math.abs(shift[axis]-rootShift[axis])>1e-6)throw new IllegalStateException("Joined rows move in opposite directions: "+shot);
                // One shared split plane in world coordinates, independent of leaf orientation.
                if(mode==6) {
                    PanelMotion rm=root.panelMotion(world.getBlockState(root.getPos()));
                    double offset=tile.getPos().getX()*motion.u[0]+tile.getPos().getZ()*motion.u[2];
                    double rootOffset=root.getPos().getX()*rm.u[0]+root.getPos().getZ()*rm.u[2];
                    if(Math.abs(motion.lowU+offset-rm.lowU-rootOffset)>1e-6)throw new IllegalStateException("Joined split centers differ: "+shot);
                }
            }
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
