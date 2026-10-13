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
        return LargeCanopySettings.supports(block.kind)?shellCells(block,pos,facing,settings):block.cells(pos,facing,depth,occupiedWidth);
    }
    /** Reserve only surface cells so hollow space remains available to other blocks. */
    public static List<BlockPos> shellCells(BlockCanopy block,BlockPos pos,EnumFacing facing,LargeCanopySettings settings){return shellCells(block,pos,facing,settings,true);}
    public static List<BlockPos> shellCells(BlockCanopy block,BlockPos pos,EnumFacing facing,LargeCanopySettings settings,boolean backAnchored){
        List<net.minecraft.util.math.AxisAlignedBB> surfaces=CanopyMesh.collision(LargeCanopyGeometry.faces(block.width,settings,backAnchored),block.kind,block.width,0,facing,pos);
        List<BlockPos> candidates=block.cells(pos,facing,settings.length());Set<BlockPos> allowed=new HashSet<>(candidates),occupied=new HashSet<>();occupied.add(pos);
        for(net.minecraft.util.math.AxisAlignedBB surface:surfaces){
            BlockPos min=new BlockPos(surface.minX,surface.minY,surface.minZ),max=new BlockPos(surface.maxX,surface.maxY,surface.maxZ);
            for(BlockPos sample:BlockPos.getAllInBox(min,max))if(allowed.contains(sample) && surface.intersects(new net.minecraft.util.math.AxisAlignedBB(sample).shrink(.00001)))occupied.add(sample.toImmutable());
        }
        List<BlockPos> result=new ArrayList<>();for(BlockPos cell:candidates)if(occupied.contains(cell))result.add(cell);
        return result;
    }
    public static boolean fitsItem(BlockCanopy block,World world,BlockPos pos,EnumFacing facing,ItemStack stack){for(BlockPos cell:itemCells(block,pos,facing,stack))if(!available(block,world,cell))return false;return true;}
    private static boolean available(BlockCanopy block,World world,BlockPos cell){return cell.getY()>=0 && cell.getY()<world.getHeight() && world.getWorldBorder().contains(cell) && world.isBlockLoaded(cell) && (world.getBlockState(cell).getBlock().isReplaceable(world,cell) || block.sharesSeatBackrest(world.getBlockState(cell)));}
    public static boolean apply(TileEntityCanopy tile,EntityPlayer player,int depth,int scale,int shade,int tint){
        if(!tile.configurable() || !tile.draws() || !LargeCanopySettings.valid(depth,scale,shade,tint))return false;
        World world=tile.getWorld();BlockCanopy block=tile.block();
        if(!tile.resizable()){
            List<TileEntityCanopy> members=new ArrayList<>();
            for(int column=0;column<tile.width()/block.width;column++){
                TileEntityCanopy constituent=TileEntityCanopy.at(world,tile.getPos().offset(tile.facing().rotateY(),column*block.width));
                if(constituent==null || constituent.owner()!=tile)return false;
                for(BlockPos cell:constituent.cells()){
                    TileEntityCanopy member=TileEntityCanopy.at(world,cell);
                    if(member==null || member.owner()!=tile || !world.isBlockModifiable(player,cell) || !player.canPlayerEdit(cell,EnumFacing.UP,player.getHeldItemMainhand()))return false;
                    members.add(member);
                }
            }
            for(TileEntityCanopy member:members){member.large.shade=shade;member.large.tint=tint;member.sync();}
            return true;
        }
        LargeCanopySettings nextSettings=new LargeCanopySettings();nextSettings.depth=depth;nextSettings.scale=scale;nextSettings.shade=shade;nextSettings.tint=tint;
        Set<BlockPos> old=new HashSet<>(tile.cells());List<BlockPos> next=shellCells(block,tile.getPos(),tile.facing(),nextSettings,tile.centeredPlacement);
        for(BlockPos cell:old){TileEntityCanopy member=TileEntityCanopy.at(world,cell);if(!world.isBlockLoaded(cell) || world.getBlockState(cell).getBlock()!=block || member==null || !tile.getPos().equals(member.anchor) || !world.isBlockModifiable(player,cell) || !player.canPlayerEdit(cell,EnumFacing.UP,player.getHeldItemMainhand()))return false;}
        for(BlockPos cell:next)if(!world.isBlockLoaded(cell) || !world.isBlockModifiable(player,cell) || !player.canPlayerEdit(cell,EnumFacing.UP,player.getHeldItemMainhand()) || !old.contains(cell) && !available(block,world,cell))return false;
        Set<BlockPos> affected=new HashSet<>(old);affected.addAll(next);
        Map<BlockPos,net.minecraft.block.state.IBlockState> before=new LinkedHashMap<>();Map<BlockPos,net.minecraft.nbt.NBTTagCompound> data=new HashMap<>();
        for(BlockPos cell:affected){before.put(cell,world.getBlockState(cell));net.minecraft.tileentity.TileEntity member=world.getTileEntity(cell);if(member!=null)data.put(cell,member.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));}
        tile.removing=true;
        try{
            for(BlockPos cell:next){if(!old.contains(cell) && !world.setBlockState(cell,world.getBlockState(tile.getPos()),2))throw new IllegalStateException("Canopy expansion was refused");TileEntityCanopy member=TileEntityCanopy.at(world,cell);if(member==null)throw new IllegalStateException("Missing canopy footprint member");member.anchor=tile.getPos();member.centeredPlacement=tile.centeredPlacement;}
            for(BlockPos cell:old)if(!next.contains(cell)){TileEntityCanopy member=TileEntityCanopy.at(world,cell);member.removing=true;if(!world.setBlockToAir(cell))throw new IllegalStateException("Canopy contraction was refused");}
            tile.large.depth=depth;tile.large.scale=scale;tile.large.shade=shade;tile.large.tint=tint;net.minecraft.nbt.NBTTagCompound settings=new net.minecraft.nbt.NBTTagCompound();tile.large.write(settings);
            for(BlockPos cell:next){TileEntityCanopy member=TileEntityCanopy.at(world,cell);member.large.read(settings);member.hollowPlacement=true;member.sync();}
            tile.sync();world.notifyNeighborsOfStateChange(tile.getPos(),block,false);return true;
        }catch(RuntimeException failure){
            for(BlockPos cell:affected){TileEntityCanopy member=TileEntityCanopy.at(world,cell);if(member!=null)member.removing=true;}
            for(Map.Entry<BlockPos,net.minecraft.block.state.IBlockState> entry:before.entrySet()){BlockPos cell=entry.getKey();world.setBlockState(cell,entry.getValue(),2);net.minecraft.tileentity.TileEntity member=world.getTileEntity(cell);if(member!=null && data.containsKey(cell)){member.readFromNBT(data.get(cell));member.markDirty();if(member instanceof TileEntityCanopy){((TileEntityCanopy)member).removing=false;((TileEntityCanopy)member).sync();}}}
            com.vandorlabs.VandorLabs.logger.warn("Canopy resize was rolled back",failure);return false;
        }finally{tile.removing=false;}
    }
    private LargeCanopyOwnership(){}
}
