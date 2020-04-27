function FindProxyForURL(url, host) {

// If the hostname matches, send direct.
   if (dnsDomainIs(host, "localdomain.com") ||
       shExpMatch(host, "(*.localdomain.com)"))
       return "DIRECT";

// If the protocol or URL matches, send direct.
   if (url.substring(0, 4)=="ftp:" ||
       shExpMatch(url, "http://localdomain.com/folder/*"))
       return "DIRECT";

// If the requested website is hosted within the internal network, send direct.
   if (isPlainHostName(host) ||
       shExpMatch(host, "*.local") ||
       isInNet(dnsResolve(host), "10.0.0.0", "255.0.0.0") ||
       isInNet(dnsResolve(host), "172.16.0.0", "255.240.0.0") ||
       isInNet(dnsResolve(host), "192.168.0.0", "255.255.0.0") ||
       isInNet(dnsResolve(host), "127.0.0.0", "255.255.255.0"))
       return "DIRECT";

// DEFAULT RULE: All other traffic, use below squid proxy servers in fail-over order.
   return "PROXY 1.2.3.4:3128; PROXY 5.6.7.8:3128";
}