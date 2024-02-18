@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import org.ssh.*


@OptIn(ExperimentalForeignApi::class)
class SSHHandler {

    fun createSSHConnection(sshdHost: String, sshdUser: String, tHost: String, tPort: Int, callback: (Int) -> Unit) =
        memScoped {
            val ms = this

            val session = ssh_new() ?: throw Error("Failed to create session")
            println("ssh:  $sshdUser $sshdHost -> $tHost $tPort")
            val strictHost = alloc<IntVar>()
            strictHost.value = 0
            val port = alloc<IntVar>()
            port.value = 22
            val verbosity = alloc<UIntVar>()
            verbosity.value = SSH_LOG_PROTOCOL.toUInt()
            val localPort = 5555
            var rc = 0
            try {
                ssh_options_set(
                    session,
                    ssh_options_e.SSH_OPTIONS_KNOWNHOSTS,
                    "/Users/kgignatyev/.ssh/known_hosts".utf8.ptr
                )
                ssh_options_set(session, ssh_options_e.SSH_OPTIONS_STRICTHOSTKEYCHECK, strictHost.ptr)
                ssh_options_set(session, ssh_options_e.SSH_OPTIONS_USER, sshdUser.utf8.ptr)
                ssh_options_set(session, ssh_options_e.SSH_OPTIONS_HOST, sshdHost.utf8.ptr)
                ssh_options_set(session, ssh_options_e.SSH_OPTIONS_PORT, port.ptr)
                ssh_options_set(session, ssh_options_e.SSH_OPTIONS_LOG_VERBOSITY, verbosity.ptr)


                rc = ssh_connect(session)
                if (rc == SSH_OK) {
                    println("Connected successfully")
                } else {
                    println("Failed to connect: ${ssh_get_error(session)?.toKStringFromUtf8()}")
                }
//                val sshk = ssh_key_new()
//                rc = ssh_get_server_publickey(session, sshk.getPointer(ms) )
                val forwarding_channel = ssh_channel_new(session)
                if (forwarding_channel == null) {
                    println("Failed to create forwarding channel, is null")
                    callback(-1)

                } else {
                    rc = ssh_channel_open_forward(
                        forwarding_channel,
                        tHost, tPort,
                        "localhost", localPort
                    );

                    if (rc == SSH_OK) {
                        callback(localPort)
                    } else {
                        println("Failed to open forward channel $tHost:$tPort $rc: ${ssh_get_error(session)?.toKStringFromUtf8()}")
                        callback(-1)
                    }
                }
                ssh_channel_free(forwarding_channel);
                ssh_disconnect(session)
            } finally {

                ssh_free(session)
            }
        }

}
