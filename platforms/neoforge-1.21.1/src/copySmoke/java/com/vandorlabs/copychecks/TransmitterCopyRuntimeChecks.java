package com.vandorlabs.copychecks;

import com.vandorlabs.neoforge.VandorLabsBootstrap;

import com.vandorlabs.neoforge.redstone.NativeTransmitterItem;
import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Opt-in actual CraftingInput construction allocation measurement; not a rendering throughput claim. */
public final class TransmitterCopyRuntimeChecks {
    private static volatile CraftingInput sink;
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("Native transmitter copy: "+message);}
    @SubscribeEvent public static void started(ServerStartedEvent event){
        String phase=System.getProperty("vandorlabs.transmitterPhase","");if(!phase.equals("copy-baseline")&&!phase.equals("copy-allocation"))return;
        if(phase.equals("copy-allocation"))recipes(event.getServer().overworld());
        List<ItemStack> items=new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY));items.set(4,NativeTransmitterItem.variant(VandorLabsBootstrap.REDSTONE_TRANSMITTER.get(),0));items.set(5,NativeTransmitterItem.variant(VandorLabsBootstrap.REDSTONE_TRANSMITTER.get(),2));
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();
        for(int i=0;i<200000;i++)sink=CraftingInput.ofPositioned(3,3,items).input();
        long[] allocations=new long[5];int count=100000;
        for(int round=0;round<allocations.length;round++){long before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<count;i++)sink=CraftingInput.ofPositioned(3,3,items).input();allocations[round]=bean.getThreadAllocatedBytes(thread)-before;}
        long contextFields=Arrays.stream(CraftingInput.class.getDeclaredFields()).filter(field->field.getName().contains("copySource")).count();require(contextFields==(phase.equals("copy-baseline")?0:1),"baseline/transformed CraftingInput context field proof");
        Arrays.sort(allocations);System.out.println("[vandorlabs][transmitter-copy] "+phase+" PASS queries="+count+" rounds=5 median-bytes/input="+allocations[2]/(double)count+" cropped="+sink.width()+"x"+sink.height());event.getServer().halt(false);
    }
    private static ItemStack configured(int tier,int channel,String key,int value){ItemStack stack=NativeTransmitterItem.variant(VandorLabsBootstrap.REDSTONE_TRANSMITTER.get(),tier);NativeTransmitterItem.configure(stack,com.vandorlabs.redstone.ChannelList.of(channel));var tag=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag();tag.putInt(key,value);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));return stack;}
    public static void cache(net.minecraft.world.level.Level world){
        ItemStack source=configured(1,7,"Source",23),target=configured(3,9,"Target",31);List<ItemStack> top=new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY)),middle=new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY));top.set(0,source);top.set(1,target);middle.set(4,source);middle.set(5,target);
        CraftingInput invalid=CraftingInput.of(3,3,top),valid=CraftingInput.of(3,3,middle);require(invalid.equals(valid),"cache regression must use vanilla-equal cropped inputs");
        var negative=new net.minecraft.world.item.crafting.RecipeCache(10);require(negative.get(world,invalid).isEmpty(),"invalid original grid rejected before cachehit");boolean invalidThenValid=negative.get(world,valid).isPresent();
        var positive=new net.minecraft.world.item.crafting.RecipeCache(10);var result=positive.get(world,valid);require(result.isPresent(),"valid original grid resolves before cachehit");boolean validThenInvalid=positive.get(world,invalid).isEmpty();
        System.out.println("[vandorlabs][transmitter-copy] cache-results invalid-to-valid="+invalidThenValid+" valid-to-invalid="+validThenInvalid);
        require(invalidThenValid&&validThenInvalid,"actual vanilla RecipeCache erased original context across cachehit");
        System.out.println("[vandorlabs][transmitter-copy] cache PASS actual RecipeCache both context orders");
    }
    private static void recipes(net.minecraft.server.level.ServerLevel world){
        cache(world);
        var recipe=(net.minecraft.world.item.crafting.CraftingRecipe)world.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("vandorlabs","redstone_transmitter_copy")).orElseThrow().value();
        for(int a=0;a<9;a++)for(int b=0;b<9;b++)if(a!=b){
            ItemStack first=configured(1,7,"FirstUnknown",23),second=configured(3,9,"SecondUnknown",31);List<ItemStack> grid=new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY));grid.set(a,first);grid.set(b,second);CraftingInput input=CraftingInput.of(3,3,grid);boolean valid=a==4&&(b==3||b==5)||b==4&&(a==3||a==5);
            require(recipe.matches(input,world)==valid,"original grid center rule source="+a+" target="+b);
            if(valid){int source=1-CraftingInput.ofPositioned(3,3,grid).left();ItemStack center=grid.get(4),target=grid.get(a==4?b:a);ItemStack output=recipe.assemble(input,world.registryAccess()),expected=target.copyWithCount(1);NativeTransmitterItem.configure(expected,NativeTransmitterItem.channels(center));require(ItemStack.isSameItemSameComponents(output,expected)&&output.getCount()==1,"target tier/unknown fields retained, only channels copied");var remaining=recipe.getRemainingItems(input);for(int slot=0;slot<input.size();slot++)require(slot==source?ItemStack.isSameItemSameComponents(remaining.get(slot),center)&&remaining.get(slot).getCount()==1:remaining.get(slot).isEmpty(),"exact source remainder once in cropped slot");}
            else require(recipe.assemble(input,world.registryAccess()).isEmpty(),"invalid grid has no output");
        }
        for(int[] size:new int[][]{{2,2},{2,1},{3,4},{4,3},{1,9},{9,1}}){List<ItemStack> grid=new ArrayList<>(Collections.nCopies(size[0]*size[1],ItemStack.EMPTY));grid.set(0,configured(0,1,"A",1));grid.set(1,configured(2,2,"B",2));require(!recipe.matches(CraftingInput.of(size[0],size[1],grid),world),"wrong original grid size rejected");}
        List<ItemStack> extra=new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY));extra.set(4,configured(0,1,"A",1));extra.set(5,configured(2,2,"B",2));extra.set(0,new ItemStack(net.minecraft.world.item.Items.DIRT));require(!recipe.matches(CraftingInput.of(3,3,extra),world),"unrelated extra item rejected");
        List<ItemStack> ambiguous=new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY));ambiguous.set(3,configured(0,1,"Left",1));ambiguous.set(4,configured(1,2,"Center",2));ambiguous.set(5,configured(2,3,"Right",3));require(!recipe.matches(CraftingInput.of(3,3,ambiguous),world),"both neighbor targets rejected");
        extra.set(0,ItemStack.EMPTY);NativeTransmitterItem.configure(extra.get(4),com.vandorlabs.redstone.ChannelList.EMPTY);var disconnected=CraftingInput.of(3,3,extra);require(recipe.matches(disconnected,world)&&NativeTransmitterItem.channels(recipe.assemble(disconnected,world.registryAccess())).isEmpty(),"unconfigured center copies disconnected channels");
        var bad=extra.get(5).get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag();bad.putInt(NativeTransmitterItem.TIER,4);extra.get(5).set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(bad));require(!recipe.matches(CraftingInput.of(3,3,extra),world),"invalid tier cannot use captured context proof");
        System.out.println("[vandorlabs][transmitter-copy] matrix PASS checks="+checks+" original-center both-neighbors wrong-grid vertical unrelated exact-remainders target-unknown components");
    }

}
