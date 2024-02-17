@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import org.ssh.*



@OptIn(ExperimentalForeignApi::class)
class SSHHandler {

    fun createSSHConnection(sshdHost: String, tHost:String, tPort:Int, callback:(Int)->Unit) =
        memScoped {

            val session = ssh_new() ?: throw Error("Failed to create session")
            val port = alloc<IntVar>()
            port.value = 22
            val verbosity = alloc<UIntVar>()
            verbosity.value = SSH_LOG_DEBUG.toUInt()
            val localPort  = 5555
            var rc = 0
            try {
                ssh_options_set(session, ssh_options_e.SSH_OPTIONS_HOST, sshdHost.utf8.getPointer(this))
                ssh_options_set(session, ssh_options_e.SSH_OPTIONS_PORT, port.ptr)
                ssh_options_set(session, ssh_options_e.SSH_OPTIONS_LOG_VERBOSITY, verbosity.ptr)


                rc = ssh_connect(session)
                if (rc == SSH_OK) {
                    println("Connected successfully")
                } else {
                    println("Failed to connect: ${ssh_get_error(session)?.toKStringFromUtf8()}")
                }
                val forwarding_channel = ssh_channel_new(session)
                if( forwarding_channel == null){
                    println("Failed to create forwarding channel, is null")
                    callback(-1)

                }
                rc = ssh_channel_open_forward(forwarding_channel,
                    tHost, tPort,
                    "localhost", localPort);

                    if(rc == SSH_OK){
                        callback(localPort)
                    }else{
                        println("Failed to open forward channel $tHost:$tPort $rc: ${ssh_get_error(session)?.toKStringFromUtf8()}")
                        callback(-1)
                    }

                ssh_channel_free(forwarding_channel);
                ssh_disconnect(session)
            } finally {

                ssh_free(session)
            }
        }

}
