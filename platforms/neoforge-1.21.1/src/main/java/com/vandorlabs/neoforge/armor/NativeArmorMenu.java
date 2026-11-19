package com.vandorlabs.neoforge.armor;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Bind the editor to the original held item or mounted equipment and synchronize a read-only preview. */
public final class NativeArmorMenu extends AbstractContainerMenu {
    private final Player owner;
    private final int selected;
    private final InteractionHand hand;
    private final ArmorStand stand;
    private final EquipmentSlot slot;
    private final ItemStack original;
    private final SimpleContainer preview = new SimpleContainer(1);
    boolean synchronizedStack;

    NativeArmorMenu(int id, Inventory inventory, InteractionHand hand) {
        this(id, inventory, hand, null, null, inventory.player.getItemInHand(hand));
    }

    NativeArmorMenu(int id, Inventory inventory, ArmorStand stand, EquipmentSlot slot) {
        this(id, inventory, null, stand, slot, stand == null ? ItemStack.EMPTY : stand.getItemBySlot(slot));
    }

    private NativeArmorMenu(int id, Inventory inventory, InteractionHand hand, ArmorStand stand,
                            EquipmentSlot slot, ItemStack original) {
        super(NativeArmorBindings.MENU, id);
        this.owner = inventory.player;
        this.selected = inventory.selected;
        this.hand = hand;
        this.stand = stand;
        this.slot = slot;
        this.original = original;
        preview.setItem(0, original.copy());
        addSlot(new Slot(preview, 0, -10000, -10000) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
    }

    ItemStack editorStack() { return preview.getItem(0); }

    @Override
    public boolean stillValid(Player player) {
        if (owner != player || !player.isAlive() || player.isSpectator()
            || original.isEmpty() || !(original.getItem() instanceof NativeArmorItem)) return false;
        if (hand != null) {
            return player.getInventory().selected == selected && player.getItemInHand(hand) == original
                && NativeConfigurationAccess.canConfigure(player);
        }
        return stand != null && NativeArmorStand.canConfigure(player, stand)
            && stand.getItemBySlot(slot) == original
            && (!(player instanceof ServerPlayer server) || server.serverLevel().getEntity(stand.getId()) == stand);
    }

    void configure(ServerPlayer player, int choice) {
        if (!stillValid(player) || !NativeArmorItem.configure(original, choice)) return;
        synchronizeArmor(player);
    }

    void sampleInventory(ServerPlayer player, int slot) {
        if (!stillValid(player) || slot < 0 || slot >= com.vandorlabs.ui.InventorySampleLayout.INVENTORY_SLOTS) return;
        int choice = NativeArmorInventorySources.choice(player.getInventory().getItem(slot));
        if (choice < 0) return;
        NativeArmorItem.applyInventorySample(original, choice);
        synchronizeArmor(player);
    }

    private void synchronizeArmor(ServerPlayer player) {
        preview.setItem(0, original.copy());
        broadcastChanges();
        if (hand != null) {
            player.getInventory().setChanged();
            int index = hand == InteractionHand.MAIN_HAND ? selected : 40;
            player.connection.send(new ClientboundContainerSetSlotPacket(
                ClientboundContainerSetSlotPacket.PLAYER_INVENTORY, 0, index, original));
        }
    }

    @Override
    public void setItem(int slot, int stateId, ItemStack stack) {
        super.setItem(slot, stateId, stack);
        synchronizedStack = true;
    }

    @Override
    public void initializeContents(int stateId, List<ItemStack> items, ItemStack carried) {
        super.initializeContents(stateId, items, carried);
        synchronizedStack = true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {}
}
