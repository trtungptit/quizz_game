package quizgame.server.net;

import quizgame.server.dao.StatisticsDao;
import quizgame.server.service.AuthService;
import quizgame.server.service.OnlineRegistry;
import quizgame.server.service.RoomManager;
import quizgame.server.service.InviteManager;
import quizgame.server.service.QuestionSetService;
import quizgame.server.service.GameManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** TCP acceptor có giới hạn kết nối và tài nguyên; port 0 dành riêng cho integration test. */
public final class QuizServer implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final AuthService auth;
    private final OnlineRegistry online;
    private final RoomManager rooms;
    private final InviteManager invites;
    private final QuestionSetService questionSets;
    private final StatisticsDao statistics;
    private final GameManager games;
    // Handler giữ socket suốt phiên: không xếp socket thứ 9 vào hàng đợi sau 8 phiên đang mở.
    private final ThreadPoolExecutor handlers = new ThreadPoolExecutor(0, 32, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "quiz-client-handler");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread acceptThread;

    public QuizServer(String host, int port, AuthService auth, OnlineRegistry online) throws IOException {
        this(host, port, auth, online, null);
    }

    public QuizServer(String host, int port, AuthService auth, OnlineRegistry online, RoomManager rooms) throws IOException {
        this(host,port,auth,online,rooms,null);
    }
    public QuizServer(String host,int port,AuthService auth,OnlineRegistry online,RoomManager rooms,InviteManager invites) throws IOException {
        this(host, port, auth, online, rooms, invites, null);
    }

    public QuizServer(String host, int port, AuthService auth, OnlineRegistry online,
                      RoomManager rooms, InviteManager invites, QuestionSetService questionSets) throws IOException {
        this(host, port, auth, online, rooms, invites, questionSets, null);
    }

    public QuizServer(String host, int port, AuthService auth, OnlineRegistry online,
                      RoomManager rooms, InviteManager invites, QuestionSetService questionSets, StatisticsDao statistics) throws IOException {
        this.statistics = statistics;
        this.auth = auth;
        this.online = online;
        this.rooms = rooms;
        this.invites = invites;
        this.questionSets = questionSets;
        this.games=rooms==null?null:rooms.games();
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(host, port), 50);
    }

    public int port() { return serverSocket.getLocalPort(); }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) throw new IllegalStateException("Server đã chạy");
        acceptThread = new Thread(this::acceptLoop, "quiz-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                clients.add(socket);
                try {
                    handlers.execute(() -> {
                        try { new ClientHandler(socket, auth, online, rooms, invites, questionSets, statistics).run(); }
                        finally { clients.remove(socket); }
                    });
                } catch (RuntimeException rejected) {
                    clients.remove(socket);
                    try { socket.close(); } catch (IOException ignored) { }
                }
            } catch (IOException ex) {
                if (running.get()) close();
            }
        }
    }

    @Override public synchronized void close() {
        if (!running.compareAndSet(true, false) && serverSocket.isClosed()) return;
        try { serverSocket.close(); } catch (IOException ignored) { }
        if (games != null) games.close();
        if (invites != null) invites.close();
        for (Socket socket : clients) try { socket.close(); } catch (IOException ignored) { }
        handlers.shutdownNow();
        try { handlers.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
}
