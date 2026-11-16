package com.vandorlabs.neoforge.rack.client;
import com.vandorlabs.rack.BatteryChargeBar;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
/** One atlas-generation overlay cache shared by placed racks and embedded tower racks. */
public final class NativeRackBatteryBar {
 private static BakedModel generation;
 private static final Map<Integer,List<BakedQuad>> cache=new HashMap<>();
 public static synchronized List<BakedQuad> quads(Map<ModelResourceLocation,BakedModel> models,Direction facing,int bay,int fill){
  BakedModel token=models.get(ModelResourceLocation.standalone(NativeRackClient.model("battery_bar_0")));if(token!=generation){generation=token;cache.clear();}fill=Math.clamp(fill,0,BatteryChargeBar.COLUMNS);if(fill==0)return List.of();int key=(facing.get3DDataValue()*4+bay)*37+fill;var found=cache.get(key);if(found!=null)return found;
  var source=Objects.requireNonNull(models.get(ModelResourceLocation.standalone(NativeRackClient.model("battery_bar_"+bay))),"Battery bar overlay");var full=new ArrayList<BakedQuad>();var random=RandomSource.create(0);full.addAll(source.getQuads(null,null,random));for(var side:Direction.values())full.addAll(source.getQuads(null,side,random));var clipped=new ArrayList<BakedQuad>();for(var quad:full){var raw=BatteryChargeBar.clip(quad.getVertices(),fill);clipped.add(NativeRackModel.rotate(new BakedQuad(raw,quad.getTintIndex(),quad.getDirection(),quad.getSprite(),false,false),facing,true));}found=List.copyOf(clipped);cache.put(key,found);return found;
 }
 private NativeRackBatteryBar(){}
}
