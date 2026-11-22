package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableCornerWall;
import com.vandorlabs.blocks.BlockProgrammableWall;
import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.network.MessageCornerGlassGeometry;
import com.vandorlabs.render.CornerGlassGeometry;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Programmable Corner Wall and Diagonal Corner Wall, placed through the item path. A flat corner
 * clicked against a flat wall's end continues it at that depth band, and a perpendicular flat wall
 * completes the join on its other end. A diagonal corner clicked against a diagonal wall's end takes
 * that wall's size and continues its slope exactly for every facing, size, lean and end; a wall
 * anchored to the opposite face never joins. The wall dialog's Size and Slope edit the corner.
 * A stack of corner walls must draw: removing it changes the image. Shots are kept for review.
 */
public final class CornerWallLiveChecks {
    private static final BlockPos ORIGIN=new BlockPos(20,100,200);
    private static final BlockPos STACK=ORIGIN.add(0,0,46);
    private static final EnumFacing[] FACINGS={EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST};
    private static int stage,ticks,checks;
    private static volatile boolean serverDone;
    private static volatile Throwable failure;
    private static LiveGallery gallery;
    private CornerWallLiveChecks(){}
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("corner-wall "+message);}
    private static Block block(String id){return Block.getBlockFromName("vandorlabs:"+id);}

    private interface Step{void run(WorldServer world,EntityPlayerMP player);}
    private static void server(Minecraft mc,Step step){
        serverDone=false;
        mc.getIntegratedServer().addScheduledTask(()->{
            try{step.run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));serverDone=true;}
            catch(RuntimeException|LinkageError error){failure=error;}
        });
    }

    public static boolean tick(Minecraft mc,File output)throws Exception{
        mc.gameSettings.pauseOnLostFocus=false;
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        if(failure!=null)throw new IllegalStateException("corner-wall server checks",failure);
        switch(stage){
            case 0:
                if(mc.player==null||++ticks<40)return false;
                server(mc,CornerWallLiveChecks::build);stage=1;return false;
            case 1:
                if(!serverDone)return false;
                if(gallery==null){
                    gallery=new LiveGallery("corner-wall",ORIGIN);
                    gallery.shot("flat",4.5,2.5,-3.5,0,35);
                    for(int mode=0;mode<3;mode++)gallery.shot("diagonal"+mode,mode*24+9.5,4.5,30,180,32);
                    gallery.shot("diagonal-close",1.0,1.3,15.4,180,18);
                    gallery.shot("stack",STACK.getX()-ORIGIN.getX()+3.5,1.5,STACK.getZ()-ORIGIN.getZ()+3.5,135,-5);
                }
                if(!gallery.tick(mc,output))return false;
                server(mc,(world,player)->{for(int y=0;y<4;y++)world.setBlockToAir(STACK.up(y));});gallery=null;stage=2;return false;
            case 2:
                if(!serverDone)return false;
                if(gallery==null)gallery=new LiveGallery("corner-wall",ORIGIN).shot("stack-removed",STACK.getX()-ORIGIN.getX()+3.5,1.5,STACK.getZ()-ORIGIN.getZ()+3.5,135,-5);
                if(!gallery.tick(mc,output))return false;
                BufferedImage with=ImageIO.read(new File(output,"corner-wall-stack.png")),without=ImageIO.read(new File(output,"corner-wall-stack-removed.png"));
                // The stack is centred in view: compare the middle band. Undrawn corners change almost nothing.
                int differ=0,total=0;
                for(int y=with.getHeight()/8;y<with.getHeight()*7/8;y++)for(int x=with.getWidth()*2/5;x<with.getWidth()*3/5;x++){
                    int a=with.getRGB(x,y),b=without.getRGB(x,y);total++;
                    if(Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255))>40)differ++;
                }
                System.out.println("[vandorlabs] corner-wall stack draws: "+differ+" of "+total+" pixels change when it is removed");
                require(differ>total/50,"stacked corner walls are drawn: only "+differ+" of "+total+" pixels change when they are removed");
                System.out.println("[vandorlabs][reprolab] corner-wall PASS checks="+checks+" flat depths and diagonal facing/size/lean/end joins, opposite anchors refused, dialog size and slope, stacked corners drawn");
                return true;
            default:return false;
        }
    }

    private static void build(WorldServer world,EntityPlayerMP player){
        player.setGameType(net.minecraft.world.GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();
        world.setWorldTime(6000);
        for(BlockPos at:BlockPos.getAllInBox(ORIGIN.add(-4,-1,-8),ORIGIN.add(76,6,52)))
            world.setBlockState(at,at.getY()==ORIGIN.getY()-1?Blocks.QUARTZ_BLOCK.getDefaultState():Blocks.AIR.getDefaultState(),2);
        flat(world,player);
        diagonal(world,player);
        dialog(world,player);
        stack(world,player);
        player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
    }
    /** Flat corners: a wall facing north at each depth band, a corner clicked on its east end, then the matching second wall. */
    private static void flat(WorldServer world,EntityPlayerMP player){
        BlockProgrammableWall wall=(BlockProgrammableWall)block("programmable_wall");
        BlockProgrammableCornerWall corner=(BlockProgrammableCornerWall)block("programmable_corner_wall");
        for(int depth=0;depth<3;depth++){
            BlockPos a=ORIGIN.add(depth*4,0,0),at=a.east();
            world.setBlockState(a,wall.getDefaultState().withProperty(BlockProgrammableWall.FACING,EnumFacing.NORTH).withProperty(BlockProgrammableWall.DEPTH,depth),3);
            require(LiveGallery.place(world,player,new ItemStack(corner),a,EnumFacing.EAST,1,.5F,.5F),"flat corner placed against a depth "+depth+" wall");
            IBlockState placed=world.getBlockState(at);
            require(placed.getBlock()==corner,"flat corner beside the clicked wall, depth "+depth+": "+placed);
            require(BlockProgrammableCornerWall.joins(world,at,a),"flat corner continues the depth "+depth+" wall: "+placed);
            // The bevel bends towards one perpendicular wall; find the facing and depth that meet its other end.
            boolean completed=false;
            for(EnumFacing step:new EnumFacing[]{EnumFacing.NORTH,EnumFacing.SOUTH}){
                BlockPos b=at.offset(step);
                for(EnumFacing facing:new EnumFacing[]{EnumFacing.EAST,EnumFacing.WEST})for(int d=0;d<3&&!completed;d++){
                    world.setBlockState(b,wall.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.DEPTH,d),3);
                    if(BlockProgrammableCornerWall.joins(world,at,b))completed=true;
                }
                if(completed)break;
                world.setBlockToAir(b);
            }
            require(completed,"a perpendicular flat wall completes the depth "+depth+" corner");
            require(corner.joinedEnds(world,at,world.getBlockState(at))==3,"both ends of the depth "+depth+" corner are joined");
        }
    }
    /** Diagonal corners: every facing, size, lean and end of a diagonal wall, as for Diagonal Corner Glass. */
    private static void diagonal(WorldServer world,EntityPlayerMP player){
        BlockProgrammableWall wall=(BlockProgrammableWall)block("programmable_diagonal_wall");
        BlockProgrammableCornerWall corner=(BlockProgrammableCornerWall)block("programmable_diagonal_corner_wall");
        int joined=0;
        for(int mode=0;mode<3;mode++)for(int f=0;f<4;f++)for(int lean=0;lean<2;lean++)for(int end=0;end<2;end++){
            EnumFacing facing=FACINGS[f];boolean inverted=lean==1;
            BlockPos at0=ORIGIN.add(mode*24+f*6,0,10+lean*10+end*5);
            world.setBlockState(at0,wall.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted),3);
            ((TileEntityAnimatedScreenSelector)world.getTileEntity(at0)).setDiagonalGeometry(mode,0);
            EnumFacing across=end==0?facing.rotateY():facing.rotateYCCW();
            float hx=across==EnumFacing.EAST?1:across==EnumFacing.WEST?0:.5F,hz=across==EnumFacing.SOUTH?1:across==EnumFacing.NORTH?0:.5F;
            String label="facing="+facing+" size="+mode+" inverted="+inverted+" end="+end;
            require(LiveGallery.place(world,player,new ItemStack(corner),at0,across,hx,.5F,hz),"diagonal corner placed against "+label);
            BlockPos at=at0.offset(across);
            require(world.getBlockState(at).getBlock()==corner,"diagonal corner beside the clicked wall: "+label);
            require(BlockProgrammableCornerWall.size(world,at)==mode,"diagonal corner takes the clicked wall's size: "+label+" got "+BlockProgrammableCornerWall.size(world,at));
            require(BlockProgrammableCornerWall.joins(world,at,at0),"diagonal corner continues the slope exactly: "+label+" corner="+world.getBlockState(at));
            BlockPos other=at.offset(across);
            world.setBlockState(other,wall.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing.getOpposite()).withProperty(BlockProgrammableWall.INVERTED,inverted),3);
            ((TileEntityAnimatedScreenSelector)world.getTileEntity(other)).setDiagonalGeometry(mode,0);
            require(!BlockProgrammableCornerWall.joins(world,at,other),"opposite-anchored wall does not join: "+label);
            world.setBlockToAir(other);
            joined++;
        }
        require(joined==48,"every diagonal facing, size, lean and end");
    }
    /** Size and Slope through the real server message with the wall dialog's container open. */
    private static void dialog(WorldServer world,EntityPlayerMP player){
        BlockProgrammableWall wall=(BlockProgrammableWall)block("programmable_diagonal_wall");
        BlockProgrammableCornerWall corner=(BlockProgrammableCornerWall)block("programmable_diagonal_corner_wall");
        BlockPos at=ORIGIN.add(0,0,40);
        world.setBlockState(at.west(),wall.getDefaultState().withProperty(BlockProgrammableWall.FACING,EnumFacing.NORTH),3);
        ((TileEntityAnimatedScreenSelector)world.getTileEntity(at.west())).setDiagonalGeometry(CornerGlassGeometry.FULL,0);
        require(LiveGallery.place(world,player,new ItemStack(corner),at.west(),EnumFacing.EAST,1,.5F,.5F),"dialog fixture corner wall");
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(at);
        player.connection.setPlayerLocation(at.getX()+.5,at.getY(),at.getZ()+3.5,180,20);
        player.openContainer=new ContainerAnimatedScreenSelector(player.inventory,tile);player.openContainer.windowId=1;
        int turn=corner.getMetaFromState(world.getBlockState(at))&3;
        for(int size=0;size<3;size++)for(int flags=0;flags<4;flags++){
            boolean inverted=(flags&1)!=0,far=(flags&2)!=0;
            MessageCornerGlassGeometry.apply(player,new MessageCornerGlassGeometry(at,size,inverted,far));
            int now=corner.getMetaFromState(world.getBlockState(at));
            require(BlockProgrammableCornerWall.size(world,at)==size,"dialog sets size "+size);
            require((now&3)==turn&&((now&4)!=0)==inverted&&((now&8)!=0)==far,"dialog sets slope and keeps the bevel's turn: meta="+now);
        }
        player.closeScreen();
    }
    /** Four flat corner walls stacked beside a four-high wall. */
    private static void stack(WorldServer world,EntityPlayerMP player){
        BlockProgrammableWall wall=(BlockProgrammableWall)block("programmable_wall");
        BlockProgrammableCornerWall corner=(BlockProgrammableCornerWall)block("programmable_corner_wall");
        for(int y=0;y<4;y++){
            BlockPos a=STACK.west().up(y);
            // Depth 0 is the middle band, so the bevel spans the cell and faces the camera.
            world.setBlockState(a,wall.getDefaultState().withProperty(BlockProgrammableWall.FACING,EnumFacing.NORTH).withProperty(BlockProgrammableWall.DEPTH,0),3);
            require(LiveGallery.place(world,player,new ItemStack(corner),a,EnumFacing.EAST,1,.5F,.5F),"stacked corner wall "+y);
            require(world.getBlockState(STACK.up(y)).getBlock()==corner,"stacked corner wall "+y+" placed");
        }
    }
}
