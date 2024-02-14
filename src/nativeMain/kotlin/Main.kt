import com.kgit2.kommand.process.Child
import com.kgit2.kommand.process.Command
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
import platform.posix.*

data class PortForwardingSSH(val proc:Child, val localPort:Int)

var next_portforward_port = 10000

val sshProcesses = mutableMapOf<String,PortForwardingSSH>()

@OptIn(ExperimentalForeignApi::class)
val cleanup = staticCFunction<Int,Unit>(  ){
    println("Got $it")
    sshProcesses.forEach { me->
        try{
            println("Killing ${me.key}")
            me.value.proc.kill()
        }catch (e:Exception){
            e.printStackTrace()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
fun main() {
    signal(SIGINT, cleanup)
    signal(SIGKILL, cleanup)
    signal(SIGTRAP, cleanup)
    embeddedServer(CIO, port = 6060, host = "127.0.0.1", module = Application::module)
        .start(wait = true)

}
val client = HttpClient(io.ktor.client.engine.cio.CIO)


fun Application.module() {
    routing {
        get("/{ssh_host}/{target_host}/{...}") {
            val sshd_host = this.context.parameters["ssh_host"]!!
            val target_host = this.context.parameters["target_host"]!!
            println(sshd_host)
            println(target_host)
            val uri = this.context.request.uri
            println(uri)
            val sshProcess = get_ssh_tunnel(sshd_host,target_host)
            val localPort = sshProcess.localPort

            val uriToCall = uri.substring(sshd_host.length+target_host.length +2)
            val urlToCall = "http://localhost:$localPort$uriToCall"
            println("calling $urlToCall \n")
            val response = client.get(urlToCall)
            call.respondText(response.bodyAsText())
        }
    }
}


fun get_ssh_tunnel( sshd_host:String, target_host:String): PortForwardingSSH {
    val portforward_port = next_portforward_port++
    val key = sshd_host+ "/" + target_host
    val child = sshProcesses.getOrPut(key) {
        println( )
        val ssh = Command("ssh")
            .arg("-tt")
            .args(listOf("-L", "$portforward_port:$target_host", sshd_host))
            .spawn()
        sleep(1u)
        PortForwardingSSH(ssh,portforward_port)
    }
    //todo: check if process is alive
    return child
}
