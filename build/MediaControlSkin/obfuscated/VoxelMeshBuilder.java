package voxelversejme;

import javax.microedition.lcdui.Image;
import javax.microedition.m3g.*;

/**
 * Построение единого меша из воксельного мира.
 *
 * Оптимизации:
 *  - два прохода: сначала считаем faces, потом заполняем точно выверенные массивы;
 *  - отсекаются грани, соприкасающиеся с любым непрозрачным блоком;
 *  - block lookup BLOCK_FACES вынесен в статику;
 *  - ноль аллокаций в горячем цикле, кроме финального массива;
 *  - Appearance кэшируется на всё время работы.
 */
public final class VoxelMeshBuilder {

    private VoxelMeshBuilder() {}

    // Тайл кодируется как (row << 4) | col.
    private static final byte[] BLOCK_FACES = {
        // AIR
        -1, -1, -1, -1, -1, -1,
        // GRASS: +Y трава (0x38), -Y земля (0x2C), бока трава (0x38)
        (byte) 0x38, (byte) 0x2C, (byte) 0x38, (byte) 0x38, (byte) 0x38, (byte) 0x38,
        // DIRT
        (byte) 0x2C, (byte) 0x2C, (byte) 0x2C, (byte) 0x2C, (byte) 0x2C, (byte) 0x2C,
        // STONE
        (byte) 0x5C, (byte) 0x5C, (byte) 0x5C, (byte) 0x5C, (byte) 0x5C, (byte) 0x5C,
        // BEDROCK
        (byte) 0x1E, (byte) 0x1E, (byte) 0x1E, (byte) 0x1E, (byte) 0x1E, (byte) 0x1E,
        // TNT
        (byte) 0xD7, (byte) 0xD5, (byte) 0xD6, (byte) 0xD6, (byte) 0xD6, (byte) 0xD6,
        // LAVA (второй тайл сверху для «лампы»)
        (byte) 0x3E, (byte) 0x3E, (byte) 0x3E, (byte) 0x3E, (byte) 0x3E, (byte) 0x3E,
        // Cactus
        (byte) 0x18, (byte) 0x18, (byte) 0x17, (byte) 0x17, (byte) 0x17, (byte) 0x17,
        // Blue demon stone
        (byte) 0x85, (byte) 0x85, (byte) 0x85, (byte) 0x85, (byte) 0x85, (byte) 0x85,
        // Pumpkin
        (byte) 0x8B, (byte) 0x8E, (byte) 0x8C, (byte) 0x8C, (byte) 0x8C, (byte) 0x8C
    };

    private static final int TILE = 16;
    private static final int ATLAS_PATH_TILE = 16; // 16x16 тайлов

    // Порядок граней: 0:+Y, 1:-Y, 2:+X, 3:-X, 4:+Z, 5:-Z
    private static final int[][] NB = {
        { 0,  1,  0}, { 0, -1,  0},
        { 1,  0,  0}, {-1,  0,  0},
        { 0,  0,  1}, { 0,  0, -1}
    };

    // Нормали: масштаб 1/256, поэтому ±256 == ±1.0
    private static final short[] FACE_NORMALS = {
         0,  256,    0,
         0, -256,    0,
         256,  0,    0,
        -256,  0,    0,
         0,    0,  256,
         0,    0, -256
    };

    private static final String ATLAS_PATH = "/voxelversejme/res/atlas.png";

    private static Appearance cachedAppearance;

