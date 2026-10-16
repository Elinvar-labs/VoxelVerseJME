package voxelversejme;

import java.util.Random;

/**
 * Данные воксельного мира + сверхпримитивный генератор рельефа.
 *
 * Формат хранения: одномерный массив byte[SX * SY * SZ], индекс = (y * SZ + z)
 * * SX + x — даёт хорошую локальность при обходе циклом for(y) for(z) for(x).
 */
public class VoxelWorld {

    public static final int SX = 32;
    public static final int SY = 32;
    public static final int SZ = 32;

    private static final int VOLUME = SX * SY * SZ;

    // Типы блоков
    public static final byte AIR = 0;
    public static final byte GRASS = 1;
    public static final byte DIRT = 2;
    public static final byte STONE = 3;
    public static final byte BEDROCK = 4;

    private final byte[] data = new byte[VOLUME];

    // ------------------------------------------------------------------
    //  Доступ
    // ------------------------------------------------------------------
    public byte get(int x, int y, int z) {
        if (x < 0 || x >= SX || y < 0 || y >= SY || z < 0 || z >= SZ) {
            return AIR;
        }
        return data[(y * SZ + z) * SX + x];
    }

    public void set(int x, int y, int z, byte v) {
        if (x < 0 || x >= SX || y < 0 || y >= SY || z < 0 || z >= SZ) {
            return;
        }
        data[(y * SZ + z) * SX + x] = v;
    }

    public void clear() {
        final byte[] d = data;
        final int n = d.length;
        for (int i = 0; i < n; i++) {
            d[i] = AIR;
        }
    }

    // ------------------------------------------------------------------
    //  Генератор: очень простая height-map на косинусах + value-noise
    // ------------------------------------------------------------------
    public void generate() {
        clear();

        final int N = 8;                       // размер сетки шума
        final Random rnd = new Random(0xBEEF); // фиксированный сид
        final int[][] noise = new int[N][N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                noise[i][j] = rnd.nextInt(3); // 0..2
            }
        }

        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                // Билинейная интерполяция грубого шума
                final float fx = (float) x / SX * (N - 1);
                final float fz = (float) z / SZ * (N - 1);
                final int x0 = (int) fx;
                final int z0 = (int) fz;
                final int x1 = (x0 + 1 < N) ? x0 + 1 : N - 1;
                final int z1 = (z0 + 1 < N) ? z0 + 1 : N - 1;
                final float tx = fx - x0;
                final float tz = fz - z0;

                final float n00 = noise[x0][z0];
                final float n10 = noise[x1][z0];
                final float n01 = noise[x0][z1];
                final float n11 = noise[x1][z1];

                final float n
                        = n00 * (1f - tx) * (1f - tz)
                        + n10 * tx * (1f - tz)
                        + n01 * (1f - tx) * tz
                        + n11 * tx * tz;

                // Итоговая высота поверхности: 3..5
                final int h = 3 + (int) (n + 0.5f);

                // Заполняем столбец
                final int base = z * SX + x;
                for (int y = 0; y <= h; y++) {
                    final byte b;
                    if (y == 0) {
                        b = BEDROCK;
                    } else if (y == h) {
                        b = GRASS;
                    } else if (y >= h - 2) {
                        b = DIRT;
                    } else {
                        b = STONE;
                    }
                    data[(y * SZ) * SX + base] = b;
                }
            }
        }

        // Простейшее украшение: колонна-«обелиск» в центре
        final int cx = SX / 2, cz = SZ / 2;
        final int top = 8;
        for (int y = 4; y <= top; y++) {
            set(cx, y, cz, STONE);
        }
        set(cx, top + 1, cz, (byte) 6); // «лампа» из лавы
    }
}
