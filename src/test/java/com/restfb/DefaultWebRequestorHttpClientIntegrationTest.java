/*
 * Copyright (c) 2010-2025 Mark Allen, Norbert Bartels.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.restfb;

import static com.restfb.testutils.RestfbAssertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class DefaultWebRequestorHttpClientIntegrationTest {

  private static HttpServer server;
  private static String baseUrl;
  private static final AtomicReference<String> lastPostBody = new AtomicReference<>("");

  @BeforeAll
  static void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/ok", exchange -> {
      byte[] body = "pong".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("facebook-api-version", "v19.0");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.createContext("/mirror", exchange -> {
      byte[] requestBody = exchange.getRequestBody().readAllBytes();
      lastPostBody.set(new String(requestBody, StandardCharsets.UTF_8));
      exchange.sendResponseHeaders(200, requestBody.length);
      exchange.getResponseBody().write(requestBody);
      exchange.close();
    });
    server.start();
    baseUrl = "http://localhost:" + server.getAddress().getPort();
  }

  @AfterAll
  static void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void executeGetUsesHttpClient() throws IOException {
    DefaultWebRequestor requestor = new DefaultWebRequestor();
    WebRequestor.Response response = requestor.executeGet(new WebRequestor.Request(baseUrl + "/ok", null));

    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getBody()).isEqualTo("pong");
    assertThat(response.getHeaders().get("facebook-api-version")).contains("v19.0");
  }

  @Test
  void executePostStreamsBody() throws IOException {
    DefaultWebRequestor requestor = new DefaultWebRequestor();
    WebRequestor.Request request = new WebRequestor.Request(baseUrl + "/mirror", null, "echo=1");
    request.setBody(Body.withData(Collections.singletonMap("message", "hi")));

    WebRequestor.Response response = requestor.executePost(request);

    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getBody()).contains("message");
    assertThat(lastPostBody.get()).contains("message");
  }
}
