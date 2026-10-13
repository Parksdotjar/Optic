package dev.optic.ui;

import dev.optic.OpticClient;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Vanilla-sized shortcuts anchored to actual menu rows, with collision-aware relocation. */
public final class PauseControls {
    public static void register() {
        ScreenEvents.AFTER_INIT.register((mc,screen,width,height) -> {
            boolean paused=screen instanceof PauseScreen;
            if(!paused && !(screen instanceof TitleScreen)) return;
            var record=paused?new RecorderIconButton(RecorderIconButton.Icon.RECORD,b -> OpticClient.manager.toggle()):null;
            var pause=paused?new RecorderIconButton(RecorderIconButton.Icon.PAUSE,b -> OpticClient.manager.togglePause()):null;
            if(pause!=null) { pause.visible=false; pause.active=false; }
//? if <26.2 {
            var settings=new RecorderIconButton(RecorderIconButton.Icon.SETTINGS,b -> mc.setScreen(new SettingsScreen(screen)));
            var library=new RecorderIconButton(RecorderIconButton.Icon.LIBRARY,b -> mc.setScreen(new LibraryScreen(screen)));
//?} else {
            /*var settings=new RecorderIconButton(RecorderIconButton.Icon.SETTINGS,b -> mc.gui.setScreen(new SettingsScreen(screen)));
            var library=new RecorderIconButton(RecorderIconButton.Icon.LIBRARY,b -> mc.gui.setScreen(new LibraryScreen(screen)));
*///?}
            RecorderIconButton[] stack=paused?new RecorderIconButton[]{record,pause,settings,library}:new RecorderIconButton[]{settings,library};
//? if <26.2 {
            for(var button:stack) Screens.getButtons(screen).add(button);
//?} else {
            /*for(var button:stack) Screens.getWidgets(screen).add(button);
*///?}
            int[] previous={Integer.MIN_VALUE};
//? if <26.2 {
            ScreenEvents.beforeRender(screen).register((s,g,mx,my,dt) -> {
//?} else {
            /*ScreenEvents.beforeExtract(screen).register((s,g,mx,my,dt) -> {
*///?}
                boolean showPause=paused && OpticClient.manager.fullRecording();
                int signature=31*(31*s.width+s.height)+(showPause?1:0);
                AbstractWidget anchor=null,feedback=null,options=null;
                for(var child:s.children()) if(child instanceof AbstractWidget w && !ours(w,stack)) {
                    signature=31*signature+w.getX(); signature=31*signature+w.getY(); signature=31*signature+w.getWidth(); signature=31*signature+w.getHeight(); signature=31*signature+(w.visible?1:0);
                    if(w.visible && w.getMessage().getContents() instanceof TranslatableContents text) {
                        String key=text.getKey();
                        if(key.equals(paused?"gui.advancements":"menu.singleplayer")) anchor=w;
                        if(key.equals("menu.sendFeedback") || key.equals("menu.feedback")) feedback=w;
                        if(key.equals("menu.options")) options=w;
                    }
                }
                if(signature!=previous[0]) {
                    previous[0]=signature;
                    int px=anchor==null?s.width/2-124:anchor.getX()-24;
                    int py=anchor==null?s.height/4+48:anchor.getY();
                    int settingsY=feedback!=null && feedback.getY()>py?feedback.getY()-py:24;
                    int libraryY=options!=null && options.getY()>py+24?options.getY()-py:48;
                    int[] dx=paused?new int[]{0,-24,0,0}:new int[]{0,0};
                    int[] dy=paused?new int[]{0,0,settingsY,libraryY}:new int[]{0,24};
                    int bx=-1,by=-1,score=Integer.MAX_VALUE;
                    if(clear(s,px,py,dx,dy,stack,showPause)) { bx=px; by=py; }
                    else for(int y=4;y<=s.height-24;y++) for(int x=showPause?28:4;x<=s.width-24;x++) {
                        int distance=Math.abs(x-px)+Math.abs(y-py);
                        if(distance<score && clear(s,x,y,dx,dy,stack,showPause)) { bx=x; by=y; score=distance; }
                    }
                    for(int i=0;i<stack.length;i++) { stack[i].visible=bx>=0; if(bx>=0) { stack[i].setX(bx+dx[i]); stack[i].setY(by+dy[i]); } }
                }
                if(record!=null) {
                    var state=OpticClient.manager.state();
                    record.updateLabel(switch(state) {
                        case IDLE -> net.minecraft.network.chat.Component.translatable("optic.record.start").getString();
                        case RECORDING -> OpticClient.manager.fullRecording()?net.minecraft.network.chat.Component.translatable("optic.record.stop",OpticClient.manager.elapsed()).getString():net.minecraft.network.chat.Component.translatable("optic.record.replay").getString();
                        case PREPARING -> net.minecraft.network.chat.Component.translatable("optic.record.preparing").getString();
                        case STOPPING -> net.minecraft.network.chat.Component.translatable("optic.record.saving").getString();
                    });
                    record.active=state!=dev.optic.recording.RecordingManager.State.STOPPING;
                    pause.updateLabel(net.minecraft.network.chat.Component.translatable(OpticClient.manager.paused()?"optic.record.resume":"optic.record.pause").getString());
                    pause.active=OpticClient.manager.fullRecording();
                    pause.visible=record.visible && showPause;
                    if(!pause.visible && s.getFocused()==pause) s.setFocused(record);
                }
            });
        });
    }
    private static boolean ours(AbstractWidget widget,RecorderIconButton[] stack) { for(var button:stack) if(widget==button) return true; return false; }
    private static boolean clear(Screen screen,int x,int y,int[] dx,int[] dy,RecorderIconButton[] stack,boolean showPause) {
        for(int i=0;i<stack.length;i++) {
            if(stack.length==4 && i==1 && !showPause) continue;
            if(x+dx[i]<4 || y+dy[i]<4 || x+dx[i]+20>screen.width-4 || y+dy[i]+20>screen.height-4) return false;
        }
        for(var child:screen.children()) if(child instanceof AbstractWidget w && w.visible && !ours(w,stack)) {
            for(int i=0;i<stack.length;i++) {
                if(stack.length==4 && i==1 && !showPause) continue;
                if(x+dx[i]<w.getRight()+2 && x+dx[i]+20>w.getX()-2 && y+dy[i]<w.getBottom()+2 && y+dy[i]+20>w.getY()-2) return false;
            }
        }
        return true;
    }
}
