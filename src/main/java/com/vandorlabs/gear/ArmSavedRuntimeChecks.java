package com.vandorlabs.gear;

import java.io.*;
import java.nio.file.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;

/** Optional external, exact saved-cell/journal fixture; never edits the supplied archive. */
final class ArmSavedRuntimeChecks {
    private static final BlockPos ROOT=new BlockPos(-79,83,161);
    private static int stage,ticks;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("arm-saved stage="+stage+": "+why);}
    static boolean run(WorldServer world,EntityPlayerMP actor,String path)throws Exception {
        if(++ticks>400){GearTileEntity pending=(GearTileEntity)world.getTileEntity(ROOT);throw new IllegalStateException("Saved arm recovery timed out at stage "+stage+" progress="+pending.progress()+" target="+pending.target()+" locked="+ArmTransactions.locked(world,ROOT)+" error="+pending.error());}
        if(stage==0){
            NBTTagCompound fixture;try(InputStream in=Files.newInputStream(Paths.get(path))){fixture=CompressedStreamTools.readCompressed(in);}
            NBTTagList cells=fixture.getTagList("Cells",10);
            for(int i=0;i<cells.tagCount();i++){
                NBTTagCompound cell=cells.getCompoundTagAt(i);BlockPos at=new BlockPos(cell.getInteger("X"),cell.getInteger("Y"),cell.getInteger("Z"));
                Block block=Block.getBlockFromName(cell.getString("Name"));require(block!=null,"saved block is registered");world.setBlockState(at,block.getStateFromMeta(cell.getInteger("Meta")),2);
                if(cell.hasKey("Tile",10)){TileEntity tile=TileEntity.create(world,cell.getCompoundTag("Tile"));require(tile!=null,"saved tile loads");world.setTileEntity(at,tile);}
            }
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);require(arm!=null&&arm.payload()!=null&&arm.error().contains("AccessDeniedException"),"exact reported stuck controller imported");
            Path file=world.getSaveHandler().getWorldDirectory().toPath().resolve("data/vandorlabs-arms-0/saved-regression.nbt");Files.createDirectories(file.getParent());try(OutputStream out=Files.newOutputStream(file)){CompressedStreamTools.writeCompressed(fixture.getCompoundTag("Journal"),out);}
            ArmTransactions.unloaded(new WorldEvent.Unload(world));ArmTransactions.loaded(new WorldEvent.Load(world));
            actor.connection.setPlayerLocation(-76.5,83,163.5,135,0);stage=1;return false;
        }
        GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);require(arm!=null,"saved controller retained");
        if(ArmTransactions.locked(world,ROOT)||ArmTransactions.locked(world,ROOT.up(2)))return false;
        if(stage==1){require(world.getBlockState(ROOT.up(2)).getBlock()==Blocks.STONE&&arm.payload()!=null&&arm.payload().materialized(),"journal restores one world-owned payload");
            arm.operator(actor);arm.target(false);stage=2;return false;}
        if(stage==2){if(arm.progress()!=0)return false;require(world.isAirBlock(ROOT.up(2))&&arm.payload()!=null&&!arm.payload().materialized(),"recovered saved stack retracts its payload");arm.target(true);stage=3;return false;}
        if(arm.progress()!=1||!arm.payload().materialized())return false;
        require(world.getBlockState(ROOT.up(2)).getBlock()==Blocks.STONE,"saved payload restored once after reversal");
        require(actor.interactionManager.tryHarvestBlock(ROOT.up(2)),"saved payload unlocks for mining");require(actor.interactionManager.tryHarvestBlock(ROOT),"saved arm unlocks for dismantling");
        System.out.println("[vandorlabs] arm-saved PASS exact supplied cells and pending journal at -79 83 161 recovery retraction reversal mining");return true;
    }
    private ArmSavedRuntimeChecks(){}
}
