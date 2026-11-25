package com.vandorlabs.vehicle;

import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.ports.*;
import com.vandorlabs.tiles.*;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockable;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import java.util.*;

/** Server-owned local transfer. Native machine ticks and native network registration stay parked. */
public final class VehicleResources {
    private static final int POWER=0, FLUID=1, ITEM=2;
    private static final int RF_PER_TICK=1000, MB_PER_TICK=100, ITEM_PER_TICK=8;
    private static final int DIRECT_ATTEMPTS=128, CHANNEL_ATTEMPTS=384, SYNC_INTERVAL=10;
    private final EntityGroundVehicle craft;
    private VehicleWorld indexed;
    private int revision=-1;
    private long tick=Long.MIN_VALUE;
    private final List<ResourcePortTile> ports=new ArrayList<>();
    private final List<TileEntityConstructor> observers=new ArrayList<>();
    private final List<TileEntityModuleRack> racks=new ArrayList<>();
    private final List<Channel> channels=new ArrayList<>();
    private final Map<BlockPos,Channel> powerRoutes=new HashMap<>(),fluidRoutes=new HashMap<>();
    private final List<Route> direct=new ArrayList<>();
    private final Set<BlockPos> changed=new LinkedHashSet<>();
    private int nextChannel,nextRoute,relayDepth,relayWork;

    VehicleResources(EntityGroundVehicle craft){this.craft=craft;}

    public static boolean serverThread(World world){
        World physical=com.vandorlabs.navigation.NavigationVehicle.physicalWorld(world);
        return physical instanceof WorldServer&&physical.getMinecraftServer()!=null&&physical.getMinecraftServer().isCallingFromMinecraftThread();
    }

    public static boolean live(TileEntity tile){
        if(tile==null||tile.isInvalid()||!(tile.getWorld() instanceof VehicleWorld))return false;
        VehicleWorld view=(VehicleWorld)tile.getWorld();
        if(view.getTileEntity(tile.getPos())!=tile||!view.structure.isActive(tile.getPos())||!serverThread(view))return false;
        if(view.craft!=null){
            EntityGroundVehicle craft=view.craft;
            return craft.view==view&&craft.world==view.parent&&craft.world.getEntityByID(craft.getEntityId())==craft
                    &&!craft.isDead&&!craft.transferInProgress&&!craft.ownershipBlocked;
        }
        GearTileEntity arm=view.navigationArm;
        return arm!=null&&arm.getWorld() instanceof VehicleWorld&&arm.payload()!=null&&!arm.payload().materialized()
                &&!arm.payload().hasOwnershipJournal()&&live(arm)&&arm.payload().view(arm)==view;
    }

    public static boolean sameSection(VehicleWorld view,BlockPos a,BlockPos b){
        return view.craft==null||view.craft.articulation.bodyAt(a)==view.craft.articulation.bodyAt(b);
    }

    /** Calls only endpoints whose resource contents are owned by the installed local tile. */
    public static <T> T endpoint(TileEntity tile,EnumFacing face,Capability<T> capability){
        if(!endpointCandidate(tile)||!live(tile))return null;
        VehicleWorld view=(VehicleWorld)tile.getWorld();
        if(tile instanceof ResourcePortTile||tile instanceof GearTileEntity||tile instanceof TileEntityHexTank
                ||tile instanceof TileEntityConstructor||tile instanceof TileEntityConstructorPart)
            return tile.getCapability(capability,face);
        if(tile instanceof TileEntityLockable)
            return capability==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY&&!view.structure.at(tile.getPos()).hasTileKey("LootTable",8)
                    ?tile.getCapability(capability,face):null;
        if(RFToolsCompat.powerCell(tile))return capability==CapabilityEnergy.ENERGY?CapabilityEnergy.ENERGY.cast(RFToolsCompat.carriedPower(tile,face)):null;
        return tile.getCapability(capability,face);
    }

    public static boolean portSignal(ResourcePortTile port){
        VehicleWorld view=(VehicleWorld)port.getWorld();
        if(port.getRedstoneChannels().isEmpty())return com.vandorlabs.redstone.LoadedRedstonePower.isPowered(view,port.getPos());
        World world=com.vandorlabs.navigation.NavigationVehicle.physicalWorld(view);
        Vec3d point=com.vandorlabs.navigation.NavigationVehicle.point(view,new Vec3d(port.getPos()).addVector(.5,.5,.5),port.getPos());
        return com.vandorlabs.redstone.RedstoneChannels.allPowered(world,new BlockPos(point),port.getRedstoneChannels());
    }

