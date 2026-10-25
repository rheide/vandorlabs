package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.ModernClientRuntimeChecks;
import com.vandorlabs.render.ControlItemProjection;
import com.vandorlabs.render.ControlMount.Point;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Actual ItemRenderer overrides, projected geometry and all configured shape item variants. */
public final class ShapeClientModelChecks {
    public static final int ICON_COLUMNS=8,ICON_CELL=22,ICON_COUNT=48;
    private static int stage,started;
    private static long frame;
    private static List<ItemStack> items;
    private ShapeClientModelChecks(){}
    private static void need(boolean b,String message){if(!b)throw new IllegalStateException("Shape items: "+message);}
    private static List<ItemStack> items(){List<ItemStack> result=new ArrayList<>();for(int layout=0;layout<3;layout++)result.add(item(false,8,0,layout));for(int tread:new int[]{1,2,4,8,16})for(int roll=0;roll<3;roll++)for(int layout=0;layout<3;layout++)result.add(item(true,tread,roll,layout));return result;}
    private static ItemStack item(boolean stair,int tread,int roll,int layout){
        ItemStack stack=new ItemStack(stair?ShapeRegistration.STAIR_ITEM.get():ShapeRegistration.SLAB_ITEM.get());CompoundTag tag=new CompoundTag();tag.putInt(ProgrammableBlockDialog.MAIN_TEXTURE_KEY,1);tag.putInt("HousingTextureVersion",1);tag.putInt("SideTexture",2);tag.putInt("MainTextureLayout",layout);tag.putBoolean("FaceTexturesEnabled",true);tag.putIntArray("FaceTextures",new int[]{3,4,5,6,7,8});tag.putInt("StairTread",tread);tag.putInt("StairRoll",roll);stack.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(tag));return stack;
    }
    private static int unrolled(int direction,int roll){return roll==0?direction:roll==1?new int[]{2,3,1,0,4,5}[direction]:new int[]{3,2,0,1,4,5}[direction];}
    public static void verify(Minecraft mc){
        items=items();int quads=0;for(int index=0;index<items.size();index++){
            ItemStack stack=items.get(index);CompoundTag before=stack.get(DataComponents.BLOCK_ENTITY_DATA).copyTag();boolean stair=index>=3;int roll=before.getInt("StairRoll");var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);List<BakedQuad> faces=new ArrayList<>();for(var pass:model.getRenderPasses(stack,false))for(Direction side:new Direction[]{null,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})faces.addAll(pass.getQuads(null,side,RandomSource.create(0)));need(!faces.isEmpty(),"actual configured model empty "+index);
            double x0=Double.POSITIVE_INFINITY,y0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1;var gui=model.getTransforms().gui;java.util.HashSet<ResourceLocation> seen=new java.util.HashSet<>();
            for(BakedQuad quad:faces){int direction=quad.getDirection().get3DDataValue();int choice;if(stair){int local=new int[]{0,1,5,4,2,3}[unrolled(direction,roll)];choice=3+local;}else choice=quad.getDirection().getAxis()==Direction.Axis.Y?1:2;
                ResourceLocation expected=ResourceLocation.parse(ModernFinishCatalog.entry(choice).texture());need(quad.getSprite().contents().name().equals(expected),"actual item face sprite "+index+"/"+quad.getDirection()+" expected"+expected+" actual"+quad.getSprite().contents().name());seen.add(expected);
                int[] data=quad.getVertices();int stride=data.length/4;for(int i=0;i<4;i++){int at=i*stride;Point p=new Point(Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2]));need(p.x>=0&&p.x<=1&&p.y>=0&&p.y<=1&&p.z>=0&&p.z<=1,"configured geometry escapes unit cell "+index);Point projected=ControlItemProjection.project(p,gui.rotation.x,gui.rotation.y,gui.rotation.z,gui.scale.x,gui.scale.y,gui.scale.z);double x=projected.x+gui.translation.x,y=projected.y+gui.translation.y;x0=Math.min(x0,x);x1=Math.max(x1,x);y0=Math.min(y0,y);y1=Math.max(y1,y);need(Float.isFinite(Float.intBitsToFloat(data[at+4]))&&Float.isFinite(Float.intBitsToFloat(data[at+5])),"nonfinite authored UV");}quads++;
            }
            need(x0>-.5&&x1<.5&&y0>-.5&&y1<.5,"actual GUI transform lacks projected padding "+index+" bounds"+x0+","+y0+","+x1+","+y1);need(seen.size()>1,"side/active face materials ignored "+index);need(before.equals(stack.get(DataComponents.BLOCK_ENTITY_DATA).copyTag()),"preview mutated dormant saved fields "+index);
        }
        System.out.println("[vandorlabs][modern] shape-items-model PASS actual48ItemRenderer variants, "+quads+" authored quads, independent6face orientation/roll sprites, slabpreview ignoresdormantfaces, saveddataretained andprojectedpadding");
    }
    public static boolean tick(Minecraft mc,int tick){
        if(stage==0){verify(mc);mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new Gallery());started=tick;frame=ModernClientRuntimeChecks.renderedFrames();stage=1;return false;}
        if(stage==1){if(tick-started<10||ModernClientRuntimeChecks.renderedFrames()-frame<3)return false;ModernClientRuntimeChecks.capture("modern-shape-items.png");stage=2;return false;}
        if(stage==2){if(!ModernClientRuntimeChecks.captured("modern-shape-items.png"))return false;mc.setScreen(null);System.out.println("[vandorlabs][modern] shape-items-client PASS48actual32px itemcells all15stairgeometryvariants x3layouts +3slablayouts");stage=3;}return stage==3;
    }
    private static final class Gallery extends Screen {
        private Gallery(){super(Component.literal("Configured slab and stair items"));}
        @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
        @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF101012);int left=(width-ICON_COLUMNS*ICON_CELL)/2,top=(height-6*ICON_CELL)/2;g.drawCenteredString(font,title,width/2,top-14,0xFFFFFF);for(int i=0;i<items.size();i++){int xx=left+i%ICON_COLUMNS*ICON_CELL,yy=top+i/ICON_COLUMNS*ICON_CELL;g.fill(xx,yy,xx+20,yy+20,0xFF282C32);g.renderItem(items.get(i),xx+2,yy+2);}super.render(g,x,y,partial);}
        @Override public boolean isPauseScreen(){return false;}
    }
}
