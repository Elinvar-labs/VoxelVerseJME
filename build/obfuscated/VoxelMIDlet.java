package voxelversejme;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

/**
 * Точка входа MIDlet-приложения. CLDC 1.1 / MIDP 2.0 + JSR-184.
 *
 * Управляет переходами между экранами:
 *   Logo -> Menu -> Game / Help
 *
 * Флаг returnToGameAfterHelp позволяет вернуться в игру из Help
 * без пересоздания мира и без повторного newWorld().
 */
public class VoxelMIDlet extends MIDlet {

    private Display     display;
    private LogoCanvas  logoCanvas;
    private MenuCanvas  menuCanvas;
    private GameCanvas  gameCanvas;
    private HelpCanvas  helpCanvas;

    private boolean splashShown           = false;
    private boolean inGame                = false;
    private boolean returnToGameAfterHelp = false;

    // ------------------------------------------------------------------
    //  Жизненный цикл MIDlet
    // ------------------------------------------------------------------
    public void startApp() {
        if (display == null) display = Display.getDisplay(this);

        if (!splashShown) {
            splashShown = true;
            logoCanvas = new LogoCanvas(this);
            menuCanvas = new MenuCanvas(this);
            gameCanvas = new GameCanvas(this);
            helpCanvas = new HelpCanvas(this);
            display.setCurrent(logoCanvas);
            logoCanvas.start();
        } else if (inGame) {
            display.setCurrent(gameCanvas);
            gameCanvas.start();
        } else {
            display.setCurrent(menuCanvas);
        }
    }

    public void pauseApp() {
        if (logoCanvas != null) logoCanvas.stop();
        if (gameCanvas != null && inGame) gameCanvas.pause();
    }

    public void destroyApp(boolean unconditional) {
        if (logoCanvas != null) logoCanvas.stop();
        if (gameCanvas != null) {
            gameCanvas.stop();
            gameCanvas.releaseMesh();
        }
        display = null;
    }

    public void exitMIDlet() {
        destroyApp(true);
        notifyDestroyed();
    }

    // ------------------------------------------------------------------
    //  Переходы между экранами
    // ------------------------------------------------------------------
    public void showMenu() {
        returnToGameAfterHelp = false;
        inGame = false;
        if (logoCanvas != null) logoCanvas.stop();
        if (gameCanvas != null) {
            gameCanvas.stop();
            gameCanvas.releaseMesh();
        }
        display.setCurrent(menuCanvas);
    }

    public void showGame() {
        returnToGameAfterHelp = false;
        if (logoCanvas != null) logoCanvas.stop();
        inGame = true;
        gameCanvas.newWorld();
        display.setCurrent(gameCanvas);
        gameCanvas.start();
    }

    /** Открыть справку. Помним, откуда пришли — из игры или из меню. */
    public void showHelp() {
        returnToGameAfterHelp = inGame;
        if (inGame && gameCanvas != null) gameCanvas.pause();
        inGame = false;
        display.setCurrent(helpCanvas);
    }

    /** Закрыть справку — вернуться туда, откуда пришли. */
    public void closeHelp() {
        if (returnToGameAfterHelp) {
            returnToGameAfterHelp = false;
            returnToGame();
        } else {
            showMenu();
        }
    }

    /** Возврат в уже сгенерированный мир без пересоздания. */
    public void returnToGame() {
        inGame = true;
        display.setCurrent(gameCanvas);
        gameCanvas.start();
    }

    Display getDisplay() { return display; }
}