FROM registry.aliyuncs.com/flow-cache/bitnami-java:1.8.322-debian-10-r91

WORKDIR application
COPY ./dependencies ./
COPY ./spring-boot-loader ./
COPY ./snapshot-dependencies ./
COPY ./application ./

ENV JAVA_TOOL_OPTIONS "-Xmx512m -Xmx2048m -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]