package com.vandorlabs.items;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityLargeProgrammableDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** The clicked position is the bottom center of the opening. Validate before writing. */
public final class ItemLargeProgrammableDoor extends ItemBlock {
    public ItemLargeProgrammableDoor(BlockLargeProgrammableDoor block){super(block);}
    @Override public EnumActionResult onItemUse(EntityPlayer player,World world,BlockPos clicked,EnumHand hand,EnumFacing side,float hitX,float hitY,float hitZ){
        ItemStack stack=player.getHeldItem(hand);if(stack.isEmpty() || !world.isBlockLoaded(clicked))return EnumActionResult.FAIL;
        BlockPos center=world.getBlockState(clicked).getBlock().isReplaceable(world,clicked)?clicked:clicked.offset(side);
        boolean horizontal=side.getAxis().isHorizontal()&&!center.equals(clicked);
        EnumFacing face=horizontal?side:player.getHorizontalFacing().getOpposite(),width=face.rotateYCCW();
        EnumFacing row=horizontal?face:EnumFacing.UP;
        BlockPos anchor=center.offset(width,-1);
        if(anchor.getY()<0 || anchor.offset(row,2).getY()>=world.getHeight())return EnumActionResult.FAIL;
        BlockLargeProgrammableDoor door=(BlockLargeProgrammableDoor)block;
        if(!door.supports(world,anchor,face,horizontal,true))return EnumActionResult.FAIL;
        BlockPos preceding=anchor.offset(row.getOpposite(),3);
        TileEntityLargeProgrammableDoor base=world.isBlockLoaded(preceding,false)?door.root(world,preceding):null;
        boolean joining=base!=null&&base.getPos().equals(preceding)&&base.joins();
        List<BlockPos> cells=new ArrayList<>();List<IBlockState> old=new ArrayList<>();
        for(int x=0;x<3;x++){
            for(int y=0;y<3;y++){
                BlockPos cell=anchor.offset(width,x).offset(row,y);
                if(!world.isBlockLoaded(cell) || !world.getBlockState(cell).getBlock().isReplaceable(world,cell) || !player.canPlayerEdit(cell,side,stack) || !world.isBlockModifiable(player,cell))return EnumActionResult.FAIL;
                cells.add(cell);old.add(world.getBlockState(cell));
            }
        }
        IBlockState state=block.getDefaultState().withProperty(BlockVandorDoor.FACING,face);
        int placed=0;
        for(BlockPos cell:cells){
            if(!world.setBlockState(cell,state,2)){
                // Roll back only cells this placement installed; no partial assembly remains.
                for(int i=0;i<placed;i++)world.setBlockState(cells.get(i),old.get(i),2);
                return EnumActionResult.FAIL;
            }
            placed++;((TileEntityLargeProgrammableDoor)world.getTileEntity(cell)).assign(anchor,horizontal);
        }
        TileEntityLargeProgrammableDoor tile=(TileEntityLargeProgrammableDoor)world.getTileEntity(anchor);
        if(stack.getSubCompound("SpaceDoorSettings")!=null)tile.applyItemSettings(stack.getSubCompound("SpaceDoorSettings"));
        tile.setPlacementDepth(horizontal?com.vandorlabs.render.PanelDepthPolicy.fromHit(hitY):PanelDepth.fromHit(face,hitX,hitZ));
        if(joining)tile.setJoin(true);
        // Only now may neighbor callbacks observe the complete structure.
        for(BlockPos cell:cells){world.notifyBlockUpdate(cell,state,state,2);world.notifyNeighborsOfStateChange(cell,block,false);}
        if(!world.isRemote){
            if(tile.joins())door.joinChanged(world,tile);
            tile.onLoad();
        }
        world.playSound(player,center,block.getSoundType().getPlaceSound(),SoundCategory.BLOCKS,1,0.8F);
        stack.shrink(1);return EnumActionResult.SUCCESS;
    }
}
