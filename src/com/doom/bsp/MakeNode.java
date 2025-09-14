package com.doom.bsp;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.doom.bsp.BSP.*;
import static com.doom.bsp.PickNode.pickNode;

public class MakeNode {
    public static void divideSegs(Structs.Seg ts, Structs.Seg[] outR, Structs.Seg[] outL) {
        Structs.Seg best = pickNode(ts);
        if (best == null) throw new IllegalStateException("Couldn't pick nodeline!");

        node_x = vertices[best.start].x;
        node_y = vertices[best.start].y;
        node_dx = (short) (vertices[best.end].x - vertices[best.start].x);
        node_dy = (short) (vertices[best.end].y - vertices[best.start].y);

        psx = vertices[best.start].x;
        psy = vertices[best.start].y;
        pex = vertices[best.end].x;
        pey = vertices[best.end].y;
        pdx = psx - pex;
        pdy = psy - pey;

        Structs.Seg rHead = null, rTail = null;
        Structs.Seg lHead = null, lTail = null;

        for (Structs.Seg cur = ts; cur != null; cur = cur.next) {
            if (cur == best) {
                Structs.Seg s = cloneSeg(cur);
                if (rHead == null) rHead = rTail = s;
                else {
                    rTail.next = s;
                    rTail = s;
                }
                continue;
            }

            lsx = vertices[cur.start].x;
            lsy = vertices[cur.start].y;
            lex = vertices[cur.end].x;
            ley = vertices[cur.end].y;
            int val = doLinesIntersect();

            boolean splits = (((val & 2) != 0) && ((val & 64) != 0))   // end-left + start-right
                    || (((val & 4) != 0) && ((val & 32) != 0));  // end-right + start-left

            if (splits) {
                short[] xy = computeIntersection();
                short sx = xy[0], sy = xy[1];

                // Reject splits too close to an endpoint
                int dxA = vertices[cur.start].x - sx;
                int dyA = vertices[cur.start].y - sy;
                int dxB = vertices[cur.end].x - sx;
                int dyB = vertices[cur.end].y - sy;
                boolean tinyA = dxA * dxA + dyA * dyA < 2;
                boolean tinyB = dxB * dxB + dyB * dyB < 2;

                // Create/append halves as NEW segs
                if (!tinyA) {
                    short vi = addVertex(sx, sy); // returns index of new vertex
                    Structs.Seg first = new Structs.Seg();
                    first.start = cur.start;
                    first.end = vi;
                    first.linedef = cur.linedef;
                    first.flip = cur.flip;
                    first.angle = computeAngle(vertices[vi].x - vertices[first.start].x,
                            vertices[vi].y - vertices[first.start].y);
                    first.dist = cur.dist;

                    if ((val & 32) != 0) {
                        lTail = appendSeg(lHead, lTail, first);
                        if (lHead == null) lHead = lTail;
                    }
                    if ((val & 64) != 0) {
                        rTail = appendSeg(rHead, rTail, first);
                        if (rHead == null) rHead = rTail;
                    }
                }

                if (!tinyB) {
                    short vi = (short) (tinyA ? addVertex(sx, sy) : (num_verts - 1));

                    Structs.Seg second = new Structs.Seg();
                    second.start = vi;
                    second.end = cur.end;
                    second.linedef = cur.linedef;
                    second.flip = cur.flip;
                    second.angle = computeAngle(vertices[second.end].x - vertices[vi].x,
                            vertices[second.end].y - vertices[vi].y);
                    second.dist = splitDist(second);

                    if ((val & 2) != 0) {
                        lTail = appendSeg(lHead, lTail, second);
                        if (lHead == null) lHead = lTail;
                    }
                    if ((val & 4) != 0) {
                        rTail = appendSeg(rHead, rTail, second);
                        if (rHead == null) rHead = rTail;
                    }
                }

                continue;
            }

            if ((val & 34) != 0) {
                Structs.Seg s = cloneSeg(cur);
                lTail = appendSeg(lHead, lTail, s);
                if (lHead == null) lHead = lTail;
            } else if ((val & 68) != 0) {
                Structs.Seg s = cloneSeg(cur);
                rTail = appendSeg(rHead, rTail, s);
                if (rHead == null) rHead = rTail;
            } else if ((val & 1) != 0 && (val & 16) != 0) {
                int bangle = best.angle;
                int cangle = cur.angle;
                int cangle2 = (cangle + 32768) & 0xFFFF;

                Structs.Seg s = cloneSeg(cur);
                if (bangle == cangle) {
                    rTail = appendSeg(rHead, rTail, s);
                    if (rHead == null) rHead = rTail;
                } else if (bangle == cangle2) {
                    lTail = appendSeg(lHead, lTail, s);
                    if (lHead == null) lHead = lTail;
                } else {
                    if ((val & 2) != 0 || (val & 32) != 0) {
                        lTail = appendSeg(lHead, lTail, s);
                        if (lHead == null) lHead = lTail;
                    } else {
                        rTail = appendSeg(rHead, rTail, s);
                        if (rHead == null) rHead = rTail;
                    }
                }
            } else {
            }
        }

        outR[0] = rHead;
        outL[0] = lHead;
    }

