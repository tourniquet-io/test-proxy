#!/bin/sh

#on windows use //C=CH\O=TourniquetCN=Test-Proxy
#on linux use /C=CH/O=Tourniquet/CN=Test-Proxy
#
# create self-signed certificate for the CA
openssl req -x509 -nodes -sha256 -days 365 -newkey rsa:2048 \
    -keyout src/main/resources/test-proxy.key \
    -out src/main/resources/test-proxy.crt \
    -subj "//C=CH\O=Tourniquet\CN=Test-Proxy"

# merge certificate and key to import into keystore
openssl pkcs12 -export \
    -in src/main/resources/test-proxy.crt \
    -inkey src/main/resources/test-proxy.key \
    -out keystore.p12 \
    -name test-proxy \
    -CAfile src/main/resources/test-proxy-ca.crt \
    -caname root \
    -passout pass:changeit
# import keystore
keytool -importkeystore \
    -deststorepass changeit \
    -destkeypass changeit \
    -destkeystore src/main/resources/proxy.jks \
    -srckeystore keystore.p12 \
    -srcstoretype PKCS12 \
    -srcstorepass changeit \
    -alias test-proxy
rm keystore.p12

#keytool -genkey -alias proxy -keystore src/main/resources/proxy.jks -storepass changeit -keypass changeit -keyalg RSA -sigalg SHA1withRSA -dname CN=Test-Proxy,O=Tourniquet,C=CH -validity 730
#keytool -export -alias proxy -keystore src/main/resources/proxy.jks -storepass changeit -keypass changeit -file src/main/resources/proxyCA.cer
