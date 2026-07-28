dependencies {
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    implementation("ch.qos.logback:logback-classic")
    implementation("com.github.loki4j:loki-logback-appender:1.6.0")
    // logback-spring.xml의 <if>(janino) 조건부 설정에 필요.
    // GRAFANA_LOKI_URL이 비어 있을 때 LOKI appender 정의·부착 자체를 건너뛰기 위함
    // (2026-07-27 EKS CrashLoopBackOff 인시던트 수정). Spring Boot의 의존성 BOM이 버전을 관리한다.
    implementation("org.codehaus.janino:janino")
}
