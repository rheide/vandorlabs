package com.vandorlabs.neoforge;

import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual native authored bake against actual Forge FaceBakery source records. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeDoorArtworkChecks {
 private static int ticks;private static boolean done;
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
  String reference=System.getProperty("vandorlabs.doorArtworkReference");if(reference==null||done)return;var mc=Minecraft.getInstance();if(++ticks<80||mc.getOverlay()!=null||!(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen))return;int models=0,quads=0;
  try(var lines=Files.lines(Path.of(reference))){for(String line:(Iterable<String>)lines::iterator){var row=JsonParser.parseString(line).getAsJsonObject();String id=row.get("id").getAsString();var model=mc.getModelManager().getModel(ModelResourceLocation.standalone(ResourceLocation.parse(id)));var actual=NativeDoorArtwork.quads(id,model);var expected=row.getAsJsonArray("quads");if(actual==null||actual.size()!=expected.size())throw new AssertionError("Door actual source face count "+id);for(int q=0;q<actual.size();q++){
   var quad=actual.get(q);var old=expected.get(q).getAsJsonObject();if(quad.getDirection().get3DDataValue()!=old.get("face").getAsInt()||quad.isShade()!=old.get("shade").getAsBoolean())throw new AssertionError("Door actual source direction/shade "+id+" quad="+q+" expected="+old.get("face")+" got="+quad.getDirection());var vertices=old.getAsJsonArray("vertices");int[] raw=quad.getVertices();for(int v=0;v<4;v++){for(int a=0;a<3;a++)if(raw[v*8+a]!=vertices.get(v*4+a).getAsInt())throw new AssertionError("Door actual source float position "+id+" quad="+q+" vertex="+v+" axis="+a+" expected="+Float.intBitsToFloat(vertices.get(v*4+a).getAsInt())+" got="+Float.intBitsToFloat(raw[v*8+a]));if((raw[v*8+7]&0xffffff)!=vertices.get(v*4+3).getAsInt())throw new AssertionError("Door actual source normal "+id+" quad="+q+" expected="+Integer.toHexString(vertices.get(v*4+3).getAsInt())+" got="+Integer.toHexString(raw[v*8+7]&0xffffff));}quads++;
  }models++;}}
  if(models!=1924||quads!=64968)throw new AssertionError("Door artwork source coverage "+models+"/"+quads);done=true;System.out.println("[vandorlabs][door-artwork] title PASS actualForge models="+models+" quads="+quads+" exact float positions/directions/normal bytes/shade; no world/server");mc.stop();
 }
}
