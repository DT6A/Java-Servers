package ru.hse.servers;

import ru.hse.servers.protocol.message.Message;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class Client {
    private final int id;
    private final TestConfig config;
    private final CountDownLatch latch;
    private final CountDownLatch startLatch;
    private final Random random = new Random();
    private final TimeCollector collector;

    Client(int id, TestConfig config, CountDownLatch latch, CountDownLatch startLatch, TimeCollector collector) {
        this.id = id;
        this.config = config;
        this.latch = latch;
        this.startLatch = startLatch;
        this.collector = collector;
    }

    private Message generateMessage() {
        List<Integer> list = new ArrayList<>(config.arraysSize);

        for (int i = 0; i < config.arraysSize; i++) {
            list.add(random.nextInt(2 * Constants.ARRAY_VALUES_ABS_MAX) - Constants.ARRAY_VALUES_ABS_MAX);
        }

        return Message.newBuilder().addAllArray(list).setLen(config.arraysSize).build();
    }

    public void run() throws Exception {
        try (SocketChannel channel = SocketChannel.open(new InetSocketAddress(Constants.HOST_IP, Constants.PORT))) {
            channel.configureBlocking(true);
            startLatch.countDown();
            startLatch.await();
            channel.write(ByteBuffer.allocate(4).putInt(config.numberOfQueriesFromEachClient).flip());
            //outputStream.writeInt(id);
            for (int i = 0; i < config.numberOfQueriesFromEachClient; i++) {
                Message message = generateMessage();

                long start = System.currentTimeMillis();

                //System.out.println("Client " + id + " is writing");
                Utils.writeMessageToChannel(channel, message);
                //System.out.println("Client " + id + " wrote, reading");
                Message result = Utils.readMessageFromChannel(channel);
                //System.out.println("Client " + id + " read finished");
                long end = System.currentTimeMillis();

                collector.putFromClient(end - start);
                System.out.println("Client " + id + " query finished in " + (end - start));
                checkResult(result.getArrayList());
                if (i + 1 != config.numberOfQueriesFromEachClient) {
                    Thread.sleep(config.pauseBetweenQueries);
                }
            }
        } catch (InterruptedException ignore) {
        } finally {
            collector.isAccepting = false;
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
}
