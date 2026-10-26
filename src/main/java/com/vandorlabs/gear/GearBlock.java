package com.vandorlabs.gear;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * World cells stay at their construction positions. Formed cells suppress
 * their static baked model; the controller's FastTESR renders the one group.
 */
public class GearBlock extends com.vandorlabs.blocks.BlockLandingGear implements ITileEntityProvider {
    public enum Kind { EXTENDER, SWING_ARM, MOUNT, PLATE, SKI, WHEEL, TRACK }

    public static final PropertyDirection FACING = com.vandorlabs.blocks.BlockVandorDirectional.FACING;
    public static final PropertyBool ASSEMBLED = PropertyBool.create("assembled");
    private final Kind kind;

    public GearBlock(String name, Kind kind) {
        super(name,false);
        this.kind = kind;
        setUnlocalizedName(com.vandorlabs.VandorLabs.MODID + "." + name);
        setCreativeTab(com.vandorlabs.VandorLabs.VANDOR_LABS_TAB);
        setHardness(2.5F);
        setResistance(8.0F);
        setSoundType(SoundType.METAL);
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH)
                .withProperty(ASSEMBLED, false));
    }

    public Kind kind() { return kind; }
    public int width(){return 1;}
    public int sizeClass(){return width();}
    public BlockPos componentRoot(BlockPos pos,IBlockState state){return pos;}
    public java.util.List<BlockPos> componentCells(World world,BlockPos pos){return java.util.Collections.singletonList(pos);}
    public boolean componentIntact(World world,BlockPos pos){return true;}
    public String wheelModel(){return width()==2?"wheel_large":"wheel";}


    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, ASSEMBLED);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3))
                .withProperty(ASSEMBLED, (meta & 4) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(ASSEMBLED) ? 4 : 0);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        return getStateForPlacement(world,pos,side,hitX,hitY,hitZ,meta,placer);
    }

    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer){
        IBlockState clicked=world.getBlockState(pos.offset(side.getOpposite()));
        EnumFacing facing=((kind==Kind.SKI||kind==Kind.WHEEL)&&clicked.getBlock()==this || sizeClass()==2 && clicked.getBlock() instanceof GearBlock && ((GearBlock)clicked.getBlock()).sizeClass()==2)?clicked.getValue(FACING):placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING,facing);
    }

    @Override
    public int damageDropped(IBlockState state) { return 0; }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) { return new GearTileEntity(); }

    @Override
    public boolean hasTileEntity(IBlockState state) { return true; }

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }

    @Override
    public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public BlockRenderLayer getBlockLayer() { return BlockRenderLayer.CUTOUT; }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        TileEntity raw=world.getTileEntity(pos);if(!world.isRemote&&raw instanceof GearTileEntity&&stack.hasTagCompound()&&stack.getTagCompound().hasKey("ModularGearSettings",10))((GearTileEntity)raw).configure(stack.getTagCompound().getCompoundTag("ModularGearSettings"));
        if(!world.isRemote&&raw instanceof GearTileEntity)((GearTileEntity)raw).operator(placer instanceof EntityPlayer?(EntityPlayer)placer:null);
        if(!world.isRemote&&raw instanceof GearTileEntity&&stack.hasTagCompound()&&stack.getTagCompound().hasKey("ArmRecovery",10)){
            net.minecraft.nbt.NBTTagCompound saved=stack.getTagCompound().getCompoundTag("ArmRecovery").copy();saved.setInteger("x",pos.getX());saved.setInteger("y",pos.getY());saved.setInteger("z",pos.getZ());((GearTileEntity)raw).readFromNBT(saved);((GearTileEntity)raw).operator(placer instanceof EntityPlayer?(EntityPlayer)placer:null);
        }
        GearTileEntity.requestNearbyRebuild(world, pos);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block neighbor, BlockPos fromPos) {
        GearTileEntity.requestNearbyRebuild(world, pos);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof GearTileEntity) {
            GearTileEntity gear=(GearTileEntity)tile;if(gear.isController()||gear.hasRetainedPayload())gear.recoverOnBreak(this);
            gear.releaseAssembly();
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                     EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if(hand!=EnumHand.MAIN_HAND)return false;
        boolean configure=player.getHeldItem(hand).getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER;
        if(!configure&&!player.getHeldItem(hand).isEmpty())return false;
        if(!world.isRemote){
            TileEntity tile=world.getTileEntity(pos);
            if(tile instanceof GearTileEntity){
                GearTileEntity root=((GearTileEntity)tile).loadedController();
                if(root==null){BlockPos top=GearAssembly.topOfColumn(world,pos);TileEntity candidate=top==null?null:world.getTileEntity(top);if(candidate instanceof GearTileEntity){((GearTileEntity)candidate).rebuild();root=((GearTileEntity)candidate).loadedController();}}
                if(root==null)player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Complete the mounting struts and connected feet first"),true);
                else if(configure){BlockPos at=pos;player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_MODULAR_GEAR,world,at.getX(),at.getY(),at.getZ());}
                else if(com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos))root.target(!root.target());
            }
        }
        return true;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        if(kind==Kind.WHEEL)return new AxisAlignedBB(.15,0,.15,.85,.75,.85);
        if (kind == Kind.PLATE) return new AxisAlignedBB(0, 0, 0, 1, 3.0 / 16, 1);
        if (kind == Kind.SKI) return new AxisAlignedBB(0, 0, 0, 1, 5.0 / 16, 1);
        return FULL_BLOCK_AABB;
    }

    @Override public net.minecraft.util.math.RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,net.minecraft.util.math.Vec3d start,net.minecraft.util.math.Vec3d end){
        // The legacy superclass has an empty model-parts list for modular gear.
        // Trace the modular pose instead, clipped to this reserved source cell.
        TileEntity raw=world.getTileEntity(pos);GearTileEntity root=raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;
        if(root!=null&&root.motionBlocked())return rayTrace(pos,start,end,getBoundingBox(state,world,pos));
        if(root!=null&&root.isOperational()){
            net.minecraft.util.math.RayTraceResult nearest=null;AxisAlignedBB cell=new AxisAlignedBB(pos);
            for(AxisAlignedBB box:root.collisionBoxes())if(box.intersects(cell)){
                net.minecraft.util.math.RayTraceResult hit=box.intersect(cell).calculateIntercept(start,end);
                if(hit!=null&&(nearest==null||start.squareDistanceTo(hit.hitVec)<start.squareDistanceTo(nearest.hitVec)))nearest=new net.minecraft.util.math.RayTraceResult(hit.hitVec,hit.sideHit,pos);
            }
            return nearest;
        }
        return rayTrace(pos,start,end,getBoundingBox(state,world,pos));
    }

    private boolean collidable(IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return !(tile instanceof GearTileEntity) || ((GearTileEntity) tile).isPartCollidable();
    }

    @Override
    @Nullable
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity member=world.getTileEntity(pos);GearTileEntity active=member instanceof GearTileEntity?((GearTileEntity)member).loadedController():null;if(active!=null&&active.isOperational())return NULL_AABB;
        if (collidable(world, pos)) return getBoundingBox(state, world, pos);
        TileEntity raw = world.getTileEntity(pos);
        if ((kind == Kind.EXTENDER || kind == Kind.SWING_ARM || kind == Kind.MOUNT)
                && raw instanceof GearTileEntity && ((GearTileEntity) raw).isController()) {
            return new AxisAlignedBB(0, 6.0 / 16, 0, 1, 1, 1);
        }
        return NULL_AABB;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
                                     List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        TileEntity part=world.getTileEntity(pos);
        GearTileEntity root=part instanceof GearTileEntity?((GearTileEntity)part).loadedController():null;
        if(root!=null&&root.isOperational()){
            if(world instanceof com.vandorlabs.vehicle.VehicleWorld&&part==root&&!GearGeometry.excluded(root))for(AxisAlignedBB box:root.collisionBoxes())if(box.getAverageEdgeLength()>0&&box.intersects(entityBox))boxes.add(box);
            return;
        }
        if (!collidable(world, pos)) {
            TileEntity raw = world.getTileEntity(pos);
            if ((kind == Kind.EXTENDER || kind == Kind.SWING_ARM || kind == Kind.MOUNT)
                    && raw instanceof GearTileEntity && ((GearTileEntity) raw).isController()) {
                addCollisionBoxToList(pos, entityBox, boxes,
                        new AxisAlignedBB(0, 14.0 / 16, 0, 1, 1, 1));
                addCollisionBoxToList(pos, entityBox, boxes,
                        new AxisAlignedBB(3.0 / 16, 6.0 / 16, 3.0 / 16, 13.0 / 16, 14.0 / 16, 13.0 / 16));
            }
            return;
        }
        if(kind==Kind.WHEEL){addCollisionBoxToList(pos,entityBox,boxes,getBoundingBox(state,world,pos));
        } else if (kind == Kind.PLATE) {
            addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(0, 0, 0, 1, 3.0 / 16, 1));
        } else if (kind == Kind.SKI) {
            boolean alongZ = state.getValue(FACING).getAxis() == EnumFacing.Axis.Z;
            addCollisionBoxToList(pos, entityBox, boxes, alongZ
                    ? new AxisAlignedBB(4.0 / 16, 0, 0, 12.0 / 16, 5.0 / 16, 1)
                    : new AxisAlignedBB(0, 0, 4.0 / 16, 1, 5.0 / 16, 12.0 / 16));
            addCollisionBoxToList(pos, entityBox, boxes, alongZ
                    ? new AxisAlignedBB(0, 3.0 / 16, 6.0 / 16, 1, 5.0 / 16, 10.0 / 16)
                    : new AxisAlignedBB(6.0 / 16, 3.0 / 16, 0, 10.0 / 16, 5.0 / 16, 1));
        } else {
            TileEntity raw = world.getTileEntity(pos);
            GearTileEntity tile = raw instanceof GearTileEntity ? (GearTileEntity) raw : null;
            boolean mount = tile == null || !state.getValue(ASSEMBLED) || tile.isController()
                    || !tile.hasLoadedController();
            if (kind == Kind.SWING_ARM) {
                int turns = tile != null && tile.isOperational() ? tile.direction().ordinal()
                        : GearCollision.quarterTurns(state.getValue(FACING));
                GearCollision.addSwingArm(pos, entityBox, boxes, turns, false);
            } else {
                addCollisionBoxToList(pos, entityBox, boxes,
                        new AxisAlignedBB(5.0 / 16, 0, 5.0 / 16, 11.0 / 16, mount ? 14.0 / 16 : 1, 11.0 / 16));
            }
            if (mount) {
                addCollisionBoxToList(pos, entityBox, boxes,
                        new AxisAlignedBB(0, 14.0 / 16, 0, 1, 1, 1));
                addCollisionBoxToList(pos, entityBox, boxes,
                        new AxisAlignedBB(3.0 / 16, 6.0 / 16, 3.0 / 16, 13.0 / 16, 14.0 / 16, 13.0 / 16));
            }
        }
        if (kind == Kind.PLATE || kind == Kind.SKI) addFootConnectionCollision(world, pos, entityBox, boxes);
    }

    private void addFootConnectionCollision(World world, BlockPos pos, AxisAlignedBB entityBox,
                                             List<AxisAlignedBB> boxes) {
        // The final part of either linkage occupies the anchor foot's cell.
        // Clipping it here prevents below-block geometry from depending on
        // whether an entity's collision query happens to visit the arm cell.
        BlockPos above = pos.up();
        if (!GearAssembly.loaded(world, above)) return;
        IBlockState aboveState = world.getBlockState(above);
        if (!GearAssembly.isExtender(aboveState)) return;
        TileEntity raw = world.getTileEntity(above);
        GearTileEntity tile = raw instanceof GearTileEntity ? (GearTileEntity) raw : null;
        GearTileEntity root = tile == null ? null : tile.loadedController();
        if (root != null && root.isOperational() && !root.fullyDeployed()) return;
        if (GearAssembly.isSwingArm(aboveState)) {
            int turns = root != null && root.isOperational() ? root.direction().ordinal()
                    : GearCollision.quarterTurns(aboveState.getValue(FACING));
            GearCollision.addSwingArm(pos, entityBox, boxes, turns, true);
        } else if (root != null && root.isOperational()) {
            addCollisionBoxToList(pos, entityBox, boxes,
                    new AxisAlignedBB(4.0 / 16, 3.0 / 16, 4.0 / 16, 12.0 / 16, 5.0 / 16, 12.0 / 16));
            addCollisionBoxToList(pos, entityBox, boxes,
                    new AxisAlignedBB(5.0 / 16, 5.0 / 16, 5.0 / 16, 11.0 / 16, 1, 11.0 / 16));
        }
    }
    public static java.util.List<AxisAlignedBB> movingArmParts(){return loadParts("block/gear/swing_linkage",8);}
    public static java.util.List<AxisAlignedBB> nativeModelParts(String path){return loadParts(path);}
    public static java.util.List<AxisAlignedBB> modelParts(String name){return loadParts("block/gear/"+name);}
    @Override public void harvestBlock(World world,EntityPlayer player,BlockPos pos,IBlockState state,TileEntity tile,ItemStack tool){
        if(tile instanceof GearTileEntity&&((GearTileEntity)tile).recoveryDropped())return;
        net.minecraft.stats.StatBase stat=net.minecraft.stats.StatList.getBlockStats(this);if(stat!=null)player.addStat(stat);player.addExhaustion(.005F);
        ItemStack drop=new ItemStack(this);if(tile instanceof GearTileEntity)drop.setTagInfo("ModularGearSettings",((GearTileEntity)tile).settings());spawnAsEntity(world,pos,drop);
    }
    @Override public IBlockState withRotation(IBlockState state,net.minecraft.util.Rotation rotation){return state.withProperty(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public IBlockState withMirror(IBlockState state,net.minecraft.util.Mirror mirror){return state.withProperty(FACING,mirror.mirror(state.getValue(FACING)));}

    @Override public double vehicleBottomY(IBlockAccess world,BlockPos pos){TileEntity raw=world.getTileEntity(pos);GearTileEntity root=raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;if(root!=null&&root.isExtendedTrackStroke()&&root.landingDeployed()){double y=Double.POSITIVE_INFINITY;for(AxisAlignedBB box:root.collisionBoxes())y=Math.min(y,box.minY);if(Double.isFinite(y))return y;}return root!=null&&root.assembly()!=null?root.getPos().getY()-root.assembly().columnHeight()-(root.armSize()==2 && root.assembly().kind()==com.vandorlabs.gear.core.GearTopology.FootKind.WHEEL?1:0):pos.getY()-(kind==Kind.WHEEL && width()==2?1:0);}

    @Override public ItemStack getPickBlock(IBlockState state,net.minecraft.util.math.RayTraceResult hit,World world,BlockPos pos,EntityPlayer player){ItemStack stack=new ItemStack(this);TileEntity raw=world.getTileEntity(pos);GearTileEntity root=raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;if(root!=null)stack.setTagInfo("ModularGearSettings",root.settings());return stack;}
    @Override public void getDrops(net.minecraft.util.NonNullList<ItemStack> drops,IBlockAccess world,BlockPos pos,IBlockState state,int fortune){ItemStack stack=new ItemStack(this);TileEntity raw=world.getTileEntity(pos);if(raw instanceof GearTileEntity&&((GearTileEntity)raw).hasRetainedPayload())return;GearTileEntity root=raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;if(root!=null)stack.setTagInfo("ModularGearSettings",root.settings());drops.add(stack);}

}
