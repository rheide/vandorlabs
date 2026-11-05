package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.persistence.HousingSettings;
import com.vandorlabs.render.HousingFacePlane;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Independent actual-Forge-model oracle comparison. The portrait asset exists only in this smoke source set. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class CubeReferenceChecks {
    private static JsonObject reference;
    private static int runs;
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event){
        String path=System.getProperty("vandorlabs.cubeReference","");if(path.isEmpty())return;
        try{reference=JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();}
        catch(java.io.IOException failure){throw new IllegalStateException("Cannot read Forge cube reference",failure);}
        int choice=reference.getAsJsonObject("choices").get("portrait").getAsInt();
        String id=reference.getAsJsonArray("world").asList().stream().map(JsonElement::getAsJsonObject).filter(row->row.get("profile").getAsString().equals("portrait")).findFirst().orElseThrow().getAsJsonArray("quads").get(0).getAsJsonObject().get("material").getAsString();
        ModernFinishCatalog.registerAsset(choice,new ModernFinishCatalog.Entry(id,"Portrait fixture","Test only",false,false,choice,"vandorlabs:block/cube_portrait_fixture",.5f,1));
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Native cube reference: "+message);}
    public static void run(Minecraft mc){
        if(reference==null)return;int worldCases=0,itemCases=0;
        var block=VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get();
        var worldRows=new ArrayList<>(reference.getAsJsonArray("world").asList());boolean reverse=(runs++&1)!=0;if(reverse)Collections.reverse(worldRows);
        for(var value:worldRows){
            var row=value.getAsJsonObject();int main=row.get("main").getAsInt(),layout=row.get("layout").getAsInt();boolean enabled=row.has("facesEnabled")&&row.get("facesEnabled").getAsBoolean();
            int[] faces=row.has("faceChoices")?ints(row.getAsJsonArray("faceChoices")):new int[]{-1,-1,-1,-1,-1,-1};
            var state=block.defaultBlockState().setValue(ProgrammableBlock.FACING,Direction.from3DDataValue(row.get("facing").getAsInt()));
            var data=ModelData.builder().with(ProgrammableBlockEntity.SETTINGS,new ProgrammableBlockDialog.State(main,layout,enabled,faces,-1));
            var planes=new HousingFacePlane[6];if(row.has("planes"))for(var entry:row.getAsJsonObject("planes").entrySet()){var plane=entry.getValue().getAsJsonObject();planes[Integer.parseInt(entry.getKey())]=new HousingFacePlane(ints(plane.getAsJsonArray("min")),ints(plane.getAsJsonArray("size")));}
            data.with(ProgrammableBlockEntity.JOINED,new JoinedCubeFaces(planes));
            var model=mc.getBlockRenderer().getBlockModel(state);List<BakedQuad> actual=collect(model,state,data.build(),row.get("visible").getAsInt());
            compare(row.get("key").getAsString(),row.getAsJsonArray("quads"),actual);worldCases++;
        }
        for(var value:reference.getAsJsonArray("items")){
            var row=value.getAsJsonObject();ItemStack stack=new ItemStack(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get());
            if(!row.get("key").getAsString().equals("default")){
                CompoundTag tag=new CompoundTag();tag.putInt(HousingSettings.MAIN,row.get("main").getAsInt());tag.putInt(HousingSettings.LAYOUT,row.get("layout").getAsInt());
                tag.putBoolean(HousingSettings.FACES_ENABLED,row.get("faces").getAsBoolean());tag.putIntArray(HousingSettings.FACES,new int[]{1,2,3,4,5,6});if(row.get("versioned").getAsBoolean())tag.putInt(HousingSettings.VERSION,1);
                stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(tag));
            }
            var before=stack.copy();var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
            var transform=model.getTransforms().gui;float[] actualTransform={transform.rotation.x(),transform.rotation.y(),transform.rotation.z(),transform.translation.x()*16,transform.translation.y()*16,transform.translation.z()*16,transform.scale.x(),transform.scale.y(),transform.scale.z()};
            for(int i=0;i<9;i++)require(Math.abs(actualTransform[i]-row.getAsJsonArray("gui").get(i).getAsDouble())<1e-5,"item GUI transform "+row.get("key"));
            require(ItemStack.isSameItemSameComponents(before,stack),"item rendering mutated saved data");compare("item/"+row.get("key").getAsString(),row.getAsJsonArray("quads"),collect(model,null,ModelData.EMPTY,63));itemCases++;
        }
        System.out.println("[vandorlabs][cube] Forge-reference PASS world="+worldCases+" items="+itemCases+" order="+(reverse?"reverse":"forward")+" actual emitted positions/UV/materials/shading and GUI transforms, independent current Forge exporter; portrait is test-only registered artwork");
    }
    private static int[] ints(JsonArray values){int[] result=new int[values.size()];for(int i=0;i<result.length;i++)result[i]=values.get(i).getAsInt();return result;}
    private static List<BakedQuad> collect(BakedModel model,net.minecraft.world.level.block.state.BlockState state,ModelData data,int visible){
        List<BakedQuad> result=new ArrayList<>();for(int index=-1;index<6;index++)for(var quad:model.getQuads(state,index<0?null:Direction.from3DDataValue(index),net.minecraft.util.RandomSource.create(0),data,null))if((visible&(1<<quad.getDirection().get3DDataValue()))!=0)result.add(quad);return result;
    }
    private static void compare(String key,JsonArray expected,List<BakedQuad> actual){
        require(expected.size()==actual.size(),key+" quad count "+expected.size()+" != "+actual.size());
        for(var value:expected){var row=value.getAsJsonObject();Direction face=Direction.from3DDataValue(row.get("face").getAsInt());var quad=actual.stream().filter(candidate->candidate.getDirection()==face).findFirst().orElseThrow();
            require(quad.isShade()==row.get("shade").getAsBoolean()&&quad.getTintIndex()==row.get("tint").getAsInt(),key+" shade/tint "+face);
            var entry=ModernFinishCatalog.allChoices().stream().map(ModernFinishCatalog.Choice::entry).filter(candidate->candidate.texture().equals(quad.getSprite().contents().name().toString())&&candidate.id().equals(row.get("material").getAsString())).findFirst().orElseThrow(()->new IllegalStateException(key+" material "+row.get("material")+" != "+quad.getSprite().contents().name()));
            int[] data=quad.getVertices();int stride=data.length/4;
            for(var point:row.getAsJsonArray("vertices")){var expectedPoint=point.getAsJsonArray();int found=-1;
                for(int vertex=0;vertex<4;vertex++){boolean matches=true;for(int axis=0;axis<3;axis++)if(Math.abs(Float.intBitsToFloat(data[vertex*stride+axis])-expectedPoint.get(axis).getAsDouble())>1e-5)matches=false;if(matches)found=vertex;}
                require(found>=0,key+" point missing "+face+" "+point);
                for(int uv=0;uv<2;uv++){double low=uv==0?quad.getSprite().getU0():quad.getSprite().getV0(),high=uv==0?quad.getSprite().getU1():quad.getSprite().getV1(),used=uv==0?entry.u():entry.v();double coordinate=(Float.intBitsToFloat(data[found*stride+4+uv])-low)/(high-low)/used;
                    require(Math.abs(coordinate-expectedPoint.get(uv+3).getAsDouble())<2e-4,key+" UV mismatch face="+face+" vertex="+point+" axis="+uv+" actual="+coordinate);}
            }
        }
    }
}
