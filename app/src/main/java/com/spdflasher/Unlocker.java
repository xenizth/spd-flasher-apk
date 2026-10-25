package com.spdflasher;

import java.util.Arrays;

/**
 * Byte-level port of TomKing062's CVE-2022-38694_unlock_bootloader patch tools
 * (chsize.c, gen_spl-unlock.c and gen_spl-unlock-legacy.c), MIT licence.
 *
 * The dumped splloader is trusted-firmware ("DHTB") format: a small header
 * block followed by code. The patchers figure out the real image size from the
 * "size" records in the header and trim the partition padding, then turn the
 * signature-verification failure branch of the next-stage (do_cboot) check
 * into ARM64 NOPs, so unsigned loaders can boot.
 */
final class Unlocker {
    private Unlocker() { }

    static final int DHTB = 0x42544844;   // "DHTB" little-endian
    static final int NOP = 0xD503201F;

    static class PatchException extends Exception {
        PatchException(String m) { super(m); }
    }

    static final class SplResult {
        /** Trimmed (and patched) SPL image ready to write or download. */
        final byte[] data;
        /** Number of signature-check sites NOP-ed out. */
        final int patches;
        /** True when the image was already exactly sized, so nothing was done. */
        final boolean alreadySized;
        SplResult(byte[] data, int patches, boolean alreadySized) {
            this.data = data; this.patches = patches; this.alreadySized = alreadySized;
        }
    }

    private static int u32(byte[] b, int o) {
        return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8 | (b[o + 2] & 0xFF) << 16 | (b[o + 3] & 0xFF) << 24;
    }

    private static int u16(byte[] b, int o) {
        return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8;
    }

    private static void w32(byte[] b, int o, int v) {
        b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); b[o + 2] = (byte) (v >> 16); b[o + 3] = (byte) (v >> 24);
    }

    /** chsize.c: cut the DHTB image down to its real size (drops trailing zeros / padding). */
    static byte[] chsize(byte[] mem) throws PatchException {
        return Arrays.copyOf(mem, realSize(mem));
    }

    /**
     * gen_spl-unlock.c (Android 11+): trim to real size and NOP every
     *   BL <verify> ; CBZ W0, <fail+2> ; MOV W0, #v
     * sequence in the code region, so the next stage's signature check is skipped.
     */
    static SplResult genSplUnlock(byte[] mem) throws PatchException {
        int size = realSize(mem);
        int hdr = u32(mem, 0x30);
        if ((hdr & 0xFFFFFFFFL) + 0x260 >= mem.length) return new SplResult(mem.clone(), 0, true);
        byte[] out = Arrays.copyOf(mem, size);
        int patched = 0;
        int limit = Math.min(hdr + 0x200, size);
        for (int off = 0x200; off + 12 <= limit; off += 4) {
            if (u32(out, off) == 0x34000060                       // CBZ W0, <imm>
                    && (u32(out, off - 4) >>> 8) == 0x940000      // BL in a BL+CBZ pair
                    && u16(out, off + 6) == 0x5280) {             // MOVZ W0, ...
                w32(out, off - 4, NOP);
                w32(out, off, NOP);
                w32(out, off + 4, NOP);
                w32(out, off + 8, NOP);
                off += 8;
                patched++;
            }
        }
        return new SplResult(out, patched, false);
    }

    /**
     * gen_spl-unlock-legacy.c (Android 8 to 10): NOP every
     *   BL <verify> ; CBZ W0, <fail> ; B <fail_cont>
     * run from the first BL of the sequence up to the end (offset + 0xC).
     */
    static SplResult genSplUnlockLegacy(byte[] mem) throws PatchException {
        int size = realSize(mem);
        int hdr = u32(mem, 0x30);
        if ((hdr & 0xFFFFFFFFL) + 0x260 >= mem.length) return new SplResult(mem.clone(), 0, true);
        byte[] out = Arrays.copyOf(mem, size);
        int patched = 0;
        int end = 0, start = 0;
        int limit = Math.min(hdr + 0x200, size);
        for (int off = 0x200; off + 12 <= limit; off += 4) {
            if (u16(out, off + 2) == 0x9400                       // BL ...
                    && u16(out, off + 6) == 0x3400                // CBZ ...
                    && u32(out, off + 8) == 0x14000000) {         // B ...
                end = off + 0xC;
            }
            if (end != 0) {
                for (int i = off - 4; i >= 0x200; i -= 4) {
                    if (u16(out, i + 2) == 0x9400 && u16(out, i + 6) == 0x3400) {
                        start = i;
                        break;
                    }
                }
                if (start != 0) {
                    for (int i = start; i < end; i += 4) w32(out, i, NOP);
                    patched += (end - start) / 4;
                    start = 0;
                    end = 0;
                } else {
                    end = 0;
                }
            }
        }
        return new SplResult(out, patched, false);
    }

    /**
     * Real image size hidden in the trusted-firmware header (offset 0x30 is an
     * anchor; several sized records follow at hdr+0x200, first valid wins).
     */
    private static int realSize(byte[] mem) throws PatchException {
        if (mem.length < 0x260) throw new PatchException("file is too short to be SPRD trusted firmware");
        if (u32(mem, 0) != DHTB) throw new PatchException("The file is not sprd trusted firmware");
        int hdr = u32(mem, 0x30);
        if (hdr == 0) throw new PatchException("broken sprd trusted firmware");
        if ((hdr & 0xFFFFFFFFL) + 0x260 >= mem.length) return mem.length;
        int base = hdr + 0x200;
        long a, b;
        a = u32(mem, base + 0x50) & 0xFFFFFFFFL; b = u32(mem, base + 0x58) & 0xFFFFFFFFL;
        if (a != 0 && b != 0) return checkSize(a + b);
        a = u32(mem, base + 0x30) & 0xFFFFFFFFL; b = u32(mem, base + 0x38) & 0xFFFFFFFFL;
        if (a != 0 && b != 0) return checkSize(a + b);
        a = u32(mem, base + 0x20) & 0xFFFFFFFFL; b = u32(mem, base + 0x28) & 0xFFFFFFFFL;
        if (a != 0 && b != 0) return checkSize(a + b);
        return checkSize((hdr & 0xFFFFFFFFL) + 0x200);
    }

    private static int checkSize(long size) throws PatchException {
        if (size > Integer.MAX_VALUE) throw new PatchException("image size out of range");
        return (int) size;
    }

    /** true when the first {@code len} bytes are all zero (locked unlock-state stamp). */
    static boolean stampAllZero(byte[] b, int len) {
        int n = Math.min(len, b.length);
        for (int i = 0; i < n; i++) if (b[i] != 0) return false;
        return true;
    }
}
