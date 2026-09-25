package com.vandorlabs;

import com.vandorlabs.client.TEAnimatedScreenSelector;
import com.vandorlabs.client.TESlidingDoor;
import com.vandorlabs.client.TEControlledRamp;
import com.vandorlabs.client.ReproLab;
import com.vandorlabs.client.RenderChairSeat;
import com.vandorlabs.client.ParticleThrusterStream;
import com.vandorlabs.entity.EntityChairSeat;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntitySlidingDoor;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.client.model.obj.OBJLoader;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ClientProxy extends CommonProxy {
    @Override public void preInit(FMLPreInitializationEvent event) {
        OBJLoader.INSTANCE.addDomain(VandorLabs.MODID);
        MinecraftForge.EVENT_BUS.register(new com.vandorlabs.client.SpaceDoorTextures());
    }

    @Override public void spawnThrusterParticle(World world, BlockPos pos,
            EnumFacing facing, int color, int style, float speed) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getMinecraft();
        if (minecraft.effectRenderer == null) return;
        minecraft.effectRenderer.addEffect(
                new ParticleThrusterStream(world, pos, facing, color, style, speed));
    }

    @Override public void spawnThrusterParticle(World world, double x, double y, double z,
            EnumFacing facing, int color, int style, float speed, float spreadScale) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getMinecraft();
        if (minecraft.effectRenderer == null) return;
        minecraft.effectRenderer.addEffect(new ParticleThrusterStream(world, x, y, z,
                facing, color, style, speed, spreadScale));
    }

    @Override public void platformMotion(com.vandorlabs.network.MessagePlatformMotion message) {
        net.minecraft.client.Minecraft client=net.minecraft.client.Minecraft.getMinecraft();
        client.addScheduledTask(()->message.apply(client.player));
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        net.minecraft.block.Block programmableDoor = net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS
                .getValue(new net.minecraft.util.ResourceLocation(VandorLabs.MODID, "programmable_door"));
        if (programmableDoor != null) {
            net.minecraft.client.Minecraft.getMinecraft().getItemColors().registerItemColorHandler(
                    (stack, tintIndex) -> tintIndex == 0 ? 0xA8A8A8 : 0xFFFFFF,
                    net.minecraft.item.Item.getItemFromBlock(programmableDoor));
        }
        RenderingRegistry.registerEntityRenderingHandler(EntityChairSeat.class,
                RenderChairSeat::new);
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySlidingDoor.class, new TESlidingDoor());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntitySpaceDoor.class, new TESlidingDoor());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityProgrammableGlass.class,
                new com.vandorlabs.client.TEProgrammableGlass());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityControlledRamp.class, new TEControlledRamp());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityAnimatedScreenSelector.class, new TEAnimatedScreenSelector());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityProgrammableLight.class,
                new TEAnimatedScreenSelector());
        if (System.getProperty("vandorlabs.reprolab") != null) {
            MinecraftForge.EVENT_BUS.register(new ReproLab());
        }
    }
}
