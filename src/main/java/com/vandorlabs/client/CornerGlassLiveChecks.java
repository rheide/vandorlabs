package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.blocks.BlockProgrammableCornerGlass;
import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import java.io.File;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

final class CornerGlassLiveChecks {
    private static final BlockPos BASE=new BlockPos(5,109,5);
    private static final String FLAT="vandorlabs:programmable_corner_glass",SLOPED="vandorlabs:programmable_diagonal_corner_glass";
    private static int ticks;
    private CornerGlassLiveChecks(){}
    private static void require(boolean condition,String label){if(!condition)throw new IllegalStateException(label);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(++ticks==40)mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            verify(world,player);
            world.setBlockState(BASE.add(3,0,3),Blocks.STONE.getDefaultState(),3);
            player.connection.setPlayerLocation(BASE.getX()+3,BASE.getY()+1,BASE.getZ()+3,135,27);
        }).get();
        if(ticks<65||!mc.world.isBlockLoaded(BASE)||mc.world.getBlockState(BASE).getBlock()!=Block.getBlockFromName(FLAT)){
            require(ticks<240,"client never received both corner panes");
            return false;
        }
        require(mc.world.getBlockState(BASE.east(2)).getBlock()==Block.getBlockFromName(SLOPED),"sloped model arrived on the client");
        ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",
                new File(output,"corner-glass-middle.png"));
        System.out.println("[vandorlabs][reprolab] corner-glass PASS registers/actual placement/two straight joins/Join off/metadata/sloped profile/client");
        return true;
    }
    private static void verify(WorldServer world,EntityPlayerMP player){
        BlockProgrammableGlass straight=(BlockProgrammableGlass)Block.getBlockFromName("vandorlabs:programmable_glass");
        BlockProgrammableCornerGlass corner=(BlockProgrammableCornerGlass)Block.getBlockFromName(FLAT);
        BlockProgrammableCornerGlass diagonal=(BlockProgrammableCornerGlass)Block.getBlockFromName(SLOPED);
        for(BlockPos at:BlockPos.getAllInBox(BASE.add(-2,-1,-2),BASE.add(4,3,4)))world.setBlockToAir(at);
        world.setBlockState(BASE.down(),Blocks.STONE.getDefaultState(),3);
        world.setBlockState(BASE.west(),straight.getDefaultState(),3);
        world.setBlockState(BASE.north(),straight.getDefaultState().withProperty(BlockGlassWall.ROTATED,true),3);
        for(BlockProgrammableCornerGlass variant:new BlockProgrammableCornerGlass[]{corner,diagonal})for(int meta=0;meta<12;meta++)
            require(variant.getMetaFromState(variant.getStateFromMeta(meta))==meta,"placement metadata");
        ItemStack stack=new ItemStack(corner);
        player.setHeldItem(EnumHand.MAIN_HAND,stack);
        require(stack.getItem().onItemUse(player,world,BASE.west(),EnumHand.MAIN_HAND,EnumFacing.EAST,1,.5F,.5F)==EnumActionResult.SUCCESS,
                "place the corner from the edge of a Middle pane");
        IBlockState state=world.getBlockState(BASE);
        require(state.getBlock()==corner&&state.getValue(BlockGlassWall.DEPTH)==0&&state.getValue(BlockProgrammableCornerGlass.TURN)==0,
                "placement chooses a middle northwest bevel");
        require(corner.frameMask(world,BASE,state)==12,"both Middle straight contacts omit their shared rails");
        require(!straight.getActualState(world.getBlockState(BASE.west()),world,BASE.west()).getValue(BlockGlassWall.RIGHT),
                "straight contact reciprocally hides its inside rail");
        ((TileEntityProgrammableGlass)world.getTileEntity(BASE.north())).setJoin(false);
        require(corner.frameMask(world,BASE,state)==14,"Join Off retains the north edge");
        ((TileEntityProgrammableGlass)world.getTileEntity(BASE.north())).setJoin(true);
        world.setBlockState(BASE.east(2),diagonal.getStateFromMeta(0),3);
        require(corner.collisionRayTrace(state,world,BASE,new Vec3d(BASE).addVector(.25,.5,0),
                new Vec3d(BASE).addVector(.25,.5,.85))!=null,"ray crosses the bevel");
        require(corner.collisionRayTrace(state,world,BASE,new Vec3d(BASE).addVector(.9,.5,0),
                new Vec3d(BASE).addVector(.9,.5,.85))==null,"ray clears the missing corner");
    }
}
