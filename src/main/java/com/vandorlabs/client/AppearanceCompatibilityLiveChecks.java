package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.persistence.*;
import com.vandorlabs.tiles.*;
import java.io.File;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/** Real creative middle-click packets, native placement, and the resulting rendered item models. */
final class AppearanceCompatibilityLiveChecks {
    private static final BlockPos SOURCE=new BlockPos(1450,110,1450),TARGET=SOURCE.east(6),CATWALK=SOURCE.east(16);
    private static final String[] FAMILIES={"programmable_door","large_programmable_door","programmable_trapdoor",
            "programmable_diagonal_trapdoor","programmable_block","programmable_wall","programmable_diagonal_wall",
            "programmable_porthole_wall","programmable_porthole_block","programmable_diagonal_porthole"};
    private static final List<ItemStack> picked=new ArrayList<>();
    private static int stage,ticks,index;
    private static ItemStack expected=ItemStack.EMPTY,beforePick=ItemStack.EMPTY;
    private static long previousModel;
    private static List<String> modelFailures;
    private static void require(boolean ok,String why){if(ok)return;if(modelFailures!=null){modelFailures.add(why);return;}throw new IllegalStateException("Appearance pick family="+FAMILIES[Math.min(index/2,FAMILIES.length-1)]+" variant="+(index%2)+" stage="+stage+": "+why);}

