package com.vandorlabs.garage;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Native authorization, activation and old/new persistence, independent of screenshot setup. */
public final class SectionalDoorRuntimeChecks {
    private static int checks;
    private static void require(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("sectional runtime: "+why);}
    public static void run(WorldServer world,EntityPlayerMP actor){
        checks=0;packets();Block door=Block.getBlockFromName("vandorlabs:garage_door");BlockPos origin=new BlockPos(1240,110,1240);int assemblies=0;
        try{
            for(EnumFacing face:EnumFacing.HORIZONTALS){BlockPos base=origin.add(assemblies++*8,0,0);EnumFacing right=face.rotateY();for(int col=0;col<3;col++)for(int row=0;row<2;row++)world.setBlockState(base.offset(right,col).up(row),door.getDefaultState().withProperty(GarageDoorBlock.FACING,face),3);for(int n=0;n<4;n++)DeferredTileLoad.flush(world);GarageDoorTile root=GarageDoorTile.at(world,base.up());
                require(root.columns==3,"cached native span "+face);for(int direction=0;direction<4;direction++)for(boolean straight:new boolean[]{false,true}){
                    GarageDoorSettings settings=new GarageDoorSettings(direction,straight,direction%3,0,true,direction,straight,GarageDoorSettings.DEFAULT.glass);root.configureAll(actor,ChannelList.of(64999),10,false,true,-1,settings);
                    for(int col=0;col<3;col++)for(int row=0;row<2;row++){GarageDoorTile tile=GarageDoorTile.at(world,base.offset(right,col).up(row));require(tile.settings.equals(settings)&&tile.columns==3&&tile.column==col,"linked settings/span "+face+"/"+direction);NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());GarageDoorTile loaded=new GarageDoorTile();loaded.readFromNBT(saved);require(loaded.settings.equals(settings),"saved mechanism/window settings");}
                    root.operate(actor,false);root.operate(actor,true);require(root.targetOpen&&root.duration==(direction<2?2:3)*10,"direction-dependent full travel duration");root.operate(actor,false);
                }
                root.configureAll(actor,ChannelList.of(64999),10,false,true,-1,GarageDoorSettings.DEFAULT.activation(1));root.setChannelSignal(true);DeferredTileLoad.flush(world);require(root.targetOpen,"ON activation opens for channel power");boolean denied=false;try{root.toggle(actor);}catch(IllegalArgumentException expected){denied=true;}require(denied&&root.targetOpen,"manual click cannot override redstone activation");denied=false;try{root.operate(actor,false);}catch(IllegalArgumentException expected){denied=true;}require(denied,"dialog operation cannot override redstone activation");root.setChannelSignal(false);DeferredTileLoad.flush(world);require(!root.targetOpen,"ON activation closes when signal removed");
                root.configureAll(actor,ChannelList.of(64999),10,false,true,-1,GarageDoorSettings.DEFAULT.activation(2));DeferredTileLoad.flush(world);require(root.targetOpen,"OFF activation opens without signal");root.setChannelSignal(true);DeferredTileLoad.flush(world);require(!root.targetOpen,"OFF activation closes with signal");
                root.configureAll(actor,ChannelList.of(64999),10,false,true,-1,GarageDoorSettings.DEFAULT);root.operate(actor,true);root.setChannelSignal(false);DeferredTileLoad.flush(world);require(root.targetOpen,"Manual preserves hand-selected target");
                NBTTagCompound legacy=root.writeToNBT(new NBTTagCompound());legacy.removeTag("GarageSettings");GarageDoorTile old=new GarageDoorTile();old.readFromNBT(legacy);require(old.settings.equals(GarageDoorSettings.LEGACY)&&!old.writeToNBT(new NBTTagCompound()).hasKey("GarageSettings"),"absent previous-release settings retain legacy input semantics and format");
            }
        }finally{for(BlockPos p:BlockPos.getAllInBox(origin.add(-4,-1,-4),origin.add(30,3,4)))world.setBlockToAir(p);}
        System.out.println("[vandorlabs][reprolab] sectional-runtime PASS checks="+checks+" four facings/directions both angles native spans linked settings saved data Manual/ON/OFF bounded packets legacy absent fields");
    }
    private static void packets(){
        int variants=0;for(int direction=0;direction<4;direction++)for(boolean straight:new boolean[]{false,true})for(int layout=0;layout<3;layout++)for(int activation=-1;activation<=2;activation++)for(boolean window:new boolean[]{false,true})for(int shape=0;shape<4;shape++)for(boolean join:new boolean[]{false,true})for(int tint=0;tint<4;tint++){
            GarageDoorSettings s=new GarageDoorSettings(direction,straight,layout,activation,window,shape,join,new DoorGlassAppearance(-1,shape,tint,layout));NBTTagCompound n=new NBTTagCompound();s.write(n);require(GarageDoorSettings.read(n).equals(s),"optional settings NBT matrix");io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();try{s.encode(bytes);require(bytes.readableBytes()==14&&GarageDoorSettings.decode(bytes).equals(s),"fixed settings matrix roundtrip");}finally{bytes.release();}variants++;
        }
        io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();try{new GarageDoorMessage(7,0,10,true,true,ChannelList.of(17,27,37)).settings(-1,GarageDoorSettings.DEFAULT).toBytes(bytes);require(bytes.readableBytes()==38,"measured full settings packet bytes");GarageDoorMessage decoded=new GarageDoorMessage();decoded.fromBytes(bytes.duplicate());io.netty.buffer.ByteBuf round=io.netty.buffer.Unpooled.buffer();try{decoded.toBytes(round);require(Arrays.equals(io.netty.buffer.ByteBufUtil.getBytes(bytes),io.netty.buffer.ByteBufUtil.getBytes(round)),"complete settings packet roundtrip");}finally{round.release();}
            for(int kind=0;kind<4;kind++){io.netty.buffer.ByteBuf malformed=bytes.copy();try{if(kind==0)malformed.writerIndex(malformed.writerIndex()-1);if(kind==1)malformed.writeByte(0);if(kind==2)malformed.setByte(25,7);if(kind==3)malformed.setByte(36,255);boolean rejected=false;try{new GarageDoorMessage().fromBytes(malformed);}catch(IllegalArgumentException expected){rejected=true;}require(rejected,"bounded malformed extension "+kind);}finally{malformed.release();}}
        }finally{bytes.release();}
        System.out.println("[vandorlabs][reprolab] sectional-settings PASS variants="+variants+" legacy-bytes=20 extended-bytes=38 roundtrip truncated/oversized/invalid flags rejected");
    }
    private SectionalDoorRuntimeChecks(){}
}
