package com.vandorlabs.neoforge.builders;

import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.builders.client.NativeDriveScreen;
import com.vandorlabs.neoforge.builders.client.NativeBuilderScreen;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.redstone.ChannelList;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

final class NativeDriveClientFixture {
    private static UUID actor;
    private static int serverStep;
    private static final ChannelList BANK = ChannelList.of(317, 318);

    private static BlockPos root(int drive) { return NativeBuilderClientFixture.ROOT.east(drive * 8); }
    private static BlockPos ack(int stage) { return new BlockPos(-2, 95, stage - 2); }

    static void login(ServerPlayer player) {
        actor = player.getUUID();
        var world = player.serverLevel();
        for (int x = -3; x <= 29; x++) for (int z = -5; z <= 5; z++) {
            world.setBlock(new BlockPos(x, 95, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 96; y <= 100; y++) world.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        for (int i = 0; i <= NativeDriveDesign.SUPPORTED.size(); i++) {
            String id = i == 3 ? "synthesis_tower" : NativeDriveDesign.SUPPORTED.get(i).id;
            var root = root(i);
            var core = NativeBuilderBindings.CORES.get(id);
            var design = core.design;
            var layout = new CoreLayout(design.width(), design.height(), design.depth(), Direction.NORTH.get3DDataValue(), 0, 0);
            world.setBlock(root, core.defaultBlockState(), 3);
            for (int x = 0; x < design.width(); x++) for (int y = 0; y < design.height(); y++) for (int z = 0; z < design.depth(); z++) {
                var at = root.offset(x, y, z);
                if (!at.equals(root) && layout.frame(x, y, z)) world.setBlock(at, NativeBuilderFormation.matter().defaultBlockState(), 3);
            }
        }
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        held(player, ItemStack.EMPTY, aim(0, 0));
        System.out.println("[vandorlabs][builder-client] drive-setup PASS Survival and authored frames");
    }

    private static Vec3 aim(int machine, int action) {
        var root = root(machine);
        if(machine==3)return new Vec3(root.getX()+(action==0?.5:2.8),root.getY()+(action==0?.5:.95),0);
        var drive = NativeDriveDesign.SUPPORTED.get(machine);
        return switch (action) {
            case 0 -> new Vec3(root.getX() + .5, root.getY() + .5, 0);
            case 3 -> new Vec3(root.getX() + drive.chipX, root.getY() + drive.chipY, drive.chipZ);
            default -> new Vec3(root.getX() + 1.8, root.getY() + 1.275, 0);
        };
    }

    static void apply(ServerPlayer player, int operation) {
        need(player.getUUID().equals(actor) && serverStep == operation && operation < 14, "ordered drive actor");
        int machine = operation / 4;
        var tile = (NativeBuilderEntity) player.serverLevel().getBlockEntity(root(machine));
        need(tile != null && tile.formed() && tile.available() && tile.synthesisReady(), "async formation ready");
        if(machine==3){
            if(operation==12){
                tile.setItem(3,com.vandorlabs.neoforge.credentials.NativeChipTypes.variant("catalog"));
                held(player,ItemStack.EMPTY,aim(machine,1));
            }else{
                need(player.containerMenu==player.inventoryMenu&&tile.selectedDesign()==NativeBuilderProducts.OCULUS,
                        "Tower selects Oculus from the real scrolled Catalog list");
                System.out.println("[vandorlabs][builder-client] drive-controls PASS native menus/Configurator/activity/chips/Tower wheel and drag");
            }
            player.serverLevel().setBlock(ack(operation), Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
            serverStep++;
            return;
        }
        var settings = tile.driveSettings();
        switch (operation % 4) {
            case 0 -> held(player, machine==2?new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()):ItemStack.EMPTY, aim(machine, 1));
            case 1 -> {
                need(player.containerMenu == player.inventoryMenu && settings.redstone() && settings.inverted() && settings.levels()
                        && !settings.animated() && settings.speed() == 0 && settings.particles() == 0
                        && settings.threshold() == (tile.drive().exhaust ? 7 : 8) && tile.channels().equals(BANK),
                        "ordinary open sends real Drive controls and reverse cycling");
                held(player, machine==2?ItemStack.EMPTY:new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()), aim(machine, 2));
            }
            case 2 -> {
                need(player.containerMenu == player.inventoryMenu && settings.redstone() && settings.inverted()
                        && tile.driveLevel() == 15 && (machine!=2||tile.driveActivity()==0), "same drive session or Oculus state click preserves configured power");
                held(player, NativeCredentialItems.dataChip(3), aim(machine, 3));
            }
            case 3 -> {
                need(player.getMainHandItem().isEmpty() && NativeCredentialItems.company(tile.getItem(3)) == 3,
                        "one mounted chip transfers through the authored hit");
                held(player, ItemStack.EMPTY, aim(machine+1, 0));
            }
            default -> throw new IllegalArgumentException("Drive action");
        }
        player.serverLevel().setBlock(ack(operation), Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
        serverStep++;
    }

    static void logout(Player player) {
        if (!player.getUUID().equals(actor)) return;
        need(serverStep == 14, "both drive journeys complete");
        System.out.println("[vandorlabs][builder-client] logout PASS");
    }

    private static void held(ServerPlayer player, ItemStack stack, Vec3 target) {
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.connection.send(new ClientboundSetCarriedItemPacket(0));
        player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, 0, stack.copy()));
        player.inventoryMenu.broadcastChanges();
        float pitch = (float) Math.toDegrees(Math.atan2(96 + player.getEyeHeight() - target.y, target.z + 2.5));
        player.connection.teleport(target.x, 96, -2.5, 0, pitch);
    }

