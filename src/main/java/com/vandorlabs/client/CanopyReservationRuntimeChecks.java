package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.*;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.*;

/** Permission and parked closed-pose regressions for released canopy ownership. */
final class CanopyReservationRuntimeChecks {
    private static final BlockPos POS=new BlockPos(-180,110,180);
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("canopy released reservations: "+text);}
    static void run(World world,EntityPlayerMP actor){
        Vec3d before=actor.getPositionVector();GameType game=actor.interactionManager.getGameType();ItemStack held=actor.getHeldItemMainhand().copy();long time=world.getTotalWorldTime();
        try{
            clear(world);BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:large_front_canopy_glass");
            LargeCanopySettings settings=new LargeCanopySettings();settings.depth=24;settings.scale=50;settings.hinge=2;settings.openable=true;
            ItemStack stack=new ItemStack(block);NBTTagCompound item=new NBTTagCompound();settings.write(item);stack.setTagCompound(item);
            IBlockState state=block.getDefaultState().withProperty(BlockCanopy.FACING,EnumFacing.NORTH);world.setBlockState(POS,state,3);block.onBlockPlacedBy(world,POS,state,null,stack);
            TileEntityCanopy root=TileEntityCanopy.at(world,POS);root.toggle();advance(world,root,24);
            require(root.progress==1&&root.hasReleasedCells(),"opened fixture has physically released helpers");
            actor.setPosition(POS.getX()+.5,POS.getY()+2,POS.getZ()-2);actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,ItemStack.EMPTY);
            LargeCanopySettings next=new LargeCanopySettings();NBTTagCompound config=new NBTTagCompound();root.large.write(config);next.read(config);next.depth=40;
            Set<BlockPos> affected=new HashSet<>(root.cells());affected.addAll(LargeCanopyOwnership.shellCells(block,POS,root.facing(),next,root.centeredPlacement));
            BlockPos foreign=POS.west(4);world.setBlockState(foreign,Blocks.GOLD_BLOCK.getDefaultState(),3);affected.add(foreign);
            Map<BlockPos,IBlockState> states=new HashMap<>();Map<BlockPos,NBTTagCompound> tiles=new HashMap<>();
            for(BlockPos cell:affected){states.put(cell,world.getBlockState(cell));TileEntity tile=world.getTileEntity(cell);tiles.put(cell,tile==null?null:tile.writeToNBT(new NBTTagCompound()));}
            actor.setGameType(GameType.ADVENTURE);
            try{require(!LargeCanopyOwnership.apply(root,actor,next),"uneditable Adventure geometry change is denied");}finally{actor.setGameType(game);}
            for(BlockPos cell:affected){TileEntity tile=world.getTileEntity(cell);require(states.get(cell).equals(world.getBlockState(cell))&&Objects.equals(tiles.get(cell),tile==null?null:tile.writeToNBT(new NBTTagCompound())),"denied resize preserves released helpers, source NBT and foreign blocks at "+cell);}
            require(root.hasReleasedCells(),"denied shape edit does not reacquire any open helper");
            BlockPos extension=affected.stream().filter(cell->!root.cells().contains(cell)&&!cell.equals(foreign)&&world.isAirBlock(cell)).findFirst().orElseThrow(()->new IllegalStateException("missing expanded footprint fixture"));
            world.setBlockState(extension,Blocks.DIAMOND_BLOCK.getDefaultState(),3);states.put(extension,world.getBlockState(extension));tiles.put(extension,null);
            require(!LargeCanopyOwnership.apply(root,actor,next),"foreign block in expanded footprint refuses resize");
            for(BlockPos cell:affected){TileEntity tile=world.getTileEntity(cell);require(states.get(cell).equals(world.getBlockState(cell))&&Objects.equals(tiles.get(cell),tile==null?null:tile.writeToNBT(new NBTTagCompound())),"obstructed expansion preserves old released ownership before restore at "+cell);}
            world.setBlockToAir(extension);
            // This is the saved pose produced when a captured canopy closes before parking:
            // logical closed pose, with physical helpers still released in its snapshot.
            NBTTagCompound closed=root.writeToNBT(new NBTTagCompound());closed.setBoolean("Open",false);closed.setFloat("Progress",0);closed.setBoolean("CanopyWaitingForReservations",false);
            root.readFromNBT(closed);root.onLoad();advance(world,root,2);
            require(root.progress==0&&!root.hasReleasedCells()&&!root.waitingForReservations,"parked closed released snapshot reacquires clear footprint");
            for(BlockPos cell:root.cells())require(TileEntityCanopy.at(world,cell)!=null&&TileEntityCanopy.at(world,cell).owner()==root,"restored parked helper retains exact owner at "+cell);
            root.toggle();advance(world,root,24);require(root.progress==1&&root.hasReleasedCells(),"blocked parked fixture opens and releases again");
            BlockPos blocker=root.cells().stream().filter(cell->root.isReleasedCell(cell)&&world.isAirBlock(cell)).findFirst().orElseThrow(()->new IllegalStateException("missing released obstruction fixture"));
            world.setBlockState(blocker,Blocks.DIAMOND_BLOCK.getDefaultState(),3);
            closed=root.writeToNBT(new NBTTagCompound());closed.setBoolean("Open",false);closed.setFloat("Progress",0);closed.setBoolean("CanopyWaitingForReservations",false);
            root.readFromNBT(closed);root.onLoad();advance(world,root,24);
            require(root.progress==1&&root.waitingForReservations&&!root.targetOpen,"blocked parked closed snapshot returns to a safe open waiting pose");
            require(world.getBlockState(blocker).getBlock()==Blocks.DIAMOND_BLOCK&&world.getBlockState(foreign).getBlock()==Blocks.GOLD_BLOCK,"parked recovery never overwrites foreign blocks");
            world.setBlockToAir(blocker);advance(world,root,64);
            require(root.progress==0&&!root.waitingForReservations&&!root.hasReleasedCells(),"clearing parked obstacle wakes close and fully restores helpers");
            require(world.getBlockState(foreign).getBlock()==Blocks.GOLD_BLOCK,"successful retry preserves unrelated foreign block");
            System.out.println("[vandorlabs][reprolab] canopy-released-reservation PASS denied-shape-edit-exact-snapshot no-reacquire-before-permission parked-closed-NBT air-reacquire foreign-block-safe-wait removal-retry");
        }finally{actor.setGameType(game);actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,held);clear(world);world.setTotalWorldTime(time);actor.setPosition(before.x,before.y,before.z);}
    }
    private static void advance(World world,TileEntityCanopy root,int ticks){for(int i=0;i<ticks;i++){world.setTotalWorldTime(world.getTotalWorldTime()+1);CanopyPreparationRuntimeChecks.await(root);root.update();}}
    private static void clear(World world){for(BlockPos cell:BlockPos.getAllInBox(POS.add(-7,-3,-4),POS.add(9,13,8))){world.getChunkFromBlockCoords(cell);world.setBlockToAir(cell);}}
}
