package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.items.*;
import com.vandorlabs.network.*;
import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** Saved, copied and wire-format layout contracts against registered game objects. */
final class TextureLayoutRuntimeChecks {
    static void run(World world,EntityPlayerMP player){
        BlockPos pos=new BlockPos(166,225,166);int count=0;
        try{
            for(String id:new String[]{"programmable_block","programmable_slab","programmable_stairs","programmable_wall","programmable_diagonal_wall","programmable_viewscreen","programmable_light","programmable_glass","programmable_diagonal_glass","programmable_door","programmable_trapdoor"}){
                Block block=Block.getBlockFromName("vandorlabs:"+id);require(block!=null,"registered "+id);
                for(int mode=0;mode<3;mode++){
                    world.setBlockState(pos,block.getDefaultState());TileEntity tile=world.getTileEntity(pos);
                    NBTTagCompound values=new NBTTagCompound();
                    String key=tile instanceof TileEntitySpaceDoor?ProgrammableSettings.DOOR_TEXTURE_LAYOUT:tile instanceof TileEntityProgrammableTrapdoor?ProgrammableSettings.TRAPDOOR_TEXTURE_LAYOUT:ProgrammableSettings.TEXTURE_LAYOUT;
                    boolean glass=tile instanceof TileEntityProgrammableGlass || id.contains("diagonal_glass");
                    if(glass){NBTTagCompound layout=new NBTTagCompound();layout.setInteger("Mode",mode);layout.setBoolean("Stretch",mode!=1);layout.setBoolean("TextureJoin",true);values.setTag(ProgrammableSettings.GLASS_LAYOUT,layout);}else values.setInteger(key,mode);
                    if(tile instanceof TileEntityProgrammableLight)values.setInteger(ProgrammableSettings.LIGHT_OFF_FACE_TEXTURE,8);
                    require(ProgrammableSettings.apply(world,pos,values),"apply layout "+id);
                    require(layout(tile,id)==mode,"applied mode "+id);
                    TileEntity restored=block.createTileEntity(world,block.getDefaultState());restored.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
                    require(layout(restored,id)==mode,"saved mode "+id);
                    NBTTagCompound captured=ProgrammableSettings.capture(world,pos);
                    if(tile instanceof TileEntityProgrammableLight)require(captured.getInteger(ProgrammableSettings.LIGHT_OFF_FACE_TEXTURE)==8,"independent OFF artwork capture");
                    require((glass?captured.getCompoundTag(ProgrammableSettings.GLASS_LAYOUT).getInteger("Mode"):captured.getInteger(key))==mode,"capture mode "+id);
                    ItemStack picked=block.getPickBlock(world.getBlockState(pos),null,world,pos,player);
                    ItemStack configured=ProgrammableSettings.applyToItem(picked,values);require(!configured.isEmpty(),"configured item "+id);
                    NBTTagCompound item=configured.getSubCompound(tile instanceof TileEntityProgrammableGlass?"ProgrammableGlassSettings":"BlockEntityTag");
                    require(item!=null,"configured item settings "+id);
                    String savedKey=tile instanceof TileEntityProgrammableGlass?"LayoutMode":tile instanceof TileEntitySpaceDoor?"DoorTextureLayout":tile instanceof TileEntityProgrammableTrapdoor?"TrapdoorTextureLayout":glass?"GlassTextureLayout":id.equals("programmable_viewscreen")?"SurfaceTextureLayout":"MainTextureLayout";
                    require(item.getInteger(savedKey)==mode,"item mode "+id+" key="+savedKey);
                    require(ProgrammableSettings.apply(world,pos,DuplifierApplyOptions.selected(captured,DuplifierApplyOptions.ALL)),"copy apply "+id);
                    require(layout(world.getTileEntity(pos),id)==mode,"copy mode "+id);
                    world.setBlockToAir(pos);count++;
                }
            }
            ChannelList channels=ChannelList.parse("7, 19, 65535");
            for(int mode=0;mode<3;mode++){
                wire(new MessageProgrammableLight(pos,0,15,true,7).withChannels(channels).withTextureLayout(mode),new MessageProgrammableLight(),mode,channels);
                wire(new MessageSpaceDoor(pos,0,0,true,7,0,0,true,true,0,true).withChannels(channels).withTextureLayout(mode),new MessageSpaceDoor(),mode,channels);
                wire(new MessageProgrammableTrapdoor(pos,0,0,true,0,7,false).withChannels(channels).withTextureLayout(mode),new MessageProgrammableTrapdoor(),mode,channels);
                wire(new MessageProgrammableSlabSides(pos,mode),new MessageProgrammableSlabSides(),mode,null);
            }
        }finally{world.setBlockToAir(pos);world.setBlockToAir(pos.up());}
        System.out.println("[vandorlabs][reprolab] texture-layout PASS "+count+" saved/copied/item modes and multi-channel packet round trips");
    }
    private static int layout(TileEntity tile,String id){
        if(tile instanceof TileEntityProgrammableGlass)return ((TileEntityProgrammableGlass)tile).getTextureLayout();
        if(tile instanceof TileEntitySpaceDoor)return ((TileEntitySpaceDoor)tile).getTextureLayout();
        if(tile instanceof TileEntityProgrammableTrapdoor)return ((TileEntityProgrammableTrapdoor)tile).getTextureLayout();
        TileEntityAnimatedScreenSelector screen=(TileEntityAnimatedScreenSelector)tile;
        return id.contains("diagonal_glass")?screen.getGlassTextureLayout():id.equals("programmable_viewscreen")?screen.getSurfaceTextureLayout():screen.getMainTextureLayout();
    }
    private static void wire(IMessage source,IMessage target,int mode,ChannelList channels){
        ByteBuf data=Unpooled.buffer();try{
            source.toBytes(data);target.fromBytes(data);require(!data.isReadable(),"packet consumed");
            java.lang.reflect.Field field=target.getClass().getDeclaredField("layout");field.setAccessible(true);require(field.getInt(target)==mode,"packet mode");
            if(channels!=null)require(channels.equals(target.getClass().getMethod("getRedstoneChannels").invoke(target)),"packet channel list");
        }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}finally{data.release();}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("texture-layout: "+message);}
}
