function FindProxyForURL(url, host) {

    if (shExpMatch(host, "*.local")) {
        // do nothing
    }

    if (isInNet(dnsResolve(host), "10.0.0.0", "255.0.0.0")) {
        // do nothing
    }

    if (isResolvable (host) ) {
          // do nothing
    }

    if (isPlainHostName (host) ) {
          // do nothing
    }

    if (shExpMatch(host, "(*.localdomain.com)")) {
        // do nothing
    }

    if (dnsDomainIs(host, "localdomain.com")) {
        // do nothing
    }

    if (localHostOrDomainIs(host, "localdomain.com")) {
        // do nothing
    }

    if (weekdayRange("MON", "FRI", "GMT")) {

    }

    if (dateRange(1, "JUN", 1995, 15, "AUG", 1995)) {

    }

    if (timeRange(0, 0, 0, 0, 0, 30)) {

    }

    var dnsDomainLvl = dnsDomainLevels("www");
    if (!( typeof dnsDomainLvl === 'number')) {
        throw new Error ("dnsDomainLevels: wrong result");
    }

    var myIp = myIpAddress();

    return 'DIRECT';
}