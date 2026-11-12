package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockPressurePlateWeighted;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Real local redstone at every strength, followed by client-observed joined On/Off. */
final class JoinedDisplayPowerRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(620,100,620);
    private static int stage,ticks,frameEdit;
    private static boolean restoringFrame;
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException("joined display power stage="+stage+": "+why);}
    private static void server(Minecraft mc,Runnable run)throws Exception{mc.getIntegratedServer().addScheduledTask(run).get();}
    static boolean tick(Minecraft mc)throws Exception{
        if(stage==7)return true;
        require(++ticks<600,"client/native power update timeout edit="+frameEdit+" restoring="+restoringFrame+" mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPositionVector());
        if(stage>=5)return frameEdits(mc);
        if(stage==0){server(mc,()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);DisplayLocalReloadChecks.run(world);matrix(world);
            for(BlockPos p:BlockPos.getAllInBox(BASE.add(-3,-2,-3),BASE.add(4,4,3)))world.setBlockToAir(p);
            for(int x=0;x<2;x++)for(int y=0;y<2;y++){BlockPos p=BASE.add(x,y,0);world.setBlockState(p,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),3);configure((TileEntityAnimatedScreenSelector)world.getTileEntity(p));}
            world.setBlockState(BASE.west(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);
            net.minecraft.entity.player.EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.connection.setPlayerLocation(620.5,102,624,180,10);
        });stage=1;return false;}
        if(stage==4){
            for(int x=0;x<3;x++)for(int y=0;y<3;y++){net.minecraft.tileentity.TileEntity raw=mc.world.getTileEntity(BASE.add(x,y,0));if(!(raw instanceof TileEntityAnimatedScreenSelector))return false;TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)raw;if(tile.getSignalLevel()!=10||tile.activationState()!=2||tile.getSurfaceTexture(0)!=2)return false;}
            System.out.println("[vandorlabs] display-local-reload-client PASS joined 3x3 Wall Slider physical level 10 saved reload no retrigger");
            mc.player.closeScreen();mc.playerController.setGameType(net.minecraft.world.GameType.CREATIVE);mc.player.inventory.currentItem=0;
            server(mc,()->{
                WorldServer world=mc.getIntegratedServer().getWorld(0);
                for(int x=0;x<3;x++)for(int y=0;y<3;y++){
                    BlockPos pos=BASE.add(x,y,0);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
                    frameProfile(tile.frameAppearance,false);JoinedDisplayPower.changed(tile);tile.markDirty();world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);
                }
                net.minecraft.entity.player.EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                player.interactionManager.setGameType(net.minecraft.world.GameType.CREATIVE);player.inventory.currentItem=0;player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));player.inventoryContainer.detectAndSendChanges();player.connection.setPlayerLocation(620.5,102,624,180,10);
                BlockPos pos=BASE.add(2,2,0);player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR,world,pos.getX(),pos.getY(),pos.getZ());
            });stage=5;return false;
        }
        int expected=stage==2?0:15;
        for(int x=0;x<2;x++)for(int y=0;y<2;y++){
            net.minecraft.tileentity.TileEntity raw=mc.world.getTileEntity(BASE.add(x,y,0));if(!(raw instanceof TileEntityAnimatedScreenSelector))return false;
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)raw;if(tile.activationMode()!=2||tile.getSignalLevel()!=expected)return false;
            GlassSurfaceGroups.Layout layout=ScreenSurfaceGroups.layout(tile);require(layout.columns==2&&layout.rows==2,"local activation must preserve 2x2 joining");
        }
        if(stage==1){server(mc,()->mc.getIntegratedServer().getWorld(0).setBlockToAir(BASE.west()));stage=2;return false;}
        if(stage==2){server(mc,()->mc.getIntegratedServer().getWorld(0).setBlockState(BASE.add(2,1,0),Blocks.REDSTONE_BLOCK.getDefaultState(),3));stage=3;return false;}
        server(mc,()->DisplayLocalReloadChecks.reloadedScene(mc.getIntegratedServer().getWorld(0),BASE));stage=4;return false;
    }
    /** Warm server groups, then edit through the authenticated network handler without changing blocks. */
    private static boolean frameEdits(Minecraft mc)throws Exception{
        BlockPos pos=BASE.add(2,2,0);TileEntityAnimatedScreenSelector client=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(pos);
        if(!(mc.currentScreen instanceof GuiAnimatedScreenSelector)||client==null||mc.playerController.getCurrentGameType()!=net.minecraft.world.GameType.CREATIVE||mc.player.inventory.currentItem!=0||mc.player.getHeldItemMainhand().getItem()!=com.vandorlabs.items.ModItems.CONFIGURIZER||mc.player.getDistanceSq(620.5,102,624)>.05)return false;
        if(stage==5){
            if(!frameMatches(client.frameAppearance,restoringFrame))return false;
            server(mc,()->{
                WorldServer world=mc.getIntegratedServer().getWorld(0);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
                require(LoadedRedstonePower.level(world,pos)==0,"edited far corner must have no local source");
                require(tile.getSignalLevel()==(restoringFrame?0:10),"warm pre-edit server group "+frameEdit);
            });
            FrameAppearance draft=new FrameAppearance();net.minecraft.nbt.NBTTagCompound saved=new net.minecraft.nbt.NBTTagCompound();client.frameAppearance.write(saved);draft.read(saved);frameProfile(draft,!restoringFrame);
            com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageFrameAppearance(client,draft));stage=6;return false;
        }
        java.util.concurrent.atomic.AtomicBoolean applied=new java.util.concurrent.atomic.AtomicBoolean();
        server(mc,()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
            if(!frameMatches(tile.frameAppearance,!restoringFrame))return;
            require(tile.getSignalLevel()==(restoringFrame?10:0),"frame packet retained stale joined power edit="+frameEdit+" restoring="+restoringFrame);applied.set(true);
        });
        if(!applied.get()||!frameMatches(client.frameAppearance,!restoringFrame))return false;
        if(restoringFrame){frameEdit++;restoringFrame=false;}else restoringFrame=true;
        if(frameEdit<4){stage=5;return false;}
        mc.player.closeScreen();stage=7;
        System.out.println("[vandorlabs][reprolab] joined-display-power PASS seven families 16 local levels shared geometry bank isolation native 2x2 client On Off opposite corner no channel feedback frame packets Fit Split Top Bottom detach-rejoin");return true;
    }
    private static void frameProfile(FrameAppearance appearance,boolean changed){
        appearance.displayFit=!(changed&&frameEdit==0);appearance.split=!(changed&&frameEdit==1);
        appearance.splitTop=changed&&frameEdit==2?2:0;appearance.splitBottom=changed&&frameEdit==3?3:1;
    }
    private static boolean frameMatches(FrameAppearance appearance,boolean changed){
        return appearance.displayFit==!(changed&&frameEdit==0)&&appearance.split==!(changed&&frameEdit==1)
                &&appearance.splitTop==(changed&&frameEdit==2?2:0)&&appearance.splitBottom==(changed&&frameEdit==3?3:1);
    }
    private static void configure(TileEntityAnimatedScreenSelector tile){for(int band=0;band<4;band++)require(tile.configureActivation(2,band,band,1,1),"activation fixture configured");}
    private static void matrix(WorldServer world){
        Block[] blocks={ModBlocks.ANIMATED_SCREEN_SELECTOR,ModBlocks.PROGRAMMABLE_FULL_INPUT,ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN,ModBlocks.PROGRAMMABLE_INPUT,ModBlocks.PROGRAMMABLE_CONSOLE,ModBlocks.PROGRAMMABLE_HALF_CONSOLE,Block.getBlockFromName("vandorlabs:programmable_diagonal_half_screen")};
        BlockPos origin=BASE.add(20,0,0);
        for(Block block:blocks){
            for(BlockPos p:BlockPos.getAllInBox(origin.add(-4,-3,-4),origin.add(5,5,5)))world.setBlockToAir(p);
            world.setBlockState(origin,block.getDefaultState(),2);TileEntityAnimatedScreenSelector first=(TileEntityAnimatedScreenSelector)world.getTileEntity(origin);configure(first);
            DisplayJoinGeometry.Basis basis=DisplayJoinGeometry.basis(first,world.getBlockState(origin));List<TileEntityAnimatedScreenSelector> tiles=new ArrayList<>();tiles.add(first);
            for(int x=0;x<2;x++)for(int y=0;y<2;y++)if(x!=0||y!=0){BlockPos p=origin.add(basis.steps[0].getX()*x+basis.steps[2].getX()*y,basis.steps[0].getY()*x+basis.steps[2].getY()*y,basis.steps[0].getZ()*x+basis.steps[2].getZ()*y);world.setBlockState(p,block.getDefaultState(),2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);configure(tile);tiles.add(tile);}
            BlockPos source=origin.subtract(basis.steps[0]);world.setBlockState(source.down(),Blocks.STONE.getDefaultState(),2);world.setBlockState(source,Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.getDefaultState(),2);
            for(int level=0;level<=15;level++){
                world.setBlockState(source,Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.getDefaultState().withProperty(BlockPressurePlateWeighted.POWER,level),2);first.localInputChanged();
                require(LoadedRedstonePower.level(world,origin)==level,"native source level "+block+" level="+level);
                for(TileEntityAnimatedScreenSelector tile:tiles){int expected=tile==first||DisplayJoinGeometry.supportsJoining(first)?level:0;require(tile.getSignalLevel()==expected,"joined strength "+block+" level="+level+" at="+tile.getPos()+" actual="+tile.getSignalLevel());require(tile.transmittedChannels().isEmpty(),"joined receiver must not repeat channels");}
            }
            if(DisplayJoinGeometry.supportsJoining(first)){
                TileEntityAnimatedScreenSelector last=tiles.get(3);last.configureActivation(2,3,7,1,1);
                require(last.getSignalLevel()==0,"different state bank must not receive the neighbouring group's local power");
                last.configureActivation(2,3,3,1,1);require(last.getSignalLevel()==15,"matching settings rejoin immediately");
            }
        }
    }
    private JoinedDisplayPowerRuntimeChecks(){}
}
