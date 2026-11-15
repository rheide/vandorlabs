package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Canonical native registration and real configured-item admission, without enabling the unfinished public runtime. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRampCanonicalChecks {
    private static int ticks,checks;
    private static final BlockPos BASE=new BlockPos(27400,105,27400);
    private static String phase(){return System.getProperty("vandorlabs.transmitterPhase","");}
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Canonical Ramp: "+why);}
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){if(!phase().startsWith("ramp-canonical-")||++ticks!=20)return;try{
        var world=event.getServer().overworld();var actor=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(world);actor.setGameMode(GameType.SURVIVAL);actor.getInventory().clearContent();
        need(NativeRampBindings.enabled(),"explicit private activation");need(BuiltInRegistries.BLOCK.get(ResourceLocation.parse("vandorlabs:programmable_ramp"))==NativeRampBindings.BLOCK&&BuiltInRegistries.BLOCK.get(ResourceLocation.parse("vandorlabs:programmable_ramp_slab"))==NativeRampBindings.SLAB,"canonical block identities");
        need(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(NativeRampBindings.ENTITY).equals(ResourceLocation.parse("vandorlabs:programmable_ramp")),"released common tile identity");need(NativeRampBindings.PART.asItem()==Items.AIR,"generated parts have no placeable item");
        int index=0;for(int kind=0;kind<2;kind++)for(var scan:Direction.values())for(boolean top:new boolean[]{false,true}){
            var pos=BASE.east(index++*3);world.getChunkAt(pos);var block=kind==0?NativeRampBindings.BLOCK:NativeRampBindings.SLAB;var item=kind==0?NativeRampBindings.ITEM:NativeRampBindings.SLAB_ITEM;
            if(phase().endsWith("write")){
                for(var p:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,2,1)))world.setBlock(p,Blocks.AIR.defaultBlockState(),3);world.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
                actor.setYRot(scan.getAxis().isHorizontal()?scan.toYRot():90);actor.setXRot(scan==Direction.UP?-70:scan==Direction.DOWN?70:0);actor.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-2);
                var settings=NativeRampSettings.defaults().itemChoices(scan.getAxis().isHorizontal()?scan.get3DDataValue():Direction.WEST.get3DDataValue());settings.putInt("EndOffsetHalfSteps",0);settings.putInt("EndOffset",0);
                var data=new CompoundTag();data.put(NativeRampItem.CONFIGURATION,settings);var stack=new ItemStack(item,2);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));actor.setItemInHand(InteractionHand.MAIN_HAND,stack);
                var face=top?Direction.DOWN:Direction.UP;var support=top?pos.above():pos.below();if(top)world.setBlock(support,Blocks.STONE.defaultBlockState(),3);
                var hit=new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),face,support,false);
                need(actor.gameMode.useItemOn(actor,world,stack,InteractionHand.MAIN_HAND,hit).consumesAction()&&stack.getCount()==1,"configured Survival placement consumes one item");
                need(world.getBlockEntity(pos) instanceof NativeRampEntity,"canonical item factory");var tile=(NativeRampEntity)world.getBlockEntity(pos);
                need(!tile.inert()&&tile.controller()!=null&&tile.settings().endHalf()==0&&actor.getUUID().equals(NativeRampAuthorization.actorId(tile)),"installed choices and physical actor authority");need(NativeRampBlock.scanFacing(tile.getBlockState())==scan,"player-relative six-face scan");
                if(kind==1)need((tile.getBlockState().getValue(NativeRampSlabBlock.HALF)==net.minecraft.world.level.block.state.properties.Half.TOP)==top,"horizontal slab half from clicked support");
                var before=NativeRampCopiedSettings.capture(tile);var footer=new CompoundTag();footer.putIntArray("redstone_channel",new int[]{7,11});var plan=NativeRampCopiedSettings.prepare(tile,footer);need(plan.applicable()&&!plan.geometry()&&NativeRampCopiedSettings.apply(tile,actor,plan),"channel-only synchronous copier footer");need(tile.settings().channels().size()==2,"bounded channel bank committed");var restore=NativeRampCopiedSettings.prepare(tile,before);need(restore.applicable()&&NativeRampCopiedSettings.apply(tile,actor,restore)&&before.equals(NativeRampCopiedSettings.capture(tile)),"settings-only geometry and footer roundtrip");
                need(world.getRecipeManager().byKey(ResourceLocation.parse("vandorlabs:"+(kind==0?"programmable_ramp":"programmable_ramp_slab"))).isPresent(),"native recipe loaded");
            }else{
                need(world.getBlockState(pos).is(block)&&world.getBlockEntity(pos) instanceof NativeRampEntity,"cold canonical owner present");var tile=(NativeRampEntity)world.getBlockEntity(pos);need(!tile.inert()&&tile.settings().endHalf()==0&&tile.controller()!=null,"cold settings/host intact");
            }
        }
        System.out.println("[vandorlabs][ramp-canonical] "+(phase().endsWith("write")?"write":"read")+" PASS checks="+checks+" configuredPlacement/sixScanFaces/slabHalves/typedCopy/recipes/coldIdentity");event.getServer().halt(false);
    }catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][ramp-canonical] FAIL "+failure);event.getServer().halt(false);}}
    private NativeRampCanonicalChecks(){}
}
