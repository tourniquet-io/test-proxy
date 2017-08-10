FROM anapsix/alpine-java

ARG mavenUrl = http://repo1.maven.org/maven2/io/tourniquet/tourniquet-proxy-service/1.0/tourniquet-proxy-service-1.0-jar-with-dependencies.jar
RUN mkdir /opt/proxy && \
    apk add --update curl && \
    curl --output /opt/proxy/test-proxy.jar  && \
    apk del curl && \
    rm -rf /tmp/* /var/cache/apk/*

EXPOSE 7099 28080
CMD java -jar /opt/proxy/test-proxy.jar
