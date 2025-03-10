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
package com.restfb.scope;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ScopeBuilderTest {

  @Test
  void noPermission() {
    ScopeBuilder s = new ScopeBuilder();
    assertThat(s).hasToString("public_profile");
  }

  @Test
  void noPublicProfilePermission() {
    ScopeBuilder s = new ScopeBuilder(true);
    assertThat(s.toString()).isEmpty();
  }

  @Test
  void singlePermission() {
    ScopeBuilder s = new ScopeBuilder();
    s.addPermission(FacebookPermissions.USER_GENDER);
    assertThat(s).hasToString("public_profile,user_gender");
  }

  @Test
  void twoPermissions() {
    ScopeBuilder s = new ScopeBuilder();
    s.addPermission(FacebookPermissions.USER_GENDER);
    s.addPermission(FacebookPermissions.USER_AGE_RANGE);
    assertThat(s).hasToString("public_profile,user_gender,user_age_range");
  }

  @Test
  void multiPermissions() {
    ScopeBuilder s = new ScopeBuilder(true);
    List<FacebookPermissions> permissions = new ArrayList<>();
    permissions.add(FacebookPermissions.THREADS_BASIC);
    permissions.add(FacebookPermissions.THREADS_CONTENT_PUBLISH);
    permissions.add(FacebookPermissions.THREADS_MANAGE_INSIGHTS);
    s.addPermissions( permissions);
    assertThat(s).hasToString("threads_basic,threads_content_publish,threads_manage_insights");
  }

}
