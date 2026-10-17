package net.pieroxy.mom.sink;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Accepts SMTP connections, one {@link SmtpSession} thread each, up to {@code maxConnections} at once. */
public class SmtpServer {
  private final static Logger LOGGER = Logger.getLogger(SmtpServer.class.getName());

  private final SinkConfiguration config;
  private final TlsContextProvider tls;
  private final MessageStore store;
  private final ServerSocket serverSocket;
  private final ThreadPoolExecutor sessions;
  private final Thread acceptThread;

  public SmtpServer(SinkConfiguration config, TlsContextProvider tls, MessageStore store) throws IOException {
    this.config = config;
    this.tls = tls;
    this.store = store;
    this.serverSocket = new ServerSocket();
    this.serverSocket.setReuseAddress(true);
    this.serverSocket.bind(new InetSocketAddress(InetAddress.getByName(config.getSmtpBindAddress()), config.getSmtpPort()));
    AtomicInteger counter = new AtomicInteger();
    // No queue: a connection beyond maxConnections is refused right away rather than left waiting.
    this.sessions = new ThreadPoolExecutor(0, config.getMaxConnections(), 60, TimeUnit.SECONDS, new SynchronousQueue<>(),
        r -> new Thread(r, "smtp-session-" + counter.incrementAndGet()));
    this.acceptThread = new Thread(this::acceptLoop, "smtp-accept");
  }

  /** The actual port listened on (useful when configured as 0). */
  public int getPort() {
    return serverSocket.getLocalPort();
  }

  public void start() {
    acceptThread.start();
    LOGGER.info("SMTP listening on " + config.getSmtpBindAddress() + ":" + getPort()
        + (tls == null ? " (no STARTTLS)" : " (STARTTLS)") + (config.isSmtpProxyProtocol() ? " expecting PROXY protocol" : ""));
  }

  public void stop() {
    try {
      serverSocket.close();
    } catch (IOException ignored) {
      // Closing anyway.
    }
    sessions.shutdownNow();
  }

  private void acceptLoop() {
    while (!serverSocket.isClosed()) {
      Socket socket;
      try {
        socket = serverSocket.accept();
      } catch (SocketException e) {
        return; // Closed by stop().
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "SMTP accept failed", e);
        continue;
      }
      try {
        sessions.execute(new SmtpSession(socket, config, tls, store));
      } catch (RejectedExecutionException e) {
        refuse(socket);
      }
    }
  }

  private void refuse(Socket socket) {
    LOGGER.info("[" + socket.getInetAddress().getHostAddress() + "] Refused: already " + config.getMaxConnections() + " connections");
    try (socket; OutputStream out = socket.getOutputStream()) {
      out.write(("421 4.3.2 " + config.getHostname() + " Too many connections, try again later\r\n").getBytes(StandardCharsets.US_ASCII));
    } catch (IOException ignored) {
      // The client went away already.
    }
  }
}
