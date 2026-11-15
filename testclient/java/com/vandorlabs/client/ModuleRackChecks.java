package com.vandorlabs.client;

import com.mojang.authlib.GameProfile;
import com.vandorlabs.CommonProxy;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockModuleRack;
import com.vandorlabs.copier.CopyMaterials;
import com.vandorlabs.copier.CopyWorld;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.rack.ModuleRackGeometry;
import com.vandorlabs.tiles.TileEntityModuleRack;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/** Actual block/tile callbacks in a bounded memory world. The separate live
 * fixture verifies player packets and chunk-upload animation handoff. */
public final class ModuleRackChecks {
    private static final BlockPos AT = new BlockPos(0, 80, 0);
    private static int cases;

    private static final class Player extends EntityPlayer {
        boolean allowed = true;
        Player(NonRenderingChecks.MemoryWorld world) {
            super(world, new GameProfile(new UUID(0, 91), "ModuleRackChecks"));
            capabilities.allowEdit = true;
            setPosition(.5, 80, 2.5);
        }
        @Override public boolean isCreative() { return capabilities.isCreativeMode; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean canPlayerEdit(BlockPos pos, EnumFacing side, ItemStack stack) { return allowed; }
    }

    private static final class VisualProxy extends CommonProxy {
        int changes, initials, removals, previousMask;
        long[] previousSequences;
        @Override public void moduleRackUpdated(TileEntityModuleRack tile, int previous,
                long[] sequences, boolean initial) {
            changes++;
            if (initial) initials++;
            previousMask = previous;
            previousSequences = sequences.clone();
            tile.setRenderMask(tile.getOccupiedMask());
        }
        @Override public void moduleRackRemoved(TileEntityModuleRack tile) { removals++; }
    }

    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        ForgeRegistries.ITEMS.register(ModItems.BLANK_MODULE);
        ForgeRegistries.ITEMS.register(ModItems.AUTOPILOT_MODULE);
        GameRegistry.registerTileEntity(com.vandorlabs.tiles.TileEntityAutopilot.class, new ResourceLocation("vandorlabs:autopilot"));
        GameRegistry.registerTileEntity(TileEntityModuleRack.class,
                new ResourceLocation("vandorlabs:module_rack_checks"));
        BlockModuleRack block = new BlockModuleRack();
        ForgeRegistries.BLOCKS.register(block);
        ForgeRegistries.ITEMS.register(new ItemBlock(block).setRegistryName(block.getRegistryName()));
        CommonProxy before = VandorLabs.proxy;
        try {
            VandorLabs.proxy = null;
            placement(block);
            configuration(block);
            autopilotData();
            autopilotHost();
            mountedModulePersistence();
            autopilotCodec();
            autopilotMigrationData();
            persistence();
            interactions(block);
            dropOwnership(block);
            copierOwnership();
            visualPackets(block);
            replacementPackets(block);
            require(!ITickable.class.isAssignableFrom(TileEntityModuleRack.class), "rack must never register an idle tick");
            TileEntityModuleRack tile = new TileEntityModuleRack();
            require(!tile.shouldRenderInPass(0) && !tile.shouldRenderInPass(1), "no settled TESR passes");
        } finally { VandorLabs.proxy = before; }
        System.out.println("Module Rack PASS cases=" + cases
                + " 16 occupancy states, six-face slot/button callbacks, full item NBT, exact ownership,"
                + " compact appearance packets, duplicate/stale update suppression, no idle tile ticks/TESR");
    }

    private static void placement(BlockModuleRack block) {
        NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(false);
        Player player = new Player(world);
        player.rotationYaw = 0;
        for (float pitch : new float[] {-80, -10, 0, 10, 80}) {
            player.rotationPitch = pitch;
            EnumFacing expected = pitch > 45 ? EnumFacing.UP : pitch < -45 ? EnumFacing.DOWN : EnumFacing.NORTH;
            for (EnumFacing clicked : EnumFacing.values())
                require(block.getStateForPlacement(world, AT, clicked, .5F, .5F, .5F, 0, player)
                        .getValue(BlockModuleRack.FACING) == expected,
                        "rack uses Programmable Light player-view placement: pitch=" + pitch + " clicked=" + clicked);
        }
    }