    private static Structs.Seg cloneSeg(Structs.Seg a) {
        Structs.Seg s = new Structs.Seg();
        s.start = a.start;
        s.end = a.end;
        s.linedef = a.linedef;
        s.flip = a.flip;
        s.angle = a.angle;
        s.dist = a.dist;
        s.next = null;
        return s;
    }

    private static Structs.Seg appendSeg(Structs.Seg head, Structs.Seg tail, Structs.Seg s) {
        if (tail != null) {
            tail.next = s;
            return s;
        }
        return s;
    }

    private static short addVertex(short x, short y) {
        Structs.Vertex v = new Structs.Vertex();
        v.x = x;
        v.y = y;
        vertices = Arrays.copyOf(vertices, num_verts + 1);
        vertices[num_verts] = v;
        return (short) (num_verts++);
    }

    private static int countSegs(Structs.Seg s) {
        int c = 0;
        for (; s != null; s = s.next) c++;
        return c;
    }

    private static void fillNodeBoxesFromList(Structs.Node tn, Structs.Seg list) {
        findLimits(list);
        tn.maxy1 = (short) lmaxy;
        tn.miny1 = (short) lminy;
        tn.minx1 = (short) lminx;
        tn.maxx1 = (short) lmaxx;
        tn.maxy2 = (short) lmaxy;
        tn.miny2 = (short) lminy;
        tn.minx2 = (short) lminx;
        tn.maxx2 = (short) lmaxx;
    }

    public static Structs.Node createNode(Structs.Seg ts) {
        Structs.Node tn = new Structs.Node();
        Structs.Seg[] rights = new Structs.Seg[1];
        Structs.Seg[] lefts = new Structs.Seg[1];

        divideSegs(ts, rights, lefts);
        num_nodes++;

        tn.x = node_x;
        tn.y = node_y;
        tn.dx = node_dx;
        tn.dy = node_dy;

        // child boxes
        findLimits(lefts[0]);
        tn.maxy2 = (short) lmaxy;
        tn.miny2 = (short) lminy;
        tn.minx2 = (short) lminx;
        tn.maxx2 = (short) lmaxx;
        findLimits(rights[0]);
        tn.maxy1 = (short) lmaxy;
        tn.miny1 = (short) lminy;
        tn.minx1 = (short) lminx;
        tn.maxx1 = (short) lmaxx;

        int nParent = countSegs(ts);
        int nL = (lefts[0] != null) ? countSegs(lefts[0]) : 0;
        int nR = (rights[0] != null) ? countSegs(rights[0]) : 0;

        if (nL == 0 || nR == 0 || nL >= nParent || nR >= nParent) {

            // Make a leaf from the non-empty side
            Structs.Seg src = (nL >= nR) ? lefts[0] : rights[0];
            if (src == null) src = ts;

            int ss = createSSector(src);

            // Use src bounds for both boxes
            findLimits(src);
            short bMaxY = (short) lmaxy, bMinY = (short) lminy, bMinX = (short) lminx, bMaxX = (short) lmaxx;
            tn.maxy1 = tn.maxy2 = bMaxY;
            tn.miny1 = tn.miny2 = bMinY;
            tn.minx1 = tn.minx2 = bMinX;
            tn.maxx1 = tn.maxx2 = bMaxX;

            tn.chright = (short) (0x8000 | ss);
            tn.chleft = (short) (0x8000 | ss);
            tn.nextr = tn.nextl = null;
            tn.nodeNum = (short) (num_nodes - 1);
            return tn;
        }

        // Left child
        if (isItConvex(lefts[0])) {
            tn.nextl = createNode(lefts[0]);
            tn.chleft = 0;
        } else {
            tn.nextl = null;
            tn.chleft = (short) (createSSector(lefts[0]) | 0x8000);
        }

        // Right child
        if (isItConvex(rights[0])) {
            tn.nextr = createNode(rights[0]);
            tn.chright = 0;
        } else {
            tn.nextr = null;
            tn.chright = (short) (createSSector(rights[0]) | 0x8000);
        }

        tn.nodeNum = (short) (num_nodes - 1);
        return tn;
    }

