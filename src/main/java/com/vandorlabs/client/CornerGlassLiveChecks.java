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
    private static boolean baseDone;
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(baseDone){
            require(++ticks<1200,"bounded corner gallery");
            if(!gallery(mc,output))return false;
            System.out.println("[vandorlabs][reprolab] corner-glass PASS registers/actual placement/two straight joins/Join off/metadata/sloped profile/24 stack placements/client/gallery");
            return true;
        }
        if(++ticks==40)mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            verify(world,player);
            for(int x=1;x<=5;x++)for(int z=1;z<=6;z++)
                world.setBlockState(BASE.add(x,-1,z),Blocks.QUARTZ_BLOCK.getDefaultState(),3);
            world.setWorldTime(6000);
            player.capabilities.allowFlying=true;player.capabilities.isFlying=true;player.sendPlayerAbilities();
            player.connection.setPlayerLocation(BASE.getX()+4.5,BASE.getY()+.3,BASE.getZ()+5.5,148,17);
        }).get();
        if(ticks<65||!mc.world.isBlockLoaded(BASE)||mc.world.getBlockState(BASE).getBlock()!=Block.getBlockFromName(FLAT)){
            require(ticks<240,"client never received both corner panes");
            return false;
        }
        require(mc.world.getBlockState(BASE.up()).getBlock()==Block.getBlockFromName(FLAT),"stacked pane arrived on client");
        require(!((TileEntityProgrammableGlass)mc.world.getTileEntity(BASE.up())).hasFastRenderer(),"stacked corner keeps its authored renderer");
        require(mc.world.getBlockState(BASE.east(2)).getBlock()==Block.getBlockFromName(SLOPED),"sloped model arrived on the client");
        mc.setRenderViewEntity(mc.player);
        mc.gameSettings.hideGUI=true;
        mc.gameSettings.thirdPersonView=0;
        mc.player.rotationYaw=mc.player.prevRotationYaw=148;
        mc.player.rotationPitch=mc.player.prevRotationPitch=17;
        if(ticks<85)return false;
        require(mc.player.getDistanceSq(BASE.getX()+4.5,BASE.getY()+.3,BASE.getZ()+5.5)<1,"visible fixture camera");
        ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",
                new File(output,"corner-glass-middle.png"));
        CornerGlassIconChecks.run(mc,output);
        baseDone=true;
        return false;
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
        for(BlockProgrammableCornerGlass variant:new BlockProgrammableCornerGlass[]{corner,diagonal})for(int meta=0;meta<12;meta++){
            BlockPos support=BASE.add(-6,0,-6);
            world.setBlockState(support,variant.getStateFromMeta(meta),3);
            world.setBlockToAir(support.up());
            ItemStack placing=new ItemStack(variant);
            player.setHeldItem(EnumHand.MAIN_HAND,placing);
            require(placing.getItem().onItemUse(player,world,support,EnumHand.MAIN_HAND,EnumFacing.UP,.8F,1,.2F)==EnumActionResult.SUCCESS,
                    "stack corner through the actual ItemBlock hand overload");
            require(world.getBlockState(support.up()).getBlock()==variant&&variant.getMetaFromState(world.getBlockState(support.up()))==meta,
                    "stack retains every cardinal turn and Near/Middle/Far metadata="+meta);
            world.setBlockToAir(support.up());world.setBlockToAir(support);
        }
        world.setBlockState(BASE.up(),corner.getStateFromMeta(0),3);
        world.setBlockState(BASE.east(2),diagonal.getStateFromMeta(0),3);
        world.setBlockState(BASE.east(2).up(),diagonal.getStateFromMeta(0),3);
        require(corner.collisionRayTrace(state,world,BASE,new Vec3d(BASE).addVector(.25,.5,0),
                new Vec3d(BASE).addVector(.25,.5,.85))!=null,"ray crosses the bevel");
        require(corner.collisionRayTrace(state,world,BASE,new Vec3d(BASE).addVector(.9,.5,0),
                new Vec3d(BASE).addVector(.9,.5,.85))==null,"ray clears the missing corner");
    }
    private static final BlockPos GALLERY=new BlockPos(40,109,40);
    private static LiveGallery gallery;
    private static boolean galleryRequested;
    private static volatile boolean galleryBuilt;
    private static boolean gallery(Minecraft mc,File output)throws Exception{
        if(!galleryRequested){
            galleryRequested=true;
            mc.getIntegratedServer().addScheduledTask(()->{
                long start=System.nanoTime();
                System.out.println("[vandorlabs][reprolab] corner-glass gallery build start");
                buildGallery(mc.getIntegratedServer().getWorld(0));galleryBuilt=true;
                System.out.println("[vandorlabs][reprolab] corner-glass gallery built ms="+(System.nanoTime()-start)/1000000);
            });
        }
        if(!galleryBuilt)return false;
        if(gallery==null){
            gallery=new LiveGallery("corner-glass-gallery",GALLERY)
                    .shot("near-outside",2.3,1.7,2.3,135F,20F).shot("near-inside",-1.6,1.7,-1.6,-45F,20F)
                    .shot("middle-outside",6.3,1.7,2.3,135F,20F).shot("middle-inside",2.4,1.7,-1.6,-45F,20F)
                    .shot("far-outside",10.3,1.7,2.3,135F,20F).shot("far-inside",6.4,1.7,-1.6,-45F,20F)
                    .shot("floor-contact",5.6,.25,1.9,150F,12F)
                    .shot("sloped-outside",6.3,1.7,8.3,135F,15F).shot("sloped-inside",2.4,1.7,4.4,-45F,15F)
                    .shot("overview",11.5,3.5,9.5,135F,25F)
                    .shot("solo-rail-inside",13.3,.9,-1.7,-45F,35F).shot("solo-rail-outside",15.3,.9,.3,135F,35F);
        }
        return gallery.tick(mc,output);
    }
    private static void tint(WorldServer world,BlockPos at,int shade){
        TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)world.getTileEntity(at);
        if(tile!=null){tile.setShade(shade);tile.setJoin(true);}
    }
    private static void buildGallery(WorldServer world){
        BlockProgrammableGlass straight=(BlockProgrammableGlass)Block.getBlockFromName("vandorlabs:programmable_glass");
        BlockProgrammableCornerGlass corner=(BlockProgrammableCornerGlass)Block.getBlockFromName(FLAT);
        BlockProgrammableCornerGlass diagonal=(BlockProgrammableCornerGlass)Block.getBlockFromName(SLOPED);
        for(BlockPos at:BlockPos.getAllInBox(GALLERY.add(-6,-1,-6),GALLERY.add(14,4,12)))
            world.setBlockState(at,at.getY()==GALLERY.getY()-1?Blocks.QUARTZ_BLOCK.getDefaultState():Blocks.AIR.getDefaultState(),2);
        int[] depths={1,0,2};int[] mirror={0,2,1};
        for(int i=0;i<3;i++)for(int row=0;row<2;row++){
            BlockProgrammableCornerGlass variant=row==0?corner:diagonal;
            int d=depths[i];
            BlockPos at=GALLERY.add(i*4,0,row*6);
            for(int h=0;h<2;h++){
                BlockPos c=at.up(h);
                if(h==0)world.setBlockState(c,variant.getStateFromMeta(d*4),3);
                else{
                    // Stack the upper corner through the real item path.
                    EntityPlayerMP player=world.getMinecraftServer().getPlayerList().getPlayers().get(0);
                    require(LiveGallery.place(world,player,new ItemStack(variant),at,EnumFacing.UP,.3F,1,.7F),"stack corner in gallery");
                    require(world.getBlockState(c).getBlock()==variant&&variant.getMetaFromState(world.getBlockState(c))==d*4,"gallery stack keeps the lower bevel");
                }
                if(row==0){
                    world.setBlockState(c.west(),straight.getDefaultState().withProperty(BlockGlassWall.DEPTH,d),3);
                    world.setBlockState(c.north(),straight.getDefaultState().withProperty(BlockGlassWall.ROTATED,true).withProperty(BlockGlassWall.DEPTH,mirror[d]),3);
                    tint(world,c.west(),4);tint(world,c.north(),4);
                }
                tint(world,c,4);
            }
        }
        // A lone Middle corner isolates its own frame from neighbour joins.
        world.setBlockState(GALLERY.add(14,0,-1),corner.getStateFromMeta(0),3);tint(world,GALLERY.add(14,0,-1),4);
        world.setBlockState(GALLERY.add(14,-1,-1),Blocks.WOOL.getStateFromMeta(14),3);
        world.setWorldTime(6000);
    }
}
