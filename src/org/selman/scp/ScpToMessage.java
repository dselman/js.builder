/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Contributors:
 *  Daniel Selman - Derived work from Apache Ant, removed everything but scp upload
 *
 */
package org.selman.scp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * Utility class to carry out an upload scp transfer.
 */
public class ScpToMessage extends AbstractSshMessage {

    private static final int BUFFER_SIZE = 1024;

    private File localFile;
    private String remotePath;

    /**
     * Constructor for ScpToMessage
     * @param session the ssh session to use
     */
    public ScpToMessage(Session session) {
        super(session);
    }

    /**
     * Constructor for a local file to remote.
     * @param verbose if true do verbose logging
     * @param session the scp session to use
     * @param aLocalFile the local file
     * @param aRemotePath the remote path
     * @since Ant 1.6.2
     */
    public ScpToMessage(Session session,
                        File aLocalFile,
                        String aRemotePath) {
        this(session, aRemotePath);
        this.localFile = aLocalFile;
    }

    /**
     * Constructor for ScpToMessage.
     * @param verbose if true do verbose logging
     * @param session the scp session to use
     * @param aRemotePath the remote path
     * @since Ant 1.6.2
     */
    private ScpToMessage(Session session,
                         String aRemotePath) {
        super(session);
        this.remotePath = aRemotePath;
    }

    /**
     * Carry out the transfer.
     * @throws IOException on i/o errors
     * @throws JSchException on errors detected by scp
     */
    public void execute() throws IOException, JSchException {
        if (localFile != null) {
            doSingleTransfer();
        }
        log("done.\n");
    }

    private void doSingleTransfer() throws IOException, JSchException {
        String cmd = "scp -t " + remotePath;
        Channel channel = openExecChannel(cmd);
        try {

            OutputStream out = channel.getOutputStream();
            InputStream in = channel.getInputStream();

            channel.connect();

            waitForAck(in);
            sendFileToRemote(localFile, in, out);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }

    private void sendFileToRemote(File localFile,
                                   InputStream in,
                                   OutputStream out) throws IOException {
        // send "C0644 filesize filename", where filename should not include '/'
        long filesize = localFile.length();
        String command = "C0644 " + filesize + " ";
        command += localFile.getName();
        command += "\n";

        out.write(command.getBytes());
        out.flush();

        waitForAck(in);

        // send a content of lfile
        FileInputStream fis = new FileInputStream(localFile);
        byte[] buf = new byte[BUFFER_SIZE];
        long totalLength = 0;

        try {
            while (true) {
                int len = fis.read(buf, 0, buf.length);
                if (len <= 0) {
                    break;
                }
                out.write(buf, 0, len);
                totalLength += len;
            }
            out.flush();
            sendAck(out);
            waitForAck(in);
        } finally {
            fis.close();
        }
    }

    /**
     * Get the local file
     * @return the local file
     */
    public File getLocalFile() {
        return localFile;
    }

    /**
     * Get the remote path
     * @return the remote path
     */
    public String getRemotePath() {
        return remotePath;
    }
}
