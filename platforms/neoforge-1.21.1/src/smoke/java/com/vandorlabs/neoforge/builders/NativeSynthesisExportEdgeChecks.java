package com.vandorlabs.neoforge.builders;

import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Actual unloaded adjacent inventory: no player or forced-neighbour tickets hide accidental chunk loads. */
final class NativeSynthesisExportEdgeChecks {
    private static final BlockPos ROOT=new BlockPos(1600,120,1600),CHEST=ROOT.offset(-1,0,2);
    private static final TicketType<Long> TICKET=TicketType.create("vandor_builder_export_smoke",Long::compare);
    private static int stage,wait,checks;
    private static NativeBuilderEntity owner(ServerLevel world){return (NativeBuilderEntity)world.getBlockEntity(ROOT);}
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Edge ItemOut stage="+stage+" wait="+wait+": "+why);}
    static boolean tick(ServerLevel world,ServerPlayer actor){
        if(++wait>500)throw new AssertionError("Edge ItemOut timeout stage="+stage+" chestLoaded="+(world.getChunkSource().getChunkNow(CHEST.getX()>>4,CHEST.getZ()>>4)!=null));
        if(stage==0){
            // A normal FORCED level31 ticket keeps neighbouring FULL chunks. Radius0 is FULL-only level33.
            ChunkPos chunk=new ChunkPos(ROOT);world.getChunkSource().addRegionTicket(TICKET,chunk,0,chunk.toLong());world.getChunkAt(ROOT);
            var block=NativeBuilderBindings.CORES.get("synthesis_tower");world.setBlock(ROOT,block.defaultBlockState().setValue(NativeBuilderCoreBlock.FACING,Direction.NORTH),3);
            var owner=owner(world);var d=owner.design();for(int x=0;x<d.width();x++)for(int y=0;y<d.height();y++)for(int z=0;z<d.depth();z++){var at=owner.cell(x,y,z);if(!at.equals(ROOT))world.setBlock(at,owner.layout().frame(x,y,z)?NativeBuilderFormation.matter().defaultBlockState():Blocks.AIR.defaultBlockState(),3);}
            actor.setPos(1601.5,120,1596.5);need(NativeBuilderTransactions.request(owner,actor,true),"real async formation admitted at chunk west boundary");stage=1;wait=0;return false;
        }
        var owner=owner(world);
        if(stage==1){
            if(!owner.available()||!owner.formed()||!owner.synthesisReady())return false;
            owner.configureRedstone(0,ChannelList.EMPTY);world.getChunkAt(CHEST);world.setBlock(CHEST,Blocks.CHEST.defaultBlockState(),3);var chest=(ChestBlockEntity)world.getBlockEntity(CHEST);for(int slot=0;slot<27;slot++)chest.setItem(slot,new ItemStack(Items.COBBLESTONE,64));
            owner.setItem(0,new ItemStack(NativeBuilderFormation.matter(),2));owner.setItem(1,NativeSynthesisRuntime.result(0));world.getServer().saveEverything(true,true,true);stage=2;wait=0;return false;
        }
        if(stage==2){
            if(world.getChunkSource().getChunkNow(CHEST.getX()>>4,CHEST.getZ()>>4)!=null)return false;
            need(world.getChunkSource().getChunkNow(ROOT.getX()>>4,ROOT.getZ()>>4)!=null,"machine chunk remains physically installed");stage=3;wait=0;
        }
        if(stage==3){
            NativeBuilderEntity.tick(world,ROOT,owner.getBlockState(),owner);
            need(world.getChunkSource().getChunkNow(CHEST.getX()>>4,CHEST.getZ()>>4)==null,"ItemOut never requests unloaded target chunk");need(owner.getItem(1).getCount()==1&&owner.getItem(0).getCount()==2&&!owner.processing(),"unloaded target preserves output and queued input");
            if(wait<12)return false;
            var targetChunk=new ChunkPos(CHEST);world.getChunkSource().addRegionTicket(TICKET,targetChunk,0,targetChunk.toLong());world.getChunkAt(CHEST);var chest=(ChestBlockEntity)world.getBlockEntity(CHEST);need(chest.getItem(0).getCount()==64&&chest.getItem(0).is(Items.COBBLESTONE),"actual reloaded chest retains saved contents");chest.setItem(0,ItemStack.EMPTY);stage=4;wait=0;return false;
        }
        if(stage==4){
            NativeBuilderEntity.tick(world,ROOT,owner.getBlockState(),owner);if(wait<16)return false;var chest=(ChestBlockEntity)world.getBlockEntity(CHEST);
            need(chest.getItem(0).getCount()==1&&chest.getItem(0).is(NativeSynthesisRuntime.result(0).getItem())&&owner.getItem(1).isEmpty()&&owner.getItem(0).getCount()==1&&owner.processing()&&!owner.working(),"reloaded target receives exactly one output and queued powerless recipe starts chest="+chest.getItem(0)+" input="+owner.getItem(0)+" output="+owner.getItem(1)+" processing="+owner.processing()+" ready="+owner.synthesisReady()+" available="+owner.available()+" enabled="+owner.enabled()+" working="+owner.working()+" port="+owner.itemPort()+" chestLoaded="+(world.getChunkSource().getChunkNow(CHEST.getX()>>4,CHEST.getZ()>>4)!=null));
            var targetChunk=new ChunkPos(CHEST);world.getChunkSource().removeRegionTicket(TICKET,targetChunk,0,targetChunk.toLong());var chunk=new ChunkPos(ROOT);world.getChunkSource().removeRegionTicket(TICKET,chunk,0,chunk.toLong());System.out.println("[vandorlabs][builder-export-edge] PASS checks="+checks+" actualUnloadedChest/noChunkLoad/reloadExactlyOne/queuedInput");stage=5;return true;
        }
        return stage==5;
    }
}
