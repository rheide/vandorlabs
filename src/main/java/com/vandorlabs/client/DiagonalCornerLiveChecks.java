package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableCornerGlass;
import com.vandorlabs.blocks.BlockProgrammableWall;
import com.vandorlabs.container.ContainerProgrammableGlass;
import com.vandorlabs.network.MessageCornerGlassGeometry;
import com.vandorlabs.render.CornerGlassGeometry;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reported requirements for Diagonal Corner Glass: placed by clicking the end of any diagonal
 * glass, it takes that glass's size (tall / full / wide), lean and in-block position, and its
 * bevel continues the slope exactly; it never joins glass at a different position; the dialog's
 * Size and Slope change it like diagonal glass. Corners are placed through the real item path for
 * every facing (near and far anchoring), size and lean, then photographed for review.
 */
public final class DiagonalCornerLiveChecks {
    private static final BlockPos ORIGIN=new BlockPos(20,100,120);
    private static final EnumFacing[] FACINGS={EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST};
    private static int stage,ticks,checks;
    private static volatile boolean built;
    private static volatile Throwable failure;
    private static LiveGallery gallery;
    private DiagonalCornerLiveChecks(){}
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("diagonal-corner "+message);}

    public static boolean tick(Minecraft mc,File output)throws Exception{
        mc.gameSettings.pauseOnLostFocus=false;
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        if(failure!=null)throw new IllegalStateException("diagonal-corner server checks",failure);
        if(stage==0){
            if(mc.player==null||++ticks<40)return false;
            stage=1;
            mc.getIntegratedServer().addScheduledTask(()->{
                try{build(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));built=true;}
                catch(RuntimeException|LinkageError error){failure=error;}
            });
            return false;
        }
        if(!built)return false;
        if(gallery==null){
            gallery=new LiveGallery("diagonal-corner",ORIGIN);
            // Look over each size's rows of joined corners from the south-west, above.
            for(int mode=0;mode<3;mode++)gallery.shot("size"+mode,mode*24+9.5,4.5,22,180,32);
            // Close-ups: north-facing glass (lean off, east end) and its corner, from the south.
            for(int mode=0;mode<3;mode++)gallery.shot("close"+mode,mode*24+1.0,1.3,3.4,180,18);
            for(int mode=0;mode<3;mode++)gallery.shot("close-lean"+mode,mode*24+1.0,1.3,13.4,180,18);
        }
        if(!gallery.tick(mc,output))return false;
        System.out.println("[vandorlabs] diagonal-corner PASS checks="+checks+" every facing/size/lean continued by a clicked corner; other positions never join; dialog size and slope");
        return true;
    }

    private static int side(EnumFacing face){return (face.getHorizontalIndex()+2)&3;}
    private static void build(WorldServer world,EntityPlayerMP player){
        player.setGameType(net.minecraft.world.GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();
        world.setWorldTime(6000);
        BlockProgrammableWall glass=(BlockProgrammableWall)Block.getBlockFromName("vandorlabs:programmable_diagonal_glass");
        BlockProgrammableCornerGlass corner=(BlockProgrammableCornerGlass)Block.getBlockFromName("vandorlabs:programmable_diagonal_corner_glass");
        for(BlockPos at:BlockPos.getAllInBox(ORIGIN.add(-4,-1,-4),ORIGIN.add(76,4,40)))
            world.setBlockState(at,at.getY()==ORIGIN.getY()-1?Blocks.QUARTZ_BLOCK.getDefaultState():Blocks.AIR.getDefaultState(),2);
        List<String> joined=new ArrayList<>();
        for(int mode=0;mode<3;mode++)for(int f=0;f<4;f++)for(int lean=0;lean<2;lean++)for(int end=0;end<2;end++){
            EnumFacing facing=FACINGS[f];boolean inverted=lean==1;
            BlockPos wall=ORIGIN.add(mode*24+f*6,0,lean*10+end*5);
            world.setBlockState(wall,glass.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted),3);
            ((TileEntityAnimatedScreenSelector)world.getTileEntity(wall)).setDiagonalGeometry(mode,0);
            // Click the slope's end face; the pane runs across the facing.
            EnumFacing across=end==0?facing.rotateY():facing.rotateYCCW();
            float hx=across==EnumFacing.EAST?1:across==EnumFacing.WEST?0:.5F,hz=across==EnumFacing.SOUTH?1:across==EnumFacing.NORTH?0:.5F;
            require(LiveGallery.place(world,player,new ItemStack(corner),wall,across,hx,.5F,hz),"corner placed against "+facing+" size="+mode+" inverted="+inverted);
            BlockPos at=wall.offset(across);
            require(world.getBlockState(at).getBlock()==corner,"corner beside the clicked glass");
            TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)world.getTileEntity(at);
            require(tile.getCornerMode()==mode,"corner takes the clicked glass size "+mode+" got "+tile.getCornerMode());
            String label="facing="+facing+" size="+mode+" inverted="+inverted+" end="+end+" corner="+world.getBlockState(at);
            require(BlockProgrammableCornerGlass.joinsDiagonalWall(world,at,wall),"corner continues the slope exactly: "+label);
            require((corner.frameMask(world,at,world.getBlockState(at))&3)!=3,"corner hides its rail where it meets the glass: "+label);
            joined.add(label);
            // Glass anchored to the opposite face at the same lean is a different in-block position.
            BlockPos other=at.offset(across);
            world.setBlockState(other,glass.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing.getOpposite()).withProperty(BlockProgrammableWall.INVERTED,inverted),3);
            ((TileEntityAnimatedScreenSelector)world.getTileEntity(other)).setDiagonalGeometry(mode,0);
            require(!BlockProgrammableCornerGlass.joinsDiagonalWall(world,at,other),"opposite-anchored glass does not join: "+label);
            world.setBlockToAir(other);
        }
        System.out.println("[vandorlabs] diagonal-corner placed and joined "+joined.size()+" corners");
        dialog(world,player,corner,glass);
        player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
    }
    /** Size and Slope through the real server message with the glass dialog's container open. */
    private static void dialog(WorldServer world,EntityPlayerMP player,BlockProgrammableCornerGlass corner,BlockProgrammableWall glass){
        BlockPos at=ORIGIN.add(0,0,30);
        world.setBlockState(at.west(),glass.getDefaultState().withProperty(BlockProgrammableWall.FACING,EnumFacing.NORTH),3);
        ((TileEntityAnimatedScreenSelector)world.getTileEntity(at.west())).setDiagonalGeometry(CornerGlassGeometry.FULL,0);
        require(LiveGallery.place(world,player,new ItemStack(corner),at.west(),EnumFacing.EAST,1,.5F,.5F),"dialog fixture corner");
        TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)world.getTileEntity(at);
        // A dialog is only usable within reach, as for a player standing at the corner.
        player.connection.setPlayerLocation(at.getX()+.5,at.getY(),at.getZ()+3.5,180,20);
        player.openContainer=new ContainerProgrammableGlass(tile);player.openContainer.windowId=1;
        int meta=corner.getMetaFromState(world.getBlockState(at));
        for(int size=0;size<3;size++)for(int flags=0;flags<4;flags++){
            boolean inverted=(flags&1)!=0,far=(flags&2)!=0;
            MessageCornerGlassGeometry.apply(player,new MessageCornerGlassGeometry(at,size,inverted,far));
            IBlockState state=world.getBlockState(at);int now=corner.getMetaFromState(state);
            require(((TileEntityProgrammableGlass)world.getTileEntity(at)).getCornerMode()==size,"dialog sets size "+size);
            require((now&3)==(meta&3)&&((now&4)!=0)==inverted&&((now&8)!=0)==far,"dialog sets slope and keeps the bevel's turn: meta="+now);
        }
        player.closeScreen();
    }
}
