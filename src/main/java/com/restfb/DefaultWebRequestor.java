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

import static com.restfb.logging.RestFBLogger.HTTP_LOGGER;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.*;
import java.util.function.BiConsumer;

import com.restfb.types.FacebookReelAttachment;
import com.restfb.util.StringUtils;
import com.restfb.util.UrlUtils;

/**
 * Default implementation of a service that sends HTTP requests to the Facebook API endpoint.
 * 
 * @author <a href="http://restfb.com">Mark Allen</a>
 */
public class DefaultWebRequestor implements WebRequestor {
  /**
   * Arbitrary unique boundary marker for multipart {@code POST}s.
   */
  private static final String MULTIPART_BOUNDARY = "**boundarystringwhichwill**neverbeencounteredinthewild**";

  /**
   * Line separator for multipart {@code POST}s.
   */
  private static final String MULTIPART_CARRIAGE_RETURN_AND_NEWLINE = "\r\n";

  /**
   * Hyphens for multipart {@code POST}s.
   */
  private static final String MULTIPART_TWO_HYPHENS = "--";

  /**
   * Default buffer size for multipart {@code POST}s.
   */
  private static final int MULTIPART_DEFAULT_BUFFER_SIZE = 8192;

  /**
   * By default, how long should we wait for a response (in ms)?
   */
  private static final int DEFAULT_READ_TIMEOUT_IN_MS = 180000;

  private Map<String, List<String>> currentHeaders;

  private DebugHeaderInfo debugHeaderInfo;

  /**
   * By default, this is true, to prevent breaking existing usage
   */
  private boolean autocloseBinaryAttachmentStream = true;

  protected enum HttpMethod {
    GET, DELETE, POST
  }

  @Override
  public Response executeGet(Request request) throws IOException {
    return execute(HttpMethod.GET, request);
  }

  private Response executeReelUpload(Request request) throws IOException {
    Optional<FacebookReelAttachment> reelOpt = request.getReel();

    if (!reelOpt.isPresent()) {
      throw new IllegalArgumentException("Try uploading reel with corrupt request");
    }

    FacebookReelAttachment reel = reelOpt.get();

    logRequestAndAttachmentOnDebug(request, request.getBinaryAttachments());

    HttpURLConnection httpUrlConnection = null;

    try {
      String url = request.getUrl();
      httpUrlConnection = openConnection(new URL(url));
      httpUrlConnection.setReadTimeout(DEFAULT_READ_TIMEOUT_IN_MS);

      // Allow subclasses to customize the connection if they'd like to - set
      // their own headers, timeouts, etc.
      customizeConnection(httpUrlConnection);

      httpUrlConnection.setRequestMethod(HttpMethod.POST.name());
      httpUrlConnection.setDoOutput(true);
      httpUrlConnection.setUseCaches(false);

      httpUrlConnection.setRequestProperty("Connection", "Keep-Alive");

      initHeaderAccessToken(httpUrlConnection, request);
      fillReelHeader(httpUrlConnection, reel);

      httpUrlConnection.connect();

      if (reel.isBinary()) {
        try (OutputStream outputStream = httpUrlConnection.getOutputStream()) {
          write(reel.getData(), outputStream, MULTIPART_DEFAULT_BUFFER_SIZE);
        }
      }

      HTTP_LOGGER.debug("Response headers: {}", httpUrlConnection.getHeaderFields());
      fillHeaderAndDebugInfo(httpUrlConnection);
      return fetchResponse(httpUrlConnection);
    } finally {
      closeAttachmentsOnAutoClose(request.getBinaryAttachments());
      closeQuietly(httpUrlConnection);
    }
  }

  private void fillReelHeader(HttpURLConnection httpUrlConnection, FacebookReelAttachment reel) {
    if (reel.isBinary()) {
      httpUrlConnection.setRequestProperty("offset", "0");
      httpUrlConnection.setRequestProperty("file_size", String.valueOf(reel.getFileSizeInBytes()));
    } else {
      httpUrlConnection.setRequestProperty("file_url", reel.getReelUrl());
    }
  }

