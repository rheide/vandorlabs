package com.vandorlabs.client;

import com.vandorlabs.container.ContainerMaxwellDrive;
import com.vandorlabs.drives.MaxwellRuntimeChecks;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.TileEntityConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidStack;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.concurrent.Future;

/** Actual reserved-member clicks, server configuration/purge packets, and world-frame captures. */
final class MaxwellDriveLiveChecks {
    private static final BlockPos ROOT = new BlockPos(24, 100, 24);
    private static final BlockPos LARGE = ROOT.east(26);
    private static final BlockPos CONTACT = LARGE.east(8).up(7);
    private static final double X = CONTACT.getX() + .5, Z = CONTACT.getZ() - 2.5;
    private static final LiveGallery GALLERY = new LiveGallery("shot_maxwell", ROOT)
            .shot("assembly-front", -2.5, 3, -5.7, -31, 23)
            .shot("afterburner-rear", 4.8, 2.7, 7.5, 149, 22)
            .shot("scaled-inlets", 20, 7, -8, -37, 21);
    private static Future<?> setup;
    private static int stage, ticks;

    private MaxwellDriveLiveChecks() {}

    static boolean tick(Minecraft mc, File output) throws Exception {
        if (mc.player == null || mc.world == null || mc.getIntegratedServer() == null) return false;
        if (stage == 0) {
            if (setup == null) {
                setup = mc.getIntegratedServer().addScheduledTask(() -> setup(mc));
                return false;
            }
            if (!setup.isDone()) return false;
            setup.get();
            next(1);
        }
        if (stage == 1) {
            if (!GALLERY.tick(mc, output)) return false;
            mc.gameSettings.hideGUI = false;
            prepareClick(mc, ItemStack.EMPTY);
            next(2);
            return false;
        }
        require(++ticks < 400, "stage=" + stage + " ticks=" + ticks + " mode=" + mc.playerController.getCurrentGameType()
                + " held=" + mc.player.getHeldItemMainhand() + " pos=" + mc.player.getPosition() + " screen=" + mc.currentScreen);
        if (stage == 2 || stage == 4) {
            boolean configurizer = stage == 4;
            if (!readyToClick(mc, configurizer)) return false;
            Vec3d eye = mc.player.getPositionEyes(1);
            RayTraceResult ray = mc.world.rayTraceBlocks(eye, eye.add(mc.player.getLook(1).scale(4)), false, false, false);
            require(ray != null && CONTACT.equals(ray.getBlockPos()) && ray.sideHit == EnumFacing.NORTH, "aim reaches the large drive member");
            mc.playerController.processRightClickBlock(mc.player, mc.world, ray.getBlockPos(), ray.sideHit, ray.hitVec, EnumHand.MAIN_HAND);
            next(stage + 1);
            return false;
        }
        if (stage == 3 && dialog(mc) != null) {
            capture(mc, output, "engine-dialog");
            mc.player.closeScreen();
            prepareClick(mc, new ItemStack(ModItems.CONFIGURIZER));
            next(4);
            return false;
        }
        if (stage == 5 && dialog(mc) != null) {
            GuiNavigationTable gui = dialog(mc);
            click(gui, 21, 0);
            require(gui.control(9).displayString.equals("Throttle: Digital"), "shared Redstone pane opens in digital mode");
            click(gui, 9, 0);
            require(gui.control(9).displayString.equals("Throttle: Analog"), "left click selects analog mode");
            click(gui, 9, 1);
            require(gui.control(9).displayString.equals("Throttle: Digital"), "right click selects previous throttle mode");
            click(gui, 9, 0);
            capture(mc, output, "redstone-dialog");
            click(gui, 20, 0);
            click(gui, 93, 0);
            next(6);
            return false;
        }
        if (stage == 6 && mc.world.getTileEntity(LARGE) instanceof TileEntityConstructor) {
            TileEntityConstructor engine = (TileEntityConstructor) mc.world.getTileEntity(LARGE);
            if (engine.maxwellFuel() != 0 || !engine.driveSettings().levels) return false;
            mc.player.closeScreen();
            System.out.println("[vandorlabs][reprolab] maxwell-drive PASS ordinary/Configurator member clicks, analog previous/next, purge round trip, world captures");
            return true;
        }
        return false;
    }

