package com.vandorlabs.client;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.vandorlabs.render.SpaceDoorControlPanel;
/** Actual TESlidingDoor pad emission with deterministic atlas regions and no GL context. */
public final class DoorPadLegacyChecks {
 static TextureAtlasSprite sprite(String name,int x){TextureAtlasSprite s=new TextureAtlasSprite(name){};s.setIconWidth(16);s.setIconHeight(16);s.initSprite(64,64,x,16,false);return s;}
 public static void main(String[] args)throws Exception{
  TextureAtlasSprite buttons=sprite("buttons",0),wall=sprite("wall",32);java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");int count=0;
  for(boolean sliding:new boolean[]{false,true})for(boolean far:new boolean[]{false,true})for(SpaceDoorControlPanel.Side side:new SpaceDoorControlPanel.Side[]{SpaceDoorControlPanel.Side.LEFT,SpaceDoorControlPanel.Side.RIGHT}){
   BufferBuilder b=new BufferBuilder(1024);b.begin(7,DefaultVertexFormats.BLOCK);TESlidingDoor.panelGeometry(b,sliding,far,side,buttons,wall);b.finishDrawing();if(b.getVertexCount()!=24)throw new AssertionError("Pad six faces");
   for(int i=0;i<b.getVertexCount();i++)for(int j=0;j<6;j++){int value=b.getByteBuffer().getInt(i*28+j*4);hash.update((byte)(value>>>24));hash.update((byte)(value>>>16));hash.update((byte)(value>>>8));hash.update((byte)value);}count++;
  }StringBuilder digest=new StringBuilder();for(byte b:hash.digest())digest.append(String.format("%02x",b&255));if(!digest.toString().equals("40e381ccb5a832863d471d070a03d2acca71099ed79f3fc7abb9d89e6d09d43d"))throw new AssertionError("Released pad emission changed "+digest);System.out.println("Door numeric pad actual Forge PASS variants="+count+" SHA256="+digest);
 }
}
