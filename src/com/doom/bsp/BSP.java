package com.doom.bsp;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.doom.bsp.Structs.*;

/**
 * Literal port of BSP.C into Java.
 */
public final class BSP {

    public static RandomAccessFile infile;
    public static FileChannel inCh;
    public static WadHeader wad;
    public static DirectoryEntry[] direc;

    public static Thing[] things;
    public static int num_things;
    public static Vertex[] vertices;
    public static int num_verts;
    public static LineDef[] linedefs;
    public static int num_lines;
    public static SideDef[] sidedefs;
    public static int num_sides;
    public static Sector[] sectors;
    public static int num_sects;

    public static int num_tsegs = 0;
    public static Seg tsegs; // linked list head

    public static int num_ssectors = 0;
    public static SSector[] ssectors = new SSector[32768];

    public static int num_psegs = 0;
    public static PSeg[] psegs = new PSeg[131072];

    public static int num_nodes = 0;
    public static Node nodelist;

    public static int num_pnodes = 0;
    public static PNode[] pnodes = new PNode[65536];

    public static int lminx, lmaxx, lminy, lmaxy;
    public static int mapminx, mapmaxx, mapminy, mapmaxy;
    public static BlockHead blockhead = new BlockHead();
    public static byte[] blockptrs; // ushort[]
    public static byte[] blocklists;
    public static int blockptrs_size;

    public static int psx, psy, pex, pey, pdx, pdy;
    public static int lsx, lsy, lex, ley;

    public static short node_x, node_y, node_dx, node_dy;

    public static int pnodeIndex = 0;

    public static int selectedMarkerIdx = -1;
    public static String selectedLevelName = null;
    private BSP() {
    }

