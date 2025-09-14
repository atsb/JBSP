# JBSP
JBSP - a conservative Java rewrite of BSP 1.1 for DOOM/DOOM II

JBSP is a small, dependency-free Java toolkit that reads a map from a WAD and (re)builds its SEGS / SSECTORS / NODES / BLOCKMAP / REJECT using a straightforward BSP algorithm. It includes a minimal Swing UI

Compatible with Java 17+

# Design

* Names stored as US-ASCII, geometry serialized little-endian.

* Uses in-memory arrays for lumps.

* Preallocated working arrays (e.g., SSECTORS/PNODES/PSEGS).

# How BSP works

1. Read map marker + base geometry from the input WAD: THINGS, LINEDEFS, SIDEDEFS, SECTORS, VERTEXES.

2. Create SEGS from linedefs/sidedefs; compute bounds.

3. BSP recursion

4. PickNode selects a partition candidate (balance vs. splits).

5. MakeNode.divideSegs classifies/splits segs, recurses.

6. Leaf sets become SSECTORS.

7. Emit lumps: SEGS, SSECTORS, NODES (BLOCKMAP, REJECT) and write a new PWAD.
   
<img width="835" height="559" alt="Screenshot 2025-09-14 021231" src="https://github.com/user-attachments/assets/34bfd04b-7101-4d02-8041-c487c44df9cc" />
