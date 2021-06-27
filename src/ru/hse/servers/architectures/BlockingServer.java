package ru.hse.servers.architectures;

import ru.hse.servers.Client;
import ru.hse.servers.Constants;
import ru.hse.servers.TestConfig;
import ru.hse.servers.Utils;
import ru.hse.servers.protocol.message.Message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class BlockingServer extends AbstractServer {
    private final TestConfig config;
    private ServerSocket serverSocket;
    private final ExecutorService acceptWorker = Executors.newSingleThreadExecutor();
    private volatile boolean isWorking = true;
    private final List<ClientHandler> clients = new ArrayList<>();
    private final CountDownLatch startLatch;

    public BlockingServer(TestConfig config, CountDownLatch startLatch) {
        this.config = config;
        this.startLatch = startLatch;
    }

    @Override
    public void start() throws IOException {
        serverSocket = new ServerSocket(Constants.PORT);
        acceptWorker.submit(() -> acceptClients(serverSocket));
    }

    private void acceptClients(ServerSocket socket) {
        try (ServerSocket ignored = socket) {
            while (isWorking) {
                try {
                    Socket clientSocket = socket.accept();
                    System.out.println("Accepted client");
                    ClientHandler handler = new ClientHandler(clientSocket);
                    clients.add(handler);
                    startLatch.countDown();
                    handler.processClient();
                } catch (SocketException ignore) {
                }
            }
        }
        catch (EOFException ignore) {

        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws IOException {
        isWorking = false;
        serverSocket.close();
        acceptWorker.shutdown();
        workers.shutdown();
        for (ClientHandler client : clients) {
            client.stop();
        }
    }

    @Override
    public double getMeanTime() {
        List<Long> results = clients.stream().flatMap(c -> c.results.stream()).collect(Collectors.toList());
        return ((double) results.stream().reduce(0L, Long::sum)) / results.size();
    }

    private class ClientHandler {
        private final Socket socket;

        public final ExecutorService reader = Executors.newSingleThreadExecutor();
        public final ExecutorService writer = Executors.newSingleThreadExecutor();

        private final DataInputStream inputStream;
        private final DataOutputStream outputStream;

        private volatile boolean working = true;

        public final List<Long> results = new CopyOnWriteArrayList<>();

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());
        }

        private void sendData(int clientId, int taskId, List<Integer> data) {
            writer.submit(() -> {
                try {
                    Utils.writeMessage(outputStream, Message.newBuilder()
                            .setClientId(clientId).setTaskId(taskId).addAllArray(data).build());
                } catch (SocketException ignore) {
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        public void processClient() {
            reader.submit(() -> {
                try {
                    int numberOfQueries = inputStream.readInt();
                    //int clientId = inputStream.readInt();
                    //System.out.println("NQ " + numberOfQueries);
                    for (int i = 0; i < numberOfQueries; i++) {
                        Message msg = Utils.readMessage(inputStream);
                        List<Integer> data = msg.getArrayList();
                        //int finalI = i;
                        workers.submit(() -> {
                            //System.out.println("Client " + clientId + " started sorting");
                            long start = System.currentTimeMillis();
                            List<Integer> result = processData(data);
                            long end = System.currentTimeMillis();
                            if (!isStopped) {
                                results.add(end - start);
                            }
                            //System.out.println("Client " + clientId + " finished sorting");
                            sendData(msg.getClientId(), msg.getTaskId(), result);
                            //System.out.println("Client " + clientId + " result sent");
                            //System.out.println("Wrote " + finalI);
                        });
                    }
                } catch (SocketException | EOFException ignore) {
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        public void stop() {
            System.out.println("Stopping client handler");
            isStopped = false;
            working = false;
            reader.shutdownNow();
            writer.shutdownNow();
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
