package com.vandorlabs.neoforge.surface;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** The smallest native drawing-root/hole/unequal-pad lifecycle before controller consumers are added. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeSurfaceRuntimeChecks {
    private static net.neoforged.neoforge.attachment.AttachmentType<String> contextAttachment;
    private static ServerLevel expectedWorld;private static BlockPos expectedPosition;private static int contextReads;
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD) public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!enabled())return;event.register(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.ATTACHMENT_TYPES,helper->{contextAttachment=net.neoforged.neoforge.attachment.AttachmentType.builder(()->"retained").serialize(new net.neoforged.neoforge.attachment.IAttachmentSerializer<Tag,String>(){
            public String read(net.neoforged.neoforge.attachment.IAttachmentHolder owner,Tag tag,HolderLookup.Provider registries){contextReads++;if(!(owner instanceof net.minecraft.world.level.block.entity.BlockEntity tile)||tile.getLevel()!=expectedWorld||!tile.getBlockPos().equals(expectedPosition))throw new IllegalStateException("Attachment physical deserialize context");return tag.getAsString();}
            public Tag write(String value,HolderLookup.Provider registries){return StringTag.valueOf(value);}
        }).build();helper.register(net.minecraft.resources.ResourceLocation.parse("vandorlabs:surface_staging_context"),contextAttachment);});}
    }
    private static final BlockPos BASE=new BlockPos(40,110,40);
    private static ServerLevel world;private static int ticks,checks;private static long idleScans,idlePackets;
    private static final class Actor extends net.neoforged.neoforge.common.util.FakePlayer {Actor(ServerLevel level){super(level,new com.mojang.authlib.GameProfile(new UUID(0,2211599),"NativeSurfaceRuntime"));}}
    private static boolean enabled(){return Boolean.getBoolean("vandorlabs.surfaceRuntimeChecks");}
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Surface tick="+ticks+": "+why);}
    @SubscribeEvent public static void started(ServerStartedEvent event){if(!enabled())return;try{world=event.getServer().overworld();for(int x=BASE.getX()-1;x<=BASE.getX()+5;x++)for(int z=BASE.getZ()-1;z<=BASE.getZ()+4;z++){world.setChunkForced(x>>4,z>>4,true);world.getChunkAt(new BlockPos(x,BASE.getY(),z));}for(int x=0;x<3;x++)for(int z=0;z<3;z++)place(x,z);}catch(Throwable failure){fail(failure);}}
    private static void place(int x,int z){world.setBlock(BASE.offset(x,0,z),NativeSurfaceBindings.BLOCK.defaultBlockState(),3);}
    private static NativeSurfaceEntity tile(int x,int z){return (NativeSurfaceEntity)world.getBlockEntity(BASE.offset(x,0,z));}
    private static void size(int width,int height,int cells){int count=0;for(int x=0;x<4;x++)for(int z=0;z<3;z++)if(world.getBlockEntity(BASE.offset(x,0,z)) instanceof NativeSurfaceEntity tile){need(tile.layout().geometry.width==width&&tile.layout().geometry.height==height,"all members agree on exact filled layout");count++;}need(count==cells,"remaining physical cells");}
    private static long scans(){long result=0;for(int x=0;x<4;x++)for(int z=0;z<3;z++)if(world.getBlockEntity(BASE.offset(x,0,z)) instanceof NativeSurfaceEntity tile)result+=tile.discoveryScans;return result;}
    private static long packets(){long result=0;for(int x=0;x<4;x++)for(int z=0;z<3;z++)if(world.getBlockEntity(BASE.offset(x,0,z)) instanceof NativeSurfaceEntity tile)result+=tile.visualPackets;return result;}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(!enabled()||world==null)return;try{ticks++;switch(ticks){
        case 2->{size(3,3,9);data();staging();world.setBlock(BASE,Blocks.AIR.defaultBlockState(),3);}
        case 4->{size(1,1,8);place(0,0);}
        case 6->{size(3,3,9);world.setBlock(BASE.offset(1,0,1),Blocks.AIR.defaultBlockState(),3);}
        case 8->{size(1,1,8);place(1,1);}
        case 10->{size(3,3,9);for(int z=0;z<3;z++)place(3,z);}
        case 12->{size(4,3,12);for(int x=0;x<4;x++)for(int z=0;z<3;z++)need(tile(x,z).appearanceLayout().cells.size()==1,"unequal Crop faces remain independently visible");var actor=new Actor(world);actor.setGameMode(GameType.SURVIVAL);actor.setPos(BASE.getX()+.5,BASE.getY()+1,BASE.getZ()-.5);need(tile(0,0).configure(actor,"landing","landing-grey",0),"authorized complete-pad appearance");}
        case 14->{for(int x=0;x<4;x++)for(int z=0;z<3;z++){need(tile(x,z).appearanceLayout().cells.size()==12,"unequal Stretch shared drawing coordinates");need(tile(x,z).getModelData().get(NativeSurfaceEntity.VISUAL)!=null,"every surviving member has terrain quads independent of root renderer");}for(int z=0;z<3;z++)world.setBlock(BASE.offset(0,0,z),Blocks.AIR.defaultBlockState(),3);}
        case 16->{size(3,3,9);idleScans=scans();idlePackets=packets();}
        case 56->{need(scans()==idleScans&&packets()==idlePackets,"settled pads perform no repeated topology scan or packet churn");System.out.println("[vandorlabs][surface-runtime] PASS checks="+checks+" rootRemoval/holeRepair/unequalPads/idle/SourceNBT/inertRaw");world=null;event.getServer().halt(false);}
        default->need(ticks<80,"bounded exact lifecycle");
    }}catch(Throwable failure){fail(failure);}}
    private static void data(){var original=new CompoundTag();original.putString("SurfaceOn","landing");original.putString("SurfaceOff","landing-grey");original.putInt("SurfaceLayout",1);original.putLong("SurfaceGroupOffset",BlockPos.ZERO.asLong());original.putInt("SurfaceGroupSize",1);original.putInt("SurfaceGroupNormal",1);original.putString("UnknownSurfaceField","retained");var staged=new NativeSurfaceEntity(NativeSurfaceBindings.ENTITY,BASE,NativeSurfaceBindings.BLOCK.defaultBlockState());staged.setLevel(world);staged.loadWithComponents(original,world.registryAccess());need(!staged.inert()&&staged.activeArtwork().equals("landing"),"Source appearance defaults/load");var saved=staged.saveWithoutMetadata(world.registryAccess());need(saved.getString("UnknownSurfaceField").equals("retained")&&!original.contains("NativeSurfaceSchema"),"unknown own data retained and input never mutated");var malformed=original.copy();malformed.putInt("SurfaceGroupSize",33);var bad=new NativeSurfaceEntity(NativeSurfaceBindings.ENTITY,BASE,staged.getBlockState());bad.setLevel(world);bad.loadWithComponents(malformed,world.registryAccess());need(bad.inert(),"out-of-bounds saved group remains inert");var after=bad.saveWithoutMetadata(world.registryAccess());for(var key:malformed.getAllKeys())need(Objects.equals(malformed.get(key),after.get(key)),"exact failed original raw "+key);}
    private static void staging(){
        var probe=BASE.east(6);for(var block:List.of(Blocks.CHEST,Blocks.OAK_SIGN,NativeSurfaceBindings.BLOCK)){world.setBlock(probe,block.defaultBlockState(),3);var source=world.getBlockEntity(probe);var raw=source.saveWithFullMetadata(world.registryAccess());var attachments=new CompoundTag();attachments.putString("vandorlabs:surface_staging_context","retained");raw.put("neoforge:attachments",attachments);var before=raw.copy();var cell=new com.vandorlabs.neoforge.transfer.NativeRawCell(probe,source.getBlockState(),raw,2*1024*1024);expectedWorld=world;expectedPosition=probe;int reads=contextReads;var staged=cell.stage(world,tile->!(tile instanceof NativeSurfaceEntity surface)||!surface.inert(),Objects::equals);need(staged.getLevel()==world&&staged.getBlockPos().equals(probe)&&contextReads==reads+1,"chest/sign/Surface physical world+position before attachment decode");need(world.getBlockEntity(probe)==source&&raw.equals(before)&&cell.tile().equals(before),"staging never publishes or mutates original opaque data");var mutated=cell.tile();mutated.putString("Unknown","changed copy");need(!cell.tile().contains("Unknown"),"raw accessor immutable ownership");world.setBlock(probe,Blocks.AIR.defaultBlockState(),3);}
        var palette=NbtUtils.writeBlockState(NativeSurfaceBindings.BLOCK.defaultBlockState());palette.getCompound("Properties").remove("formed");boolean refused=false;try{com.vandorlabs.neoforge.transfer.NativeRawCell.decodePalette(world.registryAccess(),palette,Map.of());}catch(IllegalArgumentException expected){refused=true;}need(refused,"missing listed property cannot silently default");need(com.vandorlabs.neoforge.transfer.NativeRawCell.decodePalette(world.registryAccess(),palette,Map.of("formed","false"))==NativeSurfaceBindings.BLOCK.defaultBlockState(),"explicit old-palette absence migration");palette.putString("Name","missing:surface");refused=false;try{com.vandorlabs.neoforge.transfer.NativeRawCell.decodePalette(world.registryAccess(),palette,Map.of());}catch(IllegalArgumentException expected){refused=true;}need(refused,"unknown block never falls back to AIR");
    }
    private static void fail(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][surface-runtime] FAIL "+failure);var server=world==null?net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer():world.getServer();world=null;if(server!=null)server.halt(false);}
}
