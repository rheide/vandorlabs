package com.vandorlabs.client;

import com.mojang.authlib.GameProfile;
import com.vandorlabs.CommonProxy;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockModuleRack;
import com.vandorlabs.copier.CopyMaterials;
import com.vandorlabs.copier.CopyWorld;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.rack.ModuleRackGeometry;
import com.vandorlabs.tiles.TileEntityModuleRack;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/** Actual block/tile callbacks in a bounded memory world. The separate live
 * fixture verifies player packets and chunk-upload animation handoff. */
public final class ModuleRackChecks {
    private static final BlockPos AT = new BlockPos(0, 80, 0);
    private static int cases;

    private static final class Player extends EntityPlayer {
        boolean allowed = true;
        Player(NonRenderingChecks.MemoryWorld world) {
            super(world, new GameProfile(new UUID(0, 91), "ModuleRackChecks"));
            capabilities.allowEdit = true;
            setPosition(.5, 80, 2.5);
        }
        @Override public boolean isCreative() { return capabilities.isCreativeMode; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean canPlayerEdit(BlockPos pos, EnumFacing side, ItemStack stack) { return allowed; }
    }

    private static final class VisualProxy extends CommonProxy {
        int changes, initials, removals, previousMask;
        long[] previousSequences;
        @Override public void moduleRackUpdated(TileEntityModuleRack tile, int previous,
                long[] sequences, boolean initial) {
            changes++;
            if (initial) initials++;
            previousMask = previous;
            previousSequences = sequences.clone();
            tile.setRenderMask(tile.getOccupiedMask());
        }
        @Override public void moduleRackRemoved(TileEntityModuleRack tile) { removals++; }
    }

    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        ForgeRegistries.ITEMS.register(ModItems.BLANK_MODULE);
        GameRegistry.registerTileEntity(TileEntityModuleRack.class,
                new ResourceLocation("vandorlabs:module_rack_checks"));
        BlockModuleRack block = new BlockModuleRack();
        ForgeRegistries.BLOCKS.register(block);
        ForgeRegistries.ITEMS.register(new ItemBlock(block).setRegistryName(block.getRegistryName()));
        CommonProxy before = VandorLabs.proxy;
        try {
            VandorLabs.proxy = null;
            persistence();
            interactions(block);
            dropOwnership(block);
            copierOwnership();
            visualPackets(block);
            replacementPackets(block);
            require(!ITickable.class.isAssignableFrom(TileEntityModuleRack.class), "rack must never register an idle tick");
            TileEntityModuleRack tile = new TileEntityModuleRack();
            require(!tile.shouldRenderInPass(0) && !tile.shouldRenderInPass(1), "no settled TESR passes");
        } finally { VandorLabs.proxy = before; }
        System.out.println("Module Rack PASS cases=" + cases
                + " 16 occupancy states, six-face slot/button callbacks, full item NBT, exact ownership,"
                + " compact appearance packets, duplicate/stale update suppression, no idle tile ticks/TESR");
    }

    private static ItemStack module(int count, int id) {
        ItemStack result = new ItemStack(ModItems.BLANK_MODULE, count);
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("OwnerNote", "Module " + id);
        payload.setIntArray("Payload", new int[] {id, 9, 18, 27});
        NBTTagCompound nested = new NBTTagCompound();
        nested.setString("OpaqueThirdPartyData", "keep this byte-for-byte");
        payload.setTag("Nested", nested);
        result.setTagCompound(payload);
        result.setStackDisplayName("Test Module " + id);
        return result;
    }

