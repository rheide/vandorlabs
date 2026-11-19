package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.door.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real item use, world ticks, attachment ownership and reversal through the horizontal hatch. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeHorizontalDoorArmChecks {
    private static final BlockPos ROOT=LargeDoorWorldClientFixture.POS.offset(15,0,0);
    private static final BlockPos HATCH=ROOT.offset(3,2,0);
    private static final BlockPos SUPPORT=ROOT.offset(5,2,0);
    private static final Direction MOUNT=Direction.EAST;
    private static ServerPlayer actor;
    private static NativeLargeDoorEntity hatch;
    private static AABB aperture;
    private static int step,ticks;
    private static boolean outboundCrossing,returnCrossing,complete;
    private NativeHorizontalDoorArmChecks(){}

    private static void need(boolean condition,String message){
        if(!condition)throw new IllegalStateException("horizontal-door-arm step="+step+" tick="+ticks+" "+message);
    }
    public static void start(ServerPlayer player){
        actor=player;
        var level=player.serverLevel();
        actor.connection.teleport(ROOT.getX()+2,ROOT.getY()+1,ROOT.getZ()-3,0,0);
        for(var at:BlockPos.betweenClosed(ROOT.offset(-3,-3,-4),ROOT.offset(9,7,5)))level.setBlock(at,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);
        level.setBlock(ROOT.offset(2,0,-3),Blocks.STONE.defaultBlockState(),Block.UPDATE_CLIENTS);
        for(int z=0;z<2;z++){
            BlockPos root=ROOT.south(z);
            level.setBlock(root.west(),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
            var choices=NativeGearSettings.defaults().choices();
            choices.putBoolean("JoinArms",true);
            choices.putInt("JoinRange",3);
            choices.putInt("Direction",1);
            choices.putInt("KinkDirection",1);
            choices.putBoolean("MiddleHinge",false);
            choices.putInt("HingeMode",0);
            var tag=new CompoundTag();tag.put(NativeGearItem.SETTINGS,choices);
            var sections=new ItemStack(NativeGearBindings.ITEMS.get(NativeGearDefinition.ROTATING.id),4);
            sections.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
            actor.setItemInHand(InteractionHand.MAIN_HAND,sections);
            for(int x=0;x<4;x++)use(root.east(x-1),MOUNT,"Rotating Arm section");
            actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Blocks.DIAMOND_BLOCK));
            use(root.east(3),MOUNT,"joined platform payload");
        }
        for(int z=-1;z<=1;z++)level.setBlock(SUPPORT.south(z),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        var stack=new ItemStack(LargeDoorWorldClientFixture.item());
        var settings=new CompoundTag();
        settings.putInt("SpaceDoorSchema",2);
        settings.putInt("SpaceDesign",23);
        settings.putInt("SpaceDetail",1);
        settings.putBoolean("SpaceFramed",false);
        settings.putBoolean("SpaceDoorPanel",false);
        settings.putBoolean("SpaceDoorSliding",true);
        settings.putInt("SpaceSlideDirection",2);
        var tag=new CompoundTag();tag.put("SpaceDoorSettings",settings);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        actor.setItemInHand(InteractionHand.MAIN_HAND,stack);
        use(SUPPORT,Direction.WEST,"horizontal Large Door");
        hatch=NativeLargeDoorLifecycle.root(level,HATCH);
        need(hatch!=null&&hatch.isHorizontal()&&hatch.settings().door().sliding&&!hatch.settings().door().framed,"bare sliding hatch settings installed");
        double y=HATCH.getY()+.5;
        aperture=new AABB(ROOT.getX()+2,y-.0001,ROOT.getZ()-1,ROOT.getX()+5,y+.0001,ROOT.getZ()+2);
        actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
    }
    private static void use(BlockPos pos,Direction side,String name){
        ItemStack stack=actor.getMainHandItem();
        var hit=new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(side.getNormal()).scale(.5)),side,pos,false);
        var result=actor.gameMode.useItemOn(actor,actor.serverLevel(),stack,InteractionHand.MAIN_HAND,hit);
        need(result.consumesAction(),"normal interaction "+name+" at "+pos+" result="+result+" stack="+stack);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(actor==null||complete)return;
        var level=actor.serverLevel();
        var arm=NativeGearFormation.at(level,ROOT);
        need(++ticks<450,"motion timeout "+(arm==null?"missing":arm.progress()+" "+arm.error()));
        need(NativeLargeDoorLifecycle.root(level,HATCH)==hatch&&NativeLargeDoorLifecycle.complete(level,hatch),"hatch reservation stays intact");
        if(step==0){
            if(arm==null||!arm.controller()||arm.columns().size()!=2)return;
            need(arm.assembly().columnHeight()==4&&arm.mount()==MOUNT,"joined wall-mounted geometry");
            use(ROOT,Direction.WEST,"joined toggle");
            step=1;
            return;
        }
        if(step==1){
            if(!arm.blocked())return;
            need(arm.error().contains("Motion blocked")&&!hatch.getBlockState().getValue(NativeLargeDoorBlock.OPEN),"closed hatch blocks armed motion "+arm.error());
            need(arm.payload()!=null&&arm.payload().size()==2,"whole joined captured ownership survives refusal");
            step=2;
        }
        if(step==2){
            if(NativeGearJournal.busy(arm)||arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return;
            use(HATCH,Direction.UP,"open hatch");
            need(hatch.getBlockState().getValue(NativeLargeDoorBlock.OPEN),"opened through a member");
            use(ROOT,Direction.WEST,"retry joined toggle");
            step=3;
            return;
        }
        if(arm.payload()!=null&&!arm.payload().materialized()){
            boolean crossing=arm.payload().boxes(arm,arm.progress()).stream().anyMatch(box->box.intersects(aperture));
            if(step==3)outboundCrossing|=crossing;else returnCrossing|=crossing;
            need(level.getBlockState(ROOT.east(4)).isAir()&&level.getBlockState(ROOT.east(4).south()).isAir(),"captured surface has no duplicate native source");
        }
        need(!arm.blocked(),"open stroke is obstructed at "+arm.progress()+": "+arm.error());
        if(step==3){
            if(arm.progress()!=0||NativeGearJournal.busy(arm))return;
            need(outboundCrossing&&arm.payload()!=null&&arm.payload().size()==2,"opened hatch passes complete surface");
            use(ROOT,Direction.WEST,"reverse joined toggle");step=4;
            return;
        }
        if(arm.progress()!=1||NativeGearJournal.busy(arm)||arm.payload()==null||!arm.payload().materialized())return;
        need(returnCrossing&&arm.payload().size()==2,"reverse returns both original surface owners");
        for(int z=0;z<2;z++)need(level.getBlockState(ROOT.east(4).south(z)).is(Blocks.DIAMOND_BLOCK)
                &&level.getBlockState(ROOT.west().south(z)).is(Blocks.STONE),"native surface and support at original position");
        complete=true;
        actor.getInventory().setItem(8,new ItemStack(net.minecraft.world.item.Items.EMERALD));
        actor.inventoryMenu.broadcastChanges();
        System.out.println("[vandorlabs][large-door-world-client] horizontal-arm PASS closed obstruction/open passage/joined surface/journals/reversal");
    }
}
