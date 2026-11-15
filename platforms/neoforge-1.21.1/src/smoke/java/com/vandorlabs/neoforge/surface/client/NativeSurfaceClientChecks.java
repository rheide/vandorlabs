package com.vandorlabs.neoforge.surface.client;

import com.vandorlabs.neoforge.surface.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** One real-player pad, standard picker, mining-root opacity and ordinary repair. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeSurfaceClientChecks {
    private static int stage,ticks,age,checks,placed,oldWindow;private static boolean capturePending,firstImage;
    private static final BlockPos ROOT=NativeSurfaceClientFixture.ROOT;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Surface client stage="+stage+" age="+age+" tick="+ticks+": "+why);}
    private static void next(int value){stage=value;age=0;}
    private static NativeSurfaceEntity tile(Minecraft mc,int x,int z){return mc.level.getBlockEntity(ROOT.offset(x,0,z)) instanceof NativeSurfaceEntity value?value:null;}
    private static void mode(int operation){PacketDistributor.sendToServer(new NativeSurfaceClientFixture.Mode(operation));}
    private static boolean ack(Minecraft mc,int operation){return mc.level.getBlockState(NativeSurfaceClientFixture.ACK.east(operation)).is(Blocks.STONE);}
    private static BlockHitResult aim(Minecraft mc,BlockPos pos,Direction face){var point=Vec3.atCenterOf(pos).add(face.getStepX()*.5,face.getStepY()*.5,face.getStepZ()*.5);var delta=point.subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))));mc.player.yRotO=mc.player.getYRot();mc.player.xRotO=mc.player.getXRot();mc.gameRenderer.pick(1);need(mc.hitResult instanceof BlockHitResult actual&&actual.getBlockPos().equals(pos)&&actual.getDirection()==face,"actual fresh vanilla ray reaches "+pos+"/"+face+" got="+mc.hitResult);return (BlockHitResult)mc.hitResult;}
    private static void use(Minecraft mc,BlockPos pos,Direction face){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,aim(mc,pos,face));}
    private static void click(NativeSurfaceScreen screen,int id,int mouse){var button=screen.control(id);need(button!=null&&button.active&&screen.mouseClicked(button.getX()+button.getWidth()/2D,button.getY()+8,mouse),"actual widget "+id);}
    private static void search(NativeSurfaceScreen screen){int choice=NativeSurfaceArtwork.choice("material:floor_floor_tile_green");need(choice>=0,"shared Surface artwork in native material catalog");screen.materialPicker().searchField().setValue(com.vandorlabs.neoforge.ModernFinishCatalog.option(choice).texture());}
    private static void choose(NativeSurfaceScreen screen){int choice=NativeSurfaceArtwork.choice("material:floor_floor_tile_green");var picker=screen.materialPicker();int row=picker.rows().indexOf(choice);need(row>=0&&row<picker.visible(),"actual filtered material row");need(screen.mouseClicked(picker.searchField().getX()+8,picker.top()+row*22+10,0),"actual standard material selection");}
    private static boolean configured(Minecraft mc){for(int x=0;x<2;x++)for(int z=0;z<2;z++){var tile=tile(mc,x,z);if(tile==null||tile.layoutMode()!=1||!tile.inactiveArtwork().equals(NativeSurfaceArtwork.fromChoice(NativeSurfaceArtwork.choice("material:floor_floor_tile_green")))||tile.layout().cells.size()!=4)return false;}return true;}
    private static void image(Minecraft mc,String name)throws Exception{need(mc.getOverlay()==null,"no loading overlay in capture");mc.getToasts().clear();mc.gui.getChat().clearMessages(true);try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){var path=mc.gameDirectory.toPath().resolve("screenshots/"+name+".png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);}}
    private static void opaque(Minecraft mc,NativeSurfaceEntity tile){var model=mc.getBlockRenderer().getBlockModel(tile.getBlockState());var quads=model.getQuads(tile.getBlockState(),null,net.minecraft.util.RandomSource.create(0),tile.getModelData(),net.minecraft.client.renderer.RenderType.cutoutMipped());double area=0;int backing=0;for(var quad:quads){need(quad.getDirection()==tile.normal(),"cell face retains outward normal");if(!quad.getSprite().contents().name().equals(net.minecraft.resources.ResourceLocation.parse("minecraft:block/black_concrete")))continue;backing++;int[] data=quad.getVertices();int stride=data.length/4;double[] a=new double[3],b=new double[3];for(int axis=0;axis<3;axis++){a[axis]=Float.intBitsToFloat(data[2*stride+axis])-Float.intBitsToFloat(data[axis]);b[axis]=Float.intBitsToFloat(data[stride+axis])-Float.intBitsToFloat(data[axis]);}double x=a[1]*b[2]-a[2]*b[1],y=a[2]*b[0]-a[0]*b[2],z=a[0]*b[1]-a[1]*b[0];area+=Math.sqrt(x*x+y*y+z*z)/2;}need(backing==2&&Math.abs(area-1)<1e-6,"every cell emits a complete opaque backing independent of drawing root");}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {if(!NativeSurfaceClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}if(stage==100&&mc.level==null){System.out.println("[vandorlabs][surface-client] client PASS checks="+checks+" survivalPlacement/sharedDedicatedRoutes/previousRight/materialPicker/rootMining/opaqueSurvivors/repair");next(101);mc.stop();return;}if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(!(mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen),"unexpected disconnect");need(++ticks<1200&&++age<240,"bounded journey mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position()+" screen="+mc.screen);if(age%80==0)System.out.println("[vandorlabs][surface-client] progress stage="+stage+" age="+age+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position());var gui=mc.screen instanceof NativeSurfaceScreen value?value:null;switch(stage){
        case 0->{if(mc.getOverlay()!=null||mc.screen!=null||mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||mc.player.getMainHandItem().getCount()!=8||mc.player.position().distanceToSqr(.5,96,3.5)>.01)return;if(!com.vandorlabs.neoforge.transfer.client.NativeSnapshotPaletteChecks.ready(mc))return;next(1);}
        case 1->{if(placed>0&&tile(mc,(placed-1)%2,(placed-1)/2)==null)return;if(mc.player.getMainHandItem().getCount()!=8-placed)return;if(placed==1&&!firstImage){mc.options.hideGui=true;next(1011);return;}if(placed==4){for(int x=0;x<2;x++)for(int z=0;z<2;z++)if(tile(mc,x,z).layout().cells.size()!=4)return;mc.options.hideGui=true;next(1010);return;}use(mc,ROOT.offset(placed%2,-1,placed/2),Direction.UP);placed++;}
        case 2->{if(!ack(mc,0)||mc.player.getInventory().selected!=3||!mc.player.getMainHandItem().isEmpty())return;mc.options.keyShift.setDown(true);next(3);}
        case 3->{if(!mc.player.isShiftKeyDown()||age<6)return;use(mc,ROOT,Direction.UP);next(4);}
        case 4->{if(gui==null)return;mc.options.keyShift.setDown(false);oldWindow=gui.getMenu().containerId;click(gui,1,1);search(gui);next(5);}
        case 5->{if(gui==null)return;choose(gui);next(6);}
        case 6->{if(gui==null||!configured(mc))return;if(age<12||NativeSurfacePixels.cleanFrames()<2)return;image(mc,"native-surface-settings");click(gui,2,0);mode(1);next(7);}
        case 7->{if(!ack(mc,1)||mc.player.getInventory().selected!=1||!mc.player.getMainHandItem().is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get())||mc.player.isShiftKeyDown())return;use(mc,ROOT,Direction.UP);next(8);}
        case 8->{if(gui==null)return;need(gui.getMenu().containerId!=oldWindow&&gui.control(1)!=null&&gui.materialPicker()!=null,"same dedicated controls via Configurizer");click(gui,2,0);PacketDistributor.sendToServer(new NativeSurfaceSettingsPayload(oldWindow,ROOT,"original_on","original_off",0));mode(2);next(9);}
        case 9->{if(!ack(mc,2)||mc.player.getInventory().selected!=2||!mc.player.getMainHandItem().is(Items.STONE_PICKAXE)||mc.player.position().distanceToSqr(.5,96,-2.5)>.01)return;need(configured(mc),"closed-window settings packet cannot change the pad");aim(mc,ROOT,Direction.NORTH);mc.gameMode.startDestroyBlock(ROOT,Direction.NORTH);next(10);}
        case 10->{if(mc.level.getBlockState(ROOT).is(NativeSurfaceBindings.BLOCK)){aim(mc,ROOT,Direction.NORTH);mc.gameMode.continueDestroyBlock(ROOT,Direction.NORTH);return;}mc.gameMode.stopDestroyBlock();for(int x=0;x<2;x++)for(int z=0;z<2;z++)if(x!=0||z!=0){var tile=tile(mc,x,z);if(tile==null||tile.appearanceLayout().cells.size()!=1)return;opaque(mc,tile);}mode(3);next(11);}
        case 11->{if(!ack(mc,3)||age<50)return;mode(4);next(12);}
        case 12->{if(!ack(mc,4)||mc.player.getInventory().selected!=0||mc.player.getMainHandItem().getCount()!=4)return;use(mc,ROOT.below(),Direction.UP);next(13);}
        case 13->{if(tile(mc,0,0)==null||mc.player.getMainHandItem().getCount()!=3)return;mode(5);next(14);}
        case 14->{if(!ack(mc,5)||mc.player.getInventory().selected!=1||!mc.player.getMainHandItem().is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return;use(mc,ROOT,Direction.NORTH);next(15);}
        case 15->{if(gui==null)return;click(gui,1,1);search(gui);next(16);}
        case 16->{if(gui==null||!NativeSurfacePixels.begin())return;choose(gui);next(17);}
        case 17->{if(gui==null||!configured(mc))return;click(gui,2,0);next(18);}
        case 18->{if(mc.screen!=null||age<20||!NativeSurfacePixels.uploaded())return;for(int x=0;x<2;x++)for(int z=0;z<2;z++)opaque(mc,tile(mc,x,z));NativeSurfacePixels.arm();next(181);}
        case 181->{if(!NativeSurfacePixels.done())return;mode(6);next(19);}
        case 1010->{if(age<4||NativeSurfacePixels.cleanFrames()<2)return;image(mc,"native-surface-complete");mc.options.hideGui=false;mode(0);next(2);}
        case 1011->{if(age<4||NativeSurfacePixels.cleanFrames()<2)return;image(mc,"native-surface-first");mc.options.hideGui=false;firstImage=true;next(1);}
        case 19->{if(!ack(mc,6))return;mc.level.disconnect();mc.disconnect();next(100);}
        default->{}
    }}
}
