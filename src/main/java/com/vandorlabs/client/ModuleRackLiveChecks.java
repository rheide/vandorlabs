package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockModuleRack;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.rack.ModuleRackGeometry;
import com.vandorlabs.tiles.TileEntityModuleRack;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;

/** Isolated real-client regression: six faces, Survival packets, moving model,
 * terrain publication and zero settled module draws. Enabled only by ReproLab. */
@SideOnly(Side.CLIENT)
final class ModuleRackLiveChecks {
    private static final BlockPos AT = new BlockPos(88, 110, 88);
    private static final EnumFacing[] FACES = {EnumFacing.SOUTH, EnumFacing.NORTH,
            EnumFacing.EAST, EnumFacing.WEST, EnumFacing.UP, EnumFacing.DOWN};
    private static int direction, slot, stage, wait;
    private static int placementStage, placementWait;
    private static volatile String placementStatus="setup";
    private static final java.lang.reflect.Field PLACEMENT_TELEPORT=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.network.NetHandlerPlayServer.class,"targetPos","field_184362_y");
    private static final BlockPos PLACE = AT.add(-6, 0, 0);
    private static long idleDraws, insertionDraws, capturedDraws;
    private static long stressSequence;
    private static boolean stressed;
    private static boolean replacedWhileMoving;
    private static long replacedIncarnation;
    private static int movingFrames, extractionFrames;
    private static File output;

    private ModuleRackLiveChecks() { }

    static EntityPlayerMP player(Minecraft mc) {
        return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
    }
    static void server(Minecraft mc, Runnable action) throws Exception {
        mc.getIntegratedServer().addScheduledTask(action).get();
    }
    private static TileEntityModuleRack tile(World world) {
        return world.getTileEntity(AT) instanceof TileEntityModuleRack
                ? (TileEntityModuleRack)world.getTileEntity(AT) : null;
    }
    private static void next(int value) { stage = value; wait = 0; }

    static boolean tick(Minecraft mc, File directory) throws Exception {
        if("read".equals(System.getProperty("vandorlabs.moduleRackMigrationPhase","")))return ModuleRackMigrationLiveChecks.read(mc,directory);
        if (placementStage < 3) return placement(mc);
        if (Boolean.getBoolean("vandorlabs.moduleRackAutopilotOnly")) return ModuleRackAutopilotLiveChecks.tick(mc, directory);
        if (Boolean.getBoolean("vandorlabs.moduleRackSettingsOnly")) return ModuleRackSettingsLiveChecks.tick(mc, directory);
        if (direction >= FACES.length) return true;
        output = directory;
        if (++wait > 1000) throw new IllegalStateException("Module Rack timeout face="
                + FACES[direction] + " slot=" + slot + " stage=" + stage
                + " clientMode=" + mc.player.isCreative() + " hand=" + mc.player.getHeldItemMainhand()
                + " active=" + ModuleRackAnimation.activeCount());
        EnumFacing facing = FACES[direction];
        mc.gameSettings.pauseOnLostFocus = false;
        mc.gameSettings.hideGUI = true;
        mc.gameSettings.viewBobbing = false;
        mc.gameSettings.clouds = 0;
        mc.gameSettings.thirdPersonView = 0;
        if (stage == 0) {
            mc.player.closeScreen();
            mc.playerController.setGameType(GameType.SURVIVAL);
            mc.player.inventory.currentItem = 0;
            mc.getConnection().sendPacket(new CPacketHeldItemChange(0));
            server(mc, () -> {
                EntityPlayerMP p = player(mc);
                WorldServer world = p.getServerWorld();
                world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
                world.setWorldTime(6000);
                world.getWorldInfo().setRaining(false);
                p.interactionManager.setGameType(GameType.SURVIVAL);
                p.capabilities.allowFlying = true;
                p.capabilities.isFlying = true;
                p.sendPlayerAbilities();
                for (BlockPos at : BlockPos.getAllInBox(AT.add(-2, -2, -2), AT.add(2, 2, 2)))
                    world.setBlockToAir(at);
                world.setBlockState(AT.offset(facing.getOpposite()), Blocks.STONE.getDefaultState(), 3);
                BlockModuleRack block = (BlockModuleRack)Block.getBlockFromName("vandorlabs:module_rack");
                world.setBlockState(AT, block.getDefaultState().withProperty(BlockModuleRack.FACING, facing), 3);
                p.inventory.clear();
                p.inventory.currentItem = 0;
                ItemStack modules = new ItemStack(ModItems.BLANK_MODULE, 8);
                NBTTagCompound payload = new NBTTagCompound();
                payload.setString("RackLiveCheck", "retained opaque module NBT");
                modules.setTagCompound(payload);
                p.setHeldItem(EnumHand.MAIN_HAND, modules);
                camera(p, facing);
                p.inventoryContainer.detectAndSendChanges();
            });
            slot = 0; movingFrames = 0; extractionFrames = 0; stressed = false; next(1); return false;
        }
        if (stage == 1) {
            TileEntityModuleRack rack = tile(mc.world);
            if (rack == null || mc.player.isCreative() || mc.player.inventory.currentItem != 0
                    || mc.player.getHeldItemMainhand().getCount() != 8 || wait < 30) return false;
            require(mc.world.getBlockState(AT).getValue(BlockModuleRack.FACING) == facing, "client facing sync");
            require(rack.getOccupiedMask() == 0 && ModuleRackAnimation.activeCount() == 0, "empty rack animates");
            capture(mc, "empty");
            click(mc, facing, slot, false);
            next(2); return false;
        }
        if (stage == 2) {
            TileEntityModuleRack rack = tile(mc.world);
            if (rack == null || rack.getOccupiedMask() != (1 << (slot + 1)) - 1) return false;
            require(ModuleRackAnimation.activeCount() > 0, "insertion did not start transient renderer");
            server(mc, () -> {
                TileEntityModuleRack owned = tile(player(mc).world);
                require(owned.getOccupiedMask() == (1 << (slot + 1)) - 1, "server slot selection");
                require(player(mc).getHeldItemMainhand().getCount() == 7 - slot, "Survival insertion consumes exactly one");
                require("retained opaque module NBT".equals(owned.getStackInSlot(slot).getTagCompound().getString("RackLiveCheck")),
                        "packet insertion lost module data");
            });
            next(3); return false;
        }
        if (stage == 3) {
            TileEntityModuleRack rack = tile(mc.world);
            if (direction == 1 && slot == 0 && !replacedWhileMoving
                    && ModuleRackAnimation.drawCount() > insertionDraws) {
                require(ModuleRackAnimation.activeCount() > 0, "replacement must exercise an active insertion");
                replacedIncarnation = rack.getIncarnation();
                server(mc, () -> {
                    WorldServer world = player(mc).getServerWorld();
                    ItemStack moving = tile(world).removeModule(0);
                    IBlockState state = world.getBlockState(AT);
                    world.setBlockToAir(AT);
                    world.setBlockState(AT, state, 3);
                    require(tile(world).insertModule(0, moving, false) && moving.isEmpty(),
                            "moving replacement lost owned module");
                });
                insertionDraws = ModuleRackAnimation.drawCount();
                capturedDraws = insertionDraws;
                replacedWhileMoving = true;
                next(10); return false;
            }
            if (ModuleRackAnimation.activeCount() != 0 || rack.getRenderMask() != rack.getOccupiedMask()) return false;
            require(ModuleRackAnimation.drawCount() > insertionDraws, "moving module never rendered for " + facing + " slot " + slot);
            idleDraws = ModuleRackAnimation.drawCount();
            next(4); return false;
        }
        if (stage == 4) {
            if (wait < 24) return false;
            require(ModuleRackAnimation.activeCount() == 0 && ModuleRackAnimation.drawCount() == idleDraws,
                    "settled rack keeps performing tile draws");
            verifyModel(mc);
            if (slot == 0) capture(mc, "one_seated");
            if (++slot < 4) {
                click(mc, facing, slot, false);
                next(2); return false;
            }
            capture(mc, "all_populated");
            // Rebuild terrain without re-inserting any item; no cosmetic motion may replay.
            if (direction == 0) mc.refreshResources();
            else mc.renderGlobal.loadRenderers();
            next(5); return false;
        }
        if (stage == 5) {
            if (wait < 40) return false;
            require(tile(mc.world).getOccupiedMask() == 15 && ModuleRackAnimation.activeCount() == 0
                    && ModuleRackAnimation.drawCount() == idleDraws, "terrain reload replays insertion");
            if (direction == 0 && !stressed) {
                stressSequence = tile(mc.world).getInsertionSequence(0);
                insertionDraws = ModuleRackAnimation.drawCount();
                // Two changes before the client can upload the empty terrain. The final
                // occupancy is unchanged; the insertion sequence still starts one motion.
                server(mc, () -> {
                    TileEntityModuleRack rack = tile(player(mc).world);
                    ItemStack module = rack.removeModule(0);
                    require(!module.isEmpty() && rack.insertModule(0, module, false) && module.isEmpty(),
                            "rapid remove/reinsert lost item ownership");
                });
                stressed = true; next(8); return false;
            }
            server(mc, () -> {
                EntityPlayerMP p = player(mc);
                p.inventory.currentItem = 1;
                p.inventoryContainer.detectAndSendChanges();
            });
            mc.player.inventory.currentItem = 1;
            mc.getConnection().sendPacket(new CPacketHeldItemChange(1));
            slot = 0; next(6); return false;
        }
        if (stage == 6) {
            if (mc.player.inventory.currentItem != 1 || !mc.player.getHeldItemMainhand().isEmpty()) return false;
            click(mc, facing, slot, true);
            next(7); return false;
        }
        if (stage == 7) {
            if (tile(mc.world).getOccupiedMask() != (15 & ~((1 << (slot + 1)) - 1))) return false;
            server(mc, () -> {
                int owned = 0;
                for (ItemStack item : player(mc).inventory.mainInventory)
                    if (item.getItem() == ModItems.BLANK_MODULE) owned += item.getCount();
                require(owned == 5 + slot, "extraction did not return one owned module");
            });
            require(ModuleRackAnimation.activeCount() > 0, "extraction did not start the transient renderer");
            next(11); return false;
        }
        if (stage == 11) {
            if (ModuleRackAnimation.activeCount() > 0) return false;
            require(ModuleRackAnimation.drawCount() > insertionDraws, "extraction never drew its outward slide");
            if (slot == 0) require(extractionFrames > 0, "no actual extraction framebuffer captured");
            if (++slot < 4) { next(6); return false; }
            idleDraws = ModuleRackAnimation.drawCount(); next(12); return false;
        }
        if (stage == 12) {
            if (wait < 24) return false;
            require(ModuleRackAnimation.activeCount() == 0 && ModuleRackAnimation.drawCount() == idleDraws,
                    "empty rack retains an animation proxy or performs idle draws");
            require(movingFrames > 0, "no moving-module framebuffer captured for " + facing);
            System.out.println("[vandorlabs][reprolab] module-rack face PASS " + facing.getName()
                    + " four Survival insert/remove packets, populated indicators, chunk handoff and idle draws");
            if (++direction == 1 && Boolean.getBoolean("vandorlabs.moduleRackAnimationOnly")) {
                direction = FACES.length;
                System.out.println("[vandorlabs][reprolab] module-rack PASS targeted insertion/extraction/reversal/reload/idle");
                return true;
            }
            if (direction < FACES.length) { next(0); return false; }
            System.out.println("[vandorlabs][reprolab] module-rack PASS six faces, 24 targeted Survival insert/remove pairs,"
                    + " moving-frame screenshots, active same-position replacement, rapid reinsertion, resource reload,"
                    + " seated chunk rendering and zero idle module draws");
            return true;
        }
        if (stage == 8) {
            if (tile(mc.world).getInsertionSequence(0) == stressSequence) return false;
            require(ModuleRackAnimation.activeCount() > 0, "rapid reinsertion missed unchanged-occupancy event");
            next(9); return false;
        }
        if (stage == 9) {
            if (ModuleRackAnimation.activeCount() != 0 || tile(mc.world).getRenderMask() != 15) return false;
            require(ModuleRackAnimation.drawCount() > insertionDraws, "rapid reinsertion never drew");
            idleDraws = ModuleRackAnimation.drawCount();
            next(5); return false;
        }
        if (stage == 10) {
            if (tile(mc.world).getIncarnation() == replacedIncarnation) return false;
            require(tile(mc.world).getOccupiedMask() == 1 && ModuleRackAnimation.activeCount() > 0,
                    "replacement did not restart its fresh insertion");
            next(3); return false;
        }
        return false;
    }

    static void render(Minecraft mc) {
        if (output == null || direction >= FACES.length || slot != 0 || !(stage == 2 || stage == 3 || stage == 7 || stage == 11)
                || ModuleRackAnimation.activeCount() == 0 || mc.world == null
                || ModuleRackAnimation.drawCount() <= capturedDraws) return;
        TileEntityModuleRack rack = tile(mc.world);
        if (rack == null || (rack.getRenderMask() & 1) != 0) return;
        try {
            if (stage == 7 || stage == 11) {
                capture(mc, extractionFrames++ == 0 ? "extract_start" : "extract_moving");
            } else capture(mc, movingFrames++ == 0 ? "insert_start" : "insert_moving");
            capturedDraws = ModuleRackAnimation.drawCount();
        } catch (Exception failure) { throw new IllegalStateException("Rack insertion capture", failure); }
    }

    private static boolean placementPacketReady(Minecraft mc)throws Exception {
        final double x=mc.player.posX,y=mc.player.posY,z=mc.player.posZ;
        final int slot=mc.player.inventory.currentItem,count=mc.player.getHeldItemMainhand().getCount();
        final net.minecraft.item.Item item=mc.player.getHeldItemMainhand().getItem();
        return mc.getIntegratedServer().callFromMainThread(()->{
            EntityPlayerMP actor=player(mc);boolean pending=PLACEMENT_TELEPORT.get(actor.connection)!=null;
            placementStatus="teleportPending="+pending+" serverMode="+actor.interactionManager.getGameType()+" serverSlot="+actor.inventory.currentItem
                    +" serverHeld="+actor.getHeldItemMainhand()+" serverPosition="+actor.getPositionVector()+" serverTarget="+actor.world.getBlockState(PLACE)+" tile="+actor.world.getTileEntity(PLACE);
            return !pending&&actor.interactionManager.getGameType()==GameType.SURVIVAL&&actor.inventory.currentItem==slot
                    &&actor.getHeldItemMainhand().getItem()==item&&actor.getHeldItemMainhand().getCount()==count&&actor.getDistanceSq(x,y,z)<.0001;
        }).get();
    }

    /** The reported floor-click regression uses the real Survival placement packet and a shallow view. */
    private static boolean placement(Minecraft mc) throws Exception {
        if (++placementWait%40==0){placementPacketReady(mc);System.out.println("[vandorlabs][reprolab] Rack placement waiting stage="+placementStage+" age="+placementWait+" clientHeld="+mc.player.getHeldItemMainhand()+" clientTarget="+mc.world.getBlockState(PLACE)+" "+placementStatus);}
        if (placementWait > 160) throw new IllegalStateException("Rack placement timeout stage=" + placementStage
                + " mode=" + mc.playerController.getCurrentGameType() + " slot=" + mc.player.inventory.currentItem
                + " held=" + mc.player.getHeldItemMainhand() + " pos=" + mc.player.getPositionVector()+" "+placementStatus);
        if (placementStage == 0) {
            mc.player.closeScreen(); mc.playerController.setGameType(GameType.SURVIVAL);
            mc.player.inventory.currentItem = 0;
            mc.getConnection().sendPacket(new CPacketHeldItemChange(0));
            server(mc, () -> {
                EntityPlayerMP p = player(mc); WorldServer world = p.getServerWorld();
                for (BlockPos at : BlockPos.getAllInBox(PLACE.add(-1, -1, -1), PLACE.add(1, 2, 4)))
                    world.setBlockState(at, at.getY() == PLACE.getY() - 1 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), 3);
                p.interactionManager.setGameType(GameType.SURVIVAL); p.inventory.clear(); p.inventory.currentItem = 0;
                p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Block.getBlockFromName("vandorlabs:module_rack")));
                p.setHeldItem(EnumHand.OFF_HAND, ItemStack.EMPTY);
                p.connection.setPlayerLocation(PLACE.getX() + .5, PLACE.getY(), PLACE.getZ() + 3.5, 180, 10);
                p.inventoryContainer.detectAndSendChanges();
            });
            placementStage = 1; placementWait = 0; return false;
        }
        if (placementStage == 1) {
            if (mc.playerController.getCurrentGameType() != GameType.SURVIVAL || mc.player.inventory.currentItem != 0
                    || mc.player.getHeldItemMainhand().getItem() != net.minecraft.item.Item.getItemFromBlock(Block.getBlockFromName("vandorlabs:module_rack"))
                    || mc.player.getPositionVector().squareDistanceTo(PLACE.getX() + .5, PLACE.getY(), PLACE.getZ() + 3.5) > .02
                    || Math.abs(mc.player.rotationPitch - 10) > .1 || Math.abs(Math.abs(mc.player.rotationYaw) - 180) > .1) return false;
            if(!placementPacketReady(mc))return false;
            require(mc.playerController.processRightClickBlock(mc.player, mc.world, PLACE.down(), EnumFacing.UP,
                    new Vec3d(PLACE.getX() + .5, PLACE.getY(), PLACE.getZ() + .5), EnumHand.MAIN_HAND) == EnumActionResult.SUCCESS,
                    "real shallow floor placement packet refused");
            placementStage = 2; placementWait = 0; return false;
        }
        if (!(mc.world.getBlockState(PLACE).getBlock() instanceof BlockModuleRack) || !mc.player.getHeldItemMainhand().isEmpty()) return false;
        require(mc.world.getBlockState(PLACE).getValue(BlockModuleRack.FACING) == EnumFacing.SOUTH,
                "floor click must face the shallow-view player, rather than point upward");
        server(mc, () -> {
            EntityPlayerMP p = player(mc);
            require(p.interactionManager.getGameType() == GameType.SURVIVAL && p.getHeldItemMainhand().isEmpty()
                    && p.world.getBlockState(PLACE).getValue(BlockModuleRack.FACING) == EnumFacing.SOUTH,
                    "server Survival item consumption and player-facing floor placement");
        });
        placementStage = 3; placementWait = 0;
        System.out.println("[vandorlabs][reprolab] module-rack placement PASS real Survival shallow-floor player-facing consumption");
        if (Boolean.getBoolean("vandorlabs.moduleRackPlacementOnly")) {
            direction = FACES.length;
            System.out.println("[vandorlabs][reprolab] module-rack PASS targeted placement"); return true;
        }
        return false;
    }

    static BlockPos placedRackPosition() { return PLACE; }

    private static void click(Minecraft mc, EnumFacing facing, int slot, boolean button) {
        insertionDraws = ModuleRackAnimation.drawCount();
        capturedDraws = insertionDraws;
        Vec3d target = frontPoint(facing, (button ? 14.5 : 7) / 16.0,
                ModuleRackGeometry.slotCenterY(slot) / 16.0).addVector(AT.getX(), AT.getY(), AT.getZ());
        require(mc.playerController.processRightClickBlock(mc.player, mc.world, AT, facing, target,
                EnumHand.MAIN_HAND) == EnumActionResult.SUCCESS, "real slot packet refused");
    }

    private static Vec3d frontPoint(EnumFacing facing, double u, double v) {
        switch (facing) {
            case UP: return new Vec3d(u, 1, 1 - v);
            case DOWN: return new Vec3d(u, 0, v);
            case NORTH: return new Vec3d(1 - u, v, 0);
            case SOUTH: return new Vec3d(u, v, 1);
            case WEST: return new Vec3d(0, v, u);
            default: return new Vec3d(1, v, 1 - u);
        }
    }

    private static void camera(EntityPlayerMP p, EnumFacing facing) {
        Vec3d center = frontPoint(facing, .5, .5).addVector(AT.getX(), AT.getY(), AT.getZ());
        Vec3d u = frontPoint(facing, 1, .5).subtract(frontPoint(facing, 0, .5));
        Vec3d v = frontPoint(facing, .5, 1).subtract(frontPoint(facing, .5, 0));
        Vec3d eye = center.addVector(facing.getFrontOffsetX() * 2.7, facing.getFrontOffsetY() * 2.7, facing.getFrontOffsetZ() * 2.7)
                .add(u.scale(.7)).add(v.scale(.55));
        Vec3d delta = center.subtract(eye);
        float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float)-Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        p.connection.setPlayerLocation(eye.x, eye.y - p.getEyeHeight(), eye.z, yaw, pitch);
    }

    private static void verifyModel(Minecraft mc) {
        IBlockState state = mc.world.getBlockState(AT);
        IBlockState extended = state.getBlock().getExtendedState(state, mc.world, AT);
        IBakedModel model = mc.getBlockRendererDispatcher().getModelForState(state);
        require(model instanceof ModuleRackModel, "rack did not install cached terrain model");
        List<BakedQuad> quads = model.getQuads(extended, null, 0);
        require(!quads.isEmpty(), "rack terrain model is empty");
        for (int sample = 0; sample < 1000; sample++)
            require(model.getQuads(extended, null, sample) == quads, "steady appearance allocates a new quad list");
        require(((ModuleRackModel)model).cachedCombinations() <= 81, "unbounded appearance cache");
    }

    private static void capture(Minecraft mc, String name) throws Exception {
        ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth, mc.displayHeight, mc.getFramebuffer()),
                "png", new File(output, "module_rack_" + FACES[direction].getName() + "_" + name + ".png"));
    }
    private static void require(boolean value, String reason) {
        if (!value) throw new IllegalStateException("Module Rack live: " + reason);
    }
}