    /** An arm's other end can meet either a terminal device or another carried conduit. */
    public static <T> T armEndpoint(TileEntity tile,EnumFacing face,Capability<T> capability){
        if(!TransferNetworkCompat.onboard(tile))return endpoint(tile,face,capability);
        if(!live(tile))return null;
        VehicleWorld view=(VehicleWorld)tile.getWorld();
        return view.craft==null?null:view.craft.signals.resources.relay(tile,face,capability);
    }

    <T> T relay(TileEntity tile,EnumFacing face,Capability<T> capability){
        if(tile.getWorld()!=indexed||face==null)return null;
        BlockPos pos=tile.getPos();
        if(capability==CapabilityEnergy.ENERGY){
            Channel channel=powerRoutes.get(pos);
            TransferNetworkCompat.Lane lane=channel==null?null:channel.lanes.get(pos);
            if(lane!=null)return CapabilityEnergy.ENERGY.cast(new IEnergyStorage(){
                @Override public int receiveEnergy(int amount,boolean simulate){return !canReceive()||amount<=0?0:channel.first(indexed,false,CapabilityEnergy.ENERGY,0,(e,target)->{
                    int received=e.canReceive()?e.receiveEnergy(Math.min(RF_PER_TICK,amount),simulate):0;
                    if(received>0&&!simulate)saved(target);return received;
                });}
                @Override public int extractEnergy(int amount,boolean simulate){return !canExtract()||amount<=0?0:channel.first(indexed,true,CapabilityEnergy.ENERGY,0,(e,target)->{
                    int removed=e.canExtract()?e.extractEnergy(Math.min(RF_PER_TICK,amount),simulate):0;
                    if(removed>0&&!simulate)saved(target);return removed;
                });}
                @Override public int getEnergyStored(){return channel.first(indexed,true,CapabilityEnergy.ENERGY,0,(storage,target)->storage.getEnergyStored());}
                @Override public int getMaxEnergyStored(){return channel.first(indexed,true,CapabilityEnergy.ENERGY,0,(storage,target)->storage.getMaxEnergyStored());}
                @Override public boolean canReceive(){return live(tile)&&lane.accepts(face)&&!channel.target.isEmpty();}
                @Override public boolean canExtract(){return live(tile)&&lane.emits(face)&&!channel.source.isEmpty();}
            });
        }
        if(capability==CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY){
            Channel channel=fluidRoutes.get(pos);
            TransferNetworkCompat.Lane lane=channel==null?null:channel.lanes.get(pos);
            if(lane!=null)return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(new IFluidHandler(){
                private final net.minecraftforge.fluids.capability.IFluidTankProperties[] properties={
                        new net.minecraftforge.fluids.capability.FluidTankProperties(null,0,lane.accepts(face),lane.emits(face))};
                @Override public net.minecraftforge.fluids.capability.IFluidTankProperties[] getTankProperties(){return properties.clone();}
                @Override public int fill(FluidStack resource,boolean commit){
                    if(!live(tile)||!lane.accepts(face)||resource==null||resource.amount<=0)return 0;
                    FluidStack portion=resource.copy();portion.amount=Math.min(portion.amount,MB_PER_TICK);
                    return channel.first(indexed,false,CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,0,(handler,target)->{
                        if(!fluidSink(target))return 0;
                        int filled=handler.fill(portion.copy(),commit);if(filled>0&&commit)saved(target);return filled;
                    });
                }
                @Override public FluidStack drain(FluidStack resource,boolean commit){
                    if(resource==null||resource.amount<=0)return null;
                    FluidStack portion=resource.copy();portion.amount=Math.min(portion.amount,MB_PER_TICK);
                    return drainFrom(handler->handler.drain(portion.copy(),commit),commit);
                }
                @Override public FluidStack drain(int max,boolean commit){return max<=0?null:drainFrom(handler->handler.drain(Math.min(max,MB_PER_TICK),commit),commit);}
                private FluidStack drainFrom(java.util.function.Function<IFluidHandler,FluidStack> drain,boolean commit){
                    if(!live(tile)||!lane.emits(face))return null;
                    return channel.first(indexed,true,CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,null,(handler,target)->{
                        FluidStack removed=drain.apply(handler);if(removed!=null&&commit)saved(target);return removed;
                    });
                }
            });
        }
        return null;
    }

