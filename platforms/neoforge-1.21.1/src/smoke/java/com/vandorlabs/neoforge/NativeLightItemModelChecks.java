package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.light.NativeLightBlock;
import com.vandorlabs.neoforge.client.NativeBlockMaterials;
import com.vandorlabs.tiles.ProgrammableLightTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Actual atlas/item templates; no public Light registry or player-path acceptance claim. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeLightItemModelChecks {
    private static final String[] FAMILIES={"programmable_light","programmable_light_frame","programmable_light_slab"};
    private static ModelResourceLocation id(String value){return ModelResourceLocation.standalone(ResourceLocation.parse(value));}
    private static String model(int family,int style,boolean lit){return "vandorlabs:item/configured/"+FAMILIES[family]+"_"+ProgrammableLightTextures.IDS[style]+(lit?"_on":"_off");}
    private static void need(boolean value,String why){if(!value)throw new AssertionError("Light item atlas: "+why);}
    @SubscribeEvent public static void additional(ModelEvent.RegisterAdditional event){if("true".equals(System.getenv("VANDOR_TRAPDOOR_RENDER_CHECK")))for(int family=0;family<3;family++)for(int style=0;style<6;style++)for(boolean lit:new boolean[]{false,true})event.register(id(model(family,style,lit)));}
    public static void run(){
        var mc=Minecraft.getInstance();var atlas=mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);int checks=0,variants=0;
        int oak=NativeMaterialRegistry.snapshot().samples().get(net.minecraft.world.level.block.Blocks.OAK_DOOR);
        for(int family=0;family<3;family++){
            var fallback=mc.getModelManager().getModel(id(model(family,0,true)));
            if(family==0)dynamicSource(fallback,name->atlas.apply(ResourceLocation.parse(name)));
            var wrapper=new NativeLightItemModel(fallback,NativeLightBlock.Kind.values()[family],name->mc.getModelManager().getModel(id(name)),name->atlas.apply(ResourceLocation.parse(name)));
            for(int style=0;style<6;style++)for(boolean lit:new boolean[]{false,true}){
                var source=mc.getModelManager().getModel(id(model(family,style,lit)));need(source!=mc.getModelManager().getMissingModel(),"authored template "+model(family,style,lit));variants++;
                String entryId="light_"+ProgrammableLightTextures.IDS[style]+(lit?"":"_off");var lamp=atlas.apply(ResourceLocation.parse(ModernFinishCatalog.ENTRIES.stream().filter(entry->entry.id().equals(entryId)).findFirst().orElseThrow().texture()));
                for(int choice:new int[]{-1,0,oak}){
                    var tag=new CompoundTag();tag.putInt("LightTexture",style);tag.putBoolean("LightOn",lit);tag.putInt("LightLevel",15);if(choice>=0)tag.putInt("LightFaceTexture",choice);var root=new CompoundTag();root.put("BlockEntityTag",tag);var stack=new ItemStack(Items.STONE);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));
                    var selected=wrapper.getOverrides().resolve(wrapper,stack,null,null,0);need(selected.getRenderPasses(stack,false).get(0)==selected,"selected render pass");
                    var custom=choice<0?null:NativeBlockMaterials.material(choice);var entry=choice<0?null:ModernFinishCatalog.entry(choice);var target=choice<0?null:custom==null?atlas.apply(ResourceLocation.parse(entry.texture())):custom.lower();float usedU=custom==null&&entry!=null?entry.u():1,usedV=custom==null&&entry!=null?entry.v():1;
                    for(int side=-1;side<6;side++){
                        Direction direction=side<0?null:Direction.values()[side];var before=source.getQuads(null,direction,RandomSource.create(42),ModelData.EMPTY,null);var after=selected.getQuads(null,direction,RandomSource.create(42),ModelData.EMPTY,null);need(before.size()==after.size(),"template quad count");
                        for(int q=0;q<before.size();q++){var old=before.get(q);var next=after.get(q);boolean changed=target!=null&&old.getSprite()==lamp;need(next.getSprite()==(changed?target:old.getSprite()),"only lamp quads retargeted");int[] a=old.getVertices(),b=next.getVertices();need(a.length==b.length,"retained format");int stride=a.length/4;
                            for(int vertex=0;vertex<4;vertex++)for(int field=0;field<stride;field++){int at=vertex*stride+field;if(!changed||field!=4&&field!=5)need(a[at]==b[at],"retained geometry/color/normal/UV");else{float coordinate=Float.intBitsToFloat(a[at]);var sprite=old.getSprite();float expected=field==4?target.getU((coordinate-sprite.getU0())/(sprite.getU1()-sprite.getU0())*usedU):target.getV((coordinate-sprite.getV0())/(sprite.getV1()-sprite.getV0())*usedV);need(Float.floatToRawIntBits(expected)==b[at],"authored sub-face crop");}checks++;}
                        }
                    }
                    padded(selected);
                    if(style==0&&lit&&choice==-1)for(int repeat=0;repeat<10000;repeat++)need(wrapper.getOverrides().resolve(wrapper,stack,null,null,0)==selected,"stable immutable component/model identity");
                }
            }
        }
        System.out.println("[vandorlabs][trapdoor-render] Light inventory atlas PASS templates="+variants+" vertexFields="+checks+" materialKinds=3 padded GUI transforms/component identity/reload");
    }
    /** Resolve actual canonical ItemRenderer models through the production bake hook. */
    public static void canonical(){
        var mc=Minecraft.getInstance();var atlas=mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);int variants=0;
        for(int family=0;family<3;family++)for(int style=0;style<6;style++)for(boolean lit:new boolean[]{false,true}){
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("vandorlabs",FAMILIES[family]));need(item instanceof com.vandorlabs.neoforge.light.NativeLightItem,"canonical item "+family);
            var stack=new ItemStack(item);var tag=new CompoundTag();tag.putInt("LightTexture",style);tag.putBoolean("LightOn",lit);tag.putInt("LightLevel",15);var root=new CompoundTag();root.put("BlockEntityTag",tag);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));
            var selected=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
            String entryId="light_"+ProgrammableLightTextures.IDS[style]+(lit?"":"_off");var expected=atlas.apply(ResourceLocation.parse(ModernFinishCatalog.ENTRIES.stream().filter(entry->entry.id().equals(entryId)).findFirst().orElseThrow().texture()));int lamps=0;
            for(int side=-1;side<6;side++)for(var quad:selected.getQuads(null,side<0?null:Direction.values()[side],RandomSource.create(42),ModelData.EMPTY,null))if(quad.getSprite()==expected)lamps++;
            need(lamps>0,"production selector lamp "+family+"/"+style+"/"+lit);padded(selected);variants++;
        }
        String[] named={"programmable_light","programmable_light_frame","programmable_light_slab","programmable_trapdoor","programmable_diagonal_trapdoor"};
        String[] labels={"Programmable Light","Programmable Light Frame","Programmable Light Slab","Programmable Trapdoor","Programmable Diagonal Trapdoor"};
        for(int index=0;index<named.length;index++){var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("vandorlabs",named[index]));need(new ItemStack(item).getHoverName().getString().equals(labels[index]),"canonical translated item name "+named[index]);}
        System.out.println("[vandorlabs][light-editor-client] canonical inventory PASS actual ItemRenderer variants="+variants);
    }
    private static void dynamicSource(BakedModel source,java.util.function.Function<String,net.minecraft.client.renderer.texture.TextureAtlasSprite> atlas){
        boolean[] visible={false};
        var dynamic=new net.neoforged.neoforge.client.model.BakedModelWrapper<BakedModel>(source){
            @Override public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState state,Direction side,RandomSource random,ModelData data,net.minecraft.client.renderer.RenderType layer){return visible[0]?source.getQuads(state,side,random,data,layer):java.util.List.of();}
        };
        var wrapper=new NativeLightItemModel(source,NativeLightBlock.Kind.FULL,name->dynamic,atlas);
        var stack=new ItemStack(Items.STONE);var selected=wrapper.getOverrides().resolve(wrapper,stack,null,null,0);
        need(selected.getQuads(null,null,RandomSource.create(0),ModelData.EMPTY,null).isEmpty(),"dynamic source initial state");
        visible[0]=true;int total=0;for(int face=-1;face<6;face++)total+=selected.getQuads(null,face<0?null:Direction.values()[face],RandomSource.create(0),ModelData.EMPTY,null).size();
        need(total>0,"dynamic resource model is not frozen at selection time");
    }
    private static void padded(BakedModel model){
        var gui=model.getTransforms().gui;double limit=15/32D;
        for(int face=-1;face<6;face++)for(var quad:model.getQuads(null,face<0?null:Direction.values()[face],RandomSource.create(0),ModelData.EMPTY,null)){
            int[] data=quad.getVertices();int stride=data.length/4;for(int vertex=0;vertex<4;vertex++){
                int at=vertex*stride;double x=(Float.intBitsToFloat(data[at])-.5)*gui.scale.x(),y=(Float.intBitsToFloat(data[at+1])-.5)*gui.scale.y(),z=(Float.intBitsToFloat(data[at+2])-.5)*gui.scale.z();double rz=Math.toRadians(gui.rotation.z()),ry=Math.toRadians(gui.rotation.y()),rx=Math.toRadians(gui.rotation.x());double a=x*Math.cos(rz)-y*Math.sin(rz);y=x*Math.sin(rz)+y*Math.cos(rz);x=a;a=x*Math.cos(ry)+z*Math.sin(ry);z=z*Math.cos(ry)-x*Math.sin(ry);x=a;y=y*Math.cos(rx)-z*Math.sin(rx);x+=gui.translation.x();y+=gui.translation.y();need(Math.abs(x)<limit&&Math.abs(y)<limit,"GUI padding after authored rotation");
            }
        }
    }
}