    /**
     * Return all map markers present in a WAD:
     * marker = 0-length lump followed by THINGS & LINEDEFS before the next marker.
     */
    public static List<MapEntry> listMaps(String wadPath) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(wadPath, "r")) {
            FileChannel ch = raf.getChannel();
            WadHeader h = BSPWad.readHeader(ch);
            DirectoryEntry[] d = BSPWad.readDirectory(ch, h);
            List<MapEntry> result = new ArrayList<>();
            for (int i = 0; i < d.length; i++) {
                String name = new String(d[i].name, StandardCharsets.US_ASCII).trim();
                if (d[i].length == 0 && !name.isEmpty()) {
                    boolean hasThings = false, hasLines = false;
                    for (int j = i + 1; j < d.length; j++) {
                        if (d[j].length == 0) break;
                        String n2 = new String(d[j].name, StandardCharsets.US_ASCII).trim();
                        if ("THINGS".equals(n2)) hasThings = true;
                        else if ("LINEDEFS".equals(n2)) hasLines = true;
                        if (hasThings && hasLines) {
                            result.add(new MapEntry(name, i));
                            break;
                        }
                    }
                }
            }
            return result;
        }
    }

    public static void openWad(String path) throws IOException {
        infile = new RandomAccessFile(path, "r");
        inCh = infile.getChannel();
        wad = BSPWad.readHeader(inCh);
        direc = BSPWad.readDirectory(inCh, wad);
        System.out.printf("%s input file: %s. %d dir entries at %d.%n",
                wad.typeString(), path, wad.numEntries, wad.dirStart);
    }

    private static int findDirInSelectedMap(String name) {
        if (selectedMarkerIdx < 0) {
            for (int i = 0; i < direc.length; i++) {
                String n = new String(direc[i].name, StandardCharsets.US_ASCII).trim();
                if (n.equals(name)) return i;
            }
            throw new IllegalStateException("Cannot find " + name);
        }
        for (int i = selectedMarkerIdx + 1; i < direc.length; i++) {
            if (direc[i].length == 0) break; // next marker
            String n = new String(direc[i].name, StandardCharsets.US_ASCII).trim();
            if (n.equals(name)) return i;
        }
        throw new IllegalStateException("Cannot find " + name + " in map " + selectedLevelName);
    }

    public static void getThings() throws IOException {
        int n = findDirInSelectedMap("THINGS");
        ByteBuffer buf = ByteBuffer.allocate(direc[n].length).order(ByteOrder.LITTLE_ENDIAN);
        inCh.read(buf, direc[n].start);
        buf.flip();
        int count = direc[n].length / 10;
        things = new Thing[count];
        for (int i = 0; i < count; i++) {
            Thing t = new Thing();
            t.x = buf.getShort();
            t.y = buf.getShort();
            t.angle = buf.getShort();
            t.type = buf.getShort();
            t.options = buf.getShort();
            things[i] = t;
        }
        num_things = count;
    }

    public static void getLinedefs() throws IOException {
        int n = findDirInSelectedMap("LINEDEFS");
        ByteBuffer buf = ByteBuffer.allocate(direc[n].length).order(ByteOrder.LITTLE_ENDIAN);
        inCh.read(buf, direc[n].start);
        buf.flip();
        int count = direc[n].length / 14;
        linedefs = new LineDef[count];
        for (int i = 0; i < count; i++) {
            LineDef ld = new LineDef();
            ld.start = buf.getShort();
            ld.end = buf.getShort();
            ld.flags = buf.getShort();
            ld.type = buf.getShort();
            ld.tag = buf.getShort();
            ld.sidedef1 = buf.getShort();
            ld.sidedef2 = buf.getShort();
            linedefs[i] = ld;
        }
        num_lines = count;
    }

    public static void getSidedefs() throws IOException {
        int n = findDirInSelectedMap("SIDEDEFS");
        ByteBuffer buf = ByteBuffer.allocate(direc[n].length).order(ByteOrder.LITTLE_ENDIAN);
        inCh.read(buf, direc[n].start);
        buf.flip();
        int count = direc[n].length / 30;
        sidedefs = new SideDef[count];
        for (int i = 0; i < count; i++) {
            SideDef sd = new SideDef();
            sd.xoff = buf.getShort();
            sd.yoff = buf.getShort();
            buf.get(sd.tex1);
            buf.get(sd.tex2);
            buf.get(sd.tex3);
            sd.sector = buf.getShort();
            sidedefs[i] = sd;
        }
        num_sides = count;
    }

    public static void getSectors() throws IOException {
        int n = findDirInSelectedMap("SECTORS");
        ByteBuffer buf = ByteBuffer.allocate(direc[n].length).order(ByteOrder.LITTLE_ENDIAN);
        inCh.read(buf, direc[n].start);
        buf.flip();
        int count = direc[n].length / 26;
        sectors = new Sector[count];
        for (int i = 0; i < count; i++) {
            Sector s = new Sector();
            s.floorh = buf.getShort();
            s.ceilh = buf.getShort();
            buf.get(s.floort);
            buf.get(s.ceilt);
            s.light = buf.getShort();
            s.special = buf.getShort();
            s.tag = buf.getShort();
            sectors[i] = s;
        }
        num_sects = count;
    }

    public static void getVertexes() throws IOException {
        int n = findDirInSelectedMap("VERTEXES");
        ByteBuffer buf = ByteBuffer.allocate(direc[n].length).order(ByteOrder.LITTLE_ENDIAN);
        inCh.read(buf, direc[n].start);
        buf.flip();
        int count = direc[n].length / 4;
        Vertex[] tmpv = new Vertex[count];
        for (int i = 0; i < count; i++) {
            Vertex v = new Vertex();
            v.x = buf.getShort();
            v.y = buf.getShort();
            tmpv[i] = v;
        }

        vertices = new Vertex[count];
        int used = 0;
        for (int i = 0; i < count; i++) {
            if (referenceVertex(i)) {
                Vertex nv = new Vertex();
                nv.x = tmpv[i].x;
                nv.y = tmpv[i].y;
                for (int t = 0; t < num_lines; t++) {
                    if (linedefs[t].start == i) linedefs[t].start = (short) used;
                    if (linedefs[t].end == i) linedefs[t].end = (short) used;
                }
                vertices[used++] = nv;
            }
        }
        num_verts = used;
        vertices = Arrays.copyOf(vertices, used);
    }

    private static boolean referenceVertex(int i) {
        for (int t = 0; t < num_lines; t++)
            if (linedefs[t].start == i || linedefs[t].end == i) return true;
        return false;
    }

    public static Seg createSegs() {
        System.out.println("Creating Segs ..........");
        Seg head = null, tail = null;
        num_tsegs = 0;

        for (int n = 0; n < num_lines; n++) {
            LineDef ld = linedefs[n];
            short fv = ld.start;
            short tv = ld.end;

            // FRONT
            if (ld.sidedef1 != -1) {
                Seg s1 = new Seg();
                s1.start = fv;
                s1.end = tv;
                int dx = vertices[tv].x - vertices[fv].x;
                int dy = vertices[tv].y - vertices[fv].y;
                s1.angle = computeAngle(dx, dy);
                s1.linedef = (short) n;
                s1.flip = 0;
                s1.dist = 0;
                if (head == null) head = s1;
                else tail.next = s1;
                tail = s1;
                num_tsegs++;
            }

            // BACK
            if (ld.sidedef2 != -1) {
                Seg s2 = new Seg();
                s2.start = tv;
                s2.end = fv;
                int dx = vertices[fv].x - vertices[tv].x;
                int dy = vertices[fv].y - vertices[tv].y;
                s2.angle = computeAngle(dx, dy);
                s2.linedef = (short) n;
                s2.flip = 1;
                s2.dist = 0;
                if (head == null) head = s2;
                else tail.next = s2;
                tail = s2;
                num_tsegs++;
            }
        }
        return head;
    }

    public static int computeAngle(int dx, int dy) {
        double w = Math.atan2(dy, dx) * (65536.0 / (Math.PI * 2.0));
        if (w < 0) w = 65536.0 + w;
        return ((int) w) & 0xFFFF;
    }

    public static void progress() {
    }

    // Blockmap
    public static final class BlockHead {
        public short minx, miny, xblocks, yblocks;
    }

    public record MapEntry(String name, int markerIndex) {

        @Override
            public String toString() {
                return name;
            }
        }
}
