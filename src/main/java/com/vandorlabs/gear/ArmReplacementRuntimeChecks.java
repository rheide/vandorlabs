package com.vandorlabs.gear;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Retains the supplied pillar and all member ticks during remove/replace cycles. */
final class ArmReplacementRuntimeChecks {
    private static final BlockPos ROOT=new BlockPos(-79,86,166);
    private static int stage,ticks,cycles;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("arm-replacement stage="+stage+" cycle="+cycles+": "+why);}
    static boolean run(WorldServer world,EntityPlayerMP actor,String path)throws Exception {
        require(++ticks<600,"bounded movement and journal completion");
        if(stage==0){
            NBTTagCompound data;try(java.io.InputStream in=java.nio.file.Files.newInputStream(java.nio.file.Paths.get(path))){data=CompressedStreamTools.readCompressed(in);}
            for(BlockPos at:BlockPos.getAllInBox(new BlockPos(-86,81,162),new BlockPos(-74,90,170)))world.setBlockToAir(at);
            NBTTagList cells=data.getTagList("Cells",10);
            for(int n=0;n<cells.tagCount();n++){NBTTagCompound cell=cells.getCompoundTagAt(n);BlockPos at=new BlockPos(cell.getInteger("X"),cell.getInteger("Y"),cell.getInteger("Z"));Block b=Block.getBlockFromName(cell.getString("Name"));world.setBlockState(at,b.getStateFromMeta(cell.getInteger("Meta")),2);if(cell.hasKey("Tile",10))world.setTileEntity(at,TileEntity.create(world,cell.getCompoundTag("Tile")));}
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);require(arm!=null&&arm.payload()!=null,"saved rotating attachment present");arm.operator(actor);actor.setGameType(net.minecraft.world.GameType.CREATIVE);actor.connection.setPlayerLocation(-82.5,84,169,0,0);stage=1;return false;
        }
        GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);require(arm!=null,"controller retained");
        if(ArmTransactions.locked(world,ROOT))return false;
        if(stage==1){
            if(arm.progress()!=0||arm.payload()==null||!arm.payload().materialized())return false;
            require(!arm.motionBlocked(),"rotated endpoint is unobstructed");
            java.util.Set<BlockPos> cells=arm.payload().placedCells(arm);require(cells.size()==1,"single world-owned payload");
            for(BlockPos at:cells)require(actor.interactionManager.tryHarvestBlock(at)&&world.isAirBlock(at),"mine rotated world block");stage=2;return false;
        }
        if(stage==2){if(arm.payload()!=null)return false;arm.target(true);stage=3;return false;}
        if(stage==3){if(arm.progress()!=1)return false;
            require(!arm.motionBlocked(),"empty arm returns to construction pose");
            actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(Blocks.STONE));require(actor.getHeldItemMainhand().onItemUse(actor,world,ROOT.west(2),EnumHand.MAIN_HAND,EnumFacing.WEST,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"place replacement against extended tip");actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);arm.target(false);stage=4;return false;
        }
        if(arm.progress()!=0||arm.payload()==null||!arm.payload().materialized())return false;
        require(!arm.motionBlocked()&&world.isAirBlock(ROOT.west(3)),"replacement completes and returns to block mode");
        for(BlockPos at:arm.payload().placedCells(arm))require(world.getBlockState(at).getBlock()==Blocks.STONE,"replacement is a selectable world block");
        if(++cycles<2){stage=1;return false;}
        System.out.println("[vandorlabs] arm-replacement PASS saved second world pillar three member ticks remove extend replace retract twice block mode unlocked");return true;
    }
    private ArmReplacementRuntimeChecks(){}
}