    public static void findLimits(Structs.Seg ts) {
        int minx = Integer.MAX_VALUE, miny = Integer.MAX_VALUE;
        int maxx = Integer.MIN_VALUE, maxy = Integer.MIN_VALUE;
        for (Structs.Seg s = ts; s != null; s = s.next) {
            int x1 = vertices[s.start].x, y1 = vertices[s.start].y;
            int x2 = vertices[s.end].x, y2 = vertices[s.end].y;
            if (x1 < minx) minx = x1;
            if (x2 < minx) minx = x2;
            if (y1 < miny) miny = y1;
            if (y2 < miny) miny = y2;
            if (x1 > maxx) maxx = x1;
            if (x2 > maxx) maxx = x2;
            if (y1 > maxy) maxy = y1;
            if (y2 > maxy) maxy = y2;
        }
        lminx = minx;
        lmaxx = maxx;
        lminy = miny;
        lmaxy = maxy;
    }

    public static int sectorOfSeg(Structs.Seg s) {
        Structs.LineDef ld = linedefs[s.linedef];
        short sd = (s.flip != 0) ? ld.sidedef2 : ld.sidedef1;
        if (sd == -1) return -1;            // <-- do NOT fall back
        return sidedefs[sd].sector;
    }

    public static boolean isItConvex(Structs.Seg ts) {
        if (ts == null) return false;
        int sector = sectorOfSeg(ts);

        for (Structs.Seg line = ts.next; line != null; line = line.next) {
            if (sectorOfSeg(line) != sector) return true;
        }

        for (Structs.Seg a = ts; a != null; a = a.next) {
            for (Structs.Seg b = ts; b != null; b = b.next) {
                if (b == a) continue;
                lsx = vertices[a.start].x;
                lsy = vertices[a.start].y;
                lex = vertices[a.end].x;
                ley = vertices[a.end].y;
                psx = vertices[b.start].x;
                psy = vertices[b.start].y;
                pex = vertices[b.end].x;
                pey = vertices[b.end].y;
                pdx = psx - pex;
                pdy = psy - pey;
                int val = doLinesIntersect();
                if ((val & 34) != 0) return true;
            }
        }
        return false;
    }

    private static short offsetAlong(Structs.LineDef ld, short segStartIdx) {
        Structs.Vertex v1 = vertices[ld.start];
        Structs.Vertex v2 = vertices[ld.end];
        Structs.Vertex vs = vertices[segStartIdx];

        long dx = (long) v2.x - v1.x;
        long dy = (long) v2.y - v1.y;
        long sx = (long) vs.x - v1.x;
        long sy = (long) vs.y - v1.y;

        double len = Math.hypot(dx, dy);
        if (len <= 0.0) return 0;
        long proj = Math.round((sx * dx + sy * dy) / len);
        if (proj < Short.MIN_VALUE) proj = Short.MIN_VALUE;
        if (proj > Short.MAX_VALUE) proj = Short.MAX_VALUE;
        return (short) proj;
    }

    private static int sideFacingPoint(Structs.LineDef ld, int cx, int cy) {
        Structs.Vertex v1 = vertices[ld.start];
        Structs.Vertex v2 = vertices[ld.end];
        long dx = (long) v2.x - v1.x;
        long dy = (long) v2.y - v1.y;
        long rx = (long) cx - v1.x;
        long ry = (long) cy - v1.y;
        long cross = dx * ry - dy * rx;
        return (cross <= 0) ? 0 : 1;
    }

