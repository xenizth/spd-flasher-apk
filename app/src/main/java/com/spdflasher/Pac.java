package com.spdflasher;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Unisoc / Spreadtrum .pac firmware packages (the format ResearchDownload and
 * SPD Research write).{@code PartHeader} strings are UTF-16LE, the package layout is
 * 2124-byte header + 2580-byte entries, magic 0xFFFafffa.
 *
 * The plan produced here is executed through spd_dump: every data image becomes a write
 * ("w") of the device partition named in the package's embedded XML scheme, every erase
 * operation becomes an erase ("e"). FDL/FDL2 payloads are ignored - the app always uses
 * its own configured loader pair.
 */
final class Pac {
    private static final int HDR_SIZE = 2124, ENTRY_SIZE = 2580;
    private static final int MAGIC = 0xFFFafffa;
    private static final int MAX_XML = 8 << 20;
    private static final Pattern PART = Pattern.compile("^[A-Za-z0-9_\\-]+$");

    /** One flash action decoded from the package. */
    static final class Op {
        final String part;      // device partition the scheme maps this file to
        final String fileName;  // image name inside the package (for display)
        final boolean erase;    // true: erase only, no payload
        final long offset;      // payload offset inside the .pac (-1 for erase)
        final long size;        // payload size (0 for erase)
        Op(String part, String fileName, boolean erase, long offset, long size) {
            this.part = part; this.fileName = fileName; this.erase = erase; this.offset = offset; this.size = size;
        }
        String sizeText() { return erase ? "erase" : MainActivity.sizeText(size); }
    }

    static final class Plan {
        final String product;
        String xmlName = "";
        final List<Op> ops = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        Plan(String product) { this.product = product; }
        int writes() { int n = 0; for (Op o : ops) if (!o.erase) n++; return n; }
        int erases() { int n = 0; for (Op o : ops) if (o.erase) n++; return n; }
    }

    private static final class Entry {
        final String id, fileName;
        final long size, offset;
        final int flag;
        Entry(String id, String fileName, long size, long offset, int flag) {
            this.id = id; this.fileName = fileName; this.size = size; this.offset = offset; this.flag = flag;
        }
    }

