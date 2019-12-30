package com.andrewgilmartin.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.IIOException;

/**
 * A simple file serving HTTP server. It can only GET and PUT files. There are
 * no authorization or other safety limitations.
 *
 * FileHttpServer was originally written to support a series of blog postings
 * about moving an ancient Ant build to Maven. That series needed a simple HTTP
 * server to act as a Maven repository manager.
 *
 * Usage {@code
 *
 * java ... com.andrewgilmartin.http.FileHttpServer port directory concurrency
 *
 * }
 */
public final class FileHttpServer {

    private static final Logger LOGGER = Logger.getLogger(FileHttpServer.class.getName());

    public static void main(String... args) throws Exception {
        if (args.length != 3) {
            System.out.printf("usage: java ... %s port directory concurrency\n", FileHttpServer.class.getName());
        } else {
            int port = Integer.parseInt(args[0]);
            File root = new File(args[1]);
            int concurrency = Integer.parseInt(args[2]);
            FileHttpServer server = new FileHttpServer(port, root, concurrency);
        }
    }

    public FileHttpServer(int port, File root, int concurrency) throws IOException {
        LOGGER.log(Level.INFO, "Serving {0} at http://localhost:{1,number,0}/", new Object[]{ root.getCanonicalPath(), port });
        HttpServer server = HttpServer.create(new InetSocketAddress(port), concurrency);
        server.setExecutor(Executors.newFixedThreadPool(concurrency));
        server.createContext("/", new ContentHandler(root));
        server.start();
    }

    private static class ContentHandler implements HttpHandler {

        private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
        private final File root;

        public ContentHandler(File root) {
            this.root = root;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            switch (exchange.getRequestMethod()) {
                case "GET":
                    get(exchange);
                    break;
                case "PUT":
                    put(exchange);
                    break;
                default:
                    exchange.sendResponseHeaders(HTTP_BAD_REQUEST, 0);
                    exchange.getResponseBody().close();
            }
        }

        private void get(HttpExchange exchange) throws IOException {
            File file = getRequestedFile(exchange);
            LOGGER.log(Level.INFO, "GET {0}", file);
            if (!file.exists()) {
                exchange.sendResponseHeaders(HTTP_NOT_FOUND, 0);
                exchange.getResponseBody().close();
            } else if (file.isFile()) {
                exchange.getResponseHeaders().add("content-type", APPLICATION_OCTET_STREAM);
                exchange.sendResponseHeaders(HTTP_OK, file.length());
                try (FileInputStream input = new FileInputStream(file); OutputStream output = exchange.getResponseBody()) {
                    transferTo(input, output);
                }
            } else {
                exchange.sendResponseHeaders(HTTP_BAD_REQUEST, 0);
                exchange.getResponseBody().close();
            }
        }

        private void put(HttpExchange exchange) throws IOException {
            File file = getRequestedFile(exchange);
            LOGGER.log(Level.INFO, "PUT {0}", file);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            if (file.getParentFile().exists()) {
                try (InputStream input = exchange.getRequestBody(); FileOutputStream output = new FileOutputStream(file)) {
                    transferTo(input, output);
                }
                exchange.sendResponseHeaders(HTTP_NO_CONTENT, -1);
            } else {
                exchange.sendResponseHeaders(HTTP_INTERNAL_ERROR, 0);
                exchange.getResponseBody().close();
            }
        }

        private void transferTo(InputStream input, OutputStream output) throws IOException {
            byte[] buffer = new byte[10_000];
            for (int length = input.read(buffer); length != -1; length = input.read(buffer)) {
                output.write(buffer, 0, length);
            }
        }

        private File getRequestedFile(HttpExchange exchange) throws IOException {
            String basePath = exchange.getHttpContext().getPath();
            String uriPath = exchange.getRequestURI().getPath();
            String relativePath = "/".equals(basePath) ? uriPath.substring(1) : uriPath.substring(basePath.length() + 1);
            if (relativePath.contains("..")) {
                throw new IIOException("relative paths are unsupported");
            }
            File file = new File(root, relativePath).getCanonicalFile();
            return file;
        }
    }
}

// END
