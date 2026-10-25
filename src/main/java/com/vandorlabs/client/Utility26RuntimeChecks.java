package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.coupler.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.redstone.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Authoritative boundary discovery, channel gates and optional-mod restoration checks. */
final class Utility26RuntimeChecks {
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException("2.6 utility: "+why);}
    static void run(WorldServer world,net.minecraft.entity.player.EntityPlayerMP actor){
        BlockPos base=new BlockPos(470,104,160);
        for(BlockPos p:BlockPos.getAllInBox(base.add(-2,-2,-2),base.add(10,6,4)))world.setBlockToAir(p);
        BlockCoupler coupler=(BlockCoupler)Block.getBlockFromName("vandorlabs:square_coupler");
        Block thruster=Block.getBlockFromName("vandorlabs:impulse_engine");
        try{
            for(int x=0;x<3;x++){
                world.setBlockState(base.east(x).down(),Blocks.STONE.getDefaultState(),2);
                world.setBlockState(base.east(x),thruster.getDefaultState().withProperty(BlockPropulsionLight.FACING,EnumFacing.DOWN),2);
                world.setBlockState(base.east(x).up(),Blocks.IRON_BLOCK.getDefaultState(),2);
            }
            BlockPos seat=base.up(2);world.setBlockState(seat,ModBlocks.PILOT_SEAT.getDefaultState(),2);
            ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,seat,world.getBlockState(seat),actor,new net.minecraft.item.ItemStack(ModBlocks.PILOT_SEAT));
            world.setBlockState(base.east().up(2),Block.getBlockFromName("vandorlabs:vehicle_controller").getDefaultState(),2);
            BlockPos a=base.add(3,1,0),b=a.east(),extra=b.east();
            world.setBlockState(a,coupler.getDefaultState().withProperty(BlockCoupler.FACING,EnumFacing.EAST),2);
            world.setBlockState(b,coupler.getDefaultState().withProperty(BlockCoupler.FACING,EnumFacing.WEST),2);
            world.setBlockState(extra,Blocks.GOLD_BLOCK.getDefaultState(),2);
            TileCoupler first=(TileCoupler)world.getTileEntity(a),second=(TileCoupler)world.getTileEntity(b);
            for(boolean onA:new boolean[]{false,true})for(boolean onB:new boolean[]{false,true}){
                first.setChannelSignal(onA);second.setChannelSignal(onB);
                VehicleScan scan=new VehicleScan(world,seat);for(int i=0;i<100&&!scan.step(128);i++){}
                require(scan.result!=null,"thruster boundary scan: "+scan.error);
                require(scan.result.gearCount==3,"three downward thrusters provide boundaries");
                require(scan.result.at(base.down().subtract(scan.origin))==null,"thruster scanned into terrain");
                require((scan.result.at(extra.subtract(scan.origin))!=null)==(onA&&onB),"two independent coupler gates");
            }
            first.configure(ChannelList.of(64123,64124),true);first.setChannelSignal(false);require(first.connected(),"inverted no-signal lock");first.setChannelSignal(true);require(!first.connected(),"inverted powered release");
            TileCoupler restored=new TileCoupler();restored.readFromNBT(first.writeToNBT(new NBTTagCompound()));require(restored.inverted()&&restored.getRedstoneChannels().size()==2,"saved gate configuration");
            io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();new MessageCoupler(9,"64123,64124",true).toBytes(bytes);new MessageCoupler().fromBytes(bytes);require(!bytes.isReadable(),"coupler packet round trip consumes exact payload");bytes.release();
            bytes=io.netty.buffer.Unpooled.buffer();bytes.writeInt(9);bytes.writeShort(257);bytes.writeZero(258);boolean refused=false;try{new MessageCoupler().fromBytes(bytes);}catch(IllegalArgumentException expected){refused=true;}finally{bytes.release();}require(refused,"oversized packet refused before allocation");
            for(String id:new String[]{"programmable_half_input","programmable_full_input","programmable_half_console","programmable_console","animated_screen_selector"}){
                Block block=Block.getBlockFromName("vandorlabs:"+id);if(block==null||block==Blocks.AIR)continue;
                net.minecraft.tileentity.TileEntity tile=block.createTileEntity(world,block.getDefaultState());
                require(tile instanceof TileEntityAnimatedScreenSelector&&((TileEntityAnimatedScreenSelector)tile).getHousingTexture()==ScreenHousingTextures.screenIndex("door_inner"),"new device default "+id);
            }
            carriedCoupler(world,base.west(3));
            scaffolds(world,actor,base.add(0,0,12));
            foreignRotations(world);
            nestedLanding(world,actor);
        }finally{for(BlockPos p:BlockPos.getAllInBox(base.add(-2,-2,-2),base.add(10,6,4)))world.setBlockToAir(p);}
        System.out.println("[vandorlabs][reprolab] utility26-boundaries PASS coupler-off-on-inverted saved-settings bounded-packet down-thrusters terrain-excluded device-defaults");
    }
    private static void carriedCoupler(WorldServer world,BlockPos sourcePos){
        Block sourceBlock=null;for(Block block:Block.REGISTRY)if(block instanceof BlockIndustrialLever){sourceBlock=block;break;}
        require(sourceBlock!=null,"channel source fixture");
        for(EnumFacing side:EnumFacing.values())world.setBlockState(sourcePos.offset(side),Blocks.STONE.getDefaultState(),2);
        world.setBlockState(sourcePos,sourceBlock.getDefaultState(),2);
        TileEntityRedstoneChannel source=(TileEntityRedstoneChannel)world.getTileEntity(sourcePos);
        source.setRedstoneChannels(ChannelList.of(64321));RedstoneChannels.register(source);
        TileCoupler receiver=new TileCoupler();receiver.setPos(new BlockPos(1,0,0));receiver.configure(ChannelList.of(64321),false);
        VehicleStructure snapshot=new VehicleStructure(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null),new VehicleStructure.Cell(receiver.getPos(),Block.getBlockFromName("vandorlabs:square_coupler").getDefaultState(),receiver.writeToNBT(new NBTTagCompound()))),BlockPos.ORIGIN,EnumFacing.NORTH,3);
        EntityGroundVehicle craft=new EntityGroundVehicle(world);
        try{
            craft.install(snapshot);craft.setPosition(sourcePos.getX()+10,sourcePos.getY()+4,sourcePos.getZ());craft.signals.open();
            for(boolean on:new boolean[]{true,false,true}){
                source.setLocalOn(on);require(world.getTileEntity(sourcePos)==source,"channel source remains mounted");require(RedstoneChannels.level(world,64321)==(on?15:0),"channel bank fixture follows source");craft.signals.tick();
                require(craft.view.getBlockState(receiver.getPos()).getValue(BlockCoupler.LOCKED)==on,"carried channel gate "+on);
                require(craft.structure.at(receiver.getPos()).state.getValue(BlockCoupler.LOCKED)==on,"carried gate saved in immutable snapshot");
            }
        }finally{craft.signals.close();craft.setDead();world.setBlockToAir(sourcePos);for(EnumFacing side:EnumFacing.values())world.setBlockToAir(sourcePos.offset(side));}
        System.out.println("[vandorlabs][reprolab] carried-couplers PASS parent-channel-input snapshot-state repeated-reversal");
    }
    private static void scaffolds(WorldServer world,net.minecraft.entity.player.EntityPlayerMP actor,BlockPos root){
        Block scaffold=Block.getBlockFromName("immersiveengineering:metal_decoration1");
        require(scaffold!=null&&scaffold!=Blocks.AIR,"IE fixture installed");
        net.minecraft.util.math.Vec3d before=actor.getPositionVector();actor.setPosition(root.getX()+.5,root.getY()+1,root.getZ()+2);
        try{for(int meta:new int[]{1,2,3,5,6,7}){
            IBlockState material=scaffold.getStateFromMeta(meta);require(com.vandorlabs.compat.RampMaterials.isScaffolding(material),"scaffold accepted "+meta);
            world.setBlockState(root,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState(),2);
            world.setBlockState(root.north(),material,2);
            TileEntityRampController controller=(TileEntityRampController)world.getTileEntity(root);
            try{
                controller.setOwner(actor);require(controller.configure(actor,1,2,true,true,false,true),"scaffold ramp configuration: "+controller.status);
                require(controller.request(true),"scaffold capture: "+controller.status);
                require(controller.attached(),"scaffold captured "+meta);
                controller.request(false);
            }finally{world.setBlockToAir(root);}
            require(world.getBlockState(root.north()).getBlock()==scaffold,"scaffold recovery "+meta);
            world.setBlockToAir(root.north());
        }
        }finally{actor.setPosition(before.x,before.y,before.z);}
        System.out.println("[vandorlabs][reprolab] scaffolding-ramps PASS variants=6 capture-recovery");
    }
    private static void nestedLanding(WorldServer world,net.minecraft.entity.player.EntityPlayerMP actor){
        BlockPos base=new BlockPos(460,120,200);
        for(BlockPos p:BlockPos.getAllInBox(base.add(-1,-7,-1),base.add(8,4,2)))world.setBlockToAir(p);
        try{
            for(int x=0;x<7;x++)world.setBlockState(base.east(x),Blocks.IRON_BLOCK.getDefaultState(),2);
            for(int x:new int[]{0,3,6}){
                for(int y:new int[]{-1,-4})world.setBlockState(base.add(x,y,0),com.vandorlabs.gear.GearRegistry.GEAR_EXTENDER.getDefaultState(),2);
                for(int y:new int[]{-2,-5})world.setBlockState(base.add(x,y,0),com.vandorlabs.gear.GearRegistry.LANDING_PLATE.getDefaultState(),2);
                world.setBlockState(base.add(x,-3,0),Blocks.IRON_BLOCK.getDefaultState(),2);world.setBlockState(base.add(x,-6,0),Blocks.STONE.getDefaultState(),2);
                for(int y:new int[]{-1,-4}){com.vandorlabs.gear.GearTileEntity gear=(com.vandorlabs.gear.GearTileEntity)world.getTileEntity(base.add(x,y,0));require(gear.rebuild()&&gear.isLandingAssembly(),"nested gear fixture "+gear.error());}
            }
            BlockPos seat=base.up();world.setBlockState(seat,ModBlocks.PILOT_SEAT.getDefaultState(),2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,seat,world.getBlockState(seat),actor,new net.minecraft.item.ItemStack(ModBlocks.PILOT_SEAT));
            world.setBlockState(base.east().up(),Block.getBlockFromName("vandorlabs:vehicle_controller").getDefaultState(),2);
            VehicleScan scan=new VehicleScan(world,seat);for(int i=0;i<100&&!scan.step(128);i++){}
            require(scan.result!=null,"nested landing scan "+scan.error);require(scan.result.gearCount==3,"only outermost landing supports count");
            for(int x:new int[]{0,3,6}){require(scan.result.at(base.add(x,-5,0).subtract(scan.origin))!=null,"lower landing plate captured");require(scan.result.at(base.add(x,-3,0).subtract(scan.origin))!=null,"intermediate payload captured");require(scan.result.at(base.add(x,-6,0).subtract(scan.origin))==null,"outer landing boundary excludes ground");}
        }finally{for(BlockPos p:BlockPos.getAllInBox(base.add(-1,-7,-1),base.add(8,4,2)))world.setBlockToAir(p);}
        System.out.println("[vandorlabs][reprolab] nested-landing-boundaries PASS layers=2 intermediate-blocks outermost-supports terrain-excluded");
    }
    private static void foreignRotations(WorldServer world){
        Block glow=Block.getBlockFromName("mekanism:glowpanel");
        if(glow==null||glow==Blocks.AIR){System.out.println("[vandorlabs][reprolab] optional Mekanism rotation checks unavailable");return;}
        int count=0;
        for(EnumFacing side:EnumFacing.values()){
            BlockPos p=new BlockPos(1,0,0);net.minecraft.tileentity.TileEntity tile=glow.createTileEntity(world,glow.getDefaultState());tile.setPos(p);tile.setWorld(world);
            NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());tag.setInteger("side",side.getIndex());
            VehicleStructure source=new VehicleStructure(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null),new VehicleStructure.Cell(p,glow.getDefaultState(),tag)),BlockPos.ORIGIN,EnumFacing.NORTH,3);
            VehicleRotation.validate(source,new VehicleWorld(world,source));
            VehicleStructure rotated=VehicleRotation.rotate(source,new VehicleWorld(world,source),1);
            require(rotated.at(VehicleRotation.cell(p,2,1,1)).tileData().getInteger("side")==net.minecraft.util.Rotation.CLOCKWISE_90.rotate(side).getIndex(),"glow panel actual quarter turn");
            for(int i=0;i<3;i++)rotated=VehicleRotation.rotate(rotated,new VehicleWorld(world,rotated),1);
            require(source.write().equals(rotated.write()),"glow panel NBT four-turn round trip");count++;
        }
        System.out.println("[vandorlabs][reprolab] mekanism-glowpanel-rotation PASS orientations="+count);
    }
    static void models(net.minecraft.client.Minecraft mc){
        BlockCoupler block=(BlockCoupler)Block.getBlockFromName("vandorlabs:square_coupler");int count=0;
        for(EnumFacing facing:EnumFacing.values())for(boolean locked:new boolean[]{false,true}){
            IBlockState state=block.getDefaultState().withProperty(BlockCoupler.FACING,facing).withProperty(BlockCoupler.LOCKED,locked);
            require(block.getStateFromMeta(block.getMetaFromState(state))==state,"coupler state round trip");
            net.minecraft.client.renderer.block.model.IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);
            require(model!=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel(),"coupler native model "+facing+locked);
            java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> faces=new java.util.ArrayList<>(model.getQuads(state,null,0));for(EnumFacing side:EnumFacing.values())faces.addAll(model.getQuads(state,side,0));
            for(net.minecraft.client.renderer.block.model.BakedQuad face:faces)require(!face.getSprite().getIconName().equals("missingno"),"coupler artwork packaged "+facing+locked);
            int quads=faces.size();require(quads>=36,"nine authored cuboids "+facing+locked);count++;
        }
        System.out.println("[vandorlabs][reprolab] coupler-models PASS variants="+count);
    }
}
