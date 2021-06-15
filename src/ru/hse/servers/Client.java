package ru.hse.servers;

import ru.hse.servers.protocol.message.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Client {
    private final int id;
    private final TestConfig config;
    private final CountDownLatch latch;
    private final CountDownLatch startLatch;
    private final Random random = new Random();
    private final TimeCollector collector;
    private final ExecutorService writeThread = Executors.newSingleThreadExecutor();
    private final List<Task> tasks = new CopyOnWriteArrayList<>();

    Client(int id, TestConfig config, CountDownLatch latch, CountDownLatch startLatch, TimeCollector collector) {
        this.id = id;
        this.config = config;
        this.latch = latch;
        this.startLatch = startLatch;
        this.collector = collector;

        for (int i = 0; i < config.numberOfQueriesFromEachClient; i++) {
            tasks.add(new Task(i));
        }
    }

    private Message generateMessage(int taskId) {
        List<Integer> list = new ArrayList<>(config.arraysSize);

        for (int i = 0; i < config.arraysSize; i++) {
            list.add(random.nextInt(2 * Constants.ARRAY_VALUES_ABS_MAX) - Constants.ARRAY_VALUES_ABS_MAX);
        }

        return Message.newBuilder().addAllArray(list).setTaskId(taskId).setClientId(id).build();
    }

    public void run() throws Exception {
        try (SocketChannel channel = SocketChannel.open(new InetSocketAddress(Constants.HOST_IP, Constants.PORT))) {
            channel.configureBlocking(true);
            startLatch.countDown();
            startLatch.await();
            channel.write(ByteBuffer.allocate(4).putInt(config.numberOfQueriesFromEachClient).flip());

            writeThread.submit(() -> {
                for (int i = 0; i < config.numberOfQueriesFromEachClient; i++) {
                    Task task = tasks.get(i);
                    task.startTask();
                    try {
                        Utils.writeMessageToChannel(channel, task.message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (i + 1 != config.numberOfQueriesFromEachClient) {
                        try {
                            Thread.sleep(config.pauseBetweenQueries);
                        } catch (InterruptedException ignore) {
                            ignore.printStackTrace();
                        }
                    }
                }
            });

            for (int i = 0; i < config.numberOfQueriesFromEachClient; i++) {
                Message result = Utils.readMessageFromChannel(channel);
                //System.out.println("Client " + id + " read finished");
                Task task = tasks.get(result.getTaskId());
                task.endTask();
                checkResult(result.getArrayList());
            }
        } catch (InterruptedException ignore) {
            ignore.printStackTrace();
        } finally {
            collector.isAccepting = false;
            writeThread.shutdownNow();
            latch.countDown();
            System.out.println("Client " + id + " finished");
        }
    }

    private void checkResult(List<Integer> result) {
        if (result.size() != config.arraysSize) {
            throw new RuntimeException("Got invalid array size " + result.size() + ", expected " + config.arraysSize);
        }
        for (int i = 0; i < result.size() - 1; i++) {
            if (result.get(i) > result.get(i + 1))
                throw new RuntimeException("Got unsorted array");;
        }
    }

    private class Task {
        public final int taskId;
        public final Message message;
        public long start;
        public long end;

        public Task(int taskId) {
            this.taskId = taskId;
            message = generateMessage(taskId);
        }

        public void startTask() {
            start = System.currentTimeMillis();
        }

        public void endTask() {
            end = System.currentTimeMillis();
            collector.putFromClient(end - start);
            System.out.println("Client " + id + " query finished in " + (end - start));
        }
    }
}
