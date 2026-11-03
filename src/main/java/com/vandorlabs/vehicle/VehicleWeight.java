package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.*;
import net.minecraftforge.items.*;
import java.util.*;
import java.util.function.IntFunction;
import java.util.regex.Pattern;

/** Informational scan estimate. One full stone block is one weight unit; no physics reads this value. */
public final class VehicleWeight {
    public static final int VERSION=1;
    private static final int MAX_DEPTH=8,MAX_WORK=262144,MAX_SLOTS=8192;
    private static final Pattern CAMEL=Pattern.compile("([a-z])([A-Z])");
    private static final Pattern SEPARATORS=Pattern.compile("[^a-z]+");
    public static final class Result {
        public final double units;public final boolean incomplete;
        Result(double units,boolean incomplete){this.units=units;this.incomplete=incomplete;}
    }
    private interface Task{void run();}
    private final VehicleStructure structure;private final VehicleWorld view;
    private final ArrayDeque<Task> pending=new ArrayDeque<>();
    private final Map<IBlockState,Double> blocks=new IdentityHashMap<>();
    private final Map<Long,Double> items=new HashMap<>();
    private final Set<Object> inventories=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
    private final Set<String> sharedTanks=new HashSet<>();
    private final Set<Object> fluidHandlers=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
    private final int depth;
    private int cell,work;private double units;private boolean incomplete,done;
    public VehicleWeight(VehicleStructure structure,VehicleWorld view){this(structure,view,0);}
    private VehicleWeight(VehicleStructure structure,VehicleWorld view,int depth){this.structure=structure;this.view=view;this.depth=depth;}
    public boolean step(int budget,long deadline){
        if(done)return true;
        int count=0;
        while(count++<budget){
            if(cell<structure.cells.size()){
                VehicleStructure.Cell next=structure.cells.get(cell++);add(blocks.computeIfAbsent(next.state,VehicleWeight::blockUnits));
                TileEntity tile=view.getTileEntity(next.pos);
                if(tile!=null)try{contents(next,tile,depth);}catch(RuntimeException|LinkageError|ReflectiveOperationException unavailable){incomplete=true;VehicleCompatibility.warn(next.state.getBlock(),"weight estimate",unavailable);}
            }else if(!pending.isEmpty()){
                if(++work>MAX_WORK){pending.clear();incomplete=true;done=true;return true;}
                try{pending.removeFirst().run();}catch(RuntimeException|LinkageError unavailable){incomplete=true;VehicleCompatibility.warn(structure.cells.get(0).state.getBlock(),"stored-content weight estimate",unavailable);}
            }else{done=true;return true;}
            if((count&31)==0&&System.nanoTime()>=deadline)return false;
        }
        return false;
    }
    public Result result(){if(!done)throw new IllegalStateException("Weight estimate pending");return new Result(units,incomplete);}
    private void queue(Task task){if(pending.size()<MAX_WORK)pending.addLast(task);else incomplete=true;}
    private void add(double amount){if(Double.isFinite(amount)&&amount>=0&&Double.isFinite(units+amount))units+=amount;else incomplete=true;}
    private static String words(String hint){return "_"+SEPARATORS.matcher(CAMEL.matcher(hint).replaceAll("$1_$2").toLowerCase(Locale.ROOT)).replaceAll("_")+"_";}
    private static boolean has(String words,String... choices){for(String choice:choices)if(words.contains("_"+choice+"_"))return true;return false;}
    private static double material(String hint,Material fallback){
        String w=words(hint);double result;
        if(has(w,"gold","golden","uranium","tungsten"))result=7.5;
        else if(has(w,"osmium","iridium","platinum"))result=8.5;
        else if(has(w,"lead"))result=4.5;
        else if(has(w,"silver"))result=4.2;
        else if(has(w,"copper","bronze","brass"))result=3.5;
        else if(has(w,"iron","steel","metal","nickel","invar","tin","zinc"))result=3;
        else if(has(w,"aluminum","aluminium"))result=1.1;
        else if(has(w,"titanium"))result=1.8;
        else if(has(w,"wood","wooden","plank","planks","log","oak","spruce","birch","jungle","acacia","bamboo"))result=.25;
        else if(has(w,"wool","cloth","fabric","leather"))result=.08;
        else if(has(w,"leaves","leaf","plant","vine"))result=.03;
        else if(has(w,"snow"))result=.1;
        else if(has(w,"ice"))result=.37;
        else if(has(w,"rubber","plastic"))result=.4;
        else if(has(w,"dirt","earth","sand","gravel","soil"))result=.6;
        else if(has(w,"brick","bricks","clay"))result=.8;
        else if(has(w,"diamond"))result=1.4;
        else if(has(w,"glass","stone","rock","marble","granite","basalt","quartz","concrete","obsidian","emerald"))result=1;
        else if(fallback==Material.IRON||fallback==Material.ANVIL)result=3;
        else if(fallback==Material.WOOD)result=.25;
        else if(fallback==Material.CLOTH||fallback==Material.CARPET)result=.08;
        else if(fallback==Material.LEAVES||fallback==Material.PLANTS||fallback==Material.VINE)result=.03;
        else if(fallback==Material.GROUND||fallback==Material.GRASS||fallback==Material.SAND)result=.6;
        else if(fallback==Material.SNOW||fallback==Material.CRAFTED_SNOW)result=.1;
        else if(fallback==Material.ICE||fallback==Material.PACKED_ICE)result=.37;
        else result=1;
        return has(w,"ore")?Math.max(1,1+.2*(result-1)):result;
    }
    private static double blockUnits(IBlockState state){
        Block b=state.getBlock();Material m=state.getMaterial();if(m==Material.AIR||m==Material.FIRE)return 0;
        String hint=b.getRegistryName()==null?b.getUnlocalizedName():b.getRegistryName().toString();
        for(net.minecraft.block.properties.IProperty<?> property:state.getPropertyKeys())if(property.getName().equals("variant")||property.getName().equals("type")||property.getName().equals("material"))hint+="_"+state.getValue(property);
        double fraction=1;String w=words(hint);
        if(b instanceof BlockSlab)fraction=((BlockSlab)b).isDouble()?1:.5;
        else if(b instanceof BlockStairs)fraction=.75;
        else if(b instanceof BlockSnow)fraction=state.getValue(BlockSnow.LAYERS)/8D;
        else if(b instanceof BlockTrapDoor||b instanceof BlockDoor||has(w,"door","trapdoor","canopy"))fraction=.1875;
        else if(b instanceof BlockPane||b instanceof BlockFence||has(w,"bars","bar","fence"))fraction=.125;
        else if(b instanceof BlockCarpet||b instanceof BlockPressurePlate||b instanceof BlockRailBase)fraction=.0625;
        else if(b instanceof BlockTorch||b instanceof BlockButton)fraction=.015625;
        else if(has(w,"slab"))fraction=has(w,"double")?1:.5;
        else if(has(w,"stairs","stair"))fraction=.75;
        else if(has(w,"wall","panel","screen","viewscreen","console","tank","chair","seat"))fraction=.25;
        else if(has(w,"chest","wheel","track","gear","ramp"))fraction=.5;
        else if(has(w,"card","chip","slot","switch","slider","dial"))fraction=.05;
        return material(hint,m)*fraction;
    }
    private double itemUnits(ItemStack stack){
        long key=(long)Item.getIdFromItem(stack.getItem())<<32|(stack.getMetadata()&0xFFFFFFFFL);Double known=items.get(key);if(known!=null)return known;
        Item item=stack.getItem();String name=item.getRegistryName()==null?item.getUnlocalizedName(stack):item.getRegistryName().toString()+"_"+item.getUnlocalizedName(stack);String w=words(name);double weight;
        if(item instanceof ItemBlock){Block block=((ItemBlock)item).getBlock();IBlockState state;try{state=block.getStateFromMeta(stack.getMetadata());}catch(RuntimeException badMeta){state=block.getDefaultState();incomplete=true;}weight=blocks.computeIfAbsent(state,VehicleWeight::blockUnits);}
        else if(has(w,"helmet","chestplate","leggings","boots"))weight=material(name,Material.IRON)*(has(w,"helmet")?5:has(w,"chestplate")?8:has(w,"leggings")?7:4)/9;
        else if(has(w,"pickaxe","axe","sword","shovel","hoe"))weight=material(name,Material.IRON)*(has(w,"pickaxe","axe")?3:has(w,"sword","hoe")?2:1)/9+.01;
        else if(has(w,"ingot","gem","diamond","emerald","dust"))weight=material(name,Material.ROCK)/9;
        else if(has(w,"nugget"))weight=material(name,Material.ROCK)/81;
        else if(has(w,"plate","sheet"))weight=material(name,Material.IRON)/18;
        else if(has(w,"bucket"))weight=.05;
        else if(has(w,"chip"))weight=.002;
        else if(has(w,"card","paper","map"))weight=.001;
        else if(has(w,"stick"))weight=.005;
        else weight=.01*material(name,Material.ROCK);
        items.put(key,weight);return weight;
    }
    private void inventory(Object identity,IntFunction<ItemStack> read,int slots,double copies,int depth){
        if(identity!=null&&!inventories.add(identity))return;if(slots<0||slots>MAX_SLOTS){incomplete=true;slots=Math.max(0,Math.min(MAX_SLOTS,slots));}final int size=slots;
        queue(new Task(){int slot;public void run(){if(slot>=size)return;ItemStack stack=read.apply(slot++);if(!stack.isEmpty())stack(stack,copies,depth);if(slot<size)queue(this);}});
    }
    private void stack(ItemStack stack,double copies,int depth){
        if(stack.isEmpty())return;double count=copies*Math.max(0,stack.getCount());add(itemUnits(stack)*count);
        if(depth>=MAX_DEPTH){incomplete=true;return;}
        IItemHandler inventory=stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);
        NBTTagCompound block=stack.getSubCompound("BlockEntityTag");
        if(inventory!=null)inventory(null,inventory::getStackInSlot,inventory.getSlots(),count,depth+1);
        else if(block!=null)items(block.getTagList("Items",10),count,depth+1);
        IFluidHandlerItem fluid=stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY,null);
        if(fluid!=null)fluids(fluid,count);else if(block!=null){for(String key:new String[]{"Tank","Fluid","cachedFluid"})if(block.hasKey(key,10)){fluid(block.getCompoundTag(key),count);break;}contained(block,count);}
    }
    private void items(NBTTagList list,double copies,int depth){
        if(list.tagCount()==0)return;queue(new Task(){int index;public void run(){stack(new ItemStack(list.getCompoundTagAt(index++)),copies,depth);if(index<list.tagCount())queue(this);}});
    }
    private void fluid(FluidStack fluid,double copies){if(fluid==null||fluid.amount<=0)return;double density=Math.max(1,Math.abs((double)fluid.getFluid().getDensity(fluid)));add(copies*.4*fluid.amount/1000D*density/1000D);}
    private void fluid(NBTTagCompound data,double copies){FluidStack fluid=FluidStack.loadFluidStackFromNBT(data);if(fluid!=null)fluid(fluid,copies);else if(data.getInteger("Amount")>0){add(copies*.4*data.getInteger("Amount")/1000D);incomplete=true;}}
    private void fluids(IFluidHandler handler,double copies){IFluidTankProperties[] tanks=handler.getTankProperties();if(tanks.length>MAX_SLOTS)incomplete=true;for(int i=0;i<Math.min(MAX_SLOTS,tanks.length);i++)fluid(tanks[i].getContents(),copies);}
    private void contained(NBTTagCompound tag,double copies){NBTTagCompound data=tag.getCompoundTag("ContainedFluids");for(int side=0;side<2;side++)if(data.hasKey("Side"+side,10))fluid(data.getCompoundTag("Side"+side),copies);}
    private void contents(VehicleStructure.Cell cell,TileEntity tile,int depth)throws ReflectiveOperationException{
        if(MekanismTankCompat.tank(tile)){
            NBTTagCompound data=cell.tileData();String key=data.getString("cachedID");if(key.isEmpty()){key=cell.pos.toString();incomplete|=data.hasKey("cachedFluid",10);}
            if(sharedTanks.add("mekanism:"+key)){fluid(data.getCompoundTag("cachedFluid"),1);items(data.getTagList("Items",10),1,depth);}
        }else{
            if(tile.getClass().getName().equals("mekanism.common.tile.TileEntityBin")){long count=((Number)tile.getClass().getMethod("getItemCount").invoke(tile)).longValue();ItemStack type=(ItemStack)tile.getClass().getField("itemType").get(tile);if(count>0&&!type.isEmpty())stack(type,count/Math.max(1D,type.getCount()),depth);}
            else{List<ItemStack> foreign=VehicleForeignCompat.inventory(tile);
                if(foreign!=null){for(ItemStack item:foreign)queue(()->stack(item,1,depth));}
                else if(tile instanceof IInventory){IInventory inventory=(IInventory)tile;if(!inventory.isEmpty())inventory(inventory,inventory::getStackInSlot,inventory.getSizeInventory(),1,depth);}
                else{IItemHandler handler=tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);if(handler==null)for(EnumFacing side:EnumFacing.values()){handler=tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,side);if(handler!=null)break;}if(handler!=null)inventory(handler,handler::getStackInSlot,handler.getSlots(),1,depth);}
            }
            if(tile instanceof TileEntityHexTank)fluid(((TileEntityHexTank)tile).tank.getFluid(),1);
            else{IFluidHandler handler=tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,null);if(handler==null)for(EnumFacing side:EnumFacing.values()){handler=tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,side);if(handler!=null)break;}if(handler!=null&&fluidHandlers.add(handler))fluids(handler,1);}
            if(tile.getClass().getName().equals("mekanism.common.tile.TileEntityGasTank")){Object tank=tile.getClass().getField("gasTank").get(tile);add(Math.max(0,((Number)tank.getClass().getMethod("getStored").invoke(tank)).doubleValue())*.0005/1000D);}
        }
        ContainedFluids contained=WaterloggedParts.data(tile);if(contained!=null)for(int side=0;side<2;side++)fluid(contained.get(side),1);
        if(tile instanceof TileEntityAnimatedScreenSelector){ItemStack token=((TileEntityAnimatedScreenSelector)tile).ownedChip();if(!token.isEmpty())queue(()->stack(token,1,depth));}
        if(tile instanceof TileEntitySpaceDoor)for(int side=0;side<2;side++){ItemStack token=((TileEntitySpaceDoor)tile).keyReader(side).getStackInSlot(0);if(!token.isEmpty())queue(()->stack(token,1,depth));}
        if(tile instanceof com.vandorlabs.gear.GearTileEntity&&((com.vandorlabs.gear.GearTileEntity)tile).hasRetainedPayload()){
            NBTTagCompound payload=cell.tileData().getCompoundTag("ArmPayload");if(!payload.getBoolean("Materialized")){
                if(depth>=MAX_DEPTH)incomplete=true;
                else{VehicleStructure nested=VehicleStructure.readDisplay(payload.getCompoundTag("Blocks"));VehicleWorld nestedView=new VehicleWorld(view.parent,nested);VehicleWeight estimate=new VehicleWeight(nested,nestedView,depth+1);queue(new Task(){public void run(){if(estimate.step(256,System.nanoTime()+1_000_000L)){Result result=estimate.result();add(result.units);incomplete|=result.incomplete;}else queue(this);}});}
            }
        }
    }
    public static String label(double units){if(!Double.isFinite(units)||units<0)return "not scanned";return String.format(Locale.ROOT,units<1000000?"~%,.1f units":"~%.3g units",units);}
}
