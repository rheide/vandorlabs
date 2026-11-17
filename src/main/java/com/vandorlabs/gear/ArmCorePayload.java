package com.vandorlabs.gear;

import com.vandorlabs.blocks.*;
import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.VehicleStructure;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Ownership closure for existing formed Core machines; never follows decorative neighbours. */
final class ArmCorePayload {
    private ArmCorePayload(){}
    static boolean machine(IBlockState state){return state.getBlock() instanceof BlockConstructorPart||state.getBlock() instanceof BlockConstructorCore&&state.getValue(BlockConstructorCore.FORMED);}
    static boolean typed(VehicleStructure.Cell cell){return cell.state.getBlock() instanceof BlockConstructorCore||cell.state.getBlock() instanceof BlockConstructorPart;}
    static boolean contains(VehicleStructure snapshot){for(VehicleStructure.Cell cell:snapshot.cells)if(typed(cell))return true;return false;}
    static final class Group {
        final BlockPos owner;final Set<BlockPos> cells;
        Group(BlockPos owner,Set<BlockPos> cells){this.owner=owner;this.cells=Collections.unmodifiableSet(cells);}
    }
    static final class Discovery {
        private final World world;private final Map<BlockPos,Group> groups=new HashMap<>();
        Discovery(World world){this.world=world;}
        Group at(BlockPos contact){
            Group cached=groups.get(contact);if(cached!=null)return cached;
            if(!GearAssembly.loaded(world,contact))throw new IllegalArgumentException("Load the complete Core footprint before moving it");IBlockState state=world.getBlockState(contact);if(!machine(state))return null;
            TileEntity touched=tile(contact);BlockPos owner=touched instanceof TileEntityConstructorPart?((TileEntityConstructorPart)touched).owner():contact;
            TileEntity candidate=tile(owner);if(!(candidate instanceof TileEntityConstructor)||!((TileEntityConstructor)candidate).formed())throw new IllegalArgumentException("Restore the complete formed Core before moving it");
            TileEntityConstructor core=(TileEntityConstructor)candidate;CoreLayout layout=core.layout();if(layout==null||!layout.canonicalCorner()||layout.cells()>CoreLayout.MAX_CELLS)throw new IllegalArgumentException("Invalid formed Core footprint");
            Set<BlockPos> members=new LinkedHashSet<>();
            for(int x=0;x<layout.width;x++)for(int y=0;y<layout.height;y++)for(int z=0;z<layout.depth;z++){
                BlockPos at=core.cell(x,y,z);TileEntity member=tile(at);
                if(at.equals(owner)){if(member!=core)throw new IllegalArgumentException("Core identity changed");}
                else if(!(world.getBlockState(at).getBlock() instanceof BlockConstructorPart)||!(member instanceof TileEntityConstructorPart)||!((TileEntityConstructorPart)member).owner().equals(owner)||((TileEntityConstructorPart)member).frameMatter()!=layout.frame(x,y,z))throw new IllegalArgumentException("Restore every Core reservation before moving it");
                if(!members.add(at))throw new IllegalArgumentException("Overlapping Core reservations");
            }
            if(!members.contains(contact)||world.getTileEntity(owner)!=core)throw new IllegalArgumentException("Core ownership changed");
            Group result=new Group(owner,members);for(BlockPos at:members){Group old=groups.putIfAbsent(at,result);if(old!=null&&old!=result)throw new IllegalArgumentException("Overlapping Core ownership");}return result;
        }
        private TileEntity tile(BlockPos at){if(!GearAssembly.loaded(world,at))throw new IllegalArgumentException("Load the complete Core footprint before moving it");TileEntity tile=world.getTileEntity(at);if(tile==null||tile.isInvalid()||tile.getWorld()!=world||!tile.getPos().equals(at))throw new IllegalArgumentException("Core block data is unavailable");return tile;}
    }
    /** Saved captured ownership must also be complete; provider-owned compounds remain opaque. */
    static void validate(VehicleStructure snapshot){
        Set<BlockPos> claimed=new HashSet<>();
        for(VehicleStructure.Cell root:snapshot.cells)if(root.state.getBlock() instanceof BlockConstructorCore&&root.state.getValue(BlockConstructorCore.FORMED)){
            if(!known(root))throw new IllegalArgumentException("Core block data is unavailable");BlockConstructorCore block=(BlockConstructorCore)root.state.getBlock();
            if(root.hasTileKey("ObserverInverted")&&!root.hasTileKey("ObserverInverted",1)||root.tileBoolean("ObserverInverted")&&!block.design.equals("standalone_dish")&&!block.design.equals("standalone_antenna"))throw new IllegalArgumentException("Invalid observer orientation");
            for(String key:new String[]{"ConstructorCoreX","ConstructorCoreZ"})if(root.hasTileKey(key)&&!root.hasTileKey(key,3))throw new IllegalArgumentException("Invalid Core corner data");
            int width=block.width,height=block.height,depth=block.depth;
            if(root.hasTileKey(com.vandorlabs.navigation.ObserverScale.WIDTH_TAG)){
                if(!root.hasTileKey(com.vandorlabs.navigation.ObserverScale.WIDTH_TAG,3)||!com.vandorlabs.navigation.ObserverScale.observer(block.design))throw new IllegalArgumentException("Invalid observer size");
                width=com.vandorlabs.navigation.ObserverScale.width(block.design,root.tileInteger(com.vandorlabs.navigation.ObserverScale.WIDTH_TAG));height=com.vandorlabs.navigation.ObserverScale.height(block.design,width);depth=width;
            }
            CoreLayout layout=new CoreLayout(width,height,depth,root.state.getValue(BlockVandorDirectional.FACING).getIndex(),root.tileInteger("ConstructorCoreX"),root.tileInteger("ConstructorCoreZ"));if(!layout.canonicalCorner())throw new IllegalArgumentException("Invalid Core corner");
            for(int x=0;x<layout.width;x++)for(int y=0;y<layout.height;y++)for(int z=0;z<layout.depth;z++){
                BlockPos at=root.pos.add(layout.x(x,z),root.tileBoolean("ObserverInverted")?-y:y,layout.z(x,z));VehicleStructure.Cell cell=snapshot.at(at);
                if(cell==null||!claimed.add(at))throw new IllegalArgumentException("Incomplete or overlapping carried Core footprint");
                if(at.equals(root.pos)){if(cell!=root)throw new IllegalArgumentException("Core identity changed");}
                else if(!(cell.state.getBlock() instanceof BlockConstructorPart)||!known(cell)||!cell.hasTileKey("ConstructorOwner",4)||!at.add(BlockPos.fromLong(cell.tileLong("ConstructorOwner"))).equals(root.pos)||!cell.hasTileKey("ConstructorMatter",1)||cell.tileBoolean("ConstructorMatter")!=layout.frame(x,y,z))throw new IllegalArgumentException("Incomplete carried Core reservations");
            }
        }
        for(VehicleStructure.Cell cell:snapshot.cells)if(cell.state.getBlock() instanceof BlockConstructorPart&&!claimed.contains(cell.pos))throw new IllegalArgumentException("Orphaned carried Core reservation; original attachment retained");
    }
    /** Inverse native-yaw edits stay inside the original authorized cell correspondence. */
    static VehicleStructure[] refreshed(VehicleStructure original,VehicleStructure placed,VehicleStructure current,int[] basis){
        if(!contains(original)||!yaw(basis)||original.cells.size()!=placed.cells.size()||original.cells.size()!=current.cells.size())throw new IllegalArgumentException("Restore the Core to its construction pose before editing it");
        int[] inverse=ArmLooseGearData.inverse(basis);List<VehicleStructure.Cell> restored=new ArrayList<>();
        for(int i=0;i<original.cells.size();i++){
            VehicleStructure.Cell before=original.cells.get(i),nativeCell=placed.cells.get(i),now=current.cells.get(i);
            if(!nativeCell.pos.equals(now.pos))throw new IllegalArgumentException("Core edit changed the authorized footprint");
            IBlockState state;NBTTagCompound data;
            if(typed(now)){
                if(now.state.getBlock()!=before.state.getBlock())throw new IllegalArgumentException("Restore the original Core block before moving it");
                state=state(now,now.state,inverse);data=data(now,inverse);
            }else{
                if(now.hasTileData()||!now.state.isFullCube())throw new IllegalArgumentException("Unsupported replacement inside the Core footprint");
                state=ArmPayload.rotateState(now.state,ArmGridTiles.axes(inverse));data=null;
            }
            if(state==null)throw new IllegalArgumentException("Core orientation cannot be represented as native blocks");
            restored.add(new VehicleStructure.Cell(before.pos,state,data==null?null:com.vandorlabs.vehicle.VehicleTileData.translated(data,before.pos.subtract(now.pos))));
        }
        VehicleStructure result=VehicleStructure.display(restored);validate(result);validate(current);ArmGridTiles.validate(result,current,basis);return new VehicleStructure[]{result,current};
    }
    private static boolean known(VehicleStructure.Cell cell){return cell.hasTileData()&&cell.tileString("id").equals(cell.state.getBlock() instanceof BlockConstructorCore?"vandorlabs:constructor":"vandorlabs:constructor_part");}
    static boolean yaw(int[] m){return com.vandorlabs.gear.core.CoreNativeOrientation.yaw(m);}
    static IBlockState state(VehicleStructure.Cell cell,IBlockState state,int[] basis){
        if(!typed(cell)||!known(cell)||state.getBlock()!=cell.state.getBlock()||!yaw(basis))return null;
        if(state.getBlock() instanceof BlockConstructorCore){if(state.getPropertyKeys().size()!=2)return null;EnumFacing face=state.getValue(BlockVandorDirectional.FACING);EnumFacing next=EnumFacing.getFacingFromVector(basis[0]*face.getFrontOffsetX()+basis[2]*face.getFrontOffsetZ(),0,basis[6]*face.getFrontOffsetX()+basis[8]*face.getFrontOffsetZ());state=state.withProperty(BlockVandorDirectional.FACING,next);}
        else if(!state.getPropertyKeys().isEmpty())return null;
        return state.getBlock().getStateFromMeta(state.getBlock().getMetaFromState(state))==state?state:null;
    }
    static NBTTagCompound data(VehicleStructure.Cell cell,int[] basis){
        if(!typed(cell)||!known(cell)||!yaw(basis))throw new IllegalArgumentException("Core orientation cannot be represented as native blocks");NBTTagCompound result=cell.tileData();
        if(cell.state.getBlock() instanceof BlockConstructorPart){if(!cell.hasTileKey("ConstructorOwner",4))throw new IllegalArgumentException("Missing Core owner offset");BlockPos old=BlockPos.fromLong(cell.tileLong("ConstructorOwner"));result.setLong("ConstructorOwner",new BlockPos(basis[0]*old.getX()+basis[2]*old.getZ(),old.getY(),basis[6]*old.getX()+basis[8]*old.getZ()).toLong());}return result;
    }
}