    private static void persistence() throws Exception {
        for (int mask = 0; mask < 16; mask++) {
            TileEntityModuleRack rack = new TileEntityModuleRack();
            for (int slot = 0; slot < 4; slot++) {
                ItemStack input = module(2, slot);
                if ((mask & (1 << slot)) != 0) {
                    require(rack.insertModule(slot, input, false), "insert " + slot);
                    require(input.getCount() == 1, "survival consumes exactly one");
                    ItemStack expected = module(1, slot);
                    require(ItemStack.areItemStacksEqual(expected, rack.getStackInSlot(slot)), "complete inserted item NBT");
                    ItemStack copy = rack.getStackInSlot(slot);
                    copy.getTagCompound().setString("OwnerNote", "changed copy");
                    copy.setCount(0);
                    require(ItemStack.areItemStacksEqual(expected, rack.getStackInSlot(slot)), "getter aliases owned stack");
                    long revision = rack.getRevision();
                    require(!rack.insertModule(slot, input, false), "occupied slot replaced");
                    require(input.getCount() == 1 && rack.getRevision() == revision, "rejected insertion changed ownership");
                }
            }
            require(rack.getOccupiedMask() == mask && rack.getRenderMask() == mask, "mask " + mask);
            NBTTagCompound saved = rack.writeToNBT(new NBTTagCompound());
            TileEntityModuleRack reloaded = new TileEntityModuleRack();
            reloaded.readFromNBT(saved.copy());
            require(reloaded.getOccupiedMask() == mask && reloaded.getRenderMask() == mask, "reload mask");
            for (int slot = 0; slot < 4; slot++) {
                require(ItemStack.areItemStacksEqual(rack.getStackInSlot(slot), reloaded.getStackInSlot(slot)), "reload stack " + slot);
                require(rack.getInsertionSequence(slot) == reloaded.getInsertionSequence(slot), "reload animation sequence");
            }
            require(saved.equals(reloaded.writeToNBT(new NBTTagCompound())), "NBT round trip");
            NBTTagCompound appearance = rack.getUpdateTag();
            require(!appearance.hasKey("Items") && !appearance.toString().contains("OwnerNote"), "private item NBT in chunk update");
            require(!rack.getUpdatePacket().getNbtCompound().hasKey("Items"), "private items in update packet");
            int drained = reloaded.drainModules().size();
            require(drained == Integer.bitCount(mask) && reloaded.drainModules().isEmpty(), "drain did not transfer ownership once");
            require(reloaded.getOccupiedMask() == 0, "drain mask");
            cases++;
        }
        TileEntityModuleRack rack = new TileEntityModuleRack();
        ItemStack wrong = new ItemStack(Items.STICK, 8);
        require(!rack.insertModule(0, wrong, false) && wrong.getCount() == 8, "wrong item accepted");
        for (int slot : new int[] {-1, 4, Integer.MAX_VALUE}) {
            ItemStack held = module(2, 3);
            require(!rack.insertModule(slot, held, false) && held.getCount() == 2, "invalid slot consumed item");
            require(rack.removeModule(slot).isEmpty() && rack.getStackInSlot(slot).isEmpty(), "invalid slot lookup");
        }
        ItemStack large = module(1, 2);
        large.getTagCompound().setByteArray("LargeOpaquePayload", new byte[65536]);
        require(rack.insertModule(0, large, false), "large NBT insert");
        TileEntityModuleRack small = new TileEntityModuleRack();
        small.insertModule(0, module(1, 2), false);
        require(encoded(rack.getUpdatePacket().getNbtCompound()) == encoded(small.getUpdatePacket().getNbtCompound()), "packet grows with arbitrary item NBT");
        System.out.println("Module Rack appearance packet NBT bytes=" + encoded(rack.getUpdatePacket().getNbtCompound())
                + " with either small or 65536-byte item payload (transport envelope excluded)");
        ItemStack removed = rack.removeModule(0);
        require(removed.getCount() == 1 && removed.getTagCompound().getByteArray("LargeOpaquePayload").length == 65536,
                "removal lost opaque data");
        require(rack.removeModule(0).isEmpty(), "duplicate extraction");
        ItemStack creative = module(2, 4);
        require(rack.insertModule(1, creative, true) && creative.getCount() == 2, "creative insertion policy");
    }

    private static TileEntityModuleRack install(NonRenderingChecks.MemoryWorld world, BlockModuleRack block, EnumFacing facing) {
        world.setBlockState(AT, block.getDefaultState().withProperty(BlockModuleRack.FACING, facing), 3);
        return (TileEntityModuleRack)world.getTileEntity(AT);
    }

