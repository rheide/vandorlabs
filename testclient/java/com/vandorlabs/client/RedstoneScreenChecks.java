package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import java.util.*;

final class RedstoneScreenChecks {
    static void run(){
        if(TileEntity.getKey(TileEntityRedstoneScreen.class)==null)net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityRedstoneScreen.class,new ResourceLocation("minecraft","redstone_screen_check"));
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(0,100,0);
        world.setBlockState(pos,ModBlocks.PROGRAMMABLE_REDSTONE_SCREEN.getDefaultState(),2);
        TileEntityRedstoneScreen screen=(TileEntityRedstoneScreen)world.getTileEntity(pos);
        require(screen.configureRows(Arrays.asList("Doors","Lights","Unlinked"),Arrays.asList(ChannelList.of(41,42),ChannelList.of(42),ChannelList.EMPTY),0),"valid rows rejected");
        require(!screen.rows().get(0).active(),"new rows active");
        screen.toggleRow(0);require(RedstoneChannels.allPowered(world,ChannelList.of(41,42)),"row did not power all channels");
        require(screen.rows().get(0).active() && screen.rows().get(1).active(),"overlapping rows did not highlight");
        screen.toggleRow(1);require(RedstoneChannels.allPowered(world,ChannelList.of(41)) && !RedstoneChannels.allPowered(world,ChannelList.of(42)),"overlap bridged independent channels");
        require(!screen.rows().get(0).active() && !screen.rows().get(1).active(),"mixed channels highlighted active");
        NBTTagCompound saved=screen.writeToNBT(new NBTTagCompound());
        TileEntityRedstoneScreen restored=new TileEntityRedstoneScreen();restored.readFromNBT(saved);
        require(restored.rows().size()==3 && restored.rows().get(0).channels.equals(ChannelList.of(41,42)),"rows lost in NBT");
        require(restored.rows().get(0).latchedChannels().equals(ChannelList.of(41)),"partial latch lost in NBT");
        screen.toggleRow(0);require(screen.rows().get(0).active(),"partial click did not activate every channel");
        screen.toggleRow(0);require(!RedstoneChannels.allPowered(world,ChannelList.of(41)),"row did not turn off");
        require(!screen.configureRows(Arrays.asList("Bad\nlabel"),Arrays.asList(ChannelList.of(1)),0),"control label accepted");
        screen.toggleRow(0);screen.configureRows(Collections.emptyList(),Collections.emptyList(),0);
        require(!RedstoneChannels.allPowered(world,ChannelList.of(41)),"removed row retained a power source");
        screen.configureRows(Arrays.asList("Saved"),Arrays.asList(ChannelList.of(41)),0);screen.toggleRow(0);screen.onChunkUnload();
        require(!RedstoneChannels.allPowered(world,ChannelList.of(41)),"unloaded screen retained a power source");
        BlockPos target=pos.east(3);world.setBlockState(target,ModBlocks.PROGRAMMABLE_DIAGONAL_REDSTONE_SCREEN.getDefaultState(),2);
        TileEntityRedstoneScreen copy=(TileEntityRedstoneScreen)world.getTileEntity(target);
        require(com.vandorlabs.items.ProgrammableSettings.apply(world,target,com.vandorlabs.items.ProgrammableSettings.capture(world,pos)),"Duplifier rows not applicable");
        require(copy.rows().size()==1 && copy.rows().get(0).label.equals("Saved") && copy.rows().get(0).channels.equals(ChannelList.of(41)),"Duplifier lost screen row configuration");
        projection();packets();
        System.out.println("PASS: redstone-screen row toggles, ALL highlight, independent overlap, NBT, removal/unload and all 14 mounting projections");
    }
    static Vec3d world(Vec3d point,EnumFacing facing){
        double x=point.x/16-.5,y=point.y/16-.5,z=point.z/16-.5,wx=x,wy=y,wz=z;
        switch(facing){case EAST:wx=-z;wz=x;break;case SOUTH:wx=-x;wz=-z;break;case WEST:wx=z;wz=-x;break;
            case UP:wx=-x;wy=-z;wz=-y;break;case DOWN:wy=z;wz=-y;break;default:break;}
        return new Vec3d(wx+.5,wy+.5,wz+.5);
    }
    private static void projection(){
        for(boolean diagonal:new boolean[]{false,true})for(EnumFacing facing:EnumFacing.values())for(boolean inverted:new boolean[]{false,true}){
            if(!diagonal && inverted || diagonal && facing.getAxis()==EnumFacing.Axis.Y)continue;
            IBlockState state=diagonal?ModBlocks.PROGRAMMABLE_DIAGONAL_REDSTONE_SCREEN.getDefaultState().withProperty(BlockProgrammableDiagonalScreen.FACING,facing).withProperty(BlockProgrammableDiagonalScreen.INVERTED,inverted):ModBlocks.PROGRAMMABLE_REDSTONE_SCREEN.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,facing);
            ScreenSurface.Quad q=RedstoneScreenInteractions.surface(state);
            for(int row=0;row<8;row++)for(int test=0;test<4;test++){
                double u=test==1?50:109,v=24+12*row+(test==2?11:5);
                Vec3d center=new Vec3d(q.topRight.x+(q.topLeft.x-q.topRight.x)*u/128,q.topRight.y+(q.bottomRight.y-q.topRight.y)*v/128,q.topRight.z+(q.bottomRight.z-q.topRight.z)*v/128);
                Vec3d start=world(center.addVector(0,q.ny*16,q.nz*16),facing),end=world(center.addVector(0,-q.ny*16,-q.nz*16),facing);
                int actual=RedstoneScreenInteractions.hitRow(state,test==3?end:start,test==3?start:end,8);
                require(actual==(test==0?row:-1),"row hit projection differs: "+facing+" diagonal="+diagonal+" inverted="+inverted+" test="+test+" row="+row);
            }
        }
    }
    private static void packets(){
        com.vandorlabs.network.MessageRedstoneScreen packet=new com.vandorlabs.network.MessageRedstoneScreen(new BlockPos(1,2,3),Arrays.asList("Doors","Lights"),Arrays.asList(ChannelList.of(3,9),ChannelList.of(9)),0);
        io.netty.buffer.ByteBuf buffer=io.netty.buffer.Unpooled.buffer();packet.toBytes(buffer);
        try{
            java.lang.reflect.Field valid=packet.getClass().getDeclaredField("valid");valid.setAccessible(true);
            for(int mode=0;mode<4;mode++){
                io.netty.buffer.ByteBuf input=buffer.copy();if(mode==1)input.setByte(12,9);if(mode==2)input.writerIndex(input.writerIndex()-1);if(mode==3)input.writeByte(0);
                com.vandorlabs.network.MessageRedstoneScreen decoded=new com.vandorlabs.network.MessageRedstoneScreen();decoded.fromBytes(input);input.release();
                require(valid.getBoolean(decoded)==(mode==0),"screen packet bound/rejection differs mode="+mode);
                if(mode==0){io.netty.buffer.ByteBuf encoded=io.netty.buffer.Unpooled.buffer();decoded.toBytes(encoded);require(io.netty.buffer.ByteBufUtil.equals(buffer,encoded),"row packet changed contents");encoded.release();}
            }
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}finally{buffer.release();}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
