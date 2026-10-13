package dev.optic.ui;

import net.minecraft.client.Minecraft;
//? if <26.2 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class FlatButton extends Button {
    public FlatButton(int x, int y, int w, String label, OnPress action) {
        this(x,y,w,Component.literal(label),action);
    }
    public FlatButton(int x,int y,int w,Component label,OnPress action) {
        super(x,y,w,20,label,action,DEFAULT_NARRATION);
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
    }
//? if <26.2 {
    @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
//?} else {
    /*@Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
*///?}
        int x = getX(), y = getY();
        g.fill(x, y, x + width, y + height, isFocused() ? 0xff777777 : 0xff303030);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, isHoveredOrFocused() && active ? 0xff171717 : 0xff0b0b0b);
        var font=Minecraft.getInstance().font;
//? if <26.2 {
        g.drawCenteredString(font, font.plainSubstrByWidth(getMessage().getString(),width-8), x + width / 2, y + 6, active ? 0xffffffff : 0xff888888);
//?} else {
        /*g.centeredText(font, font.plainSubstrByWidth(getMessage().getString(),width-8), x + width / 2, y + 6, active ? 0xffffffff : 0xff888888);
*///?}
    }
}