    private static float[] hit(EnumFacing facing, int slot, boolean button) {
        float u = (float)((button ? 14.5 : 7) / 16.0);
        float y = (float)(ModuleRackGeometry.slotCenterY(slot) / 16.0);
        switch (facing) {
            case UP: return new float[] {u, 1, 1 - y};
            case DOWN: return new float[] {u, 0, y};
            case SOUTH: return new float[] {u, y, 1};
            case NORTH: return new float[] {1 - u, y, 0};
            case WEST: return new float[] {0, y, u};
            case EAST: return new float[] {1, y, 1 - u};
            default: throw new AssertionError(facing);
        }
    }

    private static boolean activate(BlockModuleRack block, NonRenderingChecks.MemoryWorld world,
            Player player, EnumFacing face, EnumHand hand, float[] hit) {
        return block.onBlockActivated(world, AT, world.getBlockState(AT), player, hand,
                face, hit[0], hit[1], hit[2]);
    }

    private static void interactions(BlockModuleRack block) {
        for (EnumFacing facing : EnumFacing.values()) for (int slot = 0; slot < 4; slot++) {
            NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(false);
            TileEntityModuleRack rack = install(world, block, facing);
            Player player = new Player(world);
            IBlockState placed = block.getStateForPlacement(world, AT, facing, .5F, .5F, .5F, 0, player);
            require(placed.getValue(BlockModuleRack.FACING) == facing, "placement must use clicked face " + facing);
            require(block.getStateFromMeta(block.getMetaFromState(placed)).equals(placed), "face metadata round trip " + facing);
            player.setHeldItem(EnumHand.MAIN_HAND, module(2, slot));
            float[] hit = hit(facing, slot, false);
            player.allowed = false;
            require(!activate(block, world, player, facing, EnumHand.MAIN_HAND, hit), "permission denial");
            player.allowed = true;
            require(!activate(block, world, player, facing.getOpposite(), EnumHand.MAIN_HAND, hit), "rear insertion");
            require(rack.getOccupiedMask() == 0 && player.getHeldItemMainhand().getCount() == 2, "rejected activation mutated");
            require(activate(block, world, player, facing, EnumHand.MAIN_HAND, hit), "front insertion");
            require(rack.getOccupiedMask() == (1 << slot) && player.getHeldItemMainhand().getCount() == 1, "targeted slot insertion");
            int updates = world.updates;
            activate(block, world, player, facing, EnumHand.MAIN_HAND, hit);
            require(world.updates == updates && player.getHeldItemMainhand().getCount() == 1, "occupied click emitted update/consumed item");
            IExtendedBlockState state = (IExtendedBlockState)block.getExtendedState(world.getBlockState(AT), world, AT);
            require(state.getValue(BlockModuleRack.OCCUPIED_MASK) == 1 << slot
                    && state.getValue(BlockModuleRack.MODULE_MASK) == 1 << slot, "chunk state mask");
            ItemStack picked = block.getPickBlock(world.getBlockState(AT), new RayTraceResult(new Vec3d(AT), facing, AT), world, AT, player);
            require(picked.getTagCompound() == null, "pick block copied installed modules");
            player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            require(activate(block, world, player, facing, EnumHand.MAIN_HAND, hit(facing, slot, true)), "slot button extraction");
            require(rack.getOccupiedMask() == 0 && moduleCount(player) == 1, "button did not return exactly one module");
            cases++;
        }
        NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(false);
        TileEntityModuleRack rack = install(world, block, EnumFacing.SOUTH);
        Player player = new Player(world);
        player.setHeldItem(EnumHand.OFF_HAND, module(2, 0));
        float[] hit = hit(EnumFacing.SOUTH, 0, false);
        require(!activate(block, world, player, EnumFacing.SOUTH, EnumHand.MAIN_HAND, hit), "empty main hand swallows off-hand insertion");
        require(activate(block, world, player, EnumFacing.SOUTH, EnumHand.OFF_HAND, hit), "off-hand insertion");
        require(player.getHeldItemOffhand().getCount() == 1 && rack.getOccupiedMask() == 1, "off-hand count");
        player.setHeldItem(EnumHand.OFF_HAND, ItemStack.EMPTY);
        for (int index = 0; index < player.inventory.mainInventory.size(); index++)
            player.inventory.mainInventory.set(index, new ItemStack(Items.STICK, 64));
        player.inventory.currentItem = 0;
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        // Leave no main-inventory room while using the empty off hand to extract.
        player.inventory.mainInventory.set(0, new ItemStack(Items.STICK, 64));
        activate(block, world, player, EnumFacing.SOUTH, EnumHand.OFF_HAND, hit);
        require(rack.getOccupiedMask() == 1 && rack.getStackInSlot(0).getCount() == 1,
                "full inventory silently discarded installed module");
    }

