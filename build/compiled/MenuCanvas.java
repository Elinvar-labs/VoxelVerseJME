package voxelversejme;

import javax.microedition.lcdui.*;

/**
 * Главное меню: фон menu_bg.png + две текстурированные (button.png) кнопки.
 * Навигация: UP/DOWN (или 2/8). Подтверждение: FIRE/OK/5 или тап.
 */
public class MenuCanvas extends Canvas implements CommandListener {

    private static final String BG_PATH = "/voxelversejme/res/menu_bg.png";
    private static final String BTN_PATH = "/voxelversejme/res/button.png";

    private static final int BTN_W = 160;
    private static final int BTN_H = 32;
    private static final int BTN_GAP = 12;

    private static final int BTN_CREATE = 0;
    private static final int BTN_QUIT = 1;
    private static final int BTN_HELP = 2;
    private static final int BTN_COUNT = 3;

    private int bHelpY;

    // Цвета выделения
    private static final int COLOR_SELECTED_BORDER = 0xFFFF00;   // жёлтая рамка
    private static final int COLOR_SELECTED_TEXT = 0xFFFF00;   // жёлтый текст
    private static final int COLOR_NORMAL_TEXT = 0xFFFFFF;

    private final VoxelMIDlet midlet;
    private final Command exitCommand;

    private Image bg;
    private Image button;
    private Image scaledButton;   // кэш отмасштабированной кнопки

    // Геометрия кнопок (обновляется в paint)
    private int bCreateX, bCreateY;
    private int bQuitX, bQuitY;

    // Текущий выбранный пункт
    private int selectedIndex = BTN_CREATE;

    public MenuCanvas(VoxelMIDlet midlet) {
        this.midlet = midlet;
        try {
            setFullScreenMode(true);
        } catch (Throwable ignored) {
        }

        try {
            bg = Image.createImage(BG_PATH);
        } catch (Throwable t) {
            System.err.println("MenuCanvas: no bg: " + t);
        }
        try {
            button = Image.createImage(BTN_PATH);
        } catch (Throwable t) {
            System.err.println("MenuCanvas: no button: " + t);
        }

        exitCommand = new Command("Exit", Command.EXIT, 1);
        addCommand(exitCommand);
        setCommandListener(this);
    }

    // ------------------------------------------------------------------
    //  Отрисовка
    // ------------------------------------------------------------------
    protected void paint(Graphics g) {
        final int w = getWidth();
        final int h = getHeight();

        // --- Фон ---
        if (bg != null) {
            drawStretched(g, bg, 0, 0, w, h);
        } else {
            g.setColor(0x202030);
            g.fillRect(0, 0, w, h);
        }

        // --- Кнопки ---
        final int cx = w / 2;
        final int totalH = BTN_H * 3 + BTN_GAP * 2;
        final int top = (h - totalH) / 2;

        bCreateX = cx - BTN_W / 2;
        bCreateY = top;
        bQuitX = cx - BTN_W / 2;
        bQuitY = top + BTN_H + BTN_GAP;
        bHelpY = top + (BTN_H + BTN_GAP) * 2;

        drawButton(g, bCreateX, bCreateY, "Create World",
                selectedIndex == BTN_CREATE);
        drawButton(g, bQuitX, bQuitY, "Quit",
                selectedIndex == BTN_QUIT);
        drawButton(g, bQuitX, bHelpY, "Help",
                selectedIndex == BTN_HELP);

        drawButton(g, bCreateX, bCreateY, "Create World",
                selectedIndex == BTN_CREATE);
        drawButton(g, bQuitX, bQuitY, "Quit",
                selectedIndex == BTN_QUIT);

        // --- Подсказка снизу ---
        g.setColor(0xC0C0C0);
        g.setFont(Font.getFont(Font.FACE_PROPORTIONAL,
                Font.STYLE_PLAIN, Font.SIZE_SMALL));
        g.drawString("\u2191\u2193  select   \u2022   FIRE  ok",
                w / 2, h - 6, Graphics.HCENTER | Graphics.BOTTOM);
    }

