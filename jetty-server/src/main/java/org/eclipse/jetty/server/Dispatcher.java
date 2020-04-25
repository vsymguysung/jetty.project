//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.server;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.BadMessageException;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.MultiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Dispatcher implements RequestDispatcher
{
    private static final Logger LOG = LoggerFactory.getLogger(Dispatcher.class);

    /**
     * Dispatch include attribute names
     */
    public static final String __INCLUDE_PREFIX = "javax.servlet.include.";

    /**
     * Dispatch include attribute names
     */
    public static final String __FORWARD_PREFIX = "javax.servlet.forward.";

    private final ContextHandler _contextHandler;
    private final HttpURI _uri;
    private final String _pathInContext;
    private final String _named;

    public Dispatcher(ContextHandler contextHandler, HttpURI uri, String pathInContext)
    {
        _contextHandler = contextHandler;
        _uri = uri;
        _pathInContext = pathInContext;
        _named = null;
    }

    public Dispatcher(ContextHandler contextHandler, String name) throws IllegalStateException
    {
        _contextHandler = contextHandler;
        _uri = null;
        _pathInContext = null;
        _named = name;
    }

    public void error(ServletRequest request, ServletResponse response) throws ServletException, IOException
    {
        forward(request, response, DispatcherType.ERROR);
    }

    @Override
    public void include(ServletRequest request, ServletResponse response) throws ServletException, IOException
    {
        Request baseRequest = Request.getBaseRequest(request);

        if (!(request instanceof HttpServletRequest))
            request = new ServletRequestHttpWrapper(request);
        if (!(response instanceof HttpServletResponse))
            response = new ServletResponseHttpWrapper(response);

        final DispatcherType old_type = baseRequest.getDispatcherType();
        final Attributes old_attr = baseRequest.getAttributes();
        final MultiMap<String> old_query_params = baseRequest.getQueryParameters();
        try
        {
            baseRequest.setDispatcherType(DispatcherType.INCLUDE);
            baseRequest.getResponse().include();
            if (_named != null)
            {
                _contextHandler.handle(_named, baseRequest, (HttpServletRequest)request, (HttpServletResponse)response);
            }
            else
            {
                IncludeAttributes attr = new IncludeAttributes(old_attr);

                attr._requestURI = _uri.getPath();
                attr._contextPath = _contextHandler.getContextPath();
                attr._servletPath = null; // set by ServletHandler
                attr._pathInfo = _pathInContext;
                attr._query = _uri.getQuery();
                attr._mapping = null; //set by ServletHandler
                if (attr._query != null)
                    baseRequest.mergeQueryParameters(baseRequest.getQueryString(), attr._query, false);
                baseRequest.setAttributes(attr);

                _contextHandler.handle(_pathInContext, baseRequest, (HttpServletRequest)request, (HttpServletResponse)response);
            }
        }
        finally
        {
            baseRequest.setAttributes(old_attr);
            baseRequest.getResponse().included();
            baseRequest.setQueryParameters(old_query_params);
            baseRequest.resetParameters();
            baseRequest.setDispatcherType(old_type);
        }
    }

    @Override
    public void forward(ServletRequest request, ServletResponse response) throws ServletException, IOException
    {
        forward(request, response, DispatcherType.FORWARD);
    }

    protected void forward(ServletRequest request, ServletResponse response, DispatcherType dispatch) throws ServletException, IOException
    {
        Request baseRequest = Request.getBaseRequest(request);
        Response baseResponse = baseRequest.getResponse();
        baseResponse.resetForForward();

        if (!(request instanceof HttpServletRequest))
            request = new ServletRequestHttpWrapper(request);
        if (!(response instanceof HttpServletResponse))
            response = new ServletResponseHttpWrapper(response);

        final HttpURI old_uri = baseRequest.getHttpURI();
        final String old_context_path = baseRequest.getContextPath();
        final String old_servlet_path = baseRequest.getServletPath();
        final String old_path_info = baseRequest.getPathInfo();
        final HttpServletMapping old_mapping = baseRequest.getHttpServletMapping();

        final MultiMap<String> old_query_params = baseRequest.getQueryParameters();
        final Attributes old_attr = baseRequest.getAttributes();
        final DispatcherType old_type = baseRequest.getDispatcherType();

        try
        {
            baseRequest.setDispatcherType(dispatch);

            if (_named != null)
            {
                _contextHandler.handle(_named, baseRequest, (HttpServletRequest)request, (HttpServletResponse)response);
            }
            else
            {
                ForwardAttributes attr = new ForwardAttributes(old_attr);

                //If we have already been forwarded previously, then keep using the established
                //original value. Otherwise, this is the first forward and we need to establish the values.
                //Note: the established value on the original request for pathInfo and
                //for queryString is allowed to be null, but cannot be null for the other values.
                if (old_attr.getAttribute(FORWARD_REQUEST_URI) != null)
                {
                    attr._pathInfo = (String)old_attr.getAttribute(FORWARD_PATH_INFO);
                    attr._query = (String)old_attr.getAttribute(FORWARD_QUERY_STRING);
                    attr._requestURI = (String)old_attr.getAttribute(FORWARD_REQUEST_URI);
                    attr._contextPath = (String)old_attr.getAttribute(FORWARD_CONTEXT_PATH);
                    attr._servletPath = (String)old_attr.getAttribute(FORWARD_SERVLET_PATH);
                    attr._mapping = (HttpServletMapping)old_attr.getAttribute(FORWARD_MAPPING);
                }
                else
                {
                    attr._pathInfo = old_path_info;
                    attr._query = old_uri.getQuery();
                    attr._requestURI = old_uri.getPath();
                    attr._contextPath = old_context_path;
                    attr._servletPath = old_servlet_path;
                    attr._mapping = old_mapping;
                }

                HttpURI uri = new HttpURI(old_uri.getScheme(), old_uri.getHost(), old_uri.getPort(),
                    _uri.getPath(), _uri.getParam(), _uri.getQuery(), _uri.getFragment());

                baseRequest.setHttpURI(uri);

                baseRequest.setContextPath(_contextHandler.getContextPath());
                baseRequest.setServletPath(null);
                baseRequest.setPathInfo(_pathInContext);

                if (_uri.getQuery() != null || old_uri.getQuery() != null)
                {
                    try
                    {
                        baseRequest.mergeQueryParameters(old_uri.getQuery(), _uri.getQuery(), true);
                    }
                    catch (BadMessageException e)
                    {
                        // Only throw BME if not in Error Dispatch Mode
                        // This allows application ErrorPageErrorHandler to handle BME messages
                        if (dispatch != DispatcherType.ERROR)
                        {
                            throw e;
                        }
                        else
                        {
                            LOG.warn("Ignoring Original Bad Request Query String: " + old_uri, e);
                        }
                    }
                }

                baseRequest.setAttributes(attr);

                _contextHandler.handle(_pathInContext, baseRequest, (HttpServletRequest)request, (HttpServletResponse)response);

                // If we are not async and not closed already, then close via the possibly wrapped response.
                if (!baseRequest.getHttpChannelState().isAsync() && !baseResponse.getHttpOutput().isClosed())
                {
                    try
                    {
                        response.getOutputStream().close();
                    }
                    catch (IllegalStateException e)
                    {
                        response.getWriter().close();
                    }
                }
            }
        }
        finally
        {
            baseRequest.setHttpURI(old_uri);
            baseRequest.setContextPath(old_context_path);
            baseRequest.setServletPath(old_servlet_path);
            baseRequest.setPathInfo(old_path_info);
            baseRequest.setQueryParameters(old_query_params);
            baseRequest.resetParameters();
            baseRequest.setAttributes(old_attr);
            baseRequest.setDispatcherType(old_type);
        }
    }

    @Override
    public String toString()
    {
        return String.format("Dispatcher@0x%x{%s,%s}", hashCode(), _named, _uri);
    }

    private class ForwardAttributes extends Attributes.Wrapper
    {
        String _requestURI;
        String _contextPath;
        String _servletPath;
        String _pathInfo;
        String _query;
        HttpServletMapping _mapping;

        ForwardAttributes(Attributes attributes)
        {
            super(attributes);
        }

        @Override
        public Object getAttribute(String key)
        {
            if (Dispatcher.this._named == null)
            {
                if (key.equals(FORWARD_PATH_INFO))
                    return _pathInfo;
                if (key.equals(FORWARD_REQUEST_URI))
                    return _requestURI;
                if (key.equals(FORWARD_SERVLET_PATH))
                    return _servletPath;
                if (key.equals(FORWARD_CONTEXT_PATH))
                    return _contextPath;
                if (key.equals(FORWARD_QUERY_STRING))
                    return _query;
                if (key.equals(FORWARD_MAPPING))
                    return _mapping;
            }

            if (key.startsWith(__INCLUDE_PREFIX))
                return null;

            return _attributes.getAttribute(key);
        }

        @Override
        public Enumeration<String> getAttributeNames()
        {
            HashSet<String> set = new HashSet<>();
            Enumeration<String> e = _attributes.getAttributeNames();
            while (e.hasMoreElements())
            {
                String name = e.nextElement();
                if (!name.startsWith(__INCLUDE_PREFIX) &&
                    !name.startsWith(__FORWARD_PREFIX))
                    set.add(name);
            }

            if (_named == null)
            {
                if (_pathInfo != null)
                    set.add(FORWARD_PATH_INFO);
                else
                    set.remove(FORWARD_PATH_INFO);
                set.add(FORWARD_REQUEST_URI);
                set.add(FORWARD_SERVLET_PATH);
                set.add(FORWARD_CONTEXT_PATH);
                set.add(FORWARD_MAPPING);
                if (_query != null)
                    set.add(FORWARD_QUERY_STRING);
                else
                    set.remove(FORWARD_QUERY_STRING);
            }

            return Collections.enumeration(set);
        }

        @Override
        public void setAttribute(String key, Object value)
        {
            if (_named == null && key.startsWith("javax.servlet."))
            {
                if (key.equals(FORWARD_PATH_INFO))
                    _pathInfo = (String)value;
                else if (key.equals(FORWARD_REQUEST_URI))
                    _requestURI = (String)value;
                else if (key.equals(FORWARD_SERVLET_PATH))
                    _servletPath = (String)value;
                else if (key.equals(FORWARD_CONTEXT_PATH))
                    _contextPath = (String)value;
                else if (key.equals(FORWARD_QUERY_STRING))
                    _query = (String)value;
                else if (key.equals(FORWARD_MAPPING))
                    _mapping = (HttpServletMapping)value;
                else if (value == null)
                    _attributes.removeAttribute(key);
                else
                    _attributes.setAttribute(key, value);
            }
            else if (value == null)
                _attributes.removeAttribute(key);
            else
                _attributes.setAttribute(key, value);
        }

        @Override
        public String toString()
        {
            return "FORWARD+" + _attributes.toString();
        }

        @Override
        public void clearAttributes()
        {
            throw new IllegalStateException();
        }

        @Override
        public void removeAttribute(String name)
        {
            setAttribute(name, null);
        }
    }

    private class IncludeAttributes extends Attributes.Wrapper
    {
        String _requestURI;
        String _contextPath;
        String _servletPath;
        String _pathInfo;
        String _query;
        HttpServletMapping _mapping;

        IncludeAttributes(Attributes attributes)
        {
            super(attributes);
        }

        @Override
        public Object getAttribute(String key)
        {
            if (Dispatcher.this._named == null)
            {
                if (key.equals(INCLUDE_PATH_INFO))
                    return _pathInfo;
                if (key.equals(INCLUDE_SERVLET_PATH))
                    return _servletPath;
                if (key.equals(INCLUDE_CONTEXT_PATH))
                    return _contextPath;
                if (key.equals(INCLUDE_QUERY_STRING))
                    return _query;
                if (key.equals(INCLUDE_REQUEST_URI))
                    return _requestURI;
                if (key.equals(INCLUDE_MAPPING))
                    return _mapping;
            }
            else if (key.startsWith(__INCLUDE_PREFIX))
                return null;

            return _attributes.getAttribute(key);
        }

        @Override
        public Enumeration<String> getAttributeNames()
        {
            HashSet<String> set = new HashSet<>();
            Enumeration<String> e = _attributes.getAttributeNames();
            while (e.hasMoreElements())
            {
                String name = e.nextElement();
                if (!name.startsWith(__INCLUDE_PREFIX))
                    set.add(name);
            }

            if (_named == null)
            {
                if (_pathInfo != null)
                    set.add(INCLUDE_PATH_INFO);
                else
                    set.remove(INCLUDE_PATH_INFO);
                set.add(INCLUDE_REQUEST_URI);
                set.add(INCLUDE_SERVLET_PATH);
                set.add(INCLUDE_CONTEXT_PATH);
                set.add(INCLUDE_MAPPING);
                if (_query != null)
                    set.add(INCLUDE_QUERY_STRING);
                else
                    set.remove(INCLUDE_QUERY_STRING);
            }

            return Collections.enumeration(set);
        }

        @Override
        public void setAttribute(String key, Object value)
        {
            if (_named == null && key.startsWith("javax.servlet."))
            {
                if (key.equals(INCLUDE_PATH_INFO))
                    _pathInfo = (String)value;
                else if (key.equals(INCLUDE_REQUEST_URI))
                    _requestURI = (String)value;
                else if (key.equals(INCLUDE_SERVLET_PATH))
                    _servletPath = (String)value;
                else if (key.equals(INCLUDE_CONTEXT_PATH))
                    _contextPath = (String)value;
                else if (key.equals(INCLUDE_QUERY_STRING))
                    _query = (String)value;
                else if (key.equals(INCLUDE_MAPPING))
                    _mapping = (HttpServletMapping)value;
                else if (value == null)
                    _attributes.removeAttribute(key);
                else
                    _attributes.setAttribute(key, value);
            }
            else if (value == null)
                _attributes.removeAttribute(key);
            else
                _attributes.setAttribute(key, value);
        }

        @Override
        public String toString()
        {
            return "INCLUDE+" + _attributes.toString();
        }

        @Override
        public void clearAttributes()
        {
            throw new IllegalStateException();
        }

        @Override
        public void removeAttribute(String name)
        {
            setAttribute(name, null);
        }
    }
}
