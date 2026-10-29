package com.vandorlabs;

import com.vandorlabs.compat.BetterBuildersWandsCompat;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class CommonProxy {
    /** Physical server and integrated-server light reads use only shared APIs. */
    public int vehicleCombinedLight(World world,BlockPos pos,int minimum){return sharedVehicleCombinedLight(world,pos,minimum);}
    public static int sharedVehicleCombinedLight(World world,BlockPos pos,int minimum){
        int sky=0,block=0;boolean skylight=world.provider.hasSkyLight();
        if(world.isBlockLoaded(pos)){
            if(world.getBlockState(pos).useNeighborBrightness()){
                for(EnumFacing face:EnumFacing.VALUES)if(face!=EnumFacing.DOWN){
                    BlockPos neighbor=pos.offset(face);if(!world.isBlockLoaded(neighbor))continue;
                    if(skylight)sky=Math.max(sky,world.getLightFor(net.minecraft.world.EnumSkyBlock.SKY,neighbor));
                    block=Math.max(block,world.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK,neighbor));
                }
            }else{
                if(skylight)sky=world.getLightFor(net.minecraft.world.EnumSkyBlock.SKY,pos);
                block=world.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK,pos);
            }
        }
        return (sky<<20)|(Math.max(block,minimum)<<4);
    }
    public void vehicleBuildingVisuals(com.vandorlabs.vehicle.VehicleWorld world){}
    public double cockpitMotion(com.vandorlabs.tiles.TileEntityCockpit tile,boolean screens,float partial){return (screens?tile.screenLift:tile.seatSlide).sample(tile.getWorld().getTotalWorldTime()+partial);}
    public void gearMotion(com.vandorlabs.network.MessageGearMotion packet) { }
    public void turntablePacket(com.vandorlabs.network.MessageTurntableSnapshot packet) { }
    public double materialAspect(int choice){return 1;}
    public void vehiclePacket(com.vandorlabs.vehicle.VehicleNetwork.Packet packet) { }
    public String customTexture(int choice){return "vandorlabs:blocks/dark_wall_panel";}
    public boolean customDoor(int choice){return false;}
    public void platformMotion(com.vandorlabs.network.MessagePlatformMotion message) { }
    public void preInit(FMLPreInitializationEvent event) { }
    public void spawnThrusterParticle(World world, BlockPos pos, EnumFacing facing,
            int color, int style, float speed) { }
    public void spawnThrusterParticle(World world, double x, double y, double z,
            EnumFacing facing, int color, int style, float speed, float spreadScale) { }
    public void spawnRotatedThrusterParticle(World world, double x, double y, double z,
            EnumFacing facing, int color, int style, float speed, float spreadScale, float yaw) { }

    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(BetterBuildersWandsCompat.INSTANCE);
        MinecraftForge.EVENT_BUS.register(com.vandorlabs.tiles.OffsetTrapdoorInteractions.INSTANCE);
        MinecraftForge.EVENT_BUS.register(com.vandorlabs.tiles.MovedDoorInteractions.INSTANCE);
    }
}
