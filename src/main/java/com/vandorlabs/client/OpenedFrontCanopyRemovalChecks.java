package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.canopy.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Open windows release physical reservations and cached surfaces while preserving foreign blocks. */
final class OpenedFrontCanopyRemovalChecks {
    private static final BlockPos BASE=new BlockPos(40,145,40);
    private static void require(boolean value,String what){if(!value)throw new IllegalStateException("opened front removal: "+what);}
    static void check(World world,EntityPlayerMP player){
        int cases=0;
        for(String kind:new String[]{"front_canopy_glass","large_front_canopy_glass"})for(int mount=0;mount<4;mount++)for(int hinge=0;hinge<4;hinge++)for(int variant=0;variant<3;variant++){
            BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:"+kind);EnumFacing facing=EnumFacing.HORIZONTALS[hinge];
            String detail=kind+" mount="+mount+" hinge="+hinge+" variant="+variant;
            for(BlockPos cell:BlockPos.getAllInBox(BASE.add(-12,-12,-12),BASE.add(12,12,12))){world.getChunkFromBlockCoords(cell);world.setBlockToAir(cell);}
            IBlockState state=block.getDefaultState().withProperty(BlockCanopy.FACING,facing);
            LargeCanopySettings settings=new LargeCanopySettings();settings.depth=32;settings.scale=40;settings.mount=mount;settings.hinge=hinge;settings.openable=true;
            ItemStack stack=new ItemStack(block);NBTTagCompound n=new NBTTagCompound();settings.write(n);stack.setTagCompound(n);
            world.setBlockState(BASE,state,2);block.onBlockPlacedBy(world,BASE,state,null,stack);TileEntityCanopy owner=TileEntityCanopy.at(world,BASE);
            List<BlockPos> footprint=new ArrayList<>(owner.cells());AxisAlignedBB closed=owner.selectionBounds();owner.progress=owner.previous=1;owner.targetOpen=true;List<AxisAlignedBB> opened=new ArrayList<>(owner.boxes());AxisAlignedBB swept=closed.union(owner.selectionBounds());
            BlockPos marker=BASE.up(12);world.setBlockState(marker,Blocks.GOLD_BLOCK.getDefaultState(),2);
            // Reproduce older copied/resized roots whose reservation list omitted still-linked helpers.
            if(variant==2)owner.setFootprint(Collections.singletonList(BASE));
            BlockPos target=variant==1?footprint.stream().filter(p->!p.equals(BASE)).findFirst().get():BASE;
            require(player.interactionManager.tryHarvestBlock(target),"actual harvest "+detail);
            for(BlockPos cell:footprint){IBlockState remaining=world.getBlockState(cell);require(world.isAirBlock(cell) && remaining.getBlock().isReplaceable(world,cell) && world.mayPlace(Blocks.STONE,cell,true,EnumFacing.UP,player) && TileEntityCanopy.at(world,cell)==null,"no invisible reservation "+detail+" cell="+cell+" remaining="+remaining+" tile="+world.getTileEntity(cell));}
            require(CanopyInteractions.owners(world,swept).isEmpty(),"no drawing/collision owner "+detail);
            require(world.getCollisionBoxes(null,swept.shrink(.00001)).isEmpty(),"closed and opened region walkable "+detail+" collisions="+world.getCollisionBoxes(null,swept.shrink(.00001)));
            for(AxisAlignedBB surface:opened)require(world.getCollisionBoxes(null,surface.shrink(.000001)).isEmpty(),"cached open surface removed "+detail);
            require(world.getBlockState(marker).getBlock()==Blocks.GOLD_BLOCK,"foreign block preserved "+detail);world.setBlockToAir(marker);cases++;
        }
        rampOverlap(world,player);orphans(world,player);
        System.out.println("[vandorlabs][reprolab] opened-front-removal PASS cases="+cases+" widths=3,5 mounts=4 hinges=4 root-follower stale-footprint no-reservations no-collision replaceable ramp-overlap orphan-repair");
    }
    private static void rampOverlap(World world,EntityPlayerMP player){
        BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:large_front_canopy_glass");
        IBlockState state=block.getDefaultState();LargeCanopySettings settings=new LargeCanopySettings();settings.depth=64;settings.openable=true;settings.hinge=0;
        ItemStack stack=new ItemStack(block);NBTTagCompound n=new NBTTagCompound();settings.write(n);stack.setTagCompound(n);world.setBlockState(BASE,state,3);block.onBlockPlacedBy(world,BASE,state,null,stack);TileEntityCanopy owner=TileEntityCanopy.at(world,BASE);
        List<BlockPos> footprint=new ArrayList<>(owner.cells());owner.progress=owner.previous=1;owner.targetOpen=true;List<AxisAlignedBB> open=new ArrayList<>(owner.boxes());
        BlockPos source=null,controllerPos=null,power=null;
        for(AxisAlignedBB box:open){BlockPos target=new BlockPos(box.getCenter()),candidate=target.up(),control=candidate.west(),signal=control.west();
            if(footprint.contains(target)||footprint.contains(candidate)||footprint.contains(control)||footprint.contains(signal))continue;
            if(world.isAirBlock(target)&&world.isAirBlock(candidate)&&world.isAirBlock(control)&&world.isAirBlock(signal)){source=candidate;controllerPos=control;power=signal;break;}}
        require(source!=null,"open leaf has a free overlapping ramp fixture cell");
        world.setBlockState(controllerPos,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.EAST),3);TileEntityRampController controller=(TileEntityRampController)world.getTileEntity(controllerPos);
        Vec3d oldPlayer=player.getPositionVector();
        try{
        player.setPosition(controllerPos.getX()-2,controllerPos.getY()+1,controllerPos.getZ()+.5);
        require(controller.usable(player),"overlap ramp configuration is in reach controller="+controllerPos+" player="+player.getPositionVector());
        world.setBlockState(source,Blocks.STONE.getDefaultState(),3);require(controller.configure(player,1,2,true,true,false,true),"overlap ramp configured: status="+controller.status+" source="+source+" controller="+controllerPos+" player="+player.getPositionVector());world.setBlockState(power,Blocks.REDSTONE_BLOCK.getDefaultState(),3);controller.updatePower();require(controller.attached()&&!controller.error,"overlap ramp deployed: "+controller.status);ControllerRuntimeChecks.elapsed(controller,controller.durationTicks()+1);
        Map<BlockPos,List<AxisAlignedBB>> expected=new LinkedHashMap<>();for(BlockPos cell:controller.vehicleCells()){TileEntityControlledRamp part=(TileEntityControlledRamp)world.getTileEntity(cell);expected.put(cell,new ArrayList<>(part.boxes(0)));}
        require(expected.entrySet().stream().anyMatch(entry->entry.getValue().stream().anyMatch(box->open.stream().anyMatch(shell->shell.intersects(box.offset(entry.getKey()))))),"deployed ramp physically overlaps opened leaf");
        require(player.interactionManager.tryHarvestBlock(BASE),"harvest opened canopy around deployed ramp");controller.updatePower();
        require(!controller.error && controller.attached() && controller.isOpen(),"canopy neighbor updates preserve deployed ramp: "+controller.status);
        for(Map.Entry<BlockPos,List<AxisAlignedBB>> entry:expected.entrySet()){net.minecraft.tileentity.TileEntity raw=world.getTileEntity(entry.getKey());require(world.getBlockState(entry.getKey()).getBlock()==ModBlocks.CONTROLLED_RAMP && raw instanceof TileEntityControlledRamp && controller.owns((TileEntityControlledRamp)raw),"foreign ramp reservation preserved "+entry.getKey()+" state="+world.getBlockState(entry.getKey())+" tile="+raw);require(entry.getValue().toString().equals(((TileEntityControlledRamp)raw).boxes(0).toString()),"ramp collision unchanged "+entry.getKey());}
        for(BlockPos cell:footprint)require(world.isAirBlock(cell),"canopy footprint clear beside ramp "+cell+" state="+world.getBlockState(cell));
        require(controller.recover(true),"overlap ramp restores originals");for(BlockPos cell:expected.keySet())require(world.getBlockState(cell).getBlock()!=ModBlocks.CONTROLLED_RAMP,"ramp recovery leaves no invisible reservations "+cell);world.setBlockToAir(source);world.setBlockToAir(controllerPos);world.setBlockToAir(power);
        }finally{player.setPosition(oldPlayer.x,oldPlayer.y,oldPlayer.z);}
    }
    private static void orphans(World world,EntityPlayerMP player){
        BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:large_front_canopy_glass");BlockPos stale=BASE.east(8),missing=stale.west(2);world.setBlockState(stale,block.getDefaultState(),2);TileEntityCanopy orphan=TileEntityCanopy.at(world,stale);orphan.anchor=missing;
        RayTraceResult hit=block.collisionRayTrace(world.getBlockState(stale),world,stale,new Vec3d(stale).addVector(.5,2,.5),new Vec3d(stale).addVector(.5,-1,.5));require(hit!=null && hit.getBlockPos().equals(stale),"old invisible orphan is targetable");require(player.interactionManager.tryHarvestBlock(hit.getBlockPos())&&world.isAirBlock(stale),"old orphan can be harvested");
        world.setBlockState(stale,block.getDefaultState(),2);orphan=TileEntityCanopy.at(world,stale);orphan.anchor=missing;long time=world.getTotalWorldTime();try{world.setTotalWorldTime((time/20+1)*20);orphan.update();require(world.isAirBlock(stale),"loaded orphan auto-repairs within twenty ticks");}finally{world.setTotalWorldTime(time);}
    }
    private OpenedFrontCanopyRemovalChecks(){}
}
