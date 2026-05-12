package net.spartanb312.grunteon.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import java.nio.file.Path
import kotlin.io.path.Path

@SpringBootApplication
@EnableConfigurationProperties(BackendProperties::class)
class BackendApplication

fun main(args: Array<String>) {
    if (args.firstOrNull() == "worker") {
        require(args.size == 2) { "Usage: grunt-backend worker <job-dir>" }
        WorkerJobRunner.run(Path(args[1]))
        return
    }
    runApplication<BackendApplication>(*args)
}

@ConfigurationProperties("grunteon.backend")
class BackendProperties {
    var workDir: Path = Path("work/backend-jobs")
    var queueKey: String = "grunteon:jobs:queue"
    var jobKeyPrefix: String = "grunteon:jobs:"
    var workerConcurrency: Int = 2
    var javaExecutable: String = ""
    var workerJavaOptions: List<String> = emptyList()
    var workerTimeoutSeconds: Long = 0
}
