package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import com.vandorlabs.render.*;
import com.google.gson.*;
import net.minecraft.block.state.IBlockState;
import java.io.InputStreamReader;
public final class DoorPoseLegacyChecks {
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();JsonArray all=new JsonParser().parse(new InputStreamReader(DoorPoseLegacyChecks.class.getResourceAsStream("/assets/vandorlabs/data/blocks.json"),"UTF-8")).getAsJsonArray();int count=0;
  for(boolean sliding:new boolean[]{false,true})for(boolean framed:new boolean[]{false,true}){
   String id=SpaceDoorModels.modelId(2,sliding,framed)+"_paired";JsonObject definition=null;for(JsonElement e:all)if(e.getAsJsonObject().get("id").getAsString().equals(id))definition=e.getAsJsonObject();if(definition==null)throw new AssertionError("Missing actual motion template "+id);
   JsonObject d=definition;BlockDetailedDoor paired=new BlockDetailedDoor("pose_pair_"+sliding+framed,sliding?BlockVandorDoor.DoorMotion.SLIDING:BlockVandorDoor.DoorMotion.HINGED,sliding,true,false,d.get("left_pivot").getAsFloat(),d.get("right_pivot").getAsFloat(),d.get("pivot_z").getAsFloat(),d.get("left_angle").getAsFloat(),d.get("right_angle").getAsFloat(),d.get("left_slide").getAsFloat(),d.get("right_slide").getAsFloat());BlockSpaceDoor model=new BlockSpaceDoor("pose_single_"+sliding+framed,sliding,framed,paired);
   for(boolean pair:new boolean[]{false,true})for(boolean right:new boolean[]{false,true})for(int direction=0;direction<4;direction++)for(double p:new double[]{0,.125,.5,.875,1})for(double scale:new double[]{1,1.5}){
    IBlockState state=model.getDefaultState().withProperty(BlockConnectingDetailedDoor.PAIRED,pair);BlockDetailedDoor motion=model.getVisualModel(state);double[] expected=new double[5];if(sliding&&direction!=0)expected[1]=com.vandorlabs.persistence.SpaceDoorData.verticalTravel(framed,direction)*p*scale;else{DoorLeafTransform source=DoorLeafTransform.calculate(sliding,motion.getSlide(right),motion.getPivot(right),motion.getPivotZ(),motion.getAngle(right),p);expected[0]=source.translateX*scale;expected[2]=source.pivotX*scale;expected[3]=source.pivotZ;expected[4]=source.angleDegrees;}
    double[] actual=new double[5];DoorRenderPose.write(sliding,framed,pair,right,direction,p,scale,actual);for(int i=0;i<5;i++)if(Double.doubleToRawLongBits(expected[i])!=Double.doubleToRawLongBits(actual[i]))throw new AssertionError("Actual source pose "+id+" paired="+pair+" right="+right+" dir="+direction+" component="+i+" expected="+expected[i]+" got="+actual[i]);count++;
   }
  }System.out.println("Door render pose actual Forge PASS cases="+count+" exact template float pivots, paired/bare travel and scaled vertical motion");
 }
}