    private static void configuration(BlockModuleRack block) {
        TileEntityModuleRack rack = new TileEntityModuleRack(); rack.setPos(AT);
        NBTTagCompound original = rack.writeToNBT(new NBTTagCompound());
        require(!original.hasKey("RackHousing") && !original.hasKey("RackEmit"), "new defaults preserve old Rack field absence");
        rack.setEmitsRedstone(true);
        int[] levels = {0, 4, 8, 11, 15};
        require(rack.output.level() == levels[0], "empty enabled output");
        for (int slot = 0; slot < 4; slot++) {
            require(rack.insertModule(slot, module(1, slot), false), "configured Rack insertion");
            require(rack.output.level() == levels[slot + 1], "proportional Rack level " + (slot + 1));
        }
        rack.setEmitsRedstone(false); require(rack.output.level() == 0, "emission switch gates every occupied slot");
        rack.setEmitsRedstone(true); rack.setHousingTexture(0);
        com.vandorlabs.redstone.TransmitterSlots.configure(rack.output, com.vandorlabs.redstone.ChannelList.of(42,43), 32, false);
        rack.getTileData().setString("PrivateTestData", "saved only");
        NBTTagCompound saved = rack.writeToNBT(new NBTTagCompound());
        TileEntityModuleRack restored = new TileEntityModuleRack(); restored.readFromNBT(saved);
        require(restored.getHousingTexture() == 0 && restored.emitsRedstone() && restored.output.level() == 15,
                "housing and output survive save/load");
        require(restored.output.transmissionRange() == 32 && restored.output.getRedstoneChannels().equals(com.vandorlabs.redstone.ChannelList.of(42,43))
                && !com.vandorlabs.redstone.TransmitterSlots.localRedstone(restored), "standard channel/range/local settings survive");
        NBTTagCompound appearance = restored.getUpdateTag();
        require(!appearance.hasKey("Items") && !appearance.hasKey("ForgeCaps") && !appearance.hasKey("ForgeData")
                && !appearance.toString().contains("PrivateTestData"), "appearance excludes inventories, caps and private saved fields");
        TileEntityModuleRack client = new TileEntityModuleRack(); client.handleUpdateTag(appearance);
        require(client.getHousingTexture() == 0 && client.emitsRedstone()
                && client.output.getRedstoneChannels().equals(restored.output.getRedstoneChannels()), "public configuration round trip");
        com.vandorlabs.network.MessageModuleRack message = new com.vandorlabs.network.MessageModuleRack(9, AT, 0, true);
        io.netty.buffer.ByteBuf encoded = io.netty.buffer.Unpooled.buffer();
        try {
            message.toBytes(encoded); require(encoded.readableBytes() == 17, "configuration request is fixed seventeen bytes");
            com.vandorlabs.network.MessageModuleRack decoded = new com.vandorlabs.network.MessageModuleRack();
            io.netty.buffer.ByteBuf copy = encoded.copy();
            try { decoded.fromBytes(copy); } finally { copy.release(); }
            for (int length : new int[] {0, 16, 18}) {
                io.netty.buffer.ByteBuf malformed = io.netty.buffer.Unpooled.buffer().writeZero(length);
                try { boolean rejected = false; try { decoded.fromBytes(malformed); } catch (IllegalArgumentException expected) { rejected = true; }
                    require(rejected, "malformed configuration length refused before reading fields");
                } finally { malformed.release(); }
            }
        } finally { encoded.release(); }
        NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(false);
        TileEntityModuleRack previous = install(world, block, EnumFacing.SOUTH);
        previous.insertModule(0, module(1, 8), false);
        TileEntityModuleRack replacement = new TileEntityModuleRack(); replacement.setPos(AT);
        replacement.readFromNBT(previous.writeToNBT(new NBTTagCompound())); replacement.setWorld(world); world.tiles.put(AT, replacement);
        require(previous.removeModule(0).isEmpty() && previous.drainModules().isEmpty()
                && replacement.getOccupiedMask() == 1, "replaced physical tile cannot release another owner's modules");
        cases++;
    }

    private static void autopilotData() {
        com.vandorlabs.tiles.TileEntityAutopilot controller = new com.vandorlabs.tiles.TileEntityAutopilot();
        controller.setPos(AT); controller.setRedstoneChannels(com.vandorlabs.redstone.ChannelList.of(73,74));
        controller.trigger = 2; controller.markerMeta = 5;
        NBTTagCompound original = controller.writeToNBT(new NBTTagCompound());
        NBTTagCompound caps = new NBTTagCompound(); caps.setString("BlockState", "opaque minecraft:air; do not migrate");
        original.setTag("ForgeCaps", caps.copy());
        NBTTagCompound extra = new NBTTagCompound(); extra.setInteger("FutureSetting", 37); original.setTag("ForeignAutopilotData", extra);
        controller.readFromNBT(original); controller.setStatus("Changed after load");
        NBTTagCompound saved = controller.writeToNBT(new NBTTagCompound());
        require(saved.getCompoundTag("ForgeCaps").equals(caps) && saved.getCompoundTag("ForeignAutopilotData").equals(extra),
                "old Autopilot opaque capabilities and unknown own fields round trip without reinterpretation");
        require(saved.getString("AutopilotStatus").equals("Changed after load"), "legacy preservation does not freeze navigation state");
        NBTTagCompound appearance = controller.getUpdateTag();
        require(!appearance.hasKey("ForgeCaps") && !appearance.hasKey("ForeignAutopilotData") && !appearance.hasKey("ForgeData"),
                "legacy private data stays out of appearance packets");
        ItemStack module = module(1, 17); com.vandorlabs.rack.ModuleAutopilotData.legacy(module, saved);
        com.vandorlabs.rack.AutopilotModuleSettings settings = com.vandorlabs.rack.ModuleAutopilotData.settings(module);
        require(settings.channels.equals(com.vandorlabs.redstone.ChannelList.of(73,74)) && settings.activation == 2 && settings.markerMetadata == 5,
                "module receives the established Autopilot configuration");
        require(!module.getTagCompound().getCompoundTag(com.vandorlabs.rack.ModuleAutopilotData.LEGACY).hasKey("ForgeCaps")
                && module.getTagCompound().getCompoundTag(com.vandorlabs.rack.ModuleAutopilotData.LEGACY).getCompoundTag("ForeignAutopilotData").equals(extra),
                "module backup preserves unknown own data without installing another capability owner");
        com.vandorlabs.rack.LegacyAutopilotData preservation = new com.vandorlabs.rack.LegacyAutopilotData(original,new NBTTagCompound());
        NBTTagCompound current = new NBTTagCompound(), liveCaps = new NBTTagCompound(); liveCaps.setInteger("ActualLiveState", 9); current.setTag("ForgeCaps",liveCaps);
        require(preservation.preserve(current).getCompoundTag("ForgeCaps").equals(liveCaps), "legitimate physical capability changes remain authoritative");
        cases++;
    }