    private static int le32(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static String utf16(byte[] b, int off, int maxChars) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxChars && off + 2 * i + 1 < b.length; i++) {
            int c = (b[off + 2 * i] & 0xFF) | ((b[off + 2 * i + 1] & 0xFF) << 8);
            if (c == 0) break;
            sb.append((char) c);
        }
        return sb.toString().trim();
    }

    static Plan parse(File f) throws IOException {
        if (f == null || !f.isFile()) throw new IOException("the package file is gone");
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            long len = r.length();
            if (len < HDR_SIZE + ENTRY_SIZE) throw new IOException("file is too small to be a .pac package");
            byte[] h = new byte[HDR_SIZE];
            r.readFully(h);
            if (le32(h, 0x844) != MAGIC) throw new IOException("not a .pac package (magic mismatch)");
            int count = le32(h, 0x434), listStart = le32(h, 0x438);
            if (count < 1 || count > 128) throw new IOException("bad package file count: " + count);
            if (listStart < HDR_SIZE || listStart + (long) count * ENTRY_SIZE > len)
                throw new IOException("corrupt package directory");

            Plan p = new Plan(utf16(h, 0x34, 256));
            Map<String, Entry> byId = new HashMap<>();
            Entry xml = null;
            byte[] ent = new byte[ENTRY_SIZE];
            for (int i = 0; i < count; i++) {
                r.seek(listStart + (long) i * ENTRY_SIZE);
                r.readFully(ent);
                long size = ((long) le32(ent, 0x5FC) << 32) | (le32(ent, 0x604) & 0xFFFFFFFFL);
                long off = ((long) le32(ent, 0x600) << 32) | (le32(ent, 0x610) & 0xFFFFFFFFL);
                int flag = le32(ent, 0x608);
                Entry e = new Entry(utf16(ent, 4, 256), utf16(ent, 0x204, 256), size, off, flag);
                if (e.size > 0 && (e.offset < HDR_SIZE + (long) count * ENTRY_SIZE || e.offset + e.size > len))
                    throw new IOException("image '" + e.fileName + "' lies outside the package (corrupt .pac?)");
                if (!e.id.isEmpty()) byId.putIfAbsent(e.id.toUpperCase(Locale.US), e);
                if (xml == null && e.fileName.toLowerCase(Locale.US).endsWith(".xml")) xml = e;
            }
            if (xml == null)
                throw new IOException("no scheme XML found - this app flashes ResearchDownload-style .pac files only");
            if (xml.size <= 0 || xml.size > MAX_XML)
                throw new IOException("scheme XML is missing or too large (" + xml.size + " bytes)");
            byte[] xb = new byte[(int) xml.size];
            r.seek(xml.offset);
            r.readFully(xb);
            p.xmlName = xml.fileName;
            parseScheme(decodeText(xb), byId, p);
            if (p.ops.isEmpty()) throw new IOException("the scheme holds nothing to flash for this package");
            return p;
        }
    }

    /** UTF-8, falling back to UTF-16LE when the blob is full of zero bytes. */
    private static String decodeText(byte[] b) {
        int zeros = 0;
        for (int i = 0; i < Math.min(400, b.length); i++) if (b[i] == 0) zeros++;
        Charset cs = zeros > 100 ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_8;
        return new String(b, cs).replaceAll("\0", "");
    }

    private static final Pattern FILE_TAG = Pattern.compile("<file\\b([^>]*)>(.*?)</file>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTR = Pattern.compile("\\b(id|type)\\s*=\\s*\"([^\"]*)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ID_TEXT = Pattern.compile("<id\\b[^>]*>([^<]+)</id>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALIAS_TEXT = Pattern.compile("<idalias\\b[^>]*>([^<]+)</idalias>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPE_TEXT = Pattern.compile("<type\\b[^>]*>([^<]+)</type>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHECK_TEXT = Pattern.compile("<checkflag\\b[^>]*>\\s*([0-9]+)\\s*</checkflag>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCK_ID = Pattern.compile("<block\\b[^>]*\\bid\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ERASE_TYPE = Pattern.compile("eraseflash", Pattern.CASE_INSENSITIVE);
    private static final Pattern OP_ERASE = Pattern.compile("<operation\\b[^>]*type\\s*=\\s*\"erase", Pattern.CASE_INSENSITIVE);
    private static final Pattern FDL_TYPE = Pattern.compile("fdl", Pattern.CASE_INSENSITIVE);

    private static void parseScheme(String xml, Map<String, Entry> byId, Plan p) {
        Matcher files = FILE_TAG.matcher(xml);
        while (files.find()) {
            String open = files.group(1), body = files.group(2);
            String id = attr(open, "id"), type = attr(open, "type");
            if (type == null) { Matcher m = TYPE_TEXT.matcher(body); type = m.find() ? m.group(1).trim() : null; }
            if (id == null) { Matcher m = ID_TEXT.matcher(body); id = m.find() ? m.group(1).trim() : null; }
            String alias = null;
            Matcher am = ALIAS_TEXT.matcher(body);
            if (am.find()) alias = am.group(1).trim();
            if (id == null || id.isEmpty()) { p.notes.add("scheme file without an ID - skipped"); continue; }

            if (type != null && FDL_TYPE.matcher(type).find()) {
                p.notes.add("loader '" + id + "' ignored - the app's own FDL pair is used");
                continue;
            }
            String block = null;
            Matcher bm = BLOCK_ID.matcher(body);
            if (bm.find()) block = bm.group(1).trim();
            if (block == null || block.isEmpty() || !PART.matcher(block).matches()) {
                p.notes.add("'" + id + "' has no partition mapping - skipped");
                continue;
            }
            boolean erase = type != null && ERASE_TYPE.matcher(type).find()
                    || OP_ERASE.matcher(body).find();
            Entry e = byId.get(id.toUpperCase(Locale.US));
            if (e == null && alias != null) e = byId.get(alias.toUpperCase(Locale.US));
            boolean hasData = e != null && e.flag == 1 && e.size > 0
                    && !(e.fileName.toLowerCase(Locale.US).endsWith(".xml"));
            if (erase) p.ops.add(new Op(block, id, true, -1, 0));
            if (hasData) {
                p.ops.add(new Op(block, e.fileName, false, e.offset, e.size));
            } else {
                boolean mandatory;
                Matcher cm = CHECK_TEXT.matcher(body);
                int attrCheck = attrInt(open, "checkflag");
                mandatory = (cm.find() ? Integer.parseInt(cm.group(1)) : attrCheck) == 1;
                if (mandatory && !erase)
                    throw new IllegalArgumentException("mandatory image '" + id + "' is missing from the package");
                if (!erase)
                    p.notes.add("'" + id + "': image not in the package - skipped");
                else if (e != null && e.flag == 1 && e.size > 0)
                    p.notes.add("image for erase-only '" + id + "' ignored (" + e.fileName + ")");
            }
        }
    }

    private static String attr(String tag, String name) {
        Matcher m = Pattern.compile("\\b" + name + "\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(tag);
        return m.find() ? m.group(1).trim() : null;
    }

    private static int attrInt(String tag, String name) {
        Matcher m = Pattern.compile("\\b" + name + "\\s*=\\s*\"?([0-9]+)", Pattern.CASE_INSENSITIVE).matcher(tag);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** Streams one image out of the package into {@code dest}. */
    static void extract(File pac, Op o, File dest) throws IOException {
        try (RandomAccessFile r = new RandomAccessFile(pac, "r")) {
            long total = r.length();
            if (o.offset < 0 || o.size <= 0 || o.offset > total || o.offset + o.size > total)
                throw new IOException("image '" + o.fileName + "' lies outside the package");
            r.seek(o.offset);
            try (java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
                byte[] buf = new byte[1 << 20];
                long done = 0;
                while (done < o.size) {
                    long left = o.size - done;
                    int n = r.read(buf, 0, (int) Math.min(buf.length, left));
                    if (n < 0) throw new IOException("unexpected end of package");
                    out.write(buf, 0, n);
                    done += n;
                }
            }
        }
    }
}
