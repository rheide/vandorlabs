package com.vandorlabs.client;

import com.vandorlabs.programmer.*;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.util.EnumFacing;
import org.lwjgl.BufferUtils;

/** Physical journal uses the same batched solid controls and font pass as Redstone screens. */
public final class ProgrammerJournalRenderer {
    private static final java.util.Map<EnumFacing,FloatBuffer> MATRICES=new java.util.EnumMap<>(EnumFacing.class);
    private static FloatBuffer matrix(EnumFacing facing){return MATRICES.computeIfAbsent(facing,f->{
        ProgrammerTransform.Affine a=ProgrammerGeometry.transform(f);net.minecraft.util.math.Vec3d across=ProgrammerJournalSurface.ACROSS.scale(1D/ProgrammerJournalSurface.WIDTH),down=ProgrammerJournalSurface.DOWN.scale(1D/ProgrammerJournalSurface.HEIGHT),n=ProgrammerJournalSurface.NORMAL,origin=ProgrammerJournalSurface.ORIGIN.add(n.scale(.001));
        FloatBuffer b=BufferUtils.createFloatBuffer(16);double depth=Math.sqrt(across.lengthSquared());b.put(new float[]{
            (float)(a.xx*across.x+a.xz*across.z),(float)across.y,(float)(a.zx*across.x+a.zz*across.z),0,
            (float)(a.xx*down.x+a.xz*down.z),(float)down.y,(float)(a.zx*down.x+a.zz*down.z),0,
            (float)((a.xx*n.x+a.xz*n.z)*depth),(float)(n.y*depth),(float)((a.zx*n.x+a.zz*n.z)*depth),0,
            (float)a.x(origin.x,origin.z),(float)origin.y,(float)a.z(origin.x,origin.z),1});b.flip();return b;
    });}
    public static void draw(ProgrammerTile tile,double x,double y,double z){
        net.minecraft.item.ItemStack chip=com.vandorlabs.vehicle.VehicleChip.isVehicle(tile.getStackInSlot(1))?tile.getStackInSlot(1):tile.getStackInSlot(2);boolean vehicle=com.vandorlabs.vehicle.VehicleChip.isVehicle(chip);ProgrammerJournalView view=tile.journalDisplay();if(view==null&&!vehicle)return;int page=tile.journalDisplayPage();
        GlStateManager.pushMatrix();try{
            GlStateManager.translate(x,y,z);FloatBuffer transform=matrix(tile.getProgrammerFacing());transform.rewind();GlStateManager.multMatrix(transform);
            contents(chip,view,page);
        }finally{GlStateManager.popMatrix();}
    }
    static void contents(net.minecraft.item.ItemStack chip,ProgrammerJournalView view,int page){contents(chip,view,page,136);}
    static void contents(net.minecraft.item.ItemStack chip,ProgrammerJournalView view,int page,int height){
        contents(chip,view,page,height,VehicleShapeRenderer.ticks()*1.2);
    }
    static void contents(net.minecraft.item.ItemStack chip,ProgrammerJournalView view,int page,int height,double angle){
        boolean vehicle=com.vandorlabs.vehicle.VehicleChip.isVehicle(chip);
        if(view!=null)page=Math.max(0,Math.min(page,view.pages.size()-1));
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);RedstoneScreenRenderer.bindWhite();
            BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,BlockSurfaceFormat.get());
            RedstoneScreenRenderer.rect(buffer,0,0,320,height,0,0xff000000);RedstoneScreenRenderer.rect(buffer,3,3,317,20,.01,0xff152330);
            if(!vehicle&&view!=null)RedstoneScreenRenderer.rect(buffer,ProgrammerJournalSurface.PREVIOUS_LEFT,height-20,ProgrammerJournalSurface.PREVIOUS_RIGHT,height-4,.01,page>0?0xff23465a:0xff121b23);
            if(!vehicle&&view!=null)RedstoneScreenRenderer.rect(buffer,ProgrammerJournalSurface.NEXT_LEFT,height-20,ProgrammerJournalSurface.NEXT_RIGHT,height-4,.01,page+1<view.pages.size()?0xff23465a:0xff121b23);
            Tessellator.getInstance().draw();FontRenderer font=Minecraft.getMinecraft().fontRenderer;
            if(vehicle){
                VehicleShapeRenderer.draw(VehicleShapeRenderer.shape(chip.getTagCompound()),160,20+(height-20)/2F,280,height-31,angle);
                fitted(font,com.vandorlabs.vehicle.VehicleChip.name(chip).isEmpty()?"Blank Vehicle Chip":com.vandorlabs.vehicle.VehicleChip.name(chip),7,8,306,0xc8eeff);
                GlStateManager.color(1,1,1,1);GlStateManager.disableBlend();return;
            }
            if(view==null&&ChipJournal.master(chip)&&companyLogo(chip,height))return;
            if(view==null){fitted(font,chip.isEmpty()?"Insert a Data Chip":ChipJournal.master(chip)?"Master Chip":"Blank Data Chip",7,8,306,0xc8eeff);if(!chip.isEmpty())fitted(font,chip.getDisplayName(),8,48,304,0xe2edf5);GlStateManager.color(1,1,1,1);GlStateManager.disableBlend();return;}
            fitted(font,view.title.isEmpty()?"Untitled Journal":view.title,7,8,306,0xc8eeff);
            java.util.List<String> lines=view.pages.get(page).lines;for(int i=0;i<lines.size();i++)fitted(font,lines.get(i),8,23+i*9,304,0xe2edf5);
            RedstoneScreenRenderer.text(font,"< Previous",12,height-16,page>0?0xc8eeff:0x697987);RedstoneScreenRenderer.text(font,"Next >",270,height-16,page+1<view.pages.size()?0xc8eeff:0x697987);
            String counter=view.source+"  "+(page+1)+" / "+view.pages.size();RedstoneScreenRenderer.text(font,counter,(320-font.getStringWidth(counter))/2,height-16,0xa6bfd0);
            GlStateManager.color(1,1,1,1);GlStateManager.disableBlend();
    }
    private static final int[] COMPANY_LOGOS=companyLogos();
    private static int[] companyLogos(){int[] choices=new int[7];java.util.Arrays.fill(choices,-1);for(int company=1;company<7;company++){String id="corp_"+com.vandorlabs.items.ItemDesignToken.DESIGNS[company]+"_logo";for(int i=0;i<com.vandorlabs.tiles.ScreenHousingTextures.IDS.length;i++)if(id.equals(com.vandorlabs.tiles.ScreenHousingTextures.IDS[i]))choices[company]=i;}return choices;}
    static int companyChoice(net.minecraft.item.ItemStack chip){return ChipJournal.master(chip)?COMPANY_LOGOS[chip.getMetadata()]:-1;}

    private static boolean companyLogo(net.minecraft.item.ItemStack chip,int height){
        int choice=companyChoice(chip);if(choice<0)return false;
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(com.vandorlabs.tiles.ScreenHousingTextures.fullTexture(choice));Minecraft.getMinecraft().getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.color(1,1,1,1);
        double aspect=UnifiedTextureSprites.aspect(sprite),w=Math.min(304,(height-16)*aspect),h=w/aspect,x=(320-w)/2,y=(height-h)/2;
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(7,BlockSurfaceFormat.get());b.pos(x,y+h,.02).color(255,255,255,255).tex(sprite.getMinU(),sprite.getMaxV()).lightmap(240,240).normal(0,0,1).endVertex();b.pos(x+w,y+h,.02).color(255,255,255,255).tex(sprite.getMaxU(),sprite.getMaxV()).lightmap(240,240).normal(0,0,1).endVertex();b.pos(x+w,y,.02).color(255,255,255,255).tex(sprite.getMaxU(),sprite.getMinV()).lightmap(240,240).normal(0,0,1).endVertex();b.pos(x,y,.02).color(255,255,255,255).tex(sprite.getMinU(),sprite.getMinV()).lightmap(240,240).normal(0,0,1).endVertex();Tessellator.getInstance().draw();GlStateManager.disableBlend();return true;
    }
    private static void fitted(FontRenderer font,String text,int x,int y,int width,int color){
        int measured=font.getStringWidth(text);if(measured<=width){RedstoneScreenRenderer.text(font,text,x,y,color);return;}
        GlStateManager.pushMatrix();try{GlStateManager.translate(x,y,0);float scale=width/(float)measured;GlStateManager.scale(scale,scale,1);RedstoneScreenRenderer.text(font,text,0,0,color);}finally{GlStateManager.popMatrix();}
    }
    private ProgrammerJournalRenderer(){}
}
