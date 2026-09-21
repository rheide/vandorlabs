package com.vandorlabs;

import com.vandorlabs.compat.BetterBuildersWandsCompat;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class CommonProxy {
    public void platformMotion(com.vandorlabs.network.MessagePlatformMotion message) { }
    public void preInit(FMLPreInitializationEvent event) { }
    public void spawnThrusterParticle(World world, BlockPos pos, EnumFacing facing,
            int color, int style, float speed) { }
    public void spawnThrusterParticle(World world, double x, double y, double z,
            EnumFacing facing, int color, int style, float speed, float spreadScale) { }

    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(BetterBuildersWandsCompat.INSTANCE);
    }
}
