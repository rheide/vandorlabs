package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.trapdoor.NativeTrapdoorSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Actual baked inventory templates and atlas sampling; public item binding has separate acceptance. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeTrapdoorItemChecks {
    private static final String[] IDS={"programmable_trapdoor","programmable_diagonal_trapdoor"};
    private static ModelResourceLocation id(String name){return ModelResourceLocation.standalone(ResourceLocation.parse("vandorlabs:item/"+name));}
    private static void need(boolean value,String reason){if(!value)throw new AssertionError("Trapdoor item: "+reason);}
    @SubscribeEvent public static void additional(ModelEvent.RegisterAdditional event){if("true".equals(System.getenv("VANDOR_TRAPDOOR_RENDER_CHECK")))for(String name:IDS)event.register(id(name));}
    public static void run(){
        var mc=Minecraft.getInstance();var atlas=mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
        int door=0;for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)if(ModernFinishCatalog.ENTRIES.get(i).door()!=null){door=i;break;}
        int oak=NativeMaterialRegistry.snapshot().samples().get(net.minecraft.world.level.block.Blocks.OAK_DOOR);
        int checks=0;
        for(String name:IDS){
            var source=mc.getModelManager().getModel(id(name));need(source!=mc.getModelManager().getMissingModel(),"loaded authored "+name);
            int[] choice={0},reads={0};
            var wrapper=new NativeTrapdoorItemModel(source,stack->{reads[0]++;return choice[0];},texture->atlas.apply(ResourceLocation.parse(texture)));
            for(int selected:new int[]{NativeTrapdoorSettings.defaultTexture(),0,door,oak}){
                choice[0]=selected;var model=wrapper.getOverrides().resolve(wrapper,ItemStack.EMPTY,null,null,0);
                need(model.getRenderPasses(ItemStack.EMPTY,false).get(0)==model,"selected render pass");
                var custom=com.vandorlabs.neoforge.client.NativeBlockMaterials.material(selected);var entry=ModernFinishCatalog.entry(selected);
                var sprite=custom==null?atlas.apply(ResourceLocation.parse(entry.texture())):custom.lower();float usedU=custom==null?entry.u():1,usedV=custom==null?entry.v():1;
                for(int side=-1;side<6;side++){
                    Direction face=side<0?null:Direction.values()[side];
                    var before=source.getQuads(null,face,RandomSource.create(42),ModelData.EMPTY,null);var after=model.getQuads(null,face,RandomSource.create(42),ModelData.EMPTY,null);need(before.size()==after.size(),"authored quad count");
                    for(int q=0;q<before.size();q++){
                        BakedQuad original=before.get(q),changed=after.get(q);int[] a=original.getVertices(),b=changed.getVertices();need(a.length==b.length&&changed.getSprite()==sprite,"sprite and format");int stride=a.length/4;
                        for(int v=0;v<4;v++)for(int field=0;field<stride;field++){
                            int at=v*stride+field;if(field!=4&&field!=5){need(a[at]==b[at],"retained geometry/color/normal");continue;}
                            var old=original.getSprite();float uv=Float.intBitsToFloat(a[at]);float expected=field==4?sprite.getU((uv-old.getU0())/(old.getU1()-old.getU0())*usedU):sprite.getV((uv-old.getV0())/(old.getV1()-old.getV0())*usedV);
                            need(Float.floatToRawIntBits(expected)==b[at],"authored sub-face UV range");checks++;
                        }
                    }
                }
            }
            var stack=new ItemStack(net.minecraft.world.item.Items.STONE);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(new net.minecraft.nbt.CompoundTag()));
            var first=wrapper.getOverrides().resolve(wrapper,stack,null,null,0);int count=reads[0];
            for(int i=0;i<10000;i++)need(wrapper.getOverrides().resolve(wrapper,stack,null,null,0)==first,"unchanged component/model identity");
            need(reads[0]==count,"immutable component decoded only once");
        }
        System.out.println("[vandorlabs][trapdoor-render] inventory PASS templates=2 materialKinds=4 exactUVChecks="+checks+" componentReads=1/10001");
    }
}
