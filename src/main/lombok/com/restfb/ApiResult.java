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

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;

/**
 * Carries the result of a Graph API call plus response metadata such as debug headers.
 *
 * @param <T>
 *          type of the mapped result
 */
@Getter
public final class ApiResult<T> implements Serializable {

  private static final long serialVersionUID = 1L;

  private final T result;

  private final DebugHeaderInfo debugHeaderInfo;

  private final Map<String, List<String>> responseHeaders;

  private final Duration duration;

  private final String httpMethod;

  private final String requestUrl;

  private ApiResult(T result, DebugHeaderInfo debugHeaderInfo, Map<String, List<String>> responseHeaders,
      Duration duration, String httpMethod, String requestUrl) {
    this.result = result;
    this.debugHeaderInfo = debugHeaderInfo;
    this.responseHeaders = responseHeaders;
    this.duration = duration;
    this.httpMethod = httpMethod;
    this.requestUrl = requestUrl;
  }

  public static <T> ApiResult<T> withMetadata(T result, DebugHeaderInfo debugHeaderInfo,
      Map<String, List<String>> responseHeaders, Duration duration, String httpMethod, String requestUrl) {
    return new ApiResult<>(result, debugHeaderInfo, copyHeaders(responseHeaders), duration, httpMethod,
      requestUrl);
  }

  public static <T> ApiResult<T> withoutMetadata(T result) {
    return new ApiResult<>(result, null, null, null, null, null);
  }

  private static Map<String, List<String>> copyHeaders(Map<String, List<String>> headers) {
    if (headers == null || headers.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, List<String>> copy = new LinkedHashMap<>();
    headers.forEach((key, value) -> {
      if (value == null) {
        copy.put(key, null);
      } else {
        copy.put(key, Collections.unmodifiableList(new ArrayList<>(value)));
      }
    });
    return Collections.unmodifiableMap(copy);
  }
}
