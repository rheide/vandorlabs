package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.GlassTextures;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;

/**
 * Every stationary pane is chunk geometry: joined translucent artwork, opaque stock
 * and artwork panes (solid layer) and the faintest stock tints. Pane-only owners
 * leave the compiled tile list; an animated Screen pane keeps its own renderer.
 */
final class GlassArtworkTerrainLiveChecks {
    private static final BlockPos ORIGIN=new BlockPos(1312,150,160);
    /** One configured cell: grid position and the real tile configuration. */
    private static final class Cell {
        final int x,y,shade,tint,mode;final boolean join,artwork,screen;final String label;
        Cell(String label,int x,int y,int shade,int tint,boolean artwork,boolean screen,boolean join,int mode){this.label=label;this.x=x;this.y=y;this.shade=shade;this.tint=tint;this.artwork=artwork;this.screen=screen;this.join=join;this.mode=mode;}
        int material(){return screen?GlassArtworkTerrainLiveChecks.screen:artwork?GlassArtworkTerrainLiveChecks.artwork:-1;}
        boolean opaque(){return tint==GlassTextures.OPAQUE;}
        boolean animated(){return screen&&mode==2;}
        BlockPos pos(){return ORIGIN.add(x,y,0);}
    }
    private static final int SOFT_CYAN=1,SOFT_DARK_GREY=2;
    private static final Cell[] CELLS={
        new Cell("artwork-joined",0,0,0,1,true,false,true,1),new Cell("artwork-joined",1,0,0,1,true,false,true,1),new Cell("artwork-joined",2,0,0,1,true,false,true,1),
        new Cell("animated-screen",0,1,0,1,false,true,false,2),new Cell("opaque-stock",1,1,0,GlassTextures.OPAQUE,false,false,false,1),new Cell("soft-cyan-mild",2,1,SOFT_CYAN,0,false,false,false,1),
        new Cell("soft-dark-grey-mild",0,2,SOFT_DARK_GREY,0,false,false,false,1),new Cell("opaque-artwork",1,2,0,GlassTextures.OPAQUE,true,false,false,1),new Cell("soft-cyan-normal",2,2,SOFT_CYAN,1,false,false,false,1),
    };
    private static int stage,ticks,wait,artwork=-1,screen=-1;
    private static volatile boolean placed;
    private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("glass-artwork-terrain stage="+stage+" tick="+ticks+": "+why);}
    private static BlockProgrammableGlass block(){for(net.minecraft.block.Block value:ModBlocks.BLOCKS)if(value.getClass()==BlockProgrammableGlass.class)return (BlockProgrammableGlass)value;throw new IllegalStateException("Missing Programmable Glass");}
    private static TileEntityProgrammableGlass tile(Minecraft mc,BlockPos pos){TileEntity value=mc.world.getTileEntity(pos);return value instanceof TileEntityProgrammableGlass?(TileEntityProgrammableGlass)value:null;}
    private static void choose(){
        need("soft_cyan".equals(GlassTextures.IDS[SOFT_CYAN])&&"soft_dark_grey".equals(GlassTextures.IDS[SOFT_DARK_GREY]),"faint palette fixture indices");
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
        for(int x=-1;x<=3;x++)for(int y=-1;y<=3;y++)world.setBlockToAir(ORIGIN.add(x,y,0));
        for(Cell cell:CELLS){
            world.setBlockState(cell.pos(),block().getDefaultState(),3);
            TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)world.getTileEntity(cell.pos());
            tile.configure(1,cell.shade,cell.material(),1,cell.join,cell.tint,false,cell.mode,1);
        }
        actor.setGameType(GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();
        actor.connection.setPlayerLocation(ORIGIN.getX()+1.5,ORIGIN.getY()+.5,ORIGIN.getZ()-5,0,0);
        placed=true;
    }
    static boolean tick(Minecraft mc){
        need(++ticks<1200,"bounded actual client lifecycle");mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;
        if(stage==0){if(ticks<80)return false;choose();mc.getIntegratedServer().addScheduledTask(()->place(mc));stage=1;wait=ticks;return false;}
        if(stage==1){
            need(ticks-wait<400,"client wall setup placed="+placed+" position="+mc.player.getPositionVector());
            if(!placed||mc.player.getDistanceSq(ORIGIN.getX()+1.5,ORIGIN.getY()+.5,ORIGIN.getZ()-5)>1)return false;
            for(Cell cell:CELLS){
                TileEntityProgrammableGlass tile=tile(mc,cell.pos());
                if(tile==null||tile.getMaterial()!=cell.material()||tile.getTint()!=cell.tint||tile.getShade()!=cell.shade||tile.glassPlayback.mode()!=cell.mode)return false;
            }
            stage=2;wait=ticks;return false;
        }
        if(stage==2){
            StringBuilder pending=new StringBuilder();
            for(Cell cell:CELLS){
                if(cell.animated())continue;
                TileEntityProgrammableGlass tile=tile(mc,cell.pos());
                if(tile==null||!GlassPaneTerrain.rendered(tile))pending.append(' ').append(cell.label).append('=').append(tile==null?"missing":GlassPaneTerrain.fallbackReason(tile));
            }
            if(pending.length()>0){need(ticks-wait<600,"stationary panes reach native terrain:"+pending+" "+GlassPaneTerrain.diagnostics());return false;}
            for(Cell cell:CELLS){
                TileEntityProgrammableGlass tile=tile(mc,cell.pos());
                if(cell.animated()){need(!GlassPaneTerrain.rendered(tile),"animated Screen keeps its renderer reason="+GlassPaneTerrain.fallbackReason(tile));continue;}
                GlassPaneTerrainMesh mesh=(GlassPaneTerrainMesh)tile.paneTerrainMesh;
                need(mesh.opaque==cell.opaque(),cell.label+" chunk layer opaque="+mesh.opaque);
                need(!tile.shouldRenderInPass(0)&&!tile.shouldRenderInPass(1),cell.label+" pane-only owner still dispatched to its tile renderer");
            }
            wait=ticks;stage=3;return false;
        }
        if(stage==3){
            if(ticks-wait<20)return false;
            java.io.File shot=new java.io.File(mc.mcDataDir,"screenshots");
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,"glass-artwork-terrain.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            System.out.println("[vandorlabs][reprolab] glass-artwork-terrain "+GlassPaneTerrain.diagnostics());
            System.out.println("[vandorlabs][reprolab] utility26-focused PASS glass-artwork-terrain static-artwork="+artwork+" cells="+(CELLS.length-1)
                    +" opaque-stock opaque-artwork soft-cyan-mild/normal soft-dark-grey-mild animated-screen="+screen+" kept-renderer screenshot="+shot);
            return true;
        }
        return false;
    }
    private GlassArtworkTerrainLiveChecks(){}
}