    private static void autopilotHost() {
        TileEntityModuleRack rack=new TileEntityModuleRack();rack.setPos(AT);
        require(com.vandorlabs.rack.ModuleAutopilots.count(rack)==0,"empty Rack is not a navigation controller");
        ItemStack input=new ItemStack(ModItems.AUTOPILOT_MODULE,2);
        com.vandorlabs.rack.AutopilotModuleSettings configured=new com.vandorlabs.rack.AutopilotModuleSettings(
                com.vandorlabs.redstone.ChannelList.of(73,74),1,"minecraft:concrete",5);
        com.vandorlabs.rack.ModuleAutopilotData.configure(input,configured);
        require(rack.insertModule(2,input,false)&&input.getCount()==1,"Autopilot insertion consumes one physical module");
        require(rack.primaryModuleSlot()==2&&rack.moduleKind(2)==2&&rack.autopilotCount()==1
                &&rack.getRedstoneChannels().equals(configured.channels)&&rack.trigger==1&&rack.markerMeta==5,"physical Rack hosts existing navigation receiver fields");
        rack.setChannelSignal(true);require(rack.enabled(),"module receiving edge latches existing navigation journey");
        NBTTagCompound saved=rack.writeToNBT(new NBTTagCompound());
        require(!saved.hasKey("AutopilotEnabled")&&!saved.hasKey("RedstoneChannels"),"module navigation fields stay with the item, not blank Rack top-level legacy fields");
        TileEntityModuleRack restored=new TileEntityModuleRack();restored.readFromNBT(saved);
        require(restored.enabled()&&restored.moduleSettings(2).equals(configured),"module runtime/configuration survive physical host reload");
        long sequence=restored.getInsertionSequence(2);
        require(!restored.configureModule(2,sequence+1,configured),"stale module incarnation refuses configuration");
        require(restored.insertModule(0,new ItemStack(ModItems.AUTOPILOT_MODULE),false),"second physical navigation module retained");
        require(com.vandorlabs.rack.ModuleAutopilots.count(restored)==2&&!restored.enabled(),"multiple modules are counted and cannot silently select a navigation controller");
        require(restored.moduleReceivers().size()==2,"every installed Autopilot has a receiving adapter");
        ItemStack removed=restored.removeModule(2);
        require(com.vandorlabs.rack.ModuleAutopilotData.state(removed).getBoolean("AutopilotEnabled"),"extraction preserves that module's latched runtime");
        ItemStack relocated=removed.copy();com.vandorlabs.rack.ModuleAutopilotData.placed(relocated);
        require(!com.vandorlabs.rack.ModuleAutopilotData.state(relocated).getBoolean("AutopilotEnabled")
                &&com.vandorlabs.rack.ModuleAutopilotData.settings(relocated).equals(configured),"manual relocation preserves choices without starting an old-location journey");
        require(restored.autopilotCount()==1&&restored.primaryModuleSlot()==0,"removal rebinds installed navigation owner");
        NBTTagCompound appearance=rack.getUpdateTag();
        require(appearance.getInteger("ModuleKinds")==2<<(2*2)&&!appearance.hasKey("Items")&&!appearance.hasKey("ForgeCaps")
                &&!appearance.toString().contains("AutopilotEnabled"),"appearance carries only typed module kind and public configuration");
        TileEntityModuleRack client=new TileEntityModuleRack();client.handleUpdateTag(appearance);
        require(client.moduleKind(2)==2&&client.moduleSettings(2).equals(configured),"client module tabs receive public settings without owned items");
        require(new com.vandorlabs.tiles.TileEntityWaypoint().getClass().getSuperclass()==com.vandorlabs.tiles.TileEntityAutopilot.class,"Waypoint retains existing superclass behavior");
        cases++;
    }

