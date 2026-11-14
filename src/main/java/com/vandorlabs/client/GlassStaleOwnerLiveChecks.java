package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import com.vandorlabs.tiles.GlassMaterials;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.FrameAppearance;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;

/** Delayed client publication must tolerate owners removed before their queued render callback. */
final class GlassStaleOwnerLiveChecks {
    private static final BlockPos POS=new BlockPos(1280,150,128);
    private static int stage,ticks,wait,checks;private static TileEntityProgrammableGlass stale;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("glass-stale-owner stage="+stage+" tick="+ticks+": "+why);}
    private static BlockProgrammableGlass block(){for(net.minecraft.block.Block value:ModBlocks.BLOCKS)if(value instanceof BlockProgrammableGlass)return (BlockProgrammableGlass)value;throw new IllegalStateException("Missing native Glass");}
    private static int frame(){int frame=ScreenHousingTextures.screenIndex("frames/frames-screen-full/frame_bolted_1254");need(frame>=0&&FrameAppearance.valid(frame),"authored valid frame fixture");return frame;}
    private static void place(WorldServer world,BlockPos pos){world.setBlockState(pos,block().getDefaultState(),3);TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)world.getTileEntity(pos);tile.setJoin(true);tile.setAppearance(tile.getTint(),true);tile.frameAppearance.front=frame();tile.frameAppearance.tile=false;tile.markDirty();world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
    private static TileEntityProgrammableGlass tile(Minecraft mc,BlockPos pos){TileEntity value=mc.world.getTileEntity(pos);return value instanceof TileEntityProgrammableGlass?(TileEntityProgrammableGlass)value:null;}
    private static void delayed(Minecraft mc){
        // Keep the reference live to reproduce notification ordering, not merely isInvalid short-circuiting.
        stale.validate();GlassMaterials.changed();GlassFrameMesh.clear();
        need(GlassSurfaceGroups.frameMask(stale)==15,"removed/replaced owner cannot read a panel facing or cache a replacement contour");
        GlassFrameMesh.get(stale);ProgrammableGlassPasses.emptyOpaquePass(stale,null);GlassPaneTerrain.flush();
        stale.invalidate();need(!GlassPaneTerrain.rendered(stale),"removed owner retains no terrain admission");
    }
    static boolean tick(Minecraft mc)throws Exception{
        need(++ticks<900,"bounded actual client lifecycle");mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;
        if(stage==0&&ticks<80)return false;
        if(stage==0){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);world.getChunkFromBlockCoords(POS);world.setBlockToAir(POS);world.setBlockToAir(POS.east());place(world,POS);place(world,POS.east());actor.setGameType(GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.connection.setPlayerLocation(POS.getX()+.5,POS.getY(),POS.getZ()-4,0,0);}).get();stage=1;return false;}
        if(stage==1){TileEntityProgrammableGlass current=tile(mc,POS),next=tile(mc,POS.east());need(ticks<400,"client panel setup state="+mc.world.getBlockState(POS)+" tile="+current+" front="+(current==null?-99:current.frameAppearance.front)+" expected="+frame()+" position="+mc.player.getPositionVector());if(current==null||next==null||current.frameAppearance.front!=frame()||!current.isJoin()||mc.player.getDistanceSq(POS.getX()+.5,POS.getY(),POS.getZ()-4)>1)return false;stale=current;need((GlassSurfaceGroups.frameMask(current)&2)==0,"actual joined contour warmed");GlassFrameMesh.get(current);wait=ticks;stage=2;return false;}
        if(stage==2){if(ticks-wait<15)return false;mc.getIntegratedServer().addScheduledTask(()->mc.getIntegratedServer().getWorld(0).setBlockToAir(POS)).get();stage=3;return false;}
        if(stage==3){if(!mc.world.isAirBlock(POS)||mc.world.getTileEntity(POS)!=null)return false;delayed(mc);TileEntityProgrammableGlass next=tile(mc,POS.east());need(next!=null&&(GlassSurfaceGroups.frameMask(next)&1)!=0,"surviving neighbour exposes removed edge");mc.getIntegratedServer().addScheduledTask(()->mc.getIntegratedServer().getWorld(0).setBlockState(POS,Blocks.STONE.getDefaultState(),3)).get();stage=4;return false;}
        if(stage==4){if(mc.world.getBlockState(POS).getBlock()!=Blocks.STONE)return false;delayed(mc);mc.getIntegratedServer().addScheduledTask(()->place(mc.getIntegratedServer().getWorld(0),POS)).get();stage=5;return false;}
        if(stage==5){TileEntityProgrammableGlass replacement=tile(mc,POS);if(replacement==null||replacement==stale||replacement.frameAppearance.front!=frame())return false;delayed(mc);need((GlassSurfaceGroups.frameMask(replacement)&2)==0,"replacement owner retains correct contour after stale query");GlassFrameChunks.reload();GlassPaneTerrain.flush();wait=ticks;stage=6;return false;}
        if(ticks-wait<30)return false;need(tile(mc,POS)!=null&&tile(mc,POS.east())!=null,"replacement remains installed after pending frame refresh");System.out.println("[vandorlabs][reprolab] glass-stale-owner PASS checks="+checks+" actualJoinedGlass/AIR/STONE/replacement/delayedCallback/frameRefresh");System.out.println("[vandorlabs][reprolab] utility26-focused PASS glass-stale-owner");return true;
    }
    private GlassStaleOwnerLiveChecks(){}
}
