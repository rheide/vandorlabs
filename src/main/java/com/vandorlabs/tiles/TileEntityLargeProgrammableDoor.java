package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockLargeProgrammableDoor;
import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.render.LargeDoorMount;
import com.vandorlabs.render.LargeDoorJoin;
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
    private boolean join;
    private final LargeDoorJoin.Group<TileEntityLargeProgrammableDoor> singleDoor=LargeDoorJoin.singleton(this);
    private long joinTick=Long.MIN_VALUE;
    private LargeDoorJoin.Group<TileEntityLargeProgrammableDoor> joinGroup;
    public boolean joins(){return join;}
    public void setJoin(boolean next){
        if(join==next)return;
        join=next;
        joinGroup=null;
        markDirty();
        if(world!=null&&!world.isRemote){
            net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
            world.notifyBlockUpdate(pos,state,state,2);
            if(state.getBlock() instanceof BlockLargeProgrammableDoor)
                ((BlockLargeProgrammableDoor)state.getBlock()).joinChanged(world,this);
        }
    }
    public boolean joinCompatible(TileEntityLargeProgrammableDoor other){
        return other.join&&other.securityMode()==0&&panelMode()==other.panelMode()&&horizontal==other.horizontal
                &&isSliding()==other.isSliding()&&getSlideDirection()==other.getSlideDirection()
                &&getPlacementDepth()==other.getPlacementDepth()&&isFramed()==other.isFramed()
                &&getTrigger()==other.getTrigger();
    }
    public LargeDoorJoin.Group<TileEntityLargeProgrammableDoor> joinedGroup(){
        if(!join||securityMode()!=0||world==null||world instanceof com.vandorlabs.vehicle.VehicleWorld)
            return singleDoor;
        long tick=world.getTotalWorldTime();
        if(joinGroup!=null&&joinTick==tick)return joinGroup;
        joinTick=tick;
        net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
        if(!(state.getBlock() instanceof BlockLargeProgrammableDoor))return joinGroup=singleDoor;
        BlockLargeProgrammableDoor door=(BlockLargeProgrammableDoor)state.getBlock();
        EnumFacing face=state.getValue(BlockVandorDoor.FACING);
        return joinGroup=LargeDoorJoin.resolve(this,(column,row)->{
            BlockPos at=cell(pos,face,LargeDoorJoin.PITCH*column,LargeDoorJoin.PITCH*row);
            if(!world.isBlockLoaded(at,false))return null;
            TileEntityLargeProgrammableDoor other=door.root(world,at);
            return other!=null&&other.getPos().equals(at)&&world.getBlockState(at).getValue(BlockVandorDoor.FACING)==face
                    &&joinCompatible(other)&&door.complete(world,other)?other:null;
        });
    }
    public void invalidateJoin(){joinGroup=null;}
    @Override public NBTTagCompound itemSettings(){
        NBTTagCompound tag=super.itemSettings();
        if(join)tag.setBoolean(LargeDoorJoin.TAG,true);
        return tag;
    }
    @Override public void applyItemSettings(NBTTagCompound tag){
        super.applyItemSettings(tag);
        setJoin(tag.getBoolean(LargeDoorJoin.TAG));
    }
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
    @Override public boolean readerHit(Vec3d hit,EnumFacing side){
        LargeDoorJoin.Group<TileEntityLargeProgrammableDoor> group=joinedGroup();
        if(!group.joined())return localReaderHit(hit,side);
        BlockPos origin=group.anchor.getPos();
        int facing=world.getBlockState(origin).getValue(BlockVandorDoor.FACING).getHorizontalIndex();
        Vec3d local=LargeDoorJoin.unproject(Vec3d::new,facing,horizontal,group.columns,group.rows,
                hit.x-origin.getX(),hit.y-origin.getY(),hit.z-origin.getZ());
        return group.anchor.localReaderHit(local.add(new Vec3d(origin)),side);
    }
    private boolean localReaderHit(Vec3d hit,EnumFacing side){return super.readerHit(uprightHit(hit),side);}
    public boolean isAnchor(){return assigned && column==0 && row==0;}
    public BlockPos anchorPos(){if(world==null||column==0&&row==0)return pos;
        net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
        if(!(state.getBlock() instanceof BlockLargeProgrammableDoor))return pos;
        return anchorPos(state.getValue(BlockVandorDoor.FACING));}
    public BlockPos anchorPos(EnumFacing front){return cell(pos,front,-column,-row);}
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
    private int collisionColumns,collisionRows;
    private java.util.List<AxisAlignedBB> collisionBoxes;
    private java.util.List<java.util.List<AxisAlignedBB>> collisionCells;
    private com.vandorlabs.blocks.DoorCollisionIndex collisionIndex;
    /** Reuse immutable transformed boxes across the assembly's nine cell queries. */
    public java.util.List<AxisAlignedBB> collisionGeometry(net.minecraft.util.EnumFacing face,boolean open) {
        LargeDoorJoin.Group<TileEntityLargeProgrammableDoor> group=joinedGroup();
        return group.anchor.groupCollision(face,open,group.columns,group.rows);
    }
    private java.util.List<AxisAlignedBB> groupCollision(EnumFacing face,boolean open,int columns,int rows){
        boolean sliding=isSliding();
        int key=(isFramed()?1:0)|(sliding?2:0)|(getSlideDirection()<<2)|(open?16:0)
                |(getPlacementDepth()<<5)|(hasHinges()?128:0)|(hasPanel()?256:0)|(face.getHorizontalIndex()<<9)|(panelMode()<<12)|(securityMode()<<14)|(horizontal?1<<17:0);
        if(collisionBoxes!=null && collisionKey==key && pos.equals(collisionPos)&&collisionColumns==columns&&collisionRows==rows)return collisionBoxes;
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
        if(columns!=1||rows!=1)boxes.replaceAll(box->LargeDoorJoin.project(AxisAlignedBB::new,face.getHorizontalIndex(),
                horizontal,columns,rows,box.minX-pos.getX(),box.minY-pos.getY(),box.minZ-pos.getZ(),
                box.maxX-pos.getX(),box.maxY-pos.getY(),box.maxZ-pos.getZ()).offset(pos));
        collisionColumns=columns;collisionRows=rows;
        collisionKey=key;collisionPos=pos.toImmutable();collisionBoxes=java.util.Collections.unmodifiableList(boxes);
        collisionCells=null;
        collisionIndex=null;
        return collisionBoxes;
    }
    /** Geometry and its broad phase have exactly the same key and lifetime. */
    public com.vandorlabs.blocks.DoorCollisionIndex collisionIndex(net.minecraft.util.EnumFacing face,boolean open){
        java.util.List<AxisAlignedBB> geometry=collisionGeometry(face,open);
        if(getClass()!=TileEntityLargeProgrammableDoor.class)return new com.vandorlabs.blocks.DoorCollisionIndex(geometry);
        TileEntityLargeProgrammableDoor anchor=joinedGroup().anchor;
        if(anchor.collisionIndex==null)anchor.collisionIndex=new com.vandorlabs.blocks.DoorCollisionIndex(geometry);
        return anchor.collisionIndex;
    }
    /**
     * Each of the nine block cells owns a slice of the same assembly. Cache that
     * partition along with its geometry, rather than clipping every box again
     * for every entity collision query. Edge cells retain the overhanging leaf.
     */
    public java.util.List<AxisAlignedBB> collisionCellGeometry(net.minecraft.util.EnumFacing face,
            boolean open,int column,int row) {
        LargeDoorJoin.Group<TileEntityLargeProgrammableDoor> group=joinedGroup();
        if(group.joined())return group.anchor.collisionSlice(face,
                group.anchor.getWorld().getBlockState(group.anchor.getPos()).getValue(BlockVandorDoor.OPEN),
                column+3*group.column,row+3*group.row,group.columns,group.rows);
        return collisionSlice(face,open,column,row,1,1);
    }
    private java.util.List<AxisAlignedBB> collisionSlice(EnumFacing face,boolean open,int column,int row,int columns,int rows){
        boolean ordinary=columns==1&&rows==1;
        java.util.List<AxisAlignedBB> all=ordinary?collisionGeometry(face,open):groupCollision(face,open,columns,rows);
        int width=3*columns,height=3*rows,index=row*width+column;
        boolean cacheable=getClass()==TileEntityLargeProgrammableDoor.class
                &&column>=0&&column<width&&row>=0&&row<height;
        if(cacheable){
            if(collisionCells==null)collisionCells=new java.util.ArrayList<>(
                    java.util.Collections.<java.util.List<AxisAlignedBB>>nCopies(width*height,null));
            java.util.List<AxisAlignedBB> cached=collisionCells.get(index);
            if(cached!=null)return cached;
        }
        AxisAlignedBB cell=collisionPartition(face,column,row,width,height);
        java.util.List<AxisAlignedBB> clipped=new java.util.ArrayList<>();
        for(AxisAlignedBB box:all)if(box.intersects(cell))clipped.add(box.intersect(cell));
        java.util.List<AxisAlignedBB> result=java.util.Collections.unmodifiableList(clipped);
        if(cacheable)collisionCells.set(index,result);
        return result;
    }
    public AxisAlignedBB collisionPartition(EnumFacing face,int column,int row){
        return collisionPartition(face,column,row,3,3);
    }
    private AxisAlignedBB collisionPartition(EnumFacing face,int column,int row,int columns,int rows){
        BlockPos cell=cell(pos,face,column,row);
        EnumFacing width=face.rotateYCCW(),height=rowDirection(face);
        double[] min={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        double[] max={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
        clipAxis(min,max,cell,width,column,columns);
        clipAxis(min,max,cell,height,row,rows);
        return new AxisAlignedBB(min[0],min[1],min[2],max[0],max[1],max[2]);
    }
    private static void clipAxis(double[] min,double[] max,BlockPos cell,EnumFacing axis,int index,int count){
        double low=index==0?Double.NEGATIVE_INFINITY:0;
        double high=index==count-1?Double.POSITIVE_INFINITY:1;
        boolean positive=axis.getAxisDirection()==EnumFacing.AxisDirection.POSITIVE;
        int i=axis.getAxis()==EnumFacing.Axis.X?0:axis.getAxis()==EnumFacing.Axis.Y?1:2;
        int coordinate=axis.getAxis()==EnumFacing.Axis.X?cell.getX():axis.getAxis()==EnumFacing.Axis.Y?cell.getY():cell.getZ();
        min[i]=coordinate+(positive?low:1-high);
        max[i]=coordinate+(positive?high:1-low);
    }
    private BlockPos renderBoundsPos;
    private AxisAlignedBB renderBounds;
    private boolean renderJoin;
    @Override public AxisAlignedBB getRenderBoundingBox(){
        if(renderBounds==null||!pos.equals(renderBoundsPos)||renderJoin!=join){
            renderJoin=join;
            renderBoundsPos=pos.toImmutable();
            int extra=join?6:0;
            renderBounds=new AxisAlignedBB(pos.getX()-4-extra,pos.getY()-3-extra,pos.getZ()-4-extra,
                    pos.getX()+5+extra,pos.getY()+7+extra,pos.getZ()+5+extra);
        }
        return renderBounds;
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setBoolean("LargeDoorAssigned",assigned);if(join)tag.setBoolean(LargeDoorJoin.TAG,true);if(horizontal)tag.setBoolean("LargeDoorHorizontal",true);tag.setInteger("LargeDoorColumn",column);tag.setInteger("LargeDoorRow",row);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){
        // Assign role before the base class schedules redstone registration.
        join=tag.getBoolean(LargeDoorJoin.TAG);joinGroup=null;
        horizontal=tag.getBoolean("LargeDoorHorizontal");assigned=tag.getBoolean("LargeDoorAssigned");column=tag.getInteger("LargeDoorColumn");row=tag.getInteger("LargeDoorRow");
        if(column<0 || column>2 || row<0 || row>2)assigned=false;
        super.readFromNBT(tag);
    }
}
