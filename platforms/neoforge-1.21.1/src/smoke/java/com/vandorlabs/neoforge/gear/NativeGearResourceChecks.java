package com.vandorlabs.neoforge.gear;

import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.neoforge.ports.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandler;

/** Real mounted columns, journals and native resource owners at both endpoints. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeGearResourceChecks {
    private static final BlockPos ORIGIN = new BlockPos(0,100,0);
    private static final Direction MOUNT = Direction.DOWN;
    private static final Direction INPUT = MOUNT.getOpposite();
    private static final NativePortKind[] PORTS = {NativePortKind.POWER,NativePortKind.FLUID,NativePortKind.MATTER};
    private static final ItemStack CARGO = new ItemStack(Items.GOLD_INGOT,3);
    private static final int RF=17, MB=27;
    private static ServerLevel world;
    private static FakePlayer actor;
    private static IItemHandler oldItems;
    private static int stage,ticks,preparing,held,moving;
    private static boolean running;

    private static String phase() {return System.getProperty("vandorlabs.gearResourcePhase","");}
    private static BlockPos column(int index) {return ORIGIN.east(index*2);}
    private static BlockPos target(int index, int stroke) {return column(index).relative(MOUNT,stroke);}
    private static NativeGearEntity arm() {
        return NativeGearFormation.at(world,ORIGIN).controllerEntity();
    }
    private static void need(boolean condition,String description) {
        if (!condition) throw new AssertionError("Native arm resources "+phase()+" stage="+stage+" tick="+ticks+": "+description);
    }
    private static void use(BlockPos block, Direction side, ItemStack stack) {
        actor.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var hit = Vec3.atCenterOf(block).add(Vec3.atLowerCornerOf(side.getNormal()).scale(.5));
        need(actor.gameMode.useItemOn(actor,world,stack,InteractionHand.MAIN_HAND,
                new BlockHitResult(hit,side,block,false)).consumesAction(),"ordinary Survival use at "+block);
    }
    private static ItemStack armSections() {
        var options = NativeGearSettings.defaults().choices();
        options.putBoolean("JoinArms",true);
        options.putInt("JoinRange",2);
        var tag = new CompoundTag();
        tag.put(NativeGearItem.SETTINGS,options);
        var stack = new ItemStack(NativeGearBindings.ITEMS.get(NativeGearDefinition.EXTENDER.id),2);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        return stack;
    }
    private static void mode(ArmResource mode) {
        var root = arm();
        var options = root.settings().editableChoices();
        options.putInt(ArmResource.CHOICE,mode.ordinal());
        need(root.configure(NativeGearSettings.readChoices(options),root.target()),"set transfer to "+mode);
    }
    private static void counts(int endpoint,int expected) {
        var power = (NativePowerPortEntity)world.getBlockEntity(target(0,endpoint));
        var fluids = (NativeFluidPortEntity)world.getBlockEntity(target(1,endpoint));
        var items = (NativeMatterPortEntity)world.getBlockEntity(target(2,endpoint));
        need(power!=null&&fluids!=null&&items!=null,"all three native receivers installed at stroke "+endpoint);
        need(power.stored()==RF*expected&&fluids.stored().getAmount()==MB*expected
                &&items.stored(0).getCount()==CARGO.getCount()*expected,"only one real owner per resource at "+endpoint);
    }
    private static void transfer(int endpoint,int previous) {
        counts(endpoint,previous);
        mode(ArmResource.POWER);
        var power = world.getCapability(Capabilities.EnergyStorage.BLOCK,column(0),INPUT);
        need(power!=null&&power.canReceive()&&!power.canExtract(),"mounted side offers incoming RF only capability="+power+" mode="+arm().settings().resourceTransfer()+" root="+arm().getBlockPos()+" endpoint="+arm().progress()+" mount="+arm().mount()+" frame="+NativeGearPayload.frame(arm(),arm().progress()));
        need(world.getCapability(Capabilities.EnergyStorage.BLOCK,column(0),MOUNT)==null
                &&world.getCapability(Capabilities.EnergyStorage.BLOCK,column(0),null)==null,"other faces deny RF");
        need(power.receiveEnergy(RF,true)==RF,"simulate RF");
        counts(endpoint,previous);
        need(power.receiveEnergy(RF,false)==RF&&power.extractEnergy(RF,false)==0,"execute RF without reverse transfer");
        mode(ArmResource.FLUID);
        need(power.receiveEnergy(RF,false)==0,"previous RF reference uses the current setting");
        var fluid = world.getCapability(Capabilities.FluidHandler.BLOCK,column(1),INPUT);
        var water = new FluidStack(Fluids.WATER,MB);
        need(fluid!=null&&fluid.getTanks()==1&&fluid.fill(water,FluidAction.SIMULATE)==MB,"simulate on second column");
        need(fluid.fill(water,FluidAction.EXECUTE)==MB&&fluid.drain(MB,FluidAction.EXECUTE).isEmpty(),"execute fluids without drain");
        mode(ArmResource.ITEM);
        oldItems = world.getCapability(Capabilities.ItemHandler.BLOCK,column(2),INPUT);
        need(oldItems!=null&&oldItems.insertItem(0,CARGO,true).isEmpty(),"simulate on third column");
        need(oldItems.insertItem(0,CARGO,false).isEmpty()&&oldItems.extractItem(0,3,false).isEmpty(),"execute items without extraction");
        counts(endpoint,previous+1);
    }

    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if (!phase().equals("roundtrip")&&!phase().equals("reload")) return;
        try {
            world = event.getServer().overworld();
            actor = new FakePlayer(world,new com.mojang.authlib.GameProfile(new UUID(0,2211230),"ArmResources"));
            actor.setGameMode(GameType.SURVIVAL);
            actor.setPos(.5,98,-3);
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
                world.setChunkForced(x,z,true);
                world.getChunk(x,z);
            }
            if (phase().equals("roundtrip")) {
                for (int x=-2;x<=6;x++) for (int y=96;y<=103;y++) for (int z=-4;z<=2;z++)
                    world.setBlock(new BlockPos(x,y,z),y==96?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                for(int i=0;i<PORTS.length;i++){
                    actor.setPos(column(i).getX()+.5,98,-3);
                    BlockPos mounting = column(i).relative(INPUT);
                    world.setBlock(mounting,Blocks.STONE.defaultBlockState(),3);
                    var stack = armSections();
                    use(mounting,MOUNT,stack);
                    use(column(i),MOUNT,stack);
                    need(stack.isEmpty(),"two supported sections consume exactly two");
                }
            }
            running = true;
        } catch(Throwable failure) {
            fail(failure);
        }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(ServerTickEvent.Post event) {
        if (!running) return;
        try {
            var root = arm();
            need(++ticks<450,"root="+root.progress()+" error="+root.error()+" busy="+NativeGearJournal.busy(root));
            if (root.columns().size()!=3||!root.resourceEndpointAvailable()&&stage==0
                    ||NativeGearJournal.escrowAdmission(world)!=NativeGearJournal.Status.READY) return;
            if (phase().equals("reload")) {
                need(root.progress()==1&&root.settings().resourceTransfer()==ArmResource.ITEM,"save preserves mode and deployed endpoint");
                counts(2,3);
                transfer(2,3);
                finish();
                return;
            }
            if (stage==0) {
                need(world.getCapability(Capabilities.EnergyStorage.BLOCK,column(0),INPUT)==null
                        &&world.getCapability(Capabilities.FluidHandler.BLOCK,column(1),INPUT)==null
                        &&world.getCapability(Capabilities.ItemHandler.BLOCK,column(2),INPUT)==null,"fitted legacy/default arm starts Off");
                for (int n=0;n<5;n++) {
                    var at = ORIGIN.east(n).relative(MOUNT,2);
                    world.setBlock(at,n%2==0?NativePortBindings.BLOCKS.get(PORTS[n/2]).defaultBlockState():Blocks.STONE.defaultBlockState(),3);
                    if (world.getBlockEntity(at) instanceof NativeResourcePortEntity port)
                        need(port.configure(NativePortData.placed(INPUT.get3DDataValue())),"mount-facing Port input");
                }
                actor.setPos(.5,98,-3);
                stage=1;
                return;
            }
            if (stage==1) {
                if (ticks<12) return;
                transfer(2,0);
                use(ORIGIN,Direction.NORTH,ItemStack.EMPTY);
                stage=2;
                return;
            }
            if (NativeGearJournal.busy(root)||root.progress()>0&&root.progress()<1) {
                if (NativeGearJournal.busy(root)) preparing++;
                if (NativeGearHolds.reserved(world,ORIGIN)) held++;
                if (root.progress()>0&&root.progress()<1) moving++;
                need(oldItems.insertItem(0,CARGO,false)==CARGO,"previous capability denies motion and live journal transfer");
            }
            if (root.progress()!=(root.target()?1:0)||root.payload()==null
                    ||!root.payload().materialized()||NativeGearJournal.busy(root)) return;
            if (stage==2) {
                need(!root.target()&&root.hasStoredSections(),"retracted with displaced sections separate");
                transfer(1,1);
                use(ORIGIN,Direction.NORTH,ItemStack.EMPTY);
                stage=3;
            } else {
                need(root.target()&&!root.hasStoredSections()&&preparing>0&&held>0&&moving>0,"real journal, held cells and moving world observed");
                transfer(2,2);
                finish();
            }
        } catch(Throwable failure) {
            fail(failure);
        }
    }

    private static void finish() {
        running=false;
        System.out.println("[vandorlabs][gear-resources] "+phase()+" PASS allResources/nativeEndpoints/simulation/sided/mode/savedOwners journal="+preparing+" held="+held+" moving="+moving);
        world.getServer().saveEverything(true,true,true);
        world.getServer().halt(false);
    }

    private static void fail(Throwable failure) {
        running=false;
        failure.printStackTrace();
        System.out.println("[vandorlabs][gear-resources] "+phase()+" FAIL "+failure);
        if (world!=null) world.getServer().halt(false);
    }
    private NativeGearResourceChecks() {}
}
