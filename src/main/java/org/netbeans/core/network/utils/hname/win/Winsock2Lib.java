package org.netbeans.core.network.utils.hname.win;

import com.sun.jna.Library;
import com.sun.jna.Native;

/**
 * Winsock2 library on Windows
 */
public interface Winsock2Lib extends Library {

    Winsock2Lib INSTANCE = Native.loadLibrary("ws2_32", Winsock2Lib.class);

    int gethostname(byte[] name, int namelen);
}
