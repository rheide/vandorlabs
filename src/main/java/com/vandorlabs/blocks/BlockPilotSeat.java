package com.vandorlabs.blocks;

import com.vandorlabs.entity.EntityChairSeat;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

/** Pilot seat with an invisible upper cell reserving the backrest. */
public class BlockPilotSeat extends BlockVandorDirectional {
    public static final net.minecraft.block.properties.PropertyInteger HEIGHT=net.minecraft.block.properties.PropertyInteger.create("height",0,3);
    public static final net.minecraft.block.properties.PropertyInteger ARMS=net.minecraft.block.properties.PropertyInteger.create("arms",0,3);
    public static final PropertyBool UPPER = BlockBridgeChair.UPPER;
    public static final double SEAT_HEIGHT = 7D / 16D;
    private static final AxisAlignedBB BOUNDS = new AxisAlignedBB(-.125, 0, 0, 1.125, 1, 1.125);
    private static final AxisAlignedBB BACK = new AxisAlignedBB(-.125, 0, 0, 1.125, .75, 1.125);

    public BlockPilotSeat() {
        this("pilot_seat");
    }
    protected BlockPilotSeat(String name) {
        super(name);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(UPPER, false).withProperty(HEIGHT,0).withProperty(ARMS,0));
        setLightOpacity(0);
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING, UPPER,HEIGHT,ARMS); }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3)).withProperty(UPPER, (meta & 4) != 0);
    }
    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(UPPER) ? 4 : 0);
    }
    @Override public boolean hasTileEntity(IBlockState state){return !state.getValue(UPPER);}
    @Override public net.minecraft.tileentity.TileEntity createTileEntity(World world,IBlockState state){return new com.vandorlabs.tiles.TileEntityPilotSeat();}
    public static double seatHeight(IBlockAccess world,BlockPos lower){
        net.minecraft.tileentity.TileEntity tile=world.getTileEntity(lower);
        return tile instanceof com.vandorlabs.tiles.TileEntityPilotSeat?((com.vandorlabs.tiles.TileEntityPilotSeat)tile).seatHeight():SEAT_HEIGHT;
    }
    @Override public IBlockState getActualState(IBlockState state,IBlockAccess world,BlockPos pos){
        net.minecraft.tileentity.TileEntity tile=world.getTileEntity(state.getValue(UPPER)?pos.down():pos);
        BlockPos lower=state.getValue(UPPER)?pos.down():pos;EnumFacing facing=state.getValue(FACING);
        int arms=(world.getBlockState(lower.offset(facing.rotateYCCW())).isFullCube()?1:0)|(world.getBlockState(lower.offset(facing.rotateY())).isFullCube()?2:0);
        return state.withProperty(ARMS,arms).withProperty(HEIGHT,tile instanceof com.vandorlabs.tiles.TileEntityPilotSeat?((com.vandorlabs.tiles.TileEntityPilotSeat)tile).getHeight():0);
    }
    @Override public int damageDropped(IBlockState state) { return 0; }
    /** Open-bottom shells share their lower air space with the rendered backrest. */
    public static boolean canopyAbove(IBlockAccess world,BlockPos pos) {
        net.minecraft.block.Block block=world.getBlockState(pos.up()).getBlock();
        if(!(block instanceof BlockCanopy))return false;
        return ((BlockCanopy)block).supportsSeatBackrest();
    }
    @Override public void neighborChanged(IBlockState state,World world,BlockPos pos,net.minecraft.block.Block block,BlockPos from) {
        if(!world.isRemote && !state.getValue(UPPER) && world.isAirBlock(pos.up()))
            world.setBlockState(pos.up(),state.withProperty(UPPER,true),3);
        super.neighborChanged(state,world,pos,block,from);
    }
    @Override public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return pos.getY() < world.getHeight() - 1 && super.canPlaceBlockAt(world, pos)
                && (canopyAbove(world,pos) || world.getBlockState(pos.up()).getBlock().isReplaceable(world, pos.up()));
    }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote) {
            if(!canopyAbove(world,pos))world.setBlockState(pos.up(), state.withProperty(UPPER, true), 3);
            net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);
            if(raw instanceof com.vandorlabs.tiles.TileEntityPilotSeat && placer instanceof EntityPlayer){((com.vandorlabs.tiles.TileEntityPilotSeat)raw).owner=placer.getUniqueID();com.vandorlabs.tiles.WorldActionAuthorization.authorize(raw,(EntityPlayer)placer);raw.markDirty();}
        }
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        int height=getActualState(state,world,pos).getValue(HEIGHT);
        AxisAlignedBB box=state.getValue(UPPER)?new AxisAlignedBB(-.125,0,0,1.125,.5+height/8D,1.125):new AxisAlignedBB(-.125,0,0,1.125,canopyAbove(world,pos)?1.5+height/8D:Math.min(1,.75+height/8D),1.125);
        return PanelPlacement.rotateFromNorth(box,state.getValue(FACING));
    }
    @Override public RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,Vec3d start,Vec3d end){
        BlockPos lower=state.getValue(UPPER)?pos.down():pos;
        for(EntityPlayer player:world.playerEntities){
            if(!(player.getRidingEntity() instanceof EntityChairSeat))continue;
            EntityChairSeat mount=(EntityChairSeat)player.getRidingEntity();
            // The client mount's anchor is not sent as entity metadata; its centered position identifies this seat.
            BlockPos occupied=mount.getChairPos()==null?new BlockPos(mount.posX,mount.posY,mount.posZ):mount.getChairPos();
            if(!mount.isDead&&lower.equals(occupied)&&start.squareDistanceTo(player.getPositionEyes(1))<1e-8)return null;
        }
        return super.collisionRayTrace(state,world,pos,start,end);
    }

    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
            EntityPlayer player, EnumHand hand, EnumFacing face, float x, float y, float z) {
        if (hand != EnumHand.MAIN_HAND) return false;
        // Let the canopy item replace the invisible backrest reservation in either placement order.
        if(player.getHeldItem(hand).getItem() instanceof net.minecraft.item.ItemBlock){
            net.minecraft.block.Block held=((net.minecraft.item.ItemBlock)player.getHeldItem(hand).getItem()).getBlock();
            if(held instanceof BlockCanopy && ((BlockCanopy)held).supportsSeatBackrest())return false;
        }
        if(player.isSneaking()) {
            BlockPos lower=state.getValue(UPPER)?pos.down():pos;
            if(!world.isRemote && !player.isSpectator() && world.isBlockModifiable(player,lower) && player.canPlayerEdit(lower,face,player.getHeldItem(hand))) {
                com.vandorlabs.tiles.TileEntityPilotSeat tile=(com.vandorlabs.tiles.TileEntityPilotSeat)world.getTileEntity(lower);
                if(tile!=null){tile.owner=player.getUniqueID();com.vandorlabs.tiles.WorldActionAuthorization.authorize(tile,player);tile.markDirty();com.vandorlabs.vehicle.VehicleService.previewAssembly((net.minecraft.entity.player.EntityPlayerMP)player,lower);}
            }
            return true;
        }
        if (state.getValue(UPPER)) return onBlockActivated(world, pos.down(), state.withProperty(UPPER, false), player, hand, face, x, y, z);
        if (world.isRemote) return true;
        for (EntityChairSeat seat : world.getEntitiesWithinAABB(EntityChairSeat.class,
                new AxisAlignedBB(pos).grow(.25, 1, .25))) {
            if (seat.isDead || !pos.equals(seat.getChairPos())) continue;
            if (seat.getPassengers().isEmpty() && player.startRiding(seat, true))com.vandorlabs.vehicle.VehicleService.taxiAssembly((net.minecraft.entity.player.EntityPlayerMP)player,pos);
            return true;
        }
        EntityChairSeat seat = new EntityChairSeat(world, pos, seatHeight(world,pos));
        seat.rotationYaw = state.getValue(FACING).getHorizontalAngle();
        if (world.spawnEntity(seat)) {
            if(!player.startRiding(seat, true))seat.setDead();
            else com.vandorlabs.vehicle.VehicleService.taxiAssembly((net.minecraft.entity.player.EntityPlayerMP)player,pos);
        }
        return true;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        BlockPos lower = state.getValue(UPPER) ? pos.down() : pos;
        if(state.getValue(UPPER) && canopyAbove(world,lower) && world.getBlockState(lower).getBlock()==this){
            super.breakBlock(world,pos,state);return;
        }
        if (!world.isRemote)
            for (EntityChairSeat seat : world.getEntitiesWithinAABB(EntityChairSeat.class,
                    new AxisAlignedBB(lower).grow(.25, 1, .25)))
                if (lower.equals(seat.getChairPos())) { seat.removePassengers(); seat.setDead(); }
        BlockPos other = state.getValue(UPPER) ? pos.down() : pos.up();
        IBlockState partner = world.getBlockState(other);
        if (partner.getBlock() == this && partner.getValue(UPPER) != state.getValue(UPPER)) world.setBlockToAir(other);
        super.breakBlock(world, pos, state);
    }
}
