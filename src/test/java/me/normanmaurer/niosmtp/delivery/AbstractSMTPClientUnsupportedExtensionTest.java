/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package me.normanmaurer.niosmtp.delivery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;


import org.apache.james.protocols.impl.NettyServer;
import org.apache.james.protocols.smtp.SMTPConfigurationImpl;
import org.apache.james.protocols.smtp.SMTPProtocol;
import org.apache.james.protocols.smtp.SMTPProtocolHandlerChain;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.esmtp.EhloCmdHandler;
import org.apache.james.protocols.smtp.core.esmtp.StartTlsCmdHandler;
import org.junit.Test;

import me.normanmaurer.niosmtp.SMTPUnsupportedExtensionException;
import me.normanmaurer.niosmtp.core.SimpleMessageInput;
import me.normanmaurer.niosmtp.delivery.DeliveryResult;
import me.normanmaurer.niosmtp.delivery.SMTPDeliveryAgent;
import me.normanmaurer.niosmtp.delivery.SMTPDeliveryAgentConfig.PipeliningMode;
import me.normanmaurer.niosmtp.delivery.SMTPDeliveryFuture;
import me.normanmaurer.niosmtp.delivery.impl.SMTPDeliveryAgentConfigImpl;
import me.normanmaurer.niosmtp.delivery.impl.SMTPDeliveryTransactionImpl;
import me.normanmaurer.niosmtp.transport.SMTPClientTransport;
import me.normanmaurer.niosmtp.transport.SMTPClientTransportFactory;
import me.normanmaurer.niosmtp.util.TestUtils;


public abstract class AbstractSMTPClientUnsupportedExtensionTest {

    protected SMTPDeliveryAgentConfigImpl createConfig() {
        SMTPDeliveryAgentConfigImpl conf = new SMTPDeliveryAgentConfigImpl();
        conf.setConnectionTimeout(4);
        conf.setResponseTimeout(5);
        return conf;
    }


    protected abstract SMTPClientTransportFactory createFactory();
    
    
    @Test
    public void testDependOnPipelining() throws Exception {
        checkDependOnPipelining(new DependOnPipeliningAssertCheck());
    }
    
    @Test
    public void testDependOnPipeliningNonBlocking() throws Exception {
        checkDependOnPipelining(new AsyncAssertCheck(new DependOnPipeliningAssertCheck()));
    }
    
    private final class DependOnPipeliningAssertCheck extends AssertCheck {

        @Override
        protected void onDeliveryResult(Iterator<DeliveryResult> result) {
            assertTrue(result.hasNext());
            
            // We expect to receive an exception as PIPELINING was not supported
            DeliveryResult dr = result.next();
            assertFalse(dr.isSuccess());
            assertNull(dr.getRecipientStatus());
            assertEquals(SMTPUnsupportedExtensionException.class, dr.getException().getClass());
            
            assertFalse(result.hasNext());

        }
        
    }
    
    
    private void checkDependOnPipelining(AssertCheck check) throws Exception {
        int port = TestUtils.getFreePort();

        SMTPConfigurationImpl config = new SMTPConfigurationImpl();
        SMTPProtocolHandlerChain chain = new SMTPProtocolHandlerChain() {

            @Override
            protected List<Object> initDefaultHandlers() {
                List<Object> defaultHandlers =  super.initDefaultHandlers();
                for (int i = 0 ; i < defaultHandlers.size(); i++) {
                    Object h = defaultHandlers.get(i);
                    if (h instanceof EhloCmdHandler) {
                        defaultHandlers.remove(h);
                        defaultHandlers.add(new EhloCmdHandler() {

                            @SuppressWarnings("unchecked")
                            @Override
                            public List<String> getImplementedEsmtpFeatures(SMTPSession session) {
                                return Collections.EMPTY_LIST;
                            }
                            
                        });
                    }
                }
                return defaultHandlers;
            }


            
        };
        NettyServer smtpServer = new NettyServer(new SMTPProtocol(chain, config));
        smtpServer.setListenAddresses(Arrays.asList(new InetSocketAddress(port)));
        smtpServer.bind();

        
        SMTPClientTransport transport = createFactory().createPlain();
        SMTPDeliveryAgent c = new SMTPDeliveryAgent(transport);

        SMTPDeliveryAgentConfigImpl conf = createConfig();
        conf.setPipeliningMode(PipeliningMode.DEPEND);

        SMTPDeliveryFuture future = c.deliver(new InetSocketAddress(port), conf, new SMTPDeliveryTransactionImpl("from@example.com", Arrays.asList(new String[] { "to@example.com" }), new SimpleMessageInput(new ByteArrayInputStream("msg".getBytes()))));
        try {
            
            check.onSMTPClientFuture(future);
        } finally {
            transport.destroy();
            smtpServer.unbind();
        }
    }
 
    @Test
    public void testDependOnStartTLS() throws Exception {
        checkDependOnStartTLS(new DependOnStartTLSAssertCheck());
    }
    
    
    @Test
    public void testDependOnStartTLSNonBlocking() throws Exception {
        checkDependOnStartTLS(new AsyncAssertCheck(new DependOnStartTLSAssertCheck()));
    }
    
    
    
    
    private class DependOnStartTLSAssertCheck extends AssertCheck {

        
        protected void onDeliveryResult(Iterator<DeliveryResult> result) {
            assertTrue(result.hasNext());

            // We expect to receive an exception as STARTTLS was not supported
            DeliveryResult dr = result.next();
            assertFalse(dr.isSuccess());
            assertNull(dr.getRecipientStatus());
            assertEquals(SMTPUnsupportedExtensionException.class, dr.getException().getClass());
            
            assertFalse(result.hasNext());

        }
        
    }

    private void checkDependOnStartTLS(AssertCheck check) throws Exception {
        int port = TestUtils.getFreePort();

        SMTPConfigurationImpl config = new SMTPConfigurationImpl() {

            @Override
            public boolean isStartTLSSupported() {
                return true;
            }
            
        };
       
        SMTPProtocolHandlerChain chain = new SMTPProtocolHandlerChain() {

            @Override
            protected List<Object> initDefaultHandlers() {
                List<Object> defaultHandlers =  super.initDefaultHandlers();
                for (int i = 0 ; i < defaultHandlers.size(); i++) {
                    Object h = defaultHandlers.get(i);
                    if (h instanceof StartTlsCmdHandler) {
                        defaultHandlers.remove(h);
                        return defaultHandlers;
                    }
                }
                return defaultHandlers;
            }


            
        };
        NettyServer smtpServer = new NettyServer(new SMTPProtocol(chain, config), BogusSslContextFactory.getServerContext());
        smtpServer.setListenAddresses(Arrays.asList(new InetSocketAddress(port)));
        
        smtpServer.bind();

        
        SMTPClientTransport transport = createFactory().createStartTLS(BogusSslContextFactory.getClientContext(), true);
        SMTPDeliveryAgent c = new SMTPDeliveryAgent(transport);

        SMTPDeliveryAgentConfigImpl conf = createConfig();

        SMTPDeliveryFuture future = c.deliver(new InetSocketAddress(port), conf, new SMTPDeliveryTransactionImpl("from@example.com", Arrays.asList(new String[] { "to@example.com" }), new SimpleMessageInput(new ByteArrayInputStream("msg".getBytes()))));
        try {
            check.onSMTPClientFuture(future);
        } finally {
            transport.destroy();
            smtpServer.unbind();
        }
    }

}