    void tick(){
        VehicleWorld view=craft.view;
        if(tick==craft.world.getTotalWorldTime()||view==null||!serverThread(view))return;
        tick=craft.world.getTotalWorldTime();
        if(indexed!=view||revision!=view.tileRevision)index(view);
        java.util.function.Consumer<BlockPos> controlUpdates=view.controlUpdates;
        view.controlUpdates=changed::add;
        try{
            for(ResourcePortTile port:ports)if(live(port))port.update();
            for(int i=0,size=direct.size();i<Math.min(size,DIRECT_ATTEMPTS);i++){
                Route route=direct.get(nextRoute);nextRoute=(nextRoute+1)%size;
                for(int kind=0;kind<3;kind++)transfer(view,route.source,route.target,kind);
            }
            int work=CHANNEL_ATTEMPTS;
            for(int i=0,size=channels.size();i<size&&work>0;i++){
                Channel channel=channels.get(nextChannel);nextChannel=(nextChannel+1)%size;
                work=channel.transfer(view,work);
            }
            if(craft.flying()&&!racks.isEmpty())supplyObserversFromRacks(view);
            for(TileEntityConstructor observer:observers)if(live(observer))observer.sampleCarriedObserver();
        }finally{
            view.controlUpdates=controlUpdates;
        }
    }

    void flushChanges(Set<BlockPos> dirty,boolean force){
        if(indexed==craft.view&&(force||tick%SYNC_INTERVAL==0)){dirty.addAll(changed);changed.clear();}
    }
    void close(){changed.clear();indexed=null;ports.clear();observers.clear();racks.clear();channels.clear();direct.clear();powerRoutes.clear();fluidRoutes.clear();}

    private void index(VehicleWorld view){
        if(indexed!=view)changed.clear();
        indexed=view;revision=view.tileRevision;
        ports.clear();observers.clear();racks.clear();channels.clear();direct.clear();powerRoutes.clear();fluidRoutes.clear();
        nextChannel=nextRoute=0;
        for(TileEntity tile:view.tiles.values()){
            if(tile instanceof ResourcePortTile)ports.add((ResourcePortTile)tile);
            if(tile instanceof TileEntityModuleRack)racks.add((TileEntityModuleRack)tile);
            if(tile instanceof TileEntityConstructor){
                TileEntityConstructor core=(TileEntityConstructor)tile;
                if(core.observer())observers.add(core);
            }
            if(!(tile instanceof GearTileEntity||tile instanceof TileEntityConstructor||tile instanceof TileEntityConstructorPart)||!view.structure.isActive(tile.getPos()))continue;
            for(EnumFacing side:EnumFacing.VALUES){
                if(!deviceFace(tile,side,POWER)&&!deviceFace(tile,side,FLUID)&&!deviceFace(tile,side,ITEM))continue;
                TileEntity adjacent=view.getTileEntity(tile.getPos().offset(side));
                if(!endpointCandidate(adjacent)||!sameSection(view,tile.getPos(),adjacent.getPos()))continue;
                Face inner=new Face(tile.getPos(),side),outer=new Face(adjacent.getPos(),side.getOpposite());
                direct.add(new Route(outer,inner)); // Feed an inlet or an arm configured as input.
                if(tile instanceof GearTileEntity)direct.add(new Route(inner,outer));
            }
        }
        indexChannels(view,POWER);
        indexChannels(view,FLUID);
    }

    /** In Flight, Module Rack Battery Modules cover whatever this tick's wired supply left unmet.
     * Wired routes run first, so a powered Fluxduct keeps the rack's charge in reserve. A rack
     * only pays for a full tick of observer demand; partial charge stays in the module.
     */
    private void supplyObserversFromRacks(VehicleWorld view){
        for(TileEntityConstructor observer:observers){
            if(!live(observer))continue;
            IEnergyStorage inlet=endpoint(view.getTileEntity(observer.observerPowerPort()),EnumFacing.DOWN,CapabilityEnergy.ENERGY);
            if(inlet==null||!inlet.canReceive())continue;
            int wanted=Math.max(0,inlet.receiveEnergy(com.vandorlabs.navigation.ObserverPower.RF_PER_TICK,true)),available=0;
            if(wanted<=0)continue;
            for(TileEntityModuleRack rack:racks)if(available<wanted)available+=rack.extractCarriedBatteryEnergy(wanted-available,true);
            if(available<wanted)continue;
            for(TileEntityModuleRack rack:racks){
                if(wanted<=0)break;
                int offered=rack.extractCarriedBatteryEnergy(wanted,true);
                if(offered<=0)continue;
                int delivered=inlet.receiveEnergy(offered,false);
                if(delivered>0&&rack.extractCarriedBatteryEnergy(delivered,false)!=delivered)
                    throw new IllegalStateException("Carried Battery Module changed its charge during one transfer");
                wanted-=delivered;
                if(delivered<offered)break;
            }
        }
    }

