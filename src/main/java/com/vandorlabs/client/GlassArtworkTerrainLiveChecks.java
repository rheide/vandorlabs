package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;

/**
 * Static artwork glass is owned by native chunk geometry and leaves the tile list;
 * an animated Screen pane in the same wall keeps its own renderer.
 */
final class GlassArtworkTerrainLiveChecks {
    private static final BlockPos ORIGIN=new BlockPos(1312,150,160);
    private static final int WIDTH=3,HEIGHT=2;
    private static int stage,ticks,wait,artwork=-1,screen=-1;
    private static volatile boolean placed;
    private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("glass-artwork-terrain stage="+stage+" tick="+ticks+": "+why);}
    private static BlockProgrammableGlass block(){for(net.minecraft.block.Block value:ModBlocks.BLOCKS)if(value.getClass()==BlockProgrammableGlass.class)return (BlockProgrammableGlass)value;throw new IllegalStateException("Missing Programmable Glass");}
    private static TileEntityProgrammableGlass tile(Minecraft mc,BlockPos pos){TileEntity value=mc.world.getTileEntity(pos);return value instanceof TileEntityProgrammableGlass?(TileEntityProgrammableGlass)value:null;}
    private static BlockPos cell(int x,int y){return ORIGIN.add(x,y,0);}
    private static void choose(){
        for(int i=0;i<4096&&(artwork<0||screen<0);i++){
            if(!com.vandorlabs.tiles.GlassMaterials.valid(i))continue;
            String category=ScreenHousingTextures.category(i);
            if(screen<0&&"Screens".equals(category))screen=i;
            else if(artwork<0&&"Materials".equals(category))artwork=i;
        }
        need(artwork>=0&&screen>=0,"static artwork and Screen materials available artwork="+artwork+" screen="+screen);
    }
    private static void place(Minecraft mc){
        WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
        world.getChunkFromBlockCoords(ORIGIN);
        for(int x=-1;x<=WIDTH;x++)for(int y=-1;y<=HEIGHT;y++)world.setBlockToAir(cell(x,y));
        for(int x=0;x<WIDTH;x++)for(int y=0;y<HEIGHT;y++){
            BlockPos pos=cell(x,y);world.setBlockState(pos,block().getDefaultState(),3);
            TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)world.getTileEntity(pos);
            boolean animated=x==WIDTH-1&&y==HEIGHT-1;
            // Join Off keeps the animated Screen cell out of the static artwork surface.
            tile.configure(1,0,animated?screen:artwork,1,!animated,1,false,animated?2:1,1);
        }
        actor.setGameType(GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();
        actor.connection.setPlayerLocation(ORIGIN.getX()+1.5,ORIGIN.getY(),ORIGIN.getZ()-4,0,0);
        placed=true;
    }
    static boolean tick(Minecraft mc){
        need(++ticks<1200,"bounded actual client lifecycle");mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;
        if(stage==0){if(ticks<80)return false;choose();mc.getIntegratedServer().addScheduledTask(()->place(mc));stage=1;wait=ticks;return false;}
        if(stage==1){
            need(ticks-wait<400,"client wall setup placed="+placed+" position="+mc.player.getPositionVector());
            if(!placed||mc.player.getDistanceSq(ORIGIN.getX()+1.5,ORIGIN.getY(),ORIGIN.getZ()-4)>1)return false;
            for(int x=0;x<WIDTH;x++)for(int y=0;y<HEIGHT;y++){
                TileEntityProgrammableGlass tile=tile(mc,cell(x,y));boolean animated=x==WIDTH-1&&y==HEIGHT-1;
                if(tile==null||tile.getMaterial()!=(animated?screen:artwork)||tile.glassPlayback.mode()!=(animated?2:1))return false;
            }
            stage=2;wait=ticks;return false;
        }
        if(stage==2){
            StringBuilder pending=new StringBuilder();
            for(int x=0;x<WIDTH;x++)for(int y=0;y<HEIGHT;y++){
                if(x==WIDTH-1&&y==HEIGHT-1)continue;
                TileEntityProgrammableGlass tile=tile(mc,cell(x,y));
                if(tile==null||!GlassPaneTerrain.rendered(tile))pending.append(' ').append(x).append(',').append(y).append('=').append(tile==null?"missing":GlassPaneTerrain.fallbackReason(tile));
            }
            if(pending.length()>0){need(ticks-wait<600,"static artwork panes reach native terrain:"+pending+" "+GlassPaneTerrain.diagnostics());return false;}
            TileEntityProgrammableGlass animated=tile(mc,cell(WIDTH-1,HEIGHT-1));
            need(animated!=null&&!GlassPaneTerrain.rendered(animated),"animated Screen keeps its renderer reason="+(animated==null?"missing":GlassPaneTerrain.fallbackReason(animated)));
            wait=ticks;stage=3;return false;
        }
        if(stage==3){
            if(ticks-wait<20)return false;
            java.io.File shot=new java.io.File(mc.mcDataDir,"screenshots");
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,"glass-artwork-terrain.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            System.out.println("[vandorlabs][reprolab] glass-artwork-terrain "+GlassPaneTerrain.diagnostics());
            System.out.println("[vandorlabs][reprolab] utility26-focused PASS glass-artwork-terrain static-artwork="+artwork+" cells="+(WIDTH*HEIGHT-1)+" animated-screen="+screen+" kept-renderer screenshot="+shot);
            return true;
        }
        return false;
    }
    private GlassArtworkTerrainLiveChecks(){}
}
