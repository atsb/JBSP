package com.doom.bsp;

import static com.doom.bsp.BSP.*;
import static com.doom.bsp.MakeNode.doLinesIntersect;

public class PickNode {
    public static Structs.Seg pickNode(Structs.Seg ts) {
        int bestgrade = 32767;
        Structs.Seg best = ts;

        for (Structs.Seg part = ts; part != null; part = part.next) {
            progress();

            psx = vertices[part.start].x;
            psy = vertices[part.start].y;
            pex = vertices[part.end].x;
            pey = vertices[part.end].y;
            pdx = psx - pex;
            pdy = psy - pey;

            int num_splits = 0, num_left = 0, num_right = 0;
            int seg_count = 0;

            for (Structs.Seg check = ts; check != null; check = check.next) {
                seg_count++;
                if (check == part) {
                    num_right++;
                    continue;
                }

                lsx = vertices[check.start].x;
                lsy = vertices[check.start].y;
                lex = vertices[check.end].x;
                ley = vertices[check.end].y;

                int val = doLinesIntersect();

                if (((val & 2) != 0 && (val & 64) != 0) || ((val & 4) != 0 && (val & 32) != 0)) {
                    num_splits++;
                    num_left++;
                    num_right++;
                } else {
                    if ((val & 1) != 0 && (val & 16) != 0) {
                        int bangle = part.angle;
                        int cangle = check.angle;
                        int cangle2 = (cangle + 32768) & 0xFFFF;

                        if (bangle == cangle) {
                            num_right++;
                        } else if (bangle == cangle2) {
                            num_left++;
                        } else {
                            if ((val & 2) != 0 || (val & 32) != 0) num_left++;
                            else num_right++;
                        }
                    } else {
                        if ((val & 4) != 0 || (val & 64) != 0) num_right++;
                        if ((val & 2) != 0 || (val & 32) != 0) num_left++;
                    }
                }
            }

            if (num_right > 0 && num_left > 0) {
                int maxSide = Math.max(num_right, num_left);
                int newSplits = (num_right + num_left) - seg_count;
                int grade = maxSide + newSplits * 8;

                if (grade < bestgrade) {
                    bestgrade = grade;
                    best = part;
                }
            }
        }
        return best;
    }
}
