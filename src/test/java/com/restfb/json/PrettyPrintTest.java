/*******************************************************************************
 * Copyright (c) 2015 EclipseSource.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 ******************************************************************************/
package com.restfb.json;

import static com.restfb.json.PrettyPrint.*;
import static java.util.Locale.US;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class PrettyPrintTest {

  private StringWriter output;

  @BeforeEach
  void setUp() {
    output = new StringWriter();
  }

  @Test
  void testIndentWithSpaces_emptyArray() throws IOException {
    new JsonArray().writeTo(output, indentWithSpaces(2));

    assertEquals("[\n  \n]", output.toString());
  }

  @Test
  void testIndentWithSpaces_emptyObject() throws IOException {
    new JsonObject().writeTo(output, indentWithSpaces(2));

    assertEquals("{\n  \n}", output.toString());
  }

  @Test
  void testIndentWithSpaces_array() throws IOException {
    new JsonArray().add(23).add(42).writeTo(output, indentWithSpaces(2));

    assertEquals("[\n  23,\n  42\n]", output.toString());
  }

  @Test
  void testIndentWithSpaces_nestedArray() throws IOException {
    new JsonArray().add(23)
                   .add(new JsonArray().add(42))
                   .writeTo(output, indentWithSpaces(2));

    assertEquals("[\n  23,\n  [\n    42\n  ]\n]", output.toString());
  }

  @Test
  void testIndentWithSpaces_object() throws IOException {
    new JsonObject().add("a", 23).add("b", 42).writeTo(output, indentWithSpaces(2));

    assertEquals("{\n  \"a\": 23,\n  \"b\": 42\n}", output.toString());
  }

  @Test
  void testIndentWithSpaces_nestedObject() throws IOException {
    new JsonObject().add("a", 23)
                    .add("b", new JsonObject().add("c", 42))
                    .writeTo(output, indentWithSpaces(2));

    assertEquals("{\n  \"a\": 23,\n  \"b\": {\n    \"c\": 42\n  }\n}", output.toString());
  }

  @Test
  void testIndentWithSpaces_zero() throws IOException {
    new JsonArray().add(23).add(42).writeTo(output, indentWithSpaces(0));

    assertEquals("[\n23,\n42\n]", output.toString());
  }

  @Test
  void testIndentWithSpaces_one() throws IOException {
    new JsonArray().add(23).add(42).writeTo(output, indentWithSpaces(1));

    assertEquals("[\n 23,\n 42\n]", output.toString());
  }

  @Test
  void testIndentWithSpaces_failsWithNegativeValues() {
    try {
      indentWithSpaces(-1);
      fail();
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().toLowerCase(US).contains("negative"));
    }
  }

  @Test
  void testIndentWithSpaces_createsIndependentInstances() {
    Writer writer = mock(Writer.class);

    WriterConfig config = indentWithSpaces(1);
    Object instance1 = config.createWriter(writer);
    Object instance2 = config.createWriter(writer);

    assertNotSame(instance1, instance2);
  }

  @Test
  void testIndentWithTabs() throws IOException {
    new JsonArray().add(23).add(42).writeTo(output, indentWithTabs());

    assertEquals("[\n\t23,\n\t42\n]", output.toString());
  }

  @Test
  void testIndentWithTabs_createsIndependentInstances() {
    Writer writer = mock(Writer.class);

    WriterConfig config = indentWithTabs();
    Object instance1 = config.createWriter(writer);
    Object instance2 = config.createWriter(writer);

    assertNotSame(instance1, instance2);
  }

  @Test
  void testSingleLine_nestedArray() throws IOException {
    new JsonArray().add(23).add(new JsonArray().add(42)).writeTo(output, singleLine());

    assertEquals("[23, [42]]", output.toString());
  }

  @Test
  void testSingleLine_nestedObject() throws IOException {
    new JsonObject().add("a", 23)
                    .add("b", new JsonObject().add("c", 42))
                    .writeTo(output, singleLine());

    assertEquals("{\"a\": 23, \"b\": {\"c\": 42}}", output.toString());
  }

  @Test
  void testSingleLine_createsIndependentInstances() {
    Writer writer = mock(Writer.class);

    WriterConfig config = singleLine();
    Object instance1 = config.createWriter(writer);
    Object instance2 = config.createWriter(writer);

    assertNotSame(instance1, instance2);
  }

}
