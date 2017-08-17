FROM anapsix/alpine-java:8_server-jre_unlimited

ADD target/tourniquet-proxy-standalone.jar /opt/proxy/tourniquet-proxy-standalone.jar

EXPOSE 7099 28080

CMD /usr/bin/java -jar /opt/proxy/tourniquet-proxy-standalone.jar
