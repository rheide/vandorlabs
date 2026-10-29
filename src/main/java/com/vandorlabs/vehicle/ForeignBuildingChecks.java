package com.vandorlabs.vehicle;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import com.vandorlabs.blocks.ModBlocks;

/** Installed-mod metadata, orientation, persistence and refusal matrices. */
public final class ForeignBuildingChecks {
    public static boolean domain(String name){return Arrays.asList("libvulpes","advancedrocketry","cfm","galacticraftcore","galacticraftplanets","thermalfoundation","thermalexpansion","thermaldynamics","thermalinnovation").contains(name);}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("foreign-buildings: "+why);}
    @SuppressWarnings({"rawtypes","unchecked"}) private static IBlockState facing(IBlockState state,EnumFacing face){for(IProperty p:state.getPropertyKeys())if(p.getName().equals("facing")&&p.getAllowedValues().contains(face))return state.withProperty(p,face);return state;}
    private static EnumFacing facing(IBlockState state){for(IProperty<?> p:state.getPropertyKeys())if(p.getName().equals("facing")&&state.getValue(p) instanceof EnumFacing)return (EnumFacing)state.getValue(p);return EnumFacing.NORTH;}
    private static VehicleStructure.Cell cell(WorldServer world,IBlockState state,BlockPos pos,EntityPlayerMP actor){
        TileEntity tile=state.getBlock().hasTileEntity(state)?state.getBlock().createTileEntity(world,state):null;NBTTagCompound data=null;
        if(tile!=null){
            tile.setWorld(world);tile.setPos(pos);
            if(tile instanceof IInventory&&((IInventory)tile).getSizeInventory()>0)((IInventory)tile).setInventorySlotContents(0,new ItemStack(Items.DIAMOND,3));
            data=tile.writeToNBT(new NBTTagCompound());
            if(tile.getClass().getName().endsWith("TileEntityMailBox")){data.setString("OwnerName",actor.getName());data.setString("OwnerUUID",actor.getUniqueID().toString());tile.readFromNBT(data);data=tile.writeToNBT(new NBTTagCompound());}
            if(tile.getClass().getName().endsWith("TileEntityScreen"))data.setInteger("type",2);
        }
        return new VehicleStructure.Cell(pos,state,data);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static VehicleStructure snapshot(WorldServer world,IBlockState state,EntityPlayerMP actor){
        BlockPos p=new BlockPos(2,2,2);List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));cells.add(cell(world,state,p,actor));
        String type=state.getBlock().getClass().getSimpleName(),name=state.getBlock().getRegistryName().getResourcePath(),partner=null;BlockPos other=null;
        if(type.equals("BlockBath")||type.equals("BlockModernBed")){boolean bottom=name.endsWith("bottom");partner=name.replace(bottom?"bottom":"top",bottom?"top":"bottom");other=p.offset(bottom?facing(state):facing(state).getOpposite());}
        else if(type.equals("BlockFreezer")){other=p.up();partner="fridge";}else if(type.equals("BlockFridge")){other=p.down();partner="freezer";}
        else if(type.equals("BlockModernSlidingDoor")){for(IProperty property:state.getPropertyKeys())if(property.getName().equals("top")){boolean top=(Boolean)state.getValue(property);other=top?p.down():p.up();cells.add(cell(world,state.withProperty(property,!top),other,actor));}}
        if(partner!=null){Block block=Block.getBlockFromName("cfm:"+partner);require(block!=null,"native furniture partner "+partner);cells.add(cell(world,facing(block.getDefaultState(),facing(state)),other,actor));}
        return new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,3);
    }
    public static void run(WorldServer world,EntityPlayerMP actor){
        require(BuildingBlockCompat.validScreenDimensions(4,4,4,4),"maximum screen dimensions");
        require(!BuildingBlockCompat.validScreenDimensions(Integer.MAX_VALUE,Integer.MAX_VALUE,0,0),"overflowing horizontal screen dimensions");
        require(!BuildingBlockCompat.validScreenDimensions(0,0,Integer.MAX_VALUE,Integer.MAX_VALUE),"overflowing vertical screen dimensions");
        require(!BuildingBlockCompat.validScreenDimensions(-1,0,0,0),"negative screen dimensions");
        int blocks=0,states=0,accepted=0,rejected=0,tiles=0,negative=0;
        Map<String,Integer> totals=new TreeMap<>();Set<String> failures=new LinkedHashSet<>();
        for(Block block:Block.REGISTRY){
            String domain=block.getRegistryName().getResourceDomain();if(!domain(domain))continue;blocks++;
            Set<IBlockState> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            for(int meta=0;meta<16;meta++){
                IBlockState state;try{state=block.getStateFromMeta(meta);}catch(RuntimeException invalid){continue;}
                if(!seen.add(state))continue;states++;
                TileEntity raw=state.getBlock().hasTileEntity(state)?state.getBlock().createTileEntity(world,state):null;
                VehicleStructure.Cell probe=new VehicleStructure.Cell(new BlockPos(2,2,2),state,raw==null?null:new NBTTagCompound());
                boolean eligible=MovingBlockSupport.supported(probe,raw);
                if(domain.equals("thermalexpansion")||domain.equals("thermaldynamics"))require(!eligible,"RF/fluid/network blocks remain deferred: "+block.getRegistryName());
                if(!eligible){rejected++;continue;}
                // Fluids and intangible temporary effects are excluded from this building scope.
                if(state.getMaterial().isLiquid()||block.isAir(state,world,BlockPos.ORIGIN)){rejected++;continue;}
                VehicleStructure tested=null;
                try{
                    VehicleStructure source=snapshot(world,state,actor),cycle=source;VehicleWorld view=new VehicleWorld(world,source);tested=source;
                    if(state.getBlockHardness(view,new BlockPos(2,2,2))<0){rejected++;continue;}
                    MovingBlockSupport.validate(source,view);
                    for(int turn=0;turn<4;turn++){
                        VehicleStructure previous=cycle;cycle=VehicleRotation.rotate(previous,new VehicleWorld(world,previous),1);MovingBlockSupport.validate(cycle,new VehicleWorld(world,cycle));
                        if(turn==0){VehicleStructure.Cell turned=cycle.at(VehicleRotation.cell(new BlockPos(2,2,2),(int)source.bounds.maxX,(int)source.bounds.maxZ,1));for(IProperty<?> property:state.getPropertyKeys())if(state.getValue(property) instanceof EnumFacing)require(turned.state.getValue(property)==Rotation.CLOCKWISE_90.rotate((EnumFacing)state.getValue(property)),"quarter-turn facing "+state);}
                    }
                    require(cycle.write().equals(source.write()),"four-turn complete NBT/state round trip "+state);
                    if(raw!=null){
                        raw.setWorld(world);raw.setPos(new BlockPos(2,2,2));
                        tiles++;VehicleStructure.Cell original=source.at(new BlockPos(2,2,2));NBTTagCompound extra=original.tileData();extra.setInteger("UnknownImportantData",42);boolean denied=false;
                        try{MovingBlockSupport.requireExactTileData(new VehicleStructure.Cell(original.pos,state,extra),view.getTileEntity(original.pos));}catch(IllegalArgumentException expected){denied=true;}
                        require(denied,"unknown tile data remains refused");negative++;
                        // Incomplete native furniture must fail before source removal.
                        if(source.cells.size()>2){List<VehicleStructure.Cell> partial=new ArrayList<>(source.cells);partial.remove(partial.size()-1);VehicleStructure cut=new VehicleStructure(partial,source.seat,source.facing,3);denied=false;try{MovingBlockSupport.validate(cut,new VehicleWorld(world,cut));}catch(IllegalArgumentException expected){denied=true;}require(denied,"cropped furniture assembly refused");negative++;}
                        if(raw.getClass().getName().endsWith("TileEntityTV"))for(String key:new String[]{"getWidth","getHeight","getScreenYOffset","getScreenZOffset"})try{require(Objects.equals(key.equals("getScreenZOffset")?state.getBlock().getClass().getMethod(key,IBlockState.class).invoke(state.getBlock(),state):raw.getClass().getMethod(key).invoke(raw),raw.getClass().getMethod(key).invoke(view.getTileEntity(original.pos))),"native TV constructor dimensions "+state);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
                    }
                    accepted++;totals.merge(domain,1,Integer::sum);
                }catch(RuntimeException|LinkageError failure){
                    String key=block.getClass().getName()+" / "+(raw==null?"none":raw.getClass().getName());
                    if(failures.add(key)){
                        System.err.println("foreign-buildings FAILED "+block.getRegistryName()+" meta="+meta+": "+failure);
                        if(tested!=null){VehicleWorld check=new VehicleWorld(world,tested);for(VehicleStructure.Cell c:tested.cells)if(c.tileData()!=null&&check.getTileEntity(c.pos)!=null){NBTTagCompound wanted=c.tileData(),got=check.getTileEntity(c.pos).writeToNBT(new NBTTagCompound());Set<String> keys=new TreeSet<>(wanted.getKeySet());keys.addAll(got.getKeySet());for(String k:keys)if(!Objects.equals(wanted.getTag(k),got.getTag(k)))System.err.println("foreign-buildings DIFF "+k+" expected="+wanted.getTag(k)+" actual="+got.getTag(k));}}
                    }
                }
            }
        }
        require(failures.isEmpty(),"failed native class groups: "+failures);
        Block coil=Block.getBlockFromName("libvulpes:coil0");require(coil!=null,"installed LibVulpes coils");require(BuildingBlockCompat.supported(new VehicleStructure.Cell(BlockPos.ORIGIN,coil.getStateFromMeta(0),null),null),"ordinary coil0 supported");
        System.out.println("[vandorlabs] foreign-buildings PASS blocks="+blocks+" states="+states+" accepted="+accepted+" refused="+rejected+" tile-cases="+tiles+" negatives="+negative+" by-mod="+totals+" four-turn orientation full NBT inventory native furniture pairs TV dimensions unknown-data refusal");
    }
    private ForeignBuildingChecks(){}
}
