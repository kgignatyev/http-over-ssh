# HTTP through SSH 

Project tries to use Kotlin/Native to create
a simple HTTP server that on demand creates a SSH connection to a remote server 
with port forwarding to a local port.

Mainly intended to be used by Prometheus to scrape metrics from a remote server

## Usage

```shell
./gradlew build
# ./gradlew runReleaseExecutableNative
./build/bin/native/releaseExecutable/http-over-ssh.kexe

```

Then in a browser enter an url like this: 
`http://localhost:6060/ubuntu@ladder.kgignatyev.com/localhost:7070/api/public/mgmt`

This will create an SSH connection to `ladder.kgignatyev.com` with port forwarding from `localhost:7070` 
a <local port>, then make an HTTP request to `localhost:<local port>/api/public/mgmt` and return the response.



