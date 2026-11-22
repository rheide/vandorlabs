package com.vandorlabs.container;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.tiles.TileEntityConstructorPart;
import com.vandorlabs.vehicle.EntityGroundVehicle;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Bind configuration to the clicked member, even when the Core is across a large frame. */
public final class ContainerMaxwellDrive extends ContainerTokenSlot {
    public static final int PURGE_TANK = 1;
    private final TileEntityConstructor engine;
    private final BlockPos contact;
    private final EntityGroundVehicle craft;

    private ContainerMaxwellDrive(EntityPlayer player, TileEntityConstructor engine, BlockPos contact, EntityGroundVehicle craft) {
        super(player.inventory, engine);
        this.engine = engine;
        this.contact = contact;
        this.craft = craft;
    }

    public static TileEntityConstructor at(World world, BlockPos pos) {
        if (!world.isBlockLoaded(pos, false)) return null;
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityConstructorPart) {
            BlockPos core = ((TileEntityConstructorPart) tile).owner();
            if (!world.isBlockLoaded(core, false)) return null;
            tile = world.getTileEntity(core);
        }
        if (!(tile instanceof TileEntityConstructor)) return null;
        TileEntityConstructor engine = (TileEntityConstructor) tile;
        return engine.maxwell() && engine.renderReady() ? engine : null;
    }

    public static void open(EntityPlayer player, World world, BlockPos contact) {
        if (player.world.isRemote) return;
        if (world instanceof VehicleWorld) {
            EntityGroundVehicle craft = ((VehicleWorld) world).craft;
            if (craft == null) return;
            int packed = contact.getX() | contact.getY() << 6 | contact.getZ() << 12;
            player.openGui(VandorLabs.instance, GuiHandler.GUI_MAXWELL_DRIVE, player.world,
                    craft.getEntityId(), packed, Integer.MIN_VALUE);
        } else {
            player.openGui(VandorLabs.instance, GuiHandler.GUI_MAXWELL_DRIVE, world,
                    contact.getX(), contact.getY(), contact.getZ());
        }
    }

    public static ContainerMaxwellDrive resolve(EntityPlayer player, World world, int x, int y, int z) {
        EntityGroundVehicle craft = null;
        BlockPos contact;
        if (z == Integer.MIN_VALUE) {
            Entity entity = world.getEntityByID(x);
            if (!(entity instanceof EntityGroundVehicle) || y < 0 || y >= 1 << 18) return null;
            craft = (EntityGroundVehicle) entity;
            if (craft.view == null) return null;
            world = craft.view;
            contact = new BlockPos(y & 63, (y >> 6) & 63, y >> 12);
        } else contact = new BlockPos(x, y, z);
        TileEntityConstructor engine = at(world, contact);
        if (engine == null) return null;
        ContainerMaxwellDrive container = new ContainerMaxwellDrive(player, engine, contact, craft);
        return container.canInteractWith(player) ? container : null;
    }

    @Override public boolean carriedMachine() { return craft != null; }

    @Override public boolean canInteractWith(EntityPlayer player) {
        if (player.isDead || player.isSpectator() || at(engine.getWorld(), contact) != engine) return false;
        if (craft == null) return ConfigurationAccess.canEditDialog(player, engine.getWorld(), contact)
                && !com.vandorlabs.gear.ArmTransactions.locked(engine.getWorld(), engine.getPos());
        if (craft.isDead || craft.ownershipBlocked || craft.transferInProgress || craft.world != player.world
                || craft.view != engine.getWorld() || !craft.structure.isActive(engine.getPos())) return false;
        Vec3d center = craft.toWorld(new Vec3d(contact).addVector(.5, .5, .5), contact);
        BlockPos below = new BlockPos(center);
        return player.getDistanceSq(center.x, center.y, center.z) <= 64 && player.world.isBlockModifiable(player, below)
                && player.canPlayerEdit(below, EnumFacing.UP, player.getHeldItemMainhand());
    }

    @Override public void configureMachine(EntityPlayerMP player, com.vandorlabs.navigation.NavigationScreenSettings settings,
            int[] surfaces, int mask, int behavior, com.vandorlabs.redstone.ChannelList channels, com.vandorlabs.drives.DriveSettings drive) {
        if (!canInteractWith(player) || surfaces.length != 0 || drive == null) return;
        super.configureMachine(player, settings, surfaces, mask, behavior, channels, drive);
        if (craft != null) craft.signals.transmitterChanged(engine);
    }

    @Override public boolean enchantItem(EntityPlayer player, int id) {
        return id == PURGE_TANK && canInteractWith(player) && engine.purgeMaxwell();
    }
}