    // ------------------------------------------------------------------
    //  Построение
    // ------------------------------------------------------------------
    public static Mesh buildMesh(VoxelWorld w) {
        final int SX = VoxelWorld.SX, SY = VoxelWorld.SY, SZ = VoxelWorld.SZ;

        // === Первый проход: считаем количество видимых граней ===
        int faces = 0;
        for (int y = 0; y < SY; y++) {
            for (int z = 0; z < SZ; z++) {
                for (int x = 0; x < SX; x++) {
                    final byte b = w.get(x, y, z);
                    if (b == 0) continue;
                    final int base = b * 6;
                    for (int f = 0; f < 6; f++) {
                        if (BLOCK_FACES[base + f] == -1) continue;
                        final int nx = x + NB[f][0];
                        final int ny = y + NB[f][1];
                        final int nz = z + NB[f][2];
                        if (w.get(nx, ny, nz) == 0) faces++;
                    }
                }
            }
        }

        if (faces == 0) return null;

        final int vCount  = faces * 4;
        final short[] pos = new short[vCount * 3];
        final short[] nor = new short[vCount * 3];
        final short[] uv  = new short[vCount * 2];
        final int[]   strips = new int[faces];

        // === Второй проход: заполняем массивы ===
        int vi = 0, si = 0;
        for (int y = 0; y < SY; y++) {
            for (int z = 0; z < SZ; z++) {
                for (int x = 0; x < SX; x++) {
                    final byte b = w.get(x, y, z);
                    if (b == 0) continue;

                    final int base = b * 6;
                    final int x0 = x << 8, x1 = (x + 1) << 8;
                    final int y0 = y << 8, y1 = (y + 1) << 8;
                    final int z0 = z << 8, z1 = (z + 1) << 8;

                    for (int f = 0; f < 6; f++) {
                        final byte tile = BLOCK_FACES[base + f];
                        if (tile == -1) continue;
                        if (w.get(x + NB[f][0], y + NB[f][1], z + NB[f][2]) != 0) continue;

                        final int col = tile & 0x0F;
                        final int row = (tile >> 4) & 0x0F;
                        final short u0 = (short) (col * TILE);
                        final short u1 = (short) (u0 + TILE);
                        final short v0 = (short) (row * TILE);
                        final short v1 = (short) (v0 + TILE);

                        switch (f) {
                            case 0: // +Y
                                put(pos, uv, vi,     x0, y1, z1, u0, v0);
                                put(pos, uv, vi + 1, x1, y1, z1, u1, v0);
                                put(pos, uv, vi + 2, x0, y1, z0, u0, v1);
                                put(pos, uv, vi + 3, x1, y1, z0, u1, v1);
                                break;
                            case 1: // -Y
                                put(pos, uv, vi,     x0, y0, z0, u0, v0);
                                put(pos, uv, vi + 1, x1, y0, z0, u1, v0);
                                put(pos, uv, vi + 2, x0, y0, z1, u0, v1);
                                put(pos, uv, vi + 3, x1, y0, z1, u1, v1);
                                break;
                            case 2: // +X
                                put(pos, uv, vi,     x1, y0, z0, u1, v0);
                                put(pos, uv, vi + 1, x1, y1, z0, u1, v1);
                                put(pos, uv, vi + 2, x1, y0, z1, u0, v0);
                                put(pos, uv, vi + 3, x1, y1, z1, u0, v1);
                                break;
                            case 3: // -X
                                put(pos, uv, vi,     x0, y0, z1, u1, v0);
                                put(pos, uv, vi + 1, x0, y1, z1, u1, v1);
                                put(pos, uv, vi + 2, x0, y0, z0, u0, v0);
                                put(pos, uv, vi + 3, x0, y1, z0, u0, v1);
                                break;
                            case 4: // +Z
                                put(pos, uv, vi,     x1, y0, z1, u1, v0);
                                put(pos, uv, vi + 1, x1, y1, z1, u1, v1);
                                put(pos, uv, vi + 2, x0, y0, z1, u0, v0);
                                put(pos, uv, vi + 3, x0, y1, z1, u0, v1);
                                break;
                            default: // -Z
                                put(pos, uv, vi,     x0, y0, z0, u0, v0);
                                put(pos, uv, vi + 1, x0, y1, z0, u0, v1);
                                put(pos, uv, vi + 2, x1, y0, z0, u1, v0);
                                put(pos, uv, vi + 3, x1, y1, z0, u1, v1);
                                break;
                        }

                        putNormal(nor, vi,     f);
                        putNormal(nor, vi + 1, f);
                        putNormal(nor, vi + 2, f);
                        putNormal(nor, vi + 3, f);

                        strips[si++] = 4;
                        vi += 4;
                    }
                }
            }
        }

        // === Сборка M3G-объектов ===
        try {
            final VertexArray posArr = new VertexArray(vCount, 3, 2);
            posArr.set(0, vCount, pos);

            final VertexArray norArr = new VertexArray(vCount, 3, 2);
            norArr.set(0, vCount, nor);

            final VertexArray uvArr = new VertexArray(vCount, 2, 2);
            uvArr.set(0, vCount, uv);

            final VertexBuffer vb = new VertexBuffer();
            vb.setPositions(posArr, 1f / 256f, null);   // 256 -> 1.0
            vb.setNormals(norArr);
            vb.setTexCoords(0, uvArr, 1f / 256f, null);

            final IndexBuffer ib = new TriangleStripArray(0, strips);

            return new Mesh(vb, ib, getAppearance());
        } catch (Throwable t) {
            System.err.println("VoxelMeshBuilder: build failed: " + t);
            return null;
        }
    }