  @Override
  public Response executePost(Request request) throws IOException {
    // special handling for reel upload
    if (request.isReelUpload()) {
      return executeReelUpload(request);
    }

    List<BinaryAttachment> binaryAttachments = request.getBinaryAttachments();

    logRequestAndAttachmentOnDebug(request, binaryAttachments);

    HttpURLConnection httpUrlConnection = null;

    try {
      String url = buildPostUrl(request, binaryAttachments);
      httpUrlConnection = openConnection(new URL(url));
      httpUrlConnection.setReadTimeout(DEFAULT_READ_TIMEOUT_IN_MS);

      // Allow subclasses to customize the connection if they'd like to - set
      // their own headers, timeouts, etc.
      customizeConnection(httpUrlConnection);

      httpUrlConnection.setRequestMethod(HttpMethod.POST.name());
      httpUrlConnection.setDoOutput(true);
      httpUrlConnection.setUseCaches(false);

      initHeaderAccessToken(httpUrlConnection, request);

      if (!binaryAttachments.isEmpty()) {
        setMultipartRequestProperties(httpUrlConnection);
      }

      if (request.hasBody()) {
        setJsonRequestProperties(httpUrlConnection);
      }

      httpUrlConnection.connect();

      try (OutputStream outputStream = httpUrlConnection.getOutputStream()) {

        // If we have binary attachments, the body is just the attachments and the
        // other parameters are passed in via the URL.
        // Otherwise the body is the URL parameter string.
        if (!binaryAttachments.isEmpty()) {
          writeBinaryAttachments(binaryAttachments, outputStream);
        } else {
          writeRequestToOutputStream(request, outputStream);
        }
      }

      HTTP_LOGGER.debug("Response headers: {}", httpUrlConnection.getHeaderFields());
      fillHeaderAndDebugInfo(httpUrlConnection);
      return fetchResponse(httpUrlConnection);
    } finally {
      closeAttachmentsOnAutoClose(binaryAttachments);
      closeQuietly(httpUrlConnection);
    }
  }

  private void writeBinaryAttachments(List<BinaryAttachment> binaryAttachments, OutputStream outputStream) throws IOException {
    for (BinaryAttachment binaryAttachment : binaryAttachments) {
      writeBinaryAttachmentToOutputStream(binaryAttachment, outputStream);
    }
  }

  private void setJsonRequestProperties(HttpURLConnection httpUrlConnection) {
    httpUrlConnection.setRequestProperty("Content-Type", "application/json");
  }

