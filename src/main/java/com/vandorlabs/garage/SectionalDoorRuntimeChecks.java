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
        checks=0;packets();carriedSelection(world);Block door=Block.getBlockFromName("vandorlabs:garage_door");BlockPos origin=new BlockPos(1240,110,1240);int assemblies=0;
        try{
            for(EnumFacing face:EnumFacing.HORIZONTALS){BlockPos base=origin.add(assemblies++*8,0,0);EnumFacing right=face.rotateY();for(int col=0;col<3;col++)for(int row=0;row<2;row++)world.setBlockState(base.offset(right,col).up(row),door.getDefaultState().withProperty(GarageDoorBlock.FACING,face),3);for(int n=0;n<4;n++)DeferredTileLoad.flush(world);GarageDoorTile root=GarageDoorTile.at(world,base.up());
                require(root.columns==3,"cached native span "+face);for(int direction=0;direction<4;direction++)for(boolean straight:new boolean[]{false,true}){
                    GarageDoorSettings settings=new GarageDoorSettings(direction,straight,direction%3,0,true,direction,straight,GarageDoorSettings.DEFAULT.glass);root.configureAll(actor,ChannelList.of(64999),10,false,true,-1,settings);
                    for(int col=0;col<3;col++)for(int row=0;row<2;row++){GarageDoorTile tile=GarageDoorTile.at(world,base.offset(right,col).up(row));require(tile.settings.equals(settings)&&tile.columns==3&&tile.column==col,"linked settings/span "+face+"/"+direction);NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());GarageDoorTile loaded=new GarageDoorTile();loaded.readFromNBT(saved);require(loaded.settings.equals(settings),"saved mechanism/window settings");}
                    root.operate(actor,false);root.operate(actor,true);require(root.targetOpen&&root.duration==(direction<2?2:3)*10,"direction-dependent full travel duration");root.operate(actor,false);
                }
                localParts(world,actor,root,base,right);
                root.configureAll(actor,ChannelList.of(64999),10,false,true,-1,GarageDoorSettings.DEFAULT.activation(1));root.setChannelSignal(true);DeferredTileLoad.flush(world);require(root.targetOpen,"ON activation opens for channel power");boolean denied=false;try{root.toggle(actor);}catch(IllegalArgumentException expected){denied=true;}require(denied&&root.targetOpen,"manual click cannot override redstone activation");denied=false;try{root.operate(actor,false);}catch(IllegalArgumentException expected){denied=true;}require(denied,"dialog operation cannot override redstone activation");root.setChannelSignal(false);DeferredTileLoad.flush(world);require(!root.targetOpen,"ON activation closes when signal removed");
                root.configureAll(actor,ChannelList.of(64999),10,false,true,-1,GarageDoorSettings.DEFAULT.activation(2));DeferredTileLoad.flush(world);require(root.targetOpen,"OFF activation opens without signal");root.setChannelSignal(true);DeferredTileLoad.flush(world);require(!root.targetOpen,"OFF activation closes with signal");
                root.configureAll(actor,ChannelList.of(64999),10,false,true,-1,GarageDoorSettings.DEFAULT);root.operate(actor,true);root.setChannelSignal(false);DeferredTileLoad.flush(world);require(root.targetOpen,"Manual preserves hand-selected target");
                NBTTagCompound legacy=root.writeToNBT(new NBTTagCompound());legacy.removeTag("GarageSettings");GarageDoorTile old=new GarageDoorTile();old.readFromNBT(legacy);require(old.settings.equals(GarageDoorSettings.LEGACY)&&!old.writeToNBT(new NBTTagCompound()).hasKey("GarageSettings"),"absent previous-release settings retain legacy input semantics and format");
            }
        }finally{for(BlockPos p:BlockPos.getAllInBox(origin.add(-4,-1,-4),origin.add(30,3,4)))world.setBlockToAir(p);}
        System.out.println("[vandorlabs][reprolab] sectional-runtime PASS checks="+checks+" four facings/directions both angles native spans linked settings saved data Manual/ON/OFF bounded packets legacy absent fields");
    }
    private static void carriedSelection(WorldServer world){
        net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:garage_door");net.minecraft.util.math.BlockPos p=net.minecraft.util.math.BlockPos.ORIGIN;
        com.vandorlabs.vehicle.VehicleStructure snapshot=com.vandorlabs.vehicle.VehicleStructure.display(Collections.singletonList(new com.vandorlabs.vehicle.VehicleStructure.Cell(p,block.getDefaultState(),null)));com.vandorlabs.vehicle.VehicleWorld view=new com.vandorlabs.vehicle.VehicleWorld(world,snapshot);GarageDoorTile tile=new GarageDoorTile();tile.setWorld(view);tile.setPos(p);view.tiles.put(p,tile);tile.from=.25;tile.targetOpen=true;tile.started=world.getTotalWorldTime();tile.duration=100;
        require(view.isRemote&&!world.isRemote,"physical-side fixture");require(Math.abs(com.vandorlabs.VandorLabs.proxy.garageSelectionPose(tile)-.25)<1e-8,"carried server selection uses authoritative pose");tile.started=world.getTotalWorldTime()-50;require(Math.abs(com.vandorlabs.VandorLabs.proxy.garageSelectionPose(tile)-.625)<1e-8,"carried server pose follows server timeline without a client clock cache");
        System.out.println("[vandorlabs][reprolab] sectional-carried-side PASS remote-marked snapshot uses physical server pose without client animation state");
    }
    private static void localParts(WorldServer world,EntityPlayerMP actor,GarageDoorTile root,BlockPos base,EnumFacing right){
        root.configureAll(actor,ChannelList.EMPTY,10,false,true,-1,GarageDoorSettings.DEFAULT);
        GarageDoorTile part=GarageDoorTile.at(world,base.offset(right));
        GarageDoorSettings window=new GarageDoorSettings(3,true,0,0,true,2,false,GarageDoorSettings.DEFAULT.glass,2);
        part.configurePart(actor,ChannelList.EMPTY,10,false,true,-1,window);
        for(int col=0;col<3;col++)for(int y=0;y<2;y++){GarageDoorTile tile=GarageDoorTile.at(world,base.offset(right,col).up(y));require(tile.settings.window==(tile==part),"opening changes only selected block");require(tile.settings.direction==3&&tile.settings.position==2&&tile.settings.layout==0,"shared artwork/motion/placement");}
        root.refreshColumn();require(!root.settings.window&&part.settings.window,"topology repair preserves individual openings");
        motionPackets(world,actor,root);
        for(int position=0;position<3;position++){
            root.configureAll(actor,ChannelList.EMPTY,10,false,true,-1,new GarageDoorSettings(0,false,1,0,false,0,false,GarageDoorSettings.DEFAULT.glass,position));
            root.fluids.set(0,new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER,1000));
            require(com.vandorlabs.blocks.WaterloggedParts.fluid(world,root.getPos(),1)==null,"closed door retains only stored water side");
            root.operate(actor,true);root.from=1;root.duration=1;root.started=world.getTotalWorldTime()-2;
            require(com.vandorlabs.blocks.WaterloggedParts.fluid(world,root.getPos(),1)!=null,"open door shares water across opening");
            com.vandorlabs.blocks.DoorWaterGush.opened(world,root.getPos());require(root.waterOutlets!=0,"open wet segment supplies vanilla water outlet");
            GarageDoorTile loaded=new GarageDoorTile();loaded.readFromNBT(root.writeToNBT(new NBTTagCompound()));require(loaded.fluids.occupied(0)&&loaded.settings.position==position,"water/placement persist");
            root.operate(actor,false);root.from=0;root.started=world.getTotalWorldTime()-2;
            com.vandorlabs.blocks.DoorWaterGush.opened(world,root.getPos());require(root.waterOutlets==0,"close drains owned water outlet");
            root.fluids.set(0,null);for(EnumFacing f:EnumFacing.HORIZONTALS)if(world.getBlockState(root.getPos().offset(f)).getMaterial()==net.minecraft.block.material.Material.WATER)world.setBlockToAir(root.getPos().offset(f));
        }
        System.out.println("[vandorlabs][reprolab] sectional-local-fluid PASS individual openings shared artwork topology placement water persistence reversible outlets");
    }
    private static void motionPackets(WorldServer world,EntityPlayerMP actor,GarageDoorTile root){
        root.configureAll(actor,ChannelList.EMPTY,10,false,true,-1,new GarageDoorSettings(3,true,0,0,false,0,false,GarageDoorSettings.DEFAULT.glass));root.operate(actor,false);root.from=0;root.operate(actor,true);
        com.vandorlabs.physics.RigidTransform t=GarageDoorInteractions.transform(root,1);double[] center=t.point(.5,0,0),normal=t.vector(0,0,-1);
        net.minecraft.util.math.Vec3d eye=new net.minecraft.util.math.Vec3d(center[0]+normal[0]*2,center[1]+normal[1]*2,center[2]+normal[2]*2),end=new net.minecraft.util.math.Vec3d(center[0]-normal[0],center[1]-normal[1],center[2]-normal[2]);
        net.minecraft.util.math.RayTraceResult hit=GarageDoorInteractions.hit(root,eye,end,1);require(hit!=null,"predicted endpoint ray");actor.setPosition(eye.x,eye.y-actor.getEyeHeight(),eye.z);actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);
        io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();try{
            new com.vandorlabs.network.MessageGarageDoorUse(root,hit.hitVec).toBytes(bytes);require(bytes.readableBytes()==64,"bounded visible-panel packet bytes");bytes.setDouble(56,1);
            com.vandorlabs.network.MessageGarageDoorUse decoded=new com.vandorlabs.network.MessageGarageDoorUse();decoded.fromBytes(bytes.duplicate());io.netty.buffer.ByteBuf round=io.netty.buffer.Unpooled.buffer();try{decoded.toBytes(round);require(Arrays.equals(io.netty.buffer.ByteBufUtil.getBytes(bytes),io.netty.buffer.ByteBufUtil.getBytes(round)),"visible-panel packet roundtrip");}finally{round.release();}
            BlockPos wall=new BlockPos((eye.x+hit.hitVec.x)*.5,(eye.y+hit.hitVec.y)*.5,(eye.z+hit.hitVec.z)*.5);require(world.isAirBlock(wall),"occluder test needs air");world.setBlockState(wall,net.minecraft.init.Blocks.STONE.getDefaultState(),2);require(!decoded.interact(actor)&&root.targetOpen,"solid obstruction blocks visible-panel packet");world.setBlockToAir(wall);
            require(decoded.interact(actor)&&!root.targetOpen,"client endpoint ahead of server may close current stroke");require(!decoded.interact(actor),"replayed old sequence cannot reopen door");
            for(int kind=0;kind<3;kind++){io.netty.buffer.ByteBuf malformed=bytes.copy();try{if(kind==0)malformed.writeByte(0);if(kind==1)malformed.writerIndex(63);if(kind==2)malformed.setDouble(56,Double.NaN);com.vandorlabs.network.MessageGarageDoorUse bad=new com.vandorlabs.network.MessageGarageDoorUse();bad.fromBytes(malformed);require(!bad.interact(actor),"malformed visible-panel packet "+kind);}finally{malformed.release();}}
        }finally{bytes.release();}
        System.out.println("[vandorlabs][reprolab] sectional-use-safety PASS bytes=64 roundtrip slow-server visual endpoint real occluder replay/truncation/trailing/NaN refused");
    }
    private static void packets(){
        int variants=0;for(int direction=0;direction<4;direction++)for(boolean straight:new boolean[]{false,true})for(int layout=0;layout<3;layout++)for(int activation=-1;activation<=2;activation++)for(boolean window:new boolean[]{false,true})for(int shape=0;shape<4;shape++)for(boolean join:new boolean[]{false,true})for(int tint=0;tint<4;tint++){
            GarageDoorSettings s=new GarageDoorSettings(direction,straight,layout,activation,window,shape,join,new DoorGlassAppearance(-1,shape,tint,layout));NBTTagCompound n=new NBTTagCompound();s.write(n);require(GarageDoorSettings.read(n).equals(s),"optional settings NBT matrix");io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();try{s.encode(bytes);require(bytes.readableBytes()==GarageDoorSettings.PACKET_BYTES&&GarageDoorSettings.decode(bytes).equals(s),"fixed settings matrix roundtrip");}finally{bytes.release();}variants++;
        }
        io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();try{new GarageDoorMessage(7,0,10,true,true,ChannelList.of(17,27,37)).settings(-1,GarageDoorSettings.DEFAULT).toBytes(bytes);require(bytes.readableBytes()==39,"measured full settings packet bytes");GarageDoorMessage decoded=new GarageDoorMessage();decoded.fromBytes(bytes.duplicate());io.netty.buffer.ByteBuf round=io.netty.buffer.Unpooled.buffer();try{decoded.toBytes(round);require(Arrays.equals(io.netty.buffer.ByteBufUtil.getBytes(bytes),io.netty.buffer.ByteBufUtil.getBytes(round)),"complete settings packet roundtrip");}finally{round.release();}
            for(int kind=0;kind<4;kind++){io.netty.buffer.ByteBuf malformed=bytes.copy();try{if(kind==0)malformed.writerIndex(malformed.writerIndex()-2);if(kind==1)malformed.writeByte(0);if(kind==2)malformed.setByte(25,7);if(kind==3)malformed.setByte(36,255);boolean rejected=false;try{new GarageDoorMessage().fromBytes(malformed);}catch(IllegalArgumentException expected){rejected=true;}require(rejected,"bounded malformed extension "+kind);}finally{malformed.release();}}
        }finally{bytes.release();}
        System.out.println("[vandorlabs][reprolab] sectional-settings PASS variants="+variants+" legacy-bytes=20 extended-bytes=39 roundtrip truncated/oversized/invalid flags rejected");
    }
    private SectionalDoorRuntimeChecks(){}
}
