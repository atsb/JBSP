package com.doom.bsp;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.doom.bsp.Structs.DirectoryEntry;
import static com.doom.bsp.Structs.WadHeader;

public final class BSPWad {

    public static WadHeader readHeader(FileChannel ch) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        ch.read(buf, 0);
        buf.flip();
        WadHeader h = new WadHeader();
        buf.get(h.type);
        h.numEntries = buf.getInt();
        h.dirStart = buf.getInt();
        return h;
    }

    public static DirectoryEntry[] readDirectory(FileChannel ch, WadHeader h) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(h.numEntries * 16).order(ByteOrder.LITTLE_ENDIAN);
        ch.read(buf, h.dirStart);
        buf.flip();
        DirectoryEntry[] dir = new DirectoryEntry[h.numEntries];
        for (int i = 0; i < h.numEntries; i++) {
            DirectoryEntry e = new DirectoryEntry();
            e.start = buf.getInt();
            e.length = buf.getInt();
            buf.get(e.name);
            dir[i] = e;
        }
        return dir;
    }

    public static byte[] readLump(FileChannel ch, DirectoryEntry e) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(e.length);
        ch.read(buf, e.start);
        return buf.array();
    }

    public static void writePWad(File out, List<Lump> lumps) throws IOException {
        int offset = 12;
        for (Lump l : lumps) {
            l.dir.start = offset;
            l.dir.length = l.data.length;
            offset += l.data.length;
        }
        int dirStart = offset;

        // Header
        try (RandomAccessFile raf = new RandomAccessFile(out, "rw");
             FileChannel ch = raf.getChannel()) {
            ByteBuffer header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
            header.put("PWAD".getBytes(StandardCharsets.US_ASCII));
            header.putInt(lumps.size());
            header.putInt(dirStart);
            header.flip();
            ch.write(header, 0);

            // Lumps
            int pos = 12;
            for (Lump l : lumps) {
                ch.write(ByteBuffer.wrap(l.data), pos);
                pos += l.data.length;
            }

            // Directory
            ByteBuffer dirbuf = ByteBuffer.allocate(lumps.size() * 16).order(ByteOrder.LITTLE_ENDIAN);
            for (Lump l : lumps) {
                dirbuf.putInt(l.dir.start);
                dirbuf.putInt(l.dir.length);
                dirbuf.put(l.dir.name);
            }
            dirbuf.flip();
            ch.write(dirbuf, dirStart);
        }
    }

    public static final class Lump {
        public final DirectoryEntry dir = new DirectoryEntry();
        public byte[] data;

        public Lump(String name, byte[] data) {
            this.data = data;
            dir.setName(name);
        }
    }
}
