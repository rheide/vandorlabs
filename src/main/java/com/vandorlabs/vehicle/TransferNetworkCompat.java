package com.vandorlabs.vehicle;

import java.lang.reflect.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
    private static final String GC="micdoodle8.mods.galacticraft.core.tile.TileEntity";
    private static final Set<String> MEK_TYPES=new HashSet<>(Arrays.asList("UniversalCable","MechanicalPipe","PressurizedTube","ThermodynamicConductor","LogisticalTransporter","RestrictiveTransporter","DiversionTransporter"));
    private static boolean thermal(TileEntity tile){return tile!=null&&tile.getClass().getName().startsWith("cofh.thermaldynamics.duct.tiles.");}
    private static boolean mekanism(TileEntity tile){String name=tile==null?"":tile.getClass().getName();return name.startsWith(MEK)&&MEK_TYPES.contains(name.substring(MEK.length()));}
    private static boolean galacticraft(TileEntity tile){String name=type(tile);return name.equals(GC+"AluminumWire")||name.equals(GC+"FluidPipe");}
    private static String type(TileEntity tile){return tile==null?"":tile.getClass().getName();}
    public static boolean component(TileEntity tile){return thermal(tile)||mekanism(tile)||galacticraft(tile);}
    static boolean onboard(TileEntity tile){
        String name=type(tile);
        return galacticraft(tile)||name.equals(MEK+"UniversalCable")||name.equals(MEK+"MechanicalPipe")
                ||name.startsWith("cofh.thermaldynamics.duct.tiles.TileDuctEnergy")
                ||name.startsWith("cofh.thermaldynamics.duct.tiles.TileDuctFluid")
                ||name.startsWith("cofh.thermaldynamics.duct.tiles.TileDuctOmni");
    }
    static boolean galacticraftStorage(TileEntity tile){String name=type(tile);return name.equals(GC+"EnergyStorageModule")||name.equals(GC+"FluidTank");}
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
    /** Exact descriptors avoid resolving unrelated Mekanism methods that mention MCMultiPart. */
    private static final class MekanismAccess {
        final MethodHandle transmitter,network,hasNetwork,update,joining,members,tile;
        MekanismAccess(ClassLoader loader)throws ReflectiveOperationException{
            Class<?> owner=Class.forName(MEK+"Transmitter",false,loader);
            Class<?> impl=Class.forName("mekanism.common.transmitters.TransmitterImpl",false,loader);
            Class<?> grid=Class.forName("mekanism.api.transmitters.DynamicNetwork",false,loader);
            transmitter=virtual(owner,"getTransmitter",impl);network=virtual(impl,"getTransmitterNetwork",grid);
            hasNetwork=virtual(impl,"hasTransmitterNetwork",boolean.class);update=virtual(owner,"updateShare",void.class);
            joining=virtual(grid,"getTransmittersToAdd",Set.class);members=virtual(grid,"getTransmitters",Set.class);
            tile=virtual(impl,"getTileEntity",owner);
        }
        private static MethodHandle virtual(Class<?> owner,String method,Class<?> result)throws ReflectiveOperationException{
            return MethodHandles.publicLookup().findVirtual(owner,method,MethodType.methodType(result));
        }
    }
    private static MekanismAccess mek;
    private static MekanismAccess mekanismAccess(TileEntity tile)throws ReflectiveOperationException{
        if(mek==null)mek=new MekanismAccess(tile.getClass().getClassLoader());return mek;
    }
    private static Object invoke(MethodHandle method,Object target)throws ReflectiveOperationException{
        try{return method.invoke(target);}catch(RuntimeException|LinkageError failure){throw new InvocationTargetException(failure);}
        catch(Error failure){throw failure;}catch(Throwable failure){throw new InvocationTargetException(failure);}
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
                }else if(mekanism(tile)){
                    MekanismAccess access=mekanismAccess(tile);
                    Object transmitter=invoke(access.transmitter,tile);Object grid=invoke(access.network,transmitter);
                    if(grid!=null&&inspected.add(grid)){
                        require(((Set<?>)invoke(access.joining,grid)).isEmpty(),"Wait for the Mekanism network to finish joining");
                        for(Object member:(Iterable<?>)invoke(access.members,grid))requireMember(world,selected,(TileEntity)invoke(access.tile,member));
                    }
                }else for(EnumFacing side:EnumFacing.VALUES){
                    BlockPos neighbor=pos.offset(side);
                    require(world.isBlockLoaded(neighbor,false),"Load the complete Galacticraft network before moving it");
                    TileEntity other=world.getTileEntity(neighbor);
                    if(type(other).equals(type(tile)))requireMember(world,selected,other);
                }
            }
            // Compute every native save share while the complete grid still exists.
            for(BlockPos pos:positions){TileEntity tile=world.getTileEntity(pos);if(mekanism(tile))invoke(mekanismAccess(tile).update,tile);}
        }catch(ReflectiveOperationException|LinkageError failure){throw new IllegalArgumentException("Cannot inspect the installed transfer network",failure);}
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
                MekanismAccess access=mekanismAccess(tile);
                Object transmitter=invoke(access.transmitter,tile);
                if(!(Boolean)invoke(access.hasNetwork,transmitter)){
                    String name=tile.getClass().getName();
                    if(name.endsWith("UniversalCable"))tile.getClass().getField("lastWrite").setDouble(tile,((Number)field(field(tile,"buffer"),"amount")).doubleValue());
                    else if(name.endsWith("MechanicalPipe"))tile.getClass().getField("lastWrite").set(tile,call(field(tile,"buffer"),"getFluid"));
                    else if(name.endsWith("PressurizedTube"))tile.getClass().getField("lastWrite").set(tile,call(field(tile,"buffer"),"getGas"));
                }
                return tile.writeToNBT(new NBTTagCompound());
            }
            if(!thermal(tile))return tile.writeToNBT(new NBTTagCompound());
            Map<String,NBTTagCompound> fluids=new HashMap<>();Map<Object,Object> connections=new IdentityHashMap<>();
            for(Object unit:units(tile))if(call(unit,"getGrid")==null&&token(unit).equals("Fluid")){
                FluidStack stored=(FluidStack)field(unit,"fluidForGrid"),connection=(FluidStack)field(unit,"myConnectionFluid");
                NBTTagCompound data=new NBTTagCompound();if(stored!=null)stored.writeToNBT(data);
                if(connection!=null)data.setTag("ConnFluid",connection.writeToNBT(new NBTTagCompound()));fluids.put(token(unit),data);connections.put(unit,connection);
            }
            NBTTagCompound result;
            try{result=tile.writeToNBT(new NBTTagCompound());}finally{for(Map.Entry<Object,Object> entry:connections.entrySet())entry.getKey().getClass().getField("myConnectionFluid").set(entry.getKey(),entry.getValue());}
            // A joined Fluxduct node writes an explicit zero share. The same empty
            // buffer writes no share after loading without a native grid.
            NBTTagCompound energy=result.getCompoundTag("Energy");
            if(energy.hasKey("Energy",3)&&energy.getInteger("Energy")==0)energy.removeTag("Energy");
            for(Map.Entry<String,NBTTagCompound> entry:fluids.entrySet()){NBTTagCompound data=result.getCompoundTag(entry.getKey());data.merge(entry.getValue());if(!data.hasNoTags())result.setTag(entry.getKey(),data);}
            return result;
        }catch(ReflectiveOperationException|LinkageError failure){throw new IllegalArgumentException("Cannot preserve the installed transfer buffer",failure);}
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
    static void rotate(NBTTagCompound data,net.minecraft.util.Rotation rotation){
        EnumFacing east=rotation.rotate(EnumFacing.EAST),south=rotation.rotate(EnumFacing.SOUTH);
        rotate(data,new int[]{east.getFrontOffsetX(),0,south.getFrontOffsetX(),0,1,0,east.getFrontOffsetZ(),0,south.getFrontOffsetZ()});
    }

    /** One independent RF or fluid lane. Blocked/attached faces cannot bypass native policy. */
    static final class Lane {
        final String family;
        final int incoming,outgoing,links;
        Lane(String family,int incoming,int outgoing,int links){this.family=family;this.incoming=incoming;this.outgoing=outgoing;this.links=links;}
        boolean accepts(EnumFacing side){return (incoming&(1<<side.getIndex()))!=0;}
        boolean emits(EnumFacing side){return (outgoing&(1<<side.getIndex()))!=0;}
        boolean connects(Lane other,EnumFacing side){return other!=null&&family.equals(other.family)&&(links&(1<<side.getIndex()))!=0&&(other.links&(1<<side.getOpposite().getIndex()))!=0;}
    }
    static Lane lane(TileEntity tile,VehicleStructure.Cell cell,boolean energy){
        if(!onboard(tile))return null;
        String name=type(tile);NBTTagCompound data=cell.tileData();int incoming=63,outgoing=63,links=63;
        if(galacticraft(tile)){
            if(energy!=name.endsWith("AluminumWire"))return null;
            String color="";
            if(!energy)for(net.minecraft.block.properties.IProperty<?> property:cell.state.getPropertyKeys())if(property.getName().equals("color"))color=cell.state.getValue(property).toString();
            if(!energy&&cell.state.getBlock().getRegistryName().getResourcePath().equals("fluid_pipe_pull"))outgoing=0;
            return new Lane("galacticraft"+color,incoming,outgoing,links);
        }
        if(mekanism(tile)){
            if(energy!=name.endsWith("UniversalCable"))return null;
            for(EnumFacing side:EnumFacing.VALUES){
                int bit=1<<side.getIndex(),mode=data.getInteger("connection"+side.getIndex());
                if(mode==1||mode==3)incoming&=~bit;
                if(mode==2||mode==3)outgoing&=~bit;
                if(mode==3)links&=~bit;
            }
            return new Lane("mekanism",incoming,outgoing,links);
        }
        boolean omni=name.contains("TileDuctOmni")||name.contains("TileDuctFluid$Energy");
        if(!omni&&energy!=name.contains("TileDuctEnergy"))return null;
        byte[] connections=data.getByteArray("Connections");
        for(int i=0;i<connections.length&&i<6;i++)if(connections[i]==1||connections[i]==2&&!energy)links&=~(1<<i);
        int attached=0;NBTTagList attachments=data.getTagList("Attachments",10);
        for(int i=0;i<attachments.tagCount();i++){int side=attachments.getCompoundTagAt(i).getInteger("side");if(side>=0&&side<6)attached|=1<<side;}
        links&=~attached;
        return new Lane("thermal",links,links,links);
    }
    private static EnumFacing side(EnumFacing side,int[] b){int x=side.getFrontOffsetX(),y=side.getFrontOffsetY(),z=side.getFrontOffsetZ();return EnumFacing.getFacingFromVector(b[0]*x+b[1]*y+b[2]*z,b[3]*x+b[4]*y+b[5]*z,b[6]*x+b[7]*y+b[8]*z);}
    private TransferNetworkCompat(){}
}
