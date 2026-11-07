package com.vandorlabs.neoforge.programmer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
/** Registry lookup only: canonical acceptance never creates substitute block or entity types. */
final class ProgrammerCanonicalMode {
 static final ResourceLocation ID=ResourceLocation.parse("vandorlabs:data_chip_programmer");
 static boolean enabled(){return Boolean.getBoolean("vandorlabs.programmerCanonicalCheck");}
 static NativeProgrammerBlock block(){var block=BuiltInRegistries.BLOCK.get(ID);if(!(block instanceof NativeProgrammerBlock value))throw new AssertionError("Canonical Programmer not registered");return value;}
 @SuppressWarnings("unchecked")static BlockEntityType<NativeProgrammerEntity> type(){var type=BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ID);if(type==null)throw new AssertionError("Canonical Programmer entity missing");return (BlockEntityType<NativeProgrammerEntity>)type;}
 @SuppressWarnings("unchecked")static MenuType<NativeProgrammerMenu> menu(){var type=BuiltInRegistries.MENU.get(ID);if(type==null)throw new AssertionError("Canonical Programmer menu missing");return (MenuType<NativeProgrammerMenu>)type;}
 static void validate(){if(!enabled())return;if(!(BuiltInRegistries.ITEM.get(ID) instanceof NativeProgrammerItem item)||item.getBlock()!=block()||!type().isValid(block().defaultBlockState())||BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("vandorlabs:programmer_host_fixture")))throw new AssertionError("Canonical Programmer ownership/type/item identity or private fixture registered");}
 private ProgrammerCanonicalMode(){}
}