    private void indexChannels(VehicleWorld view,int kind){
        Map<BlockPos,TransferNetworkCompat.Lane> lanes=new LinkedHashMap<>();
        for(TileEntity tile:view.tiles.values()){
            if(!view.structure.isActive(tile.getPos()))continue;
            TransferNetworkCompat.Lane lane=TransferNetworkCompat.lane(tile,view.structure.at(tile.getPos()),kind==POWER);
            if(lane!=null)lanes.put(tile.getPos(),lane);
        }
        Set<BlockPos> visited=new HashSet<>();
        for(BlockPos start:lanes.keySet()){
            if(!visited.add(start))continue;
            Set<Face> inputs=new LinkedHashSet<>(),outputs=new LinkedHashSet<>();
            Map<BlockPos,TransferNetworkCompat.Lane> connected=new HashMap<>();
            ArrayDeque<BlockPos> pending=new ArrayDeque<>();pending.add(start);
            while(!pending.isEmpty()){
                BlockPos at=pending.removeFirst();TransferNetworkCompat.Lane lane=lanes.get(at);
                connected.put(at,lane);
                for(EnumFacing side:EnumFacing.VALUES){
                    BlockPos neighbor=at.offset(side);
                    if(!sameSection(view,at,neighbor))continue;
                    if(lane.connects(lanes.get(neighbor),side)){
                        if(visited.add(neighbor))pending.add(neighbor);
                    }else if(endpointCandidate(view.getTileEntity(neighbor))&&deviceFace(view.getTileEntity(neighbor),side.getOpposite(),kind)){
                        Face endpoint=new Face(neighbor,side.getOpposite());
                        if(lane.accepts(side))inputs.add(endpoint);
                        if(lane.emits(side))outputs.add(endpoint);
                    }
                }
            }
            if(inputs.isEmpty()&&outputs.isEmpty())continue;
            Channel channel=new Channel(kind,inputs,outputs,connected);
            for(BlockPos pos:connected.keySet())(kind==POWER?powerRoutes:fluidRoutes).put(pos,channel);
            if(!inputs.isEmpty()&&!outputs.isEmpty())channels.add(channel);
        }
    }

    private static boolean endpointCandidate(TileEntity tile){
        if(tile==null)return false;
        if(tile instanceof ResourcePortTile||tile instanceof GearTileEntity||tile instanceof TileEntityHexTank
                ||tile instanceof TileEntityConstructor||tile instanceof TileEntityConstructorPart
                ||tile instanceof TileEntityLockable&&tile.getClass().getName().startsWith("net.minecraft.tileentity."))return true;
        String type=tile.getClass().getName();
        return type.equals("mekanism.common.tile.TileEntityEnergyCube")||type.equals("mekanism.common.tile.TileEntityFluidTank")
                ||type.equals("cofh.thermalexpansion.block.storage.TileCell")||type.equals("cofh.thermalexpansion.block.storage.TileTank")
                ||TransferNetworkCompat.galacticraftStorage(tile)||RFToolsCompat.powerCell(tile);
    }

    private static boolean deviceFace(TileEntity tile,EnumFacing side,int kind){
        TileEntity owner=tile;
        if(tile instanceof TileEntityConstructorPart)owner=tile.getWorld().getTileEntity(((TileEntityConstructorPart)tile).owner());
        if(!(owner instanceof TileEntityConstructor))return !(tile instanceof TileEntityConstructorPart);
        TileEntityConstructor core=(TileEntityConstructor)owner;
        return kind==POWER&&core.observer()&&side==EnumFacing.DOWN&&tile.getPos().equals(core.observerPowerPort())
                ||kind==FLUID&&core.maxwell()&&tile.getPos().equals(core.maxwellPort(side));
    }

