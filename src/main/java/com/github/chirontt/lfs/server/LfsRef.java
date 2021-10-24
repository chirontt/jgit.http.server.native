/*
 * Copyright (C) 2021, Tue Ton <chirontt@gmail.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.github.chirontt.lfs.server;

/**
 * Class representing the server ref that the LFS objects/locks belong to.
 *
 */
public class LfsRef {
    //fully-qualified server refspec
    private String name;

    public LfsRef() {
        this(null);
    }

    public LfsRef(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
