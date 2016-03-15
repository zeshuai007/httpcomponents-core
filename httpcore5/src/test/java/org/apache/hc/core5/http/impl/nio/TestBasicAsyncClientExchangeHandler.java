/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.hc.core5.http.impl.nio;

import java.util.concurrent.ExecutionException;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.ContentDecoder;
import org.apache.hc.core5.http.nio.ContentEncoder;
import org.apache.hc.core5.http.nio.HttpAsyncRequestProducer;
import org.apache.hc.core5.http.nio.HttpAsyncResponseConsumer;
import org.apache.hc.core5.http.nio.NHttpClientConnection;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

public class TestBasicAsyncClientExchangeHandler {

    private HttpAsyncRequestProducer requestProducer;
    private HttpAsyncResponseConsumer<Object> responseConsumer;
    private HttpContext context;
    private HttpProcessor httpProcessor;
    private NHttpClientConnection conn;
    private BasicAsyncClientExchangeHandler<Object> exchangeHandler;
    private ContentEncoder encoder;
    private ContentDecoder decoder;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        this.requestProducer = Mockito.mock(HttpAsyncRequestProducer.class);
        this.responseConsumer = Mockito.mock(HttpAsyncResponseConsumer.class);
        this.context = new BasicHttpContext();
        this.conn = Mockito.mock(NHttpClientConnection.class);
        this.httpProcessor = Mockito.mock(HttpProcessor.class);
        this.exchangeHandler = new BasicAsyncClientExchangeHandler<>(
                this.requestProducer,
                this.responseConsumer,
                null,
                this.context,
                this.conn,
                this.httpProcessor);
        this.encoder = Mockito.mock(ContentEncoder.class);
        this.decoder = Mockito.mock(ContentDecoder.class);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testInvalidExecution() throws Exception {
        try {
            new BasicAsyncClientExchangeHandler<>(
                    null,
                    this.responseConsumer,
                    null,
                    this.context,
                    this.conn,
                    this.httpProcessor);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException ex) {
        }
        try {
            new BasicAsyncClientExchangeHandler<>(
                    this.requestProducer,
                    null,
                    null,
                    this.context,
                    this.conn,
                    this.httpProcessor);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException ex) {
        }
        try {
            new BasicAsyncClientExchangeHandler<>(
                    this.requestProducer,
                    this.responseConsumer,
                    null,
                    null,
                    this.conn,
                    this.httpProcessor);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException ex) {
        }
        try {
            new BasicAsyncClientExchangeHandler<>(
                    this.requestProducer,
                    this.responseConsumer,
                    null,
                    this.context,
                    null,
                    this.httpProcessor);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException ex) {
        }
        try {
            new BasicAsyncClientExchangeHandler<>(
                    this.requestProducer,
                    this.responseConsumer,
                    null,
                    this.context,
                    this.conn,
                    null);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException ex) {
        }
    }

    @Test
    public void testClose() throws Exception {
        Assert.assertFalse(this.exchangeHandler.getFuture().isCancelled());
        this.exchangeHandler.close();
        Mockito.verify(this.requestProducer).close();
        Mockito.verify(this.responseConsumer).close();
        Assert.assertTrue(this.exchangeHandler.getFuture().isCancelled());
    }

    @Test
    public void testGenerateRequest() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        Mockito.when(this.requestProducer.generateRequest()).thenReturn(request);

        final HttpRequest result = this.exchangeHandler.generateRequest();

        Assert.assertSame(request, result);