    private static void mountedModulePersistence() {
        TileEntityModuleRack rack=new TileEntityModuleRack();rack.setPos(AT);rack.setHousingTexture(0);rack.setEmitsRedstone(true);
        ItemStack input=new ItemStack(ModItems.AUTOPILOT_MODULE);
        NBTTagCompound state=com.vandorlabs.rack.ModuleAutopilotData.state(input);state.setInteger("AutopilotDepartureHeight",37);state.setInteger("AutopilotDepartureMode",2);state.setBoolean("AutopilotDepartureDirect",false);
        NBTTagCompound opaque=new NBTTagCompound();opaque.setString("LimitPolicy","retained");state.setTag("ForeignNavigationLimits",opaque);com.vandorlabs.rack.ModuleAutopilotData.state(input,state);
        NBTTagCompound privateItem=new NBTTagCompound();privateItem.setString("Credential","first-module-only");input.getTagCompound().setTag("OpaqueItem",privateItem);
        require(rack.insertModule(1,input,false),"insert module carrying limits/runtime/opaque state");
        ItemStack other=new ItemStack(ModItems.AUTOPILOT_MODULE);com.vandorlabs.rack.AutopilotModuleSettings independent=new com.vandorlabs.rack.AutopilotModuleSettings(com.vandorlabs.redstone.ChannelList.of(19),1,"minecraft:concrete",3);com.vandorlabs.rack.ModuleAutopilotData.configure(other,independent);
        require(rack.insertModule(0,other,false),"second distinct Autopilot module installed");
        com.vandorlabs.rack.AutopilotModuleSettings edited=new com.vandorlabs.rack.AutopilotModuleSettings(com.vandorlabs.redstone.ChannelList.of(9,73),2,"minecraft:wool",14);
        require(rack.configureModule(1,rack.getInsertionSequence(1),edited),"mounted editor commits directly to physical module");
        ItemStack extracted=rack.removeModule(1); // No tick or Rack serialization between edit and extraction.
        NBTTagCompound owned=com.vandorlabs.rack.ModuleAutopilotData.state(extracted);
        require(com.vandorlabs.rack.ModuleAutopilotData.settings(extracted).equals(edited),"immediate extraction retains route/activation/channels");
        require(owned.getInteger("AutopilotDepartureHeight")==37&&owned.getInteger("AutopilotDepartureMode")==2&&!owned.getBoolean("AutopilotDepartureDirect")&&owned.getCompoundTag("ForeignNavigationLimits").equals(opaque),"module limits and unknown state stay with item");
        require(rack.getHousingTexture()==0&&rack.emitsRedstone(),"rack housing/output independent of module extraction");
        require(rack.moduleSettings(0).equals(independent),"editing/extracting second slot never overwrites other Autopilot settings");
        require(extracted.getTagCompound().getCompoundTag("OpaqueItem").equals(privateItem),"opaque item data stays with edited module");
        TileEntityModuleRack target=new TileEntityModuleRack();target.setPos(AT.up());require(target.insertModule(3,extracted,false),"edited module reinserts into another Rack and slot");
        TileEntityModuleRack restored=new TileEntityModuleRack();restored.readFromNBT(target.writeToNBT(new NBTTagCompound()));
        ItemStack after=restored.removeModule(3);require(com.vandorlabs.rack.ModuleAutopilotData.settings(after).equals(edited),"reinsert/reload/extract preserves edited module choices");
        require(com.vandorlabs.rack.ModuleAutopilotData.state(after).getCompoundTag("ForeignNavigationLimits").equals(opaque),"reinsert/reload preserves module-only opaque limits");
        require(after.getTagCompound().getCompoundTag("OpaqueItem").equals(privateItem),"opaque item data survives module relocation/reload");
        require(restored.getHousingTexture()==-1&&!restored.emitsRedstone(),"module never imports previous Rack housing/output");cases++;
    }

    private static void autopilotCodec() {
        com.vandorlabs.rack.AutopilotModuleSettings config=new com.vandorlabs.rack.AutopilotModuleSettings(
                com.vandorlabs.redstone.ChannelList.of(7,73),2,"minecraft:concrete",4);
        io.netty.buffer.ByteBuf encoded=io.netty.buffer.Unpooled.buffer(),round=io.netty.buffer.Unpooled.buffer();
        try {
            new com.vandorlabs.network.MessageModuleAutopilot(9,AT,2,4,config).toBytes(encoded);
            int bytes=encoded.readableBytes();require(bytes==25+8+"minecraft:concrete".length(),"typed module packet has bounded exact size without item data");
            com.vandorlabs.network.MessageModuleAutopilot decoded=new com.vandorlabs.network.MessageModuleAutopilot();
            io.netty.buffer.ByteBuf input=encoded.copy();try{decoded.fromBytes(input);}finally{input.release();}decoded.toBytes(round);require(encoded.equals(round),"typed module settings round trip byte exactly");
            for(int mutation=0;mutation<5;mutation++) {
                io.netty.buffer.ByteBuf bad=encoded.copy();
                try {
                    if(mutation==0)bad.setByte(12,4);
                    else if(mutation==1)bad.setByte(21,65);
                    else if(mutation==2)bad.setByte(30,3);
                    else if(mutation==3)bad.setByte(31,129);
                    else bad.writeByte(0);
                    boolean refused=false;try{new com.vandorlabs.network.MessageModuleAutopilot().fromBytes(bad);}catch(IllegalArgumentException expected){refused=true;}
                    require(refused,"malformed module slot/channel/activation/marker/trailing data refused before apply "+mutation);
                }finally{bad.release();}
            }
        }finally{encoded.release();round.release();}
        cases++;
    }