  private void setMultipartRequestProperties(HttpURLConnection httpUrlConnection) {
    httpUrlConnection.setRequestProperty("Connection", "Keep-Alive");
    httpUrlConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + MULTIPART_BOUNDARY);
  }

  private String buildPostUrl(Request request, List<BinaryAttachment> binaryAttachments) {
    return request.getUrl() + ((!binaryAttachments.isEmpty() || request.hasBody()) ? "?" + request.getParameters() : "");
  }

  private void writeBinaryAttachmentToOutputStream(BinaryAttachment binaryAttachment, OutputStream outputStream) throws IOException {
    StringBuilder formData = createBinaryAttachmentFormData(binaryAttachment);
    outputStream.write(formData.toString().getBytes(StringUtils.ENCODING_CHARSET));
    write(binaryAttachment.getData(), outputStream, MULTIPART_DEFAULT_BUFFER_SIZE);
    outputStream.write((MULTIPART_CARRIAGE_RETURN_AND_NEWLINE + MULTIPART_TWO_HYPHENS + MULTIPART_BOUNDARY
            + MULTIPART_TWO_HYPHENS + MULTIPART_CARRIAGE_RETURN_AND_NEWLINE).getBytes(StringUtils.ENCODING_CHARSET));
  }

  private static void writeRequestToOutputStream(Request request, OutputStream outputStream) throws IOException {
    if (request.hasBody()) {
      outputStream.write(request.getBody().getData().getBytes(StringUtils.ENCODING_CHARSET));
    } else {
      outputStream.write(request.getParameters().getBytes(StringUtils.ENCODING_CHARSET));
    }
  }

  private static void logRequestAndAttachmentOnDebug(Request request, List<BinaryAttachment> binaryAttachments) {
    if (HTTP_LOGGER.isDebugEnabled()) {
      HTTP_LOGGER.debug("Executing a POST to " + request.getUrl() + " with parameters "
              + (!binaryAttachments.isEmpty() ? "" : "(sent in request body): ")
              + UrlUtils.urlDecode(request.getParameters())
              + (!binaryAttachments.isEmpty() ? " and " + binaryAttachments.size() + " binary attachment[s]." : ""));
    }
  }

  private StringBuilder createBinaryAttachmentFormData(BinaryAttachment binaryAttachment) {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(MULTIPART_TWO_HYPHENS).append(MULTIPART_BOUNDARY).append(MULTIPART_CARRIAGE_RETURN_AND_NEWLINE)
      .append("Content-Disposition: form-data; name=\"").append(createFormFieldName(binaryAttachment))
      .append("\"; filename=\"").append(binaryAttachment.getFilename()).append("\"");

    stringBuilder.append(MULTIPART_CARRIAGE_RETURN_AND_NEWLINE).append("Content-Type: ")
      .append(binaryAttachment.getContentType());

    stringBuilder.append(MULTIPART_CARRIAGE_RETURN_AND_NEWLINE).append(MULTIPART_CARRIAGE_RETURN_AND_NEWLINE);
    return stringBuilder;
  }

  private void closeAttachmentsOnAutoClose(List<BinaryAttachment> binaryAttachments) {
    if (autocloseBinaryAttachmentStream && !binaryAttachments.isEmpty()) {
      binaryAttachments.stream().filter(BinaryAttachment::hasBinaryData).map(BinaryAttachment::getData).forEach(this::closeQuietly);
    }
  }

  protected void initHeaderAccessToken(HttpURLConnection httpUrlConnection, Request request) {
    if (request.isReelUpload()) {
      httpUrlConnection.setRequestProperty("Authorization", "OAuth " + request.getHeaderAccessToken());
    } else if (request.hasHeaderAccessToken()) {
      httpUrlConnection.setRequestProperty("Authorization", "Bearer " + request.getHeaderAccessToken());
    }
  }

  /**
   * Given a {@code url}, opens and returns a connection to it.
   * <p>
   * If you'd like to pipe your connection through a proxy, this is the place to do so.
   * 
   * @param url
   *          The URL to connect to.
   * @return A connection to the URL.
   * @throws IOException
   *           If an error occurs while establishing the connection.
   * @since 1.6.3
   */
  protected HttpURLConnection openConnection(URL url) throws IOException {
    return new Java11HttpClientConnection(url, createHttpClientBuilder());
  }

  /**
   * Factory for the HTTP client builder used by {@link Java11HttpClientConnection}.
   *
   * <p>
   * Subclasses may override this if they need a custom executor, SSL context, or proxy configuration.
   * </p>
   *
   * @return pre-configured {@link HttpClient.Builder}
   */
  protected HttpClient.Builder createHttpClientBuilder() {
    return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL);
  }

  /**
   * Hook method which allows subclasses to easily customize the {@code connection}s created by
   * {@link #executeGet(com.restfb.WebRequestor.Request)} and {@link #executePost(com.restfb.WebRequestor.Request)} -
   * for example, setting a custom read timeout or request header.
   * <p>
   * This implementation is a no-op.
   * 
   * @param connection
   *          The connection to customize.
   */
  protected void customizeConnection(HttpURLConnection connection) {
    // This implementation is a no-op
  }

  /**
   * Attempts to cleanly close a resource, swallowing any exceptions that might occur since there's no way to recover
   * anyway.
   * <p>
   * It's OK to pass {@code null} in, this method will no-op in that case.
   * 
   * @param closeable
   *          The resource to close.
   */
  protected void closeQuietly(Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (Exception t) {
        HTTP_LOGGER.warn("Unable to close {}: ", closeable, t);
      }
    }
  }

  /**
   * Attempts to cleanly close an {@code HttpURLConnection}, swallowing any exceptions that might occur since there's no
   * way to recover anyway.
   * <p>
   * It's OK to pass {@code null} in, this method will no-op in that case.
   * 
   * @param httpUrlConnection
   *          The connection to close.
   */
  protected void closeQuietly(HttpURLConnection httpUrlConnection) {
    try {
      Optional.ofNullable(httpUrlConnection).ifPresent(HttpURLConnection::disconnect);
    } catch (Exception t) {
      HTTP_LOGGER.warn("Unable to disconnect {}: ", httpUrlConnection, t);
    }
  }

  /**
   * Writes the contents of the {@code source} stream to the {@code destination} stream using the given
   * {@code bufferSize}.
   * 
   * @param source
   *          The source stream to copy from.
   * @param destination
   *          The destination stream to copy to.
   * @param bufferSize
   *          The size of the buffer to use during the copy operation.
   * @throws IOException
   *           If an error occurs when reading from {@code source} or writing to {@code destination}.
   * @throws NullPointerException
   *           If either {@code source} or @{code destination} is {@code null}.
   */
  protected void write(InputStream source, OutputStream destination, int bufferSize) throws IOException {
    if (source == null || destination == null) {
      throw new IllegalArgumentException("Must provide non-null source and destination streams.");
    }

    int read;
    byte[] chunk = new byte[bufferSize];
    while ((read = source.read(chunk)) > 0)
      destination.write(chunk, 0, read);
  }

  /**
   * Creates the form field name for the binary attachment filename by stripping off the file extension - for example,
   * the filename "test.png" would return "test".
   * 
   * @param binaryAttachment
   *          The binary attachment for which to create the form field name.
   * @return The form field name for the given binary attachment.
   */
  protected String createFormFieldName(BinaryAttachment binaryAttachment) {

    if (binaryAttachment.getFieldName() != null) {
      return binaryAttachment.getFieldName();
    }

    String name = binaryAttachment.getFilename();
    return Optional.ofNullable(name).filter(f -> f.contains(".")).map(f -> f.substring(0, f.lastIndexOf('.')))
      .orElse(name);
  }

  /**
   * returns if the binary attachment stream is closed automatically
   * 
   * @since 1.7.0
   * @return {@code true} if the binary stream should be closed automatically, {@code false} otherwise
   */
  public boolean isAutocloseBinaryAttachmentStream() {
    return autocloseBinaryAttachmentStream;
  }

  /**
   * define if the binary attachment stream is closed automatically after sending the content to facebook
   * 
   * @since 1.7.0
   * @param autocloseBinaryAttachmentStream
   *          {@code true} if the {@link BinaryAttachment} stream should be closed automatically, {@code false}
   *          otherwise
   */
  public void setAutocloseBinaryAttachmentStream(boolean autocloseBinaryAttachmentStream) {
    this.autocloseBinaryAttachmentStream = autocloseBinaryAttachmentStream;
  }

  /**
   * access to the current response headers
   * 
   * @return the current reponse header map
   */
  public Map<String, List<String>> getCurrentHeaders() {
    return currentHeaders;
  }

  @Override
  public Response executeDelete(Request request) throws IOException {
    return execute(HttpMethod.DELETE, request);
  }

  @Override
  public DebugHeaderInfo getDebugHeaderInfo() {
    return debugHeaderInfo;
  }

  private Response execute(HttpMethod httpMethod, Request request) throws IOException {
    HTTP_LOGGER.debug("Making a {} request to {} with parameters {}", httpMethod.name(), request.getUrl(),
      request.getParameters());

    HttpURLConnection httpUrlConnection = null;

    try {
      httpUrlConnection = openConnection(new URL(request.getFullUrl()));
      httpUrlConnection.setReadTimeout(DEFAULT_READ_TIMEOUT_IN_MS);
      httpUrlConnection.setUseCaches(false);
      httpUrlConnection.setRequestMethod(httpMethod.name());

      initHeaderAccessToken(httpUrlConnection, request);

      // Allow subclasses to customize the connection if they'd like to - set
      // their own headers, timeouts, etc.
      customizeConnection(httpUrlConnection);

      httpUrlConnection.connect();

      HTTP_LOGGER.trace("Response headers: {}", httpUrlConnection.getHeaderFields());
      fillHeaderAndDebugInfo(httpUrlConnection);
      return fetchResponse(httpUrlConnection);
    } finally {
      closeQuietly(httpUrlConnection);
    }
  }

  protected void fillHeaderAndDebugInfo(HttpURLConnection httpUrlConnection) {
    currentHeaders = Collections.unmodifiableMap(httpUrlConnection.getHeaderFields());

    String usedApiVersion = StringUtils.trimToEmpty(httpUrlConnection.getHeaderField("facebook-api-version"));
    HTTP_LOGGER.debug("Facebook used the API {} to answer your request", usedApiVersion);

    Version usedVersion = Version.getVersionFromString(usedApiVersion);
    DebugHeaderInfo.DebugHeaderInfoFactory factory =
        DebugHeaderInfo.DebugHeaderInfoFactory.create().setVersion(usedVersion);

    Arrays.stream(FbHeaderField.values()).forEach(f -> f.getPutHeader().accept(httpUrlConnection, factory));
    debugHeaderInfo = factory.build();
  }

  protected Response fetchResponse(HttpURLConnection httpUrlConnection) throws IOException {
    InputStream inputStream = null;
    try {
      inputStream = getInputStreamFromUrlConnection(httpUrlConnection);
    } catch (IOException e) {
      HTTP_LOGGER.warn("An error occurred while making a {} request to {}:", httpUrlConnection.getRequestMethod(),
        httpUrlConnection.getURL(), e);
    }

    Response response = new Response(httpUrlConnection.getResponseCode(), StringUtils.fromInputStream(inputStream));
    HTTP_LOGGER.debug("Facebook responded with {}", response);
    return response;
  }

  private InputStream getInputStreamFromUrlConnection(HttpURLConnection httpUrlConnection) throws IOException {
    return httpUrlConnection.getResponseCode() != HttpURLConnection.HTTP_OK ? httpUrlConnection.getErrorStream()
        : httpUrlConnection.getInputStream();
  }

  private enum FbHeaderField {
    X_FB_TRACE_ID((c, f) -> f.setTraceId(getHeaderOrEmpty(c, "x-fb-trace-id"))), //
    X_FB_REV((c, f) -> f.setRev(getHeaderOrEmpty(c, "x-fb-rev"))), //
    X_FB_DEBUG((c, f) -> f.setDebug(getHeaderOrEmpty(c, "x-fb-debug"))), //
    X_APP_USAGE((c, f) -> f.setAppUsage(getHeaderOrEmpty(c, "x-app-usage"))), //
    X_PAGE_USAGE((c, f) -> f.setPageUsage(getHeaderOrEmpty(c, "x-page-usage"))), //
    X_AD_ACCOUNT_USAGE((c, f) -> f.setAdAccountUsage(getHeaderOrEmpty(c, "x-ad-account-usage"))), //
    X_BUSINESS_USE_CASE_USAGE((c, f) -> f.setBusinessUseCaseUsage(getHeaderOrEmpty(c, "x-business-use-case-usage")));

    private final BiConsumer<HttpURLConnection, DebugHeaderInfo.DebugHeaderInfoFactory> putHeader;

    FbHeaderField(BiConsumer<HttpURLConnection, DebugHeaderInfo.DebugHeaderInfoFactory> headerFunction) {
      this.putHeader = headerFunction;
    }

    public BiConsumer<HttpURLConnection, DebugHeaderInfo.DebugHeaderInfoFactory> getPutHeader() {
      return putHeader;
    }

    private static String getHeaderOrEmpty(HttpURLConnection connection, String fieldName) {
      return StringUtils.trimToEmpty(connection.getHeaderField(fieldName));
    }
  }

  /**
   * HttpURLConnection-compatible wrapper backed by the Java 11 {@link HttpClient}.
   */
  private static final class Java11HttpClientConnection extends HttpURLConnection {

    private static final int HTTP_ERROR_THRESHOLD = 400;

    private final HttpClient.Builder httpClientBuilder;
    private final Map<String, List<String>> requestHeaders = new LinkedHashMap<>();
    private Map<String, List<String>> responseHeaders = Collections.emptyMap();
    private List<Map.Entry<String, List<String>>> responseHeaderIndex = Collections.emptyList();
    private final BodyStorage bodyStorage = new BodyStorage();

    private HttpResponse<byte[]> httpResponse;
    private IOException sendException;
    private int readTimeoutMillis;
    private int connectTimeoutMillis;

    Java11HttpClientConnection(URL url, HttpClient.Builder builder) {
      super(url);
      this.httpClientBuilder = builder;
    }

    @Override
    public void connect() {
      connected = true;
    }

    @Override
    public void disconnect() {
      try {
        bodyStorage.close();
      } catch (IOException e) {
        HTTP_LOGGER.warn("Unable to close request body storage", e);
      }
    }

    @Override
    public boolean usingProxy() {
      return false;
    }

    @Override
    public void setReadTimeout(int timeout) {
      this.readTimeoutMillis = timeout;
    }

    @Override
    public int getReadTimeout() {
      return readTimeoutMillis;
    }

    @Override
    public void setConnectTimeout(int timeout) {
      this.connectTimeoutMillis = timeout;
    }

    @Override
    public int getConnectTimeout() {
      return connectTimeoutMillis;
    }

    @Override
    public void setRequestProperty(String key, String value) {
      if (key == null) {
        throw new NullPointerException("Request header key must not be null");
      }
      List<String> values = new ArrayList<>();
      values.add(value);
      requestHeaders.put(key, values);
    }

    @Override
    public void addRequestProperty(String key, String value) {
      if (key == null) {
        throw new NullPointerException("Request header key must not be null");
      }
      requestHeaders.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
      if (connected) {
        throw new IllegalStateException("Cannot access request headers after connection is made");
      }
      return Collections.unmodifiableMap(requestHeaders);
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
      if (!getDoOutput()) {
        setDoOutput(true);
      }
      return bodyStorage.outputStream();
    }

    @Override
    public InputStream getInputStream() throws IOException {
      ensureResponse();
      if (getResponseCode() >= HTTP_ERROR_THRESHOLD) {
        throw new IOException(buildErrorMessage());
      }
      return toStream(httpResponse.body());
    }

    @Override
    public InputStream getErrorStream() {
      try {
        ensureResponse();
        if (getResponseCode() >= HTTP_ERROR_THRESHOLD || getResponseCode() != HttpURLConnection.HTTP_OK) {
          return toStream(httpResponse.body());
        }
      } catch (IOException e) {
        return null;
      }
      return null;
    }

    @Override
    public int getResponseCode() throws IOException {
      ensureResponse();
      return httpResponse.statusCode();
    }

    @Override
    public String getResponseMessage() throws IOException {
      ensureResponse();
      return "";
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
      try {
        ensureResponse();
      } catch (IOException e) {
        return Collections.emptyMap();
      }
      return responseHeaders;
    }

    @Override
    public String getHeaderField(String name) {
      try {
        ensureResponse();
      } catch (IOException e) {
        return null;
      }

      if (name == null) {
        return null;
      }

      for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
        if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
          return entry.getValue().isEmpty() ? null : entry.getValue().get(0);
        }
      }
      return null;
    }

    @Override
    public String getHeaderFieldKey(int n) {
      try {
        ensureResponse();
      } catch (IOException e) {
        return null;
      }
      if (n < 0 || n >= responseHeaderIndex.size()) {
        return null;
      }
      return responseHeaderIndex.get(n).getKey();
    }

    @Override
    public String getHeaderField(int n) {
      try {
        ensureResponse();
      } catch (IOException e) {
        return null;
      }
      if (n < 0 || n >= responseHeaderIndex.size()) {
        return null;
      }
      List<String> values = responseHeaderIndex.get(n).getValue();
      return values.isEmpty() ? null : values.get(0);
    }

    private void ensureResponse() throws IOException {
      if (httpResponse != null) {
        return;
      }

      if (sendException != null) {
        throw sendException;
      }

      try {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(url.toURI());
        String methodName = Optional.ofNullable(method).orElse(HttpMethod.GET.name());
        requestBuilder.method(methodName, buildBodyPublisher());

        if (readTimeoutMillis > 0) {
          requestBuilder.timeout(Duration.ofMillis(readTimeoutMillis));
        }

        requestHeaders.forEach((k, v) -> v.forEach(value -> requestBuilder.header(k, value)));

        if (connectTimeoutMillis > 0) {
          httpClientBuilder.connectTimeout(Duration.ofMillis(connectTimeoutMillis));
        }

        HttpClient client = httpClientBuilder.build();
        httpResponse = client.send(requestBuilder.build(), BodyHandlers.ofByteArray());
        responseHeaders = createHeaderFieldMap(httpResponse);
      } catch (URISyntaxException e) {
        sendException = new IOException("Unable to create request URI", e);
        throw sendException;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        sendException = new IOException("Interrupted while performing request", e);
        throw sendException;
      } catch (IOException e) {
        sendException = e;
        throw e;
      } finally {
        try {
          bodyStorage.close();
        } catch (IOException e) {
          HTTP_LOGGER.warn("Unable to cleanup request body storage", e);
        }
      }
    }

    private BodyPublisher buildBodyPublisher() throws IOException {
      if (!getDoOutput() || !bodyStorage.hasData()) {
        return BodyPublishers.noBody();
      }

      bodyStorage.prepareForRead();
      Path bodyPath = bodyStorage.getTempFile();

      return BodyPublishers.ofInputStream(() -> {
        try {
          return Files.newInputStream(bodyPath, StandardOpenOption.READ);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    }

    private Map<String, List<String>> createHeaderFieldMap(HttpResponse<byte[]> response) {
      Map<String, List<String>> headers = new LinkedHashMap<>();
      response.headers().map().forEach((k, v) -> headers.put(k, Collections.unmodifiableList(v)));
      responseHeaders = Collections.unmodifiableMap(headers);
      responseHeaderIndex = new ArrayList<>(headers.entrySet());
      return responseHeaders;
    }

    private static InputStream toStream(byte[] body) {
      byte[] safeBody = Optional.ofNullable(body).orElse(new byte[0]);
      return new ByteArrayInputStream(safeBody);
    }

    private String buildErrorMessage() throws IOException {
      return "Server returned HTTP response code: " + getResponseCode() + " for URL: " + url;
    }

    private static final class BodyStorage implements Closeable {

      private Path tempFile;
      private OutputStream outputStream;
      private boolean hasData;

      OutputStream outputStream() throws IOException {
        if (outputStream == null) {
          tempFile = Files.createTempFile("restfb-request", ".bin");
          outputStream = Files.newOutputStream(tempFile, StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        }
        hasData = true;
        return outputStream;
      }

      boolean hasData() {
        return hasData;
      }

      Path getTempFile() {
        return tempFile;
      }

      void prepareForRead() throws IOException {
        if (outputStream != null) {
          outputStream.flush();
          outputStream.close();
          outputStream = null;
        }
      }

      @Override
      public void close() throws IOException {
        if (outputStream != null) {
          outputStream.close();
          outputStream = null;
        }
        if (tempFile != null) {
          Files.deleteIfExists(tempFile);
          tempFile = null;
        }
        hasData = false;
      }
    }
  }

}
