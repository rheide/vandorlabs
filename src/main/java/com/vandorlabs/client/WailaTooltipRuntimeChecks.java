package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.compat.WailaCompat;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.VehicleStructure;
import com.vandorlabs.vehicle.VehicleWorld;
import mcp.mobius.waila.api.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.*;

/** Checks actual installed HUD discovery against synchronized appearance data. */
public final class WailaTooltipRuntimeChecks {
    private WailaTooltipRuntimeChecks() { }

    public static void run(Minecraft mc, File output) throws Exception {
        require(net.minecraftforge.fml.common.Loader.isModLoaded("waila"), "Hwyla must be installed for this focus");
        int finish = ScreenHousingTextures.screenIndex("integrated_circuit_board_panel_1");
        require("Circuit Panel".equals(HousingTextureList.name(finish)), "known catalog label");
        int checks = 0;
        for (String id : new String[]{"programmable_block", "programmable_slab", "programmable_wall",
                "programmable_porthole_block", "programmable_viewscreen", "programmable_console",
                "programmable_half_console", "programmable_trapdoor", "programmable_diagonal_trapdoor",
                "programmable_glass", "programmable_diagonal_glass", "programmable_light"}) {
            Block block = block(id);
            IBlockState state = block.getDefaultState();
            TileEntity tile = block.createTileEntity(mc.world, state);
            require(tile != null, "fixture tile " + id);
            String movement = null;
            if (tile instanceof TileEntityProgrammableLight) {
                ((TileEntityProgrammableLight) tile).setFaceTexture(finish);
            } else if (tile instanceof TileEntityProgrammableTrapdoor) {
                ((TileEntityProgrammableTrapdoor) tile).configure(finish, 0, true,
                        com.vandorlabs.persistence.SpaceDoorData.TRIGGER_DISABLED, 0);
                movement = "Sliding";
            } else if (tile instanceof TileEntityProgrammableGlass) {
                ((TileEntityProgrammableGlass) tile).setMaterial(finish);
            } else {
                TileEntityAnimatedScreenSelector panel = (TileEntityAnimatedScreenSelector) tile;
                panel.setHousingTexture(finish);
                if (block instanceof BlockProgrammableDiagonalGlass) panel.setGlassMaterial(finish);
                if (TileEntityAnimatedScreenSelector.supportsActivation(block)) panel.setSurfaceTexture(0, finish);
            }
            World world = fixture(mc, Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN, state, null)));
            install((VehicleWorld) world, BlockPos.ORIGIN, tile);
            check(world, BlockPos.ORIGIN, "Circuit Panel", movement);
            checks++;
        }
        for (String id : new String[]{"programmable_door", "large_programmable_door"}) {
            Block block = block(id);
            boolean large = block instanceof BlockLargeProgrammableDoor;
            for (boolean sliding : new boolean[]{false, true}) {
                List<VehicleStructure.Cell> cells = new ArrayList<>();
                IBlockState state = block.getDefaultState().withProperty(BlockVandorDoor.FACING, EnumFacing.SOUTH);
                int width = large ? 3 : 1, height = large ? 3 : 2;
                for (int x = 0; x < width; x++) for (int y = 0; y < height; y++)
                    cells.add(new VehicleStructure.Cell(new BlockPos(x, y, 0),
                            large || y == 0 ? state : state.withProperty(BlockVandorDoor.HALF, BlockDoor.EnumDoorHalf.UPPER), null));
                VehicleWorld world = fixture(mc, cells);
                for (VehicleStructure.Cell cell : cells) {
                    TileEntitySpaceDoor tile = large ? new TileEntityLargeProgrammableDoor() : new TileEntitySpaceDoor();
                    if (cell.pos.equals(BlockPos.ORIGIN)) {
                        tile.configure(2, 1, true, 0, false, sliding);
                        tile.setFaceTexture(finish);
                    }
                    install(world, cell.pos, tile);
                    if (large) ((TileEntityLargeProgrammableDoor) tile).assign(BlockPos.ORIGIN);
                }
                for (VehicleStructure.Cell cell : cells) {
                    check(world, cell.pos, "Circuit Panel", sliding ? "Sliding" : "Rotating");
                    checks++;
                }
            }
        }
        System.out.println("[vandorlabs][reprolab] waila-appearance PASS installed providers=" + checks
                + " primary textures, upper/large owner lookups, rotating/sliding, no duplicate lines");
    }

    public static void check(World world, BlockPos pos, String texture, String movement) throws Exception {
        TileEntity tile = world.getTileEntity(pos);
        Class<?> registrarType = Class.forName("mcp.mobius.waila.api.impl.ModuleRegistrar");
        Object registrar = registrarType.getMethod("instance").invoke(null);
        Map<?, ?> groups = (Map<?, ?>) registrarType.getMethod("getBodyProviders", Object.class).invoke(registrar, tile);
        List<IWailaDataProvider> own = new ArrayList<>();
        for (Object group : groups.values()) for (Object provider : (List<?>) group)
            if (provider instanceof WailaCompat) own.add((IWailaDataProvider) provider);
        require(own.size() == 1, "one discovered appearance provider for " + tile.getClass().getSimpleName());
        IWailaDataAccessor accessor = (IWailaDataAccessor) Proxy.newProxyInstance(
                IWailaDataAccessor.class.getClassLoader(), new Class<?>[]{IWailaDataAccessor.class},
                (proxy, method, args) -> method.getName().equals("getWorld") ? world
                        : method.getName().equals("getPosition") ? pos : null);
        IWailaConfigHandler config = (IWailaConfigHandler) Proxy.newProxyInstance(
                IWailaConfigHandler.class.getClassLoader(), new Class<?>[]{IWailaConfigHandler.class},
                (proxy, method, args) -> true);
        List<String> actual = own.get(0).getWailaBody(ItemStack.EMPTY, new ArrayList<>(), accessor, config);
        List<String> expected = new ArrayList<>();
        expected.add("Texture: " + texture);
        if (movement != null) expected.add("Movement: " + movement);
        require(expected.equals(actual), "expected " + expected + " at " + pos + ", received " + actual);
    }

    private static VehicleWorld fixture(Minecraft mc, List<VehicleStructure.Cell> cells) {
        VehicleWorld world = new VehicleWorld(mc.world, VehicleStructure.display(cells));
        world.origin = new BlockPos(0, 80, 0);
        return world;
    }

    private static void install(VehicleWorld world, BlockPos pos, TileEntity tile) {
        tile.setWorld(world);
        tile.setPos(pos);
        world.tiles.put(pos, tile);
    }

    private static Block block(String id) {
        Block block = Block.getBlockFromName("vandorlabs:" + id);
        require(block != null, "registered block " + id);
        return block;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Waila appearance: " + message);
    }
}