    private static void need(boolean value, String reason) {
        if (!value) throw new AssertionError("Drive player step " + serverStep + ": " + reason);
    }

    @EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
    public static final class ClientChecks {
        private static int step, delay;
        private static boolean sent, disconnected, complete, scrolled;

        @SubscribeEvent public static void tick(ClientTickEvent.Post tick) {
            if (!NativeBuilderClientFixture.enabled() || !NativeBuilderClientFixture.drivesOnly() || complete) return;
            var mc = Minecraft.getInstance();
            try {
                if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro) {
                    intro.onClose();
                    return;
                }
                if (disconnected && mc.level == null) {
                    complete = true;
                    System.out.println("[vandorlabs][builder-client] client PASS LongburnMeridianOculus physicalSurvival/dialogs/Configurator/mount");
                    mc.stop();
                    return;
                }
                if (mc.player == null || mc.level == null || mc.gameMode == null) return;
                if (++delay > 450) throw new AssertionError("Drive client operation " + step + " mode "
                        + mc.gameMode.getPlayerMode() + " slot " + mc.player.getInventory().selected
                        + " held " + mc.player.getMainHandItem() + " position " + mc.player.position() + " screen " + mc.screen);
                if (step > 0 && !mc.level.getBlockState(ack(step - 1)).is(Blocks.EMERALD_BLOCK)) return;
                if (step == 14) {
                    disconnected = true;
                    mc.level.disconnect();
                    mc.disconnect();
                    return;
                }
                int machine = step / 4, action = step % 4;
                if (!(mc.level.getBlockEntity(root(machine)) instanceof NativeBuilderEntity tile)) return;
                if (!sent) {
                    if (!ready(mc, aim(machine, action), machine, action)) return;
                    var hit = mc.player.pick(mc.player.blockInteractionRange(), 1, false);
                    need(hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                            && NativeBuilderBindings.ownerAt(mc.level, block.getBlockPos()) == tile, "real ray reaches an authored owned cell");
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, (BlockHitResult) hit);
                    sent = true;
                    return;
                }
                if (action == 0 && (!tile.formed() || tile.transactionBlocked())) return;
                if(action==2&&machine==2&&tile.driveActivity()!=0)return;
                if(machine==3&&action==1){
                    if(!(mc.screen instanceof NativeBuilderScreen builder)
                            ||(builder.getMenu().value(10)&1<<NativeBuilderProducts.OCULUS)==0)return;
                    if(!scrolled){scrollToOculus(builder);scrolled=true;return;}
                    if(builder.getMenu().value(0)!=NativeBuilderProducts.OCULUS)return;
                    builder.onClose();
                }else if (action == 1 || action == 2&&machine!=2) {
                    if (!(mc.screen instanceof NativeDriveScreen drive)) return;
                    if (action == 1) edit(drive, tile.drive().exhaust);
                    else {
                        click(drive, "Redstone", 0);
                        need(drive.children().stream().anyMatch(widget -> widget instanceof EditBox field && BANK.equals(ChannelList.parse(field.getValue()))),
                                "Configurator opens identical controls and persisted channels");
                    }
                    drive.onClose();
                }
                if (action == 3 && (tile.getItem(3).isEmpty() || !mc.player.getMainHandItem().isEmpty())) return;
                PacketDistributor.sendToServer(new NativeBuilderClientFixture.Mode(step++));
                sent = false;
                delay = 0;
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.out.println("[vandorlabs][builder-client] FAIL " + failure);
                mc.stop();
            }
        }

        private static boolean ready(Minecraft mc, Vec3 target, int machine, int operation) {
            if (mc.screen != null || mc.gameMode.getPlayerMode() != GameType.SURVIVAL || mc.player.getInventory().selected != 0
                    || mc.player.position().distanceToSqr(target.x, 96, -2.5) > .01
                    || mc.player.getLookAngle().dot(target.subtract(mc.player.getEyePosition()).normalize()) <= .9999) return false;
            var stack = mc.player.getMainHandItem();
            return machine==3 ? stack.isEmpty() : operation == (machine==2?1:2) ? stack.is(VandorLabsBootstrap.CONFIGURIZER.get())
                    : operation == 3 ? NativeCredentialItems.accepts(stack, true) : stack.isEmpty();
        }

        private static void scrollToOculus(NativeBuilderScreen screen){
            var first=screen.control(0);
            need(first!=null&&screen.control(NativeBuilderProducts.OCULUS)==null,"nine designs overflow the initial eight visible rows");
            double startX=first.getX()+2,startY=first.getY()+2;
            double trackX=first.getX()+first.getWidth()+3,bottom=first.getY()+first.getHeight()*8-2;
            need(screen.mouseScrolled(startX,startY,0,-1)&&screen.control(NativeBuilderProducts.OCULUS)!=null,"wheel reveals the ninth design");
            need(screen.mouseClicked(trackX,bottom,0)&&screen.mouseDragged(trackX,startY,0,0,startY-bottom),"scrollbar drags back to the first design");
            screen.mouseReleased(trackX,startY,0);
            need(screen.control(0)!=null&&screen.control(NativeBuilderProducts.OCULUS)==null,"scrollbar restores the top of the list");
            need(screen.mouseScrolled(startX,startY,0,-1),"wheel works after scrollbar release");
            var oculus=screen.control(NativeBuilderProducts.OCULUS);
            need(oculus!=null&&screen.mouseClicked(oculus.getX()+4,oculus.getY()+4,0),"actual Oculus recipe selection click");
        }

        private static void edit(NativeDriveScreen screen, boolean exhaust) {
            click(screen, "Animated", 0);
            click(screen, "Speed Normal", 1);
            click(screen, "Redstone", 0);
            for (var child : screen.children()) if (child instanceof EditBox box) box.setValue("317,318");
            click(screen, "Activation Always", 0);
            click(screen, "Engine Redstone On", 1);
            click(screen, "Brightness On/Off", 0);
            if (exhaust) {
                click(screen, "Particles at level 8", 1);
                click(screen, "Engine", 0);
                click(screen, "Particles Light", 1);
            }
        }

        private static void click(NativeDriveScreen screen, String label, int button) {
            for (var child : screen.children()) {
                if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals(label)) {
                    need(screen.mouseClicked(widget.getX() + widget.getWidth() / 2., widget.getY() + widget.getHeight() / 2., button),
                            "actual option click " + label);
                    return;
                }
            }
            throw new AssertionError("Missing drive option " + label);
        }
    }

    private NativeDriveClientFixture() {}
}
