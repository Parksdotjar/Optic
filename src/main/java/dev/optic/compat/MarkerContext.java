package dev.optic.compat;
import dev.optic.markers.Marker;
import net.minecraft.client.Minecraft;

/** Game-thread snapshot only, opt-in; never stores addresses, UUIDs or other players. */
public final class MarkerContext {
    public static Marker.Context snapshot(boolean enabled) {
        var mc=Minecraft.getInstance(); if(!enabled || mc.player==null || mc.level==null) return null;
        var p=mc.player.blockPosition();
        String label=mc.getSingleplayerServer()!=null?mc.getSingleplayerServer().getWorldData().getLevelName():mc.getCurrentServer()!=null?mc.getCurrentServer().name:"Multiplayer";
        if(label.length()>128) label=label.substring(0,128);
        return new Marker.Context(p.getX(),p.getY(),p.getZ(),mc.level.dimension().identifier().toString(),label);
    }
}
