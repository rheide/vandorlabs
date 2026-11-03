package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.items.ModItems;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.*;
import net.minecraftforge.items.*;

/** Read-only mass accounting, including independent chest halves and joined per-cell tanks. */
final class VehicleWeightRuntimeChecks {
    private static int assertions;
    private static void require(boolean ok,String why){assertions++;if(!ok)throw new IllegalStateException("vehicle-weight: "+why);}
    private static void close(double a,double b,String why){require(Math.abs(a-b)<1E-8,why+" actual="+a+" expected="+b);}
    private static VehicleStructure.Cell cell(BlockPos at,IBlockState state,TileEntity tile){if(tile!=null)tile.setPos(at);return new VehicleStructure.Cell(at,state,tile==null?null:tile.writeToNBT(new NBTTagCompound()));}
    private static VehicleWeight.Result estimate(VehicleStructure source,VehicleWorld view){
        NBTTagCompound before=source.write();Map<BlockPos,NBTTagCompound> tiles=new HashMap<>();for(TileEntity tile:view.tiles.values())if(!(tile.getClass().isAnonymousClass()))tiles.put(tile.getPos(),tile.writeToNBT(new NBTTagCompound()));
        VehicleWeight weight=new VehicleWeight(source,view);int slices=0;while(!weight.step(17,System.nanoTime()+2_000_000L)){if(++slices>20000)throw new IllegalStateException("Weight work did not terminate");}
        require(before.equals(source.write()),"source snapshot remains immutable");for(Map.Entry<BlockPos,NBTTagCompound> tile:tiles.entrySet())require(tile.getValue().equals(view.getTileEntity(tile.getKey()).writeToNBT(new NBTTagCompound())),"inventory/fluid reads never extract or drain "+tile.getKey());
        return weight.result();
    }
    private static double estimate(WorldServer world,VehicleStructure.Cell... cells){VehicleStructure source=VehicleStructure.display(Arrays.asList(cells));VehicleWeight.Result result=estimate(source,new VehicleWorld(world,source));require(!result.incomplete,"known inventories and fluids completely accounted");return result.units;}
    private static double block(WorldServer world,Block block){return estimate(world,cell(BlockPos.ORIGIN,block.getDefaultState(),null));}
    static void run(WorldServer world,ItemStack writtenChip)throws Exception{
        assertions=0;double stone=block(world,Blocks.STONE),wood=block(world,Blocks.PLANKS),iron=block(world,Blocks.IRON_BLOCK),gold=block(world,Blocks.GOLD_BLOCK);
        close(stone,1,"stone reference unit");require(gold>iron&&iron>stone&&stone>wood,"material ordering");close(block(world,Blocks.WOODEN_SLAB),wood*.5,"slab uses half material");close(block(world,Blocks.OAK_STAIRS),wood*.75,"stairs use three quarters");require(block(world,Blocks.GLASS_PANE)<block(world,Blocks.GLASS),"thin pane lighter than full glass");
        TileEntityChest a=new TileEntityChest(),b=new TileEntityChest();double empty=estimate(world,cell(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState(),a));
        a.setInventorySlotContents(2,new ItemStack(Blocks.IRON_BLOCK,64));b.setInventorySlotContents(7,new ItemStack(Blocks.PLANKS,64));
        close(estimate(world,cell(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState(),a),cell(BlockPos.ORIGIN.east(),Blocks.CHEST.getDefaultState(),b)),empty*2+64*(iron+wood),"adjacent chest halves counted once each");
        a.clear();ItemStack shulker=new ItemStack(Blocks.PURPLE_SHULKER_BOX);a.setInventorySlotContents(0,shulker.copy());double unfilled=estimate(world,cell(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState(),a));NBTTagList list=new NBTTagList();NBTTagCompound stored=new ItemStack(Blocks.IRON_BLOCK,16).writeToNBT(new NBTTagCompound());stored.setByte("Slot",(byte)4);list.appendTag(stored);NBTTagCompound nested=new NBTTagCompound();nested.setTag("Items",list);shulker.setTagInfo("BlockEntityTag",nested);a.setInventorySlotContents(0,shulker);
        close(estimate(world,cell(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState(),a)),unfilled+16*iron,"nested shulker contents");
        a.clear();ItemStack blank=new ItemStack(ModItems.DATA_CHIP);VehicleChip.blank(blank);a.setInventorySlotContents(0,blank);double blankMass=estimate(world,cell(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState(),a));a.setInventorySlotContents(0,writtenChip.copy());close(estimate(world,cell(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState(),a)),blankMass,"chip design bytes do not become physical cargo");
        Block tank=Block.getBlockFromName("vandorlabs:hexagonal_fuel_tank");IBlockState tankState=tank.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.UP);
        TileEntityHexTank lower=new TileEntityHexTank(),upper=new TileEntityHexTank();double tanks=estimate(world,cell(BlockPos.ORIGIN,tankState,lower),cell(BlockPos.ORIGIN.up(),tankState,upper));lower.tank.setFluid(new FluidStack(FluidRegistry.WATER,1000));upper.tank.setFluid(new FluidStack(FluidRegistry.WATER,2000));
        close(estimate(world,cell(BlockPos.ORIGIN,tankState,lower),cell(BlockPos.ORIGIN.up(),tankState,upper)),tanks+1.2,"joined tank fluid counted by per-cell ownership");
        a.clear();a.setInventorySlotContents(0,new ItemStack(Items.WATER_BUCKET));double water=estimate(world,cell(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState(),a));a.setInventorySlotContents(0,new ItemStack(Items.BUCKET));close(water-estimate(world,cell(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState(),a)),.4,"carried bucket contents");
        a.clear();a.setInventorySlotContents(0,new ItemStack(Items.IRON_INGOT,9));close(estimate(world,cell(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState(),a))-empty,iron,"nine metal ingots equal their storage block");
        a.setInventorySlotContents(0,new ItemStack(Items.DIAMOND_SWORD));double sword=estimate(world,cell(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState(),a));a.setInventorySlotContents(0,new ItemStack(Items.DIAMOND));require(sword>estimate(world,cell(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState(),a)),"diamond tools are not treated as one loose gem");
        capability(world,iron);
        TileEntityVehicleController old=new TileEntityVehicleController();require(!old.writeToNBT(new NBTTagCompound()).hasKey("VehicleWeight"),"legacy default omits annotation");old.vehicleWeight=123.5;old.vehicleWeightVersion=VehicleWeight.VERSION;old.vehicleWeightIncomplete=true;TileEntityVehicleController loaded=new TileEntityVehicleController();loaded.readFromNBT(old.writeToNBT(new NBTTagCompound()));close(loaded.vehicleWeight,123.5,"weight NBT round trip");require(loaded.vehicleWeightVersion==VehicleWeight.VERSION&&loaded.vehicleWeightIncomplete,"estimator version and partial marker persist");
        System.out.println("[vandorlabs][reprolab] vehicle-weight PASS assertions="+assertions+" materials partial-shapes stack-counts double-chest nested-shulker joined-tanks bucket-contents shared-capability no-design-ghost-mass read-only bounded-work NBT");
    }
    private static void capability(WorldServer world,double iron){
        VehicleStructure source=VehicleStructure.display(Arrays.asList(cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),null),cell(BlockPos.ORIGIN.east(),Blocks.STONE.getDefaultState(),null)));VehicleWorld view=new VehicleWorld(world,source);ItemStackHandler handler=new ItemStackHandler(2);handler.setStackInSlot(1,new ItemStack(Blocks.IRON_BLOCK,5));
        for(BlockPos at:new BlockPos[]{BlockPos.ORIGIN,BlockPos.ORIGIN.east()}){TileEntity tile=new TileEntity(){@Override public <T>T getCapability(Capability<T> capability,EnumFacing side){return capability==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY?(T)handler:null;}};tile.setWorld(view);tile.setPos(at);view.tiles.put(at,tile);}
        VehicleWeight.Result result=estimate(source,view);close(result.units,2+5*iron,"shared inventory handler counted once");require(handler.getStackInSlot(1).getCount()==5,"capability not consumed");
        ItemStackHandler oversized=new ItemStackHandler(9000);TileEntity tile=new TileEntity(){@Override public <T>T getCapability(Capability<T> capability,EnumFacing side){return capability==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY?(T)oversized:null;}};tile.setWorld(view);tile.setPos(BlockPos.ORIGIN);view.tiles.clear();view.tiles.put(BlockPos.ORIGIN,tile);require(estimate(source,view).incomplete,"oversized unknown handler remains bounded and marked partial");
    }
    private VehicleWeightRuntimeChecks(){}
}
