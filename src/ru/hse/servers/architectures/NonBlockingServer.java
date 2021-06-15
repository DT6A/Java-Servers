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
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static java.lang.Math.min;

public class NonBlockingServer extends AbstractServer {
    private volatile boolean isWorking = true;

    private Selector readSelector;
    private Selector writeSelector;
    private final ExecutorService readPool = Executors.newSingleThreadExecutor();
    private final ExecutorService writePool = Executors.newSingleThreadExecutor();

    private final Queue<ClientHandler> readQueue = new ConcurrentLinkedQueue<>();
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final List<Integer> sentMsgs = new CopyOnWriteArrayList<>();

    private final TestConfig config;
    private final TimeCollector collector;

    public NonBlockingServer(TestConfig config, TimeCollector collector) {
        this.config = config;
        this.collector = collector;
    }

    @Override
    public void start() throws IOException {
        readSelector = Selector.open();
        writeSelector = Selector.open();

        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
            serverSocketChannel.bind(new InetSocketAddress(Constants.PORT));
            readPool.submit(() -> {
               while (isWorking) {
                   try {
                       while (!readQueue.isEmpty()) {
                           ClientHandler handler = readQueue.poll();
                           if (handler == null) {
                               break;
                           }
                           handler.channel.register(readSelector, SelectionKey.OP_READ, handler);
                       }

                       if (readSelector.select(1000) == 0) {
                            continue;
                       }
                       Iterator<SelectionKey> iterator = readSelector.selectedKeys().iterator();
                       while (iterator.hasNext()) {
                           SelectionKey selectionKey = iterator.next();
                           ClientHandler handler = (ClientHandler)selectionKey.attachment();

                           if (handler.isFinished()) {
                               selectionKey.cancel();
                               iterator.remove();
                               continue;
                           }

                           handler.channel.read(handler.readBuffer);
                           handler.fillMessage();

                           if (handler.isMessageCollected()) {
                               //Client . finished
                               //System.out.println("Collected message");
                               List<Integer> receivedData = handler.getMessage().getArrayList();
                               //System.out.println("Collected message " + handler.userId);
                               workers.submit(() -> {
                                   long start = System.currentTimeMillis();
                                   List<Integer> result = processData(receivedData);
                                   long end = System.currentTimeMillis();
                                   collector.putFromServer(end - start);
                                   Message response = Message.newBuilder()
                                           .setTaskId(handler.taskId).setClientId(handler.userId).addAllArray(result).build();
                                   handler.messages.offer(response);
                                   //handler.makeWriteBuffer();
                                   //writeQueue.offer(handler);
                                   writeSelector.wakeup();
                               });
                               handler.resetRead();
                           }

                           iterator.remove();
                       }
                   } catch (IOException e) {
                       e.printStackTrace();
                   }
               }
            });

            writePool.submit(() -> {
                while (isWorking) {
                    try {
                        for (int i = 0; i < clients.size(); i++) {
                            ClientHandler handler = clients.get(i);
                            if ((!handler.everStarted || !handler.writeBuffer.hasRemaining()) && !handler.messages.isEmpty()) {
                                handler.everStarted = true;
                                handler.resetWrite();
                                Message message = handler.messages.poll();
                                handler.makeWriteBuffer(message);
                                //System.out.println("Registering");
                                handler.channel.register(writeSelector, SelectionKey.OP_WRITE, handler);
                                //System.out.println("Registered");
                            }
                        }
                        //System.out.println(sentMsgs);
                        if (writeSelector.select(1000) == 0) {
                            continue;
                        }
                        //System.out.println("Selected");
                        Iterator<SelectionKey> iterator = writeSelector.selectedKeys().iterator();
                        while (iterator.hasNext()) {
                            SelectionKey selectionKey = iterator.next();
                            ClientHandler handler = (ClientHandler)selectionKey.attachment();

                            handler.channel.write(handler.writeBuffer);
                            //System.out.println("Writting data " + handler.userId);
                            if (!handler.writeBuffer.hasRemaining()) {
                                sentMsgs.set(handler.userId, sentMsgs.get(handler.userId) + 1);
                                //System.out.println("Canceling " + handler.userId);
                                selectionKey.interestOps(0);
                                //selectionKey.cancel();
                                //System.out.println("Canceled " + handler.userId);
                                //handler.resetWrite();
                                //System.out.println("Wrote " + handler.userId);
                            }

                            iterator.remove();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

            while (isWorking) {
                SocketChannel channel = null;
                try {
                     channel = serverSocketChannel.accept();
                } catch (ClosedByInterruptException ignore) {
                    break;
                }
                //System.out.println("Accepted client");
                sentMsgs.add(0);
                channel.configureBlocking(false);
                ClientHandler handler = new ClientHandler(channel);
                clients.add(handler);
                readQueue.offer(handler);
                readSelector.wakeup(); // vibe check
            }
        }
    }

    @Override
    public void stop() throws IOException {
        isWorking = false;
        readSelector.close();
        writeSelector.close();
        readPool.shutdownNow();
        writePool.shutdownNow();
        workers.shutdownNow();
        for (ClientHandler handler : clients) {
            handler.channel.close();
        }
    }

    private static class ClientHandler {
        private volatile boolean everStarted = false;
        private final Queue<Message> messages = new ConcurrentLinkedQueue<>();
        private int messageLen;
        private int numberOfTasks;
        private int tasksCompleted;
        byte[] message = new byte[0];
        public ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        public ByteBuffer writeBuffer;
        public List<Integer> result;
        public final SocketChannel channel;
        private volatile int userId;
        private volatile int taskId;

        private ClientHandler(SocketChannel channel) {
            this.channel = channel;
        }

        public void fillMessage() {
            readBuffer.flip();

            if (numberOfTasks == 0) {
                if (readBuffer.remaining() >= 4) {
                    numberOfTasks = readBuffer.getInt();
                } else {
                    readBuffer.flip();
                    return;
                }
            }

            if (messageLen == 0) {
                if (readBuffer.remaining() >= 4) {
                    messageLen = readBuffer.getInt();
                } else {
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

        public void makeWriteBuffer(Message msg) {
            byte[] response = msg.toByteArray();
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
        }

        public boolean isFinished() {
            return numberOfTasks > 0 && tasksCompleted == numberOfTasks;
        }
    }
}
