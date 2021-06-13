package ru.hse.servers.architectures;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.commons.lang.ArrayUtils;
import ru.hse.servers.Constants;
import ru.hse.servers.TestConfig;
import ru.hse.servers.TimeCollector;
import ru.hse.servers.protocol.message.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class AsynchronousServer extends AbstractServer {
    private final TestConfig config;
    private final TimeCollector collector;
    private volatile boolean isWorking = true;
    private final ReentrantLock waitLock = new ReentrantLock();
    private final Condition finishCondition = waitLock.newCondition();

    public AsynchronousServer(TestConfig config, TimeCollector collector) {
        this.config = config;
        this.collector = collector;
    }

    @Override
    public void start() throws IOException {
        try (AsynchronousServerSocketChannel acceptChannel = AsynchronousServerSocketChannel.open()) {
            acceptChannel.bind(new InetSocketAddress(Constants.PORT));
            acceptChannel.accept(acceptChannel, new AcceptHandler());
            try {
                waitLock.lock();
                while (isWorking) {
                    finishCondition.await();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                waitLock.unlock();
            }
        }
    }

    @Override
    public void stop() throws IOException {
        try {
            waitLock.lock();
            isWorking = false;
            finishCondition.signal();
        } finally {
            waitLock.unlock();
            workers.shutdownNow();
        }
    }

    private static class ClientHandler {
        private int messageLen;
        private int numberOfTasks;
        private int tasksCompleted;
        byte[] message = new byte[0];
        public ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        public ByteBuffer writeBuffer;
        public List<Integer> result;
        public final AsynchronousSocketChannel channel;

        private ClientHandler(AsynchronousSocketChannel channel) {
            this.channel = channel;
        }

        public void fillMessage() {
            readBuffer.flip();

            if (numberOfTasks == 0) {
                if (readBuffer.remaining() >= 4) {
                    numberOfTasks = readBuffer.getInt();
                }
                else {
                    readBuffer.flip();
                    return;
                }
            }

            if (messageLen == 0) {
                if (readBuffer.remaining() >= 4) {
                    messageLen = readBuffer.getInt();
                }
                else {
                    readBuffer.compact();
                    return;
                }
            }
            int readyToRead = readBuffer.remaining();
            byte[] tmp = new byte[readyToRead];
            readBuffer.get(tmp);
            message = ArrayUtils.addAll(message, tmp);
            readBuffer.compact();
        }

        public boolean isMessageCollected() {
            return messageLen > 0 && message.length == messageLen;
        }

        public Message getMessage() throws InvalidProtocolBufferException {
            if (!isMessageCollected()) {
                return null;
            }
            return Message.parseFrom(message);
        }

        public void makeWriteBuffer() {
            byte[] response = Message.newBuilder().setLen(result.size()).addAllArray(result).build().toByteArray();
            writeBuffer = ByteBuffer.allocate(4 + response.length);
            writeBuffer.putInt(response.length);
            writeBuffer.put(response);
            writeBuffer.flip();
        }

        public void resetRead() {
            messageLen = 0;
            message = new byte[0];
        }

        public void resetWrite() {
            result = null;
            tasksCompleted++;
            if (tasksCompleted == numberOfTasks) {
                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel> {
        @Override
        public void completed(AsynchronousSocketChannel result, AsynchronousServerSocketChannel attachment) {
            if (attachment.isOpen()) {
                attachment.accept(attachment, this);
                ClientHandler clientContext = new ClientHandler(result);
                result.read(clientContext.readBuffer, clientContext, new ReadHandler());
            }
            if (result != null && result.isOpen()) {
                System.out.println("Accepted client");
            }
        }

        @Override
        public void failed(Throwable exc, AsynchronousServerSocketChannel attachment) {
        }
    }

    private class ReadHandler implements CompletionHandler<Integer, ClientHandler> {
        @Override
        public void completed(Integer result, ClientHandler attachment) {
            attachment.fillMessage();
            if (attachment.isMessageCollected()) {
                try {
                    Message msg = attachment.getMessage();
                    long start = System.currentTimeMillis();
                    Future<?> future = workers.submit(() -> {attachment.result = processData(msg.getArrayList());});
                    future.get();
                    long end = System.currentTimeMillis();
                    collector.putFromServer(end - start);
                    attachment.makeWriteBuffer();
                    attachment.resetRead();
                    if (attachment.channel.isOpen()) {
                        attachment.channel.write(attachment.writeBuffer, attachment, new WriteHandler());
                    }
                } catch (InvalidProtocolBufferException | InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
            else if (attachment.channel.isOpen()) {
                attachment.channel.read(attachment.readBuffer, attachment, this);
            }
        }

        @Override
        public void failed(Throwable exc, ClientHandler attachment) {
            exc.printStackTrace();
        }
    }

    private class WriteHandler implements CompletionHandler<Integer, ClientHandler> {
        @Override
        public void completed(Integer result, ClientHandler attachment) {
            if (attachment.writeBuffer.hasRemaining()) {
                if (attachment.channel.isOpen()) {
                    attachment.channel.write(attachment.writeBuffer, attachment, this);
                }
            }
            else if (attachment.channel.isOpen()) {
                attachment.resetWrite();
                if (attachment.channel.isOpen()) {
                    attachment.channel.read(attachment.readBuffer, attachment, new ReadHandler());
                }
            }
        }

        @Override
        public void failed(Throwable exc, ClientHandler attachment) {
            exc.printStackTrace();
        }
    }
}
