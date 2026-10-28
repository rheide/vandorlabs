package com.vandorlabs.copier;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.*;

/** Exact obtainable items and actual retained inventories, including nested captured blocks. */
public final class CopyMaterials {
    public static final class Need {
        public final ItemStack item;public final boolean exact;public long count;
        Need(ItemStack item,long count,boolean exact){this.exact=exact;this.item=item.copy();this.item.setCount(1);this.count=count;}
        public String description(){return item.getDisplayName()+" x "+count;}
    }
    private CopyMaterials(){}
    public static List<Need> forSnapshot(VehicleStructure snapshot,VehicleWorld view,boolean creative){List<Need> result=new ArrayList<>();if(!creative)collect(snapshot,view,result,0);result.sort((a,b)->Boolean.compare(b.exact,a.exact));return Collections.unmodifiableList(result);}
    private static void add(List<Need> needs,ItemStack item,long count){add(needs,item,count,true);}
    private static void add(List<Need> needs,ItemStack item,long count,boolean exact){if(count<0)throw new IllegalArgumentException("Invalid stored item count");if(item.isEmpty()||count==0)return;if(count>Integer.MAX_VALUE)throw new IllegalArgumentException("Stored item count is too large to account for safely");for(Need need:needs)if(need.exact==exact&&matches(need.item,item)){need.count=Math.addExact(need.count,count);return;}if(needs.size()>=32768)throw new IllegalArgumentException("Too many distinct stored materials");needs.add(new Need(item,count,exact));}
    public static boolean matches(ItemStack a,ItemStack b){return !a.isEmpty()&&!b.isEmpty()&&a.getItem()==b.getItem()&&a.getMetadata()==b.getMetadata()&&ItemStack.areItemStackTagsEqual(a,b);}
    public static boolean satisfies(Need need,ItemStack supplied){
        if(need.exact)return matches(need.item,supplied);
        if(supplied.isEmpty()||need.item.getItem()!=supplied.getItem()||need.item.getMetadata()!=supplied.getMetadata())return false;
        if(!supplied.areCapsCompatible(need.item))return false;
        if(!supplied.hasTagCompound())return true;
        // Empty configured building blocks remain valid base materials. Never consume a filled carrier as an empty block.
        String domain=supplied.getItem().getRegistryName().getResourceDomain();return (domain.equals("vandorlabs")||domain.equals("minecraft"))&&!retained(supplied.getTagCompound(),0);
    }
    private static boolean retained(NBTBase tag,int depth){
        if(depth>32)return true;
        if(tag instanceof NBTTagCompound){NBTTagCompound data=(NBTTagCompound)tag;for(String key:data.getKeySet()){
            NBTBase value=data.getTag(key);if((key.equals("Items")||key.equals("Inventory")||key.equals("inventory"))&&value instanceof NBTTagList&&((NBTTagList)value).tagCount()>0)return true;
            if(key.equals("VandorTransmitters")&&value instanceof NBTTagCompound&&!((NBTTagCompound)value).hasNoTags())return true;
            if(key.equals("InsertedToken")&&value instanceof NBTTagCompound&&!new ItemStack((NBTTagCompound)value).isEmpty())return true;
            if((key.equals("Energy")||key.equals("energy")||key.equals("energyStored")||key.equals("electricityStored")||key.equals("Amount"))&&value instanceof net.minecraft.nbt.NBTPrimitive&&((net.minecraft.nbt.NBTPrimitive)value).getDouble()>0)return true;
            if(retained(value,depth+1))return true;
        }}else if(tag instanceof NBTTagList){NBTTagList list=(NBTTagList)tag;for(int i=0;i<list.tagCount();i++)if(retained(list.get(i),depth+1))return true;}return false;
    }
    private static void collect(VehicleStructure snapshot,VehicleWorld view,List<Need> needs,int depth){
        if(depth>=8)throw new IllegalArgumentException("Captured material nesting exceeds eight structures");
        for(VehicleStructure.Cell cell:snapshot.cells){if(cell.tileData()!=null&&cell.tileData().hasKey("LootTable",8)&&!cell.tileData().getString("LootTable").isEmpty())throw new IllegalArgumentException("Open loot containers before Survival copying them");TileEntity tile=view.getTileEntity(cell.pos);if(VehicleForeignCompat.toolbox(tile)){ItemStack carrier=VehicleForeignCompat.toolboxMaterial(tile,cell.state);add(needs,carrier,1);continue;}ItemStack base=base(cell,view);boolean carrier=false;
            if(storedResources(tile,cell.tileData())){
                if(!base.isEmpty()&&(tile instanceof TileEntityBattery||tile instanceof TileEntityHexTank)){ItemStack pick=cell.state.getBlock().getPickBlock(cell.state,null,view,cell.pos,null);NBTTagCompound source=cell.tileData(),data=pick.getSubCompound("BlockEntityTag");if(data==null||!resourceData(source,data))throw new IllegalArgumentException("Stored resources have no lossless material item: "+cell.state.getBlock().getLocalizedName());base=pick;carrier=true;}
                else throw new IllegalArgumentException("Empty stored energy, fluids or fuel before Survival copying "+cell.state.getBlock().getLocalizedName());
            }
            add(needs,base,base.isEmpty()?0:base.getCount(),carrier);
            if(!carrier)inventory(tile,needs);
            NBTTagCompound data=cell.tileData();if(data==null)continue;
            if(data.hasKey("ArmPayload",10)){
                NBTTagCompound payload=data.getCompoundTag("ArmPayload");if(payload.getTagList("OwnershipJournal",10).tagCount()>0)throw new IllegalArgumentException("Finish the arm's recovery before copying it");
                if(!payload.getBoolean("Materialized")){VehicleStructure held=VehicleStructure.readDisplay(payload.getCompoundTag("Blocks"));VehicleWorld nested=new VehicleWorld(view.parent,held);MovingBlockSupport.validate(held,nested);collect(held,nested,needs,depth+1);}
            }
            if(tile instanceof TileEntityRampController)ramp(snapshot,view,cell,needs,depth);
        }
    }
    private static boolean resourceData(NBTTagCompound source,NBTTagCompound item){for(String key:new String[]{"Energy","Tank","ContainedFluids"})if(source.hasKey(key)&&!Objects.equals(source.getTag(key),item.getTag(key)))return false;return true;}
    private static boolean storedResources(TileEntity tile,NBTTagCompound tag){
        if(tile==null||tag==null)return false;
        com.vandorlabs.blocks.ContainedFluids contained=WaterloggedParts.data(tile);if(contained!=null&&contained.any())return true;
        if(tile instanceof net.minecraft.tileentity.TileEntityFurnace&&tag.getShort("BurnTime")>0)return true;
        for(EnumFacing side:new EnumFacing[]{null,EnumFacing.DOWN,EnumFacing.UP,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.EAST}){
            net.minecraftforge.energy.IEnergyStorage energy=tile.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,side);if(energy!=null&&energy.getEnergyStored()>0)return true;
            net.minecraftforge.fluids.capability.IFluidHandler fluids=tile.getCapability(net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,side);if(fluids!=null)for(net.minecraftforge.fluids.capability.IFluidTankProperties tank:fluids.getTankProperties())if(tank.getContents()!=null&&tank.getContents().amount>0)return true;
        }
        if(tile.getClass().getName().equals("mekanism.common.tile.TileEntityGasTank"))try{Object gas=tile.getClass().getField("gasTank").get(tile);return ((Number)gas.getClass().getMethod("getStored").invoke(gas)).intValue()>0;}catch(ReflectiveOperationException unsupported){throw new IllegalArgumentException("Cannot account for the gas tank contents exactly");}
        if(tile.getClass().getName().equals("mekanism.common.tile.TileEntityEnergyCube")||tile.getClass().getName().startsWith("blusunrize.immersiveengineering.common.blocks.metal.TileEntityCapacitor")){
            for(String key:new String[]{"electricityStored","energyStored","Energy"})if(tag.hasKey(key,99)&&tag.getDouble(key)>0)return true;
        }
        return false;
    }
    private static void inventory(TileEntity tile,List<Need> needs){
        if(tile==null)return;
        NBTTagCompound transmitters=com.vandorlabs.redstone.TransmitterSlots.savedSlots(tile);for(String key:transmitters.getKeySet()){ItemStack transmitter=new ItemStack(transmitters.getCompoundTag(key));if(!com.vandorlabs.items.ItemRedstoneTransmitter.valid(transmitter)||transmitter.getCount()!=1)throw new IllegalArgumentException("Invalid inserted transmitter; remove it before Survival copying");add(needs,transmitter,1);}
        if(tile.getClass().getName().equals("mekanism.common.tile.TileEntityBin"))try{long count=((Number)tile.getClass().getMethod("getItemCount").invoke(tile)).longValue();if(count<0)throw new IllegalArgumentException("Invalid stored bin count");ItemStack type=(ItemStack)tile.getClass().getField("itemType").get(tile);if(count>0&&type.isEmpty())throw new IllegalArgumentException("Stored bin items cannot be identified");add(needs,type,count);return;}catch(ReflectiveOperationException unsupported){throw new IllegalArgumentException("Cannot account for the bin inventory exactly");}
        List<ItemStack> foreign=VehicleForeignCompat.inventory(tile);if(foreign!=null){for(ItemStack item:foreign)add(needs,item,item.getCount());return;}
        if(tile instanceof IInventory){IInventory inventory=(IInventory)tile;int slots=inventory.getSizeInventory();if(slots<0||slots>8192)throw new IllegalArgumentException("Inventory slot count exceeds the copy limit");for(int slot=0;slot<slots;slot++){ItemStack item=inventory.getStackInSlot(slot);add(needs,item,item.getCount());}}
        else{IItemHandler handler=tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);if(handler==null)for(EnumFacing side:EnumFacing.values()){handler=tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,side);if(handler!=null)break;}if(handler!=null){int slots=handler.getSlots();if(slots<0||slots>8192)throw new IllegalArgumentException("Inventory slot count exceeds the copy limit");for(int slot=0;slot<slots;slot++){ItemStack item=handler.getStackInSlot(slot);add(needs,item,item.getCount());}}}
        if(tile instanceof TileEntitySpaceDoor){TileEntitySpaceDoor door=(TileEntitySpaceDoor)tile;for(int side=0;side<2;side++){ItemStack token=door.keyReader(side).getStackInSlot(0);add(needs,token,token.getCount());}}
        if(tile instanceof net.minecraft.tileentity.TileEntityBrewingStand){NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());if(tag.getByte("Fuel")>0)add(needs,new ItemStack(net.minecraft.init.Items.BLAZE_POWDER),1);}
    }
    private static ItemStack base(VehicleStructure.Cell cell,VehicleWorld view){
        Block block=cell.state.getBlock();TileEntity tile=view.getTileEntity(cell.pos);
        if(VehicleForeignCompat.materialDummy(tile))return ItemStack.EMPTY;
        if(block instanceof com.vandorlabs.programmer.ProgrammerBlock&&cell.state.getValue(com.vandorlabs.programmer.ProgrammerBlock.PART)!=0)return ItemStack.EMPTY;
        if(block instanceof BlockConstructorPart)return cell.tileData()!=null&&cell.tileData().getBoolean("ConstructorMatter")?new ItemStack(Block.getBlockFromName("vandorlabs:programmable_matter_block")):ItemStack.EMPTY;
        if(block instanceof BlockControlledRamp||block instanceof BlockPistonExtension||tile instanceof TileEntityCockpitPart)return ItemStack.EMPTY;
        if(block instanceof BlockLargeProgrammableDoor){TileEntityLargeProgrammableDoor root=((BlockLargeProgrammableDoor)block).root(view,cell.pos);if(root==null)throw new IllegalArgumentException("Complete the large door before copying it");if(!root.getPos().equals(cell.pos))return ItemStack.EMPTY;}
        else if(block instanceof BlockDoor&&cell.state.getValue(BlockDoor.HALF)==BlockDoor.EnumDoorHalf.UPPER)return ItemStack.EMPTY;
        if(block instanceof BlockBed&&cell.state.getValue(BlockBed.PART)==BlockBed.EnumPartType.HEAD)return ItemStack.EMPTY;
        if(block instanceof BlockDoublePlant&&cell.state.getValue(BlockDoublePlant.HALF)==BlockDoublePlant.EnumBlockHalf.UPPER)return ItemStack.EMPTY;
        if((block instanceof BlockBridgeChair||block instanceof BlockConnectedSeat||block instanceof BlockPilotSeat&&!(block instanceof BlockCockpit))&&cell.state.getValue(BlockBridgeChair.UPPER))return ItemStack.EMPTY;
        if(tile instanceof TileEntityCanopy&&((TileEntityCanopy)tile).anchor!=null&&!((TileEntityCanopy)tile).anchor.equals(cell.pos))return ItemStack.EMPTY;
        if(tile instanceof TileEntityLandingGear&&((TileEntityLandingGear)tile).owner()!=null)return ItemStack.EMPTY;
        if(block instanceof BlockWheel&&!((BlockWheel)block).root(cell.pos,cell.state).equals(cell.pos))return ItemStack.EMPTY;
        if(block instanceof com.vandorlabs.gear.LargeGearBlock&&!((com.vandorlabs.gear.LargeGearBlock)block).componentRoot(cell.pos,cell.state).equals(cell.pos))return ItemStack.EMPTY;
        ItemStack result=block.getItem(view,cell.pos,cell.state);if(result.isEmpty())throw new IllegalArgumentException("No obtainable material item for "+block.getLocalizedName());result=result.copy();int count=result.getCount();if(block instanceof BlockSlab&&((BlockSlab)block).isDouble())count=2;if(block instanceof BlockSnow)count=cell.state.getValue(BlockSnow.LAYERS);if(count<1||count>64)throw new IllegalArgumentException("Block material quantity is not recoverable exactly");result.setCount(count);result.setTagCompound(null);return result;
    }
    private static BlockPos position(NBTTagCompound tag){return tag.hasKey("X",99)?new BlockPos(tag.getInteger("X"),tag.getInteger("Y"),tag.getInteger("Z")):BlockPos.fromLong(tag.getLong("Pos"));}
    private static void ramp(VehicleStructure outer,VehicleWorld view,VehicleStructure.Cell controller,List<Need> needs,int depth){
        NBTTagCompound data=controller.tileData();NBTTagList sources=data.getTagList("Sources",10),tiles=data.getTagList("RampSourceTiles",10);if(sources.tagCount()==0)return;
        IBlockState original=NBTUtil.readBlockState(data.getCompoundTag("Original"));Map<BlockPos,NBTTagCompound> saved=new HashMap<>();for(int i=0;i<tiles.tagCount();i++)saved.put(position(tiles.getCompoundTagAt(i)),tiles.getCompoundTagAt(i).getCompoundTag("Tile"));
        for(int i=0;i<sources.tagCount();i++){BlockPos at=position(sources.getCompoundTagAt(i));VehicleStructure.Cell live=outer.at(at);if(live!=null&&live.state==original)continue;NBTTagCompound tile=saved.get(at);VehicleStructure nested=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,original,tile==null?null:VehicleTileData.translated(tile,BlockPos.ORIGIN.subtract(at)))));VehicleWorld local=new VehicleWorld(view.parent,nested);MovingBlockSupport.validate(nested,local);collect(nested,local,needs,depth+1);}
    }
}