    private static int moduleCount(Player player) {
        int result = 0;
        for (ItemStack stack : player.inventory.mainInventory)
            if (stack.getItem() == ModItems.BLANK_MODULE) result += stack.getCount();
        return result;
    }

    private static void dropOwnership(BlockModuleRack block) {
        NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(false);
        TileEntityModuleRack rack = install(world, block, EnumFacing.SOUTH);
        for (int slot = 0; slot < 4; slot++) rack.insertModule(slot, module(1, slot), false);
        IBlockState state = world.getBlockState(AT);
        block.breakBlock(world, AT, state);
        int count = 0;
        for (Entity entity : world.loadedEntityList) if (entity instanceof EntityItem) {
            ItemStack dropped = ((EntityItem)entity).getItem();
            require(dropped.getItem() == ModItems.BLANK_MODULE && dropped.hasTagCompound(), "break drop item payload");
            count += dropped.getCount();
        }
        require(count == 4 && rack.drainModules().isEmpty(), "break did not drop exactly four owned modules");
        int entities = world.loadedEntityList.size();
        block.breakBlock(world, AT, state);
        require(world.loadedEntityList.size() == entities, "repeated break duplicated drops");
    }

    private static void visualPackets(BlockModuleRack block) {
        VisualProxy proxy = new VisualProxy();
        VandorLabs.proxy = proxy;
        NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(true);
        TileEntityModuleRack client = install(world, block, EnumFacing.SOUTH);
        TileEntityModuleRack server = new TileEntityModuleRack();
        TileEntityModuleRack fresh = new TileEntityModuleRack();
        fresh.setWorld(world);
        server.insertModule(0, module(1, 0), false);
        fresh.onDataPacket(null, server.getUpdatePacket());
        require(proxy.changes == 1 && proxy.initials == 0, "fresh first insertion was mistaken for a chunk snapshot");
        proxy.changes = 0;
        server = new TileEntityModuleRack();
        client.handleUpdateTag(server.getUpdateTag());
        require(proxy.initials == 1 && client.getOccupiedMask() == 0, "initial snapshot");
        server.insertModule(2, module(1, 2), false);
        SPacketUpdateTileEntity inserted = server.getUpdatePacket();
        client.onDataPacket(null, inserted);
        require(client.getOccupiedMask() == 4 && proxy.changes == 2 && proxy.initials == 1, "live visual transition");
        int renders = world.renderUpdates;
        client.onDataPacket(null, inserted);
        require(proxy.changes == 2 && world.renderUpdates == renders, "duplicate packet restarts/rebuilds");
        client.setRenderMask(client.getRenderMask());
        require(world.renderUpdates == renders, "unchanged render mask rebuilds chunk");
        server.removeModule(2);
        client.onDataPacket(null, server.getUpdatePacket());
        require(client.getOccupiedMask() == 0 && proxy.changes == 3, "remove packet");
        client.onDataPacket(null, inserted);
        require(client.getOccupiedMask() == 0 && proxy.changes == 3, "stale packet rewinds occupancy");
        client.readFromNBT(server.writeToNBT(new NBTTagCompound()));
        require(!client.insertModule(0, module(1, 0), false) && client.removeModule(2).isEmpty()
                && client.drainModules().isEmpty(), "client claimed physical items");
        client.handleUpdateTag(server.getUpdateTag());
        require(proxy.initials == 2, "chunk snapshot must be initial, not insertion replay");
        client.onChunkUnload();
        client.invalidate();
        require(proxy.removals == 2, "client unload cleanup");
        VandorLabs.proxy = null;
    }

