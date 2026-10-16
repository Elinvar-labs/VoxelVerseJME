package voxelversejme;

import javax.microedition.lcdui.*;
import javax.microedition.m3g.*;

/**
 * Игровой экран.
 *
 * Реализовано:
 *   - воксельный мир 32x32x32 с простым генератором;
 *   - гравитация и прыжок игрока;
 *   - горизонтальные коллизии и жёсткие границы мира;
 *   - raycast (DDA) для ломания/установки блоков;
 *   - crosshair;
 *   - цикл дня и ночи через цвет Background;
 *   - команды Menu и Help.
 */
public class GameCanvas extends Canvas implements Runnable, CommandListener {

    // ------------------------------------------------------------------
    //  Константы
    // ------------------------------------------------------------------
    private static final float MOVE_SPEED   = 0.25f;
    private static final float ROT_SPEED    = 3.0f;
    private static final float PITCH_LIMIT  = 80f;
    private static final int   FRAME_DELAY  = 40;   // ~25 FPS

    private static final int   MAX_RAY_STEPS = 64;

    private static final byte  AIR           = 0;
    private static final byte  BEDROCK       = 4;
    private static final int   MAX_BLOCK_TYPE = 9;

    // --- Физика игрока (AABB) ---
    private static final float GRAVITY         = 0.05f;
    private static final float JUMP_SPEED      = 0.35f;
    private static final float MAX_FALL_SPEED  = -1.0f;
    private static final float EYE_HEIGHT      = 1.62f;
    private static final float PLAYER_HALF     = 0.30f;
    private static final float PLAYER_HEIGHT   = 1.80f;

    // --- Цикл дня и ночи ---
    private static final float SKY_SPEED = 0.0007f;
    private static final int   SKY_NIGHT_R =  10, SKY_NIGHT_G =  14, SKY_NIGHT_B =  30;
    private static final int   SKY_DAY_R   = 144, SKY_DAY_G   = 184, SKY_DAY_B   = 224;

    // --- Crosshair ---
    private static final int CROSS_ARM = 8;
    private static final int CROSS_GAP = 2;

    // ------------------------------------------------------------------
    //  Ссылки
    // ------------------------------------------------------------------
    private final VoxelMIDlet midlet;

    // --- M3G ---
    private final Graphics3D g3d;
    private final Camera      cam;
    private final Transform   camT   = new Transform();
    private final Transform   lightT = new Transform();
    private final Background  bg     = new Background();
    private Mesh              mesh;

    // --- Мир ---
    private VoxelWorld world;

    // --- Камера ---
    private float camX, camY, camZ;
    private float yaw, pitch;

    // --- Физика ---
    private float   velocityY = 0f;
    private boolean onGround  = false;

    // --- Состояния клавиш ---
    private boolean kForward, kBackward, kStrafeLeft, kStrafeRight;
    private boolean kYawLeft, kYawRight, kPitchUp, kPitchDown;

    // --- Поток ---
    private volatile boolean running = false;
    private volatile boolean paused  = false;
    private Thread thread;

    // --- Raycast ---
    private final int[] hitBlock   = new int[3];
    private final int[] placeBlock = new int[3];
    private boolean hasHit = false;

    // --- Режим ---
    private boolean isPlacingMode    = false;
    private byte    currentBlockType = 1;

    // --- Цикл дня/ночи ---
    private float skyPhase = 0f;

    // --- Команды ---
    private final Command menuCommand;
    private final Command helpCommand;

    // ------------------------------------------------------------------
    //  Конструктор
    // ------------------------------------------------------------------
    public GameCanvas(VoxelMIDlet midlet) {
        this.midlet = midlet;

        try { setFullScreenMode(true); } catch (Throwable ignored) {}

        g3d = Graphics3D.getInstance();
        cam = new Camera();
        final int cw = Math.max(1, getWidth());
        final int ch = Math.max(1, getHeight());
        cam.setPerspective(60f, (float) cw / ch, 0.1f, 200f);

        final Light ambLight = new Light();
        ambLight.setMode(Light.AMBIENT);
        ambLight.setColor(0xFFFFFFFF);
        g3d.addLight(ambLight, lightT);

        bg.setColor(0xFF90A8C0);

        world = new VoxelWorld();

        menuCommand = new Command("Menu", Command.BACK,   1);
        helpCommand = new Command("Help", Command.SCREEN, 2);
        addCommand(menuCommand);
        addCommand(helpCommand);
        setCommandListener(this);
    }

