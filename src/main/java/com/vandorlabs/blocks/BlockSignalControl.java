package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntitySignalControl;
import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Supplied four-state controls reuse mounted switch placement and channel configuration. */
public class BlockSignalControl extends BlockVandorSwitch {
    public static final PropertyInteger LEVEL=PropertyInteger.create("level",0,3);
    private final String kind;
    private final int detail;
    public BlockSignalControl(String id,String kind,int detail){super(id);this.kind=kind;this.detail=detail;setDefaultState(getDefaultState().withProperty(LEVEL,0));}
    public String controlKind(){return kind;}
    public static final String[] TYPES={"thruster_lever","airliner_throttle","fighter_throttle"};
    public int controlType(){for(int i=0;i<TYPES.length;i++)if(TYPES[i].equals(kind))return i;return 0;}
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){
        Block next=world.getBlockState(pos).getBlock();
        if(hasSelectableType() && next instanceof BlockSignalControl && ((BlockSignalControl)next).hasSelectableType())return;
        super.breakBlock(world,pos,state);
    }
    private Block itemBlock(){return hasSelectableType()?Block.getBlockFromName("vandorlabs:thruster_lever"):this;}

    public boolean hasAdjustableBase(){return true;}
    public boolean hasSelectableType(){return !"wall_slider".equals(kind);}
    public static final net.minecraftforge.common.property.IUnlistedProperty<Integer> MOUNT=ProgrammableHousingState.integer("control_mount");
    @Override protected BlockStateContainer createBlockState(){return new net.minecraftforge.common.property.ExtendedBlockState(this,new net.minecraft.block.properties.IProperty[]{FACING,ON,ROTATION,LEVEL},new net.minecraftforge.common.property.IUnlistedProperty[]{MOUNT});}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntitySignalControl();}
    @Override public IBlockState getActualState(IBlockState state,IBlockAccess world,BlockPos pos){
        state=super.getActualState(state,world,pos);TileEntity tile=world.getTileEntity(pos);
        return state.withProperty(LEVEL,tile instanceof TileEntitySignalControl?((TileEntitySignalControl)tile).getStep():0);
    }
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing facing,float x,float y,float z){
        if(player.isSneaking())return super.onBlockActivated(world,pos,state,player,hand,facing,x,y,z);
        if(!world.isRemote && world.getTileEntity(pos) instanceof TileEntitySignalControl){
            TileEntitySignalControl control=(TileEntitySignalControl)world.getTileEntity(pos);control.setStep((control.getStep()+1)%4);
            world.playSound(null,pos,net.minecraft.init.SoundEvents.BLOCK_LEVER_CLICK,SoundCategory.BLOCKS,.3F,.5F+control.getStep()*.1F);
        }
        return true;
    }
    @Override public int getLightValue(IBlockState state,IBlockAccess world,BlockPos pos){return 0;}
    public AxisAlignedBB originalBounds(IBlockState state){
        EnumFacing facing=state.getValue(FACING);
        int rotation=state.getValue(ROTATION);
        String mount=facing.getName();
        if(facing.getAxis()==EnumFacing.Axis.Y)mount=(facing==EnumFacing.UP?"floor":"ceiling")+(rotation%2==0?"_z":"_x");
        AxisAlignedBB box=SignalControlBounds.get(detail,kind,mount);
        return facing.getAxis()==EnumFacing.Axis.Y && rotation>=2?new AxisAlignedBB(1-box.maxX,box.minY,1-box.maxZ,1-box.minX,box.maxY,1-box.minZ):box;
    }
    public AxisAlignedBB supportBounds(IBlockState state){
        double a=kind.equals("thruster_lever") || kind.equals("wall_slider")?3/16D:kind.equals("fighter_throttle")?1.5/16D:1/16D;
        double b=kind.equals("thruster_lever")?2/16D:1/16D;EnumFacing face=state.getValue(FACING);
        if(face.getAxis()==EnumFacing.Axis.Y){if(state.getValue(ROTATION)%2==1){double swap=a;a=b;b=swap;}double y=face==EnumFacing.UP?0:1;return new AxisAlignedBB(a,y,b,1-a,y,1-b);}
        if(face.getAxis()==EnumFacing.Axis.Z){double z=face==EnumFacing.SOUTH?0:1;return new AxisAlignedBB(a,b,z,1-a,1-b,z);}
        double x=face==EnumFacing.EAST?0:1;return new AxisAlignedBB(x,b,a,x,1-b,1-a);
    }
    @Override public IBlockState getExtendedState(IBlockState state,IBlockAccess world,BlockPos pos){
        TileEntity tile=world.getTileEntity(pos);int mount=0;
        if(hasAdjustableBase() && tile instanceof TileEntitySignalControl){TileEntitySignalControl t=(TileEntitySignalControl)tile;mount=t.getBaseHeight()+4*(t.getBaseTilt()+4*t.getTiltDirection());}
        return ((net.minecraftforge.common.property.IExtendedBlockState)state).withProperty(MOUNT,mount);
    }
    public java.util.List<AxisAlignedBB> collisionPieces(IBlockState state,IBlockAccess world,BlockPos pos){
        state=getActualState(state,world,pos);TileEntity tile=world.getTileEntity(pos);
        if(!hasAdjustableBase())return java.util.Collections.emptyList();
        TileEntitySignalControl t=tile instanceof TileEntitySignalControl?(TileEntitySignalControl)tile:null;
        return SignalControlShape.boxes(this,state,t==null?0:t.getBaseHeight(),t==null?0:t.getBaseTilt(),t==null?0:t.getTiltDirection());
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){
        if(!hasAdjustableBase())return originalBounds(getActualState(state,world,pos));
        AxisAlignedBB result=null;for(AxisAlignedBB box:collisionPieces(state,world,pos))result=result==null?box:result.union(box);return result;
    }
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return hasAdjustableBase()?getBoundingBox(state,world,pos):NULL_AABB;}
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,java.util.List<AxisAlignedBB> boxes,net.minecraft.entity.Entity entity,boolean actual){
        for(AxisAlignedBB box:collisionPieces(state,world,pos))addCollisionBoxToList(pos,query,boxes,box);
    }
    @Override public void getDrops(NonNullList<ItemStack> drops,IBlockAccess world,BlockPos pos,IBlockState state,int fortune){
        ItemStack stack=new ItemStack(itemBlock());TileEntity tile=world.getTileEntity(pos);
        if(tile instanceof TileEntitySignalControl)stack.setTagInfo("RedstoneChannelSettings",((TileEntitySignalControl)tile).configuration());drops.add(stack);
    }
    @Override public boolean removedByPlayer(IBlockState state,World world,BlockPos pos,EntityPlayer player,boolean willHarvest){return willHarvest || super.removedByPlayer(state,world,pos,player,false);}
    @Override public void harvestBlock(World world,EntityPlayer player,BlockPos pos,IBlockState state,TileEntity tile,ItemStack tool){super.harvestBlock(world,player,pos,state,tile,tool);world.setBlockToAir(pos);}
    @Override public ItemStack getPickBlock(IBlockState state,RayTraceResult target,World world,BlockPos pos,EntityPlayer player){
        ItemStack stack=new ItemStack(itemBlock());TileEntity tile=world.getTileEntity(pos);
        if(tile instanceof TileEntitySignalControl)stack.setTagInfo("RedstoneChannelSettings",((TileEntitySignalControl)tile).configuration());
        return stack;
    }
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,EntityLivingBase placer,ItemStack stack){
        NBTTagCompound settings=stack.getSubCompound("RedstoneChannelSettings");TileEntity tile=world.getTileEntity(pos);
        if(!world.isRemote && settings!=null && tile instanceof TileEntitySignalControl){((TileEntitySignalControl)tile).configureLimits(settings.getInteger("LowLimit"),settings.getInteger("HighLimit"));((TileEntitySignalControl)tile).readMount(settings);}
        super.onBlockPlacedBy(world,pos,state,placer,stack);
        if(!world.isRemote && settings!=null && settings.hasKey("ControlType",3) && tile instanceof TileEntitySignalControl)((TileEntitySignalControl)tile).configureType(settings.getInteger("ControlType"));
    }
}
