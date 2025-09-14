package com.doom.bsp;

import java.nio.charset.StandardCharsets;

public final class Structs {

    private Structs() {
    }

    public static final class WadHeader {
        public byte[] type = new byte[4]; // "IWAD" or "PWAD"
        public int numEntries;
        public int dirStart;

        public String typeString() {
            return new String(type, StandardCharsets.US_ASCII);
        }

        public void setType(String s) {
            byte[] b = s.getBytes(StandardCharsets.US_ASCII);
            for (int i = 0; i < 4; i++) type[i] = i < b.length ? b[i] : 0;
        }
    }

    public static final class DirectoryEntry {
        public int start;
        public int length;
        public byte[] name = new byte[8];

        public String nameString() {
            int len = 0;
            while (len < 8 && name[len] != 0) len++;
            return new String(name, 0, len, StandardCharsets.US_ASCII);
        }

        public void setName(String s) {
            byte[] b = s.getBytes(StandardCharsets.US_ASCII);
            for (int i = 0; i < 8; i++) name[i] = i < b.length ? b[i] : 0;
        }
    }

    public static final class Thing {
        public short x, y, angle, type, options;
    }

    public static final class Vertex {
        public short x, y;
    }

    public static final class LineDef {
        public short start, end;
        public short flags, type, tag;
        public short sidedef1, sidedef2;
    }

    public static final class SideDef {
        public short xoff, yoff;
        public byte[] tex1 = new byte[8];
        public byte[] tex2 = new byte[8];
        public byte[] tex3 = new byte[8];
        public short sector;
    }

    public static final class Sector {
        public short floorh, ceilh;
        public byte[] floort = new byte[8];
        public byte[] ceilt = new byte[8];
        public short light, special, tag;
    }

    public static final class Seg {
        public short start, end;
        public int angle;    // unsigned short in C
        public short linedef;
        public short flip;
        public int dist;     // unsigned short in C
        public Seg next;     // linked list like in C
    }

    public static final class PSeg {
        public short start, end;
        public int angle;
        public short linedef;
        public short flip;
        public int dist;
    }

    public static final class Node {
        public short x, y;
        public short dx, dy;
        public short maxy1, miny1, minx1, maxx1;
        public short maxy2, miny2, minx2, maxx2;
        public short chright, chleft; // child indices or 0x8000 | ssector index
        public Node nextr, nextl;
        public short nodeNum;
    }

    public static final class PNode {
        public short x, y, dx, dy;
        public short maxy1, miny1, minx1, maxx1;
        public short maxy2, miny2, minx2, maxx2;
        public short chright, chleft;
    }

    public static final class SSector {
        public short num;
        public short first;
    }

    public static final class Splitter {
        public short halfx, halfy, halfsx, halfsy;
    }
}
