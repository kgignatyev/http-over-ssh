@file:OptIn(ExperimentalForeignApi::class)

import io.ktor.utils.io.charsets.*
import kotlinx.cinterop.*
import org.ssh.*
import platform.posix.size_tVar
import platform.posix.sleep


@OptIn(ExperimentalForeignApi::class)
class SSHHandler {

    fun createSSHConnection(sshdHost: String, sshdUser: String, tHost: String, tPort: Int, callback: (Int,String) -> Unit) =
        memScoped {
            val ms = this

            val session = ssh_new() ?: throw Error("Failed to create session")
            println("ssh:  $sshdUser $sshdHost -> $tHost $tPort")
            val strictHost = alloc<IntVar>()
            strictHost.value = 0
            val port = alloc<IntVar>()
            port.value = 22
            val verbosity = alloc<UIntVar>()
            verbosity.value = SSH_LOG_TRACE.toUInt()
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
                val key  = alloc<ssh_keyVar>()

                if (ssh_get_server_publickey(session, key.ptr) != SSH_OK) {
                    error("Failed to get server public key")
                }
                val keyV = key.value!!

                val hashPtrVar = alloc<CPointerVar<UByteVar>>()
                val hashLenVar = alloc<size_tVar>()
                if (ssh_get_publickey_hash(keyV,
                        ssh_publickey_hash_type.SSH_PUBLICKEY_HASH_SHA256, hashPtrVar.ptr, hashLenVar.ptr) != SSH_OK) {
                    error("Failed to get public key hash")
                }
                val hashPtr = hashPtrVar.value!!
                val hashLen = hashLenVar.value

                // Now you can use hashPtr for verification purposes, for example, print the hash
                println("Public Key Hash (SHA256):")
                for (i in 0 until hashLen.toInt()) {
                    print(hashPtr[i].toInt() and 0xff)
                }
                if(ssh_userauth_autopubkey(session, null) != SSH_OK) {
                    error("Failed to authenticate")
                }
                val forwarding_channel = ssh_channel_new(session)
                if (forwarding_channel == null) {
                    println("Failed to create forwarding channel, is null")
                    callback(-1,"error")

                } else {
                    rc = ssh_channel_open_forward(
                        forwarding_channel,
                        "www.google.com", 80,
                        "localhost", localPort
                    );

                    if (rc == SSH_OK) {
                        println("\n\nForwarding channel opened for 30 seconds")
                        val getRequest = """
                            GET / HTTP/1.1
                            Host: www.google.com
                            Connection: close
                            
                        """.trimIndent()
                        println( getRequest)
                        val written = ssh_channel_write(forwarding_channel, getRequest.utf8.ptr, getRequest.length.toUInt())
                        println("Written $written, sent ${getRequest.length}")
                        ssh_channel_send_eof(forwarding_channel)

                        val isOpen = ssh_channel_is_open(forwarding_channel)
                        println("Channel open: $isOpen")
                        val bufferLength = 4096
                        val buffer = allocArray<ByteVar>(bufferLength)
                        var totalBytesRead = 0
                        val stringBuilder = StringBuilder()
                        var retryCount = 0
                        do {
                            println("reading ${retryCount}...")
                            val bytesRead = ssh_channel_read(forwarding_channel, buffer, bufferLength.toUInt(), 0)
                            if (bytesRead > 0) {
                                retryCount = 1000
                                // Assuming the buffer contains text data; adjust for binary data as needed
                                val readString = buffer.readBytes(bytesRead).toString()
                                stringBuilder.append(readString)
                                totalBytesRead += bytesRead
                            }else{
                                retryCount++
                                sleep(1.toUInt())
                            }
                        } while (bytesRead > 0 || retryCount < 5)
                        println("Read $totalBytesRead bytes: ${stringBuilder}")
                        callback(localPort,stringBuilder.toString())
                    } else {
                        println("Failed to open forward channel $tHost:$tPort $rc: ${ssh_get_error(session)?.toKStringFromUtf8()}")
                    }
                }
                ssh_channel_free(forwarding_channel);
                ssh_disconnect(session)
            } finally {

                ssh_free(session)
            }
        }

}
