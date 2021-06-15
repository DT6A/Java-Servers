package ru.hse.servers;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import ru.hse.servers.architectures.AbstractServer;
import ru.hse.servers.architectures.AsynchronousServer;
import ru.hse.servers.architectures.BlockingServer;
import ru.hse.servers.architectures.NonBlockingServer;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ServerTesting implements Runnable {
    private final Scanner in = new Scanner(System.in);
    private TestConfig config;
    private final String[] HEADERS = { "Length", "Clients", "Pause", "Queries", "Time" };
    private final List<CSVNote> resultsClient = new ArrayList<>();
    private final List<CSVNote> resultsServer = new ArrayList<>();

    private static class CSVNote {
        public int arraySize;
        public int clientsNumber;
        public int pause;
        public int queries;
        public double time;

        CSVNote(TestConfig config, double result) {
            arraySize = config.arraysSize;
            clientsNumber = config.numberOfClients;
            pause = config.pauseBetweenQueries;
            queries = config.numberOfQueriesFromEachClient;
            time = result;
        }
    }

    public static void main(String[] args) {
        ServerTesting app = new ServerTesting();
        app.run();
    }

    private int readInteger() {
        while (true) {
            try {
                return in.nextInt();
            } catch (InputMismatchException e) {
                System.out.println("Integer is required");
                in.next();
            }
        }
    }

    private TestConfig collectConfig() {
        int arraysSize = 0;
        int numberOfClients = 0;
        int pauseBetweenQueries = 0;
        int numberOfQueriesFromEachClient;
        int lowerBound;
        int upperBound;
        int step;
        TestConfig.ArchitectureType architectureType;
        TestConfig.VaryingParameter varyingParameter;

        while (true) {
            System.out.println("Choose architecture by entering number:");
            System.out.println("\t1. Asynchronous");
            System.out.println("\t2. Blocking");
            System.out.println("\t3. Non-blocking");

            int num = readInteger();

            if (num == 1) {
                architectureType = TestConfig.ArchitectureType.ASYNC;
                break;
            }
            else if (num == 2) {
                architectureType = TestConfig.ArchitectureType.BLOCKING;
                break;
            }
            else if (num == 3) {
                architectureType = TestConfig.ArchitectureType.NON_BLOCKING;
                break;
            }
            else {
                System.out.println("Invalid number");
            }
        }

        System.out.println("Enter number of queries from each client (positive integer):");
        while (true) {
            numberOfQueriesFromEachClient = readInteger();
            if (numberOfQueriesFromEachClient > 0)
                break;
            System.out.println("Positive integer is required");
        }

        while (true) {
            System.out.println("Choose changing parameter by entering number:");
            System.out.println("\t1. Array size");
            System.out.println("\t2. Number of clients");
            System.out.println("\t3. Queries interval (ms)");

            int num = readInteger();

            if (num == 1) {
                varyingParameter = TestConfig.VaryingParameter.LENGTH;
                break;
            }
            else if (num == 2) {
                varyingParameter = TestConfig.VaryingParameter.CLIENTS;
                break;
            }
            else if (num == 3) {
                varyingParameter = TestConfig.VaryingParameter.PAUSE;
                break;
            }
            else {
                System.out.println("Invalid number");
            }
        }

        while (true) {
            System.out.println("Enter lower bound for parameter:");
            lowerBound = readInteger();
            if (lowerBound > 0) {
                break;
            }
            System.out.println("Positive integer is required");
        }

        while (true) {
            System.out.println("Enter upper bound for parameter:");
            upperBound = readInteger();
            if (upperBound <= 0) {
                System.out.println("Positive integer is required");
                continue;
            }
            if (upperBound < lowerBound) {
                System.out.println("Upper bound can not be less than lower bound");
                continue;
            }
            break;
        }

        while (true) {
            System.out.println("Enter step for parameter:");
            step = readInteger();
            if (step <= 0) {
                System.out.println("Positive integer is required");
                continue;
            }
            break;
        }

        if (varyingParameter != TestConfig.VaryingParameter.LENGTH) {
            while (true) {
                System.out.println("Enter array length:");
                arraysSize = readInteger();
                if (arraysSize > 0) {
                    break;
                }
                System.out.println("Positive integer is required");
            }
        }

        if (varyingParameter != TestConfig.VaryingParameter.CLIENTS) {
            while (true) {
                System.out.println("Enter number of clients:");
                numberOfClients = readInteger();
                if (numberOfClients > 0) {
                    break;
                }
                System.out.println("Positive integer is required");
            }
        }

        if (varyingParameter != TestConfig.VaryingParameter.PAUSE) {
            while (true) {
                System.out.println("Enter length of pause between queries (ms):");
                pauseBetweenQueries = readInteger();
                if (pauseBetweenQueries > 0) {
                    break;
                }
                System.out.println("Positive integer is required");
            }
        }

        return new TestConfig(arraysSize,
                numberOfClients,
                pauseBetweenQueries,
                numberOfQueriesFromEachClient,
                lowerBound, upperBound,
                step, architectureType,
                varyingParameter);
    }

    private void saveResultsToCSV(String fileName, List<CSVNote> results) {
        String OUT_DIR = "output/";
        try {
            Files.createDirectories(Paths.get(OUT_DIR));
        } catch (IOException e) {
            System.out.println("Failed to create output directory");
        }
        FileWriter out;
        try {
            out = new FileWriter(OUT_DIR + fileName);
        } catch (IOException e) {
            System.out.println("Failed to create output file");
            return;
        }
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(HEADERS))) {
            for (CSVNote note : results) {
                printer.printRecord(note.arraySize, note.clientsNumber, note.pause, note.queries, note.time);
            }
        } catch (IOException e) {
            System.out.println("Failed to write to output file");;
        }
    }

    @Override
    public void run() {
        //config = collectConfig();
        config = new TestConfig(3000, 10, 50, 30, 1, 501, 50,
                TestConfig.ArchitectureType.ASYNC, TestConfig.VaryingParameter.PAUSE);
        config.initStepping();
        ExecutorService serverThread = Executors.newSingleThreadExecutor();
        do {
            TimeCollector collector = new TimeCollector();
            System.out.println(config);
            ClientsRunner clientsRunner = new ClientsRunner(config, collector);
            AbstractServer server = null;
            if (config.architectureType == TestConfig.ArchitectureType.BLOCKING) {
                server = new BlockingServer(config, collector);
            }
            else if (config.architectureType == TestConfig.ArchitectureType.NON_BLOCKING) {
                server = new NonBlockingServer(config, collector);
            }
            else {
                server = new AsynchronousServer(config, collector);
            }
            Future<?> serverFuture = null;
            try {
                //server.start();
                AbstractServer finalServer = server;
                serverFuture = serverThread.submit(() -> {
                    try {
                        finalServer.start();
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                });
                Thread.sleep(100);
                clientsRunner.run();
            } catch (InterruptedException ignored) {
                ignored.printStackTrace();
            } finally {
                try {
                    server.stop();
                    serverFuture.cancel(true);
                    resultsClient.add(new CSVNote(config, collector.getClientMeanTime()));
                    resultsServer.add(new CSVNote(config, collector.getServerMeanTime()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } while (config.step());
        saveResultsToCSV("CLIENT_" + config.toCSVFileName(), resultsClient);
        saveResultsToCSV("SERVER_" + config.toCSVFileName(), resultsServer);
        serverThread.shutdownNow();
    }
}
