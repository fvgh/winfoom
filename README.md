# winfoom
### Basic Proxy Facade for NTLM, Kerberos, SOCKS and Proxy Auto Config file proxies
# Overview
Winfoom is an HTTP(s) proxy server facade that allows applications to authenticate through the following proxies: 

* NTML or Kerberos HTTP authenticated proxy
* SOCKS version 4 or 5, with or without authentication
* Proxy Auto Config files

typically used in corporate environments, without having to deal with the actual handshake.

A lot of software applications have problems when dealing with an authenticated proxy server's protocol. 
Winfoom sits between the corporate proxy and applications and offloads the authentication and the proxy's protocol, acting as a facade. 
This way, the software application will only have to deal with a basic proxy with no authentication.

An example of such a facade for NTLM proxies is [CNTLM](http://cntlm.sourceforge.net/)

# Getting Started
## Download Winfoom
### Download prepackaged
To try out Winfoom without needing to download the source and package it, check out the [releases](https://github.com/ecovaci/winfoom/releases) for a prepackaged `winfoom.zip`.
Winfoom is a Java application and comes with a release that includes a Java environment [AdoptOpenJDK](https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.5%2B10/OpenJDK11U-jdk_x64_windows_hotspot_11.0.5_10.msi),
so you don't have to install anything.

### Build from source code
If you decide to build the executable *jar* file from the source code, you would need these prerequisites:
* Java JDK 11(+)
* Maven 3.x version

First download  the source code from [releases](https://github.com/ecovaci/winfoom/releases) and unzip it.

Then open a terminal and execute this command inside the `winfoom-x.x.x` directory:

```
 mvn clean package
```

Now you should have the generated executable *jar* file under the *target* directory.

## Run Winfoom
> 👉 Note: Winfoom only works on Windows OS!

The prepackaged `winfoom.zip` contains a single executable file: `launch.bat`. 

Available commands:
* `launch.bat` launches the application using the bundled JRE.
* `launch.bat --debug` launches the application using the bundled JRE in debug mode.
* `launch.bat --systemjre` launches the application using your system JRE - you'll need a JRE v.11 (at least).
* `launch.bat --debug --systemjre`  launches the application using your system JRE in debug mode.

The fastest way to run Winfoom is by double-click on `launch.bat` file.

## Winfoom's logs
The application log file is placed under `<user.home.dir>/.winfoom/logs` directory.

## Configuration
Winfoom has a graphical user interface that allows configuration.
 
The first thing to select is the proxy type:
1) `HTTP` - if the remote proxy is NTLM, KERBEROS or any other HTTP proxy
2) `SOCKS4` - if the remote proxy is SOCKS version 4
3) `SOCKS5` - if the remote proxy is SOCKS version 5
4) `PAC` - if the proxy is using a Proxy Auto Config file
5) `DIRECT` - no proxy, used for various testing environments

Then fill in the required fields. You can use the field's tooltip to get more information.

### Authentication
* For HTTP proxy type, Winfoom uses the current Windows user credentials to authenticate to the remote proxy.
* For SOCKS5 proxy type, the user/password need the be provided when required.

### Test
To test it, open a browser, let's say Firefox and configure proxy like this:

![firefox](https://github.com/ecovaci/winfoom/blob/master/assets/img/firefox.png)

Now you should be able to access any URL without Firefox asking for credentials.

_If you don't have an available proxy, you still can test WinFoom by installing [WinGate](https://www.wingate.com/) and configure it to act 
as a NTML proxy._


# Todo
   - Linux/MacOS porting.
   
# Coding Guidance

Please review these docs below about coding practices.

* [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
* [Java Code Conventions](https://www.oracle.com/technetwork/java/codeconventions-150003.pdf)   

# Feedback

Any feedback or suggestions are welcome. 
It is hosted with an Apache 2.0 license so issues, forks and PRs are most appreciated.


