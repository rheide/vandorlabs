package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.blocks.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.init.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Real model/atlas and inventory interaction checks for the common Custom picker. */
final class CustomMaterialRuntimeChecks {
    static void run(net.minecraft.entity.player.EntityPlayer player) {
        Minecraft mc=Minecraft.getMinecraft();
        try{SharedTextureResources.check(mc);}catch(java.io.IOException error){throw new IllegalStateException("Shared texture resource lookup",error);}
        int imported=0;java.util.Set<String> categories=new java.util.HashSet<>();
        for(int i=ScreenHousingTextures.LEGACY_COUNT;i<ScreenHousingTextures.BUILTIN_COUNT;i++)if(ScreenHousingTextures.IDS[i].startsWith("imported_")) {
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(i));
            require(!sprite.getIconName().equals("missingno") && sprite.getIconWidth()>0,"imported texture missing from atlas: "+ScreenHousingTextures.IDS[i]);
            categories.add(ScreenHousingTextures.category(i));imported++;
        }
        require(imported==41 && categories.equals(new java.util.HashSet<>(java.util.Arrays.asList("Tech","Hull","Trapdoors","Windows"))),"imported category/texture coverage");
        checkMenu(mc);
        checkTurntableGeneral(mc);
        checkCorp(mc);
        JoinedHousingRuntimeChecks.run(mc);
        int hatchNames=0;
        for(int i=ScreenHousingTextures.LEGACY_COUNT;i<ScreenHousingTextures.BUILTIN_COUNT;i++)
            if("Trapdoors".equals(ScreenHousingTextures.category(i))) {
                require(ScreenHousingTextures.label(i)!=null && ScreenHousingTextures.label(i).replaceFirst(" (Small|Large)$","").length()<=20,"hatch label still too long");hatchNames++;
            }
        require(hatchNames==16 && "Armored Hatch".equals(ScreenHousingTextures.label(ScreenHousingTextures.DEFAULT_TRAPDOOR)),"short hatch labels/default");
        System.out.println("[vandorlabs][reprolab] short-trapdoor-labels PASS eight designs, stable legacy aliases");
        for(net.minecraft.block.Block hatch:new net.minecraft.block.Block[]{ModBlocks.PROGRAMMABLE_TRAPDOOR,ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR}) {
            net.minecraft.client.renderer.block.model.IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(new ItemStack(hatch),mc.world,player);
            require(model.getParticleTexture().getIconName().equals(ScreenHousingTextures.fullTexture(ScreenHousingTextures.DEFAULT_TRAPDOOR)),"plain hatch item uses old default artwork");
        }
        System.out.println("[vandorlabs][reprolab] imported-materials-runtime PASS (41 retained textures, four categories, both default hatch icons)");
        ItemStack stone=new ItemStack(Blocks.STONE),door=new ItemStack(Items.OAK_DOOR);
        int stoneId=CustomBlockMaterials.choice(stone),doorId=CustomBlockMaterials.choice(door);
        require(CustomBlockMaterials.block(door)==Blocks.OAK_DOOR,"vanilla door item mapping");
        require(CustomBlockTextures.isDoor(doorId),"door classification");
        require(!CustomBlockTextures.sprite(doorId,false).getIconName().equals(CustomBlockTextures.sprite(doorId,true).getIconName()),"upper/lower door artwork");
        require(CustomBlockTextures.texture(stoneId).equals("minecraft:blocks/stone"),"vanilla block artwork");
        net.minecraft.block.Block programmableDoor=net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_door");
        for(boolean framed:new boolean[]{false,true})for(int detail=0;detail<3;detail++) {
            int metadata=TileEntitySpaceDoor.metadata(0,detail,framed,false,false,1,false);
            SelectedDoorGeometry selected=DoorRenderModels.get(programmableDoor,metadata).selected();
            double inset=framed?1/16D:0;
            require(Math.abs(selected.bounds[0]-inset)<1e-6 && Math.abs(selected.bounds[3]-(1-inset))<1e-6,"selected door frame width");
            require(Math.abs(selected.bounds[1]-inset)<1e-6 && Math.abs(selected.bounds[4]-(2-inset))<1e-6,"selected door frame height");
            require(Math.abs(selected.bounds[2]-12.24/16D)<1e-6 && Math.abs(selected.bounds[5]-14.24/16D)<1e-6,"selected door native depth");
            require(selected.getQuads(null,null,0).stream().anyMatch(q->q.getSprite().getIconName().endsWith("/hinge")),"replacement leaf lost its hinges");
            require(selected.getQuads(null,null,0).stream().anyMatch(q->!q.getSprite().getIconName().endsWith("/hinge") && q.getFace().getAxis()!=net.minecraft.util.EnumFacing.Axis.Z),"replacement leaf lost native edges");
            require(DoorRenderModels.get(programmableDoor,metadata+2160).selected().getQuads(null,null,0).stream().noneMatch(q->q.getSprite().getIconName().endsWith("/hinge")),"hinges-off replacement retained hardware");
        }
        net.minecraft.block.Block other=net.minecraft.block.Block.getBlockFromName("immersiveengineering:stone_decoration");
        if(other!=null) {
            int choice=CustomBlockMaterials.choice(new ItemStack(other));require(choice>=0,"mod block item selection");
            require(CustomBlockTextures.texture(choice).startsWith("immersiveengineering:"),"mod block artwork");
        }
        ItemStack configured=new ItemStack(ModBlocks.PROGRAMMABLE_BLOCK);NBTTagCompound tag=new NBTTagCompound();tag.setInteger("housingTexture",stoneId);configured.setTagInfo("BlockEntityTag",tag);
        net.minecraft.client.renderer.block.model.IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(configured,player.world,player);
        require(model.getParticleTexture().getIconName().equals("minecraft:blocks/stone"),"custom configured inventory artwork");
        require(CustomBlockMaterials.choice(configured)==stoneId,"sampling configured programmable material");
        tag.setInteger("housingTexture",ScreenHousingTextures.doorIndex(0,1));
        require(CustomBlockMaterials.choice(configured)==ScreenHousingTextures.canonicalChoice(ScreenHousingTextures.doorIndex(0,1)),"sampling configured built-in material preserves canonical artwork");
        int doorChoice=ScreenHousingTextures.doorIndex(0,1);
        net.minecraft.client.renderer.texture.TextureAtlasSprite full=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(doorChoice));
        net.minecraft.client.renderer.texture.TextureAtlasSprite half=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(doorChoice));
        require(Math.abs(UnifiedTextureSprites.aspect(full)-.5)<1e-5 && Math.abs(UnifiedTextureSprites.aspect(half)-1)<1e-5,"full door preview / square block half aspect");
        String[] nativeIds=new String[ModBlocks.SCREEN_OPTIONS.size()];int ni=0;for(ModBlocks.ScreenOption option:ModBlocks.SCREEN_OPTIONS)nativeIds[ni++]=option.bareId;
        for(String id:nativeIds)require(ScreenHousingTextures.screenIndex(id+"_static")>=ScreenHousingTextures.LEGACY_COUNT,"native screen thumbnail "+id);
        for(String id:TileEntityAnimatedScreenSelector.INPUT_PANELS)require(ScreenHousingTextures.screenIndex("console_inputs/"+id+"_static")>=ScreenHousingTextures.LEGACY_COUNT,"native input thumbnail "+id);
        BlockPos pos=new BlockPos(player.posX,248,player.posZ);
        player.world.setBlockState(pos,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState());
        try {
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)player.world.getTileEntity(pos);tile.setHousingTexture(stoneId);
            net.minecraft.block.state.IBlockState state=ModBlocks.PROGRAMMABLE_BLOCK.getExtendedState(player.world.getBlockState(pos),player.world,pos);
            model=mc.getBlockRendererDispatcher().getModelForState(player.world.getBlockState(pos));
            java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads=model.getQuads(state,null,0);require(quads.size()==6,"custom housing geometry");
            for(net.minecraft.client.renderer.block.model.BakedQuad quad:quads)require(quad.getSprite().getIconName().equals("minecraft:blocks/stone"),"custom world housing artwork");
        }finally{player.world.setBlockToAir(pos);}
        ItemStack original=player.inventory.getStackInSlot(0);net.minecraft.inventory.Container container=player.openContainer;
        net.minecraft.client.gui.GuiScreen previous=mc.currentScreen;
        try {
            player.inventory.setInventorySlotContents(0,door.copy());final int[] picked={-1};
            GuiCustomTexture gui=new GuiCustomTexture(new net.minecraft.client.gui.GuiScreen(){},id->picked[0]=id);
            mc.displayGuiScreen(gui);int left=(gui.width-252)/2,top=(gui.height-198)/2;
            gui.mouseClicked(left+47,top+143,0);gui.mouseReleased(left+126,top+45,0);
            require(picked[0]==doorId,"drag into custom sample slot");
            require(player.inventory.getStackInSlot(0).getItem()==door.getItem() && player.inventory.getStackInSlot(0).getCount()==1,"sample consumed inventory item");
            require(player.openContainer==container,"custom picker replaced authorized container");
        }catch(java.io.IOException e){throw new RuntimeException(e);}finally{player.inventory.setInventorySlotContents(0,original);mc.displayGuiScreen(previous);}
        System.out.println("[vandorlabs][reprolab] custom-materials-runtime PASS");
    }
    static void checkLightPicker(GuiProgrammableLight gui,java.io.File output) {
        try {
            java.lang.reflect.Field options=HousingTextureList.class.getDeclaredField("options"),count=HousingTextureList.class.getDeclaredField("count");
            options.setAccessible(true);count.setAccessible(true);
            int expected=new ProgrammableDialogLayout(gui.width,gui.height).rows(true);
            require(expected>=7,"light dialog has too few texture rows");
            for(String name:new String[]{"faceList","housingList"}) {
                java.lang.reflect.Field field=GuiProgrammableLight.class.getDeclaredField(name);field.setAccessible(true);
                HousingTextureList list=(HousingTextureList)field.get(gui);
                require(count.getInt(list)==expected,"light picker visible rows");
                java.util.Map<?,?> entries=(java.util.Map<?,?>)options.get(list);
                int on=0,off=0;
                for(int i=0;i<ScreenHousingTextures.IDS.length;i++) {
                    if(ScreenHousingTextures.isLightOff(i)){off++;require(!entries.containsKey(i),"Off texture in light picker");}
                }
                for(int style=0;style<6;style++)if(entries.containsKey(ScreenHousingTextures.lightIndex(style)))on++;
                int amber=ScreenHousingTextures.screenIndex("lights/amber_hex_on");
                require(entries.containsKey(64) && entries.containsKey(amber),"hex light choices missing");
                for(String artwork:new String[]{"bussard_classic","bussard_modern","deflector_amber","deflector_blue","nacelle_a","nacelle_d","nacelle_defiant","nacelle_intrepid"})
                    require(entries.containsKey(ScreenHousingTextures.screenIndex("lights/"+artwork+"_on")),"new light missing from picker: "+artwork);
                require(on==6 && off==23,"light picker paired artwork coverage");
                for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(ScreenHousingTextures.lightOnly(i))require(entries.containsKey(i)!=ScreenHousingTextures.isLightOff(i),"Corp light ON picker filter");
            }
            java.lang.reflect.Field offTab=GuiProgrammableLight.class.getDeclaredField("editingOff");offTab.setAccessible(true);offTab.setBoolean(gui,true);gui.initGui();
            java.lang.reflect.Field face=GuiProgrammableLight.class.getDeclaredField("faceList");face.setAccessible(true);
            java.util.Map<?,?> offEntries=(java.util.Map<?,?>)options.get(face.get(gui));
            for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(ScreenHousingTextures.lightOnly(i))require(offEntries.containsKey(i),"Corp light OFF picker pair missing");
            offTab.setBoolean(gui,false);gui.initGui();
            java.lang.reflect.Field selection=GuiProgrammableLight.class.getDeclaredField("selected"),offSelection=GuiProgrammableLight.class.getDeclaredField("offSelected"),tab=GuiProgrammableLight.class.getDeclaredField("textureTab");selection.setAccessible(true);offSelection.setAccessible(true);tab.setAccessible(true);
            int saved=selection.getInt(gui),savedOff=offSelection.getInt(gui),savedTab=tab.getInt(gui);
            for(int view=0;view<3;view++){
                String id=view==0?"corp_ashiro_logo":view==1?"corp_ashiro_light_on":"corp_ashiro_light_off";int choice=-1;
                for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(id.equals(ScreenHousingTextures.IDS[i]))choice=i;
                require(choice>=0,"Corp preview choice");offTab.setBoolean(gui,view==2);tab.setInt(gui,view==2?1:0);selection.setInt(gui,choice);offSelection.setInt(gui,choice);gui.initGui();
                Minecraft mc=Minecraft.getMinecraft();mc.getFramebuffer().bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();gui.drawScreen(-1,-1,0);
                try{javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new java.io.File(output,"shot_corp_picker_"+view+".png"));}catch(java.io.IOException e){throw new RuntimeException(e);}
            }
            selection.setInt(gui,saved);offSelection.setInt(gui,savedOff);tab.setInt(gui,savedTab);offTab.setBoolean(gui,false);gui.initGui();
            System.out.println("[vandorlabs][reprolab] light-picker-runtime PASS rows="+expected);
        } catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    private static void checkCorp(Minecraft mc){
        int general=0,lights=0;
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(ScreenHousingTextures.IDS[i].startsWith("corp_")){
            boolean only=ScreenHousingTextures.lightOnly(i);
            if(only&&!ScreenHousingTextures.isLightOff(i))require(!ScreenHousingTextures.label(i).endsWith(" On"),"Corp lights omit On suffix");
            require(HousingTextureList.generalTexture(i)!=only&&com.vandorlabs.items.ItemProgrammableArmor.listedMaterial(i)!=only,"Corp light artwork leaked into general/armor picker");
            require(ScreenHousingTextures.category(i).equals(only?"Lights":"Corp"),"Corp materials and main Lights category");
            require(mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(i))!=mc.getTextureMapBlocks().getMissingSprite(),"Corp artwork missing from atlas");
            if(only){lights++;if(!ScreenHousingTextures.isLightOff(i)){
                TileEntityProgrammableLight tile=new TileEntityProgrammableLight();tile.setFaceTexture(i);tile.configure(0,15,false,17,0,com.vandorlabs.persistence.SpaceDoorData.TRIGGER_REDSTONE_ON);
                tile.setChannelSignal(true);String on=tile.activeFaceTexture();tile.setChannelSignal(false);String off=tile.activeFaceTexture();
                require(!on.equals(off)&&off.endsWith("_off"),"Corp automatic OFF pair/redstone");
                TileEntityProgrammableLight copy=new TileEntityProgrammableLight();copy.readFromNBT(tile.writeToNBT(new NBTTagCompound()));require(copy.activeFaceTexture().equals(off),"Corp light pair save/load");
            }}else general++;
        }
        require(general==18&&lights==12,"Corp artwork coverage");
        System.out.println("[vandorlabs][reprolab] corp-textures PASS 18 general, six light pairs, atlas, picker isolation, redstone and persistence");
    }
    private static void checkMenu(Minecraft mc) {
        HousingTextureList list=new HousingTextureList(0,0,180,23);
        require(list.selected()==23 && "seamed_padding".equals(ScreenHousingTextures.IDS[23]),"hidden saved selection lost");
        int retired=0;
        String fallback=ScreenHousingTextures.texture(0);
        for(int choice=0;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++)if(!ScreenHousingTextures.visible(choice)) {
            com.google.gson.JsonObject entry=ScreenHousingTextures.entry(choice);
            if(entry!=null && entry.has("alias")) {
                int target=ScreenHousingTextures.clamp(choice);
                require(target!=choice && ScreenHousingTextures.visible(target),"legacy High alias target");
                require(ScreenHousingTextures.texture(choice).equals(ScreenHousingTextures.texture(target)),"legacy High square artwork");
                require(ScreenHousingTextures.fullTexture(choice).equals(ScreenHousingTextures.fullTexture(target)),"legacy High face artwork");
                require(mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(choice))!=mc.getTextureMapBlocks().getMissingSprite(),"legacy High target not stitched");
                continue;
            }
            retired++;
            require(ScreenHousingTextures.clamp(choice)==choice,"retired saved choice number changed");
            require(ScreenHousingTextures.texture(choice).equals(fallback)
                    && ScreenHousingTextures.fullTexture(choice).equals(fallback)
                    && ScreenHousingTextures.texture(choice,false).equals(fallback),"retired choice references removed artwork");
            require(mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(choice))
                    !=mc.getTextureMapBlocks().getMissingSprite(),"retired fallback missing from atlas");
            TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setHousingTexture(choice);
            TileEntityAnimatedScreenSelector copy=new TileEntityAnimatedScreenSelector();
            copy.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
            require(copy.getHousingTexture()==choice,"retired saved choice did not survive reload");
        }
        require(retired==16,"unexpected retired texture count");
        System.out.println("[vandorlabs][reprolab] retired-texture-fallback PASS: 16 saved IDs, default atlas artwork and persistence");
        for(int choice=0;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++) {
            require(!ScreenHousingTextures.category(choice).matches("Texture Pack [12]|Hull Plating|Computing|Fuel|Power"),"obsolete category");
            if(ScreenHousingTextures.visible(choice))require(!HousingTextureList.name(choice).matches("T[12] .*"),"texture pack prefix retained");
        }
        for(int choice:new int[]{12,23,24,25,67,77})require(!ScreenHousingTextures.visible(choice),"removed choice visible");
        require(ScreenHousingTextures.category(31).equals("Tech") && ScreenHousingTextures.category(35).equals("Hull")
                && ScreenHousingTextures.category(29).equals("Panels"),"legacy category moves");
        java.util.Map<String,Integer> counts=new java.util.HashMap<>();
        for(int choice=0;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++)if(ScreenHousingTextures.visible(choice))
            counts.merge(ScreenHousingTextures.category(choice),1,Integer::sum);
        require(counts.get("Hull")==21 && counts.get("Panels")==19 && counts.get("Industrial")==14 && counts.get("Tech")==25,"approved Hull/Panels/Industrial/Tech counts");
        HousingTextureList moved=new HousingTextureList(0,0,180,32);
        require(moved.selected()==32 && "Heavy Bulkhead 1".equals(HousingTextureList.name(32)) && "Industrial".equals(ScreenHousingTextures.category(32)),"moved saved choice changed identity");
        try {
            java.lang.reflect.Field expanded=HousingTextureList.class.getDeclaredField("expanded");expanded.setAccessible(true);
            require(((java.util.Set<?>)expanded.get(moved)).contains("Industrial"),"moved material did not expand its new category");
        }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        System.out.println("[vandorlabs][reprolab] industrial-category-runtime PASS Hull=21 Panels=19 Industrial=14 Tech=25; saved material opens new category");
        int amber=ScreenHousingTextures.screenIndex("lights/amber_hex_on");
        for(String finish:new String[]{"pale_grey","industrial_grey","dark_cyan_metallic"})for(String variant:new String[]{"plain","speaker","light_on","light_off"}){
            String id="ceiling/"+finish+"/"+variant;int choice=ScreenHousingTextures.screenIndex(id);require(choice>=ScreenHousingTextures.LEGACY_COUNT,"retained ceiling choice "+id);
            if(variant.equals("plain")||variant.equals("speaker"))require("Panels".equals(ScreenHousingTextures.category(choice)),"ceiling panel category");
            String texture=ScreenHousingTextures.fullTexture(choice);net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(texture);
            require(sprite!=mc.getTextureMapBlocks().getMissingSprite()&&sprite.getIconName().equals(texture)&&sprite.getIconWidth()==64&&sprite.getIconHeight()==64,"native64 ceiling atlas resolution "+id);
            net.minecraft.util.ResourceLocation location=new net.minecraft.util.ResourceLocation(texture);
            try(net.minecraft.client.resources.IResource resource=mc.getResourceManager().getResource(new net.minecraft.util.ResourceLocation(location.getResourceDomain(),"textures/"+location.getResourcePath()+".png"))){
                java.awt.image.BufferedImage image=javax.imageio.ImageIO.read(resource.getInputStream());require(image!=null&&image.getWidth()==64&&image.getHeight()==64,"loaded ceiling PNG preserves native64 "+id);
            }catch(java.io.IOException error){throw new IllegalStateException("Ceiling resource "+id,error);}
        }
        for(int choice:new int[]{64,amber,ScreenHousingTextures.screenIndex("ceiling/pale_grey/light_on"),ScreenHousingTextures.screenIndex("ceiling/industrial_grey/light_on"),ScreenHousingTextures.screenIndex("ceiling/dark_cyan_metallic/light_on")}) {
            TileEntityProgrammableLight tile=new TileEntityProgrammableLight();tile.setFaceTexture(choice);
            for(boolean on:new boolean[]{false,true}) {
                tile.setOn(on);
                String texture=ScreenHousingTextures.texture(tile.getFaceTexture(),tile.isOn());
                require(!texture.equals(ScreenHousingTextures.texture(choice,!on)),"hex artwork did not switch");
                require(mc.getTextureMapBlocks().getAtlasSprite(texture)!=mc.getTextureMapBlocks().getMissingSprite(),"hex atlas artwork missing");
                TileEntityProgrammableLight copy=new TileEntityProgrammableLight();copy.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
                require(copy.getFaceTexture()==choice && copy.isOn()==on,"hex save/load state");
            }
            tile.configure(0,15,false,17,0,com.vandorlabs.persistence.SpaceDoorData.TRIGGER_REDSTONE_ON);
            tile.setChannelSignal(false);require(!tile.isOn(),"unpowered hex light on");
            tile.setChannelSignal(true);require(tile.isOn(),"powered hex light off");
        }
        System.out.println("[vandorlabs][reprolab] texture-menu-runtime PASS saved hidden choices, categories, paired hex light state and persistence");
    }
    private static void checkTurntableGeneral(Minecraft mc) {
        try {
            java.lang.reflect.Field field=HousingTextureList.class.getDeclaredField("options");field.setAccessible(true);
            HousingTextureList[] lists={new HousingTextureList(0,0,180,0),HousingTextureList.forDoors(0,0,0,180,0),
                    HousingTextureList.forGlassMaterials(1,0,0,180,0,-1),HousingTextureList.forCategory("Trapdoors",0,0,180,0),
                    HousingTextureList.forDisplay(0,0,180,0,8,true)};
            for(String id:com.vandorlabs.turntable.TurntableTextures.IDS){
                int choice=com.vandorlabs.turntable.TurntableTextures.materialChoice(id);
                require(ScreenHousingTextures.validChoice(choice) && "Surface".equals(ScreenHousingTextures.category(choice)),"Surface material catalog "+id);
                require(HousingTextureList.generalTexture(choice),"Turntable artwork excluded from general picker "+id);
                for(HousingTextureList list:lists)require(((java.util.Map<?,?>)field.get(list)).containsKey(choice),"Turntable artwork missing from a shared block picker "+id);
                require(mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(choice))!=mc.getTextureMapBlocks().getMissingSprite(),"Turntable artwork missing from atlas "+id);
            }
            System.out.println("[vandorlabs][reprolab] turntable-general-textures PASS 14 designs five shared block pickers and stitched sprites");
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Turntable shared picker inspection",error);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException("Custom material: "+message);}
}
