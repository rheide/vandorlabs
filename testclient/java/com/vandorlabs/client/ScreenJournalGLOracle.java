package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.programmer.*;
import com.vandorlabs.items.ModItems;
import java.io.*;
import java.lang.reflect.*;
import java.nio.FloatBuffer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
/** Actual released non-Vehicle journal canvas and font fitting; final submissions only are intercepted. */
final class ScreenJournalGLOracle {
    static final class Capture extends BufferBuilder {
        final List<JsonObject> draws=new ArrayList<>();final FloatBuffer matrix=BufferUtils.createFloatBuffer(16);
        Capture(){super(1<<17);}
        @Override public void begin(int mode,net.minecraft.client.renderer.vertex.VertexFormat format){matrix.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);super.begin(mode,format);}
        void finish(){finishDrawing();if(getVertexCount()==0)return;JsonObject draw=new JsonObject();draw.addProperty("stride",getVertexFormat().getNextOffset());draw.addProperty("vertices",getVertexCount());draw.add("matrix",matrix(matrix));JsonArray raw=new JsonArray();for(int i=0;i<getVertexCount()*getVertexFormat().getNextOffset();i+=4)raw.add(getByteBuffer().getInt(i));draw.add("data",raw);draws.add(draw);}
    }
    private static JsonArray matrix(FloatBuffer values){JsonArray result=new JsonArray();for(int i=0;i<16;i++)result.add(Float.floatToRawIntBits(values.get(i)));return result;}
    static final class CpuTessellator extends Tessellator {final Capture buffer;CpuTessellator(Capture buffer){super(16);this.buffer=buffer;}@Override public BufferBuilder getBuffer(){return buffer;}@Override public void draw(){buffer.finish();}}
    static final class SpyFont extends FontRenderer {
        final FontRenderer original;final List<JsonObject> text=new ArrayList<>();final FloatBuffer matrix=BufferUtils.createFloatBuffer(16);
        SpyFont(Minecraft mc,FontRenderer original){super(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.getTextureManager(),false);this.original=original;}
        @Override public int getStringWidth(String value){return original.getStringWidth(value);}
        @Override public String trimStringToWidth(String value,int width){return original.trimStringToWidth(value,width);}
        @Override public int drawString(String value,int x,int y,int color){JsonObject command=new JsonObject();command.addProperty("text",value);command.addProperty("x",x);command.addProperty("y",y);command.addProperty("color",color);command.addProperty("width",original.getStringWidth(value));matrix.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);command.add("matrix",matrix(matrix));text.add(command);return original.getStringWidth(value);}
    }
    static void capture(Minecraft mc,File target)throws Exception{
        Field singleton=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(Tessellator.class,"INSTANCE","field_78398_a"),modifiers=Field.class.getDeclaredField("modifiers");modifiers.setAccessible(true);int flags=singleton.getModifiers();modifiers.setInt(singleton,flags&~Modifier.FINAL);Object oldTess=singleton.get(null);Capture capture=new Capture();FontRenderer oldFont=mc.fontRenderer;SpyFont font=new SpyFont(mc,oldFont);singleton.set(null,new CpuTessellator(capture));mc.fontRenderer=font;GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();int profiles=0;
        try(PrintWriter out=new PrintWriter(target,"UTF-8")){
            for(int variant=0;variant<12;variant++)for(int height:new int[]{136,160,320,640})for(int requested:new int[]{0,1,999}){
                ItemStack chip=variant==0?ItemStack.EMPTY:new ItemStack(ModItems.DATA_CHIP,1,variant>=3&&variant<=8?variant-2:0);
                if(variant==2)chip.setStackDisplayName("CUSTOM BLANK DATA CHIP WITH A LONG NAME THAT NEEDS FITTING");
                if(variant>=9){String[] entries=new String[8];Arrays.fill(entries,"");entries[0]=variant==9?"First line\nSecond line":variant==10?String.join("",Collections.nCopies(512,"W")):String.join("",Collections.nCopies(512,"\n"));entries[1]="A second entry";ChipJournal.write(chip,true,variant==9?"":variant==10?String.join("",Collections.nCopies(40,"W")):"MULTIPAGE",entries);}
                ProgrammerJournalView view=ProgrammerJournalView.from(chip);int page=view==null?0:Math.max(0,Math.min(requested,view.pages.size()-1));capture.draws.clear();font.text.clear();GL11.glLoadIdentity();ProgrammerJournalRenderer.contents(chip,view,requested,height,0);if(capture.draws.isEmpty()||capture.draws.size()>2)throw new AssertionError("Journal draw coverage");JsonObject row=new JsonObject();row.addProperty("variant",variant);row.addProperty("height",height);row.addProperty("requested",requested);row.addProperty("occupied",!chip.isEmpty());row.addProperty("company",chip.isEmpty()?0:chip.getMetadata());row.addProperty("master",ChipJournal.master(chip));row.addProperty("displayName",chip.isEmpty()?"":chip.getDisplayName());row.addProperty("companyChoice",ProgrammerJournalRenderer.companyChoice(chip));row.addProperty("page",page);
                if(view!=null){row.addProperty("title",view.title);row.addProperty("source",view.source);row.addProperty("pages",view.pages.size());row.add("lines",new Gson().toJsonTree(view.pages.get(page).lines));}
                JsonArray draws=new JsonArray();for(JsonObject draw:capture.draws)draws.add(draw);row.add("draws",draws);row.add("text",new Gson().toJsonTree(font.text));out.println(row);profiles++;
            }
        }finally{GL11.glPopMatrix();GL11.glPopAttrib();mc.fontRenderer=oldFont;singleton.set(null,oldTess);modifiers.setInt(singleton,flags);}
        if(profiles!=144||target.length()>8*1024*1024)throw new AssertionError("Journal source coverage/size");System.out.println("[vandorlabs][screen-journal-source] profiles="+profiles+" bytes="+target.length());
    }
}
