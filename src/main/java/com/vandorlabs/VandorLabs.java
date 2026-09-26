package com.vandorlabs;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraft.util.ResourceLocation;
import com.vandorlabs.entity.EntityChairSeat;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntitySlidingDoor;
import org.apache.logging.log4j.Logger;

@Mod(modid = VandorLabs.MODID, name = VandorLabs.NAME, version = VandorLabs.VERSION, acceptedMinecraftVersions = "[1.12.2]")
public class VandorLabs {

    public static final String MODID = "vandorlabs";
    public static final String NAME = "Vandor Labs";
    public static final String VERSION = "1.1";
    /** Bump on every test build so logs identify the exact binary. */
    public static final String BUILD_ID = "t49";

    @Mod.Instance(MODID)
    public static VandorLabs instance;

    public static final CreativeTabs VANDOR_LABS_TAB = new VandorLabsTab();

    public static Logger logger;

    @SidedProxy(clientSide = "com.vandorlabs.ClientProxy", serverSide = "com.vandorlabs.CommonProxy")
    public static CommonProxy proxy;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
        logger = event.getModLog();
        GameRegistry.registerTileEntity(TileEntitySlidingDoor.class, "vandorlabs:sliding_door");
        GameRegistry.registerTileEntity(com.vandorlabs.tiles.TileEntityProgrammableGlass.class, "vandorlabs:programmable_glass");
        GameRegistry.registerTileEntity(com.vandorlabs.tiles.TileEntitySpaceDoor.class, "vandorlabs:programmable_door");
        GameRegistry.registerTileEntity(com.vandorlabs.tiles.TileEntityRampController.class, "vandorlabs:programmable_ramp");
        GameRegistry.registerTileEntity(com.vandorlabs.tiles.TileEntityControlledRamp.class, "vandorlabs:controlled_ramp");
        GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class, "vandorlabs:programmable_viewscreen");
        GameRegistry.registerTileEntity(com.vandorlabs.tiles.TileEntityProgrammableTrigger.class,
                "vandorlabs:programmable_trigger_block");
        GameRegistry.registerTileEntity(com.vandorlabs.tiles.TileEntityProgrammableLight.class, "vandorlabs:programmable_light");
        GameRegistry.registerTileEntity(com.vandorlabs.tiles.TileEntityProgrammableChair.class, "vandorlabs:programmable_chair");
        GameRegistry.registerTileEntity(com.vandorlabs.tiles.TileEntityRedstoneChannel.class, "vandorlabs:redstone_channel");
        GameRegistry.registerTileEntity(com.vandorlabs.tiles.TileEntityRedstoneLight.class, "vandorlabs:redstone_light");
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "chair_seat"),
                EntityChairSeat.class, "chair_seat", 1, this, 32, 10, false);
        PacketHandler.register();
        logger.info("Vandor Labs pre-initialization: engaging warp drive...");
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        com.vandorlabs.compat.WorldEditRotationCompat.install();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(instance, new GuiHandler());
        proxy.init(event);
        logger.info("Vandor Labs initialized. Boldly going... build " + BUILD_ID);
    }
}
