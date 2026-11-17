package com.vandorlabs.vehicle;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
/** Opt-in navigation fixture uses the real asynchronous transfer/journal worker. */
public final class NavigationVehicleTestBridge {
    private NavigationVehicleTestBridge(){}
    public static void assemble(WorldServer world,VehicleStructure source,BlockPos origin,EntityPlayerMP actor,java.util.function.Consumer<EntityGroundVehicle> done,java.util.function.Consumer<Exception> failed){VehicleTransfer.submit(world,source,origin,()->done.accept(VehicleTransfer.assemble(world,source,origin,actor)),failed);}
    public static void park(WorldServer world,EntityGroundVehicle craft,BlockPos origin,EntityPlayerMP actor,Runnable done,java.util.function.Consumer<Exception> failed){VehicleTransfer.submit(world,()->{VehicleTransfer.park(world,craft,origin,actor,false);done.run();},failed);}
    public static boolean busy(WorldServer world){return VehicleTransfer.busy(world);}
}
