package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.turntable.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Actual placement/restore and channel edges, including held signals across saves. */
final class TurntableFollowupRuntimeChecks {
    static void run(WorldServer world,EntityPlayerMP player)throws Exception{
        BlockPos seed=new BlockPos(520,205,170),owner=seed.west();
        BlockTurntableSurface surface=(BlockTurntableSurface)Block.getBlockFromName("vandorlabs:turntable_surface");
        BlockTurntableController controller=(BlockTurntableController)Block.getBlockFromName("vandorlabs:turntable_controller");
        Source first=new Source(world,seed.up(8),65011),second=new Source(world,seed.up(9),65012);
        ItemStack held=player.getHeldItemMainhand();
        try{
            for(int x=-2;x<=5;x+=7)for(int z=-2;z<=5;z+=7)world.getChunkFromBlockCoords(seed.add(x,0,z));
            for(int x=0;x<3;x++)for(int z=0;z<3;z++)world.setBlockState(seed.add(x,0,z),surface.getDefaultState());
            for(EnumFacing side:EnumFacing.HORIZONTALS){BlockPos at=seed.offset(side);require(controller.getStateForPlacement(world,at,side,.5F,.5F,.5F,0,player).getValue(BlockVandorDirectional.FACING)==side.getOpposite(),"controller points back to clicked surface "+side);}
            world.setBlockState(owner,controller.getStateForPlacement(world,owner,EnumFacing.WEST,.5F,.5F,.5F,0,player));
            TileEntityTurntableController tile=(TileEntityTurntableController)world.getTileEntity(owner);tile.height=1;
            String[] suffix={"","_bottom_right","_top_right","_top_left"};
            for(int i=0;i<8;i++)world.setBlockState(seed.add(i%3,1,i/3),Block.getBlockFromName("vandorlabs:rocket_thruster_wedge"+suffix[i%4]).getDefaultState().withProperty(BlockPropulsionLight.FACING,i<4?EnumFacing.UP:EnumFacing.DOWN));
            tile.scan(player);tile.display().setAngle(90);
            player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(Blocks.LEVER));
            require(!controller.onBlockActivated(world,owner,world.getBlockState(owner),player,EnumHand.MAIN_HAND,EnumFacing.WEST,.5F,.5F,.5F),"controller permits attached items after capture");
            require(player.interactionManager.processRightClickBlock(player,world,player.getHeldItemMainhand(),EnumHand.MAIN_HAND,owner,EnumFacing.WEST,.5F,.5F,.5F)==EnumActionResult.SUCCESS&&world.getBlockState(owner.west()).getBlock()==Blocks.LEVER,"real lever placement against captured controller");
            TurntableSnapshot snapshot=tile.display().snapshot,expected=snapshot.rotated(90,tile.display().view);
            for(com.vandorlabs.vehicle.VehicleStructure.Cell cell:snapshot.blocks.cells){
                IBlockState before=cell.state,after=before.withRotation(Rotation.CLOCKWISE_90),cycle=before;
                for(int n=0;n<4;n++)cycle=cycle.withRotation(Rotation.CLOCKWISE_90);
                require(cycle==before&&after.getBlock()!=before.getBlock(),"vertical wedge corner rotates and round trips");
            }
            tile.restore(player);for(com.vandorlabs.vehicle.VehicleStructure.Cell cell:expected.blocks.cells)require(world.getBlockState(expected.origin.add(cell.pos))==cell.state,"quarter-turn restores wedge corners");
            for(BlockPos p:tile.layout.cells)require(!BlockTurntableSurface.locked(world,p),"Restore releases every surface lock");
            RedstoneChannels.register(first);RedstoneChannels.register(second);
            NBTTagCompound settings=tile.writeToNBT(new NBTTagCompound());settings.setString("DirectionChannels","65011,65012");tile.settings(settings);tile.update();boolean original=tile.clockwise;
            first.on=true;RedstoneChannels.inputChanged(first);tile.update();require(tile.clockwise==original,"direction waits for all channels");
            second.on=true;RedstoneChannels.inputChanged(second);tile.update();require(tile.clockwise!=original,"rising direction pulse reverses once");
            for(int n=0;n<40;n++)tile.update();require(tile.clockwise!=original,"held pulse does not retrigger");
            TileEntityTurntableController saved=new TileEntityTurntableController();saved.readFromNBT(tile.writeToNBT(new NBTTagCompound()));saved.setWorld(world);saved.setPos(owner);saved.update();require(saved.clockwise==tile.clockwise,"held direction pulse survives save/load");
            first.on=false;RedstoneChannels.inputChanged(first);tile.update();require(tile.clockwise!=original,"falling edge does not reverse");
            first.on=true;RedstoneChannels.inputChanged(first);tile.update();require(tile.clockwise==original,"next pulse reverses again");
            tile.directionChannels=ChannelList.EMPTY;tile.update();first.on=false;RedstoneChannels.inputChanged(first);first.on=true;RedstoneChannels.inputChanged(first);tile.update();require(tile.clockwise==original,"zero disables direction pulses");
            first.on=false;second.on=false;RedstoneChannels.inputChanged(first);RedstoneChannels.inputChanged(second);
            settings=tile.writeToNBT(new NBTTagCompound());settings.setString("RestoreChannels","65011,65012");tile.settings(settings);tile.scan(player);tile.update();
            first.on=true;RedstoneChannels.inputChanged(first);tile.update();require(tile.captureId!=null,"Restore waits for all assigned channels");
            second.on=true;RedstoneChannels.inputChanged(second);tile.update();require(tile.captureId==null&&tile.display()==null,"Restore channel rising pulse returns blocks");
            tile.scan(player);for(int n=0;n<10;n++)tile.update();require(tile.captureId!=null,"held Restore pulse does not consume a subsequent capture");
            saved=new TileEntityTurntableController();saved.readFromNBT(tile.writeToNBT(new NBTTagCompound()));saved.setWorld(world);saved.setPos(owner);saved.update();
            require(saved.restoreChannels.equals(tile.restoreChannels)&&tile.captureId!=null,"held Restore pulse and channels survive save/load without replay");
            first.on=false;RedstoneChannels.inputChanged(first);tile.update();first.on=true;RedstoneChannels.inputChanged(first);tile.update();require(tile.captureId==null,"next Restore pulse returns a subsequent capture");
            settings=tile.writeToNBT(new NBTTagCompound());settings.setString("RestoreChannels","0");tile.settings(settings);tile.scan(player);tile.update();
            first.on=false;RedstoneChannels.inputChanged(first);tile.update();first.on=true;RedstoneChannels.inputChanged(first);tile.update();require(tile.captureId!=null,"zero disables Restore pulses");tile.restore(player);
            for(double[] migration:new double[][]{{.1,.5},{.6,.5},{1.25,1.5},{10.2,10},{30,10},{Double.NaN,1}}){NBTTagCompound legacy=tile.writeToNBT(new NBTTagCompound());legacy.setDouble("RPM",migration[0]);TileEntityTurntableController migrated=new TileEntityTurntableController();migrated.readFromNBT(legacy);require(migrated.rpm==migration[1],"legacy RPM migrates to half-step range");}

        }finally{player.setHeldItem(EnumHand.MAIN_HAND,held);RedstoneChannels.unregister(first);RedstoneChannels.unregister(second);for(BlockPos at:BlockPos.getAllInBox(seed.add(-3,-2,-3),seed.add(5,3,5)))if(!world.isAirBlock(at))world.setBlockToAir(at);}
        for(String family:new String[]{"rocket_thruster","ion_drive","plasma_vent","impulse_engine"})for(String suffix:new String[]{"","_bottom_right","_top_right","_top_left"})for(EnumFacing face:EnumFacing.values()){
            IBlockState state=Block.getBlockFromName("vandorlabs:"+family+"_wedge"+suffix).getDefaultState().withProperty(BlockPropulsionLight.FACING,face),cycle=state;
            for(int n=0;n<4;n++)cycle=cycle.withRotation(Rotation.CLOCKWISE_90);require(cycle==state,"all wedge families round trip "+family+face);
        }
        System.out.println("[vandorlabs][reprolab] turntable-followups PASS controller orientation lever attachment exact surface locks wedge restore direction and Restore pulses held-save/load half-step RPM migration");
    }
    private static final class Source extends TileEntity implements RedstoneChannelMember{
        final int channel;boolean on;Source(WorldServer world,BlockPos pos,int channel){this.channel=channel;setWorld(world);setPos(pos);}
        public boolean isChannelMemberActive(){return !isInvalid();}public TileEntity channelTile(){return this;}public int getRedstoneChannel(){return channel;}public void setRedstoneChannel(int value){}public boolean hasLocalRedstoneSignal(){return on;}public void setChannelSignal(boolean value){}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("turntable follow-up: "+message);}
}
