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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import com.restfb.exception.FacebookGoawayNetworkException;
import org.junit.jupiter.api.Test;

import com.restfb.exception.FacebookRstStreamNetworkException;

class DefaultFacebookClientTest {

  @Test
  void throwsStreamResetExceptionOnRstStream() {
    TestableFacebookClient client = new TestableFacebookClient();

    DefaultFacebookClient.Requestor failingRequestor = () -> {
      throw new IOException("Received RST_STREAM: Internal error");
    };

    assertThatThrownBy(() -> client.invokeMakeRequest(failingRequestor))
      .isInstanceOf(FacebookRstStreamNetworkException.class).hasMessageContaining("RST_STREAM");
  }

  @Test
  void throwsGoawyExceptionOnRstStream() {
    TestableFacebookClient client = new TestableFacebookClient();

    DefaultFacebookClient.Requestor failingRequestor = () -> {
      throw new IOException("Received GOAWAY from upstream");
    };

    assertThatThrownBy(() -> client.invokeMakeRequest(failingRequestor))
      .isInstanceOf(FacebookGoawayNetworkException.class).hasMessageContaining("GOAWAY");
  }

  private static class TestableFacebookClient extends DefaultFacebookClient {
    TestableFacebookClient() {
      super("token", new DefaultWebRequestor(), new DefaultJsonMapper(), Version.LATEST);
    }

    String invokeMakeRequest(Requestor requestor) {
      return makeRequestAndProcessResponse(requestor);
    }
  }
}
