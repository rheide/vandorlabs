package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockLargeProgrammableDoor;
import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.render.LargeDoorMount;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;

/** Nine cells address one configuration and one renderer, without loading neighbors. */
public class TileEntityLargeProgrammableDoor extends TileEntitySpaceDoor {
    public TileEntityLargeProgrammableDoor(){configure(DEFAULT_LARGE_DESIGN,1,true);}
    private int column,row;
    private boolean assigned;
    private boolean horizontal;
    public void assign(BlockPos anchor){assign(anchor,false);}
    public void assign(BlockPos anchor,boolean horizontal){
        this.horizontal=horizontal;
        if(horizontal){
            EnumFacing front=world.getBlockState(pos).getValue(BlockVandorDoor.FACING);
            column=distance(anchor,pos,front.rotateYCCW());
            row=distance(anchor,pos,front);
        }else{
            column=Math.abs(pos.getX()-anchor.getX())+Math.abs(pos.getZ()-anchor.getZ());
            row=pos.getY()-anchor.getY();
        }
        assigned=true;markDirty();
    }
    public boolean isHorizontal(){return horizontal;}
    public EnumFacing rowDirection(EnumFacing front){return horizontal?front:EnumFacing.UP;}
    public BlockPos cell(BlockPos anchor,EnumFacing front,int column,int row){
        return anchor.offset(front.rotateYCCW(),column).offset(rowDirection(front),row);
    }
    public static int distance(BlockPos anchor,BlockPos cell,EnumFacing direction){
        return (cell.getX()-anchor.getX())*direction.getFrontOffsetX()
                +(cell.getY()-anchor.getY())*direction.getFrontOffsetY()
                +(cell.getZ()-anchor.getZ())*direction.getFrontOffsetZ();
    }
    public static int hitCell(BlockPos anchor,Vec3d hit,EnumFacing direction){
        double along=(hit.x-anchor.getX())*direction.getFrontOffsetX()
                +(hit.y-anchor.getY())*direction.getFrontOffsetY()
                +(hit.z-anchor.getZ())*direction.getFrontOffsetZ();
        if(direction.getAxisDirection()==EnumFacing.AxisDirection.NEGATIVE)along+=1;
        return net.minecraft.util.math.MathHelper.clamp(net.minecraft.util.math.MathHelper.floor(along),0,2);
    }
    private Vec3d uprightHit(Vec3d hit){
        if(!horizontal)return hit;
        EnumFacing front=world.getBlockState(pos).getValue(BlockVandorDoor.FACING);
        int facing=front.getHorizontalIndex();
        double row=LargeDoorMount.row(facing,hit.x-pos.getX(),hit.z-pos.getZ());
        double normal=hit.y-pos.getY();
        double axis=front.getAxisDirection()==EnumFacing.AxisDirection.POSITIVE?1-normal:normal;
        return new Vec3d(front.getAxis()==EnumFacing.Axis.X?pos.getX()+axis:hit.x,pos.getY()+row,
                front.getAxis()==EnumFacing.Axis.Z?pos.getZ()+axis:hit.z);
    }
    @Override public int readerIndex(Vec3d hit){return super.readerIndex(uprightHit(hit));}
    @Override public boolean readerHit(Vec3d hit,EnumFacing side){return super.readerHit(uprightHit(hit),side);}
    public boolean isAnchor(){return assigned && column==0 && row==0;}
    public BlockPos anchorPos(){if(world==null||column==0&&row==0)return pos;
        net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
        if(!(state.getBlock() instanceof BlockLargeProgrammableDoor))return pos;
        return cell(pos,state.getValue(BlockVandorDoor.FACING),-column,-row);}
    @Override protected boolean isLowerDoor(){return isAnchor() && world!=null && world.getBlockState(pos).getBlock() instanceof BlockLargeProgrammableDoor;}
    @Override public BlockPos mate(){return null;}
    @Override public int localSignalLevel(int channel){
        if(!isLowerDoor())return 0;
        EnumFacing front=world.getBlockState(pos).getValue(BlockVandorDoor.FACING);
        int level=0;for(int x=0;x<3;x++)for(int y=0;y<3;y++)level=Math.max(level,com.vandorlabs.redstone.LoadedRedstonePower.level(world,cell(pos,front,x,y)));
        return level;
    }
    @Override public boolean hasLocalRedstoneSignal(){
        if(!isLowerDoor())return false;
        net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
        EnumFacing front=state.getValue(BlockVandorDoor.FACING);
        for(int x=0;x<3;x++)for(int y=0;y<3;y++)
            if(com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world,cell(pos,front,x,y)))return true;
        return false;
    }
    @Override public boolean usable(net.minecraft.entity.player.EntityPlayer player){
        if(!isAnchor() || !super.usable(player))return false;
        EnumFacing front=world.getBlockState(pos).getValue(BlockVandorDoor.FACING);
        for(int x=0;x<3;x++)for(int y=0;y<3;y++){
            BlockPos cell=cell(pos,front,x,y);
            if(!world.isBlockLoaded(cell) || !player.canPlayerEdit(cell,rowDirection(front),player.getHeldItemMainhand()) || !world.isBlockModifiable(player,cell))return false;
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
                |(getPlacementDepth()<<5)|(hasHinges()?128:0)|(hasPanel()?256:0)|(face.getHorizontalIndex()<<9)|(panelMode()<<12)|(securityMode()<<14)|(horizontal?1<<17:0);
        if(collisionBoxes!=null && collisionKey==key && pos.equals(collisionPos))return collisionBoxes;
        java.util.List<AxisAlignedBB> boxes=new java.util.ArrayList<>();
        for(com.vandorlabs.render.LargeDoorGeometry.Box b:com.vandorlabs.render.LargeDoorGeometry.boxes(isFramed(),sliding,getSlideDirection(),open,positionOffset(),face.getHorizontalIndex(),hasPanel(),getPlacementDepth()==2))
            boxes.add(new AxisAlignedBB(b.x0,b.y0,b.z0,b.x1,b.y1,b.z1).offset(pos));
        if(hasPanel()&&!boxes.isEmpty()){
            AxisAlignedBB pad=boxes.remove(boxes.size()-1).offset(-pos.getX(),-pos.getY(),-pos.getZ());
            if(securityMode()>0){pad=pad.grow(0,1.05/16,0);pad=face.getAxis()==net.minecraft.util.EnumFacing.Axis.X?pad.grow(.75/16,0,0):pad.grow(0,0,.75/16);}
            boxes.add(pad.offset(pos));if(panelMode()==2)boxes.add(com.vandorlabs.blocks.BlockConfigurableSpaceDoor.mirrorPanel(pad,face).offset(pos));
        }
        if(horizontal)boxes.replaceAll(box->LargeDoorMount.horizontal(AxisAlignedBB::new,face.getHorizontalIndex(),
                box.minX-pos.getX(),box.minY-pos.getY(),box.minZ-pos.getZ(),
                box.maxX-pos.getX(),box.maxY-pos.getY(),box.maxZ-pos.getZ()).offset(pos));
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
        AxisAlignedBB cell=collisionPartition(face,column,row);
        java.util.List<AxisAlignedBB> clipped=new java.util.ArrayList<>();
        // Intersect only overlapping boxes: AxisAlignedBB normalizes reversed
        // endpoints, so clipping disjoint boxes would create phantom solids.
        for(AxisAlignedBB box:all)if(box.intersects(cell))clipped.add(box.intersect(cell));
        java.util.List<AxisAlignedBB> result=java.util.Collections.unmodifiableList(clipped);
        if(cacheable)collisionCells.set(index,result);
        return result;
    }
    public AxisAlignedBB collisionPartition(EnumFacing face,int column,int row){
        BlockPos cell=cell(pos,face,column,row);
        EnumFacing width=face.rotateYCCW(),height=rowDirection(face);
        double[] min={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        double[] max={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
        clipAxis(min,max,cell,width,column);
        clipAxis(min,max,cell,height,row);
        return new AxisAlignedBB(min[0],min[1],min[2],max[0],max[1],max[2]);
    }
    private static void clipAxis(double[] min,double[] max,BlockPos cell,EnumFacing axis,int index){
        double low=index==0?Double.NEGATIVE_INFINITY:0;
        double high=index==2?Double.POSITIVE_INFINITY:1;
        boolean positive=axis.getAxisDirection()==EnumFacing.AxisDirection.POSITIVE;
        int i=axis.getAxis()==EnumFacing.Axis.X?0:axis.getAxis()==EnumFacing.Axis.Y?1:2;
        int coordinate=axis.getAxis()==EnumFacing.Axis.X?cell.getX():axis.getAxis()==EnumFacing.Axis.Y?cell.getY():cell.getZ();
        min[i]=coordinate+(positive?low:1-high);
        max[i]=coordinate+(positive?high:1-low);
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
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setBoolean("LargeDoorAssigned",assigned);if(horizontal)tag.setBoolean("LargeDoorHorizontal",true);tag.setInteger("LargeDoorColumn",column);tag.setInteger("LargeDoorRow",row);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){
        // Assign role before the base class schedules redstone registration.
        horizontal=tag.getBoolean("LargeDoorHorizontal");assigned=tag.getBoolean("LargeDoorAssigned");column=tag.getInteger("LargeDoorColumn");row=tag.getInteger("LargeDoorRow");
        if(column<0 || column>2 || row<0 || row>2)assigned=false;
        super.readFromNBT(tag);
    }
}