    private void drawButton(Graphics g, int x, int y,
            String label, boolean selected) {
        // Кэшируем масштабированную текстуру один раз
        if (scaledButton == null && button != null) {
            scaledButton = scale(button, BTN_W, BTN_H);
        }

        // --- Фон кнопки ---
        if (scaledButton != null) {
            g.drawImage(scaledButton, x, y, Graphics.LEFT | Graphics.TOP);
        } else {
            g.setColor(selected ? 0x8090A8 : 0x607080);
            g.fillRect(x, y, BTN_W, BTN_H);
        }

        // --- Рамка выделения ---
        if (selected) {
            // Двойная жёлтая рамка — хорошо читается на любой текстуре
            g.setColor(COLOR_SELECTED_BORDER);
            g.drawRect(x - 1, y - 1, BTN_W + 1, BTN_H + 1);
            g.drawRect(x - 2, y - 2, BTN_W + 3, BTN_H + 3);

            // Маркер-стрелка слева
            final int ax = x - 10;
            final int ay = y + BTN_H / 2;
            g.fillTriangle(ax, ay - 5,
                    ax, ay + 5,
                    ax + 6, ay);
        }

        // --- Подпись ---
        g.setColor(selected ? COLOR_SELECTED_TEXT : COLOR_NORMAL_TEXT);
        g.setFont(Font.getFont(Font.FACE_PROPORTIONAL,
                Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        final Font f = g.getFont();
        final int textY = y + (BTN_H - f.getHeight()) / 2 + f.getBaselinePosition();
        g.drawString(label, x + BTN_W / 2, textY,
                Graphics.HCENTER | Graphics.BASELINE);
    }

    // ------------------------------------------------------------------
    //  Утилита: масштабирование Image (RGB) методом ближайшего соседа
    // ------------------------------------------------------------------
    private static Image scale(Image src, int dstW, int dstH) {
        final int sw = src.getWidth();
        final int sh = src.getHeight();
        final int[] srcPix = new int[sw * sh];
        src.getRGB(srcPix, 0, sw, 0, 0, sw, sh);

        final int[] dstPix = new int[dstW * dstH];
        for (int y = 0; y < dstH; y++) {
            final int sy = y * sh / dstH;
            final int srcRow = sy * sw;
            final int dstRow = y * dstW;
            for (int x = 0; x < dstW; x++) {
                dstPix[dstRow + x] = srcPix[srcRow + x * sw / dstW];
            }
        }
        return Image.createRGBImage(dstPix, dstW, dstH, true);
    }

    /**
     * Растягивает изображение на прямоугольник (грубо, поблочно).
     */
    private static void drawStretched(Graphics g, Image img,
            int x, int y, int w, int h) {
        final int sw = img.getWidth();
        final int sh = img.getHeight();
        if (sw == 0 || sh == 0) {
            return;
        }

        if (w <= sw && h <= sh) {
            g.drawRegion(img, (sw - w) / 2, (sh - h) / 2, w, h,
                    0, x, y, Graphics.LEFT | Graphics.TOP);
            return;
        }
        for (int ty = 0; ty < h; ty += sh) {
            for (int tx = 0; tx < w; tx += sw) {
                final int tw = Math.min(sw, w - tx);
                final int th = Math.min(sh, h - ty);
                g.drawRegion(img, 0, 0, tw, th,
                        0, x + tx, y + ty, Graphics.LEFT | Graphics.TOP);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Ввод
    // ------------------------------------------------------------------
    protected void keyPressed(int keyCode) {
        final int action = getGameAction(keyCode);

        // --- Навигация ---
        if (action == UP || keyCode == KEY_NUM2) {
            selectedIndex--;
            if (selectedIndex < 0) {
                selectedIndex = BTN_COUNT - 1;
            }
            repaint();
            return;
        }
        if (action == DOWN || keyCode == KEY_NUM8) {
            selectedIndex++;
            if (selectedIndex >= BTN_COUNT) {
                selectedIndex = 0;
            }
            repaint();
            return;
        }

        // --- Подтверждение ---
        if (keyCode == FIRE || keyCode == KEY_NUM5 || action == FIRE) {
            activate(selectedIndex);
            return;
        }

        // --- Быстрые клавиши ---
        if (keyCode == KEY_NUM1) {
            activate(BTN_CREATE);
        } else if (keyCode == KEY_NUM0) {
            midlet.exitMIDlet();
        }
    }

    protected void pointerPressed(int x, int y) {
        if (hit(x, y, bCreateX, bCreateY)) {
            selectedIndex = BTN_CREATE;
            repaint();
            activate(BTN_CREATE);
        } else if (hit(x, y, bQuitX, bQuitY)) {
            selectedIndex = BTN_QUIT;
            repaint();
            activate(BTN_QUIT);
        } else if (hit(x, y, bQuitX, bHelpY)) {
            selectedIndex = BTN_HELP;
            repaint();
            activate(BTN_HELP);
        }
    }

    protected void pointerMoved(int x, int y) {
        int newSel = selectedIndex;
        if (hit(x, y, bCreateX, bCreateY)) {
            newSel = BTN_CREATE;
        } else if (hit(x, y, bQuitX, bQuitY)) {
            newSel = BTN_QUIT;
        } else if (hit(x, y, bQuitX, bHelpY)) {
            newSel = BTN_HELP;
        }
        if (newSel != selectedIndex) {
            selectedIndex = newSel;
            repaint();
        }
    }

    private void activate(int index) {
        if (index == BTN_CREATE) {
            midlet.showGame();
        } else if (index == BTN_QUIT) {
            midlet.exitMIDlet();
        } else {
            midlet.showHelp();
        }
    }

    private static boolean hit(int px, int py, int rx, int ry) {
        return px >= rx && px < rx + BTN_W
                && py >= ry && py < ry + BTN_H;
    }

    public void commandAction(Command c, Displayable d) {
        if (c == exitCommand) {
            midlet.exitMIDlet();
        }
    }
}
