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

import static java.util.Collections.unmodifiableList;

import java.util.ArrayList;
import java.util.List;

import com.restfb.Facebook;

import lombok.Getter;
import lombok.Setter;

public class GranularScope extends AbstractFacebookType {

    private static final long serialVersionUID = 1L;

    /**
     * The permission granted by the user.
     *
     * @return The permission granted by the user.
     */
    @Getter
    @Setter
    @Facebook
    private String scope;

    /**
     * The target ids of Pages, Groups, or business assets the user granted the above permission for.
     */
    @Facebook("target_ids")
    private List<String> targetIds = new ArrayList<>();

    /**
     * The target ids of Pages, Groups, or business assets the user granted the above permission for.
     *
     * @return The target ids of Pages, Groups, or business assets the user granted the above permission for.
     */
    public List<String> getTargetIds() {
        return unmodifiableList(targetIds);
    }
}
