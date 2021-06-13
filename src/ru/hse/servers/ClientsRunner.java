package ru.hse.servers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientsRunner {
    private final TestConfig config;
    private final List<Client> clientList = new ArrayList<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final CountDownLatch latch;
    private final CountDownLatch startLatch;
    private final TimeCollector collector;

    ClientsRunner(TestConfig config, TimeCollector collector) {
        this.config = config;
        latch = new CountDownLatch(config.numberOfClients);
        startLatch = new CountDownLatch(config.numberOfClients);
        this.collector = collector;
    }

    public void stop() {
        pool.shutdownNow();
    }

    public void run() throws InterruptedException {
        for (int i = 0; i < config.numberOfClients; i++) {
            clientList.add(new Client(i, config, latch, startLatch, collector));
        }

        try {
            for (Client client : clientList) {
                pool.submit(() -> {
                    try {
                        client.run();
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                });
            }
            latch.await();
        }
        finally {
            stop();
        }
    }
}
