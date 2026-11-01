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
    private static int stage,ticks,captureWait;
    private static Object joined;
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException("fixed canopy chunks stage="+stage+": "+reason);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        ++ticks;
        if(ticks%100==0){TileEntityCanopy seen=TileEntityCanopy.at(mc.world,POS);System.out.println("[vandorlabs] chunk fixture stage="+stage+" client="+mc.world.getBlockState(POS)+" tile="+seen+" mesh="+(seen==null?null:seen.chunkMesh)+" mask="+(seen==null?-1:seen.mask())+" player="+mc.player.getPosition());}
        require(ticks<500,"packet/mesh timeout");
        if(stage==0){CanopyHeldItemChecks.run(mc,output);mc.gameSettings.pauseOnLostFocus=false;mc.player.closeScreen();mc.getIntegratedServer().addScheduledTask(()->{
            net.minecraft.entity.player.EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            player.setGameType(net.minecraft.world.GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();
            player.connection.setPlayerLocation(POS.getX()+.5,POS.getY()+2,POS.getZ()+4,180,20);
            net.minecraft.world.WorldServer world=player.getServerWorld();for(BlockPos at:BlockPos.getAllInBox(POS.add(-2,-1,-3),POS.add(3,2,2)))world.setBlockToAir(at);
            Block block=Block.getBlockFromName("vandorlabs:regular_canopy_glass");player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new net.minecraft.item.ItemStack(block,4));for(BlockPos at:new BlockPos[]{POS,POS.east()}){world.setBlockState(at.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),3);player.rotationYaw=at.equals(POS)?0:90;require(player.getHeldItemMainhand().onItemUse(player,world,at.down(),net.minecraft.util.EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)==net.minecraft.util.EnumActionResult.SUCCESS,"ordinary fixed glass placement");}player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);
        }).get();stage=1;return false;}
        TileEntityCanopy tile=TileEntityCanopy.at(mc.world,POS);if(tile==null||!(tile.chunkMesh instanceof FixedCanopyModel.Mesh))return false;
        if(stage==1){
            if(tile.mask()==0)return false;require(!tile.shouldRenderInPass(0)&&!tile.shouldRenderInPass(1),"native glass still submits TESR");
            FixedCanopyModel.Mesh mesh=(FixedCanopyModel.Mesh)tile.chunkMesh;require(!mesh.solid.isEmpty()&&!mesh.glass.isEmpty(),"both chunk layers missing");joined=mesh;
            mc.getIntegratedServer().addScheduledTask(()->{net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);world.setBlockToAir(POS.east());}).get();stage=2;return false;
        }
        if(stage==2){if(tile.mask()!=0||tile.chunkMesh==joined)return false;require(((FixedCanopyModel.Mesh)tile.chunkMesh).matches(tile),"stale disconnected mesh");
            mc.getIntegratedServer().addScheduledTask(()->{net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);TileEntityCanopy server=TileEntityCanopy.at(world,POS);require(server!=null,"server canopy missing state="+world.getBlockState(POS)+" raw="+world.getTileEntity(POS));world.setBlockState(POS.north(),net.minecraft.init.Blocks.STONE.getDefaultState(),3);server.large.tint=com.vandorlabs.tiles.GlassTextures.OPAQUE;server.large.shade=12;server.sync();}).get();stage=3;return false;
        }
        if(stage==3){if(mc.world.getBlockState(POS.north()).getBlock()!=net.minecraft.init.Blocks.STONE)return false;if(tile.large.tint!=com.vandorlabs.tiles.GlassTextures.OPAQUE||!((FixedCanopyModel.Mesh)tile.chunkMesh).matches(tile))return false;
            require(((FixedCanopyModel.Mesh)tile.chunkMesh).glass.isEmpty(),"opaque edit retained translucent layer");Object before=tile.chunkMesh;FixedCanopyChunks.reload();FixedCanopyChunks.flush();require(tile.chunkMesh!=null&&tile.chunkMesh!=before,"reload retained old atlas mesh");
            CanopySurfaceRuntimeChecks.fixedSideContacts();
            mc.getIntegratedServer().addScheduledTask(()->{
                net.minecraft.entity.player.EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);net.minecraft.world.WorldServer world=player.getServerWorld();
                world.setBlockToAir(POS.north());world.setBlockState(POS.east(),net.minecraft.init.Blocks.STONE.getDefaultState(),3);
                TileEntityCanopy server=TileEntityCanopy.at(world,POS);server.large.tint=1;server.large.shade=0;server.sync();
                player.connection.setPlayerLocation(POS.getX()-1.6,POS.getY()-.2,POS.getZ()+2.6,-135,22);
            }).get();stage=4;return false;
        }
        if(stage==4){
            if(mc.world.getBlockState(POS.east()).getBlock()!=net.minecraft.init.Blocks.STONE||tile.large.tint!=1||tile.large.shade!=0||!((FixedCanopyModel.Mesh)tile.chunkMesh).matches(tile))return false;
            // Allow complete world renders and asynchronous chunk uploads after
            // the client observes the contact block, finish and close camera.
            if(mc.player.getDistanceSq(POS.getX()-1.6,POS.getY()-.2,POS.getZ()+2.6)>.05)return false;
            if(++captureWait<30)return false;
            javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"canopy-solid-side-contact.png"));
            CanopyGlassCostChecks.run(mc,output);System.out.println("[vandorlabs][reprolab] fixed-canopy-chunk PASS native packets logical joins removal shade opacity reload two layers no TESR preserved carried benchmark");return true;
        }
        return false;
    }
    private FixedCanopyChunkChecks(){}
}
