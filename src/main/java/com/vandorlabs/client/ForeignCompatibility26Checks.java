package com.vandorlabs.client;

import com.vandorlabs.compat.RampMaterials;
import com.vandorlabs.vehicle.*;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Actual optional-mod classes and saved data, without a compile-time dependency on those mods. */
public final class ForeignCompatibility26Checks {
    private static final String IE="blusunrize.immersiveengineering.common.blocks.";
    private static final Set<String> ALLOWED=new LinkedHashSet<>(Arrays.asList(
            IE+"TileEntityIESlab",IE+"metal.TileEntityLantern",IE+"metal.TileEntityStructuralArm",IE+"metal.TileEntityLadder",
            IE+"wooden.TileEntityWindmill",IE+"wooden.TileEntityWallmount",IE+"metal.TileEntityToolbox",
            IE+"metal.TileEntityCapacitorLV",IE+"metal.TileEntityCapacitorMV",IE+"metal.TileEntityCapacitorHV",
            "mekanism.common.tile.TileEntityEnergyCube","mekanism.common.tile.TileEntityGlowPanel","mekanism.common.tile.TileEntityFluidTank",
            "mekanism.common.tile.TileEntityBin","mekanism.common.tile.TileEntityGasTank","mekanism.common.tile.TileEntityPersonalChest"));
    private static final Set<String> REJECTED=new LinkedHashSet<>(Arrays.asList(IE+"wooden.TileEntityWoodenPost",IE+"metal.TileEntityRazorWire",IE+"metal.TileEntityElectricLantern",
            "mekanism.common.tile.TileEntityCardboardBox","mekanism.common.tile.TileEntityDigitalMiner","mekanism.common.tile.TileEntityStructuralGlass"));
    private static final class Fixture {
        final IBlockState state;final TileEntity tile;
        Fixture(IBlockState state,TileEntity tile){this.state=state;this.tile=tile;}
    }
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Foreign compatibility 2.6: "+why);}
    public static void run(WorldServer world,EntityPlayerMP actor){
        Map<String,List<Fixture>> found=new LinkedHashMap<>();List<IBlockState> building=new ArrayList<>();Set<String> domains=new HashSet<>();int scaffolds=0;
        for(Block block:Block.REGISTRY){
            String domain=block.getRegistryName().getResourceDomain();if(!domain.equals("immersiveengineering")&&!domain.equals("mekanism"))continue;domains.add(domain);
            Set<IBlockState> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            for(int meta=0;meta<16;meta++)try{
                IBlockState state=block.getStateFromMeta(meta);if(!seen.add(state))continue;
                if(!block.hasTileEntity(state)){
                    if(RampMaterials.isScaffolding(state))scaffolds++;
                    String type=block.getClass().getName();
                    if(type.equals(IE+"metal.BlockMetalDecoration0")||type.equals(IE+"metal.BlockMetalDecoration1")||type.equals(IE+"wooden.BlockWoodenDecoration")||type.equals("mekanism.common.block.BlockPlasticFence"))building.add(state);
                    continue;
                }
                TileEntity tile=block.createTileEntity(world,state);if(tile==null)continue;
                String type=tile.getClass().getName();if(ALLOWED.contains(type)||REJECTED.contains(type)||type.equals(IE+"wooden.TileEntityWatermill"))found.computeIfAbsent(type,k->new ArrayList<>()).add(new Fixture(state,tile));
            }catch(RuntimeException|LinkageError ignored){/* Invalid metadata in an unrelated optional block is not an adapter fixture. */}
        }
        for(String type:ALLOWED){String domain=type.startsWith(IE)?"immersiveengineering":"mekanism";if(domains.contains(domain))require(found.containsKey(type),"installed class has no registered fixture: "+type);}
        int retained=0,refused=0,buildingCases=0;
        for(Map.Entry<String,List<Fixture>> group:found.entrySet())for(Fixture fixture:group.getValue()){
            String type=group.getKey();
            if(type.equals(IE+"wooden.TileEntityWatermill"))continue;
            if(REJECTED.contains(type)){
                try{VehicleStructure snapshot=snapshot(fixture.state,fixture.tile.writeToNBT(new NBTTagCompound()));VehicleRotation.validate(snapshot,new VehicleWorld(world,snapshot));throw new IllegalStateException("Unsafe optional tile accepted: "+type);}
                catch(IllegalArgumentException expected){refused++;}
                continue;
            }
            for(EnumFacing facing:EnumFacing.values()){
                TileEntity tile=fixture.state.getBlock().createTileEntity(world,fixture.state);
                set(tile,"facing",facing);set(tile,"side",facing);
                if(type.endsWith("TileEntityBin")){
                    // Bin slot zero is an extraction view and ignores insertion into an empty bin.
                    // Seed its valid stored representation without invoking its world/network callbacks.
                    set(tile,"itemType",new ItemStack(Items.DIAMOND,1));set(tile,"bottomStack",new ItemStack(Items.DIAMOND,3));
                    require(((IInventory)tile).getStackInSlot(0).getCount()==3,"bin storage fixture initialized");
                }else if(tile instanceof IInventory&&((IInventory)tile).getSizeInventory()>0)((IInventory)tile).setInventorySlotContents(0,new ItemStack(Items.DIAMOND,3));
                if(type.endsWith("TileEntityFluidTank"))try{
                    Object tank=tile.getClass().getField("fluidTank").get(tile);
                    ((net.minecraftforge.fluids.IFluidTank)tank).fill(new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER,1000),true);
                }catch(ReflectiveOperationException error){throw new IllegalStateException("Fluid tank fixture",error);}
                if(VehicleForeignCompat.toolbox(tile)){VehicleForeignCompat.inventory(tile).set(0,new ItemStack(Items.DIAMOND,3));set(tile,"name","Retained Tool Store");}
                NBTTagCompound original=tile.writeToNBT(new NBTTagCompound());
                if(original.hasKey("electricityStored",99))original.setDouble("electricityStored",12345.5);
                if(original.hasKey("ifluxEnergy",99))original.setInteger("ifluxEnergy",12345);
                if(original.hasKey("securityMode",3)){original.setInteger("securityMode",1);original.setString("ownerUUID",actor.getUniqueID().toString());}
                // Unique world-side values prove side-bank permutation instead of merely round-tripping zeros.
                if(type.contains("TileEntityCapacitor"))for(EnumFacing side:EnumFacing.values())original.setInteger("sideConfig_"+side.getIndex(),side.getIndex()%3);
                if(type.equals(IE+"TileEntityIESlab"))original.setInteger("slabType",retained%3);
                if(type.equals(IE+"metal.TileEntityStructuralArm")){original.setInteger("totalLength",3);original.setInteger("slopePosition",1);original.setBoolean("onCeiling",true);}
                original.setInteger("x",0);original.setInteger("y",0);original.setInteger("z",0);
                VehicleStructure initial=snapshot(fixture.state,original),cycle=initial;VehicleRotation.validate(initial,new VehicleWorld(world,initial));
                for(int turn=1;turn<=4;turn++){
                    cycle=VehicleRotation.rotate(cycle,new VehicleWorld(world,cycle),1);
                    VehicleStructure.Cell cell=cycle.cells.get(0);NBTTagCompound data=cell.tileData();
                    NBTTagCompound expected=original.copy();EnumFacing rotated=facing;
                    for(int n=0;n<turn;n++)rotated=Rotation.CLOCKWISE_90.rotate(rotated);
                    if(expected.hasKey("facing",3))expected.setInteger("facing",rotated.getIndex());
                    if(type.endsWith("TileEntityGlowPanel"))expected.setInteger("side",rotated.getIndex());
                    if(type.contains("TileEntityCapacitor"))for(EnumFacing side:EnumFacing.values()){
                        EnumFacing dest=side;for(int n=0;n<turn;n++)dest=Rotation.CLOCKWISE_90.rotate(dest);
                        expected.setInteger("sideConfig_"+dest.getIndex(),original.getInteger("sideConfig_"+side.getIndex()));
                    }
                    BlockPos position=cell.pos;expected.setInteger("x",position.getX());expected.setInteger("y",position.getY());expected.setInteger("z",position.getZ());
                    require(expected.equals(data),"rotation changes unrelated inventory/energy/security/slab data: "+type+" turn="+turn);
                    TileEntity loaded=new VehicleWorld(world,cycle).getTileEntity(position);require(loaded!=null&&loaded.getClass()==tile.getClass(),"actual tile reconstructs after rotation "+type);
                    NBTTagCompound reread=loaded.writeToNBT(new NBTTagCompound());
                    for(String key:new String[]{"electricityStored","ifluxEnergy","securityMode","ownerUUID","slabType","totalLength","slopePosition","onCeiling","fluidTank"})if(expected.hasKey(key))require(expected.getTag(key).equals(reread.getTag(key)),"live restored resource/configuration retains "+key+" "+type);
                    if(VehicleForeignCompat.toolbox(tile))require(VehicleForeignCompat.inventory(loaded).get(0).getItem()==Items.DIAMOND&&VehicleForeignCompat.inventory(loaded).get(0).getCount()==3,"actual IE private toolbox slots survive every turn");
                    if(tile instanceof IInventory&&((IInventory)tile).getSizeInventory()>0){ItemStack stack=((IInventory)loaded).getStackInSlot(0);require(stack.getItem()==Items.DIAMOND&&stack.getCount()==3,"inventory survives rotation "+type);}
                }
                require(cycle.cells.get(0).state==initial.cells.get(0).state&&cycle.cells.get(0).sameTileData(initial.cells.get(0)),"four turns restore full state/data "+type);retained++;
            }
        }
        for(IBlockState state:building){
            state=northConnection(state);
            VehicleStructure initial=snapshot(state,null),cycle=initial;VehicleRotation.validate(initial,new VehicleWorld(world,initial));
            for(int i=0;i<4;i++){
                cycle=VehicleRotation.rotate(cycle,new VehicleWorld(world,cycle),1);
                if(i==0)for(IProperty<?> property:state.getPropertyKeys())if(property.getName().equals("north")&&state.getValue(property) instanceof Boolean)
                    for(IProperty<?> after:cycle.cells.get(0).state.getPropertyKeys())if(after.getName().equals("east"))require(cycle.cells.get(0).state.getValue(after).equals(state.getValue(property)),"derived fence connection rotates a quarter turn");
            }
            require(cycle.cells.get(0).state==state,"four-turn building material recovery");buildingCases++;
        }
        if(domains.contains("immersiveengineering"))ieAssemblies(world,found);
        if(domains.contains("immersiveengineering"))require(scaffolds==7,"six steel/aluminum scaffold variants plus treated wood scaffold: "+scaffolds);
        System.out.println("[vandorlabs] foreign-compatibility26 PASS retained="+retained+" building="+buildingCases+" refused="+refused+" scaffolds="+scaffolds+" optional-domains="+domains+" actual registered classes full NBT inventories six facings quarter/four-turn recovery explicit network exclusions");
    }
    private static void ieAssemblies(WorldServer world,Map<String,List<Fixture>> found){
        int cases=0;BlockPos root=new BlockPos(3,3,3);List<Fixture> posts=found.get(IE+"wooden.TileEntityWoodenPost"),water=found.get(IE+"wooden.TileEntityWatermill");require(posts!=null&&posts.size()==3,"all wooden/steel/aluminum post registered variants found");require(water!=null&&!water.isEmpty(),"registered watermill variant found");
        for(Fixture fixture:posts){
            List<VehicleStructure.Cell> cells=new ArrayList<>();for(int n=0;n<4;n++)cells.add(foreignCell(world,fixture,root.up(n),n,null,EnumFacing.NORTH));for(EnumFacing side:new EnumFacing[]{EnumFacing.EAST,EnumFacing.SOUTH})cells.add(foreignCell(world,fixture,root.up(3).offset(side),3+side.getIndex(),null,EnumFacing.NORTH));
            VehicleStructure source=normalized(cells);BlockPos owner=BlockPos.ORIGIN;cycleForeign(world,source,"complete post with arms");VehicleWorld view=new VehicleWorld(world,source);List<com.vandorlabs.copier.CopyMaterials.Need> needs=com.vandorlabs.copier.CopyMaterials.forSnapshot(source,view,false);require(needs.size()==1&&needs.get(0).count==1,"post column and arms cost one obtainable root item");
            VehicleScan scan=VehicleScan.attachment(view,owner,Collections.emptySet());while(!scan.step(128)){}require(scan.result!=null&&scan.result.cells.size()==6,"native post discovery includes all required arm/vertical links: "+scan.error);VehicleScan cut=VehicleScan.attachment(view,owner,Collections.singleton(owner.up(3).east()));while(!cut.step(128)){}require(cut.result==null&&cut.error!=null,"coupler exclusion cannot crop an IE post arm");
            boolean clipped=false;try{new com.vandorlabs.turntable.TurntableBoundaries(view,BlockPos.ORIGIN,new BlockPos(0,8,8)).check(owner,fixture.state,view.getTileEntity(owner));}catch(IllegalArgumentException expected){clipped=true;}require(clipped,"prism boundary rejects cropped optional post arm");cases++;
        }
        for(Fixture fixture:water)for(EnumFacing facing:EnumFacing.HORIZONTALS){
            List<VehicleStructure.Cell> cells=new ArrayList<>();for(int y=-2;y<=2;y++)for(int width=-2;width<=2;width++)if(Math.abs(y)<2||Math.abs(width)<2)cells.add(foreignCell(world,fixture,root.add(facing.getAxis()==EnumFacing.Axis.Z?width:0,y,facing.getAxis()==EnumFacing.Axis.X?width:0),0,new int[]{width,y},facing));
            VehicleStructure source=normalized(cells);BlockPos owner=new BlockPos(facing.getAxis()==EnumFacing.Axis.Z?2:0,2,facing.getAxis()==EnumFacing.Axis.X?2:0);cycleForeign(world,source,"complete 21-cell watermill "+facing);List<com.vandorlabs.copier.CopyMaterials.Need> needs=com.vandorlabs.copier.CopyMaterials.forSnapshot(source,new VehicleWorld(world,source),false);require(needs.size()==1&&needs.get(0).count==1,"21 watermill cells cost one obtainable root item");VehicleScan scan=VehicleScan.attachment(new VehicleWorld(world,source),owner,Collections.emptySet());while(!scan.step(128)){}require(scan.result!=null&&scan.result.cells.size()==21,"native watermill discovery retains all21 linked members "+scan.error);List<VehicleStructure.Cell> partial=new ArrayList<>(source.cells);partial.remove(0);boolean refused=false;try{MovingBlockSupport.validate(VehicleStructure.display(partial),new VehicleWorld(world,VehicleStructure.display(partial)));}catch(IllegalArgumentException expected){refused=true;}require(refused,"partial watermill cannot move or copy");cases++;
        }
        for(Fixture fixture:found.get(IE+"metal.TileEntityToolbox")){
            TileEntity tile=fixture.state.getBlock().createTileEntity(world,fixture.state);VehicleForeignCompat.inventory(tile).set(0,new ItemStack(Items.DIAMOND,3));VehicleForeignCompat.inventory(tile).set(22,new ItemStack(Items.EMERALD,7));set(tile,"name","Full Tool Store");NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());VehicleStructure source=snapshot(fixture.state,saved);VehicleWorld view=new VehicleWorld(world,source);ItemStack carrier=VehicleForeignCompat.toolboxMaterial(view.getTileEntity(BlockPos.ORIGIN),fixture.state);List<com.vandorlabs.copier.CopyMaterials.Need> needs=com.vandorlabs.copier.CopyMaterials.forSnapshot(source,view,false);long exact=0;for(com.vandorlabs.copier.CopyMaterials.Need need:needs)if(need.exact){require(com.vandorlabs.copier.CopyMaterials.matches(need.item,carrier),"toolbox paid with its exact full native carrier");exact+=need.count;}require(exact==1,"filled toolbox charged once; no second contents charge");BlockPos at=new BlockPos(706,130,610);world.getChunkFromBlockCoords(at);require(world.isAirBlock(at),"isolated native Toolbox handoff fixture");
            try{world.setBlockState(at,fixture.state,2);TileEntity old=world.getTileEntity(at);old.readFromNBT(VehicleTileData.translated(saved,at));List<ItemStack> stale=VehicleForeignCompat.inventory(old);com.vandorlabs.copier.CopyWorld.freeze(world,at);require(com.vandorlabs.copier.CopyWorld.frozen(world,at),"native Toolbox replaced by inert transaction proxy");for(ItemStack item:stale)require(item.isEmpty(),"actual handoff empties stale23-slot IE list");com.vandorlabs.copier.CopyWorld.publish(world,at);TileEntity restored=world.getTileEntity(at);require(VehicleForeignCompat.inventory(restored).get(0).getCount()==3&&VehicleForeignCompat.inventory(restored).get(22).getCount()==7,"native Toolbox handoff reconstructs first/last contents");List<ItemStack> oldOutput=VehicleForeignCompat.inventory(restored);VehicleStructure exactSnapshot=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,fixture.state,saved)));com.vandorlabs.copier.CopyWorld.clear(world,exactSnapshot,at);for(ItemStack item:oldOutput)require(item.isEmpty(),"clearing published Toolbox retires prior native list");}finally{world.removeTileEntity(at);world.setBlockToAir(at);}
            VehicleForeignCompat.clearDetachedInventory(tile);for(ItemStack item:VehicleForeignCompat.inventory(tile))require(item.isEmpty(),"stale detached IE inventory references emptied");require(!saved.equals(tile.writeToNBT(new NBTTagCompound())),"live old inventory changed while retained snapshot remained independent");cases++;
        }
        Block legacy=Block.getBlockFromName("immersiveengineering:wooden_device1");IBlockState obsolete=legacy.getStateFromMeta(2);require(legacy.createTileEntity(world,obsolete)==null&&!MovingBlockSupport.supported(new VehicleStructure.Cell(BlockPos.ORIGIN,obsolete,null),null),"obsolete advanced Windmill metadata with no native tile factory is explicitly refused");
        System.out.println("[vandorlabs] foreign-IE-families PASS cases="+cases+" complete native post/watermill links four-turn NBT allVariants one-item dummy costs exact toolbox carrier+23slots cropped/excluded refusal");
    }
    private static VehicleStructure normalized(List<VehicleStructure.Cell> cells){int x=Integer.MAX_VALUE,y=x,z=x;for(VehicleStructure.Cell cell:cells){x=Math.min(x,cell.pos.getX());y=Math.min(y,cell.pos.getY());z=Math.min(z,cell.pos.getZ());}BlockPos shift=new BlockPos(-x,-y,-z);List<VehicleStructure.Cell> result=new ArrayList<>();for(VehicleStructure.Cell cell:cells)result.add(new VehicleStructure.Cell(cell.pos.add(shift),cell.state,cell.tileData()==null?null:VehicleTileData.translated(cell.tileData(),shift)));return VehicleStructure.display(result);}
    private static VehicleStructure.Cell foreignCell(WorldServer world,Fixture fixture,BlockPos at,int dummy,int[] offset,EnumFacing facing){TileEntity tile=fixture.state.getBlock().createTileEntity(world,fixture.state);tile.setPos(at);NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());if(offset==null)data.setByte("dummy",(byte)dummy);else{data.setIntArray("offset",offset);data.setInteger("facing",facing.getIndex());data.setFloat("rotation",.123F);data.setFloat("prevRotation",.1F);}return new VehicleStructure.Cell(at,fixture.state,data);}
    private static void cycleForeign(WorldServer world,VehicleStructure source,String what){VehicleStructure cycle=source;for(int turn=0;turn<4;turn++){MovingBlockSupport.validate(cycle,new VehicleWorld(world,cycle));cycle=VehicleRotation.rotate(cycle,new VehicleWorld(world,cycle),1);MovingBlockSupport.validate(cycle,new VehicleWorld(world,cycle));}require(source.write().equals(cycle.write()),"full state/private links restored after four turns: "+what);}
    @SuppressWarnings({"rawtypes","unchecked"}) private static IBlockState northConnection(IBlockState state){
        for(IProperty property:state.getPropertyKeys())if(property.getValueClass()==Boolean.class&&Arrays.asList("north","east","south","west").contains(property.getName()))state=state.withProperty(property,property.getName().equals("north"));return state;
    }
    private static VehicleStructure snapshot(IBlockState state,NBTTagCompound tile){return VehicleStructure.display(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,state,tile),new VehicleStructure.Cell(new BlockPos(2,0,3),Blocks.STONE.getDefaultState(),null)));}
    private static void set(Object instance,String name,Object value){
        for(Class<?> type=instance.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);if(!field.getType().isInstance(value))return;field.setAccessible(true);field.set(instance,value);return;}catch(NoSuchFieldException absent){}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    private ForeignCompatibility26Checks(){}
}
