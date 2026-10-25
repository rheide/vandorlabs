package com.vandorlabs.blocks;

import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.network.MessageRedstoneChannel;
import com.vandorlabs.container.ContainerRedstoneChannel;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Real authorized dialog operation and preserved physical settings across joined shape conversion. */
public final class ThrusterJoinChecks {
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException("Thruster joins: "+reason);}
    private static BlockPos point(BlockPos root,EnumFacing facing,int x,int y){PanelPlane plane=PanelPlane.of(facing);return root.offset(plane.right,x).offset(plane.up,y);}
    public static void run(WorldServer world,EntityPlayerMP actor){
        com.vandorlabs.render.ConnectedSquareChecks.run();BlockPos root=new BlockPos(810,180,320);net.minecraft.inventory.Container container=actor.openContainer;boolean creative=actor.capabilities.isCreativeMode;double px=actor.posX,py=actor.posY,pz=actor.posZ;int cases=0;
        try{for(int chunkX=-1;chunkX<=1;chunkX++)for(int chunkZ=-1;chunkZ<=1;chunkZ++)world.getChunkFromBlockCoords(root.add(chunkX*16,0,chunkZ*16));actor.capabilities.isCreativeMode=true;actor.setPosition(root.getX()+.5,root.getY()+.5,root.getZ()+2);
            for(String family:new String[]{"rocket_thruster","ion_drive","plasma_vent","impulse_engine"})for(EnumFacing facing:EnumFacing.values())for(int mirror:new int[]{-1,1}){
                BlockConnectedPropulsionLight base=(BlockConnectedPropulsionLight)Block.getBlockFromName("vandorlabs:"+family);Block hex=Block.getBlockFromName("vandorlabs:"+family+"_hexagonal");require(hex instanceof BlockConnectedHexagonalPropulsionLight,"registered connected native hex variant");Map<BlockPos,TileEntityRedstoneLight> tiles=new LinkedHashMap<>();Map<BlockPos,NBTTagCompound> before=new LinkedHashMap<>();Set<BlockPos> left=new HashSet<>();
                try{for(int square=0;square<2;square++)for(int x=0;x<2;x++)for(int y=0;y<2;y++){BlockPos at=point(root,facing,(x+2*square)*mirror,y+square);world.setBlockState(at,base.getDefaultState().withProperty(BlockPropulsionLight.FACING,facing),2);if(square==0)left.add(at);}DeferredTileLoad.flush(world);int index=0;for(BlockPos at:footprint(root,facing,mirror)){TileEntityRedstoneLight tile=(TileEntityRedstoneLight)world.getTileEntity(at);tile.setManualMode(2,false);tile.setRedstoneChannels(ChannelList.of(65000+index));tile.setChannelSignal(true);tile.setSideTexture(index++%2==0?ScreenHousingTextures.DEFAULT_TRAPDOOR:ScreenHousingTextures.INDUSTRIAL_BLOCK);tiles.put(at,tile);}
                    for(Map.Entry<BlockPos,TileEntityRedstoneLight> entry:tiles.entrySet()){BlockPos at=entry.getKey();before.put(at,entry.getValue().writeToNBT(new NBTTagCompound()));require(base.getActualState(world.getBlockState(at),world,at).getValue(base.partProperty()).size==2,"staggered base squares join independently after all eight operating modes match family="+family+" facing="+facing+" mirror="+mirror+" member="+at+" state="+world.getBlockState(at));}
                    actor.openContainer=new ContainerRedstoneChannel(tiles.get(root));require(actor.openContainer.canInteractWith(actor),"actual dialog authorization fixture");MessageRedstoneChannel packet=new MessageRedstoneChannel(root,65400,true,false,true,false,true,ScreenHousingTextures.INDUSTRIAL_BLOCK,true,1).withChannels(ChannelList.of(65400)).withParticleLevel(0).withRedstoneInverted(true).withSignalBrightness(true,2);MessageRedstoneChannel.Handler.apply(actor,packet);
                    for(Map.Entry<BlockPos,TileEntityRedstoneLight> entry:tiles.entrySet()){BlockPos at=entry.getKey();require(world.getBlockState(at).getBlock()==(left.contains(at)?hex:base),"shape conversion touches only selected complete2x2");require(world.getTileEntity(at)==entry.getValue()&&before.get(at).equals(entry.getValue().writeToNBT(new NBTTagCompound())),"actual full-flags Shape packet preserves every member's distinct channels/material/manual/particle settings and tile identity");BlockConnectedPropulsionLight block=(BlockConnectedPropulsionLight)world.getBlockState(at).getBlock();require(block.getActualState(world.getBlockState(at),world,at).getValue(block.partProperty()).size==2,"converted hex2x2 and neighboring square both remain joined");}
                    require(actor.openContainer.canInteractWith(actor),"same physical tile keeps dialog valid after shape conversion");require(BlockPropulsionLight.configureShape(world,root,0,actor),"hex2x2 returns to square as complete group");
                    if(cases==0){int[] writes={0};BlockPropulsionLight.shapeFailure=(phase,at)->{if(phase.equals("after-write")&&++writes[0]==2)throw new IllegalStateException("injected second cell shape failure");};try{require(!BlockPropulsionLight.configureShape(world,root,1,actor),"injected group conversion fails");}finally{BlockPropulsionLight.shapeFailure=null;}for(BlockPos at:tiles.keySet())require(world.getBlockState(at).getBlock()==base&&world.getTileEntity(at)==tiles.get(at)&&before.get(at).equals(tiles.get(at).writeToNBT(new NBTTagCompound())),"partial write restores exact complete footprint and original tile identities");}
                    cases++;
                }finally{BlockPropulsionLight.shapeFailure=null;for(BlockPos at:footprint(root,facing,mirror))world.setBlockToAir(at);}
            }
        }finally{BlockPropulsionLight.shapeFailure=null;actor.openContainer=container;actor.capabilities.isCreativeMode=creative;actor.setPosition(px,py,pz);}
        System.out.println("[vandorlabs][reprolab] thruster-joined-shape PASS cases="+cases+" four families sixaxes mirrored staggered2x2 actual authorized Shape packet exactpermemberNBT/containeridentity group-only conversion/back and secondcell rollback");
    }
    private static List<BlockPos> footprint(BlockPos root,EnumFacing facing,int mirror){List<BlockPos> result=new ArrayList<>();for(int square=0;square<2;square++)for(int x=0;x<2;x++)for(int y=0;y<2;y++)result.add(point(root,facing,(x+square*2)*mirror,y+square));return result;}
    private ThrusterJoinChecks(){}
}