    public static int createSSector(Structs.Seg ts) {
        if (ts == null) throw new IllegalStateException("createSSector called with null seg list");

        class Entry {
            Structs.PSeg ps;
            int sector;
            boolean oneSided;
        }
        ArrayList<Entry> local = new ArrayList<>();
        HashMap<Integer, Integer> votes = new HashMap<>();

        for (Structs.Seg s = ts; s != null; s = s.next) {
            Structs.LineDef ld = linedefs[s.linedef];

            int side = (s.flip & 1);

            short sdidx = (side == 0) ? ld.sidedef1 : ld.sidedef2;
            if (sdidx == -1) {
                short other = (side == 0) ? ld.sidedef2 : ld.sidedef1;
                if (other == -1) continue; // should not happen
                sdidx = other;
                side ^= 1;
            }
            int sector = sidedefs[sdidx].sector;
            boolean oneSided = (ld.sidedef1 == -1 || ld.sidedef2 == -1);

            Structs.PSeg ps = new Structs.PSeg();
            ps.start = s.start;
            ps.end = s.end;
            ps.angle = s.angle;
            ps.linedef = s.linedef;
            ps.flip = (short) side;
            ps.dist = offsetAlong(ld, s.start);

            Entry e = new Entry();
            e.ps = ps;
            e.sector = sector;
            e.oneSided = oneSided;
            local.add(e);

            int w = oneSided ? 2 : 1;
            votes.put(sector, votes.getOrDefault(sector, 0) + w);
        }

        int leafSector = 0;
        if (!votes.isEmpty()) {
            leafSector = votes.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).get().getKey();
        }

        // Put a seg from the leaf sector first
        int firstIdx = -1;
        for (int i = 0; i < local.size(); i++)
            if (local.get(i).oneSided && local.get(i).sector == leafSector) {
                firstIdx = i;
                break;
            }
        if (firstIdx < 0) for (int i = 0; i < local.size(); i++)
            if (local.get(i).sector == leafSector) {
                firstIdx = i;
                break;
            }
        if (firstIdx > 0) {
            Entry first = local.remove(firstIdx);
            local.add(0, first);
        }

        int first = num_psegs;
        for (Entry e : local) {
            if (num_psegs >= psegs.length) psegs = Arrays.copyOf(psegs, psegs.length * 2);
            psegs[num_psegs++] = e.ps;
        }

        if (num_ssectors >= ssectors.length) ssectors = Arrays.copyOf(ssectors, ssectors.length * 2);
        Structs.SSector ss = new Structs.SSector();
        ss.first = (short) first;
        ss.num = (short) (num_psegs - first);
        ssectors[num_ssectors++] = ss;

