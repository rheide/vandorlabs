package com.vandorlabs.blocks;

/** Shared telescopic ownership and redstone behavior with runners or landing pads. */
public final class BlockNonWheeledLandingGear extends BlockTelescopicLandingGear {
    public final boolean ski,angled;
    public BlockNonWheeledLandingGear(String name,boolean ski,boolean angled){super(name);this.ski=ski;this.angled=angled;}
    @Override public java.util.List<net.minecraft.util.math.AxisAlignedBB> vehicleBoxes(net.minecraft.world.IBlockAccess world,net.minecraft.util.math.BlockPos pos){
        java.util.List<net.minecraft.util.math.AxisAlignedBB> result=new java.util.ArrayList<>();
        for(net.minecraft.util.math.AxisAlignedBB box:super.vehicleBoxes(world,pos)){
            net.minecraft.util.math.AxisAlignedBB local=box.offset(-pos.getX(),-pos.getY(),-pos.getZ());
            com.vandorlabs.tiles.TileEntityLandingGear tile=root(world,pos);double center=tile!=null && tile.getSize()==4?.5:0;
            local=local.offset(-center,0,-center);local=PanelPlacement.rotateFromNorth(local,world.getBlockState(pos).getValue(FACING)).offset(center,0,center);
            result.add(local.offset(pos));
        }return result;
    }
    private java.util.List<net.minecraft.util.math.AxisAlignedBB> worldBoxes(net.minecraft.world.IBlockAccess world,net.minecraft.util.math.BlockPos pos){
        java.util.List<net.minecraft.util.math.AxisAlignedBB> result=new java.util.ArrayList<>();com.vandorlabs.tiles.TileEntityLandingGear root=super.root(world,pos);if(root==null)return result;
        net.minecraft.util.math.AxisAlignedBB cell=new net.minecraft.util.math.AxisAlignedBB(pos);
        for(net.minecraft.util.math.AxisAlignedBB box:vehicleBoxes(world,root.getPos()))if(box.intersects(cell.shrink(.000001)))result.add(box.intersect(cell));
        return result;
    }
    @Override public net.minecraft.util.math.AxisAlignedBB getBoundingBox(net.minecraft.block.state.IBlockState state,net.minecraft.world.IBlockAccess world,net.minecraft.util.math.BlockPos pos){
        net.minecraft.util.math.AxisAlignedBB result=null;for(net.minecraft.util.math.AxisAlignedBB box:worldBoxes(world,pos))result=result==null?box:result.union(box);return result==null?new net.minecraft.util.math.AxisAlignedBB(0,0,0,0,0,0):result.offset(-pos.getX(),-pos.getY(),-pos.getZ());
    }
    @Override public void addCollisionBoxToList(net.minecraft.block.state.IBlockState state,net.minecraft.world.World world,net.minecraft.util.math.BlockPos pos,net.minecraft.util.math.AxisAlignedBB query,java.util.List<net.minecraft.util.math.AxisAlignedBB> boxes,net.minecraft.entity.Entity entity,boolean actual){for(net.minecraft.util.math.AxisAlignedBB box:worldBoxes(world,pos))if(box.intersects(query))boxes.add(box);}
    @Override public net.minecraft.util.math.RayTraceResult collisionRayTrace(net.minecraft.block.state.IBlockState state,net.minecraft.world.World world,net.minecraft.util.math.BlockPos pos,net.minecraft.util.math.Vec3d start,net.minecraft.util.math.Vec3d end){
        net.minecraft.util.math.RayTraceResult nearest=null;
        for(net.minecraft.util.math.AxisAlignedBB box:worldBoxes(world,pos)){net.minecraft.util.math.RayTraceResult hit=box.calculateIntercept(start,end);if(hit!=null && (nearest==null || start.squareDistanceTo(hit.hitVec)<start.squareDistanceTo(nearest.hitVec)))nearest=new net.minecraft.util.math.RayTraceResult(hit.hitVec,hit.sideHit,pos);}return nearest;
    }
    public net.minecraft.util.math.BlockPos connectedRoot(net.minecraft.world.IBlockAccess world,net.minecraft.util.math.BlockPos pos){
        com.vandorlabs.tiles.TileEntityLandingGear root=super.root(world,pos);if(root==null)return pos;
        net.minecraft.util.math.BlockPos current=root.getPos();if(!ski)return current;
        for(int n=0;n<256 && current.getY()>0;n++){
            net.minecraft.util.math.BlockPos below=current.down();if(world instanceof net.minecraft.world.World && !((net.minecraft.world.World)world).isBlockLoaded(below))break;
            net.minecraft.block.state.IBlockState state=world.getBlockState(below);
            if(state.getBlock()!=this)break;
            com.vandorlabs.tiles.TileEntityLandingGear next=super.root(world,below);if(next==null || next.getPos().getY()>=current.getY())break;current=next.getPos();
        }return current;
    }
    @Override public net.minecraft.tileentity.TileEntity createTileEntity(net.minecraft.world.World world,net.minecraft.block.state.IBlockState state){return ski?new com.vandorlabs.tiles.TileEntitySkiLandingGear():super.createTileEntity(world,state);}
    @Override public boolean onBlockActivated(net.minecraft.world.World world,net.minecraft.util.math.BlockPos pos,net.minecraft.block.state.IBlockState state,net.minecraft.entity.player.EntityPlayer player,net.minecraft.util.EnumHand hand,net.minecraft.util.EnumFacing face,float x,float y,float z){
        net.minecraft.util.math.BlockPos root=connectedRoot(world,pos);return super.onBlockActivated(world,root,world.getBlockState(root),player,hand,face,x,y,z);
    }
    public boolean connectedEnd(net.minecraft.world.IBlockAccess world,net.minecraft.util.math.BlockPos pos,boolean front){
        com.vandorlabs.tiles.TileEntityLandingGear tile=super.root(world,pos);if(tile==null || !ski)return false;
        net.minecraft.util.EnumFacing facing=world.getBlockState(pos).getValue(FACING);int step=tile.getSize()==4?2:1;
        net.minecraft.util.math.BlockPos next=pos.offset(front?facing:facing.getOpposite(),step);
        if(world instanceof net.minecraft.world.World && !((net.minecraft.world.World)world).isBlockLoaded(next) || world.getBlockState(next).getBlock()!=this)return false;
        com.vandorlabs.tiles.TileEntityLandingGear other=super.root(world,next);
        return other!=null && other.getSize()==tile.getSize() && world.getBlockState(next).getValue(FACING).getAxis()==facing.getAxis() && Math.abs(other.progress-tile.progress)<.001;
    }
    public static boolean forbidsTaxi(net.minecraft.block.Block block){return block instanceof BlockNonWheeledLandingGear||block instanceof com.vandorlabs.gear.GearBlock && (((com.vandorlabs.gear.GearBlock)block).kind()==com.vandorlabs.gear.GearBlock.Kind.PLATE || ((com.vandorlabs.gear.GearBlock)block).kind()==com.vandorlabs.gear.GearBlock.Kind.SKI);}
}