        Mockito.verify(this.requestProducer).generateRequest();
        Assert.assertSame(request, this.context.getAttribute(HttpCoreContext.HTTP_REQUEST));
        Assert.assertSame(this.conn, this.context.getAttribute(HttpCoreContext.HTTP_CONNECTION));
        Mockito.verify(this.httpProcessor).process(request, this.context);
    }

    @Test
    public void testProduceContent() throws Exception {
        Mockito.when(this.encoder.isCompleted()).thenReturn(false);

        this.exchangeHandler.produceContent(this.encoder, this.conn);

        Mockito.verify(this.requestProducer).produceContent(this.encoder, this.conn);
    }

    @Test
    public void testProduceContentCompleted() throws Exception {
        Mockito.when(this.encoder.isCompleted()).thenReturn(true);

        this.exchangeHandler.produceContent(this.encoder, this.conn);

        Mockito.verify(this.requestProducer).produceContent(this.encoder, this.conn);
    }

    @Test
    public void testRequestCompleted() throws Exception {
        this.exchangeHandler.requestCompleted();

        Mockito.verify(this.requestProducer).requestCompleted(this.context);
    }

    @Test
    public void testResponseReceived() throws Exception {
        final BasicHttpResponse response = new BasicHttpResponse(200, "OK");

        this.exchangeHandler.responseReceived(response);

        Mockito.verify(this.responseConsumer).responseReceived(response);
        Assert.assertSame(response, this.context.getAttribute(HttpCoreContext.HTTP_RESPONSE));
        Mockito.verify(this.httpProcessor).process(response, this.context);
    }

    @Test
    public void testConsumeContent() throws Exception {
        this.exchangeHandler.consumeContent(this.decoder, this.conn);

        Mockito.verify(this.responseConsumer).consumeContent(this.decoder, this.conn);
    }

    @Test
    public void testFailed() throws Exception {
        final Exception ooopsie = new Exception();
        this.exchangeHandler.failed(ooopsie);

        Mockito.verify(this.requestProducer).failed(ooopsie);
        Mockito.verify(this.responseConsumer).failed(ooopsie);
        Mockito.verify(this.requestProducer).close();
        Mockito.verify(this.responseConsumer).close();
        try {
            this.exchangeHandler.getFuture().get();
        } catch (final ExecutionException ex) {
            Assert.assertSame(ooopsie, ex.getCause());
        }
    }

    @Test
    public void testFailedAfterRequest() throws Exception {
        final Exception ooopsie = new Exception();
        this.exchangeHandler.requestCompleted();
        this.exchangeHandler.failed(ooopsie);

        Mockito.verify(this.requestProducer, Mockito.never()).failed(ooopsie);
        Mockito.verify(this.responseConsumer).failed(ooopsie);
        Mockito.verify(this.requestProducer).close();
        Mockito.verify(this.responseConsumer).close();
        try {
            this.exchangeHandler.getFuture().get();
        } catch (final ExecutionException ex) {
            Assert.assertSame(ooopsie, ex.getCause());
        }
    }

    @Test
    public void testFailedwithException() throws Exception {
        final Exception ooopsie = new Exception();
        Mockito.doThrow(new RuntimeException()).when(this.responseConsumer).failed(ooopsie);
        try {
            this.exchangeHandler.failed(ooopsie);
            Assert.fail("RuntimeException expected");
        } catch (final RuntimeException ex) {
            Mockito.verify(this.requestProducer).close();
            Mockito.verify(this.responseConsumer).close();
            try {
                this.exchangeHandler.getFuture().get();
            } catch (final ExecutionException exex) {
                Assert.assertSame(ooopsie, exex.getCause());
            }
        }
    }

    @Test
    public void testCancel() throws Exception {
        this.exchangeHandler.cancel();

        Mockito.verify(this.responseConsumer).cancel();
        Mockito.verify(this.requestProducer).close();
        Mockito.verify(this.responseConsumer).close();
        Assert.assertTrue(this.exchangeHandler.getFuture().isCancelled());
    }

    @Test
    public void testResponseCompleted() throws Exception {
        final Object obj = new Object();
        Mockito.when(this.responseConsumer.getResult()).thenReturn(obj);

        this.exchangeHandler.responseCompleted();

        Mockito.verify(this.responseConsumer).responseCompleted(this.context);
        Mockito.verify(this.requestProducer).close();
        Mockito.verify(this.responseConsumer).close();
        final Object result = this.exchangeHandler.getFuture().get();
        Assert.assertSame(obj, result);
    }

    @Test
    public void testResponseFailure() throws Exception {
        final Exception ooopsie = new Exception();
        Mockito.when(this.responseConsumer.getException()).thenReturn(ooopsie);

        this.exchangeHandler.responseCompleted();

        Mockito.verify(this.responseConsumer).responseCompleted(this.context);
        Mockito.verify(this.requestProducer).close();
        Mockito.verify(this.responseConsumer).close();
        try {
            this.exchangeHandler.getFuture().get();
        } catch (final ExecutionException exex) {
            Assert.assertSame(ooopsie, exex.getCause());
        }
    }

    @Test
    public void testResponseCompletedWithException() throws Exception {
        Mockito.doThrow(new RuntimeException()).when(this.responseConsumer).responseCompleted(this.context);
        try {
            this.exchangeHandler.responseCompleted();
            Assert.fail("RuntimeException expected");
        } catch (final RuntimeException ex) {
            Mockito.verify(this.requestProducer).close();
            Mockito.verify(this.responseConsumer).close();
            try {
                this.exchangeHandler.getFuture().get();
                Assert.fail("ExecutionException expected");
            } catch (final ExecutionException exex) {
            }
        }
    }

    @Test
    public void testInputTerminated() throws Exception {
        this.exchangeHandler.inputTerminated();
        Mockito.verify(this.responseConsumer).failed(Matchers.<ConnectionClosedException>any());
        try {
            this.exchangeHandler.getFuture().get();
            Assert.fail("ExecutionException expected");
        } catch (final ExecutionException exex) {
        }
    }

    @Test
    public void testIsDone() throws Exception {
        this.exchangeHandler.isDone();
        Mockito.verify(this.responseConsumer).isDone();
    }

}
