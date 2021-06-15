package ru.hse.servers;

import org.apache.commons.lang.ArrayUtils;
import ru.hse.servers.protocol.message.Message;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

import static java.lang.Math.min;

public class Utils {
    public static void writeMessage(DataOutputStream outputStream, Message message) throws IOException {
        byte[] data = message.toByteArray();
        outputStream.writeInt(data.length);
        outputStream.write(data);
    }

    public static void writeMessageToChannel(SocketChannel channel, Message message) throws IOException {
        byte[] data = message.toByteArray();
        channel.write(ByteBuffer.allocate(data.length + 4).putInt(data.length).put(data).flip());
    }

    public static Message readMessage(DataInputStream inputStream) throws IOException {
        int len = inputStream.readInt();
        byte[] data = new byte[0];
        byte[] buf = new byte[1024];
        while (len > 0) {
            int read = inputStream.read(buf, 0, min(1024, len));
            if (read < 1024) {
                buf = Arrays.copyOfRange(buf, 0, read);
            }
            data = ArrayUtils.addAll(data, buf);
            len -= read;
        }
        return Message.parseFrom(data);
    }

    public static Message readMessageFromChannel(SocketChannel channel) throws IOException {
        ByteBuffer sizeBuf = ByteBuffer.allocate(4);
        while (sizeBuf.hasRemaining()) {
            channel.read(sizeBuf);
        }
        sizeBuf.flip();
        int len = sizeBuf.getInt();
        byte[] data = new byte[len];
        ByteBuffer buf = ByteBuffer.allocate(len);
        while (buf.hasRemaining()) {
            channel.read(buf);
        }
        buf.flip();
        buf.get(data);
        return Message.parseFrom(data);
    }
}
