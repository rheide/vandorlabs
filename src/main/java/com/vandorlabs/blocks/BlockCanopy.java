package com.vandorlabs.blocks;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityCanopy;

import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

import java.util.*;

/** Each occupied cell points to its constituent anchor; narrow shells can join sideways. */
public final class BlockCanopy extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public final String kind;
    public final int width, length, height;

    public BlockCanopy(String id) {
        super(Material.GLASS);
        kind = id;
        int[] size =
                com.vandorlabs.canopy.CanopyMesh.DIMENSIONS.get(
                        id.equals("regular_canopy_glass") ? "regular_1x1_00" : id);
        width = size[0];
        length = size[1];
        height = size[2];
        setRegistryName(VandorLabs.MODID, id);
        setUnlocalizedName(id);
        setCreativeTab(VandorLabs.VANDOR_LABS_TAB);
        setLightOpacity(0);
        setHardness(1.5F);
        setSoundType(SoundType.GLASS);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    public boolean opening() {
        return kind.equals("canopy") || kind.equals("long_canopy");
    }
    public EnumFacing placementFacing(EntityLivingBase player){
        EnumFacing facing=player.getHorizontalFacing();
        return opening() || kind.startsWith("angled_")?facing.getOpposite():facing;
    }
    public BlockPos placementAnchor(BlockPos clicked,EnumFacing facing){
        if(com.vandorlabs.canopy.LargeCanopySettings.supports(kind))return clicked.offset(facing.rotateYCCW(),width/2).down(height/2);
        return com.vandorlabs.canopy.CanopyVariants.centered(kind)?clicked.offset(facing.rotateYCCW(),width/2):clicked;
    }
    public boolean supportsSeatBackrest(){
        return kind.equals("regular_canopy_glass") || opening();
    }
    public boolean sharesSeatBackrest(IBlockState state){
        return supportsSeatBackrest()
                && state.getBlock() instanceof BlockPilotSeat && state.getValue(BlockPilotSeat.UPPER);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState s) {
        return s.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public boolean hasTileEntity(IBlockState s) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World w, IBlockState s) {
        return new TileEntityCanopy();
    }

    @Override
    public net.minecraft.block.material.EnumPushReaction getMobilityFlag(IBlockState s) {
        return net.minecraft.block.material.EnumPushReaction.BLOCK;
    }

    @Override
    public net.minecraft.block.state.BlockFaceShape getBlockFaceShape(
            IBlockAccess w, IBlockState s, BlockPos p, EnumFacing side) {
        return net.minecraft.block.state.BlockFaceShape.UNDEFINED;
    }

    @Override
    public boolean isOpaqueCube(IBlockState s) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState s) {
        return false;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState s) {
        return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public IBlockState getStateForPlacement(
            World w,
            BlockPos p,
            EnumFacing side,
            float x,
            float y,
            float z,
            int meta,
            EntityLivingBase player) {
        return getDefaultState().withProperty(FACING, placementFacing(player));
    }

    public List<BlockPos> cells(BlockPos p, EnumFacing f) {
        return cells(p,f,length);
    }
    public List<BlockPos> cells(BlockPos p,EnumFacing f,int depth) {
        return cells(p,f,depth,kind.equals("space_visor_2x1")?3:width);
    }
    public List<BlockPos> cells(BlockPos p,EnumFacing f,int depth,int occupiedWidth) {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = 0; x < occupiedWidth; x++)
            for (int z = 0; z < depth; z++)
                for (int y = 0; y < height; y++)
                    cells.add(p.offset(f.rotateY(), x).offset(f.getOpposite(), z).up(y));
        return cells;
    }

    public boolean fits(World w, BlockPos p, EnumFacing f) {
        for (BlockPos cell : cells(p, f))
            if (cell.getY() < 0
                    || cell.getY() >= w.getHeight()
                    || !w.getWorldBorder().contains(cell)
                    || !w.isBlockLoaded(cell)
                    || !(w.getBlockState(cell).getBlock().isReplaceable(w, cell) || sharesSeatBackrest(w.getBlockState(cell)))) return false;
        return true;
    }

    @Override
    public void onBlockPlacedBy(
            World w, BlockPos p, IBlockState s, EntityLivingBase entity, ItemStack stack) {
        if (w.isRemote) return;
        TileEntityCanopy root=TileEntityCanopy.at(w,p);
        if(root!=null){root.centeredPlacement=com.vandorlabs.canopy.CanopyVariants.centered(kind);root.hollowPlacement=root.resizable();}
        if(root!=null && root.configurable() && stack.hasTagCompound())root.large.read(stack.getTagCompound());
        List<BlockPos> footprint=root==null?cells(p,s.getValue(FACING)):root.cells();
        if(root!=null)root.setFootprint(footprint);
        for (BlockPos cell : footprint) {
            if (!cell.equals(p)) w.setBlockState(cell, s, 2);
            TileEntityCanopy t = TileEntityCanopy.at(w, cell);
            if (t != null) {
                t.anchor = p;
                if(root!=null){t.centeredPlacement=root.centeredPlacement;t.hollowPlacement=root.hollowPlacement;}
                if(root!=null && root.configurable()){net.minecraft.nbt.NBTTagCompound settings=new net.minecraft.nbt.NBTTagCompound();root.large.write(settings);t.large.read(settings);}
                t.sync();
            }
        }
        TileEntityCanopy t = TileEntityCanopy.at(w, p);
        if (t != null) t.tryPair();
        w.notifyNeighborsOfStateChange(p, this, false);
    }

    @Override
    public boolean onBlockActivated(
            World w,
            BlockPos p,
            IBlockState s,
            EntityPlayer player,
            EnumHand hand,
            EnumFacing side,
            float x,
            float y,
            float z) {
        if (player != null && supportsSeatBackrest() && player.getHeldItem(hand).getItem() == Item.getItemFromBlock(com.vandorlabs.blocks.ModBlocks.PILOT_SEAT)) return false;
        // Let the held canopy use the actual clicked surface, including the rear follower cell.
        if(player!=null && player.getHeldItem(hand).getItem() instanceof CanopyItem)return false;
        TileEntityCanopy selected=TileEntityCanopy.at(w,p);
        if(player!=null && selected!=null && selected.owner().configurable() && player.isSneaking()) {
            if(!w.isRemote && com.vandorlabs.items.ConfigurationAccess.canConfigure(player)){BlockPos owner=selected.owner().getPos();player.openGui(VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_LARGE_CANOPY,w,owner.getX(),owner.getY(),owner.getZ());}return true;
        }
        if (selected==null || !selected.owner().opening()) return false;
        if (!w.isRemote) {
            TileEntityCanopy t = TileEntityCanopy.at(w, p);
            if (t != null) t.owner().toggle();
        }
        return true;
    }

    @Override public void neighborChanged(IBlockState state,World world,BlockPos pos,Block block,BlockPos from){TileEntityCanopy tile=TileEntityCanopy.at(world,pos);if(tile!=null)tile.owner().refreshPower();}

    @Override
    public void breakBlock(World w, BlockPos p, IBlockState state) {
        TileEntityCanopy t = TileEntityCanopy.at(w, p);
        if (t != null && !w.isRemote) t.removeAssembly(state);
        super.breakBlock(w, p, state);
    }

    @Override
    public void harvestBlock(
            World w,
            EntityPlayer player,
            BlockPos p,
            IBlockState s,
            TileEntity tile,
            ItemStack tool) {
        spawnAsEntity(w, p, configuredStack(tile instanceof TileEntityCanopy?(TileEntityCanopy)tile:null));
    }

    @Override
    public void getDrops(
            net.minecraft.util.NonNullList<ItemStack> drops,
            IBlockAccess w,
            BlockPos p,
            IBlockState state,
            int fortune) {
        TileEntityCanopy t =
                w.getTileEntity(p) instanceof TileEntityCanopy
                        ? (TileEntityCanopy) w.getTileEntity(p)
                        : null;
        if (t == null || t.anchor == null || p.equals(t.anchor)) drops.add(configuredStack(t));
    }

    public ItemStack configuredStack(TileEntityCanopy tile){ItemStack stack=new ItemStack(this);if(tile!=null){net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();tile.owner().large.write(tag);stack.setTagCompound(tag);}return stack;}
    @Override public ItemStack getPickBlock(IBlockState state,RayTraceResult target,World world,BlockPos pos,EntityPlayer player){return configuredStack(TileEntityCanopy.at(world,pos));}
    @Override
    public void addCollisionBoxToList(
            IBlockState s,
            World w,
            BlockPos p,
            AxisAlignedBB query,
            List<AxisAlignedBB> boxes,
            Entity e,
            boolean actual) {
        /* Loaded-chunk event supplies surfaces once per owner, including moved shells. */
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(
            IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    public boolean isPassable(IBlockAccess world, BlockPos pos) {
        TileEntityCanopy tile = TileEntityCanopy.at(world, pos);
        return tile != null && tile.owner().opening() && tile.owner().progress == 1;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) {
        return FULL_BLOCK_AABB;
    }

    @Override public AxisAlignedBB getSelectedBoundingBox(IBlockState s,World world,BlockPos pos){
        TileEntityCanopy tile=TileEntityCanopy.at(world,pos);
        return tile==null?new AxisAlignedBB(pos):tile.owner().selectionBounds();
    }

    @Override
    public RayTraceResult collisionRayTrace(
            IBlockState s, World w, BlockPos p, Vec3d start, Vec3d end) {
        TileEntityCanopy t = TileEntityCanopy.at(w, p);
        if(t==null)return super.collisionRayTrace(s,w,p,start,end);
        TileEntityCanopy owner=t.owner();
        if(owner.draws())return owner.trace(start,end);
        // A loaded orphan reservation must remain removable even when it has no
        // visible owner. Never redirect its click to an unrelated canopy kind.
        if(t.anchor!=null && !t.anchor.equals(p) && w.isBlockLoaded(t.anchor)){
            TileEntityCanopy parent=TileEntityCanopy.at(w,t.anchor);
            if(parent==null || parent.block()!=this)return super.collisionRayTrace(s,w,p,start,end);
        }
        return null;
    }

    public static final class CanopyItem extends ItemBlock {
        public CanopyItem(BlockCanopy b) {
            super(b);
        }

        @Override
        public boolean canPlaceBlockOnSide(World world,BlockPos pos,EnumFacing side,EntityPlayer player,ItemStack stack){
            BlockCanopy canopy=(BlockCanopy)block;
            if(canopy.sharesSeatBackrest(world.getBlockState(pos)) || side==EnumFacing.UP && canopy.sharesSeatBackrest(world.getBlockState(pos.up())))return true;
            return super.canPlaceBlockOnSide(world,pos,side,player,stack);
        }

        @Override
        public EnumActionResult onItemUse(
                EntityPlayer player,
                World w,
                BlockPos p,
                EnumHand hand,
                EnumFacing side,
                float x,
                float y,
                float z) {
            BlockCanopy b=(BlockCanopy)block;
            TileEntityCanopy clickedCanopy=TileEntityCanopy.at(w,p);
            if(clickedCanopy!=null){
                p=new BlockPos(new Vec3d(p).addVector(x,y,z).subtract(new Vec3d(side.getDirectionVec()).scale(.0001)));
            }
            ItemStack held=player.getHeldItem(hand),configured=held.copy();
            com.vandorlabs.canopy.LargeCanopySettings settings=new com.vandorlabs.canopy.LargeCanopySettings();
            if(held.hasTagCompound())settings.read(held.getTagCompound());
            boolean large=com.vandorlabs.canopy.LargeCanopySettings.supports(b.kind);
            settings.mount=large?(side==EnumFacing.UP?2:side==EnumFacing.DOWN?3:0):side==EnumFacing.DOWN?1:0;
            if(!large && clickedCanopy!=null && side.getAxis().isHorizontal() && clickedCanopy.owner().large.mount<2)settings.mount=clickedCanopy.owner().large.mount;
            net.minecraft.nbt.NBTTagCompound tag=configured.hasTagCompound()?configured.getTagCompound():new net.minecraft.nbt.NBTTagCompound();settings.write(tag);configured.setTagCompound(tag);
            EnumFacing facing=side.getAxis().isHorizontal() && (large || b.opening() || b.kind.startsWith("angled_"))?(large?side.getOpposite():side):b.placementFacing(player);
            BlockPos dest=w.getBlockState(p).getBlock().isReplaceable(w,p) || b.sharesSeatBackrest(w.getBlockState(p))?p:p.offset(side);
            if(large && settings.mount>=2)dest=dest.offset(facing.rotateYCCW(),b.width/2).offset(facing,b.width/2);
            else dest=b.placementAnchor(dest,facing);
            com.vandorlabs.tiles.TileEntityCockpit cockpit=com.vandorlabs.cockpit.CockpitAssembly.root(w,p);
            if(cockpit!=null&&b.supportsSeatBackrest()){
                // The whole cockpit has an upper reservation, including cells that look empty.
                // Mount the shell above that envelope without replacing its controller or helpers.
                facing=cockpit.facing();
                dest=new BlockPos(p.getX(),cockpit.getPos().getY()+(int)Math.ceil(cockpit.data().bounds.maxY),p.getZ());
            }
            // The clicked wall supports the rear of a long nose, rather than its front.
            if(cockpit==null && !large && (b.kind.startsWith("angled_") || b.opening()) && side.getAxis().isHorizontal() && side==facing)dest=dest.offset(facing,b.length-1);
            for(BlockPos cell:com.vandorlabs.canopy.LargeCanopyOwnership.itemCells(b,dest,facing,configured))
                if(!w.isBlockModifiable(player,cell) || !player.canPlayerEdit(cell,side,held))return EnumActionResult.FAIL;
            if(!com.vandorlabs.canopy.LargeCanopyOwnership.fitsItem(b,w,dest,facing,configured))return EnumActionResult.FAIL;
            IBlockState state=b.getDefaultState().withProperty(FACING,facing);
            if(held.isEmpty() || !placeBlockAt(configured,player,w,dest,side,x,y,z,state))return EnumActionResult.FAIL;
            SoundType sound=b.getSoundType(state,w,dest,player);
            w.playSound(player,dest,sound.getPlaceSound(),SoundCategory.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);
            if(!player.capabilities.isCreativeMode)held.shrink(1);
            return EnumActionResult.SUCCESS;
        }
    }
}
