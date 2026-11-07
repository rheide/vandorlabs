package com.vandorlabs.segmented;

import com.vandorlabs.blocks.BlockVandorDirectional;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.List;

/** Native segments remain in place and retain ownership throughout opening. */
public final class SegmentedDoorBlock extends BlockVandorDirectional {
    public static final PropertyBool TOP=PropertyBool.create("top"),BOTTOM=PropertyBool.create("bottom"),BAKED=PropertyBool.create("baked");
    public static final net.minecraftforge.common.property.IUnlistedProperty<Integer> UV_PHASE=com.vandorlabs.blocks.ProgrammableHousingState.integer("segmented_tile_phase");
    private static final AxisAlignedBB CLOSED=new AxisAlignedBB(0,0,.5-SegmentedDoorGeometry.HALF_THICKNESS,1,1,.5+SegmentedDoorGeometry.HALF_THICKNESS);
    public SegmentedDoorBlock(){super("segmented_door");setDefaultState(getDefaultState().withProperty(TOP,true).withProperty(BOTTOM,true).withProperty(BAKED,false));setLightOpacity(0);setHardness(2.5F);}
    @Override protected BlockStateContainer createBlockState(){return new net.minecraftforge.common.property.ExtendedBlockState(this,new net.minecraft.block.properties.IProperty<?>[]{FACING,TOP,BOTTOM,BAKED},new net.minecraftforge.common.property.IUnlistedProperty<?>[]{com.vandorlabs.blocks.ProgrammableHousingState.FINISH,UV_PHASE});}
    @Override public IBlockState getExtendedState(IBlockState state,IBlockAccess world,BlockPos pos){TileEntity raw=world.getTileEntity(pos);return state instanceof net.minecraftforge.common.property.IExtendedBlockState?((net.minecraftforge.common.property.IExtendedBlockState)state).withProperty(com.vandorlabs.blocks.ProgrammableHousingState.FINISH,raw instanceof SegmentedDoorTile?((SegmentedDoorTile)raw).effectiveTexture():-1).withProperty(UV_PHASE,raw instanceof SegmentedDoorTile?((Math.max(0,Math.min(31,((SegmentedDoorTile)raw).column))<<5)|Math.max(0,Math.min(31,((SegmentedDoorTile)raw).topOffset))):0):state;}
    @Override public boolean canPlaceBlockAt(World world,BlockPos pos){if(!super.canPlaceBlockAt(world,pos))return false;int height=1;for(EnumFacing side:new EnumFacing[]{EnumFacing.UP,EnumFacing.DOWN})for(BlockPos at=pos.offset(side);world.isBlockLoaded(at)&&world.getBlockState(at).getBlock()==this;at=at.offset(side))if(++height>SegmentedDoorTile.MAX_HEIGHT)return false;return true;}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new SegmentedDoorTile();}
    @Override public IBlockState getActualState(IBlockState state,IBlockAccess world,BlockPos pos){TileEntity tile=world.getTileEntity(pos);SegmentedDoorTile door=tile instanceof SegmentedDoorTile?(SegmentedDoorTile)tile:null;return state.withProperty(TOP,door==null||door.isRoot()&&door.settings.direction==0&&door.settings.position==0).withProperty(BOTTOM,door==null||door.topOffset==door.height-1).withProperty(BAKED,door!=null&&door.clientBaked);}
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing face,float x,float y,float z,int meta,EntityLivingBase placer){for(BlockPos other:new BlockPos[]{pos.up(),pos.down()})if(world.isBlockLoaded(other)&&world.getBlockState(other).getBlock()==this)return getDefaultState().withProperty(FACING,world.getBlockState(other).getValue(FACING));return super.getStateForPlacement(world,pos,face,x,y,z,meta,placer);}
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,EntityLivingBase placer,ItemStack stack){super.onBlockPlacedBy(world,pos,state,placer,stack);changed(world,pos);}
    private static void changed(World world,BlockPos pos){if(world.isRemote)return;TileEntity tile=world.getTileEntity(pos);if(tile instanceof SegmentedDoorTile)((SegmentedDoorTile)tile).scheduleRefresh();}
    @Override public void neighborChanged(IBlockState state,World world,BlockPos pos,net.minecraft.block.Block block,BlockPos from){changed(world,pos);}
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){com.vandorlabs.blocks.DoorWaterGush.stop(world,pos);super.breakBlock(world,pos,state);changed(world,pos.up());changed(world,pos.down());}
    public static boolean configure(EntityPlayer player,World world,BlockPos pos){if(!com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos))return false;if(!world.isRemote)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_SEGMENTED_DOOR,world,pos.getX(),pos.getY(),pos.getZ());return true;}
    @Override public ItemStack getPickBlock(IBlockState state,RayTraceResult hit,World world,BlockPos pos,EntityPlayer player){ItemStack stack=new ItemStack(this);SegmentedDoorTile tile=SegmentedDoorTile.at(world,pos);if(tile!=null)stack.setTagInfo("BlockEntityTag",tile.itemSettings());return stack;}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing face,float x,float y,float z){
        if(hand!=EnumHand.MAIN_HAND||player.getHeldItem(hand).getItem() instanceof net.minecraft.item.ItemBlock||com.vandorlabs.compat.BetterBuildersWandsCompat.isBbwWand(player.getHeldItem(hand)))return false;
        if(player.isSneaking()||player.getHeldItem(hand).getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER)return configure(player,world,pos);
        TileEntity tile=world.getTileEntity(pos);if(!world.isRemote&&tile instanceof SegmentedDoorTile&&world.isBlockModifiable(player,pos))try{((SegmentedDoorTile)tile).toggle(player);}catch(IllegalArgumentException denied){player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(denied.getMessage()),true);}return true;
    }
    static AxisAlignedBB rotated(AxisAlignedBB b,IBlockState state){switch(state.getValue(FACING)){case SOUTH:return new AxisAlignedBB(1-b.maxX,b.minY,1-b.maxZ,1-b.minX,b.maxY,1-b.minZ);case WEST:return new AxisAlignedBB(b.minZ,b.minY,1-b.maxX,b.maxZ,b.maxY,1-b.minX);case EAST:return new AxisAlignedBB(1-b.maxZ,b.minY,b.minX,1-b.minZ,b.maxY,b.maxX);default:return b;}}

    public static AxisAlignedBB headerBounds(SegmentedDoorTile tile){
        int direction=tile.settings.direction;AxisAlignedBB box=null;double depth=tile.settings.depthOffset();
        if(direction==0&&tile.topOffset==0)box=new AxisAlignedBB(0,.877,.375+depth,1,1.002,.625+depth);
        if(direction==1&&tile.topOffset==tile.height-1)box=new AxisAlignedBB(0,-.002,.375+depth,1,.123,.625+depth);
        if(direction==2&&tile.column==0)box=new AxisAlignedBB(-.002,0,.375+depth,.123,1,.625+depth);
        if(direction==3&&tile.column==tile.columns-1)box=new AxisAlignedBB(.877,0,.375+depth,1.002,1,.625+depth);
        return box==null?null:rotated(box,tile.getWorld().getBlockState(tile.getPos())).offset(tile.getPos());
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){TileEntity raw=world.getTileEntity(pos);return raw instanceof SegmentedDoorTile?SegmentedDoorInteractions.bounds((SegmentedDoorTile)raw).offset(-pos.getX(),-pos.getY(),-pos.getZ()):rotated(CLOSED,state);}
    @Override public RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,Vec3d start,Vec3d end){TileEntity raw=world.getTileEntity(pos);return raw instanceof SegmentedDoorTile?SegmentedDoorInteractions.hit((SegmentedDoorTile)raw,start,end):super.collisionRayTrace(state,world,pos,start,end);}
    @Override public void updateTick(World world,BlockPos pos,IBlockState state,java.util.Random random){com.vandorlabs.blocks.DoorWaterGush.opened(world,pos);}
    @Override public Boolean isAABBInsideMaterial(World world,BlockPos pos,AxisAlignedBB box,net.minecraft.block.material.Material material){return com.vandorlabs.blocks.WaterloggedParts.intersects(world,pos,box,material);}
    @Override public Boolean isAABBInsideLiquid(World world,BlockPos pos,AxisAlignedBB box){return com.vandorlabs.blocks.WaterloggedParts.liquid(world,pos,box);}
    @Override public IBlockState getStateAtViewpoint(IBlockState state,IBlockAccess world,BlockPos pos,Vec3d point){return com.vandorlabs.blocks.WaterloggedParts.viewpoint(state,world,pos,point);}
    @Override public Boolean isEntityInsideMaterial(IBlockAccess world,BlockPos pos,IBlockState state,net.minecraft.entity.Entity entity,double height,net.minecraft.block.material.Material material,boolean head){return com.vandorlabs.blocks.WaterloggedParts.entity(world,pos,entity,height,material,head);}
    @Override public int getLightValue(IBlockState state,IBlockAccess world,BlockPos pos){return Math.max(super.getLightValue(state,world,pos),com.vandorlabs.blocks.WaterloggedParts.light(world,pos));}
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    @Override public Vec3d getFogColor(World world,BlockPos pos,IBlockState state,net.minecraft.entity.Entity entity,Vec3d original,float partial){return com.vandorlabs.blocks.WaterloggedParts.fogColor(world,pos,entity,original,partial);}
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,List<AxisAlignedBB> out,net.minecraft.entity.Entity entity,boolean actual){TileEntity raw=world.getTileEntity(pos);SegmentedDoorTile tile=raw instanceof SegmentedDoorTile?(SegmentedDoorTile)raw:null;if(tile==null||tile.closed())addCollisionBoxToList(pos,query,out,rotated(CLOSED.offset(0,0,tile==null?0:tile.settings.depthOffset()),state));AxisAlignedBB header=tile==null?null:headerBounds(tile);if(header!=null&&header.intersects(query))out.add(header);}
}