    private static void autopilotMigrationData() {
        com.vandorlabs.tiles.TileEntityAutopilot old=new com.vandorlabs.tiles.TileEntityAutopilot();old.setPos(AT);
        old.setRedstoneChannels(com.vandorlabs.redstone.ChannelList.of(73));old.setChannelSignal(true);
        old.departureAt=19;old.departureSeat=AT.west();old.departureOwner=new UUID(11,37);old.markerMeta=6;
        NBTTagCompound raw=old.writeToNBT(new NBTTagCompound());
        NBTTagCompound caps=new NBTTagCompound();caps.setString("Opaque","minecraft:air old palette remains opaque");raw.setTag("ForgeCaps",caps);
        raw.setInteger("FutureSetting",37);raw.setBoolean("RackEmit",true);
        NBTTagCompound original=raw.copy(),converted=com.vandorlabs.rack.LegacyAutopilotMigration.migrated(raw);
        require(raw.equals(original),"migration never mutates old saved/journal preimage");
        require(converted.getString("id").equals("vandorlabs:module_rack")&&!converted.hasKey("RackEmit"),"old/new field collision is archived instead of becoming Rack configuration");
        TileEntityModuleRack host=new TileEntityModuleRack();host.readFromNBT(converted);
        NBTTagCompound saved=host.writeToNBT(new NBTTagCompound());
        require(host.autopilotCount()==1&&host.enabled()&&host.departureAt==19&&host.departureSeat.equals(AT.west())
                &&host.departureOwner.equals(old.departureOwner)&&host.markerMeta==6,"same-position module preserves receiving and departure/navigation state");
        require(saved.getCompoundTag("ForgeCaps").equals(caps)&&saved.getInteger("FutureSetting")==37
                &&saved.getCompoundTag(com.vandorlabs.rack.LegacyAutopilotMigration.BACKUP).equals(original),"new tile identity preserves original opaque capabilities and complete legacy backup");
        host.setEmitsRedstone(true);NBTTagCompound lit=host.writeToNBT(new NBTTagCompound());TileEntityModuleRack reloaded=new TileEntityModuleRack();reloaded.readFromNBT(lit);reloaded.setEmitsRedstone(false);
        require(!reloaded.writeToNBT(new NBTTagCompound()).hasKey("RackEmit"),"legacy preservation never restores removed new host settings");
        require(!host.getUpdateTag().hasKey(com.vandorlabs.rack.LegacyAutopilotMigration.BACKUP)&&!host.getUpdateTag().hasKey("FutureSetting")
                &&!host.getUpdateTag().hasKey("ForgeCaps"),"migration backup remains private on public client appearance");
        NBTTagCompound canonical=saved.copy();canonical.setString("id","vandorlabs:module_rack");
        require(com.vandorlabs.rack.ModuleAutopilots.count(canonical)==1,"saved vehicle role discovery counts inserted module");
        require(com.vandorlabs.rack.ModuleAutopilots.count(original)==1,"previous saved vehicle retains compatible legacy role");
        cases++;
    }

    private static ItemStack module(int count, int id) {
        ItemStack result = new ItemStack(ModItems.BLANK_MODULE, count);
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("OwnerNote", "Module " + id);
        payload.setIntArray("Payload", new int[] {id, 9, 18, 27});
        NBTTagCompound nested = new NBTTagCompound();
        nested.setString("OpaqueThirdPartyData", "keep this byte-for-byte");
        payload.setTag("Nested", nested);
        result.setTagCompound(payload);
        result.setStackDisplayName("Test Module " + id);
        return result;
    }

