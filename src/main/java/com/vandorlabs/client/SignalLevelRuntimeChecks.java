package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Exercises numeric settlement and actual receivers in a loaded world. */
public final class SignalLevelRuntimeChecks {
    public static void run(World world) {
        BlockPos screenPos=new BlockPos(2,100,2),lightPos=screenPos.east(3),triggerPos=screenPos.east(6),enginePos=screenPos.east(9);
        Source source=new Source(world,screenPos.up(3));
        try {
            world.setBlockState(screenPos,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),2);
            RedstoneScreenContents screen=((TileEntityAnimatedScreenSelector)world.getTileEntity(screenPos)).redstoneScreen(0);
            NBTTagList rows=new NBTTagList();NBTTagCompound row=new NBTTagCompound();
            row.setString("Label","Power");ChannelData.write(row,ChannelList.of(16001));row.setBoolean("Slider",true);row.setInteger("Min",0);row.setInteger("Max",15);rows.appendTag(row);
            require(screen.applyRowConfiguration(rows),"slider configuration rejected");
            RedstoneScreenContents.Row slider=screen.rows().get(0);
            int[] expected={0,2,4,6,9,11,13,15};
            for(int i=0;i<8;i++)require(slider.segmentValue(i)==expected[i],"segment rounding");
            world.setBlockState(lightPos,ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState(),2);
            TileEntityProgrammableLight light=(TileEntityProgrammableLight)world.getTileEntity(lightPos);
            light.configureSignalBrightness(true,0);light.setRedstoneChannels(ChannelList.of(16001));
            world.setBlockState(triggerPos,ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK.getDefaultState(),2);
            TileEntityProgrammableTrigger trigger=(TileEntityProgrammableTrigger)world.getTileEntity(triggerPos);
            trigger.configure(0,3,ChannelList.of(16001));trigger.configureLevels(true,-1,1,2);
            Block engine=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:ion_drive"));
            if(!(engine instanceof BlockPropulsionLight))engine=new BlockPropulsionLight("ion_drive",false,1);
            world.setBlockState(enginePos,engine.getDefaultState(),2);
            TileEntityRedstoneLight propulsion=(TileEntityRedstoneLight)world.getTileEntity(enginePos);
            propulsion.setParticleLevel(1);propulsion.configureSignalBrightness(true,8);propulsion.setRedstoneChannels(ChannelList.of(16001));
            for(int level=0;level<=15;level++) {
                RedstoneChannels.latchLevelChanged(slider,level);
                require(RedstoneChannels.level(world,16001)==level,"channel level "+level);
                require(light.getLightLevel()==level,"light level "+level);
                require(engine.getLightValue(world.getBlockState(enginePos),world,enginePos)==level,"propulsion emission "+level);
                require(propulsion.isParticleStreamSelected()==(level>=8),"particle threshold "+level);
                require(trigger.getVisibleTexture()==(level==0?0:level<=5?1:level<=10?2:3),"trigger band "+level);
            }
            RedstoneChannels.register(source);source.level=12;RedstoneChannels.inputChanged(source);
            screen.selectSegment(0,3);require(light.getLightLevel()==12,"weaker latch suppressed source");
            source.level=4;RedstoneChannels.inputChanged(source);require(light.getLightLevel()==6,"nonzero source decrease missed");
            light.configureSignalBrightness(true,-3);require(light.getLightLevel()==3,"negative offset");
            trigger.configureLevels(false,6,1,2);require(trigger.getVisibleTexture()==3,"exact trigger");
            source.level=7;RedstoneChannels.inputChanged(source);require(trigger.getVisibleTexture()==0,"exact mismatch");
            NBTTagCompound saved=screen.writeToNBT(new NBTTagCompound());
            RedstoneScreenContents restored=new RedstoneScreenContents(new TileEntityAnimatedScreenSelector(),0);restored.readFromNBT(saved);
            require(restored.rows().get(0).latchedLevel(16001)==6 && restored.rows().get(0).slider,"numeric latch save");
            NBTTagCompound configuration=screen.configuration();
            require(!configuration.getTagList("Rows",10).getCompoundTagAt(0).hasKey("Levels"),"copy includes live power");
            TileEntityProgrammableLight restoredLight=new TileEntityProgrammableLight();restoredLight.readFromNBT(light.writeToNBT(new NBTTagCompound()));
            require(restoredLight.isSignalBrightness() && restoredLight.getLightOffset()==-3,"light save");
            Block switchBlock=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:rocker_switch"));
            if(!(switchBlock instanceof BlockVandorSwitch))switchBlock=new BlockVandorSwitch("rocker_switch",false);
            BlockPos switchPos=screenPos.south(3);
            world.setBlockState(switchPos, switchBlock.getDefaultState(),2);
            TileEntityRedstoneChannel control=(TileEntityRedstoneChannel)world.getTileEntity(switchPos);
            control.setRedstoneChannels(ChannelList.of(16001));
            RedstoneChannels.latchLevelChanged(slider,9);source.level=0;RedstoneChannels.inputChanged(source);
            require(RedstoneChannels.level(world,16001)==9 && control.getOutputLevel()==9,"linked control amplified slider");
            require(switchBlock.getWeakPower(world.getBlockState(switchPos),world,switchPos,net.minecraft.util.EnumFacing.UP)==9,"linked physical strength");
            RedstoneChannels.unregister(control);world.setBlockToAir(switchPos);
            // Joining shares intensity even when only one member subscribes to the channel.
            BlockPos joinedPos=lightPos.up();world.setBlockState(joinedPos,ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState(),2);
            TileEntityProgrammableLight joined=(TileEntityProgrammableLight)world.getTileEntity(joinedPos);
            joined.configure(joined.getTexture(),15,true,0,joined.getHousingTexture(),0);joined.configureSignalBrightness(true,0);
            light.configure(light.getTexture(),15,true,ChannelList.of(16001),light.getHousingTexture(),0);light.configureSignalBrightness(true,0);
            RedstoneChannels.latchLevelChanged(screen.rows().get(0),4);
            require(joined.getLightLevel()==4,"joined intensity missing");
            world.setBlockToAir(joinedPos);
            row.setInteger("Min",5);row.setInteger("Max",7);require(screen.applyRowConfiguration(rows),"narrow range");require(screen.rows().get(0).segments()==3 && screen.rows().get(0).segmentValue(2)==7,"narrow segments");
            row.setInteger("Min",7);require(screen.applyRowConfiguration(rows) && screen.rows().get(0).segments()==1,"single value range");
            row.setInteger("Max",6);require(!screen.applyRowConfiguration(rows),"inverted range accepted");
            System.out.println("[vandorlabs][reprolab] signal-level-runtime PASS all 16 levels, max sources, nonzero transitions, offsets, particles, trigger bands/exact matching, save and segment ranges");
        } finally {
            RedstoneChannels.unregister(source);
            for(BlockPos pos:new BlockPos[]{screenPos,lightPos,triggerPos,enginePos})world.setBlockToAir(pos);
        }
    }
    private static final class Source extends TileEntity implements RedstoneChannelMember {
        int level;
        Source(World world,BlockPos pos){setWorld(world);setPos(pos);}
        public TileEntity channelTile(){return this;}
        public int getRedstoneChannel(){return 16001;}
        public void setRedstoneChannel(int channel){}
        public boolean hasLocalRedstoneSignal(){return level>0;}
        public int localSignalLevel(int channel){return level;}
        public void setChannelSignal(boolean on){}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("signal levels: "+message);}
    private SignalLevelRuntimeChecks(){}
}
