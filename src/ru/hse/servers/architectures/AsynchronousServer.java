package ru.hse.servers.architectures;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.commons.lang.ArrayUtils;
import ru.hse.servers.Constants;
import ru.hse.servers.TestConfig;
import ru.hse.servers.protocol.message.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Math.min;

public class AsynchronousServer extends AbstractServer {
    private final TestConfig config;
    private volatile boolean isWorking = true;
    private final ReentrantLock waitLock = new ReentrantLock();
    private final Condition finishCondition = waitLock.newCondition();
    private final List<Long> results = new ArrayList<>();
    private final CountDownLatch startLatch;

    public AsynchronousServer(TestConfig config, CountDownLatch startLatch) {
        this.config = config;
        this.startLatch = startLatch;
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
                throw new RuntimeException(e);
            } finally {
                waitLock.unlock();
            }
        }
    }

    @Override
    public void stop() throws IOException {
        try {
            waitLock.lock();
            isStopped = true;
            isWorking = false;
            finishCondition.signal();
        } finally {
            waitLock.unlock();
            workers.shutdownNow();
        }
    }

    @Override
    public double getMeanTime() {
        return ((double) results.stream().reduce(0L, Long::sum)) / results.size();
    }

    private static class ClientHandler {
        private int messageLen;
        private int numberOfTasks;
        private int tasksCompleted;
        private int userId;
        private int taskId;
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
            int willRead = min(readyToRead, messageLen - message.length);
            byte[] tmp = new byte[willRead];
            readBuffer.get(tmp, 0, willRead);
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
            Message msg = Message.parseFrom(message);
            userId = msg.getClientId();
            taskId = msg.getTaskId();
            return msg;
        }

        public void makeWriteBuffer() {
            byte[] response = Message.newBuilder().setTaskId(taskId).setClientId(userId).addAllArray(result).build().toByteArray();
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
                    throw new RuntimeException(e);
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
                startLatch.countDown();
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
                    if (!isStopped) {
                        results.add(end - start);
                    }
                    attachment.makeWriteBuffer();
                    attachment.resetRead();
                    if (attachment.channel.isOpen()) {
                        attachment.channel.write(attachment.writeBuffer, attachment, new WriteHandler());
                    }
                } catch (InvalidProtocolBufferException | InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
            else if (attachment.channel.isOpen()) {
                attachment.channel.read(attachment.readBuffer, attachment, this);
            }
        }

        @Override
        public void failed(Throwable ignore, ClientHandler attachment) {
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
        public void failed(Throwable ignore, ClientHandler attachment) {
        }
    }
}
