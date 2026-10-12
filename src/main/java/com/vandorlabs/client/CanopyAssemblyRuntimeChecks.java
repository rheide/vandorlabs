package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.CanopyMesh;
import com.vandorlabs.tiles.TileEntityCanopy;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Three-wide ownership, picking and rotation contracts in a real Forge world. */
final class CanopyAssemblyRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(8,100,8);
    private static void require(boolean value,String what){if(!value)throw new IllegalStateException("canopy assembly: "+what);}
    private static void clear(World world){for(BlockPos pos:BlockPos.getAllInBox(BASE.add(-5,-1,-5),BASE.add(6,5,6)))world.setBlockToAir(pos);}
    static void check(World world) {
        BlockCanopy legacy=(BlockCanopy)Block.getBlockFromName("vandorlabs:opening_slide_2x2");
        require(legacy!=null && legacy.getCreativeTabToDisplayOn()==null,"wide item remains loadable but hidden");
        int[][] orders={{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};
        for(String mode:new String[]{"slide","hinge"}) {
        BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:opening_"+mode+"_1x2");
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int[] order:orders) {
            clear(world);IBlockState state=block.getDefaultState().withProperty(BlockCanopy.FACING,facing);
            for(int index:order) {
                BlockPos pos=BASE.offset(facing.rotateY(),index);
                require(block.fits(world,pos,facing),"constituent fits");world.setBlockState(pos,state,2);
                block.onBlockPlacedBy(world,pos,state,null,new ItemStack(block));
            }
            TileEntityCanopy owner=TileEntityCanopy.at(world,BASE);
            require(owner.draws() && owner.width()==3,"three constituents merge in every placement order");
            AxisAlignedBB bounds=owner.selectionBounds();
            require((facing.getAxis()==EnumFacing.Axis.Z?bounds.maxX-bounds.minX:bounds.maxZ-bounds.minZ)>=3,"full three-wide selection outline");
            for(int column=0;column<3;column++) {
                BlockPos pos=BASE.offset(facing.rotateY(),column);
                require(TileEntityCanopy.at(world,pos).owner()==owner,"member resolves shared owner");
                Vec3d top=CanopyMesh.rotate(new Vec3d(column+.5,3,.9),facing).add(new Vec3d(BASE));
                RayTraceResult trace=owner.trace(top,top.addVector(0,-4,0));
                require(trace!=null && trace.getBlockPos().equals(BASE),"exact glazing trace for each constituent");
                block.onBlockActivated(world,pos,state,null,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);
                for(int tick=0;tick<16;tick++)owner.update();
                require(owner.progress==1,"each member opens shared shell");
                block.onBlockActivated(world,pos,state,null,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);
                for(int tick=0;tick<16;tick++)owner.update();
                require(owner.progress==0,"each member closes shared shell");
            }
            BlockPos origin=BASE.add(-4,0,-4);
            List<VehicleStructure.Cell> cells=new ArrayList<>();
            cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.getDefaultState(),null));
            for(int column=0;column<3;column++)for(BlockPos pos:block.cells(BASE.offset(facing.rotateY(),column),facing))
                cells.add(new VehicleStructure.Cell(pos.subtract(origin),state,VehicleTileData.translated(world.getTileEntity(pos).writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(origin))));
            VehicleStructure original=new VehicleStructure(cells,BlockPos.ORIGIN,facing,1),rotated=original;
            for(int turn=0;turn<4;turn++) {
                rotated=VehicleRotation.rotate(rotated,new VehicleWorld(world,rotated),1);
                VehicleWorld view=new VehicleWorld(world,rotated);int drawing=0;
                for(VehicleStructure.Cell cell:rotated.cells) {
                    if(!(cell.state.getBlock() instanceof BlockCanopy))continue;
                    TileEntityCanopy member=(TileEntityCanopy)view.getTileEntity(cell.pos);
                    require(member.owner().width()==3,"rotated snapshot preserves three-wide links");
                    if(member.draws())drawing++;
                }
                require(drawing==1,"rotated snapshot renders one shared shell");
            }
            require(original.write().equals(rotated.write()),"four turns preserve complete canopy NBT");
            world.setBlockToAir(BASE.offset(facing.rotateY()).offset(facing.getOpposite()));
            require(TileEntityCanopy.at(world,BASE).width()==1 && TileEntityCanopy.at(world,BASE.offset(facing.rotateY(),2)).width()==1,"breaking middle splits survivors safely");
        }
        }
        fixedGlass(world);
        legacyVisor(world);
        CenteredCanopyPlacementChecks.check(world);
        CanopyRemovalRuntimeChecks.check(world);
        VehicleCanopyRuntimeChecks.check(world);
        clear(world);
        System.out.println("[vandorlabs][reprolab] canopy-three-wide PASS placement-orders=6 facings=4 member-clicks exact-trace full-highlight rotated-snapshot removal");
    }
    private static void fixedGlass(World world) {
        BlockCanopy glass=(BlockCanopy)Block.getBlockFromName("vandorlabs:regular_canopy_glass");
        for(EnumFacing first:EnumFacing.HORIZONTALS)for(EnumFacing second:EnumFacing.HORIZONTALS) {
            clear(world);
            for(int i=0;i<2;i++) {
                BlockPos pos=BASE.east(i);IBlockState state=glass.getDefaultState().withProperty(BlockCanopy.FACING,i==0?first:second);
                world.setBlockState(pos,state,2);glass.onBlockPlacedBy(world,pos,state,null,new ItemStack(glass));
            }
            TileEntityCanopy left=TileEntityCanopy.at(world,BASE),right=TileEntityCanopy.at(world,BASE.east());
            require(Integer.bitCount(left.mask())==1 && Integer.bitCount(right.mask())==1,"glass joins regardless of placement directions");
            Vec3d below=new Vec3d(BASE).addVector(.5,-1,.5),above=below.addVector(0,3,0);
            require(left.trace(below,above)!=null && left.trace(above,below)!=null,"roof picks from above and below in every direction");
            for(CanopyMesh.Face face:CanopyMesh.MODELS.get(left.model()))if(face.baseMaterial.equals("glass"))require(face.doubleSided,"glass remains transparent and visible from below");
            require(glass.getLightOpacity(glass.getDefaultState())==0,"glass transmits light");
        }
        for(String id:new String[]{"space_shuttle_3x3","space_bubble_wedge_3x3"}) {
            BlockCanopy wide=(BlockCanopy)Block.getBlockFromName("vandorlabs:"+id);
            require(wide!=null && wide.width==3,"center-seat fixed canopy registered "+id);
            for(EnumFacing facing:EnumFacing.HORIZONTALS) {
                clear(world);IBlockState state=wide.getDefaultState().withProperty(BlockCanopy.FACING,facing);
                require(wide.fits(world,BASE,facing),"wide fixed fixture fits");world.setBlockState(BASE,state,2);wide.onBlockPlacedBy(world,BASE,state,null,new ItemStack(wide));
                TileEntityCanopy tile=TileEntityCanopy.at(world,BASE);
                require(tile.width()==3 && !tile.boxes().isEmpty(),"wide fixed model and collision "+id);
                for(BlockPos cell:wide.cells(BASE,facing))require(TileEntityCanopy.at(world,cell).owner()==tile,"wide fixed followers belong to one owner");
            }
        }
        for(String id:new String[]{"space_visor_2x1","space_visor_3x1","space_visor_5x1","space_visor_5x1_double_height"}) {
            int strips=0;double height=CanopyMesh.DIMENSIONS.get(id)[2];
            for(CanopyMesh.Face face:CanopyMesh.MODELS.get(id))if(face.group.equals("top_border")) {
                strips++;for(Vec3d vertex:face.vertices)require(vertex.y>=height-1D/16-1e-8 && vertex.y<=height+1e-8,"top border stays one pixel thick "+id);
            }
            require(strips==30,"five top border segments cover the complete visor contour "+id);
        }
        }

    private static void legacyVisor(World world) {
        BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:space_visor_2x1");
        for(EnumFacing facing:EnumFacing.HORIZONTALS) {
            clear(world);IBlockState state=block.getDefaultState().withProperty(BlockCanopy.FACING,facing);
            for(BlockPos pos:block.cells(BASE,facing,1,2)) {
                world.setBlockState(pos,state,2);TileEntityCanopy tile=TileEntityCanopy.at(world,pos);tile.anchor=BASE;
                NBTTagCompound old=tile.writeToNBT(new NBTTagCompound());old.removeTag("CenteredPlacement");tile.readFromNBT(old);
            }
            BlockPos obstacle=BASE.offset(facing.rotateY(),2);world.setBlockState(obstacle,net.minecraft.init.Blocks.STONE.getDefaultState(),2);
            TileEntityCanopy owner=TileEntityCanopy.at(world,BASE);
            require(owner.cells().size()==2 && !owner.centeredPlacement,"old visor retains two-cell footprint");
            try {java.lang.reflect.Method repair=TileEntityCanopy.class.getDeclaredMethod("repairMembership");repair.setAccessible(true);repair.invoke(owner);}
            catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
            require(world.getBlockState(BASE).getBlock()==block && world.getBlockState(obstacle).getBlock()==net.minecraft.init.Blocks.STONE,"legacy visor repair preserves adjacent solid block");
        }
    }

}
