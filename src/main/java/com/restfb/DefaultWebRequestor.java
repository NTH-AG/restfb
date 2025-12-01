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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
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
import java.util.Objects;
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
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String CONTENT_TYPE_FORM_URLENCODED = "application/x-www-form-urlencoded";

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

  public static final String HEADER_CONTENT_TYPE = "Content-Type";

  private Map<String, List<String>> currentHeaders;

  private DebugHeaderInfo debugHeaderInfo;

  /**
   * By default, this is true, to prevent breaking existing usage
   */
  private boolean autocloseBinaryAttachmentStream = true;

  private final HttpClient httpClient;

  public DefaultWebRequestor() {
    this.httpClient = createHttpClientBuilder().build();
  }

  protected DefaultWebRequestor(HttpClient httpClient) {
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
  }

  protected HttpClient getHttpClient() {
    return httpClient;
  }

  protected enum HttpMethod {
    GET, DELETE, POST
  }

  @Override
  public Response executeGet(Request request) throws IOException {
    return execute(HttpMethod.GET, request);
  }

  private Response executeReelUpload(Request request) throws IOException {
    Optional<FacebookReelAttachment> reelOpt = request.getReel();

    if (reelOpt.isEmpty()) {
      throw new IllegalArgumentException("Try uploading reel with corrupt request");
    }

    FacebookReelAttachment reel = reelOpt.get();

    logRequestAndAttachmentOnDebug(request, request.getBinaryAttachments());

    TempFileBodyPublisher bodyPublisher = null;

    try {
      HttpRequest.Builder builder = openConnection(new URL(request.getUrl()));
      builder.timeout(Duration.ofMillis(DEFAULT_READ_TIMEOUT_IN_MS));
      initHeaderAccessToken(builder, request);
      fillReelHeader(builder, reel);

      customizeRequest(builder, request, HttpMethod.POST);

      BodyPublisher publisher = BodyPublishers.noBody();
      if (reel.isBinary()) {
        bodyPublisher = new TempFileBodyPublisher();
        try (OutputStream outputStream = bodyPublisher.outputStream()) {
          write(reel.getData(), outputStream, MULTIPART_DEFAULT_BUFFER_SIZE);
        }
        publisher = bodyPublisher.build();
      }

      HttpRequest httpRequest = builder.POST(publisher).build();
      return sendRequest(httpRequest);
    } finally {
      closeAttachmentsOnAutoClose(request.getBinaryAttachments());
      closeQuietly(bodyPublisher);
    }
  }

  private void fillReelHeader(HttpRequest.Builder builder, FacebookReelAttachment reel) {
    if (reel.isBinary()) {
      builder.header("offset", "0");
      builder.header("file_size", String.valueOf(reel.getFileSizeInBytes()));
    } else {
      builder.header("file_url", reel.getReelUrl());
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

    TempFileBodyPublisher bodyPublisher = null;

    try {
      String url = buildPostUrl(request, binaryAttachments);
      HttpRequest.Builder builder = openConnection(new URL(url));
      builder.timeout(Duration.ofMillis(DEFAULT_READ_TIMEOUT_IN_MS));

      initHeaderAccessToken(builder, request);

      if (!binaryAttachments.isEmpty()) {
        setMultipartRequestProperties(builder);
      } else if (request.hasBody()) {
        setJsonRequestProperties(builder);
      } else {
        setFormUrlEncodedRequestProperties(builder);
      }

      customizeRequest(builder, request, HttpMethod.POST);

      BodyPublisher publisher;
      if (!binaryAttachments.isEmpty()) {
        bodyPublisher = new TempFileBodyPublisher();
        try (OutputStream outputStream = bodyPublisher.outputStream()) {
          writeBinaryAttachments(binaryAttachments, outputStream);
        }
        publisher = bodyPublisher.build();
      } else {
        String payload =
            request.hasBody() ? request.getBody().getData() : Optional.ofNullable(request.getParameters()).orElse("");
        publisher = BodyPublishers.ofString(payload, StringUtils.ENCODING_CHARSET);
      }

      HttpRequest httpRequest = builder.POST(publisher).build();
      return sendRequest(httpRequest);
    } finally {
      closeAttachmentsOnAutoClose(binaryAttachments);
      closeQuietly(bodyPublisher);
    }
  }

  private void writeBinaryAttachments(List<BinaryAttachment> binaryAttachments, OutputStream outputStream)
      throws IOException {
    for (BinaryAttachment binaryAttachment : binaryAttachments) {
      writeBinaryAttachmentToOutputStream(binaryAttachment, outputStream);
    }
  }

  private void setMultipartRequestProperties(HttpRequest.Builder builder) {
    builder.header(HEADER_CONTENT_TYPE, "multipart/form-data;boundary=" + MULTIPART_BOUNDARY);
  }

  private void setJsonRequestProperties(HttpRequest.Builder builder) {
    builder.header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
  }

  private void setFormUrlEncodedRequestProperties(HttpRequest.Builder builder) {
    builder.header(HEADER_CONTENT_TYPE, CONTENT_TYPE_FORM_URLENCODED);
  }

  private String buildPostUrl(Request request, List<BinaryAttachment> binaryAttachments) {
    return request.getUrl()
        + ((!binaryAttachments.isEmpty() || request.hasBody()) ? "?" + request.getParameters() : "");
  }

  private void writeBinaryAttachmentToOutputStream(BinaryAttachment binaryAttachment, OutputStream outputStream)
      throws IOException {
    StringBuilder formData = createBinaryAttachmentFormData(binaryAttachment);
    outputStream.write(formData.toString().getBytes(StringUtils.ENCODING_CHARSET));
    write(binaryAttachment.getData(), outputStream, MULTIPART_DEFAULT_BUFFER_SIZE);
    outputStream.write((MULTIPART_CARRIAGE_RETURN_AND_NEWLINE + MULTIPART_TWO_HYPHENS + MULTIPART_BOUNDARY
        + MULTIPART_TWO_HYPHENS + MULTIPART_CARRIAGE_RETURN_AND_NEWLINE)
      .getBytes(StringUtils.ENCODING_CHARSET));
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
      binaryAttachments.stream().filter(BinaryAttachment::hasBinaryData).map(BinaryAttachment::getData)
        .forEach(this::closeQuietly);
    }
  }

  protected void initHeaderAccessToken(HttpRequest.Builder builder, Request request) {
    if (request.isReelUpload()) {
      builder.header("Authorization", "OAuth " + request.getHeaderAccessToken());
    } else if (request.hasHeaderAccessToken()) {
      builder.header("Authorization", "Bearer " + request.getHeaderAccessToken());
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
  protected HttpRequest.Builder openConnection(URL url) throws IOException {
    try {
      URI uri = url.toURI();
      return HttpRequest.newBuilder(uri);
    } catch (URISyntaxException e) {
      throw new IOException("Invalid URL", e);
    }
  }

  /**
   * Factory for the HTTP client builder used by this requestor.
   *
   * @return pre-configured {@link HttpClient.Builder}
   */
  protected HttpClient.Builder createHttpClientBuilder() {
    return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL);
  }

  /**
   * Hook method that lets subclasses tweak the {@link HttpRequest.Builder} before the request is sent.
   * <p>
   * Typical customizations include adding extra headers or adjusting timeouts per request type. This implementation is
   * a no-op.
   * </p>
   *
   * @param builder
   *          the builder for the outbound request
   * @param request
   *          the logical RestFB request that triggered this HTTP call
   * @param httpMethod
   *          the HTTP method used for the call
   */
  protected void customizeRequest(HttpRequest.Builder builder, Request request, HttpMethod httpMethod) {
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

    HttpRequest.Builder builder = openConnection(new URL(request.getFullUrl()));
    builder.timeout(Duration.ofMillis(DEFAULT_READ_TIMEOUT_IN_MS));

    initHeaderAccessToken(builder, request);
    customizeRequest(builder, request, httpMethod);

    switch (httpMethod) {
        case GET:
          builder.GET();
          break;
        case DELETE:
          builder.DELETE();
          break;
        default:
          throw new IllegalArgumentException("Unsupported httpMethod used");
    }

    HttpRequest httpRequest = builder.build();
    return sendRequest(httpRequest);
  }

  private Response sendRequest(HttpRequest httpRequest) throws IOException {
    try {
      HttpResponse<byte[]> httpResponse = getHttpClient().send(httpRequest, BodyHandlers.ofByteArray());
      HTTP_LOGGER.trace("Response headers: {}", httpResponse.headers().map());
      fillHeaderAndDebugInfo(httpResponse);
      return createResponse(httpResponse);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while making request", e);
    }
  }

  protected void fillHeaderAndDebugInfo(HttpResponse<byte[]> httpResponse) {
    currentHeaders = Collections.unmodifiableMap(toHeaderMap(httpResponse.headers()));

    String usedApiVersion = StringUtils.trimToEmpty(getHeaderValue(currentHeaders, "facebook-api-version"));
    HTTP_LOGGER.debug("Facebook used the API {} to answer your request", usedApiVersion);

    Version usedVersion = Version.getVersionFromString(usedApiVersion);
    DebugHeaderInfo.DebugHeaderInfoFactory factory =
        DebugHeaderInfo.DebugHeaderInfoFactory.create().setVersion(usedVersion);

    Arrays.stream(FbHeaderField.values()).forEach(f -> f.apply(currentHeaders, factory));
    debugHeaderInfo = factory.build();
  }

  protected Response createResponse(HttpResponse<byte[]> httpResponse) {
    byte[] body = Optional.ofNullable(httpResponse.body()).orElse(new byte[0]);
    Response response = new Response(httpResponse.statusCode(), StringUtils.toString(body));
    HTTP_LOGGER.debug("Facebook responded with {}", response);
    return response;
  }

  private Map<String, List<String>> toHeaderMap(HttpHeaders headers) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    headers.map().forEach((k, v) -> result.put(k, Collections.unmodifiableList(v)));
    return result;
  }

  private static String getHeaderValue(Map<String, List<String>> headers, String fieldName) {
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(fieldName)) {
        List<String> values = entry.getValue();
        if (values.isEmpty()) {
          return "";
        }
        return values.get(0);
      }
    }
    return "";
  }

  private enum FbHeaderField {
    X_FB_TRACE_ID("x-fb-trace-id", DebugHeaderInfo.DebugHeaderInfoFactory::setTraceId), //
    X_FB_REV("x-fb-rev", DebugHeaderInfo.DebugHeaderInfoFactory::setRev), //
    X_FB_DEBUG("x-fb-debug", DebugHeaderInfo.DebugHeaderInfoFactory::setDebug), //
    X_APP_USAGE("x-app-usage", DebugHeaderInfo.DebugHeaderInfoFactory::setAppUsage), //
    X_PAGE_USAGE("x-page-usage", DebugHeaderInfo.DebugHeaderInfoFactory::setPageUsage), //
    X_AD_ACCOUNT_USAGE("x-ad-account-usage", DebugHeaderInfo.DebugHeaderInfoFactory::setAdAccountUsage), //
    X_BUSINESS_USE_CASE_USAGE("x-business-use-case-usage",
        DebugHeaderInfo.DebugHeaderInfoFactory::setBusinessUseCaseUsage);

    private final String headerName;
    private final BiConsumer<DebugHeaderInfo.DebugHeaderInfoFactory, String> consumer;

    FbHeaderField(String headerName, BiConsumer<DebugHeaderInfo.DebugHeaderInfoFactory, String> consumer) {
      this.headerName = headerName;
      this.consumer = consumer;
    }

    void apply(Map<String, List<String>> headers, DebugHeaderInfo.DebugHeaderInfoFactory factory) {
      consumer.accept(factory, StringUtils.trimToEmpty(getHeaderValue(headers, headerName)));
    }
  }

  private static final class TempFileBodyPublisher implements Closeable {

    private final Path tempFile;
    private final OutputStream outputStream;
    private boolean closed;

    TempFileBodyPublisher() throws IOException {
      tempFile = Files.createTempFile("restfb-request", ".bin");
      outputStream = Files.newOutputStream(tempFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    OutputStream outputStream() {
      return outputStream;
    }

    BodyPublisher build() throws IOException {
      outputStream.flush();
      outputStream.close();
      closed = true;
      return BodyPublishers.ofFile(tempFile);
    }

    @Override
    public void close() throws IOException {
      if (!closed) {
        outputStream.close();
        closed = true;
      }
      Files.deleteIfExists(tempFile);
    }
  }

}
