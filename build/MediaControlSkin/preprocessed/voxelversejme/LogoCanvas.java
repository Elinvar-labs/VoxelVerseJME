package voxelversejme;

import javax.microedition.lcdui.*;

/**
 * Заставка: показывает logo.png ~2 секунды, затем переходит в меню.
 */
public class LogoCanvas extends Canvas implements Runnable {

    private static final String LOGO_PATH = "/voxelversejme/res/logo.png";
    private static final long SPLASH_MS = 2000L;

    private final VoxelMIDlet midlet;
    private Image logo;
    private volatile boolean running;
    private Thread thread;

    public LogoCanvas(VoxelMIDlet midlet) {
        this.midlet = midlet;
        try {
            setFullScreenMode(true);
        } catch (Throwable ignored) {
        }

        try {
            logo = Image.createImage(LOGO_PATH);
        } catch (Throwable t) {
            System.err.println("LogoCanvas: cannot load " + LOGO_PATH + ": " + t);
        }
    }

    public synchronized void start() {
        if (thread == null) {
            running = true;
            thread = new Thread(this);
            thread.start();
        }
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    public void run() {
        try {
            Thread.sleep(SPLASH_MS);
        } catch (InterruptedException ignored) {
            return;
        }
        if (running) {
            running = false;          // <-- гасим себя
            midlet.showMenu();
        }
    }

    protected void paint(Graphics g) {
        final int w = getWidth();
        final int h = getHeight();

        g.setColor(0x000000);
        g.fillRect(0, 0, w, h);

        if (logo != null) {
            g.drawImage(logo, w / 2, h / 2,
                    Graphics.HCENTER | Graphics.VCENTER);
        } else {
            g.setColor(0xFFFFFF);
            g.setFont(Font.getFont(Font.FACE_PROPORTIONAL,
                    Font.STYLE_BOLD, Font.SIZE_LARGE));
            g.drawString("VoxelVerse", w / 2, h / 2,
                    Graphics.HCENTER | Graphics.BASELINE);
        }
    }

    protected void keyPressed(int keyCode) {
        if (running) {
            running = false;          // <-- иначе таймер сработает поверх игры
            midlet.showMenu();
        }
    }

    protected void pointerPressed(int x, int y) {
        if (running) {
            running = false;
            midlet.showMenu();
        }
    }
}
