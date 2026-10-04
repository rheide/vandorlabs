package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockProgrammableTrapdoor;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Same event-driven controls, with groups laid out across the diagonal surface. */
public final class TileEntityProgrammableDiagonalTrapdoor extends TileEntityProgrammableTrapdoor {
    private boolean reverse,cachedInverted;
    private EnumFacing cachedFacing=EnumFacing.NORTH,groupFacing=EnumFacing.NORTH;
    public boolean isReverse(){return reverse;}
    private TileEntityProgrammableDiagonalTrapdoor outsideReference(List<TileEntityProgrammableTrapdoor> leaves) {
        TileEntityProgrammableDiagonalTrapdoor reference=this;
        for(TileEntityProgrammableTrapdoor leaf:leaves)if(leaf.getPos().compareTo(reference.getPos())<0)reference=(TileEntityProgrammableDiagonalTrapdoor)leaf;
        return reference;
    }
    private EnumFacing outsideFacing(List<TileEntityProgrammableTrapdoor> leaves) {
        TileEntityProgrammableDiagonalTrapdoor reference=outsideReference(leaves);
        boolean bent=false;
        for(TileEntityProgrammableTrapdoor raw:leaves) {
            TileEntityProgrammableDiagonalTrapdoor leaf=(TileEntityProgrammableDiagonalTrapdoor)raw;
            if(leaf.getPos().getY()>reference.getPos().getY() && (leaf.isInverted() ^ (leaf.facing()==reference.facing().getOpposite()))!=reference.isInverted())bent=true;
        }
        // A V assembly's outside is the convex side of its bend. A continued
        // plane retains the existing upward-opening side of its bottom row.
        return reference.isInverted() ^ bent?reference.facing().getOpposite():reference.facing();
    }
    /** Joined tall sliders clear the same outside face before separating sideways. */
    public int slideLiftDirection() {
        if(!sliding || position==2)return 1;
        return slideLiftDirection(group());
    }
    public int slideLiftDirection(List<TileEntityProgrammableTrapdoor> leaves) {
        if(!sliding || position==2 || leaves.size()<2)return 1;
        return facing()==outsideFacing(leaves)?-1:1;
    }
    /** Keep every rotating row outside the surface, including reversed coplanar rows. */
    public boolean rotationReverse() {
        if(sliding || position==2)return reverse;
        return rotationReverse(group());
    }
    public boolean rotationReverse(List<TileEntityProgrammableTrapdoor> leaves) {
        if(sliding || position==2 || leaves.size()<2)return reverse;
        boolean opposite=facing()==outsideFacing(leaves).getOpposite();
        return reverse ^ (isInverted()!=opposite);
    }

