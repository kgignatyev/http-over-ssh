@file:OptIn(ExperimentalForeignApi::class)

import com.kgit2.kommand.process.Child
import com.kgit2.kommand.process.Command
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.*
import kotlinx.cinterop.staticCFunction
import platform.posix.*
import kotlinx.coroutines.*


data class PortForwardingSSH(val proc: Child, val localPort: Int, var lastUsedAt: Long)

var next_portforward_port = 10000

val sshProcesses = mutableMapOf<String, PortForwardingSSH>()

val sshHandler = SSHHandler()

@OptIn(ExperimentalForeignApi::class)
val cleanup = staticCFunction<Int, Unit>() {
    println("Got $it")
    sshProcesses.forEach { me ->
        try {
            println("Killing ${me.key}")
            me.value.proc.kill()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun removeUnused(millisecondsAgo: Long) {
    val cutoffTime = Clock.System.now().epochSeconds - (millisecondsAgo / 1000)
    sshProcesses.filter { p -> p.value.lastUsedAt < cutoffTime }.forEach { entry ->
        println("Deleting unused process:${entry.key}")
        entry.value.proc.kill()
        sshProcesses.remove(entry.key)
    }
}


@OptIn(ExperimentalForeignApi::class)
fun main() {
    signal(SIGINT, cleanup)
    signal(SIGKILL, cleanup)
    signal(SIGTRAP, cleanup)
    val scope = CoroutineScope(Dispatchers.Default)

    scope.launch {
        while (isActive) { // Keep the coroutine running
            val checkInterval = 5 * 60 * 1000L
            delay(checkInterval) // Wait for 5 minutes
            removeUnused(checkInterval)
        }
    }
    embeddedServer(CIO, port = 6060, host = "127.0.0.1", module = Application::module)
        .start(wait = true)

}

val client = HttpClient(io.ktor.client.engine.cio.CIO)


fun Application.module() {
    routing {
        get("/{ssh_host}/{target_host}/{...}") {
            val sshdUserHost = this.context.parameters["ssh_host"]!!
            val userHostParts = sshdUserHost.split("@")
            val sshdHost = userHostParts[1]
            val sshdUser = userHostParts[0]
            val targetHost = this.context.parameters["target_host"]!!
            println(sshdHost)
            println(targetHost)
            val uri = this.context.request.uri
            println(uri)
            val targetHostParts = targetHost.split(":")
            val tHost = targetHostParts[0]
            val tPort = if (targetHostParts.size == 1) {
                80
            } else {
                targetHostParts[1].toInt()
            }
            val coroutineScope = CoroutineScope(coroutineContext)
            sshHandler.createSSHConnection(sshdHost,sshdUser, tHost, tPort) { pfPort, response ->
                println("Port forwarding to $pfPort")
                if (pfPort == -1) {
                    coroutineScope.launch {
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                } else {
                    try {
                        val uriToCall = uri.substring(sshdUserHost.length + targetHost.length + 2)
                        val urlToCall = "http://localhost:$pfPort$uriToCall"
                        println("calling $urlToCall \n")
                        coroutineScope.launch {
                            call.respondText(response)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        coroutineScope.launch {
                            call.respond(HttpStatusCode.InternalServerError)
                        }
                    }
                }
            }


        }
    }
}


fun portForwardingSSH(sshd_host: String, target_host: String): PortForwardingSSH {
    val portforwardPort = next_portforward_port++
    val key = sshd_host + "/" + target_host
    val child = sshProcesses.getOrPut(key) {
        println()
        val ssh = Command("ssh")
            .arg("-tt")
            .args(listOf("-L", "$portforwardPort:$target_host", sshd_host))
            .spawn()
        sleep(1u)
        PortForwardingSSH(ssh, portforwardPort, 0)
    }

    child.lastUsedAt = Clock.System.now().epochSeconds

    return child
}
