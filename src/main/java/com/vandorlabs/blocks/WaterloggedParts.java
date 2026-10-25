package com.vandorlabs.blocks;

import com.vandorlabs.tiles.*;
import java.util.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

/** Contained bucket fluids occupy the collision-free space on either side of a panel. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class WaterloggedParts {
    private WaterloggedParts(){}
    /** Registered fluids may use water/lava materials without being vanilla water/lava. */
    public static Fluid nativeFluid(IBlockState state){Fluid registered=FluidRegistry.lookupFluidForBlock(state.getBlock());return registered!=null?registered:state.getMaterial()==Material.WATER?FluidRegistry.WATER:state.getMaterial()==Material.LAVA?FluidRegistry.LAVA:null;}
    public static boolean supports(IBlockState state){return state.getBlock() instanceof BlockProgrammableSlab||state.getBlock() instanceof BlockProgrammableStairs||panel(state)||OpeningFluids.supports(state);}
    public static boolean panel(IBlockState state){return state.getBlock() instanceof BlockProgrammableWall||state.getBlock() instanceof BlockProgrammableGlass;}
    public static ContainedFluids data(TileEntity tile){if(tile instanceof TileEntitySpaceDoor)return ((TileEntitySpaceDoor)tile).fluids;if(tile instanceof TileEntityProgrammableTrapdoor)return ((TileEntityProgrammableTrapdoor)tile).fluids;return tile instanceof TileEntityAnimatedScreenSelector?((TileEntityAnimatedScreenSelector)tile).fluids:tile instanceof TileEntityProgrammableGlass?((TileEntityProgrammableGlass)tile).fluids:null;}
    public static FluidStack fluid(World world,BlockPos pos,int side){if(OpeningFluids.supports(world.getBlockState(pos)))return OpeningFluids.effective(world,pos,side);ContainedFluids data=data(world.getTileEntity(pos));return data==null?null:data.get(side);}
    public static boolean wet(IBlockAccess world,BlockPos pos){if(!supports(world.getBlockState(pos)))return false;ContainedFluids data=data(world.getTileEntity(pos));return data!=null&&(data.any()||world instanceof World&&OpeningFluids.supports(world.getBlockState(pos))&&OpeningFluids.adjacentWater((World)world,pos)&&(fluid((World)world,pos,0)!=null||fluid((World)world,pos,1)!=null));}
    public static final class Cell {public final AxisAlignedBB box;public final int side;Cell(AxisAlignedBB box,int side){this.box=box;this.side=side;}}
    public static final class Face {public final Vec3d[] vertices;public final int side,axis;Face(Vec3d[] vertices,int side,int axis){this.vertices=vertices;this.side=side;this.axis=axis;}}
    public static final class Volume {
        final List<AxisAlignedBB> solid;
        public final List<Cell> cells=new ArrayList<>();
        public final List<Face> faces=new ArrayList<>();
        Volume(List<AxisAlignedBB> solid){this.solid=solid;}
        public boolean hasSide(int side){for(Cell c:cells)if(c.side==side)return true;return false;}
    }
    /** Stable local sides follow the wall when a vehicle or turntable rotates it. */
    public static int side(IBlockAccess world,BlockPos pos,Vec3d local){
        IBlockState state=world.getBlockState(pos);if(world instanceof World&&OpeningFluids.supports(state))return OpeningFluids.side((World)world,pos,local);if(!panel(state))return 0;
        EnumFacing facing=PanelJoins.flatFacing(state,world,pos);if(!(state.getBlock() instanceof BlockProgrammableWall)||!((BlockProgrammableWall)state.getBlock()).isDiagonalShape())return com.vandorlabs.render.FlatPanelPlacement.side(facing.getIndex(),PanelJoins.flatNear(state,world,pos),local.x,local.z);
        double z=facing==EnumFacing.EAST?1-local.x:facing==EnumFacing.SOUTH?1-local.z:facing==EnumFacing.WEST?local.x:local.z;
        double coordinate=z,mid=PanelJoins.flatNear(state,world,pos)/16D+.125;
        if(state.getBlock() instanceof BlockProgrammableWall&&((BlockProgrammableWall)state.getBlock()).isDiagonalShape()){
            int mode=BlockProgrammableWall.geometry(world,pos);boolean inverted=state.getValue(BlockProgrammableWall.INVERTED);
            if(mode==2){coordinate=local.y;mid=com.vandorlabs.render.DiagonalWallGeometry.band(mode,inverted)+com.vandorlabs.render.DiagonalWallGeometry.near(mode,inverted,z)+.125;}
            else mid=com.vandorlabs.render.DiagonalWallGeometry.near(mode,inverted,local.y,BlockProgrammableWall.flatEnd(state,world,pos,false),BlockProgrammableWall.flatEnd(state,world,pos,true))+.125;
        }
        return coordinate<mid?0:1;
    }
    public static Volume volume(World world,BlockPos pos){
        return volume(world,pos,true);
    }
    /** Chunk workers must not mutate the tile's main-thread geometry cache. */
    public static Volume uncachedVolume(World world,BlockPos pos){return volume(world,pos,false);}
    private static Volume volume(World world,BlockPos pos,boolean cached){
        ContainedFluids data=data(world.getTileEntity(pos));long tick=world.getTotalWorldTime();
        if(cached&&data!=null&&data.geometry!=null&&data.geometryTick==tick&&pos.equals(data.geometryPos))return data.geometry;
        IBlockState state=world.getBlockState(pos).getActualState(world,pos);int mapping=panel(state)?PanelJoins.flatFacing(state,world,pos).getIndex():0;List<AxisAlignedBB> solid=new ArrayList<>();state.addCollisionBoxToList(world,pos,new AxisAlignedBB(pos),solid,null,false);
        List<AxisAlignedBB> local=new ArrayList<>();for(AxisAlignedBB box:solid)if(box.intersects(new AxisAlignedBB(pos)))local.add(box.offset(-pos.getX(),-pos.getY(),-pos.getZ()).intersect(new AxisAlignedBB(0,0,0,1,1,1)));
        // Include the side mapping in the cache even when two poses share collision bounds.
        if(cached&&data!=null&&data.geometry!=null&&data.geometry.solid.equals(local)&&data.geometryState==state&&data.geometryMapping==mapping&&pos.equals(data.geometryPos)){data.geometryTick=tick;return data.geometry;}
        java.util.List<com.vandorlabs.fluids.FluidVolume.Box> solids=new ArrayList<>();for(AxisAlignedBB box:local)solids.add(new com.vandorlabs.fluids.FluidVolume.Box(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ));
        double[][] extra=new double[3][];
        if(OpeningFluids.supports(state)){double[] plane=OpeningFluids.plane(world,pos);for(int axis=0;axis<3;axis++)extra[axis]=Math.abs(plane[axis])>.999?new double[]{.5,plane[3]/plane[axis],plane[4]/plane[axis]}:new double[]{.5};}
        com.vandorlabs.fluids.FluidVolume shared=com.vandorlabs.fluids.FluidVolume.build(solids,extra,(x,y,z)->side(world,pos,new Vec3d(x,y,z)));
        Volume result=new Volume(local);for(com.vandorlabs.fluids.FluidVolume.Cell cell:shared.cells){com.vandorlabs.fluids.FluidVolume.Box box=cell.box;result.cells.add(new Cell(new AxisAlignedBB(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ),cell.side));}
        for(com.vandorlabs.fluids.FluidVolume.Face face:shared.faces){Vec3d[] vertices=new Vec3d[4];for(int i=0;i<4;i++)vertices[i]=new Vec3d(face.vertices[i].x,face.vertices[i].y,face.vertices[i].z);result.faces.add(new Face(vertices,face.side,face.axis));}
        if(cached&&data!=null){data.geometry=result;data.geometryPos=pos.toImmutable();data.geometryTick=tick;data.geometryState=state;data.geometryMapping=mapping;}return result;
    }
    public static List<AxisAlignedBB> gaps(IBlockAccess access,BlockPos pos){if(!(access instanceof World))return Collections.emptyList();List<AxisAlignedBB> boxes=new ArrayList<>();for(Cell cell:volume((World)access,pos).cells)boxes.add(cell.box.offset(pos));return boxes;}
    private static Material material(FluidStack fluid){return fluid.getFluid().getBlock()==null?Material.WATER:fluid.getFluid().getBlock().getDefaultState().getMaterial();}
    public static Boolean liquid(World world,BlockPos pos,AxisAlignedBB box){return intersects(world,pos,box,null);}
    public static Boolean intersects(World world,BlockPos pos,AxisAlignedBB box,Material material){
        if(!wet(world,pos))return null;ContainedFluids data=data(world.getTileEntity(pos));FluidStack[] fluids={fluid(world,pos,0),fluid(world,pos,1)};
        if(material!=null&&!matches(fluids[0],material)&&!matches(fluids[1],material))return null;
        for(Cell cell:volume(world,pos).cells)if(fluids[cell.side]!=null&&(material==null||material(fluids[cell.side])==material)&&cell.box.offset(pos).intersects(box))return true;return false;
    }
    private static boolean matches(FluidStack fluid,Material material){return fluid!=null&&material(fluid)==material;}
    /** Half-open cells give shared partition boundaries one owner instead of an air seam. */
    public static FluidStack atPoint(IBlockAccess access,BlockPos pos,Vec3d point){
        if(!(access instanceof World)||!wet(access,pos))return null;
        World world=(World)access;Vec3d local=point.subtract(pos.getX(),pos.getY(),pos.getZ());
        for(Cell cell:volume(world,pos).cells){AxisAlignedBB b=cell.box;
            if(local.x>=b.minX&&local.x<b.maxX&&local.y>=b.minY&&local.y<b.maxY&&local.z>=b.minZ&&local.z<b.maxZ)return fluid(world,pos,cell.side);
        }
        return null;
    }
    public static IBlockState viewpoint(IBlockState state,IBlockAccess world,BlockPos pos,Vec3d point){
        FluidStack fluid=atPoint(world,pos,point);
        return fluid==null||fluid.getFluid().getBlock()==null?state:fluid.getFluid().getBlock().getDefaultState();
    }
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public static Vec3d fogColor(World world,BlockPos pos,Entity entity,Vec3d original,float partialTicks){
        Vec3d eye=net.minecraft.client.renderer.ActiveRenderInfo.projectViewFromEntity(entity,partialTicks);
        FluidStack fluid=atPoint(world,pos,eye);
        if(fluid==null||fluid.getFluid().getBlock()==null)return original;
        IBlockState state=fluid.getFluid().getBlock().getDefaultState();
        return state.getBlock().getFogColor(world,pos,state,entity,original,partialTicks);
    }
    public static Boolean entity(IBlockAccess world,BlockPos pos,Entity entity,double height,Material material,boolean head){
        if(!(world instanceof World)||!wet(world,pos))return null;
        if(!head)return intersects((World)world,pos,entity.getEntityBoundingBox(),material);
        if(!matches(fluid((World)world,pos,0),material)&&!matches(fluid((World)world,pos,1),material))return null;
        return matches(atPoint(world,pos,new Vec3d(entity.posX,height,entity.posZ)),material);
    }
    public static int light(IBlockAccess world,BlockPos pos){ContainedFluids data=data(world.getTileEntity(pos));int light=0;if(data!=null)for(int i=0;i<2;i++){FluidStack fluid=data.get(i);if(fluid!=null)light=Math.max(light,fluid.getFluid().getLuminosity(fluid));}return light;}
    @SubscribeEvent public static void placed(net.minecraftforge.event.world.BlockEvent.PlaceEvent event){
        if(event.getWorld().isRemote)return;
        World world=event.getWorld();Map<ContainedFluids,PlacementFill> fills=new IdentityHashMap<>();
        java.util.List<net.minecraftforge.common.util.BlockSnapshot> snapshots=event instanceof net.minecraftforge.event.world.BlockEvent.MultiPlaceEvent?((net.minecraftforge.event.world.BlockEvent.MultiPlaceEvent)event).getReplacedBlockSnapshots():java.util.Collections.singletonList(event.getBlockSnapshot());
        for(net.minecraftforge.common.util.BlockSnapshot snapshot:snapshots){
            BlockPos pos=snapshot.getPos();IBlockState state=world.getBlockState(pos);if(!supports(state))continue;ContainedFluids data=data(world.getTileEntity(pos));if(data==null)continue;
            PlacementFill fill=fills.computeIfAbsent(data,key->new PlacementFill());Fluid replaced=nativeFluid(snapshot.getReplacedBlock());if(replaced!=null&&!replaced.isGaseous()&&fill.fallback==null)fill.fallback=replaced;
            if(!panel(state)&&!OpeningFluids.supports(state)){Volume volume=volume(world,pos);for(int side=0;side<2;side++)fill.available[side]=volume.hasSide(side);}
            // Inspect the two sides of the separating leaf. Transverse water above/beside a
            // wall must not turn its dry face wet merely because both touch that cell edge.
            if(panel(state)||OpeningFluids.supports(state))for(EnumFacing face:placementFaces(world,pos,state)){
                BlockPos next=pos.offset(face);if(!world.isBlockLoaded(next))continue;Fluid adjacent=nativeFluid(world.getBlockState(next));if(adjacent==null||adjacent.isGaseous())continue;
                Vec3d sample=new Vec3d(.5,.5,.5).add(new Vec3d(face.getDirectionVec()).scale(.51));fill.sides[side(world,pos,sample)]=adjacent;
            }
        }
        for(Map.Entry<ContainedFluids,PlacementFill> entry:fills.entrySet()){
            PlacementFill fill=entry.getValue();boolean boundary=fill.sides[0]!=null||fill.sides[1]!=null;
            for(int side=0;side<2;side++){Fluid fluid=boundary?fill.sides[side]:fill.fallback;if(fluid!=null&&!entry.getKey().occupied(side)&&fill.available[side])entry.getKey().set(side,new FluidStack(fluid,1000));}
        }
    }
    private static final class PlacementFill {Fluid fallback;final Fluid[] sides=new Fluid[2];final boolean[] available={true,true};}
    private static EnumFacing[] placementFaces(World world,BlockPos pos,IBlockState state){
        if(OpeningFluids.supports(state)){double[] normal=OpeningFluids.plane(world,pos);int axis=Math.abs(normal[0])>Math.abs(normal[1])?0:1;if(Math.abs(normal[2])>Math.abs(normal[axis]))axis=2;return axis==0?new EnumFacing[]{EnumFacing.WEST,EnumFacing.EAST}:axis==1?new EnumFacing[]{EnumFacing.DOWN,EnumFacing.UP}:new EnumFacing[]{EnumFacing.NORTH,EnumFacing.SOUTH};}
        if(state.getBlock() instanceof BlockProgrammableWall&&((BlockProgrammableWall)state.getBlock()).isDiagonalShape()&&BlockProgrammableWall.geometry(world,pos)==2)return new EnumFacing[]{EnumFacing.DOWN,EnumFacing.UP};
        EnumFacing facing=PanelJoins.flatFacing(state,world,pos);return new EnumFacing[]{facing,facing.getOpposite()};
    }
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public static void bucket(PlayerInteractEvent.RightClickBlock event){
        World world=event.getWorld();BlockPos pos=event.getPos();if(!supports(world.getBlockState(pos)))return;
        EntityPlayer player=event.getEntityPlayer();ItemStack held=player.getHeldItem(event.getHand()),single=held.copy();single.setCount(1);IFluidHandlerItem container=FluidUtil.getFluidHandler(single);if(container==null)return;
        if(player.isSpectator()||!world.isBlockModifiable(player,pos)||!player.canPlayerEdit(pos,event.getFace(),held))return;
        ContainedFluids data=data(world.getTileEntity(pos));if(data==null)return;
        Vec3d hit=event.getHitVec()==null?player.getPositionEyes(1):event.getHitVec();int side=side(world,pos,hit.subtract(new Vec3d(pos)).add(new Vec3d(event.getFace().getDirectionVec()).scale(.001)));
        FluidStack incoming=container.drain(1000,false),stored=data.get(side);boolean fill=incoming!=null;
        event.setCanceled(true);event.setCancellationResult(EnumActionResult.FAIL);
        if(fill){if(incoming.amount!=1000)return;if(!com.vandorlabs.fluids.FluidVolume.canFill(incoming.amount,incoming.getFluid().isGaseous(incoming),data.occupied(side),()->OpeningFluids.supports(world.getBlockState(pos))||volume(world,pos).hasSide(side)))return;}
        else if(stored==null||container.fill(stored,false)!=1000)return;
        event.setCancellationResult(EnumActionResult.SUCCESS);if(world.isRemote)return;
        if(fill)container.drain(1000,true);else container.fill(stored,true);
        data.set(side,fill?incoming:null);
        if(!player.capabilities.isCreativeMode){ItemStack result=container.getContainer();held.shrink(1);if(held.isEmpty())player.setHeldItem(event.getHand(),result);else if(!player.inventory.addItemStackToInventory(result))player.dropItem(result,false);}
        FluidStack sound=fill?incoming:stored;world.playSound(null,pos,fill?sound.getFluid().getEmptySound(sound):sound.getFluid().getFillSound(sound),SoundCategory.BLOCKS,1,1);
    }
}
