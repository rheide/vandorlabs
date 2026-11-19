package com.vandorlabs.neoforge.seating;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.seating.client.NativeSeatScreen;
import java.nio.file.Files;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import static com.vandorlabs.neoforge.seating.NativeAdvancedChairFixture.*;

@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeAdvancedChairClientCheck {
    private static int stage, ticks, wait;
    private static String screenshot;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!enabled()) return;
        var game = Minecraft.getInstance();
        var player = game.player;
        if (player == null || game.level == null || game.gameMode == null || game.getOverlay() != null) return;
        require(++ticks < 1200, "timeout stage=" + stage + " pos=" + player.position()
            + " selected=" + player.getInventory().selected + " held=" + player.getMainHandItem()
            + " mode=" + game.gameMode.getPlayerMode());
        var block = NativeSeatBindings.BLOCKS.get(NativeSeatKind.ADVANCED);
        var owner = NativeSeatBlock.owner(game.level, ROOT);
        switch (stage) {
            case 0 -> {
                if (game.gameMode.getPlayerMode() != GameType.SURVIVAL || player.getInventory().selected != 0
                    || !player.getMainHandItem().is(NativeSeatBindings.ITEMS.get(NativeSeatKind.ADVANCED))
                    || player.position().distanceToSqr(START) > .01) return;
                use(game, ROOT.below(), Direction.UP);
                stage++;
            }
            case 1 -> {
                if (owner == null || !block.complete(game.level, ROOT) || !player.getMainHandItem().isEmpty()) return;
                require(NativeSeatBlock.owner(game.level, SIDE) == owner && NativeSeatBlock.owner(game.level, ROOT.east()) == owner
                    && NativeSeatBlock.owner(game.level, ROOT.above()) == owner && owner.pilotHeight() == 0,
                    "all four cells resolve the single placed owner");
                require(geometry(game, ROOT, owner) > 0 && geometry(game, SIDE, owner) == 0, "only anchor emits authored chair");
                player.setXRot(19);
                screenshot = "advanced-pilot-chair-placed.png";
                select(game, 1);
                stage++;
            }
            case 2 -> {
                if (screenshot != null || !player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())) return;
                use(game, SIDE, Direction.NORTH);
                stage++;
            }
            case 3 -> {
                if (!(game.screen instanceof NativeSeatScreen screen)) return;
                require(screen.getMenu().tile.getBlockPos().equals(ROOT), "side opens anchor's dedicated dialog");
                var height = screen.option(11);
                require(height != null && screen.mouseClicked(height.getX() + 8, height.getY() + 8, 1), "actual previous height input");
                stage++;
            }
            case 4 -> {
                if (owner == null || owner.pilotHeight() != 3 || !(game.screen instanceof NativeSeatScreen screen)) return;
                screenshot = "advanced-pilot-chair-height.png";
                stage++;
            }
            case 5 -> {
                if (screenshot != null) return;
                player.closeContainer();
                select(game, 3);
                wait = ticks;
                stage++;
            }
            case 6 -> {
                if (game.screen != null || ticks < wait + 6 || !player.getMainHandItem().isEmpty()) return;
                use(game, SIDE, Direction.NORTH);
                stage++;
            }
            case 7 -> {
                if (!(player.getVehicle() instanceof NativeChairMount mount) || !ROOT.equals(mount.chairPos())) return;
                var cushion = NativeAdvancedChairGeometry.cushion(Direction.NORTH, 3);
                require(Math.abs(player.getX() - ROOT.getX() - cushion.x) < .001
                    && Math.abs(player.getZ() - ROOT.getZ() - cushion.z) < .001, "real rider matches authored cushion offset");
                require(Math.abs(net.minecraft.util.Mth.wrapDegrees(player.getYRot() - mount.getYRot())) < 1,
                    "boarding faces the player towards the chair's front");
                game.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                screenshot = "advanced-pilot-chair-seated.png";
                stage++;
            }
            case 8 -> {
                if (screenshot != null) return;
                game.options.keyShift.setDown(true);
                wait = ticks;
                stage++;
            }
            case 9 -> {
                if (player.isPassenger() || ticks < wait + 4) return;
                game.options.keyShift.setDown(false);
                select(game, 2);
                wait = ticks;
                stage++;
            }
            case 10 -> {
                if (ticks < wait + 6 || !player.getMainHandItem().is(Items.DIAMOND_PICKAXE)) return;
                game.gameMode.startDestroyBlock(SIDE, Direction.NORTH);
                stage++;
            }
            case 11 -> {
                if (game.level.isEmptyBlock(SIDE) && game.level.isEmptyBlock(ROOT)
                    && game.level.isEmptyBlock(ROOT.east()) && game.level.isEmptyBlock(ROOT.above())) {
                    game.gameMode.stopDestroyBlock();
                    stage++;
                    wait = ticks;
                } else game.gameMode.continueDestroyBlock(SIDE, Direction.NORTH);
            }
            case 12 -> {
                if (ticks < wait + 8) return;
                System.out.println("[vandorlabs][advanced-chair] client PASS");
                game.stop();
                stage++;
            }
        }
    }

    private static int geometry(Minecraft game, BlockPos pos, NativeChairEntity owner) {
        var state = game.level.getBlockState(pos);
        var model = game.getBlockRenderer().getBlockModel(state);
        var data = model.getModelData(game.level, pos, state, owner.getModelData());
        var quads = model.getQuads(state, null, RandomSource.create(0), data, null);
        require(quads.stream().noneMatch(quad -> quad.getSprite().contents().name().getPath().equals("missingno")),
            "all authored materials resolve");
        return quads.size();
    }

    private static void use(Minecraft game, BlockPos pos, Direction face) {
        var point = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));
        game.gameMode.useItemOn(game.player, InteractionHand.MAIN_HAND, new BlockHitResult(point, face, pos, false));
    }

    private static void select(Minecraft game, int hotbarSlot) {
        game.player.getInventory().selected = hotbarSlot;
        game.player.connection.send(new ServerboundSetCarriedItemPacket(hotbarSlot));
    }

    @SubscribeEvent
    public static void render(RenderFrameEvent.Post event) throws Exception {
        if (!enabled() || screenshot == null) return;
        var game = Minecraft.getInstance();
        var path = game.gameDirectory.toPath().resolve("screenshots/" + screenshot);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
        } finally {
            screenshot = null;
        }
    }

    private NativeAdvancedChairClientCheck() {}
}
