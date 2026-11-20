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
    /**
     * A section of an assembled, retracted Extender that its current geometry does not reach.
     * A Large Door may take its place; the arm keeps the column through the door.
     */
    static boolean hiddenSection(World world,BlockPos cell){
        IBlockState state=world.getBlockState(cell);
        if(!(state.getBlock() instanceof com.vandorlabs.gear.GearBlock)||!com.vandorlabs.gear.GearAssembly.isExtender(state)||com.vandorlabs.gear.GearAssembly.isSwingArm(state))return false;
        net.minecraft.tileentity.TileEntity raw=world.getTileEntity(cell);
        if(!(raw instanceof com.vandorlabs.gear.GearTileEntity))return false;
        com.vandorlabs.gear.GearTileEntity arm=((com.vandorlabs.gear.GearTileEntity)raw).loadedController();
        if(arm==null||!arm.isOperational()||com.vandorlabs.gear.ArmTransactions.locked(world,arm.getPos())||arm.progress()!=0&&arm.progress()!=1)return false;
        for(BlockPos column:arm.columns())if(arm.getPos().add(column).equals(cell))return false;
        return arm.members().contains(cell)&&!arm.occupiesNow(cell);
    }
    @Override public EnumActionResult onItemUse(EntityPlayer player,World world,BlockPos clicked,EnumHand hand,EnumFacing side,float hitX,float hitY,float hitZ){
        ItemStack stack=player.getHeldItem(hand);if(stack.isEmpty() || !world.isBlockLoaded(clicked))return EnumActionResult.FAIL;
        BlockLargeProgrammableDoor door=(BlockLargeProgrammableDoor)block;
        // Clicking the edge of an installed door places the next module beside it and joins them.
        TileEntityLargeProgrammableDoor edge=world.getBlockState(clicked).getBlock()==door?door.root(world,clicked):null;
        if(edge!=null&&door.complete(world,edge)){
            EnumFacing front=world.getBlockState(edge.getPos()).getValue(BlockVandorDoor.FACING);
            EnumFacing across=front.rotateYCCW(),along=edge.isHorizontal()?front:EnumFacing.UP;
            if(side.getAxis()!=across.getAxis()&&side.getAxis()!=along.getAxis())edge=null;
        }else edge=null;
        BlockPos center=edge!=null?null:world.getBlockState(clicked).getBlock().isReplaceable(world,clicked)?clicked:clicked.offset(side);
        boolean horizontal=edge!=null?edge.isHorizontal():side.getAxis().isHorizontal()&&!center.equals(clicked);
        EnumFacing face=edge!=null?world.getBlockState(edge.getPos()).getValue(BlockVandorDoor.FACING):horizontal?side:player.getHorizontalFacing().getOpposite(),width=face.rotateYCCW();
        EnumFacing row=horizontal?face:EnumFacing.UP;
        BlockPos anchor=edge!=null?edge.getPos().offset(side,com.vandorlabs.render.LargeDoorJoin.PITCH):center.offset(width,-1);
        if(center==null)center=anchor.offset(width);
        if(anchor.getY()<0 || anchor.offset(row,2).getY()>=world.getHeight())return EnumActionResult.FAIL;
        if(!door.supports(world,anchor,face,horizontal,edge==null))return EnumActionResult.FAIL;
        BlockPos preceding=anchor.offset(row.getOpposite(),3);
        TileEntityLargeProgrammableDoor base=edge!=null?edge:world.isBlockLoaded(preceding,false)?door.root(world,preceding):null;
        if(edge==null&&(base==null||!base.getPos().equals(preceding)||!base.joins())){
            base=null;
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){
                if(Math.abs(dx)+Math.abs(dy)!=1)continue;
                BlockPos at=anchor.offset(width,3*dx).offset(row,3*dy);
                TileEntityLargeProgrammableDoor candidate=world.isBlockLoaded(at,false)?door.root(world,at):null;
                if(candidate!=null&&candidate.getPos().equals(at)&&candidate.joins()&&candidate.isHorizontal()==horizontal
                        &&world.getBlockState(at).getValue(BlockVandorDoor.FACING)==face&&door.complete(world,candidate))base=candidate;
            }
        }
        boolean joining=base!=null&&(base.joins()||base==edge);
        net.minecraft.nbt.NBTTagCompound settings=stack.getSubCompound("SpaceDoorSettings");
        if((joining||settings!=null&&settings.getBoolean(com.vandorlabs.render.LargeDoorJoin.TAG))
                &&!door.mayEditJoinedNeighbors(world,anchor,face,horizontal,player,stack))return EnumActionResult.FAIL;
        List<BlockPos> cells=new ArrayList<>();List<IBlockState> old=new ArrayList<>();
        for(int x=0;x<3;x++){
            for(int y=0;y<3;y++){
                BlockPos cell=anchor.offset(width,x).offset(row,y);
                if(!world.isBlockLoaded(cell) || !world.getBlockState(cell).getBlock().isReplaceable(world,cell)&&!hiddenSection(world,cell) || !player.canPlayerEdit(cell,side,stack) || !world.isBlockModifiable(player,cell))return EnumActionResult.FAIL;
                cells.add(cell);old.add(world.getBlockState(cell));
            }
        }
        IBlockState state=block.getDefaultState().withProperty(BlockVandorDoor.FACING,face);
        int placed=0;
        List<net.minecraft.nbt.NBTTagCompound> displaced=new ArrayList<>();Set<com.vandorlabs.gear.GearTileEntity> arms=new HashSet<>();
        for(BlockPos cell:cells){
            net.minecraft.nbt.NBTTagCompound section=null;
            if(!world.isRemote&&hiddenSection(world,cell)){
                section=new net.minecraft.nbt.NBTTagCompound();IBlockState sectionState=world.getBlockState(cell);
                section.setString("Block",sectionState.getBlock().getRegistryName().toString());section.setInteger("Meta",sectionState.getBlock().getMetaFromState(sectionState));
                section.setTag("Tile",world.getTileEntity(cell).writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
                arms.add(((com.vandorlabs.gear.GearTileEntity)world.getTileEntity(cell)).loadedController());
            }
            displaced.add(section);
            boolean set;
            com.vandorlabs.gear.GearBlock.DISPLACING.set(section!=null);
            try{set=world.setBlockState(cell,state,2);}finally{com.vandorlabs.gear.GearBlock.DISPLACING.remove();}
            if(!set){
                // Roll back only cells this placement installed; no partial assembly remains.
                for(int i=0;i<placed;i++){world.setBlockState(cells.get(i),old.get(i),2);if(displaced.get(i)!=null)BlockLargeProgrammableDoor.restoreSection(world,cells.get(i),displaced.get(i));}
                return EnumActionResult.FAIL;
            }
            placed++;TileEntityLargeProgrammableDoor part=(TileEntityLargeProgrammableDoor)world.getTileEntity(cell);part.assign(anchor,horizontal);if(section!=null)part.setDisplacedSection(section);
        }
        for(com.vandorlabs.gear.GearTileEntity arm:arms)if(arm!=null)arm.scheduleRebuild();
        TileEntityLargeProgrammableDoor tile=(TileEntityLargeProgrammableDoor)world.getTileEntity(anchor);
        if(edge!=null)tile.applyItemSettings(edge.itemSettings());
        else if(settings!=null)tile.applyItemSettings(settings);
        else if(joining)tile.applyItemSettings(base.itemSettings());
        tile.setPlacementDepth(edge!=null||joining&&settings==null?base.getPlacementDepth():horizontal?com.vandorlabs.render.PanelDepthPolicy.fromHit(hitY):PanelDepth.fromHit(face,hitX,hitZ));
        if(joining)tile.setJoin(true);
        if(edge!=null){tile.setJoin(true);if(!edge.joins())edge.setJoin(true);}
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
