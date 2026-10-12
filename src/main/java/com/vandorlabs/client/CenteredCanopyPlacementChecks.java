package com.vandorlabs.client;

import com.mojang.authlib.GameProfile;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.LargeCanopyOwnership;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import java.util.*;

/** Reject blocked or protected third cells before centered placement writes anything. */
final class CenteredCanopyPlacementChecks {
    private static final BlockPos CLICKED=new BlockPos(8,100,8);
    private static void require(boolean value,String what){if(!value)throw new IllegalStateException("centered canopy: "+what);}
    static void check(World real) {
        WorldServer world=(WorldServer)real;
        BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:space_visor_2x1");
        for(EnumFacing facing:EnumFacing.HORIZONTALS) {
            for(BlockPos pos:BlockPos.getAllInBox(CLICKED.add(-4,-1,-4),CLICKED.add(4,3,4)))world.setBlockToAir(pos);
            BlockPos anchor=block.placementAnchor(CLICKED,facing),third=anchor.offset(facing.rotateY(),2);
            final Set<BlockPos> denied=new HashSet<>();
            EntityPlayerMP player=new EntityPlayerMP(world.getMinecraftServer(),world,new GameProfile(UUID.randomUUID(),"CanopyFixture"),new PlayerInteractionManager(world)) {
                @Override public boolean canPlayerEdit(BlockPos pos,EnumFacing side,ItemStack stack){return !denied.contains(pos);}
            };
            player.rotationYaw=facing.getHorizontalAngle();player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block,2));
            List<BlockPos> cells=LargeCanopyOwnership.itemCells(block,anchor,facing,player.getHeldItemMainhand());
            require(cells.size()==3 && cells.contains(third),"preflight includes third reserved cell");
            world.setBlockState(CLICKED.down(),Blocks.STONE.getDefaultState(),2);world.setBlockState(third,Blocks.STONE.getDefaultState(),2);
            require(!LargeCanopyOwnership.fitsItem(block,world,anchor,facing,player.getHeldItemMainhand()),"third-cell obstruction rejected");
            require(player.getHeldItemMainhand().getItem().onItemUse(player,world,CLICKED.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)==EnumActionResult.FAIL,"item refuses blocked third cell");
            require(world.getBlockState(third).getBlock()==Blocks.STONE && player.getHeldItemMainhand().getCount()==2,"obstruction and item count preserved");
            for(BlockPos pos:cells)if(!pos.equals(third))require(world.isAirBlock(pos),"blocked placement leaves no partial canopy");
            world.setBlockToAir(third);denied.add(third);
            require(LargeCanopyOwnership.fitsItem(block,world,anchor,facing,player.getHeldItemMainhand()),"permission fixture is physically clear");
            require(player.getHeldItemMainhand().getItem().onItemUse(player,world,CLICKED.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)==EnumActionResult.FAIL,"item refuses protected third cell");
            for(BlockPos pos:cells)require(world.isAirBlock(pos),"permission denial leaves entire footprint untouched");
            require(player.getHeldItemMainhand().getCount()==2,"permission denial does not consume item");
        }
        System.out.println("[vandorlabs][reprolab] centered-canopy-preflight PASS third-cell-obstruction third-cell-permission facings=4 no-partial-placement");
    }
}
