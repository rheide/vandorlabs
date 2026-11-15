package com.vandorlabs.neoforge.landing;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

@net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs")
public final class NativeLandingChecks {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new IllegalStateException("Native side wheels: "+why);}
    private static final BlockPos POS=new BlockPos(176,90,8);
    @net.neoforged.bus.api.SubscribeEvent public static void started(net.neoforged.neoforge.event.server.ServerStartedEvent event){String phase=System.getProperty("vandorlabs.landingPhase","");if(phase.isEmpty())return;try{var world=event.getServer().overworld();world.getChunkAt(POS);if(phase.equals("write"))write(world);else read(world);event.getServer().saveEverything(true,true,true);System.out.println("[vandorlabs][modern] landing-"+phase+" PASS checks="+checks);}catch(Throwable failed){failed.printStackTrace();System.out.println("[vandorlabs][modern] landing-"+phase+" FAIL "+failed);}event.getServer().halt(false);}
    private static void clear(ServerLevel world){for(var pos:BlockPos.betweenClosed(POS.offset(-4,-2,-4),POS.offset(4,4,4)))world.removeBlock(pos,false);}
    private static void write(ServerLevel world){var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(world);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        for(var entry:NativeLandingBindings.WHEELS.entrySet())for(var face:Direction.Plane.HORIZONTAL){var wheel=entry.getValue();for(int broken=0;broken<(wheel.size()==2?4:1);broken++){
            clear(world);var support=POS.relative(face.getOpposite());world.setBlock(support,Blocks.STONE_SLAB.defaultBlockState(),3);player.setPos(Vec3.atCenterOf(POS).add(face.getStepX()*2,0,face.getStepZ()*2));player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(NativeLandingBindings.ITEMS.get(entry.getKey()),2));
            var result=player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(face.getStepX()*.5,0,face.getStepZ()*.5),face,support,false)));
            need(result.consumesAction()&&player.getMainHandItem().getCount()==1,"real Survival item use consumes one");
            for(int part=0;part<(wheel.size()==2?4:1);part++){var pos=wheel.cell(POS,face,part);var state=world.getBlockState(pos);need(state.is(wheel)&&state.getValue(NativeSideWheel.PART)==part&&state.getValue(NativeSideWheel.FACING)==face,"all footprint parts");need(wheel.root(pos,state).equals(POS),"root inverse");need(!state.getCollisionShape(world,pos).isEmpty(),"authored clipped collision on every part");need(world.getBlockEntity(pos)==null,"static wheel has no ticking tile");}
            var target=wheel.cell(POS,face,broken);var state=world.getBlockState(target);var loot=Block.getDrops(state,world,target,null,player,ItemStack.EMPTY);need(loot.size()==1&&loot.get(0).getCount()==1&&loot.get(0).is(wheel.asItem()),"one wheel drop from any part");world.destroyBlock(target,false);for(int part=0;part<(wheel.size()==2?4:1);part++)need(!world.getBlockState(wheel.cell(POS,face,part)).is(wheel),"all companions removed");
        }}clear(world);var wheel=NativeLandingBindings.WHEELS.get("wheel_large");for(int part=0;part<4;part++)world.setBlock(wheel.cell(POS,Direction.EAST,part),wheel.defaultBlockState().setValue(NativeSideWheel.FACING,Direction.EAST).setValue(NativeSideWheel.PART,part),3);
    }
    private static void read(ServerLevel world){var wheel=NativeLandingBindings.WHEELS.get("wheel_large");for(int part=0;part<4;part++){var pos=wheel.cell(POS,Direction.EAST,part);var state=world.getBlockState(pos);need(state.is(wheel)&&state.getValue(NativeSideWheel.FACING)==Direction.EAST&&state.getValue(NativeSideWheel.PART)==part,"cold saved palette identity");need(!state.getCollisionShape(world,pos).isEmpty(),"cold collision");}}
    private NativeLandingChecks(){}
}
