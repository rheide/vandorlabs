package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityRampController;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The active face scans material; horizontal facing remains the default travel direction. */
public class BlockRampController extends BlockVandorDirectional {
    public static final net.minecraft.block.properties.PropertyDirection ACTIVE_FACE=net.minecraft.block.properties.PropertyDirection.create("active_face");
    public static EnumFacing scanFacing(IBlockState state){EnumFacing face=state.getValue(ACTIVE_FACE);return face.getAxis()==EnumFacing.Axis.Y?face:state.getValue(FACING);}
    public static final net.minecraft.block.properties.PropertyInteger MODE=net.minecraft.block.properties.PropertyInteger.create("mode",0,6);
    public static final String[] MODE_TEXTURES={"ramp","filled_ramp","lift","extend","escalator_up","escalator_down","moat"};
    public static int modeIndex(boolean moat,int escalator,boolean elevator,boolean extend){return moat?6:escalator!=0?Math.max(1,Math.min(2,escalator))+3:elevator?(extend?3:2):(extend?1:0);}
    public static int modeIndex(NBTTagCompound data){return data==null?0:modeIndex(data.getBoolean("MoatMode"),data.getInteger("EscalatorMode"),data.getBoolean("Elevator"),data.getBoolean("ExtendSegments"));}
    public static final net.minecraft.block.properties.PropertyBool ACTIVE=net.minecraft.block.properties.PropertyBool.create("active");
    public BlockRampController() {this("programmable_ramp");}
    protected BlockRampController(String name) {
        super(name);
        setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(ACTIVE_FACE,EnumFacing.NORTH).withProperty(ACTIVE,false).withProperty(MODE,0));
    }
    @Override protected net.minecraft.block.state.BlockStateContainer createBlockState() {
        return new net.minecraft.block.state.BlockStateContainer(this,FACING,ACTIVE_FACE,ACTIVE,MODE);
    }
    @Override public IBlockState getStateFromMeta(int meta) {
        EnumFacing scan=(meta&8)!=0?((meta&1)==0?EnumFacing.UP:EnumFacing.DOWN):EnumFacing.getHorizontal(meta&3);
        return getDefaultState().withProperty(FACING,scan.getAxis()==EnumFacing.Axis.Y?EnumFacing.NORTH:scan).withProperty(ACTIVE_FACE,scan).withProperty(ACTIVE,(meta&4)!=0);
    }
    @Override public int getMetaFromState(IBlockState state) {
        EnumFacing scan=scanFacing(state);return (scan.getAxis()==EnumFacing.Axis.Y?8|(scan==EnumFacing.DOWN?1:0):scan.getHorizontalIndex()) | (state.getValue(ACTIVE)?4:0);
    }
    @Override public IBlockState getActualState(IBlockState state,net.minecraft.world.IBlockAccess world,BlockPos pos){
        state=state.withProperty(ACTIVE_FACE,scanFacing(state));
        TileEntity tile=world.getTileEntity(pos);
        if(tile instanceof TileEntityRampController){TileEntityRampController ramp=(TileEntityRampController)tile;return state.withProperty(MODE,modeIndex(ramp.moat,ramp.escalator,ramp.elevator,ramp.extendSegments));}
        return state.withProperty(MODE,0);
    }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileEntityRampController(); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, int meta, net.minecraft.entity.EntityLivingBase placer) {
        EnumFacing horizontal=DirectionalPlacement.alongPlayer(placer),scan=placer.rotationPitch>45?EnumFacing.DOWN:placer.rotationPitch< -45?EnumFacing.UP:horizontal;
        return getDefaultState().withProperty(FACING,horizontal).withProperty(ACTIVE_FACE,scan);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND || !com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos)) return false;
        if (!world.isRemote) player.openGui(VandorLabs.instance,
                GuiHandler.GUI_RAMP_CONTROLLER,world,pos.getX(),pos.getY(),pos.getZ());
        return true;
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        TileEntity te=world.getTileEntity(pos);
        if (te instanceof TileEntityRampController) ((TileEntityRampController)te).updatePower();
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te=world.getTileEntity(pos);
        if (!world.isRemote && te instanceof TileEntityRampController) ((TileEntityRampController)te).recover(true);
        com.vandorlabs.ramp.ControllerRecovery.controllerChanged(world,pos);
        super.breakBlock(world,pos,state);
    }
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,
            net.minecraft.entity.EntityLivingBase placer,net.minecraft.item.ItemStack stack) {
        TileEntity te=world.getTileEntity(pos);
        if (!world.isRemote && te instanceof TileEntityRampController) {
            TileEntityRampController ramp = (TileEntityRampController) te;
            if (placer instanceof EntityPlayer) ramp.setOwner((EntityPlayer) placer);
            NBTTagCompound settings = stack.hasTagCompound()
                    && stack.getTagCompound().hasKey("RampSettings", 10)
                    ? stack.getTagCompound().getCompoundTag("RampSettings") : null;
            if (settings != null && placer instanceof EntityPlayer) {
                ramp.configureHalfOffsets((EntityPlayer) placer,
                        settings.hasKey("StartOffsetHalfSteps",3)
                                ? settings.getInteger("StartOffsetHalfSteps")
                                : settings.getInteger("StartOffset")*2,
                        settings.hasKey("EndOffsetHalfSteps",3)
                                ? settings.getInteger("EndOffsetHalfSteps")
                                : settings.getInteger("EndOffset")*2,
                        settings.getInteger("TreadPixels"), settings.getBoolean("PowerOn"),
                        settings.getInteger("Speed") == 2, settings.getBoolean("Elevator"),
                        EnumFacing.getHorizontal(settings.getInteger("Direction") & 3),
                        settings.getInteger("TravelAxis"),
                        settings.getBoolean("ExtendSegments"), settings.getInteger("Speed"),
                        !settings.hasKey("MatchTextures") || settings.getBoolean("MatchTextures"),false,settings.getInteger("RampInterpolation"),settings.getInteger("EscalatorMode"),settings.getBoolean("MoatMode"));
                ramp.setRedstoneChannels(com.vandorlabs.redstone.ChannelData.read(settings,settings.getInteger("Channel")));
            } else {if(scanFacing(state).getAxis()==EnumFacing.Axis.Y)ramp.setPlacementDirection(state.getValue(FACING));ramp.updatePower();}
        }
    }
    @Override public ItemStack getPickBlock(IBlockState state, RayTraceResult target,
            World world, BlockPos pos, EntityPlayer player) {
        ItemStack stack = new ItemStack(this);
        TileEntity raw = world.getTileEntity(pos);
        if (raw instanceof TileEntityRampController) {
            TileEntityRampController ramp = (TileEntityRampController) raw;
            NBTTagCompound settings = new NBTTagCompound();
            settings.setBoolean("MoatMode",ramp.moat);settings.setInteger("RampInterpolation",ramp.interpolation);settings.setInteger("EscalatorMode",ramp.escalator);
            settings.setInteger("StartOffset", ramp.startOffset);
            settings.setInteger("EndOffset", ramp.endOffset());
            settings.setInteger("StartOffsetHalfSteps",ramp.startHalfSteps());
            settings.setInteger("EndOffsetHalfSteps",ramp.endHalfSteps());
            settings.setInteger("TreadPixels", ramp.treadPixels);
            settings.setBoolean("PowerOn", ramp.activateOnPower);
            settings.setBoolean("Elevator", ramp.elevator);
            settings.setInteger("Direction", ramp.rampDirection().getHorizontalIndex());
            settings.setInteger("TravelAxis", ramp.travelAxis);
            settings.setBoolean("ExtendSegments", ramp.extendSegments);
            settings.setInteger("Speed", ramp.speed);
            settings.setBoolean("MatchTextures", ramp.matchTextures);
            settings.setInteger("Channel", ramp.getRedstoneChannel());
            com.vandorlabs.redstone.ChannelData.write(settings,ramp.getRedstoneChannels());
            stack.setTagInfo("RampSettings", settings);
        }
        return stack;
    }

    @Override public void updateTick(World world,BlockPos pos,IBlockState state,java.util.Random random) {
        TileEntity te=world.getTileEntity(pos);
        if (te instanceof TileEntityRampController) ((TileEntityRampController)te).scheduledTick();
    }
}
