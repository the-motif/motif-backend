package com.motif.support

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.GenericContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName

// 테스트 전체가 같이 쓰는 컨테이너 / static 필드라 JVM당 한 번만 기동, 종료는 Testcontainers(Ryuk)가 정리
// 버전은 docker-compose.yml과 맞출 것
object SharedContainers {

    @JvmField
    @ServiceConnection
    val mysql: MySQLContainer = MySQLContainer(DockerImageName.parse("mysql:8.4"))
        .withDatabaseName("motif")

    @JvmField
    @ServiceConnection(name = "redis")
    val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:8"))
        .withExposedPorts(6379)
}
