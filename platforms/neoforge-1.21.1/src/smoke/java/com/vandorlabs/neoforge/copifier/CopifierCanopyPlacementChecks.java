package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.canopy.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Actual configured item placement and installed-world settings-only history across eleven roles. */
final class CopifierCanopyPlacementChecks {
    private static int checks;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Copied Canopy placement: "+why);}
    static void run(ServerLevel world){
        var actor=new net.neoforged.neoforge.common.util.FakePlayer(world,new com.mojang.authlib.GameProfile(new UUID(0,1901),"CopiedCanopies"));actor.setGameMode(GameType.SURVIVAL);
        int index=0;
        for(String kind:new String[]{"angled_front_1x1","angled_front_1x2","canopy","long_canopy","regular_canopy_glass","front_canopy_glass","large_front_canopy_glass","space_visor_2x1","space_visor_3x1","space_visor_5x1","space_visor_5x1_double_height"}){
            var clicked=new BlockPos(24000+32*index++,100,24000);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getChunkAt(clicked.offset(x*16,0,z*16));world.setBlock(clicked,Blocks.STONE.defaultBlockState(),18);
            var item=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("vandorlabs",kind));need(item==NativeCanopyRegistrations.ITEMS.get(kind).get()&&((NativeCanopyItem)item).getBlock()==NativeCanopyRegistrations.BLOCKS.get(kind).get(),"production canonical item/block identity "+kind);var input=new ItemStack(item,2);var values=new CompoundTag();values.putInt("glass_shade",2);values.putInt("glass_tint",3);var crafted=NativeCopifierCraftingItems.apply(input,values,world.registryAccess());need(!crafted.isEmpty()&&crafted.getCount()==1&&input.getCount()==2,"one configured item "+kind);
            actor.setPos(clicked.getX()+.5,clicked.getY()+1,clicked.getZ()-2);actor.setYRot(180);actor.setItemInHand(InteractionHand.MAIN_HAND,crafted);
            var hit=new BlockHitResult(Vec3.atCenterOf(clicked).add(0,.5,0),Direction.UP,clicked,false);need(item.useOn(new UseOnContext(world,actor,InteractionHand.MAIN_HAND,crafted,hit)).consumesAction()&&crafted.isEmpty(),"actual Survival item consumes once "+kind);
            NativeCanopyEntity owner=null;
            for(int x=-8;x<=8;x++)for(int y=0;y<=8;y++)for(int z=-8;z<=8;z++){var candidate=NativeCanopyLifecycle.at(world,clicked.offset(x,y,z));if(candidate!=null&&candidate.available()&&candidate.kind().equals(kind)&&NativeCanopyLifecycle.owner(candidate)==candidate&&candidate.publication().draws())owner=candidate;}
            need(owner!=null&&owner.getType()==NativeCanopyRegistrations.ENTITY.get(),"production installed drawing owner/type "+kind);NativeCanopyLifecycle.prepare(owner);need(owner.settings().data().shade==2&&owner.settings().data().tint==3,"crafted choices installed "+kind);
            var snapshot=owner.saveWithoutMetadata(world.registryAccess());snapshot.putString("PrivateLiveOwner","retained");owner.loadWithComponents(snapshot,world.registryAccess());NativeCanopyLifecycle.prepare(owner);
            actor.setPos(owner.getBlockPos().getX()+.5,owner.getBlockPos().getY(),owner.getBlockPos().getZ()-2);actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            var before=NativeCopifierHousingWorld.capture(world,owner.getBlockPos(),actor,Direction.NORTH);need(before!=null,"common capture "+kind);var edit=new CompoundTag();edit.putInt("glass_shade",1);NativeCopifierUndo.begin(actor,world);boolean applied;try{applied=NativeCopifierHousingWorld.applySingle(world,owner.getBlockPos(),actor,Direction.NORTH,edit);}finally{NativeCopifierUndo.finish(actor);}need(applied&&owner.settings().data().shade==1,"common installed apply "+kind);need(NativeCopifierUndo.undo(actor)&&before.equals(NativeCopifierHousingWorld.capture(world,owner.getBlockPos(),actor,Direction.NORTH)),"common settings Undo "+kind);need(owner.saveWithoutMetadata(world.registryAccess()).getString("PrivateLiveOwner").equals("retained"),"opaque live owner retained "+kind);
            NativeCopifierUndo.begin(actor,world);int connected;try{connected=NativeCopifierHousingConnected.apply(world,owner.getBlockPos(),actor,Direction.NORTH,edit,false);}finally{NativeCopifierUndo.finish(actor);}need(connected==1&&owner.settings().data().shade==1,"common bounded connected selection "+kind);need(NativeCopifierUndo.undo(actor)&&before.equals(NativeCopifierHousingWorld.capture(world,owner.getBlockPos(),actor,Direction.NORTH)),"connected selection Undo "+kind);
            var recovery=owner.saveWithoutMetadata(world.registryAccess());recovery.put(NativeCanopyEntity.RECOVERY,new CompoundTag());owner.loadWithComponents(recovery,world.registryAccess());need(owner.inert()&&NativeCopifierHousingWorld.capture(world,owner.getBlockPos(),actor,Direction.NORTH)==null,"inert recovery cannot be copied "+kind);
        }
        System.out.println("[vandorlabs][copifier-canopy-placement] PASS roles=11 checks="+checks+" actual crafted placement/capture/apply/connected/Undo/opaque/recovery");
    }
}
