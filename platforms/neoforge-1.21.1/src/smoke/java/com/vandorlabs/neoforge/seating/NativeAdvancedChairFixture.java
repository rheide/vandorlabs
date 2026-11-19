package com.vandorlabs.neoforge.seating;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.persistence.ChairSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeAdvancedChairFixture {
    static final BlockPos ROOT = new BlockPos(0, 96, 0);
    static final Vec3 START = new Vec3(.5, 96, -3);
    static final BlockPos SIDE = ROOT.west();

    static boolean enabled() { return Boolean.getBoolean("vandorlabs.advancedChairCheck"); }

    static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError("Native Advanced Pilot Chair: " + label);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer actor)) return;
        var world = actor.serverLevel();
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 3; z++) {
            world.setBlock(ROOT.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        }
        actor.setGameMode(GameType.SURVIVAL);
        actor.connection.teleport(START.x, START.y, START.z, 0, 0);
        actor.getInventory().selected = 0;
        var item = NativeSeatBindings.ITEMS.get(NativeSeatKind.ADVANCED);
        require(world.getRecipeManager().byKey(ResourceLocation.parse("vandorlabs:chair_siderests")).isPresent(),
            "canonical Advanced Pilot Chair recipe");
        var stack = new ItemStack(item);
        actor.setItemInHand(InteractionHand.MAIN_HAND, stack);
        // A real BlockItem use must refuse a blocked side without consuming or publishing parts.
        world.setBlock(SIDE, Blocks.STONE.defaultBlockState(), 3);
        var support = ROOT.below();
        var hit = new BlockHitResult(Vec3.atCenterOf(support).add(0, .5, 0), Direction.UP, support, false);
        require(!stack.useOn(new UseOnContext(actor, InteractionHand.MAIN_HAND, hit)).consumesAction()
            && actor.getMainHandItem() == stack && stack.getCount() == 1 && world.isEmptyBlock(ROOT)
            && world.isEmptyBlock(ROOT.above()) && world.isEmptyBlock(ROOT.east())
            && world.getBlockState(SIDE).is(Blocks.STONE), "blocked side leaves all four owners untouched");
        world.removeBlock(SIDE, false);
        stack.set(DataComponents.BLOCK_STATE, net.minecraft.world.item.component.BlockItemStateProperties.EMPTY
            .with(NativeAdvancedChairBlock.SIDE, 1));
        require(!stack.useOn(new UseOnContext(actor, InteractionHand.MAIN_HAND, hit)).consumesAction()
            && stack.getCount() == 1 && world.isEmptyBlock(ROOT) && world.isEmptyBlock(SIDE),
            "configured proxy property cannot replace the reserved anchor");
        stack.remove(DataComponents.BLOCK_STATE);

        for (var facing : Direction.Plane.HORIZONTAL) {
            for (int height = 0; height <= 3; height++) {
                var root = NativeAdvancedChairGeometry.shape(facing, height, NativeAdvancedChairGeometry.ROOT);
                var left = NativeAdvancedChairGeometry.shape(facing, height, NativeAdvancedChairGeometry.LEFT);
                var right = NativeAdvancedChairGeometry.shape(facing, height, NativeAdvancedChairGeometry.RIGHT);
                require(!root.isEmpty() && !left.isEmpty() && !right.isEmpty()
                    && root.bounds().minY == 0, "authored clipped parts at " + facing + "/" + height);
                require(Math.abs(NativeAdvancedChairGeometry.cushion(facing, height).y - (.1625 + height / 8D)) < 1e-6,
                    "authored four-level cushion at " + facing + "/" + height);
            }
        }

        actor.getInventory().setItem(1, new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));
        actor.getInventory().setItem(2, new ItemStack(Items.DIAMOND_PICKAXE));
        actor.inventoryMenu.broadcastChanges();
        System.out.println("[vandorlabs][advanced-chair] setup PASS");
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer actor)) return;
        var world = actor.serverLevel();
        for (int part = 0; part < NativeAdvancedChairGeometry.CELLS; part++) {
            require(world.isEmptyBlock(ROOT.offset(NativeAdvancedChairGeometry.offset(part, Direction.NORTH))),
                "side mining clears complete footprint");
        }
        var item = NativeSeatBindings.ITEMS.get(NativeSeatKind.ADVANCED);
        var stacks = new java.util.ArrayList<ItemStack>();
        for (var dropped : world.getEntitiesOfClass(ItemEntity.class, new AABB(ROOT).inflate(4))) stacks.add(dropped.getItem());
        for (int slot = 0; slot < actor.getInventory().getContainerSize(); slot++) stacks.add(actor.getInventory().getItem(slot));
        var drops = stacks.stream().filter(stack -> stack.is(item)).toList();
        require(drops.size() == 1 && drops.getFirst().getCount() == 1
            && drops.getFirst().get(DataComponents.CUSTOM_DATA).copyTag().getCompound(NativeChairEntity.ITEM_SETTINGS)
                .getInt(ChairSettings.PILOT_HEIGHT) == 3, "one configured item after mining a side part");
        var saved = drops.getFirst().get(DataComponents.CUSTOM_DATA).copyTag().getCompound("NativeChairPreserved")
            .getList("CockpitControls",net.minecraft.nbt.Tag.TAG_COMPOUND);
        var left=saved.getCompound(3);
        var right=saved.getCompound(4);
        require(left.getString("Id").equals("slider_left")&&right.getString("Id").equals("slider_right"),
            "released cockpit control indices survive pickup");
        require(java.util.Arrays.equals(left.getIntArray("LevelChannels"),new int[]{8451,8452})
            &&java.util.Arrays.equals(left.getIntArray("Levels"),new int[]{7,10})
            &&java.util.Arrays.equals(right.getIntArray("Levels"),new int[]{10}),
            "both numeric latch banks preserve partial overlap through pickup");
        System.out.println("[vandorlabs][advanced-chair] completed PASS");
        System.out.println("[vandorlabs][advanced-chair] logout PASS");
    }

    private NativeAdvancedChairFixture() {}
}
