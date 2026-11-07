package com.vandorlabs.client;
import com.vandorlabs.segmented.*;
import com.vandorlabs.tiles.DeferredTileLoad;
import net.minecraft.client.Minecraft;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.io.File;
/** Focused native placement and actual optional-wand integration, independent of density benchmarks. */
final class SegmentedDoorLiveChecks {
    private static int stage,ticks,wait;private static final BlockPos BASE=new BlockPos(1200,110,1200),TOP=BASE.up(2);
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("segmented-door: "+why);}
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(stage==6){if(!SegmentedDoorInteractionChecks.tick(mc))return false;stage=7;}
        if(stage==7)return SegmentedDoorPickLiveChecks.tick(mc);
        mc.gameSettings.pauseOnLostFocus=false;if(++ticks<40)return false;
        if(stage==0){mc.displayGuiScreen(null);mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP p=actor(mc);WorldServer w=p.getServerWorld();Block door=Block.getBlockFromName("vandorlabs:segmented_door");for(BlockPos at:BlockPos.getAllInBox(BASE.add(-2,-1,-2),BASE.add(5,6,3)))w.setBlockToAir(at);
            for(int x=0;x<3;x++)for(int y=0;y<3;y++)w.setBlockState(BASE.add(x,y,0),door.getDefaultState(),3);for(int n=0;n<4;n++)DeferredTileLoad.flush(w);
            Item wand=Item.REGISTRY.getObject(new ResourceLocation("betterbuilderswands:wanddiamond"));if(wand==null)wand=Item.REGISTRY.getObject(new ResourceLocation("betterbuilderswands:wandDiamond"));require(wand!=null,"installed BetterBuildersWands JAR required");
            p.setGameType(GameType.CREATIVE);p.inventory.clear();p.inventory.currentItem=0;p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(wand));p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();p.connection.setPlayerLocation(BASE.getX()+1.5,BASE.getY()+4,BASE.getZ()-2,0,(float)Math.toDegrees(Math.atan2(1+p.getEyeHeight(),2.5)));p.sendContainerToPlayer(p.inventoryContainer);
        }).get();mc.playerController.setGameType(GameType.CREATIVE);mc.player.inventory.currentItem=0;mc.player.capabilities.isFlying=true;stage=1;return false;}
        if(stage==1){
            if(++wait>240)throw new IllegalStateException("segmented wand setup timeout stage="+stage+" mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPositionVector());
            SegmentedDoorTile root=SegmentedDoorTile.at(mc.world,TOP.east());ItemStack held=mc.player.getHeldItemMainhand();if(root==null||!root.ready()||root.height!=3||held.isEmpty()||!held.getItem().getRegistryName().getResourceDomain().equals("betterbuilderswands")||mc.player.inventory.currentItem!=0||mc.playerController.getCurrentGameType()!=GameType.CREATIVE||mc.player.getDistanceSq(BASE.getX()+1.5,BASE.getY()+4,BASE.getZ()-2)>.1)return false;
            mc.playerController.processRightClickBlock(mc.player,mc.world,TOP.east(),EnumFacing.UP,new Vec3d(TOP.east()).addVector(.5,1,.5),EnumHand.MAIN_HAND);stage=2;wait=0;return false;
        }
        if(stage==2){if(++wait<20)return false;String failure=mc.getIntegratedServer().callFromMainThread(()->{WorldServer w=actor(mc).getServerWorld();StringBuilder why=new StringBuilder();for(int x=0;x<3;x++){SegmentedDoorTile t=SegmentedDoorTile.at(w,TOP.up().east(x));if(t==null||!t.isRoot()||t.height!=4)why.append(" column=").append(x).append(" top=").append(t==null?"missing":t.topOffset+"/"+t.height);}return why.toString();}).get();
            require(failure.isEmpty(),"real wand extrusion topology"+failure);
            for(int x=0;x<3;x++){SegmentedDoorTile top=SegmentedDoorTile.at(mc.world,TOP.up().east(x));if(top==null||!top.ready()||!top.isRoot()||top.height!=4){if(wait<120)return false;throw new IllegalStateException("segmented client header missing column="+x);}}
            System.out.println("[vandorlabs][reprolab] segmented-wand PASS real optional wand packet three-column top extrusion server/client headers");
            mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=actor(mc);p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));p.inventory.setInventorySlotContents(2,new ItemStack(net.minecraft.init.Blocks.GOLD_BLOCK,64));p.sendContainerToPlayer(p.inventoryContainer);}).get();stage=3;wait=0;return false;
        }
        if(stage==3){if(mc.player.getHeldItemMainhand().getItem()!=com.vandorlabs.items.ModItems.CONFIGURIZER||mc.player.inventory.getStackInSlot(2).getItem()!=Item.getItemFromBlock(net.minecraft.init.Blocks.GOLD_BLOCK))return false;mc.playerController.processRightClickBlock(mc.player,mc.world,TOP.up().east(),EnumFacing.UP,new Vec3d(TOP.up().east()).addVector(.5,1,.5),EnumHand.MAIN_HAND);stage=4;wait=0;return false;}
        if(stage==4){if(!(mc.currentScreen instanceof GuiSegmentedDoor)||++wait<5)return false;SegmentedDoorDialogChecks.run(mc,(GuiSegmentedDoor)mc.currentScreen,output);stage=5;wait=0;return false;}
        if(stage==5){if(++wait<10)return false;mc.player.closeScreen();mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=actor(mc);SegmentedDoorTile root=SegmentedDoorTile.at(p.getServerWorld(),TOP.up().east());require(root.settings.window&&root.settings.shape==3&&root.settings.join&&root.cellArtwork.overridden&&root.effectiveTexture()==com.vandorlabs.tiles.CustomBlockMaterials.choice(p.inventory.getStackInSlot(2)),"authorized dialog packet did not configure all window/custom fields");SegmentedDoorRuntimeChecks.run(p.getServerWorld(),p);}).get();SegmentedDoorClockChecks.run(mc);SegmentedDoorRenderChecks.run(mc,output);System.out.println("[vandorlabs][reprolab] segmented-door-live PASS actual wand/configuration packets window/glass rendered matrix legacy compatibility");stage=6;return false;
        }return false;
    }
    private SegmentedDoorLiveChecks(){}
}
