package com.vandorlabs.compat;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.segmented.SegmentedDoorTile;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityCanopy;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import com.vandorlabs.tiles.TileEntityProgrammableTrapdoor;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.IWailaRegistrar;
import mcp.mobius.waila.api.WailaPlugin;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/** Discovered only by an installed Waila/Hwyla HUD; no mandatory runtime dependency. */
@WailaPlugin(VandorLabs.MODID)
public final class WailaCompat implements IWailaPlugin, IWailaDataProvider {
    public static final String APPEARANCE = "vandorlabs.appearance";

    @Override
    public void register(IWailaRegistrar registrar) {
        registrar.addConfig(VandorLabs.NAME, APPEARANCE, true);
        // Register disjoint tile bases once, including their programmable subclasses.
        registrar.registerBodyProvider(this, TileEntityAnimatedScreenSelector.class);
        registrar.registerBodyProvider(this, TileEntitySpaceDoor.class);
        registrar.registerBodyProvider(this, TileEntityProgrammableTrapdoor.class);
        registrar.registerBodyProvider(this, TileEntityProgrammableGlass.class);
        registrar.registerBodyProvider(this, TileEntityCanopy.class);
        registrar.registerBodyProvider(this, SegmentedDoorTile.class);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public List<String> getWailaBody(ItemStack stack, List<String> tooltip,
            IWailaDataAccessor accessor, IWailaConfigHandler config) {
        if (config.getConfig(APPEARANCE)) {
            // Appearance is already synchronized for rendering, including chunk loads.
            // Never serialize inventories, credentials or whole tile NBT for a HUD.
            com.vandorlabs.client.ProgrammableTooltip.append(
                    accessor.getWorld(), accessor.getPosition(), tooltip);
        }
        return tooltip;
    }
}