        return num_ssectors - 1;
    }

    public static void reverseNodes(Structs.Node tn) {
        if (tn == null) return;
        if ((tn.chright & 0x8000) == 0) {
            reverseNodes(tn.nextr);
            tn.chright = tn.nextr.nodeNum;
        }
        if ((tn.chleft & 0x8000) == 0) {
            reverseNodes(tn.nextl);
            tn.chleft = tn.nextl.nodeNum;
        }
        if (num_pnodes >= pnodes.length) pnodes = Arrays.copyOf(pnodes, pnodes.length * 2);
        Structs.PNode pn = new Structs.PNode();
        pn.x = tn.x;
        pn.y = tn.y;
        pn.dx = tn.dx;
        pn.dy = tn.dy;
        pn.maxy1 = tn.maxy1;
        pn.miny1 = tn.miny1;
        pn.minx1 = tn.minx1;
        pn.maxx1 = tn.maxx1;
        pn.maxy2 = tn.maxy2;
        pn.miny2 = tn.miny2;
        pn.minx2 = tn.minx2;
        pn.maxx2 = tn.maxx2;
        pn.chright = tn.chright;
        pn.chleft = tn.chleft;
        pnodes[pnodeIndex++] = pn;
        tn.nodeNum = (short) (num_pnodes++);
    }

    public static int doLinesIntersect() {
        short val = 0;
        long dx2 = psx - lsx;
        long dy2 = psy - lsy;
        long dx3 = psx - lex;
        long dy3 = psy - ley;

        long a = (long) pdy * dx2 - (long) pdx * dy2;
        long b = (long) pdy * dx3 - (long) pdx * dy3;

        if ((a < 0 && b > 0) || (a > 0 && b < 0)) {
            long x = psx - pdx;
            long y = psy - pdy;

            if (a < 0 && b > 0) {
                if (Math.abs(pdx) > Math.abs(pdy)) {
                    x = (x * a + (pdx >> 1)) / pdx;
                    y = (y * a + (pdx >> 1)) / pdx;
                } else {
                    x = (x * a + (pdy >> 1)) / pdy;
                    y = (y * a + (pdy >> 1)) / pdy;
                }
            } else {
                if (Math.abs(pdx) > Math.abs(pdy)) {
                    x = (x * b + (pdx >> 1)) / pdx;
                    y = (y * b + (pdx >> 1)) / pdx;
                } else {
                    x = (x * b + (pdy >> 1)) / pdy;
                    y = (y * b + (pdy >> 1)) / pdy;
                }
            }

            long dx2l = lsx - x;
            long dy2l = lsy - y;
            long dx3l = lex - x;
            long dy3l = ley - y;

            if (dx2l == 0 && dy2l == 0) a = 0;
            else if (dx2l * dx2l + dy2l * dy2l < 4) a = 0;

            if (dx3l == 0 && dy3l == 0) b = 0;
            else if (dx3l * dx3l + dy3l * dy3l < 4) b = 0;
        }

        if (a == 0) val |= 16;
        else if (a < 0) val |= 32;
        else val |= 64;
        if (b == 0) val |= 1;
        else if (b < 0) val |= 2;
        else val |= 4;

        return val;
    }

    public static short[] computeIntersection() {
        long dx1 = pex - psx;
        long dy1 = pey - psy;
        long dx2 = lex - lsx;
        long dy2 = ley - lsy;

        if ((dx1 == 0 && dy1 == 0) || (dx2 == 0 && dy2 == 0)) {
            throw new IllegalStateException("Invalid line encountered");
        }

        double X, Y, a1, b1, a2;
        if (dx1 == 0) {
            X = psx;
            if (dx2 == 0) return new short[]{(short) lsx, (short) lsy};
            a2 = (double) ((long) ley - lsy) / ((long) lex - lsx);
            Y = a2 * X + (lsy - a2 * lsx);
        } else if (dx2 == 0) {
            X = lsx;
            a1 = (double) ((long) pey - psy) / ((long) pex - psx);
            Y = a1 * X + (psy - a1 * psx);
        } else {
            a1 = (double) ((long) pey - psy) / ((long) pex - psx);
            a2 = (double) ((long) ley - lsy) / ((long) lex - lsx);
            if (a1 == a2) return new short[]{(short) lsx, (short) lsy};
            b1 = psy - a1 * psx;
            X = (lsy - a2 * lsx - b1) / (a1 - a2);
            Y = a1 * X + b1;
        }
        return new short[]{(short) Math.round(X), (short) Math.round(Y)};
    }

    public static int splitDist(Structs.Seg ts) {
        double dx = (double) vertices[linedefs[ts.linedef].start].x - (double) vertices[ts.start].x;
        double dy = (double) vertices[linedefs[ts.linedef].start].y - (double) vertices[ts.start].y;
        return (int) Math.sqrt(dx * dx + dy * dy);
    }

    public static int createBlockmap() {
        blockhead.minx = (short) (mapminx & -8);
        blockhead.miny = (short) (mapminy & -8);
        blockhead.xblocks = (short) (((mapmaxx - (mapminx & -8)) / 128) + 1);
        blockhead.yblocks = (short) (((mapmaxy - (mapminy & -8)) / 128) + 1);

        blockptrs_size = (blockhead.xblocks * blockhead.yblocks) * 2;
        blockptrs = new byte[blockptrs_size];
        ByteBuffer ptrs = ByteBuffer.wrap(blockptrs).order(ByteOrder.LITTLE_ENDIAN);

        List<Short> lists = new ArrayList<>();
        for (int y = 0; y < blockhead.yblocks; y++) {
            for (int x = 0; x < blockhead.xblocks; x++) {
                int blockoffsWords = 4 + (blockptrs_size / 2) + lists.size();
                ptrs.putShort((short) blockoffsWords);

                // Leading 0 word per block
                lists.add((short) 0);

                int xmin = (mapminx & -8) + (x * 128);
                int ymin = (mapminy & -8) + (y * 128);
                int xmax = xmin + 127;
                int ymax = ymin + 127;

                for (int n = 0; n < num_lines; n++) {
                    if (isLineDefInside(n, xmin, ymin, xmax, ymax) != 0) {
                        lists.add((short) n);
                    }
                }
                lists.add((short) -1);
            }
        }
        ByteBuffer bl = ByteBuffer.allocate(lists.size() * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : lists) bl.putShort(s);
        blocklists = bl.array();
        return blocklists.length;
    }

    public static int isLineDefInside(int ldnum, int xmin, int ymin, int xmax, int ymax) {
        int x1 = vertices[linedefs[ldnum].start].x;
        int y1 = vertices[linedefs[ldnum].start].y;
        int x2 = vertices[linedefs[ldnum].end].x;
        int y2 = vertices[linedefs[ldnum].end].y;

        int outcode1 = 0, outcode2 = 0;
        if (x1 < xmin) outcode1 |= 1;
        else if (x1 > xmax) outcode1 |= 2;
        if (y1 < ymin) outcode1 |= 4;
        else if (y1 > ymax) outcode1 |= 8;
        if (x2 < xmin) outcode2 |= 1;
        else if (x2 > xmax) outcode2 |= 2;
        if (y2 < ymin) outcode2 |= 4;
        else if (y2 > ymax) outcode2 |= 8;

        if ((outcode1 & outcode2) != 0) return 0;
        if (outcode1 == 0 && outcode2 == 0) return 1;
        return 1;
    }

    public static void writeOutPWad(String outPath) throws IOException {
        List<BSPWad.Lump> lumps = new ArrayList<>();
        String levelName = (selectedLevelName != null) ? selectedLevelName : "MAP01";
        lumps.add(new BSPWad.Lump(levelName, new byte[0]));

        ByteBuffer th = ByteBuffer.allocate(num_things * 10).order(ByteOrder.LITTLE_ENDIAN);
        for (Structs.Thing t : things) {
            th.putShort(t.x);
            th.putShort(t.y);
            th.putShort(t.angle);
            th.putShort(t.type);
            th.putShort(t.options);
        }
        lumps.add(new BSPWad.Lump("THINGS", th.array()));

        ByteBuffer ldb = ByteBuffer.allocate(num_lines * 14).order(ByteOrder.LITTLE_ENDIAN);
        for (Structs.LineDef ld : linedefs) {
            ldb.putShort(ld.start);
            ldb.putShort(ld.end);
            ldb.putShort(ld.flags);
            ldb.putShort(ld.type);
            ldb.putShort(ld.tag);
            ldb.putShort(ld.sidedef1);
            ldb.putShort(ld.sidedef2);
        }
        lumps.add(new BSPWad.Lump("LINEDEFS", ldb.array()));

        ByteBuffer sdb = ByteBuffer.allocate(num_sides * 30).order(ByteOrder.LITTLE_ENDIAN);
        for (Structs.SideDef sd : sidedefs) {
            sdb.putShort(sd.xoff);
            sdb.putShort(sd.yoff);
            sdb.put(sd.tex1);
            sdb.put(sd.tex2);
            sdb.put(sd.tex3);
            sdb.putShort(sd.sector);
        }
        lumps.add(new BSPWad.Lump("SIDEDEFS", sdb.array()));

        ByteBuffer vtx = ByteBuffer.allocate(num_verts * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < num_verts; i++) {
            vtx.putShort(vertices[i].x);
            vtx.putShort(vertices[i].y);
        }
        lumps.add(new BSPWad.Lump("VERTEXES", vtx.array()));

        ByteBuffer segb = ByteBuffer.allocate(num_psegs * 12).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < num_psegs; i++) {
            Structs.PSeg s = psegs[i];
            segb.putShort(s.start);
            segb.putShort(s.end);
            segb.putShort((short) (s.angle & 0xFFFF));
            segb.putShort(s.linedef);
            segb.putShort(s.flip);
            segb.putShort((short) (s.dist & 0xFFFF));
        }
        lumps.add(new BSPWad.Lump("SEGS", segb.array()));

        ByteBuffer ssb = ByteBuffer.allocate(num_ssectors * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < num_ssectors; i++) {
            Structs.SSector ss = ssectors[i];
            ssb.putShort(ss.num);
            ssb.putShort(ss.first);
        }
        lumps.add(new BSPWad.Lump("SSECTORS", ssb.array()));

        ByteBuffer ndb = ByteBuffer.allocate(num_pnodes * 28).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < num_pnodes; i++) {
            Structs.PNode n = pnodes[i];
            ndb.putShort(n.x);
            ndb.putShort(n.y);
            ndb.putShort(n.dx);
            ndb.putShort(n.dy);
            ndb.putShort(n.maxy1);
            ndb.putShort(n.miny1);
            ndb.putShort(n.minx1);
            ndb.putShort(n.maxx1);
            ndb.putShort(n.maxy2);
            ndb.putShort(n.miny2);
            ndb.putShort(n.minx2);
            ndb.putShort(n.maxx2);
            ndb.putShort(n.chright);
            ndb.putShort(n.chleft);
        }
        lumps.add(new BSPWad.Lump("NODES", ndb.array()));

        ByteBuffer secb = ByteBuffer.allocate(num_sects * 26).order(ByteOrder.LITTLE_ENDIAN);
        for (Structs.Sector s : sectors) {
            secb.putShort(s.floorh);
            secb.putShort(s.ceilh);
            secb.put(s.floort);
            secb.put(s.ceilt);
            secb.putShort(s.light);
            secb.putShort(s.special);
            secb.putShort(s.tag);
        }
        lumps.add(new BSPWad.Lump("SECTORS", secb.array()));

        int rejectSize = (num_sects * num_sects + 7) / 8;
        lumps.add(new BSPWad.Lump("REJECT", new byte[rejectSize]));

        int blockmapSize = createBlockmap();
        ByteBuffer blkh = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        blkh.putShort(blockhead.minx);
        blkh.putShort(blockhead.miny);
        blkh.putShort(blockhead.xblocks);
        blkh.putShort(blockhead.yblocks);
        byte[] blk = new byte[8 + blockptrs.length + blocklists.length];
        System.arraycopy(blkh.array(), 0, blk, 0, 8);
        System.arraycopy(blockptrs, 0, blk, 8, blockptrs.length);
        System.arraycopy(blocklists, 0, blk, 8 + blockptrs.length, blocklists.length);
        lumps.add(new BSPWad.Lump("BLOCKMAP", blk));

        BSPWad.writePWad(new File(outPath), lumps);
    }

    public static void build(String in, String out, String mapName) throws Exception {
        openWad(in);
        selectedMarkerIdx = -1;
        selectedLevelName = mapName;
        for (int i = 0; i < direc.length; i++) {
            String n = new String(direc[i].name, StandardCharsets.US_ASCII).trim();
            if (direc[i].length == 0 && n.equals(mapName)) {
                selectedMarkerIdx = i;
                break;
            }
        }
        if (selectedMarkerIdx < 0) throw new IllegalStateException("Map marker not found: " + mapName);

        getThings();
        getLinedefs();
        getVertexes();
        getSidedefs();
        getSectors();

        tsegs = createSegs();
        findLimits(tsegs);
        mapminx = lminx;
        mapmaxx = lmaxx;
        mapminy = lminy;
        mapmaxy = lmaxy;

        System.out.printf("Map %s goes from X (%d,%d) Y (%d,%d)%n", mapName, lminx, lmaxx, lminy, lmaxy);
        num_nodes = 0;
        nodelist = createNode(tsegs);
        System.out.printf("%d NODES created, with %d SSECTORS.%n", num_nodes, num_ssectors);

        num_pnodes = 0;
        pnodeIndex = 0;
        reverseNodes(nodelist);

        writeOutPWad(out);
    }

    public static void build(String in, String out) throws Exception {
        openWad(in);
        selectedMarkerIdx = -1;
        selectedLevelName = null;
        getThings();
        getLinedefs();
        getVertexes();
        getSidedefs();
        getSectors();

        tsegs = createSegs();
        findLimits(tsegs);
        mapminx = lminx;
        mapmaxx = lmaxx;
        mapminy = lminy;
        mapmaxy = lmaxy;

        System.out.printf("Map goes from X (%d,%d) Y (%d,%d)%n", lminx, lmaxx, lminy, lmaxy);
        num_nodes = 0;
        nodelist = createNode(tsegs);
        System.out.printf("%d NODES created, with %d SSECTORS.%n", num_nodes, num_ssectors);

        num_pnodes = 0;
        pnodeIndex = 0;
        reverseNodes(nodelist);

        writeOutPWad(out);
    }
}
