package com.genymobile.scrcpy;

import android.net.LocalSocket;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * 继承LocalSocket，内部重定向标准输入输出。
 */
public class StdLocalSocket extends LocalSocket {
    
    private final InputStream stdin;
    private final OutputStream stdout;
    
    public StdLocalSocket() {
        super();
        this.stdin = System.in;
        this.stdout = System.out;
    }
    
    @Override
    public InputStream getInputStream() throws IOException {
        return stdin;
    }
    
    @Override
    public OutputStream getOutputStream() throws IOException {
        return stdout;
    }
    
    @Override
    public synchronized void close() throws IOException {
        // 可选实现: 禁止关闭System.in和System.out
    }
    
    
    @Override
    public void shutdownInput() throws IOException {
        //
    }
    
    @Override
    public void shutdownOutput() throws IOException {
        //
    }
    
    
    @Override
    public FileDescriptor getFileDescriptor() {
        return FileDescriptor.out;
    }
}
