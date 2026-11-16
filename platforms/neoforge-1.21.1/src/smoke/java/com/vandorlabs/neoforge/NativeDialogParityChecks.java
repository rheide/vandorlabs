package com.vandorlabs.neoforge;

import com.vandorlabs.ui.DialogStyle;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Actual production screen carousel. Client-only presentation setup makes no gameplay/authorization claim. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeDialogParityChecks {
    private record Case(String type,DialogStyle.Shell style){}
    private record Rect(int left,int top,int right,int bottom,int color){int area(){return (right-left)*(bottom-top);}}
    private static final Case[] CASES={
        new Case("com.vandorlabs.neoforge.client.NativeCubeScreen",DialogStyle.DARK),
        new Case("com.vandorlabs.neoforge.client.NativeStorageScreen",DialogStyle.DARK),
        new Case("com.vandorlabs.neoforge.client.NativeTriggerScreen",DialogStyle.DARK_TITLE_12),
        new Case("com.vandorlabs.neoforge.client.NativeDoorScreen",DialogStyle.PROGRAMMABLE),
        new Case("com.vandorlabs.neoforge.client.NativeGlassScreen",DialogStyle.DARK),
        new Case("com.vandorlabs.neoforge.client.NativeLightScreen",DialogStyle.PROGRAMMABLE),
        new Case("com.vandorlabs.neoforge.client.NativeScreenScreen",DialogStyle.PROGRAMMABLE),
        new Case("com.vandorlabs.neoforge.client.NativePropulsionScreen",DialogStyle.SIGNAL),
        new Case("com.vandorlabs.neoforge.client.NativeTrapdoorScreen",DialogStyle.PROGRAMMABLE),
        new Case("com.vandorlabs.neoforge.client.NativeSegmentedScreen",DialogStyle.SEGMENTED),
        new Case("com.vandorlabs.neoforge.client.NativeCopifierScreen",DialogStyle.COPIFIER),
        new Case("com.vandorlabs.neoforge.client.NativeCredentialReaderScreen",DialogStyle.PORT),
        new Case("com.vandorlabs.neoforge.client.NativeWirelessScreen",DialogStyle.PORT),
        new Case("com.vandorlabs.neoforge.client.NativeBinaryControlScreen",DialogStyle.SIGNAL),
        new Case("com.vandorlabs.neoforge.gear.client.NativeGearScreen",DialogStyle.GEAR),
        new Case("com.vandorlabs.neoforge.gear.track.client.NativeTrackScreen",DialogStyle.GEAR),
        new Case("com.vandorlabs.neoforge.legacylanding.client.NativeLegacyLandingScreen",DialogStyle.LANDING),
        new Case("com.vandorlabs.neoforge.reservoir.client.NativeBufferScreen",DialogStyle.BUFFER),
        new Case("com.vandorlabs.neoforge.surface.client.NativeSurfaceScreen",DialogStyle.SURFACE),
        new Case("com.vandorlabs.neoforge.rack.client.NativeRackScreen",DialogStyle.RACK),
        new Case("com.vandorlabs.neoforge.plate.client.NativePlateScreen",DialogStyle.PROGRAMMABLE),
        new Case("com.vandorlabs.neoforge.copier.client.NativeCopyScreen",DialogStyle.COPY),
        new Case("com.vandorlabs.neoforge.ports.client.NativePortScreen",DialogStyle.PORT),
        new Case("com.vandorlabs.neoforge.client.NativeCanopyScreen",DialogStyle.CANOPY),
        new Case("com.vandorlabs.neoforge.client.NativeProgrammerScreen",DialogStyle.PROGRAMMER),
        new Case("com.vandorlabs.neoforge.client.TransmitterScreen",DialogStyle.SIGNAL),
        new Case("com.vandorlabs.neoforge.seating.client.NativeSeatScreen",DialogStyle.CHAIR),    };
    private static final BlockPos POSITION=new BlockPos(0,102,0);
    private static AbstractContainerScreen<?> screen;
    private static final int START=Integer.getInteger("vandorlabs.dialogParityStart",0);
    private static int index=START,variant,ticks,settle,captures,hoverX=-1,hoverY=-1,buttonX=-1,buttonY=-1;
    private static com.mojang.blaze3d.platform.NativeImage logo;
    private static boolean capture,done;
    private static Observed observed;
    private static void need(boolean value,String message){if(!value)throw new AssertionError("Dialog parity case="+index+" variant="+variant+" "+message);}
    private static final class Observed extends GuiGraphics {
        final List<Rect> fills=new ArrayList<>();final List<int[]> logos=new ArrayList<>(),buttons=new ArrayList<>();final List<Integer> textColors=new ArrayList<>();
        Observed(Minecraft mc){super(mc,mc.renderBuffers().bufferSource());}
        @Override public void fill(int x0,int y0,int x1,int y1,int color){fills.add(new Rect(x0,y0,x1,y1,color));super.fill(x0,y0,x1,y1,color);}
        @Override public void drawCenteredString(net.minecraft.client.gui.Font font,Component text,int x,int y,int color){textColors.add(color&0xffffff);super.drawCenteredString(font,text,x,y,color);}
        @Override public void blit(ResourceLocation texture,int x,int y,float u,float v,int width,int height,int textureWidth,int textureHeight){if(texture.toString().equals(DialogStyle.LOGO))logos.add(new int[]{x,y,width,height});if(texture.toString().equals("vandorlabs:textures/gui/source_buttons.png"))buttons.add(new int[]{x,y,(int)u,(int)v,width,height});super.blit(texture,x,y,u,v,width,height,textureWidth,textureHeight);}
    }
    private static void logoPixels(Minecraft mc,com.mojang.blaze3d.platform.NativeImage image,int left,int top,int width,int height)throws Exception {
        if(logo==null)try(var stream=mc.getResourceManager().getResourceOrThrow(ResourceLocation.parse(DialogStyle.LOGO)).open()){logo=com.mojang.blaze3d.platform.NativeImage.read(stream);}
        double sx=image.getWidth()/(double)mc.getWindow().getGuiScaledWidth(),sy=image.getHeight()/(double)mc.getWindow().getGuiScaledHeight();int checked=0;
        for(int y=0;y<logo.getHeight();y++)for(int x=0;x<logo.getWidth();x++){int authored=logo.getPixelRGBA(x,y);if((authored>>>24)!=255)continue;int px=(int)Math.floor((left+(x+.5)*width/logo.getWidth())*sx),py=(int)Math.floor((top+(y+.5)*height/logo.getHeight())*sy);need((image.getPixelRGBA(px,py)&0xffffff)==(authored&0xffffff),"unoccluded original-logo pixel "+x+","+y);checked++;}need(checked>=20,"authored logo opaque sample coverage");
    }
    private static Object member(Object value,String name)throws Exception {for(Class<?> type=value.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(value);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(value.getClass()+"."+name);}
    private static UUID identity(BlockEntity owner){for(String name:new String[]{"identity","key"})try{return (UUID)owner.getClass().getMethod(name).invoke(owner);}catch(ReflectiveOperationException ignored){}return UUID.randomUUID();}
    private static Class<?> ownerType(Class<?> menu){try{return menu.getField("tile").getType();}catch(NoSuchFieldException ignored){}try{return menu.getMethod("root").getReturnType();}catch(NoSuchMethodException ignored){}return null;}
    private static BlockEntity owner(Minecraft mc,Class<?> expected,String family)throws Exception {
        if(expected==null)return null;
        var blocks=new ArrayList<net.minecraft.world.level.block.Block>();for(var block:BuiltInRegistries.BLOCK)if(BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("vandorlabs")&&block instanceof EntityBlock)blocks.add(block);
        String preferred=family.contains("Cube")?"programmable_block":family.contains("Seat")?"programmable_chair":family.contains("Buffer")?"fuel_tank":family.contains("Port")?"matter_port":family.contains("Door")?"programmable_door":family.contains("Gear")?"gear_extender":"";
        blocks.sort(Comparator.comparingInt(block->BuiltInRegistries.BLOCK.getKey(block).getPath().equals(preferred)?0:1));
        for(var block:blocks){var state=block.defaultBlockState();BlockEntity tile;try{tile=((EntityBlock)block).newBlockEntity(POSITION,state);}catch(RuntimeException ignored){continue;}if(tile==null||!expected.isInstance(tile))continue;
            mc.level.setBlock(POSITION,state,2);tile.setLevel(mc.level);mc.level.setBlockEntity(tile);
            for(var property:state.getProperties())if(property.getName().equals("half"))for(Object value:property.getPossibleValues())if(value.toString().equalsIgnoreCase("upper")){@SuppressWarnings({"unchecked","rawtypes"})var upper=state.setValue((net.minecraft.world.level.block.state.properties.Property)property,(Comparable)value);mc.level.setBlock(POSITION.above(),upper,2);}
            if(family.equals("NativeGearScreen")){
                var tag=tile.saveWithoutMetadata(mc.level.registryAccess());tag.putBoolean("GearController",true);tag.putBoolean("GearOperational",true);tag.putInt("GearHeight",1);tag.putString("GearKind","NONE");tag.putLong("GearOwnerOffset",0);var feet=new net.minecraft.nbt.ListTag();feet.add(new net.minecraft.nbt.IntArrayTag(new int[]{0,-1,0}));tag.put("GearFeet",feet);tile.loadWithComponents(tag,mc.level.registryAccess());
                need(((com.vandorlabs.neoforge.gear.NativeGearEntity)tile).controllerEntity()==tile,"presentation arm consumes complete saved controller metadata");
            }
            return tile;
        }throw new IllegalStateException("No registered presentation owner for "+expected+" family="+family);
    }
    private static Object[] arguments(Class<?>[] types,Minecraft mc,BlockEntity owner,AbstractContainerMenu menu){Object[] args=new Object[types.length];for(int i=0;i<types.length;i++){Class<?> type=types[i];if(menu!=null&&type.isInstance(menu))args[i]=menu;else if(type==int.class)args[i]=37;else if(type==Inventory.class)args[i]=mc.player.getInventory();else if(type==BlockPos.class)args[i]=POSITION;else if(type==UUID.class)args[i]=identity(owner);else if(Player.class.isAssignableFrom(type))args[i]=mc.player;else if(MenuType.class.isAssignableFrom(type))args[i]=null;else if(owner!=null&&type.isInstance(owner))args[i]=owner;else if(type==InteractionHand.class)args[i]=InteractionHand.MAIN_HAND;else if(type==Component.class)args[i]=owner==null?Component.literal("Transmitter Channels"):owner.getBlockState().getBlock().getName();else if(type.getSimpleName().equals("NativeCredentialFilter"))args[i]=new com.vandorlabs.neoforge.client.NativeCredentialFilterPicker(com.vandorlabs.neoforge.credentials.NativeCredentialItems::accessCard,false);else return null;}return args;}
    private static void create(Minecraft mc)throws Exception {
        Class<?> type=Class.forName(CASES[index].type),menuType=null;for(var constructor:type.getConstructors())if(constructor.getParameterCount()>=3&&AbstractContainerMenu.class.isAssignableFrom(constructor.getParameterTypes()[0])){menuType=constructor.getParameterTypes()[0];break;}need(menuType!=null,"actual menu type");
        BlockEntity owner=owner(mc,ownerType(menuType),type.getSimpleName());if(owner==null){String wanted=type.getSimpleName().contains("Copifier")?"copifier":"transmitter";for(var item:BuiltInRegistries.ITEM)if(BuiltInRegistries.ITEM.getKey(item).getPath().contains(wanted)){mc.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));break;}}
        AbstractContainerMenu menu=null;List<String> errors=new ArrayList<>();var constructors=new ArrayList<>(Arrays.asList(menuType.getDeclaredConstructors()));constructors.sort(Comparator.comparingInt(constructor->Arrays.asList(constructor.getParameterTypes()).contains(BlockPos.class)?1:0));
        for(var constructor:constructors){var args=arguments(constructor.getParameterTypes(),mc,owner,null);if(args==null)continue;try{constructor.setAccessible(true);menu=(AbstractContainerMenu)constructor.newInstance(args);break;}catch(ReflectiveOperationException failure){errors.add(failure.toString()+" cause="+failure.getCause());}}need(menu!=null,"menu construction "+menuType+" "+errors);
        var candidates=new ArrayList<>(Arrays.asList(type.getConstructors()));candidates.sort(Comparator.comparingInt(Constructor::getParameterCount));screen=null;for(var constructor:candidates){var args=arguments(constructor.getParameterTypes(),mc,owner,menu);if(args==null)continue;screen=(AbstractContainerScreen<?>)constructor.newInstance(args);break;}need(screen!=null,"actual screen constructor");mc.setScreen(screen);settle=0;hoverX=hoverY=buttonX=buttonY=-1;
    }
    private static void press(AbstractContainerScreen<?> target,int id,int mouse)throws Exception {var button=(net.minecraft.client.gui.components.Button)target.getClass().getMethod("control",int.class).invoke(target,id);need(button!=null&&button.active,"actual active control "+id);need(target.mouseClicked(button.getX()+button.getWidth()/2D,button.getY()+button.getHeight()/2D,mouse),"actual mouse button="+mouse+" control="+id);}
    private static boolean extraStates()throws Exception {
        if(screen instanceof com.vandorlabs.neoforge.surface.client.NativeSurfaceScreen surface){
            if(variant==2){int before=(Integer)member(surface,"mode");press(surface,1,1);need((Integer)member(surface,"mode")==Math.floorMod(before-1,3),"Source previous Surface layout");need(surface.control(3)!=null,"Source Custom option preserved");press(surface,90,0);need((Boolean)member(surface,"active"),"Source Active ID90");variant=3;return true;}
            if(variant==3){press(surface,91,0);need(!(Boolean)member(surface,"active"),"Source Inactive ID91");variant=4;return true;}
        }
        if(screen instanceof com.vandorlabs.neoforge.ports.client.NativePortScreen port){
            if(variant==2){var before=((com.vandorlabs.ports.ResourcePortSettings.Face[])member(port,"faces"))[0];press(port,0,1);need(((com.vandorlabs.ports.ResourcePortSettings.Face[])member(port,"faces"))[0].ordinal()==Math.floorMod(before.ordinal()-1,3),"Source previous Port face option");press(port,22,0);need((Integer)member(port,"page")==2,"Source Housing page");variant=3;return true;}
            if(variant==3){press(port,21,0);int before=(Integer)member(port,"mode");press(port,8,1);need((Integer)member(port,"mode")==Math.floorMod(before-1,3),"Source previous Port activation");variant=4;return true;}
        }return false;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(done||!NativeDialogParityServer.enabled())return;var mc=Minecraft.getInstance();try{if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;mc.getToasts().clear();need(++ticks<2400,"bounded carousel");if(!mc.level.hasChunkAt(POSITION))return;if(screen==null){create(mc);return;}if(++settle>=4)capture=true;}catch(Throwable failure){done=true;failure.printStackTrace();System.out.println("[vandorlabs][dialog-parity] FAIL "+failure);mc.stop();}}
    @SubscribeEvent public static void draw(ScreenEvent.Render.Post event){if(done||!capture||event.getScreen()!=screen)return;var mc=Minecraft.getInstance();try{observed=new Observed(mc);com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);observed.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0xff080a0c);observed.flush();screen.render(observed,variant==1?hoverX:variant==2?buttonX:-1,variant==1?hoverY:variant==2?buttonY:-1,0);observed.flush();}catch(Throwable failure){done=true;throw new IllegalStateException("Actual dialog render "+CASES[index].type,failure);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event)throws Exception {if(done||!capture||observed==null)return;capture=false;var mc=Minecraft.getInstance();var expected=CASES[index].style;try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){
        var path=mc.gameDirectory.toPath().resolve("screenshots/dialog-"+screen.getClass().getSimpleName()+"-"+variant+".png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);
        Rect frame=observed.fills.stream().filter(rect->rect.color==expected.body&&rect.right-rect.left>=200&&rect.bottom-rect.top>=80).max(Comparator.comparingInt(Rect::area)).orElseThrow(()->new AssertionError("Source shell ARGB/geometry missing "+CASES[index].type));
        need(frame.left>=0&&frame.top>=0&&frame.right<=mc.getWindow().getGuiScaledWidth()&&frame.bottom<=mc.getWindow().getGuiScaledHeight(),"frame fits viewport");
        if(expected.headerHeight>0)need(observed.fills.contains(new Rect(frame.left,frame.top,frame.right,frame.top+expected.headerHeight,expected.header)),"Source header height and ARGB");
        if(expected.logoHeaderHeight>0){int l=DialogStyle.logoLeft(frame.left,frame.right-frame.left,expected.logoHeaderHeight),t=DialogStyle.logoTop(frame.top,expected.logoHeaderHeight),w=DialogStyle.logoWidth(expected.logoHeaderHeight),h=DialogStyle.logoHeight(expected.logoHeaderHeight);need(observed.logos.stream().anyMatch(rect->Arrays.equals(rect,new int[]{l,t,w,h})),"actual original-logo bounds");logoPixels(mc,image,l,t,w,h);}
        var idle=observed.fills.stream().filter(rect->rect.color==DialogStyle.TAB_IDLE&&rect.bottom-rect.top>=18&&rect.bottom-rect.top<=20).findFirst();if(variant==0&&idle.isPresent()){hoverX=(idle.get().left+idle.get().right)/2;hoverY=(idle.get().top+idle.get().bottom)/2;}
        if(variant==0){var button=observed.buttons.stream().filter(rect->rect[2]==0&&rect[3]==20).findFirst();if(button.isPresent()){buttonX=button.get()[0]+button.get()[4];buttonY=button.get()[1]+button.get()[5]/2;}}
        if(variant==2&&buttonX>=0)need(observed.buttons.stream().anyMatch(rect->rect[3]==40)&&observed.textColors.contains(0xffffa0),"Source hovered button artwork/half-crop/yellow text");
        if(variant==1&&hoverX>=0)need(observed.fills.stream().anyMatch(rect->rect.color==DialogStyle.TAB_HOVER),"authored hovered notebook skin");
        need(org.lwjgl.opengl.GL11.glGetError()==org.lwjgl.opengl.GL11.GL_NO_ERROR,"actual dialog GL state");System.out.println("[vandorlabs][dialog-parity] screen PASS "+screen.getClass().getSimpleName()+" state="+variant+" sourceARGB/header/logo/viewport/notebook");captures++;
    }catch(Throwable failure){done=true;throw failure;}
        observed=null;settle=0;if(variant>=2&&extraStates())return;if(variant++<2)return;variant=0;screen=null;if(++index==CASES.length){done=true;mc.setScreen(null);mc.level.disconnect();mc.disconnect();System.out.println("[vandorlabs][dialog-parity] client PASS screens="+(CASES.length-START)+" captures="+captures+" actual production adapters; presentation only");mc.stop();}
    }
    private NativeDialogParityChecks(){}
}
