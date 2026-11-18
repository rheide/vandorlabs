package com.vandorlabs.client;

import com.vandorlabs.navigation.NavigationMotion;
import com.vandorlabs.network.MessageNavigationImage;
import com.vandorlabs.tiles.TileEntityNavigationTable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Exercise queued raster growth through the real client tick, with live GL textures. */
final class NavigationQueuedCacheChecks {
    private static Object get(Object owner, String name) throws Exception {
        Class<?> type = owner instanceof Class ? (Class<?>) owner : owner.getClass();
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner instanceof Class ? null : owner);
    }

    private static MessageNavigationImage raster(TileEntityNavigationTable tile, int serial) {
        MessageNavigationImage image = new MessageNavigationImage(tile.getPos(), 0, serial,
                0, 0, 4, 16, false, ItemStack.EMPTY, true, 0, new int[500 * 500]);
        image.width = image.height = 500;
        return image;
    }

    static void run(Minecraft mc, TileEntityNavigationTable root) throws Exception {
        NavigationMapRenderer.clear();
        List<BlockPos> peers = new ArrayList<>();
        try {
            NavigationMapRenderer.bind(root, 0, 1);
            @SuppressWarnings("unchecked") Map<BlockPos, ?> entries =
                    (Map<BlockPos, ?>) get(NavigationMapRenderer.class, "IMAGES");
            Object first = entries.get(root.getPos());
            NavigationMotion motion = (NavigationMotion) get(first, "motion");
            double now = (Double) get(NavigationMapRenderer.class, "visualTicks");
            motion.snap(0, 0, 1);
            motion.move(1, 0, 1, now);
            NavigationMapRenderer.receive(raster(root, (Integer) get(first, "serial")));
            if (get(first, "queued") == null) throw new IllegalStateException("Raster was not queued");

            // Four completed high-resolution maps fit alongside the queued CPU raster.
            // Uploading that raster adds its GPU/texture storage and crosses the budget.
            for (int i = 0; i < 4; i++) {
                BlockPos pos = root.getPos().up(i + 2);
                if (!mc.world.isAirBlock(pos)) throw new IllegalStateException("Cache fixture cell occupied");
                peers.add(pos);
                mc.world.setBlockState(pos, root.getBlockType().getDefaultState(), 2);
                TileEntityNavigationTable peer = (TileEntityNavigationTable) mc.world.getTileEntity(pos);
                peer.layout(0, pos, true, pos, 1, 1, EnumFacing.NORTH);
                NavigationMapRenderer.bind(peer, 0, 1);
                Object entry = entries.get(pos);
                NavigationMapRenderer.receive(raster(peer, (Integer) get(entry, "serial")));
            }
            if (entries.size() != 5 || !entries.containsKey(root.getPos()))
                throw new IllegalStateException("Cache fixture evicted before queued upload");
            motion.snap(1, 0, 1);
            long uploads = NavigationMapRenderer.mapUploads;
            NavigationMapRenderer.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
            if (NavigationMapRenderer.mapUploads != uploads + 1 || get(first, "queued") != null)
                throw new IllegalStateException("Client tick did not upload the queued raster");
            if (entries.size() >= 5)
                throw new IllegalStateException("Deferred cache trim did not enforce its memory budget");
            System.out.println("[vandorlabs] navigation-queued-cache PASS queued upload, active iterator, memory eviction");
        } finally {
            NavigationMapRenderer.clear();
            for (BlockPos pos : peers) mc.world.setBlockToAir(pos);
        }
    }
    private NavigationQueuedCacheChecks() {}
}