    // ------------------------------------------------------------------
    //  Публичное API
    // ------------------------------------------------------------------
    public void newWorld() {
        world.generate();
        rebuildMesh();

        camX = VoxelWorld.SX / 2f;
        camY = VoxelWorld.SY - 6f;
        camZ = VoxelWorld.SZ / 2f + 6f;
        yaw   = 0f;
        pitch = 15f;

        velocityY = 0f;
        onGround  = false;
        skyPhase  = 0.15f;   // начинаем с утра, а не с ночи
    }

    public synchronized void start() {
        paused = false;
        if (thread == null) {
            running = true;
            thread = new Thread(this, "VoxelGame");
            thread.start();
        }
    }

    public synchronized void pause() {
        paused = true;
    }

    public synchronized void stop() {
        running = false;
        paused  = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    /** Явно освобождает M3G-ресурсы перед уходом с экрана. */
    public void releaseMesh() {
        if (mesh != null) {
            mesh = null;
            System.gc();
        }
    }

    // ------------------------------------------------------------------
    //  Цикл
    // ------------------------------------------------------------------
    public void run() {
        try {
            while (running) {
                if (!paused) {
                    update();
                    repaint();
                }
                try {
                    Thread.sleep(FRAME_DELAY);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } finally {
            synchronized (this) {
                thread  = null;
                running = false;
            }
        }
    }

    // ------------------------------------------------------------------
    //  Обновление логики
    // ------------------------------------------------------------------
    private void update() {
        // Кэш тригонометрии
        final float ry   = (float) Math.toRadians(yaw);
        final float rp   = (float) Math.toRadians(pitch);
        final float cosP = (float) Math.cos(rp);
        final float sinP = (float) Math.sin(rp);
        final float cosY = (float) Math.cos(ry);
        final float sinY = (float) Math.sin(ry);

        // Вращение
        if (kYawLeft)   yaw   -= ROT_SPEED;
        if (kYawRight)  yaw   += ROT_SPEED;
        if (kPitchUp)   pitch -= ROT_SPEED;
        if (kPitchDown) pitch += ROT_SPEED;

        if (pitch >  PITCH_LIMIT) pitch =  PITCH_LIMIT;
        if (pitch < -PITCH_LIMIT) pitch = -PITCH_LIMIT;
        if (yaw >= 360f) yaw -= 360f;
        if (yaw <  0f)   yaw += 360f;

        // Направление взгляда (для raycast)
        final float fx =  cosP * sinY;
        final float fy = -sinP;
        final float fz = -cosP * cosY;

        // Горизонтальные направления (ходьба — без вертикальной составляющей)
        final float hfx =  sinY;
        final float hfz = -cosY;
        final float hrx =  cosY;
        final float hrz =  sinY;

        // --- Перемещение по горизонтали ---
        float dx = 0f, dz = 0f;
        if (kForward)     { dx += hfx * MOVE_SPEED; dz += hfz * MOVE_SPEED; }
        if (kBackward)    { dx -= hfx * MOVE_SPEED; dz -= hfz * MOVE_SPEED; }
        if (kStrafeLeft)  { dx -= hrx * MOVE_SPEED; dz -= hrz * MOVE_SPEED; }
        if (kStrafeRight) { dx += hrx * MOVE_SPEED; dz += hrz * MOVE_SPEED; }

        // Оси раздельно — «скольжение» вдоль стен
        if (dx != 0f) {
            final float nx = camX + dx;
            if (!collidesHorizontally(nx, camY, camZ)) camX = nx;
        }
        if (dz != 0f) {
            final float nz = camZ + dz;
            if (!collidesHorizontally(camX, camY, nz)) camZ = nz;
        }

        // --- Жёсткие границы мира ---
        if (camX <  PLAYER_HALF)                 camX =  PLAYER_HALF;
        if (camX >  VoxelWorld.SX - PLAYER_HALF) camX =  VoxelWorld.SX - PLAYER_HALF;
        if (camZ <  PLAYER_HALF)                 camZ =  PLAYER_HALF;
        if (camZ >  VoxelWorld.SZ - PLAYER_HALF) camZ =  VoxelWorld.SZ - PLAYER_HALF;

        // --- Гравитация ---
        velocityY -= GRAVITY;
        if (velocityY < MAX_FALL_SPEED) velocityY = MAX_FALL_SPEED;
        camY += velocityY;
        resolveVerticalCollision();

        // --- Цикл дня и ночи ---
        skyPhase += SKY_SPEED;
        if (skyPhase >= 1f) skyPhase -= 1f;

        float t = (float) ((1.0 - Math.cos(skyPhase * 2.0 * Math.PI)) * 0.5);
        t = t * t * (3f - 2f * t);

        final int r = (int) (SKY_NIGHT_R + (SKY_DAY_R - SKY_NIGHT_R) * t);
        final int g = (int) (SKY_NIGHT_G + (SKY_DAY_G - SKY_NIGHT_G) * t);
        final int b = (int) (SKY_NIGHT_B + (SKY_DAY_B - SKY_NIGHT_B) * t);
        bg.setColor(0xFF000000 | (r << 16) | (g << 8) | b);

        // --- Raycast ---
        doRaycast(fx, fy, fz);
    }

    // ------------------------------------------------------------------
    //  Raycast (DDA)
    // ------------------------------------------------------------------
    private void doRaycast(float fx, float fy, float fz) {
        hasHit = false;

        int ix = (int) Math.floor(camX);
        int iy = (int) Math.floor(camY);
        int iz = (int) Math.floor(camZ);

        final int stepX = fx >= 0f ?  1 : -1;
        final int stepY = fy >= 0f ?  1 : -1;
        final int stepZ = fz >= 0f ?  1 : -1;

        final float invX = Math.abs(fx) > 1e-6f ? Math.abs(1f / fx) : Float.MAX_VALUE;
        final float invY = Math.abs(fy) > 1e-6f ? Math.abs(1f / fy) : Float.MAX_VALUE;
        final float invZ = Math.abs(fz) > 1e-6f ? Math.abs(1f / fz) : Float.MAX_VALUE;

        // tMax — ВСЕГДА положительное расстояние до след. границы куба
        float tMaxX = (stepX > 0 ? (ix + 1 - camX) : (camX - ix)) * invX;
        float tMaxY = (stepY > 0 ? (iy + 1 - camY) : (camY - iy)) * invY;
        float tMaxZ = (stepZ > 0 ? (iz + 1 - camZ) : (camZ - iz)) * invZ;

        int px = ix, py = iy, pz = iz;

        for (int i = 0; i < MAX_RAY_STEPS; i++) {
            px = ix; py = iy; pz = iz;

            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                ix += stepX;
                tMaxX += invX;
            } else if (tMaxY <= tMaxZ) {
                iy += stepY;
                tMaxY += invY;
            } else {
                iz += stepZ;
                tMaxZ += invZ;
            }

            if (ix < 0 || ix >= VoxelWorld.SX
             || iy < 0 || iy >= VoxelWorld.SY
             || iz < 0 || iz >= VoxelWorld.SZ) {
                return;
            }

            if (world.get(ix, iy, iz) != AIR) {
                hitBlock[0]   = ix; hitBlock[1]   = iy; hitBlock[2]   = iz;
                placeBlock[0] = px; placeBlock[1] = py; placeBlock[2] = pz;
                hasHit = true;
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    //  Коллизии
    // ------------------------------------------------------------------
    private boolean collidesHorizontally(float x, float eyeY, float z) {
        final float feetY = eyeY - EYE_HEIGHT;
        final float headY = feetY + PLAYER_HEIGHT;

        final int x0 = (int) Math.floor(x - PLAYER_HALF);
        final int x1 = (int) Math.floor(x + PLAYER_HALF);
        final int y0 = (int) Math.floor(feetY);
        final int y1 = (int) Math.floor(headY);
        final int z0 = (int) Math.floor(z - PLAYER_HALF);
        final int z1 = (int) Math.floor(z + PLAYER_HALF);

        for (int bx = x0; bx <= x1; bx++) {
            for (int by = y0; by <= y1; by++) {
                for (int bz = z0; bz <= z1; bz++) {
                    if (world.get(bx, by, bz) != AIR) return true;
                }
            }
        }
        return false;
    }

    private void resolveVerticalCollision() {
        final float feetY = camY - EYE_HEIGHT;
        final float headY = feetY + PLAYER_HEIGHT;

        final int x0 = (int) Math.floor(camX - PLAYER_HALF);
        final int x1 = (int) Math.floor(camX + PLAYER_HALF);
        final int z0 = (int) Math.floor(camZ - PLAYER_HALF);
        final int z1 = (int) Math.floor(camZ + PLAYER_HALF);

        if (velocityY <= 0f) {
            // Падение
            final int fy = (int) Math.floor(feetY);
            boolean hit = false;
            for (int bx = x0; bx <= x1 && !hit; bx++)
                for (int bz = z0; bz <= z1 && !hit; bz++)
                    if (world.get(bx, fy, bz) != AIR) hit = true;

            if (hit) {
                camY      = (fy + 1) + EYE_HEIGHT;
                velocityY = 0f;
                onGround  = true;
            } else {
                onGround  = false;
            }
        } else {
            // Подъём
            final int hy = (int) Math.floor(headY);
            boolean hit = false;
            for (int bx = x0; bx <= x1 && !hit; bx++)
                for (int bz = z0; bz <= z1 && !hit; bz++)
                    if (world.get(bx, hy, bz) != AIR) hit = true;

            if (hit) {
                camY      = hy - PLAYER_HEIGHT + EYE_HEIGHT;
                velocityY = 0f;
            }
            onGround = false;
        }
    }

    // ------------------------------------------------------------------
    //  Отрисовка
    // ------------------------------------------------------------------
    protected void paint(Graphics g) {
        try {
            g3d.bindTarget(g);
            g3d.clear(bg);

            camT.setIdentity();
            camT.postTranslate(camX, camY, camZ);
            camT.postRotate(-yaw,   0f, 1f, 0f);
            camT.postRotate(-pitch, 1f, 0f, 0f);
            g3d.setCamera(cam, camT);

            if (mesh != null) {
                g3d.render(mesh, null);
            }
        } finally {
            g3d.releaseTarget();
        }

        drawCrosshair(g);
    }

    private void drawCrosshair(Graphics g) {
        final int cx  = getWidth()  / 2;
        final int cy  = getHeight() / 2;
        final int arm = CROSS_ARM;
        final int gap = CROSS_GAP;

        // Чёрная обводка
        g.setColor(0x000000);
        g.drawLine(cx - arm, cy - 1, cx - gap, cy - 1);
        g.drawLine(cx - arm, cy + 1, cx - gap, cy + 1);
        g.drawLine(cx + gap, cy - 1, cx + arm, cy - 1);
        g.drawLine(cx + gap, cy + 1, cx + arm, cy + 1);
        g.drawLine(cx - 1, cy - arm, cx - 1, cy - gap);
        g.drawLine(cx + 1, cy - arm, cx + 1, cy - gap);
        g.drawLine(cx - 1, cy + gap, cx - 1, cy + arm);
        g.drawLine(cx + 1, cy + gap, cx + 1, cy + arm);

        // Белый крест
        g.setColor(0xFFFFFF);
        g.drawLine(cx - arm, cy, cx - gap, cy);
        g.drawLine(cx + gap, cy, cx + arm, cy);
        g.drawLine(cx, cy - arm, cx, cy - gap);
        g.drawLine(cx, cy + gap, cx, cy + arm);
    }

    protected void sizeChanged(int w, int h) {
        if (cam != null && h > 0) {
            cam.setPerspective(60f, (float) w / h, 0.1f, 200f);
        }
    }

    protected void showNotify() { start(); }
    protected void hideNotify() { pause(); }

    // ------------------------------------------------------------------
    //  Ввод
    // ------------------------------------------------------------------
    protected void keyPressed(int keyCode)  { handleKey(keyCode, true);  }
    protected void keyReleased(int keyCode) { handleKey(keyCode, false); }

    private void handleKey(int keyCode, boolean pressed) {
        // ---- Отпускания ----
        if (!pressed) {
            switch (keyCode) {
                case KEY_NUM2: kForward     = false; return;
                case KEY_NUM8: kBackward    = false; return;
                case KEY_NUM4: kStrafeLeft  = false; return;
                case KEY_NUM6: kStrafeRight = false; return;
            }
            final int action = getGameAction(keyCode);
            switch (action) {
                case UP:    kPitchUp   = false; return;
                case DOWN:  kPitchDown = false; return;
                case LEFT:  kYawLeft   = false; return;
                case RIGHT: kYawRight  = false; return;
            }
            return;
        }

        // ---- Нажатия ----

        // # — переключение режима + смена типа блока в PLACE
        if (keyCode == KEY_POUND) {
            isPlacingMode = !isPlacingMode;
            if (isPlacingMode) nextPlaceableBlock();
            return;
        }

        // * — просто сменить тип блока
        if (keyCode == KEY_STAR) {
            nextPlaceableBlock();
            return;
        }

        // FIRE / OK — действие
        if (keyCode == FIRE || keyCode == KEY_NUM5) {
            performAction();
            return;
        }

        // Движение / прыжок
        switch (keyCode) {
            case KEY_NUM2: kForward     = true; return;
            case KEY_NUM8: kBackward    = true; return;
            case KEY_NUM4: kStrafeLeft  = true; return;
            case KEY_NUM6: kStrafeRight = true; return;
            case KEY_NUM1:
                if (onGround) {
                    velocityY = JUMP_SPEED;
                    onGround  = false;
                }
                return;
            case KEY_NUM3:
                return;  // зарезервировано (присесть)
        }

        // Вращение
        final int action = getGameAction(keyCode);
        switch (action) {
            case UP:    kPitchUp   = true; break;
            case DOWN:  kPitchDown = true; break;
            case LEFT:  kYawLeft   = true; break;
            case RIGHT: kYawRight  = true; break;
        }
    }

    // ------------------------------------------------------------------
    //  Действие FIRE
    // ------------------------------------------------------------------
    private void performAction() {
        if (!hasHit) return;

        if (isPlacingMode) {
            final int x = placeBlock[0];
            final int y = placeBlock[1];
            final int z = placeBlock[2];
            if (x < 0 || x >= VoxelWorld.SX
             || y < 0 || y >= VoxelWorld.SY
             || z < 0 || z >= VoxelWorld.SZ) return;
            if (world.get(x, y, z) != AIR) return;
            if (currentBlockType == BEDROCK) return;   // нельзя ставить бедрок
            if (blockIntersectsPlayer(x, y, z)) return;

            world.set(x, y, z, currentBlockType);
        } else {
            final int x = hitBlock[0];
            final int y = hitBlock[1];
            final int z = hitBlock[2];
            if (world.get(x, y, z) == BEDROCK) return; // вечный блок

            world.set(x, y, z, AIR);
        }

        rebuildMesh();
    }

    /** Следующий тип блока, который игрок может поставить (bedrock пропускаем). */
    private void nextPlaceableBlock() {
        int t = currentBlockType;
        do {
            t++;
            if (t > MAX_BLOCK_TYPE) t = 1;
        } while (t == BEDROCK);
        currentBlockType = (byte) t;
    }

    private boolean blockIntersectsPlayer(int bx, int by, int bz) {
        final float feetY = camY - EYE_HEIGHT;
        final float headY = feetY + PLAYER_HEIGHT;
        return (bx + 1 > camX - PLAYER_HALF) && (bx < camX + PLAYER_HALF)
            && (by + 1 > feetY)             && (by < headY)
            && (bz + 1 > camZ - PLAYER_HALF) && (bz < camZ + PLAYER_HALF);
    }

    private void rebuildMesh() {
        // Сначала выбрасываем старый меш и намекаем GC — иначе новая аллокация
        // идёт поверх нативного буфера, и на слабых устройствах это выглядит как «2× ОЗУ».
        if (mesh != null) {
            mesh = null;
            System.gc();
        }
        mesh = VoxelMeshBuilder.buildMesh(world);
    }

    // ------------------------------------------------------------------
    //  Команды
    // ------------------------------------------------------------------
    public void commandAction(Command c, Displayable d) {
        if (c == menuCommand) {
            stop();
            midlet.showMenu();
        } else if (c == helpCommand) {
            // Не stop() — иначе потеряем мир. showHelp() сам поставит на паузу.
            midlet.showHelp();
        }
    }
}