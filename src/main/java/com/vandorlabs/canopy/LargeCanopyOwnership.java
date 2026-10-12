package com.vandorlabs.canopy;
import java.util.*;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
/** Validate the complete new footprint before changing any blocks. */
public final class LargeCanopyOwnership {
    public static List<BlockPos> itemCells(BlockCanopy block,BlockPos pos,EnumFacing facing,ItemStack stack){
        LargeCanopySettings settings=new LargeCanopySettings();if(stack.hasTagCompound())settings.read(stack.getTagCompound());
        int depth=LargeCanopySettings.supports(block.kind)?settings.length():block.length;
        // New two-wide Visors are centered within three reserved cells; old tile NBT keeps its own footprint.
        int occupiedWidth=block.kind.equals("space_visor_2x1")?3:block.width;
        return block.cells(pos,facing,depth,occupiedWidth);
    }
    public static boolean fitsItem(BlockCanopy block,World world,BlockPos pos,EnumFacing facing,ItemStack stack){for(BlockPos cell:itemCells(block,pos,facing,stack))if(!available(block,world,cell))return false;return true;}
    private static boolean available(BlockCanopy block,World world,BlockPos cell){return cell.getY()>=0 && cell.getY()<world.getHeight() && world.getWorldBorder().contains(cell) && world.isBlockLoaded(cell) && (world.getBlockState(cell).getBlock().isReplaceable(world,cell) || block.sharesSeatBackrest(world.getBlockState(cell)));}
    public static boolean apply(TileEntityCanopy tile,EntityPlayer player,int depth,int scale,int shade,int tint){
        if(!tile.configurable() || !tile.draws() || !LargeCanopySettings.valid(depth,scale,shade,tint))return false;
        World world=tile.getWorld();BlockCanopy block=tile.block();Set<BlockPos> old=new HashSet<>(tile.cells());List<BlockPos> next=block.cells(tile.getPos(),tile.facing(),Math.max(1,(depth+15)/16));
        for(BlockPos cell:old){TileEntityCanopy member=TileEntityCanopy.at(world,cell);if(!world.isBlockLoaded(cell) || world.getBlockState(cell).getBlock()!=block || member==null || !tile.getPos().equals(member.anchor) || !world.isBlockModifiable(player,cell) || !player.canPlayerEdit(cell,EnumFacing.UP,player.getHeldItemMainhand()))return false;}
        for(BlockPos cell:next)if(!world.isBlockLoaded(cell) || !world.isBlockModifiable(player,cell) || !player.canPlayerEdit(cell,EnumFacing.UP,player.getHeldItemMainhand()) || !old.contains(cell) && !available(block,world,cell))return false;
        Set<BlockPos> affected=new HashSet<>(old);affected.addAll(next);
        Map<BlockPos,net.minecraft.block.state.IBlockState> before=new LinkedHashMap<>();Map<BlockPos,net.minecraft.nbt.NBTTagCompound> data=new HashMap<>();
        for(BlockPos cell:affected){before.put(cell,world.getBlockState(cell));net.minecraft.tileentity.TileEntity member=world.getTileEntity(cell);if(member!=null)data.put(cell,member.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));}
        tile.removing=true;
        try{
            for(BlockPos cell:next){if(!old.contains(cell) && !world.setBlockState(cell,world.getBlockState(tile.getPos()),2))throw new IllegalStateException("Canopy expansion was refused");TileEntityCanopy member=TileEntityCanopy.at(world,cell);if(member==null)throw new IllegalStateException("Missing canopy footprint member");member.anchor=tile.getPos();}
            for(BlockPos cell:old)if(!next.contains(cell)){TileEntityCanopy member=TileEntityCanopy.at(world,cell);member.removing=true;if(!world.setBlockToAir(cell))throw new IllegalStateException("Canopy contraction was refused");}
            tile.large.depth=depth;tile.large.scale=scale;tile.large.shade=shade;tile.large.tint=tint;net.minecraft.nbt.NBTTagCompound settings=new net.minecraft.nbt.NBTTagCompound();tile.large.write(settings);
            for(BlockPos cell:next){TileEntityCanopy member=TileEntityCanopy.at(world,cell);member.large.read(settings);member.sync();}
            tile.sync();world.notifyNeighborsOfStateChange(tile.getPos(),block,false);return true;
        }catch(RuntimeException failure){
            for(BlockPos cell:affected){TileEntityCanopy member=TileEntityCanopy.at(world,cell);if(member!=null)member.removing=true;}
            for(Map.Entry<BlockPos,net.minecraft.block.state.IBlockState> entry:before.entrySet()){BlockPos cell=entry.getKey();world.setBlockState(cell,entry.getValue(),2);net.minecraft.tileentity.TileEntity member=world.getTileEntity(cell);if(member!=null && data.containsKey(cell)){member.readFromNBT(data.get(cell));member.markDirty();if(member instanceof TileEntityCanopy){((TileEntityCanopy)member).removing=false;((TileEntityCanopy)member).sync();}}}
            com.vandorlabs.VandorLabs.logger.warn("Canopy resize was rolled back",failure);return false;
        }finally{tile.removing=false;}
    }
    private LargeCanopyOwnership(){}
}