    private static void setup(Minecraft mc) {
        EntityPlayerMP player = player(mc);
        WorldServer world = player.getServerWorld();
        MaxwellRuntimeChecks.run(world);
        world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
        world.setWorldTime(6000);
        world.getWorldInfo().setRaining(false);
        player.setGameType(GameType.CREATIVE);
        for (BlockPos p : BlockPos.getAllInBox(ROOT.add(-7, -1, -10), LARGE.add(14, -1, 18)))
            world.setBlockState(p, Blocks.STONE.getDefaultState(), 2);
        ItemStack core = new ItemStack(net.minecraft.block.Block.getBlockFromName("vandorlabs:longburn_drive_core"));
        player.rotationYaw = EnumFacing.SOUTH.getHorizontalAngle();
        require(LiveGallery.place(world, player, core, ROOT.down(), EnumFacing.UP, .5F, .5F, .5F), "ordinary Core item placement");
        TileEntityConstructor base = MaxwellRuntimeChecks.assemble(world, ROOT, 3, EnumFacing.NORTH, 0, 0);
        TileEntityConstructor large = MaxwellRuntimeChecks.assemble(world, LARGE, 9, EnumFacing.NORTH, 0, 0);
        for (TileEntityConstructor engine : new TileEntityConstructor[]{base, large}) {
            require(MaxwellRuntimeChecks.inlet(engine, EnumFacing.NORTH).fill(new FluidStack(MaxwellRuntimeChecks.testFuel(), 650), true) == 650,
                    "gallery fixture fills the formed front inlet");
        }
        base.configureMachine(new com.vandorlabs.navigation.NavigationScreenSettings(-1, false, 1, true, false), new int[0],
                false, 127, 0, com.vandorlabs.redstone.ChannelList.EMPTY);
        world.setBlockState(ROOT.down(), Blocks.REDSTONE_BLOCK.getDefaultState(), 3);
    }

    private static void prepareClick(Minecraft mc, ItemStack held) {
        mc.getIntegratedServer().addScheduledTask(() -> {
            EntityPlayerMP player = player(mc);
            player.setGameType(GameType.SURVIVAL);
            player.inventory.currentItem = 0;
            player.connection.sendPacket(new SPacketHeldItemChange(0));
            player.capabilities.allowFlying = true;
            player.capabilities.isFlying = true;
            player.sendPlayerAbilities();
            player.setSneaking(false);
            player.setHeldItem(EnumHand.MAIN_HAND, held);
            player.setHeldItem(EnumHand.OFF_HAND, ItemStack.EMPTY);
            player.inventoryContainer.detectAndSendChanges();
            player.connection.setPlayerLocation(X, CONTACT.getY() + .5 - player.getEyeHeight(), Z, 0, 0);
        });
    }

    private static boolean readyToClick(Minecraft mc, boolean configurizer) {
        return ticks >= 10 && !mc.playerController.isInCreativeMode() && mc.player.inventory.currentItem == 0
                && mc.player.capabilities.isFlying && mc.player.getDistanceSq(X, CONTACT.getY() + .5 - mc.player.getEyeHeight(), Z) < .01
                && (configurizer ? mc.player.getHeldItemMainhand().getItem() == ModItems.CONFIGURIZER : mc.player.getHeldItemMainhand().isEmpty());
    }

    private static GuiNavigationTable dialog(Minecraft mc) {
        if (ticks < 10 || !(mc.currentScreen instanceof GuiNavigationTable)) return null;
        GuiNavigationTable gui = (GuiNavigationTable) mc.currentScreen;
        require(gui.inventorySlots instanceof ContainerMaxwellDrive, "both click paths open the dedicated Maxwell container");
        return gui;
    }

    private static void click(GuiNavigationTable gui, int id, int button) throws Exception {
        net.minecraft.client.gui.GuiButton control = gui.control(id);
        require(control != null, "missing Maxwell dialog control " + id);
        gui.mouseClicked(control.x + control.width / 2, control.y + control.height / 2, button);
    }

    private static void capture(Minecraft mc, File output, String name) throws Exception {
        ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth, mc.displayHeight, mc.getFramebuffer()), "png",
                new File(output, "shot_maxwell-" + name + ".png"));
    }

    private static EntityPlayerMP player(Minecraft mc) { return mc.getIntegratedServer().getPlayerList().getPlayers().get(0); }
    private static void next(int phase) { stage = phase; ticks = 0; }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Maxwell live: " + message);
    }
}
