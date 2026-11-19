package com.vandorlabs.neoforge.gear;

import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.navigation.ObserverPower;
import com.vandorlabs.neoforge.navigation.observer.*;
import com.vandorlabs.neoforge.ports.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** A real Power Port supplies both upright observer families through two-section moving arms. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeGearObserverPowerChecks {
    private record Case(String design,BlockPos arm,BlockPos observer) {
        BlockPos supply() {return arm.below();}
        BlockPos core(int phase) {return observer.below(phase==0?1:0);}
    }
    private static final Case[] SCENES={
        new Case("standalone_dish",new BlockPos(0,100,0),new BlockPos(0,102,0)),
        new Case("standalone_antenna",new BlockPos(7,100,0),new BlockPos(6,102,0))
    };
    private static ServerLevel world;
    private static FakePlayer actor;
    private static int stage,ticks,motionChecks,heldChecks;
    private static boolean finished;
    private static final UUID[] identities=new UUID[SCENES.length];
    private static final int[] supplyAtDeparture=new int[SCENES.length];
    private static boolean enabled() {return "observer".equals(System.getProperty("vandorlabs.gearResourcePhase"));}
    private static void need(boolean value,String why) {
        if(!value)throw new AssertionError("Observer arm power stage="+stage+" tick="+ticks+": "+why);
    }
    private static void use(BlockPos pos,Direction face,ItemStack stack) {
        actor.setItemInHand(InteractionHand.MAIN_HAND,stack);
        Vec3 point=Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));
        need(actor.gameMode.useItemOn(actor,world,stack,InteractionHand.MAIN_HAND,
                new BlockHitResult(point,face,pos,false)).consumesAction(),"ordinary Survival use at "+pos);
    }
    private static NativeObserverEntity observer(Case scene,int endpoint) {
        return world.getBlockEntity(scene.core(endpoint)) instanceof NativeObserverEntity tile?tile:null;
    }
    private static NativePowerPortEntity supply(Case scene) {return (NativePowerPortEntity)world.getBlockEntity(scene.supply());}
    private static NativeGearEntity arm(Case scene) {return NativeGearFormation.at(world,scene.arm);}
    private static void placeArms(Case scene) {
        actor.setPos(scene.arm.getX()+.5,100,scene.arm.getZ()-3);
        world.setBlock(scene.supply(),NativePortBindings.BLOCKS.get(NativePortKind.POWER).defaultBlockState(),3);
        need(supply(scene).configure(NativePortData.placed(Direction.DOWN.get3DDataValue())),"upward Port output");
        var stack=new ItemStack(NativeGearBindings.ITEMS.get(NativeGearDefinition.EXTENDER.id),2);
        actor.setShiftKeyDown(true);
        use(scene.supply(),Direction.UP,stack);
        actor.setShiftKeyDown(false);
        use(scene.arm,Direction.UP,stack);
        need(stack.isEmpty(),"two arm parts consumed");
    }
    private static void command(Case scene) {
        actor.setPos(scene.arm.getX()+.5,100,scene.arm.getZ()-3);
        use(scene.arm,Direction.NORTH,ItemStack.EMPTY);
    }

    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(!enabled())return;
        try {
            world=event.getServer().overworld();
            actor=new FakePlayer(world,new com.mojang.authlib.GameProfile(new UUID(0,2211231),"ObserverArmPower"));
            actor.setGameMode(GameType.SURVIVAL);
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){world.setChunkForced(x,z,true);world.getChunk(x,z);}
            for(int i=0;i<SCENES.length;i++){
                var scene=SCENES[i];
                actor.setPos(scene.observer.getX()+.5,102,scene.observer.getZ()-3);
                for(int x=-2;x<=5;x++)for(int y=97;y<=109;y++)for(int z=-4;z<=4;z++)
                    world.setBlock(scene.arm.offset(x,y-100,z),y==97?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                world.setBlock(scene.observer,NativeObserverBindings.CORES.get(scene.design).defaultBlockState(),3);
                var tile=observer(scene,1);
                identities[i]=tile.key();
                var design=tile.design();
                var layout=new CoreLayout(design.width(),design.height(),design.depth(),tile.facing().get3DDataValue(),0,0);
                for(int x=0;x<design.width();x++)for(int y=0;y<design.height();y++)for(int z=0;z<design.depth();z++){
                    var cell=scene.observer.offset(layout.x(x,z),y,layout.z(x,z));
                    if(!cell.equals(scene.observer)&&layout.frame(x,y,z))world.setBlock(cell,NativeObserverFormation.matter().defaultBlockState(),3);
                }
                need(NativeObserverTransactions.request(tile,actor,true),"ordinary "+scene.design+" formation");
            }
        }catch(Throwable failure){fail(failure);}
    }

    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(ServerTickEvent.Post event) {
        if(!enabled()||world==null||finished)return;
        try{
            need(++ticks<500,"timed out waiting for "+java.util.Arrays.stream(SCENES).map(scene->{
                var root=arm(scene);return root==null?"absent":root.progress()+" "+root.error();
            }).toList());
            if(stage==0){
                for(var scene:SCENES){var tile=observer(scene,1);if(tile==null||!tile.formed()||!tile.available())return;}
                for(var scene:SCENES)placeArms(scene);
                stage=1;
                return;
            }
            if(stage==1){
                for(var scene:SCENES)if(!arm(scene).resourceEndpointAvailable())return;
                for(var scene:SCENES){
                    var root=arm(scene);
                    var options=root.settings().editableChoices();
                    options.putInt(ArmResource.CHOICE,ArmResource.POWER.ordinal());
                    need(root.configure(NativeGearSettings.readChoices(options),true),"enable power passage");
                    var port=supply(scene).energy(Direction.DOWN);
                    need(port.receiveEnergy(10000,false)==10000,"one physical supply charges");
                }
                stage=2;
                return;
            }
            if(stage>=3){
                for(int i=0;i<SCENES.length;i++){
                    var scene=SCENES[i];var root=arm(scene);
                    boolean held=NativeGearJournal.busy(root);
                    boolean moving=root.progress()>0&&root.progress()<1;
                    if(held)heldChecks++;
                    if(moving)motionChecks++;
                    if(held||moving)need(supply(scene).stored()==supplyAtDeparture[i],"physical Port cannot debit supply during its arm's hold or motion");
                }
            }
            if(stage>=2){
                int endpoint=stage==3?0:1;
                for(int i=0;i<SCENES.length;i++){
                    var scene=SCENES[i];
                    var root=arm(scene);
                    var tile=observer(scene,endpoint);
                    need(!root.blocked(),"observation carrier remains operable "+root.error());
                    if(root.progress()!=endpoint||NativeGearJournal.busy(root)||tile==null||!tile.powered())return;
                    need(tile.key().equals(identities[i]),"power reaches the same moved observer owner");
                    var input=world.getCapability(Capabilities.EnergyStorage.BLOCK,scene.arm,Direction.DOWN);
                    need(input!=null&&input.canReceive()&&!input.canExtract(),"current bottom mount exposes one-way RF");
                    if(endpoint==0)need(world.getBlockState(scene.core(1).above(tile.design().height()-1)).isAir(),"observer vacates previous far layer");
                }
                if(stage==4){
                    need(motionChecks>0&&heldChecks>0,"actual moving and held phases both observed");
                    System.out.println("[vandorlabs][gear-resources] observer PASS SurveyDishCore/SentinelPart/nativePowerPort/bothAlignedEndpoints/reversal/stopsWhileMoving held="+heldChecks+" moving="+motionChecks);
                    finished=true;
                    world.getServer().halt(false);
                }else{
                    for(int i=0;i<SCENES.length;i++){
                        supplyAtDeparture[i]=supply(SCENES[i]).stored();
                        command(SCENES[i]);
                    }
                    stage++;
                }
                return;
            }
        }catch(Throwable failure){fail(failure);}
    }

    private static void fail(Throwable failure){
        finished=true;
        failure.printStackTrace();
        System.out.println("[vandorlabs][gear-resources] observer FAIL "+failure);
        if(world!=null)world.getServer().halt(false);
    }
    private NativeGearObserverPowerChecks(){}
}
