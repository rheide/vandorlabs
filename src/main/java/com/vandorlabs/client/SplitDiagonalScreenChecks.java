package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableDiagonalScreen;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.blocks.RedstoneScreenInteractions;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.redstone.RedstoneChannels;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import java.io.File;
import java.util.concurrent.Future;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;

/** Configurizer, real picker packets and independent touches on the shared physical screen plane. */
final class SplitDiagonalScreenChecks {
    private static final BlockPos POS=new BlockPos(164,100,164);
    private static final int TOP=TileEntityAnimatedScreenSelector.SPLIT_TOP,BOTTOM=TileEntityAnimatedScreenSelector.SPLIT_BOTTOM;
    private static final int TOP_CHANNEL=870241,BOTTOM_CHANNEL=870242;
    private static final LiveGallery GALLERY=new LiveGallery("shot_split_diagonal",POS)
            .shot("forms",1.55,.12,-2.7,0,17)
            .shot("profile-comparison",-1.2,.3,.8,-42,15);
    private static Future<?> work;
    private static Future<Boolean> ready;
    private static int stage,wait;
    private static boolean done;

    private SplitDiagonalScreenChecks(){}
    private static EntityPlayerMP server(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static TileEntityAnimatedScreenSelector tile(net.minecraft.world.World world){return (TileEntityAnimatedScreenSelector)world.getTileEntity(POS);}
    private static void need(boolean good,String message){if(!good)throw new IllegalStateException("Split/diagonal screen stage "+stage+": "+message);}
    private static void advance(){stage++;wait=0;}
    static void begin(Minecraft mc){
        mc.playerController.setGameType(GameType.CREATIVE);
        work=mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP player=server(mc);player.dismountRidingEntity();player.setGameType(GameType.CREATIVE);
            player.inventory.setInventorySlotContents(1,ItemStack.EMPTY);
            player.inventory.currentItem=0;player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));
            player.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);player.connection.sendPacket(new SPacketHeldItemChange(0));
            player.capabilities.isFlying=true;player.sendPlayerAbilities();player.inventoryContainer.detectAndSendChanges();
            for(BlockPos cell:BlockPos.getAllInBox(POS.add(-3,-2,-4),POS.add(4,3,3)))player.world.setBlockToAir(cell);
            player.world.setBlockState(POS,ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN.getDefaultState(),3);
            player.world.setBlockState(POS.east(),ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN.getDefaultState(),3);
            BlockPos ceiling=POS.east(2);
            player.world.setBlockState(ceiling,ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN.getDefaultState()
                .withProperty(BlockProgrammableDiagonalScreen.INVERTED,true),3);
            ((TileEntityAnimatedScreenSelector)player.world.getTileEntity(ceiling)).setShallowDiagonalScreen(true);
            // An unobstructed oblique view of matching artwork on both profiles.
            for(int i=0;i<4;i++){
                BlockPos at=POS.add(i*2,0,4);
                player.world.setBlockState(at,ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN.getDefaultState()
                    .withProperty(BlockProgrammableDiagonalScreen.INVERTED,i>=2),3);
                ((TileEntityAnimatedScreenSelector)player.world.getTileEntity(at)).setShallowDiagonalScreen(i%2==0);
            }
            player.setPositionAndUpdate(POS.getX()+.5,POS.getY()+.4,POS.getZ()-2.1);
        });
    }
    static boolean tick(Minecraft mc,File output)throws Exception {
        if(done)return true;
        need(++wait<220,"timed out with "+mc.currentScreen+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPositionVector());
        if(work!=null){if(!work.isDone())return false;work.get();work=null;}
        if(stage==0&&wait>8&&localReady(mc,0)){
            if(!use(mc,0))return false;
            advance();
        }else if(stage==1&&wait>4&&mc.currentScreen instanceof GuiAnimatedScreenSelector){
            ScreenModeTransitionRuntimeChecks.button(mc.currentScreen,33);
            advance();
        }else if(stage==2&&wait>5&&tile(mc.world).isShallowDiagonalScreen()){
            work=mc.getIntegratedServer().addScheduledTask(()->geometry(server(mc)));
            ScreenModeTransitionRuntimeChecks.button(mc.currentScreen,3007);
            chooseHalf(mc,TOP);
            advance();
        }else if(stage==3&&wait>5){
            addRow(mc,TOP,"Top latch",TOP_CHANNEL);
            ScreenModeTransitionRuntimeChecks.button(mc.currentScreen,3006);
            chooseHalf(mc,BOTTOM);
            advance();
        }else if(stage==4&&wait>5){
            addRow(mc,BOTTOM,"Bottom latch",BOTTOM_CHANNEL);
            ScreenModeTransitionRuntimeChecks.button(mc.currentScreen,20);
            advance();
        }else if(stage==5&&wait>9&&mc.currentScreen==null){
            work=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=server(mc);TileEntityAnimatedScreenSelector screen=tile(p.world);
                need(screen.redstoneScreen(TOP).rows().size()==1&&screen.redstoneScreen(BOTTOM).rows().size()==1,"both half editors saved their own row");
                NBTTagCompound saved=screen.writeToNBT(new NBTTagCompound());
                TileEntityAnimatedScreenSelector loaded=(TileEntityAnimatedScreenSelector)ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN.createTileEntity(p.world,p.world.getBlockState(POS));
                loaded.setWorld(p.world);loaded.setPos(POS);loaded.readFromNBT(saved);
                need(loaded.isShallowDiagonalScreen()&&loaded.hasSplitRedstone()&&loaded.redstoneScreen(TOP).maxRows()==4
                    &&loaded.redstoneScreen(TOP).rows().get(0).channels.contains(TOP_CHANNEL)
                    &&loaded.redstoneScreen(BOTTOM).rows().get(0).channels.contains(BOTTOM_CHANNEL),"round trip keeps both choices, row banks and Shallow");
                NBTTagCompound item=((BlockProgrammableDiagonalScreen)ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN).createConfiguredDrop(screen).getSubCompound("BlockEntityTag");
                need(item!=null&&item.getBoolean(TileEntityAnimatedScreenSelector.SHALLOW_DIAGONAL)
                    &&item.hasKey(TileEntityAnimatedScreenSelector.redstoneTag(BOTTOM),10),"configured inventory item retains profile and halves");
                p.inventory.currentItem=1;p.connection.sendPacket(new SPacketHeldItemChange(1));p.inventoryContainer.detectAndSendChanges();
            });
            advance();
        }else if(stage==6&&localReady(mc,1)){
            if(!use(mc,TOP))return false;
            advance();
        }else if(stage==7&&wait>5&&localReady(mc,1)){
            if(!use(mc,BOTTOM))return false;
            advance();
        }else if(stage==8&&wait>7){
            work=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=server(mc);TileEntityAnimatedScreenSelector screen=tile(p.world);
                need(RedstoneChannels.level(p.world,POS,TOP_CHANNEL)==15&&RedstoneChannels.level(p.world,POS,BOTTOM_CHANNEL)==15,"real world touches address independent channels");
                screen.frameAppearance.split=false;screen.refreshSplitRows();
                need(RedstoneChannels.level(p.world,POS,TOP_CHANNEL)==0&&RedstoneChannels.level(p.world,POS,BOTTOM_CHANNEL)==0,"hidden halves stop publishing");
                screen.frameAppearance.split=true;screen.refreshSplitRows();
                need(RedstoneChannels.level(p.world,POS,TOP_CHANNEL)==15&&RedstoneChannels.level(p.world,POS,BOTTOM_CHANNEL)==15,"restored halves retain latched values");
            });
            advance();
        }else if(stage==9&&GALLERY.tick(mc,output)){
            System.out.println("[vandorlabs][reprolab] split-diagonal-screens PASS real Configurizer and half picker, separate live row clicks, four facings and two profile flips, NBT and pick item");
            done=true;
        }
        return done;
    }
    private static boolean localReady(Minecraft mc,int slot){
        return mc.playerController.getCurrentGameType()==GameType.CREATIVE&&mc.player.inventory.currentItem==slot
            &&(slot==0?mc.player.getHeldItemMainhand().getItem()==ModItems.CONFIGURIZER:mc.player.getHeldItemMainhand().isEmpty())
            &&tile(mc.world)!=null&&mc.player.getHeldItemOffhand().isEmpty();
    }
    private static void chooseHalf(Minecraft mc,int expected)throws Exception {
        FrameAppearancePane pane=(FrameAppearancePane)ScreenModeTransitionRuntimeChecks.field(mc.currentScreen,"framePane");
        ScreenModeTransitionRuntimeChecks.choose(mc.currentScreen,pane.picker(),-100001);
        need((Integer)ScreenModeTransitionRuntimeChecks.field(mc.currentScreen,"rowSlot")==expected,"correct half opens its own editor");
    }
    private static void addRow(Minecraft mc,int slot,String label,int channel)throws Exception {
        NativeRedstoneRowsPane[] editors=(NativeRedstoneRowsPane[])ScreenModeTransitionRuntimeChecks.field(mc.currentScreen,"rowEditors");
        RedstoneRowsPane pane=(RedstoneRowsPane)ScreenModeTransitionRuntimeChecks.field(editors[slot],"rows");
        ScreenModeTransitionRuntimeChecks.button(mc.currentScreen,600);
        pane.labelField().setText(label);pane.channelField().setText(Integer.toString(channel));
        ScreenModeTransitionRuntimeChecks.button(mc.currentScreen,580);
    }
    private static Vec3d point(IBlockState state,TileEntityAnimatedScreenSelector tile,int slot){
        ScreenSurface.Quad q=RedstoneScreenInteractions.surface(state,tile,slot);
        double v=slot==0?.35:(RedstoneScreenInteractions.rowTop(state.getBlock(),slot)+4)/64D;
        return new Vec3d(POS).addVector(.50,(q.topLeft.y+(q.bottomLeft.y-q.topLeft.y)*v)/16,
            (q.topLeft.z+(q.bottomLeft.z-q.topLeft.z)*v)/16);
    }
    private static boolean use(Minecraft mc,int control){
        Vec3d direction=point(mc.world.getBlockState(POS),tile(mc.world),control).subtract(mc.player.getPositionEyes(1));
        float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z)));
        mc.player.rotationYaw=mc.player.prevRotationYaw=yaw;mc.player.rotationPitch=mc.player.prevRotationPitch=pitch;
        if(ready==null){
            mc.player.connection.sendPacket(new CPacketPlayer.Rotation(yaw,pitch,false));
            ready=mc.getIntegratedServer().callFromMainThread(()->{
                EntityPlayerMP p=server(mc);int hotbar=control==0?0:1;
                return p.inventory.currentItem==hotbar&&p.interactionManager.getGameType()==GameType.CREATIVE
                    &&(hotbar==0?p.getHeldItemMainhand().getItem()==ModItems.CONFIGURIZER:p.getHeldItemMainhand().isEmpty())
                    &&Math.abs(p.rotationYaw-yaw)<.1&&Math.abs(p.rotationPitch-pitch)<.1;
            });
            return false;
        }
        if(!ready.isDone())return false;
        try{if(!ready.get())return false;}catch(Exception failed){throw new IllegalStateException(failed);}finally{ready=null;}
        Vec3d eye=mc.player.getPositionEyes(1),end=eye.add(Vec3d.fromPitchYaw(pitch,yaw).scale(5));
        IBlockState state=mc.world.getBlockState(POS);
        if(control!=0)need(RedstoneScreenInteractions.hitRow(state,tile(mc.world),control,eye.subtract(new Vec3d(POS)),end.subtract(new Vec3d(POS)),1)==0,"real client ray addresses its half");
        RayTraceResult hit=state.collisionRayTrace(mc.world,POS,eye,end);
        need(hit!=null,"ray hits actual shallow housing");
        mc.playerController.processRightClickBlock(mc.player,mc.world,POS,hit.sideHit,hit.hitVec,EnumHand.MAIN_HAND);
        return true;
    }
    private static void geometry(EntityPlayerMP player){
        TileEntityAnimatedScreenSelector screen=tile(player.world);
        need(screen.isShallowDiagonalScreen(),"actual profile button synchronized authoritative tile");
        BlockProgrammableDiagonalScreen block=(BlockProgrammableDiagonalScreen)ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN;
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean upper:new boolean[]{false,true}) {
            IBlockState state=block.getDefaultState().withProperty(BlockProgrammableDiagonalScreen.FACING,facing)
                .withProperty(BlockProgrammableDiagonalScreen.INVERTED,upper);
            AxisAlignedBB bounds=block.getBoundingBox(state,player.world,POS);
            need(bounds.maxY-bounds.minY==.5,"shallow collision height for "+facing);
            ScreenSurface.Quad q=RedstoneScreenInteractions.surface(state,screen,0),full=ScreenSurface.diagonal(upper,false);
            double length=Math.hypot(q.topLeft.y-q.bottomLeft.y,q.topLeft.z-q.bottomLeft.z);
            double fullLength=Math.hypot(full.topLeft.y-full.bottomLeft.y,full.topLeft.z-full.bottomLeft.z);
            need(q.ny==full.ny&&q.nz==full.nz&&Math.abs(length/fullLength
                -(q.topRight.x-q.topLeft.x)/(full.topRight.x-full.topLeft.x))<1e-7,"angle and display proportions");
            for(ScreenSurface.Vertex v:q.vertices)need(v.z>=8&&v.z<=15&&Math.abs(v.y-(upper?24-v.z:v.z-8))<1e-8,
                "image lies on the translated, clipped plane");
            com.vandorlabs.blocks.MachineMountSurface reader=com.vandorlabs.blocks.ScreenChipSupport.localMount(state,screen);
            need(Math.abs(reader.center.y-(upper?1.5-reader.center.z:reader.center.z-.5))<1e-8
                &&reader.center.x+reader.height/2<q.topLeft.x/16,"chip reader fits beside artwork");
            com.vandorlabs.blocks.DiagonalScreenShape shape=com.vandorlabs.blocks.DiagonalScreenShape.of(facing,upper,true);
            need(shape.collision.length==8,"clipped wedge has eight occupied slices");
            for(AxisAlignedBB slice:shape.collision)need(bounds.contains(new Vec3d(
                (slice.minX+slice.maxX)/2,(slice.minY+slice.maxY)/2,(slice.minZ+slice.maxZ)/2)),"collision slice within selection bounds");
            Vec3d front=com.vandorlabs.blocks.DiagonalScreenShape.world(.5,.25,.4,facing);
            need(!bounds.contains(front)&&shape.trace(BlockPos.ORIGIN,front.addVector(0,1,0),front.addVector(0,-1,0))==null,
                "front half has no selection or hidden pick face");
            Vec3d slope=com.vandorlabs.blocks.DiagonalScreenShape.world(.5,.5,.75,facing);
            Vec3d vertical=new Vec3d(0,upper?-1:1,0);
            RayTraceResult hit=shape.trace(BlockPos.ORIGIN,slope.add(vertical),slope.subtract(vertical));
            need(hit!=null&&Math.abs(hit.hitVec.y-(upper?.75:.25))<1e-7,"picked 45-degree incline");
            double y=upper?.9:.1,empty=upper?.4:.6;
            need(shape.trace(BlockPos.ORIGIN,com.vandorlabs.blocks.DiagonalScreenShape.world(-.2,y,.8,facing),com.vandorlabs.blocks.DiagonalScreenShape.world(1.2,y,.8,facing))!=null
                &&shape.trace(BlockPos.ORIGIN,com.vandorlabs.blocks.DiagonalScreenShape.world(-.2,empty,.8,facing),com.vandorlabs.blocks.DiagonalScreenShape.world(1.2,empty,.8,facing))==null,
                "ray distinguishes shallow solid and empty space for "+facing);
        }
    }
}
