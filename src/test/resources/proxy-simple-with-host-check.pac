function FindProxyForURL(url, host) {

    if (shExpMatch(host, "*.local")) {
        // do nothing
    }

    if (isInNet(dnsResolve(host), "10.0.0.0", "255.0.0.0")) {
        // do nothing
    }

    if (isPlainHostName (host) ) {
          // do nothing
    }

    return 'DIRECT';
}