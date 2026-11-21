package com.vandorlabs.vehicle;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * RFTools 1.12 blocks that Arms, vehicles and Turntables can carry. Supported tiles keep only
 * their own settings, inventory, energy or a position-free channel id, so their saved data moves
 * unchanged. Power Cells also belong to a world-saved network that lists member positions: a moved
 * cell leaves the network where it was lifted and rejoins where it is placed, keeping the shared
 * energy. Blocks bound to fixed coordinates or position-keyed registries are refused with the
 * reason, before anything is lifted.
 */
public final class RFToolsCompat {
    private static final String PREFIX="mcjty.rftools.blocks.";
    private static final Set<String> SUPPORTED=Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "generator.CoalGeneratorTileEntity","relay.RelayTileEntity","booster.BoosterTileEntity",
            "infuser.MachineInfuserTileEntity","crafter.CrafterBlockTileEntity1","crafter.CrafterBlockTileEntity2",
            "crafter.CrafterBlockTileEntity3","itemfilter.ItemFilterTileEntity","environmental.EnvironmentalControllerTileEntity",
            "security.SecurityManagerTileEntity","endergen.PearlInjectorTileEntity","endergen.EnderMonitorTileEntity",
            "spawner.SpawnerTileEntity","storage.StorageTerminalTileEntity",
            "storage.LevelEmitterTileEntity","teleporter.DialingDeviceTileEntity","shaper.ComposerTileEntity","shaper.ProjectorTileEntity","shaper.LocatorTileEntity",
            "screens.ScreenTileEntity","screens.CreativeScreenTileEntity","screens.ScreenHitTileEntity",
            "logic.counter.CounterTileEntity","logic.threelogic.ThreeLogicTileEntity","logic.wireless.RedstoneTransmitterTileEntity",
            "logic.wireless.RedstoneReceiverTileEntity","logic.analog.AnalogTileEntity","logic.sensor.SensorTileEntity",
            "logic.digit.DigitTileEntity","logic.sequencer.SequencerTileEntity","logic.wire.WireTileEntity",
            "logic.timer.TimerTileEntity","logic.invchecker.InvCheckerTileEntity",
            "powercell.PowerCellSimpleTileEntity","powercell.PowerCellNormalTileEntity",
            "powercell.PowerCellAdvancedTileEntity","powercell.PowerCellCreativeTileEntity")));
    private static final Map<String,String> REFUSED;
    static{
        Map<String,String> refused=new HashMap<>();
        String teleport="its teleport destination is registered at fixed coordinates";
        refused.put("teleporter.MatterTransmitterTileEntity",teleport);refused.put("teleporter.MatterReceiverTileEntity",teleport);
        refused.put("teleporter.SimpleDialerTileEntity","it dials a transmitter at fixed coordinates");
        String monitor="it watches a block at fixed coordinates";
        refused.put("monitor.RFMonitorBlockTileEntity",monitor);refused.put("monitor.LiquidMonitorBlockTileEntity",monitor);
        refused.put("screens.ScreenControllerTileEntity","it links screens at fixed coordinates");
        refused.put("storagemonitor.StorageScannerTileEntity","it remembers inventories at fixed coordinates");
        refused.put("elevator.ElevatorTileEntity","its shaft is tied to fixed positions");
        refused.put("blockprotector.BlockProtectorTileEntity","it protects blocks at fixed coordinates");
        String chamber="it uses a Space Chamber registered at fixed corners";
        refused.put("builder.BuilderTileEntity",chamber);refused.put("builder.SpaceChamberControllerTileEntity",chamber);
        refused.put("endergen.EndergenicTileEntity","it beams pearls to a generator at fixed coordinates");
        refused.put("spawner.MatterBeamerTileEntity","it beams matter to a spawner at fixed coordinates");
        refused.put("storage.RemoteStorageTileEntity","it is registered for remote access at its position");
        String scanner="it recalculates its scan area from its shape card against the world";
        refused.put("shaper.ScannerTileEntity",scanner);refused.put("shaper.RemoteScannerTileEntity",scanner);
        refused.put("storage.ModularStorageTileEntity","its contents live in the inserted storage module and RFTools rebuilds them against the world");
        String shield="shields are built around fixed positions";
        for(String type:new String[]{"shield.ShieldTileEntity","shield.ShieldTileEntity2","shield.ShieldTileEntity3","shield.ShieldTileEntity4",
                "shield.NoTickShieldBlockTileEntity","shield.NoTickShieldSolidBlockTileEntity","shield.TickShieldBlockTileEntity","shield.TickShieldSolidBlockTileEntity"})
            refused.put(type,shield);
        REFUSED=Collections.unmodifiableMap(refused);
    }
    private RFToolsCompat(){}

    private static String type(TileEntity tile){
        String name=tile==null?"":tile.getClass().getName();
        return name.startsWith(PREFIX)?name.substring(PREFIX.length()):null;
    }
    public static boolean rftools(TileEntity tile){return type(tile)!=null;}
    public static boolean supported(TileEntity tile){String type=type(tile);return type!=null&&SUPPORTED.contains(type);}
    /** Why an RFTools block cannot move, or null when it is supported or not an RFTools block. */
    public static String refusal(TileEntity tile){
        String type=type(tile);
        if(type==null||SUPPORTED.contains(type))return null;
        String reason=REFUSED.get(type);
        return reason!=null?reason:"it has not been checked for safe moving";
    }
    public static boolean powerCell(TileEntity tile){String type=type(tile);return type!=null&&type.startsWith("powercell.PowerCell")&&SUPPORTED.contains(type);}
    /** Copies must not join the original's Power Cell network. */
    public static boolean copyable(TileEntity tile){return supported(tile)&&!powerCell(tile);}

    /** Turn per-side data with the block: Power Cell side modes and Item Filter side masks. */
    public static void rotate(TileEntity tile,NBTTagCompound data,Rotation rotation){
        if(!supported(tile)||rotation==Rotation.NONE)return;
        if(powerCell(tile)){
            NBTTagCompound old=data.copy();
            for(EnumFacing side:EnumFacing.values()){String key="m"+side.getIndex();if(old.hasKey(key))data.setByte("m"+rotation.rotate(side).getIndex(),old.getByte(key));}
        }
        if(type(tile).equals("itemfilter.ItemFilterTileEntity"))for(String key:new String[]{"inputs","outputs"}){
            int[] before=data.getIntArray(key);if(before.length!=6)continue;int[] after=new int[6];
            for(EnumFacing side:EnumFacing.values())after[rotation.rotate(side).getIndex()]=before[side.getIndex()];data.setIntArray(key,after);
        }
        if(type(tile).equals("screens.ScreenHitTileEntity")&&data.hasKey("dx",3)){
            int dx=data.getInteger("dx"),dz=data.getInteger("dz");
            for(int turn=0;turn<rotation.ordinal();turn++){int old=dx;dx=-dz;dz=old;}
            data.setInteger("dx",dx);data.setInteger("dz",dz);
        }
    }

    /** A carried Power Cell was lifted: it leaves its network at this position, keeping the network's energy. */
    public static void lifted(World world,BlockPos pos,TileEntity tile){network(world,pos,tile,false);}
    /** A carried Power Cell was placed: it rejoins its network at this position. */
    public static void placed(World world,BlockPos pos){network(world,pos,world.getTileEntity(pos),true);}
    private static void network(World world,BlockPos pos,TileEntity tile,boolean add){
        if(world==null||world.isRemote||world instanceof VehicleWorld||!powerCell(tile))return;
        try{
            int id=(Integer)tile.getClass().getMethod("getNetworkId").invoke(tile);
            if(id<0)return;
            Class<?> networks=Class.forName("mcjty.rftools.blocks.powercell.PowerCellNetwork");
            Object channels=networks.getMethod("getChannels",World.class).invoke(null,world);
            Object network=networks.getMethod("getOrCreateNetwork",int.class).invoke(channels,id);
            Class<?> coordinate=Class.forName("mcjty.lib.varia.GlobalCoordinate");
            Object at=coordinate.getConstructor(BlockPos.class,int.class).newInstance(pos.toImmutable(),world.provider.getDimension());
            Block block=world.getBlockState(pos).getBlock();
            if(!add)block=tile.getBlockType();
            Class<?> cell=Class.forName("mcjty.rftools.blocks.powercell.PowerCellBlock");
            boolean advanced=(Boolean)cell.getMethod("isAdvanced",Block.class).invoke(null,block);
            boolean simple=(Boolean)cell.getMethod("isSimple",Block.class).invoke(null,block);
            network.getClass().getMethod(add?"add":"remove",World.class,coordinate,boolean.class,boolean.class).invoke(network,world,at,advanced,simple);
            networks.getMethod("save").invoke(channels);
        }catch(ReflectiveOperationException|LinkageError|ClassCastException failure){
            throw new IllegalStateException("RFTools Power Cell network could not be updated at "+pos,failure);
        }
    }
}
