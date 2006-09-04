/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import org.apache.http.HttpHost;
import org.apache.http.impl.entity.DefaultEntityDeserializer;
import org.apache.http.impl.entity.DefaultEntitySerializer;
import org.apache.http.impl.io.SocketHttpDataReceiver;
import org.apache.http.impl.io.SocketHttpDataTransmitter;
import org.apache.http.io.HttpDataReceiver;
import org.apache.http.io.HttpDataTransmitter;
import org.apache.http.io.SocketFactory;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

/**
 * Default implementation of a client-side HTTP connection.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class DefaultHttpClientConnection extends AbstractHttpClientConnection {

    private HttpHost targethost = null;
    private InetAddress localAddress = null;

    protected volatile boolean open;
    protected Socket socket = null;
    
    public DefaultHttpClientConnection(final HttpHost targethost, final InetAddress localAddress) {
        super();
        this.targethost = targethost;
        this.localAddress = localAddress;
    }
    
    public DefaultHttpClientConnection(final HttpHost targethost) {
        this(targethost, null);
    }
    
    public DefaultHttpClientConnection() {
        this(null, null);
    }
    
    protected void assertNotOpen() {
        if (this.open) {
            throw new IllegalStateException("Connection is already open");
        }
    }
    
    protected void assertOpen() {
        if (!this.open) {
            throw new IllegalStateException("Connection is not open");
        }
    }

    public void open(final HttpParams params) throws IOException {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        assertNotOpen();
        if (this.targethost == null) {
        	throw new IllegalStateException("Target host not specified");
        }
        SocketFactory socketfactory = this.targethost.getScheme().getSocketFactory();
        Socket socket = socketfactory.createSocket(
                this.targethost.getHostName(), this.targethost.getPort(), 
                this.localAddress, 0, 
                params);
        bind(socket, params);
    }
    
    protected void bind(final Socket socket, final HttpParams params) throws IOException {
        if (socket == null) {
            throw new IllegalArgumentException("Socket may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        assertNotOpen();
        
        socket.setTcpNoDelay(HttpConnectionParams.getTcpNoDelay(params));
        socket.setSoTimeout(HttpConnectionParams.getSoTimeout(params));
        
        int linger = HttpConnectionParams.getLinger(params);
        if (linger >= 0) {
            socket.setSoLinger(linger > 0, linger);
        }

        this.socket = socket;
        
        int buffersize = HttpConnectionParams.getSocketBufferSize(params);
        HttpDataTransmitter transmitter = new SocketHttpDataTransmitter(socket, buffersize);
        HttpDataReceiver receiver = new SocketHttpDataReceiver(socket, buffersize);
        transmitter.reset(params);
        receiver.reset(params);
        
        setHttpDataReceiver(receiver);
        setHttpDataTransmitter(transmitter);
        setMaxHeaderCount(params.getIntParameter(HttpConnectionParams.MAX_HEADER_COUNT, -1));
        setResponseFactory(new DefaultHttpResponseFactory());
        setEntitySerializer(new DefaultEntitySerializer());
        setEntityDeserializer(new DefaultEntityDeserializer());
        this.open = true;
    }

    public HttpHost getTargetHost() {
        return this.targethost;
    }
    
    public InetAddress getLocalAddress() {
        return this.localAddress;
    }
    
    public void setTargetHost(final HttpHost targethost) {
        if (targethost == null) {
            throw new IllegalArgumentException("Target host may not be null");
        }
        assertNotOpen();
        this.targethost = targethost;
    }

    public void setLocalAddress(final InetAddress localAddress) {
        assertNotOpen();
        this.localAddress = localAddress;
    }
    
    public boolean isOpen() {
        return this.open;
    }

    public void shutdown() throws IOException {
        this.open = false;
        Socket tmpsocket = this.socket;
        if (tmpsocket != null) {
            tmpsocket.close();
        }
    }
    
    public void close() throws IOException {
        if (!this.open) {
            return;
        }
        this.open = false;
        doFlush();
        try {
            this.socket.shutdownOutput();
        } catch (IOException ignore) {
        }
        try {
            this.socket.shutdownInput();
        } catch (IOException ignore) {
        }
        this.socket.close();
    }
    
}
