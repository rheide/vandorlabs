package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.items.ProgrammableSettings;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

/** Real inventory, persistence, copy, rendering and GUI boundaries in the integrated game. */
final class StorageRuntimeChecks {
    static void require(boolean result, String message) {
        if (!result) throw new IllegalStateException("Storage: " + message);
    }
    static void server(World world, EntityPlayerMP player) {
        BlockPos pos = new BlockPos(18, 24, 6), other = pos.east();
        BlockProgrammableStorage block = (BlockProgrammableStorage)ModBlocks.PROGRAMMABLE_STORAGE;
        world.setBlockState(pos, block.getDefaultState(), 3);
        world.setBlockState(other, block.getDefaultState(), 3);
        TileEntityProgrammableStorage tile = (TileEntityProgrammableStorage)world.getTileEntity(pos);
        TileEntityProgrammableStorage target = (TileEntityProgrammableStorage)world.getTileEntity(other);
        require(tile.getSizeInventory() == 27 && tile.isEmpty(), "27 empty slots");
        require(tile.getHousingTexture() == ScreenHousingTextures.DEFAULT_STORAGE, "default cabinet");
        for (int i = 0; i < 27; i++) tile.setInventorySlotContents(i, new ItemStack(Items.IRON_INGOT, 64));
        require(block.getComparatorInputOverride(world.getBlockState(pos), world, pos) == 15, "full comparator");
        require(tile.getStackInSlot(26).getCount() == 64, "last slot available");
        FaceTextures overrides = new FaceTextures(true, new int[]{-1,1,3,-1,-1,-1});
        tile.setFaceTextures(overrides);
        NBTTagCompound saved = tile.writeToNBT(new NBTTagCompound());
        TileEntityProgrammableStorage loaded = new TileEntityProgrammableStorage();
        loaded.readFromNBT(saved);
        require(loaded.getFaceTextures().equals(overrides), "face override persistence");
        require(loaded.getStackInSlot(26).getCount() == 64, "inventory persistence");
        require(!tile.getUpdateTag().hasKey("Items"), "appearance packet contains inventory");
        require(!block.createConfiguredDrop(tile).getSubCompound("BlockEntityTag").hasKey("Items"), "pick/mining drop duplicates inventory");
        tile.setHousingTexture(ScreenHousingTextures.screenIndex("storage/chest_front"));
        target.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 7));
        NBTTagCompound settings = ProgrammableSettings.capture(world, pos);
        require(ProgrammableSettings.apply(world, other, settings), "Duplifier applies appearance");
        require(target.getHousingTexture() == tile.getHousingTexture() && target.getStackInSlot(0).getCount() == 7
                && target.getStackInSlot(0).getItem() == Items.DIAMOND, "copy preserves target contents");
        require(target.getFaceTextures().equals(overrides), "copy includes face overrides");
        ItemStack configured = ProgrammableSettings.applyToItem(new ItemStack(block), settings);
        require(!configured.isEmpty() && !configured.getSubCompound("BlockEntityTag").hasKey("Items"), "crafted copy excludes inventory");
        loaded.readFromNBT(configured.getSubCompound("BlockEntityTag"));
        require(loaded.getFaceTextures().equals(overrides), "configured item preserves faces");
        tile.setFaceTextures(new FaceTextures(false, new int[]{2,2,2,2,2,2}));
        ProgrammableSettings.apply(world, other, ProgrammableSettings.capture(world, pos));
        require(!target.getFaceTextures().enabled && target.getFaceTextures().choice(1)==1
                && target.getStackInSlot(0).getCount()==7,"disabled copy retains stored faces and contents");
        tile.clear();
        require(block.getComparatorInputOverride(world.getBlockState(pos), world, pos) == 0, "empty comparator");
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            require(handler != null && handler.getSlots() == 27, "automation all sides");
            require(handler.insertItem(0, new ItemStack(Items.GOLD_INGOT, 5), false).isEmpty(), "hopper insertion");
            require(handler.extractItem(0, 5, false).getCount() == 5 && tile.isEmpty(), "hopper extraction");
        }
        tile.setInventorySlotContents(0, new ItemStack(Items.IRON_INGOT, 12));
        net.minecraft.util.NonNullList<ItemStack> backup = net.minecraft.util.NonNullList.withSize(player.inventory.getSizeInventory(), ItemStack.EMPTY);
        for(int i=0;i<backup.size();i++) { backup.set(i,player.inventory.getStackInSlot(i).copy());player.inventory.setInventorySlotContents(i,ItemStack.EMPTY); }
        try {
            ContainerChest chest = new ContainerChest(player.inventory, tile, player);
            require(chest.inventorySlots.size() == 63, "27 storage plus 36 player slots");
            require(chest.transferStackInSlot(player, 0).getCount() == 12 && tile.isEmpty(), "shift-click extraction");
            int playerSlot = -1;
            for (int i = 27; i < 63; i++) if (chest.getSlot(i).getHasStack()) playerSlot = i;
            require(playerSlot >= 27 && chest.transferStackInSlot(player, playerSlot).getCount() == 12
                    && tile.getStackInSlot(0).getCount() == 12, "shift-click insertion");
            chest.onContainerClosed(player);
        } finally { for (int i = 0; i < backup.size(); i++) player.inventory.setInventorySlotContents(i, backup.get(i)); }
        world.setBlockToAir(pos);
        int dropped=0;
        for(net.minecraft.entity.item.EntityItem entity:world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,new net.minecraft.util.math.AxisAlignedBB(pos).grow(1)))
            if(entity.getItem().getItem()==Items.IRON_INGOT){dropped+=entity.getItem().getCount();entity.setDead();}
        require(dropped==12 && tile.isEmpty(),"breaking drops contents exactly once");
        target.clear(); world.setBlockToAir(other);
        System.out.println("[vandorlabs][reprolab] storage-inventory-runtime PASS");
    }
    static void build(World world, int x, int y) {
        if (world.isRemote) return;
        int index = 0;
        for (int choice = 0; choice < ScreenHousingTextures.BUILTIN_COUNT; choice++) {
            if (!"Storage".equals(ScreenHousingTextures.category(choice))) continue;
            BlockPos pos = new BlockPos(x + (index % 5) * 2 - 4, y, -18 + (index / 5) * 3);
            world.setBlockState(pos, ModBlocks.PROGRAMMABLE_STORAGE.getDefaultState(), 3);
            ((TileEntityProgrammableStorage)world.getTileEntity(pos)).setHousingTexture(choice);
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            index++;
        }
        require(index == 10, "ten storage sets");
    }
    static void client(net.minecraft.client.Minecraft mc, int x, int y) {
        int count = 0;
        for(int choice=0;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++) {
            if(!"Storage".equals(ScreenHousingTextures.category(choice))) continue;
            for(EnumFacing face:EnumFacing.values()) {
                String name=ScreenHousingTextures.storageTexture(choice,face);
                net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(name);
                require(sprite!=mc.getTextureMapBlocks().getMissingSprite(), "missing face " + name);
            }
            count++;
        }
        require(count==10,"catalog sets");
        BlockPos pos=new BlockPos(x-4,y,-18);
        require(mc.world.getTileEntity(pos) instanceof TileEntityProgrammableStorage,"client storage tile");
        TileEntityProgrammableStorage tile=(TileEntityProgrammableStorage)mc.world.getTileEntity(pos);
        require(tile.getHousingTexture()==ScreenHousingTextures.DEFAULT_STORAGE,"default synchronization");
        java.util.Set<String> sprites=new java.util.HashSet<>();
        net.minecraft.block.state.IBlockState state=mc.world.getBlockState(pos);
        net.minecraft.client.renderer.block.model.IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);
        for(net.minecraft.client.renderer.block.model.BakedQuad quad:model.getQuads(state.getBlock().getExtendedState(state,mc.world,pos),null,0))sprites.add(quad.getSprite().getIconName());
        require(sprites.size()==3 && sprites.contains("vandorlabs:blocks/storage/cabinet_front")
                && sprites.contains("vandorlabs:blocks/storage/cabinet_top") && sprites.contains("vandorlabs:blocks/storage/cabinet_side"),"three matching world faces");
        int[] sameSet={-1,ScreenHousingTextures.DEFAULT_STORAGE,-1,-1,-1,-1};
        tile.setFaceTextures(new FaceTextures(true,sameSet));
        java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> overridden=model.getQuads(state.getBlock().getExtendedState(state,mc.world,pos),null,0);
        require(overridden.get(0).getSprite().getIconName().endsWith("cabinet_front"),"explicit top override uses selected artwork, not set top");
        tile.setFaceTextures(new FaceTextures(false,sameSet));
        require(model.getQuads(state.getBlock().getExtendedState(state,mc.world,pos),null,0).get(0).getSprite().getIconName().endsWith("cabinet_top"),"disabled override restores cached set top");
        tile.setFaceTextures(FaceTextures.DEFAULT);
        net.minecraft.client.renderer.block.model.IBakedModel item=mc.getRenderItem().getItemModelWithOverrides(new ItemStack(ModBlocks.PROGRAMMABLE_STORAGE),mc.world,mc.player);
        require(item.getParticleTexture().getIconName().equals("vandorlabs:blocks/storage/cabinet_front"),"default inventory material");
        System.out.println("[vandorlabs][reprolab] storage-material-runtime PASS");
    }
    static ItemStack hotbarStack(int slot) {
        if (slot == 0) return new ItemStack(ModBlocks.PROGRAMMABLE_STORAGE);
        TileEntityProgrammableStorage tile = new TileEntityProgrammableStorage();
        tile.setHousingTexture(ScreenHousingTextures.screenIndex(slot == 1 ? "storage/chest_front" : "storage/metal_drawers_front"));
        return ((BlockProgrammableStorage)ModBlocks.PROGRAMMABLE_STORAGE).createConfiguredDrop(tile);
    }
    static void checkHotbar(net.minecraft.client.Minecraft mc) {
        for(int slot=0;slot<3;slot++) {
            ItemStack stack=mc.player.inventory.getStackInSlot(slot);
            net.minecraft.client.renderer.block.model.IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);
            java.util.Set<String> sprites=new java.util.HashSet<>();
            java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads=new java.util.ArrayList<>(model.getQuads(null,null,0));
            for(EnumFacing face:EnumFacing.values())quads.addAll(model.getQuads(null,face,0));
            for(net.minecraft.client.renderer.block.model.BakedQuad quad:quads) {
                require(quad.getSprite()!=mc.getTextureMapBlocks().getMissingSprite(),"missing hotbar face in slot "+slot);
                sprites.add(quad.getSprite().getIconName());
            }
            String set=slot==0?"cabinet":slot==1?"chest":"metal_drawers";
            require(quads.size()==6 && sprites.size()==3 && sprites.contains("vandorlabs:blocks/storage/"+set+"_front")
                && sprites.contains("vandorlabs:blocks/storage/"+set+"_side") && sprites.contains("vandorlabs:blocks/storage/"+set+"_top"),"hotbar uses matching set "+set);
        }
        System.out.println("[vandorlabs][reprolab] storage-hotbar-runtime PASS");
    }

    static void beginFaces(GuiProgrammableWall gui) {
        gui.actionPerformed(new net.minecraft.client.gui.GuiButton(106,0,0,""));
        gui.actionPerformed(new net.minecraft.client.gui.GuiButton(107,0,0,""));
        gui.actionPerformed(new net.minecraft.client.gui.GuiButton(107,0,0,""));
        try {
            java.lang.reflect.Method choose=GuiProgrammableWall.class.getDeclaredMethod("choose",int.class);
            choose.setAccessible(true);choose.invoke(gui,ScreenHousingTextures.DEFAULT_STORAGE);
        } catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    static void checkFaces(net.minecraft.client.Minecraft mc, BlockPos pos, boolean inherited) {
        for(World world:new World[]{mc.world,mc.getIntegratedServer().getWorld(0)}) {
            TileEntityProgrammableStorage tile=(TileEntityProgrammableStorage)world.getTileEntity(pos);
            require(tile.getFaceTextures().enabled && tile.getFaceTextures().choice(1)==(inherited?-1:ScreenHousingTextures.DEFAULT_STORAGE),"face picker synchronized "+inherited);
            if(!world.isRemote)require(tile.getStackInSlot(0).getCount()==32,"face picker preserves inventory");
        }
        System.out.println("[vandorlabs][reprolab] storage-faces-gui PASS " +(inherited?"inherited":"override"));
    }

}