    private static final class Face{
        final BlockPos pos;final EnumFacing side;
        Face(BlockPos pos,EnumFacing side){this.pos=pos;this.side=side;}
        @Override public boolean equals(Object other){return other instanceof Face&&pos.equals(((Face)other).pos)&&side==((Face)other).side;}
        @Override public int hashCode(){return pos.hashCode()*31+side.getIndex();}
    }
    private static final class Route{
        final Face source,target;
        Route(Face source,Face target){this.source=source;this.target=target;}
    }
    private final class Channel{
        final int kind;final List<Face> source,target;int firstSource,firstTarget;
        final Map<BlockPos,TransferNetworkCompat.Lane> lanes;
        boolean forwarding;
        final Map<BlockPos,List<EnumFacing>> inputFaces=new HashMap<>();
        Channel(int kind,Set<Face> source,Set<Face> target,Map<BlockPos,TransferNetworkCompat.Lane> lanes){
            this.kind=kind;this.source=new ArrayList<>(source);this.target=new ArrayList<>(target);
            this.lanes=lanes;
            if(kind==POWER)for(Face face:source)inputFaces.computeIfAbsent(face.pos,unused->new ArrayList<>()).add(face.side);
        }
        <T,R> R first(VehicleWorld view,boolean pulling,Capability<T> capability,R empty,java.util.function.BiFunction<T,TileEntity,R> action){
            if(view==null||forwarding)return empty;
            if(relayDepth++==0)relayWork=96;
            forwarding=true;
            try{
                List<Face> faces=pulling?source:target;
                for(int i=0;i<Math.min(16,faces.size())&&relayWork>0;i++,relayWork--){
                    Face face=faces.get(((pulling?firstSource:firstTarget)+i)%faces.size());
                    if(!pulling&&suppliesPower(view,face.pos))continue;
                    TileEntity tile=view.getTileEntity(face.pos);T handler=endpoint(tile,face.side,capability);
                    if(handler==null)continue;
                    R result=action.apply(handler,tile);
                    if(!Objects.equals(result,empty))return result;
                }
                return empty;
            }finally{forwarding=false;relayDepth--;}
        }
        int transfer(VehicleWorld view,int work){
            int budget=Math.min(48,work);
            for(int t=0;t<target.size()&&budget>0;t++){
                Face output=target.get(firstTarget);firstTarget=(firstTarget+1)%target.size();
                work--;budget--;
                if(suppliesPower(view,output.pos))continue;
                for(int s=0;s<source.size()&&budget>0;s++){
                    Face input=source.get(firstSource);firstSource=(firstSource+1)%source.size();
                    work--;budget--;
                    if(VehicleResources.transfer(view,input,output,kind))break;
                }
            }
            return work;
        }
        private boolean suppliesPower(VehicleWorld view,BlockPos pos){
            List<EnumFacing> faces=inputFaces.get(pos);
            if(faces==null)return false;
            TileEntity tile=view.getTileEntity(pos);
            for(EnumFacing side:faces){
                IEnergyStorage power=endpoint(tile,side,CapabilityEnergy.ENERGY);
                if(power!=null&&power.canExtract())return true;
            }
            return false;
        }
    }
    private static boolean fluidSink(TileEntity tile){return tile instanceof TileEntityConstructor||tile instanceof TileEntityConstructorPart
                ||tile instanceof ResourcePortTile||tile instanceof GearTileEntity;}
    private static boolean transfer(VehicleWorld view,Face from,Face to,int kind){
        if(from.pos.equals(to.pos))return false;
        TileEntity source=view.getTileEntity(from.pos),target=view.getTileEntity(to.pos);
        if(source==null||target==null)return false;
        // Avoid round trips between passive storage faces. A machine inlet or explicit
        // arm/port face provides the direction. Tanks fill by being plumbed on purpose.
        if(kind==FLUID&&!fluidSink(target))return false;
        if(kind==ITEM&&!(source instanceof GearTileEntity||target instanceof GearTileEntity))return false;
        try{
            if(kind==POWER)return power(source,from.side,target,to.side);
            if(kind==FLUID)return fluid(source,from.side,target,to.side);
            return items(source,from.side,target,to.side);
        }catch(RuntimeException|LinkageError unsupported){
            VehicleCompatibility.warn(source.getBlockType(),"carried resource transfer",unsupported);
            return false;
        }
    }
    private static boolean power(TileEntity source,EnumFacing from,TileEntity target,EnumFacing to){
        IEnergyStorage sink=endpoint(target,to,CapabilityEnergy.ENERGY);
        if(sink==null||!sink.canReceive())return false;
        IEnergyStorage supply=endpoint(source,from,CapabilityEnergy.ENERGY);
        if(supply==null||!supply.canExtract()||supply==sink)return false;
        int max=Math.min(RF_PER_TICK,Math.max(0,sink.receiveEnergy(RF_PER_TICK,true)));
        int offered=max<=0?0:Math.min(max,Math.max(0,supply.extractEnergy(max,true)));
        if(offered<=0)return false;
        int sent=supply.extractEnergy(offered,false);
        if(sent<=0)return false;
        int delivered=sink.receiveEnergy(sent,false);
        if(delivered<sent)refundPower(source,sent-delivered);
        saved(source);if(delivered>0)saved(target);
        return delivered>0;
    }
    private static boolean fluid(TileEntity source,EnumFacing from,TileEntity target,EnumFacing to){
        IFluidHandler sink=endpoint(target,to,CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);
        if(sink==null)return false;
        IFluidHandler supply=endpoint(source,from,CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);
        if(supply==null||supply==sink)return false;
        FluidStack offered=supply.drain(MB_PER_TICK,false);
        if(offered==null||offered.amount<=0)return false;
        FluidStack request=offered.copy();request.amount=Math.min(MB_PER_TICK,Math.min(offered.amount,sink.fill(offered.copy(),false)));
        if(request.amount<=0)return false;
        FluidStack sent=supply.drain(request,true);
        if(sent==null||sent.amount<=0)return false;
        int delivered=sink.fill(sent.copy(),true);
        if(delivered<sent.amount){FluidStack remaining=sent.copy();remaining.amount-=delivered;refundFluid(source,remaining);}
        saved(source);if(delivered>0)saved(target);
        return delivered>0;
    }
    private static boolean items(TileEntity source,EnumFacing from,TileEntity target,EnumFacing to){
        IItemHandler sink=endpoint(target,to,CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
        IItemHandler supply=endpoint(source,from,CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
        if(sink==null||supply==null||supply==sink)return false;
        int sourceSlots=Math.min(64,supply.getSlots()),sinkSlots=Math.min(64,sink.getSlots());
        for(int slot=0;slot<sourceSlots;slot++){
            ItemStack offered=supply.extractItem(slot,ITEM_PER_TICK,true);
            if(offered.isEmpty())continue;
            for(int dest=0;dest<sinkSlots;dest++){
                int count=offered.getCount()-sink.insertItem(dest,offered.copy(),true).getCount();
                if(count<=0)continue;
                ItemStack sent=supply.extractItem(slot,count,false);
                if(sent.isEmpty())return false;
                ItemStack remainder=sink.insertItem(dest,sent.copy(),false);
                if(!remainder.isEmpty()){
                    int refunded=source instanceof MatterPortTile?((MatterPortTile)source).returnCarriedItems(remainder)
                            :remainder.getCount()-supply.insertItem(slot,remainder.copy(),false).getCount();
                    if(refunded!=remainder.getCount())throw new IllegalStateException("Carried item handler changed its offer during one transfer");
                }
                saved(source);if(remainder.getCount()<sent.getCount())saved(target);
                return remainder.getCount()<sent.getCount();
            }
        }
        return false;
    }
    private static void saved(TileEntity tile){
        if(!(tile instanceof GearTileEntity||tile instanceof TileEntityConstructor||tile instanceof TileEntityConstructorPart))tile.markDirty();
    }
    private static void refundPower(TileEntity source,int amount){
        if(source instanceof PowerPortTile)amount-=((PowerPortTile)source).returnCarriedEnergy(amount);
        else if(RFToolsCompat.powerCell(source))amount-=RFToolsCompat.returnCarriedPower(source,amount);
        for(EnumFacing face:EnumFacing.VALUES){
            if(amount<=0)return;
            IEnergyStorage storage=endpoint(source,face,CapabilityEnergy.ENERGY);
            if(storage!=null&&storage.canReceive())amount-=storage.receiveEnergy(amount,false);
        }
        if(amount>0)throw new IllegalStateException("Carried energy handler changed its offer during one transfer");
    }
    private static void refundFluid(TileEntity source,FluidStack remaining){
        if(source instanceof FluidPortTile)remaining.amount-=((FluidPortTile)source).returnCarriedFluid(remaining.copy());
        for(EnumFacing face:EnumFacing.VALUES){
            if(remaining.amount<=0)return;
            IFluidHandler tank=endpoint(source,face,CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);
            if(tank!=null)remaining.amount-=tank.fill(remaining.copy(),true);
        }
        if(remaining.amount>0)throw new IllegalStateException("Carried fluid handler changed its offer during one transfer");
    }
}
