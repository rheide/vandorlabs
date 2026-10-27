package com.vandorlabs.vehicle;

import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Explicit IE 0.12 native links; no private wire graph or optional client API is accessed. */
public final class VehicleForeignCompat {
    private static final String IE="blusunrize.immersiveengineering.common.blocks.";
    public static boolean type(TileEntity tile,String name){return tile!=null&&tile.getClass().getName().equals(IE+name);}
    public static boolean toolbox(TileEntity tile){return type(tile,"metal.TileEntityToolbox");}
    private static boolean post(TileEntity tile){return type(tile,"wooden.TileEntityWoodenPost");}
    private static boolean watermill(TileEntity tile){return type(tile,"wooden.TileEntityWatermill");}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
    private static NBTTagCompound data(TileEntity tile){return tile.writeToNBT(new NBTTagCompound());}
    /** Required source members are also used by bounded fragment discovery and exclusion guards. */
    public static Set<BlockPos> required(World world,BlockPos at){
        Set<BlockPos> building=BuildingBlockCompat.required(world,at);if(!building.isEmpty())return building;
        if(MekanismTankCompat.component(world.getTileEntity(at))||MekanismTankCompat.reactorGlass(world.getTileEntity(at)))return MekanismTankCompat.required(world,at);
        TileEntity tile=world.getTileEntity(at);if(!post(tile)&&!watermill(tile))return Collections.emptySet();
        NBTTagCompound tag=data(tile);Set<BlockPos> result=new LinkedHashSet<>();IBlockState state=world.getBlockState(at);
        if(post(tile)){
            int dummy=tag.getByte("dummy");require(dummy>=0&&dummy<=3||dummy>=5&&dummy<=8,"Invalid IE post member");
            BlockPos root=dummy<=3?at.down(dummy):at.offset(EnumFacing.getFront(dummy-3).getOpposite()).down(3);
            for(int i=0;i<4;i++)check(world,root.up(i),state,"wooden.TileEntityWoodenPost","dummy",i,result);
            for(EnumFacing side:EnumFacing.HORIZONTALS){BlockPos arm=root.up(3).offset(side);require(world.isBlockLoaded(arm,false),"Load the complete IE post boundary");TileEntity other=world.getTileEntity(arm);if(post(other)&&data(other).getByte("dummy")==3+side.getIndex())check(world,arm,state,"wooden.TileEntityWoodenPost","dummy",3+side.getIndex(),result);}
            require(result.contains(at),"Orphaned IE post arm");
        }else{
            int[] offset=tag.getIntArray("offset");EnumFacing facing=EnumFacing.getFront(tag.getInteger("facing"));require(offset.length==2&&Math.abs(offset[0])<=2&&Math.abs(offset[1])<=2&&facing.getAxis().isHorizontal(),"Invalid IE watermill link");
            BlockPos root=at.add(facing.getAxis()==EnumFacing.Axis.Z?-offset[0]:0,-offset[1],facing.getAxis()==EnumFacing.Axis.X?-offset[0]:0);
            for(int y=-2;y<=2;y++)for(int width=-2;width<=2;width++)if(Math.abs(y)<2||Math.abs(width)<2){
                BlockPos member=root.add(facing.getAxis()==EnumFacing.Axis.Z?width:0,y,facing.getAxis()==EnumFacing.Axis.X?width:0);require(world.isBlockLoaded(member,false),"Load the complete IE watermill");TileEntity other=world.getTileEntity(member);require(watermill(other)&&world.getBlockState(member)==state,"Include the complete IE watermill");NBTTagCompound value=data(other);require(value.getInteger("facing")==facing.getIndex()&&Arrays.equals(value.getIntArray("offset"),new int[]{width,y}),"Inconsistent IE watermill links");result.add(member.toImmutable());
            }
            require(result.contains(at),"Orphaned IE watermill corner");
        }
        return result;
    }
    private static void check(World world,BlockPos pos,IBlockState state,String type,String key,int expected,Set<BlockPos> result){require(world.isBlockLoaded(pos,false),"Load the complete IE post");TileEntity tile=world.getTileEntity(pos);require(type(tile,type)&&world.getBlockState(pos)==state&&data(tile).getByte(key)==expected,"Include the complete IE post and its arms");result.add(pos.toImmutable());}
    public static void validate(VehicleStructure snapshot,VehicleWorld view){
        Set<BlockPos> verified=new HashSet<>();for(VehicleStructure.Cell cell:snapshot.cells){if(verified.contains(cell.pos))continue;Set<BlockPos> members=required(view,cell.pos);for(BlockPos member:members)require(snapshot.at(member)!=null,"Moving/copy volume cuts an IE assembly; include every native part");verified.addAll(members);}
    }
    public static void rotate(TileEntity original,NBTTagCompound tag,Rotation rotation){
        if(post(original)){int dummy=tag.getByte("dummy");if(dummy>3){require(dummy>=5&&dummy<=8,"Invalid IE post arm direction");tag.setByte("dummy",(byte)(3+rotation.rotate(EnumFacing.getFront(dummy-3)).getIndex()));}}
        if(watermill(original)){
            NBTTagCompound before=data(original);int[] offset=tag.getIntArray("offset");require(offset.length==2,"Invalid IE watermill offset");EnumFacing facing=EnumFacing.getFront(before.getInteger("facing"));int turns=rotation.ordinal();for(int i=0;i<turns;i++){if(facing.getAxis()==EnumFacing.Axis.X)offset[0]=-offset[0];facing=Rotation.CLOCKWISE_90.rotate(facing);}tag.setIntArray("offset",offset);
        }
    }
    /** Only the native root item is obtainable; helper blocks are not separately charged. */
    public static boolean materialDummy(TileEntity tile){if(post(tile))return data(tile).getByte("dummy")!=0;if(watermill(tile)){int[] p=data(tile).getIntArray("offset");return p.length==2&&(p[0]!=0||p[1]!=0);}return false;}
    @SuppressWarnings("unchecked") public static List<ItemStack> inventory(TileEntity tile){
        if(!toolbox(tile))return null;
        try{Object result=tile.getClass().getMethod("getInventory").invoke(tile);require(result instanceof List&&((List<?>)result).size()==23,"Unsupported IE toolbox inventory");for(Object item:(List<?>)result)require(item instanceof ItemStack,"Invalid IE toolbox item");return (List<ItemStack>)result;}catch(ReflectiveOperationException failed){throw new IllegalArgumentException("Cannot preserve IE toolbox inventory",failed);}
    }
    public static ItemStack toolboxMaterial(TileEntity tile,IBlockState state){
        if(!toolbox(tile))return ItemStack.EMPTY;
        try{ItemStack result=(ItemStack)tile.getClass().getMethod("getTileDrop",net.minecraft.entity.player.EntityPlayer.class,IBlockState.class).invoke(tile,null,state);require(result!=null&&!result.isEmpty(),"Missing IE toolbox carrier item");net.minecraftforge.items.IItemHandler contents=result.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);List<ItemStack> stored=inventory(tile);require(contents!=null&&contents.getSlots()==stored.size(),"IE toolbox carrier cannot preserve all slots");for(int i=0;i<stored.size();i++)require(ItemStack.areItemStacksEqual(stored.get(i),contents.getStackInSlot(i)),"IE toolbox carrier changes stored contents");return result.copy();}catch(ReflectiveOperationException failed){throw new IllegalArgumentException("Cannot preserve IE toolbox material item",failed);}
    }
    public static void clearDetachedInventory(TileEntity tile){List<ItemStack> slots=inventory(tile);if(slots!=null)for(int i=0;i<slots.size();i++)slots.set(i,ItemStack.EMPTY);}
    private VehicleForeignCompat(){}
}