    private static void persistence() throws Exception {
        for (int mask = 0; mask < 16; mask++) {
            TileEntityModuleRack rack = new TileEntityModuleRack();
            for (int slot = 0; slot < 4; slot++) {
                ItemStack input = module(2, slot);
                if ((mask & (1 << slot)) != 0) {
                    require(rack.insertModule(slot, input, false), "insert " + slot);
                    require(input.getCount() == 1, "survival consumes exactly one");
                    ItemStack expected = module(1, slot);
                    require(ItemStack.areItemStacksEqual(expected, rack.getStackInSlot(slot)), "complete inserted item NBT");
                    ItemStack copy = rack.getStackInSlot(slot);
                    copy.getTagCompound().setString("OwnerNote", "changed copy");
                    copy.setCount(0);
                    require(ItemStack.areItemStacksEqual(expected, rack.getStackInSlot(slot)), "getter aliases owned stack");
                    long revision = rack.getRevision();
                    require(!rack.insertModule(slot, input, false), "occupied slot replaced");
                    require(input.getCount() == 1 && rack.getRevision() == revision, "rejected insertion changed ownership");
                }
            }
            require(rack.getOccupiedMask() == mask && rack.getRenderMask() == mask, "mask " + mask);
            NBTTagCompound saved = rack.writeToNBT(new NBTTagCompound());
            TileEntityModuleRack reloaded = new TileEntityModuleRack();
            reloaded.readFromNBT(saved.copy());
            require(reloaded.getOccupiedMask() == mask && reloaded.getRenderMask() == mask, "reload mask");
            for (int slot = 0; slot < 4; slot++) {
                require(ItemStack.areItemStacksEqual(rack.getStackInSlot(slot), reloaded.getStackInSlot(slot)), "reload stack " + slot);
                require(rack.getInsertionSequence(slot) == reloaded.getInsertionSequence(slot), "reload animation sequence");
            }
            require(saved.equals(reloaded.writeToNBT(new NBTTagCompound())), "NBT round trip");
            NBTTagCompound appearance = rack.getUpdateTag();
            require(!appearance.hasKey("Items") && !appearance.toString().contains("OwnerNote"), "private item NBT in chunk update");
            require(!rack.getUpdatePacket().getNbtCompound().hasKey("Items"), "private items in update packet");
            int drained = reloaded.drainModules().size();
            require(drained == Integer.bitCount(mask) && reloaded.drainModules().isEmpty(), "drain did not transfer ownership once");
            require(reloaded.getOccupiedMask() == 0, "drain mask");
            cases++;
        }
        TileEntityModuleRack rack = new TileEntityModuleRack();
        ItemStack wrong = new ItemStack(Items.STICK, 8);
        require(!rack.insertModule(0, wrong, false) && wrong.getCount() == 8, "wrong item accepted");
        for (int slot : new int[] {-1, 4, Integer.MAX_VALUE}) {
            ItemStack held = module(2, 3);
            require(!rack.insertModule(slot, held, false) && held.getCount() == 2, "invalid slot consumed item");
            require(rack.removeModule(slot).isEmpty() && rack.getStackInSlot(slot).isEmpty(), "invalid slot lookup");
        }
        ItemStack large = module(1, 2);
        large.getTagCompound().setByteArray("LargeOpaquePayload", new byte[65536]);
        require(rack.insertModule(0, large, false), "large NBT insert");
        TileEntityModuleRack small = new TileEntityModuleRack();
        small.insertModule(0, module(1, 2), false);
        require(encoded(rack.getUpdatePacket().getNbtCompound()) == encoded(small.getUpdatePacket().getNbtCompound()), "packet grows with arbitrary item NBT");
        System.out.println("Module Rack appearance packet NBT bytes=" + encoded(rack.getUpdatePacket().getNbtCompound())
                + " with either small or 65536-byte item payload (transport envelope excluded)");
        ItemStack removed = rack.removeModule(0);
        require(removed.getCount() == 1 && removed.getTagCompound().getByteArray("LargeOpaquePayload").length == 65536,
                "removal lost opaque data");
        require(rack.removeModule(0).isEmpty(), "duplicate extraction");
        ItemStack creative = module(2, 4);
        require(rack.insertModule(1, creative, true) && creative.getCount() == 2, "creative insertion policy");
    }

    private static TileEntityModuleRack install(NonRenderingChecks.MemoryWorld world, BlockModuleRack block, EnumFacing facing) {
        world.setBlockState(AT, block.getDefaultState().withProperty(BlockModuleRack.FACING, facing), 3);
        return (TileEntityModuleRack)world.getTileEntity(AT);
    }

    private static float[] hit(EnumFacing facing, int slot, boolean button) {
        float u = (float)((button ? 14.5 : 7) / 16.0);
        float y = (float)(ModuleRackGeometry.slotCenterY(slot) / 16.0);
        switch (facing) {
            case UP: return new float[] {u, 1, 1 - y};
            case DOWN: return new float[] {u, 0, y};
            case SOUTH: return new float[] {u, y, 1};
            case NORTH: return new float[] {1 - u, y, 0};
            case WEST: return new float[] {0, y, u};
            case EAST: return new float[] {1, y, 1 - u};
            default: throw new AssertionError(facing);
        }
    }

    private static boolean activate(BlockModuleRack block, NonRenderingChecks.MemoryWorld world,
            Player player, EnumFacing face, EnumHand hand, float[] hit) {
        return block.onBlockActivated(world, AT, world.getBlockState(AT), player, hand,
                face, hit[0], hit[1], hit[2]);
    }