    static boolean tick(Minecraft mc,File output)throws Exception {
        EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
        if(actor==null)return false;
        if(stage==0){
            mc.displayGuiScreen(null);
            WailaTooltipRuntimeChecks.run(mc,output);
            mc.getIntegratedServer().callFromMainThread(()->{IndustrialRenewalCatwalkRuntimeChecks.run(actor.world,CATWALK);return true;}).get();
            begin(mc,actor);return false;
        }
        require(++ticks<300,"synchronization timeout mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" expected="+expected+" pos="+mc.player.getPositionVector());
        if(stage==1){
            if(mc.playerController.getCurrentGameType()!=GameType.CREATIVE||mc.player.inventory.currentItem!=0
                    ||!near(mc,SOURCE)||!same(pick(mc.world,SOURCE,mc.player),expected))return false;
            for(int slot=0;slot<9;slot++)if(!same(mc.player.inventory.getStackInSlot(slot),beforePick))return false;
            // Both door halves/all nine large cells must resolve the same owning settings.
            Block block=Block.getBlockFromItem(expected.getItem());
            for(BlockPos p:BlockPos.getAllInBox(SOURCE.add(-1,0,0),SOURCE.add(1,2,0)))
                if(mc.world.getBlockState(p).getBlock()==block)require(same(pick(mc.world,p,mc.player),expected),"part resolves picked settings at "+p);
            BlockPos hit=SOURCE.up(block instanceof BlockLargeProgrammableDoor?2:block instanceof BlockVandorDoor?1:0);
            NBTTagCompound tag=expected.getSubCompound(block instanceof BlockVandorDoor?"SpaceDoorSettings":"BlockEntityTag");
            WailaTooltipRuntimeChecks.check(mc.world,hit,HousingTextureList.name(tag.getInteger(block instanceof BlockVandorDoor?"DoorFaceTexture":"housingTexture")),
                    block instanceof BlockVandorDoor||block instanceof BlockProgrammableTrapdoor?(index%2==0?"Rotating":"Sliding"):null);
            mc.objectMouseOver=new RayTraceResult(new Vec3d(hit).addVector(.5,.5,.5),EnumFacing.NORTH,hit);
            net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(Minecraft.class,"middleClickMouse","func_147112_ai").invoke(mc);
            require(same(mc.player.getHeldItemMainhand(),expected),"actual middle-click chooses full NBT despite other same-item hotbar entries");
            ItemStack stack=mc.player.getHeldItemMainhand().copy();
            IBakedModel model=model(mc,stack);long fingerprint=fingerprint(model);
            require(hasSprite(model,mainTexture(stack)),"selected primary material reaches rendered quads: "+mainTexture(stack));
            if(index%2==1)require(previousModel!=fingerprint,"changing only configuration changes visible item quads");
            previousModel=fingerprint;picked.add(stack);
            require(same(stack,expected),"model override does not mutate picked NBT");
            stage=2;ticks=0;return false;
        }
        if(stage==2){
            if(!mc.getIntegratedServer().callFromMainThread(()->same(actor.getHeldItemMainhand(),expected)).get())return false;
            int slot=mc.player.inventory.currentItem;
            mc.getIntegratedServer().addScheduledTask(()->{actor.setGameType(GameType.CREATIVE);fly(actor);actor.sendPlayerAbilities();actor.inventory.currentItem=slot;actor.connection.setPlayerLocation(TARGET.getX()+.5,TARGET.getY()+1,TARGET.getZ()-1.5,0,0);actor.sendContainerToPlayer(actor.inventoryContainer);}).get();
            mc.playerController.setGameType(GameType.CREATIVE);fly(mc.player);stage=3;ticks=0;return false;
        }
        if(stage==3){
            if(!near(mc,TARGET)||!same(mc.player.getHeldItemMainhand(),expected)||mc.playerController.getCurrentGameType()!=GameType.CREATIVE)return false;
            require(mc.playerController.processRightClickBlock(mc.player,mc.world,TARGET.down(),EnumFacing.UP,new Vec3d(TARGET).addVector(.5,0,.5),EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"native placement accepts picked item");
            stage=4;ticks=0;return false;
        }
        if(stage==4){
            if(!same(pick(mc.world,TARGET,mc.player),expected)||!mc.getIntegratedServer().callFromMainThread(()->same(pick(actor.world,TARGET,actor),expected)).get())return false;
            if(++index<FAMILIES.length*2){begin(mc,actor);return false;}
            require(IndustrialRenewalCatwalkRuntimeChecks.verifyClient(mc.world,CATWALK),"catwalk fixture synchronized");
            renderPicked(mc,output);checkModels(mc);
            System.out.println("[vandorlabs][reprolab] appearance-compat PASS actual middle-click/full hotbar/server slot/native placement="+index+" bounded models, split artwork, glass, geometry and rendered icons");
            return true;
        }
        return false;
    }

    private static void begin(Minecraft mc,EntityPlayerMP actor)throws Exception {
        ItemStack settings=configured(FAMILIES[index/2],index%2);
        beforePick=index%2==1?picked.get(picked.size()-1):new ItemStack(settings.getItem());
        expected=mc.getIntegratedServer().callFromMainThread(()->{
            for(BlockPos p:BlockPos.getAllInBox(SOURCE.add(-2,0,-2),TARGET.add(2,3,2)))actor.world.setBlockToAir(p);
            for(BlockPos p:BlockPos.getAllInBox(SOURCE.add(-2,-1,-2),TARGET.add(2,-1,2)))actor.world.setBlockState(p,Blocks.STONE.getDefaultState(),3);
            actor.setGameType(GameType.CREATIVE);fly(actor);actor.sendPlayerAbilities();actor.inventory.currentItem=0;actor.setHeldItem(EnumHand.MAIN_HAND,settings.copy());
            actor.connection.setPlayerLocation(SOURCE.getX()+.5,SOURCE.getY()+1,SOURCE.getZ()-1.5,0,0);
            require(actor.getHeldItemMainhand().getItem().onItemUse(actor,actor.world,SOURCE.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)==EnumActionResult.SUCCESS,"fixture native source placement");
            for(int n=0;n<3;n++)DeferredTileLoad.flush(actor.world);
            ItemStack actual=pick(actor.world,SOURCE,actor);require(!actual.isEmpty(),"fixture configured source");
            // Keep a different finish of the exact same item in every slot. Variant
            // two retains the first pick so a matching item ID cannot hide bad NBT matching.
            actor.inventory.clear();for(int i=0;i<9;i++)actor.inventory.setInventorySlotContents(i,beforePick.copy());
            actor.inventory.currentItem=0;actor.sendContainerToPlayer(actor.inventoryContainer);return actual;
        }).get();
        mc.playerController.setGameType(GameType.CREATIVE);fly(mc.player);mc.player.inventory.currentItem=0;
        stage=1;ticks=0;
    }
    private static void fly(net.minecraft.entity.player.EntityPlayer player){player.capabilities.isFlying=true;player.motionX=0;player.motionY=0;player.motionZ=0;}
    private static boolean near(Minecraft mc,BlockPos pos){return mc.player.getDistanceSq(pos.getX()+.5,pos.getY()+1,pos.getZ()-1.5)<.05;}
    private static ItemStack pick(World world,BlockPos pos,net.minecraft.entity.player.EntityPlayer player){IBlockState state=world.getBlockState(pos);return state.getBlock().getPickBlock(state,new RayTraceResult(new Vec3d(pos).addVector(.5,.5,.5),EnumFacing.NORTH,pos),world,pos,player);}
    private static boolean same(ItemStack a,ItemStack b){return !a.isEmpty()&&!b.isEmpty()&&a.getItem()==b.getItem()&&a.getMetadata()==b.getMetadata()&&ItemStack.areItemStackTagsEqual(a,b);}
    private static ItemStack configured(String name,int variant){
        Block block=Block.getBlockFromName("vandorlabs:"+name);require(block!=null,"fixture block "+name);ItemStack stack=new ItemStack(block,2);
        int finish=variant==0?ScreenHousingTextures.screenIndex("integrated_circuit_board_panel_1"):ScreenHousingTextures.DEFAULT_TRAPDOOR;
        NBTTagCompound tag=new NBTTagCompound();
        if(block instanceof BlockVandorDoor){
            TileEntitySpaceDoor tile=new TileEntitySpaceDoor();tile.configure(2,0,true,0,false,variant==1,true,SpaceDoorData.TRIGGER_DISABLED);tile.setFaceTexture(finish);tile.setTextureLayout(variant);tag=tile.itemSettings();stack.setTagInfo("SpaceDoorSettings",tag);
        }else if(block instanceof BlockProgrammableTrapdoor){
            new TrapdoorData(finish,variant,variant,SpaceDoorData.TRIGGER_DISABLED,false,false,variant==1,false,false).write(new NbtPrimitiveData(tag));stack.setTagInfo("BlockEntityTag",tag);
        }else{
            tag.setInteger("housingTexture",finish);tag.setInteger("PortholeShape",variant*3);tag.setInteger("GlassShade",variant==0?1:GlassTextures.IDS.length-1);tag.setInteger("GlassTint",1);tag.setInteger("GlassMaterial",-1);
            if(name.equals("programmable_diagonal_porthole"))tag.setBoolean(WallSettings.FULL,variant==1);stack.setTagInfo("BlockEntityTag",tag);
        }
        return stack;
    }
    private static String mainTexture(ItemStack stack){NBTTagCompound tag=stack.getSubCompound("SpaceDoorSettings");if(tag!=null)return ScreenHousingTextures.fullTexture(tag.getInteger("DoorFaceTexture"));tag=stack.getSubCompound("BlockEntityTag");return Block.getBlockFromItem(stack.getItem()) instanceof BlockProgrammableTrapdoor?ScreenHousingTextures.fullTexture(tag.getInteger("housingTexture")):ScreenHousingTextures.texture(tag.getInteger("housingTexture"));}
    private static IBakedModel model(Minecraft mc,ItemStack stack){return mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);}
    private static boolean hasSprite(IBakedModel model,String sprite){for(BakedQuad q:ItemAppearanceMesh.all(model))if(sprite.equals(q.getSprite().getIconName()))return true;return false;}
    private static long fingerprint(IBakedModel model){long hash=1;for(BakedQuad q:ItemAppearanceMesh.all(model)){hash=31*hash+q.getSprite().getIconName().hashCode();hash=31*hash+Arrays.hashCode(q.getVertexData());}return hash;}

    private static void checkModels(Minecraft mc){
        List<String> failures=new ArrayList<>();int checks;
        modelFailures=failures;
        try{checks=checkModelMatrix(mc);}finally{modelFailures=null;}
        require(failures.isEmpty(),"model assertions failed:\n - "+String.join("\n - ",failures));
        System.out.println("[vandorlabs][reprolab] picked-item-models PASS variants="+checks+" reload generation, cache reuse, glass and split vanilla artwork");
    }
    private static int checkModelMatrix(Minecraft mc){
        int checks=0;
        for(String name:new String[]{"programmable_door","large_programmable_door"})for(int design=0;design<30;design++)for(int variant=0;variant<2;variant++){
            ItemStack stack=configured(name,variant);stack.getSubCompound("SpaceDoorSettings").setInteger("SpaceDesign",design);
            IBakedModel rendered=model(mc,stack);require(hasSprite(rendered,mainTexture(stack)),name+" selected face design="+design);require(model(mc,stack)==rendered,"repeat lookup reuses prepared geometry");checks++;
        }
        int vanilla=CustomBlockMaterials.choice(new ItemStack(net.minecraft.init.Items.OAK_DOOR));
        require(CustomBlockMaterials.isCustom(vanilla)&&CustomBlockTextures.isDoor(vanilla),"fixture selects the vanilla door item");
        for(String name:new String[]{"programmable_door","large_programmable_door","programmable_trapdoor"}){
            ItemStack stack=configured(name,0);NBTTagCompound tag=stack.getSubCompound(name.endsWith("trapdoor")?"BlockEntityTag":"SpaceDoorSettings");tag.setInteger(name.endsWith("trapdoor")?"housingTexture":"DoorFaceTexture",vanilla);
            if(name.endsWith("trapdoor")){
                tag.setInteger("TrapdoorPosition",1);
                IBakedModel lower=model(mc,stack);require(hasSprite(lower,CustomBlockTextures.sprite(vanilla,false).getIconName())&&!hasSprite(lower,CustomBlockTextures.sprite(vanilla,true).getIconName()),"standalone legacy trapdoor uses the native lower-half door artwork");
                tag.setInteger("TrapdoorTextureLayout",2);
            }
            IBakedModel rendered=model(mc,stack);require(hasSprite(rendered,CustomBlockTextures.sprite(vanilla,true).getIconName())&&hasSprite(rendered,CustomBlockTextures.sprite(vanilla,false).getIconName()),"vanilla two-part door artwork "+name);checks++;
        }
        for(String name:new String[]{"programmable_door","large_programmable_door"}){
            ItemStack stack=configured(name,0);NBTTagCompound tag=stack.getSubCompound("SpaceDoorSettings");tag.setInteger("SpaceDesign",15);tag.setInteger("DoorFaceTexture",-1);
            new DoorGlassAppearance(-1,1,1,0).write(tag);long blue=fingerprint(model(mc,stack));new DoorGlassAppearance(-1,GlassTextures.IDS.length-1,1,0).write(tag);
            require(hasSprite(model(mc,stack),GlassTextures.texture(GlassTextures.IDS.length-1,1,1)),"chosen gold glass in door preview "+name);require(blue!=fingerprint(model(mc,stack)),"door glass changes model "+name);checks++;
        }
        int reactor=0;while(reactor<ScreenHousingTextures.IDS.length&&!ScreenHousingTextures.reactorArtwork(reactor))reactor++;
        require(reactor<ScreenHousingTextures.IDS.length,"Reactor artwork catalog entry");
        for(String name:new String[]{"programmable_door","large_programmable_door"}){
            ItemStack stack=configured(name,0);NBTTagCompound tag=stack.getSubCompound("SpaceDoorSettings");tag.setInteger("DoorFaceTexture",reactor);new DoorGlassAppearance(-1,GlassTextures.IDS.length-1,1,0).write(tag);
            IBakedModel rendered=model(mc,stack);require(hasSprite(rendered,ScreenHousingTextures.fullTexture(reactor))&&hasSprite(rendered,"vandorlabs:blocks/programmable_glass/metal_side")&&hasSprite(rendered,GlassTextures.texture(GlassTextures.IDS.length-1,1,1)),"Reactor frame/metal/glass use distinct atlas sprites "+name);checks++;
        }
        for(String name:new String[]{"programmable_trapdoor","programmable_diagonal_trapdoor"}){
            ItemStack stack=configured(name,0);NBTTagCompound tag=stack.getSubCompound("BlockEntityTag");Set<Long> shapes=new HashSet<>();
            for(int mode=0;mode<3;mode++){tag.setInteger("TrapdoorPosition",mode);shapes.add(fingerprint(model(mc,stack)));checks++;}
            require(shapes.size()==3,"trapdoor position/geometry changes without changing material "+name);
            int frame=ScreenHousingTextures.screenIndex("frames/frames-screen-full/frame_bolted_1254");tag.setInteger("housingTexture",frame);IBakedModel framed=model(mc,stack);
            require(hasSprite(framed,ScreenHousingTextures.fullTexture(frame)),"authored trapdoor frame artwork "+name);require(ItemAppearanceMesh.all(framed).size()>12,"authored trapdoor frame retains an aperture "+name);checks++;
        }
        for(String name:new String[]{"programmable_porthole_wall","programmable_porthole_block","programmable_diagonal_porthole"}){
            ItemStack stack=configured(name,0);NBTTagCompound tag=stack.getSubCompound("BlockEntityTag");Set<Long> shapes=new HashSet<>();
            for(int shape=0;shape<4;shape++){tag.setInteger("PortholeShape",shape);shapes.add(fingerprint(model(mc,stack)));checks++;}require(shapes.size()==4,"four distinct porthole outlines "+name);
            long blue=fingerprint(model(mc,stack));tag.setInteger("GlassShade",GlassTextures.IDS.length-1);require(blue!=fingerprint(model(mc,stack)),"porthole glazing changes independently of wall "+name);require(hasSprite(model(mc,stack),GlassTextures.texture(GlassTextures.IDS.length-1,1,1)),"gold porthole pane "+name);checks++;
            if(name.equals("programmable_diagonal_porthole")){
                shapes.clear();for(int mode=0;mode<3;mode++){tag.setBoolean(WallSettings.HALF,mode==2);tag.setBoolean(WallSettings.FULL,mode==1);shapes.add(fingerprint(model(mc,stack)));checks++;}require(shapes.size()==3,"diagonal porthole proportions");
            }
        }
        require(ConfiguredPickItemModels.retainedBytes()<=8L*1024*1024,"item appearance cache byte bound after variant matrix");
        ItemStack stack=picked.get(0);IBakedModel before=model(mc,stack);long fingerprint=fingerprint(before);ItemVariantModels.clear();IBakedModel after=model(mc,stack);
        require(before!=after&&fingerprint==fingerprint(after),"resource/format generation discards cached models without changing appearance");
        require(ConfiguredPickItemModels.retainedBytes()<=8L*1024*1024,"item appearance cache byte bound");
        return checks;
    }

    private static void renderPicked(Minecraft mc,File output)throws Exception {
        int width=320,height=128;Framebuffer frame=new Framebuffer(width,height,true);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,width,height,0,1000,3000);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.translate(0,0,-2000);
        try{
            frame.bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clearColor(.12549F,.188235F,.25098F,1);GlStateManager.clear(16640);RenderHelper.enableGUIStandardItemLighting();
            for(int i=0;i<picked.size();i++)mc.getRenderItem().renderItemAndEffectIntoGUI(picked.get(i),16+(i%8)*36,16+(i/8)*36);
            RenderHelper.disableStandardItemLighting();java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(width,height,frame);javax.imageio.ImageIO.write(image,"png",new File(output,"appearance-compat-picked-items.png"));int background=image.getRGB(0,0);long previous=0;
            for(int i=0;i<picked.size();i++){
                int ox=16+(i%8)*36,oy=16+(i/8)*36,ink=0;long hash=1;
                for(int x=0;x<16;x++)for(int y=0;y<16;y++){int pixel=image.getRGB(ox+x,oy+y);hash=31*hash+pixel;if(pixel!=background){ink++;if(i/2<4||i/2>=7)require(x>0&&y>0&&x<15&&y<15,"padded picked icon index="+i+" pixel="+x+","+y);}}
                require(ink>=5,"visible rendered picked icon "+i);if(i%2==1)require(hash!=previous,"rendered hotbar pixels update between textures "+FAMILIES[i/2]);previous=hash;
            }
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.enableCull();}
    }
    private AppearanceCompatibilityLiveChecks(){}
}
