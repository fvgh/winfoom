function FindProxyForURL(url, host) {
      return "PROXY localhost:80;SOCKS4 192.168.0.101:1080";
}