    private static void interactions(BlockModuleRack block) {
        for (EnumFacing facing : EnumFacing.values()) for (int slot = 0; slot < 4; slot++) {
            NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(false);
            TileEntityModuleRack rack = install(world, block, facing);
            Player player = new Player(world);
            player.rotationPitch = facing == EnumFacing.UP ? 80 : facing == EnumFacing.DOWN ? -80 : 0;
            player.rotationYaw = facing == EnumFacing.NORTH ? 0 : facing == EnumFacing.SOUTH ? 180 : facing == EnumFacing.WEST ? 270 : 90;
            IBlockState placed = block.getStateForPlacement(world, AT, facing, .5F, .5F, .5F, 0, player);
            require(placed.getValue(BlockModuleRack.FACING) == facing, "placement uses player view " + facing);
            require(block.getStateFromMeta(block.getMetaFromState(placed)).equals(placed), "face metadata round trip " + facing);
            player.setHeldItem(EnumHand.MAIN_HAND, module(2, slot));
            float[] hit = hit(facing, slot, false);
            player.allowed = false;
            require(!activate(block, world, player, facing, EnumHand.MAIN_HAND, hit), "permission denial");
            player.allowed = true;
            require(!activate(block, world, player, facing.getOpposite(), EnumHand.MAIN_HAND, hit), "rear insertion");
            require(rack.getOccupiedMask() == 0 && player.getHeldItemMainhand().getCount() == 2, "rejected activation mutated");
            require(activate(block, world, player, facing, EnumHand.MAIN_HAND, hit), "front insertion");
            require(rack.getOccupiedMask() == (1 << slot) && player.getHeldItemMainhand().getCount() == 1, "targeted slot insertion");
            int updates = world.updates;
            activate(block, world, player, facing, EnumHand.MAIN_HAND, hit);
            require(world.updates == updates && player.getHeldItemMainhand().getCount() == 1, "occupied click emitted update/consumed item");
            IExtendedBlockState state = (IExtendedBlockState)block.getExtendedState(world.getBlockState(AT), world, AT);
            require(state.getValue(BlockModuleRack.OCCUPIED_MASK) == 1 << slot
                    && state.getValue(BlockModuleRack.MODULE_MASK) == 1 << slot, "chunk state mask");
            ItemStack picked = block.getPickBlock(world.getBlockState(AT), new RayTraceResult(new Vec3d(AT), facing, AT), world, AT, player);
            require(picked.getTagCompound() == null, "pick block copied installed modules");
            player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            require(activate(block, world, player, facing, EnumHand.MAIN_HAND, hit(facing, slot, true)), "slot button extraction");
            require(rack.getOccupiedMask() == 0 && moduleCount(player) == 1, "button did not return exactly one module");
            cases++;
        }
        NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(false);
        TileEntityModuleRack rack = install(world, block, EnumFacing.SOUTH);
        Player player = new Player(world);
        player.setHeldItem(EnumHand.OFF_HAND, module(2, 0));
        float[] hit = hit(EnumFacing.SOUTH, 0, false);
        require(!activate(block, world, player, EnumFacing.SOUTH, EnumHand.MAIN_HAND, hit), "empty main hand swallows off-hand insertion");
        require(activate(block, world, player, EnumFacing.SOUTH, EnumHand.OFF_HAND, hit), "off-hand insertion");
        require(player.getHeldItemOffhand().getCount() == 1 && rack.getOccupiedMask() == 1, "off-hand count");
        player.setHeldItem(EnumHand.OFF_HAND, ItemStack.EMPTY);
        for (int index = 0; index < player.inventory.mainInventory.size(); index++)
            player.inventory.mainInventory.set(index, new ItemStack(Items.STICK, 64));
        player.inventory.currentItem = 0;
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        // Leave no main-inventory room while using the empty off hand to extract.
        player.inventory.mainInventory.set(0, new ItemStack(Items.STICK, 64));
        activate(block, world, player, EnumFacing.SOUTH, EnumHand.OFF_HAND, hit);
        require(rack.getOccupiedMask() == 1 && rack.getStackInSlot(0).getCount() == 1,
                "full inventory silently discarded installed module");
    }

    private static int moduleCount(Player player) {
        int result = 0;
        for (ItemStack stack : player.inventory.mainInventory)
            if (stack.getItem() == ModItems.BLANK_MODULE) result += stack.getCount();
        return result;
    }

    private static void dropOwnership(BlockModuleRack block) {
        NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(false);
        TileEntityModuleRack rack = install(world, block, EnumFacing.SOUTH);
        for (int slot = 0; slot < 4; slot++) rack.insertModule(slot, module(1, slot), false);
        IBlockState state = world.getBlockState(AT);
        block.breakBlock(world, AT, state);
        int count = 0;
        for (Entity entity : world.loadedEntityList) if (entity instanceof EntityItem) {
            ItemStack dropped = ((EntityItem)entity).getItem();
            require(dropped.getItem() == ModItems.BLANK_MODULE && dropped.hasTagCompound(), "break drop item payload");
            count += dropped.getCount();
        }
        require(count == 4 && rack.drainModules().isEmpty(), "break did not drop exactly four owned modules");
        int entities = world.loadedEntityList.size();
        block.breakBlock(world, AT, state);
        require(world.loadedEntityList.size() == entities, "repeated break duplicated drops");
    }

    private static void visualPackets(BlockModuleRack block) {
        VisualProxy proxy = new VisualProxy();
        VandorLabs.proxy = proxy;
        NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(true);
        TileEntityModuleRack client = install(world, block, EnumFacing.SOUTH);
        TileEntityModuleRack server = new TileEntityModuleRack();
        TileEntityModuleRack fresh = new TileEntityModuleRack();
        fresh.setWorld(world);
        server.insertModule(0, module(1, 0), false);
        fresh.onDataPacket(null, server.getUpdatePacket());
        require(proxy.changes == 1 && proxy.initials == 0, "fresh first insertion was mistaken for a chunk snapshot");
        proxy.changes = 0;
        server = new TileEntityModuleRack();
        client.handleUpdateTag(server.getUpdateTag());
        require(proxy.initials == 1 && client.getOccupiedMask() == 0, "initial snapshot");
        server.insertModule(2, module(1, 2), false);
        SPacketUpdateTileEntity inserted = server.getUpdatePacket();
        client.onDataPacket(null, inserted);
        require(client.getOccupiedMask() == 4 && proxy.changes == 2 && proxy.initials == 1, "live visual transition");
        int renders = world.renderUpdates;
        client.onDataPacket(null, inserted);
        require(proxy.changes == 2 && world.renderUpdates == renders, "duplicate packet restarts/rebuilds");
        client.setRenderMask(client.getRenderMask());
        require(world.renderUpdates == renders, "unchanged render mask rebuilds chunk");
        server.removeModule(2);
        client.onDataPacket(null, server.getUpdatePacket());
        require(client.getOccupiedMask() == 0 && proxy.changes == 3, "remove packet");
        client.onDataPacket(null, inserted);
        require(client.getOccupiedMask() == 0 && proxy.changes == 3, "stale packet rewinds occupancy");
        client.readFromNBT(server.writeToNBT(new NBTTagCompound()));
        require(!client.insertModule(0, module(1, 0), false) && client.removeModule(2).isEmpty()
                && client.drainModules().isEmpty(), "client claimed physical items");
        client.handleUpdateTag(server.getUpdateTag());
        require(proxy.initials == 2, "chunk snapshot must be initial, not insertion replay");
        client.onChunkUnload();
        client.invalidate();
        require(proxy.removals == 2, "client unload cleanup");
        VandorLabs.proxy = null;
    }

