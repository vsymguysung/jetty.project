//
//  ========================================================================
//  Copyright (c) 1995-2014 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.server;

import java.io.IOException;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class HttpInputOverHTTP extends HttpInput
{
    private static final Logger LOG = Log.getLogger(HttpInputOverHTTP.class);

    private final HttpConnection _httpConnection;

    public HttpInputOverHTTP(HttpConnection httpConnection)
    {
        _httpConnection = httpConnection;
    }

    @Override
    protected void produceContent() throws IOException
    {
        _httpConnection.parseContent();
    }

    @Override
    protected void blockForContent() throws IOException
    {
        _httpConnection.blockingReadFillInterested();
        super.blockForContent();
    }

    @Override
    protected void unready()
    {
        _httpConnection.asyncReadFillInterested();
    }

    @Override 
    protected void onReadPossible()
    {
        _httpConnection.getHttpChannel().getState().onReadPossible();
    }
}
