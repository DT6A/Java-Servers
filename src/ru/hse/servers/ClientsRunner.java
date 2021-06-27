package ru.hse.servers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ClientsRunner {
    private final TestConfig config;
    private final List<Client> clientList = new ArrayList<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final CountDownLatch latch;
    private final CountDownLatch startLatch;

    ClientsRunner(TestConfig config, CountDownLatch startLatch) {
        this.config = config;
        this.startLatch = startLatch;
        latch = new CountDownLatch(1);
    }

    public void stop() {
        for (Client client : clientList) {
            client.stop();
        }
        pool.shutdownNow();
    }

    public void run() throws InterruptedException {
        for (int i = 0; i < config.numberOfClients; i++) {
            clientList.add(new Client(i, config, latch, startLatch));
        }

        try {
            for (Client client : clientList) {
                pool.submit(() -> {
                    try {
                        client.run();
                    } catch (Exception e) {
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

    public double getMeanTime() {
        List<Long> results = clientList.stream().flatMap(c -> c.getResults().stream()).collect(Collectors.toList());
        return ((double) results.stream().reduce(0L, Long::sum)) / results.size();
    }
}
