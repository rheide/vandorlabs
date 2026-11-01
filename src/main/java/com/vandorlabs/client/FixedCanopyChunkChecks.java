package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.io.File;

/** Real server packets publish immutable native meshes and invalidate adjacent joins. */
final class FixedCanopyChunkChecks {
    private static final BlockPos POS=new BlockPos(320,90,320);
    private static int stage,ticks;
    private static Object joined;
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException("fixed canopy chunks stage="+stage+": "+reason);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        ++ticks;
        if(ticks%100==0){TileEntityCanopy seen=TileEntityCanopy.at(mc.world,POS);System.out.println("[vandorlabs] chunk fixture stage="+stage+" client="+mc.world.getBlockState(POS)+" tile="+seen+" mesh="+(seen==null?null:seen.chunkMesh)+" mask="+(seen==null?-1:seen.mask())+" player="+mc.player.getPosition());}
        require(ticks<500,"packet/mesh timeout");
        if(stage==0){mc.gameSettings.pauseOnLostFocus=false;mc.player.closeScreen();mc.getIntegratedServer().addScheduledTask(()->{
            net.minecraft.entity.player.EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            player.setGameType(net.minecraft.world.GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();
            player.connection.setPlayerLocation(POS.getX()+.5,POS.getY()+2,POS.getZ()+4,180,20);
            net.minecraft.world.WorldServer world=player.getServerWorld();for(BlockPos at:BlockPos.getAllInBox(POS.add(-2,-1,-3),POS.add(3,2,2)))world.setBlockToAir(at);
            Block block=Block.getBlockFromName("vandorlabs:regular_canopy_glass");world.setBlockState(POS,block.getDefaultState(),3);world.setBlockState(POS.east(),block.getDefaultState().withProperty(BlockCanopy.FACING,EnumFacing.EAST),3);
        }).get();stage=1;return false;}
        TileEntityCanopy tile=TileEntityCanopy.at(mc.world,POS);if(tile==null||!(tile.chunkMesh instanceof FixedCanopyModel.Mesh))return false;
        if(stage==1){
            if(tile.mask()==0)return false;require(!tile.shouldRenderInPass(0)&&!tile.shouldRenderInPass(1),"native glass still submits TESR");
            FixedCanopyModel.Mesh mesh=(FixedCanopyModel.Mesh)tile.chunkMesh;require(!mesh.solid.isEmpty()&&!mesh.glass.isEmpty(),"both chunk layers missing");joined=mesh;
            mc.getIntegratedServer().addScheduledTask(()->{net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);world.setBlockToAir(POS.east());}).get();stage=2;return false;
        }
        if(stage==2){if(tile.mask()!=0||tile.chunkMesh==joined)return false;require(((FixedCanopyModel.Mesh)tile.chunkMesh).matches(tile),"stale disconnected mesh");
            mc.getIntegratedServer().addScheduledTask(()->{net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);TileEntityCanopy server=TileEntityCanopy.at(world,POS);require(server!=null,"server canopy missing state="+world.getBlockState(POS)+" raw="+world.getTileEntity(POS));server.large.tint=com.vandorlabs.tiles.GlassTextures.OPAQUE;server.large.shade=12;server.sync();}).get();stage=3;return false;
        }
        if(stage==3){if(tile.large.tint!=com.vandorlabs.tiles.GlassTextures.OPAQUE||!((FixedCanopyModel.Mesh)tile.chunkMesh).matches(tile))return false;
            require(((FixedCanopyModel.Mesh)tile.chunkMesh).glass.isEmpty(),"opaque edit retained translucent layer");Object before=tile.chunkMesh;FixedCanopyChunks.reload();FixedCanopyChunks.flush();require(tile.chunkMesh!=null&&tile.chunkMesh!=before,"reload retained old atlas mesh");
            CanopyGlassCostChecks.run(mc,output);System.out.println("[vandorlabs][reprolab] fixed-canopy-chunk PASS native packets logical joins removal shade opacity reload two layers no TESR preserved carried benchmark");return true;
        }
        return false;
    }
    private FixedCanopyChunkChecks(){}
}
