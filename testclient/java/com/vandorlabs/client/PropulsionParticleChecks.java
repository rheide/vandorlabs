package com.vandorlabs.client;

import com.vandorlabs.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.network.MessageRedstoneChannel;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Actual client tick submissions retain the original core and enlarge only the added plume. */
final class PropulsionParticleChecks {
    static void run(){
        NBTTagCompound old=new NBTTagCompound();old.setBoolean("ParticleStream",true);
        TileEntityRedstoneLight migrated=new TileEntityRedstoneLight();migrated.readFromNBT(old);require(migrated.getParticleLevel()==1,"old On did not migrate to Light");
        CommonProxy previous=VandorLabs.proxy;Capture capture=new Capture();VandorLabs.proxy=capture;
        try{
            for(String id:new String[]{"rocket_thruster","ion_drive","plasma_vent","impulse_engine","vertical_hover_thruster"})for(EnumFacing facing:EnumFacing.values()){
                NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);BlockPos pos=new BlockPos(2,100,3);
                BlockPropulsionLight block=new BlockPropulsionLight(id,false,1);IBlockState state=block.getDefaultState().withProperty(BlockPropulsionLight.FACING,facing);world.setBlockState(pos,state,2);
                TileEntityRedstoneLight tile=(TileEntityRedstoneLight)world.getTileEntity(pos);int baseline=0;
                for(int level=0;level<4;level++){
                    tile.setParticleLevel(level);capture.reset();tile.update();if(level==1)baseline=capture.count;
                    require(level==0?capture.count==0:baseline>0 && capture.count==baseline*(level==1?1:level==2?3:6),"particle density differs: "+id+" "+level);
                    if(level>0)require(capture.core==(level==1?capture.count:baseline) && capture.facing==facing && capture.x==pos.getX()+.5 && capture.y==pos.getY()+.5 && capture.z==pos.getZ()+.5,"original plume center/direction/core changed");
                    if(level>1)require(capture.maxSpread>1,"extra particles did not enlarge the plume");
                    TileEntityRedstoneLight restored=new TileEntityRedstoneLight();restored.readFromNBT(tile.writeToNBT(new NBTTagCompound()));require(restored.getParticleLevel()==level,"particle level lost in NBT");
                }
                BlockPos target=pos.east(3);world.setBlockState(target,state,2);
                com.vandorlabs.items.ProgrammableSettings.apply(world,target,com.vandorlabs.items.ProgrammableSettings.capture(world,pos));
                require(((TileEntityRedstoneLight)world.getTileEntity(target)).getParticleLevel()==3,"Duplifier lost Heavy");
                world.setBlockState(pos,state.withProperty(BlockPropulsionLight.POWERED,false),2);capture.reset();tile.update();require(capture.count==0,"unpowered fixture emitted particles");
            }
            assemblies(capture);
            for(int level=0;level<4;level++){
                MessageRedstoneChannel message=new MessageRedstoneChannel(BlockPos.ORIGIN,1,true,false).withParticleLevel(level);
                io.netty.buffer.ByteBuf buffer=io.netty.buffer.Unpooled.buffer();message.toBytes(buffer);MessageRedstoneChannel decoded=new MessageRedstoneChannel();io.netty.buffer.ByteBuf copy=buffer.copy();decoded.fromBytes(copy);copy.release();require(decoded.getParticleLevel()==level && decoded.getRedstoneChannels()!=null,"particle level lost in packet");
                buffer.setByte(13,4);decoded=new MessageRedstoneChannel();decoded.fromBytes(buffer);require(decoded.getRedstoneChannels()==null,"out-of-range particle level accepted");buffer.release();
            }
        }finally{VandorLabs.proxy=previous;}
        System.out.println("PASS: propulsion Off/Light/Medium/Heavy tick counts, original core, six directions, enlarged area, old On migration, NBT, packets and copying");
    }
    private static void assemblies(Capture capture){
        for(EnumFacing face:EnumFacing.values()){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);BlockPos anchor=new BlockPos(20,100,20);BlockConnectedPropulsionLight block=new BlockConnectedPropulsionLight("rocket_thruster",false,1);
            java.util.List<TileEntityRedstoneLight> tiles=new java.util.ArrayList<>();com.vandorlabs.blocks.PanelPlane plane=com.vandorlabs.blocks.PanelPlane.of(face);
            for(int x=0;x<2;x++)for(int y=0;y<2;y++){BlockPos pos=anchor.offset(plane.right,x).offset(plane.up,y);world.setBlockState(pos,block.getDefaultState().withProperty(BlockPropulsionLight.FACING,face),2);tiles.add((TileEntityRedstoneLight)world.getTileEntity(pos));}
            double cx=0,cy=0,cz=0;
            for(int level=1;level<4;level++){
                block.configureAssembly(world,anchor,com.vandorlabs.redstone.ChannelList.EMPTY,true,level,false,true,false,0);
                for(TileEntityRedstoneLight tile:tiles)require(tile.getParticleLevel()==level,"assembly did not receive intensity");
                capture.reset();for(TileEntityRedstoneLight tile:tiles)tile.update();require(capture.count==5*(level==1?1:level==2?3:6),"assembly emitted from multiple members or lost its baseline");
                if(level==1){cx=capture.x;cy=capture.y;cz=capture.z;require(capture.maxSpread<1,"Light assembly spread changed");}
                else require(capture.x==cx && capture.y==cy && capture.z==cz && capture.maxSpread>1,"assembly plume moved or did not expand");
            }
        }
    }
    private static final class Capture extends CommonProxy {
        int count,core;double x,y,z;float maxSpread;EnumFacing facing;
        void reset(){count=core=0;maxSpread=0;}
        @Override public void spawnThrusterParticle(World world,BlockPos pos,EnumFacing facing,int color,int style,float speed){spawnThrusterParticle(world,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,facing,color,style,speed,1);}
        @Override public void spawnThrusterParticle(World world,double x,double y,double z,EnumFacing facing,int color,int style,float speed,float spread){count++;if(spread==1)core++;this.x=x;this.y=y;this.z=z;this.facing=facing;maxSpread=Math.max(maxSpread,spread);}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
