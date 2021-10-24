/*
 * Copyright (C) 2021, Tue Ton <chirontt@gmail.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.github.chirontt.lfs.server.locks.internal;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

public class LfsFileLockingText extends TranslationBundle {

    /**
     * Get an instance of this translation bundle
     *
     * @return an instance of this translation bundle
     */
    public static LfsFileLockingText get() {
        return NLS.getBundleFor(LfsFileLockingText.class);
    }

    // @formatter:off
    /***/ public String fileLockingServiceUnavailable;
    /***/ public String invalidDeleteLockEndpoint;
    /***/ public String lfsUnathorized;
    /***/ public String lockExistsForPath;
}
