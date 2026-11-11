package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockLargeProgrammableDoor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;

/** Nine cells address one configuration and one renderer, without loading neighbors. */
public class TileEntityLargeProgrammableDoor extends TileEntitySpaceDoor {
    public TileEntityLargeProgrammableDoor(){configure(DEFAULT_LARGE_DESIGN,1,true);}
    private int column,row;
    private boolean assigned;
    public void assign(BlockPos anchor){column=Math.abs(pos.getX()-anchor.getX())+Math.abs(pos.getZ()-anchor.getZ());row=pos.getY()-anchor.getY();assigned=true;markDirty();}
    public boolean isAnchor(){return assigned && column==0 && row==0;}
    public BlockPos anchorPos(){if(world==null||column==0&&row==0)return pos;
        net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
        if(!(state.getBlock() instanceof BlockLargeProgrammableDoor))return pos;
        return pos.offset(state.getValue(com.vandorlabs.blocks.BlockVandorDoor.FACING).rotateYCCW(),-column).down(row);}
    @Override protected boolean isLowerDoor(){return isAnchor() && world!=null && world.getBlockState(pos).getBlock() instanceof BlockLargeProgrammableDoor;}
    @Override public BlockPos mate(){return null;}
    @Override public int localSignalLevel(int channel){
        if(!isLowerDoor())return 0;
        net.minecraft.util.EnumFacing width=world.getBlockState(pos).getValue(com.vandorlabs.blocks.BlockVandorDoor.FACING).rotateYCCW();
        int level=0;for(int x=0;x<3;x++)for(int y=0;y<3;y++)level=Math.max(level,com.vandorlabs.redstone.LoadedRedstonePower.level(world,pos.offset(width,x).up(y)));
        return level;
    }
    @Override public boolean hasLocalRedstoneSignal(){
        if(!isLowerDoor())return false;
        net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
        net.minecraft.util.EnumFacing width=state.getValue(com.vandorlabs.blocks.BlockVandorDoor.FACING).rotateYCCW();
        for(int x=0;x<3;x++)for(int y=0;y<3;y++)
            if(com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world,pos.offset(width,x).up(y)))return true;
        return false;
    }
    @Override public boolean usable(net.minecraft.entity.player.EntityPlayer player){
        if(!isAnchor() || !super.usable(player))return false;
        net.minecraft.util.EnumFacing width=world.getBlockState(pos).getValue(com.vandorlabs.blocks.BlockVandorDoor.FACING).rotateYCCW();
        for(int x=0;x<3;x++)for(int y=0;y<3;y++){
            BlockPos cell=pos.offset(width,x).up(y);
            if(!world.isBlockLoaded(cell) || !player.canPlayerEdit(cell,net.minecraft.util.EnumFacing.UP,player.getHeldItemMainhand()) || !world.isBlockModifiable(player,cell))return false;
        }
        return true;
    }
    private int collisionKey=-1;
    private BlockPos collisionPos;
    private java.util.List<AxisAlignedBB> collisionBoxes;
    private java.util.List<java.util.List<AxisAlignedBB>> collisionCells;
    private com.vandorlabs.blocks.DoorCollisionIndex collisionIndex;
    /** Reuse immutable transformed boxes across the assembly's nine cell queries. */
    public java.util.List<AxisAlignedBB> collisionGeometry(net.minecraft.util.EnumFacing face,boolean open) {
        boolean sliding=isSliding();
        int key=(isFramed()?1:0)|(sliding?2:0)|(getSlideDirection()<<2)|(open?16:0)
                |(getPlacementDepth()<<5)|(hasHinges()?128:0)|(hasPanel()?256:0)|(face.getHorizontalIndex()<<9)|(panelMode()<<12)|(securityMode()<<14);
        if(collisionBoxes!=null && collisionKey==key && pos.equals(collisionPos))return collisionBoxes;
        java.util.List<AxisAlignedBB> boxes=new java.util.ArrayList<>();
        for(com.vandorlabs.render.LargeDoorGeometry.Box b:com.vandorlabs.render.LargeDoorGeometry.boxes(isFramed(),sliding,getSlideDirection(),open,positionOffset(),face.getHorizontalIndex(),hasPanel(),getPlacementDepth()==2))
            boxes.add(new AxisAlignedBB(b.x0,b.y0,b.z0,b.x1,b.y1,b.z1).offset(pos));
        if(hasPanel()&&!boxes.isEmpty()){
            AxisAlignedBB pad=boxes.remove(boxes.size()-1).offset(-pos.getX(),-pos.getY(),-pos.getZ());
            if(securityMode()>0){pad=pad.grow(0,1.05/16,0);pad=face.getAxis()==net.minecraft.util.EnumFacing.Axis.X?pad.grow(.75/16,0,0):pad.grow(0,0,.75/16);}
            boxes.add(pad.offset(pos));if(panelMode()==2)boxes.add(com.vandorlabs.blocks.BlockConfigurableSpaceDoor.mirrorPanel(pad,face).offset(pos));
        }
        collisionKey=key;collisionPos=pos.toImmutable();collisionBoxes=java.util.Collections.unmodifiableList(boxes);
        collisionCells=null;
        collisionIndex=null;
        return collisionBoxes;
    }
    /** Geometry and its broad phase have exactly the same key and lifetime. */
    public com.vandorlabs.blocks.DoorCollisionIndex collisionIndex(net.minecraft.util.EnumFacing face,boolean open){
        java.util.List<AxisAlignedBB> geometry=collisionGeometry(face,open);
        if(getClass()!=TileEntityLargeProgrammableDoor.class)return new com.vandorlabs.blocks.DoorCollisionIndex(geometry);
        if(collisionIndex==null)collisionIndex=new com.vandorlabs.blocks.DoorCollisionIndex(geometry);
        return collisionIndex;
    }
    /**
     * Each of the nine block cells owns a slice of the same assembly. Cache that
     * partition along with its geometry, rather than clipping every box again
     * for every entity collision query. Edge cells retain the overhanging leaf.
     */
    public java.util.List<AxisAlignedBB> collisionCellGeometry(net.minecraft.util.EnumFacing face,
            boolean open,int column,int row) {
        java.util.List<AxisAlignedBB> all=collisionGeometry(face,open);
        // Extensions may supply mutable/dynamic collisionGeometry overrides.
        boolean cacheable=getClass()==TileEntityLargeProgrammableDoor.class
                &&column>=0&&column<3&&row>=0&&row<3;
        int index=row*3+column;
        if(cacheable){
            if(collisionCells==null)collisionCells=new java.util.ArrayList<>(
                    java.util.Collections.<java.util.List<AxisAlignedBB>>nCopies(9,null));
            java.util.List<AxisAlignedBB> cached=collisionCells.get(index);
            if(cached!=null)return cached;
        }
        net.minecraft.util.EnumFacing width=face.rotateYCCW();
        BlockPos cellPos=pos.offset(width,column).up(row);
        double lo=column==0?Double.NEGATIVE_INFINITY:0,hi=column==2?Double.POSITIVE_INFINITY:1;
        double minX=Double.NEGATIVE_INFINITY,maxX=Double.POSITIVE_INFINITY;
        double minZ=Double.NEGATIVE_INFINITY,maxZ=Double.POSITIVE_INFINITY;
        if(width.getAxis()==net.minecraft.util.EnumFacing.Axis.X){
            minX=cellPos.getX()+(width.getFrontOffsetX()>0?lo:1-hi);
            maxX=cellPos.getX()+(width.getFrontOffsetX()>0?hi:1-lo);
        }else{
            minZ=cellPos.getZ()+(width.getFrontOffsetZ()>0?lo:1-hi);
            maxZ=cellPos.getZ()+(width.getFrontOffsetZ()>0?hi:1-lo);
        }
        AxisAlignedBB cell=new AxisAlignedBB(minX,row==0?Double.NEGATIVE_INFINITY:cellPos.getY(),minZ,
                maxX,row==2?Double.POSITIVE_INFINITY:cellPos.getY()+1,maxZ);
        java.util.List<AxisAlignedBB> clipped=new java.util.ArrayList<>();
        // Intersect only overlapping boxes: AxisAlignedBB normalizes reversed
        // endpoints, so clipping disjoint boxes would create phantom solids.
        for(AxisAlignedBB box:all)if(box.intersects(cell))clipped.add(box.intersect(cell));
        java.util.List<AxisAlignedBB> result=java.util.Collections.unmodifiableList(clipped);
        if(cacheable)collisionCells.set(index,result);
        return result;
    }
    private BlockPos renderBoundsPos;
    private AxisAlignedBB renderBounds;
    @Override public AxisAlignedBB getRenderBoundingBox(){
        if(renderBounds==null||!pos.equals(renderBoundsPos)){
            renderBoundsPos=pos.toImmutable();
            renderBounds=new AxisAlignedBB(pos.getX()-4,pos.getY()-3,pos.getZ()-4,
                    pos.getX()+5,pos.getY()+7,pos.getZ()+5);
        }
        return renderBounds;
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setBoolean("LargeDoorAssigned",assigned);tag.setInteger("LargeDoorColumn",column);tag.setInteger("LargeDoorRow",row);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){
        // Assign role before the base class schedules redstone registration.
        assigned=tag.getBoolean("LargeDoorAssigned");column=tag.getInteger("LargeDoorColumn");row=tag.getInteger("LargeDoorRow");
        if(column<0 || column>2 || row<0 || row>2)assigned=false;
        super.readFromNBT(tag);
    }
}
