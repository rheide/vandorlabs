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
    @Override public String customTexture(int choice){return com.vandorlabs.client.CustomBlockTextures.texture(choice);}
    @Override public boolean customDoor(int choice){return com.vandorlabs.client.CustomBlockTextures.isDoor(choice);}
    @Override public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new com.vandorlabs.client.DiagonalWallMeshCache.Events());
        MinecraftForge.EVENT_BUS.register(new com.vandorlabs.client.ProgrammableArmorTextures.Events());
        MinecraftForge.EVENT_BUS.register(new com.vandorlabs.client.ProgrammableArmorItemModels());
        OBJLoader.INSTANCE.addDomain(VandorLabs.MODID);
        net.minecraftforge.client.model.ModelLoaderRegistry.registerLoader(new com.vandorlabs.client.UnifiedItemModels());
        MinecraftForge.EVENT_BUS.register(new com.vandorlabs.client.SpaceDoorTextures());
        MinecraftForge.EVENT_BUS.register(new com.vandorlabs.client.UnifiedTextureSprites());
        MinecraftForge.EVENT_BUS.register(new com.vandorlabs.client.OffsetTrapdoorSelection());
        MinecraftForge.EVENT_BUS.register(new com.vandorlabs.client.DiagonalScreenSelection());
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
                    net.minecraft.item.Item.getItemFromBlock(programmableDoor),
                    net.minecraft.item.Item.getItemFromBlock(net.minecraft.block.Block.REGISTRY.getObject(new net.minecraft.util.ResourceLocation("vandorlabs","large_programmable_door"))));
        }
        RenderingRegistry.registerEntityRenderingHandler(EntityChairSeat.class,
                RenderChairSeat::new);
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityLandingGear.class, new com.vandorlabs.client.TELandingGear());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityProgrammableTrapdoor.class, new com.vandorlabs.client.TEProgrammableTrapdoor());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor.class,new com.vandorlabs.client.TEProgrammableTrapdoor());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySlidingDoor.class, new TESlidingDoor());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntitySpaceDoor.class, new TESlidingDoor());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityProgrammableGlass.class,
                new com.vandorlabs.client.TEProgrammableGlass());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityControlledRamp.class, new TEControlledRamp());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityAnimatedScreenSelector.class, new TEAnimatedScreenSelector());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityProgrammableTrigger.class,
                new TEAnimatedScreenSelector());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityProgrammableLight.class,
                new TEAnimatedScreenSelector());
        if (System.getProperty("vandorlabs.reprolab") != null) {
            MinecraftForge.EVENT_BUS.register(new ReproLab());
        }
    }
}
