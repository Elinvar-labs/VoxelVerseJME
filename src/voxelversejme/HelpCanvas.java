package voxelversejme;

import javax.microedition.lcdui.*;

/**
 * Экран справки: управление, геймплей, авторы.
 * Прокрутка: UP/DOWN, 2/8, drag пальцем. Возврат: FIRE/Back/тап.
 *
 * Возврат идёт через midlet.closeHelp() — если пришли из игры,
 * вернёмся в неё; если из меню — вернёмся в меню.
 */
public class HelpCanvas extends Canvas implements CommandListener {

    private static final String[] LINES = {
        "=== CONTROLS ===",
        "",
        "2 / UP     - move forward",
        "8 / DOWN   - move backward",
        "4 / LEFT   - strafe left",
        "6 / RIGHT  - strafe right",
        "1          - jump",
        "",
        "Arrows     - look around",
        "            (pitch / yaw)",
        "",
        "FIRE / 5   - break block",
        "             (or place in PLACE mode)",
        "",
        "#          - toggle PLACE / BREAK",
        "             (in PLACE it also",
        "              switches block type)",
        "*          - cycle block type",
        "",
        "Menu (soft) - back to main menu",
        "",
        "=== GAMEPLAY ===",
        "",
        "VoxelVerse is a tiny voxel sandbox.",
        "Walk around a 32x32x32 world,",
        "break blocks and place new ones.",
        "Mind gravity - you cannot fly,",
        "but you can jump over a block.",
        "",
        "Worlds are regenerated each time",
        "you press 'Create World'.",
        "",
        "=== CREDITS ===",
        "",
        "Code, art direction ....... Elinvar-labs",
        "AI assistant, code review . DeepSeek",
        "",
        "Built on JSR-184 (M3G),",
        "MIDP 2.0, CLDC 1.1.",
        "",
        "Thanks for playing!",
        "",
        ""
    };

    private final VoxelMIDlet midlet;
    private final Command     backCommand;

    private int scrollY      = 0;
    private int lineH        = 12;
    private int visibleLines = 10;
    private int totalHeight  = 0;
    private int viewHeight   = 0;

    private Font font;
    private int  fontHeight;

    // Для drag-прокрутки
    private int dragStartY      = -1;
    private int dragStartScroll = 0;

    public HelpCanvas(VoxelMIDlet midlet) {
        this.midlet = midlet;
        try { setFullScreenMode(true); } catch (Throwable ignored) {}

        backCommand = new Command("Back", Command.BACK, 1);
        addCommand(backCommand);
        setCommandListener(this);
    }

    // ------------------------------------------------------------------
    //  Отрисовка
    // ------------------------------------------------------------------
    protected void paint(Graphics g) {
        final int w = getWidth();
        final int h = getHeight();

        g.setColor(0x101018);
        g.fillRect(0, 0, w, h);

        if (font == null) {
            font = Font.getFont(Font.FACE_MONOSPACE,
                    Font.STYLE_PLAIN, Font.SIZE_SMALL);
            fontHeight = font.getHeight();
            lineH      = fontHeight;
        }
        g.setFont(font);

        final int padTop    = 6;
        final int padBottom = 6;
        viewHeight   = h - padTop - padBottom;
        totalHeight  = LINES.length * lineH;
        visibleLines = viewHeight / lineH;

        final int maxScroll = Math.max(0, totalHeight - viewHeight);
        if (scrollY < 0)         scrollY = 0;
        if (scrollY > maxScroll) scrollY = maxScroll;

        final int firstLine = scrollY / lineH;
        final int lastLine  = Math.min(LINES.length, firstLine + visibleLines + 2);

        g.setColor(0xE0E0E0);
        for (int i = firstLine; i < lastLine; i++) {
            final int y = padTop + i * lineH - scrollY;
            if (y + lineH < 0 || y > h) continue;
            g.drawString(LINES[i], 4, y, Graphics.LEFT | Graphics.TOP);
        }

        // Индикатор прокрутки
        if (totalHeight > viewHeight) {
            final int trackX = w - 3;
            final int trackY = padTop;
            final int trackH = viewHeight;
            final int thumbH = Math.max(8, trackH * viewHeight / totalHeight);
            final int thumbY = trackY
                    + (trackH - thumbH) * scrollY
                      / Math.max(1, totalHeight - viewHeight);

            g.setColor(0x303040);
            g.fillRect(trackX - 1, trackY, 2, trackH);
            g.setColor(0x8080C0);
            g.fillRect(trackX - 1, thumbY, 2, thumbH);
        }

        // Подсказка
        g.setColor(0x808080);
        g.setFont(Font.getFont(Font.FACE_PROPORTIONAL,
                Font.STYLE_PLAIN, Font.SIZE_SMALL));
        g.drawString("Back (soft key) to return",
                w / 2, h - 2, Graphics.HCENTER | Graphics.BOTTOM);
    }

    // ------------------------------------------------------------------
    //  Ввод
    // ------------------------------------------------------------------
    protected void keyPressed(int keyCode) {
        final int action = getGameAction(keyCode);
        final int step   = lineH * 2;

        if (action == UP || keyCode == KEY_NUM2) {
            scrollY -= step;
            repaint();
            return;
        }
        if (action == DOWN || keyCode == KEY_NUM8) {
            scrollY += step;
            repaint();
            return;
        }
        if (keyCode == FIRE || keyCode == KEY_NUM5 || action == FIRE) {
            midlet.closeHelp();
            return;
        }
        if (keyCode == KEY_NUM0) {
            midlet.closeHelp();
        }
    }

    protected void pointerPressed(int x, int y) {
        dragStartY      = y;
        dragStartScroll = scrollY;
    }

    protected void pointerDragged(int x, int y) {
        if (dragStartY < 0) return;
        final int dy = dragStartY - y;
        scrollY = dragStartScroll + dy * 2;
        repaint();
    }

    protected void pointerReleased(int x, int y) {
        if (dragStartY >= 0 && Math.abs(y - dragStartY) < 6) {
            midlet.closeHelp();
        }
        dragStartY = -1;
    }

    // ------------------------------------------------------------------
    //  Команды
    // ------------------------------------------------------------------
    public void commandAction(Command c, Displayable d) {
        if (c == backCommand) midlet.closeHelp();
    }
}