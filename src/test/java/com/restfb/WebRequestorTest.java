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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.restfb.util.SoftHashMap;

class WebRequestorTest {

  @Test
  void enumChecker() {
    assertThat(DefaultWebRequestor.HttpMethod.GET.name()).isEqualTo("GET");
    assertThat(DefaultWebRequestor.HttpMethod.DELETE.name()).isEqualTo("DELETE");
  }

  @Test
  void testFillHeaderAndDebugInfo() {

    String appUsage = "{ \"call_count\": 10, \"total_time\": 20, \"total_cputime\": 30 }";
    String pageUsage = "{ \"call_count\": 20, \"total_time\": 40, \"total_cputime\": 60 }";

    final Map<String, String> headerFields = new HashMap<>();
    headerFields.put("x-app-usage", appUsage);
    headerFields.put("x-page-usage", pageUsage);

    HttpResponse<byte[]> response = new DummyResponse(headerFields);

    DefaultWebRequestor defaultWebRequestor = new DefaultWebRequestor();
    defaultWebRequestor.fillHeaderAndDebugInfo(response);

    DebugHeaderInfo debugHeaderInfo = defaultWebRequestor.getDebugHeaderInfo();

    assertThat(debugHeaderInfo.getAppUsage()).isNotNull();
    assertThat(debugHeaderInfo.getAppUsage().getCallCount()).isEqualTo(10);
    assertThat(debugHeaderInfo.getAppUsage().getTotalTime()).isEqualTo(20);
    assertThat(debugHeaderInfo.getAppUsage().getTotalCputime()).isEqualTo(30);
    assertThat(debugHeaderInfo.getPageUsage()).isNotNull();
    assertThat(debugHeaderInfo.getPageUsage().getCallCount()).isEqualTo(20);
    assertThat(debugHeaderInfo.getPageUsage().getTotalTime()).isEqualTo(40);
    assertThat(debugHeaderInfo.getPageUsage().getTotalCputime()).isEqualTo(60);
  }

  @Test
  void checkMapSupplier() {
    try {
      ETagWebRequestor requestor = new ETagWebRequestor();
      ETagWebRequestor.setMapSupplier(HashMap::new);
      ETagWebRequestor requestor2 = new ETagWebRequestor();
      ETagWebRequestor.setMapSupplier(SoftHashMap::new);
    } catch (Exception e) {
      fail("some exception occurred during creating a web requestor");
    }
  }

}

class DummyResponse implements HttpResponse<byte[]> {

  private final HttpHeaders headers;

  DummyResponse(Map<String, String> headerFields) {
    Map<String, java.util.List<String>> multiValues = new HashMap<>();
    headerFields.forEach((k, v) -> multiValues.put(k, java.util.Collections.singletonList(v)));
    this.headers = HttpHeaders.of(multiValues, (name, value) -> true);
  }

  @Override
  public int statusCode() {
    return 200;
  }

  @Override
  public HttpRequest request() {
    return null;
  }

  @Override
  public Optional<HttpResponse<byte[]>> previousResponse() {
    return Optional.empty();
  }

  @Override
  public HttpHeaders headers() {
    return headers;
  }

  @Override
  public byte[] body() {
    return new byte[0];
  }

  @Override
  public Optional<javax.net.ssl.SSLSession> sslSession() {
    return Optional.empty();
  }

  @Override
  public URI uri() {
    return URI.create("http://localhost/test");
  }

  @Override
  public HttpClient.Version version() {
    return HttpClient.Version.HTTP_1_1;
  }
}