    /** Server chunk packets may coalesce an air update and a replacement rack.
     * The client keeps its tile when the final block type is unchanged. */
    private static void replacementPackets(BlockModuleRack block) {
        VisualProxy proxy = new VisualProxy();
        VandorLabs.proxy = proxy;
        NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(true);
        TileEntityModuleRack client = install(world, block, EnumFacing.SOUTH);
        TileEntityModuleRack previous = new TileEntityModuleRack();
        previous.setPos(AT);
        previous.insertModule(0, module(1, 0), false);
        for (int cycle = 0; cycle < 8; cycle++) {
            previous.insertModule(1, module(1, cycle), false);
            previous.removeModule(1);
        }
        client.handleUpdateTag(previous.getUpdateTag());
        TileEntityModuleRack replacement = new TileEntityModuleRack();
        replacement.setPos(AT);
        replacement.insertModule(0, module(1, 100), false);
        require(replacement.getRevision() < client.getRevision()
                && replacement.getInsertionSequence(0) == client.getInsertionSequence(0),
                "replacement fixture needs low revision and a coincident per-slot sequence");
        SPacketUpdateTileEntity packet = replacement.getUpdatePacket();
        client.onDataPacket(null, packet);
        require(client.getRevision() == replacement.getRevision() && client.getOccupiedMask() == 1,
                "replacement rack discarded its first authoritative packet as stale");
        require(proxy.changes == 2 && proxy.initials == 1 && proxy.previousMask == 0
                && proxy.previousSequences[0] == 0,
                "replacement insertion needs a fresh cosmetic baseline even when its sequence matches the old rack");
        int renders = world.renderUpdates;
        client.onDataPacket(null, packet);
        require(proxy.changes == 2 && world.renderUpdates == renders, "replacement duplicate replays motion");
        long incarnation = client.getIncarnation();
        client.onDataPacket(null, previous.getUpdatePacket());
        require(client.getIncarnation() == incarnation && client.getRevision() == replacement.getRevision()
                && client.getOccupiedMask() == 1 && proxy.changes == 2 && world.renderUpdates == renders,
                "late packet from removed rack overwrote its replacement");
        cases++;
        VandorLabs.proxy = null;
    }

    private static void copierOwnership() throws Exception {
        TileEntityModuleRack rack = new TileEntityModuleRack();
        rack.insertModule(0, module(1, 10), false);
        rack.insertModule(1, module(1, 20), false);
        rack.insertModule(2, module(1, 10), false);
        NBTTagCompound saved = rack.writeToNBT(new NBTTagCompound());
        List<CopyMaterials.Need> needs = new ArrayList<>();
        java.lang.reflect.Method inventory = CopyMaterials.class.getDeclaredMethod("inventory",
                net.minecraft.tileentity.TileEntity.class, List.class);
        inventory.setAccessible(true);
        inventory.invoke(null, rack, needs);
        require(needs.size() == 2, "copy cost must preserve distinct module NBT");
        long total = 0;
        for (CopyMaterials.Need need : needs) {
            require(need.exact && need.item.getItem() == ModItems.BLANK_MODULE,
                    "copy module cost must be exact item+NBT");
            if (ItemStack.areItemStackTagsEqual(need.item, module(1, 10))) require(need.count == 2, "equal-NBT module counts");
            else require(ItemStack.areItemStackTagsEqual(need.item, module(1, 20)) && need.count == 1,
                    "distinct-NBT module count");
            total += need.count;
        }
        require(total == 3 && saved.equals(rack.writeToNBT(new NBTTagCompound())), "copy costing mutated source");
        java.lang.reflect.Method empty = CopyWorld.class.getDeclaredMethod("emptyDetached",
                net.minecraft.tileentity.TileEntity.class);
        empty.setAccessible(true);
        empty.invoke(null, rack);
        require(rack.getOccupiedMask() == 0 && rack.drainModules().isEmpty(), "detached copy source retained live inventory");
        TileEntityModuleRack published = new TileEntityModuleRack();
        published.readFromNBT(saved);
        require(published.getOccupiedMask() == 7 && published.drainModules().size() == 3,
                "frozen snapshot lost module ownership");
    }

    private static int encoded(NBTTagCompound tag) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(tag, new DataOutputStream(bytes));
        return bytes.size();
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError("Module Rack: " + message);
    }
}
