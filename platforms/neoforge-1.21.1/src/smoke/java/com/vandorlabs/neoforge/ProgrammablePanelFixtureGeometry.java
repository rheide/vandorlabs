package com.vandorlabs.neoforge;
import com.google.gson.*;
import com.vandorlabs.neoforge.shape.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
/** Common actual-Forge geometry assertions for registered opaque and transparent panels. */
final class ProgrammablePanelFixtureGeometry {
 private static AABB box(JsonArray a){return new AABB(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble(),a.get(3).getAsDouble(),a.get(4).getAsDouble(),a.get(5).getAsDouble());}
 static void check(ServerLevel level,BlockPos pos,BlockState state,NativeWallEntity host,JsonObject c,int profile,java.util.function.BiConsumer<Boolean,String> check){
var geometry=host.wallAppearance().geometry();check.accept(geometry.selectionBounds().equals(box(c.getAsJsonArray("selection"))),"actual Forge broad selection bounds");var expected=Shapes.empty();for(var a:c.getAsJsonArray("boxes"))expected=Shapes.joinUnoptimized(expected,Shapes.create(box(a.getAsJsonArray())),BooleanOp.OR);var collision=state.getCollisionShape(level,pos);check.accept(!Shapes.joinIsNotEmpty(expected,collision,BooleanOp.NOT_SAME),"mapped detailed collision occupancy");check.accept(!Shapes.joinIsNotEmpty(collision,state.getShape(level,pos),BooleanOp.NOT_SAME),"detailed player ray shape retained");if(profile==6){var j=((NativeDiagonalWallGeometry.Geometry)geometry).pose().join();check.accept(j!=null&&Boolean.TRUE.equals(j.frontRight())&&Boolean.FALSE.equals(j.backRight())&&!j.straightLeft()&&j.straightRight(),"actual installed solid-cross topology matches Forge profile");}var rendered=host.renderBounds();var selection=geometry.selectionBounds().move(pos);check.accept(rendered.minX<=selection.minX&&rendered.minY<=selection.minY&&rendered.minZ<=selection.minZ&&rendered.maxX>=selection.maxX&&rendered.maxY>=selection.maxY&&rendered.maxZ>=selection.maxZ,"render box encloses displayed selection");for(var a:c.getAsJsonArray("boxes")){var b=box(a.getAsJsonArray()).move(pos);check.accept(rendered.minX<=b.minX&&rendered.minY<=b.minY&&rendered.minZ<=b.minZ&&rendered.maxX>=b.maxX&&rendered.maxY>=b.maxY&&rendered.maxZ>=b.maxZ,"render box encloses physical overhang");}
    
 }
}
