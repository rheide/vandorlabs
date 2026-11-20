package com.vandorlabs.vehicle;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

/** Typed saved shares for complete native grids. This adapter never registers or ticks a synthetic grid. */
public final class TransferNetworkCompat {
    private static final String MEK="mekanism.common.tile.transmitter.TileEntity";
    private static final Set<String> MEK_TYPES=new HashSet<>(Arrays.asList("UniversalCable","MechanicalPipe","PressurizedTube","ThermodynamicConductor","LogisticalTransporter","RestrictiveTransporter","DiversionTransporter"));
    private static boolean thermal(TileEntity tile){return tile!=null&&tile.getClass().getName().startsWith("cofh.thermaldynamics.duct.tiles.");}
    private static boolean mekanism(TileEntity tile){String name=tile==null?"":tile.getClass().getName();return name.startsWith(MEK)&&MEK_TYPES.contains(name.substring(MEK.length()));}
    public static boolean component(TileEntity tile){return thermal(tile)||mekanism(tile);}
    public static boolean armView(TileEntity tile){return component(tile)&&tile.getWorld() instanceof VehicleWorld&&((VehicleWorld)tile.getWorld()).navigationArm!=null;}
    public static boolean typed(VehicleStructure.Cell cell){
        String type=cell.state.getBlock().getClass().getName();
        return cell.hasTileData()&&(type.equals("cofh.thermaldynamics.block.BlockDuct")||type.equals("mekanism.common.block.BlockTransmitter"));
    }
    public static boolean retainFrozenPreimage(TileEntity tile){return component(tile)||tile!=null&&tile.getClass().getName().startsWith("mekanism.generators.common.tile.reactor.TileEntityReactor");}
    private static Object call(Object target,String name,Object... values)throws ReflectiveOperationException{
        for(Method method:target.getClass().getMethods())if(method.getName().equals(name)&&method.getParameterCount()==values.length)return method.invoke(target,values);
        throw new NoSuchMethodException(target.getClass().getName()+"."+name);
    }
    private static Object field(Object target,String name)throws ReflectiveOperationException{return target.getClass().getField(name).get(target);}
    private static Iterable<?> units(TileEntity tile)throws ReflectiveOperationException{return (Iterable<?>)call(tile,"getDuctUnits");}
    private static String token(Object unit)throws ReflectiveOperationException{return (String)field(call(unit,"getToken"),"key");}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}

    /** Run on the physical server before any member is serialized or detached. */
    public static void prepareCapture(World world,Collection<BlockPos> positions){
        if(world instanceof VehicleWorld)return;
        Set<BlockPos> selected=new HashSet<>(positions);Set<Object> inspected=Collections.newSetFromMap(new IdentityHashMap<>());
        try{
            for(BlockPos pos:positions){TileEntity tile=world.getTileEntity(pos);if(!component(tile))continue;
                require(world.isBlockLoaded(pos,false)&&tile.getWorld()==world&&!tile.isInvalid(),"Load the complete transfer network before moving it");
                if(thermal(tile))for(Object unit:units(tile)){
                    Object grid=call(unit,"getGrid");if(grid==null||!inspected.add(grid))continue;
                    for(String set:new String[]{"nodeSet","idleSet"})for(Object member:(Iterable<?>)field(grid,set))requireMember(world,selected,(TileEntity)field(member,"parent"));
                }else{
                    Object transmitter=call(tile,"getTransmitter");Object grid=call(transmitter,"getTransmitterNetwork");
                    if(grid!=null&&inspected.add(grid)){
                        require(((Collection<?>)call(grid,"getTransmittersToAdd")).isEmpty(),"Wait for the Mekanism network to finish joining");
                        for(Object member:(Iterable<?>)call(grid,"getTransmitters"))requireMember(world,selected,(TileEntity)call(member,"getTileEntity"));
                    }
                }
            }
            // Compute every native save share while the complete grid still exists.
            for(BlockPos pos:positions){TileEntity tile=world.getTileEntity(pos);if(mekanism(tile))call(tile,"updateShare");}
        }catch(ReflectiveOperationException failure){throw new IllegalArgumentException("Cannot inspect the installed transfer network",failure);}
    }
    private static void requireMember(World world,Set<BlockPos> selected,TileEntity tile){
        require(tile!=null&&tile.getWorld()==world&&world.isBlockLoaded(tile.getPos(),false)&&world.getTileEntity(tile.getPos())==tile&&selected.contains(tile.getPos()),"Include the complete connected transfer network, or disconnect its boundary before moving it");
    }
    /** Discovery can finish its bounded flood before refreshing native share snapshots. */
    public static List<VehicleStructure.Cell> snapshot(World world,List<VehicleStructure.Cell> cells){
        boolean found=false;List<BlockPos> positions=new ArrayList<>(cells.size());for(VehicleStructure.Cell cell:cells){positions.add(cell.pos);found|=component(world.getTileEntity(cell.pos));}
        if(!found||world instanceof VehicleWorld)return cells;prepareCapture(world,positions);
        List<VehicleStructure.Cell> result=new ArrayList<>(cells.size());for(VehicleStructure.Cell cell:cells){TileEntity tile=world.getTileEntity(cell.pos);result.add(component(tile)?new VehicleStructure.Cell(cell.pos,cell.state,write(tile)):cell);}return result;
    }
    /** Preserve decoded native buffer ownership rather than its unsaved network cache. */
    public static NBTTagCompound write(TileEntity tile){
        if(!component(tile))return tile.writeToNBT(new NBTTagCompound());
        try{
            if(mekanism(tile)){
                Object transmitter=call(tile,"getTransmitter");
                if(!(Boolean)call(transmitter,"hasTransmitterNetwork")){
                    String name=tile.getClass().getName();
                    if(name.endsWith("UniversalCable"))tile.getClass().getField("lastWrite").setDouble(tile,((Number)field(field(tile,"buffer"),"amount")).doubleValue());
                    else if(name.endsWith("MechanicalPipe"))tile.getClass().getField("lastWrite").set(tile,call(field(tile,"buffer"),"getFluid"));
                    else if(name.endsWith("PressurizedTube"))tile.getClass().getField("lastWrite").set(tile,call(field(tile,"buffer"),"getGas"));
                }
                return tile.writeToNBT(new NBTTagCompound());
            }
            Map<String,NBTTagCompound> fluids=new HashMap<>();Map<Object,Object> connections=new IdentityHashMap<>();
            for(Object unit:units(tile))if(call(unit,"getGrid")==null&&token(unit).equals("Fluid")){
                FluidStack stored=(FluidStack)field(unit,"fluidForGrid"),connection=(FluidStack)field(unit,"myConnectionFluid");
                NBTTagCompound data=new NBTTagCompound();if(stored!=null)stored.writeToNBT(data);
                if(connection!=null)data.setTag("ConnFluid",connection.writeToNBT(new NBTTagCompound()));fluids.put(token(unit),data);connections.put(unit,connection);
            }
            NBTTagCompound result;
            try{result=tile.writeToNBT(new NBTTagCompound());}finally{for(Map.Entry<Object,Object> entry:connections.entrySet())entry.getKey().getClass().getField("myConnectionFluid").set(entry.getKey(),entry.getValue());}
            for(Map.Entry<String,NBTTagCompound> entry:fluids.entrySet()){NBTTagCompound data=result.getCompoundTag(entry.getKey());data.merge(entry.getValue());if(!data.hasNoTags())result.setTag(entry.getKey(),data);}
            return result;
        }catch(ReflectiveOperationException failure){throw new IllegalArgumentException("Cannot preserve the installed transfer buffer",failure);}
    }
    public static void validateIdle(NBTTagCompound data){
        require(!data.hasKey("stacks",9)||data.getTagList("stacks",10).tagCount()==0,"Wait for traveling Mekanism items to arrive before moving this network");
        for(String key:data.getKeySet())if(data.hasKey(key,10)){
            NBTTagCompound unit=data.getCompoundTag(key);
            require(!unit.hasKey("TravellingItems",9)||unit.getTagList("TravellingItems",10).tagCount()==0,"Wait for traveling Thermal Dynamics items to arrive before moving this network");
            for(int i=0;i<6;i++)require(!unit.hasKey("crossover"+i),"Disconnect long-range transport crossovers before moving this network");
        }
    }
    public static void rotate(NBTTagCompound data,int[] basis){
        validateIdle(data);
        NBTTagCompound original=data.copy();
        for(EnumFacing side:EnumFacing.values()){
            int mapped=side(side,basis).getIndex();String key="connection"+side.getIndex();if(original.hasKey(key,3))data.setInteger("connection"+mapped,original.getInteger(key));
        }
        if(original.hasKey("modes",11)){int[] before=original.getIntArray("modes");require(before.length==6,"Invalid diversion transporter modes");int[] after=new int[6];for(EnumFacing side:EnumFacing.values())after[side(side,basis).getIndex()]=before[side.getIndex()];data.setIntArray("modes",after);}
        if(original.hasKey("Connections",7)){byte[] before=original.getByteArray("Connections");require(before.length==6,"Invalid Thermal connection bank");byte[] after=new byte[6];for(EnumFacing side:EnumFacing.values())after[side(side,basis).getIndex()]=before[side.getIndex()];data.setByteArray("Connections",after);}
        for(String key:new String[]{"Attachments","Covers"})if(data.hasKey(key,9)){
            NBTTagList before=data.getTagList(key,10);List<NBTTagCompound> rows=new ArrayList<>();for(int i=0;i<before.tagCount();i++){NBTTagCompound row=before.getCompoundTagAt(i).copy();int at=row.getInteger("side");require(at>=0&&at<6,"Invalid Thermal attachment side");row.setInteger("side",side(EnumFacing.getFront(at),basis).getIndex());rows.add(row);}
            rows.sort(Comparator.comparingInt(row->row.getInteger("side")));NBTTagList after=new NBTTagList();for(NBTTagCompound row:rows)after.appendTag(row);data.setTag(key,after);
        }
    }
    private static EnumFacing side(EnumFacing side,int[] b){int x=side.getFrontOffsetX(),y=side.getFrontOffsetY(),z=side.getFrontOffsetZ();return EnumFacing.getFacingFromVector(b[0]*x+b[1]*y+b[2]*z,b[3]*x+b[4]*y+b[5]*z,b[6]*x+b[7]*y+b[8]*z);}
    private TransferNetworkCompat(){}
}