    /** Server chunk packets may coalesce an air update and a replacement rack.
     * The client keeps its tile when the final block type is unchanged. */
    private static void replacementPackets(BlockModuleRack block) {
        VisualProxy proxy = new VisualProxy();
        VandorLabs.proxy = proxy;
        NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(true);
        TileEntityModuleRack client = install(world, block, EnumFacing.SOUTH);
        TileEntityModuleRack previous = new TileEntityModuleRack();
        previous.setPos(AT);
        previous.insertModule(0, module(1, 0), false);
        for (int cycle = 0; cycle < 8; cycle++) {
            previous.insertModule(1, module(1, cycle), false);
            previous.removeModule(1);
        }
        client.handleUpdateTag(previous.getUpdateTag());
        TileEntityModuleRack replacement = new TileEntityModuleRack();
        replacement.setPos(AT);
        replacement.insertModule(0, module(1, 100), false);
        require(replacement.getRevision() < client.getRevision()
                && replacement.getInsertionSequence(0) == client.getInsertionSequence(0),
                "replacement fixture needs low revision and a coincident per-slot sequence");
        SPacketUpdateTileEntity packet = replacement.getUpdatePacket();
        client.onDataPacket(null, packet);
        require(client.getRevision() == replacement.getRevision() && client.getOccupiedMask() == 1,
                "replacement rack discarded its first authoritative packet as stale");
        require(proxy.changes == 2 && proxy.initials == 1 && proxy.previousMask == 0
                && proxy.previousSequences[0] == 0,
                "replacement insertion needs a fresh cosmetic baseline even when its sequence matches the old rack");
        int renders = world.renderUpdates;
        client.onDataPacket(null, packet);
        require(proxy.changes == 2 && world.renderUpdates == renders, "replacement duplicate replays motion");
        long incarnation = client.getIncarnation();
        client.onDataPacket(null, previous.getUpdatePacket());
        require(client.getIncarnation() == incarnation && client.getRevision() == replacement.getRevision()
                && client.getOccupiedMask() == 1 && proxy.changes == 2 && world.renderUpdates == renders,
                "late packet from removed rack overwrote its replacement");
        cases++;
        VandorLabs.proxy = null;
    }

    private static void copierOwnership() throws Exception {
        TileEntityModuleRack rack = new TileEntityModuleRack();
        rack.insertModule(0, module(1, 10), false);
        rack.insertModule(1, module(1, 20), false);
        rack.insertModule(2, module(1, 10), false);
        NBTTagCompound saved = rack.writeToNBT(new NBTTagCompound());
        List<CopyMaterials.Need> needs = new ArrayList<>();
        java.lang.reflect.Method inventory = CopyMaterials.class.getDeclaredMethod("inventory",
                net.minecraft.tileentity.TileEntity.class, List.class);
        inventory.setAccessible(true);
        inventory.invoke(null, rack, needs);
        require(needs.size() == 2, "copy cost must preserve distinct module NBT");
        long total = 0;
        for (CopyMaterials.Need need : needs) {
            require(need.exact && need.item.getItem() == ModItems.BLANK_MODULE,
                    "copy module cost must be exact item+NBT");
            if (ItemStack.areItemStackTagsEqual(need.item, module(1, 10))) require(need.count == 2, "equal-NBT module counts");
            else require(ItemStack.areItemStackTagsEqual(need.item, module(1, 20)) && need.count == 1,
                    "distinct-NBT module count");
            total += need.count;
        }
        require(total == 3 && saved.equals(rack.writeToNBT(new NBTTagCompound())), "copy costing mutated source");
        java.lang.reflect.Method empty = CopyWorld.class.getDeclaredMethod("emptyDetached",
                net.minecraft.tileentity.TileEntity.class);
        empty.setAccessible(true);
        empty.invoke(null, rack);
        require(rack.getOccupiedMask() == 0 && rack.drainModules().isEmpty(), "detached copy source retained live inventory");
        TileEntityModuleRack published = new TileEntityModuleRack();
        published.readFromNBT(saved);
        require(published.getOccupiedMask() == 7 && published.drainModules().size() == 3,
                "frozen snapshot lost module ownership");
    }

    private static int encoded(NBTTagCompound tag) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(tag, new DataOutputStream(bytes));
        return bytes.size();
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError("Module Rack: " + message);
    }
}
