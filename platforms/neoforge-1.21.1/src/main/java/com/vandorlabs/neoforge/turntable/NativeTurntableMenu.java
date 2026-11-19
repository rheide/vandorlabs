package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.transfer.NativeCellTransactions;
import com.vandorlabs.turntable.TurntableSettings;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class NativeTurntableMenu extends AbstractContainerMenu {
    public static final int SCAN = 0;
    public static final int RESTORE = 1;
    public static final int HEIGHT = 8;
    public static final int TURN = 32;
    public static final int SPEED = 48;

    private final BlockPos pos;
    private final UUID key;
    private final Player player;

    NativeTurntableMenu(int window, Inventory inventory, BlockPos pos, UUID key) {
        super(NativeTurntableBindings.menu, window);
        this.pos = pos.immutable();
        this.key = key;
        this.player = inventory.player;
    }

    private NativeTurntableEntity tile() {
        var level = player.level();
        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof NativeTurntableEntity owner)) return null;
        return (level.isClientSide || key.equals(owner.key())) && owner.available() ? owner : null;
    }

    public boolean captured() {
        var tile = tile();
        return tile != null && tile.hasCapture();
    }

    public boolean busy() {
        var tile = tile();
        return tile == null || tile.busy();
    }

    public boolean moving() {
        var tile = tile();
        return tile != null && tile.moving();
    }

    public int height() {
        var tile = tile();
        return tile == null ? 4 : tile.settings().height;
    }

    public double rpm() {
        var tile = tile();
        return tile == null ? 1 : tile.settings().rpm;
    }

    public String status() {
        var tile = tile();
        return tile == null ? "" : tile.status();
    }

    @Override public boolean stillValid(Player actor) {
        if (actor != player) return false;
        var owner = tile();
        if (owner == null) return false;
        if (NativeConfigurationAccess.canEditDialog(actor, owner)) return true;
        if (!(actor instanceof ServerPlayer server) || actor.distanceToSqr(pos.getCenter()) > 64) return false;
        var level = server.serverLevel();
        return level.mayInteract(actor, pos) && actor.mayUseItemAt(pos, Direction.UP, actor.getMainHandItem())
                && NativeCellTransactions.sessionHeader(level, pos, key, server) != null;
    }

    @Override public boolean clickMenuButton(Player actor, int id) {
        if (!(actor instanceof ServerPlayer server) || actor.containerMenu != this || !stillValid(actor)) return false;
        var owner = tile();
        if (owner == null || owner.busy()) return false;
        try {
            if (id >= HEIGHT + TurntableSettings.MIN_HEIGHT && id <= HEIGHT + TurntableSettings.MAX_HEIGHT) {
                return owner.setHeight(id - HEIGHT);
            }
            if (id > SPEED && id <= SPEED + 20) return owner.setSpeed(id - SPEED);
            if (id >= TURN && id < TURN + 4) return owner.turn(id - TURN);
            if (id == SCAN) NativeTurntableTransfers.request(owner, server, false, 0);
            else if (id == RESTORE) {
                int nearest = Math.floorMod((int) Math.round(owner.positionAngle() / 90), 4);
                NativeTurntableTransfers.request(owner, server, true, nearest);
            }
            else return false;
            return true;
        } catch (IllegalArgumentException refusal) {
            owner.status(refusal.getMessage());
            return false;
        }
    }

    @Override public ItemStack quickMoveStack(Player actor, int slot) { return ItemStack.EMPTY; }
}