    // ------------------------------------------------------------------
    private static void put(short[] p, short[] t, int i,
                            int x, int y, int z, short u, short v) {
        final int p3 = i * 3;
        p[p3]     = (short) x;
        p[p3 + 1] = (short) y;
        p[p3 + 2] = (short) z;
        final int t2 = i * 2;
        t[t2]     = u;
        t[t2 + 1] = v;
    }

    private static void putNormal(short[] n, int i, int face) {
        final int p3 = i * 3;
        n[p3]     = FACE_NORMALS[face * 3];
        n[p3 + 1] = FACE_NORMALS[face * 3 + 1];
        n[p3 + 2] = FACE_NORMALS[face * 3 + 2];
    }

    // ------------------------------------------------------------------
    //  Appearance
    // ------------------------------------------------------------------
    private static synchronized Appearance getAppearance() {
        if (cachedAppearance == null) cachedAppearance = createAppearance();
        return cachedAppearance;
    }

    private static Appearance createAppearance() {
        final Appearance app = new Appearance();

        final Material mat = new Material();
        mat.setColor(Material.DIFFUSE, 0xFFFFFFFF);
        mat.setColor(Material.AMBIENT, 0xFFFFFFFF);
        app.setMaterial(mat);

        final CompositingMode cm = new CompositingMode();
        cm.setBlending(CompositingMode.ALPHA);
        app.setCompositingMode(cm);

        final PolygonMode pm = new PolygonMode();
        pm.setCulling(PolygonMode.CULL_BACK);
        pm.setShading(PolygonMode.SHADE_FLAT);
        pm.setPerspectiveCorrectionEnable(true);
        app.setPolygonMode(pm);

        try {
            final Image src = Image.createImage(ATLAS_PATH);
            final int iw = src.getWidth();
            final int ih = src.getHeight();
            final int[] rgb = new int[iw * ih];
            src.getRGB(rgb, 0, iw, 0, 0, iw, ih);

            final Image rgba = Image.createRGBImage(rgb, iw, ih, true);
            final Image2D i2d = new Image2D(Image2D.RGBA, rgba);

            final Texture2D tex = new Texture2D(i2d);
            tex.setFiltering(Texture2D.FILTER_NEAREST, Texture2D.FILTER_NEAREST);
            tex.setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_CLAMP);
            tex.setBlending(Texture2D.FUNC_REPLACE);
            app.setTexture(0, tex);
        } catch (Throwable t) {
            System.err.println("VoxelMeshBuilder: no atlas " + ATLAS_PATH + ": " + t);
        }

        return app;
    }
}