package com.vandorlabs.client;

import com.vandorlabs.CommonProxy;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.*;
import com.vandorlabs.compat.WailaCompat;
import com.vandorlabs.tiles.*;
import mcp.mobius.waila.api.*;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Optional API registration and real placed-tile lookup, without a GL context. */
public final class WailaTooltipChecks {
    private static final BlockPos POS = new BlockPos(8, 80, 8);
    private static int cases;
    private static final List<Class<?>> registeredTypes = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        net.minecraft.init.Bootstrap.register();
        VandorLabs.proxy = new CommonProxy();
        language();
        registration();
        NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(true);
        int finish = ScreenHousingTextures.screenIndex("integrated_circuit_board_panel_1");
        require("Circuit Panel".equals(HousingTextureList.name(finish)), "known picker label");
        BlockDetailedDoor paired = new BlockDetailedDoor("tooltip_pair_fixture", BlockVandorDoor.DoorMotion.SLIDING,
                true, true, false, 1, 15, BlockSpaceDoor.PIN_Z, 90, -90, -15, 15);
        BlockConfigurableSpaceDoor door = new BlockConfigurableSpaceDoor("tooltip_door_fixture", false, paired);
        BlockLargeProgrammableDoor large = new BlockLargeProgrammableDoor("tooltip_large_fixture", paired);
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            for (boolean sliding : new boolean[]{false, true}) {
                world.clear();
                TileEntitySpaceDoor lower = new TileEntitySpaceDoor();
                lower.configure(2, 1, true, 0, false, sliding);
                lower.setFaceTexture(finish);
                IBlockState state = door.getDefaultState().withProperty(BlockVandorDoor.FACING, facing);
                put(world, POS, state, lower);
                put(world, POS.up(), state.withProperty(BlockVandorDoor.HALF, BlockDoor.EnumDoorHalf.UPPER),
                        new TileEntitySpaceDoor()); // Deliberately stale/default upper appearance.
                expect(world, POS, "Circuit Panel", sliding ? "Sliding" : "Rotating");
                expect(world, POS.up(), "Circuit Panel", sliding ? "Sliding" : "Rotating");
                world.tiles.remove(POS);
                require(lines(world, POS.up(), true).isEmpty(), "missing root is not the upper's defaults");

                world.clear();
                IBlockState largeState = large.getDefaultState().withProperty(BlockVandorDoor.FACING, facing);
                for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) {
                    BlockPos cell = POS.offset(facing.rotateYCCW(), x).up(y);
                    TileEntityLargeProgrammableDoor tile = new TileEntityLargeProgrammableDoor();
                    if (x == 0 && y == 0) {
                        tile.configure(2, 1, true, 0, false, sliding);
                        tile.setFaceTexture(finish);
                    }
                    put(world, cell, largeState, tile);
                    tile.assign(POS);
                }
                for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++)
                    expect(world, POS.offset(facing.rotateYCCW(), x).up(y),
                            "Circuit Panel", sliding ? "Sliding" : "Rotating");
            }
        }

        world.clear();
        TileEntitySpaceDoor nativeDoor = new TileEntitySpaceDoor();
        nativeDoor.configure(2, 1, true, 0, false, false);
        put(world, POS, door.getDefaultState(), nativeDoor);
        put(world, POS.up(), door.getDefaultState().withProperty(BlockVandorDoor.HALF,
                BlockDoor.EnumDoorHalf.UPPER), new TileEntitySpaceDoor());
        expect(world, POS, "Standard", "Rotating");
        expect(world, POS.up(), "Standard", "Rotating");
        unloadedLargeOwner(world, large, finish);

        world.clear();
        BlockAnimatedScreenSelector screen = new BlockAnimatedScreenSelector();
        ModBlocks.ANIMATED_SCREEN_SELECTOR = screen;
        for (net.minecraft.block.Block block : new net.minecraft.block.Block[]{
                new BlockProgrammableBlock(), new BlockProgrammableSlab(),
                new BlockProgrammableWall("tooltip_wall_fixture", BlockProgrammableWall.Shape.PLAIN),
                new BlockProgrammablePortholeBlock(), screen, new BlockProgrammableConsole()}) {
            TileEntityAnimatedScreenSelector tile = new TileEntityAnimatedScreenSelector();
            tile.setHousingTexture(finish);
            if (TileEntityAnimatedScreenSelector.supportsActivation(block)) tile.setSurfaceTexture(0, finish);
            put(world, POS, block.getDefaultState(), tile);
            expect(world, POS, "Circuit Panel", null);
        }
        int[] special = {TileEntityAnimatedScreenSelector.REDSTONE_SURFACE,
                TileEntityAnimatedScreenSelector.CHIP_SURFACE, TileEntityAnimatedScreenSelector.VEHICLE_SURFACE};
        String[] specialNames = {"Redstone controls", "Data Chip display", "Vehicle Status"};
        for (int i = 0; i < special.length; i++) {
            TileEntityAnimatedScreenSelector tile = new TileEntityAnimatedScreenSelector();
            tile.setSurfaceTexture(0, special[i]);
            put(world, POS, screen.getDefaultState(), tile);
            expect(world, POS, specialNames[i], null);
        }
        TileEntityProgrammableTrapdoor hatch = new TileEntityProgrammableTrapdoor();
        hatch.configure(finish, 0, true, com.vandorlabs.persistence.SpaceDoorData.TRIGGER_DISABLED, 0);
        put(world, POS, new BlockProgrammableTrapdoor().getDefaultState(), hatch);
        expect(world, POS, "Circuit Panel", "Sliding");
        TileEntityProgrammableGlass glass = new TileEntityProgrammableGlass();
        glass.setShade(GlassTextures.IDS.length - 1);
        put(world, POS, new BlockProgrammableGlass("tooltip_glass_fixture").getDefaultState(), glass);
        expect(world, POS, "Gold", null);
        glass.setMaterial(finish);
        expect(world, POS, "Circuit Panel", null);
        require(lines(world, POS, false).isEmpty(), "HUD setting disables all added lines");
        canopyAndSegmented(world, finish);

        world.chunkLimit = true;
        require(lines(world, POS.add(32, 0, 0), true).isEmpty(), "unloaded positions do not read chunks");
        require(ProgrammableTooltip.appearanceTile(null, POS) == null, "missing world");
        System.out.println("Waila tooltips PASS cases=" + cases
                + " native/selected artwork, special display labels, six tile families, 4 facings, both movements, upper/9-cell owners, HUD toggle, missing/unloaded roots");
    }

    private static void unloadedLargeOwner(NonRenderingChecks.MemoryWorld world,
            BlockLargeProgrammableDoor block, int finish) {
        world.clear();
        BlockPos part = new BlockPos(15, 80, 8), root = part.east();
        IBlockState state = block.getDefaultState().withProperty(BlockVandorDoor.FACING, EnumFacing.NORTH);
        TileEntityLargeProgrammableDoor owner = new TileEntityLargeProgrammableDoor();
        owner.configure(2, 1, true, 0, false, false);
        owner.setFaceTexture(finish);
        put(world, root, state, owner);
        owner.assign(root);
        TileEntityLargeProgrammableDoor member = new TileEntityLargeProgrammableDoor();
        put(world, part, state, member);
        member.assign(root);
        expect(world, part, "Circuit Panel", "Rotating");
        world.tiles.remove(root);
        require(lines(world, part, true).isEmpty(), "loaded large part ignores missing owner");
        world.tiles.put(root, owner);
        world.chunkLimit = true;
        require(lines(world, part, true).isEmpty(), "loaded large part does not query an unloaded owner chunk");
        world.chunkLimit = false;
    }

    private static void canopyAndSegmented(NonRenderingChecks.MemoryWorld world, int finish) {
        world.clear();
        IBlockState canopyState = new BlockCanopy("canopy").getDefaultState();
        TileEntityCanopy owner = new TileEntityCanopy();
        owner.large.material = finish;
        put(world, POS, canopyState, owner);
        TileEntityCanopy constituent = new TileEntityCanopy();
        constituent.anchor = POS;
        put(world, POS.up(), canopyState, constituent);
        TileEntityCanopy paired = new TileEntityCanopy();
        paired.pair = POS;
        put(world, POS.east(), canopyState, paired);
        expect(world, POS, "Circuit Panel", null);
        expect(world, POS.up(), "Circuit Panel", null);
        expect(world, POS.east(), "Circuit Panel", null);

        world.clear();
        com.vandorlabs.segmented.SegmentedDoorTile segmented = new com.vandorlabs.segmented.SegmentedDoorTile();
        put(world, POS, new com.vandorlabs.segmented.SegmentedDoorBlock().getDefaultState(), segmented);
        expect(world, POS, "Original segmented panels", "Sliding");
        segmented.texture = finish;
        expect(world, POS, "Circuit Panel", "Sliding");
    }

    private static void put(NonRenderingChecks.MemoryWorld world, BlockPos pos, IBlockState state, TileEntity tile) {
        world.states.put(pos, state);
        tile.setWorld(world);
        tile.setPos(pos);
        world.tiles.put(pos, tile);
    }

    private static void expect(NonRenderingChecks.MemoryWorld world, BlockPos pos, String name, String motion) {
        List<String> expected = new ArrayList<>();
        expected.add("Texture: " + name);
        if (motion != null) expected.add("Movement: " + motion);
        require(expected.equals(lines(world, pos, true)), "tooltip at " + pos + " expected=" + expected);
        cases++;
    }

    private static List<String> lines(NonRenderingChecks.MemoryWorld world, BlockPos pos, boolean enabled) {
        TileEntity hit = world.tiles.get(pos);
        if (hit != null) require(registeredTypes.stream().filter(type -> type.isInstance(hit)).count() == 1,
                "exactly one registered provider for " + hit.getClass().getSimpleName());
        IWailaDataAccessor accessor = (IWailaDataAccessor) Proxy.newProxyInstance(
                IWailaDataAccessor.class.getClassLoader(), new Class<?>[]{IWailaDataAccessor.class},
                (proxy, method, args) -> method.getName().equals("getWorld") ? world
                        : method.getName().equals("getPosition") ? pos : null);
        IWailaConfigHandler config = (IWailaConfigHandler) Proxy.newProxyInstance(
                IWailaConfigHandler.class.getClassLoader(), new Class<?>[]{IWailaConfigHandler.class},
                (proxy, method, args) -> enabled);
        List<String> result = new ArrayList<>();
        require(new WailaCompat().getWailaBody(ItemStack.EMPTY, result, accessor, config) == result,
                "provider preserves the HUD list");
        return result;
    }

    private static void registration() {
        List<Class<?>> types = registeredTypes;
        IWailaRegistrar registrar = (IWailaRegistrar) Proxy.newProxyInstance(
                IWailaRegistrar.class.getClassLoader(), new Class<?>[]{IWailaRegistrar.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("registerBodyProvider")) types.add((Class<?>) args[1]);
                    else require(method.getName().equals("addConfig"), "no extra NBT/stack/network provider");
                    return null;
                });
        new WailaCompat().register(registrar);
        require(types.size() == 6, "all six programmable tile families registered");
        for (int a = 0; a < types.size(); a++) for (int b = a + 1; b < types.size(); b++)
            require(!types.get(a).isAssignableFrom(types.get(b)) && !types.get(b).isAssignableFrom(types.get(a)),
                    "no duplicate tooltip through inherited registrations");
    }

    @SuppressWarnings("unchecked")
    private static void language() throws Exception {
        net.minecraft.client.resources.Locale locale = new net.minecraft.client.resources.Locale();
        Map<String, String> translations = null;
        for (Field field : locale.getClass().getDeclaredFields()) if (Map.class.isAssignableFrom(field.getType())) {
            field.setAccessible(true); translations = (Map<String, String>) field.get(locale); break;
        }
        require(translations != null, "Minecraft locale dictionary");
        try (BufferedReader input = new BufferedReader(new InputStreamReader(
                WailaTooltipChecks.class.getResourceAsStream("/assets/vandorlabs/lang/en_us.lang"), "UTF-8"))) {
            String line;
            while ((line = input.readLine()) != null) {
                int at = line.indexOf('=');
                if (at > 0 && !line.startsWith("#")) translations.put(line.substring(0, at), line.substring(at + 1));
            }
        }
        for (Field field : I18n.class.getDeclaredFields()) if (field.getType() == locale.getClass()) {
            field.setAccessible(true); field.set(null, locale);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
