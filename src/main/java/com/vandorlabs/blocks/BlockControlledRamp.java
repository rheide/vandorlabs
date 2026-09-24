package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityControlledRamp;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Internal cells protect the platform and its next step, not the full travel column. */
public class BlockControlledRamp extends BlockVandorDirectional {
    private static final ThreadLocal<BlockPos> CARRYING=new ThreadLocal<>();
    /** Scoped to one synchronous collision query, never disables foreign collisions. */
    public static List<AxisAlignedBB> riderObstacles(World world,BlockPos owner,
            net.minecraft.entity.Entity rider,AxisAlignedBB swept) {
        CARRYING.set(owner);
        try { return world.getCollisionBoxes(rider,swept); }
        finally { CARRYING.remove(); }
    }
    public BlockControlledRamp() {
        super("controlled_ramp");
        setLightOpacity(0);
        setBlockUnbreakable();
        setResistance(6000000);
    }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world,IBlockState state) { return new TileEntityControlledRamp(); }
    public List<AxisAlignedBB> boxes(IBlockAccess world,BlockPos pos,IBlockState state,double partial) {
        TileEntity te=world.getTileEntity(pos);
        return te instanceof TileEntityControlledRamp ? ((TileEntityControlledRamp)te).boxes(partial) : Collections.emptyList();
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public boolean isPassable(IBlockAccess world,BlockPos pos) { return true; }
    @Override public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state) {
        return net.minecraft.util.EnumBlockRenderType.ENTITYBLOCK_ANIMATED;
    }
    @Override public net.minecraft.block.state.BlockFaceShape getBlockFaceShape(IBlockAccess world,IBlockState state,
            BlockPos pos,EnumFacing side) { return net.minecraft.block.state.BlockFaceShape.UNDEFINED; }
    @Override public void updateTick(World world,BlockPos pos,IBlockState state,Random random) {
        TileEntity te=world.getTileEntity(pos);
        if (te instanceof TileEntityControlledRamp) ((TileEntityControlledRamp)te).recoverOnEvent();
    }
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB entityBox,
            List<AxisAlignedBB> colliding,net.minecraft.entity.Entity entity,boolean actual) {
        TileEntity te=world.getTileEntity(pos);
        if (te instanceof TileEntityControlledRamp && ((TileEntityControlledRamp)te).belongsTo(CARRYING.get())) return;
        for (AxisAlignedBB box:boxes(world,pos,state,0)) {
            // A rising surface can advance into a rider between their movement packets.
            // Resolve only the shallow top overlap, so walking does not hit every cell as a wall.
            // Stationary platforms, foreign blocks, and the sides/underside retain normal collision.
            if (entity instanceof EntityPlayer && te instanceof TileEntityControlledRamp
                    && ((TileEntityControlledRamp)te).isMoving() && entity.motionY<=.1
                    && !((EntityPlayer)entity).capabilities.isFlying) {
                double feet=entity.getEntityBoundingBox().minY-pos.getY();
                if (box.maxY>feet && box.maxY-feet<=.5) {
                    // The surface can straddle a block boundary: the new cell's entire
                    // slice may be above the old feet, not just its top edge.
                    if (box.minY>=feet) continue;
                    box=new AxisAlignedBB(box.minX,box.minY,box.minZ,box.maxX,feet,box.maxZ);
                }
            }
            addCollisionBoxToList(pos,entityBox,colliding,box);
        }
    }
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos) { return NULL_AABB; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos) {
        List<AxisAlignedBB> parts=boxes(world,pos,state,0);
        if (parts.isEmpty()) return new AxisAlignedBB(0,0,0,0,0,0);
        AxisAlignedBB result=parts.get(0);
        for (AxisAlignedBB box:parts) result=result.union(box);
        return result;
    }
    @Override public RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,
            net.minecraft.util.math.Vec3d start,net.minecraft.util.math.Vec3d end) {
        RayTraceResult nearest=null;
        for (AxisAlignedBB box:boxes(world,pos,state,0)) {
            RayTraceResult hit=rayTrace(pos,start,end,box);
            if (hit!=null && (nearest==null || start.squareDistanceTo(hit.hitVec)<start.squareDistanceTo(nearest.hitVec))) nearest=hit;
        }
        return nearest;
    }
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,
            EnumHand hand,EnumFacing face,float hitX,float hitY,float hitZ) {
        TileEntity te=world.getTileEntity(pos);
        if (te instanceof TileEntityControlledRamp) {
            BlockPos controller=((TileEntityControlledRamp)te).controller;
            if (world.isBlockLoaded(controller) && world.getBlockState(controller).getBlock() instanceof BlockRampController)
                return world.getBlockState(controller).getBlock().onBlockActivated(world,controller,
                        world.getBlockState(controller),player,hand,face,hitX,hitY,hitZ);
        }
        return true;
    }
    @Override public Item getItemDropped(IBlockState state,Random rand,int fortune) { return net.minecraft.init.Items.AIR; }
    @Override public boolean removedByPlayer(IBlockState state,World world,BlockPos pos,EntityPlayer player,boolean willHarvest) {
        if (!world.isRemote) player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(
                "Retract or remove the Programmable Ramp to restore this platform."),true);
        return false; // Also protect the source journal from creative-mode harvesting.
    }
    @Override public ItemStack getPickBlock(IBlockState state,RayTraceResult target,World world,BlockPos pos,EntityPlayer player) {
        TileEntity te=world.getTileEntity(pos);
        if (!(te instanceof TileEntityControlledRamp)) return ItemStack.EMPTY;
        IBlockState source=((TileEntityControlledRamp)te).source;
        return new ItemStack(source.getBlock(),1,source.getBlock().damageDropped(source));
    }
}
