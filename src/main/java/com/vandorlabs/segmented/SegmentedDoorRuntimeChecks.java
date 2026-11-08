package com.vandorlabs.segmented;

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
public final class SegmentedDoorRuntimeChecks {
    private static int checks;
    private static void require(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("segmented runtime: "+why);}
    public static void run(WorldServer world,EntityPlayerMP actor){
        checks=0;packets();carriedSelection(world);Block door=Block.getBlockFromName("vandorlabs:segmented_door");BlockPos origin=new BlockPos(1240,110,1240);int assemblies=0;
        try{
            for(EnumFacing face:EnumFacing.HORIZONTALS){BlockPos base=origin.add(assemblies++*8,0,0);EnumFacing right=face.rotateY();for(int col=0;col<3;col++)for(int row=0;row<2;row++)world.setBlockState(base.offset(right,col).up(row),door.getDefaultState().withProperty(SegmentedDoorBlock.FACING,face),3);for(int n=0;n<4;n++)DeferredTileLoad.flush(world);SegmentedDoorTile root=SegmentedDoorTile.at(world,base.up());
                require(root.columns==3,"cached native span "+face);for(int direction=0;direction<4;direction++)for(boolean straight:new boolean[]{false,true}){
                    SegmentedDoorSettings settings=new SegmentedDoorSettings(direction,straight,direction%3,0,true,direction,straight,SegmentedDoorSettings.DEFAULT.glass);root.configureAll(actor,ChannelList.of(64999),10,false,true,-1,settings);
                    for(int col=0;col<3;col++)for(int row=0;row<2;row++){SegmentedDoorTile tile=SegmentedDoorTile.at(world,base.offset(right,col).up(row));require(tile.settings.equals(settings)&&tile.columns==3&&tile.column==col,"linked settings/span "+face+"/"+direction);NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());SegmentedDoorTile loaded=new SegmentedDoorTile();loaded.readFromNBT(saved);require(loaded.settings.equals(settings),"saved mechanism/window settings");}
                    root.operate(actor,false);root.operate(actor,true);require(root.targetOpen&&root.duration==(direction<2?2:3)*10,"direction-dependent full travel duration");root.operate(actor,false);
                }
                uniformMotion(world,actor,root,base,right);localParts(world,actor,root,base,right);SegmentedDoorCopyChecks.run(actor,root,SegmentedDoorTile.at(world,base.offset(right)));windowGroups(actor,root,base,right);
                root.configureAll(actor,ChannelList.of(64999),10,false,true,-1,SegmentedDoorSettings.DEFAULT.activation(1));root.setChannelSignal(true);DeferredTileLoad.flush(world);require(root.targetOpen,"ON activation opens for channel power");boolean denied=false;try{root.toggle(actor);}catch(IllegalArgumentException expected){denied=true;}require(denied&&root.targetOpen,"manual click cannot override redstone activation");denied=false;try{root.operate(actor,false);}catch(IllegalArgumentException expected){denied=true;}require(denied,"dialog operation cannot override redstone activation");root.setChannelSignal(false);DeferredTileLoad.flush(world);require(!root.targetOpen,"ON activation closes when signal removed");
                root.configureAll(actor,ChannelList.of(64999),10,false,true,-1,SegmentedDoorSettings.DEFAULT.activation(2));DeferredTileLoad.flush(world);require(root.targetOpen,"OFF activation opens without signal");root.setChannelSignal(true);DeferredTileLoad.flush(world);require(!root.targetOpen,"OFF activation closes with signal");
                root.configureAll(actor,ChannelList.of(64999),10,false,true,-1,SegmentedDoorSettings.DEFAULT);root.operate(actor,true);root.setChannelSignal(false);DeferredTileLoad.flush(world);require(root.targetOpen,"Manual preserves hand-selected target");
                NBTTagCompound legacy=root.writeToNBT(new NBTTagCompound());legacy.removeTag("SegmentedSettings");SegmentedDoorTile old=new SegmentedDoorTile();old.readFromNBT(legacy);require(old.settings.equals(SegmentedDoorSettings.LEGACY)&&!old.writeToNBT(new NBTTagCompound()).hasKey("SegmentedSettings"),"absent previous-release settings retain legacy input semantics and format");
            }
        }finally{for(BlockPos p:BlockPos.getAllInBox(origin.add(-4,-1,-4),origin.add(30,3,4)))world.setBlockToAir(p);}
        System.out.println("[vandorlabs][reprolab] segmented-runtime PASS checks="+checks+" four facings/directions both angles native spans linked settings saved data Manual/ON/OFF bounded packets legacy absent fields");
    }
    private static void uniformMotion(WorldServer world,EntityPlayerMP actor,SegmentedDoorTile root,BlockPos base,EnumFacing right){
        root.configureAll(actor,ChannelList.EMPTY,10,false,true,-1,SegmentedDoorSettings.DEFAULT);
        for(int col=0;col<3;col++){SegmentedDoorTile t=SegmentedDoorTile.at(world,base.offset(right,col).up());t.targetOpen=false;t.from=0;t.ticksPerSection=5+col*5;t.sequence+=col;}
        root.operate(actor,true);long sequence=root.sequence;
        for(int col=0;col<3;col++)for(int row=0;row<2;row++){SegmentedDoorTile t=SegmentedDoorTile.at(world,base.offset(right,col).up(row));require(t.started==root.started&&t.duration==root.duration&&t.ticksPerSection==root.ticksPerSection&&t.motionIdentity.equals(root.motionIdentity),"all server rows and columns share exact timing after mixed saved speeds");for(double partial:new double[]{0,.25,.5,.75,1})require(t.pose(partial)==root.pose(partial),"exact fractional authoritative row phase");}
        root.operate(actor,true);require(root.sequence==sequence,"repeated target does not restart common stroke");
        for(int col=0;col<3;col++)SegmentedDoorTile.at(world,base.offset(right,col).up()).started-=3;
        SegmentedDoorTile.at(world,base.offset(right,2).up()).targetOpen=false;root.operate(actor,false);
        for(int col=0;col<3;col++){SegmentedDoorTile t=SegmentedDoorTile.at(world,base.offset(right,col).up());require(t.from==root.from&&t.duration==root.duration&&t.motionIdentity.equals(root.motionIdentity),"reversal re-synchronizes a column already at requested target");}
        System.out.println("[vandorlabs][reprolab] segmented-server-clock PASS mixed saved speeds distinct sequences all rows exact fractional phases repeated inputs partial reversal");
    }
    private static void windowGroups(EntityPlayerMP actor,SegmentedDoorTile root,BlockPos base,EnumFacing right){
        root.configureAll(actor,ChannelList.EMPTY,10,false,true,-1,SegmentedDoorSettings.DEFAULT);
        SegmentedDoorSettings square=new SegmentedDoorSettings(0,false,0,0,true,2,true,SegmentedDoorSettings.DEFAULT.glass);
        for(int col=0;col<3;col++)SegmentedDoorTile.at(root.getWorld(),base.offset(right,col).up()).configurePart(actor,ChannelList.EMPTY,10,false,true,-1,square);
        SegmentedDoorWindows.Layout top=SegmentedDoorWindows.layout(root);require(top.width==3&&top.height==1,"Fit uses only top window row, not solid rows");
        SegmentedDoorTile middle=SegmentedDoorTile.at(root.getWorld(),base.offset(right).up());SegmentedDoorSettings round=new SegmentedDoorSettings(0,false,0,0,true,3,true,SegmentedDoorSettings.DEFAULT.glass);middle.configurePart(actor,ChannelList.EMPTY,10,false,true,-1,round);
        require(SegmentedDoorWindows.layout(root).width==1&&SegmentedDoorWindows.layout(middle).width==1,"different window shape separates cached Fit groups");
        middle.configurePart(actor,ChannelList.EMPTY,10,false,true,-1,SegmentedDoorSettings.DEFAULT);require(SegmentedDoorWindows.layout(root).width==1,"solid gap cannot join separated windows");
        root.configurePart(actor,ChannelList.EMPTY,10,false,true,-1,new SegmentedDoorSettings(0,false,0,0,true,2,false,SegmentedDoorSettings.DEFAULT.glass));require(SegmentedDoorWindows.layout(root).width==1&&SegmentedDoorWindows.layout(root).height==1,"Tile stays local");
        System.out.println("[vandorlabs][reprolab] segmented-window-groups PASS same-shape connected windows top row solid gap different shape local Tile cached-neighbour invalidation");
    }
    private static void carriedSelection(WorldServer world){
        net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:segmented_door");net.minecraft.util.math.BlockPos p=net.minecraft.util.math.BlockPos.ORIGIN;
        com.vandorlabs.vehicle.VehicleStructure snapshot=com.vandorlabs.vehicle.VehicleStructure.display(Collections.singletonList(new com.vandorlabs.vehicle.VehicleStructure.Cell(p,block.getDefaultState(),null)));com.vandorlabs.vehicle.VehicleWorld view=new com.vandorlabs.vehicle.VehicleWorld(world,snapshot);SegmentedDoorTile tile=new SegmentedDoorTile();tile.setWorld(view);tile.setPos(p);view.tiles.put(p,tile);tile.from=.25;tile.targetOpen=true;tile.started=world.getTotalWorldTime();tile.duration=100;
        require(view.isRemote&&!world.isRemote,"physical-side fixture");require(Math.abs(com.vandorlabs.VandorLabs.proxy.segmentedSelectionPose(tile)-.25)<1e-8,"carried server selection uses authoritative pose");tile.started=world.getTotalWorldTime()-50;require(Math.abs(com.vandorlabs.VandorLabs.proxy.segmentedSelectionPose(tile)-.625)<1e-8,"carried server pose follows server timeline without a client clock cache");
        System.out.println("[vandorlabs][reprolab] segmented-carried-side PASS remote-marked snapshot uses physical server pose without client animation state");
    }
    private static void localParts(WorldServer world,EntityPlayerMP actor,SegmentedDoorTile root,BlockPos base,EnumFacing right){
        root.configureAll(actor,ChannelList.EMPTY,10,false,true,-1,SegmentedDoorSettings.DEFAULT);
        SegmentedDoorTile part=SegmentedDoorTile.at(world,base.offset(right));
        SegmentedDoorSettings window=new SegmentedDoorSettings(3,true,0,0,true,2,false,SegmentedDoorSettings.DEFAULT.glass,2);
        part.configurePart(actor,ChannelList.EMPTY,10,false,true,-1,window);
        for(int col=0;col<3;col++)for(int y=0;y<2;y++){SegmentedDoorTile tile=SegmentedDoorTile.at(world,base.offset(right,col).up(y));require(tile.settings.window==(tile==part),"opening changes only selected block");require(tile.settings.direction==3&&tile.settings.position==2&&tile.settings.layout==0,"shared artwork/motion/placement");}
        root.refreshColumn();require(!root.settings.window&&part.settings.window,"topology repair preserves individual openings");
        motionPackets(world,actor,root);
        for(int position=0;position<3;position++){
            root.configureAll(actor,ChannelList.EMPTY,10,false,true,-1,new SegmentedDoorSettings(0,false,1,0,false,0,false,SegmentedDoorSettings.DEFAULT.glass,position));
            root.fluids.set(0,new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER,1000));
            require(com.vandorlabs.blocks.WaterloggedParts.fluid(world,root.getPos(),1)==null,"closed door retains only stored water side");
            root.operate(actor,true);root.from=1;root.duration=1;root.started=world.getTotalWorldTime()-2;
            require(com.vandorlabs.blocks.WaterloggedParts.fluid(world,root.getPos(),1)!=null,"open door shares water across opening");
            com.vandorlabs.blocks.DoorWaterGush.opened(world,root.getPos());require(root.waterOutlets!=0,"open wet segment supplies vanilla water outlet");
            SegmentedDoorTile loaded=new SegmentedDoorTile();loaded.readFromNBT(root.writeToNBT(new NBTTagCompound()));require(loaded.fluids.occupied(0)&&loaded.settings.position==position,"water/placement persist");
            root.operate(actor,false);root.from=0;root.started=world.getTotalWorldTime()-2;
            com.vandorlabs.blocks.DoorWaterGush.opened(world,root.getPos());require(root.waterOutlets==0,"close drains owned water outlet");
            root.fluids.set(0,null);for(EnumFacing f:EnumFacing.HORIZONTALS)if(world.getBlockState(root.getPos().offset(f)).getMaterial()==net.minecraft.block.material.Material.WATER)world.setBlockToAir(root.getPos().offset(f));
        }
        System.out.println("[vandorlabs][reprolab] segmented-local-fluid PASS individual openings shared artwork topology placement water persistence reversible outlets");
    }
    private static void motionPackets(WorldServer world,EntityPlayerMP actor,SegmentedDoorTile root){
        root.configureAll(actor,ChannelList.EMPTY,10,false,true,-1,new SegmentedDoorSettings(3,true,0,0,false,0,false,SegmentedDoorSettings.DEFAULT.glass));root.operate(actor,false);root.from=0;root.operate(actor,true);
        com.vandorlabs.physics.RigidTransform t=SegmentedDoorInteractions.transform(root,1);double[] center=t.point(.5,0,0),normal=t.vector(0,0,-1);
        net.minecraft.util.math.Vec3d eye=new net.minecraft.util.math.Vec3d(center[0]+normal[0]*2,center[1]+normal[1]*2,center[2]+normal[2]*2),end=new net.minecraft.util.math.Vec3d(center[0]-normal[0],center[1]-normal[1],center[2]-normal[2]);
        net.minecraft.util.math.RayTraceResult hit=SegmentedDoorInteractions.hit(root,eye,end,1);require(hit!=null,"predicted endpoint ray");actor.setPosition(eye.x,eye.y-actor.getEyeHeight(),eye.z);actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);
        io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();try{
            new com.vandorlabs.network.MessageSegmentedDoorUse(root,hit.hitVec).toBytes(bytes);require(bytes.readableBytes()==64,"bounded visible-panel packet bytes");bytes.setDouble(56,1);
            com.vandorlabs.network.MessageSegmentedDoorUse decoded=new com.vandorlabs.network.MessageSegmentedDoorUse();decoded.fromBytes(bytes.duplicate());io.netty.buffer.ByteBuf round=io.netty.buffer.Unpooled.buffer();try{decoded.toBytes(round);require(Arrays.equals(io.netty.buffer.ByteBufUtil.getBytes(bytes),io.netty.buffer.ByteBufUtil.getBytes(round)),"visible-panel packet roundtrip");}finally{round.release();}
            BlockPos wall=new BlockPos((eye.x+hit.hitVec.x)*.5,(eye.y+hit.hitVec.y)*.5,(eye.z+hit.hitVec.z)*.5);require(world.isAirBlock(wall),"occluder test needs air");world.setBlockState(wall,net.minecraft.init.Blocks.STONE.getDefaultState(),2);require(!decoded.interact(actor)&&root.targetOpen,"solid obstruction blocks visible-panel packet");world.setBlockToAir(wall);
            require(decoded.interact(actor)&&!root.targetOpen,"client endpoint ahead of server may close current stroke");require(!decoded.interact(actor),"replayed old sequence cannot reopen door");
            for(int kind=0;kind<3;kind++){io.netty.buffer.ByteBuf malformed=bytes.copy();try{if(kind==0)malformed.writeByte(0);if(kind==1)malformed.writerIndex(63);if(kind==2)malformed.setDouble(56,Double.NaN);com.vandorlabs.network.MessageSegmentedDoorUse bad=new com.vandorlabs.network.MessageSegmentedDoorUse();bad.fromBytes(malformed);require(!bad.interact(actor),"malformed visible-panel packet "+kind);}finally{malformed.release();}}
        }finally{bytes.release();}
        System.out.println("[vandorlabs][reprolab] segmented-use-safety PASS bytes=64 roundtrip slow-server visual endpoint real occluder replay/truncation/trailing/NaN refused");
    }
    private static void packets(){
        int variants=0;for(int direction=0;direction<4;direction++)for(boolean straight:new boolean[]{false,true})for(int layout=0;layout<3;layout++)for(int activation=-1;activation<=2;activation++)for(boolean window:new boolean[]{false,true})for(int shape=0;shape<4;shape++)for(boolean join:new boolean[]{false,true})for(int tint=0;tint<4;tint++){
            SegmentedDoorSettings s=new SegmentedDoorSettings(direction,straight,layout,activation,window,shape,join,new DoorGlassAppearance(-1,shape,tint,layout));NBTTagCompound n=new NBTTagCompound();s.write(n);require(SegmentedDoorSettings.read(n).equals(s),"optional settings NBT matrix");io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();try{s.encode(bytes);require(bytes.readableBytes()==SegmentedDoorSettings.PACKET_BYTES&&SegmentedDoorSettings.decode(bytes).equals(s),"fixed settings matrix roundtrip");}finally{bytes.release();}variants++;
        }
        io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();try{new SegmentedDoorMessage(7,0,10,true,true,ChannelList.of(17,27,37)).settings(-1,SegmentedDoorSettings.DEFAULT).toBytes(bytes);require(bytes.readableBytes()==40,"measured full settings packet bytes");SegmentedDoorMessage decoded=new SegmentedDoorMessage();decoded.fromBytes(bytes.duplicate());io.netty.buffer.ByteBuf round=io.netty.buffer.Unpooled.buffer();try{decoded.toBytes(round);require(Arrays.equals(io.netty.buffer.ByteBufUtil.getBytes(bytes),io.netty.buffer.ByteBufUtil.getBytes(round)),"complete settings packet roundtrip");}finally{round.release();}
            for(int kind=0;kind<4;kind++){io.netty.buffer.ByteBuf malformed=bytes.copy();try{if(kind==0)malformed.writerIndex(malformed.writerIndex()-3);if(kind==1)malformed.writeByte(0);if(kind==2)malformed.setByte(25,7);if(kind==3)malformed.setByte(36,255);boolean rejected=false;try{new SegmentedDoorMessage().fromBytes(malformed);}catch(IllegalArgumentException expected){rejected=true;}require(rejected,"bounded malformed extension "+kind);}finally{malformed.release();}}
        }finally{bytes.release();}
        System.out.println("[vandorlabs][reprolab] segmented-settings PASS variants="+variants+" legacy-bytes=20 extended-bytes=40 (+1 tread byte; original 15-byte prefix retained) roundtrip truncated/oversized/invalid flags rejected");
    }
    private SegmentedDoorRuntimeChecks(){}
}
