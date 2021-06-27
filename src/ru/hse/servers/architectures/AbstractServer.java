package ru.hse.servers.architectures;

import ru.hse.servers.Constants;
import ru.hse.servers.TestConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public abstract class AbstractServer {
    protected volatile boolean isStopped = false;
    protected final ExecutorService workers = Executors.newFixedThreadPool(Constants.WORKER_THREADS);

    public List<Integer> processData(List<Integer> data) {
        Integer[] array = data.toArray(new Integer[0]);
        for (int i = 0; i < array.length - 1; i++) {
            for (int j = 0; j < array.length - i - 1; j++) {
                if (array[j] > array[j + 1]) {
                    int tmp = array[j];
                    array[j] = array[j + 1];
                    array[j + 1] = tmp;
                }
            }
        }
        return new ArrayList<>(Arrays.asList(array));
    }

    public abstract void start() throws IOException;
    public abstract void stop() throws IOException;
    public abstract double getMeanTime();
}
