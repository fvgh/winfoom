function FindProxyForURL0(url, host) {
     if (isResolvable(host)) {
        return "DIRECT";
     } else {
        return "PROXY localhost:80";
     }
 }