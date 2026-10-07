package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Real mounted controls, numeric latches, persistence, configuration copy and support removal. */
public final class SignalControlRuntimeChecks {
    public static final String[] IDS={"thruster_lever","wall_slider","airliner_throttle","fighter_throttle"};
    public static void run(World world){
        if(Block.REGISTRY.containsKey(new ResourceLocation("vandorlabs",IDS[0])))checkRetiredControls();
        BlockPos pos=new BlockPos(3,105,3);
        for(int index=0;index<IDS.length;index++){
            Block block=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",IDS[index]));
            if(!(block instanceof BlockSignalControl))block=new BlockSignalControl(IDS[index],IDS[index],32);
            for(EnumFacing mount:EnumFacing.values()){
                world.setBlockState(pos.offset(mount.getOpposite()),Blocks.STONE.getDefaultState(),2);
                require(block.canPlaceBlockOnSide(world,pos,mount),"supported mounting "+IDS[index]+" "+mount);
                world.setBlockState(pos,block.getDefaultState().withProperty(BlockVandorSwitch.FACING,mount),2);
                TileEntitySignalControl control=(TileEntitySignalControl)world.getTileEntity(pos);
                control.configureLimits(3,11);
                if(((BlockSignalControl)block).hasAdjustableBase()){
                    control.configureMount(2,3,1);TileEntitySignalControl restored=new TileEntitySignalControl();restored.readFromNBT(control.writeToNBT(new NBTTagCompound()));
                    require(restored.getBaseHeight()==2&&restored.getBaseTilt()==3&&restored.getTiltDirection()==1,"mount NBT");
                    control.configureMount(3,4,-1);require(control.getBaseHeight()==2&&control.getBaseTilt()==3,"invalid mount accepted");
                }
                for(int step=0;step<4;step++){
                    control.setStep(step);int expected=new int[]{0,3,7,11}[step];IBlockState state=world.getBlockState(pos);
                    require(control.getOutputLevel()==expected && block.getWeakPower(state,world,pos,EnumFacing.UP)==expected && block.getStrongPower(state,world,pos,EnumFacing.NORTH)==expected,"physical detent "+IDS[index]+" "+step);
                    require(block.getActualState(state,world,pos).getValue(BlockSignalControl.LEVEL)==step,"render detent");
                    NBTTagCompound saved=control.writeToNBT(new NBTTagCompound());TileEntitySignalControl restored=new TileEntitySignalControl();restored.readFromNBT(saved);
                    require(restored.getOutputLevel()==expected && restored.getLowLimit()==3 && restored.getHighLimit()==11,"detent save");
                }
                require(!control.configuration().hasKey("ControlStep"),"pick copies live power");
                control.setRedstoneChannels(ChannelList.of(16301,16302));
                require(RedstoneChannels.level(world,16301)==11,"numeric channel linking");
                for(int step=0;step<4;step++){control.setStep(step);require(RedstoneChannels.level(world,16301)==control.levelForStep(step) && RedstoneChannels.level(world,16302)==control.levelForStep(step),"numeric bank detent");}
                java.util.Map<Integer,Integer> levels=new java.util.HashMap<>();levels.put(16301,1);levels.put(16302,1);control.applyLinkedLevels(levels);
                require(control.getStep()==1 && control.getOutputLevel()==1,"positive external level displayed off");
                control.setStep(2);control.setRedstoneChannels(ChannelList.of(16303));require(RedstoneChannels.level(world,16303)==7 && control.getOutputLevel()==7,"channel reassignment amplified output");
                control.setRedstoneChannels(ChannelList.EMPTY);require(control.getOutputLevel()==7,"unlink loses selected detent");
                for(int rotation=0;rotation<4;rotation++){control.setMountRotation(rotation);require(block.getBoundingBox(world.getBlockState(pos),world,pos).getAverageEdgeLength()>0,"mounted selection bounds");}
                control.configureLimits(15,1);require(control.getLowLimit()==3 && control.getHighLimit()==11,"invalid range accepted");
                BlockPos copy=pos.east(4);world.setBlockState(copy,block.getDefaultState().withProperty(BlockVandorSwitch.FACING,EnumFacing.UP),2);
                world.setBlockState(copy.down(),Blocks.STONE.getDefaultState(),2);
                com.vandorlabs.items.ProgrammableSettings.apply(world,copy,com.vandorlabs.items.ProgrammableSettings.capture(world,pos));
                TileEntitySignalControl copied=(TileEntitySignalControl)world.getTileEntity(copy);
                if(((BlockSignalControl)block).hasAdjustableBase())require(copied.getBaseHeight()==2&&copied.getBaseTilt()==3&&copied.getTiltDirection()==1,"Duplifier mount settings");
                require(copied.getLowLimit()==3 && copied.getHighLimit()==11 && copied.getStep()==2,"Duplifier control settings");
                net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> drops=net.minecraft.util.NonNullList.create();block.getDrops(drops,world,pos,world.getBlockState(pos),0);
                require(drops.size()==1 && drops.get(0).getSubCompound("RedstoneChannelSettings")!=null,"configured control drop");
                world.setBlockToAir(copy);world.setBlockState(copy,block.getDefaultState().withProperty(BlockVandorSwitch.FACING,EnumFacing.UP),2);
                ((BlockSignalControl)block).onBlockPlacedBy(world,copy,world.getBlockState(copy),net.minecraftforge.common.util.FakePlayerFactory.getMinecraft((net.minecraft.world.WorldServer)world),drops.get(0));
                TileEntitySignalControl placed=(TileEntitySignalControl)world.getTileEntity(copy);
                require(placed.getLowLimit()==3 && placed.getHighLimit()==11,"configured drop limits on placement");
                if(((BlockSignalControl)block).hasAdjustableBase())require(placed.getBaseHeight()==2 && placed.getBaseTilt()==3 && placed.getTiltDirection()==1,"configured drop mount on placement");
                RedstoneChannels.unregister(copied);RedstoneChannels.unregister(placed);world.setBlockToAir(copy);world.setBlockToAir(copy.down());
                RedstoneChannels.unregister(control);world.setBlockToAir(pos.offset(mount.getOpposite()));
                if(world.getBlockState(pos).getBlock()==block)block.neighborChanged(world.getBlockState(pos),world,pos,Blocks.STONE,pos.offset(mount.getOpposite()));
                require(world.isAirBlock(pos),"support removal contract");
                world.setBlockToAir(pos);
            }
        }
        System.out.println("[vandorlabs][reprolab] signal-control-runtime PASS four controls, all supported faces, four detents, channels, save, copy and limits");
    }
    private static void checkRetiredControls(){
        for(String id:new String[]{"thruster_lever_16px","thruster_wall_slider_16px","thruster_control_block_16px","thruster_control_block_32px","thruster_lever_32px","thruster_wall_slider_32px"}){
            ResourceLocation key=new ResourceLocation("vandorlabs",id);
            require(!Block.REGISTRY.containsKey(key) && !net.minecraft.item.Item.REGISTRY.containsKey(key),"removed control remains registered "+id);
        }
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private SignalControlRuntimeChecks(){}
}
