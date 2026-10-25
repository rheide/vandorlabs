package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.navigation.*;
import com.vandorlabs.network.MessageMachineDisplay;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.*;
import io.netty.buffer.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Input-only Core activation, authored surface choices and always visible reader retention. */
final class MachineDisplayRuntimeChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Machine display: "+why);}
    static void run(WorldServer world,EntityPlayerMP player){
        BlockPos root=new BlockPos(160,100,160);for(int x=9;x<=11;x++)for(int z=9;z<=11;z++)world.getChunkFromChunkCoords(x,z);int cases=0;
        for(FrameMachineDesign spec:FrameMachineDesign.all())for(EnumFacing facing:EnumFacing.HORIZONTALS){
            BlockConstructorCore block=(BlockConstructorCore)Block.getBlockFromName("vandorlabs:"+spec.block);world.setBlockState(root,block.getDefaultState(),2);BridgeMachineChecks.frame(world,root,spec,2);world.setBlockState(root,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing),2);
            // All frames are square only along their authored axes; assembly infers the compatible facing.
            TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(root);require(tile.assemble(),"fixture "+spec.design);require((tile.mount()!=null)==spec.hasChip(),"authored mount visible without Security");if(tile.isDrive())tile.configureDrive(com.vandorlabs.drives.DriveSettings.DEFAULT);
            int[] textures=new int[tile.surfaceCount()];for(int i=0;i<textures.length;i++)textures[i]=NavigationRuntimeChecks.artwork();
            BlockPos inlet=tile.cell(spec.width-1,spec.height-1,spec.depth-1).up();
            for(int bits=0;bits<16;bits++){
                boolean redstone=(bits&1)!=0,security=(bits&2)!=0,power=(bits&4)!=0,chip=(bits&8)!=0;if(security&&!spec.hasChip())continue;
                tile.configureMachine(new NavigationScreenSettings(-1,true,1,redstone,security),textures,false,16,0,ChannelList.of(64541));tile.setInventorySlotContents(0,chip&&spec.hasChip()?new ItemStack(ModItems.DATA_CHIP,1,4):ItemStack.EMPTY);
                world.setBlockState(inlet,power?Blocks.REDSTONE_BLOCK.getDefaultState():Blocks.AIR.getDefaultState(),3);
                require(tile.screenOn()==NavigationScreenPolicy.visible(redstone,false,power,false),"gate truth table "+spec.design+facing+bits);require((tile.mount()!=null)==spec.hasChip(),"authored mount visibility independent of legacy Security");require(!tile.screenSettings().security&&tile.any&&tile.allowed==127&&tile.behavior==0,"legacy security/filter/return settings ignored");
                require(tile.getOutputLevel()==0&&tile.localSignalLevel(64541)==0&&RedstoneChannels.level(world,64541)==0,"never publishes channel power");
                for(EnumFacing side:EnumFacing.values())require(!block.canProvidePower(world.getBlockState(root))&&block.getWeakPower(world.getBlockState(root),world,root,side)==0&&block.getStrongPower(world.getBlockState(root),world,root,side)==0,"no physical output");
                if(spec.hasChip())require(tile.mount().width==TokenSlotGeometry.CHIP_SIZE&&tile.mount().height==TokenSlotGeometry.CHIP_SIZE,"native mount dimensions");
                cases++;
            }
            world.setBlockToAir(inlet);tile.refreshInputs();
            tile.configureMachine(new NavigationScreenSettings(-1,false,2,true,false),textures,true,127,0,ChannelList.of(64541));
            BlockPos sourcePos=root.add(7,0,0);world.setBlockState(sourcePos.south(),Blocks.STONE.getDefaultState(),2);world.setBlockState(sourcePos,Block.getBlockFromName("vandorlabs:industrial_power_lever").getDefaultState(),2);DeferredTileLoad.flush(world);TileEntityRedstoneChannel source=(TileEntityRedstoneChannel)world.getTileEntity(sourcePos);source.setRedstoneChannels(ChannelList.of(64541));source.setLocalOn(true);require(!source.isInvalid()&&world.getBlockState(sourcePos).getValue(BlockIndustrialLever.POWERED)&&RedstoneChannels.level(world,64541)==15,"positive native channel producer registered and powered");require(tile.screenOn(),"ordinary channel activates Core");source.setLocalOn(false);require(!tile.screenOn(),"ordinary channel deactivates Core");world.setBlockToAir(sourcePos);world.setBlockToAir(sourcePos.south());
            if(spec.hasChip()){
                tile.configureMachine(new NavigationScreenSettings(-1,true,1,false,true),textures,true,127,0,ChannelList.EMPTY);tile.setInventorySlotContents(0,new ItemStack(ModItems.DATA_CHIP,1,4));
                tile.configureMachine(new NavigationScreenSettings(-1,true,1,false,false),textures,true,127,0,ChannelList.EMPTY);require(tile.mount()!=null&&!tile.isEmpty()&&tile.screenOn(),"legacy Security false keeps mount and physical chip visible");
                tile.configureMachine(new NavigationScreenSettings(-1,true,1,false,true),textures,true,127,0,ChannelList.EMPTY);require(tile.mount()!=null&&!tile.isEmpty()&&tile.screenOn(),"legacy Security true cannot gate mount or activation");tile.removeStackFromSlot(0);
            }
            NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());saved.setBoolean("NavigationSecurity",true);saved.setBoolean("AnyToken",false);saved.setInteger("AllowedTokens",16);saved.setInteger("TokenBehavior",3);TileEntityConstructor copy=new TileEntityConstructor();copy.readFromNBT(saved);copy.setWorld(world);require(!copy.screenSettings().security&&copy.any&&copy.allowed==127&&copy.behavior==0,"legacy saved Security normalized without affecting surfaces");for(int i=0;i<textures.length;i++)require(copy.surfaceChoice(i)==textures[i],"surface artwork persists");
            tile.dismantle();world.setBlockToAir(root);for(BlockPos at:BlockPos.getAllInBox(root.add(-5,-1,-5),root.add(8,6,8)))world.setBlockToAir(at);
        }
        ByteBuf payload=Unpooled.buffer(),again=Unpooled.buffer();try{
            MessageMachineDisplay message=new MessageMachineDisplay(2,NavigationScreenSettings.DEFAULT,new int[]{-2,-1},127,0,ChannelList.of(1,2));message.toBytes(payload);byte[] before=new byte[payload.readableBytes()];payload.getBytes(0,before);MessageMachineDisplay decoded=new MessageMachineDisplay();decoded.fromBytes(payload);decoded.toBytes(again);byte[] after=new byte[again.readableBytes()];again.readBytes(after);require(java.util.Arrays.equals(before,after),"machine dialog packet round trip");
        }finally{payload.release();again.release();}
        ByteBuf invalid=Unpooled.buffer();try{
            new MessageMachineDisplay(0,NavigationScreenSettings.DEFAULT,new int[0],127,0,ChannelList.EMPTY).toBytes(invalid);invalid.setByte(15,4);
            try{new MessageMachineDisplay().fromBytes(invalid);throw new IllegalStateException("unbounded surface count accepted");}catch(IllegalArgumentException expected){}
        }finally{invalid.release();}
        System.out.println("[vandorlabs] machine-display-inputs PASS cases="+cases+" all Core designs physical-part/channel inputs zero outputs legacy Security ignored always-authored mount retained chip surface persistence packet round trip");
    }
}
