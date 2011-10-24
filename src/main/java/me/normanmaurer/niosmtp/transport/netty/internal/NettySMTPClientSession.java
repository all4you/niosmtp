/**
* Licensed to niosmtp developers ('niosmtp') under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* niosmtp licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package me.normanmaurer.niosmtp.transport.netty.internal;


import javax.net.ssl.SSLEngine;

import me.normanmaurer.niosmtp.MessageInput;
import me.normanmaurer.niosmtp.SMTPClientConfig;
import me.normanmaurer.niosmtp.SMTPClientConstants;
import me.normanmaurer.niosmtp.SMTPRequest;
import me.normanmaurer.niosmtp.SMTPResponseCallback;
import me.normanmaurer.niosmtp.transport.AbstractSMTPClientSession;
import me.normanmaurer.niosmtp.transport.SMTPClientSession;
import me.normanmaurer.niosmtp.transport.SMTPDeliveryMode;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;


/**
 * {@link SMTPClientSession} implementation which uses <code>NETTY</code> under the hood.
 * 
 * @author Norman Maurer
 *
 */
class NettySMTPClientSession extends AbstractSMTPClientSession implements SMTPClientSession, SMTPClientConstants, NettyConstants{

    private int closeHandlerCount = 0;
    private int callbackCount = 0;
    private final Channel channel;
    private final SSLEngine engine;

    public NettySMTPClientSession(Channel channel, Logger logger, SMTPClientConfig config, SMTPDeliveryMode mode,  SSLEngine engine) {
        super(logger, config, mode);      
        this.channel = channel;
        this.engine = engine;
    }
    
    @Override
    public String getId() {
        return channel.getId() + "";
    }

    @Override
    public void startTLS() {
        SslHandler sslHandler =  new SslHandler(engine, false);
        channel.getPipeline().addFirst(SSL_HANDLER_KEY, sslHandler);
        sslHandler.handshake();        
    }
    
    @Override
    public void send(SMTPRequest request, SMTPResponseCallback callback) {
        channel.getPipeline().addBefore(IDLE_HANDLER_KEY, "callback" + callbackCount++, new SMTPCallbackHandlerAdapter(this, callback));
        channel.write(request);
    }
    
 

    
    @Override
    public void send(MessageInput msg, SMTPResponseCallback callback) {
        ChannelPipeline cp = channel.getPipeline();
        
        cp.addBefore(IDLE_HANDLER_KEY, "callback" + callbackCount++, new SMTPCallbackHandlerAdapter(this,callback));
        
        if (cp.get(MessageInputEncoder.class) == null) {
            channel.getPipeline().addAfter(CHUNK_WRITE_HANDLER_KEY, "messageDataEncoder", new MessageInputEncoder(this));
        }
        channel.write(msg);
            
    }
    

    @Override
    public void close() {
        channel.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }


    @Override
    public boolean isEncrypted() {
        return channel.getPipeline().get(SslHandler.class) != null;
    }


    @Override
    public void addCloseListener(CloseListener listener) {
        channel.getPipeline().addLast("closeListener" + closeHandlerCount++, new CloseListenerAdapter(this, listener));
    }


    @Override
    public boolean isClosed() {
        return !channel.isConnected();
    }
    
}