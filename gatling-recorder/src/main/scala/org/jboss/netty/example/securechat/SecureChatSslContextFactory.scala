/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.example.securechat

import java.io.{ InputStream, FileInputStream }
import java.security.{ KeyStore, Security }

import com.typesafe.scalalogging.slf4j.StrictLogging

import io.gatling.core.util.IO._
import javax.net.ssl.{ KeyManagerFactory, SSLContext }

/**
 * Creates a bogus {@link SSLContext}. A client-side context created by this
 * factory accepts any certificate even if it is invalid. A server-side context
 * created by this factory sends a bogus certificate defined in
 * {@link SecureChatKeyStore}.
 * <p>
 * You will have to create your context differently in a real world application.
 *
 * <h3>Client Certificate Authentication</h3>
 *
 * To enable client certificate authentication:
 * <ul>
 * <li>Enable client authentication on the server side by calling
 * {@link SSLEngine#setNeedClientAuth(boolean)} before creating
 * {@link SslHandler}.</li>
 * <li>When initializing an {@link SSLContext} on the client side, specify the
 * {@link KeyManager} that contains the client certificate as the first argument
 * of
 * {@link SSLContext#init(KeyManager[], javax.net.ssl.TrustManager[], java.security.SecureRandom)}
 * .</li>
 * <li>When initializing an {@link SSLContext} on the server side, specify the
 * proper {@link TrustManager} as the second argument of
 * {@link SSLContext#init(KeyManager[], javax.net.ssl.TrustManager[], java.security.SecureRandom)}
 * to validate the client certificate.</li>
 * </ul>
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version Rev: 2080 , Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010)
 */
object SecureChatSslContextFactory extends StrictLogging {

  val Protocol = "TLS"
  val PropertyKeystorePath = "gatling.recorder.keystore.path"
  val PropertyKeystorePassphrase = "gatling.recorder.keystore.passphrase"
  val DefaultKeyStore = "gatling.jks"
  val DefaultKeyStorePassphrase = "gatling"

  val ServerContext: SSLContext = {

    val algorithm = Option(Security.getProperty("ssl.KeyManagerFactory.algorithm")).getOrElse("SunX509")
    val ks = KeyStore.getInstance("JKS")

      def userSpecificKeyStore: Option[InputStream] = sys.props.get(PropertyKeystorePath)
        .map { keystorePath =>
          logger.info(s"Loading user-specified keystore: '$keystorePath'")
          new FileInputStream(keystorePath)
        }

      def defaultKeyStore: InputStream = {
        logger.info(s"Loading default keystore: '$DefaultKeyStore'")
        Option(ClassLoader.getSystemResourceAsStream(DefaultKeyStore))
          .orElse(Option(getClass.getResourceAsStream(DefaultKeyStore)))
          .getOrElse(throw new IllegalStateException(s"Couldn't load $DefaultKeyStore neither from System ClassLoader nor from current one"))
      }

    val keystoreStream = userSpecificKeyStore.getOrElse(defaultKeyStore)

    val keystorePassphrase = System.getProperty(PropertyKeystorePassphrase, DefaultKeyStorePassphrase)

    withCloseable(keystoreStream) { in =>
      val passphraseChars = keystorePassphrase.toCharArray
      ks.load(in, passphraseChars)

      // Set up key manager factory to use our key store
      val kmf = KeyManagerFactory.getInstance(algorithm)
      kmf.init(ks, passphraseChars)

      // Initialize the SSLContext to work with our key managers.
      val serverContext = SSLContext.getInstance(Protocol)
      serverContext.init(kmf.getKeyManagers, null, null)

      serverContext
    }
  }

  val ClientContext: SSLContext = {
    val clientContext = SSLContext.getInstance(Protocol)
    clientContext.init(null, SecureChatTrustManagerFactory.LooseTrustManagers, null)
    clientContext
  }
}
