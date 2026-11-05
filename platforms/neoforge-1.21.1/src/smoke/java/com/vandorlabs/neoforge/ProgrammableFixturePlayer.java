package com.vandorlabs.neoforge;

/** Real menu factory/opening-data support shared by focused programmable registration checks. */
final class ProgrammableFixturePlayer extends net.neoforged.neoforge.common.util.FakePlayer {
 private final java.util.function.BiConsumer<Boolean,String> check;
 private int window;
 ProgrammableFixturePlayer(net.minecraft.server.level.ServerLevel level,java.util.function.BiConsumer<Boolean,String> check){
  super(level,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("3960089f-955e-41e5-881a-34ded4e8bc16"),"ProgrammableFixture"));this.check=check;
 }
 @Override public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider,java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> writer){
  containerMenu=provider.createMenu(++window,getInventory(),this);
  check.accept(containerMenu instanceof ProgrammableMenu&&containerMenu.getType()==VandorLabsBootstrap.PROGRAMMABLE_MENU.get(),"same dedicated menu type");
  var bytes=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),serverLevel().registryAccess());
  try{writer.accept(bytes);check.accept(bytes.readBlockPos().equals(((ProgrammableMenu)containerMenu).tile.getBlockPos())&&!bytes.isReadable(),"exact menu opening position");}finally{bytes.release();}
  return java.util.OptionalInt.of(window);
 }
}
