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
    @Override public double cockpitMotion(com.vandorlabs.tiles.TileEntityCockpit tile,boolean screens,float partial){return com.vandorlabs.client.CockpitVisualMotion.sample(tile,screens,partial);}
    @Override public void gearMotion(com.vandorlabs.network.MessageGearMotion packet){com.vandorlabs.client.GearMotionClient.receive(packet);}
    @Override public void turntablePacket(com.vandorlabs.network.MessageTurntableSnapshot packet){net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();mc.addScheduledTask(()->{if(mc.world==null)return;net.minecraft.entity.Entity raw=mc.world.getEntityByID(packet.entity);if(raw instanceof com.vandorlabs.turntable.EntityTurntable)try{((com.vandorlabs.turntable.EntityTurntable)raw).receive(packet.total,packet.offset,packet.data);}catch(Exception invalid){com.vandorlabs.VandorLabs.logger.warn("Invalid turntable snapshot",invalid);}});}
    @Override public void vehiclePacket(com.vandorlabs.vehicle.VehicleNetwork.Packet packet){com.vandorlabs.client.VehicleClient.receive(packet);}
    @Override public String customTexture(int choice){return com.vandorlabs.client.CustomBlockTextures.texture(choice);}
    @Override public double materialAspect(int choice){return com.vandorlabs.client.UnifiedTextureSprites.aspect(net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(com.vandorlabs.tiles.ScreenHousingTextures.texture(choice)));}
    @Override public boolean customDoor(int choice){return com.vandorlabs.client.CustomBlockTextures.isDoor(choice);}
    @Override public void preInit(FMLPreInitializationEvent event) {
        com.vandorlabs.client.SharedTextureResources.install();
        com.vandorlabs.client.cockpits.CockpitModels.install();
        MinecraftForge.EVENT_BUS.register(new com.vandorlabs.client.CockpitItemModels());
        com.vandorlabs.client.constructors.UniversalConstructorModelCache.preInit();
        com.vandorlabs.programmer.client.ProgrammerModelCache.INSTANCE.register();
        RenderingRegistry.registerEntityRenderingHandler(com.vandorlabs.vehicle.EntityGroundVehicle.class, com.vandorlabs.client.RenderGroundVehicle::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityChairSeat.class, RenderChairSeat::new);
        RenderingRegistry.registerEntityRenderingHandler(com.vandorlabs.turntable.EntityTurntable.class,com.vandorlabs.client.RenderTurntable::new);
        MinecraftForge.EVENT_BUS.register(new com.vandorlabs.client.DiagonalWallMeshCache.Events());
        MinecraftForge.EVENT_BUS.register(new com.vandorlabs.client.ProgrammableArmorTextures.Events());
        MinecraftForge.EVENT_BUS.register(new com.vandorlabs.client.ProgrammableArmorItemModels());
        MinecraftForge.EVENT_BUS.register(new com.vandorlabs.client.CanopyModels());
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

    @Override public void spawnRotatedThrusterParticle(World world, double x, double y, double z,
            EnumFacing facing, int color, int style, float speed, float spreadScale, float yaw) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getMinecraft();
        if (minecraft.effectRenderer == null) return;
        ParticleThrusterStream particle = new ParticleThrusterStream(world, x, y, z,
                facing, color, style, speed, spreadScale);
        particle.rotateAround(x, z, yaw);
        minecraft.effectRenderer.addEffect(particle);
    }

    @Override public void platformMotion(com.vandorlabs.network.MessagePlatformMotion message) {
        net.minecraft.client.Minecraft client=net.minecraft.client.Minecraft.getMinecraft();
        client.addScheduledTask(()->com.vandorlabs.client.PlatformRiderVisuals.receive(message));
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(new com.vandorlabs.client.ConnectedFluidRenderer.Installer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityCockpit.class,new com.vandorlabs.client.TECockpit());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityTurntableSurface.class,new com.vandorlabs.client.TETurntableSurface());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityTokenSlot.class,new com.vandorlabs.client.TETokenSlot());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.programmer.ProgrammerTile.class,new com.vandorlabs.programmer.client.ProgrammerRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.gear.GearTileEntity.class,new com.vandorlabs.gear.client.GearRenderer());
        MinecraftForge.EVENT_BUS.register(new com.vandorlabs.client.VehicleClient());
        ((net.minecraft.client.resources.IReloadableResourceManager)net.minecraft.client.Minecraft.getMinecraft().getResourceManager())
                .registerReloadListener(manager->{com.vandorlabs.client.RenderGroundVehicle.reload();TEAnimatedScreenSelector.clearCache();});
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityCanopy.class,new com.vandorlabs.client.TECanopy());
        net.minecraft.block.Block programmableDoor = net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS
                .getValue(new net.minecraft.util.ResourceLocation(VandorLabs.MODID, "programmable_door"));
        if (programmableDoor != null) {
            net.minecraft.client.Minecraft.getMinecraft().getItemColors().registerItemColorHandler(
                    (stack, tintIndex) -> tintIndex == 0 ? 0xA8A8A8 : 0xFFFFFF,
                    net.minecraft.item.Item.getItemFromBlock(programmableDoor),
                    net.minecraft.item.Item.getItemFromBlock(net.minecraft.block.Block.REGISTRY.getObject(new net.minecraft.util.ResourceLocation("vandorlabs","large_programmable_door"))));
        }
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityLandingGear.class, new com.vandorlabs.client.TELandingGear());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityProgrammableTrapdoor.class, new com.vandorlabs.client.TEProgrammableTrapdoor());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor.class,new com.vandorlabs.client.TEProgrammableTrapdoor());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySlidingDoor.class, new TESlidingDoor());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntitySpaceDoor.class, new TESlidingDoor());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityProgrammableGlass.class,
                new com.vandorlabs.client.TEProgrammableGlass());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityControlledRamp.class, new TEControlledRamp());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityAnimatedScreenSelector.class, new TEAnimatedScreenSelector());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityHexTank.class,new com.vandorlabs.client.TEHexTank());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityBattery.class,new com.vandorlabs.client.TEBattery());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityConstructor.class,new com.vandorlabs.client.constructors.TEConstructor());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityNavigationTable.class,new com.vandorlabs.client.TENavigationTable());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityProgrammableTrigger.class,
                new TEAnimatedScreenSelector());
        ClientRegistry.bindTileEntitySpecialRenderer(com.vandorlabs.tiles.TileEntityProgrammableLight.class,
                new TEAnimatedScreenSelector());
        if (System.getProperty("vandorlabs.reprolab") != null) {
            MinecraftForge.EVENT_BUS.register(new ReproLab());
        }
    }
}
