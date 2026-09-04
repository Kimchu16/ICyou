package com.matissjurevics.icyou.render.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;

/** Length-delimits vanilla play packets inside one digest-protected snapshot. */
public final class ScenePacketStream {

    public static final int MAX_PACKETS = 4096;
    public static final int MAX_PACKET_BYTES = 4 * 1024 * 1024;

    private ScenePacketStream() {
    }

    public static byte[] encode(List<byte[]> packets) {
        Objects.requireNonNull(packets, "packets");
        if (packets.isEmpty() || packets.size() > MAX_PACKETS) {
            throw new IllegalArgumentException("Invalid scene packet count");
        }
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(packets.size());
            for (byte[] packet : packets) {
                byte[] copy = requirePacket(packet);
                buffer.writeVarInt(copy.length);
                buffer.writeBytes(copy);
                if (buffer.readableBytes() > SceneSnapshotProtocol.MAX_SNAPSHOT_BYTES) {
                    throw new IllegalArgumentException("Scene packet stream is too large");
                }
            }
            byte[] result = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), result);
            return result;
        } finally {
            buffer.release();
        }
    }

    public static List<byte[]> decode(byte[] stream) {
        Objects.requireNonNull(stream, "stream");
        if (stream.length < 1 || stream.length > SceneSnapshotProtocol.MAX_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException("Invalid scene packet stream size");
        }
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.wrappedBuffer(stream));
        try {
            int count = buffer.readVarInt();
            if (count < 1 || count > MAX_PACKETS) {
                throw new IllegalArgumentException("Invalid scene packet count");
            }
            List<byte[]> packets = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                int size = buffer.readVarInt();
                if (size < 1 || size > MAX_PACKET_BYTES || size > buffer.readableBytes()) {
                    throw new IllegalArgumentException("Invalid scene packet size");
                }
                byte[] packet = new byte[size];
                buffer.readBytes(packet);
                packets.add(packet);
            }
            if (buffer.isReadable()) {
                throw new IllegalArgumentException("Trailing scene packet data");
            }
            return List.copyOf(packets);
        } catch (IndexOutOfBoundsException error) {
            throw new IllegalArgumentException("Truncated scene packet stream", error);
        } finally {
            buffer.release();
        }
    }

    private static byte[] requirePacket(byte[] packet) {
        Objects.requireNonNull(packet, "packet");
        if (packet.length < 1 || packet.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Invalid scene packet size");
        }
        return packet.clone();
    }
}
