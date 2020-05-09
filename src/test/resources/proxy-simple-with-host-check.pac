function FindProxyForURL(url, host) {
    if (isPlainHostName (host) ) {
         return null;
    }
    return 'DIRECT';
}