    public boolean isInverted(){
        if(world!=null && world.getBlockState(pos).getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableDiagonalTrapdoor)
            cachedInverted=world.getBlockState(pos).getValue(BlockTrapDoor.HALF)==BlockTrapDoor.DoorHalf.TOP;
        return cachedInverted;
    }
    public EnumFacing facing(){
        if(world!=null && world.getBlockState(pos).getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableDiagonalTrapdoor)
            cachedFacing=world.getBlockState(pos).getValue(BlockTrapDoor.FACING);
        return cachedFacing;
    }
    void setOpeningSide(boolean value){reverse=value;}
    @Override public double motionHinge(){return assembly.isEmpty()?(reverse?15/16D:1/16D):assemblyHinge;}
    private EnumFacing width(){return facing().rotateYCCW();}
    private EnumFacing along(){return position==2?facing().getOpposite():EnumFacing.UP;}
    @Override public boolean compatible(TileEntityProgrammableTrapdoor raw) {
        if(!(raw instanceof TileEntityProgrammableDiagonalTrapdoor) || position!=raw.getPosition())return false;
        TileEntityProgrammableDiagonalTrapdoor other=(TileEntityProgrammableDiagonalTrapdoor)raw;
        if(world==null || other.world==null)return true;
        if(facing().getAxis()==other.facing().getAxis() && (position==2
                ? com.vandorlabs.blocks.PanelPlane.axis(other.pos.subtract(pos),facing())!=0
                : pos.getY()!=other.pos.getY() || position==0 && com.vandorlabs.blocks.PanelPlane.axis(other.pos.subtract(pos),facing())!=0))return true;
        if(facing()==other.facing() && isInverted()==other.isInverted())return true;
        if(!(world.getBlockState(pos).getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableDiagonalTrapdoor)
                || !(world.getBlockState(other.pos).getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableDiagonalTrapdoor))return false;
        return com.vandorlabs.blocks.DiagonalPanelGeometry.samePlane(pos,com.vandorlabs.blocks.BlockProgrammableDiagonalTrapdoor.wallState(world.getBlockState(pos)),other.pos,com.vandorlabs.blocks.BlockProgrammableDiagonalTrapdoor.wallState(world.getBlockState(other.pos)),position);
    }
    @Override protected boolean groupCompatible(TileEntityProgrammableTrapdoor other) {
        return other instanceof TileEntityProgrammableDiagonalTrapdoor && position==other.getPosition();
    }
    @Override protected List<BlockPos> squareCells(BlockPos base) {
        EnumFacing across=squareOrigin==null?width():groupFacing.rotateYCCW();
        EnumFacing down=position==2?(squareOrigin==null?along():groupFacing.getOpposite()):EnumFacing.UP;
        return Arrays.asList(base,base.offset(across),base.offset(down),base.offset(across).offset(down));
    }
    @Override protected void sync() {
        // HALF is slope/band inversion here, independent of the three geometry modes.
        markDirty();if(!configuring && world!=null){net.minecraft.block.state.IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,2);}
    }
    public void setInverted(boolean inverted) {
        if(world==null)return;
        world.setBlockState(pos,world.getBlockState(pos).withProperty(BlockTrapDoor.HALF,inverted?BlockTrapDoor.DoorHalf.TOP:BlockTrapDoor.DoorHalf.BOTTOM),2);sync();evaluatePower(true);
    }
    @Override public void pairWith(TileEntityProgrammableTrapdoor raw) {
        if(!(raw instanceof TileEntityProgrammableDiagonalTrapdoor))return;
        TileEntityProgrammableDiagonalTrapdoor other=(TileEntityProgrammableDiagonalTrapdoor)raw;
        if(world==null || world.isRemote || other.world!=world || partner!=null || other.partner!=null || !compatible(other))return;
        EnumFacing direction=EnumFacing.getFacingFromVector(pos.getX()-other.pos.getX(),pos.getY()-other.pos.getY(),pos.getZ()-other.pos.getZ());
        if(pos.distanceSq(other.pos)!=1 || direction.getAxis()!=width().getAxis() && direction.getAxis()!=along().getAxis())return;
        partner=other.pos.toImmutable();other.partner=pos.toImmutable();
        reverse=direction!=width();other.reverse=direction.getOpposite()!=other.width();
        sliding=other.sliding;slideIntoWall=other.slideIntoWall;trigger=other.trigger;setRedstoneChannel(other.channel);
        requestOpen(world.getBlockState(other.pos).getValue(BlockTrapDoor.OPEN));sync();other.sync();evaluatePower(true);
    }
    @Override public void completeSquare(EntityPlayer player,ItemStack stack) {
        if(world==null || world.isRemote)return;
        repairLinks();if(TrapdoorAssemblies.complete(this,player,stack))return;
        if(squareOrigin!=null)return;
        for(int across=-1;across<=0;across++)for(int down=-1;down<=0;down++) {
            BlockPos base=pos.offset(width(),across).offset(along(),down);
            List<BlockPos> cells=squareCells(base);List<TileEntityProgrammableDiagonalTrapdoor> leaves=new ArrayList<>();
            for(BlockPos cell:cells) {
                if(!world.isBlockLoaded(cell))break;
                TileEntity raw=world.getTileEntity(cell);
                if(!(raw instanceof TileEntityProgrammableDiagonalTrapdoor))break;
                TileEntityProgrammableDiagonalTrapdoor leaf=(TileEntityProgrammableDiagonalTrapdoor)raw;leaf.repairLinks();
                if(!compatible(leaf) || leaf.squareOrigin!=null || leaf.partner!=null && !cells.contains(leaf.partner))break;
                if(player!=null && (!player.canPlayerEdit(cell,EnumFacing.UP,stack) || !world.isBlockModifiable(player,cell)))break;
                leaves.add(leaf);
            }
            if(leaves.size()!=4)continue;
            TileEntityProgrammableDiagonalTrapdoor reference=leaves.get(0);
            for(TileEntityProgrammableDiagonalTrapdoor leaf:leaves)if(leaf!=this && leaf.partner!=null){reference=leaf;break;}
            boolean motion=reference.sliding,open=world.getBlockState(reference.pos).getValue(BlockTrapDoor.OPEN);
            int linked=reference.channel,activation=reference.trigger;
            EnumFacing basis=facing();
            for(TileEntityProgrammableDiagonalTrapdoor leaf:leaves){leaf.groupFacing=basis;leaf.partner=null;leaf.sliding=motion;leaf.slideIntoWall=reference.slideIntoWall;leaf.trigger=activation;leaf.squareOrigin=base;leaf.setRedstoneChannel(linked);}
            leaves.get(1).pairWith(leaves.get(0));leaves.get(3).pairWith(leaves.get(2));
            requestOpen(open);for(TileEntityProgrammableDiagonalTrapdoor leaf:leaves)leaf.sync();evaluatePower(true);return;
        }
    }
    @Override public void configure(int texture,int mode,boolean sliding,int trigger,int channel) {
        if(valid(texture,mode,trigger,channel) && (position==2)!=(mode==2) && world!=null && !world.isRemote)unpair();
        super.configure(texture,mode,sliding,trigger,channel);
    }
    @Override protected void reconnectLoadedGroup(){if(position!=1)completeSquare(null,ItemStack.EMPTY);}
    @Override public NBTTagCompound itemSettings(){NBTTagCompound tag=super.itemSettings();tag.setBoolean("DiagonalHalfHeight",position==2);tag.setBoolean("DiagonalFullWidth",position==1);return tag;}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setBoolean("DiagonalReverse",reverse);tag.setInteger("DiagonalFacing",facing().getHorizontalIndex());tag.setBoolean("DiagonalInverted",isInverted());tag.setInteger("DiagonalGroupFacing",groupFacing.getHorizontalIndex());return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);reverse=tag.getBoolean("DiagonalReverse");cachedFacing=EnumFacing.getHorizontal(tag.getInteger("DiagonalFacing"));cachedInverted=tag.getBoolean("DiagonalInverted");groupFacing=EnumFacing.getHorizontal(tag.getInteger("DiagonalGroupFacing"));}
}
