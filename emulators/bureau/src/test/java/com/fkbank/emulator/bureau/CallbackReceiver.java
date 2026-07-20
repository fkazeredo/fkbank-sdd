package com.fkbank.emulator.bureau;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands in for the bank endpoint the emulator calls back, so a test can inspect what actually
 * crossed the network.
 *
 * <p>It keeps the raw bytes rather than a parsed body on purpose: the signature covers those bytes
 * and nothing else, so a test that re-serialized before checking would be verifying its own
 * serializer instead of the emulator's.
 */
final class CallbackReceiver {

  /** One delivery as it arrived. */
  record Received(byte[] body, String signature) {}

  private final HttpServer server;
  private final List<Received> received = new CopyOnWriteArrayList<>();

  private CallbackReceiver(HttpServer server) {
    this.server = server;
  }

  static CallbackReceiver start() {
    try {
      // Port 0 lets the OS pick a free one, so parallel or repeated runs never collide.
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      CallbackReceiver receiver = new CallbackReceiver(server);
      server.createContext(
          "/callbacks",
          exchange -> {
            try (exchange) {
              byte[] body = exchange.getRequestBody().readAllBytes();
              String signature = exchange.getRequestHeaders().getFirst(CallbackSignature.HEADER);
              receiver.received.add(new Received(body, signature));
              exchange.sendResponseHeaders(200, -1);
            }
          });
      server.start();
      return receiver;
    } catch (IOException e) {
      throw new UncheckedIOException("Could not start the callback receiver", e);
    }
  }

  String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/callbacks";
  }

  List<Received> received() {
    return List.copyOf(received);
  }

  void forgetEverythingReceived() {
    received.clear();
  }
}
