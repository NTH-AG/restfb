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
package com.restfb.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;

import org.junit.jupiter.api.Test;

import com.restfb.AbstractJsonMapperTests;

class DeAuthTest extends AbstractJsonMapperTests {

  @Test
  void userIdCheck() {
    DeAuth exampleDeAuth = createJsonMapper().toJavaObject(jsonFromClasspath("v2_1/deauth-user"), DeAuth.class);
    assertEquals("HMAC-SHA256", exampleDeAuth.getAlgorithm());
    assertNull(exampleDeAuth.getProfileId());
    assertEquals("12345678", exampleDeAuth.getUserId());
    assertEquals(new Date(1411072285000L), exampleDeAuth.getIssuedAt());
  }

  @Test
  void pageIdCheck() {
    DeAuth exampleDeAuth = createJsonMapper().toJavaObject(jsonFromClasspath("v2_1/deauth-fanpage"), DeAuth.class);
    assertEquals("HMAC-SHA256", exampleDeAuth.getAlgorithm());
    assertEquals("324556365474", exampleDeAuth.getProfileId());
    assertNull(exampleDeAuth.getUserId());
    assertEquals(new Date(1411072285000L), exampleDeAuth.getIssuedAt());
  }

}
