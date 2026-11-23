package xuro;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** servers.dat schreiben (NBT) und Serverstatus per Minecraft-Ping abfragen. */
public final class Servers {
    private Servers() {}

    /** Legt servers.dat mit dem Server an, falls es noch keine gibt. acceptTextures=1: Server-Resource-Pack wird automatisch geladen. */
    public static void ensure(Path instance, String name, String host) throws IOException {
        Path f = instance.resolve("servers.dat");
        if (Files.exists(f)) return;
        Files.createDirectories(instance);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bo);
        o.writeByte(10); o.writeUTF("");                 // Root-Compound
        o.writeByte(9); o.writeUTF("servers");           // Liste
        o.writeByte(10); o.writeInt(1);                  // 1 Compound
        o.writeByte(1); o.writeUTF("acceptTextures"); o.writeByte(1);
        o.writeByte(8); o.writeUTF("ip"); o.writeUTF(host);
        o.writeByte(8); o.writeUTF("name"); o.writeUTF(name);
        o.writeByte(0);                                  // Ende Server
        o.writeByte(0);                                  // Ende Root
        Files.write(f, bo.toByteArray());
    }

    public static final class Status {
        public boolean online; public int players, max; public String version = "", motd = ""; public long pingMs;
    }

    static void varint(DataOutputStream o, int v) throws IOException {
        while ((v & ~0x7F) != 0) { o.writeByte((v & 0x7F) | 0x80); v >>>= 7; }
        o.writeByte(v);
    }

    static int readVarint(DataInputStream in) throws IOException {
        int n = 0, shift = 0, b;
        do {
            b = in.readUnsignedByte();
            n |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) throw new IOException("VarInt zu lang");
        } while ((b & 0x80) != 0);
        return n;
    }

    public static Status ping(String host, int port) {
        Status st = new Status();
        try (Socket s = new Socket()) {
            long t0 = System.nanoTime();
            s.connect(new InetSocketAddress(host, port), 4000);
            s.setSoTimeout(4000);
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            DataInputStream in = new DataInputStream(s.getInputStream());
            ByteArrayOutputStream hb = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(hb);
            varint(h, 0); varint(h, 767);
            byte[] hostB = host.getBytes(StandardCharsets.UTF_8);
            varint(h, hostB.length); h.write(hostB); h.writeShort(port); varint(h, 1);
            varint(out, hb.size()); out.write(hb.toByteArray());
            out.writeByte(1); out.writeByte(0); // Status-Request
            out.flush();
            readVarint(in);                      // Paketlänge
            if (readVarint(in) != 0) throw new IOException("falsches Paket");
            int len = readVarint(in);
            byte[] data = new byte[len];
            in.readFully(data);
            st.pingMs = (System.nanoTime() - t0) / 1_000_000;
            Map<String, Object> j = Json.o(Json.parse(new String(data, StandardCharsets.UTF_8)));
            st.players = (int) Json.l(Json.at(j, "players", "online"), 0);
            st.max = (int) Json.l(Json.at(j, "players", "max"), 0);
            st.version = Json.s(Json.at(j, "version", "name"), "");
            st.motd = motd(j.get("description"));
            st.online = true;
        } catch (Exception e) { st.online = false; }
        return st;
    }

    static String motd(Object d) { return strip(raw(d)); }

    private static String raw(Object d) {
        if (d == null) return "";
        if (d instanceof String) return (String) d;
        StringBuilder b = new StringBuilder();
        Map<String, Object> m = Json.o(d);
        b.append(Json.s(m.get("text"), ""));
        for (Object e : Json.a(m.get("extra"))) b.append(raw(e));
        return b.toString();
    }

    static String strip(String s) { return s.replaceAll("§.", "").replaceAll("\\s*\\n\\s*", " ").trim(); }
}
