package com.vandorlabs.neoforge.gear;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Public projection protocol checks using actual registered blocks/codecs, without publishing an inventory. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeGearPayloadViewChecks {
    private static int checks;
    private static final UUID ID=new UUID(42,210);
    private static final NativeGearPayload.Transform FRAME=new NativeGearPayload.Transform(0,0,1,0,1,0,-1,0,0,.25,-2,.75);
    private static final List<AABB> CUBE=List.of(new AABB(0,0,0,1,1,1));
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Public arm view: "+why);}
    private static void refuses(Runnable call,String why){boolean rejected=false;try{call.run();}catch(IllegalArgumentException expected){rejected=true;}need(rejected,why);}
    private static NativeGearPayload payload(Block block,CompoundTag tile){return new NativeGearPayload(ID,List.of(new NativeGearSnapshot.Cell(BlockPos.ZERO,block.defaultBlockState(),tile)),List.of(CUBE),.375,false,FRAME);}
    private static void bad(CompoundTag baseline,HolderLookup.Provider registries,Consumer<CompoundTag> edit,String why){var tag=baseline.copy();edit.accept(tag);refuses(()->NativeGearPayloadView.read(tag,registries),why);}
    private static int bytes(CompoundTag tag){try{var out=new ByteArrayOutputStream();NbtIo.write(tag,new DataOutputStream(out));return out.size();}catch(IOException impossible){throw new AssertionError(impossible);}}
    @SubscribeEvent public static void started(ServerStartedEvent event){if(!Boolean.getBoolean("vandorlabs.gearViewChecks"))return;try{run(event.getServer().registryAccess());System.out.println("[vandorlabs][gear-view] PASS checks="+checks+" private-inventory/opaque-NBT omitted; sign styling sanitized; reference/palettes/bounds strict");}catch(Throwable failure){failure.printStackTrace();}finally{event.getServer().halt(false);}}
    private static void run(HolderLookup.Provider registries){
        var plain=new CompoundTag();var privateTag=new CompoundTag();privateTag.putByteArray("Items",new byte[65536]);privateTag.putString("LootTable","private:unopened");privateTag.putString("CustomName","secret");privateTag.put("ForgeCaps",privateTag.copy());privateTag.put("neoforge:attachments",privateTag.copy());privateTag.put("components",privateTag.copy());
        for(Block block:List.of(Blocks.CHEST,Blocks.BARREL)){
            var baseline=NativeGearPayloadView.from(payload(block,plain),BlockPos.ZERO,registries);var view=NativeGearPayloadView.from(payload(block,privateTag),BlockPos.ZERO,registries);need(baseline.write().equals(view.write()),"private container fields never enter public projection "+block);need(view.reference().equals(FRAME),"capture reference retained");need(NativeGearPayloadView.read(view.write(),registries).write().equals(view.write()),"container exact public roundtrip");var leaked=view.cells().getFirst().projection();leaked.putString("Secret","mutated");need(view.cells().getFirst().projection().isEmpty(),"projection defensive copy");need(bytes(view.write())<1024,"container packet bounded independently of inventory");
        }
        for(String name:List.of("programmable_block","programmable_slab","programmable_stairs","programmable_storage")){
            var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("vandorlabs",name));need(block!=Blocks.AIR,"registered artwork block "+name);var clean=NativeGearPayloadView.from(payload(block,plain),BlockPos.ZERO,registries);var dirty=NativeGearPayloadView.from(payload(block,privateTag),BlockPos.ZERO,registries);need(clean.write().equals(dirty.write()),"housing inventory/opaque privacy "+name);need(NativeGearPayloadView.read(clean.write(),registries).write().equals(clean.write()),"housing projection exact canonical roundtrip "+name);
        }
        var text=Component.literal("Visible sign").setStyle(Style.EMPTY.withBold(true).withColor(0x123456).withInsertion("secret-insertion").withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/secret-command")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal("secret-hover"))));
        var signText=new SignText().setMessage(0,text,Component.literal("Filtered visible")).setColor(DyeColor.BLUE).setHasGlowingText(true);var sign=privateTag.copy();sign.put("front_text",SignText.DIRECT_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),signText).getOrThrow());sign.putBoolean("is_waxed",true);var signView=NativeGearPayloadView.from(payload(Blocks.OAK_SIGN,sign),BlockPos.ZERO,registries);var projected=signView.cells().getFirst().projection();var decoded=SignText.DIRECT_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),projected.get("front_text")).getOrThrow();need(decoded.getMessage(0,false).getString().equals("Visible sign")&&decoded.getMessage(0,true).getString().equals("Filtered visible"),"visible and filtered sign text retained");need(decoded.getColor()==DyeColor.BLUE&&decoded.hasGlowingText()&&projected.getBoolean("is_waxed"),"sign artwork attributes retained");need(!projected.toString().contains("secret")&&!projected.toString().contains("Items"),"sign interactive/private data omitted");need(NativeGearPayloadView.read(signView.write(),registries).write().equals(signView.write()),"sanitized sign roundtrip");
        var cube=NativeGearPayloadView.from(payload(Blocks.STONE,null),BlockPos.ZERO,registries).write();
        bad(cube,registries,t->t.putString("Unexpected","private"),"unknown root fields refused");bad(cube,registries,t->t.putInt("Schema",2),"unknown schema refused");bad(cube,registries,t->t.putUUID("Identity",new UUID(0,0)),"zero identity refused");bad(cube,registries,t->t.putDouble("CapturePhase",Double.NaN),"nonfinite phase refused");bad(cube,registries,t->t.putByte("Materialized",(byte)2),"invalid flag refused");
        bad(cube,registries,t->t.getList("Reference",Tag.TAG_DOUBLE).set(0,DoubleTag.valueOf(Double.NaN)),"nonfinite frame refused");bad(cube,registries,t->t.getList("Reference",Tag.TAG_DOUBLE).set(2,DoubleTag.valueOf(-1)),"reflected frame refused");bad(cube,registries,t->t.getList("Reference",Tag.TAG_DOUBLE).set(9,DoubleTag.valueOf(129)),"unbounded frame refused");
        bad(cube,registries,t->t.getList("States",Tag.TAG_COMPOUND).getCompound(0).putString("Name","missing:block"),"unknown block refused before AIR fallback");bad(cube,registries,t->{var properties=new CompoundTag();properties.putString("bogus","yes");t.getList("States",Tag.TAG_COMPOUND).getCompound(0).put("Properties",properties);},"unknown property refused");
        bad(cube,registries,t->t.putLongArray("Cells",new long[]{Long.MIN_VALUE}),"reserved packed bit refused");bad(cube,registries,t->t.putLongArray("Cells",new long[]{(1L<<24)|64|(64L<<8)|(64L<<16)}),"out of palette state refused");bad(cube,registries,t->t.putLongArray("Cells",new long[]{255|(64L<<8)|(64L<<16)}),"out of range cell refused");bad(cube,registries,t->{long v=t.getLongArray("Cells")[0];t.putLongArray("Cells",new long[]{v,v});},"duplicate cells refused");bad(cube,registries,t->t.putLongArray("Cells",new long[8193]),"cell count refused");
        bad(cube,registries,t->{var box=(ListTag)t.getList("Shapes",Tag.TAG_LIST).getList(0).get(0);box.set(3,DoubleTag.valueOf(Double.POSITIVE_INFINITY));},"nonfinite collision refused");bad(cube,registries,t->{var box=(ListTag)t.getList("Shapes",Tag.TAG_LIST).getList(0).get(0);box.set(3,DoubleTag.valueOf(0));},"zero volume refused");bad(cube,registries,t->{var shapes=t.getList("Shapes",Tag.TAG_LIST);shapes.add(shapes.get(0).copy());},"unused palette refused");bad(cube,registries,t->t.getList("Projections",Tag.TAG_COMPOUND).getCompound(0).putString("Items","secret"),"private injected appearance refused");bad(cube,registries,t->t.putByteArray("oversized",new byte[NativeGearPayloadView.MAX_BYTES]),"wire byte bound refused");
        var cells=new ArrayList<NativeGearSnapshot.Cell>();var shapes=new ArrayList<List<AABB>>();for(int i=0;i<8192;i++){cells.add(new NativeGearSnapshot.Cell(new BlockPos(i%32,i/1024,(i/32)%32),Blocks.STONE.defaultBlockState(),null));shapes.add(CUBE);}var large=new NativeGearPayload(ID,cells,shapes,.5,false,FRAME);var largeView=NativeGearPayloadView.from(large,BlockPos.ZERO,registries);need(bytes(largeView.write())<70000,"8192 cubes use shared palettes rather than repeated artwork");need(NativeGearPayloadView.read(largeView.write(),registries).cells().size()==8192,"maximum cell count canonical roundtrip");System.out.println("[vandorlabs][gear-view] wire chest="+bytes(NativeGearPayloadView.from(payload(Blocks.CHEST,plain),BlockPos.ZERO,registries).write())+" sign="+bytes(signView.write())+" cubes8192="+bytes(largeView.write())+" retained8192="+largeView.retainedBytes());
        bad(largeView.write(),registries,t->{t.put("States",signView.write().get("States").copy());t.put("Projections",signView.write().get("Projections").copy());},"shared projection expanded retained memory refused before cloning");
        bad(largeView.write(),registries,t->{var group=t.getList("Shapes",Tag.TAG_LIST).getList(0);while(group.size()<5)group.add(group.get(0).copy());},"expanded collision count refused before cell allocation");
    }
    private NativeGearPayloadViewChecks(){}
}
