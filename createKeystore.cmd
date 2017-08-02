keytool -genkey -alias proxy -keystore src/main/resources/proxy.jks -storepass changeit -keypass changeit -keyalg RSA -sigalg SHA1withRSA -validity 730 -dname CN=Test-Proxy,OU=Test,O=Tourniquet,C=CH
keytool -export -alias proxy -keystore src/main/resources/proxy.jks -storepass changeit -keypass changeit -file src/main/resources/proxyCA.cer
