package dev.optic.ui;

import dev.optic.OpticClient;
//? if <26.2 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Vanilla button chrome with pixel-aligned Optic artwork. */
public final class RecorderIconButton extends Button {
    public enum Icon { RECORD, PAUSE, SETTINGS, LIBRARY }
    private final Icon icon;
    private String lastLabel = "";
    private static final String[] EYE = {
        "............", "....####....", "..########..", ".###oooo###.",
        "###oo##oo###", ".###oooo###.", "..########..", "....####...."
    };
    private static final String[] COG = {
        ".....##.....", "..##.##.##..", ".##########.", ".###....###.",
        "..##....##..", "###......###", "###......###", "..##....##..",
        ".###....###.", ".##########.", "..##.##.##..", ".....##....."
    };
    private static final String[] LIBRARY = {
        "............", ".####.......", ".##########.", ".#........#.",
        ".#...#....#.", ".#...##...#.", ".#...###..#.", ".#...##...#.",
        ".#...#....#.", ".#........#.", ".##########.", "............"
    };
    private static final String[] PAUSE = {
        "###..###", "###..###", "###..###", "###..###",
        "###..###", "###..###", "###..###", "###..###"
    };
    private static final String[] PLAY = {
        "#.....", "##....", "####..", "######",
        "######", "####..", "##....", "#....."
    };
    public RecorderIconButton(Icon icon, OnPress action) {
        super(0, 0, 20, 20, Component.empty(), action, DEFAULT_NARRATION);
        this.icon = icon;
        updateLabel(Component.translatable(switch(icon) { case RECORD -> "optic.record.start"; case PAUSE -> "optic.record.pause"; case SETTINGS -> "optic.settings"; case LIBRARY -> "optic.library"; }).getString());
    }
    public void updateLabel(String label) {
        if (lastLabel.equals(label)) return;
        lastLabel = label;
        setMessage(Component.literal(label));
        setTooltip(Tooltip.create(getMessage()));
    }
//? if <26.2 {
    @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
        renderDefaultSprite(g);
//?} else {
    /*@Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        extractDefaultSprite(g);
*///?}
        String[] pixels = switch(icon) { case RECORD -> EYE; case PAUSE -> OpticClient.manager.paused()?PLAY:PAUSE; case SETTINGS -> COG; case LIBRARY -> LIBRARY; };
        int x = getX() + 4, y = getY() + (icon == Icon.RECORD ? 5 : 4);
        if(icon==Icon.PAUSE) {
            x=getX()+(getWidth()-pixels[0].length())/2;
            y=getY()+(getHeight()-pixels.length)/2;
        }
        draw(g, pixels, x + 1, y + 1, 0xff292929, true);
        draw(g, pixels, x, y, active ? 0xffeeeeee : 0xff999999, false);
        if (icon == Icon.RECORD) {
            int bx = getX() + 13, by = getY() + 3;
            g.fill(bx - 1, by - 1, bx + 5, by + 5, 0xff303030);
            int red = active ? 0xffef4444 : 0xff995555;
            if (OpticClient.manager.fullRecording()) g.fill(bx, by, bx + 4, by + 4, red);
            else { g.fill(bx + 1, by, bx + 3, by + 4, red); g.fill(bx, by + 1, bx + 4, by + 3, red); }
        }
    }
//? if <26.2 {
    private static void draw(GuiGraphics g, String[] pixels, int x, int y, int color, boolean shadow) {
//?} else {
    /*private static void draw(GuiGraphicsExtractor g, String[] pixels, int x, int y, int color, boolean shadow) {
*///?}
        for (int row = 0; row < pixels.length; row++) {
            String line = pixels[row];
            for (int col = 0; col < line.length();) {
                char pixel = line.charAt(col);
                int end = col + 1;
                while (end < line.length() && line.charAt(end) == pixel) end++;
                if (pixel != '.') g.fill(x + col, y + row, x + end, y + row + 1, pixel == 'o' && !shadow ? 0xff333333 : color);
                col = end;
            }
        }
    }
}
